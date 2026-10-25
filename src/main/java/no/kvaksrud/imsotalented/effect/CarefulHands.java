package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Careful Hands: any item the player uses works as if it carried Unbreaking at the talent's level, combined with the
 * gear's own Unbreaking by the server's {@code enchantment_stacking} rule like the other enchantment talents: by
 * default the higher of the two counts (Unbreaking III gear stays III), and only {@code add} goes past the vanilla
 * maximum. 26.3 reads Unbreaking from the item's {@code ENCHANTMENTS} data component while processing a durability
 * change (the data-driven {@code item_damage} effect, whose binomial skip probability is a function of the level),
 * so this is a virtual enchantment at the vanilla read site: the wrapped call site temporarily presents the
 * combined level in the component, runs the vanilla math unchanged — including its tool/armor branch
 * formulas — and restores the item's component exactly.
 *
 * <p>The read site itself ({@code EnchantmentHelper.processDurabilityChange}) has no entity in scope; the
 * nearest caller with one is {@code ItemStack.processDurabilityChange(int, ServerLevel, ServerPlayer)}, which
 * the mixin wraps and hands the player through here. Non-player breakage (the player argument is null) and
 * level 0 fall through to vanilla untouched.
 */
public final class CarefulHands {
	/** One call to the wrapped vanilla durability processing for {@code amount} points. */
	@FunctionalInterface
	public interface DamageProcessor {
		int process(int amount);
	}

	private CarefulHands() {
	}

	/**
	 * Pure combine rule: the gear's Unbreaking and the talent's, combined by the server's stacking rule, so Careful
	 * Hands follows the same rule as Faster Mining and More Luck (highest: gear III + talent II stays III; add: V).
	 * Negatives count as 0.
	 */
	public static int effectiveUnbreaking(TalentRules rules, int gearLevel, int talentLevel) {
		return rules.combine(gearLevel, talentLevel);
	}

	/** The player's virtual level: the talent's unbreaking bonus, 0 for a null player or no talent. */
	public static int talentLevelFor(Player player) {
		if (player == null) {
			return 0;
		}

		return TalentValues.unbreakingBonus(Talents.effectiveLevel(player, Talent.CAREFUL_HANDS));
	}

	/**
	 * Runs the vanilla durability processing as if the gear carried Unbreaking at the
	 * {@link #effectiveUnbreaking combined} level. A null player, level 0, or gear whose own Unbreaking already is
	 * the combined level (with the default rule, any gear at or above the talent's level) leaves the vanilla path
	 * untouched.
	 */
	public static int damageWithTalent(ServerLevel level, ItemStack stack, int amount, Player player, DamageProcessor vanilla) {
		int talentLevel = talentLevelFor(player);

		if (level == null || stack == null || talentLevel <= 0) {
			return vanilla.process(amount);
		}

		DataComponentType<ItemEnchantments> type = EnchantmentHelper.getComponentType(stack);
		ItemEnchantments before = stack.getOrDefault(type, ItemEnchantments.EMPTY);
		Holder<Enchantment> unbreaking = level.registryAccess().getOrThrow(Enchantments.UNBREAKING);
		int gearLevel = before.getLevel(unbreaking);
		int virtual = effectiveUnbreaking(Talents.rules(player), gearLevel, talentLevel);

		if (virtual == gearLevel) {
			return vanilla.process(amount);
		}

		boolean had = stack.has(type);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(before);
		mutable.set(unbreaking, virtual);
		stack.set(type, mutable.toImmutable());

		try {
			return vanilla.process(amount);
		} finally {
			if (had) {
				stack.set(type, before);
			} else {
				stack.remove(type);
			}
		}
	}
}
