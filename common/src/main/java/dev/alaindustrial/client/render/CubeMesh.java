package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * A designer's cubes flattened into ready vertices once, at class-load time.
 *
 * <p>Shared by every renderer that draws geometry exported from a {@code .bbmodel} with per-face UVs
 * — the energy condenser's crystal (MOD-546) and the workstation's fans and monitors (MOD-483).
 * Vanilla's {@code CubeListBuilder} cannot express those UVs (it only does the fixed "cross"
 * unwrap), so the cubes are turned into vertices here instead.
 *
 * <p><b>Why one copy and not one per renderer.</b> {@link #FACE_CORNERS} is the part that goes
 * wrong silently: a wrong bit puts two corners of a face on the far side of its cube, and the result
 * still renders — as a sheet cutting through the box, lit by a normal that no longer matches its own
 * geometry. That shipped once and read in game as "the crystal has no volume". A second hand-copied
 * table is a second chance to make the same bug, so the table, its self-check and the unrolling live
 * here alone.
 *
 * <p>Row layout, straight from the generators: {@code from} (3), {@code to} (3), rotation in degrees
 * (3), pivot (3), then the UVs of six faces in the order north, east, south, west, up, down, four
 * numbers each, {@code -1} in place of the first meaning the face is absent.
 */
final class CubeMesh {

	/** Eight floats per vertex: position, UV inside the sprite, normal. */
	private static final int STRIDE = 8;
	private static final int NO_FACE = -1;
	private static final float PIXEL = 1.0F / 16.0F;

	/**
	 * Corner picks per face, in the vanilla winding — the same order {@code FaceInfo} feeds the block
	 * bakery, so a face drawn here is visible from exactly the side it would be as part of a block
	 * model. Each entry is three bits: X, Y, Z, set meaning the {@code to} extent.
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

	static {
		// A face has to be flat, and the table above is where that quietly stops being true. Checked
		// at class load, because the alternative is checking it by eye on a small rotating object.
		for (int face = 0; face < 6; face++) {
			int axis = face == 0 || face == 2 ? 2 : (face == 1 || face == 3 ? 0 : 1);
			int bit = axis == 0 ? 0b100 : (axis == 1 ? 0b010 : 0b001);
			boolean atTo = FACE_NORMALS[face][axis] > 0.0F;
			for (int corner : FACE_CORNERS[face]) {
				if (((corner & bit) != 0) != atTo) {
					throw new IllegalStateException("face " + face + " is not flat on axis " + axis);
				}
			}
		}
	}

	private final float[] data;

	/**
	 * @param cubes        rows in the layout described on the class
	 * @param centreOffset added to X and Z after scaling, in blocks. A model drawn around zero (the
	 *                     condenser's crystal) passes {@code 0.5}; one already exported in the
	 *                     block's own 0…16 pixel space (the workstation's parts) passes {@code 0}.
	 */
	CubeMesh(float[][] cubes, float centreOffset) {
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
			at = write(cube, at, centreOffset);
		}
	}

	boolean isEmpty() {
		return this.data.length == 0;
	}

	private int write(float[] cube, int at, float centreOffset) {
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
				this.data[at++] = (point[0] + cube[9]) * PIXEL + centreOffset;
				this.data[at++] = (point[1] + cube[10]) * PIXEL;
				this.data[at++] = (point[2] + cube[11]) * PIXEL + centreOffset;
				// Vertex 0 takes (u0, v0), 1 takes (u0, v1), 2 (u1, v1), 3 (u1, v0) — the mapping
				// CuboidFace.getU/getV apply when the game bakes a face.
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

	/**
	 * Vertex colour stays white on purpose. The block/item sheets run the {@code core/item} shader,
	 * and that shader already lights every vertex by its normal — the same lighting that gives mobs
	 * and inventory items their volume. Shading the faces here as well, the way a block model bakes
	 * CardinalLighting into its vertices, multiplies the two: it was tried on the condenser, and the
	 * crystal came out darker than the frame around it. Give the shader correct normals and leave the
	 * colour alone.
	 */
	void emit(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light) {
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
