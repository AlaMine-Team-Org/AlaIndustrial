package dev.alaindustrial.core.monitor;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.SmartWireBlock;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.block.entity.SmartWireBlockEntity;
import dev.alaindustrial.core.energy.PosOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One connected monitoring system (MOD-480): wires, a core, and the panels hanging off it.
 *
 * <p><b>Panels never scan.</b> The core collects what its panels are watching, walks the wired
 * containers ONCE, and hands each panel its number. The cost of a scan is therefore one pass over
 * the containers regardless of how many panels read the result — the naive shape, where every panel
 * looks for itself, multiplies the same work by the size of the wall.
 */
public final class MonitorNetwork {

	private static final Comparator<BlockPos> POS_ORDER =
			(a, b) -> PosOrder.compare(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());

	private final ServerLevel level;
	private final Set<BlockPos> nodes = new LinkedHashSet<>();

	private boolean dirty = true;
	private final List<BlockPos> cores = new ArrayList<>();
	private final List<BlockPos> panels = new ArrayList<>();
	private final List<BlockPos> containers = new ArrayList<>();

	private int cooldown;

	public MonitorNetwork(ServerLevel level) {
		this.level = level;
	}

	public Set<BlockPos> nodes() {
		return nodes;
	}

	public void addNode(BlockPos pos) {
		nodes.add(pos.immutable());
		markDirty();
	}

	public void removeNode(BlockPos pos) {
		nodes.remove(pos);
		markDirty();
	}

	public void absorb(MonitorNetwork other) {
		nodes.addAll(other.nodes);
		markDirty();
	}

	public void markDirty() {
		dirty = true;
		cooldown = 0;
	}

	public boolean isAwake() {
		return !nodes.isEmpty();
	}

	/**
	 * Sort the nodes into roles and find the containers the wires touch.
	 *
	 * <p>Everything here is cached until the topology changes: this walks every node and asks the
	 * world for its block entity, which is exactly the work that must not happen per tick on a wall
	 * of several hundred panels.
	 */
	private void refreshIfDirty() {
		if (!dirty) {
			return;
		}
		dirty = false;
		cores.clear();
		panels.clear();
		containers.clear();
		Set<BlockPos> seenContainers = new LinkedHashSet<>();
		for (BlockPos pos : nodes) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof MonitorCoreBlockEntity) {
				cores.add(pos);
			} else if (be instanceof MonitorPanelBlockEntity) {
				panels.add(pos);
			} else if (be instanceof SmartWireBlockEntity) {
				for (Direction dir : Direction.values()) {
					BlockPos target = pos.relative(dir).immutable();
					if (nodes.contains(target) || seenContainers.contains(target)) {
						continue;
					}
					if (ContainerScan.isReadable(level, target)) {
						seenContainers.add(target);
					}
				}
			}
		}
		containers.addAll(seenContainers);
		// A stable geometric order, not hash order: it decides which panels go dark when the fitted
		// cards run out, and that answer must be the same after a reload (ADR-006).
		cores.sort(POS_ORDER);
		panels.sort(POS_ORDER);
		containers.sort(POS_ORDER);
	}

	/** Run one scan if the interval has elapsed. */
	public void tick() {
		refreshIfDirty();
		if (panels.isEmpty()) {
			return;
		}
		if (--cooldown > 0) {
			return;
		}
		cooldown = Math.max(1, Config.monitorScanIntervalTicks);

		// Exactly one core, deliberately: two cores in one wall would each bill their own upkeep and
		// each claim the same panels, so the wall refuses to run rather than working twice over.
		if (cores.size() != 1) {
			publishAll(MonitorReadout.NO_CORE);
			return;
		}
		if (!(level.getBlockEntity(cores.getFirst()) instanceof MonitorCoreBlockEntity core)) {
			publishAll(MonitorReadout.NO_CORE);
			return;
		}

		// The watched types, deduplicated: two panels showing the same item are one demand on the
		// cards, not two — the allowance is about types, not about screens.
		List<ItemStack> watched = new ArrayList<>();
		List<MonitorPanelBlockEntity> livePanels = new ArrayList<>();
		List<Integer> panelType = new ArrayList<>();
		for (BlockPos pos : panels) {
			if (!(level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel)) {
				continue;
			}
			livePanels.add(panel);
			ItemStack filter = panel.getFilter();
			if (filter.isEmpty()) {
				panelType.add(-1);
				continue;
			}
			int index = indexOf(watched, filter);
			if (index < 0) {
				watched.add(filter);
				index = watched.size() - 1;
			}
			panelType.add(index);
		}

		int allowance = core.trackableTypes();
		int servedTypes = Math.min(allowance, watched.size());
		List<ItemStack> served = watched.subList(0, servedTypes);

		int servedPanels = 0;
		for (int type : panelType) {
			if (type >= 0 && type < servedTypes) {
				servedPanels++;
			}
		}

		boolean powered = core.payUpkeep(level, servedPanels);

		StockTally tally = new StockTally(served);
		if (powered && !served.isEmpty()) {
			for (BlockPos container : containers) {
				ContainerScan.tallyAt(level, container, tally);
			}
		}

		for (int i = 0; i < livePanels.size(); i++) {
			MonitorPanelBlockEntity panel = livePanels.get(i);
			int type = panelType.get(i);
			if (type < 0) {
				panel.publish(MonitorReadout.IDLE, 0L);
			} else if (type >= servedTypes) {
				panel.publish(MonitorReadout.NO_CAPACITY, 0L);
			} else if (!powered) {
				panel.publish(MonitorReadout.NO_POWER, 0L);
			} else {
				panel.publish(MonitorReadout.OK, tally.total(type));
			}
		}
	}

	/**
	 * What the wall can currently do, for the blocks' own feedback messages.
	 *
	 * @param hasCore      whether this side of the wall still has its brain
	 * @param allowance    how many types the fitted cards permit
	 * @param watchedTypes how many DIFFERENT types the panels are asking for
	 */
	public record Capacity(boolean hasCore, int allowance, int watchedTypes) {
	}

	/** Answers "why is my panel showing a cross" without waiting for the next scan. */
	public Capacity capacity() {
		refreshIfDirty();
		if (cores.size() != 1
				|| !(level.getBlockEntity(cores.getFirst()) instanceof MonitorCoreBlockEntity core)) {
			return new Capacity(false, 0, watched().size());
		}
		return new Capacity(true, core.trackableTypes(), watched().size());
	}

	/** The distinct item types the panels of this network are asking for, in panel order. */
	private List<ItemStack> watched() {
		List<ItemStack> result = new ArrayList<>();
		for (BlockPos pos : panels) {
			if (!(level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel)) {
				continue;
			}
			ItemStack filter = panel.getFilter();
			if (!filter.isEmpty() && indexOf(result, filter) < 0) {
				result.add(filter);
			}
		}
		return result;
	}

	private void publishAll(MonitorReadout readout) {
		for (BlockPos pos : panels) {
			if (level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel) {
				panel.publish(panel.getFilter().isEmpty() ? MonitorReadout.IDLE : readout, 0L);
			}
		}
	}

	private static int indexOf(List<ItemStack> samples, ItemStack stack) {
		for (int i = 0; i < samples.size(); i++) {
			if (ItemStack.isSameItemSameComponents(samples.get(i), stack)) {
				return i;
			}
		}
		return -1;
	}

	/** Grid connectivity for this system: wire to wire or core, core to panel, panel to panel. */
	public static boolean connects(ServerLevel level, BlockPos from, BlockPos to) {
		BlockEntity a = level.getBlockEntity(from);
		BlockEntity b = level.getBlockEntity(to);
		if (a == null || b == null) {
			return false;
		}
		boolean aWire = a instanceof SmartWireBlockEntity;
		boolean bWire = b instanceof SmartWireBlockEntity;
		boolean aCore = a instanceof MonitorCoreBlockEntity;
		boolean bCore = b instanceof MonitorCoreBlockEntity;
		boolean aPanel = a instanceof MonitorPanelBlockEntity;
		boolean bPanel = b instanceof MonitorPanelBlockEntity;
		if (aWire && (bWire || bCore)) {
			return SmartWireBlock.connectsTo(level, to);
		}
		if (aCore && (bWire || bPanel)) {
			return true;
		}
		if (aPanel && (bPanel || bCore)) {
			return true;
		}
		return false;
	}
}
