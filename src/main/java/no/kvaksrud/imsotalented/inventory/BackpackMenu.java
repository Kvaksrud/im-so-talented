package no.kvaksrud.imsotalented.inventory;

import java.util.function.IntSupplier;

import no.kvaksrud.imsotalented.talent.TalentValues;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The backpack laid out like a four-row chest: its 36 slots on top, the player's inventory below. Only the first
 * {@link #unlockedSlots()} backpack slots can be used; the rest are locked (hidden from the mouse, and refusing items, so a
 * hacked client can't use them either).
 *
 * <p>The unlocked count travels as a data slot: the server reads it live from the player's talents, so the client's
 * copy follows along if the talents change while the menu is open, and the server closes the menu when none are left.
 */
public class BackpackMenu extends AbstractContainerMenu {
	public static final int COLUMNS = 9;
	public static final int ROWS = TalentValues.MAX_BACKPACK_SLOTS / COLUMNS;
	public static final int BACKPACK_SLOTS = TalentValues.MAX_BACKPACK_SLOTS;
	public static final int SLOT_SIZE = 18;
	/** Where the top left backpack slot's item sits, from the panel's corner; the same as a chest. */
	public static final int GRID_LEFT = 8;
	public static final int GRID_TOP = 18;

	private final Container container;
	private final DataSlot unlockedSlots;

	/** The client's menu: the server fills the slots and the unlocked count. */
	public BackpackMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(BACKPACK_SLOTS), DataSlot.standalone());
	}

	/** The server's menu, on the player's own backpack, with the unlocked count read from their talents. */
	public BackpackMenu(int containerId, Inventory inventory, Container container, IntSupplier unlockedSlots) {
		this(containerId, inventory, container, new DataSlot() {
			@Override
			public int get() {
				return unlockedSlots.getAsInt();
			}

			@Override
			public void set(int value) {
				// Read-only: the server works it out from the talents.
			}
		});
	}

	private BackpackMenu(int containerId, Inventory inventory, Container container, DataSlot unlockedSlots) {
		super(Backpacks.MENU, containerId);
		checkContainerSize(container, BACKPACK_SLOTS);
		this.container = container;
		this.unlockedSlots = this.addDataSlot(unlockedSlots);

		for (int row = 0; row < ROWS; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				this.addSlot(new BackpackSlot(container, column + row * COLUMNS, GRID_LEFT + column * SLOT_SIZE, GRID_TOP + row * SLOT_SIZE));
			}
		}

		this.addStandardInventorySlots(inventory, GRID_LEFT, GRID_TOP + ROWS * SLOT_SIZE + 13);
	}

	/** How many backpack slots, counted from the first, the player can use. */
	public int unlockedSlots() {
		return Math.clamp(this.unlockedSlots.get(), 0, BACKPACK_SLOTS);
	}

	/** Whether this backpack slot (0 to {@link #BACKPACK_SLOTS} - 1, not a menu slot index) can be used. */
	public boolean isUnlocked(int backpackSlot) {
		return backpackSlot < this.unlockedSlots();
	}

	/** Closes (on the server) once the talents no longer unlock any slot, like after a reset. */
	@Override
	public boolean stillValid(Player player) {
		return this.unlockedSlots() > 0 && this.container.stillValid(player);
	}

	/** Shift-click, as in a chest, except that items only go into unlocked backpack slots. */
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);

		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();

			if (slotIndex < BACKPACK_SLOTS) {
				if (!this.moveItemStackTo(stack, BACKPACK_SLOTS, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, this.unlockedSlots(), false)) {
				// The unlocked slots are always the first ones, so this range never reaches a locked slot.
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return clicked;
	}

	/** A backpack slot that only works while the talent unlocks it. */
	private final class BackpackSlot extends Slot {
		BackpackSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		private boolean unlocked() {
			return BackpackMenu.this.isUnlocked(this.getContainerSlot());
		}

		@Override
		public boolean mayPlace(ItemStack itemStack) {
			return this.unlocked() && super.mayPlace(itemStack);
		}

		@Override
		public boolean mayPickup(Player player) {
			return this.unlocked() && super.mayPickup(player);
		}

		/** Inactive slots are neither drawn nor hovered; the screen draws a lock there instead. */
		@Override
		public boolean isActive() {
			return this.unlocked();
		}
	}
}
