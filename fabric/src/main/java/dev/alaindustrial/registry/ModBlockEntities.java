package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.fabric.PortAsEnergyStorage;
import dev.alaindustrial.core.fabric.TankAsFluidStorage;
import dev.alaindustrial.core.fluid.FluidPortHost;
// MOD-022 Phase 2: machines now expose a platform-neutral EnergyPort (MachineBlockEntity#energyPort).
// The Fabric SIDED capability binding is the per-loader seam: the neutral port is published through
// Team Reborn's EnergyStorage.SIDED via the PortAsEnergyStorage reverse adapter. NeoForge binds the same
// neutral port through RegisterCapabilitiesEvent.registerBlockEntity(...) with its own EnergyHandler
// adapter. MOD-028: fluid follows the identical pattern — the neutral FluidPort (MachineBlockEntity
// subclasses implementing FluidPortHost#fluidPort) is published through FluidStorage.SIDED via the
// TankAsFluidStorage reverse adapter; NeoForge binds the same neutral port through
// RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.Fluid.BLOCK, ...).
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric {@link BlockEntityType} registration: a replay of the shared
 * {@link ContentManifest#BLOCK_ENTITIES} list, plus the Team Reborn Energy {@code SIDED} lookup that
 * publishes each machine's neutral energy buffer to the energy network.
 *
 * <p><b>MOD-307 → MOD-554.</b> MOD-307 moved each type's id, factory and valid-block set into the
 * manifest; MOD-554 moved the <b>list</b> as well. Until then this file and
 * {@code ModBlockEntitiesNeoForge} each carried their own 58-entry roster of
 * {@code ContentManifest.blockEntity("id", X.class)} lookups, and the only thing holding the two in
 * step was a Python comparison run after the fact. A type added on one loader and forgotten on the
 * other compiled and shipped as "this block has no block entity there": it does not tick, and its
 * screen does not open.
 *
 * <p><b>What is left here is the Fabric registration MECHANISM, and only that:</b> eager
 * {@code Registry.register} of a {@code BlockEntityType} built from the manifest entry, plus the
 * capability bindings. There are deliberately NO typed handles: nothing outside this file ever read
 * one — code that needs a concrete type asks the manifest
 * ({@code ContentManifest.blockEntity(id, X.class)}, then {@code BlockEntityDef#registeredType()}) —
 * so a per-type field here would be one more name to keep in step for no reader.
 */
public final class ModBlockEntities {
	private ModBlockEntities() {
	}

	/**
	 * Registers every {@link ContentManifest#BLOCK_ENTITIES} entry, in list order, then derives the
	 * per-face capabilities from the same manifest.
	 *
	 * <p>Fabric registers eagerly, so an entry's valid blocks are resolved right here — safe because
	 * {@code ModBlocks.init()} runs before {@code ModBlockEntities.init()} in the entrypoint.
	 */
	public static void init() {
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			register(def);
		}

		// MOD-403: the 40 `ModContent.X_BE = () -> X;` lines that used to sit here are gone — each
		// BLOCK_ENTITIES entry carries its own `bind`, applied by register() below, so a handle can no
		// longer be left on its throwing placeholder because someone forgot a line.

		// MOD-433: capabilities are derived from the manifest by INTERFACE, not named per block. Every
		// block entity that implements EnergyPortHost publishes its neutral EnergyPort through Team
		// Reborn's EnergyStorage.SIDED via the PortAsEnergyStorage reverse adapter (minus the two pipes —
		// see BlockCapabilityRoster.NO_ENERGY_CAPABILITY); every FluidPortHost publishes its FluidPort
		// through FluidStorage.SIDED via TankAsFluidStorage. NeoForge replays the same rosters through
		// RegisterCapabilitiesEvent. Before this, 36 + 8 hand-written lines lived here and 35 + 8 more on
		// NeoForge, and they had already drifted (the CESU was missing from the NeoForge energy list).
		//
		// This is safe where the earlier "shared list" attempt was not: the roster reads only the
		// manifest's Class objects, and def.registeredType() resolves the live BlockEntityType from the
		// vanilla registry — populated by the loop above — never a ModContent handle.
		// Item storage needs no line on Fabric: ItemStorage.SIDED wraps any Container through its
		// global fallback (the chests' combined view is the one explicit provider, in the entrypoint).
		for (ContentManifest.BlockEntityDef<?> def : BlockCapabilityRoster.energyHosts()) {
			bindEnergy(def);
		}
		for (ContentManifest.BlockEntityDef<?> def : BlockCapabilityRoster.fluidHosts()) {
			bindFluid(def);
		}
	}

	/**
	 * Publishes {@code def}'s neutral energy port through {@code EnergyStorage.SIDED}; the roster guarantees the cast.
	 *
	 * <p>MOD-448: {@code dir} is nullable here — {@code BlockApiLookup#find} lets a caller ask without naming
	 * a side, and viewer mods (Jade) do exactly that on every block under the crosshair. The nullable case
	 * is answered by {@code energyPortForLookup}, which documents the contract for both loaders; handing the
	 * null to {@code energyPort} threw inside the implementation (NPE in
	 * {@code EnergyCondenserBlockEntity.energyRoleForFace}) — a defect that predates the manifest derivation
	 * and lived in the per-block registrations before it.
	 */
	private static <T extends BlockEntity> void bindEnergy(ContentManifest.BlockEntityDef<T> def) {
		EnergyStorage.SIDED.registerForBlockEntity(
				(be, dir) -> PortAsEnergyStorage.of(((EnergyPortHost) be).energyPortForLookup(dir)),
				def.registeredType());
	}

	/**
	 * Publishes {@code def}'s neutral fluid port through {@code FluidStorage.SIDED}; the roster guarantees the cast.
	 * The side-less query goes through {@code fluidPortForLookup} for the reason given on {@link #bindEnergy} (MOD-448).
	 */
	private static <T extends BlockEntity> void bindFluid(ContentManifest.BlockEntityDef<T> def) {
		FluidStorage.SIDED.registerForBlockEntity(
				(be, dir) -> TankAsFluidStorage.of(((FluidPortHost) be).fluidPortForLookup(dir)),
				def.registeredType());
	}

	/**
	 * Registers the {@code BlockEntityType} described by one shared manifest entry (MOD-307). The id, the
	 * factory and the valid-block set all come from {@link ContentManifest#BLOCK_ENTITIES}; MOD-554 made
	 * the CALL come from there too, so this loader can no longer register a different subset than the
	 * other one.
	 *
	 * <p><b>MOD-403.</b> The registered type is also published into the entry's {@link ModContent} slot
	 * here, via the {@code bind} the manifest carries. That replaces 40 hand-written
	 * {@code ModContent.X_BE = () -> X;} lines per loader whose only guard was a startup crash.
	 */
	private static <T extends BlockEntity> void register(ContentManifest.BlockEntityDef<T> def) {
		BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
				Industrialization.id(def.id()), new BlockEntityType<>(def.factory(), def.blockSet()));
		def.bind().accept(() -> type);
	}
}
