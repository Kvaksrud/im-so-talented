package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.ImSoTalented;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * What {@link TreeXp} keeps per player, besides the {@code TreeXpLedger}. A class of its own so that loading
 * {@link TreeXp} (whose rules the unit tests run without a game) doesn't create attachments, which needs Fabric's
 * implementation; these are created when {@link TreeXp#init} first touches this class, during mod init.
 */
public final class TreeXpAttachments {
	/**
	 * Every chunk each player has been in, per dimension: saved, kept on death, never synced (the client has no use for
	 * it).
	 */
	public static final AttachmentType<VisitedChunks> VISITED_CHUNKS = AttachmentRegistry.create(ImSoTalented.id("visited_chunks"),
			builder -> builder.initializer(VisitedChunks::new).persistent(VisitedChunks.CODEC).copyOnDeath());

	/** Where each player was at the end of the last tick, for measuring travel. Server bookkeeping only. */
	public static final AttachmentType<TreeXp.Travel> TRAVEL = AttachmentRegistry.create(ImSoTalented.id("explorer_travel"));

	/** Guardian's raw-damage copy of each player's damage cooldown. Server bookkeeping only, gone on death. */
	public static final AttachmentType<GuardianCooldown> GUARDIAN_COOLDOWN = AttachmentRegistry.create(ImSoTalented.id("guardian_cooldown"),
			builder -> builder.initializer(GuardianCooldown::new));

	/**
	 * The server tick a plain pickaxe block last paid the player Gatherer XP, for
	 * {@link no.kvaksrud.imsotalented.talent.TalentValues#GATHERER_BLOCK_XP_MIN_TICKS}. Server bookkeeping only.
	 */
	public static final AttachmentType<Long> PAID_BLOCK_TICK = AttachmentRegistry.create(ImSoTalented.id("gatherer_paid_block_tick"));

	private TreeXpAttachments() {
	}

	/** Creates the attachments, by loading this class; they have to exist before the first player's data is read. */
	static void init() {
	}
}
