package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import no.kvaksrud.imsotalented.talent.Talent;
import org.junit.jupiter.api.Test;

class WeaponDamageTest {
	@Test
	void everySpecialistHasItsWeapon() {
		assertEquals(Talent.UNARMED_SPECIALIST, WeaponType.UNARMED.specialist());
		assertEquals(Talent.SWORD_SPECIALIST, WeaponType.SWORD.specialist());
		assertEquals(Talent.BOW_SPECIALIST, WeaponType.BOW.specialist());
		assertEquals(Talent.TRIDENT_SPECIALIST, WeaponType.TRIDENT.specialist());
		assertEquals(Talent.MACE_SPECIALIST, WeaponType.MACE.specialist());
		assertEquals(Talent.SPEAR_SPECIALIST, WeaponType.SPEAR.specialist());
		assertNull(WeaponType.OTHER.specialist());

		// Stronger Weapons also opens Warrior XP and Lifesteal; every other talent under it is a weapon's specialist.
		int specialists = 0;

		for (Talent child : Talent.STRONGER_WEAPONS.children()) {
			if (child == Talent.WARRIOR_XP || child == Talent.LIFESTEAL) {
				continue;
			}

			specialists++;
			boolean found = false;

			for (WeaponType type : WeaponType.values()) {
				found |= type.specialist() == child;
			}

			assertTrue(found, child + " has no weapon type");
		}

		assertEquals(6, specialists, "one specialist per weapon type but OTHER");
	}

	@Test
	void generalAndSpecialistBonusesAdd() {
		// Stronger Weapons V (+20%) and Sword Specialist III (+30%): 10 damage becomes 15.
		assertEquals(15.0F, WeaponDamage.scaled(10.0F, WeaponType.SWORD, 5, 3), 1e-4F);
		assertEquals(1.5, WeaponDamage.multiplier(WeaponType.SWORD, 5, 3), 1e-9);
		// Only the general bonus.
		assertEquals(12.0F, WeaponDamage.scaled(10.0F, WeaponType.BOW, 5, 0), 1e-4F);
		assertEquals(1.0, WeaponDamage.multiplier(WeaponType.TRIDENT, 0, 0), 1e-9);
	}

	@Test
	void otherWeaponsOnlyGetTheGeneralBonus() {
		assertEquals(0.0, WeaponType.OTHER.specialistBonus(3), 1e-9);
		assertEquals(12.0F, WeaponDamage.scaled(10.0F, WeaponType.OTHER, 5, 3), 1e-4F);
	}

	@Test
	void punchesGetFlatDamageBeforeThePercentage() {
		assertEquals(0.0, WeaponType.UNARMED.specialistBonus(3), 1e-9, "unarmed's bonus is flat, not a percentage");
		// A 1-damage punch + 1.5 from Unarmed Specialist III, then +20% from Stronger Weapons V.
		assertEquals(3.0F, WeaponDamage.scaled(1.0F, WeaponType.UNARMED, 5, 3), 1e-4F);
		assertEquals(2.0F, WeaponDamage.scaled(1.0F, WeaponType.UNARMED, 0, 2), 1e-4F);
		assertEquals(0.0, WeaponType.SWORD.flatBonus(3), 1e-9, "only punches get flat damage");
	}

	@Test
	void harmlessHitsStayHarmless() {
		assertEquals(0.0F, WeaponDamage.scaled(0.0F, WeaponType.UNARMED, 5, 3));
		assertEquals(-1.0F, WeaponDamage.scaled(-1.0F, WeaponType.SWORD, 5, 3));
		assertTrue(Float.isNaN(WeaponDamage.scaled(Float.NaN, WeaponType.SWORD, 5, 3)));
	}

	@Test
	void hugeDamageDoesNotOverflow() {
		assertEquals(Float.MAX_VALUE, WeaponDamage.scaled(Float.MAX_VALUE, WeaponType.SWORD, 5, 3));
		assertTrue(Float.isFinite(WeaponDamage.scaled(Float.MAX_VALUE, WeaponType.UNARMED, 5, 3)), "capped, not infinite");
	}
}
