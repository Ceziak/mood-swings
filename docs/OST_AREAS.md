# OST Areas

OST Areas are save-specific cuboids that control looping, non-positional sound events. They are completely separate from Mood Swings task areas and are never considered when tasks choose or validate an area. Every unique OST track used in the player's current dimension is kept running in a synchronized playback pool and muted whenever its region is not selected.

## Creating a region

Stand at the first and second corners of the cuboid, then save it with a sound event ID:

```mcfunction
/moodswings ostarea pos1
/moodswings ostarea pos2
/moodswings ostarea save main_gallery mood_swings:museum_gallery
```

Coordinates may also be supplied explicitly:

```mcfunction
/moodswings ostarea pos1 10 64 -20
/moodswings ostarea pos2 35 79 12
```

Names containing spaces must be quoted.

## Region controls

```mcfunction
/moodswings ostarea list
/moodswings ostarea info main_gallery
/moodswings ostarea sound main_gallery mood_swings:museum_gallery_night
/moodswings ostarea volume main_gallery 0.65
/moodswings ostarea priority main_gallery 10
/moodswings ostarea fade main_gallery 3.5
/moodswings ostarea shake main_gallery 0.20
/moodswings ostarea darkness main_gallery 0.35
/moodswings ostarea remove main_gallery
```

The defaults for a newly saved region are:

- volume: `1.0`
- priority: `0`
- fade-in and fade-out: `2.0` seconds
- wind camera sway: `0.0` (disabled)
- world darkening: `0.0` (disabled)

Volume, camera-sway intensity, and world-darkening intensity are limited to `0.0` through `1.0`. Fade time is limited to `0.0` through `30.0` seconds.

The sway setting is normalized rather than measured in degrees. It uses slow overlapping waves and a gentle gust envelope, so higher values feel like wind pushing into the camera instead of combat vibration. World darkening is a black overlay rendered before the HUD, which leaves hotbar, chat, inventories, menus, and other GUI elements undarkened. Both effects follow the selected OST Area and fade with its audio fade duration.

## Overlapping regions

Only one OST Area is selected for each player.

1. The region with the highest priority wins.
2. If priorities match, the smaller cuboid wins.
3. If both still match, the region ID is used as a stable final tie-breaker.

This allows a broad map ambience to use priority `0`, while a room-specific track can override it with a higher priority.

## Synchronized playback

All unique OST sounds referenced by regions in the player's current dimension begin together. Sounds that are not currently selected remain at zero volume instead of being stopped. When the selected region changes, the old and new streams only adjust their volume using the configured fades, so playback does not jump back to the beginning.

For layered versions of the same composition, such as indoor and outdoor mixes, use audio files with matching duration and arrangement. They will remain aligned after region transitions because both streams continue advancing underneath the mix.

The pool is rebuilt when the player changes dimension or when region definitions change. Optional per-area screen shake follows only the currently selected region and does not affect track synchronization. Death, spectating, disconnecting, or disabling OST Areas fades and retires the current pool.

## Borders

OST Area borders are local visual tools and use their own IDs, so they cannot collide with task-area borders.

```mcfunction
/moodswings ostarea show main_gallery
/moodswings ostarea show all
/moodswings ostarea hide main_gallery
/moodswings ostarea hide all
```

## Per-save setting

OST Areas can be enabled or disabled from the **OST Areas** config category or with:

```mcfunction
/moodswings ostarea enable
/moodswings ostarea disable
```

Disabling the system fades out active ambience and stops region evaluation without deleting any saved regions.

## Adding custom audio

Custom sounds must be available on every client through Mood Swings itself, another installed mod, or a resource pack.

Place an OGG file at:

```text
assets/mood_swings/sounds/ost/museum_gallery.ogg
```

Then register it in `assets/mood_swings/sounds.json`:

```json
{
  "museum_gallery": {
    "sounds": [
      {
        "name": "mood_swings:ost/museum_gallery",
        "stream": true
      }
    ]
  }
}
```

The command sound ID is then:

```text
mood_swings:museum_gallery
```

Stereo OGG files work well for non-positional ambience. Long music and ambience files should use `"stream": true`.

All OST Area playback is routed through Minecraft's **Master** sound category. The Music, Ambient/Environment, Records, and other category sliders do not affect it; the per-area volume remains a multiplier beneath the player's Master volume.

## Sound ID autocomplete

The `sound` argument uses Minecraft's available-sounds suggestion provider. Press **Tab** while entering the sound ID in either command to browse sounds loaded by the current modpack and active resource packs:

```mcfunction
/moodswings ostarea save hotel_lobby mood_swings:hotel.inside
/moodswings ostarea sound hotel_lobby mood_swings:hotel.outside
```

The bundled `hotel.inside` and `hotel.outside` events use matching 159.05-second seamless loops. Their identical duration is intentional so synchronized region crossfades preserve their shared timeline.

Malformed identifiers are rejected by the command parser. A correctly formatted ID can still become unavailable later if the mod or resource pack that provides it is removed.
