# Changelog

## 2.0.0

- 从原 mod 1.21.1 版本迁移

## 1.3.1

- Added a Java/KubeJS API for reading and changing the Dungeons Perspective state.
- Added an option to disable manual F4/F5 perspective switching while keeping API control available.
- Added complete perspective state restoration when switching through the API.

## 1.3.0

- Fixed terrain sections disappearing from certain perspective camera angles.
- Fixed underground blocks appearing directly in front of the camera.
- Improved Embeddium compatibility while rotating or moving the perspective camera.

## 1.2.0

- Fixed nearby entities disappearing when the perspective camera is moved away from the player.
- Fixed entity visibility changing with camera angle or chunk boundaries while underground.

## 1.1.0

- Fixed large areas of underground terrain disappearing at certain camera angles with Embeddium.
- Improved Oculus shader performance by removing unnecessary internal block faces during perspective culling.
- Fixed a client crash when leaving a world while perspective audio adjustments were active.

## 1.0.1

- Fixed camera zoom snapping back after scrolling.
- Stabilized the camera FOV while zooming in Forge 1.20.1.

## 1.0.0

- Initial unofficial Forge 1.20.1 port of Dungeons Perspective.
- Added Embeddium renderer compatibility.
- Removed the need for Fabric API and Sinytra Connector.
- Improved camera rotation and zoom smoothness.
