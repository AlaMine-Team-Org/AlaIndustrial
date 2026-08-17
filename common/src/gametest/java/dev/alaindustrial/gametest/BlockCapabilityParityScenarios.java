package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.registry.BlockCapabilityRoster;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * MOD-433 — both-loader sweep: the capability a loader hands out on a block-entity face is exactly the
 * port the block entity itself exposes there.
 *
 * <p><b>The defect class.</b> Each loader used to name the block entities to expose by hand — one
 * list per capability per loader — and the lists drifted: the CESU was registered for energy on
 * Fabric and forgotten on NeoForge, so an FE mod (or any capability-based meter) saw a CESU on one
 * loader and thin air on the other; the NeoForge item list also lacked five containers, so the mod's
 * own item pipe was blind to them there. The mod's own energy gametests could not notice, because
 * NeoForge's own network lookup takes {@code energyPort()} straight from the block entity and never
 * goes through the capability. This sweep asks the LOADER — through its capability API, the way a
 * foreign mod would — and compares with the block entity's own answer, for every entry of
 * {@link ContentManifest#BLOCK_ENTITIES} and every face.
 *
 * <p><b>Why a game test and not L1.</b> Capabilities are only registered against live
 * {@code BlockEntityType}s and only resolve on a live level, and the answer has to be checked on both
 * loaders — each binds the same neutral ports through its own API. The body is loader-neutral; the
 * three probes are the only loader-specific part and are supplied by the two lane entry points
 * (Fabric: {@code EnergyStorage.SIDED} / {@code FluidStorage.SIDED} / {@code ItemStorage.SIDED};
 * NeoForge: {@code Capabilities.Energy.BLOCK} / {@code Fluid.BLOCK} / {@code Item.BLOCK}).
 *
 * <p><b>Two oracles for the exclusion, on purpose.</b> The expected energy answer is derived from
 * {@link EnergyPortHost} minus {@link BlockCapabilityRoster#NO_ENERGY_CAPABILITY} — the same rule the
 * loaders replay, so on its own it could never notice that constant being edited. {@link #PIPES_NEVER_ENERGY}
 * therefore restates the exclusion by id, here, and is checked independently in both directions:
 * dropping a pipe from the roster's exclusion set makes the loader expose it and this sweep go red,
 * instead of the item pipe silently becoming a 0-EU endpoint of the Fabric energy network; and adding
 * any other id to the set (which would hide that block from every FE mod on both loaders with the
 * per-face check still green) fails the set-equality pin before the sweep even starts.
 *
 * <p><b>Item presence is face-independent and size-independent, and that is the Fabric contract.</b>
 * Fabric's {@code ItemStorage.SIDED} fallback wraps any {@code Container} block entity, zero-slot
 * machines included and inert front faces included (the wrapper is empty on such a face, not absent).
 * NeoForge is held to the same answer. The player never sees the difference: the item pipe refuses a
 * face with no slots before it consults the capability (MOD-234).
 */
public final class BlockCapabilityParityScenarios {
	private BlockCapabilityParityScenarios() { }

	/** Loader-side question: does this loader hand out the capability on that face right now? */
	@FunctionalInterface
	public interface Probe {
		boolean present(Level level, BlockPos pos, Direction side);
	}

	/** The three loader probes an entry point supplies. */
	public record Probes(Probe energy, Probe fluid, Probe item) { }

	/** Where each candidate block is placed in turn. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/**
	 * Floor under the sweep size. {@code BLOCK_ENTITIES} holds 46 entries today; 30 still absorbs a
	 * removal or two while noticing a whole family (the eight cable grades share one entry, so it is
	 * the machine families that would have to vanish) — an empty or truncated manifest must not pass
	 * as "nothing to check".
	 */
	private static final int MIN_EXPECTED_BLOCK_ENTITIES = 30;

	/**
	 * Restated by id, deliberately NOT read from {@link BlockCapabilityRoster#NO_ENERGY_CAPABILITY}
	 * (see the class doc). Both extend {@code EnergyBlockEntity} with a zero-capacity buffer, so their
	 * {@code energyPort} is non-null on every face — the interface rule alone would expose them.
	 */
	private static final List<String> PIPES_NEVER_ENERGY = List.of("item_pipe", "fluid_pipe");

	/**
	 * For every manifest block entity and every face: energy capability present ⇔ the block entity is
	 * an {@link EnergyPortHost} whose {@code energyPort(face)} is non-null and it is not an excluded
	 * pipe; fluid capability present ⇔ {@link FluidPortHost} with a non-null {@code fluidPort(face)};
	 * item capability present ⇔ the block entity is a {@link Container}. Reports every mismatch in one
	 * message so a single run tells the whole story.
	 */
	public static void capabilitiesMatchPorts(GameTestHelper helper, Probes probes) {
		List<String> violations = new ArrayList<>();
		int checked = 0;

		// Pin the hand-kept exception in BOTH directions. The per-face check below only notices the
		// roster's set SHRINKING (a pipe dropped -> the loader exposes it -> red). If the set GREW — say
		// "cesu" added to NO_ENERGY_CAPABILITY — expected and actual would both flip to ABSENT on both
		// loaders and the sweep would stay green while re-creating the very defect it guards against.
		if (!Set.copyOf(PIPES_NEVER_ENERGY).equals(BlockCapabilityRoster.NO_ENERGY_CAPABILITY)) {
			helper.fail("MOD-433: BlockCapabilityRoster.NO_ENERGY_CAPABILITY is " + BlockCapabilityRoster.NO_ENERGY_CAPABILITY
					+ " but the sweep's independent restatement is " + PIPES_NEVER_ENERGY
					+ " — the exclusion set is closed: only the two pipes may implement EnergyPortHost without"
					+ " publishing an energy capability; TC-CAP-PARITY");
			return;
		}

		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(def.blocks().getFirst()));
			helper.setBlock(PROBE, block.defaultBlockState());
			BlockPos abs = helper.absolutePos(PROBE);
			Level level = helper.getLevel();
			if (level.getBlockState(abs).getBlock() != block) {
				helper.fail("MOD-433 could not place `" + def.blocks().getFirst() + "` at the probe position;"
						+ " the sweep cannot vouch for block entity `" + def.id() + "`; TC-CAP-PARITY");
				return;
			}
			BlockEntity be = level.getBlockEntity(abs);
			if (be == null || !def.type().isInstance(be)) {
				helper.fail("MOD-433: `" + def.blocks().getFirst() + "` did not produce a "
						+ def.type().getSimpleName() + " (got " + (be == null ? "no block entity" : be.getClass().getSimpleName())
						+ ") — the manifest's block/type pair is broken; TC-CAP-PARITY");
				return;
			}
			checked++;

			boolean excludedPipe = PIPES_NEVER_ENERGY.contains(def.id());
			// Accumulated over the six faces; the side-less lookup below must agree with them (MOD-448).
			boolean anyFaceEnergy = false;
			boolean anyFaceFluid = false;
			for (Direction face : Direction.values()) {
				boolean expectEnergy = be instanceof EnergyPortHost host
						&& host.energyPort(face) != null
						&& !BlockCapabilityRoster.NO_ENERGY_CAPABILITY.contains(def.id());
				boolean expectFluid = be instanceof FluidPortHost host && host.fluidPort(face) != null;
				boolean expectItem = be instanceof Container;

				anyFaceEnergy |= expectEnergy;
				anyFaceFluid |= expectFluid;

				boolean energy = probes.energy().present(level, abs, face);
				boolean fluid = probes.fluid().present(level, abs, face);
				boolean item = probes.item().present(level, abs, face);

				// The independent restatement of the exclusion: a pipe must expose no energy on any face,
				// whatever the roster constant says (see class doc — two oracles on purpose).
				if (excludedPipe && energy) {
					violations.add(def.id() + "/" + face.getName()
							+ ": energy capability present on a pipe (must never be — MOD-433 exclusion)");
				}
				if (energy != expectEnergy) {
					violations.add(def.id() + "/" + face.getName() + ": energy " + present(energy)
							+ " but energyPort(face) says " + present(expectEnergy));
				}
				if (fluid != expectFluid) {
					violations.add(def.id() + "/" + face.getName() + ": fluid " + present(fluid)
							+ " but fluidPort(face) says " + present(expectFluid));
				}
				if (item != expectItem) {
					violations.add(def.id() + "/" + face.getName() + ": item " + present(item)
							+ " but the block entity " + (expectItem ? "is" : "is not") + " a Container");
				}
			}
			// MOD-448: the same lookup with NO side. Both loader APIs allow it, viewer mods (Jade) ask that
			// way about every block under the crosshair, and our own fluid HUD reads the tank like this
			// (TC-FLUID-MOD126). Two things are asserted at once. It must not THROW — before the fix the
			// loader handed the null straight to energyPort/fluidPort, which dereferenced it (NPE in
			// FluidPipeBlockEntity.faceMode / EnergyCondenserBlockEntity.energyRoleForFace), and a throw
			// here fails the scenario on whichever loader regressed. And it must AGREE with the per-face
			// answers above: present exactly when some face publishes a port, so "no particular side"
			// cannot quietly become a back door into a block that exposes nothing anywhere.
			boolean energyNoSide = probes.energy().present(level, abs, null);
			boolean fluidNoSide = probes.fluid().present(level, abs, null);
			if (energyNoSide != anyFaceEnergy) {
				violations.add(def.id() + "/no-side: energy " + present(energyNoSide)
						+ " but across the six faces it is " + present(anyFaceEnergy) + " (MOD-448)");
			}
			if (fluidNoSide != anyFaceFluid) {
				violations.add(def.id() + "/no-side: fluid " + present(fluidNoSide)
						+ " but across the six faces it is " + present(anyFaceFluid) + " (MOD-448)");
			}

			helper.setBlock(PROBE, Blocks.AIR);
		}

		// Violations before the floor: a concrete offender is more useful than "the sweep looks small".
		if (!violations.isEmpty()) {
			helper.fail("MOD-433: " + violations.size() + " capability/port mismatch(es) on this loader — "
					+ String.join("; ", violations)
					+ ". A capability is derived from the block entity's interfaces (BlockCapabilityRoster);"
					+ " a mismatch means this loader's replay of the roster is broken or a port changed"
					+ " without the roster noticing; TC-CAP-PARITY");
		}
		// A sweep that silently matched nothing would be the perfect fake guard.
		if (checked < MIN_EXPECTED_BLOCK_ENTITIES) {
			helper.fail("MOD-433 sweep inspected only " + checked + " block entities — expected at least "
					+ MIN_EXPECTED_BLOCK_ENTITIES + "; the manifest or the placement loop is broken, so this"
					+ " test proves nothing; TC-CAP-PARITY");
		}
		helper.succeed();
	}

	private static String present(boolean present) {
		return present ? "PRESENT" : "ABSENT";
	}
}
