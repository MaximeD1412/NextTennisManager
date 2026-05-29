import * as THREE from "three";
import type { CourtPosition, DebugTrajectory, SceneDimensions } from "./trajectory";

export function toScenePosition(position: CourtPosition): THREE.Vector3 {
  return new THREE.Vector3(position.x, position.z, position.y);
}

export function toSceneVector(vector: CourtPosition): THREE.Vector3 {
  return new THREE.Vector3(vector.x, vector.z, vector.y);
}

function groundPoint(x: number, y: number): THREE.Vector3 {
  return new THREE.Vector3(x, 0.018, y);
}

function addCourtLine(
  group: THREE.Group,
  material: THREE.LineBasicMaterial,
  fromX: number,
  fromY: number,
  toX: number,
  toY: number,
) {
  const geometry = new THREE.BufferGeometry().setFromPoints([
    groundPoint(fromX, fromY),
    groundPoint(toX, toY),
  ]);
  group.add(new THREE.Line(geometry, material));
}

function addCourtLines(group: THREE.Group, dimensions: SceneDimensions) {
  const white = new THREE.LineBasicMaterial({
    color: 0xf7f6ed,
    transparent: true,
    opacity: 0.95,
  });
  const alley = new THREE.LineBasicMaterial({
    color: 0xf7f6ed,
    transparent: true,
    opacity: 0.35,
  });

  const b = dimensions.baselineYAbsM;
  const s = dimensions.singlesHalfWidthM;
  const d = dimensions.doublesHalfWidthM;
  const service = dimensions.serviceLineYAbsM;

  addCourtLine(group, white, -s, -b, s, -b);
  addCourtLine(group, white, -s, b, s, b);
  addCourtLine(group, white, -s, -b, -s, b);
  addCourtLine(group, white, s, -b, s, b);

  addCourtLine(group, alley, -d, -b, d, -b);
  addCourtLine(group, alley, -d, b, d, b);
  addCourtLine(group, alley, -d, -b, -d, b);
  addCourtLine(group, alley, d, -b, d, b);

  addCourtLine(group, white, -s, -service, s, -service);
  addCourtLine(group, white, -s, service, s, service);
  addCourtLine(group, white, 0, -service, 0, service);
  addCourtLine(group, white, -d, 0, d, 0);
}

function addNet(group: THREE.Group, dimensions: SceneDimensions) {
  const netMaterial = new THREE.MeshStandardMaterial({
    color: 0xd8dde0,
    transparent: true,
    opacity: 0.28,
    side: THREE.DoubleSide,
    roughness: 0.8,
  });
  const netGeometry = new THREE.BufferGeometry();
  const x = dimensions.netPostXAbsM;
  netGeometry.setAttribute(
    "position",
    new THREE.Float32BufferAttribute(
      [
        -x,
        0,
        0,
        0,
        0,
        0,
        x,
        0,
        0,
        -x,
        dimensions.netPostHeightM,
        0,
        0,
        dimensions.netCenterHeightM,
        0,
        x,
        dimensions.netPostHeightM,
        0,
      ],
      3,
    ),
  );
  netGeometry.setIndex([0, 1, 4, 0, 4, 3, 1, 2, 5, 1, 5, 4]);
  netGeometry.computeVertexNormals();
  const net = new THREE.Mesh(netGeometry, netMaterial);
  group.add(net);

  const cordMaterial = new THREE.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.9 });
  const topCord = new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(-dimensions.netPostXAbsM, dimensions.netPostHeightM, 0),
    new THREE.Vector3(0, dimensions.netCenterHeightM, 0),
    new THREE.Vector3(dimensions.netPostXAbsM, dimensions.netPostHeightM, 0),
  ]);
  group.add(new THREE.Line(topCord, cordMaterial));

  const postMaterial = new THREE.MeshStandardMaterial({ color: 0xf7f6ed, roughness: 0.55 });
  const postRadius = Math.max(dimensions.ballRadiusM, 0.025);
  [-dimensions.netPostXAbsM, dimensions.netPostXAbsM].forEach((postX) => {
    const post = new THREE.Mesh(
      new THREE.CylinderGeometry(postRadius, postRadius, dimensions.netPostHeightM, 16),
      postMaterial,
    );
    post.position.set(postX, dimensions.netPostHeightM / 2, 0);
    group.add(post);
  });
}

function addTrajectory(group: THREE.Group, trajectory: DebugTrajectory) {
  const points = trajectory.samples.map((sample) => toScenePosition(sample.position));
  const geometry = new THREE.BufferGeometry().setFromPoints(points);
  const material = new THREE.LineBasicMaterial({
    color: 0xffd166,
    transparent: true,
    opacity: 0.72,
  });
  group.add(new THREE.Line(geometry, material));

  trajectory.events.forEach((event) => {
    const markerRadius = trajectory.dimensions.ballRadiusM * (event.kind === "bounce" ? 2.2 : 1.7);
    const marker = new THREE.Mesh(
      new THREE.SphereGeometry(markerRadius, 16, 16),
      new THREE.MeshStandardMaterial({
        color: event.kind === "bounce" ? 0xff5a5f : 0x4cc9f0,
        emissive: event.kind === "bounce" ? 0x551010 : 0x073746,
        roughness: 0.35,
      }),
    );
    marker.position.copy(toScenePosition(event.position));
    marker.position.y += event.kind === "bounce" ? markerRadius : 0;
    group.add(marker);
  });
}

export function buildCourtScene(trajectory: DebugTrajectory) {
  const group = new THREE.Group();
  const { dimensions } = trajectory;

  const apron = new THREE.Mesh(
    new THREE.PlaneGeometry(dimensions.doublesHalfWidthM * 2 + 7, dimensions.baselineYAbsM * 2 + 6),
    new THREE.MeshStandardMaterial({
      color: 0x29333d,
      roughness: 0.9,
      metalness: 0.02,
    }),
  );
  apron.rotation.x = -Math.PI / 2;
  apron.position.y = -0.014;
  group.add(apron);

  const courtSurface = new THREE.Mesh(
    new THREE.PlaneGeometry(dimensions.doublesHalfWidthM * 2, dimensions.baselineYAbsM * 2),
    new THREE.MeshStandardMaterial({
      color: 0x2d9c74,
      roughness: 0.86,
      metalness: 0.01,
    }),
  );
  courtSurface.rotation.x = -Math.PI / 2;
  group.add(courtSurface);

  addCourtLines(group, dimensions);
  addNet(group, dimensions);
  addTrajectory(group, trajectory);

  return group;
}
