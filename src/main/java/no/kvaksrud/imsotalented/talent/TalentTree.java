package no.kvaksrud.imsotalented.talent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One of the four talent trees, each a tab in the talent screen with its own XP meter. A tree's XP only comes from its
 * own activity (mining for Gatherer, fighting for Warrior, taking hits for Guardian, travelling for Explorer), and
 * every level of it gives one talent point to spend in that tree only. The id is saved with the player's XP, so never
 * rename one.
 */
public enum TalentTree {
	GATHERER("gatherer", 0xFFE0A440),
	WARRIOR("warrior", 0xFFEE7364),
	GUARDIAN("guardian", 0xFF6EA6EA),
	EXPLORER("explorer", 0xFF4CC4C0);

	private final String id;
	private final int color;
	/**
	 * Filled on first use, after both enums are initialised ({@link Talent} refers to this enum in its constants);
	 * volatile because the client and the integrated server both read them.
	 */
	private volatile List<Talent> talents;
	private volatile int maxLevel = -1;

	TalentTree(String id, int color) {
		this.id = id;
		this.color = color;
	}

	public String id() {
		return this.id;
	}

	/** The tree's color as ARGB, for its tab, its XP bar and the level-up message. */
	public int color() {
		return this.color;
	}

	public String translationKey() {
		return "talent_tree.imsotalented." + this.id;
	}

	/** This tree's talents in declaration order, which is the order they unlock in. */
	public List<Talent> talents() {
		if (this.talents == null) {
			List<Talent> talents = new ArrayList<>();

			for (Talent talent : Talent.values()) {
				if (talent.tree() == this) {
					talents.add(talent);
				}
			}

			this.talents = Collections.unmodifiableList(talents);
		}

		return this.talents;
	}

	/** The talent the tree starts from: the one with no prereqs, buyable from the start. */
	public Talent root() {
		for (Talent talent : Talent.values()) {
			if (talent.tree() == this && talent.prereqs().length == 0) {
				return talent;
			}
		}

		throw new IllegalStateException("Tree " + this.id + " has no root talent");
	}

	/** The talent that makes this tree's XP come faster. */
	public Talent xpTalent() {
		return switch (this) {
			case GATHERER -> Talent.GATHERER_XP;
			case WARRIOR -> Talent.WARRIOR_XP;
			case GUARDIAN -> Talent.GUARDIAN_XP;
			case EXPLORER -> Talent.EXPLORER_XP;
		};
	}

	/** The tree's top level: one level per talent level in it, so a maxed tree has paid for every talent. */
	public int maxLevel() {
		if (this.maxLevel < 0) {
			int total = 0;

			for (Talent talent : this.talents()) {
				total += talent.maxLevel();
			}

			this.maxLevel = total;
		}

		return this.maxLevel;
	}

	public static Optional<TalentTree> byId(String id) {
		for (TalentTree tree : values()) {
			if (tree.id.equals(id)) {
				return Optional.of(tree);
			}
		}

		return Optional.empty();
	}
}
