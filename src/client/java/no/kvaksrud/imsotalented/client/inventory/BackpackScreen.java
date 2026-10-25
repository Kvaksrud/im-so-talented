package no.kvaksrud.imsotalented.client.inventory;

import no.kvaksrud.imsotalented.client.BackpackClient;
import no.kvaksrud.imsotalented.inventory.BackpackMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The backpack, drawn like a four-row chest. Locked slots are greyed out with a padlock and explain themselves
 * on hover; the title row shows how many slots are unlocked.
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
	private static final Identifier CONTAINER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	/** 18x18: the crafter's disabled slot, a flat grey square over the slot's frame. */
	private static final Identifier LOCKED_SLOT_SPRITE = Identifier.withDefaultNamespace("container/crafter/disabled_slot");
	/** 10x14: the cartography table's padlock. */
	private static final Identifier LOCK_SPRITE = Identifier.withDefaultNamespace("container/cartography_table/locked");
	private static final int LOCK_WIDTH = 10;
	private static final int LOCK_HEIGHT = 14;
	private static final int LABEL_COLOR = 0xFF404040;
	private static final Component LOCKED_TOOLTIP = Component.translatable("container.imsotalented.backpack.locked_slot");

	public BackpackScreen(BackpackMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, DEFAULT_IMAGE_WIDTH, 114 + BackpackMenu.ROWS * BackpackMenu.SLOT_SIZE);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int gridHeight = BackpackMenu.ROWS * BackpackMenu.SLOT_SIZE + 17;
		// The chest texture's top (title and rows) and bottom (player inventory) halves, as the vanilla chest screen does.
		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, gridHeight, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, this.leftPos, this.topPos + gridHeight, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);

		for (int i = this.menu.unlockedSlots(); i < BackpackMenu.BACKPACK_SLOTS; i++) {
			Slot slot = this.menu.getSlot(i);
			int x = this.leftPos + slot.x;
			int y = this.topPos + slot.y;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCKED_SLOT_SPRITE, x - 1, y - 1, 18, 18);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_SPRITE, x + (16 - LOCK_WIDTH) / 2, y + (16 - LOCK_HEIGHT) / 2, LOCK_WIDTH, LOCK_HEIGHT);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		Component unlocked = Component.translatable("container.imsotalented.backpack.unlocked", this.menu.unlockedSlots(), BackpackMenu.BACKPACK_SLOTS);
		graphics.text(this.font, unlocked, this.imageWidth - this.titleLabelX - this.font.width(unlocked), this.titleLabelY, LABEL_COLOR, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		// Locked slots are inactive, so the menu has no hovered slot and no tooltip for them; explain them here.
		if (this.menu.getCarried().isEmpty() && this.isOverLockedSlot(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, LOCKED_TOOLTIP, mouseX, mouseY);
		}
	}

	/**
	 * The backpack's own key, if the player bound one, closes it again. The inventory key (with or without Shift)
	 * already does, as it does for every container.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (BackpackClient.OPEN_BACKPACK.matches(event)) {
			this.onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	private boolean isOverLockedSlot(int mouseX, int mouseY) {
		for (int i = this.menu.unlockedSlots(); i < BackpackMenu.BACKPACK_SLOTS; i++) {
			Slot slot = this.menu.getSlot(i);

			if (this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
				return true;
			}
		}

		return false;
	}
}
