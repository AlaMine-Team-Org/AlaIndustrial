package dev.alaindustrial.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The piezo plate's camouflage (MOD-764): the plate is drawn as a thin slice of the block it rests on — stone
 * under it, a stone plate; pink wool, a pink wool plate. The slice is made from the floor block's own baked
 * quads, squashed to the plate's box ({@code 1..15} across, {@code 0..1} or {@code 0..0.5} pixels high) with
 * each face's texture cropped to the part of the face the plate still covers, so the pattern lines up with the
 * floor around it instead of shrinking.
 *
 * <p>It is baked into the chunk mesh by the loader's model hook ({@code PiezoPlateModels} on Fabric,
 * {@code NeoForgePiezoPlateModels} on NeoForge) — the same route the kok-sagyz root takes for its soil — so a
 * field of plates costs nothing per frame and is lit like the terrain around it. The tint (grass, leaves) is
 * passed on by {@link PiezoPlateTint}.
 *
 * <p><b>Only plain full cubes are copied</b> ({@link #camouflages}): a slab, glass, a chest or a cable under
 * the plate leaves it in its own texture, because a squashed partial model reads as a glitch, not as the floor.
 */
public final class PlateCamouflage {
	private static final float INSET = 1f / 16f;
	private static final float HEIGHT_UP = 1f / 16f;
	private static final float HEIGHT_DOWN = 1f / 32f;

	private final Map<BlockStateModelPart, BlockStateModelPart> up = new ConcurrentHashMap<>();
	private final Map<BlockStateModelPart, BlockStateModelPart> down = new ConcurrentHashMap<>();

	/** True when the plate copies {@code below}: a full, opaque cube with no block entity of its own. */
	public static boolean camouflages(BlockState below) {
		return below.isSolidRender() && !below.hasBlockEntity();
	}

	/**
	 * The squashed parts of {@code below}'s model, cached per source part. A new instance per model bake:
	 * chunk meshing runs on worker threads, so the cache is concurrent and never outlives the parts it keys on.
	 */
	public List<BlockStateModelPart> parts(BlockStateModel below, RandomSource random, boolean pressed) {
		List<BlockStateModelPart> source = new ArrayList<>();
		below.collectParts(random, source);
		Map<BlockStateModelPart, BlockStateModelPart> cache = pressed ? down : up;
		List<BlockStateModelPart> out = new ArrayList<>(source.size());
		for (BlockStateModelPart part : source) {
			out.add(cache.computeIfAbsent(part, p -> squash(p, pressed ? HEIGHT_DOWN : HEIGHT_UP)));
		}
		return out;
	}

	private static BlockStateModelPart squash(BlockStateModelPart part, float height) {
		List<BakedQuad> bottom = new ArrayList<>();
		List<BakedQuad> rest = new ArrayList<>();
		for (Direction dir : Direction.values()) {
			for (BakedQuad quad : part.getQuads(dir)) {
				(dir == Direction.DOWN ? bottom : rest).add(squash(quad, height));
			}
		}
		for (BakedQuad quad : part.getQuads(null)) {
			rest.add(squash(quad, height));
		}
		return new SquashedPart(part, List.copyOf(bottom), List.copyOf(rest));
	}

	/**
	 * One quad, moved into the plate's box. The texture coordinate of every new corner is read off the old quad
	 * at the point the corner now occupies — a bilinear interpolation over the old corners — so a top face keeps
	 * its texture at its own scale, cropped by a pixel at each edge, and a side keeps the bottom strip of its own.
	 */
	static BakedQuad squash(BakedQuad quad, float height) {
		Direction.Axis normal = quad.direction().getAxis();
		Direction.Axis axisA = normal == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
		Direction.Axis axisB = normal == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
		float minA = Float.MAX_VALUE;
		float maxA = -Float.MAX_VALUE;
		float minB = Float.MAX_VALUE;
		float maxB = -Float.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			float a = coord(quad.position(i), axisA);
			float b = coord(quad.position(i), axisB);
			minA = Math.min(minA, a);
			maxA = Math.max(maxA, a);
			minB = Math.min(minB, b);
			maxB = Math.max(maxB, b);
		}
		Vector3f[] moved = new Vector3f[4];
		long[] uv = new long[4];
		for (int i = 0; i < 4; i++) {
			Vector3fc p = quad.position(i);
			moved[i] = new Vector3f(INSET + p.x() * (1 - 2 * INSET), p.y() * height, INSET + p.z() * (1 - 2 * INSET));
			float s = fraction(coord(moved[i], axisA), minA, maxA);
			float t = fraction(coord(moved[i], axisB), minB, maxB);
			uv[i] = interpolate(quad, axisA, axisB, minA, maxA, minB, maxB, s, t);
		}
		return new BakedQuad(moved[0], moved[1], moved[2], moved[3], uv[0], uv[1], uv[2], uv[3], quad.direction(),
				quad.materialInfo());
	}

	private static long interpolate(BakedQuad quad, Direction.Axis axisA, Direction.Axis axisB, float minA, float maxA,
			float minB, float maxB, float s, float t) {
		if (maxA - minA < 1e-6f || maxB - minB < 1e-6f) {
			return quad.packedUV(0);
		}
		float u = 0;
		float v = 0;
		for (int i = 0; i < 4; i++) {
			float cornerS = fraction(coord(quad.position(i), axisA), minA, maxA);
			float cornerT = fraction(coord(quad.position(i), axisB), minB, maxB);
			float weight = (cornerS > 0.5f ? s : 1 - s) * (cornerT > 0.5f ? t : 1 - t);
			u += weight * UVPair.unpackU(quad.packedUV(i));
			v += weight * UVPair.unpackV(quad.packedUV(i));
		}
		return UVPair.pack(u, v);
	}

	private static float fraction(float value, float min, float max) {
		return max - min < 1e-6f ? 0 : Math.clamp((value - min) / (max - min), 0f, 1f);
	}

	private static float coord(Vector3fc p, Direction.Axis axis) {
		return switch (axis) {
			case X -> p.x();
			case Y -> p.y();
			case Z -> p.z();
		};
	}

	/**
	 * A squashed part. Only the bottom face keeps its cull face — it lies on the floor block; the top and the
	 * sides are a pixel away from any neighbour and must be drawn whatever stands above or beside the plate.
	 */
	private record SquashedPart(BlockStateModelPart source, List<BakedQuad> bottom, List<BakedQuad> rest)
			implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(@Nullable Direction side) {
			if (side == null) {
				return rest;
			}
			return side == Direction.DOWN ? bottom : List.of();
		}

		@Override
		public boolean useAmbientOcclusion() {
			return source.useAmbientOcclusion();
		}

		@Override
		public Material.Baked particleMaterial() {
			return source.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return source.materialFlags();
		}
	}
}
