use rand::{Rng, SeedableRng};
use rand::rngs::SmallRng;

use crate::generated::{
    BallFlightSegment, Bounce, CourtPosition, NetInteraction, NetInteractionKind,
    PhysicsEnvironment, PhysicsSimulationResult, ShotSpec, Surface,
    SurfaceCoefficients, Vector3, WindVector,
};

const GRAVITY: f32 = 9.81;
const BALL_MASS_KG: f32 = 0.057;
pub const BALL_RADIUS_M: f32 = 0.0335;
const AIR_DENSITY: f32 = 1.204;
// π × r² = 3.14159 × 0.001122 ≈ 0.003525 m²
const BALL_AREA_M2: f32 = 0.003525;
pub const NET_CENTER_HEIGHT_M: f32 = 0.914;
pub const NET_POST_HEIGHT_M: f32 = 1.07;
pub const NET_POST_X_M: f32 = 5.029;
pub const SERVICE_BOX_DEPTH_M: f32 = 6.40;
pub const SINGLES_HALF_WIDTH_M: f32 = 4.115;
const DT: f32 = 0.002;
const MAX_STEPS: usize = 5000;
// Spin decay fraction applied to horizontal velocity from spin contact
const SPIN_CONTACT_FRACTION: f32 = 0.3;

// ── Vector math on [f32; 3] ────────────────────────────────────────────────

fn cross(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    [
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    ]
}

fn mag(v: [f32; 3]) -> f32 {
    (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).sqrt()
}

fn norm(v: [f32; 3]) -> [f32; 3] {
    let m = mag(v);
    if m < 1e-8 {
        return [0.0, 0.0, 1.0];
    }
    [v[0] / m, v[1] / m, v[2] / m]
}

fn add(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    [a[0] + b[0], a[1] + b[1], a[2] + b[2]]
}

fn sub(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
}

fn scale(v: [f32; 3], s: f32) -> [f32; 3] {
    [v[0] * s, v[1] * s, v[2] * s]
}

// ── Protobuf conversions ───────────────────────────────────────────────────

fn cp_to_arr(v: Option<CourtPosition>) -> [f32; 3] {
    v.map(|p| [p.x, p.y, p.z]).unwrap_or([0.0; 3])
}

fn v3_to_arr(v: Option<Vector3>) -> [f32; 3] {
    v.map(|p| [p.x, p.y, p.z]).unwrap_or([0.0; 3])
}

fn arr_to_cp(v: [f32; 3]) -> CourtPosition {
    CourtPosition { x: v[0], y: v[1], z: v[2] }
}

fn arr_to_v3(v: [f32; 3]) -> Vector3 {
    Vector3 { x: v[0], y: v[1], z: v[2] }
}

// ── Physics helpers ────────────────────────────────────────────────────────

fn net_height_at(x: f32) -> f32 {
    let t = (x.abs() / NET_POST_X_M).min(1.0);
    NET_CENTER_HEIGHT_M + t * (NET_POST_HEIGHT_M - NET_CENTER_HEIGHT_M)
}

fn wind_vec(w: Option<WindVector>, is_indoor: bool) -> [f32; 3] {
    if is_indoor {
        return [0.0; 3];
    }
    match w {
        Some(wv) => [wv.direction.cos() * wv.intensity, wv.direction.sin() * wv.intensity, 0.0],
        None => [0.0; 3],
    }
}

fn default_coeffs(opt: Option<SurfaceCoefficients>) -> SurfaceCoefficients {
    opt.unwrap_or(SurfaceCoefficients {
        drag: 0.47,
        magnus: 0.10,
        restitution: 0.75,
        friction: 0.60,
        wetness_bounce_scale: 1.0,
        wetness_friction_scale: 1.0,
    })
}

// FNV-1a hash of match_id + point_index + shot_index + "netTape" discriminator.
// Including match_id prevents two different matches with the same point/shot
// indices from producing identical tape outcomes.
fn tape_rng_seed(spec: &ShotSpec) -> u64 {
    const FNV_OFFSET: u64 = 0xcbf2_9ce4_8422_2325;
    const FNV_PRIME: u64 = 0x0000_0100_0000_01b3;
    let mut h = FNV_OFFSET;
    for b in spec.match_id.as_bytes() {
        h ^= *b as u64;
        h = h.wrapping_mul(FNV_PRIME);
    }
    h ^= spec.point_index as u64;
    h = h.wrapping_mul(FNV_PRIME);
    h ^= spec.shot_index as u64;
    h = h.wrapping_mul(FNV_PRIME);
    for b in b"netTape" {
        h ^= *b as u64;
        h = h.wrapping_mul(FNV_PRIME);
    }
    h
}

// Semi-implicit Euler acceleration (drag relative to air, Magnus relative to ball).
fn accel(
    vel: [f32; 3],
    wind: [f32; 3],
    spin_axis: [f32; 3],
    spin_rpm: f32,
    c_drag: f32,
    c_magnus: f32,
) -> [f32; 3] {
    let v_rel = sub(vel, wind);
    let v_rel_mag = mag(v_rel);
    let a_drag = scale(v_rel, -c_drag * v_rel_mag);

    let omega_rad = spin_rpm * std::f32::consts::TAU / 60.0;
    let omega_vec = scale(spin_axis, omega_rad);
    let a_magnus = scale(cross(omega_vec, v_rel), c_magnus);

    [
        a_drag[0] + a_magnus[0],
        a_drag[1] + a_magnus[1],
        a_drag[2] + a_magnus[2] - GRAVITY,
    ]
}

// ── Public API ─────────────────────────────────────────────────────────────

pub fn simulate_batch(
    env: PhysicsEnvironment,
    shots: &[ShotSpec],
    seed: u64,
) -> Vec<PhysicsSimulationResult> {
    shots
        .iter()
        .enumerate()
        .map(|(i, spec)| simulate_shot_inner(&env, spec, seed.wrapping_add(i as u64)))
        .collect()
}

pub fn simulate_shot(env: &PhysicsEnvironment, spec: &ShotSpec) -> PhysicsSimulationResult {
    simulate_shot_inner(env, spec, tape_rng_seed(spec))
}

fn simulate_shot_inner(env: &PhysicsEnvironment, spec: &ShotSpec, tape_seed: u64) -> PhysicsSimulationResult {
    let coeffs = default_coeffs(env.coefficients);
    let is_indoor = env.surface == Surface::IndoorHard as i32;

    let mut pos = cp_to_arr(spec.contact_position);
    let mut vel = v3_to_arr(spec.velocity);
    let mut spin_rpm = spec.spin_rpm;
    let spin_axis = norm(v3_to_arr(spec.spin_axis));

    let wind = wind_vec(env.wind, is_indoor);

    let c_drag = coeffs.drag * AIR_DENSITY * BALL_AREA_M2 / (2.0 * BALL_MASS_KG);
    let c_magnus = coeffs.magnus * AIR_DENSITY * BALL_AREA_M2 * BALL_RADIUS_M
        / (2.0 * BALL_MASS_KG);

    let mut segments: Vec<BallFlightSegment> = Vec::new();
    let mut net_interaction: Option<NetInteraction> = None;
    let mut bounce_event: Option<Bounce> = None;
    let mut cleared_net = false;
    let mut net_checked = false;
    let mut landing_position: Option<CourtPosition> = None;

    // Segment state
    let mut seg_start_pos = pos;
    let mut seg_start_vel = vel;
    let mut seg_elapsed = 0.0_f32;
    let mut peak_height = pos[2];

    'integration: for _ in 0..MAX_STEPS {
        let a = accel(vel, wind, spin_axis, spin_rpm, c_drag, c_magnus);
        let new_vel = add(vel, scale(a, DT));
        let new_pos = add(pos, scale(new_vel, DT)); // semi-implicit Euler

        if new_pos[2] > peak_height {
            peak_height = new_pos[2];
        }

        // ── Net crossing ──────────────────────────────────────────────────
        if !net_checked && pos[1] != 0.0 && pos[1] * new_pos[1] <= 0.0 {
            net_checked = true;

            let denom = new_pos[1] - pos[1];
            let t_frac = if denom.abs() > 1e-8 { -pos[1] / denom } else { 0.5 };
            let x_net = pos[0] + (new_pos[0] - pos[0]) * t_frac;
            let z_net = pos[2] + (new_pos[2] - pos[2]) * t_frac;
            let net_h = net_height_at(x_net);
            let vel_net = add(vel, scale(a, DT * t_frac));

            if x_net.abs() > NET_POST_X_M + BALL_RADIUS_M {
                // Around the post — no NetInteraction, just mark cleared
                cleared_net = true;
            } else if z_net >= net_h + BALL_RADIUS_M {
                cleared_net = true;
                net_interaction = Some(NetInteraction {
                    kind: NetInteractionKind::Clear as i32,
                    position: Some(CourtPosition { x: x_net, y: 0.0, z: z_net }),
                    net_height_m: net_h,
                    velocity_at_net: Some(arr_to_v3(vel_net)),
                    spin_rpm_at_net: spin_rpm,
                    pass_probability: 1.0,
                });
            } else if z_net <= net_h - BALL_RADIUS_M {
                cleared_net = false;
                net_interaction = Some(NetInteraction {
                    kind: NetInteractionKind::NetFault as i32,
                    position: Some(CourtPosition { x: x_net, y: 0.0, z: z_net }),
                    net_height_m: net_h,
                    velocity_at_net: Some(arr_to_v3(vel_net)),
                    spin_rpm_at_net: spin_rpm,
                    pass_probability: 0.0,
                });
                push_segment(
                    &mut segments,
                    seg_start_pos,
                    [x_net, 0.0, z_net],
                    seg_start_vel,
                    vel_net,
                    spin_rpm,
                    spin_axis,
                    (seg_elapsed + DT * t_frac) * 1000.0,
                    peak_height,
                );
                break 'integration;
            } else {
                // Tape contact — probabilistic, seeded
                let pp = ((z_net - (net_h - BALL_RADIUS_M)) / (2.0 * BALL_RADIUS_M))
                    .clamp(0.0, 1.0);
                let passed = SmallRng::seed_from_u64(tape_seed).gen::<f32>() < pp;
                if passed {
                    cleared_net = true;
                    let vel_post_tape = scale(vel_net, 0.85);
                    let spin_post_tape = spin_rpm * 0.70;
                    // Close the pre-tape segment at the net crossing point.
                    push_segment(
                        &mut segments,
                        seg_start_pos,
                        [x_net, 0.0, z_net],
                        seg_start_vel,
                        vel_net,
                        spin_rpm,
                        spin_axis,
                        (seg_elapsed + DT * t_frac) * 1000.0,
                        peak_height,
                    );
                    net_interaction = Some(NetInteraction {
                        kind: NetInteractionKind::NetTapePassed as i32,
                        position: Some(CourtPosition { x: x_net, y: 0.0, z: z_net }),
                        net_height_m: net_h,
                        velocity_at_net: Some(arr_to_v3(vel_post_tape)),
                        spin_rpm_at_net: spin_post_tape,
                        pass_probability: pp,
                    });
                    // Restart integration from net crossing with post-tape state.
                    pos = [x_net, 0.0, z_net];
                    vel = vel_post_tape;
                    spin_rpm = spin_post_tape;
                    seg_start_pos = pos;
                    seg_start_vel = vel;
                    seg_elapsed = 0.0;
                    peak_height = z_net;
                    continue 'integration;
                } else {
                    cleared_net = false;
                    net_interaction = Some(NetInteraction {
                        kind: NetInteractionKind::NetTapeFailed as i32,
                        position: Some(CourtPosition { x: x_net, y: 0.0, z: z_net }),
                        net_height_m: net_h,
                        velocity_at_net: Some(arr_to_v3(vel_net)),
                        spin_rpm_at_net: spin_rpm,
                        pass_probability: pp,
                    });
                    push_segment(
                        &mut segments,
                        seg_start_pos,
                        [x_net, 0.0, z_net],
                        seg_start_vel,
                        vel_net,
                        spin_rpm,
                        spin_axis,
                        (seg_elapsed + DT * t_frac) * 1000.0,
                        peak_height,
                    );
                    break 'integration;
                }
            }
        }

        // ── Ground contact ────────────────────────────────────────────────
        // Threshold at BALL_RADIUS_M (ball centre, consistent with net check).
        if pos[2] > BALL_RADIUS_M && new_pos[2] <= BALL_RADIUS_M {
            let denom = pos[2] - new_pos[2];
            let t_frac = if denom.abs() > 1e-8 { (pos[2] - BALL_RADIUS_M) / denom } else { 1.0 };
            let land_x = pos[0] + (new_pos[0] - pos[0]) * t_frac;
            let land_y = pos[1] + (new_pos[1] - pos[1]) * t_frac;
            let land = [land_x, land_y, 0.0f32];
            let vel_impact = add(vel, scale(a, DT * t_frac));

            push_segment(
                &mut segments,
                seg_start_pos,
                land,
                seg_start_vel,
                vel_impact,
                spin_rpm,
                spin_axis,
                (seg_elapsed + DT * t_frac) * 1000.0,
                peak_height,
            );

            if bounce_event.is_none() {
                // First bounce
                let wetness = env.surface_wetness.clamp(0.0, 1.0);
                let eff_restitution = coeffs.restitution
                    * (1.0 + wetness * (coeffs.wetness_bounce_scale - 1.0));
                let eff_friction = coeffs.friction
                    * (1.0 + wetness * (coeffs.wetness_friction_scale - 1.0));

                // Spin tangential direction: spin_axis × normal (z)
                let up = [0.0f32, 0.0, 1.0];
                let spin_tang = norm(cross(spin_axis, up));
                let spin_surface_speed =
                    BALL_RADIUS_M * spin_rpm * std::f32::consts::TAU / 60.0;
                let spin_contrib = spin_surface_speed * eff_friction * SPIN_CONTACT_FRACTION;

                let vz_out = eff_restitution * vel_impact[2].abs();
                let vx_out = vel_impact[0] * (1.0 - eff_friction)
                    + spin_tang[0] * spin_contrib;
                let vy_out = vel_impact[1] * (1.0 - eff_friction)
                    + spin_tang[1] * spin_contrib;

                let vel_out = [vx_out, vy_out, vz_out];
                let spin_rpm_out = spin_rpm * 0.5;

                bounce_event = Some(Bounce {
                    position: Some(arr_to_cp(land)),
                    incoming_velocity: Some(arr_to_v3(vel_impact)),
                    outgoing_velocity: Some(arr_to_v3(vel_out)),
                    incoming_spin_rpm: spin_rpm,
                    outgoing_spin_rpm: spin_rpm_out,
                    incoming_spin_axis: Some(arr_to_v3(spin_axis)),
                    outgoing_spin_axis: Some(arr_to_v3(spin_axis)),
                    surface: env.surface,
                    surface_wetness: wetness,
                });

                landing_position = Some(arr_to_cp(land));

                if is_indoor {
                    break 'integration;
                }

                // Outdoor: continue post-bounce flight from ball centre above ground.
                pos = [land_x, land_y, BALL_RADIUS_M];
                vel = vel_out;
                spin_rpm = spin_rpm_out;
                seg_start_pos = pos;
                seg_start_vel = vel_out;
                seg_elapsed = 0.0;
                peak_height = BALL_RADIUS_M;
                continue 'integration;
            } else {
                // Second landing — close post-bounce segment, done
                break 'integration;
            }
        }

        pos = new_pos;
        vel = new_vel;
        seg_elapsed += DT;
    }

    // Safety: ensure at least one segment
    if segments.is_empty() {
        push_segment(
            &mut segments,
            seg_start_pos,
            pos,
            seg_start_vel,
            vel,
            spin_rpm,
            spin_axis,
            seg_elapsed * 1000.0,
            peak_height,
        );
        if landing_position.is_none() {
            landing_position = Some(arr_to_cp(pos));
        }
    }

    PhysicsSimulationResult {
        landing_position,
        ball_flight_segments: segments,
        bounce: bounce_event,
        net_interaction,
        serve_let: None, // serve_let detection is delegated to higher-level logic
        cleared_net,
    }
}

fn push_segment(
    segments: &mut Vec<BallFlightSegment>,
    from: [f32; 3],
    to: [f32; 3],
    start_vel: [f32; 3],
    end_vel: [f32; 3],
    spin_rpm: f32,
    spin_axis: [f32; 3],
    duration_ms: f32,
    peak_height: f32,
) {
    let dist = mag(sub(to, from));
    let speed_ms = if duration_ms > 0.0 {
        dist / (duration_ms / 1000.0)
    } else {
        mag(start_vel)
    };

    segments.push(BallFlightSegment {
        from: Some(arr_to_cp(from)),
        to: Some(arr_to_cp(to)),
        duration_ms,
        speed_kmh: speed_ms * 3.6,
        spin: 0, // ShotEffect::Unspecified; Rust only has physical values
        start_velocity: Some(arr_to_v3(start_vel)),
        end_velocity: Some(arr_to_v3(end_vel)),
        spin_rpm,
        spin_axis: Some(arr_to_v3(spin_axis)),
        peak_height,
    });
}

// ── Tests ──────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    fn indoor_env(drag: f32, magnus: f32) -> PhysicsEnvironment {
        PhysicsEnvironment {
            surface: Surface::IndoorHard as i32,
            surface_wetness: 0.0,
            wind: None,
            coefficients: Some(SurfaceCoefficients {
                drag,
                magnus,
                restitution: 0.75,
                friction: 0.60,
                wetness_bounce_scale: 1.0,
                wetness_friction_scale: 1.0,
            }),
        }
    }

    fn outdoor_env(surface: Surface, wetness: f32) -> PhysicsEnvironment {
        PhysicsEnvironment {
            surface: surface as i32,
            surface_wetness: wetness,
            wind: None,
            coefficients: Some(SurfaceCoefficients {
                drag: 0.47,
                magnus: 0.10,
                restitution: 0.75,
                friction: 0.60,
                wetness_bounce_scale: if surface == Surface::Clay { 1.3 } else { 0.85 },
                wetness_friction_scale: if surface == Surface::Clay { 1.4 } else { 0.75 },
            }),
        }
    }

    // contact y=-5, z=1.5, vz=2.0: z_net ≈ 1.6m with no drag, ≈1.5m with drag=0.47 — clears 0.914m net
    fn flat_serve_spec() -> ShotSpec {
        ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -5.0, z: 1.5 }),
            velocity: Some(Vector3 { x: 0.0, y: 15.0, z: 2.0 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "test".into(),
            point_index: 1,
            shot_index: 1,
        }
    }

    #[test]
    fn flat_drive_lands_in_bounds_indoor_hard() {
        let env = indoor_env(0.47, 0.0);
        let spec = flat_serve_spec();
        let result = simulate_shot(&env, &spec);

        let land = result.landing_position.expect("should have landing");
        assert!(land.y > 0.0, "ball should cross net: y={}", land.y);
        assert!(land.y < 12.0, "ball should land in court: y={}", land.y);
        assert!(land.x.abs() < 5.0, "ball in lateral bounds: x={}", land.x);
    }

    #[test]
    fn topspin_lands_shorter_than_flat() {
        let env = indoor_env(0.47, 0.15);
        // Topspin for +y motion: spin_axis = [-1, 0, 0], spin_rpm > 0
        let flat_spec = flat_serve_spec();
        let mut topspin_spec = flat_serve_spec();
        topspin_spec.spin_rpm = 3000.0;
        topspin_spec.spin_axis = Some(Vector3 { x: -1.0, y: 0.0, z: 0.0 });

        let flat_land = simulate_shot(&env, &flat_spec)
            .landing_position
            .unwrap();
        let topspin_land = simulate_shot(&env, &topspin_spec)
            .landing_position
            .unwrap();

        assert!(
            topspin_land.y < flat_land.y,
            "topspin should land shorter: topspin_y={} flat_y={}",
            topspin_land.y,
            flat_land.y
        );
    }

    #[test]
    fn indoor_hard_emits_exactly_one_segment() {
        let env = indoor_env(0.47, 0.10);
        let result = simulate_shot(&env, &flat_serve_spec());
        assert_eq!(result.ball_flight_segments.len(), 1, "INDOOR_HARD must emit exactly 1 segment");
    }

    #[test]
    fn outdoor_emits_at_least_two_segments() {
        let env = outdoor_env(Surface::Clay, 0.0);
        let result = simulate_shot(&env, &flat_serve_spec());
        assert!(
            result.ball_flight_segments.len() >= 2,
            "outdoor must emit ≥2 segments, got {}",
            result.ball_flight_segments.len()
        );
    }

    #[test]
    fn around_net_no_interaction_emitted() {
        // Ball going wide past net post
        let env = indoor_env(0.0, 0.0);
        let spec = ShotSpec {
            contact_position: Some(CourtPosition { x: 6.0, y: -5.0, z: 1.5 }),
            velocity: Some(Vector3 { x: 0.0, y: 20.0, z: 0.0 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "test".into(),
            point_index: 0,
            shot_index: 0,
        };
        let result = simulate_shot(&env, &spec);
        assert!(result.net_interaction.is_none(), "around-net should emit no NetInteraction");
        assert!(result.cleared_net, "around-net should set cleared_net");
    }

    #[test]
    fn net_fault_sets_cleared_net_false() {
        // Ball reaches net at z≈0.60m < net fault threshold 0.88m (drag=0 so no extra fall)
        let env = indoor_env(0.0, 0.0);
        let spec = ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -3.0, z: 0.8 }),
            velocity: Some(Vector3 { x: 0.0, y: 15.0, z: 0.0 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "test".into(),
            point_index: 0,
            shot_index: 0,
        };
        let result = simulate_shot(&env, &spec);
        assert!(!result.cleared_net, "net fault must have cleared_net=false");
        let ni = result.net_interaction.expect("net fault must emit NetInteraction");
        assert_eq!(
            ni.kind,
            NetInteractionKind::NetFault as i32,
            "kind must be NetFault"
        );
    }

    #[test]
    fn tape_pass_is_deterministic() {
        // Trajectory: y=-3, vy=15, z=1.0, vz=0.5 → z_net ≈ 0.902 (tape zone [0.880, 0.948]).
        let env = indoor_env(0.0, 0.0);
        let spec = ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -3.0, z: 1.0 }),
            velocity: Some(Vector3 { x: 0.0, y: 15.0, z: 0.5 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "test".into(),
            point_index: 5,
            shot_index: 3,
        };
        let r1 = simulate_shot(&env, &spec);
        let r2 = simulate_shot(&env, &spec);
        assert_eq!(
            r1.cleared_net, r2.cleared_net,
            "determinism: cleared_net must match"
        );
        assert_eq!(
            r1.net_interaction.map(|n| n.kind),
            r2.net_interaction.map(|n| n.kind),
            "determinism: net interaction kind must match"
        );
    }

    #[test]
    fn physics_never_emits_serve_let() {
        // serve_let detection requires serve metadata (server_id, serve_number, court_side)
        // not present in ShotSpec — it is delegated to higher-level Java logic.
        let env = indoor_env(0.0, 0.0);
        let spec = ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -3.0, z: 1.0 }),
            velocity: Some(Vector3 { x: 0.0, y: 15.0, z: 0.5 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "test".into(),
            point_index: 0,
            shot_index: 0,
        };
        let result = simulate_shot(&env, &spec);
        assert!(result.serve_let.is_none(), "physics must never populate serve_let");
    }

    #[test]
    fn post_bounce_vz_positive() {
        let env = indoor_env(0.47, 0.0);
        let result = simulate_shot(&env, &flat_serve_spec());
        let b = result.bounce.expect("should bounce");
        let vz = b.outgoing_velocity.unwrap().z;
        assert!(vz > 0.0, "post-bounce vz must be positive, got {}", vz);
    }

    #[test]
    fn post_bounce_spin_reduced() {
        let env = indoor_env(0.47, 0.10);
        let spec = ShotSpec {
            spin_rpm: 2000.0,
            spin_axis: Some(Vector3 { x: -1.0, y: 0.0, z: 0.0 }),
            ..flat_serve_spec()
        };
        let result = simulate_shot(&env, &spec);
        let b = result.bounce.expect("should bounce");
        assert!(
            b.outgoing_spin_rpm < b.incoming_spin_rpm,
            "spin must be reduced by surface: {} < {}",
            b.outgoing_spin_rpm,
            b.incoming_spin_rpm
        );
    }

    #[test]
    fn clay_wet_lower_horizontal_speed_than_dry() {
        let dry_env = outdoor_env(Surface::Clay, 0.0);
        let wet_env = outdoor_env(Surface::Clay, 1.0);
        let spec = flat_serve_spec();

        let dry_b = simulate_shot(&dry_env, &spec).bounce.unwrap();
        let wet_b = simulate_shot(&wet_env, &spec).bounce.unwrap();

        let dry_hspeed = {
            let v = dry_b.outgoing_velocity.unwrap();
            (v.x * v.x + v.y * v.y).sqrt()
        };
        let wet_hspeed = {
            let v = wet_b.outgoing_velocity.unwrap();
            (v.x * v.x + v.y * v.y).sqrt()
        };
        assert!(
            wet_hspeed < dry_hspeed,
            "clay wet → lower horizontal speed: wet={} dry={}",
            wet_hspeed,
            dry_hspeed
        );
    }

    #[test]
    fn grass_wet_higher_horizontal_speed_than_dry() {
        let dry_env = outdoor_env(Surface::Grass, 0.0);
        let wet_env = outdoor_env(Surface::Grass, 1.0);
        let spec = flat_serve_spec();

        let dry_b = simulate_shot(&dry_env, &spec).bounce.unwrap();
        let wet_b = simulate_shot(&wet_env, &spec).bounce.unwrap();

        let dry_hspeed = {
            let v = dry_b.outgoing_velocity.unwrap();
            (v.x * v.x + v.y * v.y).sqrt()
        };
        let wet_hspeed = {
            let v = wet_b.outgoing_velocity.unwrap();
            (v.x * v.x + v.y * v.y).sqrt()
        };
        assert!(
            wet_hspeed > dry_hspeed,
            "grass wet → higher horizontal speed: wet={} dry={}",
            wet_hspeed,
            dry_hspeed
        );
    }

    #[test]
    fn wind_zero_on_indoor_hard() {
        let spec = flat_serve_spec();

        // Clay outdoor: with and without +x wind
        let clay_no_wind = outdoor_env(Surface::Clay, 0.0);
        let mut clay_wind = outdoor_env(Surface::Clay, 0.0);
        clay_wind.wind = Some(WindVector { direction: 0.0, intensity: 10.0 });

        // Indoor hard: with and without wind
        let mut indoor_no_wind = clay_no_wind.clone();
        indoor_no_wind.surface = Surface::IndoorHard as i32;
        let mut indoor_wind = indoor_no_wind.clone();
        indoor_wind.wind = Some(WindVector { direction: 0.0, intensity: 10.0 });

        let clay_no_wind_land = simulate_shot(&clay_no_wind, &spec).landing_position.unwrap();
        let clay_wind_land = simulate_shot(&clay_wind, &spec).landing_position.unwrap();
        let indoor_no_wind_land = simulate_shot(&indoor_no_wind, &spec).landing_position.unwrap();
        let indoor_wind_land = simulate_shot(&indoor_wind, &spec).landing_position.unwrap();

        // Indoor: wind flag has zero effect
        assert_eq!(indoor_wind_land.x, indoor_no_wind_land.x, "indoor: wind must not deflect x");
        assert_eq!(indoor_wind_land.y, indoor_no_wind_land.y, "indoor: wind must not deflect y");

        // Outdoor: +x wind must deflect ball in +x
        assert!(
            clay_wind_land.x > clay_no_wind_land.x,
            "outdoor wind must deflect ball in +x: wind_x={} no_wind_x={}",
            clay_wind_land.x,
            clay_no_wind_land.x
        );
    }

    #[test]
    fn lob_more_deflected_than_flat_drive_under_wind() {
        let mut env = outdoor_env(Surface::Clay, 0.0);
        // Wind blowing in +x
        env.wind = Some(WindVector { direction: 0.0, intensity: 8.0 });

        // Flat drive: same reliable trajectory as flat_serve_spec
        let flat = ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -5.0, z: 1.5 }),
            velocity: Some(Vector3 { x: 0.0, y: 15.0, z: 2.0 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "test".into(),
            point_index: 0,
            shot_index: 0,
        };
        // Lob: peak z > 2m — high arc from baseline
        let lob = ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -8.0, z: 1.2 }),
            velocity: Some(Vector3 { x: 0.0, y: 10.0, z: 8.0 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "test".into(),
            point_index: 0,
            shot_index: 0,
        };

        let flat_land = simulate_shot(&env, &flat).landing_position.unwrap();
        let lob_result = simulate_shot(&env, &lob);
        let lob_land = lob_result.landing_position.unwrap();

        let flat_deflection = flat_land.x.abs();
        let lob_deflection = lob_land.x.abs();

        // Verify lob peak > 2m
        let lob_peak = lob_result
            .ball_flight_segments
            .iter()
            .map(|s| s.peak_height)
            .fold(0.0f32, f32::max);
        assert!(lob_peak > 2.0, "lob peak must be >2m, got {}", lob_peak);

        assert!(
            lob_deflection > flat_deflection,
            "lob must be deflected more: lob_x={} flat_x={}",
            lob_deflection,
            flat_deflection
        );
    }

    #[test]
    fn simulate_batch_empty_returns_empty() {
        let env = indoor_env(0.47, 0.10);
        let results = crate::physics::simulate_batch(env, &[], 0);
        assert!(results.is_empty());
    }

    #[test]
    fn simulate_batch_order_preserved() {
        let fast = ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -5.0, z: 1.5 }),
            velocity: Some(Vector3 { x: 0.0, y: 25.0, z: 2.0 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "t".into(),
            point_index: 0,
            shot_index: 0,
        };
        let slow = ShotSpec {
            velocity: Some(Vector3 { x: 0.0, y: 10.0, z: 2.0 }),
            ..fast.clone()
        };
        let shots = vec![fast, slow];
        let results = crate::physics::simulate_batch(indoor_env(0.47, 0.0), &shots, 1);
        assert_eq!(results.len(), 2);
        let y0 = results[0].landing_position.as_ref().unwrap().y;
        let y1 = results[1].landing_position.as_ref().unwrap().y;
        assert!(y0 > y1, "fast shot should land deeper than slow: {} vs {}", y0, y1);
    }

    #[test]
    fn simulate_batch_deterministic() {
        let env = || indoor_env(0.47, 0.10);
        let shots: Vec<ShotSpec> = (0..3)
            .map(|i| ShotSpec {
                contact_position: Some(CourtPosition { x: 0.0, y: -5.0, z: 1.5 }),
                velocity: Some(Vector3 { x: 0.0, y: 15.0 + i as f32, z: 2.0 }),
                spin_rpm: 0.0,
                spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
                match_id: "t".into(),
                point_index: i,
                shot_index: i,
            })
            .collect();

        let r1 = crate::physics::simulate_batch(env(), &shots, 42);
        let r2 = crate::physics::simulate_batch(env(), &shots, 42);

        assert_eq!(r1.len(), r2.len());
        for (a, b) in r1.iter().zip(r2.iter()) {
            assert_eq!(
                a.landing_position.as_ref().map(|p| (p.x, p.y)),
                b.landing_position.as_ref().map(|p| (p.x, p.y)),
            );
        }
    }

    #[test]
    fn simulate_batch_different_seeds_produce_different_sequences() {
        // Trajectory chosen so z_net ≈ 0.902, inside the tape zone [0.880, 0.948].
        // Semi-implicit Euler at DT=0.002: net crossing at step 100 (t=0.2s),
        // z_net ≈ 1.0 + Σ(vz_k·DT) ≈ 0.902, pp ≈ 0.32.
        // With 100 shots (tape_seeds 0..99), P(all outcomes identical) < 10^-17.
        let spec = ShotSpec {
            contact_position: Some(CourtPosition { x: 0.0, y: -3.0, z: 1.0 }),
            velocity: Some(Vector3 { x: 0.0, y: 15.0, z: 0.5 }),
            spin_rpm: 0.0,
            spin_axis: Some(Vector3 { x: 0.0, y: 0.0, z: 1.0 }),
            match_id: "t".into(),
            point_index: 0,
            shot_index: 0,
        };
        let shots: Vec<ShotSpec> = (0..100).map(|_| spec.clone()).collect();
        let results = crate::physics::simulate_batch(indoor_env(0.0, 0.0), &shots, 0);
        let cleared: Vec<bool> = results.iter().map(|r| r.cleared_net).collect();
        let all_same = cleared.windows(2).all(|w| w[0] == w[1]);
        assert!(!all_same, "all per-shot seeds produced identical tape outcomes — seeds are not varying");
    }

    #[test]
    fn simulation_is_fully_deterministic() {
        let env = outdoor_env(Surface::Clay, 0.3);
        // contact y=-5, z=1.8, vz=2.5: clears net even with drag+Magnus
        let spec = ShotSpec {
            contact_position: Some(CourtPosition { x: 0.5, y: -5.0, z: 1.8 }),
            velocity: Some(Vector3 { x: 1.0, y: 15.0, z: 2.5 }),
            spin_rpm: 1500.0,
            spin_axis: Some(Vector3 { x: -1.0, y: 0.0, z: 0.0 }),
            match_id: "m1".into(),
            point_index: 7,
            shot_index: 4,
        };

        let r1 = simulate_shot(&env, &spec);
        let r2 = simulate_shot(&env, &spec);

        let l1 = r1.landing_position.unwrap();
        let l2 = r2.landing_position.unwrap();
        assert_eq!(l1.x, l2.x);
        assert_eq!(l1.y, l2.y);
        assert_eq!(r1.cleared_net, r2.cleared_net);
        assert_eq!(r1.ball_flight_segments.len(), r2.ball_flight_segments.len());
    }

    #[test]
    fn all_coefficients_from_environment() {
        // Different coefficients → different landing positions
        let env_soft = PhysicsEnvironment {
            surface: Surface::IndoorHard as i32,
            surface_wetness: 0.0,
            wind: None,
            coefficients: Some(SurfaceCoefficients {
                drag: 0.2,
                magnus: 0.0,
                restitution: 0.75,
                friction: 0.60,
                wetness_bounce_scale: 1.0,
                wetness_friction_scale: 1.0,
            }),
        };
        let env_drag = PhysicsEnvironment {
            coefficients: Some(SurfaceCoefficients { drag: 0.9, ..env_soft.coefficients.unwrap() }),
            ..env_soft.clone()
        };
        let spec = flat_serve_spec();
        let l_soft = simulate_shot(&env_soft, &spec).landing_position.unwrap();
        let l_drag = simulate_shot(&env_drag, &spec).landing_position.unwrap();

        assert_ne!(
            l_soft.y, l_drag.y,
            "different drag coefficients must produce different trajectories"
        );
    }
}
