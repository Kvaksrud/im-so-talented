package no.kvaksrud.imsotalented.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import no.kvaksrud.imsotalented.effect.BlockChance;
import no.kvaksrud.imsotalented.effect.DamageCooldown;
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
 * roll's damage-cooldown check, and the cooldown a shrug starts, see the damage that would really land. Beyond that the
 * order doesn't matter: the damage change has no side effects. The tree XP wrap goes around the whole method, those
 * two hooks included, so it sees every outcome of a hit.
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

	/**
	 * Block Chance: a lucky player takes nothing from this attack. The shrug starts the damage cooldown (or raises its
	 * mark) as a hit that landed would, so the rest of a crowd's swings in the window are cut down as vanilla cuts them,
	 * instead of landing in full.
	 */
	@Inject(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void imsotalented$blockChance(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player && BlockChance.tryBlock(player, level, source, damage, this.lastHurt)) {
			// Block Chance only rolls for hits vanilla wouldn't ignore, so one in the cooldown is harder than lastHurt
			// and raises it without restarting the cooldown, as it would have if it had landed.
			if (!DamageCooldown.inCooldown(player, source)) {
				player.damageCooldownTime = DamageCooldown.TICKS;
			}

			this.lastHurt = damage;
			cir.setReturnValue(false);
		}
	}

	/**
	 * Warrior and Guardian XP. Fabric's damage events don't fit: AFTER_DAMAGE skips the killing blow and reports the
	 * damage before armor, ALLOW_DAMAGE comes after Block Chance has already cancelled at HEAD, and AFTER_DEATH has no
	 * amount. Wrapping the whole method sees the health before and after (Warrior counts what the mob really lost, the
	 * killing blow included) and the raw damage of every hit on a player, whether it landed, a shield took it or Block
	 * Chance shrugged it off (Guardian). Whether vanilla will turn a hit on a player away before its cooldown is asked
	 * before the hit, since a killing blow leaves the player dying. Hits that can't earn anything go straight through.
	 */
	@WrapMethod(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")
	private boolean imsotalented$treeXp(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (!TreeXp.earnsFromHurt(self, source)) {
			return original.call(level, source, damage);
		}

		float healthBefore = self.getHealth();
		boolean rejected = self instanceof ServerPlayer && DamageCooldown.rejectsBeforeCooldown(self, level, source);
		boolean hurt = original.call(level, source, damage);
		TreeXp.afterHurt(self, source, damage, healthBefore, rejected);
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
	 * Swim Speed: the per-tick underwater damping that {@code travelInWater} multiplies the horizontal velocity by
	 * (0.8 vanilla, 0.9 sprinting, 0.96 with Dolphin's Grace) is softened for a talented swimmer, so their sustained
	 * in-water movement is 1 + 0.1 per level. The talent can be switched off, and its effective level is 0 then.
	 */
	@WrapOperation(
			method = "travelInWater(Lnet/minecraft/world/phys/Vec3;DZD)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 imsotalented$swimSpeed(Vec3 velocity, double x, double y, double z, Operation<Vec3> original) {
		if (!((Object) this instanceof Player player)) {
			return original.call(velocity, x, y, z);
		}

		int level = Talents.effectiveLevel(player, Talent.SWIM_SPEED);

		if (level <= 0) {
			return original.call(velocity, x, y, z);
		}

		// double locals on purpose: Operation.call takes Object..., and a float expression would autobox to
		// Float, which the generated bridge casts to Double and rejects (ClassCastException at runtime).
		double dampX = SwimSpeed.dampFactor((float) x, level);
		double dampZ = SwimSpeed.dampFactor((float) z, level);
		return original.call(velocity, dampX, y, dampZ);
	}
}
