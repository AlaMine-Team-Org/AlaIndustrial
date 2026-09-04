package dev.alaindustrial.registry;

import com.mojang.serialization.Codec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.item.fluid.FluidTankContents;
import dev.alaindustrial.item.misc.MutationGrades;
import dev.alaindustrial.item.tool.NetworkScanData;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.item.energy.PouchContents;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.mutation.MutationGrade;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
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
 *
 * <p><b>Which components exist is decided once, here</b> (MOD-555): {@link #COMPONENTS} is the list both
 * loaders replay, so the registration mechanism is all that differs between them.
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
	public static final Identifier BLUEPRINT_PATTERN_ID = Industrialization.id("blueprint_pattern");
	public static final Identifier BLUEPRINT_RESULT_ID = Industrialization.id("blueprint_result");
	public static final Identifier BLUEPRINT_SUBSTITUTE_ID = Industrialization.id("blueprint_substitute");
	public static final Identifier CAPSULE_FLUID_ID = Industrialization.id("capsule_fluid");
	public static final Identifier TELEPORTER_PRIVATE_ID = Industrialization.id("teleporter_private");
	public static final Identifier TELEPORTER_RTP_MODULE_ID = Industrialization.id("teleporter_rtp_module");
	public static final Identifier TELEPORTER_OWNER_ID = Industrialization.id("teleporter_owner");
	public static final Identifier TELEPORTER_POINTS_ID = Industrialization.id("teleporter_points");
	public static final Identifier FLUID_TANK_CONTENTS_ID = Industrialization.id("fluid_tank_contents");
	public static final Identifier DISTILLATION_COLUMN_CONTENTS_ID = Industrialization.id("distillation_column_contents");
	public static final Identifier MAGNET_ENABLED_ID = Industrialization.id("magnet_enabled");
	public static final Identifier STEP_ASSIST_ENABLED_ID = Industrialization.id("step_assist_enabled");
	public static final Identifier SABER_ACTIVE_ID = Industrialization.id("saber_active");
	public static final Identifier SOUL_VESSEL_KILLS_ID = Industrialization.id("soul_vessel_kills");
	public static final Identifier REPAIR_COUNT_ID = Industrialization.id("repair_count");
	public static final Identifier GEIGER_ALERT_ID = Industrialization.id("geiger_alert");

	/** Rarity grade rolled by the incubator on a successful mutation (MOD-118). */
	public static final Identifier MUTATION_GRADE_ID = Industrialization.id("mutation_grade");

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
	 * {@link dev.alaindustrial.item.energy.ItemEnergy}: absent = 0 EU, writes clamp to the item's capacity.
	 */
	public static Supplier<DataComponentType<Long>> POUCH_ENERGY = () -> {
		throw new IllegalStateException("ModDataComponents.POUCH_ENERGY read before its loader bound it");
	};

	/** Battery Pouch stored items (MOD-052) — immutable stack list with weight math, absent = empty. */
	public static Supplier<DataComponentType<PouchContents>> POUCH_CONTENTS = () -> {
		throw new IllegalStateException("ModDataComponents.POUCH_CONTENTS read before its loader bound it");
	};

	/** Assembly Blueprint 3×3 layout (MOD-275) — absent or all-empty means a blank blueprint. */
	public static Supplier<DataComponentType<BlueprintPattern>> BLUEPRINT_PATTERN = () -> {
		throw new IllegalStateException("ModDataComponents.BLUEPRINT_PATTERN read before its loader bound it");
	};

	/**
	 * What a recorded Assembly Blueprint makes, cached on the stack at write time (MOD-275).
	 *
	 * <p><b>A display cache, and nothing else.</b> The machine never reads it: every operation still
	 * re-solves {@link BlueprintPattern} against {@code RecipeType.CRAFTING}, so a datapack that changes
	 * or drops the recipe changes or stops production exactly as before. This component only answers
	 * "what does this blueprint make" for the tooltip and the window.
	 *
	 * <p><b>Why it has to be cached rather than resolved on demand.</b> Answering that question means
	 * solving a crafting recipe, and the client cannot: {@code Level.recipeAccess()} hands back a
	 * {@link net.minecraft.world.item.crafting.RecipeAccess}, whose whole 26.2 surface is
	 * {@code propertySet(…)} and {@code stonecutterRecipes()} — no crafting lookup at all (verified with
	 * {@code javap}; {@code RecipeManager.getRecipeFor} exists only on the server's own
	 * {@code ServerLevel.recipeAccess()}). {@code Item.TooltipContext} offers only
	 * {@code registries()}, a {@code HolderLookup.Provider}, which knows registries and not recipes. So
	 * the server writes the answer down once, at the moment it resolves the recipe for the "Write"
	 * button, and the client reads it back off the synced stack. Absent (a blueprint written before this
	 * existed) simply falls back to the generic "recipe recorded" line.
	 *
	 * <p><b>Stored as an {@link ItemStackTemplate}, not an {@link ItemStack}, and that is not a detail.</b>
	 * A data-component value must be immutable and must implement {@code equals}/{@code hashCode};
	 * {@code ItemStack} is mutable and declares neither, so it inherits identity equality. Fabric lets
	 * that through silently — NeoForge asserts it at runtime and every test touching such a component
	 * dies with "Data components must implement equals and hashCode". {@code ItemStackTemplate} is
	 * vanilla's immutable record for exactly this ({@code Holder<Item>} + count +
	 * {@code DataComponentPatch}, with codecs of its own), so the value compares by what it is rather
	 * than by which object it is, and it keeps the result's own components — a crafted firework or
	 * banner still names itself correctly.
	 *
	 * <p>Absent means "no result known"; nothing ever stores an empty one, and
	 * {@link dev.alaindustrial.item.assembler.AssemblyBlueprintItem#resultOf} turns it back into a fresh
	 * {@link ItemStack} for the tooltip and the window.
	 */
	public static Supplier<DataComponentType<ItemStackTemplate>> BLUEPRINT_RESULT = () -> {
		throw new IllegalStateException("ModDataComponents.BLUEPRINT_RESULT read before its loader bound it");
	};

	/**
	 * Whether this blueprint may substitute ingredients (MOD-275). Absent means <b>off</b>, like
	 * {@link #STEP_ASSIST_ENABLED} and unlike {@link #MAGNET_ENABLED}, and the default is off on purpose:
	 * a plain {@code Ingredient} compares items and ignores components, so a substituting blueprint will
	 * happily reach for an enchanted book or a half-worn tool where the player recorded a pristine one.
	 * The player opts in per blueprint, from the machine window.
	 *
	 * <p>On the stack rather than on the machine so the setting travels with the recipe: copy the
	 * blueprint to another assembler and it still substitutes, which is the behaviour a player who set it
	 * expects.
	 */
	public static Supplier<DataComponentType<Boolean>> BLUEPRINT_SUBSTITUTE = () -> {
		throw new IllegalStateException("ModDataComponents.BLUEPRINT_SUBSTITUTE read before its loader bound it");
	};

	/**
	 * Vacuum Capsule contents (MOD-063) — the single {@link Fluid} a filled capsule holds, stored as a
	 * {@link Holder Holder&lt;Fluid&gt;} (like vanilla {@code break_sound} carries a {@code Holder<SoundEvent>}).
	 * Read/written only via {@link dev.alaindustrial.item.fluid.ItemFluid}: absent = empty capsule. A registry
	 * holder's identity is stable per fluid, so two filled capsules of the same fluid share one component
	 * value and stack automatically (up to {@link dev.alaindustrial.item.fluid.FilledCapsuleItem#STACK_SIZE});
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
	 * The Distillation Column's three tanks on its item form (MOD-251) — the tower drops with its
	 * fluids preserved, the portable tank's mechanism generalized to three positional slots.
	 */
	public static Supplier<DataComponentType<dev.alaindustrial.item.fluid.DistillationColumnContents>> DISTILLATION_COLUMN_CONTENTS = () -> {
		throw new IllegalStateException("ModDataComponents.DISTILLATION_COLUMN_CONTENTS read before its loader bound it");
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
	 * Whether a broken station carries its fitted Random Jump Chip (MOD-116) into its item form.
	 *
	 * <p>Present for the same reason {@link #STORED_ENERGY} is: the upgrade cost the player a chip
	 * built out of the mod's deepest crafting chain, and losing it to a misplaced pickaxe would make
	 * moving a base a punishment. Absent = the default, no module.
	 */
	public static Supplier<DataComponentType<Boolean>> TELEPORTER_RTP_MODULE = () -> {
		throw new IllegalStateException("ModDataComponents.TELEPORTER_RTP_MODULE read before its loader bound it");
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

	/**
	 * Electromagnet on/off flag (MOD-132), toggled by shift-right-click. Absent = on, so a crafted-fresh
	 * magnet works out of the box and a switched-on magnet stays component-identical to a fresh one;
	 * disabling stores {@code false}. Read/written only via {@link dev.alaindustrial.item.tool.MagnetItem}.
	 */
	public static Supplier<DataComponentType<Boolean>> MAGNET_ENABLED = () -> {
		throw new IllegalStateException("ModDataComponents.MAGNET_ENABLED read before its loader bound it");
	};

	/** Build the {@code magnet_enabled} type both loaders register (MOD-132). */
	public static DataComponentType<Boolean> createMagnetEnabled() {
		return DataComponentType.<Boolean>builder()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				.build();
	}

	/**
	 * Soul Vessel kill counter (MOD-278): personal hostile-mob kills accumulated on the stack. Absent
	 * = 0, so a crafted-fresh vessel stays component-identical and stackable with other empty ones;
	 * the first counted kill writes the component and the vessel stops stacking from then on
	 * (different components never merge — by design, each vessel keeps its own memory). Read/written
	 * only via {@link dev.alaindustrial.item.misc.SoulVesselItem}.
	 */
	public static Supplier<DataComponentType<Integer>> SOUL_VESSEL_KILLS = () -> {
		throw new IllegalStateException("ModDataComponents.SOUL_VESSEL_KILLS read before its loader bound it");
	};

	/** Build the {@code soul_vessel_kills} type both loaders register (MOD-278). */
	public static DataComponentType<Integer> createSoulVesselKills() {
		return DataComponentType.<Integer>builder()
				.persistent(Codec.INT)
				.networkSynchronized(ByteBufCodecs.VAR_INT)
				.build();
	}

	/**
	 * How many times this rotor/wheel has been through the repair bench (MOD-384). Absent = 0, so a
	 * freshly crafted component stays component-identical to any other and the tooltip stays clean until
	 * the first repair. Read/written only via
	 * {@link dev.alaindustrial.item.misc.DurableComponentItem}.
	 *
	 * <p>It has to live on the stack rather than be derived from the lowered {@code max_damage}: the two
	 * are set together, but only this one survives a config change to the decay step, and only this one
	 * can answer "how many repairs are left" without assuming the step never moved.
	 *
	 * <p>Network-synchronised because the tooltip is drawn client-side, and persistent because the whole
	 * point is that the limit survives a re-login.
	 */
	public static Supplier<DataComponentType<Integer>> REPAIR_COUNT = () -> {
		throw new IllegalStateException("ModDataComponents.REPAIR_COUNT read before its loader bound it");
	};

	/** Build the {@code repair_count} type both loaders register (MOD-384). */
	public static DataComponentType<Integer> createRepairCount() {
		return DataComponentType.<Integer>builder()
				.persistent(Codec.INT)
				.networkSynchronized(ByteBufCodecs.VAR_INT)
				.build();
	}

	/**
	 * Whether the wearer has switched the Fluxweave leggings' step assist on (MOD-127). Absent means
	 * <b>off</b> — the inverse of {@link #MAGNET_ENABLED}, because auto-stepping changes how movement
	 * feels and not everyone wants it, so it stays off until the player asks for it.
	 *
	 * <p>The flag lives on the stack rather than on the player: persistence is then free, it travels
	 * with the trousers, and neither loader needs a per-player storage mechanism. Read/written only via
	 * {@link dev.alaindustrial.item.wearable.FluxweaveArmorItem}.
	 */
	public static Supplier<DataComponentType<Boolean>> STEP_ASSIST_ENABLED = () -> {
		throw new IllegalStateException("ModDataComponents.STEP_ASSIST_ENABLED read before its loader bound it");
	};

	/** Build the {@code step_assist_enabled} type both loaders register (MOD-127). */
	public static DataComponentType<Boolean> createStepAssistEnabled() {
		return DataComponentType.<Boolean>builder()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				.build();
	}

	/**
	 * Whether the Electric Saber's blade is switched on (MOD-149). Absent means <b>on</b>, like
	 * {@link #MAGNET_ENABLED} and unlike {@link #STEP_ASSIST_ENABLED}: a freshly crafted saber is a
	 * working weapon, and switching it off — the deliberate act — is what writes {@code false}. Two new
	 * sabers therefore stay component-identical and stack in a chest slot as one.
	 *
	 * <p>The flag lives on the stack, so it survives world reloads and travels with the weapon.
	 * Read/written only via {@link dev.alaindustrial.item.tool.ElectricSaberItem}, which rebuilds the
	 * attribute set on every write.
	 */
	public static Supplier<DataComponentType<Boolean>> SABER_ACTIVE = () -> {
		throw new IllegalStateException("ModDataComponents.SABER_ACTIVE read before its loader bound it");
	};

	/**
	 * Present on a Geiger counter whose lamp is lit red — that is, one currently hearing a HAZARD
	 * (MOD-475). Ore in the rock deliberately leaves the lamp green: the ore signal means "safe, dig
	 * here", and a red lamp has to keep meaning danger to be read at a glance.
	 *
	 * <p><b>Absent, not false, when the lamp is dark.</b> A counter that has never seen radiation must
	 * stay component-identical to a freshly crafted one, or a stack of them would refuse to merge and
	 * the item model would need a third case for "written false".
	 *
	 * <p>Written by the sweep and ONLY when the state flips, never once a tick: the value changes at
	 * most as often as a player walks in or out of a field, while the sweep runs every second and the
	 * click loop twenty times a second.
	 */
	public static Supplier<DataComponentType<Boolean>> GEIGER_ALERT = () -> {
		throw new IllegalStateException("ModDataComponents.GEIGER_ALERT read before its loader bound it");
	};

	/** Build the {@code geiger_alert} type both loaders register (MOD-475). */
	public static DataComponentType<Boolean> createGeigerAlert() {
		return DataComponentType.<Boolean>builder()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				.build();
	}

	/** Build the {@code saber_active} type both loaders register (MOD-149). */
	public static DataComponentType<Boolean> createSaberActive() {
		return DataComponentType.<Boolean>builder()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				.build();
	}

	/**
	 * Rarity grade an item carries after a successful incubator mutation (MOD-118). Absent means the
	 * ordinary outcome, so a common result stays component-identical to a plain vanilla item and keeps
	 * stacking with it. Read/written only via {@link dev.alaindustrial.item.misc.MutationGrades}, which also
	 * keeps the vanilla rarity component in sync.
	 */
	public static Supplier<DataComponentType<MutationGrade>> MUTATION_GRADE = () -> {
		throw new IllegalStateException("ModDataComponents.MUTATION_GRADE read before its loader bound it");
	};

	/** Build the {@code mutation_grade} type both loaders register (MOD-118). */
	public static DataComponentType<MutationGrade> createMutationGrade() {
		return DataComponentType.<MutationGrade>builder()
				.persistent(MutationGrades.CODEC)
				.networkSynchronized(MutationGrades.STREAM_CODEC)
				.build();
	}

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

	/** Build the {@code teleporter_rtp_module} type both loaders register (MOD-116). */
	public static DataComponentType<Boolean> createTeleporterRtpModule() {
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
	/** Build the {@code blueprint_pattern} type both loaders register (MOD-275). */
	public static DataComponentType<BlueprintPattern> createBlueprintPattern() {
		return DataComponentType.<BlueprintPattern>builder()
				.persistent(BlueprintPattern.CODEC)
				.networkSynchronized(BlueprintPattern.STREAM_CODEC)
				.build();
	}

	/** Build the {@code blueprint_substitute} type both loaders register (MOD-275). */
	public static DataComponentType<Boolean> createBlueprintSubstitute() {
		return DataComponentType.<Boolean>builder()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				.build();
	}

	/** Build the {@code blueprint_result} type both loaders register (MOD-275). */
	public static DataComponentType<ItemStackTemplate> createBlueprintResult() {
		return DataComponentType.<ItemStackTemplate>builder()
				.persistent(ItemStackTemplate.CODEC)
				.networkSynchronized(ItemStackTemplate.STREAM_CODEC)
				.build();
	}

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

	/** Build the distillation column contents type (MOD-251). */
	public static DataComponentType<dev.alaindustrial.item.fluid.DistillationColumnContents> createDistillationColumnContents() {
		return DataComponentType.<dev.alaindustrial.item.fluid.DistillationColumnContents>builder()
				.persistent(dev.alaindustrial.item.fluid.DistillationColumnContents.CODEC)
				.networkSynchronized(dev.alaindustrial.item.fluid.DistillationColumnContents.STREAM_CODEC)
				.cacheEncoding()
				.build();
	}

	/**
	 * One data-component type: its registry id, how to build it, and where to publish the registered
	 * result (MOD-555).
	 *
	 * <p>The id is the {@code Identifier} constant declared at the top rather than a bare path string,
	 * because these ids have readers outside registration — {@code AssemblerScenarios} checks the item
	 * model of the Assembly Blueprint against {@link #BLUEPRINT_RESULT_ID}. NeoForge takes the path back
	 * off it for {@code DeferredRegister}; the namespace is ours on both loaders either way.
	 *
	 * @param id      the registry id constant above
	 * @param factory builds the component type the loader registers
	 * @param bind    publishes the registered type into its handle above
	 */
	public record ComponentDef<T>(Identifier id, Supplier<DataComponentType<T>> factory,
			Consumer<Supplier<DataComponentType<T>>> bind) {
	}

	/**
	 * Every data component, in one shared registration order (MOD-555). Both loaders replay this list.
	 *
	 * <p>Each entry used to be written three times — the handle and factory here, an eager registration
	 * line in {@code IndustrializationFabric}, and a {@code DeferredHolder} field plus a binding line in
	 * {@code ModDataComponentsNeoForge}. Two of the three were in loader files, so a component added on one
	 * loader and forgotten on the other compiled and shipped: on the loader that missed it, the handle
	 * threw at the first {@code .get()}, mid-gameplay, on whatever item happened to use it first.
	 *
	 * <p>Order is not load-bearing (no entry reads another); keep new entries appended.
	 */
	public static final List<ComponentDef<?>> COMPONENTS = List.of(
			new ComponentDef<>(STORED_ENERGY_ID, ModDataComponents::createStoredEnergy, c -> STORED_ENERGY = c),
			new ComponentDef<>(NETWORK_SCAN_ID, ModDataComponents::createNetworkScan, c -> NETWORK_SCAN = c),
			new ComponentDef<>(NETWORK_ANALYZER_MODE_ID, ModDataComponents::createNetworkAnalyzerMode, c -> NETWORK_ANALYZER_MODE = c),
			new ComponentDef<>(POUCH_ENERGY_ID, ModDataComponents::createPouchEnergy, c -> POUCH_ENERGY = c),
			new ComponentDef<>(POUCH_CONTENTS_ID, ModDataComponents::createPouchContents, c -> POUCH_CONTENTS = c),
			new ComponentDef<>(BLUEPRINT_PATTERN_ID, ModDataComponents::createBlueprintPattern, c -> BLUEPRINT_PATTERN = c),
			new ComponentDef<>(BLUEPRINT_RESULT_ID, ModDataComponents::createBlueprintResult, c -> BLUEPRINT_RESULT = c),
			new ComponentDef<>(BLUEPRINT_SUBSTITUTE_ID, ModDataComponents::createBlueprintSubstitute, c -> BLUEPRINT_SUBSTITUTE = c),
			new ComponentDef<>(CAPSULE_FLUID_ID, ModDataComponents::createCapsuleFluid, c -> CAPSULE_FLUID = c),
			new ComponentDef<>(FLUID_TANK_CONTENTS_ID, ModDataComponents::createFluidTankContents, c -> FLUID_TANK_CONTENTS = c),
			new ComponentDef<>(DISTILLATION_COLUMN_CONTENTS_ID, ModDataComponents::createDistillationColumnContents, c -> DISTILLATION_COLUMN_CONTENTS = c),
			new ComponentDef<>(TELEPORTER_PRIVATE_ID, ModDataComponents::createTeleporterPrivate, c -> TELEPORTER_PRIVATE = c),
			new ComponentDef<>(TELEPORTER_RTP_MODULE_ID, ModDataComponents::createTeleporterRtpModule, c -> TELEPORTER_RTP_MODULE = c),
			new ComponentDef<>(MAGNET_ENABLED_ID, ModDataComponents::createMagnetEnabled, c -> MAGNET_ENABLED = c),
			new ComponentDef<>(SOUL_VESSEL_KILLS_ID, ModDataComponents::createSoulVesselKills, c -> SOUL_VESSEL_KILLS = c),
			new ComponentDef<>(REPAIR_COUNT_ID, ModDataComponents::createRepairCount, c -> REPAIR_COUNT = c),
			new ComponentDef<>(STEP_ASSIST_ENABLED_ID, ModDataComponents::createStepAssistEnabled, c -> STEP_ASSIST_ENABLED = c),
			new ComponentDef<>(SABER_ACTIVE_ID, ModDataComponents::createSaberActive, c -> SABER_ACTIVE = c),
			new ComponentDef<>(GEIGER_ALERT_ID, ModDataComponents::createGeigerAlert, c -> GEIGER_ALERT = c),
			new ComponentDef<>(MUTATION_GRADE_ID, ModDataComponents::createMutationGrade, c -> MUTATION_GRADE = c),
			new ComponentDef<>(TELEPORTER_OWNER_ID, ModDataComponents::createTeleporterOwner, c -> TELEPORTER_OWNER = c),
			new ComponentDef<>(TELEPORTER_POINTS_ID, ModDataComponents::createTeleporterPoints, c -> TELEPORTER_POINTS = c));
}
