package no.kvaksrud.imsotalented.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Vanilla's damage cooldown, as {@code LivingEntity.hurtServer} runs it, for the talents that have to agree with it
 * (Block Chance and Guardian XP). A hit that lands starts the cooldown at {@link #TICKS}. While more than
 * {@link #REDUCED_ABOVE} ticks of it are left, another hit only deals what it has over the hit that started it (vanilla's
 * {@code lastHurt}, which a harder hit raises without restarting the cooldown), and a hit no harder does nothing. Damage
 * types tagged {@code bypasses_cooldown} ignore it and start a new one.
 */
public final class DamageCooldown {
	/** Ticks a hit that lands starts the cooldown at. */
	public static final int TICKS = 20;
	/** While more than this many ticks of the cooldown are left, a hit only counts for what it has over the last one. */
	public static final int REDUCED_ABOVE = 10;
	/** So a hit counts in part this many ticks after the hit that started the cooldown, the first half of it. */
	public static final int WINDOW_TICKS = TICKS - REDUCED_ABOVE;

	private DamageCooldown() {
	}

	/** Whether this hit lands in the entity's damage cooldown, where vanilla only takes what it has over the last hit. */
	public static boolean inCooldown(LivingEntity entity, DamageSource source) {
		return entity.damageCooldownTime > REDUCED_ABOVE && !source.is(DamageTypeTags.BYPASSES_COOLDOWN);
	}

	/** Whether vanilla ignores a hit of {@code damage}: in the cooldown and no harder than the hit that started it. */
	public static boolean ignores(boolean inCooldown, float damage, float lastHurt) {
		return inCooldown && damage <= lastHurt;
	}

	/**
	 * Whether {@code hurtServer} turns the hit away before it gets to the cooldown: an entity immune to it, one already
	 * dying, or fire against Fire Resistance (a blaze's fireball at a player who drank the potion). Such a hit does
	 * nothing at all, so the talents leave it alone too.
	 */
	public static boolean rejectsBeforeCooldown(LivingEntity entity, ServerLevel level, DamageSource source) {
		return entity.isInvulnerableTo(level, source) || entity.isDeadOrDying()
				|| source.is(DamageTypeTags.IS_FIRE) && entity.hasEffect(MobEffects.FIRE_RESISTANCE);
	}
}
