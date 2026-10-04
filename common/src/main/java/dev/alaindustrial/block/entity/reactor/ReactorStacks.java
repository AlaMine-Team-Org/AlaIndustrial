package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * The room's columns as stacks — unbroken vertical runs (MOD-713, BE-5): the one walk behind both readers of "a
 * stack", the settling that makes a run hold its fluid as one vessel and the steam puffs over the top of a run
 * ({@link ReactorVoice}).
 */
public final class ReactorStacks {

	private ReactorStacks() {}

	/**
	 * Settles fluid inside each vertical run of columns.
	 *
	 * <p><b>A stack is one vessel, and this is the rule that makes it look like one.</b> Columns joined
	 * top to bottom are already drawn as a single unbroken tower, so a pipe touching any block of that
	 * tower fills the whole tower — connecting to a five-block column at the floor and having only the
	 * bottom block fill would contradict what the player is looking at. Columns standing side by side
	 * are separate vessels and stay separate: coolant appearing in a tower nothing is plumbed to was
	 * the version before this one, and it read as a bug however convenient it was.
	 *
	 * <p><b>Water settles to the bottom, steam collects at the top</b> — filled from one end rather than
	 * shared out evenly, which is both what a liquid does and what makes the tower readable: a half-full
	 * stack shows a solid body of water with one surface, instead of the same puddle repeated in every
	 * block with a gap above each. It also puts the steam where the exhaust is, since only the topmost
	 * block of a stack has a free upper face to vent through.
	 */
	public static void settle(List<FuelRodAssemblyBlockEntity> columns) {
		if (columns.size() < 2) {
			return;
		}
		for (List<FuelRodAssemblyBlockEntity> run : runs(columns)) {
			settleRun(run);
		}
	}

	/**
	 * The columns grouped into unbroken vertical runs — stacks — each lowest first.
	 *
	 * <p>Grouped in the order the columns come in, not a hash order, so a room lists its stacks the same way every
	 * tick and on both loaders — the puff rotation reads that order.
	 */
	public static List<List<FuelRodAssemblyBlockEntity>> runs(List<FuelRodAssemblyBlockEntity> columns) {
		Map<Long, List<FuelRodAssemblyBlockEntity>> byColumn = new LinkedHashMap<>();
		for (FuelRodAssemblyBlockEntity column : columns) {
			BlockPos at = column.getBlockPos();
			// One key per (x, z): the vertical runs inside it are separated below, after sorting.
			byColumn.computeIfAbsent(((long) at.getX() << 32) ^ (at.getZ() & 0xFFFFFFFFL),
					key -> new ArrayList<>()).add(column);
		}
		List<List<FuelRodAssemblyBlockEntity>> runs = new ArrayList<>();
		for (List<FuelRodAssemblyBlockEntity> shaft : byColumn.values()) {
			shaft.sort(Comparator.comparingInt(column -> column.getBlockPos().getY()));
			int runStart = 0;
			for (int i = 1; i <= shaft.size(); i++) {
				boolean broken = i == shaft.size()
						|| shaft.get(i).getBlockPos().getY() != shaft.get(i - 1).getBlockPos().getY() + 1;
				if (broken) {
					runs.add(shaft.subList(runStart, i));
					runStart = i;
				}
			}
		}
		return runs;
	}

	/** One unbroken tower: water poured in at the bottom, steam pushed up to the top. */
	private static void settleRun(List<FuelRodAssemblyBlockEntity> run) {
		if (run.size() < 2) {
			return;
		}
		long water = 0;
		long steam = 0;
		for (FuelRodAssemblyBlockEntity column : run) {
			water += column.waterAmount();
			steam += column.steamAmount();
		}
		for (int i = 0; i < run.size(); i++) {
			FuelRodAssemblyBlockEntity column = run.get(i);
			long take = Math.min(water, column.waterCapacity());
			column.setTank(true, take);
			water -= take;
		}
		for (int i = run.size() - 1; i >= 0; i--) {
			FuelRodAssemblyBlockEntity column = run.get(i);
			long take = Math.min(steam, column.steamCapacity());
			column.setTank(false, take);
			steam -= take;
		}
	}
}
