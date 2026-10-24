package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import no.kvaksrud.imsotalented.effect.MoreLuck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	/**
	 * More Luck as Looting. Vanilla only reads Looting through this method (for mob drops: the count increase and the
	 * extra-chance condition), so other enchantments read here are left alone.
	 */
	@ModifyReturnValue(method = "getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I", at = @At("RETURN"))
	private static int imsotalented$looting(int level, @Local(argsOnly = true) Holder<Enchantment> enchantment, @Local(argsOnly = true) LivingEntity entity) {
		return MoreLuck.looting(enchantment, entity, level);
	}

	/** More Luck as Luck of the Sea, when a player casts a fishing rod. */
	@ModifyReturnValue(
			method = "getFishingLuckBonus(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;)I",
			at = @At("RETURN"))
	private static int imsotalented$luckOfTheSea(int bonus, @Local(argsOnly = true) Entity fisher) {
		return MoreLuck.fishingLuck(fisher, bonus);
	}
}
