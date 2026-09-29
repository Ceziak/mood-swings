# JSON task definitions

Mood Swings loads tasks from server datapacks at:

```text
data/<your_namespace>/mood_swings/tasks/<task_id>.json
```

For example, `data/example/mood_swings/tasks/pool.json` registers the task
`example:pool`.

Copy [`TASK_TEMPLATE.json`](TASK_TEMPLATE.json) when creating a task. The included
[`task-definition.schema.json`](task-definition.schema.json) provides editor completion and validation in IDEs that support JSON Schema.

Run vanilla `/reload` after changing a datapack, or use `/moodswings reload` while testing. Invalid definitions are skipped and reported in the server log without preventing valid tasks from loading.

## Common fields

```json
{
  "civilian_text": "You feel like visiting the pool",
  "killer_text": "You could fake visiting the pool",
  "mood_value": 1.0,
  "weight": 1.0,
  "repeatable": true,
  "audience": "all",
  "blocks_passive_income": false,
  "condition": {
    "type": "mood_swings:stay_in_area",
    "area": "pool",
    "duration_seconds": 10
  }
}
```

- `civilian_text`: exact text shown to civilians.
- `killer_text`: exact fake-task text shown to killers.
- `mood_value`: final mood after completion, from `0.0` to `1.0`.
- `weight`: relative selection chance. Decimal weights are supported, so `0.15` is much rarer than `1.0`.
- `repeatable`: must currently be `true`.
- `audience`: `all`, `civilian`, or `killer`. Defaults to `all`.
- `blocks_passive_income`: when `true`, Wathe passive income is paused for the task holder until the task is completed or cleared.
- `<area>` or `<area name>` in either text is replaced by the selected random area's display name.

All fields can also be overridden for an individual save through Mood Swings' **Tasks** config category.

## Condition types

### Stay in a named area

```json
"condition": {
  "type": "mood_swings:stay_in_area",
  "area": "pool",
  "duration_seconds": 10
}
```

### Visit a random saved area

```json
"condition": {
  "type": "mood_swings:random_area",
  "duration_seconds": 10,
  "exclude_current": true
}
```

### Travel a distance

```json
"condition": {
  "type": "mood_swings:travel_distance",
  "distance_blocks": 50,
  "max_counted_step": 2
}
```

`max_counted_step` prevents teleports from counting as walking.

### Remain near another player

```json
"condition": {
  "type": "mood_swings:near_player",
  "range": 3,
  "duration_seconds": 10
}
```

### Stand below a tagged block

```json
"condition": {
  "type": "mood_swings:under_block",
  "block_tag": "example:working_showers",
  "duration_seconds": 10,
  "scan_height": 8,
  "require_powered": true,
  "required_facing": "down"
}
```

### Stand above a tagged block

```json
"condition": {
  "type": "mood_swings:above_block",
  "block_tag": "example:toilets",
  "duration_seconds": 10,
  "scan_depth": 1
}
```

This is useful for toilets, pressure plates, floor stations, and other activities detected below the player's feet.

`require_powered` and `required_facing` are optional for both vertical block conditions. The tag itself is a normal Minecraft block tag. A generic block used with `require_powered` must expose Minecraft's boolean `powered` block-state property.

### Kill a player

```json
"audience": "killer",
"blocks_passive_income": true,
"condition": {
  "type": "mood_swings:kill_player",
  "required_count": 1
}
```

Only direct player kills count. Use `audience: killer` when the task should never be rolled for civilians.

## Block tags

Bundled tags can be extended from a datapack. For example, to add custom toilet blocks to `#mood_swings:toilets`:

```text
data/mood_swings/tags/block/toilets.json
```

```json
{
  "replace": false,
  "values": [
    "your_mod:toilet"
  ]
}
```

The bundled tag already contains `minecraft:hopper`.

## Commands

```mcfunction
/moodswings task force example:pool
/moodswings task clear
/moodswings task clear <player>
/moodswings task disable example:pool
/moodswings task enable example:pool
/moodswings task list
/moodswings reload
```

Bare names continue to work for bundled tasks, such as `gym`; their canonical IDs are `mood_swings:gym`.
