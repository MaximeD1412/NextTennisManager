import "./styles.css";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { buildCourtScene, toScenePosition, toSceneVector } from "./court";
import { buildRustDebugTrajectory } from "./rustPhysics";
import { exampleTopspinForehand } from "./shotExamples";
import { buildDebugTrajectory, sampleAt, type DebugTrajectory } from "./trajectory";

function requiredElement<T extends Element>(selector: string): T {
  const element = document.querySelector<T>(selector);
  if (!element) {
    throw new Error(`Replay UI is missing ${selector}.`);
  }
  return element;
}

const canvas = requiredElement<HTMLCanvasElement>("#replay-canvas");
const playToggle = requiredElement<HTMLButtonElement>("#play-toggle");
const resetButton = requiredElement<HTMLButtonElement>("#reset-button");
const speedSlider = requiredElement<HTMLInputElement>("#speed-slider");
const timelineSlider = requiredElement<HTMLInputElement>("#timeline-slider");
const metricTime = requiredElement<HTMLElement>("#metric-time");
const metricSpeed = requiredElement<HTMLElement>("#metric-speed");
const metricSource = requiredElement<HTMLElement>("#metric-source");
const metricPosition = requiredElement<HTMLElement>("#metric-position");
const shotName = requiredElement<HTMLElement>("#shot-name");
const TAU = Math.PI * 2;

async function loadTrajectory(sampleMs: number): Promise<DebugTrajectory> {
  try {
    return await buildRustDebugTrajectory(sampleMs, exampleTopspinForehand);
  } catch (error) {
    console.error("Rust physics WASM unavailable, using TypeScript fallback trajectory.", error);
    return buildDebugTrajectory(sampleMs);
  }
}

function updateSourceMetric(trajectory: DebugTrajectory) {
  metricSource.textContent = trajectory.source === "rust-wasm" ? "Rust WASM" : "Fallback TS";
  shotName.textContent = trajectory.shotName;
}

function recordCanvasPixelCheck(renderer: THREE.WebGLRenderer) {
  const context = renderer.getContext();
  const width = context.drawingBufferWidth;
  const height = context.drawingBufferHeight;
  const pixels = new Uint8Array(width * height * 4);
  context.readPixels(0, 0, width, height, context.RGBA, context.UNSIGNED_BYTE, pixels);

  let sampled = 0;
  let nonDark = 0;
  let bright = 0;
  let greenish = 0;

  for (let index = 0; index < pixels.length; index += 4 * 24) {
    const red = pixels[index];
    const green = pixels[index + 1];
    const blue = pixels[index + 2];
    sampled += 1;

    if (red + green + blue > 54) {
      nonDark += 1;
    }

    if (Math.max(red, green, blue) > 180) {
      bright += 1;
    }

    if (green > red * 1.2 && green > blue * 1.1) {
      greenish += 1;
    }
  }

  const report = document.createElement("pre");
  report.id = "canvas-pixel-check";
  report.style.position = "fixed";
  report.style.left = "-9999px";
  report.textContent = JSON.stringify({
    width,
    height,
    sampled,
    nonDark,
    bright,
    greenish,
  });
  document.body.appendChild(report);
}

async function startReplay() {
  metricTime.textContent = "Chargement";
  const trajectory = await loadTrajectory(20);
  updateSourceMetric(trajectory);

  if (trajectory.samples.length === 0) {
    throw new Error("Trajectory has no sample.");
  }

  const maxTimeMs = trajectory.samples[trajectory.samples.length - 1].timeMs;

  timelineSlider.max = String(Math.round(maxTimeMs));
  timelineSlider.step = String(trajectory.sampleMs);

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: false,
    preserveDrawingBuffer: true,
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x111820);
  scene.fog = new THREE.Fog(0x111820, 22, 44);

  const camera = new THREE.PerspectiveCamera(42, window.innerWidth / window.innerHeight, 0.1, 120);
  camera.position.set(8.4, 9.2, 17.2);
  camera.lookAt(0, 0, 0);

  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.target.set(0, 0.6, 0);
  controls.maxPolarAngle = Math.PI * 0.48;
  controls.minDistance = 9;
  controls.maxDistance = 36;

  scene.add(new THREE.HemisphereLight(0xddefff, 0x101820, 1.9));

  const keyLight = new THREE.DirectionalLight(0xffffff, 2.1);
  keyLight.position.set(-4, 12, 8);
  scene.add(keyLight);

  const court = buildCourtScene(trajectory);
  scene.add(court);

  const ballRadius = trajectory.dimensions.ballRadiusM;
  const ballGroup = new THREE.Group();
  const ballSpinGroup = new THREE.Group();
  ballGroup.add(ballSpinGroup);

  const ball = new THREE.Mesh(
    new THREE.SphereGeometry(ballRadius, 32, 32),
    new THREE.MeshStandardMaterial({
      color: 0xdfff4f,
      emissive: 0x253600,
      roughness: 0.42,
    }),
  );
  ballSpinGroup.add(ball);

  const seam = new THREE.Mesh(
    new THREE.TorusGeometry(ballRadius * 1.01, Math.max(ballRadius * 0.045, 0.0015), 8, 48),
    new THREE.MeshBasicMaterial({ color: 0x172014, depthTest: false }),
  );
  seam.renderOrder = 3;
  ballSpinGroup.add(seam);

  const spinDot = new THREE.Mesh(
    new THREE.SphereGeometry(Math.max(ballRadius * 0.28, 0.008), 16, 16),
    new THREE.MeshBasicMaterial({ color: 0xff3b30, depthTest: false }),
  );
  spinDot.position.set(0, ballRadius * 1.08, 0);
  spinDot.renderOrder = 4;
  ballSpinGroup.add(spinDot);
  scene.add(ballGroup);

  let isPlaying = true;
  let playbackMs = 0;
  let playbackSpeed = Number(speedSlider.value);
  let pixelCheckRecorded = false;
  const clock = new THREE.Clock();
  const spinAxis = new THREE.Vector3();

  function setPlaying(value: boolean) {
    isPlaying = value;
    playToggle.textContent = isPlaying ? "Pause" : "Lecture";
  }

  function updateReplay(timeMs: number) {
    const sample = sampleAt(trajectory.samples, timeMs);
    ballGroup.position.copy(toScenePosition(sample.position));

    spinAxis.copy(toSceneVector(sample.spinAxis));
    if (spinAxis.lengthSq() > 0.000001 && sample.spinRpm !== 0) {
      spinAxis.normalize();
      ballSpinGroup.quaternion.setFromAxisAngle(
        spinAxis,
        (sample.spinRpm * TAU * timeMs) / 60_000,
      );
    } else {
      ballSpinGroup.quaternion.identity();
    }

    metricTime.textContent = `${Math.round(timeMs)} ms`;
    metricSpeed.textContent = `${playbackSpeed.toFixed(2)}x`;
    metricPosition.textContent = `x ${sample.position.x.toFixed(2)} / y ${sample.position.y.toFixed(2)} / z ${sample.position.z.toFixed(2)}`;
    timelineSlider.value = String(Math.round(timeMs));
  }

  function resetReplay() {
    playbackMs = 0;
    setPlaying(true);
    updateReplay(0);
  }

  playToggle.addEventListener("click", () => setPlaying(!isPlaying));
  resetButton.addEventListener("click", resetReplay);

  speedSlider.addEventListener("input", () => {
    playbackSpeed = Number(speedSlider.value);
    metricSpeed.textContent = `${playbackSpeed.toFixed(2)}x`;
  });

  timelineSlider.addEventListener("input", () => {
    playbackMs = Number(timelineSlider.value);
    setPlaying(false);
    updateReplay(playbackMs);
  });

  window.addEventListener("resize", () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  });

  function tick() {
    const deltaMs = clock.getDelta() * 1000;

    if (isPlaying) {
      playbackMs = Math.min(playbackMs + deltaMs * playbackSpeed, maxTimeMs);
      if (playbackMs >= maxTimeMs) {
        setPlaying(false);
      }
    }

    updateReplay(playbackMs);
    controls.update();
    renderer.render(scene, camera);

    if (!pixelCheckRecorded && new URLSearchParams(window.location.search).has("pixel-check")) {
      pixelCheckRecorded = true;
      recordCanvasPixelCheck(renderer);
    }

    requestAnimationFrame(tick);
  }

  resetReplay();
  tick();
}

startReplay().catch((error) => {
  console.error(error);
  metricTime.textContent = "Erreur";
  metricSource.textContent = "Indisponible";
});
