package dev.alaindustrial.client.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * The parts of a blueprint scene a block model does not draw (MOD-797): a fluid block (lava in front of a
 * pump), drawn as a pool from the fluid's own still texture, and a block whose look comes from its
 * block-entity renderer (a chest), drawn by that renderer on a block entity that has no level.
 *
 * <p>A block-entity renderer that cannot run without a level is caught once and skipped for good: the sheet
 * shows the rest of the scene instead of failing.
 */
final class BlueprintSpecials {

	/** A contents entry naming a fluid rather than an item. */
	static final String FLUID_PREFIX = "fluid:";

	/** Surface height of a pool, as a still fluid block shows it. */
	private static final float POOL_TOP = 14f / 16f;

	private static final Map<BlueprintDef.Placement, BlockEntity> ENTITIES = new HashMap<>();
	private static final Set<Block> UNDRAWABLE = new HashSet<>();

	private BlueprintSpecials() {
	}

	/** Draw {@code state} as a fluid pool if it is a fluid block; returns whether it was one. */
	static boolean drawFluid(BlockState state, PoseStack pose, SubmitNodeCollector collector) {
		FluidState fluid = state.getFluidState();
		if (fluid.isEmpty()) {
			return false;
		}
		FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
				.get(fluid.getType().defaultFluidState());
		TextureAtlasSprite sprite = model.stillMaterial().sprite();
		@SuppressWarnings("deprecation")
		var tintSource = model.tintSource();
		int tint = tintSource == null ? -1 : tintSource.color(state);
		int colour = (tint & 0x00FFFFFF) | 0xFF000000;
		collector.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(),
				(p, out) -> pool(p, out, sprite, colour));
		return true;
	}

	/**
	 * Draw the block-entity renderer of {@code state}, if it has one that runs without a level, on a block
	 * entity holding the placement's contents.
	 */
	static void drawBlockEntity(BlueprintDef.Placement placement, BlockState state, PoseStack pose,
			SubmitNodeCollector collector) {
		Block block = state.getBlock();
		if (!(block instanceof EntityBlock entityBlock) || UNDRAWABLE.contains(block)) {
			return;
		}
		try {
			BlockEntity entity = ENTITIES.computeIfAbsent(placement, p -> filled(entityBlock, state, p));
			if (entity != null) {
				submit(entity, pose, collector);
			}
		} catch (RuntimeException e) {
			UNDRAWABLE.add(block);
		}
	}

	/**
	 * A new block entity for {@code placement}, its contents in place: item ids go into the slots in order,
	 * a {@code fluid:<id>} entry fills the block's fluid tank three quarters full.
	 */
	private static BlockEntity filled(EntityBlock block, BlockState state, BlueprintDef.Placement placement) {
		BlockEntity entity = block.newBlockEntity(BlockPos.ZERO, state);
		int slot = 0;
		for (String content : placement.contents()) {
			if (content.startsWith(FLUID_PREFIX)) {
				fillTank(entity, content.substring(FLUID_PREFIX.length()));
			} else if (entity instanceof net.minecraft.world.Container container
					&& slot < container.getContainerSize()) {
				container.setItem(slot++, BlueprintItems.stack(content).copy());
			}
		}
		return entity;
	}

	private static void fillTank(BlockEntity entity, String fluidId) {
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(fluidId);
		if (!(entity instanceof dev.alaindustrial.core.fluid.FluidPortHost host) || id == null
				|| !net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id)) {
			return;
		}
		if (host.fluidPort(net.minecraft.core.Direction.UP) instanceof dev.alaindustrial.core.fluid.FluidTank tank) {
			tank.fluid = dev.alaindustrial.core.fluid.FluidHolder.of(
					net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id));
			tank.amount = tank.capacity * 3 / 4;
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void submit(BlockEntity entity, PoseStack pose, SubmitNodeCollector collector) {
		BlockEntityRenderer renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
		if (renderer == null) {
			return;
		}
		BlockEntityRenderState state = renderer.createRenderState();
		renderer.extractRenderState(entity, state, 0f, Vec3.ZERO, null);
		renderer.submit(state, pose, collector, new CameraRenderState());
	}

	/** A pool filling the block up to {@link #POOL_TOP}: the surface and the four sides, faces outward. */
	private static void pool(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite, int colour) {
		float t = POOL_TOP;
		float side = 1f - t;
		quad(pose, out, sprite, colour, 0, 1, 0, 0, t, 1, 1, t, 1, 1, t, 0, 0, t, 0, 0, 1, 1, 1, 1, 0, 0, 0);
		quad(pose, out, sprite, colour, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, t, 1, 0, t, 1, 0, 1, 1, 1, 1, side, 0, side);
		quad(pose, out, sprite, colour, 0, 0, -1, 1, 0, 0, 0, 0, 0, 0, t, 0, 1, t, 0, 0, 1, 1, 1, 1, side, 0, side);
		quad(pose, out, sprite, colour, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, t, 0, 1, t, 1, 0, 1, 1, 1, 1, side, 0, side);
		quad(pose, out, sprite, colour, -1, 0, 0, 0, 0, 0, 0, 0, 1, 0, t, 1, 0, t, 0, 0, 1, 1, 1, 1, side, 0, side);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite, int colour,
			float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		vertex(pose, out, sprite, colour, ax, ay, az, ua, va, nx, ny, nz);
		vertex(pose, out, sprite, colour, bx, by, bz, ub, vb, nx, ny, nz);
		vertex(pose, out, sprite, colour, cx, cy, cz, uc, vc, nx, ny, nz);
		vertex(pose, out, sprite, colour, dx, dy, dz, ud, vd, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite, int colour,
			float x, float y, float z, float u, float v, float nx, float ny, float nz) {
		out.addVertex(pose, x, y, z).setColor(colour).setUv(sprite.getU(u), sprite.getV(v))
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(pose, nx, ny, nz);
	}
}
