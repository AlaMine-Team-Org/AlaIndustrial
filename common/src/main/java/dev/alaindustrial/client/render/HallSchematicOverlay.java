package dev.alaindustrial.client.render;

import dev.alaindustrial.block.SettlementCoreBlock;
import dev.alaindustrial.network.HallSchematicPayload;
import dev.alaindustrial.network.HallSchematicRequestPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.settlement.works.ConstructionRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The town hall's schematic (MOD-857, owner's round 2, point 7; round 3, point 6): the hall's cells still to build,
 * shown in the world as translucent copies of the real blocks for the one player who turned it on in the core's
 * «Construction» tab. The drone fills them in; a cell built since the last reply goes on the next one.
 *
 * <p>This class owns the switch, the replies and the refresh; the drawing is {@link HallSchematicRenderer}, the
 * settlement core's block entity renderer, which writes the {@link HallSchematicGhosts} baked here from each reply —
 * the block's own model at 45 % alpha with a pale blue plan tint. A world-render hook cannot draw a textured model on
 * 26.3 ({@link FrameGizmos}), a block entity renderer can.
 *
 * <p><b>Refresh.</b> Asked for on the press and again every {@link #REFRESH_TICKS} while shown; the server reads the
 * cached plan against the world each time and keeps nothing. Gone when toggled off, on a change of dimension, when
 * the player is farther than {@link #FAR} from the core, when the core is gone, and on leaving the world
 * ({@code ClientDisconnectReset}).
 */
public final class HallSchematicOverlay {

	/** The tooltip of the switch while the schematic is shown. */
	public static final String HIDE_KEY = ConstructionRow.Action.SCHEMATIC.key() + ".hide";
	/** The action-bar line when the hall has no cell left to build. */
	public static final String COMPLETE_KEY = "settlement.alaindustrial.works.schematic.complete";

	/** How often a shown schematic asks again, in client ticks of game time. */
	static final int REFRESH_TICKS = 100;
	/** Past this many blocks from the core, along the farther horizontal axis, the schematic turns off. */
	static final double FAR = 208.0;

	@Nullable
	private static BlockPos shownCore;
	@Nullable
	private static ResourceKey<Level> dimension;
	private static long lastRequest;
	private static boolean awaitingFirst;
	private static HallSchematicGhosts ghosts = HallSchematicGhosts.empty();

	private HallSchematicOverlay() {
	}

	/** Whether the schematic of the town whose core stands at {@code core} is shown. */
	public static boolean isShown(BlockPos core) {
		return core.equals(shownCore);
	}

	/** The baked ghosts of the schematic of the core at {@code core}, empty when that schematic is not shown. */
	public static HallSchematicGhosts ghostsAt(BlockPos core) {
		return core.equals(shownCore) ? ghosts : HallSchematicGhosts.empty();
	}

	/** The switch: shows the schematic of the town whose core stands at {@code core}, or hides it if shown. */
	public static void toggle(BlockPos core) {
		if (core.equals(shownCore)) {
			clear();
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		clear();
		shownCore = core.immutable();
		dimension = level.dimension();
		awaitingFirst = true;
		request(level);
	}

	/** A reply: the cells to draw now, for the schematic still shown. */
	public static void receive(HallSchematicPayload payload) {
		if (shownCore == null || !payload.core().equals(shownCore)) {
			return; // a late reply to a schematic turned off since
		}
		if (!payload.available()) {
			clear();
			return;
		}
		dimension = payload.dimension();
		ghosts = HallSchematicGhosts.build(shownCore, payload.cells(), Minecraft.getInstance().level);
		if (awaitingFirst && payload.missing() == 0 && Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.sendOverlayMessage(Component.translatable(COMPLETE_KEY));
		}
		awaitingFirst = false;
	}

	/** Drops the schematic (switch off, dimension change, leaving the world). */
	public static void clear() {
		shownCore = null;
		dimension = null;
		awaitingFirst = false;
		ghosts = HallSchematicGhosts.empty();
	}

	/**
	 * Per-frame upkeep, called by each loader's world-render hook next to the settlement border: refreshes the shown
	 * schematic and turns it off when it no longer applies. Draws nothing; {@link HallSchematicRenderer} does.
	 */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState camera) {
		BlockPos core = shownCore;
		if (core == null) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		Vec3 eye = camera.pos;
		if (level == null || !level.dimension().equals(dimension) || eye == null) {
			clear();
			return;
		}
		if (level.isLoaded(core) && !(level.getBlockState(core).getBlock() instanceof SettlementCoreBlock)
				|| Math.max(Math.abs(eye.x - (core.getX() + 0.5)), Math.abs(eye.z - (core.getZ() + 0.5))) > FAR) {
			clear();
			return;
		}
		if (level.getGameTime() - lastRequest >= REFRESH_TICKS || level.getGameTime() < lastRequest) {
			request(level);
		}
	}

	private static void request(ClientLevel level) {
		lastRequest = level.getGameTime();
		if (shownCore != null) {
			NetworkDispatcher.get().sendToServer(new HallSchematicRequestPayload(shownCore));
		}
	}
}
