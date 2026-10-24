package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import no.kvaksrud.imsotalented.effect.BlockChance;
import no.kvaksrud.imsotalented.effect.ShieldMastery;
import no.kvaksrud.imsotalented.effect.SwimSpeed;
import no.kvaksrud.imsotalented.effect.TreeXp;
import no.kvaksrud.imsotalented.effect.WeaponDamage;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.Talents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Both hurtServer hooks sit at HEAD. Mixin applies them in declaration order, so weapon damage runs first and the block
 * roll's damage-cooldown check sees the damage that would really land. Beyond that the order doesn't matter: the damage
 * change has no side effects, and a blocked hit deals nothing whatever its damage. The tree XP wrap goes around the
 * whole method, those two hooks included, so it sees every outcome of a hit.
 * <p>
 * Players reach this through Player.hurtServer and ServerPlayer.hurtServer, so creative, PvP and difficulty have
 * already been dealt with by then.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Shadow
	protected float lastHurt;

	/** Stronger Weapons and the specialists, for attacks by a player; every attack type comes through here. */
	@ModifyVariable(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float imsotalented$weaponDamage(float damage, @Local(argsOnly = true) DamageSource source) {
		return WeaponDamage.modify(source, damage);
	}

	/** Block Chance: a lucky player takes nothing from this attack. */
	@Inject(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void imsotalented$blockChance(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player && BlockChance.tryBlock(player, source, damage, this.lastHurt)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Warrior and Guardian XP. Fabric's damage events don't fit: AFTER_DAMAGE skips the killing blow and reports the
	 * damage before armor, ALLOW_DAMAGE comes after Block Chance has already cancelled at HEAD, and AFTER_DEATH has no
	 * amount. Wrapping the whole method sees the health before and after (Warrior counts what the mob really lost, the
	 * killing blow included) and the raw damage of every hit on a player, whether it landed, a shield took it or Block
	 * Chance shrugged it off (Guardian). Hits that can't earn anything go straight through.
	 */
	@WrapMethod(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")
	private boolean imsotalented$treeXp(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (!TreeXp.earnsFromHurt(self, source)) {
			return original.call(level, source, damage);
		}

		float healthBefore = self.getHealth();
		int cooldownBefore = self.damageCooldownTime;
		float lastHurtBefore = this.lastHurt;
		boolean hurt = original.call(level, source, damage);
		TreeXp.afterHurt(self, source, damage, healthBefore, cooldownBefore, lastHurtBefore);
		return hurt;
	}

	/** Shield Mastery: a player's raised shield starts blocking sooner. */
	@ModifyExpressionValue(
			method = "getItemBlockingWith()Lnet/minecraft/world/item/ItemStack;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/BlocksAttacks;blockDelayTicks()I"))
	private int imsotalented$fasterShieldRaise(int ticks) {
		return (Object) this instanceof Player player ? ShieldMastery.raiseDelayTicks(player, ticks) : ticks;
	}

	/**
	 * Swim Speed (TOGGLEABLE): the per-tick underwater damping that {@code travelInWater} multiplies the
	 * horizontal velocity by (0.8 vanilla, 0.9 sprinting, 0.96 with Dolphin's Grace) is softened for a
	 * talented swimmer, so their sustained in-water movement is 1 + 0.1 per level. This is the first
	 * mixin that reads a TOGGLEABLE talent's EFFECTIVE level — 0 while the talent is toggled off, so the
	 * toggle removes the bonus — through the same one-call choke point every other effect uses; gliding
	 * and the fun talents (P8/P9) copy this read.
	 */
	@WrapOperation(
			method = "travelInWater(Lnet/minecraft/world/phys/Vec3;DZD)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 imsotalented$swimSpeed(Vec3 velocity, double x, double y, double z, Operation<Vec3> original) {
		if (!((Object) this instanceof Player player)) {
			return original.call(velocity, x, y, z);
		}

		int level = Talents.data(player).effectiveLevel(Talent.SWIM_SPEED);
		if (level <= 0) {
			return original.call(velocity, x, y, z);
		}

		// double locals on purpose: Operation.call takes Object..., and a float expression would autobox to
		// Float, which the generated bridge casts to Double and rejects (ClassCastException at runtime).
		double fx = SwimSpeed.dampFactor((float) x, level);
		double fz = SwimSpeed.dampFactor((float) z, level);
		return original.call(velocity, fx, y, fz);
	}
}
