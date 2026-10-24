package no.kvaksrud.imsotalented.client.mixin;

import no.kvaksrud.imsotalented.client.BackpackClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * Shift with the inventory key in the survival inventory opens the backpack instead of closing the inventory. Hooked
 * where vanilla closes a container screen for the inventory key, rather than in a screen key event: by then the recipe
 * book's search has had the key, so typing a capital E there still types.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;onClose()V"), cancellable = true)
	private void imsotalented$openBackpack(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof InventoryScreen && BackpackClient.openInsteadOfInventory(event)) {
			cir.setReturnValue(true);
		}
	}
}
