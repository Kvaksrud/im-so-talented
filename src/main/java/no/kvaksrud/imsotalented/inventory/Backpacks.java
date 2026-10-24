package no.kvaksrud.imsotalented.inventory;

import no.kvaksrud.imsotalented.ImSoTalented;
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
	 * client needs them. Kept through death; without keepInventory it is emptied onto the ground first. The id is the
	 * old extra inventory's on purpose: the talent reset leaves the slots locked, so on the first join their items
	 * move into the inventory or drop at the player's feet (see {@link #onTalentsChanged}) instead of vanishing.
	 */
	public static final AttachmentType<Backpack> BACKPACK = AttachmentRegistry.create(ImSoTalented.id("extra_inventory"),
			builder -> builder.persistent(Backpack.CODEC)
					.initializer(Backpack::new)
					.copyOnDeath());

	public static final MenuType<BackpackMenu> MENU = Registry.register(BuiltInRegistries.MENU, ImSoTalented.id("backpack"),
			new MenuType<>(BackpackMenu::new, FeatureFlags.VANILLA_SET));

	public static final Component TITLE = Component.translatable("container.imsotalented.backpack");

	/** The talent tree's key mapping (registered by the client), named in the "locked" message. */
	private static final String TALENT_TREE_KEY = "key.imsotalented.talent_tree";

	private Backpacks() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(OpenBackpackPayload.TYPE, OpenBackpackPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenBackpackPayload.TYPE, (payload, context) -> open(context.player()));
	}

	/** The player's backpack, created empty the first time. Meant for the server; the client has no copy. */
	public static Backpack get(Player player) {
		return player.getAttachedOrCreate(BACKPACK);
	}

	/** How many backpack slots, counted from the first, the player's talent unlocks: 0 without it. */
	public static int unlockedSlots(Player player) {
		return TalentValues.backpackSlots(Talents.level(player, Talent.BACKPACK));
	}

	/** Opens the backpack, or tells the player how to unlock it. */
	public static void open(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}

		if (unlockedSlots(player) <= 0) {
			// A keybind component shows the key the player bound to the talent tree, resolved on their client.
			player.sendOverlayMessage(Component.translatable("message.imsotalented.backpack_locked",
					Component.translatable(Talent.BACKPACK.translationKey()), Component.keybind(TALENT_TREE_KEY)));
			return;
		}

		Backpack container = get(player);
		player.openMenu(new SimpleMenuProvider(
				(containerId, inventory, _) -> new BackpackMenu(containerId, inventory, container, () -> unlockedSlots(player)),
				TITLE));
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
		Backpack extra = player.getAttached(BACKPACK);

		if (extra == null || extra.isEmpty()) {
			return;
		}

		for (int slot = 0; slot < extra.getContainerSize(); slot++) {
			ItemStack stack = extra.removeItemNoUpdate(slot);

			if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}

			ItemEntity drop = player.createItemStackToDrop(stack, true, false);

			if (drop != null) {
				player.level().addFreshEntity(drop);
			}
		}

		extra.setChanged();
	}

	/**
	 * Called whenever a player's talents change on the server (bought, set by command, reset) and when they join or
	 * respawn. If fewer slots are unlocked than before, the items in the slots that just locked move into the
	 * still-unlocked ones, then into the inventory, and whatever doesn't fit drops at the player's feet. An open menu
	 * follows on its own: it reads the unlocked count live, and closes when none are left.
	 */
	public static void onTalentsChanged(ServerPlayer player) {
		Backpack extra = player.getAttached(BACKPACK);

		if (extra == null) {
			return;
		}

		int unlocked = unlockedSlots(player);
		boolean moved = false;

		for (int slot = unlocked; slot < extra.getContainerSize(); slot++) {
			ItemStack stack = extra.removeItemNoUpdate(slot);

			if (stack.isEmpty()) {
				continue;
			}

			moved = true;
			extra.insert(stack, unlocked);

			if (!stack.isEmpty()) {
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}

		if (moved) {
			extra.setChanged();
		}
	}
}
