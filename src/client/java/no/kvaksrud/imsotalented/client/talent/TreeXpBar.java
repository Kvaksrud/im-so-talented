package no.kvaksrud.imsotalented.client.talent;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * A tree's XP bar: the vanilla XP bar's empty track with the filled part in the tree's color, so it reads as an XP bar
 * next to the vanilla one without a texture of its own. Used by the talent screen's header and the HUD.
 */
public final class TreeXpBar {
	public static final int HEIGHT = 5;
	/** The vanilla track is this wide; a shorter bar keeps its left part and its right end cap. */
	private static final int TRACK_WIDTH = 182;
	private static final Identifier TRACK_SPRITE = Identifier.withDefaultNamespace("hud/experience_bar_background");
	/** The fill's top, middle and bottom rows: the vanilla progress sprite is brightest in the middle, too. */
	private static final float TOP_ROW_SHADE = 0.85F;
	private static final float MIDDLE_ROW_LIGHTEN = 0.35F;
	private static final float BOTTOM_ROW_SHADE = 0.6F;
	private static final int WHITE = 0xFFFFFFFF;

	private TreeXpBar() {
	}

	/**
	 * Draws the bar with its top left corner at {@code x}, {@code y}, {@code width} wide (at most 182), filled to
	 * {@code fraction} in {@code color} (ARGB).
	 */
	public static void extract(GuiGraphicsExtractor graphics, int x, int y, int width, float fraction, int color) {
		int trackWidth = Math.min(width, TRACK_WIDTH);

		if (trackWidth == TRACK_WIDTH) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TRACK_SPRITE, x, y, TRACK_WIDTH, HEIGHT);
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TRACK_SPRITE, TRACK_WIDTH, HEIGHT, 0, 0, x, y, trackWidth - 1, HEIGHT);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TRACK_SPRITE, TRACK_WIDTH, HEIGHT, TRACK_WIDTH - 1, 0, x + trackWidth - 1, y, 1, HEIGHT);
		}

		// The track's outline stays: only its three inner rows fill, between the end caps. Rounded down, so it's only
		// full at the top.
		int filled = (int) (Math.clamp(fraction, 0.0F, 1.0F) * (trackWidth - 2));

		if (filled <= 0) {
			return;
		}

		int left = x + 1;
		int right = left + filled;
		int opaque = ARGB.opaque(color);
		graphics.fill(left, y + 1, right, y + 2, ARGB.scaleRGB(opaque, TOP_ROW_SHADE));
		graphics.fill(left, y + 2, right, y + 3, ARGB.srgbLerp(MIDDLE_ROW_LIGHTEN, opaque, WHITE));
		graphics.fill(left, y + 3, right, y + 4, ARGB.scaleRGB(opaque, BOTTOM_ROW_SHADE));
	}
}
