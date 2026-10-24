package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Fast Growth: a crop's per-random-tick growth probability is multiplied by
 * {@link TalentValues#growthMultiplier(int)} (1.0 without the talent, 2.5 at the top level), the multiplier
 * coming from the MAX Fast Growth level among players within {@link #RADIUS} blocks horizontally of the crop
 * (any height). No player in range → exactly the vanilla growth.
 *
 * <p>Vanilla grows on the roll {@code nextInt(bound) == 0} ({@code CropBlock}: bound is
 * {@code 25/growthSpeed + 1}; nether wart, sweet berry bush and cocoa each roll a fixed 10/5/5). The growth
 * mixins wrap that one roll, call the original first, and hand the failed roll plus the level state to
 * {@link #resolve}: the vanilla draw keeps the level's RNG stream bit-for-bit when no talent is active, and
 * the talent consumes one extra uniform from the level RNG only when it has a level to work with.
 */
public final class FastGrowth {
	/** Horizontal reach of the talent, in blocks (XZ plane, any height). */
	public static final int RADIUS = 16;

	/** One call to the wrapped vanilla roll: returns the vanilla {@code nextInt(bound)} result. */
	@FunctionalInterface
	public interface Roll {
		int roll(int bound);
	}

	private FastGrowth() {
	}

	/** Pure: the effective per-roll growth chance = vanilla chance × the level's multiplier, clamped to 1.0. */
	public static double growthChance(double vanillaChance, int level) {
		if (level <= 0) {
			return vanillaChance;
		}

		return Math.min(1.0, vanillaChance * TalentValues.growthMultiplier(level));
	}

	/**
	 * Pure: the extra chance the talent grants, conditional on the vanilla roll having FAILED (the roll was
	 * non-zero). With bound {@code B} the vanilla chance is {@code 1/B}, so the talent's extra chance is
	 * {@code (min(1, m/B) − 1/B) / (1 − 1/B)}; zero at level 0 (or a non-extending multiplier).
	 */
	public static double extraChance(int bound, int level) {
		if (level <= 0 || bound <= 1) {
			return 0.0;
		}

		double vanilla = 1.0 / (double) bound;
		double target = growthChance(vanilla, level);

		if (target <= vanilla) {
			return 0.0;
		}

		return (target - vanilla) / (1.0 - vanilla);
	}

	/**
	 * Pure decision. {@code vanillaRoll} is the wrapped roll's result (0 = vanilla would grow),
	 * {@code bound} its bound, {@code talentLevel} the effective Fast Growth level, and {@code extraDraw} a
	 * uniform draw in [0,1) used for the talent's extra chance. Returns 0 when the crop advances one growth
	 * step this tick, otherwise the (non-zero) original roll.
	 */
	public static int resolve(int vanillaRoll, int bound, int talentLevel, float extraDraw) {
		if (vanillaRoll == 0) {
			return 0;
		}

		if (talentLevel <= 0) {
			return vanillaRoll;
		}

		return extraDraw < extraChance(bound, talentLevel) ? 0 : vanillaRoll;
	}

	/**
	 * Game side: runs the vanilla roll first (the level RNG stream stays vanilla-identical when no talent is
	 * in play) and applies the talent to a failed roll.
	 */
	public static int resolveGrowthRoll(int bound, Level level, BlockPos pos, Roll vanilla) {
		int roll = vanilla.roll(bound);

		if (roll != 0 && level instanceof ServerLevel serverLevel) {
			int talentLevel = maxLevelInRadius(serverLevel, pos);

			if (talentLevel > 0) {
				return resolve(roll, bound, talentLevel, serverLevel.getRandom().nextFloat());
			}
		}

		return roll;
	}

	/**
	 * The MAX Fast Growth level among this level's players within {@link #RADIUS} blocks horizontally of
	 * {@code pos} (any height); 0 when none. Cost: one {@code players()} reference read (the level's
	 * always-in-memory player list — the early-out for an empty level), then per player one squared planar
	 * distance check and, only for an in-range player, one talent data read.
	 */
	public static int maxLevelInRadius(ServerLevel level, BlockPos pos) {
		List<ServerPlayer> players = level.players();

		if (players.isEmpty()) {
			return 0;
		}

		double cx = pos.getX() + 0.5;
		double cz = pos.getZ() + 0.5;
		double radiusSq = (double) RADIUS * RADIUS;
		int max = 0;

		for (ServerPlayer player : players) {
			double dx = player.getX() - cx;
			double dz = player.getZ() - cz;

			if (dx * dx + dz * dz > radiusSq) {
				continue;
			}

			int playerLevel = Talents.level(player, Talent.FAST_GROWTH);

			if (playerLevel > max) {
				max = playerLevel;
			}
		}

		return max;
	}
}
