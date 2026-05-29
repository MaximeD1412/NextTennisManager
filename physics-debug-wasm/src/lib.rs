use physics_core::generated::{
    BallFlightSegment, CourtPosition, NetInteractionKind, PhysicsEnvironment, ShotSpec, Surface,
    SurfaceCoefficients, Vector3,
};
use physics_core::physics::{
    simulate_shot, BALL_RADIUS_M, NET_CENTER_HEIGHT_M, NET_POST_HEIGHT_M, NET_POST_X_M,
    SERVICE_BOX_DEPTH_M, SINGLES_HALF_WIDTH_M,
};

const SAMPLE_WIDTH: usize = 11;
const EVENT_WIDTH: usize = 5;
const SPEC_WIDTH: usize = 21;
const SCENE_DIMENSION_WIDTH: usize = 8;
const EVENT_NET: f32 = 1.0;
const EVENT_BOUNCE: f32 = 2.0;
const BASELINE_Y_ABS_M: f32 = 11.89;
const DOUBLES_HALF_WIDTH_M: f32 = 5.485;

#[no_mangle]
pub extern "C" fn alloc_f32(len: u32) -> *mut f32 {
    let mut buffer = Vec::<f32>::with_capacity(len as usize);
    let ptr = buffer.as_mut_ptr();
    std::mem::forget(buffer);
    ptr
}

#[no_mangle]
pub extern "C" fn free_f32(ptr: *mut f32, len: u32) {
    if ptr.is_null() || len == 0 {
        return;
    }

    unsafe {
        drop(Vec::from_raw_parts(ptr, 0, len as usize));
    }
}

#[no_mangle]
pub extern "C" fn debug_trajectory_sample_width() -> u32 {
    SAMPLE_WIDTH as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_event_width() -> u32 {
    EVENT_WIDTH as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_spec_width() -> u32 {
    SPEC_WIDTH as u32
}

#[no_mangle]
pub extern "C" fn debug_scene_dimension_width() -> u32 {
    SCENE_DIMENSION_WIDTH as u32
}

#[no_mangle]
pub extern "C" fn debug_scene_dimensions_write(ptr: *mut f32) -> u32 {
    if ptr.is_null() {
        return 0;
    }

    let output = unsafe { std::slice::from_raw_parts_mut(ptr, SCENE_DIMENSION_WIDTH) };
    output.copy_from_slice(&[
        BASELINE_Y_ABS_M,
        SINGLES_HALF_WIDTH_M,
        DOUBLES_HALF_WIDTH_M,
        SERVICE_BOX_DEPTH_M,
        NET_POST_X_M,
        NET_CENTER_HEIGHT_M,
        NET_POST_HEIGHT_M,
        BALL_RADIUS_M,
    ]);

    SCENE_DIMENSION_WIDTH as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_sample_count(sample_ms: f32) -> u32 {
    let result = default_simulation();
    build_samples(&result.ball_flight_segments, sanitize_sample_ms(sample_ms)).len() as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_sample_count_for_spec(spec_ptr: *const f32) -> u32 {
    let input = simulation_input_from_spec(spec_ptr);
    let result = simulate_shot(&input.environment, &input.shot);
    build_samples(&result.ball_flight_segments, input.sample_ms).len() as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_event_count() -> u32 {
    build_events(&default_simulation()).len() as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_event_count_for_spec(spec_ptr: *const f32) -> u32 {
    let input = simulation_input_from_spec(spec_ptr);
    build_events(&simulate_shot(&input.environment, &input.shot)).len() as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_write_samples(ptr: *mut f32, sample_ms: f32) -> u32 {
    if ptr.is_null() {
        return 0;
    }

    let result = default_simulation();
    let samples = build_samples(&result.ball_flight_segments, sanitize_sample_ms(sample_ms));
    let len = samples.len() * SAMPLE_WIDTH;
    let output = unsafe { std::slice::from_raw_parts_mut(ptr, len) };

    write_samples(output, &samples);

    samples.len() as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_write_samples_for_spec(
    ptr: *mut f32,
    spec_ptr: *const f32,
) -> u32 {
    if ptr.is_null() {
        return 0;
    }

    let input = simulation_input_from_spec(spec_ptr);
    let result = simulate_shot(&input.environment, &input.shot);
    let samples = build_samples(&result.ball_flight_segments, input.sample_ms);
    let len = samples.len() * SAMPLE_WIDTH;
    let output = unsafe { std::slice::from_raw_parts_mut(ptr, len) };

    write_samples(output, &samples);

    samples.len() as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_write_events(ptr: *mut f32) -> u32 {
    if ptr.is_null() {
        return 0;
    }

    let events = build_events(&default_simulation());
    let len = events.len() * EVENT_WIDTH;
    let output = unsafe { std::slice::from_raw_parts_mut(ptr, len) };

    for (index, event) in events.iter().enumerate() {
        let offset = index * EVENT_WIDTH;
        output[offset] = event.kind;
        output[offset + 1] = event.time_ms;
        output[offset + 2] = event.position[0];
        output[offset + 3] = event.position[1];
        output[offset + 4] = event.position[2];
    }

    events.len() as u32
}

#[no_mangle]
pub extern "C" fn debug_trajectory_write_events_for_spec(
    ptr: *mut f32,
    spec_ptr: *const f32,
) -> u32 {
    if ptr.is_null() {
        return 0;
    }

    let input = simulation_input_from_spec(spec_ptr);
    let events = build_events(&simulate_shot(&input.environment, &input.shot));
    let len = events.len() * EVENT_WIDTH;
    let output = unsafe { std::slice::from_raw_parts_mut(ptr, len) };

    write_events(output, &events);

    events.len() as u32
}

fn default_simulation() -> physics_core::generated::PhysicsSimulationResult {
    simulate_shot(&default_environment(), &default_shot())
}

fn default_environment() -> PhysicsEnvironment {
    PhysicsEnvironment {
        surface: Surface::Hard as i32,
        surface_wetness: 0.0,
        wind: None,
        coefficients: Some(SurfaceCoefficients {
            drag: 0.47,
            magnus: 0.10,
            restitution: 0.75,
            friction: 0.60,
            wetness_bounce_scale: 1.0,
            wetness_friction_scale: 1.0,
        }),
    }
}

fn default_shot() -> ShotSpec {
    ShotSpec {
        contact_position: Some(CourtPosition {
            x: -2.65,
            y: -9.45,
            z: 1.18,
        }),
        velocity: Some(Vector3 {
            x: 4.9,
            y: 20.4,
            z: 2.75,
        }),
        spin_rpm: 1800.0,
        spin_axis: Some(Vector3 {
            x: -1.0,
            y: 0.0,
            z: 0.0,
        }),
        match_id: "prototype-three-js".into(),
        point_index: 1,
        shot_index: 1,
    }
}

struct SimulationInput {
    sample_ms: f32,
    environment: PhysicsEnvironment,
    shot: ShotSpec,
}

fn simulation_input_from_spec(spec_ptr: *const f32) -> SimulationInput {
    if spec_ptr.is_null() {
        return SimulationInput {
            sample_ms: 20.0,
            environment: default_environment(),
            shot: default_shot(),
        };
    }

    let spec = unsafe { std::slice::from_raw_parts(spec_ptr, SPEC_WIDTH) };

    SimulationInput {
        sample_ms: sanitize_sample_ms(spec[0]),
        environment: PhysicsEnvironment {
            surface: surface_code(spec[1]),
            surface_wetness: spec[2].clamp(0.0, 1.0),
            wind: None,
            coefficients: Some(SurfaceCoefficients {
                drag: finite_or(spec[3], 0.47),
                magnus: finite_or(spec[4], 0.10),
                restitution: finite_or(spec[5], 0.75),
                friction: finite_or(spec[6], 0.60),
                wetness_bounce_scale: finite_or(spec[7], 1.0),
                wetness_friction_scale: finite_or(spec[8], 1.0),
            }),
        },
        shot: ShotSpec {
            contact_position: Some(CourtPosition {
                x: finite_or(spec[9], 0.0),
                y: finite_or(spec[10], -9.0),
                z: finite_or(spec[11], 1.0),
            }),
            velocity: Some(Vector3 {
                x: finite_or(spec[12], 0.0),
                y: finite_or(spec[13], 20.0),
                z: finite_or(spec[14], 3.0),
            }),
            spin_rpm: finite_or(spec[15], 0.0),
            spin_axis: Some(Vector3 {
                x: finite_or(spec[16], -1.0),
                y: finite_or(spec[17], 0.0),
                z: finite_or(spec[18], 0.0),
            }),
            match_id: "prototype-three-js".into(),
            point_index: finite_or(spec[19], 1.0).round() as i32,
            shot_index: finite_or(spec[20], 1.0).round() as i32,
        },
    }
}

#[derive(Clone, Copy)]
struct TrajectorySample {
    time_ms: f32,
    position: [f32; 3],
    velocity: [f32; 3],
    spin_rpm: f32,
    spin_axis: [f32; 3],
}

#[derive(Clone, Copy)]
struct ReplayEvent {
    kind: f32,
    time_ms: f32,
    position: [f32; 3],
}

fn write_samples(output: &mut [f32], samples: &[TrajectorySample]) {
    for (index, sample) in samples.iter().enumerate() {
        let offset = index * SAMPLE_WIDTH;
        output[offset] = sample.time_ms;
        output[offset + 1] = sample.position[0];
        output[offset + 2] = sample.position[1];
        output[offset + 3] = sample.position[2];
        output[offset + 4] = sample.velocity[0];
        output[offset + 5] = sample.velocity[1];
        output[offset + 6] = sample.velocity[2];
        output[offset + 7] = sample.spin_rpm;
        output[offset + 8] = sample.spin_axis[0];
        output[offset + 9] = sample.spin_axis[1];
        output[offset + 10] = sample.spin_axis[2];
    }
}

fn write_events(output: &mut [f32], events: &[ReplayEvent]) {
    for (index, event) in events.iter().enumerate() {
        let offset = index * EVENT_WIDTH;
        output[offset] = event.kind;
        output[offset + 1] = event.time_ms;
        output[offset + 2] = event.position[0];
        output[offset + 3] = event.position[1];
        output[offset + 4] = event.position[2];
    }
}

fn sanitize_sample_ms(sample_ms: f32) -> f32 {
    if sample_ms.is_finite() {
        sample_ms.clamp(5.0, 100.0)
    } else {
        20.0
    }
}

fn finite_or(value: f32, fallback: f32) -> f32 {
    if value.is_finite() {
        value
    } else {
        fallback
    }
}

fn surface_code(value: f32) -> i32 {
    match finite_or(value, Surface::Hard as i32 as f32).round() as i32 {
        1 => Surface::Clay as i32,
        2 => Surface::Grass as i32,
        3 => Surface::Hard as i32,
        4 => Surface::IndoorHard as i32,
        _ => Surface::Hard as i32,
    }
}

fn build_samples(segments: &[BallFlightSegment], sample_ms: f32) -> Vec<TrajectorySample> {
    let mut samples = Vec::new();
    let mut segment_start_ms = 0.0;

    for (segment_index, segment) in segments.iter().enumerate() {
        let duration_ms = segment.duration_ms.max(0.0);
        let first_t = if segment_index == 0 { 0.0 } else { sample_ms };
        let mut t = first_t;

        while t < duration_ms {
            samples.push(sample_segment(segment, segment_start_ms, t));
            t += sample_ms;
        }

        samples.push(sample_segment(segment, segment_start_ms, duration_ms));
        segment_start_ms += duration_ms;
    }

    samples
}

fn sample_segment(
    segment: &BallFlightSegment,
    segment_start_ms: f32,
    elapsed_ms: f32,
) -> TrajectorySample {
    let from = court_position(segment.from).unwrap_or([0.0, 0.0, 0.0]);
    let to = court_position(segment.to).unwrap_or(from);
    let start_velocity = vector3(segment.start_velocity).unwrap_or([0.0, 0.0, 0.0]);
    let end_velocity = vector3(segment.end_velocity).unwrap_or(start_velocity);
    let spin_axis = vector3(segment.spin_axis).unwrap_or([0.0, 0.0, 1.0]);
    let duration_s = (segment.duration_ms / 1000.0).max(0.001);
    let t = (elapsed_ms / segment.duration_ms.max(0.001)).clamp(0.0, 1.0);

    let position = hermite_position(from, start_velocity, to, end_velocity, duration_s, t);
    let velocity = hermite_velocity(from, start_velocity, to, end_velocity, duration_s, t);

    TrajectorySample {
        time_ms: segment_start_ms + elapsed_ms,
        position: [position[0], position[1], position[2].max(0.0)],
        velocity,
        spin_rpm: segment.spin_rpm,
        spin_axis,
    }
}

fn build_events(result: &physics_core::generated::PhysicsSimulationResult) -> Vec<ReplayEvent> {
    let mut events = Vec::new();

    if let Some(net) = result.net_interaction {
        if net.kind != NetInteractionKind::AroundNet as i32 {
            events.push(ReplayEvent {
                kind: EVENT_NET,
                time_ms: net_time_ms(&result.ball_flight_segments).unwrap_or(0.0),
                position: court_position(net.position).unwrap_or([0.0, 0.0, 0.0]),
            });
        }
    }

    if let Some(bounce) = result.bounce {
        events.push(ReplayEvent {
            kind: EVENT_BOUNCE,
            time_ms: result
                .ball_flight_segments
                .first()
                .map(|segment| segment.duration_ms)
                .unwrap_or(0.0),
            position: court_position(bounce.position).unwrap_or([0.0, 0.0, 0.0]),
        });
    }

    events
}

fn net_time_ms(segments: &[BallFlightSegment]) -> Option<f32> {
    let mut segment_start_ms = 0.0;

    for segment in segments {
        let from = court_position(segment.from)?;
        let to = court_position(segment.to)?;

        if from[1] == 0.0 {
            return Some(segment_start_ms);
        }

        if from[1] * to[1] <= 0.0 {
            let span = to[1] - from[1];
            let factor = if span.abs() < 0.001 {
                0.0
            } else {
                (-from[1] / span).clamp(0.0, 1.0)
            };

            return Some(segment_start_ms + segment.duration_ms * factor);
        }

        segment_start_ms += segment.duration_ms;
    }

    None
}

fn court_position(position: Option<CourtPosition>) -> Option<[f32; 3]> {
    position.map(|position| [position.x, position.y, position.z])
}

fn vector3(vector: Option<Vector3>) -> Option<[f32; 3]> {
    vector.map(|vector| [vector.x, vector.y, vector.z])
}

fn hermite_position(
    p0: [f32; 3],
    v0: [f32; 3],
    p1: [f32; 3],
    v1: [f32; 3],
    duration_s: f32,
    t: f32,
) -> [f32; 3] {
    let t2 = t * t;
    let t3 = t2 * t;
    let h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
    let h10 = t3 - 2.0 * t2 + t;
    let h01 = -2.0 * t3 + 3.0 * t2;
    let h11 = t3 - t2;

    [
        h00 * p0[0] + h10 * v0[0] * duration_s + h01 * p1[0] + h11 * v1[0] * duration_s,
        h00 * p0[1] + h10 * v0[1] * duration_s + h01 * p1[1] + h11 * v1[1] * duration_s,
        h00 * p0[2] + h10 * v0[2] * duration_s + h01 * p1[2] + h11 * v1[2] * duration_s,
    ]
}

fn hermite_velocity(
    p0: [f32; 3],
    v0: [f32; 3],
    p1: [f32; 3],
    v1: [f32; 3],
    duration_s: f32,
    t: f32,
) -> [f32; 3] {
    let t2 = t * t;
    let dh00 = 6.0 * t2 - 6.0 * t;
    let dh10 = 3.0 * t2 - 4.0 * t + 1.0;
    let dh01 = -6.0 * t2 + 6.0 * t;
    let dh11 = 3.0 * t2 - 2.0 * t;

    [
        (dh00 * p0[0] + dh10 * v0[0] * duration_s + dh01 * p1[0] + dh11 * v1[0] * duration_s)
            / duration_s,
        (dh00 * p0[1] + dh10 * v0[1] * duration_s + dh01 * p1[1] + dh11 * v1[1] * duration_s)
            / duration_s,
        (dh00 * p0[2] + dh10 * v0[2] * duration_s + dh01 * p1[2] + dh11 * v1[2] * duration_s)
            / duration_s,
    ]
}
