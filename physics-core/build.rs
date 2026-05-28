use std::path::PathBuf;

fn main() {
    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap();
    let out_dir = PathBuf::from(&manifest_dir).join("src/generated");
    std::fs::create_dir_all(&out_dir).expect("failed to create src/generated");

    let proto_file = format!(
        "{manifest_dir}/../shared/proto/src/main/proto/simulator.proto"
    );
    let include_path = format!(
        "{manifest_dir}/../shared/proto/src/main/proto"
    );

    println!("cargo:rerun-if-changed={proto_file}");

    prost_build::Config::new()
        .out_dir(&out_dir)
        .compile_protos(&[proto_file.as_str()], &[include_path.as_str()])
        .expect("prost codegen failed");
}
