# Changelog

## Unreleased
- **Four talent trees with their own XP.** The talents now sit in four trees, each a tab in the talent screen: Gatherer (mining, farming, hauling), Warrior (weapons), Guardian (health, armor, the elements) and Explorer (moving around). Each tree has its own XP, earned only by its own activity: mining (5 per ore, a tenth of that for any other block a pickaxe mines), harvesting crops and fishing for Gatherer; damage you deal to hostile mobs for Warrior; damage hostile mobs aim at you for Guardian; travelling under your own power and entering new chunks for Explorer. Every tree level is one talent point to spend in that tree. Level n to n + 1 takes `ceil(level_cost_base * level_cost_growth^n)` tree XP, with defaults of 25 and 1.18. Talents no longer cost vanilla XP. The price settings were `cost_base` and `cost_growth`, priced in vanilla XP; an old settings file gets the new prices and loses the old keys.
- Each tree starts from its own root, buyable from the start: Faster Mining, Stronger Weapons, More Health and Faster Running. The weapon specialists fan out from Stronger Weapons, More Luck is Gatherer's capstone, Second Wind is Guardian's and Flight Speed is Explorer's. Lifesteal needs Stronger Weapons at level 5.
- Each tree has an XP talent (Gatherer XP, Warrior XP, Guardian XP, Explorer XP): +10% of that tree's XP per level, 3 levels.
- A thin bar above the vanilla XP bar shows a tree's level and progress. Pin a tree in its tab to keep its bar there; earning another tree's XP shows that tree for 3 seconds. A chat line announces every new tree level.
- Keys: **N** opens the talent tree (was K) and **Shift + the inventory key** opens the backpack (was J).
- The extra inventory is now the **Backpack**: 4 levels, one row of 9 slots each (36 at the top). The setting `extra_inventory_pickup` is now `backpack_pickup`.
- Balance: Unarmed Specialist +1.5 punch damage at the top (was +5, which beat a netherite sword); the other specialists reach the same +30% in 3 levels instead of 5; More Health +5 hearts (was +10); More Luck and Careful Hands stop at the vanilla maximum (Fortune III, Unbreaking III); Careful Hands now follows `enchantment_stacking` like the other enchantment talents; Bountiful Harvest +3 copies (was +5); Reach adds +0.5 for mobs (was +1.5) and keeps +1.5 for blocks; Shield Mastery reaches its top in 3 levels; Step Assist is one toggleable level that climbs full blocks.
- **Quarrying**, a new Gatherer talent (5 levels, after Reach): sneak (hold **Shift**) while you break an ore to mine the connected ore of the same kind with it. Level 1 reaches a 3×3×3 cube around the ore, each level one block wider, and level 5 the whole vein. Each block wears the tool and pays Gatherer XP as if mined by hand, it stops with 1 durability left so the tool never breaks, and every drop lands in front of you. Gatherer now tops out at level 32.
- New talents that work: Step Assist, Reach and Swim Speed (movement), Bountiful Harvest, Fast Growth and Careful Hands (farming and gear). Lifesteal, Second Wind, Fire Resistance, Water Breathing, Night Vision, Gliding and Flight Speed are in the trees but have no effect yet.
- Toggleable talents: nine talents can be switched off and on without losing the level, by right-clicking the node or with `/talents toggle <players> <talent> [on|off]`.
- `/talents xp <players> <tree> add|set <amount>` gives or sets tree XP; `list`, `max` and `reset` cover tree XP too.
- Removed: the Experience Converter block and its `converter_values` setting, XP Multiplier, and the Mining, Running, Jumping and Punching XP talents (the tree meters do their job).
- **Talents are reset** when you update: they were bought with vanilla XP, and the trees start from scratch. Vanilla XP is left alone. Items in the old extra inventory move into your inventory, or drop at your feet, on your first join.

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
