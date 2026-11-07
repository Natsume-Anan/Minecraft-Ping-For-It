# AGENTS.md

Guidance for agents working in this checkout. Product behavior is owned by
[`docs/`](docs/README.md); this file only covers orientation, build and
verification commands, change routing, and the traps that bite here. Local agent
files are supplementary execution guidance, not behavioral authority: when this
file and an owning document disagree, the document wins and this file is wrong.

## Repository at a glance

Ping For It is a Minecraft 1.21.1 mod (Java 21) that lets a player mark a block
or entity for other players. One shared codebase is packaged for Fabric, Forge
and NeoForge.

- Mod id `pingforit`; Java package root `nx.pingwheel` (shared code under
  `nx.pingwheel.common`, loader code under `nx.pingwheel.<loader>`).
- The package root and the mod id intentionally differ: the package tree is
  retained from the original mod this project forks. See [Traps](#traps).
- Four Gradle projects: `common`, `fabric`, `forge`, `neoforge`.
  `buildSrc` holds the two shared plugins, `multiloader-common` and
  `multiloader-loader`.

## Where code lives

| Path | Role |
| --- | --- |
| `common/src/main/java/nx/pingwheel/common` | All shared behavior: capture, interaction, markers, network, presentation, configuration, rendering, settings UI. |
| `common/src/test/java` | The bulk of the test suite. |
| `fabric/`, `forge/`, `neoforge/` | Loader entrypoints, platform implementations, loader metadata, loader-local mixin configs, optional integrations. |
| `buildSrc/src/main/groovy` | `multiloader-common.gradle` (base project wiring) and `multiloader-loader.gradle` (loader reuse of common). |
| `docs/` | Normative behavior contracts, decision records, verification inventories. |
| `scripts/docs` | Python tooling that checks Markdown links and duplicate link claims. |
| `.github/` | Issue templates and images only. There is no CI workflow. |

The loaders do not consume `common` as a published artifact. Each loader source
set adds the common source directory through the `commonJava` and
`commonResources` configurations and recompiles it, so every loader build
compiles the common tree again. A compile error in `common` fails all three
loaders, and `common` alone is not a runnable mod: it carries no loader
metadata, only the shared assets, the shared mixin config and the shared
access-transformer config.

### Key common packages

| Package | Responsibility |
| --- | --- |
| `client` | Client runtime, marker mirror and display state, block/entity outlines, wheel, rate courtesy, display duration. |
| `interaction` | Press capture and its async token, interaction state machine, candidate collection, cancellation, wheel selection. |
| `marker` | Server-authoritative creation pipeline, marker store, winner selection, target validation. |
| `network` | Packet types and safe decoding. Decoding never throws on corrupt input. |
| `presentation` | Snapshot negotiation, presentation policy, shared sources, inventory preview and tracking. |
| `config` | Client and server configuration files, version markers and migrations. |
| `render` | HUD overlay, direction indicators, spatial and inventory overlays. |
| `screen` | Settings screen, navigation models, server-settings presentation. |
| `mixin` | Shared mixins and inventory accessors. |
| `platform` | The loader service interfaces. |
| `integration` | Optional mod detection and external-block registration. |
| `domain` | Target, target type, ping type and marker identity models plus their catalogues. |
| `diagnostics` | Bounded diagnostic reporting. |
| `core`, `chat`, `name`, `math`, `resolve`, `util`, `topology`, `command`, `resource`, `registry` | Server core dispatch (`ServerCore`), chat text, name resolution, raycast math, target matching, rate limiting and caching, double-chest topology, client commands, resource reload and optional registry lookups. |

### Platform seam

`common` reaches loader code through `ServiceLoader`. Nine `IPlatform*Service`
interfaces live in the platform package tree
(`common/src/main/java/nx/pingwheel/common/platform`): client events, server
events, context, inventory, network, and four fluid ports (fluid, fluid registry,
fluid description, and fluid render, which sits in `platform/client`). Each loader
provides a matching `Platform*ServiceImpl` class and one `META-INF/services` entry
per interface; together with the inventory native-access port that makes ten
entries per loader.

Adding a platform capability therefore means: extend the interface, implement it
in all three loaders, and add or update three `META-INF/services` files. A
missing implementation fails at startup, not at compile time.

Inventory reads use two further loader ports in
`common/src/main/java/nx/pingwheel/common/presentation/inventory/minecraft`:
`InventoryItemNativeAccess` (one implementation and one `META-INF/services` file
per loader) and `InventoryResidentAccess` (`ServiceLoader` with a shared
mixin-based default).

## Isolation boundary

The mod targets one Minecraft version per branch. Two facts keep a version update
bounded.

The game-independent core is `config`, `diagnostics`, `domain` and `registry`
under `common/src/main/java/nx/pingwheel/common`. Those packages import neither
`net.minecraft.*` nor `com.mojang.*`, and a version update should not have to
touch them. Every other shared package is the game-facing surface; its current
size is recorded in `scripts/game-isolation/game_isolation_baseline.json`. The
three loader projects are game-facing by design and outside this check: only
`common/src/main/java` is scanned, because the loader projects exist to touch
loader APIs.

```powershell
python -B scripts/game-isolation/check_game_isolation.py
python -B -m unittest discover -s scripts/game-isolation/tests
```

The second command runs the guard's own tests; run it after changing the guard.

The check enforces four rules: core packages name no game API, including inside
strings; core packages reference only the shared packages the baseline records;
shared code names no Fabric, Forge or NeoForge API in code, and a loader class
name reached through a lazily guarded reflective lookup is the intended fail-soft
channel ([compatibility](docs/compatibility.md#optional-content-fails-soft)), so
loader names inside strings are ignored and a non-reflective one is a review
item; and no package exceeds its recorded count of game-coupled files.

When it fails, decide which case you are in. Accidental coupling belongs in an
adapter or the loader project. A real boundary move needs `--write-baseline` plus
a reviewed diff; regenerating the baseline to silence a violation turns the guard
into decoration.

## Build and verify

Prerequisites:

- JDK 21 to run Gradle itself, not only to target 21. The toolchain pins the
  compile target, but the launcher JVM must also stay within what the pinned
  Groovy and plugin stack supports: a JDK 25 launcher fails during configuration
  with `Unsupported class file major version 69`.
- Network access on a first build, for the Gradle distribution, Minecraft
  artifacts, mappings and mod dependencies.
- A writable `GRADLE_USER_HOME` and a writable checkout, since Gradle writes
  `build/` and `.gradle/` inside both.
- PowerShell on Windows, from the repository root.

Full verification of an implementation change:

```powershell
$env:GRADLE_OPTS = '-Dfile.encoding=UTF-8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
.\gradlew.bat --no-daemon --console=plain --continue :common:check :fabric:build :forge:build :neoforge:build verifyModIdentity
```

`--continue` keeps going after a module fails, so one run reports every module
instead of stopping at the first. The encoding lines matter on a non-UTF-8
console; `GRADLE_OPTS` duplicates `gradle.properties` and is harmless.

Narrower loops:

```powershell
.\gradlew.bat --no-daemon :common:test
.\gradlew.bat --no-daemon :common:test --tests 'nx.pingwheel.common.screen.*'
.\gradlew.bat --no-daemon :fabric:build
.\gradlew.bat --no-daemon verifyModIdentity
```

| Task | What it establishes |
| --- | --- |
| `:common:check` | Compiles the common tree and runs the common JUnit Platform suites. |
| `:<loader>:build` | Compiles that loader and the shared common sources, runs the loader test task, and produces its shippable jar. |
| `:<loader>:test` | Loader-local headless wrapper and resolver suites only. |
| `verifyModIdentity` | Depends on all three loader builds, then inspects the expected jars for fork identity. |

A plain `build` does not run identity verification, and each loader `build`
already includes that loader's `test` task, so do not list both.

`verifyModIdentity` is not a smoke test. Its authoritative check list and limits
are owned by [build checks](docs/testing/verification/build-checks.md#build-source-set-and-artifact-verification).
It locates the one jar named
`Ping-For-It-<mod_version>-<loader>-1.21.1.jar` in each `<loader>/build/libs`,
then rejects stale original-mod namespaces such as `assets/pingwheel/` or
`pingwheel.mixins.json`, wrong refmap routing, metadata that disagrees with
`gradle.properties`, old network-channel byte strings anywhere in the jar, and
similar identity mismatches. Treat a failure there as a packaging bug, not a
flaky check.

There is no lint, formatter or static-analysis task. The Gradle check surface is
the test tasks, the loader builds and `verifyModIdentity`; the Python checks
(documentation links and game isolation) are separate, and no CI exists to fall
back on. If the environment cannot reach the network or write
`GRADLE_USER_HOME`, record the build as not run rather than reporting a result
you did not observe.

### Run the game

```powershell
.\gradlew.bat --no-daemon :fabric:runClient
.\gradlew.bat --no-daemon :forge:runClient
.\gradlew.bat --no-daemon :neoforge:runClient
```

The client and server runs (plus data generation on Forge and NeoForge) come
from each loader's build script, and their working directories are git-ignored.
A renderer, input-handling or multiplayer change still needs an in-game check:
build and test success is not evidence that anything looked or felt right.

## Updating Minecraft and the toolchain

`gradle.properties` is the main version source, not the only one. Besides the
properties, expect the test-only `minecraft-dependencies` version hardcoded in
`common/build.gradle` and `neoforge/build.gradle`, the Create and Flywheel
artifact suffixes in
`neoforge/build.gradle`, the ForgeGradle and MixinGradle plugin versions in
`forge/build.gradle`, and `pack_format` plus the `forge:*` format keys in the two
`pack.mcmeta` files. The mixin `compatibilityLevel` entries do not follow
`java_version`; update them separately.

1. Freeze a baseline: a clean tree, a recorded verification result, and a branch
   you can compare against. Delete the previous version's build outputs and
   project cache first (`build/`, `*/build/`, `.gradle/`): they are git-ignored,
   so they survive branch switches and are the easiest way for old-version
   classes, remapped artifacts and mixin refmaps to be reused.
2. Move the toolchain first. A wrapper or plugin bump fails during configuration,
   which is easy to tell apart from a Minecraft API break. Verify each step with
   `:common:compileJava` before touching the game version.
3. Then the game version, as one atomic group: `minecraft_version`,
   `neo_form_version`, `parchment_*`, the three loader version sets with their
   compatibility ranges, `java_version` if it moved, and the hardcoded sites
   above. `common` compiles against NeoForm while the loaders use their own
   mappings; a mismatch there surfaces as `NoSuchMethod` instead of a clear error.
4. Fix compiler errors first, then the structure. The mixin contract tests cover
   what the compiler cannot: private member targets, descriptors and
   access-widener entries.
5. Re-check behavior that only lives in comments. Some code mirrors vanilla on
   purpose: the outline render type reproduces a vanilla pipeline order, the wheel
   layout assumes a font line height, the inventory budget counts container
   members, and several classes probe for methods reflectively. Those fail as a
   wrong picture or a silent fallback, not as a build error. Search for the old
   version string under `common/src/main/java` before calling the update done.
6. Run the full verification, then each loader's client.

Expect real work in `mixin`, `network`, `render`, the fluid ports under
`platform` and `presentation/inventory/minecraft`; that is the hard coupling.
`loom_version` is a SNAPSHOT, so pin it to a release when the toolchain moves.

A pure version bump should leave the isolation baseline unchanged. If the counts
move during an update, check which happened: the update added game-facing
adapters for changed vanilla APIs (review the diff), core code gained game
coupling (reject it), or the baseline was regenerated to make a failure go away.

Version and loader scope are product boundaries owned by
[compatibility](docs/compatibility.md); this section is procedure only.

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
task table in `docs/README.md`, follow its links, and stop there. For behavior
questions, the owning document beats both this file and a grepped guess at the
code; where the two disagree, report the discrepancy instead of choosing one.

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
must be independent of the code under test. A snapshot of tuning values, an
oracle recomputed with the same helper, or an assertion on private structure by
itself does not justify a test even when it passes.

For a behavior change, run the affected tests, build every affected loader, and
run `verifyModIdentity` when packaging, identity or the shippable artifact set is
in scope. Report the commands actually run and their results, and state which
checks were not run and why. The [verification index](docs/testing/verification/README.md)
separates recorded coverage, gaps and pending manual scenarios; listing a
scenario there is not evidence that it was performed.

## Traps

1. **Build tool versions are coupled.** Loom, ForgeGradle, ModDevGradle and the
   Gradle wrapper version travel together, and a newer Gradle is not verified
   against the pinned plugin set. Before blaming a build failure on the code,
   check `git status` for a local wrapper or plugin-version bump that no build on
   this machine has ever validated. Changing any of them needs a full three-loader
   build plus `verifyModIdentity`.
2. **The launcher JVM matters.** The toolchain pins compilation to 21, but
   Gradle's own JVM must be supported by the plugin stack too; a JDK 25 launcher
   dies during configuration, and the foojay resolver needs network to supply 21.
3. **Package root and mod id differ.** `nx.pingwheel` is legitimate internal
   identity: Java packages, service files, mixin packages, internal comments.
   Everything a user or the loader sees must be `pingforit`: assets namespace,
   mixin config names, network channel names, language text, metadata. Do not
   "clean up" the package names, and do not let stale `pingwheel` text leak into
   the second category.
4. **Mixin configs are triplicated.** `common` owns the shared
   `pingforit.mixins.json` with the Fabric refmap; `forge` and `neoforge` keep
   loader-local copies without a refmap and with different contents (Forge
   carries `InventoryFoodEffectAccessor`, NeoForge does not and adds Create
   visualization mixins). Adding a shared mixin means editing the common config
   and both loader copies. Existing contract tests assert today's entries across
   all three effective configs, but they enumerate known names: a newly added
   shared mixin that you forget to copy into the loader configs is not caught
   automatically and fails silently in game.
5. **Long paths.** Some loader test fixtures exceed 500 characters. Cloning,
   checking out and packaging need `LongPathsEnabled` and
   `git config core.longpaths true`.
6. **The README version can drift.** Read `mod_version` from
   `gradle.properties`; do not copy a version out of prose.
7. **`common` cannot verify itself end to end.** It has no loader metadata, so a
   change there is verified by `:common:check` plus at least one loader build.
8. **Rendered behavior needs a game session.** No automated check proves
   on-screen rendering, input feel or live multiplayer behavior; state-level
   tests exist but do not substitute for a game session. The
   [manual matrix](docs/testing/verification/manual-integration.md) owns what is
   still pending, and compiling is not proof.

## Definition of done

- The owning contract in `docs/` was read, and updated if the behavior changed.
- Focused tests were added or updated where behavior changed, under D0007.
- Affected tests and affected loader builds pass.
- `verifyModIdentity` passes when packaging or identity is affected.
- The isolation check passes, or its baseline change is deliberate and reviewed.
- The report lists commands run with results, and commands not run with reasons.
