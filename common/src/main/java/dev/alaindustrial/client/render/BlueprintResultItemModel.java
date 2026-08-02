package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.mixin.client.ItemModelsAccessor;
import dev.alaindustrial.mixin.client.ItemStackRenderStateAccessor;
import dev.alaindustrial.mixin.client.LayerRenderStateAccessor;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The product of a recorded Assembly Blueprint, drawn into the blueprint's own icon (MOD-275).
 *
 * <p>A recorded blueprint used to look exactly like a blank one everywhere outside the Assembler's
 * window: same blue sheet, same 3×3 grid motif, and the only way to tell two apart was to hover
 * each. This model type replaces that central motif with what the blueprint actually makes — so a
 * chest full of blueprints reads at a glance, on the ground, in the hotbar and in the hand.
 *
 * <p><b>Why a model type and not a screen overlay.</b> An item icon is drawn by the item-model
 * pipeline wherever it appears; nothing a {@code Screen} draws can follow the item out of that
 * screen. The vanilla precedent for compositing an <em>arbitrary, data-driven</em> item into another
 * item's icon is {@code minecraft:bundle/selected_item}
 * ({@code BundleSelectedItemSpecialRenderer}): it reads an {@code ItemStackTemplate} off the stack
 * and hands it to {@link ItemModelResolver#appendItemLayers}. This is the same mechanism against the
 * same API, pointed at {@code alaindustrial:blueprint_result}.
 *
 * <p><b>The one thing vanilla does not do</b> is draw the composed item at anything other than full
 * size. {@code appendItemLayers} appends an unknown number of layers and returns none of them, so
 * the shrink is applied afterwards to exactly the layers this call added (see
 * {@link ItemStackRenderStateAccessor}); the matrix itself comes from the surrounding
 * {@code minecraft:composite} in {@code items/assembly_blueprint.json}, so the placement inside the
 * frame is data, not a constant compiled in here.
 *
 * <p><b>Where the numbers in that matrix come from.</b> A layer's local transform is multiplied in
 * <em>after</em> the display transform, whose last step is {@code translate(-0.5, -0.5, -0.5)}
 * (checked in the 26.2 bytecode of {@code ItemTransform#apply} and
 * {@code LayerRenderState#applyTransform}) — so it operates on the model's own corner-origin
 * {@code 0..1} box, and a layer scaled by {@code s} is centred exactly when it is translated by
 * {@code (1 - s) / 2}. For the shipped {@code s = 0.625} that is {@code 0.1875} on all three axes,
 * which puts the product on texels 3..13 of the 16×16 sheet — dead centre, because the sheet's frame
 * (border ring on row/column 1 and 14, highlight row 2, shadow row 13) is symmetric and the blue
 * field's centre is therefore the icon's own centre.
 *
 * <p><b>Depth is the axis the JSON cannot reach.</b> The matrix above runs in model space, before the
 * display transform, so on {@code z} it does nothing useful: it scales the product about the paper's
 * own plane (leaving it bisected however small it gets) and any translation it carries comes out
 * rotated into a diagonal shift on screen for a block product. Depth is therefore settled in code, in
 * the sheet's frame, by {@link #onTheSheet} — which is also where the product stops obeying its own
 * model's display block.
 *
 * <p>The product is never resolved here: a crafting recipe cannot be solved on the client in 26.2.
 * It is read from the {@code blueprint_result} component the machine wrote when the player pressed
 * "Write" — the same value the tooltip names.
 */
public final class BlueprintResultItemModel implements ItemModel {
	/** The id used in {@code items/*.json} as {@code "type": "alaindustrial:blueprint_result"}. */
	public static final String TYPE_PATH = "blueprint_result";

	private final Matrix4fc transformation;

	private BlueprintResultItemModel(Matrix4fc transformation) {
		this.transformation = transformation;
	}

	/** Adds the type to the vanilla late-bound registry. Called from both loaders' client init. */
	public static void register() {
		ItemModelsAccessor.alaindustrial$idMapper()
				.put(Industrialization.id(TYPE_PATH), Unbaked.MAP_CODEC);
	}

	@Override
	public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
			ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed) {
		state.appendModelIdentityElement(this);
		ItemStack product = AssemblyBlueprintItem.resultOf(stack);
		if (product.isEmpty()) {
			return;
		}
		ItemStackRenderStateAccessor access = (ItemStackRenderStateAccessor) state;
		int first = access.alaindustrial$activeLayerCount();
		if (first == 0) {
			// Nothing to compose onto: the sheet is always drawn first by the enclosing composite.
			return;
		}
		// Always resolved as the GUI would draw it, whatever context this is — see #onTheSheet.
		resolver.appendItemLayers(state, product, ItemDisplayContext.GUI, level, owner, seed);
		int last = access.alaindustrial$activeLayerCount();
		// Read the array after the append: ensureCapacity swaps it out when the state has to grow.
		ItemStackRenderState.LayerRenderState[] layers = access.alaindustrial$layers();
		ItemTransform sheet = ((LayerRenderStateAccessor) layers[0]).alaindustrial$itemTransform();
		boolean blockLit = false;
		for (int i = first; i < last; i++) {
			LayerRenderStateAccessor layer = (LayerRenderStateAccessor) layers[i];
			blockLit |= layer.alaindustrial$usesBlockLight();
			onTheSheet(layers[i], layer, sheet);
		}
		if (blockLit && displayContext == ItemDisplayContext.GUI) {
			liftSheetToBlockLighting(layers, first);
		}
	}

	/**
	 * How thin the product is pressed along the sheet's normal, and how far it then stands off the paper
	 * (MOD-275 follow-up). Both are fractions of the item's own {@code 0..1} box, in the sheet's frame.
	 *
	 * <p>A generated sheet is 1/16 thick, so its front face is at 1/32 = 0.031. The largest product is a
	 * full cube: at the shipped composite scale it measures 0.338 from the mid-plane to its nearest
	 * corner, and {@code RELIEF_DEPTH} presses that to 0.030 — one paper-thickness. {@code RELIEF_LIFT}
	 * then puts the whole of it in front of the paper's front face with a little room to spare.
	 */
	private static final float RELIEF_DEPTH = 0.09f;
	private static final float RELIEF_LIFT = 0.07f;

	/**
	 * Re-expresses one composed product layer as artwork pressed onto the blueprint's sheet (MOD-275).
	 *
	 * <p>Two separate defects share this method, because both come from the same mistake: letting the
	 * product keep its own model's display block.
	 *
	 * <p><b>Cropped.</b> Sheet and product were both centred on the item's mid-plane, so the paper cut
	 * the product in half — everything behind the sheet's plane was simply occluded, which for a cube
	 * meant a flat-topped, flat-sided stump instead of a silhouette. No scale fixes this: a
	 * {@code minecraft:composite} scales about the model's centre, so the product stays centred on the
	 * paper however small it gets. It has to move along the sheet's normal, and it cannot do that from
	 * the JSON either — the composite's matrix is applied <em>before</em> the display transform, so a
	 * {@code z} translation there comes out rotated into a diagonal shift on screen for a block product.
	 * Here it is applied after: the product is pressed to one paper-thickness ({@code RELIEF_DEPTH})
	 * and lifted clear of the front face ({@code RELIEF_LIFT}), turning it into a relief on the sheet.
	 *
	 * <p><b>Positions, not a matrix.</b> The press is written into the quads' vertices rather than into
	 * the layer's transform on purpose. {@code PoseStack.Pose} derives its normal matrix from the pose
	 * (inverse transpose, 26.2 bytecode), so a non-uniform matrix would tip every face of the product
	 * towards the viewer and undo the lighting fix above. Vertex positions carry no normals —
	 * {@code putBakedQuad} reads those from {@code BakedQuad.direction()} — so reshaping the geometry
	 * leaves the shading untouched.
	 *
	 * <p><b>And out of the GUI.</b> A layer applies its OWN model's display transform, so outside a GUI
	 * slot the paper obeyed {@code item/generated} while the product obeyed {@code block/block}: the two
	 * flew apart, and a blueprint held in the hand showed a full-size cube hanging off the corner of the
	 * page. The product therefore wears the sheet's display transform, with its GUI orientation folded
	 * into the local matrix — so the icon is one object, at the same reading angle, everywhere.
	 */
	private void onTheSheet(ItemStackRenderState.LayerRenderState layer,
			LayerRenderStateAccessor access, ItemTransform sheet) {
		Matrix4f own = access.alaindustrial$localTransform();
		// The product's own GUI display transform, resolved to a matrix. leftHand is deliberately false:
		// the picture printed on the page must not mirror when the page moves to the other hand.
		Matrix4f placed = matrixOf(access.alaindustrial$itemTransform())
				.mul(new Matrix4f(this.transformation).mul(own));
		Matrix4f relief = new Matrix4f()
				.translation(0.0f, 0.0f, RELIEF_LIFT)
				.scale(1.0f, 1.0f, RELIEF_DEPTH);
		Matrix4f press = new Matrix4f(placed).invert().mul(relief).mul(placed);
		List<BakedQuad> quads = layer.prepareQuadList();
		for (int q = 0; q < quads.size(); q++) {
			quads.set(q, reshape(quads.get(q), press));
		}
		// The sheet's transform ends with translate(-0.5): undo it, `placed` already centres the product.
		own.set(new Matrix4f().translation(0.5f, 0.5f, 0.5f).mul(placed));
		layer.setItemTransform(sheet);
	}

	/**
	 * Hands the whole icon over to the GUI's block-lighting rig without darkening the paper (MOD-275).
	 *
	 * <p><b>The bug.</b> {@code GuiItemAtlas} picks one of two light rigs for the entire stack —
	 * {@code Lighting.Entry.ITEMS_3D} or {@code ITEMS_FLAT} — from
	 * {@code ItemStackRenderState.usesBlockLight()}, which in 26.2 is literally
	 * {@code firstLayer().usesBlockLight}. Layer 0 of a recorded blueprint is the flat paper, so a block
	 * product was lit by the rig meant for flat sprites and came out dark grey: running the two rigs'
	 * light vectors through {@code light.glsl} gives a block's faces 0.40/0.60 under {@code ITEMS_FLAT}
	 * against 0.83/1.00 under {@code ITEMS_3D}.
	 *
	 * <p><b>Why not simply compose the product first.</b> That flips the same switch the other way and
	 * costs the sheet instead — a camera-facing quad measures 1.00 under {@code ITEMS_FLAT} and 0.51
	 * under {@code ITEMS_3D}, which is the visible darkening an earlier attempt hit and reverted.
	 *
	 * <p><b>The third answer.</b> Diffuse lighting never looks at the geometry: {@code
	 * VertexConsumer.putBakedQuad} takes the normal from {@code BakedQuad.direction()} alone (26.2
	 * bytecode). So the sheet does not have to be re-lit — it has to be re-<em>labelled</em>. Of the six
	 * directions, {@code UP} is the one that measures a full 1.00 under BOTH rigs (measured with the GUI's
	 * own {@code y}-flip in the chain — {@code DOWN} is its mirror and was tried first: it renders the
	 * paper at 0.40 and turns it navy), so relabelling the paper's quads {@code UP} makes it render
	 * byte-identically to the flat-lit sheet it replaces
	 * while the product finally gets the rig its own model asked for.
	 *
	 * <p>Restricted to the GUI because it is a GUI bug: in the world every layer is lit by the level, and
	 * a face pretending to point down there would be wrong. Restricted to a block product because a flat
	 * product already wants {@code ITEMS_FLAT} and must not be touched at all.
	 */
	private static void liftSheetToBlockLighting(ItemStackRenderState.LayerRenderState[] layers, int sheetLayers) {
		for (int i = 0; i < sheetLayers; i++) {
			List<BakedQuad> quads = layers[i].prepareQuadList();
			for (int q = 0; q < quads.size(); q++) {
				quads.set(q, relabel(quads.get(q), Direction.UP));
			}
		}
		layers[0].setUsesBlockLight(true);
	}

	/**
	 * An {@link ItemTransform} as the matrix it would push onto a pose.
	 *
	 * <p>{@code ItemTransform} exposes only {@code apply(boolean, Pose)} in 26.2 — there is no getter for
	 * the matrix — so it is applied to a throwaway pose and read back off it. That also picks up the
	 * trailing {@code translate(-0.5, -0.5, -0.5)} and the {@code NO_TRANSFORM} shortcut for free,
	 * instead of this class re-deriving vanilla's order of operations from the record's three fields.
	 */
	private static Matrix4f matrixOf(ItemTransform transform) {
		PoseStack pose = new PoseStack();
		transform.apply(false, pose.last());
		return new Matrix4f(pose.last().pose());
	}

	/** The same quad, moved. {@code BakedQuad} is a record, so this is a copy with new corners. */
	private static BakedQuad reshape(BakedQuad quad, Matrix4fc matrix) {
		return new BakedQuad(move(quad.position0(), matrix), move(quad.position1(), matrix),
				move(quad.position2(), matrix), move(quad.position3(), matrix),
				quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
				quad.direction(), quad.materialInfo());
	}

	private static Vector3f move(Vector3fc corner, Matrix4fc matrix) {
		return matrix.transformPosition(corner, new Vector3f());
	}

	/** The same quad, claiming a different normal. {@code BakedQuad} is a record, so this is a copy. */
	private static BakedQuad relabel(BakedQuad quad, Direction direction) {
		return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
				quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
				direction, quad.materialInfo());
	}

	/**
	 * The unbaked form: no fields, because everything variable about this model — where the product
	 * sits and how small it is — arrives as the baking matrix from the enclosing
	 * {@code minecraft:composite}.
	 */
	public record Unbaked() implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			// The product is whatever the stack says at render time; there is no model to pre-resolve.
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
			return new BlueprintResultItemModel(transformation);
		}
	}
}
