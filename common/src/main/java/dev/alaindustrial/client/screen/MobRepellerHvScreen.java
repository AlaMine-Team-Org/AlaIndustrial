package dev.alaindustrial.client.screen;

import dev.alaindustrial.menu.MobRepellerHvMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the HV Mob Repeller. All behaviour lives in {@link AbstractMobRepellerScreen}; this
 * class exists so the HV menu type has a screen class of its own, which is what makes the
 * menu↔screen pairing a compile-time fact (see {@code docs/tools/menu_screen_parity_check.py}).
 */
public class MobRepellerHvScreen extends AbstractMobRepellerScreen<MobRepellerHvMenu> {
	public MobRepellerHvScreen(MobRepellerHvMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected int killsNeeded() {
		return 0; // top tier: nothing above it to grow into
	}
}
