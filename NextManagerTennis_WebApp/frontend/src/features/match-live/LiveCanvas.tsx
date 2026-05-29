"use client";

import { useEffect, useRef } from "react";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { buildCourtScene, toScenePosition, toSceneVector } from "./court";
import { buildRustDebugTrajectory } from "./rustPhysics";
import { exampleTopspinForehand } from "./shotExamples";
import { buildDebugTrajectory, sampleAt, type DebugTrajectory } from "./trajectory";

const TAU = Math.PI * 2;

async function loadTrajectory(sampleMs: number): Promise<DebugTrajectory> {
  try {
    return await buildRustDebugTrajectory(sampleMs, exampleTopspinForehand);
  } catch (error) {
    console.error("Rust physics WASM unavailable, using TypeScript fallback trajectory.", error);
    return buildDebugTrajectory(sampleMs);
  }
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

    if (red + green + blue > 54) nonDark += 1;
    if (Math.max(red, green, blue) > 180) bright += 1;
    if (green > red * 1.2 && green > blue * 1.1) greenish += 1;
  }

  const report = document.createElement("pre");
  report.id = "canvas-pixel-check";
  report.style.position = "fixed";
  report.style.left = "-9999px";
  report.textContent = JSON.stringify({ width, height, sampled, nonDark, bright, greenish });
  document.body.appendChild(report);
}

export default function LiveCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const playToggleRef = useRef<HTMLButtonElement>(null);
  const resetButtonRef = useRef<HTMLButtonElement>(null);
  const speedSliderRef = useRef<HTMLInputElement>(null);
  const timelineSliderRef = useRef<HTMLInputElement>(null);
  const metricTimeRef = useRef<HTMLElement>(null);
  const metricSpeedRef = useRef<HTMLElement>(null);
  const metricSourceRef = useRef<HTMLElement>(null);
  const metricPositionRef = useRef<HTMLElement>(null);
  const shotNameRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const playToggle = playToggleRef.current;
    const resetButton = resetButtonRef.current;
    const speedSlider = speedSliderRef.current;
    const timelineSlider = timelineSliderRef.current;
    const metricTime = metricTimeRef.current;
    const metricSpeed = metricSpeedRef.current;
    const metricSource = metricSourceRef.current;
    const metricPosition = metricPositionRef.current;
    const shotName = shotNameRef.current;

    if (
      !canvas || !playToggle || !resetButton || !speedSlider || !timelineSlider ||
      !metricTime || !metricSpeed || !metricSource || !metricPosition || !shotName
    ) {
      return;
    }

    let animationId: number | undefined;
    let renderer: THREE.WebGLRenderer | undefined;
    let onResize: (() => void) | undefined;
    let cancelled = false;

    async function startReplay() {
      metricTime.textContent = "Chargement";
      const trajectory = await loadTrajectory(20);

      if (cancelled) return;

      metricSource.textContent = trajectory.source === "rust-wasm" ? "Rust WASM" : "Fallback TS";
      shotName.textContent = trajectory.shotName;

      if (trajectory.samples.length === 0) {
        throw new Error("Trajectory has no sample.");
      }

      const maxTimeMs = trajectory.samples[trajectory.samples.length - 1].timeMs;

      timelineSlider.max = String(Math.round(maxTimeMs));
      timelineSlider.step = String(trajectory.sampleMs);

      renderer = new THREE.WebGLRenderer({
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

      scene.add(buildCourtScene(trajectory));

      const ballRadius = trajectory.dimensions.ballRadiusM;
      const ballGroup = new THREE.Group();
      const ballSpinGroup = new THREE.Group();
      ballGroup.add(ballSpinGroup);

      const ball = new THREE.Mesh(
        new THREE.SphereGeometry(ballRadius, 32, 32),
        new THREE.MeshStandardMaterial({ color: 0xdfff4f, emissive: 0x253600, roughness: 0.42 }),
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
          ballSpinGroup.quaternion.setFromAxisAngle(spinAxis, (sample.spinRpm * TAU * timeMs) / 60_000);
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

      onResize = () => {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer!.setSize(window.innerWidth, window.innerHeight);
      };
      window.addEventListener("resize", onResize);

      function tick() {
        if (cancelled) return;
        const deltaMs = clock.getDelta() * 1000;

        if (isPlaying) {
          playbackMs = Math.min(playbackMs + deltaMs * playbackSpeed, maxTimeMs);
          if (playbackMs >= maxTimeMs) setPlaying(false);
        }

        updateReplay(playbackMs);
        controls.update();
        renderer!.render(scene, camera);

        if (!pixelCheckRecorded && new URLSearchParams(window.location.search).has("pixel-check")) {
          pixelCheckRecorded = true;
          recordCanvasPixelCheck(renderer!);
        }

        animationId = requestAnimationFrame(tick);
      }

      resetReplay();
      tick();
    }

    startReplay().catch((error) => {
      console.error(error);
      metricTime.textContent = "Erreur";
      metricSource.textContent = "Indisponible";
    });

    return () => {
      cancelled = true;
      if (animationId !== undefined) cancelAnimationFrame(animationId);
      if (onResize) window.removeEventListener("resize", onResize);
      renderer?.dispose();
    };
  }, []);

  return (
    <main id="app">
      <canvas
        id="replay-canvas"
        ref={canvasRef}
        aria-label="Replay 3D d'une trajectoire de balle"
      />
      <section className="hud hud-top" aria-label="Etat du replay">
        <div>
          <p className="eyebrow">Prototype Three.js</p>
          <h1>Replay 3D</h1>
          <p ref={shotNameRef} className="shot-name">Chargement du coup</p>
        </div>
        <dl className="metrics">
          <div>
            <dt>Temps</dt>
            <dd ref={metricTimeRef}>0 ms</dd>
          </div>
          <div>
            <dt>Vitesse</dt>
            <dd ref={metricSpeedRef}>1.0x</dd>
          </div>
          <div>
            <dt>Moteur</dt>
            <dd ref={metricSourceRef}>Rust WASM</dd>
          </div>
          <div>
            <dt>Position</dt>
            <dd ref={metricPositionRef}>x 0.00 / y 0.00 / z 0.00</dd>
          </div>
        </dl>
      </section>
      <section className="hud hud-controls" aria-label="Controles du replay">
        <div className="button-row">
          <button ref={playToggleRef} type="button">Pause</button>
          <button ref={resetButtonRef} type="button">Rejouer</button>
        </div>
        <label className="slider-label" htmlFor="speed-slider">
          Vitesse
          <input
            ref={speedSliderRef}
            id="speed-slider"
            type="range"
            min="0.25"
            max="2.5"
            defaultValue="1"
            step="0.25"
          />
        </label>
        <label className="slider-label timeline" htmlFor="timeline-slider">
          Trajectoire
          <input
            ref={timelineSliderRef}
            id="timeline-slider"
            type="range"
            min="0"
            max="1"
            defaultValue="0"
            step="1"
          />
        </label>
      </section>
    </main>
  );
}
