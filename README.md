# Tier Tagger (AxotiersTiertagger)

A Minecraft Fabric (client-side) mod that displays every player's PvP tier from the
tier lists in-game, in your nametag, tab list, text displays and more — like this:

```
Ht1 | Ooh_Netiyiy
```

Very smol but very useful mod; you'll likey likey :)

## Credits & License — please read

This project is a **fork / derivative work** of the original TierTagger project:

- Original project: https://github.com/mctiers-dev/TierTagger
- Original creators: **uku**, **netiyiy**
- License: **MPL-2.0**

All original copyright notices and authorship are preserved in
`src/main/resources/fabric.mod.json` and in the `LICENSE` file, as required by
the MPL-2.0. This repository is maintained by OctoFiles / OctoNetwork1 and contains
modifications made on top of the original code.

## API configuration

The mod talks to a tier-list API. The default value ships as a placeholder so that
no private or internal endpoint is part of this repository:

```json
{ "apiUrl": "https://your-api-url.example.com" }
```

Set your own API base URL in the mod config screen (**Custom** entry in the Tierlist
tab) or in the config file. The expected endpoints are:

- `GET  /v2/mode/list`
- `GET  /v2/profile/{uuid}`
- `GET  /v2/profile/{uuid}/rankings`
- `GET  /v2/profile/by-name/{query}`
- `GET  /v2/profile/{uuid}/display`
- `POST /v2/profile/{uuid}/display`

## Downloading older Minecraft versions

Every supported Minecraft version is published as a release on this page. Each
release contains a source archive (`TierTagger-<mc>.zip`) with the complete
Gradle project for that version. Download the one matching your Minecraft version.

Supported: 1.20, 1.20.1 – 1.20.6, 1.21, 1.21.1 – 1.21.11, 26.1, 26.2, 26.3

The version matrix lives in [`tools/versions.json`](tools/versions.json).

## Building from source

This repository's default branch holds the main version (Minecraft 26.3).

```bash
./gradlew build
```

The jar is written to `build/libs/`. Set your own JDK if needed (Java 21 for
Minecraft 1.20.5 – 26.2, Java 25 for Minecraft 26.3):

```properties
# gradle.properties (or use JAVA_HOME / the Gradle toolchain)
org.gradle.java.home=/path/to/your/jdk
```

Dependencies (see `gradle.properties`): Fabric Loader, Fabric API and
[ukulib](https://modrinth.com/mod/ukulib).

## Multi-version generation

`tools/` contains the scripts used to generate and maintain the per-version
projects:

- `tools/generate.py` — generates a version folder from the main source
- `tools/preprocess.py` — conditional source preprocessing for version differences
- `tools/versions.json` — the version matrix (Fabric API / ukulib versions, Java target)

```bash
python tools/generate.py
```

## License

MPL-2.0 — see [`LICENSE`](LICENSE).
