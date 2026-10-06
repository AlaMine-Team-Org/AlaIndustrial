package dev.alaindustrial.compat.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.mixin.client.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Translucent render types the mod builds itself — a version facade (MOD-777, ADR-036): every Minecraft
 * line has a twin of this class with the same signature, and only the body differs, because the pipeline
 * classes moved packages and the render setup gained order-independent transparency between the lines.
 *
 * <p><b>Why a translucent block-entity surface must not write depth.</b> A block entity's translucent
 * custom geometry is drawn BEFORE the translucent terrain layer, where water is. Vanilla's
 * {@code Sheets.translucentBlockItemSheet()} writes depth like an opaque surface, so everything of the
 * translucent terrain behind such a surface then fails the depth test: behind the closed door of the
 * teleporter capsule the lake had no water at all. A surface drawn with {@link #blockSheetNoDepthWrite()}
 * still tests depth — solid blocks in front of it hide it — but leaves the depth buffer alone, the way the
 * vanilla beacon beam does. The price is the blend order: water behind the glass is laid over the glass's
 * tint instead of under it, which at the capsule glass's low alpha cannot be seen.
 *
 * <p><b>This twin: Minecraft 26.2.</b> The pipeline is vanilla's {@code RenderPipelines.ITEM_TRANSLUCENT}
 * — its shaders ({@code getVertexShader()}/{@code getFragmentShader()}: the 26.2 pipeline has no shader
 * map), bindings, vertex format ({@code ENTITY}) and alpha cutout — with depth writes off; the private item
 * snippet vanilla builds it from is out of reach, so the public pipeline is copied the way
 * {@code RootInspectionRenderer} copies its own source. The setup is vanilla's {@code itemTranslucent} on
 * the block atlas: 26.2 has no order-independent transparency, and like vanilla the type sends its output
 * to {@code OutputTarget.ITEM_ENTITY_TARGET}, so with "Fabulous" graphics the glass lands in the same
 * target as before. {@code RenderType.create} is package-private, hence {@link RenderTypeInvoker}.
 */
public final class TranslucentTypes {

	/** Vanilla's {@code itemTranslucent} alpha cutout: a texel this transparent is discarded, not blended. */
	private static final float ALPHA_CUTOUT = 0.1f;

	private static final RenderType BLOCK_SHEET_NO_DEPTH_WRITE = blockSheetType();

	private TranslucentTypes() {
	}

	/**
	 * A translucent, lit, overlay-aware type for quads in the {@code ENTITY} vertex format textured from
	 * the block atlas, which tests depth but never writes it.
	 */
	public static RenderType blockSheetNoDepthWrite() {
		return BLOCK_SHEET_NO_DEPTH_WRITE;
	}

	private static RenderType blockSheetType() {
		RenderPipeline source = RenderPipelines.ITEM_TRANSLUCENT;
		var builder = RenderPipeline.builder()
				.withLocation(Industrialization.id("pipeline/item_translucent_no_depth_write"))
				.withVertexShader(source.getVertexShader())
				.withFragmentShader(source.getFragmentShader())
				.withVertexBinding(0, source.getVertexFormatBinding(0))
				.withPrimitiveTopology(source.getPrimitiveTopology())
				.withPolygonMode(source.getPolygonMode())
				.withCull(source.isCull())
				.withShaderDefine("ALPHA_CUTOUT", ALPHA_CUTOUT)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withDepthStencilState(new DepthStencilState(source.getDepthStencilState().depthTest(), false));
		source.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
		RenderSetup setup = RenderSetup.builder(builder.build())
				// The block atlas, as the block sprite mapper names it — the same expression as the 26.3 twin.
				.withTexture("Sampler0", Sheets.BLOCKS_MAPPER.sheet())
				.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
				.useLightmap()
				.useOverlay()
				.affectsCrumbling()
				.sortOnUpload()
				.createRenderSetup();
		return RenderTypeInvoker.alaindustrial$create("alaindustrial_item_translucent_no_depth_write", setup);
	}
}
