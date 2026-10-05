package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * L1.5 characterization of the mod's NeoForge {@link FluidType}s (MOD-708, batch 0): which types exist,
 * every physical property each declares, and which type each of the eleven fluids reports. NeoForge only:
 * {@code FluidType} is NeoForge's API; Fabric's attributes are pinned by {@code FluidAttributesSnapshotGameTest}.
 *
 * <p>Every instance field of {@code FluidType} is read, by reflection, as {@code name=value}, so a property
 * that is not exposed through a getter (the swim, drown and push flags, the path types, the motion
 * scale) is pinned as well. The sound map is read as its sorted keys and sound ids. Moving the fluid types
 * into a shared list must leave every line unchanged.
 *
 * <p>The values differ from Fabric's on purpose — the Fabric side has attributes for crude oil only, a
 * known defect with its own task; this test pins the NeoForge side as it is.
 *
 * <p><b>Updated only by hand</b> from the actual lines this test prints on a mismatch, in a commit that
 * names the behaviour change (ADR-032).
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class NeoForgeFluidTypeSnapshotTest {

	/** The eleven fluids, in registration order. */
	private static final List<String> FLUIDS = List.of("oil", "flowing_oil", "diesel", "flowing_diesel",
			"fuel_oil", "flowing_fuel_oil", "biofuel", "flowing_biofuel", "nutrient_solution",
			"flowing_nutrient_solution", "steam");

	/** The reference: the mod's fluid types by id, then each fluid's type. */
	static final List<String> EXPECTED = List.of(
			"type biofuel adjacentPathType=WATER_BORDER canConvertToSource=false canDrown=false canExtinguish=false"
					+ " canHydrate=false canPushEntity=false canSwim=false density=900"
					+ " descriptionId=block.alaindustrial.biofuel dripInfo=null fallDistanceModifier=1.0"
					+ " isWaterLike=false lightLevel=0 motionScale=0.0 pathType=WATER rarity=COMMON sounds={}"
					+ " supportsBoating=false temperature=300 viscosity=1000",
			"type diesel adjacentPathType=WATER_BORDER canConvertToSource=false canDrown=false canExtinguish=false"
					+ " canHydrate=false canPushEntity=false canSwim=false density=850"
					+ " descriptionId=block.alaindustrial.diesel dripInfo=null fallDistanceModifier=1.0"
					+ " isWaterLike=false lightLevel=0 motionScale=0.0 pathType=WATER rarity=COMMON sounds={}"
					+ " supportsBoating=false temperature=300 viscosity=1200",
			"type fuel_oil adjacentPathType=WATER_BORDER canConvertToSource=false canDrown=false canExtinguish=false"
					+ " canHydrate=false canPushEntity=false canSwim=false density=950"
					+ " descriptionId=block.alaindustrial.fuel_oil dripInfo=null fallDistanceModifier=1.0"
					+ " isWaterLike=false lightLevel=0 motionScale=0.0 pathType=WATER rarity=COMMON sounds={}"
					+ " supportsBoating=false temperature=300 viscosity=2400",
			"type nutrient_solution adjacentPathType=WATER_BORDER canConvertToSource=false canDrown=false"
					+ " canExtinguish=false canHydrate=false canPushEntity=false canSwim=false density=1000"
					+ " descriptionId=block.alaindustrial.nutrient_solution dripInfo=null fallDistanceModifier=1.0"
					+ " isWaterLike=false lightLevel=0 motionScale=0.0 pathType=WATER rarity=COMMON sounds={}"
					+ " supportsBoating=false temperature=300 viscosity=900",
			"type oil adjacentPathType=WATER_BORDER canConvertToSource=false canDrown=false canExtinguish=false"
					+ " canHydrate=false canPushEntity=false canSwim=false density=900"
					+ " descriptionId=block.alaindustrial.oil dripInfo=null fallDistanceModifier=1.0"
					+ " isWaterLike=false lightLevel=0 motionScale=0.0 pathType=WATER rarity=COMMON sounds={}"
					+ " supportsBoating=false temperature=300 viscosity=3000",
			"type steam adjacentPathType=WATER_BORDER canConvertToSource=false canDrown=false canExtinguish=false"
					+ " canHydrate=false canPushEntity=false canSwim=false density=1"
					+ " descriptionId=fluid.alaindustrial.steam dripInfo=null fallDistanceModifier=1.0"
					+ " isWaterLike=false lightLevel=0 motionScale=0.0 pathType=WATER rarity=COMMON sounds={}"
					+ " supportsBoating=false temperature=373 viscosity=200",
			"fluid oil -> alaindustrial:oil",
			"fluid flowing_oil -> alaindustrial:oil",
			"fluid diesel -> alaindustrial:diesel",
			"fluid flowing_diesel -> alaindustrial:diesel",
			"fluid fuel_oil -> alaindustrial:fuel_oil",
			"fluid flowing_fuel_oil -> alaindustrial:fuel_oil",
			"fluid biofuel -> alaindustrial:biofuel",
			"fluid flowing_biofuel -> alaindustrial:biofuel",
			"fluid nutrient_solution -> alaindustrial:nutrient_solution",
			"fluid flowing_nutrient_solution -> alaindustrial:nutrient_solution",
			"fluid steam -> alaindustrial:steam");

	/** Every instance field of {@code type} as sorted {@code name=value} pairs. */
	static String properties(FluidType type) throws IllegalAccessException {
		TreeMap<String, String> values = new TreeMap<>();
		for (Field field : FluidType.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			field.setAccessible(true);
			Object value = field.get(type);
			if (value instanceof java.util.Map<?, ?> map) {
				TreeMap<String, String> sounds = new TreeMap<>();
				for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
					Object sound = entry.getValue();
					sounds.put(String.valueOf(entry.getKey()), sound instanceof net.minecraft.sounds.SoundEvent event
							? event.location().toString() : String.valueOf(sound));
				}
				value = sounds;
			}
			values.put(field.getName(), String.valueOf(value));
		}
		StringBuilder line = new StringBuilder();
		values.forEach((name, value) -> line.append(' ').append(name).append('=').append(value));
		return line.toString();
	}

	/** The lines this loader produces: the mod's types sorted by id, then the fluids in order. */
	static List<String> capture() throws IllegalAccessException {
		List<String> lines = new ArrayList<>();
		TreeMap<String, FluidType> types = new TreeMap<>();
		for (FluidType type : NeoForgeRegistries.FLUID_TYPES) {
			Identifier key = NeoForgeRegistries.FLUID_TYPES.getKey(type);
			if (key != null && Industrialization.MOD_ID.equals(key.getNamespace())) {
				types.put(key.getPath(), type);
			}
		}
		for (var entry : types.entrySet()) {
			lines.add("type " + entry.getKey() + properties(entry.getValue()));
		}
		for (String path : FLUIDS) {
			Identifier id = Industrialization.id(path);
			if (!BuiltInRegistries.FLUID.containsKey(id)) {
				lines.add("fluid " + path + " unregistered");
				continue;
			}
			Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
			lines.add("fluid " + path + " -> " + NeoForgeRegistries.FLUID_TYPES.getKey(fluid.getFluidType()));
		}
		return lines;
	}

	/**
	 * @implements MOD-708-FLD04 — the mod's NeoForge fluid types keep every property, and every fluid keeps
	 *     its type
	 */
	@Test
	void fluidTypesMatchTheReference() throws IllegalAccessException {
		assertEquals(String.join("\n", EXPECTED), String.join("\n", capture()),
				"MOD-708: NeoForge fluid types differ from the reference; if intended, copy the actual lines");
	}
}
