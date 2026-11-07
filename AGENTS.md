# AGENTS.md

Guidance for agents working in this checkout. Product behavior is owned by
[`docs/`](docs/README.md); this file only covers orientation, build and
verification commands, change routing, and the traps that bite here. Local agent
files are supplementary execution guidance, not behavioral authority: when this
file and an owning document disagree, the document wins and this file is wrong.

## Repository at a glance

Ping For It is a Minecraft mod that lets a player mark a block or entity for other
players. The product target of this branch is Minecraft **26.3** on Java **25**;
`docs/` still describes the mod as it behaves on 1.21.1 and is the contract the
migration has to satisfy.

**This branch is a build skeleton, not the mod.** It exists to prove that the
26.3 toolchain compiles and packages an empty mod with the loaders the project
keeps, before any game-facing code is ported. The 1.21.1 sources are still on
disk but are excluded from every source set.

- Mod id `pingforit`; Java package root `nx.pingwheel`.
- Gradle projects that participate: `common`, `fabric`, `neoforge`.
  `buildSrc` holds the two shared plugins, `multiloader-common` and
  `multiloader-loader`.
- `forge` is on disk but not included in `settings.gradle`. Forge itself ships
  26.3 (66.1.5 latest, 66.1.0 recommended, confirmed against Forge's promotions
  feed and its maven, and `forge-26.3-66.1.5-userdev.jar` exists). The blocker is
  the build toolchain: `forge/build.gradle` still pins the ForgeGradle 6 line
  (`[6.0.24,6.2)`), which belongs to 1.20-1.21; the 26.x line is ForgeGradle 7,
  published as `net.minecraftforge:forgegradle:7.0.40` behind the same plugin id
  `net.minecraftforge.gradle`. No official 26.3 mod template exists yet, so the
  mappings syntax for an unobfuscated version is unverified and re-adding Forge
  stays a toolchain experiment.

## Toolchain facts for 26.3

These are the facts that make 26.x different from every earlier update. Read this
section before touching a build script.

- **Minecraft 26.3 ships unobfuscated.** Its version JSON has only `client` and
  `server` downloads: there is no `client_mappings`, and Fabric stopped Yarn and
  Intermediary at 1.21.11. Mojang publishes no mapping files for 26.x at all.
- **The Fabric build therefore uses Loom's non-remapping plugin**, id
  `net.fabricmc.fabric-loom`, not the historical `fabric-loom` id. With it:
  no `mappings` line, plain `implementation`/`compileOnly` instead of
  `modImplementation`/`modCompileOnly`, and the shippable artifact is the plain
  `jar` task instead of `remapJar`.
- Applying the remapped `fabric-loom` plugin on 26.x fails with *Failed to find
  official mojang mappings for 26.3*, and `loom.officialMojangMappings()` fails
  for the same reason. Do not add a mappings line back.
- Loom is pinned to the release **1.19.4**; its plugin marker lives in the
  `net.fabricmc.fabric-loom` group, which is why `settings.gradle` filters on
  `includeGroupAndSubgroups("net.fabricmc")`.
- **Parchment has no 26.3 mappings**, so every parchment block was removed. It
  returns only if Parchment publishes for the target version.
- **"Using mappings" no longer means renaming.** In non-remap mode Loom reports
  its runtime namespace as `OFFICIAL`, that is, the names already inside the jar.
  The only mapping-shaped channel Loom still keeps is `annotations`: annotations,
  unpick metadata and javadoc in the official namespace, with no class or member
  renames. Its own validation messages are the contract — *Annotations mappings
  must contain only the official namespace*, *Annotations patches must use the
  official namespace*. Parchment-style parameter names fit that channel; a rename
  map does not.
- **Intermediary is gone with the mappings.** `meta.fabricmc.net` reports
  intermediary `0.0.0` for 26.3, no mixin refmap is generated, and the mixin
  annotation processor is off unless a project asks for `useLegacyMixinAp`.
- The Gradle wrapper is **9.9.0** and the launcher JVM must be **JDK 25**: Loom
  1.18+ refuses to run on older JVMs, and Gradle 9 is the first Gradle line that
  runs on Java 25. Launching Gradle from a JDK 21 install is the most common way
  to break a build that otherwise looks correct.
- NeoForge is built with ModDevGradle `2.0.148`; `common` is a plain Java library
  in this branch because it has no game imports to compile against.
- `gradle.properties` is the version source: `minecraft_version=26.3`,
  `java_version=25`, `neo_form_version=26.3-1`, `mod_dev_gradle_version=2.0.148`,
  `fabric_loader_version=0.20.1`, `loom_version=1.19.4`, `neoforge_version=26.3.0.101`.

## Skeleton scope

What the skeleton keeps:

| Kept | Why |
| --- | --- |
| `common/src/main/resources` (assets, lang, sounds, textures, empty mixin config, access-transformer placeholder) | Packaging is what the skeleton verifies. |
| Loader metadata (`fabric.mod.json`, `META-INF/neoforge.mods.toml`, `pack.mcmeta` — the last only in `neoforge`, and in the excluded `forge`) | Identity checks and loader acceptance. |
| Empty loader mixin configs and the access-widener header | The files must keep parsing once mixins return. The access widener is deliberately *not* wired (`loom { accessWidenerPath }` and the `fabric.mod.json` key are both off) because its `v2 named` header has no namespace to match yet. |
| `README.md`, `LICENSE`, `docs/`, `AGENTS.md` | Unrelated to the build. |

What the skeleton drops or disables, and how it comes back:

| Dropped | Restore point |
| --- | --- |
| Mod sources | `multiloader-common.gradle` points `main`/`test` at `src/skeleton/**`; deleting that block restores `src/main/java`. |
| Entrypoints and `@Mod` classes | Loader metadata and `src/skeleton/java` stubs; add with the port. |
| Mixin, access-transformer and access-widener entries | The config files exist but are empty; the bodies return with the port. |
| Fabric API, ModMenu, Create, Flywheel, Ponder, Distant Horizons, Sable | Dependency blocks are commented in the loader scripts and `multiloader-common`. |
| ModDevGradle in `common` | The `neoForge { }` block and the compiled-artifact test wiring are kept as a comment in `common/build.gradle`. |
| Forge project | `include("forge")` in `settings.gradle`, plus a verified 26.3-capable ForgeGradle setup. `forge/build.gradle` also calls `:common`'s `createMinecraftArtifacts`, which does not exist while `common` has ModDevGradle switched off, so that wiring must change first. |
| `loaderVersion` and `displayTest` in `neoforge.mods.toml` | Both are optional or retired in 26.x (FancyModLoader is 12.x); re-add only against the target version's own metadata contract. |
| `META-INF/services` provider files | Ten in each loader's `src/main/resources` (ten counts the inventory port), one in each loader's `src/test/resources`, seven in `common/src/test/resources`, ten in the excluded `forge/`. The main-resource files are comment-only placeholders: FancyModLoader derives a JPMS module descriptor from each mod jar and aborts startup with `InvalidModuleDescriptorException` when a named provider class is absent, so a dangling entry is a launch failure, not an unused file. |

## Where code lives

| Path | Role |
| --- | --- |
| `common/src/skeleton/java` | The only source tree `common` compiles today: one placeholder class. |
| `fabric/src/skeleton/java`, `neoforge/src/skeleton/java` | Loader placeholders. |
| `common/src/main/java/nx/pingwheel/common` | The 1.21.1 shared tree: capture, interaction, markers, network, presentation, configuration, rendering, settings UI. Not compiled in this branch; it is the migration input. |
| `common/src/main/resources` | Shared assets, the shared mixin config, the access-transformer placeholder. |
| `fabric/`, `neoforge/` | Loader build scripts, metadata, run configuration and the empty loader-local mixin configs. |
| `buildSrc/src/main/groovy` | `multiloader-common.gradle` (base project wiring) and `multiloader-loader.gradle` (loader reuse of the common source tree). |
| `docs/` | Normative behavior contracts, decision records, verification inventories. |
| `scripts/docs` | Python tooling that checks Markdown links and duplicate link claims. |

The loaders do not consume `common` as a published artifact. Each loader source
set adds the common source directory through the `commonJava` and `commonResources`
configurations and recompiles it, so a compile error in `common` fails every
loader build, and `common` alone is not a runnable mod: it carries no loader
metadata.

### Platform seam (returns with the port)

`common` reaches loader code through `ServiceLoader`. Nine `IPlatform*Service`
interfaces live in the platform package tree; with the inventory native-access
port that makes ten `META-INF/services` entries per loader. A missing
implementation fails at startup, not at compile time.

## Build and verify

Prerequisites:

- **JDK 25** to launch Gradle itself, not only to target 25.
- **JDK 21 must also be installed and auto-detectable.** ModDevGradle's
  `NeoFormRuntimeTask` hardcodes the NeoForm runtime launcher to Java 21, so
  `createMinecraftArtifacts` fails with *Cannot find a Java installation ...
  languageVersion=21* when no JDK 21 is found. The foojay resolver is disabled in
  `settings.gradle`, so Gradle only sees JDKs it can auto-detect; point
  `org.gradle.java.installations.paths` at both installs when detection fails.
- Network access on a first build, for the Gradle distribution, Minecraft
  artifacts, NeoForge and the loader dependencies.
- A writable `GRADLE_USER_HOME` and a writable checkout.
- PowerShell on Windows, from the repository root.

Narrowest loop first; it isolates the Java toolchain from every game artifact:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:GRADLE_OPTS = '-Dfile.encoding=UTF-8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
.\gradlew.bat --no-daemon --console=plain :common:compileJava
```

Then the loaders and the packaging check:

```powershell
.\gradlew.bat --no-daemon --console=plain --continue :fabric:build :neoforge:build verifyModIdentity
```

And finally the game, which is the only proof that a client mod starts:

```powershell
.\gradlew.bat --no-daemon --console=plain :fabric:runClient
.\gradlew.bat --no-daemon --console=plain :neoforge:runClient
```

| Task | What it establishes |
| --- | --- |
| `:common:compileJava` | The Java 25 toolchain and the Gradle/JDK pairing. No game artifacts. |
| `:fabric:build` | Loom's non-remapping path, the Fabric jar, the loader metadata. |
| `:neoforge:build` | ModDevGradle against 26.3, the NeoForge jar, the loader metadata. |
| `verifyModIdentity` | Depends on both loader builds, then inspects the jars for fork identity. |
| `:<loader>:runClient` | The mod loads and the client starts. |

Verified state of this branch (2026-11-07): `:common:compileJava`,
`:fabric:build`, `:neoforge:build` and `verifyModIdentity` complete without
errors and produce `Ping-For-It-0.6.0-pfi-skeleton-fabric-26.3.jar` and
`Ping-For-It-0.6.0-pfi-skeleton-neoforge-26.3.jar` with the expanded metadata
and the `assets/pingforit/` namespace. `:neoforge:runClient` reaches the main
menu and lists `Ping For It 0.6.0-pfi-skeleton (pingforit)` in the FML mod list.
`:fabric:runClient` has not been launched on this branch.

There is no lint, formatter or static-analysis task and no CI. If the environment
cannot reach the network or write `GRADLE_USER_HOME`, record the build as not run
rather than reporting a result you did not observe.

A skeleton build proves the toolchain; it says nothing about the mod. Until the
port starts, a green build must not be described as the mod working.

## Isolation boundary

The mod targets one Minecraft version per branch, and that is what keeps a
version update bounded. The game-independent core is `config`, `diagnostics`,
`domain` and `registry` under `common/src/main/java/nx/pingwheel/common`: those
packages import neither `net.minecraft.*` nor `com.mojang.*`. Everything else in
that tree is the game-facing surface, and the loader projects are game-facing by
design.

The guard lives in `scripts/game-isolation` and carries the 1.21.1 baseline
(559 files, 248 game-coupled). **The `scripts/game-isolation` directory is absent
from this branch's history**; restore it from the 1.21.1 baseline commit before
relying on the check:

```powershell
git checkout 722c0a4 -- scripts/game-isolation
python -B scripts/game-isolation/check_game_isolation.py
python -B -m unittest discover -s scripts/game-isolation/tests
```

When it fails, decide which case you are in. Accidental coupling belongs in an
adapter or the loader project. A real boundary move needs `--write-baseline` plus
a reviewed diff; regenerating the baseline to silence a violation turns the guard
into decoration. A pure version bump should leave the baseline unchanged.

## Updating Minecraft and the toolchain

`gradle.properties` is the main version source, not the only one. Expect the
hardcoded `minecraft-dependencies` version in the loader scripts when the
behavioral tests return, the `pack_format` and `forge:*` keys in `pack.mcmeta`,
the mixin `compatibilityLevel` entries (they do not follow `java_version`), and the
Loom plugin id itself, which is the one setting a future version may flip back.

1. Freeze a baseline: a clean tree, a recorded verification result, and a branch
   you can compare against. Delete the previous version's outputs and project
   cache first (`build/`, `*/build/`, `.gradle/`): they are git-ignored, so they
   survive branch switches and are the easiest way for old-version classes and
   remapped artifacts to be reused.
2. Move the toolchain first: the wrapper, then Loom or ModDevGradle. A plugin bump
   fails during configuration, which is easy to tell apart from a game API break.
   Verify with `:common:compileJava` before touching the game version.
3. Then the game version as one atomic group: `minecraft_version`,
   `neo_form_version`, `java_version` if it moved, the loader version sets with
   their compatibility ranges, and the hardcoded sites above.
4. When the game starts or stops shipping mappings, the Loom plugin id and the
   presence of a `mappings` line change together. Check the Fabric template for
   the target version before assuming either.
5. Fix compiler errors first, then the structure. The mixin contract tests cover
   what the compiler cannot: private member targets, descriptors and
   access-widener entries.
6. Run the full verification, then each loader's client.

## Documentation rules

[`docs/README.md`](docs/README.md) is the navigation entry point: it routes a
task to the owning document and indexes decisions D0001-D0017 (there is no
D0014). [`docs/AGENTS.md`](docs/AGENTS.md) owns the authoring rules. The ones an
agent is most likely to break:

- Each substantive fact has exactly one primary owner. Secondary pages keep the
  interface semantics and a link, not a copy of the rules or values.
- Product documentation never links to source files or line numbers.
- Configuration file names, keys, syntax and examples are owned by the client and
  server configuration pages; tuning defaults and bounds stay in code.
- Do not write fix narratives ("now", "no longer", "currently") into behavioral
  contracts.
- A subagent may edit `docs/` only when it is explicitly authorized and the task
  contains no code change; otherwise it reports proposed changes.

The documentation set is large. Do not read it all: pick the owning page from the
task table in `docs/README.md` and stop there. While this branch is a skeleton,
`docs/` describes 1.21.1 behavior; treat a mismatch with the code as the port's
work item, not as a documentation bug.

Documentation changes are link-checked with:

```powershell
python -B scripts/docs/broken_link_check.py
```

That command scans `docs/**/*.md` only. It does not validate this file, the
repository README, or external URIs, and it never proves that a stated behavior
is correct.

## Testing expectations

[D0007](docs/decisions/D0007-no-meaningless-tests.md) is the binding rule: a test
must protect a named contract, boundary or regression, and its expected value
must be independent of the code under test. The skeleton ships placeholder test
classes only, because a placeholder that asserts nothing protects nothing; real
tests return with the port. Gradle 9 fails a test task that compiles test classes
but discovers no tests, so the shared build script disables
`failOnNoDiscoveredTests`; restore the strict default with the first real test.

For a behavior change, run the affected tests, build every affected loader, and
run `verifyModIdentity` when packaging, identity or the shippable artifact set is
in scope. Report the commands actually run and their results, and state which
checks were not run and why.

## Traps

1. **The Loom plugin id is not cosmetic.** `fabric-loom` remaps and needs
   mappings; `net.fabricmc.fabric-loom` does not remap and must not declare a
   `mappings` line. Mixing the two produces *Failed to find official mojang
   mappings* or a jar whose classes were never remapped.
2. **The launcher JVM must be 25.** The toolchain pins compilation to 25 and Loom
   1.18+ refuses older JVMs; unlike the 1.21.1 branch, JDK 21 as the Gradle JVM is
   no longer the safe choice.
3. **No mappings exist for 26.x.** `loom.officialMojangMappings()`, Parchment,
   Yarn and `net.fabricmc:intermediary` all fail or do not exist for 26.3. A
   tutorial that adds a `mappings` line is describing 1.21.x.
4. **ModDevGradle run types are named, not free-form.** A run block called
   `data` fails with *Trying to prepare unknown run: data. Available run types:
   [client, clientData, serverData, gameTestServer, server, junit]*. The datagen
   runs are `clientData` and `serverData`.
5. **NeoForge still runs a real toolchain.** ModDevGradle 2.0.148 keeps the NFRT
   pipeline (download, decompile, recompile) and has no "unobfuscated" mode, even
   though 26.3 is unobfuscated. That is why `common` must not apply ModDevGradle
   while it compiles only stubs: it would process all of 26.3 for nothing.
6. **The access widener header namespace is unresolved for 26.x.** Loom's
   non-remap runtime namespace is `OFFICIAL`, the 1.21.1 files say
   `accessWidener v2 named`, and 26.x examples in the wild use
   `*.classtweaker`. Decide the header and the file extension against the target
   version's own template before wiring `accessWidenerPath` again; a mismatch is a
   build-time failure, and an undeclared widener is silently ignored.
7. **Package root and mod id differ.** `nx.pingwheel` is legitimate internal
   identity: Java packages, service files, mixin packages, internal comments.
   Everything a user or the loader sees must be `pingforit`: assets namespace,
   mixin config names, network channel names, language text, metadata.
8. **Mixin configs are triplicated.** `common` owns the shared
   `pingforit.mixins.json`; `forge` and `neoforge` keep loader-local copies with
   different contents. Adding a shared mixin means editing the common config and
   both loader copies. The skeleton keeps all three, empty.
9. **The skeleton is two source trees.** `src/main/java` and `src/skeleton/java`
   both exist and only one is compiled. Editing a 1.21.1 class has no effect on
   the build, and a green build does not mean that tree compiles.
10. **A stale `build/` lies.** Old-version classes, remapped jars and refmaps
   survive branch switches. Delete the build outputs before judging a version
   change.
11. **Long paths.** Some loader fixtures exceed 500 characters. Cloning, checking
   out and packaging need `LongPathsEnabled` and `git config core.longpaths true`.
12. **The README version can drift.** Read `mod_version` from `gradle.properties`;
   do not copy a version out of prose.
13. **Lombok 1.18.32 cannot run on javac 25.** The skeleton compiles with a Java
    25 toolchain, where the Lombok annotation processor aborts with
    `java.lang.ExceptionInInitializerError` from `LombokProcessor`. The skeleton
    therefore runs no annotation processor; restoring Lombok means restoring a
    release that supports 25 (1.18.48 or later as of this branch).
14. **Rendered behavior needs a game session.** No automated check proves
    on-screen rendering, input feel or live multiplayer behavior.

## Definition of done

- The owning contract in `docs/` was read, and updated if the behavior changed.
- Focused tests were added or updated where behavior changed, under D0007.
- Affected tests and affected loader builds pass.
- `verifyModIdentity` passes when packaging or identity is affected.
- The isolation check passes, or its baseline change is deliberate and reviewed.
- The report lists commands run with results, and commands not run with reasons.
