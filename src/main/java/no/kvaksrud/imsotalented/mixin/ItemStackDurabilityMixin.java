package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import no.kvaksrud.imsotalented.effect.CarefulHands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Careful Hands, a virtual Unbreaking. 26.3 reads the unbreaking level from the item's ENCHANTMENTS data
 * component inside {@code EnchantmentHelper.processDurabilityChange} — no entity in scope there — so this
 * wraps that call at its nearest caller with the player in scope: {@code ItemStack.processDurabilityChange},
 * which every server-side durability change (tools and armor, {@code hurtAndBreak} and
 * {@code hurtWithoutBreaking}) funnels through. The target is static, so {@code original} is called with all
 * three of its arguments (level, stack, amount) — the wrapped call's full stack.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {
	@WrapOperation(
			method = "processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)I",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
	private int imsotalented$carefulHands(ServerLevel level, ItemStack stack, int amount, Operation<Integer> original,
			@Local(argsOnly = true) ServerPlayer player) {
		return CarefulHands.damageWithTalent(level, stack, amount, player, amount2 -> original.call(level, stack, amount2));
	}
}
