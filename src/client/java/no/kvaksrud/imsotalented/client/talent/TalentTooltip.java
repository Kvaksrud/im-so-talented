package no.kvaksrud.imsotalented.client.talent;

import java.util.ArrayList;
import java.util.List;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentData;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;

/**
 * The hover text for a talent node: what it does, what the next level adds, what it costs (a point of its tree), and
 * why it can't be bought if it can't. A talent that isn't in the game yet says so instead of offering itself. Every
 * line is word-wrapped to {@link #MAX_WIDTH} so the tooltip stays narrow next to the tree. The same text, unwrapped,
 * is what the narrator reads for the node.
 */
public final class TalentTooltip {
	/** Tooltip lines wrap at this width, like vanilla's widget tooltips; the talent screen's other tooltips use it too. */
	public static final int MAX_WIDTH = 170;

	private TalentTooltip() {
	}

	/**
	 * The tooltip's paragraphs, with an empty component for the blank line under the description.
	 *
	 * @param live whether the server has the mod; without it the lines about buying and toggling are left out, since
	 *             nothing can be bought or toggled there
	 */
	public static List<Component> text(Player player, Talent talent, boolean live) {
		List<Component> text = new ArrayList<>();
		TalentData data = Talents.data(player);
		int level = data.level(talent);
		int max = talent.maxLevel();

		text.add(Component.translatable(talent.translationKey()).withStyle(ChatFormatting.WHITE));
		text.add(Component.translatable(talent.descriptionKey()).withStyle(ChatFormatting.GRAY));
		text.add(CommonComponents.EMPTY);
		text.add(Component.translatable("screen.imsotalented.level", level, max).withStyle(ChatFormatting.GRAY));

		if (level > 0) {
			text.add(Component.translatable("screen.imsotalented.effect_now", effect(talent, level)).withStyle(ChatFormatting.WHITE));
		}

		if (level < max) {
			text.add(Component.translatable("screen.imsotalented.effect_next", effect(talent, level + 1)).withStyle(ChatFormatting.AQUA));
		}

		if (!talent.implemented()) {
			// Not for sale and no toggle, whatever its level.
			text.add(Component.translatable("screen.imsotalented.not_yet").withStyle(ChatFormatting.RED));
			return text;
		}

		if (!live) {
			return text;
		}

		if (level < max) {
			text.add(priceLine(player, talent.tree()));
		}

		switch (Talents.canBuy(player, talent)) {
			case MAXED -> text.add(Component.translatable("screen.imsotalented.maxed").withStyle(ChatFormatting.GOLD));
			case LOCKED -> text.add(Component.translatable("screen.imsotalented.requires", Talents.unmetPrereqNames(data, talent)).withStyle(ChatFormatting.RED));
			case BUYABLE -> text.add(Component.translatable("screen.imsotalented.click_to_buy").withStyle(ChatFormatting.GREEN));
			// The price line above already says when the next point comes, and an implemented talent is never NOT_YET.
			case NO_POINTS, NOT_YET -> {
			}
		}

		// The toggle control: a toggleable talent the player owns, in every game mode.
		if (Talents.canToggle(player, talent)) {
			boolean off = data.isDisabled(talent);

			if (off) {
				text.add(Component.translatable("screen.imsotalented.toggled_off").withStyle(ChatFormatting.RED));
			}

			text.add(Component.translatable(off ? "screen.imsotalented.toggle_hint_on" : "screen.imsotalented.toggle_hint").withStyle(ChatFormatting.GRAY));
		}

		return text;
	}

	/** The paragraphs word-wrapped to {@link #MAX_WIDTH}, as the tooltip shows them. */
	public static List<FormattedCharSequence> lines(Font font, List<Component> text) {
		List<FormattedCharSequence> lines = new ArrayList<>();

		for (Component paragraph : text) {
			if (paragraph.getString().isEmpty()) {
				lines.add(FormattedCharSequence.EMPTY);
			} else {
				lines.addAll(font.split(paragraph, MAX_WIDTH));
			}
		}

		return lines;
	}

	/** The paragraphs as the narrator reads them, one after another without the blank line. */
	public static Component narration(List<Component> text) {
		return CommonComponents.joinForNarration(text.stream()
				.filter(paragraph -> !paragraph.getString().isEmpty())
				.toArray(Component[]::new));
	}

	/** What the talent does at this level, like "+12% damage". */
	public static Component effect(Talent talent, int level) {
		return Component.translatable(TalentValues.effectKey(talent, level), TalentValues.effectArgs(talent, level));
	}

	/**
	 * The price of the next level: one point of the talent's tree, free in creative, or when the tree's next point
	 * comes if there's none to spend. That's the level after the points already spent, which is the next tree level
	 * unless a command has set talent levels above what the tree's XP pays for.
	 */
	private static Component priceLine(Player player, TalentTree tree) {
		Component treeName = Component.translatable(tree.translationKey());

		if (Talents.isCreative(player)) {
			return Component.translatable("screen.imsotalented.free_in_creative").withStyle(ChatFormatting.YELLOW);
		}

		if (Talents.points(player, tree) > 0) {
			return Component.translatable("screen.imsotalented.cost", treeName).withStyle(ChatFormatting.YELLOW);
		}

		int nextPointAt = Talents.nextPointLevel(player, tree);
		return Component.translatable("screen.imsotalented.no_points", treeName, nextPointAt).withStyle(ChatFormatting.RED);
	}
}
