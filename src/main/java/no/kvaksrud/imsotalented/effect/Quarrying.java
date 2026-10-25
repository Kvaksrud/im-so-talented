package no.kvaksrud.imsotalented.effect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

/**
 * Quarrying: breaking an ore while sneaking (holding Shift, the sneak key) breaks the ore of the same kind connected
 * to it as well, each block as if the player had mined it: its drops (Fortune and More Luck included), the tool's wear
 * (Unbreaking and Careful Hands included), Gatherer XP, stats and hunger. Each level reaches further, from a 3x3x3 cube
 * around the ore at level 1 to the whole vein at the top level ({@link TalentValues#quarryCubeSize}). Every drop of the
 * break, the first ore's too, lands at the player's feet, not out by the vein where it could fall into lava or down a
 * ravine.
 *
 * <ul>
 * <li>"The same kind" is the same block, or one that shares a {@code c:ores/<kind>} tag with it, so iron ore and
 *     deepslate iron ore go together. Only ores ({@code c:ores}) start a quarry, and stone around them is left.</li>
 * <li>Only with a tool that gets the ore's drops, and every block still has to be one the tool can mine.</li>
 * <li>It stops with 1 durability left, so a tool (an enchanted pickaxe above all) never breaks from quarrying, and
 *     at {@link TalentValues#QUARRY_MAX_BLOCKS}; either way the player is told on the action bar. Switching tools,
 *     spawn protection and other mods' break checks stop or skip blocks as for any break.</li>
 * <li>Ore in a chunk that isn't loaded counts as not ore: the search never loads or generates a chunk on the server
 *     thread, and never breaks a block in one.</li>
 * </ul>
 *
 * <p>All server side: the client already sends the sneak key with its movement input. The wrap of
 * {@code ServerPlayerGameMode.destroyBlock} ({@code ServerPlayerGameModeMixin}) hands every block a player breaks to
 * {@link #destroyBlock}, and {@code BlockMixin} moves the drops through {@link #gather}.
 */
public final class Quarrying {
	private static final String ORE_KIND_PREFIX = "ores/";

	/** While a quarry runs: the blocks its own breaks hand back through {@link #destroyBlock} are plain breaks. */
	private static boolean quarrying;
	/** Where the running quarry's drops go (the player's feet when it started), and in which level; null when none runs. */
	private static @Nullable Level dropLevel;
	private static @Nullable Vec3 dropPoint;

	private Quarrying() {
	}

	/** One plain break of a block, the wrapped vanilla {@code destroyBlock}: true if it broke. */
	@FunctionalInterface
	public interface Breaker {
		boolean destroy(BlockPos pos);
	}

	// Pure rules

	/**
	 * Where a quarry may reach from the ore at {@code ore}: a cube of {@link TalentValues#quarryCubeSize} blocks, or
	 * anywhere at the top level. An odd size is centered on the ore; an even one has its extra layer on the far side
	 * from the player on each axis, deeper into what's being mined.
	 *
	 * @param eye the player's eye position, which says which side is the far one
	 */
	public static BoundingBox reach(BlockPos ore, int level, Vec3 eye) {
		if (TalentValues.quarriesWholeVein(level)) {
			return BoundingBox.infinite();
		}

		int size = TalentValues.quarryCubeSize(level);
		int x = spanStart(ore.getX(), size, eye.x);
		int y = spanStart(ore.getY(), size, eye.y);
		int z = spanStart(ore.getZ(), size, eye.z);
		return new BoundingBox(x, y, z, x + size - 1, y + size - 1, z + size - 1);
	}

	/** The first block of a {@code size}-long span through {@code block}, the odd one out on the side away from {@code viewer}. */
	static int spanStart(int block, int size, double viewer) {
		int before = (size - 1) / 2;

		if (size % 2 == 0 && viewer > block + 0.5) {
			before++;
		}

		return block - before;
	}

	/**
	 * The blocks a quarry from {@code origin} takes besides the origin, nearest first: every block {@code sameOre} accepts
	 * that touches the origin or one already taken (sides, edges and corners), inside {@code reach}, at most
	 * {@code max} of them.
	 */
	public static List<BlockPos> vein(BlockPos origin, Predicate<BlockPos> sameOre, BoundingBox reach, int max) {
		List<BlockPos> vein = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Queue<BlockPos> next = new ArrayDeque<>();
		seen.add(origin);
		next.add(origin);

		while (!next.isEmpty() && vein.size() < max) {
			BlockPos from = next.remove();

			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos pos = from.offset(dx, dy, dz);

						if (vein.size() < max && seen.add(pos) && reach.isInside(pos) && sameOre.test(pos)) {
							vein.add(pos);
							next.add(pos);
						}
					}
				}
			}
		}

		return vein;
	}

	/**
	 * Whether a tool can take one more block and keep at least 1 durability: a block costs at most 1 (Unbreaking and
	 * Careful Hands only ever skip it). Tools without durability never stop.
	 */
	public static boolean canWear(int maxDamage, int damage) {
		return maxDamage <= 0 || maxDamage - damage > 1;
	}

	// Game side

	/**
	 * Every block a player breaks comes through here, from the wrap of {@code ServerPlayerGameMode.destroyBlock}: a
	 * plain break unless the player sneaks while breaking an ore, which then takes the rest of the vein after it. The vein is
	 * found before the first ore goes, from the ore as it was.
	 *
	 * @param destroy the wrapped vanilla break, Fabric's break events included
	 */
	public static boolean destroyBlock(ServerPlayer player, BlockPos pos, Breaker destroy) {
		int level = Talents.effectiveLevel(player, Talent.QUARRYING);

		if (quarrying || level <= 0 || !player.isShiftKeyDown()) {
			return destroy.destroy(pos);
		}

		ServerLevel world = player.level();
		BlockState ore = world.getBlockState(pos);
		ItemStack tool = player.getMainHandItem();

		if (!ore.is(ConventionalBlockTags.ORES) || !tool.isCorrectToolForDrops(ore)) {
			return destroy.destroy(pos);
		}

		Set<TagKey<Block>> kinds = oreKinds(ore);
		// One block past the cap, to tell a vein the cap cut short from one that ends right there.
		int max = TalentValues.QUARRY_MAX_BLOCKS - 1;
		List<BlockPos> vein = vein(pos, other -> world.isLoaded(other) && isSameOre(world.getBlockState(other), ore, kinds),
				reach(pos, level, player.getEyePosition()), max + 1);
		boolean capped = vein.size() > max;

		if (capped) {
			vein.removeLast();
		}

		quarrying = true;
		dropLevel = world;
		dropPoint = player.position();

		try {
			if (!destroy.destroy(pos)) {
				return false;
			}

			for (BlockPos next : vein) {
				ItemStack held = player.getMainHandItem();

				// The player switched to something else, or the tool is gone: nothing to say, they know.
				if (held != tool || held.isEmpty()) {
					return true;
				}

				if (!canWear(held.getMaxDamage(), held.getDamageValue())) {
					player.sendOverlayMessage(Component.translatable("message.imsotalented.quarry_stopped_tool", held.getHoverName()));
					return true;
				}

				// Skipped: a block in a chunk that unloaded since the search, one something changed since (a block
				// update), a kind the tool can't mine (nether gold ore and gold ore share a tag), or one where the player
				// may not build (spawn protection).
				if (world.isLoaded(next)) {
					BlockState state = world.getBlockState(next);

					if (isSameOre(state, ore, kinds) && held.isCorrectToolForDrops(state) && world.mayInteract(player, next)) {
						player.gameMode.destroyBlock(next);
					}
				}
			}

			if (capped) {
				player.sendOverlayMessage(Component.translatable("message.imsotalented.quarry_stopped_cap", TalentValues.QUARRY_MAX_BLOCKS));
			}

			return true;
		} finally {
			quarrying = false;
			dropLevel = null;
			dropPoint = null;
		}
	}

	/**
	 * From {@code BlockMixin}: moves an item a block drops in this level to the running quarry's drop point, the
	 * player's feet, standing still so it stays there. Does nothing when no quarry runs.
	 */
	public static void gather(Level level, Entity drop) {
		Vec3 point = gatherPoint(level);

		if (point != null) {
			drop.setPos(point.x, point.y, point.z);
			drop.setDeltaMovement(Vec3.ZERO);
		}
	}

	/** Where a block's experience goes: the running quarry's drop point, or where vanilla puts it. */
	public static Vec3 experiencePoint(Level level, Vec3 vanilla) {
		Vec3 point = gatherPoint(level);
		return point != null ? point : vanilla;
	}

	private static @Nullable Vec3 gatherPoint(Level level) {
		return quarrying && level == dropLevel ? dropPoint : null;
	}

	/** The ore's {@code c:ores/<kind>} tags: iron ore's is {@code c:ores/iron}, which deepslate iron ore shares. */
	private static Set<TagKey<Block>> oreKinds(BlockState ore) {
		Set<TagKey<Block>> kinds = new HashSet<>();

		ore.typeHolder().tags()
				.filter(tag -> tag.location().getNamespace().equals("c") && tag.location().getPath().startsWith(ORE_KIND_PREFIX))
				.forEach(kinds::add);

		return kinds;
	}

	private static boolean isSameOre(BlockState state, BlockState ore, Set<TagKey<Block>> kinds) {
		if (state.is(ore.getBlock())) {
			return true;
		}

		if (!state.is(ConventionalBlockTags.ORES)) {
			return false;
		}

		for (TagKey<Block> kind : kinds) {
			if (state.is(kind)) {
				return true;
			}
		}

		return false;
	}
}
