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
import net.minecraft.world.level.block.SweetBerryBushBlock;

/**
 * Fast Growth for the sweet berry bush: the per-tick growth roll {@code nextInt(5) == 0} gets the
 * player-multiplied extra chance. The handler takes the wrapped call's receiver (the level's
 * {@link RandomSource}) first, as MixinExtras requires for instance targets; the call itself goes through
 * {@code original}.
 */
@Mixin(SweetBerryBushBlock.class)
public abstract class SweetBerryBushGrowthMixin {
	@WrapOperation(
			method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int imsotalented$fastGrowth(RandomSource random, int bound, Operation<Integer> original,
			@Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
		return FastGrowth.resolveGrowthRoll(bound, level, pos, bound2 -> original.call(random, bound2));
	}
}
