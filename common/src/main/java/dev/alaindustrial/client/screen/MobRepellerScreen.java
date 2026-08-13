package dev.alaindustrial.client.screen;

import dev.alaindustrial.Config;
import dev.alaindustrial.menu.MobRepellerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the LV Mob Repeller. All behaviour lives in {@link AbstractMobRepellerScreen}; this
 * class exists so the LV menu type has a screen class of its own, which is what makes the
 * menu↔screen pairing a compile-time fact (see {@code docs/tools/menu_screen_parity_check.py}).
 */
public class MobRepellerScreen extends AbstractMobRepellerScreen<MobRepellerMenu> {
	public MobRepellerScreen(MobRepellerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected int killsNeeded() {
		return Config.mobRepellerEvolveKillsMv;
	}
}
