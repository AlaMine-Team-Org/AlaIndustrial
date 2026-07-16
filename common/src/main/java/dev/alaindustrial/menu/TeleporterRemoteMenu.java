package dev.alaindustrial.menu;

import dev.alaindustrial.item.TeleportPoint;
import dev.alaindustrial.item.TeleportPoints;
import dev.alaindustrial.item.TeleporterRemoteItem;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TeleportNoticePayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.teleporter.TeleportEngine;
import dev.alaindustrial.teleporter.TeleportWarmupManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The remote's screen (MOD-093): pick a station, rename it, drop it, jump to it.
 *
 * <p><b>Why this menu holds no slots and no stack.</b> The remote is an item, and the mod's other
 * menus all belong to block entities — this is the first item-opened menu here. Rather than shipping
 * the stack through the open-packet (which needs a different API on each loader:
 * {@code ExtendedScreenHandlerFactory} on Fabric, {@code IContainerFactory} on NeoForge), the menu
 * simply reads the remote out of the player's main hand on both sides. That is loader-neutral, and
 * it keeps the server authoritative: every action re-reads the live stack instead of trusting
 * anything the client remembered.
 *
 * <p>{@link #stillValid} closes the screen the moment the hand stops holding a remote — swapping
 * slots or dropping it mid-screen cannot leave an orphaned menu writing into whatever is there now.
 */
public class TeleporterRemoteMenu extends AbstractContainerMenu {
	/** How the button id encodes an action + a row: {@code action * STRIDE + index}. */
	private static final int STRIDE = 100;

	/** What a button press means. Ordinals are part of the wire format — append, never reorder. */
	public enum Action {
		SELECT,
		DELETE,
		TELEPORT;

		private static final Action[] VALUES = values();

		static Action decode(int buttonId) {
			int ordinal = buttonId / STRIDE;
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
		}
	}

	private final Player player;
	/** Client-side selection; the server re-reads it from the button id, never trusting this. */
	private int selected = -1;

	public TeleporterRemoteMenu(int syncId, Inventory playerInventory) {
		super(ModContent.TELEPORTER_REMOTE_MENU.get(), syncId);
		this.player = playerInventory.player;
	}

	/** Encode a press for {@link #clickMenuButton}; used by the screen. */
	public static int buttonId(Action action, int index) {
		return action.ordinal() * STRIDE + index;
	}

	/** The live remote — either hand, main first — or empty if the player is no longer holding one. */
	public ItemStack remote() {
		return TeleporterRemoteItem.heldRemote(player);
	}

	/** The points on the live remote — always read fresh, never cached across a tick. */
	public TeleportPoints points() {
		ItemStack stack = remote();
		return stack.isEmpty() ? TeleportPoints.EMPTY
				: stack.getOrDefault(ModDataComponents.TELEPORTER_POINTS.get(), TeleportPoints.EMPTY);
	}

	public int getSelected() {
		return selected;
	}

	public void setSelected(int index) {
		this.selected = index;
	}

	/**
	 * Every button on this screen, over vanilla's own container-button packet.
	 *
	 * <p>No custom C2S payload is needed for these: the packet is plain vanilla, works on both
	 * loaders with zero registration, and carries the one int these actions need. Renaming is the
	 * exception — it carries a string — and goes through the mod's own payload.
	 *
	 * <p>Everything here is validated server-side from the live stack: the index is bounds-checked
	 * against the real list, and ownership is re-checked by the engine. The client's {@code selected}
	 * never reaches this method.
	 */
	@Override
	public boolean clickMenuButton(Player clicker, int buttonId) {
		if (!(clicker instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		Action action = Action.decode(buttonId);
		int index = buttonId % STRIDE;
		if (action == null) {
			return false;
		}
		ItemStack stack = remote();
		if (stack.isEmpty()) {
			return false;
		}
		TeleportPoints points = points();
		if (!points.isValidIndex(index)) {
			return false;
		}

		switch (action) {
			case SELECT -> setSelected(index);
			case DELETE -> {
				stack.set(ModDataComponents.TELEPORTER_POINTS.get(), points.without(index));
				setSelected(-1);
				syncRemote(serverPlayer);
			}
			case TELEPORT -> startJump(serverPlayer, stack, points.get(index));
		}
		return true;
	}

	/**
	 * Push the edited remote back to the client.
	 *
	 * <p>This screen owns no slots, so vanilla's per-tick {@code containerMenu.broadcastChanges()} has
	 * nothing to diff and the client keeps rendering the stack it had when the screen opened — a
	 * rename showed the old name until the screen was reopened. The player's own inventory menu is
	 * what tracks the hand slot, and it keeps its synchronizer while another container is open
	 * ({@code ServerPlayer#initMenu(inventoryMenu)}), so asking it to broadcast sends the one changed
	 * slot on vanilla's container id 0 — which the client applies whatever screen is up.
	 */
	private static void syncRemote(ServerPlayer serverPlayer) {
		serverPlayer.inventoryMenu.broadcastChanges();
	}

	/** Same gate as right-clicking in the air with the remote, minus the item-in-hand plumbing. */
	private void startJump(ServerPlayer serverPlayer, ItemStack stack, TeleportPoint point) {
		if (TeleportWarmupManager.isWarming(serverPlayer)) {
			deny(serverPlayer, TeleportEngine.Denial.ALREADY_WARMING);
			return;
		}
		if (TeleportWarmupManager.isOnCooldown(serverPlayer)) {
			notify(serverPlayer, Component.translatable("alaindustrial.teleporter.cooldown",
					TeleportWarmupManager.cooldownSecondsLeft(serverPlayer)));
			return;
		}
		TeleportEngine.Denial denial = TeleportEngine.checkPolicy(serverPlayer, stack, point);
		if (!denial.allowed()) {
			deny(serverPlayer, denial);
			return;
		}
		TeleportWarmupManager.start(serverPlayer, point);
		serverPlayer.sendSystemMessage(Component.translatable("alaindustrial.teleporter.warmup_started",
				point.displayName(), TeleportEngine.computeCost(serverPlayer, point)).withStyle(ChatFormatting.GRAY), false);
		// The countdown happens in the world, not behind a screen the player is staring at.
		serverPlayer.closeContainer();
	}

	private static void deny(ServerPlayer serverPlayer, TeleportEngine.Denial denial) {
		notify(serverPlayer, denial.message().copy());
	}

	/**
	 * Tell the player why nothing happened — on their screen, not under it.
	 *
	 * <p>Both routes on purpose. The payload is what the player actually reads: this menu's screen
	 * covers the action bar completely, so a refusal sent only there is invisible at exactly the
	 * moment it matters ("I pressed Teleport and nothing happened"). The action-bar copy still goes
	 * out because the same refusals reach players whose screen has since closed, and because chat
	 * history is where someone looks for what they missed.
	 */
	private static void notify(ServerPlayer serverPlayer, Component reason) {
		NetworkDispatcher.get().sendToPlayer(serverPlayer, new TeleportNoticePayload(reason));
		serverPlayer.sendSystemMessage(reason.copy().withStyle(ChatFormatting.RED), true);
	}

	/**
	 * Rename, driven by the mod's C2S payload (a string will not fit in a button id). Server-side
	 * validation lives here so the payload handler cannot skip it.
	 */
	public void rename(ServerPlayer serverPlayer, int index, String name) {
		ItemStack stack = remote();
		if (stack.isEmpty()) {
			return;
		}
		TeleportPoints points = points();
		if (!points.isValidIndex(index)) {
			return;
		}
		stack.set(ModDataComponents.TELEPORTER_POINTS.get(), points.renamed(index, name));
		syncRemote(serverPlayer);
	}

	/** No slots: nothing to shift-click. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	/** Open only while the hand still holds the remote this screen is editing. */
	@Override
	public boolean stillValid(Player player) {
		return !remote().isEmpty();
	}
}
