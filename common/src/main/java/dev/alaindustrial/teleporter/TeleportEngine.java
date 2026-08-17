package dev.alaindustrial.teleporter;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.core.teleport.RtpSiteFinder;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.stats.PlayerStatsTracker;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The rules of a jump (MOD-092): may it happen, what does it cost, and how does it actually fire.
 *
 * <p><b>Everything about "is this jump allowed" lives in {@link #checkPolicy}, and everything about
 * "what does it cost" lives in {@link #computeCost} — deliberately, and nowhere else.</b> Today the
 * mod is same-dimension only; turning cross-dimension jumps on later (a per-dimension price
 * multiplier, Nether coordinate scaling) is then an edit to these two methods rather than a hunt for
 * scattered {@code if (dim != dim)} checks. {@link TeleportPoint} already stores the dimension, so
 * that change needs no data migration either.
 */
public final class TeleportEngine {
	private TeleportEngine() {
	}

	/** Max stacked items counted for the weight multiplier: a full 36-slot inventory of 64s. */
	private static final int FULL_INVENTORY_ITEMS = 36 * 64;

	/** Why a jump cannot happen; {@link #OK} means it can. Each carries the message the player sees. */
	public enum Denial {
		OK(null),
		NOT_BOUND("alaindustrial.teleporter.not_bound"),
		NOT_YOUR_REMOTE("alaindustrial.teleporter.not_your_remote"),
		CROSS_DIM("alaindustrial.teleporter.cross_dim"),
		MOUNTED("alaindustrial.teleporter.mounted"),
		ALREADY_WARMING("alaindustrial.teleporter.already_warming"),
		COOLDOWN("alaindustrial.teleporter.cooldown"),
		NO_STATION("alaindustrial.teleporter.no_station"),
		NO_ACCESS("alaindustrial.teleporter.no_access"),
		NOT_ENOUGH_EU("alaindustrial.teleporter.not_enough_eu"),
		// --- random jump only (MOD-116) ---
		RTP_NO_MODULE("alaindustrial.teleporter.rtp_no_module"),
		RTP_WRONG_DIMENSION("alaindustrial.teleporter.rtp_wrong_dimension"),
		RTP_NO_SAFE_SPOT("alaindustrial.teleporter.rtp_no_safe_spot");

		@Nullable
		private final String key;

		Denial(@Nullable String key) {
			this.key = key;
		}

		public boolean allowed() {
			return this == OK;
		}

		/** The translatable message for this denial; never called for {@link #OK}. */
		public Component message() {
			return Component.translatable(key);
		}
	}

	/**
	 * Everything checked before a warmup starts — cheap player-side rules first, then the one
	 * chunk-forcing look at the target.
	 *
	 * <p>This runs at trigger time, not only after the warmup, so that a player standing in a mine
	 * learns "the station is out of power" immediately instead of waiting fifteen seconds to be told
	 * no. It runs again at the end of the warmup (with the final numbers) as the authoritative check
	 * — see {@link TeleportWarmupManager}.
	 */
	public static Denial checkPolicy(ServerPlayer player, ItemStack remote, TeleportPoint point) {
		// --- rules that need nothing but the player ---
		if (player.isPassenger()) {
			return Denial.MOUNTED;
		}
		// Same-dimension only (MVP). This single check is the cross-dimension gate: lift it here and
		// price it in computeCost, and the rest of the feature already works.
		if (point.dim() != player.level().dimension()) {
			return Denial.CROSS_DIM;
		}

		// --- the target: one synchronous chunk load, then read the station ---
		TeleporterBlockEntity station = stationAt(player.level(), point.pos());
		if (station == null) {
			return Denial.NO_STATION;
		}
		if (!station.allowsAccess(player.getUUID())) {
			return Denial.NO_ACCESS;
		}
		if (station.getEnergyStorage().getAmount() < computeCost(player, point)) {
			return Denial.NOT_ENOUGH_EU;
		}
		return Denial.OK;
	}

	/**
	 * Load the target's chunk and return the station standing there, or null if it is gone.
	 *
	 * <p>{@code getChunkAt} forces a synchronous load (blocking while still servicing the server's
	 * own tasks — verified against 26.2's {@code ServerChunkCache}), which is what lets a player jump
	 * home to a base nobody is standing in. The mod has no chunk-loading system and does not need one
	 * for this.
	 */
	@Nullable
	public static TeleporterBlockEntity stationAt(Level level, BlockPos pos) {
		level.getChunkAt(pos);
		return level.getBlockEntity(pos) instanceof TeleporterBlockEntity station ? station : null;
	}

	/**
	 * Price of a jump: {@code (base + distance × perBlock) × weight}, paid by the target station.
	 *
	 * <p>Weight runs 1.0 (empty) to 2.0 (full) by how stuffed the player's pack is — travel light,
	 * pay less. It counts main+hotbar only, via {@code getNonEquipmentItems()}: in 26.2
	 * {@code Inventory.getContainerSize()} returns 43 and quietly mixes armour and offhand into
	 * {@code getItem}, so the obvious loop would charge players for their boots.
	 */
	public static long computeCost(ServerPlayer player, TeleportPoint point) {
		double distance = Math.sqrt(player.blockPosition().distSqr(point.pos()));
		double flat = Config.teleporterBaseCost + distance * Config.teleporterCostPerBlock;
		return Math.round(flat * weight(player));
	}

	/** 1.0 … 2.0 by inventory fullness (main + hotbar only — no armour, no offhand). */
	public static double weight(ServerPlayer player) {
		int items = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			items += stack.getCount();
		}
		return 1.0 + Math.min(items, FULL_INVENTORY_ITEMS) / (double) FULL_INVENTORY_ITEMS;
	}

	/**
	 * Fire the jump: teleport first, charge only on success.
	 *
	 * <p><b>The order is the whole point.</b> Deducting before the teleport opens a window where
	 * {@code teleportTo} fails and the EU is simply gone. Both halves happen inside one server tick,
	 * and game logic is single-threaded, so nothing can slip between them.
	 *
	 * <p>The landing follows vanilla {@code /tp}: validate the destination is in bounds, then kill
	 * vertical momentum and plant the player on the ground — otherwise a player who triggered the
	 * jump mid-fall keeps that fall speed and takes the damage on arrival.
	 *
	 * @return true if the player actually moved and the station was charged.
	 */
	public static boolean execute(ServerPlayer player, TeleportPoint point, long cost) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		TeleporterBlockEntity station = stationAt(level, point.pos());
		if (station == null || station.getEnergyStorage().getAmount() < cost) {
			return false;
		}
		// Land on top of the station block, centred.
		Vec3 target = new Vec3(point.pos().getX() + 0.5, point.pos().getY() + 1.0, point.pos().getZ() + 0.5);
		if (!Level.isInSpawnableBounds(BlockPos.containing(target))) {
			return false;
		}
		boolean moved = player.teleportTo(level, target.x, target.y, target.z, Set.<Relative>of(),
				player.getYRot(), player.getXRot(), true);
		if (!moved) {
			return false;
		}
		player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
		player.setOnGround(true);
		station.getEnergyStorage().drainInternal(cost);
		station.setChanged();
		// The jump is paid for; book it against the player who made it (MOD-361). The station drains its
		// buffer straight from here rather than through a machine cycle, so nothing else would ever tell
		// the dashboard this EU was spent — a player living on the teleporter saw "Consumed: 0 EU".
		// Booked here, next to the deduction and behind the same success guard, so a refused or aborted
		// jump can never leave a phantom entry; it is spending, not work, so it grants no mastery.
		PlayerStatsTracker.get().recordSpending(level.getServer(), player.getUUID(), cost);
		return true;
	}

	// --- random jump (MOD-116) -----------------------------------------------------------------
	//
	// Parallel methods rather than one polymorphic "jump target". A random jump differs from a
	// targeted one in every part that matters — there is no station at the far end to validate, to
	// price by distance, or to land on top of — and it is the only second kind of jump there is. An
	// abstraction over a set of two, one of which is the degenerate case of the other, would cost
	// more to read than the duplication it removes. If a third kind ever appears, THAT is the moment
	// to unify them.

	/**
	 * Everything checked before a random jump's warmup starts, and again when it ends.
	 *
	 * <p>{@code payingStation} is the row the player had selected in the remote: a random jump has no
	 * destination station, so the one that must exist, be reachable and be solvent is the station at
	 * the near end.
	 */
	public static Denial checkRtpPolicy(ServerPlayer player, TeleportPoint payingStation) {
		if (player.isPassenger()) {
			return Denial.MOUNTED;
		}
		// Overworld only. The search reads a surface height and trusts it, which the other two
		// dimensions make meaningless: the Nether's heightmap finds its bedrock ceiling, and the End is
		// mostly void between islands. Refusing outright is honest; guessing there would land people
		// inside rock or in mid-air over nothing.
		if (player.level().dimension() != Level.OVERWORLD) {
			return Denial.RTP_WRONG_DIMENSION;
		}
		if (payingStation.dim() != player.level().dimension()) {
			return Denial.CROSS_DIM;
		}

		TeleporterBlockEntity station = stationAt(player.level(), payingStation.pos());
		if (station == null) {
			return Denial.NO_STATION;
		}
		if (!station.allowsAccess(player.getUUID())) {
			return Denial.NO_ACCESS;
		}
		if (!station.hasRtpModule()) {
			return Denial.RTP_NO_MODULE;
		}
		if (station.getEnergyStorage().getAmount() < rtpCost()) {
			return Denial.NOT_ENOUGH_EU;
		}
		return Denial.OK;
	}

	/**
	 * What a random jump costs: a flat number, not the targeted jump's distance formula.
	 *
	 * <p>Flat because the player has to read the price off the button before pressing it, and where
	 * the dice will land is unknown until after the search has run. Inventory weight is left out for
	 * the same reason — a price that moved while the screen was open would be a price nobody could
	 * check.
	 */
	public static long rtpCost() {
		return Config.teleporterRtpCost;
	}

	/**
	 * Roll a destination, or {@code null} if the search gave up.
	 *
	 * <p>Called at trigger time and again at the end of the warmup — the second call is what catches a
	 * spot that stopped being safe while the player stood still, and it costs nothing extra because
	 * the chunk it needs is the one the first call already pulled in.
	 */
	@Nullable
	public static BlockPos findRtpSite(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		RandomSource random = level.getRandom();
		BlockPos origin = player.blockPosition();
		RtpSiteFinder.Site site = RtpSiteFinder.find(new RtpTerrain(level), origin.getX(), origin.getZ(),
				Config.teleporterRtpMinRadius, Config.teleporterRtpRadius, level.getSeaLevel(),
				Config.teleporterRtpMaxAttempts, random::nextDouble);
		return site == null ? null : new BlockPos(site.x(), site.y(), site.z());
	}

	/**
	 * Measure the rolled column again at the end of the warmup.
	 *
	 * <p>The COLUMN is kept and only its height re-read: the player was promised the spot the dice
	 * chose, and re-rolling would quietly turn "the jump you started" into a different one. Its chunk
	 * is already loaded by then, so this is nearly free.
	 *
	 * @return the corrected landing spot, or {@code null} if the column stopped being safe
	 */
	@Nullable
	public static BlockPos revalidateRtpSite(ServerPlayer player, BlockPos rolled) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		int feet = new RtpTerrain(level).safeFeetY(rolled.getX(), rolled.getZ());
		return feet == RtpSiteFinder.NO_SITE ? null : new BlockPos(rolled.getX(), feet, rolled.getZ());
	}

	/**
	 * Fire a random jump: teleport first, charge only on success — the same order, and for the same
	 * reason, as {@link #execute}.
	 *
	 * <p>{@code target} is a FEET position, so the player is placed at it rather than a block above
	 * it: unlike a targeted jump, which lands on top of a known block, the search has already
	 * accounted for the two clear blocks the player occupies.
	 */
	public static boolean executeRtp(ServerPlayer player, TeleportPoint payingStation, BlockPos target,
			long cost) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		TeleporterBlockEntity station = stationAt(level, payingStation.pos());
		if (station == null || !station.hasRtpModule()
				|| station.getEnergyStorage().getAmount() < cost) {
			return false;
		}
		Vec3 destination = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
		if (!Level.isInSpawnableBounds(BlockPos.containing(destination))) {
			return false;
		}
		boolean moved = player.teleportTo(level, destination.x, destination.y, destination.z,
				Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
		if (!moved) {
			return false;
		}
		player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
		player.setOnGround(true);
		station.getEnergyStorage().drainInternal(cost);
		station.setChanged();
		PlayerStatsTracker.get().recordSpending(level.getServer(), player.getUUID(), cost);
		return true;
	}
}
