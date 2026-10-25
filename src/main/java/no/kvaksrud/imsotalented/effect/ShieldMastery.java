package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.world.entity.player.Player;

/**
 * Shield Mastery: a raised shield (anything with the {@code blocks_attacks} component) starts blocking sooner, and an
 * axe hit disables it for less time. Both are decided on the server; the client only uses the raise delay for how far
 * the head can turn while blocking, which is cosmetic.
 */
public final class ShieldMastery {
	private ShieldMastery() {
	}

	/** Ticks the shield has to be up before it blocks (5 for a vanilla shield). */
	public static int raiseDelayTicks(Player player, int ticks) {
		int level = Talents.effectiveLevel(player, Talent.SHIELD_MASTERY);
		return level > 0 ? reducedTicks(ticks, TalentValues.shieldRaiseReduction(level)) : ticks;
	}

	/** Seconds an attacker's axe disables the shield for, before the shield's own cooldown scale. */
	public static float disableSeconds(Player player, float seconds) {
		int level = Talents.effectiveLevel(player, Talent.SHIELD_MASTERY);
		return level > 0 ? reducedSeconds(seconds, TalentValues.shieldDisableReduction(level)) : seconds;
	}

	/** Ticks shortened by a fraction, rounded to the nearest tick. */
	public static int reducedTicks(int ticks, double reduction) {
		return (int) Math.round(ticks * (1.0 - reduction));
	}

	public static float reducedSeconds(float seconds, double reduction) {
		return (float) (seconds * (1.0 - reduction));
	}
}
