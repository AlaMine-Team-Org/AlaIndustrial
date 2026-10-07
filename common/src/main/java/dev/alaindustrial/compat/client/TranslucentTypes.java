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
 *
 * <p><b>A surface with solid texels needs a second pass (MOD-780).</b> A texel at full alpha drawn with
 * {@link #blockSheetNoDepthWrite()} leaves no depth either, so water behind it is later blended over it: the
 * plate of the reactor door would turn blue wherever the coolant stands behind it. Such a surface is drawn
 * twice — first with {@link #blockSheetSolidTexels()}, which keeps only its solid texels and writes their
 * depth, then whole with {@link #blockSheetNoDepthWrite()}, which redraws those texels unchanged (alpha 1
 * replaces) and blends the translucent ones. The first pass has no blending, so it goes to the solid feature
 * phase and its depth is in place before any translucent geometry, the terrain's water included.
 */
public final class TranslucentTypes {

	/** Vanilla's {@code itemTranslucent} alpha cutout: a texel this transparent is discarded, not blended. */
	private static final float ALPHA_CUTOUT = 0.1f;

	/**
	 * The solid-texel pass keeps a texel only from this alpha up. Above the most opaque translucent texel the
	 * mod draws (tinted glass, 200/255) and below a fully solid one (255/255), so a translucent texel never
	 * turns opaque in the first pass.
	 */
	private static final float SOLID_TEXEL_CUTOUT = 0.9f;

	private static final RenderType BLOCK_SHEET_NO_DEPTH_WRITE = blockSheetType();
	private static final RenderType BLOCK_SHEET_SOLID_TEXELS = solidTexelsType();

	private TranslucentTypes() {
	}

	/**
	 * A translucent, lit, overlay-aware type for quads in the {@code ENTITY} vertex format textured from
	 * the block atlas, which tests depth but never writes it.
	 */
	public static RenderType blockSheetNoDepthWrite() {
		return BLOCK_SHEET_NO_DEPTH_WRITE;
	}

	/**
	 * An opaque, lit, overlay-aware type for quads in the {@code ENTITY} vertex format textured from the
	 * block atlas, which draws only the texels at least {@value #SOLID_TEXEL_CUTOUT} opaque and writes their
	 * depth — the first pass of a surface that mixes solid and translucent texels (see the class doc).
	 */
	public static RenderType blockSheetSolidTexels() {
		return BLOCK_SHEET_SOLID_TEXELS;
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

	/**
	 * Vanilla's {@code RenderPipelines.ITEM_CUTOUT} — no blending, depth written — with a higher alpha cutout.
	 * The setup is vanilla's {@code itemCutout} on the block atlas without {@code affectsCrumbling}: the second
	 * pass covers the whole surface and carries the crack overlay, which drawn twice would darken.
	 */
	private static RenderType solidTexelsType() {
		RenderPipeline source = RenderPipelines.ITEM_CUTOUT;
		var builder = RenderPipeline.builder()
				.withLocation(Industrialization.id("pipeline/item_cutout_solid_texels"))
				.withVertexShader(source.getVertexShader())
				.withFragmentShader(source.getFragmentShader())
				.withVertexBinding(0, source.getVertexFormatBinding(0))
				.withPrimitiveTopology(source.getPrimitiveTopology())
				.withPolygonMode(source.getPolygonMode())
				.withCull(source.isCull())
				.withShaderDefine("ALPHA_CUTOUT", SOLID_TEXEL_CUTOUT)
				.withColorTargetState(source.getColorTargetState())
				.withDepthStencilState(source.getDepthStencilState());
		source.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
		RenderSetup setup = RenderSetup.builder(builder.build())
				.withTexture("Sampler0", Sheets.BLOCKS_MAPPER.sheet())
				.useLightmap()
				.useOverlay()
				.createRenderSetup();
		return RenderTypeInvoker.alaindustrial$create("alaindustrial_item_cutout_solid_texels", setup);
	}
}
