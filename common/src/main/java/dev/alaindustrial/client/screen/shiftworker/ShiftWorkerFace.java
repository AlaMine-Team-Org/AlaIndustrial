package dev.alaindustrial.client.screen.shiftworker;

import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A shift worker's face in a small square of a screen (owner request 7, round 4 of stage 6): the worker's own head
 * — its look's skin, hair and helmet glass — drawn by the worker's renderer, as the card draws the whole body
 * ({@link ShiftWorkerCardScreen}). The body is a display body dressed in the record's look, one per worker, kept
 * while the screen lives; the square clips everything below the neck.
 */
public final class ShiftWorkerFace {

	/** Where the head's middle is above the feet, in blocks: the humanoid head spans 24..32 model pixels. */
	private static final float HEAD_CENTRE = 1.75F;
	/** GUI pixels per block for each pixel of the square: the helmet turned {@link #TURN} degrees fills ~0.75. */
	private static final float SCALE_PER_PIXEL = 1.35F;
	/** How far the face is turned from the viewer, in degrees: enough to read it as a head, not a flat square. */
	private static final float TURN = 20.0F;
	/** Dressed bodies kept at most; a roster far longer than this simply dresses again. */
	private static final int MAX_BODIES = 64;

	private final Map<String, ShiftWorker> bodies = new HashMap<>();

	/**
	 * Draws the face of worker {@code number} in look {@code look} into the {@code size}-pixel square at
	 * {@code (x0, y0)}. Nothing is drawn while this client has no level or the body cannot be made.
	 */
	public void draw(GuiGraphicsExtractor graphics, String number, Identifier look, boolean slim, int settlement,
			int x0, int y0, int size) {
		ShiftWorker body = body(number, look, slim, settlement);
		if (body == null) {
			return;
		}
		EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body)
				.createRenderState(body, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F + TURN;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
		}
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		graphics.entity(state, size * SCALE_PER_PIXEL, new Vector3f(0.0F, HEAD_CENTRE, 0.0F), rotation,
				new Quaternionf(), x0, y0, x0 + size, y0 + size);
	}

	/**
	 * The face on the card's dark model background; greyed like the card's model when the worker is not alive.
	 */
	public void drawFramed(GuiGraphicsExtractor graphics, String number, Identifier look, boolean slim, int settlement,
			boolean alive, int x0, int y0, int size) {
		graphics.fill(x0, y0, x0 + size, y0 + size, GuiStyle.MODEL_BACK_BOTTOM);
		draw(graphics, number, look, slim, settlement, x0, y0, size);
		if (!alive) {
			graphics.nextStratum();
			graphics.fill(x0, y0, x0 + size, y0 + size, GuiStyle.CLOSED_WASH);
		}
	}

	private @Nullable ShiftWorker body(String number, Identifier look, boolean slim, int settlement) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return null;
		}
		String key = number + '|' + look + '|' + slim;
		ShiftWorker known = bodies.get(key);
		if (known != null && known.level() == minecraft.level) {
			return known;
		}
		// A body made only to be looked at, with an id of its own: the renderer reads it (26.3 throws otherwise).
		ShiftWorker worker = ShiftWorker.displayBody(ModMobs.SHIFT_WORKER.type(), minecraft.level);
		if (worker == null) {
			return null;
		}
		RecordNumber.parse(number).ifPresent(parsed -> worker.showRecord(ShiftWorkerRecord.issued(
				parsed, UUID.randomUUID(), look, slim, settlement)));
		if (bodies.size() >= MAX_BODIES) {
			bodies.clear();
		}
		bodies.put(key, worker);
		return worker;
	}
}
