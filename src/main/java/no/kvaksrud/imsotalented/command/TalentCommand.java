package no.kvaksrud.imsotalented.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.effect.TreeXpAttachments;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentData;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.Talents;
import org.jspecify.annotations.Nullable;

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
 *
 * <p>Feedback works like vanilla's commands: the line names the player when one was changed and counts them when more
 * were, and only players something happened to count. Targets a toggle or XP gift couldn't change are reported to the
 * operator as failures; the command returns how many players it changed.
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

	private static final SuggestionProvider<CommandSourceStack> TOGGLEABLE_TALENTS = (context, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Talent.values()).filter(TalentCommand::toggleable).map(Talent::id), builder);

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
										.suggests(TOGGLEABLE_TALENTS)
										// Without on/off the command flips each target's current state.
										.executes(context -> toggle(context, null))
										.then(Commands.literal("on").executes(context -> toggle(context, true)))
										.then(Commands.literal("off").executes(context -> toggle(context, false))))))
				.then(Commands.literal("xp")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("tree", StringArgumentType.word())
										.suggests(TREES)
										.then(Commands.literal("add")
												.then(Commands.argument("amount", LongArgumentType.longArg(1))
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

		Component name = Component.translatable(talent.translationKey());
		sendSuccess(context.getSource(), targets,
				player -> Component.translatable("commands.imsotalented.set.single", name, level, player.getDisplayName()),
				count -> Component.translatable("commands.imsotalented.set.multiple", name, level, count));
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

		sendSuccess(source, targets,
				player -> Component.translatable("commands.imsotalented.max.single", player.getDisplayName()),
				count -> Component.translatable("commands.imsotalented.max.multiple", count));
		return targets.size();
	}

	/**
	 * Back to a new player: no talents, no toggles, no tree XP. The chunks Explorer has paid for are forgotten too, and
	 * so is the tree XP ledger (XP earned but not yet committed, and each tree's hourly allowance), so everything can be
	 * earned again from scratch.
	 */
	private static int reset(CommandSourceStack source, Collection<ServerPlayer> targets) {
		for (ServerPlayer player : targets) {
			Talents.setData(player, TalentData.EMPTY);
			player.removeAttached(TreeXpAttachments.VISITED_CHUNKS);
			// The next tick starts measuring afresh, so the chunk the player stands in pays again too.
			player.removeAttached(TreeXpAttachments.TRAVEL);
			player.removeAttached(ImSoTalented.TREE_XP_LEDGER);
		}

		sendSuccess(source, targets,
				player -> Component.translatable("commands.imsotalented.reset.single", player.getDisplayName()),
				count -> Component.translatable("commands.imsotalented.reset.multiple", count));
		return targets.size();
	}

	/** Whether the talent has a toggle at all: toggleable, and implemented (an unimplemented one can't be owned). */
	private static boolean toggleable(Talent talent) {
		return talent.toggleable() && talent.implemented();
	}

	/**
	 * Switches a talent on or off for every target, or flips each target's own state when on/off is omitted. Targets
	 * who don't own the talent are reported to the operator; the targets themselves see nothing.
	 *
	 * @param on the state to set, or null to flip
	 */
	private static int toggle(CommandContext<CommandSourceStack> context, @Nullable Boolean on) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		String id = StringArgumentType.getString(context, "talent");
		Talent talent = Talent.byId(id).orElseThrow(() -> UNKNOWN_TALENT.create(id));

		if (!toggleable(talent)) {
			throw NOT_TOGGLEABLE.create(id);
		}

		Component name = Component.translatable(talent.translationKey());
		List<ServerPlayer> turnedOn = new ArrayList<>();
		List<ServerPlayer> turnedOff = new ArrayList<>();
		List<ServerPlayer> notOwned = new ArrayList<>();

		for (ServerPlayer player : targets) {
			// Without on/off, flip: a talent that is on for this player goes off, and one that is off goes on.
			boolean isOn = !Talents.data(player).isDisabled(talent);
			boolean wanted = on != null ? on : !isOn;

			switch (Talents.toggle(player, talent, wanted, false)) {
				case ON -> turnedOn.add(player);
				case OFF -> turnedOff.add(player);
				case REJECTED -> notOwned.add(player);
			}
		}

		if (turnedOn.isEmpty() && turnedOff.isEmpty()) {
			source.sendFailure(targets.size() == 1
					? Component.translatable("commands.imsotalented.toggle.not_owned", notOwned.getFirst().getDisplayName(), name)
					: Component.translatable("commands.imsotalented.toggle.none", name));
			return 0;
		}

		for (ServerPlayer player : notOwned) {
			source.sendFailure(Component.translatable("commands.imsotalented.toggle.not_owned", player.getDisplayName(), name));
		}

		// A flip can turn the talent on for some targets and off for others: one line for each.
		sendToggled(source, name, true, turnedOn);
		sendToggled(source, name, false, turnedOff);
		return turnedOn.size() + turnedOff.size();
	}

	private static void sendToggled(CommandSourceStack source, Component name, boolean on, List<ServerPlayer> players) {
		if (players.isEmpty()) {
			return;
		}

		Component state = Component.translatable(on ? "commands.imsotalented.on" : "commands.imsotalented.off");
		sendSuccess(source, players,
				player -> Component.translatable("commands.imsotalented.toggle.single", name, state, player.getDisplayName()),
				count -> Component.translatable("commands.imsotalented.toggle.multiple", name, state, count));
	}

	/**
	 * Gives or sets a tree's XP, with the level-up message when it crosses into a new level. Either way the tree stops at
	 * its top level: adding more gives only what is left to the top (nothing at the top), and setting more sets the top.
	 * A gift reports the XP that actually went in, and the targets already at the top as failures.
	 */
	private static int xp(CommandContext<CommandSourceStack> context, boolean set) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		String id = StringArgumentType.getString(context, "tree");
		TalentTree tree = TalentTree.byId(id).orElseThrow(() -> UNKNOWN_TREE.create(id));
		long amount = LongArgumentType.getLong(context, "amount");

		if (set) {
			long xp = Math.min(amount, ImSoTalented.config().rules().xpForLevel(tree.maxLevel()));

			for (ServerPlayer player : targets) {
				Talents.setXp(player, tree, xp);
			}

			sendSuccess(source, targets,
					player -> Component.translatable("commands.imsotalented.xp_set.single", Talents.treeName(tree), xp, player.getDisplayName()),
					count -> Component.translatable("commands.imsotalented.xp_set.multiple", Talents.treeName(tree), xp, count));
			return targets.size();
		}

		List<ServerPlayer> given = new ArrayList<>();
		List<ServerPlayer> capped = new ArrayList<>();
		long added = 0L;

		for (ServerPlayer player : targets) {
			long xp = Talents.addXp(player, tree, amount);

			if (xp > 0L) {
				given.add(player);
				added += xp;
			} else {
				capped.add(player);
			}
		}

		if (given.isEmpty()) {
			source.sendFailure(targets.size() == 1
					? Component.translatable("commands.imsotalented.xp_add.capped", capped.getFirst().getDisplayName(), Talents.treeName(tree))
					: Component.translatable("commands.imsotalented.xp_add.none", Talents.treeName(tree)));
			return 0;
		}

		for (ServerPlayer player : capped) {
			source.sendFailure(Component.translatable("commands.imsotalented.xp_add.capped", player.getDisplayName(), Talents.treeName(tree)));
		}

		long total = added;
		sendSuccess(source, given,
				player -> Component.translatable("commands.imsotalented.xp_add.single", total, Talents.treeName(tree), player.getDisplayName()),
				count -> Component.translatable("commands.imsotalented.xp_add.multiple", count, total, Talents.treeName(tree)));
		return given.size();
	}

	/**
	 * Tells the operator (and the other operators, as vanilla does) what happened to these players: by name when it is
	 * one player, by count when more. The message is only built if someone gets it.
	 */
	private static void sendSuccess(CommandSourceStack source, Collection<ServerPlayer> players, Function<ServerPlayer, Component> single,
			IntFunction<Component> multiple) {
		source.sendSuccess(() -> players.size() == 1 ? single.apply(players.iterator().next()) : multiple.apply(players.size()), true);
	}
}
