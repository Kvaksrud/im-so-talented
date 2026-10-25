package no.kvaksrud.imsotalented.effect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * The seven classic farm crops that Bountiful Harvest, Fast Growth and Gatherer's crop XP work with: wheat, carrots,
 * potatoes, beetroots, nether wart, the sweet berry bush and cocoa. Anything else that grows is left alone, including
 * torchflowers and other blocks that are crops in code too.
 *
 * <p>A harvest is recognised by its loot table's key, taken from the crop block itself, so a datapack that replaces a
 * table keeps the talent working and another namespace's {@code blocks/wheat} doesn't get it. Picking a sweet berry
 * bush by hand is a harvest of its own, rolled from {@code harvest/sweet_berry_bush}.
 *
 * <p>The tables are built the first time a crop grows or a loot table rolls, which is always after the game has
 * registered its blocks and items. {@link #isRipe(int, int)} needs no game.
 */
public final class Crops {
	/** A sweet berry bush has berries to pick from this age on: vanilla lets a player pick it above age 1. */
	public static final int BERRY_PICK_AGE = 2;

	private Crops() {
	}

	/**
	 * One kind of crop harvest: the crop, its main item, and the age from which the crop counts as ripe. For breaking a
	 * crop that's its top age: a young carrot, potato, nether wart or cocoa pod drops one of its item too, and paying for
	 * those would pay for planting and breaking the same item over and over. For picking berries it's
	 * {@link #BERRY_PICK_AGE}, since picking leaves the bush in place.
	 *
	 * @param picked whether a player picks it by hand (the loot context's {@code interacting_entity}) instead of breaking
	 *               it ({@code this_entity})
	 */
	public record Harvest(Block crop, Item main, IntegerProperty age, int ripeAge, boolean picked) {
		/** Whether the harvested crop was ripe: this harvest's crop, at its ripe age or past it. */
		public boolean isRipe(@Nullable BlockState state) {
			return state != null && state.is(this.crop) && Crops.isRipe(state.getValue(this.age), this.ripeAge);
		}

		/** The player who broke or picked the crop; null for explosions, mobs and anything else that isn't a player. */
		public @Nullable ServerPlayer harvester(LootContext context) {
			return context.getOptional(this.picked ? LootContextParams.INTERACTING_ENTITY : LootContextParams.THIS_ENTITY)
					instanceof ServerPlayer player ? player : null;
		}
	}

	/** Whether a crop at {@code age} is ripe, for one that ripens at {@code ripeAge}. */
	public static boolean isRipe(int age, int ripeAge) {
		return age >= ripeAge;
	}

	/** The harvest this loot table rolls, or null for any other table. */
	public static @Nullable Harvest harvest(ResourceKey<LootTable> table) {
		return Tables.BY_TABLE.get(table);
	}

	/**
	 * Whether Fast Growth may speed this block up: one of the seven, short of its top age. Cocoa rolls for growth before
	 * it checks its age, so a fully grown pod's roll is left to vanilla: no player scan and no extra draw.
	 */
	public static boolean canGrow(BlockState state) {
		Harvest harvest = Tables.BY_BLOCK.get(state.getBlock());
		return harvest != null && !harvest.isRipe(state);
	}

	/** The harvests, built on first use: by the crop's block (breaking it) and by loot table (breaking and picking). */
	private static final class Tables {
		static final Map<Block, Harvest> BY_BLOCK;
		static final Map<ResourceKey<LootTable>, Harvest> BY_TABLE;

		static {
			List<Harvest> broken = List.of(
					broken(Blocks.WHEAT, Items.WHEAT, CropBlock.AGE),
					broken(Blocks.CARROTS, Items.CARROT, CropBlock.AGE),
					broken(Blocks.POTATOES, Items.POTATO, CropBlock.AGE),
					broken(Blocks.BEETROOTS, Items.BEETROOT, BeetrootBlock.AGE),
					broken(Blocks.NETHER_WART, Items.NETHER_WART, NetherWartBlock.AGE),
					broken(Blocks.SWEET_BERRY_BUSH, Items.SWEET_BERRIES, SweetBerryBushBlock.AGE),
					broken(Blocks.COCOA, Items.COCOA_BEANS, CocoaBlock.AGE));
			Map<Block, Harvest> byBlock = new HashMap<>();
			Map<ResourceKey<LootTable>, Harvest> byTable = new HashMap<>();

			for (Harvest harvest : broken) {
				byBlock.put(harvest.crop(), harvest);
				harvest.crop().getLootTable().ifPresent(table -> byTable.put(table, harvest));
			}

			byTable.put(BuiltInLootTables.HARVEST_SWEET_BERRY_BUSH,
					new Harvest(Blocks.SWEET_BERRY_BUSH, Items.SWEET_BERRIES, SweetBerryBushBlock.AGE, BERRY_PICK_AGE, true));
			BY_BLOCK = Map.copyOf(byBlock);
			BY_TABLE = Map.copyOf(byTable);
		}

		private Tables() {
		}

		/** Breaking the crop, ripe at the top of its age property. */
		private static Harvest broken(Block crop, Item main, IntegerProperty age) {
			return new Harvest(crop, main, age, age.getPossibleValues().getLast(), false);
		}
	}
}
