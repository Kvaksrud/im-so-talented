package no.kvaksrud.imsotalented.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Overflow into the backpack must never duplicate or lose items, and must leave locked slots alone. */
class BackpackTest {
	/** Items get their components (like max stack size) from the registries a world loads, so build those too. */
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
				.forEach(DataComponentInitializers.PendingComponents::apply);
	}

	@Test
	void oneRowPerLevel() {
		int[] expected = {0, 9, 18, 27, 36};

		for (int level = 0; level <= Talent.BACKPACK.maxLevel(); level++) {
			assertEquals(expected[level], TalentValues.backpackSlots(level), "level " + level);
		}

		assertEquals(TalentValues.MAX_BACKPACK_SLOTS, TalentValues.backpackSlots(99), "clamped to the top level");
		assertEquals(0, TalentValues.backpackSlots(-1));
		assertEquals(TalentValues.MAX_BACKPACK_SLOTS, new Backpack().getContainerSize(), "the top level opens every slot");
	}

	@Test
	void topsUpMatchingStacksBeforeEmptySlots() {
		Backpack inventory = new Backpack();
		inventory.setItem(3, new ItemStack(Items.DIAMOND, 60));
		ItemStack picked = new ItemStack(Items.DIAMOND, 10);

		assertEquals(10, inventory.insert(picked, TalentValues.backpackSlots(1)));
		assertTrue(picked.isEmpty());
		assertEquals(64, inventory.getItem(3).getCount());
		assertEquals(6, inventory.getItem(0).getCount());
		assertTrue(inventory.getItem(1).isEmpty());
	}

	@Test
	void neverUsesLockedSlotsAndShrinksByWhatWentIn() {
		Backpack inventory = new Backpack();
		// Level 1 opens the first row. Slots 1 to 8 are full of something else; slot 9, the second row, is locked.
		for (int slot = 1; slot < 9; slot++) {
			inventory.setItem(slot, new ItemStack(Items.STONE, 64));
		}
		inventory.setItem(9, new ItemStack(Items.DIAMOND, 1));
		ItemStack picked = new ItemStack(Items.DIAMOND, 64 * 2 + 5);

		int stored = inventory.insert(picked, TalentValues.backpackSlots(1));

		assertEquals(64, stored);
		assertEquals(64 + 5, picked.getCount());
		assertEquals(64, inventory.getItem(0).getCount());
		assertEquals(Items.STONE, inventory.getItem(8).getItem());
		assertEquals(1, inventory.getItem(9).getCount(), "the locked stack isn't topped up");
		assertTrue(inventory.getItem(10).isEmpty());
	}

	@Test
	void nothingGoesInWithoutUnlockedSlots() {
		Backpack inventory = new Backpack();
		ItemStack picked = new ItemStack(Items.DIAMOND, 5);

		assertEquals(0, inventory.insert(picked, 0));
		assertEquals(5, picked.getCount());
		assertTrue(inventory.isEmpty());
	}

	@Test
	void unstackableItemsTakeOneSlotEach() {
		Backpack inventory = new Backpack();
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		ItemStack otherSword = new ItemStack(Items.DIAMOND_SWORD);

		assertEquals(1, inventory.insert(sword, 2));
		assertEquals(1, inventory.insert(otherSword, 2));
		assertEquals(0, inventory.insert(new ItemStack(Items.DIAMOND_SWORD), 2));
		assertEquals(1, inventory.getItem(0).getCount());
		assertEquals(1, inventory.getItem(1).getCount());
	}

	@Test
	void differentComponentsDoNotMerge() {
		Backpack inventory = new Backpack();
		ItemStack named = new ItemStack(Items.DIAMOND, 10);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Shiny"));
		inventory.setItem(0, named);

		inventory.insert(new ItemStack(Items.DIAMOND, 10), TalentValues.MAX_BACKPACK_SLOTS);

		assertEquals(10, inventory.getItem(0).getCount());
		assertEquals(10, inventory.getItem(1).getCount());
	}

	@Test
	void savesFilledSlotsAndReadsThemBack() {
		Backpack inventory = new Backpack();
		inventory.setItem(0, new ItemStack(Items.DIAMOND, 12));
		inventory.setItem(35, new ItemStack(Items.NETHERITE_INGOT, 3));

		var saved = Backpack.CODEC.encodeStart(JsonOps.INSTANCE, inventory).getOrThrow();
		assertEquals(2, saved.getAsJsonArray().size());

		Backpack read = Backpack.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		assertEquals(Map.of(0, "minecraft:diamond x12", 35, "minecraft:netherite_ingot x3"), describe(read));
	}

	@Test
	void skipsUnreadableItemsButKeepsTheRest() {
		var saved = JsonOps.INSTANCE.createList(List.of(
				item(0, "minecraft:diamond", 4),
				item(1, "minecraft:no_such_item", 1),
				item(99, "minecraft:diamond", 1),
				item(2, "minecraft:emerald", 7)).stream());

		Backpack read = Backpack.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();

		assertEquals(Map.of(0, "minecraft:diamond x4", 2, "minecraft:emerald x7"), describe(read));
	}

	private static JsonElement item(int slot, String id, int count) {
		JsonObject object = new JsonObject();
		object.addProperty("Slot", slot);
		object.addProperty("id", id);
		object.addProperty("count", count);
		return object;
	}

	private static Map<Integer, String> describe(Backpack inventory) {
		Map<Integer, String> filled = new TreeMap<>();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty()) {
				filled.put(slot, stack.typeHolder().getRegisteredName() + " x" + stack.getCount());
			}
		}

		return filled;
	}
}
