# Changelog

## 0.2.0 (unreleased)
Everything here is compared with 0.1.0. **Talents are reset when you update** (see the last item).

- **Four talent trees with their own XP.** The four branches are now trees, each a tab in the talent screen: Gatherer (mining, farming, hauling), Warrior (weapons), Guardian (health, armor, the elements) and Explorer (moving around). Each tree has its own XP, earned only by its own activity: mining (5 per ore, a tenth of that for any other block a pickaxe mines, at most one such block every half second), harvesting fully grown crops and fishing for Gatherer; damage you deal to hostile mobs for Warrior; damage hostile mobs aim at you for Guardian, counted once per damage cooldown like vanilla damage; travelling under your own power and entering new chunks for Explorer (gliding pays half). Every tree level is one talent point to spend in that tree. Level n to n + 1 takes `ceil(level_cost_base * level_cost_growth^n)` tree XP, with defaults of 25 and 1.18.
- **Talents no longer cost vanilla XP**, and vanilla XP is left alone. The price settings `cost_base` and `cost_growth` priced talents in vanilla XP, so they're replaced by `level_cost_base` and `level_cost_growth`.
- **Tree XP needs an active player:** nobody earns any after 5 minutes without turning the camera, so held keys, fishing farms and auto-clickers earn nothing on their own. Each tree earns full XP up to 3,000 an hour, then a tenth of it until the hour is up.
- Each tree starts from its own root, buyable from the start: Faster Mining, Stronger Weapons, More Health and Faster Running. Most talents need one level in the talent above them; More Luck needs Reach and Fast Growth, Second Wind needs Block Chance and Shield Mastery, and Lifesteal needs Stronger Weapons at level 5.
- A thin bar above the vanilla XP bar shows a tree's level and progress. Pin a tree in its tab to keep its bar there (`pinned_tree` in `config/imsotalented-client.properties`); earning another tree's XP shows that tree for 3 seconds. The bar isn't shown in creative or spectator.
- A new tree level plays a sound and posts a chat line with the points to spend there; at the top level the line says the tree earns no more XP.
- Keys: **N** opens the talent tree (was K) and **Shift + the inventory key** opens the backpack (was J). The talent screen works with the keyboard and the narrator.
- New talents that work:
  - An XP talent in each tree (Gatherer XP, Warrior XP, Guardian XP, Explorer XP): +10% of that tree's XP per level, 3 levels. They replace XP Multiplier.
  - **Quarrying** (Gatherer, 5 levels, after Reach): sneak (hold **Shift**) while you break an ore to mine the connected ore of the same kind with it. Level 1 reaches a 3×3×3 cube around the ore, each level one block wider, and level 5 the whole vein, at most 256 blocks. Each block wears the tool and pays Gatherer XP as if mined by hand. It stops with 1 durability left so the tool never breaks, and says why when it stops early. It skips blocks you aren't allowed to break (spawn protection, other mods' claims) and blocks in unloaded chunks, and every drop gathers at your feet.
  - **Bountiful Harvest** (Gatherer, 3 levels): +1 copy of the crop's main item per level from fully grown wheat, carrots, potatoes, beetroots, nether wart, sweet berries (picked by right-click too) and cocoa. **Fast Growth** (3 levels): those seven crops grow up to 2.5× faster within 16 blocks of you.
  - **Reach** (Gatherer, 3 levels): +1.5 blocks of block reach and +0.5 for mobs. **Careful Hands** (Gatherer, 3 levels): gear works as if it had Unbreaking III, combined with the gear's own like the other enchantment talents.
  - **Step Assist** (Explorer, 1 level): walk up full blocks without jumping. **Swim Speed** (Explorer, 3 levels): +30% swim speed.
- Coming later: Lifesteal, Second Wind, Fire Resistance, Water Breathing, Night Vision, Gliding and Flight Speed have their place and their numbers in the trees, marked **SOON**, but can't be bought or toggled until they work.
- **Toggles:** Faster Running, Higher Jumping, Safe Falling, Step Assist and Swim Speed can be switched off and on without losing the level, in every game mode, by right-clicking the node or with `/talents toggle <players> <talent> [on|off]`.
- Balance of 0.1.0's talents: Unarmed Specialist +1.5 punch damage at the top (was +5, which beat a netherite sword); the other specialists reach the same +30% in 3 levels instead of 5; More Health +5 hearts (was +10); More Luck stops at the vanilla maximum (Fortune, Looting and Luck of the Sea III); Shield Mastery reaches its top in 3 levels.
- The extra inventory is now the **Backpack**: 4 levels, one row of 9 slots each (36 at the top). Shift-click moves stacks between it and your inventory. When it loses slots, a chat line tells you how many items moved and how many dropped. The setting `extra_inventory_pickup` is now `backpack_pickup`, and an old file's value carries over.
- `/talents xp <players> <tree> add|set <amount>` gives or sets tree XP; `list`, `max` and `reset` cover tree XP too. `max` also switches every talent on, and `reset` also forgets the chunks Explorer has paid for, the XP not yet counted and the hourly allowance. Every reply names the player when there's one and reports only what changed.
- On a server without the mod, or with another version of it, the talent screen and a message say so instead of looking live, and the HUD bar stays hidden.
- Removed: the Experience Converter block and its `converter_values` setting, and XP Multiplier.
- **Talents are reset** when you update: they were bought with vanilla XP, and the trees start from scratch. Vanilla XP is left alone. Items in the old extra inventory move into your inventory, or drop at your feet, on your first join, and a chat line tells you how many.

## 0.1.0
- First version, for Minecraft 26.3 (Fabric).
- A talent tree (K, or the button above your inventory) with 18 talents in four branches, bought with XP points. Every point you buy makes the next one cost more.
  - **Utility:** Faster Mining (works like Efficiency), More Luck (Fortune, Looting and Luck of the Sea), XP Multiplier, Extra Inventory.
  - **Weapons:** Stronger Weapons, and specialists for unarmed, sword, bow, trident, mace and spear.
  - **Defense:** More Health, Stronger Armor, Shield Mastery, Block Chance.
  - **Passive:** Faster Running, Higher Jumping, Safe Falling.
- An extra inventory of up to 36 slots (J, or the chest button above your inventory). Items you pick up that don't fit your inventory go there.
- The Experience Converter block trades diamonds and netherite for XP.
- `/talents list|set|max|reset` for operators.
- Settings in `config/imsotalented.properties`: talent prices, how talents combine with enchantments, what the converter takes, and extra inventory pickup.
