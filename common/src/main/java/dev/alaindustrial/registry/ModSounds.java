package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;

/**
 * Every sound event the mod registers, declared once for both loaders (MOD-022 facade, MOD-555).
 *
 * <p>NeoForge freezes the vanilla {@code SOUND_EVENT} registry before mod construction, so a direct
 * {@code Registry.register} (fine on Fabric) throws {@code Registry is already frozen} there. The
 * registration MECHANISM therefore has to differ per loader — Fabric registers eagerly during init,
 * NeoForge queues on a {@code DeferredRegister} — but nothing else does: {@link #SOUNDS} says which
 * events exist, under which id, with which audible range, and into which handle the result is bound.
 * Each loader replays that one list in a single loop.
 *
 * <p><b>Before MOD-555 each event was written three times</b> — an id constant plus a {@code create*}
 * factory here, an eager registration line in {@code IndustrializationFabric}, and a
 * {@code DeferredHolder} field plus a binding line in {@code ModSoundsNeoForge}. Two of the three lived
 * in loader files, so a sound added to one loader and forgotten on the other compiled, tested green and
 * went silent for half the players. No compiler and no test could have said so.
 *
 * <p><b>The handles below are what content code reads</b> ({@code ModSounds.MACERATOR_GRIND.get()}); the
 * replaying loader binds them. Reading one before its loader has run throws with the handle's name
 * rather than returning null — see {@link #unbound}.
 */
public final class ModSounds {

	/**
	 * One sound event: its registry path, how to build it, and where to publish the registered result.
	 *
	 * <p>The factory takes no argument and closes over the id, because the two loaders hand it different
	 * things — Fabric calls it and registers the result itself, NeoForge passes it straight to
	 * {@code DeferredRegister.register(String, Supplier)} — while the {@code SoundEvent} carries its own
	 * identifier either way.
	 *
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory builds the event instance the loader registers
	 * @param bind    publishes the registered event into its handle above
	 */
	public record SoundDef(String id, Supplier<SoundEvent> factory, Consumer<Supplier<SoundEvent>> bind) {
	}

	/**
	 * A sound whose audible radius scales with its volume, the way vanilla machine sounds do. The
	 * default: use it unless there is a reason for a fixed range, and write that reason down.
	 */
	private static SoundDef variableRange(String id, Consumer<Supplier<SoundEvent>> bind) {
		return new SoundDef(id, () -> SoundEvent.createVariableRangeEvent(Industrialization.id(id)), bind);
	}

	/** A sound audible out to a fixed radius regardless of volume. */
	private static SoundDef fixedRange(String id, float range, Consumer<Supplier<SoundEvent>> bind) {
		return new SoundDef(id, () -> SoundEvent.createFixedRangeEvent(Industrialization.id(id), range), bind);
	}

	/** What a handle holds until its loader binds it: a loud failure, never a silent NPE. */
	private static Supplier<SoundEvent> unbound(String handle) {
		return () -> {
			throw new IllegalStateException("ModSounds." + handle + " read before its loader bound it");
		};
	}

	public static Supplier<SoundEvent> MACERATOR_GRIND = unbound("MACERATOR_GRIND");
	public static Supplier<SoundEvent> GENERATOR_HUM = unbound("GENERATOR_HUM");
	public static Supplier<SoundEvent> ELECTRIC_FURNACE_HUM = unbound("ELECTRIC_FURNACE_HUM");
	public static Supplier<SoundEvent> SOLAR_PANEL_HUM = unbound("SOLAR_PANEL_HUM");
	public static Supplier<SoundEvent> IRON_CHEST_OPEN = unbound("IRON_CHEST_OPEN");
	public static Supplier<SoundEvent> IRON_CHEST_CLOSE = unbound("IRON_CHEST_CLOSE");
	public static Supplier<SoundEvent> SCYTHE_SWING = unbound("SCYTHE_SWING");
	public static Supplier<SoundEvent> EXTRACTOR_HUM = unbound("EXTRACTOR_HUM");
	public static Supplier<SoundEvent> WATER_MILL_HUM = unbound("WATER_MILL_HUM");
	public static Supplier<SoundEvent> WIND_MILL_HUM = unbound("WIND_MILL_HUM");
	public static Supplier<SoundEvent> COMPRESSOR_HUM = unbound("COMPRESSOR_HUM");
	public static Supplier<SoundEvent> GARDEN_DRONE_FLY = unbound("GARDEN_DRONE_FLY");
	public static Supplier<SoundEvent> PUMP_HUM = unbound("PUMP_HUM");
	public static Supplier<SoundEvent> CANNING_MACHINE_HUM = unbound("CANNING_MACHINE_HUM");
	public static Supplier<SoundEvent> GALVANIC_BATH_HUM = unbound("GALVANIC_BATH_HUM");
	public static Supplier<SoundEvent> SAWMILL_HUM = unbound("SAWMILL_HUM");
	public static Supplier<SoundEvent> POLYMERIZER_HUM = unbound("POLYMERIZER_HUM");
	public static Supplier<SoundEvent> CHARGE_PAD_HUM = unbound("CHARGE_PAD_HUM");
	public static Supplier<SoundEvent> ENERGY_CONDENSER_HUM = unbound("ENERGY_CONDENSER_HUM");
	public static Supplier<SoundEvent> COMPONENT_REPAIR_BENCH_HUM = unbound("COMPONENT_REPAIR_BENCH_HUM");
	public static Supplier<SoundEvent> INCUBATOR_HUM = unbound("INCUBATOR_HUM");
	public static Supplier<SoundEvent> REACTOR_HUM = unbound("REACTOR_HUM");
	public static Supplier<SoundEvent> REACTOR_ALARM = unbound("REACTOR_ALARM");
	public static Supplier<SoundEvent> REACTOR_SPINDOWN = unbound("REACTOR_SPINDOWN");
	public static Supplier<SoundEvent> REACTOR_DOOR_OPEN = unbound("REACTOR_DOOR_OPEN");
	public static Supplier<SoundEvent> REACTOR_DOOR_CLOSE = unbound("REACTOR_DOOR_CLOSE");

	/**
	 * Every sound event, in one shared registration order. Both loaders replay this list; see
	 * {@link SoundDef}. Order is not load-bearing (no entry reads another), but keep new entries
	 * appended so a diff shows what was added rather than where it was inserted.
	 */
	public static final List<SoundDef> SOUNDS = List.of(
			variableRange("macerator_grind", s -> MACERATOR_GRIND = s),
			variableRange("generator_hum", s -> GENERATOR_HUM = s),
			// The id is `electric_furnace`, not `electric_furnace_hum`: it predates the naming the later
			// machine loops follow, and renaming it would break every pack that already references it.
			variableRange("electric_furnace", s -> ELECTRIC_FURNACE_HUM = s),
			// Fixed 10 blocks rather than the generator's variable range: solar farms place many sources
			// close together and each stacks into the others, so one panel is tuned shorter-range than a
			// lone generator to keep a farm from becoming a wall of sound. Loudness is the block's humVolume().
			fixedRange("solar_panel_hum", 10.0f, s -> SOLAR_PANEL_HUM = s),
			variableRange("iron_chest_open", s -> IRON_CHEST_OPEN = s),
			variableRange("iron_chest_close", s -> IRON_CHEST_CLOSE = s),
			// MOD-068 — played once per successful AOE clear, not once per broken crop.
			variableRange("scythe_swing", s -> SCYTHE_SWING = s),
			// MOD-143 — a single lit machine each (pattern A).
			variableRange("extractor_hum", s -> EXTRACTOR_HUM = s),
			// Fixed 12 blocks, same reasoning as the solar panel: players line water mills up in rows
			// along a channel, and wind farms stack rotors just as tightly.
			fixedRange("water_mill_hum", 12.0f, s -> WATER_MILL_HUM = s),
			fixedRange("wind_mill_hum", 12.0f, s -> WIND_MILL_HUM = s),
			variableRange("compressor_hum", s -> COMPRESSOR_HUM = s),
			// MOD-329 — fixed 8 blocks, the shortest loop in the mod and deliberately so: this is the only
			// sound whose source MOVES, and it flies to the player rather than waiting to be walked up to.
			// A wider radius would leave a drone crossing the far side of its own farm audible indoors.
			fixedRange("garden_drone_fly", 8.0f, s -> GARDEN_DRONE_FLY = s),
			variableRange("pump_hum", s -> PUMP_HUM = s),
			variableRange("canning_machine_hum", s -> CANNING_MACHINE_HUM = s),
			variableRange("galvanic_bath_hum", s -> GALVANIC_BATH_HUM = s),
			// MOD-447 — the second wave of machine loops.
			variableRange("sawmill_hum", s -> SAWMILL_HUM = s),
			variableRange("polymerizer_hum", s -> POLYMERIZER_HUM = s),
			// The charging station runs on ChargePadState.CHARGING rather than `lit` (pattern C).
			variableRange("charge_pad_hum", s -> CHARGE_PAD_HUM = s),
			variableRange("energy_condenser_hum", s -> ENERGY_CONDENSER_HUM = s),
			// Soft rhythmic hammer taps with a metallic ring, playing only while a repair is running.
			variableRange("component_repair_bench_hum", s -> COMPONENT_REPAIR_BENCH_HUM = s),
			// The mutation chamber's nutrient bath: a warm wet gurgle under the dome, chosen over the
			// obvious Geiger ticking so the machine reads as growing something rather than as a hazard —
			// the irradiation is the method here, not the point.
			variableRange("incubator_hum", s -> INCUBATOR_HUM = s),
			// MOD-472 — the reactor's core drone, played per VOICED COLUMN rather than once at the
			// controller: the noise belongs to the fuel racks standing on the floor, and a room the player
			// walks around should sound like a hall, not like a panel on the wall. The controller picks
			// which columns are voiced (ReactorControllerBlockEntity) so a packed room cannot stack dozens.
			//
			// The variable range is COSMETIC here: SoundEvent#getRange is read only by
			// ServerLevel.playSeededSound, which decides who receives the packet, and this loop is created
			// client-side by MachineHumClientHook — so no range from here ever reaches it. The audible
			// distance comes from `attenuation_distance` in sounds.json (absent, so vanilla's 16) and the
			// loudness from the block's humVolume. A fixed range here would look like a knob and do nothing.
			variableRange("reactor_hum", s -> REACTOR_HUM = s),
			// The overheat warning, and the one sound that has to carry twice as far as anything else —
			// which takes TWO settings, not one. The fixed 32 decides who is sent the packet at all;
			// `attenuation_distance: 32` in sounds.json decides how far it is then audible on the client
			// (that key defaults to 16, and no other entry in this mod sets it). Setting one without the
			// other gives an alarm delivered to players who cannot hear it. The two must stay in step: an
			// alarm carrying no further than a machine hum fails at its one job — telling a player who is
			// somewhere else that the reactor needs them.
			fixedRange("reactor_alarm", 32.0f, s -> REACTOR_ALARM = s),
			// It reports, it does not warn — so no extended range.
			variableRange("reactor_spindown", s -> REACTOR_SPINDOWN = s),
			// The airlock, replacing vanilla IRON_DOOR_OPEN / IRON_DOOR_CLOSE.
			variableRange("reactor_door_open", s -> REACTOR_DOOR_OPEN = s),
			variableRange("reactor_door_close", s -> REACTOR_DOOR_CLOSE = s));

	private ModSounds() {
	}
}
