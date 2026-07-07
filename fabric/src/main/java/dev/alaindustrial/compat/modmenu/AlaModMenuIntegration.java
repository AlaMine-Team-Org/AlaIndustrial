package dev.alaindustrial.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.alaindustrial.client.AlaConfigScreen;

/** Optional Fabric Mod Menu bridge. Loaded only when Mod Menu is installed. */
public final class AlaModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return AlaConfigScreen::new;
	}
}
