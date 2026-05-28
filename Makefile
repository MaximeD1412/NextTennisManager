.PHONY: generate generate-ts generate-rust build

## generate: produces TypeScript stubs, Rust types (physics-core/src/generated/), and Java classes
generate: generate-ts generate-rust
	./gradlew build

## generate-ts: runs ts-proto codegen into NextManagerTennis_WebApp/frontend/src/generated/
generate-ts:
	cd NextManagerTennis_WebApp/frontend && npm run generate:proto

## generate-rust: invokes build.rs via cargo build, writing prost types to physics-core/src/generated/
generate-rust:
	cargo build --manifest-path physics-core/Cargo.toml

build:
	./gradlew build
