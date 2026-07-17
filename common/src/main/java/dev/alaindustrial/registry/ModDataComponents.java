package dev.alaindustrial.registry;

import com.mojang.serialization.Codec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.AnalyzerMode;
import dev.alaindustrial.item.FluidTankContents;
import dev.alaindustrial.item.NetworkScanData;
import dev.alaindustrial.item.PouchContents;
import dev.alaindustrial.item.TeleportPoints;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/**
 * Custom data components (MOD-022 facade). {@link #STORED_ENERGY} lets an energy-storage block carry
 * its buffered EU on its dropped item (R-BRK-07), so a charged BatteryBox keeps its charge through
 * break → place. Storage block entities emit/read it via collect/applyImplicitComponents; the block's
 * loot table copies it from the block entity onto the drop. {@link #NETWORK_SCAN} carries the Network
 * Analyzer's last reading on the tool itself, so the tooltip can show it after the actionbar fades.
 *
 * <p>NeoForge freezes the vanilla {@code DATA_COMPONENT_TYPE} registry before mod construction, so a
 * direct {@code Registry.register} (fine on Fabric) throws {@code Registry is already frozen} there.
 * Each loader binds the handles below during its own registration — Fabric via an eager
 * {@code Registry.register}, NeoForge via a {@code DeferredRegister} holder (itself a {@link Supplier}) —
 * and content reads them lazily through {@code .get()}.
 */
public final class ModDataComponents {
	private ModDataComponents() {
	}

	/** Registry ids, shared by both loaders' registration. */
	public static final Identifier STORED_ENERGY_ID = Industrialization.id("stored_energy");
	public static final Identifier NETWORK_SCAN_ID = Industrialization.id("network_scan");
	public static final Identifier NETWORK_ANALYZER_MODE_ID = Industrialization.id("network_analyzer_mode");
	public static final Identifier POUCH_ENERGY_ID = Industrialization.id("pouch_energy");
	public static final Identifier POUCH_CONTENTS_ID = Industrialization.id("pouch_contents");
	public static final Identifier CAPSULE_FLUID_ID = Industrialization.id("capsule_fluid");
	public static final Identifier TELEPORTER_PRIVATE_ID = Industrialization.id("teleporter_private");
	public static final Identifier TELEPORTER_OWNER_ID = Industrialization.id("teleporter_owner");
	public static final Identifier TELEPORTER_POINTS_ID = Industrialization.id("teleporter_points");
	public static final Identifier FLUID_TANK_CONTENTS_ID = Industrialization.id("fluid_tank_contents");

	/** Buffered EU carried on a storage block's item form. Bound once per loader before first access. */
	public static Supplier<DataComponentType<Long>> STORED_ENERGY = () -> {
		throw new IllegalStateException("ModDataComponents.STORED_ENERGY read before its loader bound it");
	};

	/** Last Network Analyzer scan, stored on the tool so its tooltip can replay the reading (MOD-016). */
	public static Supplier<DataComponentType<NetworkScanData>> NETWORK_SCAN = () -> {
		throw new IllegalStateException("ModDataComponents.NETWORK_SCAN read before its loader bound it");
	};

	/** Network Analyzer's current mode (TRAVERSE / STOP_AT_STORAGE), persisted on the tool (MOD-047). */
	public static Supplier<DataComponentType<AnalyzerMode>> NETWORK_ANALYZER_MODE = () -> {
		throw new IllegalStateException("ModDataComponents.NETWORK_ANALYZER_MODE read before its loader bound it");
	};

	/**
	 * Battery Pouch EU buffer (MOD-052) — the item-in-inventory charge, distinct from {@link #STORED_ENERGY}
	 * (which carries a <em>block's</em> buffer across break/place). Read/written only via
	 * {@link dev.alaindustrial.item.ItemEnergy}: absent = 0 EU, writes clamp to the item's capacity.
	 */
	public static Supplier<DataComponentType<Long>> POUCH_ENERGY = () -> {
		throw new IllegalStateException("ModDataComponents.POUCH_ENERGY read before its loader bound it");
	};

	/** Battery Pouch stored items (MOD-052) — immutable stack list with weight math, absent = empty. */
	public static Supplier<DataComponentType<PouchContents>> POUCH_CONTENTS = () -> {
		throw new IllegalStateException("ModDataComponents.POUCH_CONTENTS read before its loader bound it");
	};

	/**
	 * Vacuum Capsule contents (MOD-063) — the single {@link Fluid} a filled capsule holds, stored as a
	 * {@link Holder Holder&lt;Fluid&gt;} (like vanilla {@code break_sound} carries a {@code Holder<SoundEvent>}).
	 * Read/written only via {@link dev.alaindustrial.item.ItemFluid}: absent = empty capsule. A registry
	 * holder's identity is stable per fluid, so two filled capsules of the same fluid share one component
	 * value and stack automatically (up to {@link dev.alaindustrial.item.FilledCapsuleItem#STACK_SIZE});
	 * different fluids never merge.
	 */
	public static Supplier<DataComponentType<Holder<Fluid>>> CAPSULE_FLUID = () -> {
		throw new IllegalStateException("ModDataComponents.CAPSULE_FLUID read before its loader bound it");
	};

	/** Portable fluid tank's atomic item-form contents: registry fluid + positive amount in mB. */
	public static Supplier<DataComponentType<FluidTankContents>> FLUID_TANK_CONTENTS = () -> {
		throw new IllegalStateException("ModDataComponents.FLUID_TANK_CONTENTS read before its loader bound it");
	};

	/**
	 * Teleporter privacy flag (MOD-091) carried on the station's item form, alongside the buffered EU
	 * in {@link #STORED_ENERGY}. Note what is <em>not</em> here: the station's owner. Ownership is
	 * re-assigned to whoever places the block (battery-box semantics — hand a charged station to a
	 * friend and it becomes theirs), so it lives only in the block entity's NBT and never travels on
	 * the item. Absent = the default, {@code private}.
	 */
	public static Supplier<DataComponentType<Boolean>> TELEPORTER_PRIVATE = () -> {
		throw new IllegalStateException("ModDataComponents.TELEPORTER_PRIVATE read before its loader bound it");
	};

	/**
	 * Owner of a Teleporter Remote (MOD-092) — the UUID it binds to on first use. Only the owner can
	 * bind or jump with it; a stolen remote is a paperweight. Unlike the station's owner (which is
	 * re-assigned on every placement), this one travels with the item, because the item IS the thing
	 * being owned.
	 */
	public static Supplier<DataComponentType<UUID>> TELEPORTER_OWNER = () -> {
		throw new IllegalStateException("ModDataComponents.TELEPORTER_OWNER read before its loader bound it");
	};

	/**
	 * The stations a remote knows (MOD-093) — a named list, replacing MOD-092's single point. Safe to
	 * swap outright rather than migrate: the remote has never been craftable or in the creative tab,
	 * so no player can be holding the old component.
	 */
	public static Supplier<DataComponentType<TeleportPoints>> TELEPORTER_POINTS = () -> {
		throw new IllegalStateException("ModDataComponents.TELEPORTER_POINTS read before its loader bound it");
	};

	/** Build the {@code teleporter_owner} type both loaders register (MOD-092). */
	public static DataComponentType<UUID> createTeleporterOwner() {
		return DataComponentType.<UUID>builder()
				.persistent(UUIDUtil.CODEC)
				.networkSynchronized(UUIDUtil.STREAM_CODEC)
				.build();
	}

	/** Build the {@code teleporter_points} type both loaders register (MOD-093). */
	public static DataComponentType<TeleportPoints> createTeleporterPoints() {
		return DataComponentType.<TeleportPoints>builder()
				.persistent(TeleportPoints.CODEC)
				.networkSynchronized(TeleportPoints.STREAM_CODEC)
				.build();
	}

	/** Build the {@code teleporter_private} type both loaders register (MOD-091). */
	public static DataComponentType<Boolean> createTeleporterPrivate() {
		return DataComponentType.<Boolean>builder()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				.build();
	}

	/** Build the {@code stored_energy} type both loaders register. */
	public static DataComponentType<Long> createStoredEnergy() {
		return DataComponentType.<Long>builder()
				.persistent(Codec.LONG)
				.networkSynchronized(ByteBufCodecs.VAR_LONG)
				.build();
	}

	/** Build the {@code network_scan} type both loaders register. */
	public static DataComponentType<NetworkScanData> createNetworkScan() {
		return DataComponentType.<NetworkScanData>builder()
				.persistent(NetworkScanData.CODEC)
				.networkSynchronized(NetworkScanData.STREAM_CODEC)
				.build();
	}

	/** Build the {@code network_analyzer_mode} type both loaders register (MOD-047). */
	public static DataComponentType<AnalyzerMode> createNetworkAnalyzerMode() {
		return DataComponentType.<AnalyzerMode>builder()
				.persistent(AnalyzerMode.CODEC)
				.networkSynchronized(AnalyzerMode.STREAM_CODEC)
				.build();
	}

	/**
	 * Build the {@code pouch_energy} type both loaders register (MOD-052).
	 *
	 * <p>{@code ignoreSwapAnimation()}: the passive drain rewrites this component once per second,
	 * and without the flag every write re-triggers the first-person re-equip animation — a held
	 * pouch visibly "bobs" each second (player-reported). The flag excludes this component from the
	 * hand renderer's stack comparison ({@code ItemStack.matchesIgnoringComponents}).
	 */
	public static DataComponentType<Long> createPouchEnergy() {
		return DataComponentType.<Long>builder()
				.persistent(Codec.LONG)
				.networkSynchronized(ByteBufCodecs.VAR_LONG)
				.ignoreSwapAnimation()
				.build();
	}

	/** Build the {@code pouch_contents} type both loaders register (MOD-052). */
	public static DataComponentType<PouchContents> createPouchContents() {
		return DataComponentType.<PouchContents>builder()
				.persistent(PouchContents.CODEC)
				.networkSynchronized(PouchContents.STREAM_CODEC)
				.build();
	}

	/**
	 * Build the {@code capsule_fluid} type both loaders register (MOD-063).
	 *
	 * <p>Codec/stream-codec follow the vanilla registry-holder recipe (verified against the 26.2
	 * sources — {@code Potion.CODEC} / {@code DataComponents.BREAK_SOUND}): the persistent side encodes
	 * the fluid by its registry id via {@link net.minecraft.core.Registry#holderByNameCodec()}, the
	 * network side via {@link ByteBufCodecs#holderRegistry}. {@code cacheEncoding()} matches vanilla and
	 * avoids re-encoding the (immutable) holder on every sync. Being persistent is also what lets the
	 * item model select on it (vanilla {@code minecraft:select} / {@code minecraft:component}).
	 */
	public static DataComponentType<Holder<Fluid>> createCapsuleFluid() {
		return DataComponentType.<Holder<Fluid>>builder()
				.persistent(BuiltInRegistries.FLUID.holderByNameCodec())
				.networkSynchronized(ByteBufCodecs.holderRegistry(Registries.FLUID))
				.cacheEncoding()
				.build();
	}

	/** Build the portable tank contents type (MOD-111). */
	public static DataComponentType<FluidTankContents> createFluidTankContents() {
		return DataComponentType.<FluidTankContents>builder()
				.persistent(FluidTankContents.CODEC)
				.networkSynchronized(FluidTankContents.STREAM_CODEC)
				.cacheEncoding()
				.build();
	}
}
