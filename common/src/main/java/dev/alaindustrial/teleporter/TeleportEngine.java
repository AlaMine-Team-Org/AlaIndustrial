package dev.alaindustrial.teleporter;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.item.TeleportPoint;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
		NOT_ENOUGH_EU("alaindustrial.teleporter.not_enough_eu");

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
		station.getEnergyStorage().amount -= cost;
		station.setChanged();
		return true;
	}
}
