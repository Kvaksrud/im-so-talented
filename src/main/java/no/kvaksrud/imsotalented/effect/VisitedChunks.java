package no.kvaksrud.imsotalented.effect;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.LongStream;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Every chunk a player has entered, per dimension, so Explorer pays for each chunk only once however often the player
 * comes back. Kept with the player for good (through death too), since a chunk that paid once must never pay again.
 *
 * <p>Stored as one bitmap per region of 32 × 32 chunks (the grid the region files use): 16 longs a region, whether the
 * player crossed it once or walked every chunk of it. A player who has explored a few thousand chunks costs a few
 * kilobytes this way, where one long per chunk would cost eight bytes for each.
 *
 * <p>Saved as a compound of dimension id to one long array per dimension, a run of {@value #ENTRY}-long entries: the
 * region's packed coordinates, then its {@value #WORDS} bitmap words. Reading is lenient: a short tail is dropped and
 * repeated regions are merged, so a damaged list costs some chunks at worst (they pay again), never the player.
 *
 * <p>Mutable, like the backpack: the attachment holds this one instance and Fabric saves its current state whenever
 * the player is saved. Server thread only.
 */
public final class VisitedChunks {
	/** A region is 2^5 = 32 chunks on a side. */
	private static final int REGION_SHIFT = 5;
	private static final int REGION_MASK = (1 << REGION_SHIFT) - 1;
	/** Bitmap words per region: 32 × 32 chunks is 1024 bits. */
	static final int WORDS = (1 << (2 * REGION_SHIFT)) / Long.SIZE;
	/** Longs per saved region: its key, then its bitmap. */
	static final int ENTRY = 1 + WORDS;

	public static final Codec<VisitedChunks> CODEC = Codec.unboundedMap(Codec.STRING,
					Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream))
			.xmap(VisitedChunks::fromSaved, VisitedChunks::toSaved);

	/** Dimension id to region key to that region's bitmap. */
	private final Map<String, Long2ObjectOpenHashMap<long[]>> dimensions = new HashMap<>();

	/**
	 * Marks the chunk as visited in this dimension.
	 *
	 * @return true the first time, false if the player had been there before
	 */
	public boolean visit(String dimension, int chunkX, int chunkZ) {
		Long2ObjectOpenHashMap<long[]> regions = this.dimensions.computeIfAbsent(dimension, _ -> new Long2ObjectOpenHashMap<>());
		long key = regionKey(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
		long[] bits = regions.get(key);

		if (bits == null) {
			bits = new long[WORDS];
			regions.put(key, bits);
		}

		int bit = bitIndex(chunkX, chunkZ);
		long mask = 1L << (bit & (Long.SIZE - 1));

		if ((bits[bit >>> 6] & mask) != 0L) {
			return false;
		}

		bits[bit >>> 6] |= mask;
		return true;
	}

	/** Whether the player has been in this chunk of this dimension. */
	public boolean contains(String dimension, int chunkX, int chunkZ) {
		Long2ObjectOpenHashMap<long[]> regions = this.dimensions.get(dimension);
		long[] bits = regions != null ? regions.get(regionKey(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT)) : null;

		if (bits == null) {
			return false;
		}

		int bit = bitIndex(chunkX, chunkZ);
		return (bits[bit >>> 6] & (1L << (bit & (Long.SIZE - 1)))) != 0L;
	}

	/** How many chunks the player has been in, in this dimension. */
	public int count(String dimension) {
		Long2ObjectOpenHashMap<long[]> regions = this.dimensions.get(dimension);

		if (regions == null) {
			return 0;
		}

		int count = 0;

		for (long[] bits : regions.values()) {
			for (long word : bits) {
				count += Long.bitCount(word);
			}
		}

		return count;
	}

	/** How many chunks the player has been in, in every dimension together. */
	public int count() {
		int count = 0;

		for (String dimension : this.dimensions.keySet()) {
			count += this.count(dimension);
		}

		return count;
	}

	/** The packed coordinates of a region, like {@code ChunkPos.pack}: x in the low 32 bits, z in the high. */
	private static long regionKey(int regionX, int regionZ) {
		return (regionX & 0xFFFFFFFFL) | ((long) regionZ << 32);
	}

	/** The chunk's bit in its region's bitmap: row by row, 32 chunks to a row. The mask keeps negatives in range. */
	private static int bitIndex(int chunkX, int chunkZ) {
		return ((chunkZ & REGION_MASK) << REGION_SHIFT) | (chunkX & REGION_MASK);
	}

	/** The save format: per dimension, its regions in key order (so the same chunks always save the same way). */
	private Map<String, long[]> toSaved() {
		Map<String, long[]> saved = new HashMap<>();

		for (Map.Entry<String, Long2ObjectOpenHashMap<long[]>> dimension : this.dimensions.entrySet()) {
			long[] keys = dimension.getValue().keySet().toLongArray();
			Arrays.sort(keys);
			long[] out = new long[keys.length * ENTRY];

			for (int i = 0; i < keys.length; i++) {
				out[i * ENTRY] = keys[i];
				System.arraycopy(dimension.getValue().get(keys[i]), 0, out, i * ENTRY + 1, WORDS);
			}

			if (out.length > 0) {
				saved.put(dimension.getKey(), out);
			}
		}

		return saved;
	}

	/** Reads the save format; see the class comment for what a damaged list does. */
	private static VisitedChunks fromSaved(Map<String, long[]> saved) {
		VisitedChunks visited = new VisitedChunks();

		for (Map.Entry<String, long[]> dimension : saved.entrySet()) {
			long[] in = dimension.getValue();
			Long2ObjectOpenHashMap<long[]> regions = new Long2ObjectOpenHashMap<>();

			for (int start = 0; start + ENTRY <= in.length; start += ENTRY) {
				long[] bits = regions.get(in[start]);

				if (bits == null) {
					bits = new long[WORDS];
					regions.put(in[start], bits);
				}

				for (int word = 0; word < WORDS; word++) {
					bits[word] |= in[start + 1 + word];
				}
			}

			if (!regions.isEmpty()) {
				visited.dimensions.put(dimension.getKey(), regions);
			}
		}

		return visited;
	}
}
