package dev.alaindustrial.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-716 (CLI-5): every recipe-viewer click target IS its screen's own {@code PROGRESS_AREA} — the same object,
 * not an equal copy — so the rectangle cannot drift from the arrow the screen draws. Before the batch the
 * targets carried literals that "tracked" the screens' private constants by comment only.
 *
 * <p>The golden {@link MachineRecipeViewerTargetsGoldenTest} pins the numbers; this test pins where they come
 * from. Runs on the NeoForge L1.5 lane for the same reason as the golden one: the screens need the game jar.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class MachineRecipeViewerTargetsScreenConstantTest {

	@Test
	void everyTargetIsItsScreensConstant(MinecraftServer server) throws ReflectiveOperationException {
		Map<Class<?>, Object> areas = new LinkedHashMap<>();
		for (MachineRecipeViewerTargets.Target t : MachineRecipeViewerTargets.ALL) {
			areas.put(t.screenClass(), t.progressArea());
		}
		for (MachineRecipeViewerTargets.FluidTarget t : MachineRecipeViewerTargets.FLUID_ALL) {
			areas.put(t.screenClass(), t.progressArea());
		}
		for (MachineRecipeViewerTargets.AlloyTarget t : MachineRecipeViewerTargets.ALLOY_ALL) {
			areas.put(t.screenClass(), t.progressArea());
		}
		for (MachineRecipeViewerTargets.CanningTarget t : MachineRecipeViewerTargets.CANNING_ALL) {
			areas.put(t.screenClass(), t.progressArea());
		}
		for (MachineRecipeViewerTargets.InfoTarget t : MachineRecipeViewerTargets.INFO_ALL) {
			areas.put(t.screenClass(), t.progressArea());
		}
		assertEquals(15, areas.size(), "one target per screen, fifteen screens: " + areas.keySet());
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<Class<?>, Object> entry : areas.entrySet()) {
			Field field = entry.getKey().getField("PROGRESS_AREA");
			assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, field.getModifiers()
					& (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL), entry.getKey() + ".PROGRESS_AREA");
			if (field.get(null) != entry.getValue()) {
				wrong.add(entry.getKey().getSimpleName());
			}
		}
		assertEquals(List.of(), wrong, "targets that are not their screen's PROGRESS_AREA");
	}
}
