package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import org.junit.jupiter.api.Test;

class CarefulHandsTest {
	private static final TalentRules HIGHEST = TalentRules.DEFAULTS;
	private static final TalentRules ADD = new TalentRules(25, 1.18, EnchantmentStacking.ADD);

	@Test
	void byDefaultTheHigherOfTheTwoCounts() {
		assertEquals(3, CarefulHands.effectiveUnbreaking(HIGHEST, 0, 3), "no gear Unbreaking, talent III = Unbreaking III");
		assertEquals(3, CarefulHands.effectiveUnbreaking(HIGHEST, 1, 3), "Unbreaking I gear with talent III works like III");
		assertEquals(3, CarefulHands.effectiveUnbreaking(HIGHEST, 3, 2), "Unbreaking III gear stays III, never past the vanilla max");
		assertEquals(3, CarefulHands.effectiveUnbreaking(HIGHEST, 3, 0), "no talent: the gear level stands");
		assertEquals(0, CarefulHands.effectiveUnbreaking(HIGHEST, 0, 0));
	}

	@Test
	void addPutsGearAndTalentTogether() {
		assertEquals(6, CarefulHands.effectiveUnbreaking(ADD, 3, 3), "Unbreaking III gear with talent III = VI");
		assertEquals(3, CarefulHands.effectiveUnbreaking(ADD, 0, 3));
		assertEquals(3, CarefulHands.effectiveUnbreaking(ADD, 3, 0));
	}

	@Test
	void negativesCountAsZero() {
		assertEquals(0, CarefulHands.effectiveUnbreaking(HIGHEST, -1, -2), "both negative = nothing");
		assertEquals(3, CarefulHands.effectiveUnbreaking(HIGHEST, -1, 3), "a negative gear level counts as 0");
		assertEquals(3, CarefulHands.effectiveUnbreaking(ADD, 3, -2), "a negative talent level counts as 0");
	}

	@Test
	void noPlayerNoTalent() {
		assertEquals(0, CarefulHands.talentLevelFor(null), "breakage without a player (a dispenser, a mob) gets nothing");
	}
}
