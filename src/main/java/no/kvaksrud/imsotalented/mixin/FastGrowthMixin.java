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
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fast Growth. In 26.3 each of these blocks' {@code randomTick} makes exactly one {@code nextInt} call, the growth roll
 * that grows the crop on 0: {@code CropBlock} (wheat, carrots and potatoes, and beetroots through {@code super})
 * {@code nextInt(25/growthSpeed + 1)}, nether wart {@code nextInt(10)}, the sweet berry bush and cocoa
 * {@code nextInt(5)}. {@link FastGrowth#resolveGrowthRoll} decides from the block state whether the talent applies, so
 * torchflowers, which reach the same {@code CropBlock} code, and a fully grown cocoa pod get the vanilla roll alone.
 * The handler takes the wrapped call's receiver (the level's {@link RandomSource}) first, as MixinExtras requires for
 * instance targets; the call itself goes through {@code original}.
 */
@Mixin({CropBlock.class, NetherWartBlock.class, SweetBerryBushBlock.class, CocoaBlock.class})
public abstract class FastGrowthMixin {
	@WrapOperation(
			method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int imsotalented$fastGrowth(RandomSource random, int bound, Operation<Integer> original, @Local(argsOnly = true) BlockState state,
			@Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
		return FastGrowth.resolveGrowthRoll(bound, level, pos, state, rollBound -> original.call(random, rollBound));
	}
}
