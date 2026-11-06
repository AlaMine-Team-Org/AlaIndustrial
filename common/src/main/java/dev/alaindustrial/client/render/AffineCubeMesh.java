package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * A designer's cubes placed by a full matrix rather than by Euler angles about one pivot (MOD-950), flattened into
 * vertices once at class-load time.
 *
 * <p>{@link CubeMesh} turns a cube about one pivot, which is all a vanilla model element can do. The quarry's
 * drill turns its augers twice — the cube about its own origin, then its group about another — and no single pivot
 * expresses that, so its generator ({@code tools/gen_quarry_assets.py}) composes every rotation into one affine
 * map and hands it over per cube. The face table and the UV mapping are {@link CubeMesh}'s, so a face drawn here is
 * visible from the same side and textured the same way as there.
 *
 * <p>Row layout: {@code from} (3) and {@code to} (3) of the cube unrotated, the 3 x 3 matrix (row-major, 9), the
 * translation (3), then the UVs of six faces north, east, south, west, up, down — four numbers each, as fractions
 * of the sprite, {@code -1} in place of the first meaning the face is absent. Positions in pixels.
 */
final class AffineCubeMesh {

	/** Eight floats per vertex: position, UV inside the sprite, normal. */
	private static final int STRIDE = 8;
	private static final int ROW = 42;
	private static final int UV_AT = 18;
	private static final float NO_FACE = -1.0F;
	private static final float PIXEL = 1.0F / 16.0F;

	private final float[] data;

	AffineCubeMesh(float[][] cubes) {
		int faces = 0;
		for (float[] cube : cubes) {
			if (cube.length != ROW) {
				throw new IllegalArgumentException("a quarry cube row has " + ROW + " numbers, got " + cube.length);
			}
			for (int face = 0; face < 6; face++) {
				if (cube[UV_AT + face * 4] != NO_FACE) {
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

	boolean isEmpty() {
		return this.data.length == 0;
	}

	private int write(float[] cube, int at) {
		for (int face = 0; face < 6; face++) {
			int uvAt = UV_AT + face * 4;
			if (cube[uvAt] == NO_FACE) {
				continue;
			}
			float[] n = CubeMesh.FACE_NORMALS[face];
			float[] normal = linear(cube, n[0], n[1], n[2]);
			for (int vertex = 0; vertex < 4; vertex++) {
				int corner = CubeMesh.FACE_CORNERS[face][vertex];
				float[] point = linear(cube,
						(corner & 0b100) != 0 ? cube[3] : cube[0],
						(corner & 0b010) != 0 ? cube[4] : cube[1],
						(corner & 0b001) != 0 ? cube[5] : cube[2]);
				this.data[at++] = (point[0] + cube[15]) * PIXEL;
				this.data[at++] = (point[1] + cube[16]) * PIXEL;
				this.data[at++] = (point[2] + cube[17]) * PIXEL;
				// Vertex 0 takes (u0, v0), 1 (u0, v1), 2 (u1, v1), 3 (u1, v0) — CubeMesh's mapping.
				this.data[at++] = (vertex == 0 || vertex == 1) ? cube[uvAt] : cube[uvAt + 2];
				this.data[at++] = (vertex == 0 || vertex == 3) ? cube[uvAt + 1] : cube[uvAt + 3];
				this.data[at++] = normal[0];
				this.data[at++] = normal[1];
				this.data[at++] = normal[2];
			}
		}
		return at;
	}

	/** The row's matrix applied to {@code (x, y, z)}. */
	private static float[] linear(float[] cube, float x, float y, float z) {
		return new float[] {
				cube[6] * x + cube[7] * y + cube[8] * z,
				cube[9] * x + cube[10] * y + cube[11] * z,
				cube[12] * x + cube[13] * y + cube[14] * z,
		};
	}

	/** White vertices and the shader's own lighting by normal, as {@link CubeMesh#emit} explains. */
	void emit(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light) {
		emit(pose, consumer, sprite, light, 255, 255, 255);
	}

	/** The same, the texture tinted by {@code red, green, blue} (0..255): the quarry's hot drill head, MOD-952. */
	void emit(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int light, int red, int green,
			int blue) {
		for (int at = 0; at < this.data.length; at += STRIDE) {
			consumer.addVertex(pose, this.data[at], this.data[at + 1], this.data[at + 2])
					.setColor(red, green, blue, 255)
					.setUv(sprite.getU(this.data[at + 3]), sprite.getV(this.data[at + 4]))
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, this.data[at + 5], this.data[at + 6], this.data[at + 7]);
		}
	}
}
