# Polyglot

An enterprise-grade, annotation-based **multi-platform command library** for the JVM, written in
Kotlin. Define a command tree once with a single `@Command` annotation (or a type-safe DSL) and run
it on the CLI, Discord (JDA), PaperMC, Fabric and NeoForge — with built-in argument parsing,
tab-completion, permissions, and coroutine-based execution.

## Modules

| Module | Artifact | Target |
|---|---|---|
| `core` | `lol.simeon.polyglot:core` | Platform-agnostic engine (JVM 21) |
| `platform-cli` | `lol.simeon.polyglot:platform-cli` | Command-line / stdin (JVM 21) |
| `platform-jda` | `lol.simeon.polyglot:platform-jda` | Discord via JDA 6 — slash + prefix (JVM 21) |
| `platform-paper-common` | `lol.simeon.polyglot:platform-paper-common` | Shared Paper/Brigadier bridge (JVM 21) |
| `platform-paper-legacy` | `lol.simeon.polyglot:platform-paper-legacy` | PaperMC **1.21.11** (JVM 21) |
| `platform-paper-modern` | `lol.simeon.polyglot:platform-paper-modern` | PaperMC **26.2** (JVM 25) |
| `platform-brigadier` | `lol.simeon.polyglot:platform-brigadier` | Generic command tree → Brigadier translation (JVM 21) |
| `platform-minecraft-common` | `lol.simeon.polyglot:platform-minecraft-common` | Shared vanilla (Mojang-mapped) sender, guards, argument types (JVM 21) |
| `platform-fabric-legacy` | `lol.simeon.polyglot:platform-fabric-legacy` | Fabric, Minecraft **1.21.11** (JVM 21) |
| `platform-fabric-modern` | `lol.simeon.polyglot:platform-fabric-modern` | Fabric, Minecraft **26.2** (JVM 25) |
| `platform-neoforge-legacy` | `lol.simeon.polyglot:platform-neoforge-legacy` | NeoForge **21.11** / Minecraft 1.21.11 (JVM 21) |
| `platform-neoforge-modern` | `lol.simeon.polyglot:platform-neoforge-modern` | NeoForge **26.2** / Minecraft 26.2 (JVM 25) |

## Core concepts

- **One `@Command`**: annotate a class as the root, member functions as leaf subcommands, and nested
  `@Command` classes as subgroups — arbitrarily deep (sub-sub-commands and beyond).
- **First parameter is the sender** (or the whole `CommandContext`); the rest are arguments, resolved
  by name (compile with `-java-parameters`, which the build does automatically).
- **Built-in argument types**: all primitives, `String`, `Char`, and **any enum** — plus
  per-platform types (`Material`/`Player`/`World` on Paper, `Member`/`User`/`Role` on JDA,
  `ServerPlayer`/`Entity`/`ServerLevel`/`Item`/`Block` on Fabric & NeoForge).
- **Consumer-extensible**: register your own `ArgumentParser`, `SuggestionProvider`, and
  `PermissionResolver`.
- **Coroutines**: command handlers may be `suspend` functions.

### Annotations

`@Command`, `@Default` (bare-group handler), `@Argument` (rename), `@Optional`, `@Greedy`
(rest-of-input), `@Named` (`--name value` / `-x value`), `@Flag` (boolean `--name`), `@Permission`,
`@Suggestion` (named provider), `@Range`, `@Choice`.

**Guards** — beyond the platform-agnostic string `@Permission`, a command class or function may carry
platform-specific preconditions that run before the handler (and before descending into a group, so a
guard on a group protects its subcommands). On JDA: `@RequirePermissions(Permission.…)` (type-safe,
AND semantics, checked per-channel) and `@GuildOnly`. Both are also mirrored onto Discord's native
command metadata for root commands (`DefaultMemberPermissions` / guild-only context) so unauthorized
users don't even see the command. On Fabric/NeoForge: `@OpLevel(n)` (vanilla operator level) and
`@PlayerOnly`, both mirrored into the Brigadier tree's `requires` so unauthorized sources don't see
the node (any guard implementing `BrigadierRequirement` gets the same treatment). Register your own
via a `GuardContributor`.

### Named options, flags & validation

```kotlin
@Command(name = "run")
fun run(
    sender: MySender,
    service: String,                                   // positional
    @Named("count", shorthand = 'c') count: Int,       // --count 3  or  -c 3
    @Named("region") region: String?,                  // optional named
    @Flag("dry", shorthand = 'd') dry: Boolean,        // --dry / -d  -> true
) { /* ... */ }
```

Named options and flags are order-independent relative to positionals. Beyond `@Range`/`@Choice`,
register custom `ArgumentValidator`s via the DSL, and localize all user-facing errors with
`ResourceBundleMessageProvider`.

## Example — annotations

```kotlin
enum class Difficulty { PEACEFUL, EASY, NORMAL, HARD }

@Command(name = "party", description = "Party management")
class PartyCommand {

    @Default
    fun overview(sender: MySender) = sender.reply("Use /party create|admin")

    @Command(name = "create", aliases = ["new"])
    fun create(sender: MySender, name: String, @Optional size: Int?) {
        sender.reply("Created '$name' (size ${size ?: 4})")
    }

    @Command(name = "difficulty")
    @Permission("party.difficulty")
    fun difficulty(sender: MySender, level: Difficulty) {         // enum auto-parsed + auto-completed
        sender.reply("Difficulty = $level")
    }

    @Command(name = "admin")                                       // nested group -> sub-sub-commands
    class Admin {
        @Command(name = "kick")
        suspend fun kick(sender: MySender, target: String, @Greedy reason: String) {
            /* ... */
        }
    }
}

manager.register(PartyCommand())
```

## Example — DSL

```kotlin
manager.register(
    command<MySender>("math") {
        subcommand("add") {
            argument<Int>("a")
            argument<Int>("b")
            executes { ctx -> ctx.sender.reply("${ctx.get<Int>("a") + ctx.get<Int>("b")}") }
        }
    },
)
```

## Platform usage

### CLI
```kotlin
val manager = CliCommandManager()
manager.register(PartyCommand())
manager.execute(ConsoleCommandSender(), """party create "My Party" 6""")
val completions = manager.completeLine(ConsoleCommandSender(), "party ")
```

### JDA (slash + prefix)
```kotlin
val manager = JdaCommandManager(prefixProvider = { listOf("!") })   // MESSAGE_CONTENT intent required
manager.register(PartyCommand())
jda.addEventListener(manager.eventListener)
manager.updateGuildCommands(guild)                                  // push slash commands
```
The `JdaSender` handed to a command exposes `user`, `member`, `guild`, `channel` (a JDA
`MessageChannelUnion` — call `asGuildChannel()`, `asPrivateChannel()`, `asVoiceChannel()`, …) and
`guildChannel` (the `GuildChannel`, or `null` in DMs). `reply(message, ephemeral = …)` posts
publicly by default; pass `ephemeral = true` for an invoker-only reply (ignored by prefix commands).
Handlers may be `suspend` and call `sender.defer(ephemeral = …)` to keep a slash interaction alive
past Discord's 3-second window while doing async work (prefix commands show a typing indicator
instead); after deferring, ephemerality is fixed by that `defer` call. Guard commands with
`@RequirePermissions`/`@GuildOnly` (or the `requirePermissions(…)` / `guildOnly()` DSL helpers).

Discord limits slash nesting to group → subcommand (depth 2). Deeper trees are **flattened**: a leaf
at `root → a → b → c` is registered as command `root`, group `a`, subcommand `b-c`, and the manager
reverses the mapping on dispatch (so command names must not contain `-`). Prefix commands keep the
full nesting.

### PaperMC
```kotlin
// in your JavaPlugin (choose the module matching your server version)
val manager = ModernPaperPlatform.createManager(this)   // or LegacyPaperPlatform for 1.21.11
manager.register(PartyCommand())
manager.install()                                       // publishes via the COMMANDS lifecycle event
```

### Fabric & NeoForge

Both loaders share `platform-minecraft-common` (`MinecraftSender`, guards, argument types) and
`platform-brigadier`, which translates the command tree into a **native Brigadier tree**: every
subcommand is a literal, positional arguments get native argument types (`IntegerArgumentType` with
`@Range` bounds, `EntityArgument.player()`, `DimensionArgument`, item/block resources, …) so clients
validate and highlight them, and everything else (enums, `@Choice`, custom `ArgumentParser`s) becomes
a string node parsed and completed by the core engine. `@Named` options and `@Flag`s are collected in
one trailing `options` node (`--count 3 -d`); a trailing `@Greedy` argument absorbs them instead.

```kotlin
// Fabric — from your ModInitializer (Fabric Language Kotlin)
val manager = FabricPlatform.createManager()          // fabric-permissions-api picked up when loaded
manager.register(PartyCommand())
FabricPlatform.install(manager)                       // CommandRegistrationCallback

// NeoForge — during mod construction (KotlinForForge)
val manager = NeoForgePlatform.createManager(namespace = MOD_ID)
manager.register(PartyCommand())
NeoForgePlatform.install(manager)                     // RegisterCommandsEvent + PermissionGatherEvent
```

`MinecraftSender` exposes `source` (the `CommandSourceStack`), `player`, `entity`, `level`, `server`,
`reply(String | Component)` and `replyError(...)`. String `@Permission` nodes default to vanilla
operator level 2 (`OpLevelPermissionResolver(level)`); on Fabric they are checked through
[fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) when that mod is present,
on NeoForge they are registered as boolean `PermissionNode`s (`<namespace>:<node>`) with
`PermissionAPI`. Handlers run on the server thread (`suspend` handlers block it — offload heavy
work yourself). The 1.21.11 Fabric artifact is intermediary-mapped (`modImplementation` it); 26.2
is unobfuscated, so every Minecraft artifact is a plain Mojang-mapped library there. Nest the chain
into your mod with `include(...)` (Fabric) / `jarJar(...)` (NeoForge); the Kotlin runtime comes from
Fabric Language Kotlin / KotlinForForge.

## Examples

Runnable demos live under `examples/` (not published):

```bash
./gradlew :examples:cli-sample:run        # end-to-end CLI demo
```

`examples/jda-sample` and `examples/paper-sample` show bot and plugin wiring;
`examples/fabric-sample` and `examples/neoforge-sample` are mods you can launch with
`./gradlew :examples:fabric-sample:runServer` / `:examples:neoforge-sample:runServer`.

## Building

```bash
./gradlew build          # compile, test, detekt, coverage verification (all modules)
./gradlew koverHtmlReport # aggregated coverage report
./gradlew publishToMavenLocal
```

- **Static analysis**: detekt with the `OneTopLevelClassOrObjectPerFile` rule enabled (production
  sources).
- **Coverage**: Kover, aggregated with an **80% line** verification floor (currently ~85%).
- **Toolchains**: JVM 21 everywhere except the `*-modern` modules (JVM 25, per Minecraft 26.2).
- **Minecraft**: `platform-minecraft-common` is compiled once against vanilla 1.21.11 via
  ModDevGradle's vanilla mode and runs unchanged on 26.2 (verified by compiling it against 26.2);
  `platform-fabric-legacy` recompiles its sources so Loom can remap them to intermediary. Loom and
  ModDevGradle download Minecraft on first build. The Fabric/NeoForge glue modules are excluded from
  the coverage aggregate (they only run inside a live loader); the common and Brigadier modules are
  unit-tested with a real `CommandDispatcher` and bootstrapped vanilla registries.
- **CI**: `.github/workflows/ci.yml` builds/tests on every push and PR; `publish.yml` releases to
  Maven Central on a `v*` tag (set the `MAVEN_CENTRAL_*` secrets and store the signing key as
  base64 in `SIGNING_IN_MEMORY_KEY`).

> **Before publishing:** replace the `github.com/simeon/Polyglot` placeholders in
> `build-logic/src/main/kotlin/polyglot.published-library.gradle.kts` with your real repository URL,
> and verify the `lol.simeon` namespace on the Central Portal.

## Notes on versions

- detekt uses the `2.0.0-alpha` line (the only one compatible with Kotlin 2.4.10); the required rule
  lives in the `libraries` ruleset.
- The javadoc jar is currently empty because Dokka's classic pipeline does not yet run on the JDK 25
  toolchain; re-enable `JavadocJar.Dokka(...)` once supported.
