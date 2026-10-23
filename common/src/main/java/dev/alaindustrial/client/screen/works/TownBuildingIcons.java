package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.Industrialization;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The pictures of the town buildings on the cards of the core's «Construction» tab (owner's round 3 of 0.1.219,
 * R3-3): one atlas, {@code textures/gui/town_buildings.png}, drawn by {@code tools/gen_town_building_icons.py} from
 * the structure templates — {@link #CELL} pixels to a picture, {@link #COLUMNS} to a row, in the order of
 * {@link #ICONS}. The L1 test {@code SettlementCoreLayoutTest} checks that the generator lists the same ids in the same
 * order.
 */
public final class TownBuildingIcons {

	private static final Identifier ATLAS = Industrialization.id("textures/gui/town_buildings.png");
	private static final int TEX = 256;
	public static final int CELL = 48;
	public static final int COLUMNS = 5;

	/** The pictures, {@code <style>/<building>}, as the generator lays them out. */
	public static final List<String> ICONS = List.of(
			"plains/town_hall", "plains/capsule_house_1", "plains/capsule_house_2", "plains/capsule_house_3",
			"plains/capsule_house_4", "plains/warehouse", "plains/pump_station", "plains/guard_post",
			"plains/workshop",
			"desert/town_hall", "desert/capsule_house_1", "desert/capsule_house_2", "desert/capsule_house_3",
			"desert/capsule_house_4", "desert/warehouse", "desert/pump_station", "desert/guard_post",
			"desert/workshop");

	private TownBuildingIcons() {
	}

	/** Whether the atlas has a picture of {@code icon}. */
	public static boolean has(String icon) {
		return ICONS.contains(icon);
	}

	/** Draws the picture of {@code icon} in a {@code size} x {@code size} square at ({@code x}, {@code y}). */
	public static void draw(GuiGraphicsExtractor graphics, String icon, int x, int y, int size) {
		int index = ICONS.indexOf(icon);
		if (index < 0) {
			return;
		}
		float scale = size / (float) CELL;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS, 0, 0, (index % COLUMNS) * CELL, (index / COLUMNS) * CELL,
				CELL, CELL, TEX, TEX);
		graphics.pose().popMatrix();
	}
}
