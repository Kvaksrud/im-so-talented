package no.kvaksrud.imsotalented.talent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Every talent, declared tree by tree ({@link TalentTree}) and within a tree in the order they unlock, so every prereq
 * is declared before the talent that lists it. The id is what's saved with the player, so never rename one. The icon
 * is an item id, resolved on the client only, so this enum loads without a running game (the unit tests use it). What
 * each level does is in {@link TalentValues}.
 *
 * <p>{@code row} and {@code column} place the talent in its tree's tab: row 0 is the root, and a column may be a half
 * step (1.5) to sit between two others. The tab centers the tree on the span of its columns.
 *
 * <p>{@code toggleable} marks the talents the player can switch off without losing the level; the mechanism is
 * {@link TalentData#withDisabled} plus the right-click toggle in the tree and {@code /talents toggle}
 * ({@link Talents#toggle}).
 */
public enum Talent {
	// Gatherer: mining, farming and hauling
	FASTER_MINING("faster_mining", TalentTree.GATHERER, 5, new TalentPrereq[0], "minecraft:diamond_pickaxe", false, 0, 1.5),
	GATHERER_XP("gatherer_xp", TalentTree.GATHERER, 3, prereqs(FASTER_MINING), "minecraft:experience_bottle", false, 1, 0),
	REACH("reach", TalentTree.GATHERER, 3, prereqs(FASTER_MINING), "minecraft:spyglass", false, 1, 1),
	CAREFUL_HANDS("careful_hands", TalentTree.GATHERER, 3, prereqs(FASTER_MINING), "minecraft:iron_pickaxe", false, 1, 2),
	BOUNTIFUL_HARVEST("bountiful_harvest", TalentTree.GATHERER, 3, prereqs(FASTER_MINING), "minecraft:wheat", false, 1, 3),
	BACKPACK("backpack", TalentTree.GATHERER, 4, prereqs(CAREFUL_HANDS), "minecraft:bundle", false, 2, 2),
	FAST_GROWTH("fast_growth", TalentTree.GATHERER, 3, prereqs(BOUNTIFUL_HARVEST), "minecraft:bone_meal", false, 2, 3),
	MORE_LUCK("more_luck", TalentTree.GATHERER, 3, prereqs(REACH, FAST_GROWTH), "minecraft:rabbit_foot", false, 3, 1.5),

	// Warrior: damage with the weapons you use
	STRONGER_WEAPONS("stronger_weapons", TalentTree.WARRIOR, 5, new TalentPrereq[0], "minecraft:iron_sword", false, 0, 3.5),
	WARRIOR_XP("warrior_xp", TalentTree.WARRIOR, 3, prereqs(STRONGER_WEAPONS), "minecraft:experience_bottle", false, 1, 0),
	UNARMED_SPECIALIST("unarmed_specialist", TalentTree.WARRIOR, 3, prereqs(STRONGER_WEAPONS), "minecraft:leather", false, 1, 1),
	SWORD_SPECIALIST("sword_specialist", TalentTree.WARRIOR, 3, prereqs(STRONGER_WEAPONS), "minecraft:diamond_sword", false, 1, 2),
	SPEAR_SPECIALIST("spear_specialist", TalentTree.WARRIOR, 3, prereqs(STRONGER_WEAPONS), "minecraft:iron_spear", false, 1, 3),
	MACE_SPECIALIST("mace_specialist", TalentTree.WARRIOR, 3, prereqs(STRONGER_WEAPONS), "minecraft:mace", false, 1, 4),
	BOW_SPECIALIST("bow_specialist", TalentTree.WARRIOR, 3, prereqs(STRONGER_WEAPONS), "minecraft:bow", false, 1, 5),
	TRIDENT_SPECIALIST("trident_specialist", TalentTree.WARRIOR, 3, prereqs(STRONGER_WEAPONS), "minecraft:trident", false, 1, 6),
	LIFESTEAL("lifesteal", TalentTree.WARRIOR, 3, needs(STRONGER_WEAPONS, 5), "minecraft:blaze_rod", false, 2, 3.5),

	// Guardian: health, armor and the elements
	MORE_HEALTH("more_health", TalentTree.GUARDIAN, 5, new TalentPrereq[0], "minecraft:golden_apple", false, 0, 2),
	GUARDIAN_XP("guardian_xp", TalentTree.GUARDIAN, 3, prereqs(MORE_HEALTH), "minecraft:experience_bottle", false, 1, 0),
	STRONGER_ARMOR("stronger_armor", TalentTree.GUARDIAN, 5, prereqs(MORE_HEALTH), "minecraft:iron_chestplate", false, 1, 1),
	SHIELD_MASTERY("shield_mastery", TalentTree.GUARDIAN, 3, prereqs(MORE_HEALTH), "minecraft:shield", false, 1, 2),
	FIRE_RESISTANCE("fire_resistance", TalentTree.GUARDIAN, 3, prereqs(MORE_HEALTH), "minecraft:magma_cream", true, 1, 3.5),
	BLOCK_CHANCE("block_chance", TalentTree.GUARDIAN, 5, prereqs(STRONGER_ARMOR), "minecraft:shulker_shell", false, 2, 1),
	WATER_BREATHING("water_breathing", TalentTree.GUARDIAN, 3, prereqs(FIRE_RESISTANCE), "minecraft:nautilus_shell", true, 2, 3),
	NIGHT_VISION("night_vision", TalentTree.GUARDIAN, 1, prereqs(FIRE_RESISTANCE), "minecraft:golden_carrot", true, 2, 4),
	SECOND_WIND("second_wind", TalentTree.GUARDIAN, 3, prereqs(BLOCK_CHANCE, SHIELD_MASTERY), "minecraft:glistering_melon_slice", false, 3, 1.5),

	// Explorer: moving around the world
	FASTER_RUNNING("faster_running", TalentTree.EXPLORER, 5, new TalentPrereq[0], "minecraft:sugar", true, 0, 1.5),
	HIGHER_JUMPING("higher_jumping", TalentTree.EXPLORER, 5, prereqs(FASTER_RUNNING), "minecraft:slime_ball", true, 1, 0),
	STEP_ASSIST("step_assist", TalentTree.EXPLORER, 1, prereqs(FASTER_RUNNING), "minecraft:oak_stairs", true, 1, 1),
	SWIM_SPEED("swim_speed", TalentTree.EXPLORER, 3, prereqs(FASTER_RUNNING), "minecraft:heart_of_the_sea", true, 1, 2),
	EXPLORER_XP("explorer_xp", TalentTree.EXPLORER, 3, prereqs(FASTER_RUNNING), "minecraft:experience_bottle", false, 1, 3),
	SAFE_FALLING("safe_falling", TalentTree.EXPLORER, 5, prereqs(HIGHER_JUMPING), "minecraft:feather", true, 2, 0),
	GLIDING("gliding", TalentTree.EXPLORER, 3, prereqs(SAFE_FALLING), "minecraft:phantom_membrane", true, 3, 0),
	FLIGHT_SPEED("flight_speed", TalentTree.EXPLORER, 5, prereqs(GLIDING), "minecraft:elytra", false, 4, 0);

	/** Every prereq in this list is required at level 1. */
	private static TalentPrereq[] prereqs(Talent... talents) {
		TalentPrereq[] out = new TalentPrereq[talents.length];
		for (int i = 0; i < talents.length; i++) {
			out[i] = new TalentPrereq(talents[i], 1);
		}
		return out;
	}

	/** One prereq at a higher level than 1, like Lifesteal's Stronger Weapons 5. */
	private static TalentPrereq[] needs(Talent talent, int minLevel) {
		return new TalentPrereq[] {new TalentPrereq(talent, minLevel)};
	}

	private final String id;
	private final TalentTree tree;
	private final int maxLevel;
	private final TalentPrereq[] prereqs;
	private final String icon;
	private final boolean toggleable;
	private final int row;
	private final double column;

	Talent(String id, TalentTree tree, int maxLevel, TalentPrereq[] prereqs, String icon, boolean toggleable, int row, double column) {
		this.id = id;
		this.tree = tree;
		this.maxLevel = maxLevel;
		this.prereqs = prereqs;
		this.icon = icon;
		this.toggleable = toggleable;
		this.row = row;
		this.column = column;
	}

	public String id() {
		return this.id;
	}

	public TalentTree tree() {
		return this.tree;
	}

	public int maxLevel() {
		return this.maxLevel;
	}

	/** The levels this talent needs in other talents before it can be bought; empty means unlocked from the start. */
	public TalentPrereq[] prereqs() {
		return this.prereqs;
	}

	/** Item id shown as the talent's icon, like {@code minecraft:diamond_pickaxe}. */
	public String icon() {
		return this.icon;
	}

	/** Whether the level can be toggled off without losing it; only these talents get the right-click control and the command. */
	public boolean toggleable() {
		return this.toggleable;
	}

	/** The talent's row in its tree's tab: 0 for the root, one more per step down. */
	public int row() {
		return this.row;
	}

	/** The talent's column in its tree's tab, from 0 at the left; a half step sits between two columns. */
	public double column() {
		return this.column;
	}

	public String translationKey() {
		return "talent.imsotalented." + this.id;
	}

	public String descriptionKey() {
		return this.translationKey() + ".desc";
	}

	public String effectKey() {
		return this.translationKey() + ".effect";
	}

	/** The talents that list this one as a prereq, in declaration order: a talent is a child of every prereq it needs. */
	public List<Talent> children() {
		List<Talent> children = new ArrayList<>();

		for (Talent talent : values()) {
			for (TalentPrereq prereq : talent.prereqs) {
				if (prereq.talent() == this) {
					children.add(talent);
					break;
				}
			}
		}

		return Collections.unmodifiableList(children);
	}

	public static Optional<Talent> byId(String id) {
		for (Talent talent : values()) {
			if (talent.id.equals(id)) {
				return Optional.of(talent);
			}
		}

		return Optional.empty();
	}

	/** Every level of every talent: what a fully talented player has bought. */
	public static int totalLevels() {
		int total = 0;

		for (Talent talent : values()) {
			total += talent.maxLevel;
		}

		return total;
	}
}
