# I'm So Talented

A Fabric mod for Minecraft 26.3 with four talent trees. Each tree has its own XP, and you earn it by doing that tree's thing: mine and farm for Gatherer, fight for Warrior, take hits for Guardian, travel for Explorer. Talents make you mine faster, quarry whole ore veins, find more loot, hit harder, take less damage, carry more and move better.

## How it works
- Press **N** (or the XP-bottle button above your inventory) to open the talent tree. Each tree has its own tab.
- Every tree has an XP meter and levels. When the meter fills, the tree goes up a level and you get one **talent point** to spend in that tree only. Click a talent to spend a point on its next level. Buying spends the point, never the XP, so the bar never drops.
- All four meters fill from the start, even before you buy a tree's first talent. A tree stops levelling once it has a point for every talent level in it.
- Levelling a tree posts a chat line telling you how many points you have to spend there.
- Vanilla XP has nothing to do with talents: it stays for enchanting, anvils and Mending.
- In creative, talents are free and tree XP isn't earned.
- Talents, tree XP and unspent points are saved with your player and kept when you die.
- A talent shows what it needs when it's locked. Most need one level in the talent above them; Lifesteal needs Stronger Weapons at level 5.

**Toggling.** Nine talents are *toggleable*: Higher Jumping, Faster Running, Step Assist, Safe Falling, Swim Speed, Gliding, Night Vision, Fire Resistance and Water Breathing. **Right-click the node** to switch a bought talent off (and back on) without losing it, or use `/talents toggle <players> <talent> [on|off]` (operators; omit `on`/`off` to flip). A toggled-off talent shows dimmed with an **OFF** badge and has no effect until you toggle it back on. Toggling costs nothing and is saved with your player. In creative, talents are always on and the toggle control is hidden.

## Earning tree XP

| Tree | Earns from | XP | Doesn't count |
|---|---|---|---|
| Gatherer | Mining with a pickaxe (ores pay the most), harvesting fully grown crops, catching fish | 5 per ore, 0.5 per other pickaxe block (stone, deepslate, netherrack, an ore with Silk Touch), 1 per crop, 5 per catch | Blocks for other tools (sand, dirt, gravel, logs), the wrong tool or a bare hand |
| Warrior | Damage you deal to hostile mobs, melee or ranged | 1 per point of damage | Overkill past the mob's remaining health, players, passive mobs, thorns and fire |
| Guardian | Damage hostile mobs aim at you, counted before armor, shield and Block Chance, as long as you survive the hit | 2 per point of damage | Falls, fire, drowning and other damage that isn't from a mob, the hit that kills you |
| Explorer | Moving under your own power while holding a movement key (walking, sprinting, wading, swimming; elytra gliding at half rate), and every chunk you enter for the first time, however you get there | 1 per 25 blocks, 1 per new chunk | Riding, boats, minecarts, floating in water, being pushed or knocked back |

Each tree's XP talent (Gatherer XP, Warrior XP, Guardian XP, Explorer XP) adds 10% per level to that tree's XP. The numbers aim at roughly 1,500 XP for an hour of each activity.

**Prices.** Going from level n to level n + 1 takes `ceil(25 × 1.18^n)` tree XP, the same in every tree: level 1 takes 25, level 10 takes 111 and level 20 takes 581. Maxing a tree takes 16,700 to 27,600 tree XP, roughly 11 to 18 hours of its activity (Gatherer has the most levels). Both numbers are [settings](#settings).

**On the HUD.** One thin bar sits just above the vanilla XP bar, in the tree's color. Pin a tree with the pin button in its tab and its bar stays there. When you earn XP in another tree, that tree borrows the slot for 3 seconds. With nothing pinned the bar only shows for 3 seconds after you earn XP. The pin is a setting on your own computer.

## Talents

| Tree | Talent | Levels | At the top level |
|---|---|---|---|
| Gatherer | Faster Mining | 5 | Mines like Efficiency V |
| | Gatherer XP | 3 | +30% Gatherer XP |
| | Reach | 3 | +1.5 blocks of block reach (6.0) and +0.5 for mobs (3.5) |
| | Careful Hands | 3 | Gear works as if it had Unbreaking III |
| | Bountiful Harvest | 3 | +3 copies of the crop's main item per harvest (mature wheat 1 → 4) |
| | Backpack | 4 | 36 backpack slots, one row of 9 per level |
| | Fast Growth | 3 | Crops within 16 blocks grow 2.5x faster |
| | Quarrying | 5 | Sneak while you break an ore to mine the whole connected vein with it (a 3×3×3 cube at level 1, one block wider per level, the whole vein at level 5) |
| | More Luck | 3 | Fortune, Looting and Luck of the Sea III |
| Warrior | Stronger Weapons | 5 | +20% damage with every attack |
| | Warrior XP | 3 | +30% Warrior XP |
| | Unarmed Specialist | 3 | +1.5 punch damage |
| | Sword, Spear, Mace, Bow and Trident Specialist | 3 each | +30% damage with that weapon |
| | Lifesteal | 3 | Heals 15% of the damage you deal (not in the game yet) |
| Guardian | More Health | 5 | +5 hearts |
| | Guardian XP | 3 | +30% Guardian XP |
| | Stronger Armor | 5 | +25% armor and armor toughness |
| | Shield Mastery | 3 | The shield blocks as soon as it's raised, and an axe disables it 75% shorter |
| | Fire Resistance | 3 | 30 seconds of fire resistance when you catch fire, at most every 3 minutes (not in the game yet) |
| | Block Chance | 5 | 10% chance to shrug off an attack from a mob or player, shield or not |
| | Water Breathing | 3 | Breathes like Respiration III (not in the game yet) |
| | Night Vision | 1 | Night vision while toggled on (not in the game yet) |
| | Second Wind | 3 | Regenerates 0.45 health per second while hurt (not in the game yet) |
| Explorer | Faster Running | 5 | +20% movement speed |
| | Higher Jumping | 5 | Jump about 2.2 blocks high (1.25 in vanilla) |
| | Step Assist | 1 | Walk up full blocks without jumping (step height 1.0, vanilla 0.6) |
| | Swim Speed | 3 | 30% more horizontal movement in water |
| | Explorer XP | 3 | +30% Explorer XP |
| | Safe Falling | 5 | Fall 10 more blocks before it hurts, and take 50% less fall damage |
| | Gliding | 3 | Fall no faster than 0.4 blocks per tick (not in the game yet) |
| | Flight Speed | 5 | Glide and fly 25% faster (not in the game yet) |

Talents marked "not in the game yet" can be bought but don't do anything yet.

**Talents and enchantments.** Faster Mining, More Luck and Careful Hands work like the matching enchantments, and reach the vanilla maximum: Efficiency V, Fortune III, Unbreaking III. The gear you hold keeps its own enchantment level when that's higher, so a Fortune I pickaxe with More Luck III digs like Fortune III, and a Fortune III pickaxe with More Luck II stays Fortune III. Set `enchantment_stacking=add` to add them together instead: then Fortune III with More Luck III digs like Fortune VI.

**Weapon damage.** Stronger Weapons and the matching specialist add up: a sword at the top of both does 50% more damage. They apply to real attacks only, meaning melee, sweeps, spear jabs and charges, mace smashes, arrows and tridents. Thorns, fire and explosions don't get the bonus.

**Farming.** Bountiful Harvest adds one copy of the crop's main item per level to your harvests of the classic seven: wheat, carrots, potatoes, beetroots, nether wart, sweet berry bushes and cocoa. Only the main item's count grows: seeds and side drops are untouched, and a stage that hasn't produced its main item (young wheat drops only seeds) gets no bonus. Explosions, animals and players without the talent get nothing. Fast Growth makes those same crops grow faster: the per-tick growth roll is multiplied by 1.0 + 0.5 per level, using the highest Fast Growth level among players within 16 blocks horizontally of the crop (any height). With no player in reach the crops grow exactly as in vanilla.

## Quarrying
- **Sneak** (hold **Shift**) while you break an ore, and the ore of the same kind touching it breaks too, corners included. Iron ore and deepslate iron ore count as one kind. Stone around the vein stays.
- How far it reaches: a 3×3×3 cube around the ore at level 1, 4×4×4 at level 2, then 5×5×5 and 6×6×6, and the whole vein at level 5. An even-sized cube puts its extra layer on the far side from you. One break takes at most 256 blocks.
- Every block counts as if you mined it yourself: the same drops (Fortune and More Luck too), 5 Gatherer XP per ore, and 1 durability per block (Unbreaking and Careful Hands still save some).
- It only starts with a tool that gets the ore's drops, and it stops with 1 durability left, so a pickaxe never breaks from quarrying. It also stops if you switch items.
- Everything the vein drops, items and XP, lands in front of you.
- Without sneaking you mine one block as usual.

## Backpack
- Press **Shift** and your inventory key (**Shift + E** by default), or the bundle button above your inventory, to open it. It also opens from inside your inventory. Locked slots show a padlock.
- Without the Backpack talent, Shift + E just opens your inventory. Controls also has an **Open Backpack** key of its own, unbound by default.
- Each level of the Backpack talent unlocks one row of 9 slots, up to 36.
- Items you pick up that don't fit your inventory go into your backpack. You can turn that off with `backpack_pickup`.
- When you die, it drops like your normal inventory. With `keep_inventory` on, it stays.
- If an operator lowers the talent, items in slots that are now locked move to free slots, or to your inventory, or drop at your feet.

## Commands
`/talents` needs operator rights (permission level 2).
- `/talents list <player>`: each tree's level, XP and points to spend, and their talent levels.
- `/talents set <players> <talent> <level>`
- `/talents xp <players> <tree> add|set <amount>`: give tree XP or set it outright.
- `/talents max <players>`: every talent at its top level, and every tree at its top level.
- `/talents reset <players>`: no talents and no tree XP.
- `/talents toggle <players> <talent> [on|off]`: switch a toggleable talent off/on (or flip, when `on`/`off` is omitted).

## Settings
The first start writes `config/imsotalented.properties`. Only the server's copy counts (in singleplayer, your own). The server sends players the prices and the stacking rule when they join. Changes take effect the next time a world is opened or the server starts. Values that don't parse fall back to the default, and out-of-range values are clamped.

| Setting | Default | What it does |
|---|---|---|
| `level_cost_base` | `25` | Tree XP the first level of a tree takes. |
| `level_cost_growth` | `1.18` | Each next level takes this many times the XP of the one before: level n to n + 1 takes `ceil(level_cost_base * level_cost_growth^n)`. `1.0` keeps it flat; allowed is 1.0 to 2.0. |
| `enchantment_stacking` | `highest` | `highest`: the higher of the talent and your gear's enchantment counts. `add`: both together. |
| `backpack_pickup` | `true` | Whether pickups that don't fit your inventory go into your backpack. |

The pinned HUD bar is a client setting, in `config/imsotalented-client.properties`.

## Updating from 0.1.0
Talents are reset: they used to be bought with vanilla XP, and the four trees start from scratch. Your vanilla XP is left alone. Items in the old extra inventory aren't lost: on your first join its slots are locked, so they move into your inventory or drop at your feet. The Experience Converter is gone, along with its recipe. The settings file gets the new prices: the old `cost_base` and `cost_growth` priced talents in vanilla XP, so they're replaced by `level_cost_base` and `level_cost_growth` at their defaults.

## Requirements
| Minecraft | Loader | Needs | Java |
|---|---|---|---|
| 26.3 | Fabric | Fabric Loader 0.19.5+, Fabric API 0.162.0+26.3+ | 25 |

Install it on the server and on every player's client.

## Development
- JDK 25 (`brew install openjdk@25`).

```bash
./gradlew build              # build/libs/imsotalented-fabric-<version>+mc26.3.jar; also runs the unit tests
./gradlew runClient          # dev client with the mod loaded
./gradlew runClientGameTest  # automated in-game test (opens a game window)
```

**Unit tests** (`./gradlew test`) cover:
- the talent list's structure (trees, roots, prerequisites, positions, level totals);
- tree levels and prices;
- every talent's numbers;
- the saved talent data;
- the settings file;
- what earns tree XP and what doesn't;
- the weapon, mining, shield, harvest, growth and unbreaking math;
- quarrying's reach per level, the vein search and where it stops for the tool;
- the backpack's storage.

**The client game test** plays through the main flows in survival and takes screenshots into `build/run/clientGameTest/screenshots/` (prefixed `ist_`).

**Art:** `python3 tools/gen_textures.py` redraws the mod icon using only Python's standard library, with an enlarged preview in `build/texture-preview/`.

### Code map
- `talent/`: the talent list and the four trees, what each level does (`TalentValues`), saved player data with tree XP (`TalentData`), tree level prices and enchantment stacking (`TalentRules`), buying and earning (`Talents`) and attribute talents (`TalentAttributes`).
- `effect/` and `mixin/`: talents that change game code, like mining speed, quarrying, loot, weapon damage, block chance and shields, and the hooks that earn tree XP.
- `inventory/`: the backpack, its menu, pickup overflow and death drops.
- `client/`: the tabbed talent screen, the HUD bar, the backpack screen, keys and inventory buttons.

## Plans
- Give Lifesteal, Second Wind, Fire Resistance, Water Breathing, Night Vision, Gliding and Flight Speed their effects.
- A way to reset your talents and spend the points again.

## License
CC0-1.0
