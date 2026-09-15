package dev.alaindustrial.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every block entity renderer that draws beyond its own block tells NeoForge where it draws (MOD-633).
 *
 * <p><b>The trap.</b> NeoForge patches {@code BlockEntityRenderDispatcher.tryExtractRenderState} to skip a
 * renderer whose {@code getRenderBoundingBox} is outside the view frustum, and it runs that test BEFORE it
 * looks at {@code shouldRenderOffScreen} — for globally rendered block entities too. The default box is the
 * block entity's own block. So on NeoForge, {@code shouldRenderOffScreen() == true} does not keep anything
 * drawn: whatever a renderer puts outside its block is culled the moment that block leaves the view. The
 * teleporter capsule's door vanished from inside the capsule this way (MOD-632). Vanilla, and so Fabric,
 * never tests a block entity against the frustum, which is why only this lane can see the problem.
 *
 * <p><b>The rule.</b> A renderer that overrides {@code shouldRenderOffScreen} is saying it draws beyond its
 * block, so it must also override {@code getRenderBoundingBox}. The override is written in {@code common/}
 * without {@code @Override}, because vanilla's {@code BlockEntityRenderer} has no such method — which also
 * means nothing but this test notices when it stops overriding: a box typed against the wrong class, or a
 * hook NeoForge renamed, leaves an ordinary method nobody calls. What proves the override is the bridge
 * javac writes when compiling {@code common/} against NeoForge's patched interface here: a method taking a
 * plain {@code BlockEntity}. That bridge is the call NeoForge makes, so the rule looks for it.
 *
 * <p><b>What it does not see.</b> A renderer that draws outside its block and never overrides
 * {@code shouldRenderOffScreen} — it is culled by its section on vanilla as well, so it is a different
 * defect, and nothing in the bytecode says how far a renderer draws.
 *
 * <p>Read from bytecode, never by loading a renderer: they are client classes and this lane is headless.
 * Plain {@code @Test} with floors, for the reason {@link NeoForgeBackendAgnosticRenderingTest} gives.
 */
class OffScreenRendererBoxTest {

	private static final String RENDERER = "net.minecraft.client.renderer.blockentity.BlockEntityRenderer";
	private static final String HOOK = "net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension";
	private static final String OFF_SCREEN = "shouldRenderOffScreen";
	private static final String BOX = "getRenderBoundingBox";
	private static final String BLOCK_ENTITY = "net.minecraft.world.level.block.entity.BlockEntity";
	private static final String BOX_TYPE = "net.minecraft.world.phys.AABB";

	/**
	 * Renderers known to draw beyond their block. Not the list the rule checks — it checks every renderer it
	 * finds — but a floor: if one of these is not found as an off-screen renderer, the import or the
	 * detection has stopped reaching the code this test exists for, and an empty violation list means nothing.
	 */
	private static final List<String> KNOWN_OFF_SCREEN = List.of(
			"dev.alaindustrial.client.render.GardenDroneBlockEntityRenderer",
			"dev.alaindustrial.client.render.IncubatorBlockEntityRenderer",
			"dev.alaindustrial.client.render.RadiantSolarPanelBlockEntityRenderer",
			"dev.alaindustrial.client.render.TeleporterCapsuleDoorRenderer",
			"dev.alaindustrial.client.render.WaterMillWheelBlockEntityRenderer",
			"dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer");

	private static JavaClasses productionClasses;

	@BeforeAll
	static void importProductionClasses() {
		productionClasses = new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages("dev.alaindustrial", "net.neoforged.neoforge.client.extensions");

		// Floor: the hook still exists under this name and shape. If NeoForge renamed it, the rule below would
		// be demanding a bridge to a method nobody calls any more.
		assertTrue(productionClasses.contain(HOOK) && productionClasses.get(HOOK).getMethods().stream()
						.anyMatch(OffScreenRendererBoxTest::isBoxBridge),
				HOOK + "." + BOX + "(BlockEntity) is gone - find NeoForge's new block entity culling hook, move "
						+ "every renderer's box onto it and update this test");

		List<String> offScreen = productionClasses.stream()
				.filter(OffScreenRendererBoxTest::isOffScreenRenderer)
				.map(JavaClass::getName)
				.toList();
		for (String known : KNOWN_OFF_SCREEN) {
			assertTrue(offScreen.contains(known), known + " is not detected as a renderer overriding "
					+ OFF_SCREEN + " (found: " + offScreen + ") - the rule below is not reaching the renderers it "
					+ "guards. If the renderer was deliberately changed, update KNOWN_OFF_SCREEN");
		}
	}

	@Test
	void everyOffScreenRendererDeclaresItsBox() {
		List<String> missing = violations(productionClasses);
		assertTrue(missing.isEmpty(), "renderers that override " + OFF_SCREEN + " but not " + BOX + " on NeoForge: "
				+ missing + ". NeoForge culls them by their own block before it reads " + OFF_SCREEN + ", so "
				+ "everything they draw outside that block vanishes when the block leaves the view. Add "
				+ "`public AABB " + BOX + "(<the renderer's block entity type> blockEntity)` covering what the "
				+ "renderer draws - without @Override, see TeleporterCapsuleDoorRenderer");
	}

	/** Proof the rule can fail: it has to flag both fixtures that get the box wrong, and only those. */
	@Test
	void ruleFlagsARendererWithoutABoxOrWithAMisTypedOne() {
		JavaClasses fixtures = new ClassFileImporter().importPackages("dev.alaindustrial.arch");
		String prefix = OffScreenRendererBoxTest.class.getName() + "$";
		List<String> flagged = violations(fixtures).stream()
				.filter(name -> name.startsWith(prefix))
				.toList();
		assertEquals(List.of(prefix + "OffScreenWithMisTypedBox", prefix + "OffScreenWithoutBox"), flagged,
				"the rule must flag exactly the two broken fixtures - if it flags the compliant one, the bridge is "
						+ "not being recognised; if it misses one, it would pass the production code vacuously");
		assertTrue(isOffScreenRenderer(fixtures.get(prefix + "OffScreenWithBox")),
				"the compliant fixture must still count as an off-screen renderer, or it proves nothing");
	}

	/** Renderers that override {@code shouldRenderOffScreen} and have no box bridge, sorted by name. */
	private static List<String> violations(JavaClasses classes) {
		return classes.stream()
				.filter(OffScreenRendererBoxTest::isOffScreenRenderer)
				.filter(type -> type.getAllMethods().stream()
						.noneMatch(method -> !method.getOwner().isInterface() && isBoxBridge(method)))
				.map(JavaClass::getName)
				.sorted()
				.toList();
	}

	private static boolean isOffScreenRenderer(JavaClass type) {
		return !type.isInterface() && type.isAssignableTo(RENDERER) && type.getAllMethods().stream()
				.anyMatch(method -> !method.getOwner().isInterface() && method.getName().equals(OFF_SCREEN)
						&& method.getRawParameterTypes().isEmpty());
	}

	private static boolean isBoxBridge(JavaMethod method) {
		return method.getName().equals(BOX)
				&& method.getRawParameterTypes().size() == 1
				&& method.getRawParameterTypes().get(0).getName().equals(BLOCK_ENTITY)
				&& method.getRawReturnType().getName().equals(BOX_TYPE);
	}

	// ---- Fixtures. Abstract, so they need nothing but the two methods under test; never loaded, only read. ----

	abstract static class OffScreenWithoutBox implements BlockEntityRenderer<ChestBlockEntity, BlockEntityRenderState> {
		@Override
		public boolean shouldRenderOffScreen() {
			return true;
		}
	}

	abstract static class OffScreenWithBox implements BlockEntityRenderer<ChestBlockEntity, BlockEntityRenderState> {
		@Override
		public boolean shouldRenderOffScreen() {
			return true;
		}

		public AABB getRenderBoundingBox(ChestBlockEntity blockEntity) {
			return new AABB(blockEntity.getBlockPos());
		}
	}

	/** The mistake written without {@code @Override} invites: the right name on the wrong type is not an override. */
	abstract static class OffScreenWithMisTypedBox
			implements BlockEntityRenderer<ChestBlockEntity, BlockEntityRenderState> {
		@Override
		public boolean shouldRenderOffScreen() {
			return true;
		}

		public AABB getRenderBoundingBox(BlockPos pos) {
			return new AABB(pos);
		}
	}
}
