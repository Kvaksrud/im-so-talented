package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import no.kvaksrud.imsotalented.effect.Quarrying;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;

/**
 * Quarrying. Every block a player breaks goes through {@code destroyBlock}, whether mined, insta-mined or broken in
 * creative. The wrap goes around the whole method, Fabric's break events included, so the first ore is a normal break
 * with its drops and wear done before the rest of the vein goes, and each of those is a normal break too.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Shadow
	@Final
	protected ServerPlayer player;

	@WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z")
	private boolean imsotalented$quarry(BlockPos pos, Operation<Boolean> original) {
		return Quarrying.destroyBlock(this.player, pos, target -> original.call(target));
	}
}
