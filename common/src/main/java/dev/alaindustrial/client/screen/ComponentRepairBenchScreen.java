package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import dev.alaindustrial.core.machine.ComponentTier;
import dev.alaindustrial.core.machine.RepairStatus;
import dev.alaindustrial.item.misc.DurableComponentItem;
import dev.alaindustrial.menu.ComponentRepairBenchMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Texture-backed screen for the Component Repair Bench (MOD-384). The frame, energy fill and progress
 * arrow are the shared {@link ProgressMachineScreen} treatment; what this screen adds is the teaching.
 *
 * <p><b>Why this screen carries more hinting than its neighbours.</b> Every other machine here is
 * self-explanatory from a recipe viewer: press the arrow, see "ingot in, plate out". The bench has no
 * recipes at all — it mutates the component in the slot — so a recipe viewer has nothing to show and a
 * first-time player is left with two grey squares and an arrow. Playtesting confirmed exactly that
 * ("I could not work out how to repair anything"). So the screen answers the three questions itself:
 *
 * <ul>
 *   <li><b>What goes where</b> — cycling ghost items in the empty slots (rotor ↔ wheel on the left of
 *       the arrow's target, the three materials on the material side).</li>
 *   <li><b>What do I do now</b> — the status row never goes silent; an empty bench asks for a worn
 *       component instead of showing nothing.</li>
 *   <li><b>What will it cost</b> — hovering the arrow prints the live cost, the durability price and
 *       how many repairs the component has left.</li>
 * </ul>
 */
public class ComponentRepairBenchScreen extends ProgressMachineScreen<ComponentRepairBenchMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/component_repair_bench.png");

	/** Golden progress arrow in the atlas service area — same geometry as the Electric Furnace family. */
	private static final ProgressSpec PROGRESS = new ProgressSpec(
			176, 44, 25, 9,   // sprite u/v/w/h
			82, 38,            // dest x/y in the 176×166 frame
			true);             // min 1 px: a T3 repair is 2250 ticks, so the first visible pixel is slow

	/** Hover box of the progress arrow, matching {@link #PROGRESS}'s destination. */
	private static final int ARROW_X = 82;
	private static final int ARROW_Y = 38;
	private static final int ARROW_W = 25;
	private static final int ARROW_H = 9;

	/**
	 * How long each item stays up in a cycling ghost hint. Was 1200 ms; playtesting called that
	 * flicker, so it is three times slower — long enough to read one answer before the next.
	 */
	private static final long GHOST_CYCLE_MS = 3600L;

	/** Wrap width for the arrow tooltip, matching the upgrade panel's. */
	private static final int TOOLTIP_WIDTH = 200;

	/**
	 * Baseline (relative to topPos) of the centred status line, in the clear band between the slot row
	 * and the player inventory label. Public for the same reason as
	 * {@code WaterMillScreen.STATUS_TEXT_Y}: the L3 status-row gate crops its pixel comparison to
	 * exactly this row, and a copy of the number in the test would drift the moment the layout moves.
	 */
	public static final int STATUS_TEXT_Y = 57;

	/**
	 * Horizontal band the status row is centred inside — clear of the energy bar, which occupies
	 * x=17..27 and extends past this row's baseline.
	 */
	private static final int STATUS_BAND_LEFT = 32;
	private static final int STATUS_BAND_RIGHT = 168;

	/**
	 * Smallest the status row may shrink to before it stops being readable at GUI scale 2.
	 *
	 * <p>0.7 rather than a rounder 0.75 for measured headroom: the longest shipped translation of the
	 * "insert a worn part" prompt runs about 176 px against a 136 px band, i.e. it needs 0.77 — a 0.75
	 * floor would have clipped the very string that exposed this bug, and the next translation could
	 * be longer still. Below the floor the label overflows rather than becoming unreadable, and that
	 * is the signal to shorten the translation instead.
	 */
	private static final float MIN_STATUS_SCALE = 0.7f;

	public ComponentRepairBenchScreen(ComponentRepairBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/** The component currently loaded, or empty. Read off the synced slot, so it is client-safe. */
	private ItemStack target() {
		return this.menu.getSlot(ComponentRepairBenchBlockEntity.TARGET_SLOT).getItem();
	}

	/** The grade of the loaded component, or {@code null} when the slot holds nothing recognised. */
	private ComponentTier loadedTier() {
		return AbstractGeneratorBlockEntity.tierOf(target(), null);
	}

	/** Pick the entry of {@code options} whose turn it is, so a hint can name several valid answers. */
	private static ItemStack cycling(List<Item> options) {
		if (options.isEmpty()) {
			return ItemStack.EMPTY;
		}
		// System.currentTimeMillis() is the clock the rest of this package already animates on
		// (MachineScreen's press flashes, UpgradePanelController) — one time source, not two.
		int i = (int) ((System.currentTimeMillis() / GHOST_CYCLE_MS) % options.size());
		return new ItemStack(options.get(i));
	}

	/**
	 * Ghost hints for both empty slots.
	 *
	 * <p>The component hint cycles rotor ↔ wheel because both are equally valid and a single frozen
	 * picture would read as "only this one fits". The material hint is smarter: with a component
	 * already loaded it stops cycling and shows the ONE plate that grade actually needs — at that
	 * point the player is no longer asking "what kind of thing goes here" but "which one do I fetch".
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, ComponentRepairBenchBlockEntity.TARGET_SLOT,
				cycling(List.of(ModContent.WINDMILL_ROTOR.get(), ModContent.WATER_MILL_WHEEL.get())));

		ComponentTier tier = loadedTier();
		ItemStack materialHint = tier != null
				? new ItemStack(ComponentRepairBenchBlockEntity.materialFor(tier))
				: cycling(List.of(
						ComponentRepairBenchBlockEntity.materialFor(ComponentTier.WINDMILL_ROTOR),
						ComponentRepairBenchBlockEntity.materialFor(ComponentTier.WINDMILL_ROTOR_REINFORCED),
						ComponentRepairBenchBlockEntity.materialFor(ComponentTier.WINDMILL_ROTOR_ADVANCED)));
		ghostHint(graphics, ComponentRepairBenchBlockEntity.MATERIAL_SLOT, materialHint);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.drawMachineFrame(graphics, mouseX, mouseY, partialTick);
		drawStatusText(graphics);
	}

	/** One centred row: what the bench is doing, or the single reason it is not. Never blank. */
	private void drawStatusText(GuiGraphicsExtractor graphics) {
		RepairStatus status = this.menu.getStatus();
		String key = switch (status) {
			// An empty bench used to say nothing, on the theory that two empty slots are the message.
			// Playtesting disagreed: it is the state a player meets first and the one they get stuck on.
			case NO_TARGET -> "gui.alaindustrial.component_repair_bench.insert_component";
			case NOT_DAMAGED -> "gui.alaindustrial.component_repair_bench.not_damaged";
			case LIMIT_REACHED -> "gui.alaindustrial.component_repair_bench.limit_reached";
			case NEEDS_MATERIAL -> "gui.alaindustrial.component_repair_bench.needs_material";
			case READY -> "gui.alaindustrial.component_repair_bench.repairing";
		};
		Component label = Component.translatable(key);
		int colour = status == RepairStatus.READY ? GuiStyle.TEXT : GuiStyle.TEXT_DIM;
		drawFittedStatus(graphics, label, colour);
	}

	/**
	 * Draw the status label inside the band between the energy bar and the right border, shrinking it
	 * rather than letting it escape the frame.
	 *
	 * <p><b>Both edges are load-bearing and both were got wrong once.</b> Centring across the whole
	 * window put the label over the energy bar (the bar runs to y=64, this row sits at y=57). Clamping
	 * only the left edge then pushed long locales out through the right border instead — Russian
	 * the longest translation of the prompt ran past the frame in the dev client. A status row cannot be
	 * truncated (the whole point is that the player reads the reason) and there is no second line to
	 * wrap onto: the band below is 8 px before the inventory label. So it scales to fit, which is also
	 * the only approach that cannot be re-broken by a future translation.
	 */
	private void drawFittedStatus(GuiGraphicsExtractor graphics, Component label, int colour) {
		int band = STATUS_BAND_RIGHT - STATUS_BAND_LEFT;
		int width = this.font.width(label);
		int y = this.topPos + STATUS_TEXT_Y;
		if (width <= band) {
			graphics.text(this.font, label,
					this.leftPos + STATUS_BAND_LEFT + (band - width) / 2, y, colour, false);
			return;
		}
		// Floor the shrink: past roughly three quarters the row stops being readable at GUI scale 2,
		// and a label that long is a translation to shorten, not a rendering problem to hide.
		float scale = Math.max(MIN_STATUS_SCALE, (float) band / width);
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.leftPos + STATUS_BAND_LEFT, y);
		graphics.pose().scale(scale, scale);
		// Centre inside the band measured in the SCALED coordinate space, or the row drifts left.
		int tx = Math.max(0, (int) ((band / scale - width) / 2.0f));
		graphics.text(this.font, label, tx, 0, colour, false);
		graphics.pose().popMatrix();
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
		// The arrow stands in for the recipe entry this machine cannot have: pressing it in a recipe
		// viewer finds nothing, so hovering it has to carry the whole deal instead.
		if (this.isHovering(ARROW_X, ARROW_Y, ARROW_W, ARROW_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, repairTooltip(), mouseX, mouseY);
		}
	}

	/**
	 * Cost, durability price and remaining allowance — for the loaded grade, or the T1 example while
	 * the bench is empty.
	 *
	 * <p>Every number here comes off a sync channel, never out of {@link dev.alaindustrial.Config}:
	 * the client's config is a separate file, so a locally-read figure would print confidently wrong
	 * values on any server that retuned its balance. Only the material NAME is resolved client-side,
	 * and that is safe — it is derived from the synced stack, not from a tunable.
	 */
	private List<FormattedCharSequence> repairTooltip() {
		ComponentTier tier = loadedTier();
		ComponentTier shown = tier != null ? tier : ComponentTier.WINDMILL_ROTOR;
		ItemStack material = new ItemStack(ComponentRepairBenchBlockEntity.materialFor(shown));

		int cost = this.menu.getRepairEuCost();
		int decay = this.menu.getDecayPercent();
		int max = this.menu.getMaxRepairs();

		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.alaindustrial.component_repair_bench.tip_title"));
		lines.add(Component.translatable("gui.alaindustrial.component_repair_bench.tip_cost",
						material.getHoverName(), cost)
				.withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("gui.alaindustrial.component_repair_bench.tip_rule", decay, max)
				.withStyle(ChatFormatting.GRAY));
		if (tier != null) {
			int done = DurableComponentItem.repairs(target());
			lines.add(Component.translatable("gui.alaindustrial.component_repair_bench.tip_left",
							Math.max(0, max - done), max)
					.withStyle(ChatFormatting.GRAY));
		}

		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(this.font.split(line, TOOLTIP_WIDTH));
		}
		return wrapped;
	}
}
