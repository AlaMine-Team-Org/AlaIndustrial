package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.core.machine.RotorSpin;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The energy crystal inside the Energy Condenser (MOD-546).
 *
 * <p>It is the block's gauge, and it reads at a glance: the crystal a condenser is carrying IS the
 * clot it would hand over right now. Tier I is the small cell, tier II grows the shell around it,
 * tier III adds the halves that breathe apart and back together. Below the first threshold there is
 * no clot yet and nothing is drawn — a crystal hanging in a condenser whose slot is empty promises
 * an item the player cannot take.
 *
 * <p>Built from the designer's cubes rather than from a sphere of latitude rings: the shape, its
 * three stages and the turn rate all come from one Blockbench file, and the geometry is generated
 * out of it into {@link EnergyCondenserCrystalGeometry}. The frame around it is an ordinary block
 * model and lights normally — only the crystal is forced bright, the same split the incubator uses
 * for its item and its glass.
 *
 * <p>Why hand-written vertices instead of {@code ModelPart}, which the centrifuge and the sprinkler
 * use: the designer's texture is a 69×69 palette addressed with per-face UVs, often one pixel to a
 * face, and a vanilla {@code CubeListBuilder} can only lay out box UVs. Feeding this through box UVs
 * would mean repacking the texture with a script — replacing the very file the designer edits.
 */
public final class EnergyCondenserBlockEntityRenderer
		implements BlockEntityRenderer<EnergyCondenserBlockEntity,
				EnergyCondenserBlockEntityRenderer.State> {

	private static final SpriteId SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("energy_condenser_body_on"));
	private static final RenderType SOLID_TYPE =
			SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	/**
	 * The force field around the cell is the only translucent part — one cube per stage, drawn at
	 * 39 % alpha straight out of the texture. It goes last so the solid crystal behind it is already
	 * in the depth buffer.
	 */
	private static final RenderType FIELD_TYPE =
			SPRITE.renderType(ignored -> Sheets.translucentBlockItemSheet());

	/** One turn per three seconds in the source animation — 60 ticks, so six degrees a tick. */
	private static final float SPIN_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 60.0);
	/** The float and the breathing share the animation's three-second loop. */
	private static final float CYCLE_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 60.0);
	private static final float PIXEL = 1.0F / 16.0F;
	/** Amplitudes straight from the keyframes: half a pixel for the bare core, a quarter for the
	 * bigger crystals, one pixel for the halves that pull apart. */
	private static final float BOB_SMALL = 0.25F * PIXEL;
	private static final float BOB_LARGE = 0.5F * PIXEL;
	private static final float SPREAD = 1.0F * PIXEL;

	private static final Stage[] STAGES = {
			new Stage(EnergyCondenserCrystalGeometry.TIER1_BODY,
					EnergyCondenserCrystalGeometry.TIER1_FIELD,
					EnergyCondenserCrystalGeometry.TIER1_SPIN,
					EnergyCondenserCrystalGeometry.TIER1_SPIN_DOWN,
					EnergyCondenserCrystalGeometry.TIER1_SPIN_UP,
					BOB_LARGE),
			new Stage(EnergyCondenserCrystalGeometry.TIER2_BODY,
					EnergyCondenserCrystalGeometry.TIER2_FIELD,
					EnergyCondenserCrystalGeometry.TIER2_SPIN,
					EnergyCondenserCrystalGeometry.TIER2_SPIN_DOWN,
					EnergyCondenserCrystalGeometry.TIER2_SPIN_UP,
					BOB_SMALL),
			new Stage(EnergyCondenserCrystalGeometry.TIER3_BODY,
					EnergyCondenserCrystalGeometry.TIER3_FIELD,
					EnergyCondenserCrystalGeometry.TIER3_SPIN,
					EnergyCondenserCrystalGeometry.TIER3_SPIN_DOWN,
					EnergyCondenserCrystalGeometry.TIER3_SPIN_UP,
					BOB_SMALL),
	};

	private final SpriteGetter sprites;

	public EnergyCondenserBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		/** 0, 1 or 2 — index into {@link #STAGES}, not the clot tier itself. */
		int stage;
		float angle;
		float bob;
		float spread;
		/** No clot banked yet: nothing is drawn, and that absence is the readout. */
		boolean empty;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EnergyCondenserBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition,
				breakProgress);
		// The crystal IS the clot in the output slot, so it may not appear before that clot exists.
		// Below the first threshold the slot is empty and the screen says "below tier I" — a crystal
		// hanging there anyway promises the player an item they cannot take.
		int tier = entity.tierForBank();
		state.empty = tier <= 0;
		state.stage = Math.max(0, tier - 1);
		// floorMod before the float: a world tens of millions of ticks old loses tick resolution in
		// a float, and the crystal freezes mid-turn.
		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
		float time = Math.floorMod(gameTime, RotorSpin.TIME_WRAP) + partialTicks;
		state.angle = time * SPIN_RADIANS_PER_TICK;
		// The keyframes ramp linearly up and back down, which turns at the peak with a visible
		// kink; a raised cosine covers the same travel over the same three seconds and reads as
		// floating rather than as being pulled.
		float wave = 0.5F - 0.5F * Mth.cos(time * CYCLE_RADIANS_PER_TICK);
		state.bob = STAGES[state.stage].bob() * wave;
		state.spread = SPREAD * wave;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (state.empty) {
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(SPRITE);
		Stage stage = STAGES[state.stage];

		poseStack.pushPose();
		poseStack.translate(0.0F, state.bob, 0.0F);
		submit(collector, poseStack, SOLID_TYPE, stage.body(), sprite);

		if (!stage.spin().isEmpty() || !stage.spinDown().isEmpty() || !stage.spinUp().isEmpty()) {
			poseStack.pushPose();
			// The pivot is the middle of the block on X and Z; its height does not matter, because
			// a turn about Y leaves everything on that axis where it was.
			poseStack.translate(0.5F, 0.0F, 0.5F);
			poseStack.mulPose(Axis.YP.rotation(state.angle));
			poseStack.translate(-0.5F, 0.0F, -0.5F);
			submit(collector, poseStack, SOLID_TYPE, stage.spin(), sprite);
			submitOffset(collector, poseStack, stage.spinDown(), sprite, -state.spread);
			submitOffset(collector, poseStack, stage.spinUp(), sprite, state.spread);
			poseStack.popPose();
		}

		submit(collector, poseStack, FIELD_TYPE, stage.field(), sprite);
		poseStack.popPose();
	}

	private static void submitOffset(SubmitNodeCollector collector, PoseStack poseStack, Mesh mesh,
			TextureAtlasSprite sprite, float offsetY) {
		if (mesh.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.0F, offsetY, 0.0F);
		submit(collector, poseStack, SOLID_TYPE, mesh, sprite);
		poseStack.popPose();
	}

	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type,
			Mesh mesh, TextureAtlasSprite sprite) {
		if (mesh.isEmpty()) {
			return;
		}
		collector.submitCustomGeometry(poseStack, type,
				(pose, consumer) -> mesh.emit(pose, consumer, sprite));
	}

	/** One stage of the crystal: the parts that move independently, plus its float amplitude. */
	private record Stage(Mesh body, Mesh field, Mesh spin, Mesh spinDown, Mesh spinUp, float bob) {
		Stage(float[][] body, float[][] field, float[][] spin, float[][] spinDown, float[][] spinUp,
				float bob) {
			this(new Mesh(body), new Mesh(field), new Mesh(spin), new Mesh(spinDown),
					new Mesh(spinUp), bob);
		}
	}

	/**
	 * A stage's cubes flattened into ready vertices once, at class-load time.
	 *
	 * <p>The rotation of a cube is trigonometry, and doing it per frame per block would pay for the
	 * designer's detail every tick. Here each cube is expanded, rotated and turned into normals once;
	 * what is left in the render path is a walk over a float array.
	 */
	private static final class Mesh {
		/** Eight floats per vertex: position, UV inside the sprite, normal. */
		private static final int STRIDE = 8;
		private static final int NO_FACE = -1;

		/**
		 * Corner picks per face, in the vanilla winding — the same order {@code FaceInfo} feeds the
		 * block bakery, so a face drawn here is visible from exactly the side it would be as part of
		 * a block model. Each entry is three bits: X, Y, Z, set meaning the {@code to} extent.
		 */
		private static final int[][] FACE_CORNERS = {
				{0b110, 0b100, 0b000, 0b010}, // north — z at from
				{0b111, 0b101, 0b100, 0b110}, // east  — x at to
				{0b011, 0b001, 0b101, 0b111}, // south — z at to
				{0b010, 0b000, 0b001, 0b011}, // west  — x at from
				{0b010, 0b011, 0b111, 0b110}, // up    — y at to
				{0b001, 0b000, 0b100, 0b101}, // down  — y at from
		};
		private static final float[][] FACE_NORMALS = {
				{0.0F, 0.0F, -1.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 1.0F},
				{-1.0F, 0.0F, 0.0F}, {0.0F, 1.0F, 0.0F}, {0.0F, -1.0F, 0.0F},
		};

		private final float[] data;

		static {
			// A face has to be flat, and this table is where that quietly stops being true: the four
			// corner picks are bit patterns, and a wrong bit puts two of them on the far side of the
			// cube. The result still renders — as a slab cutting through the box, with a normal that
			// no longer matches its own geometry, which lights it as if it faced somewhere else. That
			// shipped once and read in game as "the crystal has no volume". Checked here, at class
			// load, because the alternative is checking it by eye on a rotating four-pixel object.
			for (int face = 0; face < 6; face++) {
				int axis = face == 0 || face == 2 ? 2 : (face == 1 || face == 3 ? 0 : 1);
				int bit = axis == 0 ? 0b100 : (axis == 1 ? 0b010 : 0b001);
				boolean atTo = FACE_NORMALS[face][axis] > 0.0F;
				for (int corner : FACE_CORNERS[face]) {
					if (((corner & bit) != 0) != atTo) {
						throw new IllegalStateException(
								"face " + face + " is not flat on axis " + axis);
					}
				}
			}
		}

		private Mesh(float[][] cubes) {
			int faces = 0;
			for (float[] cube : cubes) {
				for (int face = 0; face < 6; face++) {
					if (cube[12 + face * 4] != NO_FACE) {
						faces++;
					}
				}
			}
			this.data = new float[faces * 4 * STRIDE];
			int at = 0;
			for (float[] cube : cubes) {
				at = write(cube, at);
			}
		}

		private boolean isEmpty() {
			return this.data.length == 0;
		}

		private int write(float[] cube, int at) {
			float[][] rotation = rotationMatrix(cube[6], cube[7], cube[8]);
			for (int face = 0; face < 6; face++) {
				int uvAt = 12 + face * 4;
				if (cube[uvAt] == NO_FACE) {
					continue;
				}
				float[] normal = rotate(rotation, FACE_NORMALS[face][0], FACE_NORMALS[face][1],
						FACE_NORMALS[face][2]);
				for (int vertex = 0; vertex < 4; vertex++) {
					int corner = FACE_CORNERS[face][vertex];
					float[] point = rotate(rotation,
							pick(cube, 0, corner & 0b100) - cube[9],
							pick(cube, 1, corner & 0b010) - cube[10],
							pick(cube, 2, corner & 0b001) - cube[11]);
					// Back into block space: the crystal is modelled around zero on X and Z, and a
					// block's own space starts at its corner.
					this.data[at++] = (point[0] + cube[9]) * PIXEL + 0.5F;
					this.data[at++] = (point[1] + cube[10]) * PIXEL;
					this.data[at++] = (point[2] + cube[11]) * PIXEL + 0.5F;
					// Vertex 0 takes (u0, v0), 1 takes (u0, v1), 2 (u1, v1), 3 (u1, v0) — the
					// mapping CuboidFace.getU/getV apply when the game bakes a face.
					this.data[at++] = (vertex == 0 || vertex == 1) ? cube[uvAt] : cube[uvAt + 2];
					this.data[at++] = (vertex == 0 || vertex == 3) ? cube[uvAt + 1] : cube[uvAt + 3];
					this.data[at++] = normal[0];
					this.data[at++] = normal[1];
					this.data[at++] = normal[2];
				}
			}
			return at;
		}

		/** {@code axis} 0/1/2 picks X/Y/Z; a set bit takes the {@code to} extent. */
		private static float pick(float[] cube, int axis, int bit) {
			return bit != 0 ? cube[3 + axis] : cube[axis];
		}

		/**
		 * Rz·Ry·Rx — the order Blockbench composes a cube's rotation, and the one
		 * {@code Matrix4f.rotationZYX} applies to a model element in 26.2.
		 */
		private static float[][] rotationMatrix(float x, float y, float z) {
			float rx = x * Mth.DEG_TO_RAD;
			float ry = y * Mth.DEG_TO_RAD;
			float rz = z * Mth.DEG_TO_RAD;
			float cx = Mth.cos(rx);
			float sx = Mth.sin(rx);
			float cy = Mth.cos(ry);
			float sy = Mth.sin(ry);
			float cz = Mth.cos(rz);
			float sz = Mth.sin(rz);
			return new float[][] {
					{cy * cz, cz * sx * sy - cx * sz, cx * cz * sy + sx * sz},
					{cy * sz, cx * cz + sx * sy * sz, cx * sy * sz - cz * sx},
					{-sy, cy * sx, cx * cy},
			};
		}

		private static float[] rotate(float[][] matrix, float x, float y, float z) {
			return new float[] {
					matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z,
					matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z,
					matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z,
			};
		}

		private void emit(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite) {
			// Full bright, and only here: the crystal is the light source, the frame around it is a
			// block model and lights the ordinary way.
			//
			// Vertex colour stays white on purpose. The sheets above run the `core/item` shader, and
			// that shader already lights every vertex by its normal (`minecraft_mix_light`, two
			// directional lights over a 0.4 ambient) — the same lighting that gives mobs and inventory
			// items their volume. Shading the faces here as well, the way a block model bakes
			// CardinalLighting into its vertices, multiplies the two: it was tried, and the crystal
			// came out darker than the frame around it and visibly lopsided from one side to the
			// other. Give the shader correct normals and leave the colour alone.
			int light = LightCoordsUtil.FULL_BRIGHT;
			for (int at = 0; at < this.data.length; at += STRIDE) {
				consumer.addVertex(pose, this.data[at], this.data[at + 1], this.data[at + 2])
						.setColor(255, 255, 255, 255)
						.setUv(sprite.getU(this.data[at + 3]), sprite.getV(this.data[at + 4]))
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light)
						.setNormal(pose, this.data[at + 5], this.data[at + 6], this.data[at + 7]);
			}
		}
	}
}
