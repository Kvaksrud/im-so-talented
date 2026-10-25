package no.kvaksrud.imsotalented.inventory;

import java.util.ArrayList;
import java.util.List;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.network.RequestThrottle;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * The Backpack talent: a second inventory of up to {@link TalentValues#MAX_BACKPACK_SLOTS} slots, opened with a key
 * or a button in the inventory. Items picked up that don't fit the inventory overflow into it (when the server allows
 * it), it drops on death like the inventory, and it is kept through death with keepInventory.
 */
public final class Backpacks {
	/**
	 * The player's backpack. Not synced: the menu sends the slots while it is open, and nothing else on the
	 * client needs them. Kept through death; without keepInventory it is emptied onto the ground first. The id is still
	 * 0.1.0's {@code extra_inventory} on purpose: the talent reset leaves its slots locked, so on the first join the old
	 * items move into the inventory or drop at the player's feet (see {@link #onTalentsChanged}) instead of vanishing.
	 */
	public static final AttachmentType<Backpack> BACKPACK = AttachmentRegistry.create(ImSoTalented.id("extra_inventory"),
			builder -> builder.persistent(Backpack.CODEC)
					.initializer(Backpack::new)
					.copyOnDeath());

	public static final MenuType<BackpackMenu> MENU = Registry.register(BuiltInRegistries.MENU, ImSoTalented.id("backpack"),
			new MenuType<>(BackpackMenu::new, FeatureFlags.VANILLA_SET));

	public static final Component TITLE = Component.translatable("container.imsotalented.backpack");

	private Backpacks() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(OpenBackpackPayload.TYPE, OpenBackpackPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenBackpackPayload.TYPE, (payload, context) -> {
			if (RequestThrottle.allow(context.player(), RequestThrottle.Request.OPEN_BACKPACK)) {
				open(context.player());
			}
		});
	}

	/** The player's backpack, created empty the first time. Meant for the server; the client has no copy. */
	public static Backpack get(Player player) {
		return player.getAttachedOrCreate(BACKPACK);
	}

	/** How many backpack slots, counted from the first, the player's talent unlocks: 0 without it. */
	public static int unlockedSlots(Player player) {
		return TalentValues.backpackSlots(Talents.level(player, Talent.BACKPACK));
	}

	/**
	 * Opens the backpack, or tells the player how to unlock it. Spectators and dead players are refused without a word
	 * (their client shouldn't have asked). Every refusal is logged at debug.
	 */
	public static void open(ServerPlayer player) {
		if (player.isSpectator() || player.isDeadOrDying()) {
			logRefusedOpen(player, player.isSpectator() ? "spectator" : "dead");
			return;
		}

		if (unlockedSlots(player) <= 0) {
			// A keybind component shows the key the player bound to the talent tree, resolved on their client.
			player.sendOverlayMessage(Component.translatable("message.imsotalented.backpack_locked",
					Component.translatable(Talent.BACKPACK.translationKey()), Component.keybind(Talents.TALENT_TREE_KEY)));
			logRefusedOpen(player, "locked");
			return;
		}

		Backpack backpack = get(player);
		player.openMenu(new SimpleMenuProvider(
				(containerId, inventory, _) -> new BackpackMenu(containerId, inventory, backpack, () -> unlockedSlots(player)),
				TITLE));
	}

	private static void logRefusedOpen(ServerPlayer player, String reason) {
		ImSoTalented.LOGGER.debug("Refused opening the backpack for {} ({}): {}", player.getName().getString(), player.getUUID(), reason);
	}

	/**
	 * Called after the normal inventory took what it could of a stack the player is given or picks up: puts the rest
	 * into the unlocked backpack slots and shrinks the stack by exactly that much. Creative players are left alone (the
	 * game already voids what doesn't fit for them), and so is everyone when the server turned it off.
	 *
	 * @return how many items went into the backpack
	 */
	public static int storeOverflow(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || !ImSoTalented.config().backpackPickup() || player.hasInfiniteMaterials() || player.isSpectator()) {
			return 0;
		}

		int unlocked = unlockedSlots(player);

		if (unlocked <= 0) {
			return 0;
		}

		return get(player).insert(stack, unlocked);
	}

	/**
	 * Drops everything in the backpack where the player died, as the game does with the inventory when
	 * keepInventory is off. Items with Curse of Vanishing are destroyed instead, like in the inventory.
	 */
	public static void dropOnDeath(Player player) {
		Backpack backpack = player.getAttached(BACKPACK);

		if (backpack == null || backpack.isEmpty()) {
			return;
		}

		for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
			ItemStack stack = backpack.removeItemNoUpdate(slot);

			if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}

			ItemEntity drop = player.createItemStackToDrop(stack, true, false);

			if (drop != null) {
				player.level().addFreshEntity(drop);
			}
		}

		backpack.setChanged();
	}

	/**
	 * Called whenever a player's talents change on the server (bought, set by command, reset) and when they join or
	 * respawn. If fewer slots are unlocked than before, the items in the slots that just locked move into the
	 * still-unlocked ones, then into the inventory, and whatever doesn't fit drops at the player's feet. The player is
	 * told in chat how many items moved and dropped, and the server log lists them. An open menu follows on its own: it
	 * reads the unlocked count live, and closes when none are left.
	 */
	public static void onTalentsChanged(ServerPlayer player) {
		Backpack backpack = player.getAttached(BACKPACK);

		if (backpack == null) {
			return;
		}

		int unlocked = unlockedSlots(player);
		int movedCount = 0;
		int droppedCount = 0;
		List<String> moved = new ArrayList<>();
		List<String> dropped = new ArrayList<>();

		for (int slot = unlocked; slot < backpack.getContainerSize(); slot++) {
			ItemStack stack = backpack.removeItemNoUpdate(slot);

			if (stack.isEmpty()) {
				continue;
			}

			// Read before the moves below use the stack up.
			String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			int count = stack.getCount();
			backpack.insert(stack, unlocked);

			if (!stack.isEmpty()) {
				// Vanilla drops what doesn't fit the inventory as this very stack, so what is left in it lies on the ground.
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}

			int lost = stack.getCount();

			if (count > lost) {
				movedCount += count - lost;
				moved.add(item + " × " + (count - lost));
			}

			if (lost > 0) {
				droppedCount += lost;
				dropped.add(item + " × " + lost);
			}
		}

		if (movedCount + droppedCount == 0) {
			return;
		}

		backpack.setChanged();
		player.sendSystemMessage(Component.translatable("message.imsotalented.backpack_shrank", movedCount, droppedCount));
		ImSoTalented.LOGGER.info("The backpack of {} ({}) shrank to {} slots: moved {} into it or the inventory; dropped {}",
				player.getName().getString(), player.getUUID(), unlocked,
				moved.isEmpty() ? "nothing" : String.join(", ", moved),
				dropped.isEmpty() ? "nothing" : String.join(", ", dropped) + " at " + player.blockPosition().toShortString());
	}
}
