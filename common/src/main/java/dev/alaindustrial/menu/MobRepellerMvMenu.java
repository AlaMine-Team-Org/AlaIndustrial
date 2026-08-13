package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;

/**
 * Menu for the MV Mob Repeller — the 16-block field.
 *
 * <p>A thin subclass, exactly like the solar panel family's per-tier menus: the behaviour lives in
 * {@link MobRepellerMenu}, but each menu TYPE needs its own class so a menu↔screen pair stays a
 * compile-time fact (a shared class makes the two indistinguishable — see
 * {@code docs/tools/menu_screen_parity_check.py}).
 */
public class MobRepellerMvMenu extends MobRepellerMenu {
	/** Server side. */
	public MobRepellerMvMenu(int syncId, Inventory playerInventory, MobRepellerBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.MOB_REPELLER_MV_MENU.get(), syncId, playerInventory, be, access,
				ModContent.MOB_REPELLER_MV.get());
	}

	/** Client side. */
	public MobRepellerMvMenu(int syncId, Inventory playerInventory) {
		super(ModContent.MOB_REPELLER_MV_MENU.get(), syncId, playerInventory, ModContent.MOB_REPELLER_MV.get());
	}
}
