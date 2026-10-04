package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.entity.StockDisplayFrameEntity;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/**
 * Renderer for the Stock Display Frame (MOD-066). The count is drawn <b>flat on the frame face</b>
 * (sign-text idiom: {@code SubmitNodeCollector.submitText} with a {@code scale(s, -s, s)} pose —
 * see vanilla {@code AbstractSignRenderer}), inside the frame's bottom strip; the displayed filter
 * item is raised and slightly shrunk to free that strip. {@code submit} re-implements the vanilla
 * {@code ItemFrameRenderer} body (frame model + item) because the item transform must change —
 * every pose constant below is copied from the decompiled 26.2 source, with the deviations marked.
 */
public class StockDisplayFrameRenderer extends ItemFrameRenderer<StockDisplayFrameEntity> {
	/** Hide the count past this distance (storage-mod convention; keeps big warehouses cheap). */
	private static final double MAX_TEXT_DISTANCE = 24.0;
	/** Font scale on the frame plane: 8px glyphs → 0.16 blocks tall (fits the 3-px bottom strip). */
	private static final float TEXT_SCALE = 0.02F;
	/** Text center: 0.17 blocks below frame center — the freed bottom strip of the inner opening. */
	private static final float TEXT_Y = -0.17F;
	/** Raise the filter item by 1.3px and shrink 0.5→0.4 so the bottom strip is clear. */
	private static final float ITEM_RAISE = 0.08F;
	private static final float ITEM_SCALE_WITH_COUNT = 0.4F;

	/**
	 * The frame's own border texture (block atlas; stitched via the mod's
	 * {@code assets/minecraft/atlases/blocks.json} single-source entry — atlas JSONs merge
	 * additively across packs). The backboard reuses the vanilla item-frame background sprite at
	 * runtime (already in the block atlas via the vanilla model) — referencing it is fine, copying
	 * the PNG into the mod would not be (rule 3).
	 */
	private static final SpriteId WOOD_SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("stock_display_frame_wood"));
	private static final SpriteId BACK_SPRITE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("item_frame");
	/** Same cutout block/item sheet the water-mill custom geometry uses (correct depth/cull/mips). */
	private static final RenderType RENDER_TYPE =
			WOOD_SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	/** Vanilla directional diffuse, same constants block models get baked with. */
	private static final float SHADE_NS = 0.8F;
	private static final float SHADE_WE = 0.6F;
	private static final float SHADE_UP = 1.0F;
	private static final float SHADE_DOWN = 0.5F;

	private final SpriteGetter sprites;

	/** Vanilla render state + the synced stock count extracted alongside it. */
	public static class State extends ItemFrameRenderState {
		public int count = StockDisplayFrameEntity.NO_CONTAINER;
	}

	public StockDisplayFrameRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.sprites = context.getSprites();
	}

	@Override
	public ItemFrameRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(StockDisplayFrameEntity entity, ItemFrameRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state instanceof State s) {
			s.count = entity.getStockCount();
		}
		// Drop the vanilla frame model the base extract resolved — this renderer draws its own
		// border geometry (custom texture) + the vanilla backboard sprite in submit(). The vanilla
		// model cannot be re-textured: BlockModelResolver.updateForItemFrame hardwires the
		// minecraft:item_frame fake blockstate.
		state.frameModel.clear();
	}

	@Override
	public void submit(ItemFrameRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		int count = state instanceof State s ? s.count : StockDisplayFrameEntity.NO_CONTAINER;
		// The count shows only when a filter item is in the frame — an empty frame stays a plain
		// frame (player decision 2026-07-12; the empty-frame "count everything" mode was dropped).
		boolean showCount = count >= 0
				&& !state.item.isEmpty()
				&& state.distanceToCameraSq < MAX_TEXT_DISTANCE * MAX_TEXT_DISTANCE;

		// Leash/name-tag pass of EntityRenderer.submit (vanilla ItemFrameRenderer calls it first).
		super.submitNameDisplay(state, poseStack, collector, camera);

		// -- Frame + item: vanilla ItemFrameRenderer.submit body (26.2), item transform adjusted. --
		poseStack.pushPose();
		Direction direction = state.direction;
		Vec3 renderOffset = this.getRenderOffset(state);
		poseStack.translate(-renderOffset.x(), -renderOffset.y(), -renderOffset.z());
		poseStack.translate(direction.getStepX() * 0.46875, direction.getStepY() * 0.46875,
				direction.getStepZ() * 0.46875);
		float xRot;
		float yRot;
		if (direction.getAxis().isHorizontal()) {
			xRot = 0.0F;
			yRot = 180.0F - direction.toYRot();
		} else {
			xRot = -90 * direction.getAxisDirection().getStep();
			yRot = 180.0F;
		}
		Poses.rotate(poseStack, Axis.XP.rotationDegrees(xRot));
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(yRot));

		// Own frame geometry instead of the (cleared) vanilla frameModel: the same element boxes and
		// UVs as vanilla models/block/template_item_frame.json, but the border samples the mod's
		// texture while the backboard keeps the vanilla background sprite.
		if (!state.isInvisible) {
			poseStack.pushPose();
			poseStack.translate(-0.5F, -0.5F, -0.5F);
			TextureAtlasSprite wood = this.sprites.get(WOOD_SPRITE);
			TextureAtlasSprite back = this.sprites.get(BACK_SPRITE);
			int light = state.lightCoords;
			collector.submitCustomGeometry(poseStack, RENDER_TYPE,
					(pose, buffer) -> renderFrameGeometry(pose, buffer, wood, back, light));
			poseStack.popPose();
		}

		if (state.isInvisible) {
			poseStack.translate(0.0F, 0.0F, 0.5F);
		} else {
			poseStack.translate(0.0F, 0.0F, 0.4375F);
		}

		// Filter item (maps are rejected by the entity, so no map branch). Deviation from vanilla:
		// while the count is shown, the item moves up and shrinks to clear the bottom strip.
		if (!state.item.isEmpty()) {
			poseStack.pushPose();
			Poses.rotate(poseStack, Axis.ZP.rotationDegrees(state.rotation * 360.0F / 8.0F));
			if (showCount) {
				poseStack.translate(0.0F, ITEM_RAISE, 0.0F);
				poseStack.scale(ITEM_SCALE_WITH_COUNT, ITEM_SCALE_WITH_COUNT, ITEM_SCALE_WITH_COUNT);
			} else {
				poseStack.scale(0.5F, 0.5F, 0.5F);
			}
			state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY,
					state.outlineColor);
			poseStack.popPose();
		}

		// -- The count, flat on the frame face (sign-text idiom). --
		// The frame's local space points +z INTO the wall (the item sits at z=+0.4375 by the
		// backboard); a sign's front-text space is exactly this rotated 180° around Y (sign:
		// YP(-toYRot), frame: YP(180-toYRot)). Flip into sign-front orientation so the glyphs face
		// the viewer unmirrored, then nudge +z (now toward the viewer) off the backboard — the same
		// offset direction as the wall sign's TEXT_OFFSET (verified against StandingSignRenderer).
		if (showCount) {
			Font font = this.getFont();
			Poses.rotate(poseStack, Axis.YP.rotationDegrees(180.0F));
			poseStack.translate(0.0F, TEXT_Y, 0.03F);
			poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
			FormattedCharSequence text =
					Component.literal(formatCount(count)).getVisualOrderText();
			collector.submitText(poseStack, -font.width(text) / 2.0F, -4.0F, text, false,
					Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, 0xFFFFFFFF, 0, 0xFF000000);
		}

		poseStack.popPose();
	}

	/**
	 * The frame's element boxes + per-face UVs, transcribed 1:1 from vanilla
	 * {@code models/block/template_item_frame.json} (coordinates in 16ths of a block; the caller's
	 * pose already holds the {@code translate(-0.5,-0.5,-0.5)} model-space origin). The border bars
	 * sample the mod texture, the backboard the vanilla background sprite.
	 */
	private static void renderFrameGeometry(PoseStack.Pose pose, VertexConsumer buffer,
			TextureAtlasSprite wood, TextureAtlasSprite back, int light) {
		QuadEmitter quads = new QuadEmitter(pose, buffer, light);
		// Backboard (3,3,15.5)-(13,13,16): the visible face is north; south sits against the wall.
		pixelFace(quads, back, Direction.NORTH, 3, 3, 15.5F, 13, 13, 16, 3, 3, 13, 13);
		pixelFace(quads, back, Direction.SOUTH, 3, 3, 15.5F, 13, 13, 16, 3, 3, 13, 13);
		// Bottom bar (2,2,15)-(14,3,16).
		bar(quads, wood, 2, 2, 15, 14, 3, 16,
				new float[]{2, 13, 14, 14}, new float[]{2, 13, 14, 14},
				new float[]{2, 15, 14, 16}, new float[]{2, 0, 14, 1},
				new float[]{15, 13, 16, 14}, new float[]{0, 13, 1, 14});
		// Top bar (2,13,15)-(14,14,16).
		bar(quads, wood, 2, 13, 15, 14, 14, 16,
				new float[]{2, 2, 14, 3}, new float[]{2, 2, 14, 3},
				new float[]{2, 15, 14, 16}, new float[]{2, 0, 14, 1},
				new float[]{15, 2, 16, 3}, new float[]{0, 2, 1, 3});
		// Left bar (2,3,15)-(3,13,16) — no up/down faces in the vanilla template.
		pixelFace(quads, wood, Direction.NORTH, 2, 3, 15, 3, 13, 16, 13, 3, 14, 13);
		pixelFace(quads, wood, Direction.SOUTH, 2, 3, 15, 3, 13, 16, 2, 3, 3, 13);
		pixelFace(quads, wood, Direction.WEST, 2, 3, 15, 3, 13, 16, 15, 3, 16, 13);
		pixelFace(quads, wood, Direction.EAST, 2, 3, 15, 3, 13, 16, 0, 3, 1, 13);
		// Right bar (13,3,15)-(14,13,16).
		pixelFace(quads, wood, Direction.NORTH, 13, 3, 15, 14, 13, 16, 2, 3, 3, 13);
		pixelFace(quads, wood, Direction.SOUTH, 13, 3, 15, 14, 13, 16, 13, 3, 14, 13);
		pixelFace(quads, wood, Direction.WEST, 13, 3, 15, 14, 13, 16, 15, 3, 16, 13);
		pixelFace(quads, wood, Direction.EAST, 13, 3, 15, 14, 13, 16, 0, 3, 1, 13);
	}

	/** A horizontal bar with all six faces (n/s/up/down/w/e UV rects in template order). */
	private static void bar(QuadEmitter quads, TextureAtlasSprite sprite,
			float x1, float y1, float z1, float x2, float y2, float z2,
			float[] n, float[] s, float[] up, float[] down, float[] w, float[] e) {
		pixelFace(quads, sprite, Direction.NORTH, x1, y1, z1, x2, y2, z2, n[0], n[1], n[2], n[3]);
		pixelFace(quads, sprite, Direction.SOUTH, x1, y1, z1, x2, y2, z2, s[0], s[1], s[2], s[3]);
		pixelFace(quads, sprite, Direction.UP, x1, y1, z1, x2, y2, z2, up[0], up[1], up[2], up[3]);
		pixelFace(quads, sprite, Direction.DOWN, x1, y1, z1, x2, y2, z2, down[0], down[1], down[2], down[3]);
		pixelFace(quads, sprite, Direction.WEST, x1, y1, z1, x2, y2, z2, w[0], w[1], w[2], w[3]);
		pixelFace(quads, sprite, Direction.EAST, x1, y1, z1, x2, y2, z2, e[0], e[1], e[2], e[3]);
	}

	/**
	 * One box face in the template's units — coordinates and UVs in 16ths — laid out by
	 * {@link QuadEmitter#cubeFace} (vanilla block-model corner order and UV rule) and shaded by the side
	 * it faces, the way a baked block model is.
	 */
	private static void pixelFace(QuadEmitter quads, TextureAtlasSprite sprite, Direction dir,
			float x1, float y1, float z1, float x2, float y2, float z2, float u1, float v1, float u2, float v2) {
		float shade = switch (dir) {
			case NORTH, SOUTH -> SHADE_NS;
			case WEST, EAST -> SHADE_WE;
			case UP -> SHADE_UP;
			case DOWN -> SHADE_DOWN;
		};
		quads.sprite(sprite).shade(shade).cubeFace(dir, x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16,
				u1 / 16, v1 / 16, u2 / 16, v2 / 16);
	}

	/** Exact below 10 000, then abbreviated: 12345 → "12.3k", 3400000 → "3.4M". */
	static String formatCount(int count) {
		if (count < 10_000) {
			return Integer.toString(count);
		}
		if (count < 1_000_000) {
			return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
		}
		return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
	}
}
