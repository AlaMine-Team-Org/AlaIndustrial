package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.registry.ModEffects;
import dev.alaindustrial.registry.ModSounds;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * L2 snapshot of the mod's content registries (MOD-699), checked on both loaders against the reviewed
 * reference in {@link RegistrySnapshot} and {@link RegistrySnapshotItems}.
 *
 * <p><b>What it replaces.</b> The composition of the registries — which blocks, items, block-entity →
 * block pairs, menus, fluids, sounds, data components, effects, particles and criteria exist — used to be
 * held by hand-kept counters in {@code docs/tools/content/loader_parity_check.py} and
 * {@code menu_screen_parity_check.py}. A counter cannot see one id replaced by another, and it reads the
 * source text, not what the loader actually registered. This scenario asks the live registry of the loader
 * it runs on and compares every line: a missing and an added id are both named.
 *
 * <p><b>One line per fact, sorted.</b> {@code block <id> <properties>} for every block, {@code item <id>
 * <properties>}, {@code block_entity <type>:<block>} for every block a type is valid for, and
 * {@code <registry> <id>} for the rest. Sorting makes a reordered manifest change nothing; the two orders
 * the manifest itself calls load-bearing are asserted separately.
 *
 * <p><b>A block's properties</b> (MOD-741; format in {@link RegistrySnapshotBlockLine}) are those of its
 * {@code BlockBehaviour.Properties} chain, so moving or tidying a chain in a domain file is a visible diff:
 * destroy time, whether a correct tool is needed for drops ({@code tool=}, from
 * {@code requiresCorrectToolForDrops()}), light, piston reaction, occlusion, sound, explosion resistance and
 * map colour on every line; the highest light of any state, friction, the speed, jump and bounce factors and
 * the lava, replaceable and liquid flags only where they differ. Read on the default state, except
 * {@code lightmax=}, which walks every state: the lit machines emit light only while they run. Random
 * ticking is pinned per state by {@link BlockPropsCharacterizationScenarios}, once for both lines, and is
 * deliberately not repeated here.
 *
 * <p><b>Two invariants no reference can hold</b>, checked first: those load-bearing registration orders,
 * and that every registered entry is held by one of the mod's handles ({@code ModContent},
 * {@code ModSounds}, …). Two entries binding the same slot leave one of them registered and unreachable;
 * the loser is named here.
 *
 * <p><b>A shipped id does not vanish silently.</b> The update command refuses to drop an id from the
 * reference unless {@link RegistrySnapshot#REMOVED} announces it with a task number; the command never writes
 * that line itself.
 *
 * <p><b>Updated only by the explicit command</b> in {@link RegistrySnapshot}'s javadoc — never by
 * {@code regen.py}, a hook, {@code new_machine.py} or a merge driver: a reference that regenerates itself
 * agrees with every change and checks nothing (the MOD-201 lesson).
 */
public final class RegistrySnapshotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RegistrySnapshotScenarios::registrySnapshotMatches, "registry_snapshot_matches")
						.fabricId("RegistrySnapshotGameTest", "registrySnapshotMatches").ticks(20, 40));

		private Roster() {}
	}

	private RegistrySnapshotScenarios() {}

	/** System property naming the DIRECTORY the explicit update command writes the two reference files into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.registrySnapshot.writeTo";

	/** How many differing lines of each kind a failure message spells out; the log carries the rest. */
	private static final int SHOWN = 40;

	/** Where a block's destroy speed is asked; the default state does not depend on the position. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/** Registries whose entries are plain ids, keyed by their section name in the reference. */
	private static final Map<String, Registry<?>> ID_SECTIONS = idSections();

	/** Registries whose every entry must be held by a handle, keyed by the section name used in messages. */
	private static final Map<String, Registry<?>> HANDLED = handledRegistries();

	/** Classes whose {@code public static Supplier} fields are the mod's handles. */
	private static final List<Class<?>> HANDLE_HOLDERS = List.of(ModContent.class, ModSounds.class,
			ModDataComponents.class, ModEffects.class, ModCriteria.class);

	/** {@code SoundType} constants by instance; a sound no constant holds is recorded as {@code other}. */
	private static final Map<SoundType, String> SOUND_NAMES = constantNames(SoundType.class);

	/** {@code MapColor} constants by instance; a colour no constant holds is recorded as {@code id<N>}. */
	private static final Map<MapColor, String> MAP_COLOR_NAMES = constantNames(MapColor.class);

	/**
	 * The live registries of this loader match the reviewed reference, line for line.
	 */
	public static void registrySnapshotMatches(GameTestHelper helper) {
		List<String> invariants = new ArrayList<>();
		checkLoadBearingOrder(invariants);
		checkEveryEntryIsHeld(invariants);
		if (!invariants.isEmpty()) {
			helper.fail("MOD-699: " + String.join("; ", invariants));
			return;
		}
		List<String> removedProblems = checkRemovedSection(RegistrySnapshot.REMOVED);
		if (!removedProblems.isEmpty()) {
			helper.fail("MOD-699: RegistrySnapshot.REMOVED — " + String.join("; ", removedProblems));
			return;
		}
		List<String> actual = capture(helper);
		List<String> expected = new ArrayList<>(RegistrySnapshot.LINES);
		expected.addAll(RegistrySnapshotItems.LINES);
		List<String> returned = removedButRegistered(RegistrySnapshot.REMOVED, actual);
		if (!returned.isEmpty()) {
			helper.fail("MOD-699: " + returned + " are listed in RegistrySnapshot.REMOVED but registered again — drop"
					+ " their REMOVED lines when an id comes back");
			return;
		}
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			List<String> unannounced = unannouncedRemovals(expected, actual, RegistrySnapshot.REMOVED);
			if (!unannounced.isEmpty()) {
				helper.fail("MOD-699: refusing to rewrite the registry snapshot — " + unannounced + " would disappear"
						+ " from it. An id that shipped lives in players' worlds; removing it needs a line"
						+ " \"<section> <id> — MOD-XXX <reason>\" in RegistrySnapshot.REMOVED first");
				return;
			}
			rewrite(helper, Path.of(writeTo), actual);
			return;
		}
		List<String> compared = RegistrySnapshot.PROPERTIES_CAPTURED ? actual : keysOnly(actual);
		String difference = difference(expected, compared);
		if (difference.isEmpty()) {
			if (!RegistrySnapshot.PROPERTIES_CAPTURED) {
				// The hand-derived seed holds the composition only; the properties are captured by the first run
				// of the update command. Log the capture so the reviewer of that run can compare it with this one.
				logCapture("composition matches the seed; block and item properties are not in the reference yet"
						+ " — run the update command in RegistrySnapshot's javadoc", actual);
			}
			helper.succeed();
			return;
		}
		logCapture("the registries no longer match the reference", actual);
		helper.fail("MOD-699: the registries of this loader no longer match RegistrySnapshot/RegistrySnapshotItems.\n"
				+ difference
				+ "If the change is intended, update the reference with the explicit command in RegistrySnapshot's"
				+ " javadoc and review the diff; the full capture is in the log.");
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Capture
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/** Every line of this loader's snapshot, sorted. */
	static List<String> capture(GameTestHelper helper) {
		Set<String> loaderItems = new TreeSet<>();
		for (ContentManifest.ItemDef def : ContentManifest.ITEMS) {
			if (def.factory() == null) {
				loaderItems.add(def.id());
			}
		}
		List<Identifier> blocks = modKeys(BuiltInRegistries.BLOCK);
		List<String> lines = new ArrayList<>();
		for (Identifier id : blocks) {
			lines.add(blockLine(helper, id));
		}
		for (Identifier id : modKeys(BuiltInRegistries.ITEM)) {
			lines.add(itemLine(id, loaderItems.contains(id.getPath())));
		}
		for (Identifier typeId : modKeys(BuiltInRegistries.BLOCK_ENTITY_TYPE)) {
			BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(typeId);
			for (Identifier blockId : blocks) {
				if (type.isValid(BuiltInRegistries.BLOCK.getValue(blockId).defaultBlockState())) {
					lines.add("block_entity " + typeId.getPath() + ":" + blockId.getPath());
				}
			}
		}
		for (Map.Entry<String, Registry<?>> section : ID_SECTIONS.entrySet()) {
			for (Identifier id : modKeys(section.getValue())) {
				lines.add(section.getKey() + " " + id.getPath());
			}
		}
		Collections.sort(lines);
		return lines;
	}

	// MOD-498: getLightEmission(), getSoundType(), ignitedByLava(), Block.getExplosionResistance() and
	// Block.getBounceRestitution() are deprecated only by NeoForge's patch, in favour of positional forms that do not
	// exist on Fabric; liquid() is deprecated in vanilla too, with no replacement. Each no-argument form returns the
	// very Properties field Fabric reads, which is what the reference pins — the method reads and does nothing else.
	@SuppressWarnings("deprecation")
	private static String blockLine(GameTestHelper helper, Identifier id) {
		Block block = BuiltInRegistries.BLOCK.getValue(id);
		BlockState state = block.defaultBlockState();
		int lightMax = 0;
		for (BlockState possible : block.getStateDefinition().getPossibleStates()) {
			lightMax = Math.max(lightMax, possible.getLightEmission());
		}
		return new RegistrySnapshotBlockLine(
				id.getPath(),
				state.getDestroySpeed(helper.getLevel(), helper.absolutePos(PROBE)),
				state.requiresCorrectToolForDrops(),
				state.getLightEmission(),
				lightMax,
				String.valueOf(state.getPistonPushReaction()),
				state.canOcclude(),
				SOUND_NAMES.getOrDefault(state.getSoundType(), "other"),
				block.getExplosionResistance(),
				mapColorName(block.defaultMapColor()),
				block.getFriction(),
				block.getSpeedFactor(),
				block.getJumpFactor(),
				block.getBounceRestitution(),
				state.ignitedByLava(),
				state.canBeReplaced(),
				state.liquid(),
				block.getLootTable().map(key -> key.identifier().toString()).orElse("none"))
				.toLine();
	}

	/** The constant's name, or {@code id<N>} for a colour no {@code MapColor} constant holds. */
	private static String mapColorName(MapColor color) {
		String name = MAP_COLOR_NAMES.get(color);
		return name != null ? name : "id" + color.id;
	}

	/**
	 * An item's line. The class of a {@code loaderItem} entry differs per loader by design (the forge hammer's
	 * crafting-remainder hook), so its shared class is recorded with a {@code +loader} mark instead —
	 * one reference then serves both lanes.
	 */
	private static String itemLine(Identifier id, boolean loaderClass) {
		Item item = BuiltInRegistries.ITEM.getValue(id);
		ItemStack stack = new ItemStack(item);
		String type = loaderClass ? simpleName(sharedClass(item.getClass())) + "+loader" : simpleName(item.getClass());
		return "item " + id.getPath()
				+ " class=" + type
				+ " stack=" + stack.getMaxStackSize()
				+ " damage=" + stack.getMaxDamage()
				+ " rarity=" + stack.get(DataComponents.RARITY)
				+ " fireproof=" + yesNo(stack.has(DataComponents.DAMAGE_RESISTANT));
	}

	/**
	 * The class both loaders share for a {@code loaderItem}: the nearest one up the hierarchy that is not a
	 * loader's own. A loader may register the shared class itself while the other registers a subclass of it
	 * (the electric hoe and shovel on the 26.2 line), so "the superclass" answers differently per lane; the
	 * loader classes are told apart by their {@code Fabric} / {@code NeoForge} name suffix.
	 */
	private static Class<?> sharedClass(Class<?> type) {
		Class<?> shared = type;
		while (isLoaderClass(shared) && shared.getSuperclass() != null) {
			shared = shared.getSuperclass();
		}
		return shared;
	}

	private static boolean isLoaderClass(Class<?> type) {
		String name = type.getSimpleName();
		return name.endsWith("Fabric") || name.endsWith("NeoForge");
	}

	/** The simple name, walking up past anonymous classes so the line never depends on their numbering. */
	private static String simpleName(Class<?> type) {
		Class<?> named = type;
		String suffix = "";
		while (named.getSimpleName().isEmpty() && named.getSuperclass() != null) {
			named = named.getSuperclass();
			suffix = "~";
		}
		return named.getSimpleName() + suffix;
	}

	/** This mod's ids in {@code registry}, sorted. */
	private static List<Identifier> modKeys(Registry<?> registry) {
		Map<String, Identifier> sorted = new TreeMap<>();
		for (Identifier id : registry.keySet()) {
			if (Industrialization.MOD_ID.equals(id.getNamespace())) {
				sorted.put(id.getPath(), id);
			}
		}
		return List.copyOf(sorted.values());
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Invariants no reference can hold
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * The two registration orders {@code ContentManifest} calls load-bearing: the wall torch reads the
	 * standing torch's loot table, and the filled capsule takes the empty one as its crafting remainder.
	 */
	private static void checkLoadBearingOrder(List<String> violations) {
		requireBefore(violations, "block", BuiltInRegistries.BLOCK, "enriched_uranium_torch", "enriched_uranium_wall_torch");
		requireBefore(violations, "item", BuiltInRegistries.ITEM, "vacuum_capsule", "filled_vacuum_capsule");
	}

	private static <T> void requireBefore(List<String> violations, String section, Registry<T> registry,
			String first, String then) {
		int index = 0;
		int firstAt = -1;
		int thenAt = -1;
		for (T value : registry) {
			Identifier key = registry.getKey(value);
			if (key != null && Industrialization.MOD_ID.equals(key.getNamespace())) {
				if (key.getPath().equals(first)) {
					firstAt = index;
				} else if (key.getPath().equals(then)) {
					thenAt = index;
				}
			}
			index++;
		}
		if (firstAt < 0 || thenAt < 0 || firstAt > thenAt) {
			violations.add(section + " " + first + " must be registered before " + section + " " + then
					+ " (positions " + firstAt + " and " + thenAt + ") — the later entry reads the earlier one while"
					+ " it is built; keep ContentManifest entries appended, not interleaved");
		}
	}

	/**
	 * Every entry this mod registered is the value of at least one handle. A duplicated bind slot registers
	 * both entries and leaves the first one reachable by nothing — {@code ModContent.verifyAllBound()} only
	 * sees the slot that was never written, not the entry that lost its slot.
	 */
	private static void checkEveryEntryIsHeld(List<String> violations) {
		Set<Object> held = Collections.newSetFromMap(new IdentityHashMap<>());
		List<String> unreadable = new ArrayList<>();
		for (Class<?> holder : HANDLE_HOLDERS) {
			for (Field field : holder.getDeclaredFields()) {
				int mods = field.getModifiers();
				if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || Supplier.class != field.getType()) {
					continue;
				}
				try {
					Object value = ((Supplier<?>) field.get(null)).get();
					held.add(value instanceof Holder<?> holderValue ? holderValue.value() : value);
				} catch (IllegalAccessException | RuntimeException e) {
					// Holds nothing: an entry that depended on it is reported below, with this as the likely cause.
					unreadable.add(holder.getSimpleName() + "." + field.getName() + " (" + e + ")");
				}
			}
		}
		for (Map.Entry<String, Registry<?>> section : HANDLED.entrySet()) {
			Registry<?> registry = section.getValue();
			for (Identifier id : modKeys(registry)) {
				if (!held.contains(registry.getValue(id))) {
					violations.add(section.getKey() + " " + id.getPath() + " is registered but no handle holds it — two"
							+ " manifest entries probably bind the same slot, and the other one overwrote it"
							+ (unreadable.isEmpty() ? "" : "; handles that could not be read: " + unreadable));
				}
			}
		}
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Removed ids (batch 4): a shipped id leaves the reference only with a task behind it
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/** One {@code REMOVED} entry: {@code <section> <id> — MOD-<n> <reason>}. */
	private static final Pattern REMOVED_ENTRY = Pattern.compile("[a-z_]+ [a-z0-9_.:]+ — MOD-\\d+ \\S.*");

	/** The {@code <section> <id>} an entry announces. */
	static String removedKey(String entry) {
		int dash = entry.indexOf(" — ");
		return dash < 0 ? entry : entry.substring(0, dash);
	}

	/**
	 * A {@code block_entity <type>:<block>} line is a pairing, not an id: a block moving to another type is a
	 * reviewed diff, not a removal. Every other section names an id that is written into players' worlds.
	 */
	private static boolean isShippedId(String key) {
		return !key.startsWith("block_entity ");
	}

	static List<String> checkRemovedSection(List<String> removed) {
		List<String> problems = new ArrayList<>();
		Set<String> seen = new TreeSet<>();
		for (String entry : removed) {
			if (!REMOVED_ENTRY.matcher(entry).matches()) {
				problems.add("\"" + entry + "\" is not \"<section> <id> — MOD-XXX <reason>\"");
			} else if (!seen.add(removedKey(entry))) {
				problems.add(removedKey(entry) + " is listed twice");
			}
		}
		return problems;
	}

	static List<String> removedButRegistered(List<String> removed, List<String> actual) {
		Set<String> now = new TreeSet<>(keysOnly(actual));
		List<String> back = new ArrayList<>();
		for (String entry : removed) {
			if (now.contains(removedKey(entry))) {
				back.add(removedKey(entry));
			}
		}
		return back;
	}

	/** Shipped ids of the reference that the capture no longer has and that no {@code REMOVED} line announces. */
	static List<String> unannouncedRemovals(List<String> expected, List<String> actual, List<String> removed) {
		Set<String> now = new TreeSet<>(keysOnly(actual));
		Set<String> announced = new TreeSet<>();
		for (String entry : removed) {
			announced.add(removedKey(entry));
		}
		List<String> out = new ArrayList<>();
		for (String line : expected) {
			String key = key(line);
			if (isShippedId(key) && !now.contains(key) && !announced.contains(key)) {
				out.add(key);
			}
		}
		return out;
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Comparison and the explicit update
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/** The first two tokens — {@code <section> <id>} — which identify a line whatever its properties say. */
	static String key(String line) {
		int second = line.indexOf(' ', line.indexOf(' ') + 1);
		return second < 0 ? line : line.substring(0, second);
	}

	private static List<String> keysOnly(List<String> lines) {
		List<String> keys = new ArrayList<>();
		for (String line : lines) {
			keys.add(key(line));
		}
		return keys;
	}

	/** Gone, new and changed lines, each named; empty when the two lists agree. */
	static String difference(List<String> expected, List<String> actual) {
		Map<String, String> before = byKey(expected);
		Map<String, String> after = byKey(actual);
		List<String> gone = new ArrayList<>();
		List<String> added = new ArrayList<>();
		List<String> changed = new ArrayList<>();
		for (Map.Entry<String, String> line : before.entrySet()) {
			String now = after.get(line.getKey());
			if (now == null) {
				gone.add(line.getValue());
			} else if (!now.equals(line.getValue())) {
				changed.add(line.getValue() + "  ->  " + now);
			}
		}
		for (Map.Entry<String, String> line : after.entrySet()) {
			if (!before.containsKey(line.getKey())) {
				added.add(line.getValue());
			}
		}
		StringBuilder out = new StringBuilder();
		describe(out, "gone (in the reference, not registered)", gone);
		describe(out, "new (registered, not in the reference)", added);
		describe(out, "changed", changed);
		return out.toString();
	}

	private static Map<String, String> byKey(List<String> lines) {
		Map<String, String> out = new TreeMap<>();
		for (String line : lines) {
			out.put(key(line), line);
		}
		return out;
	}

	private static void describe(StringBuilder out, String label, List<String> lines) {
		if (lines.isEmpty()) {
			return;
		}
		out.append("  ").append(lines.size()).append(' ').append(label).append(":\n");
		for (String line : lines.subList(0, Math.min(SHOWN, lines.size()))) {
			out.append("    ").append(line).append('\n');
		}
		if (lines.size() > SHOWN) {
			out.append("    … and ").append(lines.size() - SHOWN).append(" more\n");
		}
	}

	/** Writes both reference files for {@code actual}, then fails on purpose so the run is never taken for a check. */
	private static void rewrite(GameTestHelper helper, Path directory, List<String> actual) {
		List<String> items = new ArrayList<>();
		List<String> rest = new ArrayList<>();
		for (String line : actual) {
			(line.startsWith("item ") ? items : rest).add(line);
		}
		try {
			Files.writeString(directory.resolve("RegistrySnapshot.java"),
					RegistrySnapshotSource.main(rest, RegistrySnapshot.REMOVED), StandardCharsets.UTF_8);
			Files.writeString(directory.resolve("RegistrySnapshotItems.java"),
					RegistrySnapshotSource.items(items), StandardCharsets.UTF_8);
		} catch (IOException e) {
			helper.fail("could not write the registry snapshot into " + directory + ": " + e);
			return;
		}
		helper.fail("registry snapshot rewritten into " + directory + " (" + actual.size() + " lines) — review the"
				+ " diff, then run again without -D" + WRITE_TO_PROPERTY);
	}

	private static void logCapture(String why, List<String> actual) {
		Industrialization.LOGGER.info("MOD-699 registry snapshot: {} ({} lines)\n{}", why, actual.size(),
				String.join("\n", actual));
	}

	private static String yesNo(boolean value) {
		return value ? "yes" : "no";
	}

	private static Map<String, Registry<?>> idSections() {
		Map<String, Registry<?>> sections = new LinkedHashMap<>();
		sections.put("block_entity_type", BuiltInRegistries.BLOCK_ENTITY_TYPE);
		sections.put("menu", BuiltInRegistries.MENU);
		sections.put("fluid", BuiltInRegistries.FLUID);
		sections.put("sound_event", BuiltInRegistries.SOUND_EVENT);
		sections.put("data_component_type", BuiltInRegistries.DATA_COMPONENT_TYPE);
		sections.put("mob_effect", BuiltInRegistries.MOB_EFFECT);
		sections.put("particle_type", BuiltInRegistries.PARTICLE_TYPE);
		sections.put("trigger_type", BuiltInRegistries.TRIGGER_TYPES);
		return Collections.unmodifiableMap(sections);
	}

	/** Particles are left out on purpose: they are load-time constants with no bind slot (see ModParticles). */
	private static Map<String, Registry<?>> handledRegistries() {
		Map<String, Registry<?>> registries = new LinkedHashMap<>();
		registries.put("block", BuiltInRegistries.BLOCK);
		registries.put("item", BuiltInRegistries.ITEM);
		registries.put("block_entity_type", BuiltInRegistries.BLOCK_ENTITY_TYPE);
		registries.put("menu", BuiltInRegistries.MENU);
		registries.put("fluid", BuiltInRegistries.FLUID);
		registries.put("sound_event", BuiltInRegistries.SOUND_EVENT);
		registries.put("data_component_type", BuiltInRegistries.DATA_COMPONENT_TYPE);
		registries.put("mob_effect", BuiltInRegistries.MOB_EFFECT);
		registries.put("trigger_type", BuiltInRegistries.TRIGGER_TYPES);
		return Collections.unmodifiableMap(registries);
	}

	/**
	 * The names of {@code type}'s own {@code public static final} constants, by instance. Read by reflection so a
	 * constant the mod starts using is named the moment it is used, not after someone extends a hand-kept list;
	 * when two constants hold the same instance the lexicographically smaller name wins, so the line never depends
	 * on the order {@link Class#getFields()} happens to return.
	 */
	static <T> Map<T, String> constantNames(Class<T> type) {
		Map<T, String> names = new IdentityHashMap<>();
		for (Field field : type.getFields()) {
			int mods = field.getModifiers();
			if (!Modifier.isStatic(mods) || !Modifier.isFinal(mods) || field.getType() != type) {
				continue;
			}
			try {
				names.merge(type.cast(field.get(null)), field.getName(), (a, b) -> a.compareTo(b) <= 0 ? a : b);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("cannot read " + type.getSimpleName() + "." + field.getName(), e);
			}
		}
		return Collections.unmodifiableMap(names);
	}
}
