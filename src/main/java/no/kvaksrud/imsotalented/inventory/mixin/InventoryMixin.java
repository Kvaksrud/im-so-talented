package no.kvaksrud.imsotalented.inventory.mixin;

import no.kvaksrud.imsotalented.inventory.Backpacks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Shadow
	@Final
	public Player player;

	/**
	 * Picked-up items (and anything else handed to the player: /give, arrows, bucket results) go through
	 * {@code add(-1, stack)}, which shrinks the stack by what fit and returns whether anything did. What is left goes
	 * into the backpack; returning true then makes ItemEntity count it as picked up (and discard the entity once
	 * the stack is empty), exactly as when the inventory took it.
	 *
	 * <p>Only the "anywhere" slot -1 overflows: a caller naming a slot (like putting an item back) wants that slot.
	 * At every return the stack holds the remainder; the slot parameter is only reassigned when it was -1 and there
	 * was a free slot, and then the stack is empty.
	 */
	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
	private void imsotalented$overflowIntoBackpack(int slot, ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
		if (slot == -1 && !itemStack.isEmpty() && this.player instanceof ServerPlayer serverPlayer
				&& Backpacks.storeOverflow(serverPlayer, itemStack) > 0) {
			cir.setReturnValue(true);
		}
	}
}
