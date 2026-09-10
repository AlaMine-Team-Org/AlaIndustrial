package dev.alaindustrial.client.render;

import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorStructure;
import dev.alaindustrial.client.AlaClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Translucent schematic of the seven cells a Mirror Concentrator still needs to become the
 * two-by-two-by-two machine (MOD-603).
 *
 * <p><b>It has no state at all.</b> Everything it draws is read out of the world in front of the
 * player, this frame: which block the crosshair is on, which of the four boxes around that core the
 * player is standing beside, and which of its cells are already filled. Nothing is stored, nothing
 * is synced, and there is no packet — which is also why the choice of direction needs none. The
 * player picks a side by walking round the machine and building there; the schematic only says
 * where the parts would go.
 *
 * <p><b>Held open by the sneak key, announced by a line of text.</b> Exactly the shape the kok-sagyz
 * root inspection has: looking at the machine puts a hint beside the hotbar saying which key to
 * hold, and holding it fades the schematic in over about four ticks. A schematic that appeared
 * merely because the crosshair drifted across the machine flashed at the player every time they
 * turned around; behind a key it appears when it was asked for and never otherwise.
 *
 * <p><b>Cheap by construction.</b> Scanning for nearby machines every frame would cost thousands of
 * block lookups; instead the crosshair does the finding. Looking at the grown panel shows the box on
 * the player's side, and looking at a section already placed finds the core it belongs to — at most
 * twenty-eight lookups, and only while something relevant is under the cursor.
 *
 * <p>Drawn on the gizmo path with {@link OverlayGeometry}, exactly like the Mob Repeller's personal
 * dome, and hung off the same per-frame seam both loaders already provide. The root inspection's
 * offscreen target and its two {@code GameRenderer} mixins are deliberately NOT copied: they exist
 * so roots can overlap themselves through solid ground, and seven boxes in open air need none of it.
 */
public final class ConcentratorSchematicRenderer {
	private ConcentratorSchematicRenderer() {
	}

	/** A cell waiting to be filled: pale blue, the same reading as the cable placement ghost. */
	private static final int FILL_FREE = 0x2680E5FF;
	private static final int EDGE_FREE = 0xB3D8F5FF;
	/** The section's own cage, drawn solid enough to read as the block that will land there. */
	private static final int GHOST_FRAME_FREE = 0x6690C8E8;
	private static final int GHOST_GLASS_FREE = 0x2AB8E4F0;
	/** A cell something else is standing in: amber, so "in the way" never reads as "ready". */
	private static final int FILL_BLOCKED = 0x26E8A23C;
	private static final int EDGE_BLOCKED = 0xB3FFC978;
	private static final int GHOST_FRAME_BLOCKED = 0x66E0A44C;
	private static final int GHOST_GLASS_BLOCKED = 0x2AF0C888;
	private static final float EDGE_WIDTH = 2.0f;

	/**
	 * How far the schematic is pulled inside its cell, in blocks.
	 *
	 * <p>Without it the outline lies exactly on the block boundary, coplanar with the ground and with
	 * any neighbour, and the depth buffer cannot decide which is in front: the edges strobe as the
	 * player turns their head. Two thousandths of a block is under a tenth of a pixel on the texture
	 * grid — invisible, and enough for the comparison to have an answer.
	 */
	private static final double INSET = 0.002;

	/** How long the schematic takes to fade in or out, in ticks — the root inspection's own pace. */
	private static final float FADE_TICKS = 4.0f;

	/** Eased 0..1 visibility. Client-only and per-frame; nothing here is ever saved or synced. */
	private static float opacity;
	/** The line drawn beside the hotbar, or {@code null} when the player is not looking at a machine. */
	@Nullable
	private static Component hint;

	/** Per-frame submit, called by the loader adapters with the render-time collector. */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState camera) {
		if (!AlaClientConfig.concentratorSchematicEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			return;
		}
		BlockPos core = coreUnderCrosshair(client, level);
		boolean holding = client.options.keyShift.isDown();
		float step = 1.0F / FADE_TICKS;
		opacity = core != null && holding
				? Math.min(1.0F, opacity + step)
				: Math.max(0.0F, opacity - step);
		if (core == null) {
			hint = null;
			return;
		}
		Direction facing = facingToward(core, client.player.position());

		int missing = 0;
		int blocked = 0;
		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE) {
				continue;
			}
			BlockPos cell = core.offset(part.worldOffset(facing));
			if (ConcentratorStructure.isLooseSection(level.getBlockState(cell))) {
				continue; // already delivered — showing it again would only hide the work left
			}
			missing++;
			boolean free = level.getBlockState(cell).canBeReplaced();
			if (!free) {
				blocked++;
			}
			if (opacity > 0.0F) {
				addSectionGhost(gizmos, cell, free, opacity);
			}
		}
		hint = describe(client, missing, blocked, holding);
		if (missing > 0 && opacity > 0.0F) {
			gizmos.submit(collector, camera, false);
		}
	}

	/**
	 * The line beside the hotbar: what to press, or what is left to do while pressing it.
	 *
	 * <p>The idle line names the key rather than describing the machine, because that is the one
	 * thing a player who has never grown a concentrator cannot find out by looking.
	 */
	@Nullable
	private static Component describe(Minecraft client, int missing, int blocked, boolean holding) {
		if (missing == 0) {
			return null; // whole and working: there is nothing left to say
		}
		if (!holding) {
			return Component.translatable("hud.alaindustrial.concentrator_assembly.hint",
					client.options.keyShift.getTranslatedKeyMessage());
		}
		if (blocked > 0) {
			return Component.translatable("hud.alaindustrial.concentrator_assembly.blocked",
					missing, blocked);
		}
		return Component.translatable("hud.alaindustrial.concentrator_assembly.left", missing);
	}

	/**
	 * The hint beside the hotbar, drawn by each loader's HUD seam.
	 *
	 * <p>Placed like the root inspection's, and for the same reason: under the crosshair it would
	 * cover the machine the player leaned in to look at. Kept in one method so the two loaders cannot
	 * put it in different places.
	 */
	public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (hint == null || client.gui.screen() != null || client.gui.hud.isHidden()) {
			return;
		}
		int width = client.font.width(hint);
		int left = graphics.guiWidth() / 2 + HOTBAR_HALF + 6;
		if (client.options.mainHand().get() == HumanoidArm.LEFT) {
			left += OFFHAND_SLOT;
		}
		int y = graphics.guiHeight() - 16;
		if (left + width > graphics.guiWidth() - 4) {
			left = Math.max(2, graphics.guiWidth() - width - 4);
			y -= 24;
		}
		graphics.fill(left - 4, y - 3, left + width + 4, y + 12, 0x99000000);
		graphics.text(client.font, hint, left, y, 0xFFF0E7CC);
	}

	/** Half the vanilla hotbar's width — the strip the hint must not sit on top of. */
	private static final int HOTBAR_HALF = 91;
	/** The off-hand slot, which vanilla draws on the side OPPOSITE the main hand. */
	private static final int OFFHAND_SLOT = 29;

	/**
	 * One cell of the schematic: a translucent stand-in for the section that goes there, plus the
	 * outline that says where the cell ends.
	 *
	 * <p>The stand-in is the section's OWN cage, not a generic cube — the boxes come from the same
	 * builder that writes the block's model, so what the schematic promises and what the player ends
	 * up placing cannot drift apart. It is drawn as fills without wireframes: outlining twenty-one
	 * small boxes would bury the silhouette under its own lines.
	 */
	private static void addSectionGhost(DrawableGizmoPrimitives gizmos, BlockPos cell, boolean free,
			float fade) {
		double x = cell.getX();
		double y = cell.getY();
		double z = cell.getZ();
		int frame = faded(free ? GHOST_FRAME_FREE : GHOST_FRAME_BLOCKED, fade);
		for (double[] box : ConcentratorSectionGhost.FRAME) {
			OverlayGeometry.addBoxFill(gizmos, shrunk(x, y, z, box), frame);
		}
		OverlayGeometry.addBoxFill(gizmos, shrunk(x, y, z, ConcentratorSectionGhost.GLASS),
				faded(free ? GHOST_GLASS_FREE : GHOST_GLASS_BLOCKED, fade));
		OverlayGeometry.addBoxOutline(gizmos,
				new AABB(x + INSET, y + INSET, z + INSET,
						x + 1 - INSET, y + 1 - INSET, z + 1 - INSET),
				faded(free ? FILL_FREE : FILL_BLOCKED, fade),
				faded(free ? EDGE_FREE : EDGE_BLOCKED, fade), EDGE_WIDTH);
	}

	/** The same colour at {@code fade} of its alpha — how the schematic eases in and out. */
	private static int faded(int argb, float fade) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(fade, 0.0F, 1.0F));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	/** One box of the ghost in world space, pulled inside the cell by {@link #INSET}. */
	private static AABB shrunk(double x, double y, double z, double[] box) {
		return new AABB(x + box[0] + INSET, y + box[1] + INSET, z + box[2] + INSET,
				x + box[3] - INSET, y + box[4] - INSET, z + box[5] - INSET);
	}

	/**
	 * The core of the structure the player is pointing at, or {@code null}.
	 *
	 * <p>Two ways in: the crosshair is on the grown panel itself, or it is on a section the player
	 * has already put down, in which case the panel it belongs to is found by trying every cell that
	 * section could be. Both are bounded lookups, unlike a radius scan.
	 */
	@Nullable
	private static BlockPos coreUnderCrosshair(Minecraft client, ClientLevel level) {
		HitResult hit = client.hitResult;
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = block.getBlockPos();
		if (ConcentratorStructure.isLooseCore(level.getBlockState(pos))) {
			return pos;
		}
		if (!ConcentratorStructure.isLooseSection(level.getBlockState(pos))) {
			return null;
		}
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (ConcentratorPart part : ConcentratorPart.CELLS) {
				if (part == ConcentratorPart.CORE) {
					continue;
				}
				BlockPos candidate = pos.subtract(part.worldOffset(facing));
				if (ConcentratorStructure.isLooseCore(level.getBlockState(candidate))) {
					return candidate;
				}
			}
		}
		return null;
	}

	/**
	 * Which of the four boxes to draw: the one that grows toward the player.
	 *
	 * <p>A structure expands one step along its core's right hand and one step straight back from its
	 * face, so each facing covers exactly one horizontal quadrant around the core. The quadrant the
	 * player is standing in picks the facing — walk round the machine and the schematic follows,
	 * which is the whole of the "choose a side" mechanic.
	 */
	private static Direction facingToward(BlockPos core, Vec3 player) {
		double dx = player.x - (core.getX() + 0.5);
		double dz = player.z - (core.getZ() + 0.5);
		int wantX = dx < 0.0 ? -1 : 1;
		int wantZ = dz < 0.0 ? -1 : 1;
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			Direction right = facing.getClockWise();
			Direction backward = facing.getOpposite();
			int expandX = right.getStepX() + backward.getStepX();
			int expandZ = right.getStepZ() + backward.getStepZ();
			if (expandX == wantX && expandZ == wantZ) {
				return facing;
			}
		}
		return Direction.NORTH;
	}
}
