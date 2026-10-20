# Building

```
./gradlew generateAssets   # datagen for every version
./gradlew build            # versions/<v>/build/libs/artifacts-<version>+<v>.jar, one per version
```

The house build is documented once for every first-party mod in the workspace's `docs/STYLE.md`.
This file covers only where this mod departs from it.

- **Multi-version build** (Stonecutter): 26.2 and 26.3, with 26.2 committed. Each version's keys
  are in `versions/<v>/gradle.properties`.
  - 26.2 builds against kalic0re 0.31.0+26.2, Polymer 0.17.3 and Fabric API 0.159.0.
  - 26.3 builds against kalic0re 0.31.0+26.3, Polymer 0.18.4 and Fabric API 0.161.0.
  - Each dev server run takes the first two free ports in `dev_server_ports` and writes them to
    its version's `run/server.properties`.
- **26.3 ports**, each a `//?` block:
  - loot pools roll an int provider (`ArtifactsLoot`);
  - the explode packet takes a play-sound flag (`AltitudeNudge`);
  - swings take an animation (`WingBoost`);
  - recipe datagen builds into bootstrap contexts (`ArtifactsRecipeProvider`).
- **Datagen runs per version**, into the gitignored `versions/<v>/src/main/generated`, because 26.3
  writes recipes and their advancements in a different format. Run `generateAssets`, then `build`,
  never both in one invocation. `build.mod.gradle` keeps a leftover root `src/main/generated` out of
  every version.
- **Shadow's client side is 26.2-only.** 26.3 replaced the blaze3d pipeline API that kalic0re's
  hidden-model renderers build on, and they have no 26.3 port. On 26.3 the enchantment still exists
  and enchants, but a client running this mod draws the item as usual.
  - `ClientMain`'s renderer setup, the two Shadow client mixins, their accessor and
    `ArtifactsRenderTypeFactory` are wrapped in `//? if <26.3`.
  - Mixin JSON can't hold a version condition, so each version has its own
    `versions/<v>/src/main/resources/artifacts.mixins.json`. Add a new mixin to both.
  - `checkMixinConfig`, which `classes` runs, fails a version whose config and compiled mixin
    classes disagree.
- **kalic0re** `item`, `effect`, `enchant`, `death` and `click` are relocated into the jar from
  `club.rainbowkitty.kalic0re:kalic0re:<kalic0re_version>`, taken from `~/.m2` first, then from
  kalic0re's GitHub Packages. A stale or missing copy shows up as a compile error naming a kalic0re
  class.
- **Releases** — a pushed `v<mod_version>` tag publishes every version's jar to this repo's GitHub
  Packages (`publish-maven.yml`) and makes a GitHub release with the jars (`release.yml`); nothing
  goes to Modrinth or CurseForge. A clean CI runner needs each pinned kalic0re version published to
  kalic0re's public GitHub Packages.
- **The jar to install** is `artifacts-<version>+<v>.jar` for the server's version. `-unshaded` and
  `-sources` beside it are not.
