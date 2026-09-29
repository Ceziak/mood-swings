# Changelog

## 1.4.9

- Added Wathe's native live cooldown line beneath the Lockdown shop icon while the ability is unavailable.
- The red `On cooldown: %s` text now uses Wathe's own countdown calculation, localization, color, and tooltip placement.

## 1.4.8

- Added dedicated per-save Lockdown Areas, completely separate from Mood task areas and OST Areas.
- Added `/moodswings lockdown area pos1`, `pos2`, `save`, `remove`, `info`, `list`, `show`, and `hide` commands.
- Lockdown purchases now list and validate only dedicated Lockdown Areas in the player's current dimension.
- Added Lockdown Area visibility and deletion controls to the Lockdown config category.
- Increased OST Area camera motion and reshaped it into smooth, slow wind-like gusts instead of barely visible micro-shake.
- Added per-OST-Area world darkening with `/moodswings ostarea darkness <name> <0.0-1.0>` and a matching config field.
- World darkening renders before Minecraft's HUD, keeping the hotbar, chat, inventories, menus, and other GUI elements unaffected.
- Camera sway and world darkening both transition using the selected OST Area's fade duration.

## 1.4.7

- Reworked the Lockdown area picker to use Wathe's native killer-shop visual language instead of the custom card panel.
- Area choices now use Wathe's real `gui/shop_slot_tool` frame, gold/brown price plaques, the Wathe coin glyph, and the same shop-item hover sheen.
- Lockdown purchase success and failure feedback now use player-only `MASTER` shop audio, matching Wathe's native store behavior and pitch variation.
- Preserved all 1.4.6 Wathe-door compatibility and optimized synchronized OST behavior.

## 1.4.6

- Extended Wathe-style temporary door jamming to `wathe:metal_sheet_door` and `wathe:cockpit_door`, the two Wathe doors implemented as vanilla `DoorBlock`s.
- All nine Wathe door variants now participate in Lockdown while preserving Wathe's native block-entity jam behavior for existing custom doors.
- Crouch-using Wathe's lockpick can now jam the metal sheet and cockpit doors with the same cooldown, sound, and jam duration as Wathe's custom doors.
- Jammed vanilla Wathe doors reject hand opening and programmatic opening attempts and use Wathe's native locked-door feedback.
- Kept the compatibility scoped to Wathe door blocks instead of altering Minecraft's global `wooden_doors` tag.
- Rebuilt the Lockdown area picker with a cleaner Wathe-inspired card layout, compact cost/duration/cooldown status tiles, hover states, responsive columns, and minimal pagination controls.
- Removed the old flat button-list presentation and hard-coded summary strip.

## 1.4.5

- Optimized the bundled hotel OST from 65-minute source renders into synchronized 159.05-second seamless loops.
- Chose a common musical return point shared by both hotel ambience variants and preserved identical durations for synchronized playback.
- Added a six-second wrap crossfade at the beginning of each rendered loop to remove audible end-to-start discontinuities.
- Reduced the bundled hotel audio from roughly 128 MB to about 4 MB without changing the sound event IDs or OST Area configuration.
- Kept both tracks stereo, streamed, Master-category routed, and compatible with the synchronized playback watchdog.

## 1.4.4

- Added a self-healing OST playback watchdog that detects streams dropped by Minecraft or OpenAL and rebuilds the complete synchronized pool.
- Added a five-second startup grace period and persistent-failure confirmation to avoid false recovery loops while long streamed OGG files initialize.
- Added a short source-release delay and rebuild cooldown to prevent rapid OpenAL stop/start allocation races.
- OST streams now remain silently alive for ten seconds across brief train resets, death transitions, and temporary server-side silent states.
- Replaced true zero-volume muting with an inaudible keep-alive gain so inactive synchronized tracks are not culled by the audio pipeline.
- Added a clear warning when the watchdog has to recover a failed OST pool.

## 1.4.3

- Replaced restart-on-region-change OST playback with a persistent per-dimension sound pool.
- Every unique OST track used in the current dimension now starts together and keeps advancing while muted outside its selected region.
- Moving between OST Areas now crossfades volume between already-running streams instead of replaying either track from the beginning.
- Preserved the existing priority, smaller-area tie-break, Master-category routing, per-area volume, and fade settings.
- Removed obsolete client state that tracked only one active sound instance.
- Added optional per-OST-Area screen shake with `/moodswings ostarea shake <name> <0.0-1.0>`.
- Added an editable screen-shake field to the OST Areas config tab.
- Kept the effect intentionally tiny even at maximum strength and synchronized its transition with the area's fade duration.
- Combined OST camera motion safely with the existing murder-task shake.

## 1.4.2

- Routed every OST Area sound through Minecraft's Master sound category.
- OST ambience is now controlled only by the Master volume slider, regardless of the selected sound event or its source mod/resource pack.
- Existing area volume, fade, looping, and crossfade behavior is unchanged.

## 1.4.1

- Added native sound-event autocomplete to `/moodswings ostarea save` and `/moodswings ostarea sound`.
- Sound suggestions now follow the sounds currently available to the Minecraft client, including mod and active resource-pack sound events.
- Replaced the loose sound text argument with Minecraft's identifier parser, preventing malformed sound IDs from being saved.

## 1.4.0
- Bundled streamed `mood_swings:hotel.outside` and `mood_swings:hotel.inside` ambience events.

- Added a completely separate per-save OST Area system that never participates in mood tasks.
- Added `/moodswings ostarea pos1`, `pos2`, `save`, `remove`, `info`, `list`, `show`, and `hide` commands.
- Added commands for changing an OST Area's sound event, volume, priority, and fade duration.
- Added priority-based overlap handling; smaller regions win equal-priority ties.
- Added server-authoritative per-player region selection with state-change-only networking.
- Added non-positional looping client audio with smooth crossfades and cleanup on death, spectating, dimension changes, disconnect, deletion, and feature shutdown.
- Added a dedicated OST Areas config category with a per-save enable toggle and editable region settings.
- Added backwards-compatible save migration defaults for existing worlds.
- Added OST authoring and custom OGG registration documentation.

## 1.3.15

- Reworked the Lockdown shop tooltip to match Wathe's muted multi-line item description style.
- Added the final public-facing description, including crowbar-blasted door behavior.
- Added a server-authoritative two-minute Lockdown cooldown per player.
- Cooldown is checked both before opening the area picker and again before activation.
- Added Lockdown cooldown information to the area picker and per-save config screen.

## 1.3.14

- Added a localized description to the Lockdown shop item.
- Replaced the basic area list with a responsive, styled Lockdown control screen.
- Moved every player-facing Lockdown string into the language file and cleaned up the activation message.
- Added a dedicated Lockdown category to the Cloth Config menu.
- Lockdown can now be enabled or disabled independently for each save.
- Disabling Lockdown removes its Wathe shop entry on both the server and connected clients.
- Existing saves default to Lockdown enabled when the new setting is first introduced.
- Renamed the public-facing Debug category to Appearance & Tools.

## 1.3.13

- Lockdown purchase failures now use Wathe's native red `Purchase Failed` action-bar text.
- Lockdown purchase failures now play Wathe's `UI_SHOP_BUY_FAIL` sound with the same randomized pitch range as the original shop.
- Detailed validation reasons are retained in the debug log instead of being shown to players.

## 1.3.12

- Added a native Wathe shop entry for the new Lockdown system.
- Clicking Lockdown opens a paged selector containing existing Mood Swings named areas in the current dimension.
- Selecting an area locks every lockpick-compatible Wathe door inside it for 30 seconds.
- Open doors are closed when the lockdown starts, blasted doors are ignored, and existing longer jam timers are preserved.
- The $200 cost is charged only after the server validates the area and successfully finds at least one door.
- Added the Lockdown shop icon resource.

## 1.3.11

- Added a 30-second last-attacker credit window for Wathe eliminations.
- Successful player damage now combat-tags the victim for 600 server ticks.
- If Wathe later eliminates that victim without an attacker, the most recent valid attacker is supplied to Wathe.
- Environmental eliminations such as being knocked below the play area now grant normal Wathe kill credit and complete the murder task.
- A newer successful hit replaces the previous attacker; direct Wathe killers and poisoners still take priority.

## 1.3.10

- Removed the large blank spacing between murder-task scrolling phrases.
- Removed the extra 12-pixel gap where the scrolling strip loops.
- Phrases now flow continuously with a single normal space between them.


## 1.3.9

- After the murder task remains active for 120 seconds, sprinting is disabled.
- After 120 seconds, the selected hotbar slot jumps to a different random slot at irregular intervals.
- Killing a player immediately restores controls, while heartbeat, camera shake, and vignette now decay smoothly over four seconds.
- Murder-task elapsed time is persisted and shared with server-side overdue checks.
- Fixed the stale BUILD.bat output filename.

## 1.3.8

- Fixed a crash triggered by killing a player even when no murder task was active.
- Wathe kill callbacks now return immediately unless a custom kill-player task exists.
- Kill progress is processed on the server executor after Wathe finishes its elimination logic.
- Kill completion now touches only matching custom kill tasks instead of force-running the full task ticker.

## 1.3.7

- Removed the diamond separator glyphs from the endlessly scrolling murder-task text.
- Murder phrases are now separated using clean empty spacing only.

## 1.3.6

- Replaced the murder HUD line with an endlessly scrolling, randomized strip containing all eight pressure messages.
- Added a heartbeat that begins after 30 seconds and accelerates exponentially.
- Added an exponentially darkening edge vignette beginning after 30 seconds.
- Retuned camera shake to reach its cap at two minutes.
- Instinct now becomes unavailable after 120 seconds instead of 180 seconds.


## 1.3.5

- Saved each player's active task, progress, and next-task cooldown independently per dimension.
- Switching dimensions now stores the current dimension state and restores the destination dimension state.
- Migrated old global task NBT into the player's current dimension automatically.
- Added an upgrade compatibility replacement for the obsolete `PlayerShopComponentMixin.java` source.

## 1.3.4

- Removed disabled tasks immediately, including tasks restored from old player NBT.
- Fixed Wathe native placeholder tasks surviving when a saved custom task could not be restored.
- Added a final generation guard so disabled or invalid tasks cannot enter the active task map.
- Added a short completion-condition grace window to prevent single-tick position or block-state flicker from erasing progress.
- Improved shower and toilet detection across the player's full footprint.
- Made named-area detection tolerant at block and selection boundaries.

## 1.3.3

- Fixed murder-task passive-income blocking by intercepting Wathe's real MurderGameMode payment call.
- Added exponentially increasing camera shake while the murder task remains active.
- Instinct is disabled after the murder task has remained active for three minutes.

## 1.3.2

- Fixed `mood_swings:murder` not completing after a Wathe knife kill.
- Wathe eliminations are now detected directly through its `GameFunctions.killPlayer` flow.

## 1.3.1

- Added Wathe-style scrolling and shaking animation to the active `mood_swings:murder` task.
- Fixed Minecraft 1.21.1 `DrawContext.drawTextWithShadow` compatibility.

## 1.3.0

- Fixed custom task conditions failing to progress or complete reliably when Wathe returned early from its mood tick.
- Added `/moodswings task clear <player>` while preserving the self-targeted command.
- Added the repeatable `mood_swings:toilet` task using the extensible `#mood_swings:toilets` block tag.
- Added the rare killer-only `mood_swings:murder` task, completed by killing a player.
- Added JSON `audience` support with `all`, `civilian`, and `killer` values.
- Added decimal task weights for genuinely rare rolls.
- Added JSON `blocks_passive_income` support and pause behavior for Wathe passive income.
- Added the `above_block` and `kill_player` JSON condition types.
- Added config-menu controls and documentation for all new task fields and conditions.

## 1.2.0

- Replaced the minimal border-only config with a complete three-category Cloth Config screen.
- Added dynamic per-save task editing for all JSON task types, plus enable/disable controls for native Wathe tasks.
- Added per-save task overrides without modifying the source datapack JSON.
- Added area listing, renaming, deletion, and per-area border toggles to the config menu.
- Added Debug controls for showing/hiding all borders, border color/opacity, snapshot refreshes, and JSON task reloads.
- Added client/server config synchronization with operator-only editing.
- Replaced the mod icon with the supplied pixel-art icon.


## 1.1.1

- Fixed compilation on Minecraft 1.21.1 by parsing JSON `required_facing` values with `Direction.byName`.
- Added upgrade compatibility placeholders that overwrite obsolete 1.0.x hard-coded task sources when files are copied into an existing repository.

## 1.1.0

- Replaced hardcoded custom-task definitions with datapack JSON files.
- Added the `stay_in_area`, `random_area`, `travel_distance`, `near_player`, and `under_block` condition types.
- Added dynamic task discovery, command suggestions, per-save enabling/disabling, and reload support.
- Added a reusable task template, JSON Schema, and full authoring documentation.
- Added persistent client-side 3D cuboid borders for `/moodswings area show`.
- Added `/moodswings area show all`, `hide <name>`, and `hide all`.
- Made area-border appearance configurable per client through Cloth Config.
- Kept active task definitions stable across datapack reloads by saving a task snapshot in NBT.
- Preserved Wathe's native task HUD and rotation behavior.

## 1.0.0

- Initial public release.
- Added six custom mood tasks.
- Added named areas and per-save task controls.
