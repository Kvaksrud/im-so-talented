package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import no.kvaksrud.imsotalented.effect.MiningSpeed;
import no.kvaksrud.imsotalented.effect.ShieldMastery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.world.entity.player.Player;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/**
	 * Faster Mining. The first attribute read in getDestroySpeed is {@code mining_efficiency}, which is only added when
	 * the tool is right for the block (speed above 1), just like Efficiency. Runs on both sides.
	 */
	@ModifyExpressionValue(
			method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D", ordinal = 0))
	private double imsotalented$fasterMining(double miningEfficiency) {
		return MiningSpeed.miningEfficiency((Player) (Object) this, miningEfficiency);
	}

	/** Shield Mastery: an axe disables this player's shield for less time. */
	@ModifyArg(
			method = "blockUsingItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;FZ)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/BlocksAttacks;disable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/item/ItemStack;)V"),
			index = 2)
	private float imsotalented$shorterShieldDisable(float seconds) {
		return ShieldMastery.disableSeconds((Player) (Object) this, seconds);
	}
}
