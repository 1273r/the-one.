# THE ONE — Paper 1.21.11

A complete Java rebuild of the original THE ONE Skript. This is a **Paper server plugin**, not a client mod. It preserves the seven kill-based evolutions and replaces the old particle loop with an independently rotating 3D orbital system.

## Requirements

- Minecraft / Paper **1.21.11**
- Java **21**
- No Skript, SkBee, ItemsAdder, ProtocolLib, or other plugin dependency

## Build

The repository's **Actions → Build THE ONE → Run workflow** job compiles and tests the plugin. Download the `THE-ONE-Paper-1.21.11` artifact from a successful run. The artifact contains the shaded, server-ready JAR.

For a local build with Gradle 8.14.3 installed:

```bash
gradle clean test shadowJar
```

The output is `build/libs/the-one-paper-1.0.0.jar`.

## Install safely

1. Stop your test server and make a backup.
2. Put the JAR in `plugins/`.
3. Start Paper 1.21.11 once, then stop it.
4. Edit `plugins/THE_ONE/config.yml` if desired.
5. Start the server and run `/one info`.

The plugin creates its own SQLite file at `plugins/THE_ONE/the-one.db`. Do not delete this file if you want to keep progression.

## Original progression

| Kills | Stage | Rings | Buff |
|---:|---|---:|---|
| 0–1 | AWAKENING | 2 | Speed I |
| 2–4 | WITNESS | 3 | Resistance I |
| 5–9 | ASCENDED | 4 | Strength I |
| 10–14 | EXALTED | 5 | Speed II |
| 15–19 | DIVINE | 6 | Resistance II |
| 20–29 | UNBOUND | 8 | Strength II |
| 30+ | THE ONE | 10 | Strength II |

Only kills made by the current holder advance the progression. Progress is saved by UUID, survives restarts, and is not lost when a player changes their name.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/one set <player>` | `theone.admin` | Assign the holder |
| `/one remove` | `theone.admin` | Remove the holder |
| `/one kills <amount> [player]` | `theone.admin` | Set kill count |
| `/one reset <player>` | `theone.admin` | Reset a player's progression |
| `/one info [player]` | `theone.admin` | Inspect stage and holder |
| `/one reload` | `theone.admin` | Reload configuration |
| `/one effects` | `theone.admin` | Toggle orbital visuals |
| `/makeone <player>` | `theone.admin` | Legacy assignment command |
| `/removeone` | `theone.admin` | Legacy removal command |
| `/forceprog <kills>` | `theone.admin` | Set your own kills for testing |
| `/oneinfo` | `theone.admin` | Show holder information |
| `/progress` | `theone.use` | View your progression |

## Visual engine

- Stage-scaled orbital count: 2, 3, 4, 5, 6, 8, then 10 rings.
- Separate angular velocities and slowly evolving tilt for each ring.
- Stage-specific colors; THE ONE uses a multi-color palette.
- Highlight sparks, four moving inward energy streams, and a central particle accent.
- Action-bar progress and stage-up title / sound sequence.
- Configurable density, radius, spacing, speed, particle size, and a hard per-tick particle ceiling.

The visuals are made from server-side particles and sounds. **A Paper plugin cannot install or force a GPU shader on a player's client.** Players can use Iris or another compatible shader loader and a shader pack on their own clients; the plugin's particles will render over that.

## Performance and testing

The renderer only runs for the active online holder, uses a configurable particle ceiling, and skips rendering when visuals are disabled. Progression thresholds, ring counts, stage buff rules, and SQLite persistence have automated unit tests. Test first on a staging copy of your server, especially if you use other plugins that alter potion effects or player death handling.

## Data and privacy

The SQLite database stores player UUIDs, kill counts, and the current holder UUID. No external service or telemetry is used.
