package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.block.CableBlock;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * The stub of cable from a cable beside the quarry's socket foundation into the drawn tower (MOD-951, owner
 * 2026-11-05). The cable's arm stops at the socket's cell, but the designer's tower stands back from the cell's faces
 * at its foot, so the cable seemed to hang in the air; this draws the arm on, a cable's cross-section, from the cell
 * face into the cell. Drawn in the copper cable's texture, the cable of the quarry stand.
 */
final class QuarryCableJoint {

	private static final float PIXEL = 1.0F / 16.0F;
	/** The cable arm's cross-section, in pixels of the cell, and how deep the stub reaches into the socket's cell. */
	private static final float LOW = 5.0F;
	private static final float HIGH = 11.0F;
	private static final float DEPTH = 8.0F;
	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
		Direction.DOWN};

	private QuarryCableJoint() {
	}

	/** Bit {@code side.ordinal()} set for every face of {@code socket} a cable touches. */
	static int cabledFaces(Level level, BlockPos socket) {
		int mask = 0;
		for (Direction side : SIDES) {
			if (level.getBlockState(socket.relative(side)).getBlock() instanceof CableBlock) {
				mask |= 1 << side.ordinal();
			}
		}
		return mask;
	}

	/** Emits the stubs of {@code mask} in the socket's cell, whose corner is the pose's origin. */
	static void emit(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light, int mask) {
		for (Direction side : SIDES) {
			if ((mask & (1 << side.ordinal())) == 0) {
				continue;
			}
			float[] box = box(side);
			cube(pose, consumer, sprite, light, box);
		}
	}

	/** The stub's box for a cable on {@code side}: the cross-section in the middle, from that face inward. */
	private static float[] box(Direction side) {
		float near = 0.0F;
		float far = DEPTH;
		float[] lo = {LOW, LOW, LOW};
		float[] hi = {HIGH, HIGH, HIGH};
		int axis = side.getAxis().ordinal();
		boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		lo[axis] = positive ? 16.0F - far : near;
		hi[axis] = positive ? 16.0F - near : far;
		return new float[] {lo[0] * PIXEL, lo[1] * PIXEL, lo[2] * PIXEL, hi[0] * PIXEL, hi[1] * PIXEL, hi[2] * PIXEL};
	}

	private static void cube(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite sprite, int light, float[] b) {
		float x0 = b[0];
		float y0 = b[1];
		float z0 = b[2];
		float x1 = b[3];
		float y1 = b[4];
		float z1 = b[5];
		quad(pose, c, sprite, light, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
		quad(pose, c, sprite, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(pose, c, sprite, light, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
		quad(pose, c, sprite, light, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
		quad(pose, c, sprite, light, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
		quad(pose, c, sprite, light, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite sprite, int light, float nx,
			float ny, float nz, float... v) {
		float[][] uv = {{5, 5}, {5, 11}, {11, 11}, {11, 5}};
		for (int i = 0; i < 4; i++) {
			c.addVertex(pose, v[i * 3], v[i * 3 + 1], v[i * 3 + 2])
					.setColor(255, 255, 255, 255)
					.setUv(sprite.getU(uv[i][0] / 16.0F), sprite.getV(uv[i][1] / 16.0F))
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, nx, ny, nz);
		}
	}
}
