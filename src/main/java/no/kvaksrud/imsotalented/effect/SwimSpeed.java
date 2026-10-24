package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.TalentValues;

/**
 * Swim Speed (stage FLIGHT, toggleable): multiplies the player's horizontal movement in water by
 * {@code 1 + swimSpeedBonus(level)} (1.3 at the top level), and only while the player swims under its own
 * power — the effect lives in the underwater travel path, so riding a boat or raft never triggers it.
 *
 * <p>26.3's {@code LivingEntity.travelInWater} damps the swimmer's horizontal velocity by a per-tick
 * factor (0.8 in water, 0.9 while sprinting, 0.96 under Dolphin's Grace), and the movement a swimmer
 * sustains is inversely proportional to {@code 1 - factor}. The factor is therefore not scaled (at the top
 * level 0.8 x 1.3 would be 1.04 and the motion would run away); it is mapped to
 * {@code 1 - (1 - factor)/(1 + bonus)}, which stays below 1 and multiplies the sustained movement by
 * exactly {@code (1 + bonus)}.
 *
 * <p>This can't be a {@link no.kvaksrud.imsotalented.talent.TalentAttributes} row: the one water attribute
 * 26.3 has, {@code Attributes.WATER_MOVEMENT_EFFICIENCY} (default 0, range 0..1), is read in the same
 * travel code, but it can only pull the factor down toward 0.546 (a drag) — it can never express a speed-up.
 *
 * <p>Because Swim Speed is TOGGLEABLE, the mixin that applies it must read the player's EFFECTIVE level —
 * 0 while the talent is toggled off — through the same choke point every other effect uses:
 *
 * <pre>
 * int level = Talents.data(player).effectiveLevel(Talent.SWIM_SPEED);
 * </pre>
 *
 * That one helper call is the pattern the gliding and fun talents (P8/P9) copy: read the effective level,
 * early-out at 0, apply the pure math.
 */
public final class SwimSpeed {
	private SwimSpeed() {
	}

	/**
	 * The per-tick underwater damping factor a swimmer at this level gets, from the vanilla base factor
	 * (0.8 in water, 0.9 sprinting, 0.96 under Dolphin's Grace): {@code 1 - (1 - base)/(1 + bonus)}.
	 * Level 0 (or a negative, for safety) returns {@code base} unchanged, so a talent-less player pays
	 * nothing and the handler early-outs before touching the original call's result.
	 */
	public static float dampFactor(float base, int level) {
		double bonus = level > 0 ? TalentValues.swimSpeedBonus(level) : 0.0;
		if (bonus <= 0.0) {
			return base;
		}

		return (float) (1.0 - (1.0 - base) / (1.0 + bonus));
	}
}
