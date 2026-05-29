import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "../../..");
const source = resolve(
  repoRoot,
  "target/wasm32-unknown-unknown/release/physics_debug_wasm.wasm",
);
const destination = resolve(scriptDir, "../public/physics_debug_wasm.wasm");

mkdirSync(dirname(destination), { recursive: true });
copyFileSync(source, destination);
console.log(`Copied ${source} -> ${destination}`);
