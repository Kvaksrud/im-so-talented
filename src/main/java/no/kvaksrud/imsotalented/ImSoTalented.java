package no.kvaksrud.imsotalented;

import java.nio.file.Path;

import com.google.common.annotations.VisibleForTesting;
import no.kvaksrud.imsotalented.command.TalentCommand;
import no.kvaksrud.imsotalented.effect.TalentEffects;
import no.kvaksrud.imsotalented.inventory.Backpacks;
import no.kvaksrud.imsotalented.network.BuyTalentPayload;
import no.kvaksrud.imsotalented.network.RequestThrottle;
import no.kvaksrud.imsotalented.network.TalentRulesPayload;
import no.kvaksrud.imsotalented.network.ToggleTalentPayload;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentData;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.Talents;
import no.kvaksrud.imsotalented.talent.TreeXpLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

public class ImSoTalented implements ModInitializer {
	public static final String MOD_ID = "imsotalented";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile ImSoTalentedConfig config = ImSoTalentedConfig.DEFAULTS;
	/** The rules the server sent this client; the defaults until then. Only read on the client. */
	private static volatile TalentRules clientRules = TalentRules.DEFAULTS;

	/**
	 * The player's talent levels and tree XP: saved, kept through death, and synced to that player only (for the tree,
	 * the XP bars and mining speed). The id is new with the four trees, so talents saved by older versions (under
	 * {@code talents}, bought with vanilla XP) are left behind: every player starts the trees from scratch.
	 */
	public static final AttachmentType<TalentData> TALENTS = AttachmentRegistry.create(id("talent_trees"),
			builder -> builder.persistent(TalentData.CODEC)
					.syncWith(TalentData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/**
	 * The server's tree XP bookkeeping per player ({@link TreeXpLedger}): XP earned but not yet committed, each tree's
	 * hourly allowance and the last camera turn. Not saved and not synced; kept through death so dying doesn't reset
	 * the hourly allowance.
	 */
	public static final AttachmentType<TreeXpLedger> TREE_XP_LEDGER = AttachmentRegistry.create(id("tree_xp_ledger"),
			builder -> builder.initializer(TreeXpLedger::new).copyOnDeath());

	/**
	 * When the player's last buy, toggle and backpack request was handled ({@link RequestThrottle}). Not saved, not
	 * synced and not copied on respawn: it lives as long as the player entity.
	 */
	public static final AttachmentType<RequestThrottle> REQUEST_THROTTLE = AttachmentRegistry.create(id("request_throttle"),
			builder -> builder.initializer(RequestThrottle::new));

	@Override
	public void onInitialize() {
		// Read for every world or server start, so an edit doesn't need a game restart. Until a server starts nothing
		// reads them, so the defaults stand in.
		ServerLifecycleEvents.SERVER_STARTING.register(_ -> loadConfig());

		PayloadTypeRegistry.serverboundPlay().register(BuyTalentPayload.TYPE, BuyTalentPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleTalentPayload.TYPE, ToggleTalentPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TalentRulesPayload.TYPE, TalentRulesPayload.CODEC);

		// The client only asks for what its own checks allow, so an unknown id means a modified or mismatched client.
		ServerPlayNetworking.registerGlobalReceiver(BuyTalentPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			if (RequestThrottle.allow(player, RequestThrottle.Request.BUY)) {
				Talent.byId(payload.talentId()).ifPresentOrElse(talent -> Talents.tryBuy(player, talent),
						() -> logUnknownTalent(player, "buying", payload.talentId()));
			}
		});

		// The payload carries the state the player clicked for, so a double click or a command in between can't turn
		// it the other way; the server checks the player may toggle it at all.
		ServerPlayNetworking.registerGlobalReceiver(ToggleTalentPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			if (RequestThrottle.allow(player, RequestThrottle.Request.TOGGLE)) {
				Talent.byId(payload.talentId()).ifPresentOrElse(talent -> Talents.toggle(player, talent, payload.on(), true),
						() -> logUnknownTalent(player, "toggling", payload.talentId()));
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			sender.sendPacket(new TalentRulesPayload(TalentRulesPayload.PROTOCOL, config.rules()));
			Talents.applyAll(player);
		});

		// Fabric copies attachments (the talents) to the new player in its own AFTER_RESPAWN listener, so this one has to
		// run after it; mod order alone doesn't promise that.
		Identifier afterAttachments = id("after_attachments");
		ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Event.DEFAULT_PHASE, afterAttachments);
		ServerPlayerEvents.AFTER_RESPAWN.register(afterAttachments, (oldPlayer, newPlayer, alive) -> {
			Talents.applyAll(newPlayer);

			if (!alive) {
				// Vanilla fills health before the talent's extra hearts are back; fill those too.
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}
		});

		// Tree XP earned during the tick is committed once at its end: one talent data sync per player at most.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Talents.commitPending(player);
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TalentCommand.register(dispatcher));

		TalentEffects.init();
		Backpacks.init();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static ImSoTalentedConfig config() {
		return config;
	}

	/**
	 * For the game tests only: replaces the settings until the next world or server start. Not part of the mod's API;
	 * the settings come from the file.
	 */
	@VisibleForTesting
	public static void setConfig(ImSoTalentedConfig newConfig) {
		config = newConfig;
	}

	public static TalentRules clientRules() {
		return clientRules;
	}

	/** Called by the client when the server sends its rules. */
	public static void setClientRules(TalentRules rules) {
		clientRules = rules;
	}

	private static void loadConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(ImSoTalentedConfig.FILE_NAME);
		config = ImSoTalentedConfig.load(file);
		LOGGER.info("Settings in effect (from {}): {}", file, config.summary());
	}

	private static void logUnknownTalent(ServerPlayer player, String action, String talentId) {
		LOGGER.debug("Rejected {} \"{}\" for {} ({}): unknown talent", action, talentId, player.getName().getString(), player.getUUID());
	}
}
