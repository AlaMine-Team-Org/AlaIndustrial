package dev.alaindustrial.gametest;

import com.mojang.serialization.Codec;
import dev.alaindustrial.core.teleport.RemoteLog;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.item.energy.PouchContents;
import dev.alaindustrial.item.fluid.DistillationColumnContents;
import dev.alaindustrial.item.fluid.FluidTankContents;
import dev.alaindustrial.item.module.ItemModules;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.item.tool.DrillUpgrades;
import dev.alaindustrial.item.tool.MagnetFilter;
import dev.alaindustrial.item.tool.NetworkScanData;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.registry.ModDataComponents;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Unit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * L2 sweep over every item data component the mod registers (MOD-701, batch 0).
 *
 * <p>For each entry of {@link ModDataComponents#COMPONENTS} the registered type's persistent codec
 * runs encode -> decode -> encode on a sample and both tags must be equal; the decoded value must
 * also equal the sample, which is what catches a field dropped from a codec (the field then decodes
 * to its default, and a default re-encodes just as stably). The network codec then carries the same
 * sample across a buffer and must hand back an equal value with no bytes left over.
 *
 * <p><b>Why a sample table and not a generated value.</b> A codec can only be exercised on a value,
 * and the one value that exercises every field is one where no field sits at its default — which is
 * a decision about the type, not something a loop can guess. A component with no sample and no
 * {@link #EXEMPT} entry fails the test by name, so a new component cannot quietly stay outside the
 * sweep; a sample or exemption for an id the list no longer holds fails too.
 */
public final class DataComponentRoundTripScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(DataComponentRoundTripScenarios::everyComponentRoundTrips,
								"save_format_every_component_round_trips")
						.fabricId("SaveFormatGameTest", "everyComponentRoundTrips").ticks(20, 40));

		private Roster() {}
	}

	private DataComponentRoundTripScenarios() {}

	/**
	 * Components deliberately not swept, each with its reason. Empty today: all 29 are persistent and
	 * networked. A transient component (no persistent codec) belongs here, never in {@link #samples()}.
	 */
	private static final Map<Identifier, String> EXEMPT = Map.of();

	/** One non-default value per component id. Built per run: several values need the registries. */
	private static Map<Identifier, Object> samples() {
		Map<Identifier, Object> s = new LinkedHashMap<>();
		s.put(ModDataComponents.STORED_ENERGY_ID, 123_456L);
		s.put(ModDataComponents.NETWORK_SCAN_ID, new NetworkScanData(12, 3, 4, 2, 500L, 300L, 250L));
		s.put(ModDataComponents.NETWORK_ANALYZER_MODE_ID, AnalyzerMode.STOP_AT_STORAGE);
		s.put(ModDataComponents.POUCH_ENERGY_ID, 9_876L);
		s.put(ModDataComponents.POUCH_CONTENTS_ID, new PouchContents(
				List.of(new ItemStack(Items.COBBLESTONE, 40), new ItemStack(Items.TORCH, 5))));
		s.put(ModDataComponents.BLUEPRINT_PATTERN_ID, BlueprintPattern.of(List.of(
				new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT),
				ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY,
				new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT))));
		s.put(ModDataComponents.BLUEPRINT_RESULT_ID, new ItemStackTemplate(Items.HOPPER, 3));
		s.put(ModDataComponents.BLUEPRINT_SUBSTITUTE_ID, true);
		s.put(ModDataComponents.CAPSULE_FLUID_ID, BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA));
		s.put(ModDataComponents.FLUID_TANK_CONTENTS_ID, tank(Fluids.WATER, 3_000L));
		s.put(ModDataComponents.DISTILLATION_COLUMN_CONTENTS_ID, new DistillationColumnContents(
				Optional.of(tank(Fluids.WATER, 1_200L)), Optional.empty(), Optional.of(tank(Fluids.LAVA, 7L)), 42));
		s.put(ModDataComponents.TELEPORTER_PRIVATE_ID, true);
		s.put(ModDataComponents.TELEPORTER_RTP_MODULE_ID, true);
		s.put(ModDataComponents.MAGNET_ENABLED_ID, true);
		s.put(ModDataComponents.SOUL_VESSEL_KILLS_ID, 17);
		s.put(ModDataComponents.REPAIR_COUNT_ID, 3);
		s.put(ModDataComponents.STEP_ASSIST_ENABLED_ID, true);
		s.put(ModDataComponents.SABER_ACTIVE_ID, true);
		s.put(ModDataComponents.GEIGER_ALERT_ID, true);
		s.put(ModDataComponents.MUTATION_GRADE_ID, MutationGrade.EPIC);
		s.put(ModDataComponents.TELEPORTER_OWNER_ID, new UUID(0x0123_4567_89AB_CDEFL, 0x7EDC_BA98_7654_3210L));
		s.put(ModDataComponents.TELEPORTER_POINTS_ID, new TeleportPoints(List.of(
				new TeleportPoint(Level.OVERWORLD, new BlockPos(10, 64, -20), "Home", 1),
				new TeleportPoint(Level.NETHER, new BlockPos(-5, 70, 3), "", 2))));
		s.put(ModDataComponents.DRILL_UPGRADES_ID, new DrillUpgrades(List.of(DrillUpgrades.COLUMN_BORE)));
		s.put(ModDataComponents.DRILL_COLUMN_ENABLED_ID, true);
		s.put(ModDataComponents.TELEPORTER_LOG_ID, new RemoteLog(3, 1, List.of(
				new RemoteLog.Entry(1, 100L, RemoteLog.Kind.JUMPED, false, new RemoteLog.Station("Home", 1),
						RemoteLog.Station.NONE, 500, 0, 0),
				new RemoteLog.Entry(2, 250L, RemoteLog.Kind.RANDOM_JUMPED, true, new RemoteLog.Station("", 3),
						RemoteLog.Station.NONE, 12, -40, 800))));
		s.put(ModDataComponents.ELECTRIC_BOW_CHARGED_ID, Unit.INSTANCE);
		s.put(ModDataComponents.CABLE_COLOR_ID, DyeColor.LIME);
		s.put(ModDataComponents.ITEM_MODULES_ID, new ItemModules(
				List.of(ItemStack.EMPTY, new ItemStack(Items.REDSTONE), new ItemStack(Items.GLOWSTONE_DUST, 2))));
		s.put(ModDataComponents.MAGNET_FILTER_ID, new MagnetFilter(List.of(
				new MagnetFilter.Cell("minecraft:stone", ""), MagnetFilter.Cell.EMPTY,
				new MagnetFilter.Cell("minecraft:iron_ingot", "c:ingots")), true, MagnetFilter.Match.TAG));
		return s;
	}

	private static FluidTankContents tank(Fluid fluid, long amount) {
		return new FluidTankContents(BuiltInRegistries.FLUID.wrapAsHolder(fluid), amount);
	}

	/**
	 * @implements R-PER-01 — every data component of {@code ModDataComponents.COMPONENTS} keeps its
	 *     value through its persistent codec and its network codec; a component with no sample fails
	 *     by name.
	 * @covers R-PER-01
	 */
	public static void everyComponentRoundTrips(GameTestHelper helper) {
		if (ModDataComponents.COMPONENTS.isEmpty()) {
			helper.fail("ModDataComponents.COMPONENTS is empty — the sweep would check nothing");
			return;
		}
		RegistryAccess registries = helper.getLevel().registryAccess();
		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		Map<Identifier, Object> samples = samples();
		List<String> failures = new ArrayList<>();
		List<Identifier> listed = new ArrayList<>();
		for (ModDataComponents.ComponentDef<?> def : ModDataComponents.COMPONENTS) {
			Identifier id = def.id();
			listed.add(id);
			if (EXEMPT.containsKey(id)) {
				continue;
			}
			DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
			Object sample = samples.get(id);
			if (type == null) {
				failures.add(id + ": not registered");
			} else if (sample == null) {
				failures.add(id + ": no sample in DataComponentRoundTripScenarios.samples() and no EXEMPT entry");
			} else {
				String problem = roundTrip(type, sample, ops, registries);
				if (problem != null) {
					failures.add(id + ": " + problem);
				}
			}
		}
		for (Identifier id : samples.keySet()) {
			if (!listed.contains(id)) {
				failures.add(id + ": has a sample but is not in ModDataComponents.COMPONENTS any more");
			}
		}
		for (Identifier id : EXEMPT.keySet()) {
			if (!listed.contains(id)) {
				failures.add(id + ": is exempt but is not in ModDataComponents.COMPONENTS any more");
			}
		}
		if (!failures.isEmpty()) {
			helper.fail(failures.size() + " component(s) do not round-trip:\n  " + String.join("\n  ", failures));
			return;
		}
		helper.succeed();
	}

	/** The first thing wrong with {@code rawSample}'s trip through {@code type}'s codecs, or null. */
	@SuppressWarnings("unchecked")
	private static <T> @Nullable String roundTrip(DataComponentType<T> type, Object rawSample, RegistryOps<Tag> ops,
			RegistryAccess registries) {
		T sample = (T) rawSample;
		Codec<T> codec = type.codec();
		if (codec == null) {
			return "has no persistent codec — list it in EXEMPT with the reason instead of a sample";
		}
		try {
			Tag first = codec.encodeStart(ops, sample).getOrThrow();
			T decoded = codec.parse(ops, first).getOrThrow();
			Tag second = codec.encodeStart(ops, decoded).getOrThrow();
			if (!first.equals(second)) {
				return "persistent codec is not stable: " + first + " re-encodes as " + second;
			}
			if (!sameValue(sample, decoded)) {
				return "persistent codec changed the value: " + sample + " -> " + decoded + " (via " + first + ")";
			}
			RegistryFriendlyByteBuf buffer = RegistryFriendlyByteBuf.decorator(registries).apply(Unpooled.buffer());
			type.streamCodec().encode(buffer, sample);
			T fromWire = type.streamCodec().decode(buffer);
			if (buffer.readableBytes() != 0) {
				return "network codec left " + buffer.readableBytes() + " unread byte(s)";
			}
			if (!sameValue(sample, fromWire)) {
				return "network codec changed the value: " + sample + " -> " + fromWire;
			}
		} catch (RuntimeException e) {
			return "threw " + e;
		}
		return null;
	}

	/**
	 * Value equality where the type's own {@code equals} cannot give it: a holder is compared by what
	 * it holds, a blueprint by its stacks ({@code ItemStack} has identity equality, and so does a
	 * record generated over a list of them). Every other sample type has a value {@code equals}.
	 *
	 * <p>The blueprint branch works around a production defect this sweep found, filed as MOD-723:
	 * {@code BlueprintPattern} lacks the value {@code equals} its siblings {@code PouchContents} and
	 * {@code ItemModules} have. Remove the branch together with that fix.
	 */
	private static boolean sameValue(Object a, Object b) {
		if (a instanceof Holder<?> ha && b instanceof Holder<?> hb) {
			return ha.value() == hb.value();
		}
		if (a instanceof BlueprintPattern pa && b instanceof BlueprintPattern pb) {
			if (pa.items().size() != pb.items().size()) {
				return false;
			}
			for (int i = 0; i < pa.items().size(); i++) {
				if (!ItemStack.matches(pa.items().get(i), pb.items().get(i))) {
					return false;
				}
			}
			return true;
		}
		return Objects.equals(a, b);
	}
}
