# Dungeons Perspective Unofficial

**Dungeons Perspective Unofficial** is an unofficial Forge 1.20.1 port of the original **Dungeons Perspective** mod. It adds a Minecraft Dungeons-inspired pseudo-isometric camera while keeping the original project's core idea: giving Minecraft a more readable top-down combat and exploration view.

The camera can be rotated, zoomed, and adjusted while you play. The port also includes world-aware targeting and block-occlusion handling so the camera remains useful around buildings, terrain, and underground spaces.

## Original Project

This project is an unofficial port and is not affiliated with the original author. Please visit the original project for its official releases, support, and latest versions:

- [Original CurseForge project](https://www.curseforge.com/minecraft/mc-mods/dungeons-perspective)
- [Original GitHub repository](https://github.com/cleannrooster/dungeons-perspective)

## Features

- Minecraft Dungeons-inspired pseudo-isometric third-person perspective
- Adjustable camera distance with smooth camera movement and zoom transitions
- Free horizontal camera rotation and quick axis-aligned camera snapping
- Dynamic FOV response and adjustable zoom scaling
- Camera-relative movement and optional rotation toward the mouse cursor
- Cursor-based block and entity targeting from the camera view
- Optional lock-on targeting and target cycling
- Optional click-to-move mode
- Optional orthogonal camera mode for a flatter top-down view
- Context-based camera movement that can respond to the player's motion
- Block-occlusion handling to improve visibility around terrain and structures
- Vertical look mode for adjusting the camera's vertical targeting
- Mount and flying-camera handling included in the port
- Embeddium renderer compatibility

Some advanced options are experimental and may be less suitable for underground areas or multiplayer servers. The Forge port does not claim feature parity with every version of the original project.

## Default Controls

These controls can be remapped from Minecraft's Controls menu:

| Action | Default key or button |
| --- | --- |
| Toggle the Dungeons Perspective camera | `F4` |
| Move/rotate the camera | Hold middle mouse button and drag |
| Snap camera to an axis-aligned view | `Home` |
| Rotate camera left/right | `Left Arrow` / `Right Arrow` |
| Zoom in/out | Mouse wheel, `Up Arrow` / `Down Arrow` |
| Toggle vertical look mode | `Right Alt` |
| Interact while using click-to-move | `G` |
| Cycle lock-on target | Unbound by default |

The normal Minecraft perspective key can also be used to leave the custom camera mode. Mouse-wheel zoom can be disabled in the configuration file if hotbar scrolling is preferred.

## Configuration

The port stores its settings in `config/dungeons_iso.json`. The available options include:

- Enable the camera automatically when entering a world
- Force the camera perspective to stay enabled
- Enable or disable mouse-wheel zoom
- Enable click-to-move and camera-relative movement
- Turn toward the mouse cursor while moving
- Enable context-based smooth camera movement
- Adjust camera movement, FOV, and zoom factors
- Enable orthogonal mode
- Enable lock-on targeting

The default configuration starts the perspective automatically, uses camera-relative movement, and enables mouse-wheel zoom.

## Requirements

- Minecraft 1.20.1
- Forge 47.2 or newer
- Embeddium 0.3.31 or newer

Fabric API, Sinytra Connector, Sodium, Oculus, and Combat Roll are not required.

## Installation

1. Install Minecraft 1.20.1 with Forge 47.2 or newer.
2. Install Embeddium 0.3.31 or newer.
3. Place the Dungeons Perspective Unofficial JAR in the `mods` folder.

Embeddium is a required runtime dependency for this port. Fabric API, Sinytra Connector, Sodium, Oculus, and Combat Roll are not required. Embeddium compatibility is built into the port and the mod does not bundle Embeddium.

This project is an unofficial port and is not affiliated with or endorsed by the original Dungeons Perspective author.

## Credits

- Original Dungeons Perspective mod: cleannrooster/Forg
- Unofficial Forge 1.20.1 port: QLNPLUS
- Renderer compatibility: Embeddium

## License

This port is released under the [MIT License](LICENSE), following the original project's license.
