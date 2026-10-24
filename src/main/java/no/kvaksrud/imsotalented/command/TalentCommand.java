package no.kvaksrud.imsotalented.command;

import java.util.Arrays;
import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.effect.TreeXp;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentData;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * /talents for operators (permission level 2): look at, set, max out, reset or toggle a player's talents, and give or
 * set tree XP for testing. Talent levels set here cost no talent points; buying is done in the talent tree.
 */
public final class TalentCommand {
	private static final DynamicCommandExceptionType UNKNOWN_TALENT = new DynamicCommandExceptionType(
			id -> Component.translatable("commands.imsotalented.unknown_talent", id));

	private static final DynamicCommandExceptionType UNKNOWN_TREE = new DynamicCommandExceptionType(
			id -> Component.translatable("commands.imsotalented.unknown_tree", id));

	private static final DynamicCommandExceptionType NOT_TOGGLEABLE = new DynamicCommandExceptionType(
			id -> Component.translatable("commands.imsotalented.not_toggleable", id));

	private static final SuggestionProvider<CommandSourceStack> TALENTS = (context, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Talent.values()).map(Talent::id), builder);

	private static final SuggestionProvider<CommandSourceStack> TREES = (context, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(TalentTree.values()).map(TalentTree::id), builder);

	private TalentCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("talents")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> list(context.getSource(), EntityArgument.getPlayer(context, "player")))))
				.then(Commands.literal("set")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("talent", StringArgumentType.word())
										.suggests(TALENTS)
										.then(Commands.argument("level", IntegerArgumentType.integer(0))
												.executes(TalentCommand::set)))))
				.then(Commands.literal("max")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> max(context.getSource(), EntityArgument.getPlayers(context, "targets")))))
				.then(Commands.literal("reset")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> reset(context.getSource(), EntityArgument.getPlayers(context, "targets")))))
				.then(Commands.literal("toggle")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("talent", StringArgumentType.word())
										.suggests(TALENTS)
										// Without on/off the command flips each target's current state.
										.executes(context -> toggle(context, null))
										.then(Commands.literal("on").executes(context -> toggle(context, true)))
										.then(Commands.literal("off").executes(context -> toggle(context, false))))))
				.then(Commands.literal("xp")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("tree", StringArgumentType.word())
										.suggests(TREES)
										.then(Commands.literal("add")
												.then(Commands.argument("amount", LongArgumentType.longArg(0))
														.executes(context -> xp(context, false))))
										.then(Commands.literal("set")
												.then(Commands.argument("amount", LongArgumentType.longArg(0))
														.executes(context -> xp(context, true))))))));
	}

	/** Each tree's level, XP and points to spend, with its talents under it. Returns the talent points spent. */
	private static int list(CommandSourceStack source, ServerPlayer player) {
		TalentData data = Talents.data(player);
		MutableComponent text = Component.translatable("commands.imsotalented.list", player.getDisplayName());

		for (TalentTree tree : TalentTree.values()) {
			text.append("\n").append(Component.translatable("commands.imsotalented.list.tree",
					Talents.treeName(tree), Talents.treeLevel(player, tree), data.xp(tree), Talents.points(player, tree)));

			for (Talent talent : tree.talents()) {
				text.append("\n").append(Component.translatable("commands.imsotalented.list.entry",
						Component.translatable(talent.translationKey()), data.level(talent), talent.maxLevel()));
			}
		}

		source.sendSuccess(() -> text, false);
		return data.pointsSpent();
	}

	private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		String id = StringArgumentType.getString(context, "talent");
		Talent talent = Talent.byId(id).orElseThrow(() -> UNKNOWN_TALENT.create(id));
		int level = Math.min(IntegerArgumentType.getInteger(context, "level"), talent.maxLevel());

		for (ServerPlayer player : targets) {
			Talents.setLevel(player, talent, level);
		}

		context.getSource().sendSuccess(() -> Component.translatable("commands.imsotalented.set",
				Component.translatable(talent.translationKey()), level, targets.size()), true);
		return targets.size();
	}

	/**
	 * Every talent at its top level and every tree at its top level, so the XP bars read "max" too. Toggles are cleared
	 * (everything on). No level-up messages: they'd be four lines of noise per player.
	 */
	private static int max(CommandSourceStack source, Collection<ServerPlayer> targets) {
		TalentRules rules = ImSoTalented.config().rules();
		TalentData maxed = TalentData.EMPTY;

		for (Talent talent : Talent.values()) {
			maxed = maxed.with(talent, talent.maxLevel());
		}

		for (TalentTree tree : TalentTree.values()) {
			maxed = maxed.withXp(tree, rules.xpForLevel(tree.maxLevel()));
		}

		for (ServerPlayer player : targets) {
			Talents.setData(player, maxed);
		}

		source.sendSuccess(() -> Component.translatable("commands.imsotalented.max", targets.size()), true);
		return targets.size();
	}

	/**
	 * Back to a new player: no talents, no toggles, no tree XP. The chunks Explorer has paid for are forgotten too, and
	 * so are the fractions of a point carried over, so everything can be earned again from scratch.
	 */
	private static int reset(CommandSourceStack source, Collection<ServerPlayer> targets) {
		for (ServerPlayer player : targets) {
			Talents.setData(player, TalentData.EMPTY);
			player.removeAttached(TreeXp.VISITED_CHUNKS);
			// The next tick starts measuring afresh, so the chunk the player stands in pays again too.
			player.removeAttached(TreeXp.TRAVEL);
			player.removeAttached(ImSoTalented.TREE_XP_FRACTIONS);
		}

		source.sendSuccess(() -> Component.translatable("commands.imsotalented.reset", targets.size()), true);
		return targets.size();
	}

	/** Toggles a talent on/off for every target, or flips each target's current state when on/off is omitted. */
	private static int toggle(CommandContext<CommandSourceStack> context, Boolean on) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		String id = StringArgumentType.getString(context, "talent");
		Talent talent = Talent.byId(id).orElseThrow(() -> UNKNOWN_TALENT.create(id));

		if (!talent.toggleable()) {
			throw NOT_TOGGLEABLE.create(id);
		}

		for (ServerPlayer player : targets) {
			// Without on/off each target flips: a disabled (off) talent goes on, an enabled one goes off.
			Talents.toggle(player, talent, on == null ? Talents.data(player).isDisabled(talent) : on);
		}

		context.getSource().sendSuccess(() -> Component.translatable("commands.imsotalented.toggle",
				Component.translatable(talent.translationKey()), targets.size()), true);
		return targets.size();
	}

	/**
	 * Gives or sets a tree's XP, with the level-up message when it crosses into a new level. Either way the tree stops at
	 * its top level: adding more does nothing, and setting more sets the top.
	 */
	private static int xp(CommandContext<CommandSourceStack> context, boolean set) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		String id = StringArgumentType.getString(context, "tree");
		TalentTree tree = TalentTree.byId(id).orElseThrow(() -> UNKNOWN_TREE.create(id));
		long amount = LongArgumentType.getLong(context, "amount");

		if (set) {
			long xp = Math.min(amount, ImSoTalented.config().rules().xpForLevel(tree.maxLevel()));

			for (ServerPlayer player : targets) {
				Talents.setXp(player, tree, xp);
			}

			context.getSource().sendSuccess(() -> Component.translatable("commands.imsotalented.xp_set",
					Talents.treeName(tree), xp, targets.size()), true);
		} else {
			for (ServerPlayer player : targets) {
				Talents.addXp(player, tree, amount);
			}

			context.getSource().sendSuccess(() -> Component.translatable("commands.imsotalented.xp_add",
					amount, Talents.treeName(tree), targets.size()), true);
		}

		return targets.size();
	}
}
