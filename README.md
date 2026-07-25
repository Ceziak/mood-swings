# Mood Swings

Mood Swings is an unofficial Fabric addon for **Wathe: Murder Mystery** that adds configurable mood tasks and reusable named areas while keeping Wathe's original mood HUD and animations. Wathe is required and is not bundled with this project.

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.18.4 or newer
- Fabric API 0.116.14+1.21.1 or newer
- Wathe 1.3.2-1.21.1
- Cloth Config API 15.0.140
- Java 21
- Mod Menu 11.0.3 is optional and provides the config button

Install Mood Swings, Wathe, Fabric API, and Cloth Config on both the server and every client.

## Included tasks

- `take_a_shower`: remain beneath an active downward-facing shower head for the configured duration.
- `socialize`: remain near another living, non-spectating player for the configured duration.
- `gym`: remain inside the configured gym area for the configured duration.
- `strange_noise`: visit a randomly selected named area in the player's current dimension and remain there for the configured duration. The selected area's saved display name appears directly in the task text.
- `walk`: travel the configured horizontal distance. Large teleport steps do not count.
- `rooftop`: remain inside the configured rooftop area for the configured duration.

Every custom task is repeatable and fully refills real mood when completed. Killer roles receive separate fake-task wording. Tasks are shown through Wathe's native mood UI, with no extra progress-bar overlay.

## Named areas

Stand at the first corner:

/moodswings area pos1


Stand at the opposite corner:

/moodswings area pos2


Save the selection:

/moodswings area save gym
/moodswings area save rooftop


Area names may contain spaces when quoted. The internal lookup is case-insensitive. Both corners must be selected in the same dimension.

/moodswings area save "fitness room"


Other commands:

/moodswings area list
/moodswings area info gym
/moodswings area show gym
/moodswings area remove gym


## Per-save task controls

These settings are stored inside the current Minecraft save:

/moodswings task list
/moodswings task disable outside
/moodswings task disable gym
/moodswings task enable gym


The save data is stored at:

<save>/mood_swings/settings.json


## Testing

/moodswings task force take_a_shower
/moodswings task force strange_noise
/moodswings task force walk
/moodswings task clear


Forced custom tasks return to Wathe's normal task rotation immediately after completion or clearing.


## Configuration

Global task timings, distances, ranges, and area IDs are stored at:

config/mood_swings.json


With Mod Menu installed, open **Mods → Mood Swings → Configure**. On a dedicated server, the server's config file is authoritative. Reload it without restarting:

/moodswings reload


The default shower head is supplied through the block tag:

#mood_swings:shower_heads


Datapacks may add compatible blocks to this tag.

## Building

Install Java 21, extract the project, then run `BUILD.bat`. The built JAR appears in `dist/`.

## Public release notes

- Mod ID and registry namespace: `mood_swings`
- Main Java package: `net.ceziak.mood_swings`
- License: MIT
- No map-specific content is hardcoded. Named areas, durations, ranges, and distances are configurable.
- Resource packs may replace the English task strings, and datapacks may extend the shower-head block tag.
