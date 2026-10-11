package dev.alaindustrial.item.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.config.Knob;
import dev.alaindustrial.item.ToolConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * L1 for {@link ItemBuffers} (MOD-790): every component is a client-visible buffer knob of {@link ToolConfig} with
 * the same name and type; {@link ItemBuffers#live()} reads those knobs, {@link ItemBuffers#shown()} the server's
 * snapshot component by component — never a neighbour's value — and falls back to the live ones without one;
 * receiving the snapshot writes no knob.
 */
class ItemBuffersTest {

	@AfterEach
	void forgetTheServer() {
		ServerBalance.reset();
	}

	private static Field knob(RecordComponent component) throws NoSuchFieldException {
		return ToolConfig.class.getField(component.getName());
	}

	private static int value(RecordComponent component, ItemBuffers buffers) throws ReflectiveOperationException {
		return (int) component.getAccessor().invoke(buffers);
	}

	/**
	 * @implements MOD-790-IB01 — each component of ItemBuffers is a ToolConfig buffer knob of the same name, int,
	 *     flagged clientVisible
	 * @covers MOD-790
	 */
	@Test
	void everyComponentIsAClientVisibleToolConfigKnob() throws NoSuchFieldException {
		RecordComponent[] components = ItemBuffers.class.getRecordComponents();
		assertEquals(17, components.length, "one component per powered-item buffer knob");
		for (RecordComponent component : components) {
			Field field = knob(component);
			Knob knob = field.getAnnotation(Knob.class);
			assertTrue(knob != null && knob.clientVisible(), component.getName() + " must be a clientVisible knob");
			assertTrue(Modifier.isStatic(field.getModifiers()) && field.getType() == int.class
					&& component.getType() == int.class, component.getName() + " must be a static int knob");
		}
	}

	/**
	 * @implements MOD-790-IB02 — live() reads ToolConfig, shown() reads the server's snapshot knob by knob and the
	 *     local knobs without one; receiving it writes nothing to ToolConfig
	 * @covers MOD-790
	 */
	@Test
	void liveReadsTheKnobsAndShownTheServer() throws ReflectiveOperationException {
		RecordComponent[] components = ItemBuffers.class.getRecordComponents();
		Map<String, Object> server = new LinkedHashMap<>();
		Map<String, Integer> local = new LinkedHashMap<>();
		for (int i = 0; i < components.length; i++) {
			int knob = knob(components[i]).getInt(null);
			local.put(components[i].getName(), knob);
			// distinct per component, so a swapped pair of arguments is caught
			server.put(components[i].getName(), knob * 3 + i + 1);
		}

		ItemBuffers live = ItemBuffers.live();
		for (RecordComponent component : components) {
			assertEquals(local.get(component.getName()), value(component, live), "live()." + component.getName());
			assertEquals(local.get(component.getName()), value(component, ItemBuffers.shown()),
					"shown()." + component.getName() + " without a snapshot is the local knob");
		}

		assertTrue(ServerBalance.receive(KnobSnapshot.of(server).encode()));
		ItemBuffers shown = ItemBuffers.shown();
		for (RecordComponent component : components) {
			assertEquals(server.get(component.getName()), value(component, shown), "shown()." + component.getName());
			assertEquals(local.get(component.getName()), knob(component).getInt(null),
					"receiving the snapshot rewrote ToolConfig." + component.getName());
		}
		assertEquals(live, ItemBuffers.live(), "live() follows ToolConfig, not the snapshot");

		ServerBalance.reset();
		assertEquals(live, ItemBuffers.shown(), "after reset() shown() is the local balance again");
	}
}
