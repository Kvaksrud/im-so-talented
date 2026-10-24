package no.kvaksrud.imsotalented;

import no.kvaksrud.imsotalented.command.TalentCommand;
import no.kvaksrud.imsotalented.effect.TalentEffects;
import no.kvaksrud.imsotalented.inventory.Backpacks;
import no.kvaksrud.imsotalented.network.BuyTalentPayload;
import no.kvaksrud.imsotalented.network.TalentRulesPayload;
import no.kvaksrud.imsotalented.network.ToggleTalentPayload;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentData;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.Talents;
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
	 * Tree XP earned but not yet a whole point, per tree in {@link TalentTree} order. Pure server bookkeeping: not
	 * saved, not synced, so a fraction of a point is lost on leaving, which nobody will notice.
	 */
	public static final AttachmentType<double[]> TREE_XP_FRACTIONS = AttachmentRegistry.createDefaulted(id("tree_xp_fractions"),
			() -> new double[TalentTree.values().length]);

	@Override
	public void onInitialize() {
		loadConfig();
		// Read again for every world or server start, so an edit doesn't need a game restart.
		ServerLifecycleEvents.SERVER_STARTING.register(_ -> loadConfig());

		PayloadTypeRegistry.serverboundPlay().register(BuyTalentPayload.TYPE, BuyTalentPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleTalentPayload.TYPE, ToggleTalentPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TalentRulesPayload.TYPE, TalentRulesPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BuyTalentPayload.TYPE, (payload, context) ->
				Talent.byId(payload.talentId()).ifPresent(talent -> Talents.tryBuy(context.player(), talent)));

		// The payload carries only the id; the server reads the current state and flips it: a talent that is
		// disabled (off) goes on, one that is enabled goes off.
		ServerPlayNetworking.registerGlobalReceiver(ToggleTalentPayload.TYPE, (payload, context) ->
				Talent.byId(payload.talentId()).ifPresent(talent ->
						Talents.toggle(context.player(), talent, Talents.data(context.player()).isDisabled(talent))));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			sender.sendPacket(new TalentRulesPayload(config.rules()));
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

	/** Replaces the settings until the next world or server start; the game tests use this. */
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
		config = ImSoTalentedConfig.load(FabricLoader.getInstance().getConfigDir().resolve(ImSoTalentedConfig.FILE_NAME));
	}
}
