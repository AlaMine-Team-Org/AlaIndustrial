package dev.alaindustrial.compat.rei;

import dev.alaindustrial.client.MachineScreen;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZonesProvider;
import net.minecraft.client.renderer.Rect2i;

/**
 * Tells REI which screen regions the MOD-080 upgrade UI occupies (gear tab + open panel) so its item
 * grid keeps clear of them. One registration on {@link MachineScreen} covers all machine screens (REI
 * matches by {@code isAssignableFrom}) and REI re-queries every frame, so the draggable panel and its
 * open/closed state update live. Rectangles are absolute, read from {@link MachineScreen#extraGuiAreas()}.
 */
public final class AlaReiExclusionZones implements ExclusionZonesProvider<MachineScreen<?>> {

	@Override
	public Collection<Rectangle> provide(MachineScreen<?> screen) {
		List<Rectangle> out = new ArrayList<>();
		for (Rect2i r : screen.extraGuiAreas()) {
			out.add(new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
		}
		return out;
	}
}
