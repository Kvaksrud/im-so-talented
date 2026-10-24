package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Block Chance: now and then an attack from a mob or another player does nothing at all, with or without a shield.
 * Checked when the damage arrives in {@code LivingEntity.hurtServer}, after the player's own checks (creative,
 * PvP off, peaceful) have let it through.
 */
public final class BlockChance {
	private BlockChance() {
	}

	/**
	 * Whether this attack on the player is blocked. If it is, the player hears a shield block and sees "Blocked!".
	 *
	 * @param damage   the damage about to be dealt
	 * @param lastHurt the damage of the hit that started the player's current damage cooldown
	 */
	public static boolean tryBlock(ServerPlayer player, DamageSource source, float damage, float lastHurt) {
		int level = Talents.level(player, Talent.BLOCK_CHANCE);

		if (level <= 0 || !isBlockable(player, source) || isIgnoredByCooldown(player, source, damage, lastHurt)) {
			return false;
		}

		if (player.getRandom().nextDouble() >= TalentValues.blockChance(level)) {
			return false;
		}

		player.level().playSound(null, player, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F + player.getRandom().nextFloat() * 0.4F);
		player.sendOverlayMessage(Component.translatable("message.imsotalented.blocked"));
		return true;
	}

	/** An attack by some other living thing (melee, its arrows, its explosion), and not something like /kill. */
	private static boolean isBlockable(ServerPlayer player, DamageSource source) {
		return source.getEntity() instanceof LivingEntity attacker && attacker != player
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !player.isDeadOrDying();
	}

	/**
	 * Right after being hurt, vanilla ignores hits that are no harder than the last one. Rolling for those would show
	 * "Blocked!" for hits that do nothing anyway, like a crowd of zombies attacking at once.
	 */
	private static boolean isIgnoredByCooldown(ServerPlayer player, DamageSource source, float damage, float lastHurt) {
		return player.damageCooldownTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN) && damage <= lastHurt;
	}
}
