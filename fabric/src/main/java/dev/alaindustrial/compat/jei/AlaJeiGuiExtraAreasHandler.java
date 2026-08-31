package dev.alaindustrial.compat.jei;

import dev.alaindustrial.client.screen.MachineScreen;
import java.util.List;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.renderer.Rect2i;

/**
 * Tells JEI which screen regions the MOD-080 upgrade UI occupies (gear tab + open panel) so its item
 * grid keeps clear of them and its overlay is not hidden behind the panel. One registration on
 * {@link MachineScreen} covers all machine screens (JEI matches by {@code isInstance}). The rectangles
 * are absolute and dynamic (drag-aware), read live from {@link MachineScreen#extraGuiAreas()}.
 */
public final class AlaJeiGuiExtraAreasHandler implements IGuiContainerHandler<MachineScreen<?>> {

	@Override
	public List<Rect2i> getGuiExtraAreas(MachineScreen<?> screen) {
		return screen.extraGuiAreas();
	}
}
