package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.block.FluidTapBlock;
import dev.alaindustrial.block.entity.FluidTapBlockEntity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The fluid behind the Tap's glass (MOD-837): the buffer's own fluid, with its still sprite and tint from the
 * loader's fluid model — the same source the portable tank reads — filled from the nozzle back toward the
 * source by the buffer's share of its capacity.
 *
 * <p>The liquid is a few boxes 0.5 px inside the glass tubes of the model, laid out in the SOUTH-facing frame
 * of the model ({@code tools/model_sources/fluid_tap/}) and turned to the block's facing the way the blockstate
 * turns the model. Boxes that touch leave out the faces they share, so the liquid reads as one body.
 *
 * <p>The glass is baked into the block model and drawn with the translucent terrain after this geometry, so the
 * liquid shows through it.
 */
public final class FluidTapBlockEntityRenderer
		implements BlockEntityRenderer<FluidTapBlockEntity, FluidTapBlockEntityRenderer.State> {
	private static final float PX = 1.0F / 16.0F;
	/** The liquid's square section, 0.5 px inside the 4 px glass. */
	private static final float LO = 6.5F;
	private static final float HI = 9.5F;

	public FluidTapBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FluidTapBlockEntity entity, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		state.boxes = List.of();
		long amount = entity.tank.amount;
		if (amount <= 0 || entity.tank.fluid.isEmpty()) {
			return;
		}
		Fluid fluid = entity.tank.fluid.fluid();
		FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
				.get(fluid.defaultFluidState());
		if (model == null) {
			return;
		}
		Level level = entity.getLevel();
		BlockState block = entity.getBlockState();
		state.sprite = model.stillMaterial().sprite();
		state.color = tint(model, fluid, level, entity) | (fluid == Fluids.LAVA ? 0xF0000000 : 0xD8000000);
		state.light = level == null || fluid == Fluids.LAVA
				? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(level, entity.getBlockPos());
		float share = Math.min(1.0F, (float) amount / entity.tank.capacity);
		state.boxes = turned(liquid(block.getValue(FluidTapBlock.CEILING), share),
				block.getValue(FluidTapBlock.FACING));
	}

	/** The fluid model's tint, or none: a fluid that paints itself from its sprite declares no tint. */
	// MOD-498 — FluidModel#tintSource() is deprecated by NeoForge only (see FluidTankBlockEntityRenderer); the
	// replacement type does not exist on Fabric, so the vanilla accessor is the one both loaders share.
	@SuppressWarnings("deprecation")
	private static int tint(FluidModel model, Fluid fluid, @Nullable Level level, FluidTapBlockEntity entity) {
		var tintSource = model.tintSource();
		if (tintSource == null) {
			return 0x00FFFFFF;
		}
		BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
		int tint = level instanceof BlockAndTintGetter getter
				? tintSource.colorInWorld(legacy, getter, entity.getBlockPos())
				: tintSource.color(legacy);
		return tint & 0x00FFFFFF;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.boxes.isEmpty() || state.sprite == null) {
			return;
		}
		collector.submitCustomGeometry(poseStack, Sheets.translucentBlockItemSheet(),
				(pose, consumer) -> render(pose, consumer, state));
	}

	private static void render(PoseStack.Pose pose, VertexConsumer out, State state) {
		QuadEmitter quads = new QuadEmitter(pose, out, state.light).sprite(state.sprite).color(state.color);
		for (Box b : state.boxes) {
			for (Direction face : Direction.values()) {
				if (b.skip.contains(face)) {
					continue;
				}
				float[] uv = switch (face.getAxis()) {
					case Y -> new float[] {b.x0, b.z0, b.x1, b.z1};
					case X -> new float[] {b.z0, 16 - b.y1, b.z1, 16 - b.y0};
					case Z -> new float[] {b.x0, 16 - b.y1, b.x1, 16 - b.y0};
				};
				quads.cubeFace(face, b.x0 * PX, b.y0 * PX, b.z0 * PX, b.x1 * PX, b.y1 * PX, b.z1 * PX,
						uv[0] * PX, uv[1] * PX, uv[2] * PX, uv[3] * PX);
			}
		}
	}

	// --- the liquid's boxes, in pixels, SOUTH-facing frame ---------------------------------------------

	/** An axis-aligned box of liquid in pixels, and the faces it shares with a neighbouring box. */
	record Box(float x0, float y0, float z0, float x1, float y1, float z1, Set<Direction> skip) {
	}

	/**
	 * The liquid of a tap whose buffer is {@code share} full, in the SOUTH-facing frame. It fills from the nozzle
	 * back toward the source, each stretch of tube in turn by its volume.
	 *
	 * <p>Wall form: the drop to the nozzle (y 1.5..6.5), the elbow (y 6.5..9.5), then the horizontal tube back
	 * to the source (z 6.5..0.5). Ceiling form: the elbow and the tube out to the front fill together by height
	 * (y 6.5..9.5), then the drop from the source above (y 9.5..15.5).
	 */
	static List<Box> liquid(boolean ceiling, float share) {
		List<Box> boxes = new ArrayList<>();
		if (ceiling) {
			float lower = 3 * 3 * 9;
			float upper = 3 * 6 * 3;
			float volume = share * (lower + upper);
			float level = LO + 3 * Math.min(1, volume / lower);
			float rise = 6 * Math.max(0, (volume - lower) / upper);
			boolean up = rise > 0;
			boxes.add(box(LO, LO, LO, HI, level, HI, up ? EnumSet.of(Direction.SOUTH, Direction.UP)
					: EnumSet.of(Direction.SOUTH)));
			boxes.add(box(LO, LO, HI, HI, level, 15.5F, EnumSet.of(Direction.NORTH)));
			if (up) {
				boxes.add(box(LO, HI, LO, HI, HI + rise, HI, EnumSet.of(Direction.DOWN)));
			}
			return boxes;
		}
		float drop = 3 * 5 * 3;
		float elbow = 3 * 3 * 3;
		float tube = 3 * 3 * 6;
		float volume = share * (drop + elbow + tube);
		float dropTop = 1.5F + 5 * Math.min(1, volume / drop);
		float elbowRise = 3 * clamp((volume - drop) / elbow);
		float tubeRun = 6 * clamp((volume - drop - elbow) / tube);
		boolean hasElbow = elbowRise > 0;
		boolean hasTube = tubeRun > 0;
		boxes.add(box(LO, 1.5F, LO, HI, dropTop, HI,
				hasElbow ? EnumSet.of(Direction.UP) : EnumSet.noneOf(Direction.class)));
		if (hasElbow) {
			Set<Direction> skip = hasTube ? EnumSet.of(Direction.DOWN, Direction.NORTH) : EnumSet.of(Direction.DOWN);
			boxes.add(box(LO, LO, LO, HI, LO + elbowRise, HI, skip));
		}
		if (hasTube) {
			boxes.add(box(LO, LO, LO - tubeRun, HI, HI, LO, EnumSet.of(Direction.SOUTH)));
		}
		return boxes;
	}

	private static float clamp(float value) {
		return Math.max(0, Math.min(1, value));
	}

	private static Box box(float x0, float y0, float z0, float x1, float y1, float z1, Set<Direction> skip) {
		return new Box(x0, y0, z0, x1, y1, z1, skip);
	}

	/** {@code boxes} turned from the SOUTH-facing frame to {@code facing}, faces and all. */
	static List<Box> turned(List<Box> boxes, Direction facing) {
		List<Box> out = new ArrayList<>(boxes.size());
		for (Box b : boxes) {
			double[] a = FluidTapBlock.turn(b.x0 / 16.0, b.z0 / 16.0, facing);
			double[] c = FluidTapBlock.turn(b.x1 / 16.0, b.z1 / 16.0, facing);
			Set<Direction> skip = EnumSet.noneOf(Direction.class);
			for (Direction face : b.skip) {
				skip.add(turnFace(face, facing));
			}
			out.add(new Box((float) (Math.min(a[0], c[0]) * 16), b.y0, (float) (Math.min(a[1], c[1]) * 16),
					(float) (Math.max(a[0], c[0]) * 16), b.y1, (float) (Math.max(a[1], c[1]) * 16), skip));
		}
		return out;
	}

	/** A face of the SOUTH-facing frame turned with the model: as many clockwise steps as SOUTH takes to facing. */
	private static Direction turnFace(Direction face, Direction facing) {
		if (face.getAxis() == Direction.Axis.Y) {
			return face;
		}
		Direction step = Direction.SOUTH;
		Direction turned = face;
		while (step != facing) {
			step = step.getClockWise();
			turned = turned.getClockWise();
		}
		return turned;
	}

	public static final class State extends BlockEntityRenderState {
		private List<Box> boxes = List.of();
		private @Nullable TextureAtlasSprite sprite;
		private int color = -1;
		private int light = LightCoordsUtil.FULL_BRIGHT;
	}
}
