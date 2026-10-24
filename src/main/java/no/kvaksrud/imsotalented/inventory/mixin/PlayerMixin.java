package no.kvaksrud.imsotalented.inventory.mixin;

import no.kvaksrud.imsotalented.inventory.Backpacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/**
	 * Right after the inventory drops on death, the backpack drops the same way, under the same rule: with
	 * keepInventory on it stays, and copy-on-death carries it to the respawned player.
	 */
	@Inject(method = "dropEquipment(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("TAIL"))
	private void imsotalented$dropBackpack(ServerLevel level, CallbackInfo ci) {
		if (!level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			Backpacks.dropOnDeath((Player) (Object) this);
		}
	}
}
