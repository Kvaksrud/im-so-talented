package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Explorer pays for a chunk only the first time, so the set of visited chunks must never forget one or mix them up. */
class VisitedChunksTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	@Test
	void aChunkIsNewOnlyOnce() {
		VisitedChunks visited = new VisitedChunks();
		assertFalse(visited.contains(OVERWORLD, 3, 4));
		assertTrue(visited.visit(OVERWORLD, 3, 4), "the first visit pays");
		assertFalse(visited.visit(OVERWORLD, 3, 4), "coming back doesn't");
		assertTrue(visited.contains(OVERWORLD, 3, 4));
		assertEquals(1, visited.count());
	}

	@Test
	void eachDimensionHasItsOwnChunks() {
		VisitedChunks visited = new VisitedChunks();
		visited.visit(OVERWORLD, 0, 0);
		assertTrue(visited.visit(NETHER, 0, 0), "the same coordinates in the Nether are a new chunk");
		assertEquals(1, visited.count(OVERWORLD));
		assertEquals(1, visited.count(NETHER));
		assertEquals(0, visited.count("minecraft:the_end"));
	}

	@Test
	void neighboursAcrossRegionAndSignBordersStayApart() {
		VisitedChunks visited = new VisitedChunks();
		int[][] chunks = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}, {31, 0}, {32, 0}, {-32, 0}, {-33, 0}, {0, 31}, {0, 32},
				{1_874_999, -1_875_000}, {-1_875_000, 1_874_999}};

		for (int[] chunk : chunks) {
			assertTrue(visited.visit(OVERWORLD, chunk[0], chunk[1]), "chunk " + chunk[0] + ", " + chunk[1] + " is new");
		}

		for (int[] chunk : chunks) {
			assertTrue(visited.contains(OVERWORLD, chunk[0], chunk[1]));
		}

		assertEquals(chunks.length, visited.count(), "no two chunks share a bit");
		assertFalse(visited.contains(OVERWORLD, 1, 0));
		assertFalse(visited.contains(OVERWORLD, 33, 0));
	}

	@Test
	void savesAndLoads() {
		VisitedChunks visited = new VisitedChunks();

		for (int x = -40; x < 40; x += 3) {
			visited.visit(OVERWORLD, x, x / 2);
		}

		visited.visit(NETHER, 5, -7);

		Tag saved = VisitedChunks.CODEC.encodeStart(NbtOps.INSTANCE, visited).getOrThrow();
		assertTrue(((CompoundTag) saved).get(OVERWORLD) instanceof LongArrayTag, "one long array per dimension");
		VisitedChunks read = VisitedChunks.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();

		assertEquals(visited.count(OVERWORLD), read.count(OVERWORLD));
		assertEquals(1, read.count(NETHER));
		assertTrue(read.contains(NETHER, 5, -7));

		for (int x = -40; x < 40; x += 3) {
			assertTrue(read.contains(OVERWORLD, x, x / 2), "chunk " + x);
			assertFalse(read.visit(OVERWORLD, x, x / 2), "a chunk that paid before the save doesn't pay after it");
		}
	}

	@Test
	void theSameChunksAlwaysSaveTheSameWay() {
		VisitedChunks forward = new VisitedChunks();
		VisitedChunks backward = new VisitedChunks();

		for (int i = 0; i < 200; i += 7) {
			forward.visit(OVERWORLD, i, -i);
			backward.visit(OVERWORLD, 196 - i, i - 196);
		}

		assertEquals(29, forward.count());

		assertEquals(VisitedChunks.CODEC.encodeStart(NbtOps.INSTANCE, forward).getOrThrow(),
				VisitedChunks.CODEC.encodeStart(NbtOps.INSTANCE, backward).getOrThrow());
	}

	@Test
	void aDamagedListLosesOnlyWhatIsBroken() {
		VisitedChunks visited = new VisitedChunks();
		visited.visit(OVERWORLD, 2, 2);
		long[] region = ((LongArrayTag) ((CompoundTag) VisitedChunks.CODEC.encodeStart(NbtOps.INSTANCE, visited).getOrThrow()).get(OVERWORLD)).getAsLongArray();
		assertEquals(VisitedChunks.ENTRY, region.length, "one region: its key and its bitmap");

		// The region twice (merged), then a cut-off third entry (dropped).
		long[] damaged = new long[VisitedChunks.ENTRY * 2 + 3];
		System.arraycopy(region, 0, damaged, 0, VisitedChunks.ENTRY);
		System.arraycopy(region, 0, damaged, VisitedChunks.ENTRY, VisitedChunks.ENTRY);
		damaged[VisitedChunks.ENTRY + 1] |= 1L;
		CompoundTag tag = new CompoundTag();
		tag.put(OVERWORLD, new LongArrayTag(damaged));
		tag.put(NETHER, new LongArrayTag(new long[3]));

		VisitedChunks read = VisitedChunks.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		assertTrue(read.contains(OVERWORLD, 2, 2));
		assertTrue(read.contains(OVERWORLD, 0, 0), "the second copy's extra chunk is merged in");
		assertEquals(2, read.count());
		assertEquals(0, read.count(NETHER), "a dimension with only a broken entry is empty");
		assertEquals(new CompoundTag(), VisitedChunks.CODEC.encodeStart(NbtOps.INSTANCE, new VisitedChunks()).getOrThrow(),
				"nothing visited saves nothing");
	}
}
