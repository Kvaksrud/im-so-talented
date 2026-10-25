package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * More Luck: a virtual Fortune, Looting and Luck of the Sea. Each is raised where vanilla reads the enchantment's level
 * for that purpose, so it works with any tool (or none), and the loot tables decide what it does, as with the real
 * enchantments. Server side only: loot and fishing never run on the client.
 */
public final class MoreLuck {
	private MoreLuck() {
	}

	/**
	 * Fortune, read by the loot functions that use it ({@code ApplyBonusCount}, {@code BonusLevelTableCondition}). The
	 * breaking player is the loot context's {@code this_entity}; explosions have none, so they get no bonus.
	 */
	public static int fortune(Holder<Enchantment> enchantment, int gearLevel, LootContext context) {
		if (enchantment.is(Enchantments.FORTUNE) && context.getOptional(LootContextParams.THIS_ENTITY) instanceof Player player) {
			return withTalent(player, gearLevel);
		}

		return gearLevel;
	}

	/** Looting: vanilla only reads it through {@code EnchantmentHelper.getEnchantmentLevel}, for the attacker. */
	public static int looting(Holder<Enchantment> enchantment, LivingEntity entity, int gearLevel) {
		if (entity instanceof Player player && enchantment.is(Enchantments.LOOTING)) {
			return withTalent(player, gearLevel);
		}

		return gearLevel;
	}

	/** Luck of the Sea: its bonus to fishing luck is its level, so the talent's level is added the same way. */
	public static int fishingLuck(Entity fisher, int gearBonus) {
		return fisher instanceof Player player ? withTalent(player, gearBonus) : gearBonus;
	}

	private static int withTalent(Player player, int gearLevel) {
		int level = Talents.effectiveLevel(player, Talent.MORE_LUCK);
		return level > 0 ? Talents.rules(player).combine(gearLevel, TalentValues.luckLevel(level)) : gearLevel;
	}
}
