package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * «Find» on a row of the core's worker list (owner's round 4 of 0.1.219, R4-13): the worker glows for
 * {@link #LOCATE_TICKS}, its outline seen through walls, so the player can walk to it.
 *
 * <p><b>Who may ask.</b> Anyone the town admits who may see the worker's card ({@link ShiftWorkerProfiles#mayView}),
 * not only the owner: finding a worker changes nothing. Only a living record glows.
 *
 * <p><b>Who sees it.</b> The outline is vanilla's glowing effect on the worker, so every player near sees it for those
 * seconds; the asker alone hears the line on the action bar. A worker whose chunk is not loaded cannot glow, and the
 * line says so.
 */
public final class ShiftWorkerLocator {

	/** How long «Find» outlines a worker, in ticks: eight seconds. */
	public static final int LOCATE_TICKS = 160;
	/** The action bar line after «Find»: the worker is outlined. */
	public static final String SHOWN_KEY = "settlement.alaindustrial.core.roster.locate.shown";
	/** The action bar line after «Find»: the worker is not loaded, nobody to outline. */
	public static final String FAR_KEY = "settlement.alaindustrial.core.roster.locate.far";

	private ShiftWorkerLocator() {
	}

	/** {@code player} pressed «Find» at worker {@code number}. */
	public static void locate(ServerPlayer player, RecordNumber number) {
		MinecraftServer server = player.level().getServer();
		Optional<ShiftWorkerRecord> found = ShiftWorkers.book(server).byNumber(number)
				.filter(record -> record.status() == ShiftWorkerRecord.Status.ALIVE
						&& ShiftWorkerProfiles.mayView(player, record));
		if (found.isEmpty()) {
			return;
		}
		ShiftWorkerRecord record = found.get();
		Optional<ShiftWorker> worker = SettlementRegistry.get(server).find(record.settlement())
				.map(town -> server.getLevel(town.dimension()))
				.map(level -> level.getEntity(record.entity()))
				.filter(entity -> entity instanceof ShiftWorker && !entity.isRemoved())
				.map(ShiftWorker.class::cast);
		if (worker.isEmpty()) {
			player.sendOverlayMessage(Component.translatable(FAR_KEY, record.number().toString()));
			return;
		}
		worker.get().addEffect(new MobEffectInstance(MobEffects.GLOWING, LOCATE_TICKS, 0, false, false));
		player.sendOverlayMessage(Component.translatable(SHOWN_KEY, record.number().toString()));
	}
}
