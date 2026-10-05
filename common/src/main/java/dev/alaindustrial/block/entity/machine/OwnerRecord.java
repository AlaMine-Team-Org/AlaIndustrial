package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.stats.PlayerStatsTracker;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Who placed a machine (MOD-133): the player's UUID and a snapshot of their name, saved under
 * {@code Owner} and {@code OwnerName}.
 *
 * <p>Set once at placement by {@link dev.alaindustrial.block.AbstractMachineBlock#setPlacedBy} and again
 * on every re-place — it does not ride the dropped item. Empty for a machine placed by non-player means
 * (a structure, the {@code /ala demo} stand); every player-stats hook treats a missing owner as a no-op.
 *
 * <p>One component of the machine base (MOD-712, BE-1). Whether a machine records an owner at all is the
 * base's {@code tracksOwner()}; this class holds and persists the value and pays the owner's XP.
 */
public final class OwnerRecord {

	@Nullable
	private UUID owner;
	/** Owner's name at placement time — a display snapshot (no UUID→name lookup for offline players). */
	private String ownerName = "";

	/** Set the owner; a null UUID clears ownership, a null name is stored as {@code ""}. */
	public void set(@Nullable UUID owner, @Nullable String ownerName) {
		this.owner = owner;
		this.ownerName = ownerName == null ? "" : ownerName;
	}

	/** The placer's UUID, or null for a machine placed by non-player means. */
	@Nullable
	public UUID uuid() {
		return owner;
	}

	/** The placer's name snapshot, or {@code ""} when there is no owner. */
	public String name() {
		return ownerName;
	}

	/** True when {@code player} is the owner. */
	public boolean is(UUID player) {
		return owner != null && owner.equals(player);
	}

	/**
	 * MOD-133: credit one completed unit of useful work (its full EU cost) to the owner — the sole XP
	 * source. Called once per completed operation (never per tick), so a redstone contraption that aborts
	 * an operation before completion burns EU but earns no XP. A no-op off-server, without an owner, or for
	 * non-positive cost; the tracker additionally ignores it when the owner is offline or in creative.
	 */
	public void creditUsefulWork(Level level, long euCost) {
		UUID placer = owner;
		if (euCost <= 0 || placer == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		PlayerStatsTracker.get().recordUsefulWork(serverLevel.getServer(), placer, euCost);
	}

	/**
	 * Write {@code Owner} (absent when there is none) and {@code OwnerName}. The teleporter station used the
	 * same keys before ownership moved to the base, so existing stations round-trip without a migration.
	 */
	public void save(ValueOutput output) {
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.putString("OwnerName", ownerName);
	}

	/** Read what {@link #save} wrote. */
	public void load(ValueInput input) {
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("OwnerName", "");
	}
}
