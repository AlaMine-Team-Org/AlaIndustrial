package dev.alaindustrial.settlement.works;

import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TownObstructionPayload;
import dev.alaindustrial.settlement.SettlementRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * «Show it» at a stopped job's «In the way: X at x y z» (MOD-890, owner's playtest of 0.1.220; the obstruction is
 * R143's): the block in the way blinks with an outline seen through walls for {@link #HIGHLIGHT_TICKS}, as «Find» shows
 * a worker (R142). Only the asker sees it.
 *
 * <p><b>Who may ask (R98).</b> One of the town's own, with its core's screen open and standing by it
 * ({@link TownPulseService#openCore}); and only for a cell one of the town's stopped jobs names as in the way — a
 * forged request for any other cell, or from a stranger, gets no reply. The decision ({@link #answer}) is apart from
 * the send, so a both-lane gametest checks it: a mock player on NeoForge takes no mod payload.
 */
public final class TownObstructionService {

	/** How long the block in the way blinks, in ticks: eight seconds, as «Find» (R142). */
	public static final int HIGHLIGHT_TICKS = 160;

	private TownObstructionService() {
	}

	/** {@code viewer} pressed «show it» at the line naming {@code pos}. */
	public static void request(ServerPlayer viewer, BlockPos pos) {
		answer(viewer, pos).ifPresent(payload -> {
			NetworkDispatcher dispatcher = NetworkDispatcher.get();
			if (dispatcher.canSendToPlayer(viewer, TownObstructionPayload.TYPE)) {
				dispatcher.sendToPlayer(viewer, payload);
			}
		});
	}

	/** The reply to {@code viewer}'s «show it» at {@code pos}, or empty when it is refused. */
	public static Optional<TownObstructionPayload> answer(ServerPlayer viewer, BlockPos pos) {
		Optional<SettlementCoreBlockEntity> core = TownPulseService.openCore(viewer);
		if (core.isEmpty() || core.get().settlementId() <= 0) {
			return Optional.empty();
		}
		Optional<TownWorks> works = SettlementRegistry.get(viewer.level().getServer()).works()
				.find(core.get().settlementId());
		if (works.isEmpty() || !named(works.get(), pos)) {
			return Optional.empty();
		}
		return Optional.of(new TownObstructionPayload(pos.immutable(), HIGHLIGHT_TICKS));
	}

	/** Whether a stopped job of {@code works} names {@code pos} as the cell in its way. */
	static boolean named(TownWorks works, BlockPos pos) {
		for (WorkJob job : works.queue().jobs()) {
			if (job.status() != WorkJobStatus.BLOCKED || job.reason().isEmpty()) {
				continue;
			}
			BlockReason reason = job.reason().get();
			Optional<int[]> cell = BlockReason.obstructedCell(reason.key(), reason.args());
			if (cell.isPresent() && cell.get()[0] == pos.getX() && cell.get()[1] == pos.getY()
					&& cell.get()[2] == pos.getZ()) {
				return true;
			}
		}
		return false;
	}
}
