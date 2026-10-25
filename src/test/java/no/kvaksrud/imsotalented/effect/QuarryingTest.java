package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/** Quarrying's reach per level, the vein search and where it stops for the tool, without a game. */
class QuarryingTest {
	private static final BlockPos ORE = new BlockPos(10, 20, 30);
	/** A player west of, level with and north of the ore: the far side is east, up the y axis (eye level) and south. */
	private static final Vec3 EYE = new Vec3(7.5, 20.5, 27.5);

	@Test
	void levelOneToFourReachACubeThatGrowsByOne() {
		for (int level = 1; level < Talent.QUARRYING.maxLevel(); level++) {
			BoundingBox reach = Quarrying.reach(ORE, level, EYE);
			int size = level + 2;
			assertEquals(size, TalentValues.quarryCubeSize(level), "level " + level + " is " + size + " across");
			assertEquals(size, reach.getXSpan());
			assertEquals(size, reach.getYSpan());
			assertEquals(size, reach.getZSpan());
			assertTrue(reach.isInside(ORE), "the ore itself is inside");
			assertFalse(TalentValues.quarriesWholeVein(level));
		}
	}

	@Test
	void anOddCubeIsCenteredOnTheOre() {
		BoundingBox three = Quarrying.reach(ORE, 1, EYE);
		assertEquals(new BoundingBox(9, 19, 29, 11, 21, 31), three);
		BoundingBox five = Quarrying.reach(ORE, 3, EYE);
		assertEquals(new BoundingBox(8, 18, 28, 12, 22, 32), five);
	}

	@Test
	void anEvenCubeHasItsExtraLayerOnTheFarSide() {
		// The player is west and north (lower x and z) and at the ore's height, so the extra layer goes east and south,
		// and up (the eye at the ore's middle counts as the lower side).
		assertEquals(new BoundingBox(9, 19, 29, 12, 22, 32), Quarrying.reach(ORE, 2, EYE));

		// From the other side it goes the other way.
		Vec3 eastSouthAbove = new Vec3(14.5, 25.0, 33.5);
		assertEquals(new BoundingBox(8, 18, 28, 11, 21, 31), Quarrying.reach(ORE, 2, eastSouthAbove));
		assertEquals(new BoundingBox(7, 17, 27, 12, 22, 32), Quarrying.reach(ORE, 4, eastSouthAbove), "6 across at level 4");
	}

	@Test
	void theTopLevelTakesTheWholeVein() {
		int top = Talent.QUARRYING.maxLevel();
		assertEquals(5, top, "five levels, as asked");
		assertTrue(TalentValues.quarriesWholeVein(top));
		BoundingBox reach = Quarrying.reach(ORE, top, EYE);
		assertTrue(reach.isInside(ORE.offset(1000, -100, -1000)), "no cube at the top level");
	}

	@Test
	void theVeinIsEveryTouchingOreNearestFirst() {
		// A line of ore going east from the ore, with a diagonal step, and one ore that touches nothing.
		Set<BlockPos> ores = new HashSet<>(List.of(ORE, ORE.east(), ORE.east(2), ORE.east(3).above(), ORE.west(3)));

		List<BlockPos> vein = Quarrying.vein(ORE, ores::contains, BoundingBox.infinite(), 100);
		assertEquals(List.of(ORE.east(), ORE.east(2), ORE.east(3).above()), vein,
				"the origin isn't in the list, corners count, and the loose ore isn't connected");
	}

	@Test
	void theVeinStaysInsideTheReachAndUnderTheCap() {
		Set<BlockPos> ores = new HashSet<>();

		for (int x = 0; x < 10; x++) {
			ores.add(ORE.east(x));
		}

		List<BlockPos> inCube = Quarrying.vein(ORE, ores::contains, Quarrying.reach(ORE, 1, EYE), 100);
		assertEquals(List.of(ORE.east()), inCube, "a 3x3x3 cube reaches one block east");

		List<BlockPos> inSix = Quarrying.vein(ORE, ores::contains, Quarrying.reach(ORE, 4, EYE), 100);
		assertEquals(3, inSix.size(), "6 across with the extra layers east: x 8..13, so three more");

		List<BlockPos> capped = Quarrying.vein(ORE, ores::contains, BoundingBox.infinite(), 4);
		assertEquals(List.of(ORE.east(), ORE.east(2), ORE.east(3), ORE.east(4)), capped, "the cap keeps the nearest");
	}

	@Test
	void aSolidBlobIsTakenWhole() {
		Set<BlockPos> ores = new HashSet<>();

		for (BlockPos pos : BlockPos.betweenClosed(ORE.offset(-1, -1, -1), ORE.offset(1, 1, 1))) {
			ores.add(pos.immutable());
		}

		assertEquals(26, Quarrying.vein(ORE, ores::contains, Quarrying.reach(ORE, 1, EYE), 100).size(), "a 3x3x3 blob of ore");
	}

	@Test
	void itStopsWithOneDurabilityLeft() {
		assertTrue(Quarrying.canWear(250, 0), "a new iron pickaxe");
		assertTrue(Quarrying.canWear(250, 248), "2 left: one more block, then 1 is left");
		assertFalse(Quarrying.canWear(250, 249), "1 left: the tool is kept");
		assertFalse(Quarrying.canWear(250, 250), "nothing left");
		assertTrue(Quarrying.canWear(0, 0), "an item without durability never stops");
	}

	@Test
	void theTooltipNamesTheCubeOrTheWholeVein() {
		assertEquals("3×3×3", TalentValues.effectArgs(Talent.QUARRYING, 1)[0]);
		assertEquals("6×6×6", TalentValues.effectArgs(Talent.QUARRYING, 4)[0]);
		assertEquals(Talent.QUARRYING.effectKey(), TalentValues.effectKey(Talent.QUARRYING, 4));
		assertEquals(Talent.QUARRYING.effectKey() + ".whole_vein", TalentValues.effectKey(Talent.QUARRYING, 5));
		assertEquals(0, TalentValues.effectArgs(Talent.QUARRYING, 5).length);
	}

	@Test
	void oneBreakTakesAtMost256Blocks() {
		assertEquals(256, TalentValues.QUARRY_MAX_BLOCKS, "the first ore and 255 more");

		// A 300-ore blob (10 x 6 x 5) mined at the top level: the first ore and its 255 nearest neighbours go.
		Set<BlockPos> blob = new HashSet<>();

		for (BlockPos pos : BlockPos.betweenClosed(ORE, ORE.offset(9, 5, 4))) {
			blob.add(pos.immutable());
		}

		assertEquals(300, blob.size());
		BoundingBox anywhere = Quarrying.reach(ORE, Talent.QUARRYING.maxLevel(), EYE);
		List<BlockPos> vein = Quarrying.vein(ORE, blob::contains, anywhere, TalentValues.QUARRY_MAX_BLOCKS - 1);
		assertEquals(255, vein.size());
		assertEquals(255, new HashSet<>(vein).size(), "no block twice");
		assertFalse(vein.contains(ORE), "the first ore isn't among its neighbours");
		assertTrue(blob.containsAll(vein));

		// The quarry looks one block further to tell a capped vein from one that ends right there.
		assertEquals(256, Quarrying.vein(ORE, blob::contains, anywhere, TalentValues.QUARRY_MAX_BLOCKS).size());
		assertEquals(299, Quarrying.vein(ORE, blob::contains, anywhere, 1000).size(), "uncapped it would take the whole blob");
	}

	@Test
	void theNearestBlocksGoFirstWhenTheCapCutsAVeinShort() {
		Set<BlockPos> line = new HashSet<>();

		for (int x = 0; x < 300; x++) {
			line.add(ORE.east(x));
		}

		List<BlockPos> vein = Quarrying.vein(ORE, line::contains, BoundingBox.infinite(), TalentValues.QUARRY_MAX_BLOCKS - 1);
		assertEquals(ORE.east(), vein.getFirst());
		assertEquals(ORE.east(255), vein.getLast(), "the 255 nearest, in order");
	}
}
