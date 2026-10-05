package dev.alaindustrial.compat.rei;

import dev.alaindustrial.client.screen.MagnetScreen;
import dev.alaindustrial.item.tool.MagnetFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * Drag an item from REI onto a magnet filter cell (MOD-592) — the REI twin of the JEI ghost handler.
 * Only item entries, and only while a filter is fitted.
 */
final class MagnetFilterDragVisitor implements DraggableStackVisitor<MagnetScreen> {

	@Override
	public <R extends Screen> boolean isHandingScreen(R screen) {
		return screen instanceof MagnetScreen;
	}

	@Override
	public DraggedAcceptorResult acceptDraggedStack(DraggingContext<MagnetScreen> context, DraggableStack stack) {
		ItemStack item = itemOf(stack);
		MagnetScreen screen = context.getScreen();
		if (item.isEmpty() || !screen.acceptsSamples()) {
			return DraggedAcceptorResult.PASS;
		}
		Point at = context.getCurrentPosition();
		int cell = screen.cellAt(at.getX(), at.getY());
		if (cell < 0) {
			return DraggedAcceptorResult.PASS;
		}
		MagnetScreen.sendSample(cell, item);
		return DraggedAcceptorResult.CONSUMED;
	}

	@Override
	public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<MagnetScreen> context,
			DraggableStack stack) {
		MagnetScreen screen = context.getScreen();
		if (itemOf(stack).isEmpty() || !screen.acceptsSamples()) {
			return Stream.empty();
		}
		List<Rectangle> cells = new ArrayList<>(MagnetFilter.CELLS);
		for (int i = 0; i < MagnetFilter.CELLS; i++) {
			Rect2i area = screen.cellArea(i);
			cells.add(new Rectangle(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
		}
		return Stream.of(BoundsProvider.ofRectangles(cells));
	}

	private static ItemStack itemOf(DraggableStack stack) {
		EntryStack<?> entry = stack.getStack();
		return entry.getType() == VanillaEntryTypes.ITEM ? entry.castValue() : ItemStack.EMPTY;
	}
}
