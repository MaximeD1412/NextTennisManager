// Golden fixture tests: same (env, shots, seed) must always produce the same
// binary-encoded PhysicsBatchResponse. CI fails when physics output diverges
// from the committed fixture without a deliberate version bump.

use std::{fs, io::{self, Read, Write}, path::Path, process::{Command, Stdio}};

use physics_core::generated::{
    CourtPosition, PhysicsBatchRequest, PhysicsBatchResponse, PhysicsEnvironment,
    ShotSpec, Surface, SurfaceCoefficients, Vector3,
};
use physics_core::physics::simulate_batch;
use prost::Message;

// ── Fixture inputs ─────────────────────────────────────────────────────────────

fn fixture_env() -> PhysicsEnvironment {
    PhysicsEnvironment {
        surface: Surface::Clay as i32,
        surface_wetness: 0.2,
        wind: None,
        coefficients: Some(SurfaceCoefficients {
            drag: 0.47,
            magnus: 0.10,
            restitution: 0.75,
            friction: 0.60,
            wetness_bounce_scale: 1.3,
            wetness_friction_scale: 1.4,
        }),
    }
}

fn fixture_shots() -> Vec<ShotSpec> {
    vec![
        // Flat drive from baseline
        ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -5.0, z: 1.5 }),
            velocity: Some(Vector3 { x: 0.0, y: 15.0, z: 2.0 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "golden".into(),
            point_index: 0,
            shot_index: 0,
        },
        // Topspin shot
        ShotSpec {
            contact_position: Some(CourtPosition { x: 0.5, y: -6.0, z: 1.8 }),
            velocity: Some(Vector3 { x: 0.5, y: 14.0, z: 3.0 }),
            spin_rpm: 2500.0,
            spin_axis: Some(Vector3 { x: -1.0, y: 0.0, z: 0.0 }),
            match_id: "golden".into(),
            point_index: 0,
            shot_index: 1,
        },
        // Slice — low, angled
        ShotSpec {
            contact_position: Some(CourtPosition { x: -1.0, y: -4.0, z: 1.2 }),
            velocity: Some(Vector3 { x: 1.0, y: 12.0, z: 1.0 }),
            spin_rpm: 1000.0,
            spin_axis: Some(Vector3 { x: 1.0, y: 0.0, z: 0.0 }),
            match_id: "golden".into(),
            point_index: 0,
            shot_index: 2,
        },
    ]
}

const FIXTURE_SEED: u64 = 0xDEAD_BEEF_1337_0042;

// ── Golden fixture test ────────────────────────────────────────────────────────

#[test]
fn golden_batch_fixture() {
    let fixture_path =
        Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/golden/batch_001.pb");

    let results = simulate_batch(fixture_env(), &fixture_shots(), FIXTURE_SEED);
    let response = PhysicsBatchResponse { results };
    let encoded = response.encode_to_vec();

    if fixture_path.exists() {
        let stored = fs::read(&fixture_path)
            .expect("failed to read golden fixture");
        assert_eq!(
            encoded, stored,
            "physics output diverges from golden fixture — bump the simulator version if this change is intentional"
        );
    } else {
        fs::create_dir_all(fixture_path.parent().unwrap()).unwrap();
        fs::write(&fixture_path, &encoded).expect("failed to write golden fixture");
        panic!(
            "Generated golden fixture at {:?}; commit it and re-run tests.",
            fixture_path
        );
    }
}

// ── Sidecar round-trip test ────────────────────────────────────────────────────

#[test]
fn sidecar_round_trip() {
    let sidecar_bin = env!("CARGO_BIN_EXE_sidecar");

    let request = PhysicsBatchRequest {
        env: Some(fixture_env()),
        shots: fixture_shots(),
        seed: FIXTURE_SEED,
    };
    let request_bytes = request.encode_to_vec();

    let mut req_with_framing = Vec::new();
    write_varint(&mut req_with_framing, request_bytes.len() as u64).unwrap();
    req_with_framing.extend_from_slice(&request_bytes);

    let mut child = Command::new(sidecar_bin)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit())
        .spawn()
        .expect("failed to spawn sidecar");

    child
        .stdin
        .take()
        .unwrap()
        .write_all(&req_with_framing)
        .expect("failed to write to sidecar stdin");
    // Close stdin so the sidecar exits cleanly after processing
    drop(child.stdin.take());

    let output = child.wait_with_output().expect("failed to wait for sidecar");
    assert!(output.status.success(), "sidecar exited with error: {:?}", output.status);

    let mut stdout = output.stdout.as_slice();
    let len = read_varint(&mut stdout).expect("failed to read response length varint");
    let mut body = vec![0u8; len as usize];
    stdout.read_exact(&mut body).expect("failed to read response body");

    let response = PhysicsBatchResponse::decode(body.as_slice())
        .expect("failed to decode PhysicsBatchResponse");

    assert_eq!(
        response.results.len(),
        fixture_shots().len(),
        "sidecar must return one result per shot"
    );

    // Verify sidecar output matches direct simulate_batch call
    let direct_results = simulate_batch(fixture_env(), &fixture_shots(), FIXTURE_SEED);
    let direct_response = PhysicsBatchResponse { results: direct_results };
    assert_eq!(
        response.encode_to_vec(),
        direct_response.encode_to_vec(),
        "sidecar output must match direct simulate_batch output"
    );
}

// ── Varint helpers (mirrors sidecar implementation) ───────────────────────────

fn read_varint<R: io::Read>(reader: &mut R) -> io::Result<u64> {
    let mut result: u64 = 0;
    let mut shift = 0u32;
    loop {
        let mut byte = [0u8; 1];
        reader.read_exact(&mut byte)?;
        let b = byte[0] as u64;
        result |= (b & 0x7F) << shift;
        if b & 0x80 == 0 {
            return Ok(result);
        }
        shift += 7;
        if shift >= 64 {
            return Err(io::Error::new(io::ErrorKind::InvalidData, "varint overflow"));
        }
    }
}

fn write_varint<W: io::Write>(writer: &mut W, mut value: u64) -> io::Result<()> {
    loop {
        let mut byte = (value & 0x7F) as u8;
        value >>= 7;
        if value != 0 {
            byte |= 0x80;
        }
        writer.write_all(&[byte])?;
        if value == 0 {
            break;
        }
    }
    Ok(())
}
