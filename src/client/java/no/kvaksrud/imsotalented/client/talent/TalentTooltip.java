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
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;

/**
 * The hover text for a talent node: what it does, what the next level adds, what it costs (a point of its tree), and
 * why it can't be bought if it can't. Every line is word-wrapped to {@link #MAX_WIDTH} so the tooltip stays narrow next
 * to the tree.
 */
public final class TalentTooltip {
	private static final int MAX_WIDTH = 170;

	private TalentTooltip() {
	}

	public static List<FormattedCharSequence> lines(Font font, Player player, Talent talent) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		TalentData data = Talents.data(player);
		int level = data.level(talent);
		int max = talent.maxLevel();

		add(lines, font, Component.translatable(talent.translationKey()).withStyle(ChatFormatting.WHITE));
		add(lines, font, Component.translatable(talent.descriptionKey()).withStyle(ChatFormatting.GRAY));
		lines.add(FormattedCharSequence.EMPTY);
		add(lines, font, Component.translatable("screen.imsotalented.level", level, max).withStyle(ChatFormatting.GRAY));

		if (level > 0) {
			add(lines, font, Component.translatable("screen.imsotalented.effect_now", effect(talent, level)).withStyle(ChatFormatting.WHITE));
		}

		if (level < max) {
			add(lines, font, Component.translatable("screen.imsotalented.effect_next", effect(talent, level + 1)).withStyle(ChatFormatting.AQUA));
			add(lines, font, priceLine(player, talent.tree()));
		}

		switch (Talents.canBuy(player, talent)) {
			case MAXED -> add(lines, font, Component.translatable("screen.imsotalented.maxed").withStyle(ChatFormatting.GOLD));
			case LOCKED -> add(lines, font, Component.translatable("screen.imsotalented.requires", Talents.unmetPrereqNames(data, talent)).withStyle(ChatFormatting.RED));
			case BOUGHT -> add(lines, font, Component.translatable("screen.imsotalented.click_to_buy").withStyle(ChatFormatting.GREEN));
			// The price line above already says when the next point comes.
			case NO_POINTS -> {
			}
		}

		// The toggle control: a toggleable talent the player owns, outside creative (talents are always on there).
		if (talent.toggleable() && level > 0 && !player.hasInfiniteMaterials()) {
			boolean off = data.isDisabled(talent);

			if (off) {
				add(lines, font, Component.translatable("screen.imsotalented.toggled_off").withStyle(ChatFormatting.RED));
			}

			add(lines, font, Component.translatable(off ? "screen.imsotalented.toggle_hint_on" : "screen.imsotalented.toggle_hint").withStyle(ChatFormatting.GRAY));
		}

		return lines;
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

		if (player.hasInfiniteMaterials()) {
			return Component.translatable("screen.imsotalented.free_in_creative").withStyle(ChatFormatting.YELLOW);
		}

		if (Talents.points(player, tree) > 0) {
			return Component.translatable("screen.imsotalented.cost", treeName).withStyle(ChatFormatting.YELLOW);
		}

		int nextPointAt = Talents.nextPointLevel(player, tree);
		return Component.translatable("screen.imsotalented.no_points", treeName, nextPointAt).withStyle(ChatFormatting.RED);
	}

	private static void add(List<FormattedCharSequence> lines, Font font, Component text) {
		lines.addAll(font.split(text, MAX_WIDTH));
	}
}
