package no.kvaksrud.imsotalented.inventory;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.talent.TalentValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * A player's backpack: always {@link TalentValues#MAX_BACKPACK_SLOTS} slots, of which the Backpack talent
 * unlocks the first few. Locked slots stay empty: shrinking the talent moves their items out.
 *
 * <p>It is a mutable container kept as a data attachment, so the open menu, picked-up overflow and the death drops all
 * work on this one instance. Fabric writes every persistent attachment whenever the player is saved, so changing it in
 * place is enough to have it saved.
 */
public class Backpack extends SimpleContainer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImSoTalented.MOD_ID);

	/**
	 * Saved as a list of the filled slots, like a chest. Reading is lenient on purpose: an item that no longer exists
	 * (a removed mod) is skipped with a warning instead of failing the whole list, which would lose every other item.
	 */
	public static final Codec<Backpack> CODEC = Codec.of(
			ItemStackWithSlot.CODEC.listOf().comap(Backpack::filledSlots),
			Codec.PASSTHROUGH.listOf().map(Backpack::fromSaved));

	public Backpack() {
		super(TalentValues.MAX_BACKPACK_SLOTS);
	}

	/**
	 * Puts as much of the stack as fits into the first {@code slots} slots: first onto matching stacks, then into
	 * empty slots, like picking up into the inventory. The slots after those (the locked ones) are never touched. Shrinks
	 * the stack by exactly what went in, so nothing is duplicated or lost.
	 *
	 * @return how many items went in
	 */
	public int insert(ItemStack stack, int slots) {
		int before = stack.getCount();
		int end = Math.min(slots, this.getContainerSize());

		for (int slot = 0; slot < end && !stack.isEmpty(); slot++) {
			ItemStack existing = this.getItem(slot);

			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				int moved = Math.min(stack.getCount(), this.getMaxStackSize(existing) - existing.getCount());

				if (moved > 0) {
					existing.grow(moved);
					stack.shrink(moved);
				}
			}
		}

		for (int slot = 0; slot < end && !stack.isEmpty(); slot++) {
			if (this.getItem(slot).isEmpty()) {
				this.setItem(slot, stack.split(this.getMaxStackSize(stack)));
			}
		}

		int stored = before - stack.getCount();

		if (stored > 0) {
			this.setChanged();
		}

		return stored;
	}

	private List<ItemStackWithSlot> filledSlots() {
		List<ItemStackWithSlot> filled = new ArrayList<>();

		for (int slot = 0; slot < this.getContainerSize(); slot++) {
			ItemStack stack = this.getItem(slot);

			if (!stack.isEmpty()) {
				filled.add(new ItemStackWithSlot(slot, stack.copy()));
			}
		}

		return filled;
	}

	/** The saved entries, each read on its own; one that can't be read or has no slot here is skipped with its raw data logged. */
	private static Backpack fromSaved(List<Dynamic<?>> saved) {
		Backpack backpack = new Backpack();

		for (Dynamic<?> entry : saved) {
			DataResult<ItemStackWithSlot> result = ItemStackWithSlot.CODEC.parse(entry);
			result.error().ifPresent(error -> LOGGER.warn("Skipping a backpack item that could not be read ({}): {}", error.message(), entry.getValue()));
			result.result().ifPresent(item -> {
				if (item.isValidInContainer(backpack.getContainerSize())) {
					backpack.setItem(item.slot(), item.stack());
				} else {
					LOGGER.warn("Skipping a backpack item in slot {}, past the backpack's {} slots: {}", item.slot(), backpack.getContainerSize(),
							entry.getValue());
				}
			});
		}

		return backpack;
	}
}
