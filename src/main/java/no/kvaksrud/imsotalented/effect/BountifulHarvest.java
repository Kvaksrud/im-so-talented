package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Bountiful Harvest: a player with the talent harvests the crop's main item {@code level} extra times
 * (L5: wheat 1 → 6). The level is read from the breaking player in the loot context's {@code THIS_ENTITY}
 * at loot time — no per-player caching, and explosions, animals and talent-less players get nothing.
 *
 * <p>Only the main item's count is increased, and only when the harvest actually produced one: the 26.3
 * tables of the seven targets produce their main item at the full growth stage (wheat, carrots, potatoes,
 * beetroots; nether wart and the sweet berry bush at their max age), but not every stage of every crop —
 * young wheat produces only seeds, a young sweet berry bush produces nothing, and young carrots, potatoes
 * and nether wart produce a single main item (cocoa produces 1 young / 3 ripe). The bonus is therefore
 * granted only when the rolled drops already contain the main item ({@link #bonusFor(int, int)}): it never
 * conjures the item from a stage that hasn't produced it. Seeds, side drops and everything else keep their
 * vanilla behavior.
 *
 * <p>The wiring is in {@link TalentEffects#init()} (a {@code LootTableEvents.MODIFY_DROPS} listener), which
 * is server-side — loot never runs on the client.
 */
public final class BountifulHarvest {
	private BountifulHarvest() {
	}

	/**
	 * The classic farm harvests, encoded as explicit block id → main-drop item id pairs (the 26.3 ids,
	 * confirmed against {@code data/minecraft/loot_table/blocks/*.json}). Null for blocks that are not
	 * part of the talent. Pure and game-free, so the unit tests exercise it without a running game.
	 */
	public static String mainDropId(String blockId) {
		return switch (blockId) {
			case "wheat" -> "wheat";
			case "carrots" -> "carrot";
			case "potatoes" -> "potato";
			case "beetroots" -> "beetroot";
			case "nether_wart" -> "nether_wart";
			case "sweet_berry_bush" -> "sweet_berries";
			case "cocoa" -> "cocoa_beans";
			default -> null;
		};
	}

	/** The main-drop item for a block id, resolved from the item registry; null when the block is not targeted. */
	public static Item mainDropOf(String blockId) {
		String itemId = mainDropId(blockId);

		if (itemId == null) {
			return null;
		}

		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("minecraft", itemId)).orElse(null);
	}

	/** The main-drop item for a block loot table path (such as {@code blocks/wheat}); null for other tables. */
	public static Item mainDropForTablePath(String tablePath) {
		if (tablePath == null || !tablePath.startsWith("blocks/")) {
			return null;
		}

		return mainDropOf(tablePath.substring("blocks/".length()));
	}

	/** Pure: the extra main-item copies this talent level adds per harvest; level 0 (and negatives) add nothing. */
	public static int extraItems(int level) {
		return level > 0 ? TalentValues.harvestBonus(level) : 0;
	}

	/**
	 * Pure: the bonus copies for a harvest that produced {@code mainCount} copies of the main item — the level's
	 * bonus only when the harvest actually produced at least one (a young sweet berry bush produces nothing and
	 * a young wheat produces only seeds, so neither gets a bonus); 0 at level 0 or for a non-positive count.
	 */
	public static int bonusFor(int mainCount, int level) {
		return mainCount > 0 && level > 0 ? TalentValues.harvestBonus(level) : 0;
	}

	/**
	 * The bonus for this loot roll: the breaking player's level, in extra copies. Reads the breaking entity
	 * ({@code THIS_ENTITY}) from the context, the same way the More Luck loot mixins read it; a non-player
	 * breaker (explosion, mob, no entity) or a player without the talent adds nothing.
	 */
	public static int extraItems(LootContext context) {
		if (context.getOptional(LootContextParams.THIS_ENTITY) instanceof Player player) {
			return extraItems(Talents.level(player, Talent.BOUNTIFUL_HARVEST));
		}

		return 0;
	}
}
