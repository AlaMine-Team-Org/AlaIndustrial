package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.environment.WindMillRotorGeometry;
import dev.alaindustrial.core.machine.ComponentTier;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Renders the visible wooden rotor in front of wind mill blocks. */
public final class WindMillRotorBlockEntityRenderer<T extends MachineBlockEntity>
		implements BlockEntityRenderer<T, WindMillRotorBlockEntityRenderer.State> {
	/**
	 * One sprite per rotor grade (MOD-385), so the player reads a farm's upgrade level off the blades
	 * from a distance instead of having to open every mill. All three live on the same block atlas, so
	 * {@link #RENDER_TYPE} stays shared and nothing has to be re-baked per grade.
	 */
	private static final SpriteId SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("wind_mill_rotor_blades_3d"));
	private static final SpriteId SPRITE_REINFORCED =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("wind_mill_rotor_blades_reinforced_3d"));
	private static final SpriteId SPRITE_ADVANCED =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("wind_mill_rotor_blades_advanced_3d"));
	private static final RenderType RENDER_TYPE = SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	/**
	 * The blade sprite for the rotor in {@code stack}. Resolution is client-side and needs no packet of
	 * its own: {@code MachineBlockEntity.getUpdateTag} is {@code saveWithoutMetadata}, so the machine's
	 * inventory already reaches every watching client (the same guarantee the mute-chip check relies on).
	 */
	private static SpriteId spriteFor(net.minecraft.world.item.ItemStack stack) {
		ComponentTier tier = ComponentTier.forItemPath(
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
		if (tier == ComponentTier.WINDMILL_ROTOR_REINFORCED) {
			return SPRITE_REINFORCED;
		}
		if (tier == ComponentTier.WINDMILL_ROTOR_ADVANCED) {
			return SPRITE_ADVANCED;
		}
		return SPRITE;
	}
	/** Half-extent of the rotor quad, shared with the interference check (MOD-634). */
	private static final float HALF_SIZE = (float) WindMillRotorGeometry.DISC_HALF_SIZE;
	/**
	 * How far in front of the mill's centre the rotor quad hangs, along its facing, in blocks. Read from the
	 * class the interference check reads too: while each kept its own number, the check looked for the disc
	 * half a block further out than it is drawn (MOD-634).
	 */
	private static final float ROTOR_PUSH = (float) WindMillRotorGeometry.DISC_PUSH;

	private final SpriteGetter sprites;

	public WindMillRotorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		state.facing = facing(entity.getBlockState());
		// Interference (MOD-051): when a neighbouring mill's rotor disc overlaps this one's, both
		// mills stall and hide their blades — rendering two overlapping coplanar quads would clip
		// and z-fight. Channel 3 is the synced mode code shared by the whole wind mill family.
		boolean interfered = entity.getDataAccess().get(3) == WindMillBlockEntity.MODE_INTERFERENCE;
		// The rotor lives in slot 0 (ROTOR_SLOT) on every wind mill — the T1 WindMillBlockEntity AND the
		// two T2 branches (high-altitude / storm), which extend AbstractGeneratorBlockEntity rather than
		// WindMillBlockEntity. This renderer is only bound to those three block entities, so reading slot 0
		// directly is safe and gates the blades on ALL of them. (The old `instanceof WindMillBlockEntity`
		// check silently exempted both T2 mills — they rendered blades even with an empty rotor slot.)
		net.minecraft.world.item.ItemStack rotor = entity.getItem(WindMillBlockEntity.ROTOR_SLOT);
		boolean hasRotor = !rotor.isEmpty();
		state.visible = !interfered && hasRotor;
		// Grade → sprite (MOD-385). Picked here, in extractRenderState, so `submit` stays a pure draw.
		state.sprite = hasRotor ? spriteFor(rotor) : SPRITE;

		int production = entity.getDataAccess().get(2);
		state.angle = production <= 0 ? 0.0F : rotationAngle(entity, partialTicks, production);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera) {
		if (!state.visible) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		rotateToFacing(poseStack, state.facing);
		poseStack.translate(0.0F, 0.0F, -ROTOR_PUSH);
		poseStack.rotate(Axis.ZP.rotation(state.angle));
		TextureAtlasSprite sprite = sprites.get(state.sprite);
		// The rotor is a decorative overhang drawn as a flat cutout quad in front of the block. It
		// spans 2×2 blocks (HALF_SIZE) and floats off the face, so positional block light at the BE's
		// own coords leaves its corners dark — and the NeoForge custom-geometry pipeline shades
		// cutoutBlockItemSheet darker than Fabric's for the same light value. Forcing FULL_BRIGHT
		// (the vanilla pattern for flags/skulls/shields) makes the blades render at full brightness,
		// identically on both loaders and regardless of where the block sits.
		submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE,
				(pose, consumer) -> renderRotorQuad(pose, consumer, sprite, LightCoordsUtil.FULL_BRIGHT));
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer, ahead of
	 * {@link #shouldRenderOffScreen} (MOD-633). Its default is the mill's own block, so a blade reaching
	 * into view while that block was past the edge of the screen was culled with the rest of the rotor. The
	 * box is the disc the blades sweep — the square quad's corners, not its edges, as it turns.
	 *
	 * <p>No {@code @Override}: only NeoForge's {@code BlockEntityRenderer} declares this method, and vanilla
	 * — so Fabric — never tests a block entity against the frustum. {@code OffScreenRendererBoxTest} on the
	 * NeoForge lane fails if it stops overriding.
	 */
	public AABB getRenderBoundingBox(T blockEntity) {
		BlockPos pos = blockEntity.getBlockPos();
		Direction facing = facing(blockEntity.getBlockState());
		double reach = HALF_SIZE * Math.sqrt(2.0);
		double cx = pos.getX() + 0.5 + facing.getStepX() * ROTOR_PUSH;
		double cy = pos.getY() + 0.5;
		double cz = pos.getZ() + 0.5 + facing.getStepZ() * ROTOR_PUSH;
		double hx = facing.getAxis() == Direction.Axis.X ? 0.0 : reach;
		double hz = facing.getAxis() == Direction.Axis.Z ? 0.0 : reach;
		// The disc is flat; the mill's own block gives the box its depth.
		return new AABB(cx - hx, cy - reach, cz - hz, cx + hx, cy + reach, cz + hz).minmax(new AABB(pos));
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	private static Direction facing(BlockState blockState) {
		return blockState.hasProperty(HorizontalMachineBlock.FACING)
				? blockState.getValue(HorizontalMachineBlock.FACING)
				: Direction.NORTH;
	}

	private static float rotationAngle(MachineBlockEntity entity, float partialTicks, int production) {
		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
		float radiansPerTick = 0.08F + Math.min(production, 16) * 0.035F;
		return (gameTime + partialTicks) * radiansPerTick;
	}

	private static void rotateToFacing(PoseStack poseStack, Direction facing) {
		switch (facing) {
			case SOUTH -> poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
			case WEST -> poseStack.rotate(Axis.YP.rotationDegrees(90.0F));
			case EAST -> poseStack.rotate(Axis.YP.rotationDegrees(-90.0F));
			default -> {
			}
		}
	}

	private static void renderRotorQuad(PoseStack.Pose pose, VertexConsumer consumer,
			TextureAtlasSprite sprite, int light) {
		float u0 = sprite.getU0();
		float u1 = sprite.getU1();
		float v0 = sprite.getV0();
		float v1 = sprite.getV1();
		// Front face: one complete 64x64 sprite, not a cuboid UV unwrap. This keeps the rotor
		// proportional and prevents transparent atlas regions from appearing on side faces.
		vertex(pose, consumer, -HALF_SIZE, -HALF_SIZE, 0.0F, u0, v1, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, HALF_SIZE, -HALF_SIZE, 0.0F, u1, v1, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, HALF_SIZE, HALF_SIZE, 0.0F, u1, v0, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, -HALF_SIZE, HALF_SIZE, 0.0F, u0, v0, light, 0.0F, 0.0F, -1.0F);
		// Back face so the rotor still looks correct when viewed from an angle or behind.
		vertex(pose, consumer, -HALF_SIZE, HALF_SIZE, 0.0F, u0, v0, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, HALF_SIZE, HALF_SIZE, 0.0F, u1, v0, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, HALF_SIZE, -HALF_SIZE, 0.0F, u1, v1, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, -HALF_SIZE, -HALF_SIZE, 0.0F, u0, v1, light, 0.0F, 0.0F, 1.0F);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
			float u, float v, int light, float normalX, float normalY, float normalZ) {
		consumer.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, normalX, normalY, normalZ);
	}

	public static final class State extends BlockEntityRenderState {
		private Direction facing = Direction.NORTH;
		private boolean visible;
		private float angle;
		/** Blade sprite for the installed rotor grade (MOD-385). */
		private SpriteId sprite = SPRITE;
	}
}
