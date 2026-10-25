package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Faster Mining: the tool mines as if it had Efficiency at the talent's level (or more, depending on how the server
 * combines a talent with gear). Efficiency works by adding level² + 1 to the {@code mining_efficiency} attribute while
 * the tool is in the main hand, so the talent swaps the gear's share of that attribute for the combined level's.
 * <p>
 * The client predicts mining progress with the same code, so this runs on both sides; it gives the same answer on
 * both because the talents and the server's rules are synced to the player.
 */
public final class MiningSpeed {
	private MiningSpeed() {
	}

	/** The {@code mining_efficiency} value {@code Player.getDestroySpeed} adds, with the talent in it. */
	public static double miningEfficiency(Player player, double attributeValue) {
		int level = Talents.effectiveLevel(player, Talent.FASTER_MINING);

		if (level <= 0) {
			return attributeValue;
		}

		int gearLevel = gearEfficiency(player);
		int combined = Talents.rules(player).combine(gearLevel, TalentValues.efficiencyLevel(level));
		return withEfficiency(attributeValue, gearLevel, combined);
	}

	/**
	 * Replaces the gear's Efficiency in the attribute value with another level's. Anything else in the attribute (other
	 * mods, commands) stays. Never lower than before, since the combined level is never below the gear's.
	 */
	public static double withEfficiency(double attributeValue, int gearLevel, int level) {
		return attributeValue - efficiencyBonus(gearLevel) + efficiencyBonus(level);
	}

	/** What Efficiency at this level adds to {@code mining_efficiency}: level² + 1, nothing at level 0. */
	public static int efficiencyBonus(int level) {
		return level > 0 ? level * level + 1 : 0;
	}

	private static int gearEfficiency(Player player) {
		return player.registryAccess().get(Enchantments.EFFICIENCY)
				.map(efficiency -> EnchantmentHelper.getItemEnchantmentLevel(efficiency, player.getInventory().getSelectedItem()))
				.orElse(0);
	}
}
