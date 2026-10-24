package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import org.jspecify.annotations.Nullable;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;

/**
 * What a player attacked with, as far as the weapon talents care: each kind has its specialist talent, and
 * {@link #OTHER} (axes, pickaxes, sticks, arrows without a bow…) only gets the Stronger Weapons bonus.
 */
public enum WeaponType {
	/** A punch with an empty main hand. Its specialist adds flat damage instead of a percentage. */
	UNARMED(Talent.UNARMED_SPECIALIST),
	SWORD(Talent.SWORD_SPECIALIST),
	/** An arrow fired from a bow or crossbow (hitting something with the bow itself is {@link #OTHER}). */
	BOW(Talent.BOW_SPECIALIST),
	/** A trident, thrown, in hand or in a Riptide spin. */
	TRIDENT(Talent.TRIDENT_SPECIALIST),
	/** A mace, smash attacks included. */
	MACE(Talent.MACE_SPECIALIST),
	/** A spear, jabs and charges included. */
	SPEAR(Talent.SPEAR_SPECIALIST),
	OTHER(null);

	private final @Nullable Talent specialist;

	WeaponType(@Nullable Talent specialist) {
		this.specialist = specialist;
	}

	/** The talent that makes this weapon stronger, or null for {@link #OTHER}. */
	public @Nullable Talent specialist() {
		return this.specialist;
	}

	/** Damage bonus (a fraction) from this weapon's specialist talent at this level. Unarmed's bonus is flat instead. */
	public double specialistBonus(int level) {
		return this.specialist == null || this == UNARMED ? 0.0 : TalentValues.specialistBonus(level);
	}

	/** Flat damage the specialist talent at this level adds before any percentage: only for punches. */
	public double flatBonus(int level) {
		return this == UNARMED ? TalentValues.unarmedDamage(level) : 0.0;
	}

	/**
	 * The weapon behind this damage, if it's a real attack by this player: a melee hit (including sweeps, spear jabs
	 * and charges, mace smashes and Riptide spins), or an arrow or trident they shot or threw. Null for everything else
	 * the player is to blame for, like thorns, fire, explosions and other projectiles.
	 */
	public static @Nullable WeaponType of(Player attacker, DamageSource source) {
		Entity direct = source.getDirectEntity();

		if (direct instanceof ThrownTrident trident) {
			return trident.getOwner() == attacker ? TRIDENT : null;
		}

		if (direct instanceof AbstractArrow arrow) {
			if (arrow.getOwner() != attacker) {
				return null;
			}

			// The bow or crossbow it came from; null for arrows that didn't come from one.
			ItemStack launcher = arrow.getWeaponItem();
			return launcher != null && (launcher.getItem() instanceof BowItem || launcher.getItem() instanceof CrossbowItem) ? BOW : OTHER;
		}

		if (direct == attacker && source.is(DamageTypeTags.IS_PLAYER_ATTACK)) {
			// The main hand, or the trident during a Riptide spin.
			ItemStack weapon = source.getWeaponItem();
			return ofHeldItem(weapon != null ? weapon : ItemStack.EMPTY);
		}

		return null;
	}

	/** The weapon type of an item used in melee. */
	public static WeaponType ofHeldItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return UNARMED;
		}

		if (stack.is(ItemTags.SWORDS)) {
			return SWORD;
		}

		if (stack.getItem() instanceof TridentItem) {
			return TRIDENT;
		}

		if (stack.getItem() instanceof MaceItem) {
			return MACE;
		}

		if (stack.is(ItemTags.SPEARS)) {
			return SPEAR;
		}

		return OTHER;
	}
}
