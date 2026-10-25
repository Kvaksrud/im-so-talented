# I'm So Talented

A Fabric mod for Minecraft 26.3 with four talent trees. Each tree has its own XP, and you earn it by doing that tree's thing: mine and farm for Gatherer, fight for Warrior, take hits for Guardian, travel for Explorer. Talents make you mine faster, quarry whole ore veins, find more loot, hit harder, take less damage, carry more and move better.

## How it works
- Press **N** (or the XP-bottle button above your inventory) to open the talent tree. Each tree has its own tab. The screen works with the keyboard and the narrator too.
- Every tree has an XP meter and levels. When the meter fills, the tree goes up a level and you get one **talent point** to spend in that tree only. Click a talent to spend a point on its next level. Buying spends the point, never the XP, so the bar never drops.
- All four meters fill from the start, even before you buy a tree's first talent. A tree stops levelling once it has a point for every talent level in it. Points for the talents that aren't in the game yet wait until they are.
- A new tree level plays a sound and posts a chat line telling you how many points you have to spend there. At the tree's top level the line says it earns no more XP.
- Vanilla XP has nothing to do with talents: it stays for enchanting, anvils and Mending.
- In creative, talents are free. In creative and spectator, tree XP isn't earned.
- Talents, tree XP and unspent points are saved with your player and kept when you die.
- A talent shows what it needs when it's locked. Most need one level in the talent above them. More Luck and Second Wind need both talents above them, and Lifesteal needs Stronger Weapons at level 5.

**Toggling.** Five talents are *toggleable*: Higher Jumping, Faster Running, Step Assist, Safe Falling and Swim Speed. Gliding, Night Vision, Fire Resistance and Water Breathing join them once they're in the game. **Right-click the node** to switch a bought talent off (and back on) without losing it, or use [`/talents toggle`](#commands). A toggled-off talent shows dimmed with an **OFF** badge and has no effect until you toggle it back on. Toggling costs nothing, works in every game mode (creative too) and is saved with your player.

## Earning tree XP

| Tree | Earns from | XP | Doesn't count |
|---|---|---|---|
| Gatherer | Mining with a pickaxe (ores pay the most), harvesting fully grown crops (the [seven farm crops](#farming), picking sweet berries by right-click included), catching fish | 5 per ore; 0.5 per other pickaxe block (stone, deepslate, netherrack, an ore with Silk Touch), at most one such block every half second (10 ticks); 1 per crop; 5 per catch | Blocks for other tools (sand, dirt, gravel, logs), the wrong tool or a bare hand, crops that aren't fully grown |
| Warrior | Damage you deal to hostile mobs, melee or ranged | 1 per point of damage | Overkill past the mob's remaining health, players, passive mobs, thorns and fire |
| Guardian | Damage hostile mobs aim at you, counted before armor, shield and Block Chance, as long as you survive the hit. A hit counts once per damage cooldown, as vanilla damage does | 2 per point of damage | Falls, fire, drowning and other damage that isn't from a mob, hits vanilla ignores during the damage cooldown, the hit that kills you |
| Explorer | Moving under your own power while holding a movement key (walking, sprinting, wading, swimming; elytra gliding at half rate), and every chunk you enter for the first time, however you get there | 1 per 25 blocks, 1 per new chunk (half while gliding) | Riding, boats, minecarts, floating in water, being pushed or knocked back |

Each tree's XP talent (Gatherer XP, Warrior XP, Guardian XP, Explorer XP) adds 10% per level to that tree's XP. The numbers aim at roughly 1,500 XP for an hour of each activity.

**Limits.** Tree XP needs someone playing: after 5 minutes without turning the camera you earn none until you look around again, so held keys, fishing farms and auto-clickers earn nothing on their own. Each tree earns full XP up to 3,000 an hour, then a tenth of it until the hour is up (the hour starts with the first XP that tree earns), so a farm can't max a tree in an afternoon.

**Prices.** Going from level n to level n + 1 takes `ceil(25 × 1.18^n)` tree XP, the same in every tree: level 1 takes 25, level 10 takes 111 and level 20 takes 581. Maxing a tree takes 16,700 to 27,600 tree XP, roughly 11 to 18 hours of its activity (Gatherer has the most levels). Both numbers are [settings](#settings).

**On the HUD.** One thin bar sits just above the vanilla XP bar, in the tree's color. Pin a tree with the pin button in its tab and its bar stays there. When you earn XP in another tree, that tree borrows the slot for 3 seconds. With nothing pinned the bar only shows for 3 seconds after you earn XP. Like the vanilla XP bar, it isn't shown in creative or spectator. The pin is a [setting on your own computer](#settings).

## Talents

| Tree | Talent | Levels | At the top level |
|---|---|---|---|
| Gatherer | Faster Mining | 5 | Mines like Efficiency V |
| | Gatherer XP | 3 | +30% Gatherer XP |
| | Reach | 3 | +1.5 blocks of block reach (6.0) and +0.5 for mobs (3.5) |
| | Careful Hands | 3 | Gear works as if it had Unbreaking III |
| | Bountiful Harvest | 3 | +3 copies of the crop's main item per harvest of a fully grown crop (wheat 1 → 4) |
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
| | Shield Mastery | 3 | The shield blocks as soon as it's raised, and an axe hit disables it for 75% less time |
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

The seven talents marked "not in the game yet" have their place and their numbers in the tree, with a **SOON** badge, but they can't be bought or toggled until they do something.

**Talents and enchantments.** Faster Mining, More Luck and Careful Hands work like the matching enchantments, and reach the vanilla maximum: Efficiency V, Fortune III, Unbreaking III. By default the gear you hold keeps its own enchantment level when that's higher, so a Fortune I pickaxe with More Luck III digs like Fortune III, and a Fortune III pickaxe with More Luck II stays Fortune III. Set `enchantment_stacking=add` to add them together instead: then Fortune III with More Luck III digs like Fortune VI.

**Weapon damage.** Stronger Weapons and the matching specialist add up: a sword at the top of both does 50% more damage. They apply to real attacks only, meaning melee, sweeps, spear jabs and charges, mace smashes, arrows and tridents. Thorns, fire and explosions don't get the bonus.

### Farming
Bountiful Harvest adds one copy of the crop's main item per level to your harvests of the classic seven: wheat, carrots, potatoes, beetroots, nether wart, sweet berry bushes and cocoa. Picking sweet berries by right-click counts as a harvest. Only fully grown crops get the bonus (a young carrot drops its one carrot and no more), and only the main item's count grows: seeds and side drops are untouched. Explosions, animals and players without the talent get nothing. Fast Growth makes those same seven crops grow faster: the per-tick growth roll is multiplied by 1.0 + 0.5 per level, using the highest Fast Growth level among players within 16 blocks horizontally of the crop (any height). With no player in reach the crops grow exactly as in vanilla.

## Quarrying
- **Sneak** (hold **Shift**) while you break an ore, and the ore of the same kind touching it breaks too, corners included. Iron ore and deepslate iron ore count as one kind. Stone around the vein stays.
- How far it reaches: a 3×3×3 cube around the ore at level 1, 4×4×4 at level 2, then 5×5×5 and 6×6×6, and the whole vein at level 5. An even-sized cube puts its extra layer on the far side from you. One break takes at most 256 blocks.
- Every block counts as if you mined it yourself: the same drops (Fortune and More Luck too), 5 Gatherer XP per ore, and 1 durability per block (Unbreaking and Careful Hands still save some).
- It skips blocks you aren't allowed to break, like spawn protection or another mod's land claim, and blocks in chunks that aren't loaded.
- It only starts with a tool that gets the ore's drops, and it stops with 1 durability left, so a pickaxe never breaks from quarrying. It also stops if you switch items. When it stops early, for the tool or at 256 blocks, the action bar says why.
- Everything the vein drops, items and XP, gathers at your feet.
- Without sneaking you mine one block as usual.

## Backpack
- Press **Shift** and your inventory key (**Shift + E** by default), or the bundle button above your inventory, to open it. It also opens from inside your inventory. Locked slots show a padlock.
- Without the Backpack talent, Shift + E just opens your inventory. Controls also has an **Open Backpack** key of its own, unbound by default.
- Each level of the Backpack talent unlocks one row of 9 slots, up to 36.
- Shift-click moves a stack between the backpack and your inventory.
- Items you pick up that don't fit your inventory go into your backpack. You can turn that off with `backpack_pickup`.
- When you die, it drops like your normal inventory. With the `keep_inventory` game rule on, it stays.
- If the backpack loses slots (an operator lowers the talent, or resets it), items in slots that are now locked move to free slots, or to your inventory, or drop at your feet, and a chat line tells you how many moved and how many dropped.

## Commands
`/talents` needs operator rights (permission level 2). `<players>` is a player name or a selector like `@a`. The reply names the player when there's one, and `toggle` and `xp add` count only the players something happened to.
- `/talents list <player>`: each tree's level, XP and points to spend, and their talent levels.
- `/talents set <players> <talent> <level>`: sets the talent to that level without spending points, capped at its top level. Level 0 removes it.
- `/talents xp <players> <tree> add <amount>`: gives tree XP, with the usual level-up lines. A tree stops at its top level, and the reply says which players' trees were already there.
- `/talents xp <players> <tree> set <amount>`: sets the tree's XP outright, at most what its top level takes.
- `/talents max <players>`: every talent at its top level and switched on, and every tree's XP at its top level.
- `/talents reset <players>`: back to a new player: no talents, no toggles and no tree XP. It also forgets the chunks Explorer has paid for, the XP not yet counted and the hourly allowance, so everything can be earned again.
- `/talents toggle <players> <talent> [on|off]`: switches a talent on or off, or flips each player's own state when `on`/`off` is left out. It works on toggleable talents that are in the game and that the player owns; the reply names the players who don't own it.

## Settings
The first start writes `config/imsotalented.properties`. Only the server's copy counts (in singleplayer, your own). The server sends players the prices and the stacking rule when they join. Changes take effect the next time a world is opened or the server starts. Values that don't parse fall back to the default, and out-of-range values are clamped. A key that isn't a setting is ignored with a warning in the log, and left out when the file is rewritten.

| Setting | Default | What it does |
|---|---|---|
| `level_cost_base` | `25` | Tree XP the first level of a tree takes. |
| `level_cost_growth` | `1.18` | Each next level takes this many times the XP of the one before: level n to n + 1 takes `ceil(level_cost_base * level_cost_growth^n)`. `1.0` keeps it flat; allowed is 1.0 to 2.0. |
| `enchantment_stacking` | `highest` | `highest`: the higher of the talent and your gear's enchantment counts. This is the "By default, gear keeps its own…" in the talent descriptions. `add`: both together, so gear never just keeps its own. |
| `backpack_pickup` | `true` | Whether pickups that don't fit your inventory go into your backpack. |

Your own computer has `config/imsotalented-client.properties`, with one setting: `pinned_tree=gatherer|warrior|guardian|explorer|none` (default `none`), the tree whose XP bar always shows. The pin button in a tree's tab writes it for you.

## Updating from 0.1.0
Talents are reset: they used to be bought with vanilla XP, and the four trees start from scratch. Your vanilla XP is left alone. Items in the old extra inventory aren't lost: on your first join its slots are locked, so they move into your inventory or drop at your feet, and a chat line tells you how many. The Experience Converter is gone, along with its recipe. The settings file gets the new prices: the old `cost_base` and `cost_growth` priced talents in vanilla XP, so they're replaced by `level_cost_base` and `level_cost_growth` at their defaults, and the log says so. `extra_inventory_pickup` carries over to `backpack_pickup`.

## Requirements
| Minecraft | Loader | Needs | Java |
|---|---|---|---|
| 26.3 | Fabric | Fabric Loader 0.19.5+, Fabric API 0.162.0+26.3+ | 25 |

Install the same version on the server and on every player's client. On a server without the mod, or with another version of it, the talent screen and a message say so, the HUD bar stays hidden, and nothing can be earned or bought there.

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
- the saved talent data, including reading a damaged save leniently;
- the settings file;
- what earns tree XP and what doesn't, the AFK rule and the hourly cap;
- the weapon, mining, shield, harvest, growth, unbreaking and swim speed math;
- quarrying's reach per level, the vein search and where it stops for the tool;
- the visited-chunk storage that Explorer pays new chunks from;
- the backpack's storage.

**The client game test** plays through the main flows in survival and takes screenshots into `build/run/clientGameTest/screenshots/` (prefixed `ist_`).

**Art:** `python3 tools/gen_textures.py` redraws the mod icon (the XP root branching to the four trees' colors) using only Python's standard library. It also writes the 512 x 512 project logo for CurseForge and Modrinth, `build/texture-preview/logo-512.png`, and an enlarged preview.

**CI and releases.** Once the project has a GitHub remote, `.github/workflows/build.yml` validates the Gradle wrapper, builds every push and pull request from a clean checkout and runs the unit tests. On `main`, the first push of a new `mod_version` publishes a GitHub release with the jar, as soon as `CHANGELOG.md` has a `## <mod_version>` section that isn't marked `(unreleased)`; that section becomes the release notes. To release: finish the version's CHANGELOG section, drop the `(unreleased)`, bump `mod_version` in `gradle.properties` if it isn't already, and push.

### Code map
Under `src/main/java/no/kvaksrud/imsotalented/`:
- `ImSoTalented`: the entry point. It registers the saved talent data and the tree XP bookkeeping on players, the network payloads and what the server does with them, the per-tick XP commit, the command and the settings. `ImSoTalentedConfig` reads and writes the settings file.
- `talent/`: the talent list and the four trees, what each level does (`TalentValues`), saved player data with tree XP (`TalentData`), tree level prices and enchantment stacking (`TalentRules`), buying, toggling and earning (`Talents`), the AFK check and hourly allowance (`TreeXpLedger`) and attribute talents (`TalentAttributes`).
- `effect/` and `mixin/`: talents that change game code, like mining speed, quarrying, loot, weapon damage, block chance and shields, and the hooks that earn tree XP.
- `inventory/`: the backpack, its menu, pickup overflow and death drops.
- `command/`: `/talents`.
- `network/`: the payloads between client and server: buying and toggling a talent, and the server's rules with the protocol version that tells a client it runs another version.

Under `src/client/java/no/kvaksrud/imsotalented/client/`: the tabbed talent screen, the HUD bar, the backpack screen, keys, inventory buttons and the client settings file (`ImSoTalentedClientConfig`).

## Plans
- Give Lifesteal, Second Wind, Fire Resistance, Water Breathing, Night Vision, Gliding and Flight Speed their effects.
- A way to reset your talents and spend the points again.

## License
CC0-1.0
