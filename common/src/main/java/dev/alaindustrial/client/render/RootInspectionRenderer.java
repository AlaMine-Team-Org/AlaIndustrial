package dev.alaindustrial.client.render;

import com.google.gson.Gson;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.AlaClientConfig;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** A private depth target preserves root self-occlusion without touching terrain or its depth. */
public final class RootInspectionRenderer {
	private static final Identifier SHORT_TEXTURE = Industrialization.id("textures/block/kok_sagyz_root_short_3d.png");
	private static final Identifier LONG_TEXTURE = Industrialization.id("textures/block/kok_sagyz_root_long_3d.png");
	private static final Identifier BORDER_TEXTURE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
	private static final float[][][] SHORT = mesh("short");
	private static final float[][][] LONG = mesh("long");
	private static final RenderPipeline ROOT = geometryPipeline("roots", false, true);
	private static final RenderPipeline SHELL = geometryPipeline("soil", true, false);
	private static final RenderPipeline COMPOSITE = compositePipeline();
	private static StagedVertexBuffer vertices;
	private static TextureTarget target;
	private static final int[][] BOX_FACES = {{6,4,0,2},{7,5,4,6},{3,1,5,7},{2,0,1,3},{2,3,7,6},{1,0,4,5}};
	/**
	 * The root is drawn a hair below the flower it hangs from. Its top cap sits at exactly the
	 * height of the soil block's top face, and the two are transformed from different origins
	 * (bottom-centre of the flower against lower-corner of the cell), so the rounding differs per
	 * pixel and the cap flickered against the shell every time the camera moved. One hundredth of
	 * a block is well under a texel and settles it.
	 */
	private static final double SURFACE_SINK = 0.01;
	/**
	 * How solid the soil shell reads — and, because the root is drawn behind it, also how much the
	 * ground tints the root. That tint is the point: it is what makes the root look buried rather
	 * than pasted on. Raising this hides the root; the contrast comes from {@link #ROOT_GAIN}.
	 */
	private static final float SHELL_ALPHA = 0.26f;
	/** Cell edges: bright enough to count blocks against lit ground, thin enough not to draw the eye. */
	private static final float BORDER_ALPHA = 0.55f;
	/**
	 * The root is the thing being looked for, so it is lifted clear of the ground around it. The
	 * designer's texture is genuinely dark — mean RGB (70,67,55) against a soil shell near
	 * (115,104,79) — so at unit gain the root reads DARKER than the earth it should stand out from.
	 *
	 * <p><b>Kept modest on purpose.</b> The brightest texel is (95,92,75), so a large gain clips red
	 * and green at white while blue lags and the brown washes out into a pale cream that reads as a
	 * SAND root even in plain dirt. Since the shell is drawn over the root, the soil tint carries
	 * most of the "buried" look and the gain only has to lift the root clear of it.
	 */
	private static final float ROOT_GAIN = 1.9f;
	private RootInspectionRenderer() { }

	private static RenderPipeline geometryPipeline(String name, boolean blend, boolean depthWrite) {
		RenderPipeline source = RenderPipelines.GUI_TEXTURED;
		var builder = RenderPipeline.builder().withLocation(Industrialization.id("pipeline/root_inspect_" + name))
				.withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(true)
				.withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), depthWrite))
				.withColorTargetState(new ColorTargetState(blend ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.empty(),
						source.getColorTargetState().format(), ColorTargetState.WRITE_ALL));
		source.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
		return builder.build();
	}

	private static RenderPipeline compositePipeline() {
		RenderPipeline source = RenderPipelines.ENTITY_OUTLINE_BLIT;
		var builder = RenderPipeline.builder().withLocation(Industrialization.id("pipeline/root_inspect_composite"))
				.withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
				.withDepthStencilState(Optional.empty())
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA));
		source.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
		return builder.build();
	}

	private static float[][][] mesh(String name) {
		String path = "/assets/alaindustrial/root_inspection/" + name + ".json";
		try (var stream = RootInspectionRenderer.class.getResourceAsStream(path)) {
			if (stream == null) throw new IllegalStateException("Missing root mesh: " + path);
			return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), float[][][].class);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Cannot load root mesh: " + path, e);
		}
	}

	public static void draw(RootInspection.Frame frame, CameraRenderState camera, RenderTarget main) {
		if (frame.plants().isEmpty() || frame.opacity() <= 0) return;
		if (vertices == null) vertices = new StagedVertexBuffer(() -> "Ala root inspection", 65536);
		if (target == null) target = new TextureTarget("Ala root inspection", main.width, main.height, true, main.getColorTexture().getFormat());
		else if (target.width != main.width || target.height != main.height) target.resize(main.width, main.height);
		// 26.2 uses reversed depth (GREATER_THAN_OR_EQUAL), so the empty target is cleared to zero.
		// The clear IS the dim: everything the inspection does not draw composites as this wash, so
		// the world behind it survives at (100 - rootInspectionDim) percent.
		float dim = Mth.clamp(AlaClientConfig.rootInspectionDim, 10, 95) / 100f * frame.opacity();
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				target.getColorTexture(), new Vector4f(0, 0, 0, dim), target.getDepthTexture(), 0);
		Map<Identifier, StagedVertexBuffer.Draw> roots = new LinkedHashMap<>();
		for (RootInspection.Plant plant : frame.plants()) {
			boolean full = plant.column().lower();
			Identifier texture = full ? LONG_TEXTURE : SHORT_TEXTURE;
			VertexConsumer consumer = buffer(roots, texture);
			Vec3 offset = Vec3.atBottomCenterOf(plant.flower()).subtract(camera.pos).subtract(0, SURFACE_SINK, 0);
			for (float[][] quad : full ? LONG : SHORT) {
				List<float[]> polygon = new ArrayList<>(List.of(quad));
				if (full && !plant.column().upper()) polygon = clip(polygon, -1, false);
				if (!plant.column().lower()) polygon = clip(polygon, -1, true);
				emitPolygon(consumer, polygon, offset, shade(quad), 1);
			}
		}
		Map<Identifier, StagedVertexBuffer.Draw> shells = new LinkedHashMap<>();
		List<RootInspection.Plant> sorted = new ArrayList<>(frame.plants());
		sorted.sort(Comparator.comparingDouble((RootInspection.Plant p) -> p.flower().distToCenterSqr(camera.pos)).reversed());
		for (RootInspection.Plant plant : sorted) {
			if (plant.column().upper()) shell(shells, plant.upperSoil(), plant.upperTint(), plant.flower().below(), camera.pos, frame.opacity());
			if (plant.column().lower()) shell(shells, plant.lowerSoil(), plant.lowerTint(), plant.flower().below(2), camera.pos, frame.opacity());
		}
		vertices.upload();
		try {
			// Root first, soil over it. The shell must tint the root: seeing the root THROUGH the
			// ground is what makes it look buried. Drawn the other way round — root last, opaque —
			// it reads as a sticker pasted on the soil, and the gain needed to make a sticker stand
			// out bleaches the brown into sand (owner, live play).
			drawBatches(roots, ROOT, camera,
					new Vector4f(ROOT_GAIN * frame.opacity(), ROOT_GAIN * frame.opacity(),
							ROOT_GAIN * frame.opacity(), frame.opacity()));
			drawBatches(shells, SHELL, camera, new Vector4f(1));
			try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Ala root composite", main.getColorTextureView(), Optional.empty())) {
				pass.setPipeline(COMPOSITE);
				RenderSystem.bindDefaultUniforms(pass);
				pass.bindTexture("InSampler", target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.draw(3, 1, 0, 0);
			}
		} finally {
			vertices.endFrame();
		}
	}

	private static VertexConsumer buffer(Map<Identifier, StagedVertexBuffer.Draw> batches, Identifier texture) {
		return vertices.getVertexBuilder(batches.computeIfAbsent(texture,
				ignored -> vertices.appendDraw(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.TRIANGLES)));
	}

	private static void drawBatches(Map<Identifier, StagedVertexBuffer.Draw> batches, RenderPipeline pipeline,
			CameraRenderState camera, Vector4f color) {
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(camera.viewRotationMatrix, color);
		for (var entry : batches.entrySet()) {
			var info = vertices.getExecuteInfo(entry.getValue());
			if (info == null) continue;
			var texture = Minecraft.getInstance().getTextureManager().getTexture(entry.getKey());
			try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Ala root layer",
					target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(pipeline);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transforms);
				pass.bindTexture("Sampler0", texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.setVertexBuffer(0, info.vertexBuffer().slice());
				if (info.indexBuffer() != null) {
					pass.setIndexBuffer(info.indexBuffer(), info.indexType());
					pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
				} else {
					pass.draw(info.indexCount(), 1, info.baseVertex(), 0);
				}
			}
		}
	}

	private static float shade(float[][] q) {
		Vector3f normal = new Vector3f(q[1][0] - q[0][0], q[1][1] - q[0][1], q[1][2] - q[0][2])
				.cross(q[2][0] - q[0][0], q[2][1] - q[0][1], q[2][2] - q[0][2]);
		return normal.lengthSquared() < 1e-10 ? 1 : .72f + .28f * Math.max(0, normal.normalize().dot(-.35f, .8f, -.48f));
	}

	/** Clip in model space, interpolating UV at the true block boundary. */
	private static List<float[]> clip(List<float[]> polygon, float y, boolean above) {
		List<float[]> result = new ArrayList<>();
		if (polygon.isEmpty()) return result;
		float[] previous = polygon.getLast();
		boolean before = above ? previous[1] >= y : previous[1] <= y;
		for (float[] point : polygon) {
			boolean inside = above ? point[1] >= y : point[1] <= y;
			if (inside != before) {
				float t = (y - previous[1]) / (point[1] - previous[1]);
				float[] edge = new float[5];
				for (int i = 0; i < 5; i++) edge[i] = previous[i] + t * (point[i] - previous[i]);
				result.add(edge);
			}
			if (inside) result.add(point);
			previous = point;
			before = inside;
		}
		return result;
	}

	private static void emitPolygon(VertexConsumer out, List<float[]> points, Vec3 offset, float light, float alpha) {
		for (int i = 1; i + 1 < points.size(); i++) {
			for (float[] p : new float[][] {points.getFirst(), points.get(i), points.get(i + 1)}) {
				out.addVertex((float) offset.x + p[0], (float) offset.y + p[1], (float) offset.z + p[2])
						.setUv(p[3], p[4]).setColor(light, light, light, alpha);
			}
		}
	}

	private static void shell(Map<Identifier, StagedVertexBuffer.Draw> batches, BlockState soil, int tint,
			BlockPos pos, Vec3 camera, float opacity) {
		var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(soil);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(pos.asLong()), parts);
		Vec3 offset = Vec3.atLowerCornerOf(pos).subtract(camera);
		for (var part : parts) {
			List<BakedQuad> quads = new ArrayList<>(part.getQuads(null));
			for (Direction direction : Direction.values()) quads.addAll(part.getQuads(direction));
			for (BakedQuad q : quads) {
				var out = buffer(batches, q.materialInfo().sprite().atlasLocation());
				int color = q.materialInfo().isTinted() ? tint : -1;
				for (int i : new int[] {0, 1, 2, 0, 2, 3}) {
					var p = q.position(i);
					out.addVertex((float) offset.x + p.x(), (float) offset.y + p.y(), (float) offset.z + p.z())
							.setUv(UVPair.unpackU(q.packedUV(i)), UVPair.unpackV(q.packedUV(i)))
							.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, SHELL_ALPHA * opacity);
				}
			}
		}
		// Twelve thin world-space edges give depth a stable scale, even on dark soil.
		var out = buffer(batches, BORDER_TEXTURE);
		float t = .008f;
		for (int axis = 0; axis < 3; axis++) for (int a = 0; a <= 1; a++) for (int b = 0; b <= 1; b++) {
			float[] from = new float[3], to = new float[3];
			from[axis] = -t; to[axis] = 1 + t;
			from[(axis + 1) % 3] = a - t; to[(axis + 1) % 3] = a + t;
			from[(axis + 2) % 3] = b - t; to[(axis + 2) % 3] = b + t;
			for (int[] face : BOX_FACES) {
				List<float[]> quad = new ArrayList<>(4);
				for (int corner : face) quad.add(new float[] {(corner & 4) != 0 ? to[0] : from[0],
						(corner & 2) != 0 ? to[1] : from[1], (corner & 1) != 0 ? to[2] : from[2], .5f, .5f});
				emitPolygon(out, quad, offset, .72f, BORDER_ALPHA * opacity);
			}
		}
	}

	public static void close() {
		if (target != null) { target.destroyBuffers(); target = null; }
		if (vertices != null) { vertices.close(); vertices = null; }
	}
}
