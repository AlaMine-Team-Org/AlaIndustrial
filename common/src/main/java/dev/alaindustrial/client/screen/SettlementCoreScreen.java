package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.client.render.SettlementBorderOverlay;
import dev.alaindustrial.menu.SettlementCoreMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Screen of the settlement core (MOD-831): the energy bar on the left and the town's readout beside it — whether
 * the core holds its territory, its radius, limits and upkeep, and who owns the town. No slots and no
 * inventory (see {@link SettlementCoreMenu}). The first player to open a lit core becomes the owner on the server,
 * and a dark, unowned core says how that happens under the energy bar. The one control is the eye under the bar:
 * "show borders" (MOD-843), the town's territory drawn for this player alone ({@link SettlementBorderOverlay}).
 *
 * <p><b>The owner's name rides in the title.</b> A channel carries a short, so the name cannot; the server titles a
 * claimed core's screen {@code settlement.alaindustrial.core.title_owned} with the name as its argument (the
 * teleporter station's route for the same problem, MOD-093). This screen prints the block's plain name as its
 * heading and the owner's name in the owner row. A town claimed by somebody else while the screen is open is still
 * answered by the channel, as "another player".
 *
 * <p>Text is laid out by {@link SettlementCoreLayout} with {@link TextFit}: the status row may take two lines, the
 * other rows one, the hint three, each scaled down only when wrapping is not enough (MOD-842). The tier is not
 * shown: in the first slice every core is LV, and the radius and the limits already say what the tier means.
 */
public class SettlementCoreScreen extends MachineScreen<SettlementCoreMenu> {

	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/settlement_core.png");

	/** "Show borders": the reach-preview eye, lit while this town's border is shown (MOD-843). */
	private static final ZonePreviewButton BORDERS = new ZonePreviewButton(SettlementCoreLayout.BUTTON_X,
			SettlementCoreLayout.BUTTON_Y, SettlementCoreMenu.BUTTON_TOGGLE_BORDERS,
			"settlement.alaindustrial.core.borders.show", "settlement.alaindustrial.core.borders.hide",
			SettlementBorderOverlay::isShownForOpenScreen);

	/** The owner's name as the title brought it, or null when the town had no owner when the screen opened. */
	@Nullable
	private final String ownerName;

	public SettlementCoreScreen(SettlementCoreMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, SettlementCoreLayout.IMAGE_WIDTH, SettlementCoreLayout.IMAGE_HEIGHT);
		this.ownerName = ownerNameOf(title);
	}

	/** The name argument of a claimed core's title ({@link SettlementCoreBlockEntity#OWNED_TITLE_KEY}), or null. */
	@Nullable
	static String ownerNameOf(Component title) {
		if (title.getContents() instanceof TranslatableContents translatable
				&& SettlementCoreBlockEntity.OWNED_TITLE_KEY.equals(translatable.getKey())
				&& translatable.getArgs().length > 0) {
			Object arg = translatable.getArgs()[0];
			String name = arg instanceof Component component ? component.getString() : String.valueOf(arg);
			return name.isEmpty() ? null : name;
		}
		return null;
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/** The energy bar and its tooltip, drawn by the base (MOD-716). */
	@Override
	protected EnergyBarSpec energyBar() {
		return EnergyBarSpec.LEFT;
	}

	/** No statistics tab: the core has no upgrade panel, so the chip that feeds it can never be fitted. */
	@Override
	protected boolean hasStatsTab() {
		return false;
	}

	/** The block's plain name as the heading — the owner's name has a row of its own — and no "Inventory". */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component heading = Component.translatable("block.alaindustrial.settlement_core");
		graphics.text(this.font, heading, (this.imageWidth - this.font.width(heading)) / 2,
				SettlementCoreLayout.TITLE_Y, GuiStyle.LABEL, false);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		boolean active = this.menu.isActive();
		int x = this.leftPos + SettlementCoreLayout.TEXT_LEFT;
		int width = SettlementCoreLayout.columnWidth();
		int statusLines = block(graphics, Component.translatable(statusKey(active)), x,
				this.topPos + SettlementCoreLayout.ROW_TOP, width, SettlementCoreLayout.STATUS_MAX_LINES,
				active ? GuiStyle.TEXT : GuiStyle.STATUS_BLOCKING);
		int y = this.topPos + SettlementCoreLayout.firstNumberRowY(statusLines);
		y = row(graphics, Component.translatable(SettlementCoreLayout.TERRITORY, this.menu.getTerritoryWidth(),
				this.menu.getTerritoryDepth()), x, y, GuiStyle.TEXT);
		y = row(graphics, Component.translatable(SettlementCoreLayout.HOUSES, this.menu.getHouseLimit()), x, y,
				GuiStyle.TEXT);
		y = row(graphics, Component.translatable(SettlementCoreLayout.WORKERS, this.menu.getWorkerLimit()), x, y,
				GuiStyle.TEXT);
		y = row(graphics, Component.translatable(SettlementCoreLayout.UPKEEP, this.menu.getUpkeep()), x, y,
				GuiStyle.TEXT_DIM);
		y = row(graphics, ownerLine(), x, y, GuiStyle.TEXT);
		if (this.menu.getOwnerRelation() == SettlementCoreBlockEntity.OWNER_NONE && !active && !this.menu.isUnbound()) {
			block(graphics, Component.translatable(SettlementCoreLayout.HINT),
					this.leftPos + SettlementCoreLayout.HINT_LEFT,
					this.topPos + SettlementCoreLayout.hintTop(y - this.topPos), SettlementCoreLayout.hintWidth(),
					SettlementCoreLayout.HINT_MAX_LINES, GuiStyle.TEXT_DIM);
		}
	}

	/** The eye has no panel to hide under: the core has no statistics tab and no upgrade panel. */
	@Override
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		BORDERS.draw(graphics, this.leftPos, this.topPos, mouseX, mouseY);
	}

	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		BORDERS.tooltip(graphics, this.font, this.leftPos, this.topPos, mouseX, mouseY);
	}

	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		return BORDERS.clicked(this.minecraft, this.menu.containerId, this.leftPos, this.topPos, event);
	}

	/** The first row: lit, dark, a second core of its town (R43), or a core of no town (R53). */
	private String statusKey(boolean active) {
		if (this.menu.isUnbound()) {
			return SettlementCoreLayout.STATUS_UNBOUND;
		}
		if (this.menu.isMisplaced()) {
			return SettlementCoreLayout.STATUS_MISPLACED;
		}
		return active ? SettlementCoreLayout.STATUS_ACTIVE : SettlementCoreLayout.STATUS_DARK;
	}

	/** The owner row: nobody, you, or the owner by name. */
	private Component ownerLine() {
		return switch (this.menu.getOwnerRelation()) {
			case SettlementCoreBlockEntity.OWNER_VIEWER -> Component.translatable(SettlementCoreLayout.OWNER_YOU);
			case SettlementCoreBlockEntity.OWNER_OTHER -> ownerName != null
					? Component.translatable(SettlementCoreLayout.OWNER_NAMED, ownerName)
					: Component.translatable(SettlementCoreLayout.OWNER_OTHER);
			default -> Component.translatable(SettlementCoreLayout.OWNER_NONE);
		};
	}

	/** One one-line row at {@code y}; returns the top of the next row. */
	private int row(GuiGraphicsExtractor graphics, Component text, int x, int y, int colour) {
		block(graphics, text, x, y, SettlementCoreLayout.columnWidth(), SettlementCoreLayout.ROW_MAX_LINES, colour);
		return y + SettlementCoreLayout.ROW_STEP;
	}

	/**
	 * {@code text} fitted into {@code width} at ({@code x}, {@code y}) on at most {@code maxLines} lines
	 * ({@link TextFit}); returns how many lines it took.
	 */
	private int block(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int maxLines,
			int colour) {
		TextFit.Fit fit = TextFit.fit(text.getString(), width, maxLines, this.font::width);
		for (int i = 0; i < fit.lines().size(); i++) {
			Component line = Component.literal(fit.lines().get(i));
			int top = y + i * SettlementCoreLayout.LINE_STEP;
			if (fit.scale() >= 1.0f) {
				graphics.text(this.font, line, x, top, colour, false);
				continue;
			}
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, top);
			graphics.pose().scale(fit.scale(), fit.scale());
			graphics.text(this.font, line, 0, 0, colour, false);
			graphics.pose().popMatrix();
		}
		return fit.lines().size();
	}
}
