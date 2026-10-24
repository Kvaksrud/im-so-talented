package no.kvaksrud.imsotalented.talent;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What each talent level does, and what each activity earns in tree XP, in one place: the effects, the tree screen and
 * the unit tests all read these. Percentages are fractions here (0.2 is 20%).
 *
 * <p>The rule of thumb: a talent that copies an enchantment reaches the vanilla maximum (Efficiency V, Fortune III,
 * Unbreaking III), and only {@code enchantment_stacking=add} goes past it.
 */
public final class TalentValues {
	/** Backpack slots at the top level: a second full inventory (27 storage slots plus a hotbar's 9). */
	public static final int MAX_BACKPACK_SLOTS = 36;

	/** Each level of a tree's XP talent adds this share of the tree XP its activity earns. */
	public static final double TREE_XP_BONUS_PER_LEVEL = 0.1;

	public static final double WEAPON_BONUS_PER_LEVEL = 0.04;
	public static final double SPECIALIST_BONUS_PER_LEVEL = 0.10;
	public static final double UNARMED_DAMAGE_PER_LEVEL = 0.5;
	public static final double HEALTH_PER_LEVEL = 2.0;
	public static final double ARMOR_BONUS_PER_LEVEL = 0.05;
	public static final double SHIELD_RAISE_REDUCTION_PER_LEVEL = 1.0 / 3.0;
	public static final double SHIELD_DISABLE_REDUCTION_PER_LEVEL = 0.25;
	public static final double BLOCK_CHANCE_PER_LEVEL = 0.02;
	public static final double SPEED_BONUS_PER_LEVEL = 0.04;
	public static final double JUMP_STRENGTH_PER_LEVEL = 0.03;
	public static final double SAFE_FALL_BLOCKS_PER_LEVEL = 2.0;
	public static final double FALL_DAMAGE_REDUCTION_PER_LEVEL = 0.1;

	/** Added to the step height (vanilla 0.6): the one level climbs full blocks, like a horse. */
	public static final double STEP_BONUS_PER_LEVEL = 0.4;
	/** Added to the block interaction range (vanilla 4.5). */
	public static final double REACH_BONUS_PER_LEVEL = 0.5;
	/** Added to the entity interaction range (vanilla 3.0): +0.5 at the top level, so melee can't outrange a creeper's fuse. */
	public static final double ENTITY_REACH_BONUS_PER_LEVEL = 0.5 / 3.0;
	/** Multiplied into crop growth speed: 1.0 without the talent, +0.5 per level. */
	public static final double GROWTH_MULTIPLIER_PER_LEVEL = 0.5;
	/** Fraction of the damage dealt healed back on the attacker, per level of Lifesteal. */
	public static final double LIFESTEAL_FRACTION_PER_LEVEL = 0.05;
	/** Health points per second regenerated while hurt, per level of Second Wind. */
	public static final double SECOND_WIND_REGEN_PER_LEVEL = 0.15;
	/** Seconds of fire resistance when the player catches fire, per level of Fire Resistance. */
	public static final double FIRE_RESISTANCE_SECONDS_PER_LEVEL = 10.0;
	/** Seconds before Fire Resistance can start again. */
	public static final double FIRE_RESISTANCE_COOLDOWN_SECONDS = 180.0;
	/** Swim speed is multiplied by 1 + this, per level of Swim Speed. */
	public static final double SWIM_SPEED_BONUS_PER_LEVEL = 0.1;
	/** The max fall speed while gliding (blocks/tick) is 1.0 minus this per level. */
	public static final double GLIDE_FALL_REDUCTION_PER_LEVEL = 0.2;
	/** Gliding and flight speed are multiplied by 1 + this, per level of Flight Speed. */
	public static final double FLIGHT_SPEED_MULTIPLIER_PER_LEVEL = 0.05;

	/** Gatherer XP per ore broken with the right tool and without Silk Touch. */
	public static final double GATHERER_XP_PER_ORE = 5.0;
	/**
	 * Gatherer XP per other block a pickaxe is the right tool for (stone, deepslate, netherrack, an ore with Silk Touch):
	 * a tenth of an ore. Blocks for other tools, like sand, dirt and logs, pay nothing.
	 */
	public static final double GATHERER_XP_PER_BLOCK = GATHERER_XP_PER_ORE / 10.0;
	/** Gatherer XP per mature crop harvested (the seven Bountiful Harvest crops). */
	public static final double GATHERER_XP_PER_CROP = 1.0;
	/** Gatherer XP per fish (or other catch) reeled in. */
	public static final double GATHERER_XP_PER_FISH = 5.0;
	/** Warrior XP per point of damage dealt to a hostile mob, up to the health it had left. */
	public static final double WARRIOR_XP_PER_DAMAGE = 1.0;
	/** Guardian XP per point of damage a hostile mob aims at the player, before armor, shield and Block Chance. */
	public static final double GUARDIAN_XP_PER_DAMAGE = 2.0;
	/** Explorer XP per block travelled under the player's own power. */
	public static final double EXPLORER_XP_PER_BLOCK = 1.0 / 25.0;
	/** Explorer XP for each chunk the player enters for the first time. */
	public static final double EXPLORER_XP_PER_NEW_CHUNK = 1.0;
	/** Gliding earns this share of the walking rate per block, since it covers ground much faster. */
	public static final double EXPLORER_GLIDE_FACTOR = 0.5;

	/** Quarrying's cube at level 1 is this many blocks across, one more per level up to the level below the top. */
	public static final int QUARRY_FIRST_CUBE_SIZE = 3;
	/**
	 * The most blocks one Quarrying break takes, the first ore included. The top level takes the whole vein, and this
	 * is far above any vanilla ore blob (coal's reach a few dozen); it keeps a mountain of placed ore from breaking in
	 * one tick.
	 */
	public static final int QUARRY_MAX_BLOCKS = 256;

	/** Vanilla jump strength, used to show how high a jump gets. */
	public static final double BASE_JUMP_STRENGTH = 0.42;

	private TalentValues() {
	}

	/** Tree XP from an activity is multiplied by this: 1.0 without the tree's XP talent, 1.3 at its top level. */
	public static double treeXpMultiplier(int level) {
		return 1.0 + TREE_XP_BONUS_PER_LEVEL * level;
	}

	/** Faster Mining N mines like Efficiency N. */
	public static int efficiencyLevel(int level) {
		return level;
	}

	/** More Luck N works like Fortune, Looting and Luck of the Sea N. */
	public static int luckLevel(int level) {
		return level;
	}

	/**
	 * How many blocks across the cube around the ore is that Quarrying takes the vein from: 3 at level 1 up to 6 at level
	 * 4, 0 without the talent. The top level has no cube ({@link #quarriesWholeVein}).
	 */
	public static int quarryCubeSize(int level) {
		return level <= 0 ? 0 : QUARRY_FIRST_CUBE_SIZE - 1 + Math.min(level, Talent.QUARRYING.maxLevel() - 1);
	}

	/** Whether Quarrying takes the whole vein, as it does at the top level, instead of a cube of it. */
	public static boolean quarriesWholeVein(int level) {
		return level >= Talent.QUARRYING.maxLevel();
	}

	/** Unlocked backpack slots: one full row of 9 per level, 36 at the top. */
	public static int backpackSlots(int level) {
		int max = Talent.BACKPACK.maxLevel();
		return MAX_BACKPACK_SLOTS * Math.clamp(level, 0, max) / max;
	}

	/** Damage bonus for every attack the player makes, from Stronger Weapons. */
	public static double weaponBonus(int level) {
		return WEAPON_BONUS_PER_LEVEL * level;
	}

	/** Damage bonus from a weapon specialist talent, on top of {@link #weaponBonus}, for that weapon only. */
	public static double specialistBonus(int level) {
		return SPECIALIST_BONUS_PER_LEVEL * level;
	}

	/** Flat damage added to a punch with an empty hand. A bare fist does 1 damage in vanilla. */
	public static double unarmedDamage(int level) {
		return UNARMED_DAMAGE_PER_LEVEL * level;
	}

	/** Extra max health points (2 per heart). */
	public static double extraHealth(int level) {
		return HEALTH_PER_LEVEL * level;
	}

	/** Armor and armor toughness are multiplied by 1 + this. */
	public static double armorBonus(int level) {
		return ARMOR_BONUS_PER_LEVEL * level;
	}

	/** How much of the shield's raise delay is skipped: 1.0 means it blocks the moment it's raised. */
	public static double shieldRaiseReduction(int level) {
		return Math.min(1.0, SHIELD_RAISE_REDUCTION_PER_LEVEL * level);
	}

	/** How much shorter the shield stays disabled after an axe hit. */
	public static double shieldDisableReduction(int level) {
		return Math.min(1.0, SHIELD_DISABLE_REDUCTION_PER_LEVEL * level);
	}

	/** Chance to shrug off an attack from a mob or player completely. */
	public static double blockChance(int level) {
		return BLOCK_CHANCE_PER_LEVEL * level;
	}

	/** Movement speed is multiplied by 1 + this. */
	public static double speedBonus(int level) {
		return SPEED_BONUS_PER_LEVEL * level;
	}

	/** Added to jump strength (0.42 in vanilla). */
	public static double jumpStrengthBonus(int level) {
		return JUMP_STRENGTH_PER_LEVEL * level;
	}

	/** Roughly how many blocks high a jump goes, simulating vanilla's per-tick gravity (0.08) and drag (0.98). */
	public static double jumpHeight(double jumpStrength) {
		double height = 0.0;
		double velocity = jumpStrength;

		while (velocity > 0.0) {
			height += velocity;
			velocity = (velocity - 0.08) * 0.98;
		}

		return height;
	}

	/** Extra blocks you can fall before taking damage (3 in vanilla). */
	public static double extraSafeFall(int level) {
		return SAFE_FALL_BLOCKS_PER_LEVEL * level;
	}

	/** Fall damage is multiplied by 1 - this. */
	public static double fallDamageReduction(int level) {
		return Math.min(1.0, FALL_DAMAGE_REDUCTION_PER_LEVEL * level);
	}

	/** Extra step height (blocks climbed without jumping), from Step Assist. */
	public static double stepBonus(int level) {
		return STEP_BONUS_PER_LEVEL * level;
	}

	/** Extra block reach (blocks further a block can be mined or used), from Reach. */
	public static double reachBonus(int level) {
		return REACH_BONUS_PER_LEVEL * level;
	}

	/** Extra entity reach (blocks further a mob can be hit or used), from Reach. */
	public static double entityReachBonus(int level) {
		return ENTITY_REACH_BONUS_PER_LEVEL * level;
	}

	/** Extra crop drops per harvest, from Bountiful Harvest. */
	public static int harvestBonus(int level) {
		return level;
	}

	/** Crop growth is multiplied by this: 1.0 without the talent, 2.5 at the top level. */
	public static double growthMultiplier(int level) {
		return 1.0 + GROWTH_MULTIPLIER_PER_LEVEL * level;
	}

	/** Gear works as if it had Unbreaking at this level, from Careful Hands, combined with the gear's own like the other enchantment talents. */
	public static int unbreakingBonus(int level) {
		return level;
	}

	/** Fraction of the damage dealt healed back on the attacker, from Lifesteal. */
	public static double lifestealFraction(int level) {
		return LIFESTEAL_FRACTION_PER_LEVEL * level;
	}

	/** Health points per second regenerated while hurt, from Second Wind. */
	public static double secondWindRegen(int level) {
		return SECOND_WIND_REGEN_PER_LEVEL * level;
	}

	/** Seconds of fire resistance when the player catches fire, from Fire Resistance. */
	public static double fireResistDuration(int level) {
		return FIRE_RESISTANCE_SECONDS_PER_LEVEL * level;
	}

	/** Water Breathing N works like Respiration N. */
	public static int respirationLevel(int level) {
		return level;
	}

	/** Swim speed is multiplied by 1 + this, from Swim Speed. */
	public static double swimSpeedBonus(int level) {
		return SWIM_SPEED_BONUS_PER_LEVEL * level;
	}

	/** The max fall speed while gliding (blocks/tick): 1.0 vanilla, 0.4 at the top level. */
	public static double glideMaxFall(int level) {
		return 1.0 - GLIDE_FALL_REDUCTION_PER_LEVEL * level;
	}

	/** Gliding and flight speed are multiplied by this: 1.0 without the talent, 1.25 at the top level. */
	public static double flightSpeedMultiplier(int level) {
		return 1.0 + FLIGHT_SPEED_MULTIPLIER_PER_LEVEL * level;
	}

	/**
	 * The translation key of a talent's effect text at this level: its own {@link Talent#effectKey()}, except at a level
	 * that reads differently. Quarrying's top level takes the whole vein instead of a cube.
	 */
	public static String effectKey(Talent talent, int level) {
		return talent == Talent.QUARRYING && quarriesWholeVein(level) ? talent.effectKey() + ".whole_vein" : talent.effectKey();
	}

	/**
	 * The numbers that fill in a talent's effect text ({@link #effectKey}) at this level, already formatted:
	 * percentages as whole percents, blocks with up to one decimal.
	 */
	public static Object[] effectArgs(Talent talent, int level) {
		return switch (talent) {
			case GATHERER_XP, WARRIOR_XP, GUARDIAN_XP, EXPLORER_XP -> new Object[] {percent(treeXpMultiplier(level) - 1.0)};
			case FASTER_MINING -> new Object[] {roman(efficiencyLevel(level))};
			case MORE_LUCK -> new Object[] {roman(luckLevel(level))};
			case BACKPACK -> new Object[] {backpackSlots(level), MAX_BACKPACK_SLOTS};
			case STRONGER_WEAPONS -> new Object[] {percent(weaponBonus(level))};
			case UNARMED_SPECIALIST -> new Object[] {number(unarmedDamage(level))};
			case SWORD_SPECIALIST, BOW_SPECIALIST, TRIDENT_SPECIALIST, MACE_SPECIALIST, SPEAR_SPECIALIST -> new Object[] {percent(specialistBonus(level))};
			case MORE_HEALTH -> new Object[] {number(extraHealth(level) / 2.0)};
			case STRONGER_ARMOR -> new Object[] {percent(armorBonus(level))};
			case SHIELD_MASTERY -> new Object[] {percent(shieldRaiseReduction(level)), percent(shieldDisableReduction(level))};
			case BLOCK_CHANCE -> new Object[] {percent(blockChance(level))};
			case FASTER_RUNNING -> new Object[] {percent(speedBonus(level))};
			case HIGHER_JUMPING -> new Object[] {number(jumpHeight(BASE_JUMP_STRENGTH + jumpStrengthBonus(level)))};
			case SAFE_FALLING -> new Object[] {number(extraSafeFall(level)), percent(fallDamageReduction(level))};
			case STEP_ASSIST -> new Object[] {number(0.6 + stepBonus(level))};
			case REACH -> new Object[] {number(reachBonus(level)), number(entityReachBonus(level))};
			case BOUNTIFUL_HARVEST -> new Object[] {harvestBonus(level)};
			case FAST_GROWTH -> new Object[] {number(growthMultiplier(level))};
			case CAREFUL_HANDS -> new Object[] {roman(unbreakingBonus(level))};
			case QUARRYING -> quarriesWholeVein(level) ? new Object[0] : new Object[] {cube(quarryCubeSize(level))};
			case LIFESTEAL -> new Object[] {percent(lifestealFraction(level))};
			case SECOND_WIND -> new Object[] {number(secondWindRegen(level), 2)};
			case FIRE_RESISTANCE -> new Object[] {number(fireResistDuration(level)), number(FIRE_RESISTANCE_COOLDOWN_SECONDS / 60.0)};
			case WATER_BREATHING -> new Object[] {roman(respirationLevel(level))};
			case NIGHT_VISION -> new Object[0];
			case SWIM_SPEED -> new Object[] {percent(swimSpeedBonus(level))};
			case GLIDING -> new Object[] {number(glideMaxFall(level))};
			case FLIGHT_SPEED -> new Object[] {percent(flightSpeedMultiplier(level) - 1.0)};
		};
	}

	/** 3 -> "3×3×3". */
	public static String cube(int size) {
		return size + "×" + size + "×" + size;
	}

	/** 0.2 -> "20". */
	public static String percent(double fraction) {
		return number(fraction * 100.0);
	}

	/** Up to one decimal, without trailing zeros: 2, 2.5. */
	public static String number(double value) {
		return number(value, 1);
	}

	/** Like {@link #number}, with more decimals, for small numbers like 0.05 that one decimal rounds away. */
	public static String number(double value, int decimals) {
		return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	/** Roman numerals like enchantment levels: 0 is "0", then I, II, III, IV, V... */
	public static String roman(int value) {
		if (value <= 0) {
			return "0";
		}

		int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder text = new StringBuilder();
		int rest = value;

		for (int i = 0; i < values.length; i++) {
			while (rest >= values[i]) {
				text.append(symbols[i]);
				rest -= values[i];
			}
		}

		return text.toString();
	}
}
