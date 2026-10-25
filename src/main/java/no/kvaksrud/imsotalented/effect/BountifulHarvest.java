package no.kvaksrud.imsotalented.effect;

import java.util.List;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

/**
 * Bountiful Harvest: a player with the talent gets {@link TalentValues#harvestBonus} extra copies of a ripe crop's main
 * item, one per level (at level 3 mature wheat drops 4 instead of 1). It works on the {@link Crops seven classic crops},
 * broken or, for the sweet berry bush, picked by hand.
 *
 * <p>Only a ripe crop gets it ({@link Crops.Harvest#isRipe}): a young carrot, potato, nether wart or cocoa pod drops one
 * of its item too, and a bonus on those would turn planting and breaking into endless items. And only when the harvest
 * produced the main item at all, so a table a datapack changed never conjures it. Seeds, side drops and everything
 * else keep their vanilla counts.
 *
 * <p>Read from the harvesting player in the loot context when the table rolls, server side (loot never runs on the
 * client): explosions, animals and players without the talent get nothing. The same roll pays Gatherer's crop XP
 * ({@link TreeXp#onCropHarvest}), with the same ripeness.
 */
public final class BountifulHarvest {
	private BountifulHarvest() {
	}

	/** Registers the loot drop listener; called from {@link TalentEffects#init}. */
	static void init() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Crops.Harvest harvest = table.unwrapKey().map(Crops::harvest).orElse(null);

			if (harvest != null) {
				onHarvest(harvest, context, drops);
			}
		});
	}

	/** The extra main-item copies the talent at this level adds to a ripe harvest; nothing at level 0 or below. */
	public static int extraItems(int level) {
		return level > 0 ? TalentValues.harvestBonus(level) : 0;
	}

	/**
	 * The extra copies for one harvest: the level's {@link #extraItems} when the crop was ripe and the harvest dropped
	 * at least one of its main item, otherwise none.
	 *
	 * @param mainCount how many of the main item the harvest dropped
	 */
	public static int bonusFor(int mainCount, boolean ripe, int level) {
		return mainCount > 0 && ripe ? extraItems(level) : 0;
	}

	private static void onHarvest(Crops.Harvest harvest, LootContext context, List<ItemStack> drops) {
		ServerPlayer player = harvest.harvester(context);

		if (player == null) {
			return;
		}

		int mainCount = 0;

		for (ItemStack stack : drops) {
			if (stack.is(harvest.main())) {
				mainCount += stack.getCount();
			}
		}

		boolean ripe = harvest.isRipe(context.getOptional(LootContextParams.BLOCK_STATE));
		// Counted before the bonus goes in, so the XP is the same with or without the talent.
		TreeXp.onCropHarvest(player, mainCount, ripe);
		int extra = bonusFor(mainCount, ripe, Talents.effectiveLevel(player, Talent.BOUNTIFUL_HARVEST));

		if (extra > 0) {
			drops.add(new ItemStack(harvest.main(), extra));
		}
	}
}
