package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.fresh;
import static dev.alaindustrial.gametest.SaveFormatTestSupport.load;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * L2 characterization of the cross-cutting capabilities of the machine base (MOD-712, batch 0, BE-1):
 * owner, statistics, the upgrade panel (mute chip, statistics chip, overclocker) and the evolution
 * counter, for every block entity of {@link ContentManifest#BLOCK_ENTITIES} that is a
 * {@link MachineBlockEntity}.
 *
 * <p>The MOD-701 sweep places a block with no owner and zero statistics, so the keys {@code Owner},
 * {@code OwnerName}, {@code Stats*} and {@code EvolveProgress} never carry a value in its tag. Here each
 * machine is given all of them — an owner, worked ticks, EU counters, processed items, three chips in
 * its panel and, where the save has one, an evolution counter — and then goes through save, load and
 * save again. The two tags must be equal, the getters must answer the same on both sides, and what
 * they answer is compared with a reviewed reference, because BE-1 moves exactly these getters into
 * components: a round trip alone would stay green if save and load changed the same way.
 */
public final class MachineCapabilityRoundTripScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MachineCapabilityRoundTripScenarios::machineCapabilitiesSurviveASave,
								"machine_char_capability_round_trip")
						.ticks(100));

		private Roster() {}
	}

	private MachineCapabilityRoundTripScenarios() {}

	private static final UUID OWNER = UUID.fromString("00000000-0000-4712-8000-000000000712");
	private static final String OWNER_NAME = "Characterizer";

	/** Base-capability keys whose absence from a machine's save is recorded in the reference. */
	private static final List<String> KEYS = List.of("Owner", "OwnerName", "StatsActiveTicks", "StatsEnergyIn",
			"StatsEnergyOut", "StatsEnergyGenerated", "StatsEnergyConsumed", "StatsItemsProcessed", "EvolveProgress",
			"EvolveChip", "Items", "Progress", "MaxProgress");

	/**
	 * @implements MOD-712-CH01 — owner, statistics, panel chips and evolution counter of every machine
	 *     survive save, load and save unchanged and answer the reviewed values.
	 */
	public static void machineCapabilitiesSurviveASave(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		List<String> lines = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(def.blocks().getFirst()));
			if (!(fresh(def.factory(), block) instanceof MachineBlockEntity)) {
				continue;
			}
			MachineBlockEntity source = prepared(def, block, registries);
			CompoundTag first = source.saveWithoutMetadata(registries);
			MachineBlockEntity restored = (MachineBlockEntity) load(fresh(def.factory(), block), registries, first);
			CompoundTag second = restored.saveWithoutMetadata(registries);
			if (!first.equals(second)) {
				failures.add(def.id() + ": the re-save differs — first " + first + ", then " + second);
			}
			String before = capabilities(source, first);
			String after = capabilities(restored, second);
			if (!before.equals(after)) {
				failures.add(def.id() + ": " + before + " came back as " + after);
			}
			lines.add(def.id() + ": " + after);
			lines.add(def.id() + " keys:" + missingKeys(first));
		}
		if (!failures.isEmpty()) {
			helper.fail(failures.size() + " machines lose a capability in a save:\n  " + String.join("\n  ", failures));
			return;
		}
		ReferenceLines.compare(helper, "MachineCapabilitySnapshot",
				"Reviewed owner, statistics, panel and evolution answers of every machine after a save (MOD-712) —"
						+ " the reference {@link MachineCapabilityRoundTripScenarios} compares against.",
				"*:machine_char_capability*", lines, MachineCapabilitySnapshot.LINES);
	}

	/**
	 * A machine carrying every base capability: built, given its values, saved, its evolution counter
	 * (when the save has one) set in the tag, and loaded into a fresh block entity of the same kind.
	 */
	private static MachineBlockEntity prepared(ContentManifest.BlockEntityDef<?> def, Block block,
			RegistryAccess registries) {
		MachineBlockEntity be = (MachineBlockEntity) fresh(def.factory(), block);
		be.setOwner(OWNER, OWNER_NAME);
		EnergyBuffer energy = be.getEnergyStorage();
		energy.setCountersEnabled(true);
		for (int i = 0; i < 3; i++) {
			be.recordEuRate(7);
		}
		energy.restoreCounters(11L, 13L, 17L, 19L);
		for (int i = 0; i < 5; i++) {
			be.recordItemProcessed();
		}
		if (be.hasUpgradeSlots()) {
			int start = be.upgradeSlotStart();
			be.setItem(start, new ItemStack(ModContent.MUTE_CHIP.get()));
			be.setItem(start + 1, new ItemStack(ModContent.OVERCLOCKER_CHIP_I.get()));
			be.setItem(start + 2, new ItemStack(ModContent.STATS_CHIP.get()));
		}
		CompoundTag tag = be.saveWithoutMetadata(registries);
		if (tag.contains("EvolveProgress")) {
			tag.putInt("EvolveProgress", 37);
		}
		if (tag.contains("EvolveChip")) {
			tag.putInt("EvolveChip", 1);
		}
		return (MachineBlockEntity) load(fresh(def.factory(), block), registries, tag);
	}

	/** Every getter BE-1 moves, in one line. */
	private static String capabilities(MachineBlockEntity be, CompoundTag saved) {
		EnergyBuffer energy = be.getEnergyStorage();
		return "owner=" + (be.isOwner(OWNER) ? be.getOwnerName() : String.valueOf(be.getOwner()))
				+ " active=" + be.activeTicks() + " items=" + be.totalItemsProcessed()
				+ " eu=" + energy.getTotalEnergyIn() + "/" + energy.getTotalEnergyOut() + "/"
				+ energy.getTotalEnergyGenerated() + "/" + energy.getTotalEnergyConsumed()
				+ " panel=" + flag(be.hasUpgradeSlots()) + " mute=" + flag(be.isMuted())
				+ " stats=" + flag(be.hasStatsChip()) + " oc=" + be.overclockerCount() + "/" + be.overclockerCap()
				+ " rate=" + be.effectiveEuPerTick(be.baseEuPerTick()) + " dur=" + be.effectiveDuration(200)
				+ " evolve=" + saved.getIntOr("EvolveProgress", -1) + "/" + saved.getIntOr("EvolveChip", -1);
	}

	private static String missingKeys(CompoundTag saved) {
		StringBuilder missing = new StringBuilder();
		for (String key : KEYS) {
			if (!saved.contains(key)) {
				missing.append(" -").append(key);
			}
		}
		return missing.length() == 0 ? " all" : missing.toString();
	}

	private static String flag(boolean value) {
		return value ? "1" : "0";
	}
}
