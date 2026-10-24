package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import no.kvaksrud.imsotalented.effect.FastGrowth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;

/**
 * Fast Growth for the {@code CropBlock} family (wheat, carrots, potatoes, beetroots): the per-tick growth
 * roll {@code nextInt(25/growthSpeed + 1) == 0} gets the player-multiplied extra chance. The handler takes
 * the wrapped call's receiver (the level's {@link RandomSource}) first, as MixinExtras requires for instance
 * targets; the call itself is made through {@code original} so the level RNG stream stays vanilla-identical
 * when no talent is in play.
 */
@Mixin(CropBlock.class)
public abstract class CropBlockGrowthMixin {
	@WrapOperation(
			method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int imsotalented$fastGrowth(RandomSource random, int bound, Operation<Integer> original,
			@Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
		return FastGrowth.resolveGrowthRoll(bound, level, pos, bound2 -> original.call(random, bound2));
	}
}
