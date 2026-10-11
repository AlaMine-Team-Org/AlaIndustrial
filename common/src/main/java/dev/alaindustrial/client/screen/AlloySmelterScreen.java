package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.AlloySmelterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Alloy Smelter (MOD-064). The static frame (panel, the three recessed
 * component slots, the empty energy bar and the merge arrow) comes from the 256×256 GUI atlas; the
 * dynamic layer (energy fill, progress fill) is drawn by {@link ProgressMachineScreen}.
 *
 * <p>The three input slots sit in one vertical column rather than a row: it makes visually plain that
 * they are interchangeable — a column of identical cells feeding one arrow reads as "put the parts
 * here", where a row spaced like the other machines' input→output line would suggest an order.
 *
 * <p><b>The arrow says what the machine does (MOD-457).</b> The old 25×9 arrow was the shared
 * one-in/one-out sprite every processing machine uses, and it undersold this block: the alloy smelter is
 * the only machine that takes THREE stacks and returns one. The arrow is now three strands — one per
 * input slot, aligned with them — converging into a single bar that points at the result. It spans the
 * whole input column (42×43 where the old one was 25×9) inside the ordinary 176×166 frame.
 */
public class AlloySmelterScreen extends ProgressMachineScreen<AlloySmelterMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/alloy_smelter.png");

	// Golden merge-arrow fill sprite in the atlas service area, drawn over the grey recess in the frame.
	// The recess is at (65, 22) and 42 wide; the sprite is 45 wide and sits 1-2 px off the groove from its
	// tenth column on, its tip overhanging the groove by 3 px (MOD-737, measured — a redraw is a task of its
	// own). The fill grows left-to-right along the three strands and then down the joined bar.
	// The recipe-viewer hitbox is this rect (PROGRESS_AREA), read by MachineRecipeViewerTargets.ALLOY_ALL.
	private static final ProgressSpec PROGRESS = new ProgressSpec(
			176, 50, 45, 43,  // sprite u/v/w/h
			65, 22,           // dest x/y in the 176×166 frame
			false);           // no min-1px

	/**
	 * Click area of the recipe viewers: exactly the progress sprite (MOD-737). It was a literal 42 wide — the
	 * grey groove — while the sprite the player aims at is 45, so the arrow's last three columns opened
	 * nothing; the area covers the groove and the whole fill now. The arch rule
	 * {@code progress-area-from-sprite} keeps a number from coming back here.
	 */
	public static final GuiRect PROGRESS_AREA = PROGRESS.area();

	/** Atlas, energy bar and progress sprite: the whole declared frame (MOD-716, CLI-3). */
	private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT)
			.withProgress(PROGRESS);

	public AlloySmelterScreen(AlloySmelterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, LAYOUT);
	}
}
