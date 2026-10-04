package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.block.entity.AssemblerStatus;
import dev.alaindustrial.block.entity.DistillationColumnStatus;
import dev.alaindustrial.block.entity.ElectricHeaterStatus;
import dev.alaindustrial.block.entity.FermenterStatus;
import dev.alaindustrial.block.entity.GalvanicBathStatus;
import dev.alaindustrial.block.entity.GardenDroneStatus;
import dev.alaindustrial.block.entity.IncubatorStatus;
import dev.alaindustrial.block.entity.ProcessingMachineStatus;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.block.entity.RecyclerStatus;
import dev.alaindustrial.block.entity.ThermalCentrifugeStatus;
import dev.alaindustrial.block.entity.VulcanizerStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every status enum answers {@link StatusLine} (MOD-716, CLI-3): its adapter returns the keys and the flags
 * the enum already had under its own names, and the enums that had no "blocking" notion of their own block
 * in every state except the one their screen treats as running.
 */
class StatusLineTest {

	private static final List<Class<? extends Enum<?>>> ALL = List.of(ProcessingMachineStatus.class,
			VulcanizerStatus.class, ThermalCentrifugeStatus.class, FermenterStatus.class, GalvanicBathStatus.class,
			ElectricHeaterStatus.class, IncubatorStatus.class, GardenDroneStatus.class,
			DistillationColumnStatus.class, RecyclerStatus.class, ReactorRoomStatus.class, AssemblerStatus.class,
			ToolUpgradeStatus.class, RepairStatus.class);

	@Test
	void everyEnumIsAStatusLineWithAKeyPerState() {
		for (Class<? extends Enum<?>> type : ALL) {
			assertTrue(StatusLine.class.isAssignableFrom(type), type.getSimpleName());
			for (Enum<?> value : type.getEnumConstants()) {
				String key = ((StatusLine) value).translationKey();
				// gui.alaindustrial.* for most; the recycler's own captions live under status.alaindustrial.*
				assertTrue(key != null && key.contains(".alaindustrial."), type.getSimpleName() + "." + value);
			}
		}
	}

	@Test
	void adaptersKeepTheEnumsOwnNames() {
		for (RecyclerStatus status : RecyclerStatus.values()) {
			assertEquals(status.key(), status.translationKey());
			assertEquals(!status.isSilent(), status.isBlocking());
		}
		for (ElectricHeaterStatus status : ElectricHeaterStatus.values()) {
			assertEquals(status.needsAttention(), status.isBlocking());
		}
		for (ReactorRoomStatus status : ReactorRoomStatus.values()) {
			assertEquals(status.needsAttention(), status.isBlocking());
		}
		for (ToolUpgradeStatus status : ToolUpgradeStatus.values()) {
			assertEquals(!status.canWork(), status.isBlocking());
		}
		for (RepairStatus status : RepairStatus.values()) {
			assertEquals(!status.canWork(), status.isBlocking());
		}
	}

	@Test
	void repairKeysAreTheOnesTheScreenShipped() {
		assertEquals("gui.alaindustrial.component_repair_bench.insert_component",
				RepairStatus.NO_TARGET.translationKey());
		assertEquals("gui.alaindustrial.component_repair_bench.not_damaged", RepairStatus.NOT_DAMAGED.translationKey());
		assertEquals("gui.alaindustrial.component_repair_bench.limit_reached",
				RepairStatus.LIMIT_REACHED.translationKey());
		assertEquals("gui.alaindustrial.component_repair_bench.needs_material",
				RepairStatus.NEEDS_MATERIAL.translationKey());
		assertEquals("gui.alaindustrial.component_repair_bench.repairing", RepairStatus.READY.translationKey());
	}

	@Test
	void enumsWithoutAFlagBlockEverywhereButWhileRunning() {
		assertOnlyNonBlocking(FermenterStatus.values(), Set.of(FermenterStatus.READY));
		assertOnlyNonBlocking(GalvanicBathStatus.values(), Set.of(GalvanicBathStatus.READY));
		assertOnlyNonBlocking(AssemblerStatus.values(), Set.of(AssemblerStatus.READY));
		assertOnlyNonBlocking(GardenDroneStatus.values(), Set.of(GardenDroneStatus.WORKING, GardenDroneStatus.IDLE));
		assertOnlyNonBlocking(DistillationColumnStatus.values(),
				Set.of(DistillationColumnStatus.WORKING, DistillationColumnStatus.WARMING));
	}

	private static <E extends Enum<E> & StatusLine> void assertOnlyNonBlocking(E[] values, Set<E> running) {
		for (E value : values) {
			if (running.contains(value)) {
				assertFalse(value.isBlocking(), value.name());
			} else {
				assertTrue(value.isBlocking(), value.name());
			}
		}
	}
}
