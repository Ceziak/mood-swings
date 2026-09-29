# Mood Swings

Mood Swings is an unofficial Fabric addon for **Wathe: Murder Mystery** that adds data-driven mood tasks, reusable task areas, dedicated Lockdown Areas, independent OST regions, environmental kill credit, and a configurable Lockdown ability while keeping Wathe's original HUD language and behavior.

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.18.4+
- Fabric API 0.116.14+1.21.1+
- Wathe 1.3.2-1.21.1
- Cloth Config API 15.0.140+
- Java 21
- Mod Menu is optional

Install it on both the server and every client.

## Included tasks

The eight bundled tasks are ordinary JSON definitions:

- `mood_swings:take_a_shower`
- `mood_swings:socialize`
- `mood_swings:gym`
- `mood_swings:strange_noise`
- `mood_swings:walk`
- `mood_swings:rooftop`
- `mood_swings:toilet`
- `mood_swings:murder` (killer-only, rare, blocks passive income, escalates shake/heartbeat/vignette, then disables instinct and sprinting while randomly changing the selected hotbar slot after two minutes; sensory effects fade after completion)

They live under `src/main/resources/data/mood_swings/mood_swings/tasks/` and can be overridden or supplemented by datapacks. See [`docs/TASK_DEFINITIONS.md`](docs/TASK_DEFINITIONS.md), copy [`docs/TASK_TEMPLATE.json`](docs/TASK_TEMPLATE.json), and use the included JSON Schema for editor validation.

## Named areas

```mcfunction
/moodswings area pos1
/moodswings area pos2
/moodswings area save gym
```

Area borders are now persistent client-side wireframes rather than one-shot corner particles:

```mcfunction
/moodswings area show gym
/moodswings area show all
/moodswings area hide gym
/moodswings area hide all
```

The borders wrap the complete inclusive block cuboid, remain visible until hidden or until the client disconnects, and are shown only to the command user. Each client can choose their own border color and opacity in the five-category Cloth Config screen.

Other area commands:

```mcfunction
/moodswings area list
/moodswings area info gym
/moodswings area remove gym
```


## OST Areas

OST Areas are a completely separate region system for looping map ambience. They never count as task areas and cannot be selected by a mood task.
Their playback uses Minecraft's **Master** sound category, while each region still keeps its own configurable volume.

```mcfunction
/moodswings ostarea pos1
/moodswings ostarea pos2
/moodswings ostarea save main_gallery mood_swings:museum_gallery
```

Each region stores its own sound event ID, volume, overlap priority, fade duration, wind-like camera sway, and optional world darkening:

```mcfunction
/moodswings ostarea sound main_gallery mood_swings:museum_gallery_night
/moodswings ostarea volume main_gallery 0.65
/moodswings ostarea priority main_gallery 10
/moodswings ostarea fade main_gallery 3.5
/moodswings ostarea shake main_gallery 0.20
/moodswings ostarea darkness main_gallery 0.35
/moodswings ostarea info main_gallery
/moodswings ostarea list
```

Camera sway and world darkening both use normalized `0.0` to `1.0` settings. The sway is smooth and gust-like rather than jittery. Darkening is drawn over the 3D world before Minecraft renders the HUD, so the hotbar, chat, inventories and other GUI screens remain clear. Both effects fade using the area's audio fade duration.

When regions overlap, the highest priority wins. Equal priorities prefer the smaller cuboid, making it easy to place a room-specific track inside a broad map ambience region. Every unique OST track used in the current dimension starts as part of a synchronized playback pool and keeps advancing while muted outside its selected region. Region changes therefore crossfade volume between already-running streams instead of restarting either track. Region checks remain server-authoritative, and the pool is cleaned up on death, spectating, dimension changes, disconnects, deletion, or disabling the feature.

OST Areas have their own local border commands and per-save enable setting:

```mcfunction
/moodswings ostarea show main_gallery
/moodswings ostarea show all
/moodswings ostarea hide main_gallery
/moodswings ostarea hide all
/moodswings ostarea enable
/moodswings ostarea disable
```

See [`docs/OST_AREAS.md`](docs/OST_AREAS.md) and [`docs/OST_SOUNDS_TEMPLATE.json`](docs/OST_SOUNDS_TEMPLATE.json) for audio-file registration.

Two streamed ambience events are bundled and ready to assign:

```text
mood_swings:hotel.outside
mood_swings:hotel.inside
```

The bundled hotel pair uses matching **159.05-second seamless loops**, so both tracks stay phase-aligned in the synchronized OST pool while keeping the mod download compact.


## Lockdown shop system

The Lockdown area picker uses Wathe's own tool-shop slot sprite, coin glyph, brown/gold plaques, and shop hover treatment for a native visual fit.

Killers can buy **Lockdown** from Wathe's existing shop for $200. The shop item includes a concise ability description, and selecting it opens a dedicated area-control screen containing every saved **Lockdown Area** in the player's current dimension. Choosing an area closes and jams every Wathe door variant inside the saved cuboid for 30 seconds, including the metal sheet and cockpit doors that normally use Minecraft's vanilla DoorBlock implementation. After a successful activation, that player must wait two minutes before purchasing Lockdown again; while it is unavailable, the shop tooltip shows Wathe's native red live cooldown countdown beneath the icon.

The purchase is fully server-authoritative: the role, running game, balance, cooldown, dimension, area, feature setting, and matching doors are revalidated after selection. Money is removed only when at least one door is successfully locked. Blasted Wathe block-entity doors are ignored and a pre-existing longer lockpick timer is never shortened. Crouch-using a lockpick also applies Wathe-style temporary jamming to the metal sheet and cockpit doors. Purchase failures use Wathe's original shop message and sound.

Lockdown can be enabled or disabled independently for each save from the **Lockdown** config category. It uses a dedicated region store and never reads Mood task areas or OST Areas. Create regions with:

```mcfunction
/moodswings lockdown area pos1
/moodswings lockdown area pos2
/moodswings lockdown area save lobby
```

The same command branch also provides `list`, `info`, `remove`, `show`, `show all`, `hide`, and `hide all`. The Lockdown config tab lists these regions, can outline them locally, and can delete them. See [`docs/LOCKDOWN_AREAS.md`](docs/LOCKDOWN_AREAS.md) for the full authoring reference. Disabling Lockdown removes the shop entry for that save while preserving the saved Lockdown Areas.

## Config menu

Open Mood Swings from Mod Menu to access five categories:

- **Tasks**: enable or disable every native/custom task and override the editable fields of JSON tasks for the current save.
- **Areas**: inspect, rename, delete, and individually show or hide every task area in the current save.
- **OST Areas**: enable or disable ambience for the save, edit each region's sound, volume, priority, fade time, and subtle screen shake, delete regions, and toggle their borders.
- **Lockdown**: enable or disable the shop ability for the current save and review its price, duration, cooldown, and area requirements.
- **Appearance & Tools**: show or hide area borders, change their color/opacity, refresh save data, and reload JSON task definitions.

Active task state is stored per player and per dimension. Task edits are stored as per-save overrides. The original datapack JSON remains untouched and can be restored with the task's reset toggle. Task audience, decimal roll weight, passive-income blocking, kill counts, and above-block detection are editable too.

## Per-dimension active tasks

Each player has a separate active task state for every dimension. The task itself, its progress, and the next-task cooldown are swapped when the player changes dimension and restored when they return. Existing global task NBT is migrated into the dimension where the player first loads it.

Task enable/disable controls and JSON overrides remain shared by the save.

## Per-save task controls

```mcfunction
/moodswings task list
/moodswings task disable outside
/moodswings task disable mood_swings:gym
/moodswings task enable mood_swings:gym
```

Save-specific task areas, OST Areas, disabled tasks, task overrides, and feature enable settings are stored at:

```text
<save>/mood_swings/settings.json
```

## Testing and reloads

```mcfunction
/moodswings task force mood_swings:take_a_shower
/moodswings task force mood_swings:strange_noise
/moodswings task clear
/moodswings task clear <player>
/moodswings reload
```

Vanilla `/reload` also reloads task datapacks. Active tasks retain a serialized snapshot of their definition, so their HUD text and completion rules remain stable while they are in progress.

## Building

Install Java 21, extract the project, then run `BUILD.bat`. The built JAR appears in `dist/`.

## Public API surface

- Mod ID / registry namespace: `mood_swings`
- Java package: `net.ceziak.mood_swings`
- Task resource directory: `data/<namespace>/mood_swings/tasks/`
- License: MIT

## Upgrading

Copy every file from the new release over the old project and replace existing files. The project retains compatibility placeholders for obsolete 1.0.x hard-coded task sources, preventing stale Java files from breaking upgraded repositories.

## Extended environmental kill credit

Mood Swings keeps the last successful player attacker for 30 seconds. If the victim is eliminated by Wathe during that window without a supplied killer, such as falling below the play area after being hit, Wathe receives the recent attacker as the killer. This preserves Wathe's normal killer rewards and completes active `kill_player` tasks. The newest successful attacker owns the credit, while explicit Wathe attackers and poisoners always take priority.
