# Tier Tagger (AxotiersTiertagger)

A Minecraft Fabric (client-side) mod that displays every player's PvP tier from the
tier lists in-game, in your nametag, tab list, text displays and more — like this:

```
Ht1 | Ooh_Netiyiy
```

Very smol but very useful mod; you'll likey likey :)

## Credits

Maintained by **DoctoFrog** and **DoctoCapybara**. Based on TierTagger by uku and
netiyiy (https://github.com/mctiers-dev/TierTagger), licensed under MPL-2.0.
See [`ATTRIBUTION.md`](ATTRIBUTION.md).

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

### You may only redistribute this mod with the credit intact

This mod is a derivative work of TierTagger by **uku** and **netiyiy (original
creator)** and is licensed under MPL-2.0. Anyone who builds, hosts, mirrors or
redistributes the jar — free or paid, on Modrinth, CurseForge, a launcher, a server
or anywhere else — **must** keep the original credit:

- ship [`ATTRIBUTION.md`](ATTRIBUTION.md) and `LICENSE` with your distribution, and
- keep `netiyiy (original creator)` and `uku` listed as authors in
  `src/main/resources/fabric.mod.json`

You are free to rename the mod, change the mod id and change the code, as long as
that credit stays visible and your modified files remain published under MPL-2.0.
Removing or hiding the original authors' credit is not allowed.

Full rules: [`ATTRIBUTION.md`](ATTRIBUTION.md).
