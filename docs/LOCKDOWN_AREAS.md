# Lockdown Areas

Lockdown Areas are save-specific cuboids used only by the Lockdown killer-shop ability. They are completely separate from Mood task areas and OST Areas.

## Create a region

```mcfunction
/moodswings lockdown area pos1
/moodswings lockdown area pos2
/moodswings lockdown area save lobby
```

Coordinates may be supplied explicitly after `pos1` or `pos2`. Names containing spaces must be quoted.

## Management

```mcfunction
/moodswings lockdown area list
/moodswings lockdown area info lobby
/moodswings lockdown area show lobby
/moodswings lockdown area show all
/moodswings lockdown area hide lobby
/moodswings lockdown area hide all
/moodswings lockdown area remove lobby
```

The Lockdown picker only lists Lockdown Areas in the player's current dimension. Task areas never appear in this menu.

The **Lockdown** config tab also lists saved Lockdown Areas, can toggle their local border previews, and can delete them when the config is saved.
