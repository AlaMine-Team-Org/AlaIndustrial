package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.ReactorControllerScreen;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The reactor's «Room» tab (MOD-619): the shell seen from above, the scanner's checks in the order it runs
 * them, and what to fix.
 *
 * <p><b>The map is drawn north up</b>, from the offsets the channels carry, not from the controller's point of
 * view: the directions the player is told — "4 east, 2 up" — are compass directions, and a map that turned
 * with the controller would put east on the left for half the rooms built.
 *
 * <p><b>Every hole is on the map.</b> The first playtest found one red cell on a shell with six holes in three
 * walls: the scanner stopped at the first. A cell from above is a column of blocks, so a cell carries the
 * height of its hole relative to the controller ("+2") — or "×2" when the column holds more than one, and the
 * tooltip lists them.
 *
 * <p><b>The checklist answers "what has passed" as well as "what is wrong".</b> A player fixing a room one
 * block at a time wants to see the line move down, and a single red verdict does not show that the size was
 * fine all along.
 */
public final class RoomTabPage implements ReactorTabPage {

	private static final String KEY = "gui.alaindustrial.reactor_controller.";

	// ── Geometry, relative to the panel. Public so an L3 stand can crop a band from the same numbers. ──
	public static final int MAP_X = ConsoleTabPage.CONTENT_LEFT;
	public static final int MAP_Y = 22;
	public static final int MAP_SIZE = 100;
	/** The map's cells sit inside a two-pixel frame. */
	private static final int MAP_INSET = 2;
	public static final int LIST_X = 116;
	public static final int INSIDE_Y = 23;
	public static final int LIST_Y = 35;
	public static final int LIST_ROW = 11;
	private static final int MARK_SIZE = 9;
	public static final int DETAIL_Y = ConsoleTabPage.ADVICE_Y;
	public static final int DETAIL_BOTTOM = ConsoleTabPage.ADVICE_BOTTOM;

	private static final int MAP_BACK = 0xFF1B2126;
	private static final int CELL_INTERIOR = 0xFF26323A;
	private static final int CELL_SHELL = 0xFF3E7F92;
	private static final int CELL_CONTROLLER = 0xFFE0892B;
	private static final int CELL_PROBLEM = 0xFFE23A2A;
	private static final int CELL_PROBLEM_DIM = 0xFF8A2A22;
	private static final int PROBLEM_EDGE = 0xFFFFB0A6;
	private static final int MAP_LABEL = 0xFFB9C0C7;
	private static final int ROW_FAILED_BACK = 0xFFE3BDB7;
	private static final int MARK_UNCHECKED = 0xFFA4A9AE;
	private static final int TEXT_UNCHECKED = 0xFF8E8E8E;
	private static final int INK_RED = 0xFFAA2A1A;
	private static final float LABEL_SCALE = 0.75f;
	/** Below this a height label no longer reads inside its cell, and the cell goes without one. */
	private static final float MIN_LABEL_SCALE = 0.4f;
	private static final long BLINK_MS = 400L;
	private static final int TOOLTIP_WIDTH = 200;
	private static final int[] NO_PROBLEMS = {};

	private final ReactorControllerScreen screen;

	public RoomTabPage(ReactorControllerScreen screen) {
		this.screen = screen;
	}

	@Override
	public Component title() {
		return Component.translatable(KEY + "tab.room");
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(ModContent.REACTOR_CASING.get());
	}

	/** Red while the shell is being built, amber while racks burn with no room around them. */
	@Override
	public int badgeColour() {
		ReactorConsole.Readout r = ConsoleTabPage.readout(screen.getMenu());
		if (r.formed()) {
			return 0;
		}
		return r.bare() ? ReactorPageText.FILL_AMBER : ReactorPageText.FILL_RED;
	}

	@Override
	public void init() {
	}

	@Override
	public void setShown(boolean shown) {
	}

	@Override
	public void tick() {
	}

	@Override
	public void mouseReleased(MouseButtonEvent event) {
	}

	/**
	 * The problem cells as {@code dx, dy, dz} triples from the controller: every hole a breach lists, or the one
	 * position a located verdict names — a second controller, the first glass block, a misplaced controller.
	 */
	private static int[] problems(ReactorControllerMenu menu) {
		ReactorRoomStatus status = menu.getStatus();
		if (status == ReactorRoomStatus.FORMED || !status.hasLocation()) {
			return NO_PROBLEMS;
		}
		int listed = menu.getListedHoles();
		if (status == ReactorRoomStatus.BREACH && listed > 0) {
			int[] holes = new int[3 * listed];
			for (int i = 0; i < listed; i++) {
				holes[3 * i] = menu.getHoleDx(i);
				holes[3 * i + 1] = menu.getHoleDy(i);
				holes[3 * i + 2] = menu.getHoleDz(i);
			}
			return holes;
		}
		return new int[] {menu.getBreachDx(), menu.getBreachDy(), menu.getBreachDz()};
	}

	private static RoomMapLayout layout(ReactorControllerMenu menu, int[] problems) {
		int[] cells = new int[problems.length / 3 * 2];
		for (int i = 0; i < problems.length / 3; i++) {
			cells[2 * i] = problems[3 * i];
			cells[2 * i + 1] = problems[3 * i + 2];
		}
		int inner = MAP_SIZE - 2 * MAP_INSET;
		return RoomMapLayout.of(menu.isBoxMeasured(), menu.getBoxWest(), menu.getBoxNorth(), menu.getSizeX(),
				menu.getSizeZ(), cells, inner, inner);
	}

	// ── Drawing ──────────────────────────────────────────────────────────────────────────────────

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorControllerMenu menu = screen.getMenu();
		int[] problems = problems(menu);
		drawMap(graphics, menu, problems);
		drawChecklist(graphics, menu);
		drawDetail(graphics, menu, problems);
	}

	private void drawMap(GuiGraphicsExtractor graphics, ReactorControllerMenu menu, int[] problems) {
		Font font = screen.font();
		int frameX = screen.left() + MAP_X;
		int frameY = screen.top() + MAP_Y;
		graphics.fill(frameX, frameY, frameX + MAP_SIZE, frameY + MAP_SIZE, ReactorPageText.PLATE_EDGE);
		graphics.fill(frameX + 1, frameY + 1, frameX + MAP_SIZE - 1, frameY + MAP_SIZE - 1, MAP_BACK);
		int ax = frameX + MAP_INSET;
		int ay = frameY + MAP_INSET;
		RoomMapLayout map = layout(menu, problems);

		if (menu.isBoxMeasured()) {
			int west = menu.getBoxWest();
			int north = menu.getBoxNorth();
			int sizeX = menu.getSizeX();
			int sizeZ = menu.getSizeZ();
			for (int z = map.minZ(); z < map.minZ() + map.rows(); z++) {
				for (int x = map.minX(); x < map.minX() + map.cols(); x++) {
					if (RoomMapLayout.isShell(x, z, west, north, sizeX, sizeZ)) {
						cell(graphics, ax, ay, map, x, z, CELL_SHELL);
					} else if (RoomMapLayout.isInterior(x, z, west, north, sizeX, sizeZ)) {
						cell(graphics, ax, ay, map, x, z, CELL_INTERIOR);
					}
				}
			}
		}
		cell(graphics, ax, ay, map, 0, 0, CELL_CONTROLLER);

		boolean lit = (System.currentTimeMillis() / BLINK_MS) % 2L == 0L;
		for (int i = 0; i < problems.length; i += 3) {
			if (!firstInColumn(problems, i)) {
				continue;
			}
			int px = ax + map.cellLeft(problems[i]);
			int py = ay + map.cellTop(problems[i + 2]);
			graphics.fill(px - 1, py - 1, px + map.cell() + 1, py + map.cell() + 1, PROBLEM_EDGE);
			graphics.fill(px, py, px + map.cell(), py + map.cell(), lit ? CELL_PROBLEM : CELL_PROBLEM_DIM);
			int stacked = inColumn(problems, problems[i], problems[i + 2]);
			int dy = problems[i + 1];
			String label = stacked > 1 ? "×" + stacked : dy != 0 ? (dy > 0 ? "+" : "−") + Math.abs(dy) : null;
			if (label != null) {
				drawCellLabel(graphics, font, Component.literal(label), px, py, map.cell());
			}
		}

		ReactorPageText.scaled(graphics, font, Component.translatable(KEY + "room.north"), ax + 1, ay + 1,
				LABEL_SCALE, MAP_LABEL);
		if (menu.getStatus() == ReactorRoomStatus.ROOM_UNBOUNDED) {
			Component caption = Component.translatable(KEY + "room.no_walls");
			int width = MAP_SIZE - 2 * MAP_INSET - 4;
			int shownW = Math.min(width, Math.round(font.width(caption) * LABEL_SCALE));
			ReactorPageText.scaled(graphics, font, caption, frameX + (MAP_SIZE - shownW) / 2, frameY + MAP_SIZE - 12,
					Math.min(LABEL_SCALE, (float) width / Math.max(1, font.width(caption))), MAP_LABEL);
		}
	}

	/** A label centred inside a problem cell, in white, shrunk to the cell — or left out if it cannot fit. */
	private static void drawCellLabel(GuiGraphicsExtractor graphics, Font font, Component label, int px, int py,
			int cell) {
		int textW = font.width(label);
		float scale = Math.min(LABEL_SCALE, (cell - 2f) / Math.max(1, textW));
		if (scale < MIN_LABEL_SCALE) {
			return;
		}
		int shownW = Math.round(textW * scale);
		ReactorPageText.scaled(graphics, font, label, px + (cell - shownW + 1) / 2, py + (cell - font.lineHeight + 1) / 2,
				scale, ReactorPageText.GLYPH);
	}

	/** Whether the triple at {@code index} is the first of its column — the one that draws the cell. */
	private static boolean firstInColumn(int[] problems, int index) {
		for (int j = 0; j < index; j += 3) {
			if (problems[j] == problems[index] && problems[j + 2] == problems[index + 2]) {
				return false;
			}
		}
		return true;
	}

	private static int inColumn(int[] problems, int x, int z) {
		int count = 0;
		for (int j = 0; j < problems.length; j += 3) {
			if (problems[j] == x && problems[j + 2] == z) {
				count++;
			}
		}
		return count;
	}

	/** One cell, with a pixel of gap on its south-east side once cells are big enough to show a grid. */
	private static void cell(GuiGraphicsExtractor graphics, int ax, int ay, RoomMapLayout map, int x, int z,
			int colour) {
		int gap = map.cell() >= 6 ? 1 : 0;
		int px = ax + map.cellLeft(x);
		int py = ay + map.cellTop(z);
		graphics.fill(px, py, px + map.cell() - gap, py + map.cell() - gap, colour);
	}

	private void drawChecklist(GuiGraphicsExtractor graphics, ReactorControllerMenu menu) {
		Font font = screen.font();
		ReactorRoomStatus status = menu.getStatus();
		boolean measured = menu.isBoxMeasured();
		int left = screen.left() + LIST_X;
		int right = screen.left() + ConsoleTabPage.CONTENT_RIGHT;
		Component inside = measured
				? Component.translatable(KEY + "room.inside", menu.getSizeX(), menu.getSizeY(), menu.getSizeZ())
				: Component.translatable(KEY + "room.inside_unknown");
		ReactorPageText.scaledFit(graphics, font, inside, left, screen.top() + INSIDE_Y, right - left,
				GuiStyle.TEXT_DIM);

		RoomChecklist.Check[] checks = RoomChecklist.Check.values();
		for (int i = 0; i < checks.length; i++) {
			RoomChecklist.Check check = checks[i];
			int y = screen.top() + LIST_Y + i * LIST_ROW;
			RoomChecklist.Mark mark = RoomChecklist.mark(check, status, measured);
			if (mark == RoomChecklist.Mark.FAILED) {
				graphics.fill(left - 2, y - 1, right, y + LIST_ROW - 1, ROW_FAILED_BACK);
			}
			drawMark(graphics, left, y, mark);
			Component label = switch (check) {
				case SIZE -> Component.translatable(check.translationKey(), menu.getRoomMinInner(),
						menu.getRoomMaxInner());
				case GLASS -> Component.translatable(check.translationKey(), menu.getRoomMaxGlassPercent());
				default -> Component.translatable(check.translationKey());
			};
			int colour = switch (mark) {
				case PASSED -> GuiStyle.TEXT;
				case FAILED -> INK_RED;
				case UNCHECKED -> TEXT_UNCHECKED;
			};
			int textX = left + MARK_SIZE + 3;
			ReactorPageText.scaledFit(graphics, font, label, textX, y + 1, right - 2 - textX, colour);
		}
	}

	/** A 9×9 plate: a tick on green, a cross on red, a dot on grey for a check the scan never reached. */
	private static void drawMark(GuiGraphicsExtractor graphics, int x, int y, RoomChecklist.Mark mark) {
		int plate = switch (mark) {
			case PASSED -> ReactorPageText.FILL_GREEN;
			case FAILED -> ReactorPageText.FILL_RED;
			case UNCHECKED -> MARK_UNCHECKED;
		};
		String[] glyph = switch (mark) {
			case PASSED -> MARK_TICK;
			case FAILED -> MARK_CROSS;
			case UNCHECKED -> MARK_DOT;
		};
		graphics.fill(x, y, x + MARK_SIZE, y + MARK_SIZE, ReactorPageText.PLATE_EDGE);
		graphics.fill(x + 1, y + 1, x + MARK_SIZE - 1, y + MARK_SIZE - 1, plate);
		ReactorPageText.glyph(graphics, x + 1, y + 1, glyph, ReactorPageText.GLYPH);
	}

	/**
	 * The box under the map: the verdict in its tone, what to fix, and where — or, for a shell with several
	 * holes, how many there are, since the map shows where. A sealed room says so and points at the «Console»
	 * tab, where the reactor is run.
	 */
	private void drawDetail(GuiGraphicsExtractor graphics, ReactorControllerMenu menu, int[] problems) {
		ReactorConsole.Readout r = ConsoleTabPage.readout(menu);
		ReactorRoomStatus status = menu.getStatus();
		ReactorConsole.Tone tone = r.formed() ? ReactorConsole.Tone.GOOD
				: r.bare() ? ReactorConsole.Tone.WARN : ReactorConsole.Tone.ALARM;
		List<Component> paragraphs = new ArrayList<>();
		paragraphs.add(Component.translatable(r.formed() ? KEY + "room.formed" : ReactorConsole.fixKey(status)));
		int holes = menu.getHoleCount();
		if (status == ReactorRoomStatus.BREACH && holes > 1) {
			paragraphs.add(holes > menu.getListedHoles()
					? Component.translatable(KEY + "room.holes_more", holes, menu.getListedHoles())
					: Component.translatable(KEY + "room.holes", holes));
		} else if (problems.length >= 3) {
			paragraphs.add(where(problems[0], problems[1], problems[2]));
		}
		ReactorPageText.messageBox(graphics, screen.font(), screen.left() + ConsoleTabPage.CONTENT_LEFT,
				screen.top() + DETAIL_Y, ConsoleTabPage.CONTENT_RIGHT - ConsoleTabPage.CONTENT_LEFT,
				DETAIL_BOTTOM - DETAIL_Y, tone, Component.translatable(status.translationKey()), paragraphs);
	}

	// ── Tooltips ─────────────────────────────────────────────────────────────────────────────────

	/** A problem cell lists where each of its holes is; the controller's cell names what it is. */
	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorControllerMenu menu = screen.getMenu();
		int[] problems = problems(menu);
		RoomMapLayout map = layout(menu, problems);
		int ax = screen.left() + MAP_X + MAP_INSET;
		int ay = screen.top() + MAP_Y + MAP_INSET;
		List<Component> lines = new ArrayList<>();
		for (int i = 0; i < problems.length; i += 3) {
			if (onCell(map, ax, ay, problems[i], problems[i + 2], mouseX, mouseY)) {
				lines.add(where(problems[i], problems[i + 1], problems[i + 2]));
			}
		}
		if (lines.isEmpty() && onCell(map, ax, ay, 0, 0, mouseX, mouseY)) {
			lines.add(Component.translatable(KEY + "room.controller"));
		}
		if (lines.isEmpty()) {
			return false;
		}
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(screen.font().split(line, TOOLTIP_WIDTH));
		}
		graphics.setTooltipForNextFrame(screen.font(), wrapped, mouseX, mouseY);
		return true;
	}

	private static boolean onCell(RoomMapLayout map, int ax, int ay, int x, int z, int mouseX, int mouseY) {
		int px = ax + map.cellLeft(x);
		int py = ay + map.cellTop(z);
		return mouseX >= px && mouseX < px + map.cell() && mouseY >= py && mouseY < py + map.cell();
	}

	/** "Problem: 4 east, 2 up, 3 south". */
	private static Component where(int dx, int dy, int dz) {
		return Component.translatable(KEY + "label.where").append(" ").append(describeOffset(dx, dy, dz));
	}

	/**
	 * Turns an offset into words: "4 east, 2 up, 3 south". Axes with no offset are dropped, so a breach
	 * straight above reads "2 up" rather than "0 east, 2 up, 0 south".
	 */
	private static Component describeOffset(int dx, int dy, int dz) {
		Component result = null;
		result = append(result, dx, "east", "west");
		result = append(result, dy, "up", "down");
		result = append(result, dz, "south", "north");
		return result == null ? Component.translatable(KEY + "here") : result;
	}

	private static @Nullable Component append(@Nullable Component soFar, int amount, String positiveKey,
			String negativeKey) {
		if (amount == 0) {
			return soFar;
		}
		Component piece = Component.translatable(KEY + "dir." + (amount > 0 ? positiveKey : negativeKey),
				Math.abs(amount));
		return soFar == null ? piece : soFar.copy().append(", ").append(piece);
	}

	private static final String[] MARK_TICK = {
			"......#",
			".....##",
			"#...##.",
			"##.##..",
			".###...",
			"..#....",
			".......",
	};
	private static final String[] MARK_CROSS = {
			"#.....#",
			".#...#.",
			"..#.#..",
			"...#...",
			"..#.#..",
			".#...#.",
			"#.....#",
	};
	private static final String[] MARK_DOT = {
			".......",
			".......",
			"..###..",
			"..###..",
			"..###..",
			".......",
			".......",
	};
}
