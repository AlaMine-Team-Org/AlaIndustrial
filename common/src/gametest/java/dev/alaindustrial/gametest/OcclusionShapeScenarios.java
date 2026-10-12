package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Blocks whose model does not fill the cube must not occlude a neighbour's face (MOD-776).
 *
 * <p><b>Why.</b> Vanilla {@code Block.shouldRenderFace} drops a neighbour's whole facing side when this
 * block's face occlusion shape covers it. The incubator's casing stands up to a pixel inside the cube
 * between its base band and cornice, and the kok sagyz root draws the soil it replaced (farmland is 15/16
 * of a block); with a full-cube occlusion shape the sky showed through those gaps. The registry snapshot
 * still pins {@code occludes=yes} for both and R-PHY-05 still demands {@code canOcclude()} of a full
 * collision cube, so the fix is the occlusion SHAPE, not {@code noOcclusion()} — and the light dampening
 * that a non-full shape would otherwise drop from 15 to 1 is pinned here too.
 */
public final class OcclusionShapeScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(OcclusionShapeScenarios::partialModelsHideNoNeighbourFace,
						"mod776_partial_models_hide_no_neighbour_face"),
				RosterEntry.of(OcclusionShapeScenarios::shellGlazingCastsNoShadeAndPassesSkylight,
						"mod805_shell_glazing_casts_no_shade_and_passes_skylight"));

		private Roster() {}
	}

	private OcclusionShapeScenarios() {}

	/**
	 * @implements MOD-776-AC03 — every state of {@code incubator} and {@code kok_sagyz_root} still occludes
	 *     ({@code canOcclude()}) and still dampens light fully (15), yet is not solid-rendered and no face
	 *     occlusion shape is the full block. @covers R-PHY-05
	 */
	public static void partialModelsHideNoNeighbourFace(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Block block : List.of(ModContent.INCUBATOR.get(), ModContent.KOK_SAGYZ_ROOT.get())) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				checked++;
				String where = state.toString();
				if (!state.canOcclude()) {
					problems.add(where + ": canOcclude()=false (R-PHY-05 and the registry snapshot pin true)");
				}
				if (state.getLightDampening() != 15) {
					problems.add(where + ": light dampening " + state.getLightDampening() + ", expected 15");
				}
				if (state.isSolidRender()) {
					problems.add(where + ": isSolidRender()=true — it hides every neighbour face");
				}
				for (Direction side : Direction.values()) {
					VoxelShape face = state.getFaceOcclusionShape(side);
					if (face == Shapes.block() || Block.isShapeFullBlock(face)) {
						problems.add(where + ": face " + side + " occludes as the full block");
					}
				}
			}
		}
		if (checked == 0) {
			helper.fail("MOD-776 precondition: neither block has a state — nothing was checked");
			return;
		}
		if (!problems.isEmpty()) {
			helper.fail("MOD-776: occlusion of partial models — " + problems.size() + " problem(s) over " + checked
					+ " states: " + problems);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-805-AC04 — the glazing of the reactor and of the crystal greenhouse ({@code reactor_glass},
	 *     {@code crystal_farm_glass}) lets skylight down, dampens no light and casts no ambient-occlusion shade
	 *     (shade brightness 1), in every state; the opaque cells of the same classes ({@code reactor_casing},
	 *     {@code crystal_farm_floor}) keep the solid-cube answers (shade 0.2, dampening 15). @covers R-PHY-05
	 */
	public static void shellGlazingCastsNoShadeAndPassesSkylight(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		BlockPos at = helper.absolutePos(BlockPos.ZERO);
		int checked = 0;
		for (Block glass : List.of(ModContent.REACTOR_GLASS.get(), ModContent.CRYSTAL_FARM_GLASS.get())) {
			for (BlockState state : glass.getStateDefinition().getPossibleStates()) {
				checked++;
				if (!state.propagatesSkylightDown()) {
					problems.add(state + ": propagatesSkylightDown()=false — skylight stops at the pane");
				}
				if (state.getLightDampening() != 0) {
					problems.add(state + ": light dampening " + state.getLightDampening() + ", expected 0");
				}
				if (state.getShadeBrightness(helper.getLevel(), at) != 1.0F) {
					problems.add(state + ": shade brightness " + state.getShadeBrightness(helper.getLevel(), at)
							+ ", expected 1 — the neighbours get the corner shadow of a solid cube");
				}
			}
		}
		for (Block solid : List.of(ModContent.REACTOR_CASING.get(), ModContent.CRYSTAL_FARM_FLOOR.get())) {
			BlockState state = solid.defaultBlockState();
			checked++;
			if (state.propagatesSkylightDown() || state.getLightDampening() != 15
					|| state.getShadeBrightness(helper.getLevel(), at) == 1.0F) {
				problems.add(state + ": an opaque shell cell answers like glass");
			}
		}
		if (!problems.isEmpty()) {
			helper.fail("MOD-805: shell glazing light — " + problems.size() + " problem(s) over " + checked
					+ " states: " + problems);
			return;
		}
		helper.succeed();
	}
}
