package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MiningSpeedTest {
	@Test
	void efficiencyAddsLevelSquaredPlusOne() {
		assertEquals(0, MiningSpeed.efficiencyBonus(0));
		assertEquals(2, MiningSpeed.efficiencyBonus(1));
		assertEquals(10, MiningSpeed.efficiencyBonus(3));
		assertEquals(26, MiningSpeed.efficiencyBonus(5));
		assertEquals(0, MiningSpeed.efficiencyBonus(-2), "negative levels count as none");
	}

	@Test
	void talentOnAPlainToolAddsItsEfficiency() {
		assertEquals(26.0, MiningSpeed.withEfficiency(0.0, 0, 5), 1e-9, "plain pick + Faster Mining V = Efficiency V");
	}

	@Test
	void gearEfficiencyIsSwappedNotAddedTwice() {
		// An Efficiency III pick (attribute 10) with the combined level V: 10 - 10 + 26.
		assertEquals(26.0, MiningSpeed.withEfficiency(10.0, 3, 5), 1e-9);
		// Same level: nothing changes.
		assertEquals(10.0, MiningSpeed.withEfficiency(10.0, 3, 3), 1e-9);
	}

	@Test
	void otherSourcesOfTheAttributeStay() {
		// 1.5 from something else (another mod, a command) on top of Efficiency II (5).
		assertEquals(1.5 + 17.0, MiningSpeed.withEfficiency(6.5, 2, 4), 1e-9);
	}
}
