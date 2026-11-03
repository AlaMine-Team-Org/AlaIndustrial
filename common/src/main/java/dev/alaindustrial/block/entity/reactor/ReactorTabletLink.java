package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.entity.ReactorAntennaBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Whether a reactor tablet hears its reactor right now, and if not, why (MOD-941).
 *
 * <p><b>One answer, asked every tick the screen is open.</b> The tablet's menu calls {@link #resolve} each tick and
 * shows the reactor's panel only while the answer is {@link State#LINKED}; every other state is a reason drawn over
 * the interference. Nothing is cached in the tablet: a reactor whose chunk is not loaded is "not responding", never
 * loaded on the tablet's behalf.
 *
 * <p><b>Range is one function</b> ({@link #range}), so the amplifier cards of a later tier (MOD-947) change one
 * place. Today it is the config knob and nothing else.
 */
public final class ReactorTabletLink {

	/**
	 * Slack added to the range when the reactor's chunk is not loaded and its antennas cannot be measured: a
	 * room is at most this many blocks across, so a player farther than range plus this from the controller is
	 * out of range whichever wall the antenna is in.
	 */
	private static final int ROOM_SLACK = 14;

	private ReactorTabletLink() {
	}

	/**
	 * The link's state. Ordinal order is the wire format of the menu's link channel: append, never insert.
	 */
	public enum State {
		LINKED,
		UNBOUND,
		DISCHARGED,
		OTHER_DIMENSION,
		NOT_RESPONDING,
		NO_ANTENNA,
		ANTENNA_UNPOWERED,
		OUT_OF_RANGE;

		private static final State[] VALUES = values();

		public static State byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NOT_RESPONDING;
		}

		/** The line the interference shows for this state. */
		public String translationKey() {
			return "gui.alaindustrial.reactor_tablet.link." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** A state, plus the controller to show when the state is {@link State#LINKED}. */
	public record Result(State state, @Nullable ReactorControllerBlockEntity controller) {
		static Result of(State state) {
			return new Result(state, null);
		}
	}

	/** How far this tablet hears an antenna, in blocks. One place, so later amplifier cards change one place. */
	public static int range(ItemStack tablet) {
		return Math.max(1, ReactorConfig.reactorTabletRange);
	}

	/** Whether {@code player}, holding {@code tablet}, hears the reactor it is bound to, and if not why. */
	public static Result resolve(Player player, ItemStack tablet) {
		GlobalPos bound = tablet.get(ModDataComponents.REACTOR_TABLET_LINK.get());
		if (bound == null) {
			return Result.of(State.UNBOUND);
		}
		if (ItemEnergy.get(tablet) <= 0) {
			return Result.of(State.DISCHARGED);
		}
		if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(bound.dimension())) {
			return Result.of(State.OTHER_DIMENSION);
		}
		int range = range(tablet);
		BlockPos at = bound.pos();
		// Read only what is already loaded and ticking: a chunk loaded on the tablet's behalf would be a free
		// chunk loader, and a loaded but frozen controller serves no antenna anyway.
		if (!level.isLoaded(at) || !level.shouldTickBlocksAt(at)) {
			double reach = range + ROOM_SLACK;
			return Result.of(player.distanceToSqr(Vec3.atCenterOf(at)) > reach * reach
					? State.OUT_OF_RANGE : State.NOT_RESPONDING);
		}
		if (!(level.getBlockEntity(at) instanceof ReactorControllerBlockEntity controller)) {
			return Result.of(State.NOT_RESPONDING);
		}
		if (controller.servedAntennas().isEmpty()) {
			return Result.of(State.NO_ANTENNA);
		}
		long now = level.getGameTime();
		double nearest = Double.MAX_VALUE;
		for (BlockPos antennaPos : controller.servedAntennas()) {
			if (level.getBlockEntity(antennaPos) instanceof ReactorAntennaBlockEntity antenna
					&& antenna.isTransmitting(now)) {
				nearest = Math.min(nearest, player.distanceToSqr(Vec3.atCenterOf(antennaPos)));
			}
		}
		if (nearest == Double.MAX_VALUE) {
			return Result.of(State.ANTENNA_UNPOWERED);
		}
		if (nearest > (double) range * range) {
			return Result.of(State.OUT_OF_RANGE);
		}
		return new Result(State.LINKED, controller);
	}
}
