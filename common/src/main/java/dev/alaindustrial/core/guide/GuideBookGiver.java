package dev.alaindustrial.core.guide;

import dev.alaindustrial.core.GuideBookState;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Auto-gives the Guide Book (MOD-067) to a player on their first join, exactly once. The "already
 * given" ledger lives in per-world {@link GuideBookState} (server-global {@code SavedData}), so the
 * logic is a single loader-neutral method in {@code common/}; the Fabric and NeoForge join hooks are
 * thin wrappers that call it.
 */
public final class GuideBookGiver {
	private GuideBookGiver() {
	}

	/** Give the book if this player has never received it. Safe to call on every join. */
	public static void giveIfNeeded(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		GuideBookState state = server.getDataStorage().computeIfAbsent(GuideBookState.TYPE);
		if (state.markGiven(player.getUUID())) {
			giveBook(player);
		}
	}

	/** The last hotbar slot (9th, 0-indexed) — where the book lands so it isn't in the player's hand. */
	private static final int HOTBAR_LAST_SLOT = 8;

	/**
	 * Unconditionally give a fresh book (recovery via {@code /ala guide} or a craft is separate). The
	 * book is placed in the last hotbar slot (slot 9) when it's free, so a new player doesn't spawn with
	 * it selected in-hand and can't fling it away by accident; otherwise it goes to any free slot, and
	 * if the inventory is full it drops at the player's feet.
	 */
	public static void giveBook(ServerPlayer player) {
		ItemStack book = new ItemStack(ModContent.GUIDE_BOOK.get());
		Inventory inventory = player.getInventory();
		if (inventory.getItem(HOTBAR_LAST_SLOT).isEmpty()) {
			inventory.setItem(HOTBAR_LAST_SLOT, book);
		} else if (!player.addItem(book)) {
			player.drop(book, false);
		}
	}
}
