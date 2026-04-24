# Create: Radars - 1.21.1 NeoForge Remake

This repository contains EndX WaterBucket's 1.21.1 NeoForge remake of Create: Radars, a Minecraft mod for surveillance, detection, and Create Big Cannons weapon-control workflows.

The remake focuses on bringing the original Create: Radars experience forward to Minecraft 1.21.1 on NeoForge while preserving the existing gameplay behavior as closely as possible.

## Target Versions

This release targets:

- Minecraft 1.21.1
- NeoForge 21.1.227 or newer 21.1.x builds
- Create 6.0.10
- Create Big Cannons 5.10.1 or newer 5.x builds

Sable compatibility is optional and only active when Sable is installed.

## Features

- **Radar Bearing Multiblock**: Build radar assemblies whose shape affects their capabilities.
- **Scalable Monitor Multiblock**: Visualize radar tracks and battlefield information on in-world monitors or the monitor UI.
- **Weapon Network Components**: Link radars to control blocks for automated weapon-network behavior.
- **Create Big Cannons Compatibility**: Supports CBC-related targeting and firing-control integrations where available on 1.21.1 NeoForge.

## Installation

Download a jar from Modrinth or this repository's GitHub Releases, then place it into your Minecraft `mods` folder.

To build locally:

```sh
./gradlew build
```

The generated jar will be in `build/libs/`.

## Attribution

This 1.21.1 NeoForge remake is maintained by EndX WaterBucket.

Create: Radars was originally developed by its original contributors, including Aycer, HappySG, CeoOfGoogle, Kipti, OndatraCZE, and Ray(furuochen). This remake preserves that lineage while updating the project for the newer Minecraft/NeoForge environment.

## Contributing

Issues and pull requests should target this repository:

https://github.com/DXENDBucket/Create-Radar-Sable

Please keep changes focused on porting, compatibility, bug fixes, and faithful restoration of existing behavior unless a gameplay change is discussed explicitly.

## License

The code is licensed under the MIT License. See [LICENSE](LICENSE) for details.
