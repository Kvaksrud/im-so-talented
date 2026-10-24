package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BountifulHarvestTest {
	@Test
	void extraItemsIsTheLevel() {
		assertEquals(0, BountifulHarvest.extraItems(0), "level 0 adds nothing");
		assertEquals(1, BountifulHarvest.extraItems(1));
		assertEquals(3, BountifulHarvest.extraItems(3), "top level L3 = 3 extra");
		assertEquals(0, BountifulHarvest.extraItems(-2), "negative levels add nothing");
	}

	@Test
	void theClassicHarvestsAreEncodedExplicitly() {
		assertEquals("wheat", BountifulHarvest.mainDropId("wheat"));
		assertEquals("carrot", BountifulHarvest.mainDropId("carrots"));
		assertEquals("potato", BountifulHarvest.mainDropId("potatoes"));
		assertEquals("beetroot", BountifulHarvest.mainDropId("beetroots"));
		assertEquals("nether_wart", BountifulHarvest.mainDropId("nether_wart"));
		assertEquals("sweet_berries", BountifulHarvest.mainDropId("sweet_berry_bush"));
		assertEquals("cocoa_beans", BountifulHarvest.mainDropId("cocoa"));
	}

	@Test
	void otherBlocksAreNotTargeted() {
		assertNull(BountifulHarvest.mainDropId("melon"));
		assertNull(BountifulHarvest.mainDropId("pumpkin"));
		assertNull(BountifulHarvest.mainDropId("wheat_seeds"));
		assertNull(BountifulHarvest.mainDropId(""));
	}

	@Test
	void theBonusOnlyIncreasesAProducedMainItem() {
		assertEquals(3, BountifulHarvest.bonusFor(1, 3), "mature wheat produced 1: three extra (1 -> 4)");
		assertEquals(3, BountifulHarvest.bonusFor(2, 3), "carrots at age 7 produce 2: three extra, not a multiplication");
		assertEquals(2, BountifulHarvest.bonusFor(3, 2), "cocoa at full age produces 3: two extra (the level, not the count)");
		assertEquals(0, BountifulHarvest.bonusFor(0, 3), "young wheat (seeds only, no wheat produced) gets nothing");
		assertEquals(0, BountifulHarvest.bonusFor(1, 0), "level 0 adds nothing");
		assertEquals(0, BountifulHarvest.bonusFor(0, 0));
		assertEquals(0, BountifulHarvest.bonusFor(-1, 3), "a non-positive produced count gets nothing");
	}
}
