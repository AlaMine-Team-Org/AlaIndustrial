package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.plan.PlanSheetHints;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * The town hall's log line about the plan (MOD-859, epic decision R92): when a town's core wakes, the hall's log says
 * that the hall can be restored and where the three sheets of its plan lie. Once per town: a core that goes dark and
 * lights again, or a world loaded again, writes nothing new — the line is already in the log, which is saved.
 */
public final class PlanSheetNews {

	private PlanSheetNews() {
	}

	/**
	 * Town {@code settlementId}'s core has just woken. Writes the plan line to the hall's log unless the town has no
	 * structure (no style, so no sheets), its hall stands restored already, or the log holds the line. Returns
	 * whether it wrote one.
	 */
	public static boolean coreWoke(ServerLevel level, int settlementId) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<Settlement> town = registry.find(settlementId);
		if (town.isEmpty() || town.get().style().isEmpty() || written(registry.workers(), settlementId)
				|| TownHall.restored(level, settlementId)) {
			return false;
		}
		registry.workers().addToLog(ShiftWorkerBook.LogLine.noted(settlementId,
				new ShiftWorkerBook.Note(PlanSheetHints.JOURNAL_CORE_AWAKE, level.getGameTime())));
		// INFO on purpose (MOD-833): the playtest protocol of the towns reads these lines from the log.
		Industrialization.LOGGER.info("[ala-towns] settlement {}: the hall's log tells where the plan sheets lie",
				settlementId);
		return true;
	}

	/** Whether the hall's log already holds town {@code settlementId}'s plan line. */
	static boolean written(ShiftWorkerBook book, int settlementId) {
		for (ShiftWorkerBook.LogLine line : book.log()) {
			if (line.settlement() == settlementId
					&& line.note().map(note -> PlanSheetHints.JOURNAL_CORE_AWAKE.equals(note.key())).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The words of a hall log note, or empty for any other line. What the town hall's journal will show. A note that
	 * names a worker (a broken house inlet, MOD-884) gets its number as the sentence's argument.
	 */
	public static Optional<Component> logLineText(ShiftWorkerBook.LogLine line) {
		Object[] args = line.number().map(number -> new Object[] {number.toString()}).orElse(new Object[0]);
		return line.note().map(note -> Component.translatable(note.key(), args));
	}
}
