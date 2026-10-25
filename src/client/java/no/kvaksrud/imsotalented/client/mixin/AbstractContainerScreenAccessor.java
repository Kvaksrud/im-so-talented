package no.kvaksrud.imsotalented.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Where a container screen's panel is and how wide: it moves when the recipe book opens, and there's no public getter. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int imsotalented$leftPos();

	@Accessor("topPos")
	int imsotalented$topPos();

	@Accessor("imageWidth")
	int imsotalented$imageWidth();
}
