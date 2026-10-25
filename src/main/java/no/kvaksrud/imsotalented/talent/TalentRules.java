package no.kvaksrud.imsotalented.talent;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import io.netty.buffer.ByteBuf;

/**
 * The server's rules that the client needs too: how much tree XP each tree level takes (shown in the tree and on the
 * XP bars), and how a talent combines with the same enchantment on gear (mining speed is worked out on both sides).
 * Sent to each player when they join.
 *
 * <p>Every tree levels on its own with the same prices: going from level n to n + 1 takes
 * {@code ceil(costBase * costGrowth^n)} tree XP, and each level is one talent point in that tree.
 *
 * @param costBase   tree XP the first level takes
 * @param costGrowth each next level takes this many times the XP of the one before
 * @param stacking   how a talent and the matching enchantment on gear combine
 */
public record TalentRules(int costBase, double costGrowth, EnchantmentStacking stacking) {
	public static final TalentRules DEFAULTS = new TalentRules(25, 1.18, EnchantmentStacking.HIGHEST);

	/** An id from a newer or broken server reads as the first rule instead of failing the whole packet. */
	private static final IntFunction<EnchantmentStacking> STACKING_BY_ID = ByIdMap.continuous(
			EnchantmentStacking::ordinal, EnchantmentStacking.values(), ByIdMap.OutOfBoundsStrategy.ZERO);

	public static final StreamCodec<ByteBuf, TalentRules> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TalentRules::costBase,
			ByteBufCodecs.DOUBLE, TalentRules::costGrowth,
			ByteBufCodecs.idMapper(STACKING_BY_ID, EnchantmentStacking::ordinal), TalentRules::stacking,
			TalentRules::new);

	/** Levels whose XP thresholds are worked out once per set of rules; far more than any tree has. */
	private static final int CACHED_LEVELS = 128;
	/** The XP each level starts at, per set of rules: the screen and the HUD ask every frame. */
	private static final Map<TalentRules, long[]> THRESHOLDS = new ConcurrentHashMap<>();

	/**
	 * Tree XP to go from tree level {@code level} to the next: {@code ceil(costBase * costGrowth^level)}, so the first
	 * level takes {@code costBase}. Never negative, never above {@code int}.
	 */
	public int cost(int level) {
		double cost = this.costBase * Math.pow(this.costGrowth, Math.max(0, level));

		if (Double.isNaN(cost) || cost > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}

		return (int) Math.clamp((long) Math.ceil(cost), 0L, Integer.MAX_VALUE);
	}

	/** Tree XP for all of these levels together, starting at level {@code fromLevel}. */
	public long totalCost(int fromLevel, int levels) {
		long total = 0L;

		for (int i = 0; i < levels; i++) {
			total += this.cost(fromLevel + i);
		}

		return total;
	}

	/** Tree XP it takes to reach this level from level 0. */
	public long xpForLevel(int level) {
		int clamped = Math.max(0, level);
		return clamped <= CACHED_LEVELS ? this.thresholds()[clamped] : this.totalCost(0, clamped);
	}

	/** {@code thresholds()[n]} is the XP level n starts at, for n up to {@link #CACHED_LEVELS}. */
	private long[] thresholds() {
		return THRESHOLDS.computeIfAbsent(this, rules -> {
			long[] thresholds = new long[CACHED_LEVELS + 1];

			for (int level = 0; level < CACHED_LEVELS; level++) {
				thresholds[level + 1] = thresholds[level] + rules.cost(level);
			}

			return thresholds;
		});
	}

	/**
	 * The level this much tree XP reaches, never above {@code maxLevel}. With a {@code costBase} of 0 every level is
	 * free, so any XP (even none) reaches the top.
	 */
	public int levelFor(long xp, int maxLevel) {
		int level = 0;

		while (level < maxLevel && this.xpForLevel(level + 1) <= xp) {
			level++;
		}

		return level;
	}

	/** Where this much tree XP stands: the level, the XP into it, the XP the next level takes, and whether it's the top. */
	public Progress progress(long xp, int maxLevel) {
		int level = this.levelFor(xp, maxLevel);

		if (level >= maxLevel) {
			return new Progress(level, 0L, 0L, true);
		}

		return new Progress(level, Math.max(0L, xp - this.xpForLevel(level)), this.cost(level), false);
	}

	/** The enchantment level to use, given the level on the gear and the talent's equivalent level. */
	public int combine(int gearLevel, int talentLevel) {
		return this.stacking.combine(gearLevel, talentLevel);
	}

	/**
	 * A tree's level and how far into the next one its XP is.
	 *
	 * @param level  the level reached
	 * @param into   tree XP past the start of this level
	 * @param needed tree XP this level takes to finish; 0 at the top
	 * @param maxed  whether the tree is at its top level and earns no more
	 */
	public record Progress(int level, long into, long needed, boolean maxed) {
		/** How full the bar is, from 0 to 1; full at the top. */
		public float fraction() {
			return this.maxed || this.needed <= 0L ? 1.0F : Math.clamp((float) this.into / this.needed, 0.0F, 1.0F);
		}
	}

	public enum EnchantmentStacking {
		/**
		 * The higher of the two. Gear keeps its own level: a Fortune III pick with More Luck II stays Fortune III, and
		 * a Fortune I pick with More Luck III works like Fortune III.
		 */
		HIGHEST,
		/** Both together: a Fortune III pick with More Luck III works like Fortune VI. */
		ADD;

		public int combine(int gearLevel, int talentLevel) {
			int gear = Math.max(0, gearLevel);
			int talent = Math.max(0, talentLevel);
			return this == ADD ? gear + talent : Math.max(gear, talent);
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public static Optional<EnchantmentStacking> byId(String id) {
			for (EnchantmentStacking stacking : values()) {
				if (stacking.id().equals(id.trim().toLowerCase(Locale.ROOT))) {
					return Optional.of(stacking);
				}
			}

			return Optional.empty();
		}
	}
}
