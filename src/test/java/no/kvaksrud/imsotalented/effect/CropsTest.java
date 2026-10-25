package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When a crop counts as ripe, for Bountiful Harvest and Gatherer's crop XP alike. */
class CropsTest {
	@Test
	void aCropIsRipeFromItsRipeAgeOn() {
		assertTrue(Crops.isRipe(7, 7), "wheat, carrots and potatoes at age 7");
		assertTrue(Crops.isRipe(3, 3), "beetroots, nether wart and the berry bush at age 3");
		assertTrue(Crops.isRipe(2, 2), "cocoa at age 2");
		assertFalse(Crops.isRipe(6, 7), "one step short");
		assertFalse(Crops.isRipe(0, 7), "just planted");
		assertTrue(Crops.isRipe(8, 7), "past the ripe age still counts");
	}

	@Test
	void berriesCanBePickedFromAgeTwo() {
		// Vanilla lets a player pick a sweet berry bush above age 1, and picking leaves the bush in place.
		assertEquals(2, Crops.BERRY_PICK_AGE);
		assertTrue(Crops.isRipe(2, Crops.BERRY_PICK_AGE));
		assertTrue(Crops.isRipe(3, Crops.BERRY_PICK_AGE));
		assertFalse(Crops.isRipe(1, Crops.BERRY_PICK_AGE), "a bush at age 1 has nothing to pick");
	}
}
