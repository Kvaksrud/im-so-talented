package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import no.kvaksrud.imsotalented.effect.MoreLuck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;

/** More Luck as Fortune, for drops whose count Fortune raises (ores, crops, glowstone…). */
@Mixin(ApplyBonusCount.class)
public abstract class ApplyBonusCountMixin {
	@WrapOperation(
			method = "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getItemEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ItemInstance;)I"))
	private int imsotalented$fortune(Holder<Enchantment> enchantment, ItemInstance tool, Operation<Integer> original,
			@Local(argsOnly = true) LootContext context) {
		return MoreLuck.fortune(enchantment, original.call(enchantment, tool), context);
	}
}
