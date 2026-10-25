package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Bountiful Harvest's numbers from the design: one extra main item per level, only for a ripe harvest that dropped it. */
class BountifulHarvestTest {
	@Test
	void extraItemsIsTheLevel() {
		assertEquals(0, BountifulHarvest.extraItems(0), "level 0 adds nothing");
		assertEquals(1, BountifulHarvest.extraItems(1));
		assertEquals(2, BountifulHarvest.extraItems(2));
		assertEquals(3, BountifulHarvest.extraItems(3), "top level L3 = 3 extra");
		assertEquals(0, BountifulHarvest.extraItems(-2), "negative levels add nothing");
	}

	@Test
	void ripeWheatAtTheTopLevelDropsFour() {
		// Ripe wheat drops 1 wheat in vanilla; the design's example is 4 at level 3.
		assertEquals(4, 1 + BountifulHarvest.bonusFor(1, true, 3));
		assertEquals(2, 1 + BountifulHarvest.bonusFor(1, true, 1));
	}

	@Test
	void theBonusIsTheLevelNotAMultiple() {
		assertEquals(3, BountifulHarvest.bonusFor(2, true, 3), "ripe carrots that produced 2: three extra, not a multiplication");
		assertEquals(2, BountifulHarvest.bonusFor(3, true, 2), "ripe cocoa that produced 3: two extra (the level, not the count)");
	}

	@Test
	void unripeCropsGetNoBonus() {
		// A young carrot, potato, nether wart or cocoa pod drops one of its item too: a bonus there would turn planting
		// and breaking into endless items.
		assertEquals(0, BountifulHarvest.bonusFor(1, false, 3), "a just-planted carrot");
		assertEquals(0, BountifulHarvest.bonusFor(3, false, 3));
	}

	@Test
	void theBonusOnlyIncreasesAProducedMainItem() {
		assertEquals(0, BountifulHarvest.bonusFor(0, true, 3), "a harvest that dropped none of its main item gets nothing");
		assertEquals(0, BountifulHarvest.bonusFor(-1, true, 3), "a non-positive produced count gets nothing");
		assertEquals(0, BountifulHarvest.bonusFor(1, true, 0), "level 0 adds nothing");
		assertEquals(0, BountifulHarvest.bonusFor(0, false, 0));
	}
}
