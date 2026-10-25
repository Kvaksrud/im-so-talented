package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * Stronger Weapons and the weapon specialists. Applied to the damage as it arrives in {@code LivingEntity.hurtServer},
 * which every kind of attack goes through, so crits, sweeps, enchantments and mace smashes are already in it and get
 * the same percentage; armor and the victim's shield come after.
 */
public final class WeaponDamage {
	private WeaponDamage() {
	}

	/** The damage with the attacker's weapon talents applied, if a player attacked with a weapon; otherwise unchanged. */
	public static float modify(DamageSource source, float damage) {
		if (!(source.getEntity() instanceof Player attacker)) {
			return damage;
		}

		WeaponType type = WeaponType.of(attacker, source);

		if (type == null) {
			return damage;
		}

		int general = Talents.effectiveLevel(attacker, Talent.STRONGER_WEAPONS);
		Talent specialist = type.specialist();
		int specialistLevel = specialist != null ? Talents.effectiveLevel(attacker, specialist) : 0;

		if (general <= 0 && specialistLevel <= 0) {
			return damage;
		}

		return scaled(damage, type, general, specialistLevel);
	}

	/**
	 * (damage + the punch bonus, for unarmed) × (1 + Stronger Weapons' bonus + the specialist's bonus). Damage that's
	 * zero, negative or not a number is left alone, so an attack that does nothing still does nothing.
	 */
	public static float scaled(float damage, WeaponType type, int generalLevel, int specialistLevel) {
		if (!(damage > 0.0F) || Float.isInfinite(damage)) {
			return damage;
		}

		double result = (damage + type.flatBonus(specialistLevel)) * multiplier(type, generalLevel, specialistLevel);
		return (float) Math.min(result, Float.MAX_VALUE);
	}

	/** 1 + Stronger Weapons' bonus + the specialist's percentage bonus for this weapon. */
	public static double multiplier(WeaponType type, int generalLevel, int specialistLevel) {
		return 1.0 + TalentValues.weaponBonus(generalLevel) + type.specialistBonus(specialistLevel);
	}
}
