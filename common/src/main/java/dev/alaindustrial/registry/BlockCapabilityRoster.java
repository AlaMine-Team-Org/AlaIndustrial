package dev.alaindustrial.registry;

import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.registry.content.AgricultureContent;
import dev.alaindustrial.registry.content.FluidContent;
import dev.alaindustrial.registry.content.StorageContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.Container;

/**
 * Which block entities publish which capability, derived from {@link ContentManifest#BLOCK_ENTITIES}
 * by <b>interface</b> (MOD-433) — one answer for both loaders.
 *
 * <p><b>Why this exists.</b> Until MOD-433 each loader kept its own hand-written list of the block
 * entities to expose: 36 {@code EnergyStorage.SIDED} lines on Fabric, a 35-entry
 * {@code ENERGY_BLOCK_ENTITIES} on NeoForge, and two more pairs of lists for fluid and item. Two lists
 * for one fact drift, and they did: the CESU was on the Fabric list and missing from the NeoForge one,
 * so any FE mod (and any capability-based meter) saw the CESU on Fabric and not on NeoForge; the item
 * list on NeoForge lacked the CESU, the distillation column and the three mob repellers, so the mod's
 * OWN item pipe — which resolves neighbours only through the capability on that loader — was blind to
 * those five blocks on NeoForge alone. The MOD-193 defect class, recurring. A block entity now
 * publishes a capability because of what it <i>is</i>: it implements {@link EnergyPortHost},
 * {@link FluidPortHost} or {@link Container}, and both loaders replay the same three rosters.
 *
 * <p><b>Why plain {@code Class} checks and no registry reads.</b> The rosters are computed from
 * {@code def.type()} only, so they can be evaluated at any time — including during Fabric's eager
 * {@code ModBlockEntities.init()} and NeoForge's static init — without touching {@link ModContent}
 * (which is exactly what the earlier "shared list" attempt got wrong: it read {@code ModContent}
 * handles before the loaders had bound them and crashed at startup). The loader resolves the live
 * {@code BlockEntityType} through {@code def.registeredType()} at its own registration moment, which
 * throws rather than guesses if it is asked too early.
 *
 * <p><b>The hand-maintained exceptions.</b> {@link #NO_ENERGY_CAPABILITY} holds three block-entity
 * DEFINITIONS (MOD-711; they were ids, and a block id there excluded nothing): {@code item_pipe}
 * (the item pipe and its advanced grade), {@code fluid_pipe} (one block entity behind five blocks: the
 * basic, advanced and reinforced fluid pipes and both steam pipes) and {@code sprinkler}. Both pipes
 * extend {@code EnergyBlockEntity} for its tick/persistence scaffolding and therefore implement
 * {@link EnergyPortHost} with a zero-capacity buffer. Since MOD-691 they report {@code NONE} on every
 * face, so {@code energyPort()} is {@code null} on both loaders and they are no endpoint of the energy
 * network anywhere; before that the default {@code BOTH} role made them 0-EU endpoints on NeoForge,
 * whose own lookup takes {@code energyPort()} straight from the block entity. Neither loader ever
 * registered them for energy. The exclusion is pinned by the both-loader gametest sweep
 * {@code BlockCapabilityParityScenarios}, which asserts independently — by id, not through this
 * constant — that the pipes expose no energy on any of their blocks, and that every id here names a
 * manifest block entity.
 *
 * <p>The sprinkler (MOD-525) is the third, for a different reason: it extends {@code EnergyBlockEntity}
 * for the same tick/persistence scaffolding but draws no EU at all — a zero-capacity buffer and
 * {@code NONE} on every face — so advertising an energy store would offer the network an endpoint that
 * can never take or give anything. It is paid in nutrient solution instead, through its fluid port.
 *
 * <p>Chests are ordinary {@link Container}s here; how a loader wraps them (the MOD-391 combined
 * double-chest view) is that loader's choice at replay time.
 */
public final class BlockCapabilityRoster {
	private BlockCapabilityRoster() {
	}

	/**
	 * Block-entity definitions that implement {@link EnergyPortHost} but must NOT publish an energy
	 * capability. Definitions, not ids (MOD-711): a block id cannot be put here by type. See the class doc
	 * for why these, and why the list is closed by a gametest rather than by convention.
	 */
	public static final Set<ContentManifest.BlockEntityDef<?>> NO_ENERGY_CAPABILITY = Set.of(
			StorageContent.ITEM_PIPE_BE, FluidContent.FLUID_PIPE_BE, AgricultureContent.SPRINKLER_BE);

	/** Every manifest entry whose block entity is an {@link EnergyPortHost}, minus {@link #NO_ENERGY_CAPABILITY}. */
	public static List<ContentManifest.BlockEntityDef<?>> energyHosts() {
		List<ContentManifest.BlockEntityDef<?>> hosts = new ArrayList<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			if (EnergyPortHost.class.isAssignableFrom(def.type()) && !NO_ENERGY_CAPABILITY.contains(def)) {
				hosts.add(def);
			}
		}
		return List.copyOf(hosts);
	}

	/** Every manifest entry whose block entity is a {@link FluidPortHost}. */
	public static List<ContentManifest.BlockEntityDef<?>> fluidHosts() {
		List<ContentManifest.BlockEntityDef<?>> hosts = new ArrayList<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			if (FluidPortHost.class.isAssignableFrom(def.type())) {
				hosts.add(def);
			}
		}
		return List.copyOf(hosts);
	}

	/**
	 * Every manifest entry whose block entity is a {@link Container} — chests included, zero-slot
	 * machines included. Zero-slot machines (the moonlit/daylight panels, the teleporter, the charging
	 * station, the electric heater) are deliberately NOT filtered out: Fabric's {@code ItemStorage.SIDED}
	 * wraps any {@code Container} through its global fallback regardless of size, and parity means the
	 * NeoForge side answers the same. The item pipe never draws an arm to such a face on either loader —
	 * {@code ItemPipeBlock.hasEndpointCandidate} refuses a face with no slots (MOD-234) before it ever
	 * consults the capability — so an empty handler is invisible to the player.
	 */
	public static List<ContentManifest.BlockEntityDef<?>> itemContainers() {
		List<ContentManifest.BlockEntityDef<?>> containers = new ArrayList<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			if (Container.class.isAssignableFrom(def.type())) {
				containers.add(def);
			}
		}
		return List.copyOf(containers);
	}
}
