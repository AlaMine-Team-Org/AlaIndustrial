package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The one place a block entity renderer's hand-built geometry becomes vertices (MOD-716, CLI-9).
 *
 * <p>Every renderer that draws its own quads into {@code submitCustomGeometry} writes the same six
 * elements per vertex — position through the pose, colour, UV, no overlay, light, normal through the
 * pose — and before this class each of eight renderers wrote them through its own private
 * {@code vertex}/{@code quad}/{@code face}, with the float arguments in a different order every time.
 * Here they are written once, at two levels:
 * <ul>
 *   <li><b>an arbitrary quad</b> — four corners and four UVs in the order given, for geometry that is not
 *       an axis-aligned box face: the water wheel's rim, the capsule door's turned panes, a flat ring;
 *       {@link #quadBothSides} adds the reverse winding the translucent and cutout sheets need to show a
 *       face from behind, and its {@code Matrix4fc} overload takes the corners in a turned element's own
 *       space;</li>
 *   <li><b>an axis-aligned box face</b> — {@link #cubeFace}, corners and normal taken from
 *       {@link CubeMesh#FACE_CORNERS} and {@link CubeMesh#FACE_NORMALS}, the checked table the baked
 *       meshes use, with the block-model UV rule. New box geometry belongs here, not in a fresh corner
 *       list.</li>
 * </ul>
 *
 * <p><b>What a renderer still decides.</b> The colour — an ARGB value, or a grey {@link #shade} written
 * through the float overload of {@code setColor}, exactly as the renderers that shade by hand always
 * did — the light, and whether UVs are fractions of a {@link #sprite} or already atlas coordinates. The
 * emitter calls the same {@code VertexConsumer} overloads with the same values the private helpers did,
 * so moving a renderer onto it changes no vertex byte.
 */
final class QuadEmitter {

	private final PoseStack.Pose pose;
	private final VertexConsumer out;
	private int light;
	private int color = -1;
	private float shade;
	private boolean shaded;
	@Nullable
	private TextureAtlasSprite sprite;

	QuadEmitter(PoseStack.Pose pose, VertexConsumer out, int light) {
		this.pose = pose;
		this.out = out;
		this.light = light;
	}

	/** Packed light for the vertices that follow. */
	QuadEmitter light(int light) {
		this.light = light;
		return this;
	}

	/** ARGB colour for the vertices that follow, written through {@code setColor(int)}. */
	QuadEmitter color(int argb) {
		this.color = argb;
		this.shaded = false;
		return this;
	}

	/**
	 * A grey multiplier for the vertices that follow, written as {@code setColor(shade, shade, shade, 1)} —
	 * for the renderers that bake their own directional shading into the colour.
	 */
	QuadEmitter shade(float shade) {
		this.shade = shade;
		this.shaded = true;
		return this;
	}

	/**
	 * Read the UVs that follow as fractions of {@code sprite} (mapped through {@code getU}/{@code getV});
	 * {@code null} takes them as atlas coordinates, as given.
	 */
	QuadEmitter sprite(@Nullable TextureAtlasSprite sprite) {
		this.sprite = sprite;
		return this;
	}

	/** One vertex. */
	void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
		VertexConsumer vertex = this.out.addVertex(this.pose, x, y, z);
		vertex = this.shaded ? vertex.setColor(this.shade, this.shade, this.shade, 1.0F) : vertex.setColor(this.color);
		TextureAtlasSprite atlas = this.sprite;
		vertex.setUv(atlas == null ? u : atlas.getU(u), atlas == null ? v : atlas.getV(v))
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(this.light)
				.setNormal(this.pose, nx, ny, nz);
	}

	/** One quad, wound in the order the corners are given, each corner with its own UV. */
	void quad(float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		vertex(ax, ay, az, ua, va, nx, ny, nz);
		vertex(bx, by, bz, ub, vb, nx, ny, nz);
		vertex(cx, cy, cz, uc, vc, nx, ny, nz);
		vertex(dx, dy, dz, ud, vd, nx, ny, nz);
	}

	/**
	 * {@link #quad}, then the same four corners back from the last ({@code d, c, b, a}) with the normal
	 * negated — the second winding a culling sheet needs to show the face from behind.
	 */
	void quadBothSides(float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		quad(nx, ny, nz, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, ua, va, ub, vb, uc, vc, ud, vd);
		quad(-nx, -ny, -nz, dx, dy, dz, cx, cy, cz, bx, by, bz, ax, ay, az, ud, vd, uc, vc, ub, vb, ua, va);
	}

	/**
	 * {@link #quadBothSides} for a quad stated in an element's own space — a model element turned by its
	 * own rotation, say: each corner goes through {@code element} and is divided by {@code unitsPerBlock}
	 * (16 for design pixels), the normal goes through the element's rotation and is normalised.
	 */
	void quadBothSides(Matrix4fc element, float unitsPerBlock, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		Vector3f normal = element.transformDirection(new Vector3f(nx, ny, nz)).normalize();
		Vector3f a = element.transformPosition(new Vector3f(ax, ay, az)).div(unitsPerBlock);
		Vector3f b = element.transformPosition(new Vector3f(bx, by, bz)).div(unitsPerBlock);
		Vector3f c = element.transformPosition(new Vector3f(cx, cy, cz)).div(unitsPerBlock);
		Vector3f d = element.transformPosition(new Vector3f(dx, dy, dz)).div(unitsPerBlock);
		quadBothSides(normal.x(), normal.y(), normal.z(), a.x(), a.y(), a.z(), b.x(), b.y(), b.z(),
				c.x(), c.y(), c.z(), d.x(), d.y(), d.z(), ua, va, ub, vb, uc, vc, ud, vd);
	}

	/**
	 * One face of the axis-aligned box {@code (x0, y0, z0)..(x1, y1, z1)}, corners and normal from
	 * {@link CubeMesh#FACE_CORNERS} — the order the block bakery feeds a model element's face — and the
	 * block-model UV rule: corners 0 and 1 take {@code u0}, corners 0 and 3 take {@code v0}.
	 */
	void cubeFace(Direction face, float x0, float y0, float z0, float x1, float y1, float z1,
			float u0, float v0, float u1, float v1) {
		int index = switch (face) {
			case NORTH -> 0;
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			case UP -> 4;
			case DOWN -> 5;
		};
		float[] normal = CubeMesh.FACE_NORMALS[index];
		int[] corners = CubeMesh.FACE_CORNERS[index];
		for (int i = 0; i < 4; i++) {
			int corner = corners[i];
			vertex((corner & 0b100) != 0 ? x1 : x0, (corner & 0b010) != 0 ? y1 : y0, (corner & 0b001) != 0 ? z1 : z0,
					i == 0 || i == 1 ? u0 : u1, i == 0 || i == 3 ? v0 : v1, normal[0], normal[1], normal[2]);
		}
	}
}
