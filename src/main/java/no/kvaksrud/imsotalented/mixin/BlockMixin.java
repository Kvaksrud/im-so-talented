package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import no.kvaksrud.imsotalented.effect.Quarrying;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Quarrying puts every drop at the player's feet. Block drops all spawn through the private {@code popResource}
 * that takes the item entity's factory, and block experience through {@code popExperience}; both only move anything
 * while a quarry runs ({@link Quarrying#gather}).
 */
@Mixin(Block.class)
public abstract class BlockMixin {
	@WrapOperation(
			method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private static boolean imsotalented$gatherDrop(Level level, Entity drop, Operation<Boolean> original) {
		Quarrying.gather(level, drop);
		return original.call(level, drop);
	}

	@ModifyArg(
			method = "popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
			index = 1)
	private Vec3 imsotalented$gatherExperience(ServerLevel level, Vec3 pos, int amount) {
		return Quarrying.experiencePoint(level, pos);
	}
}
