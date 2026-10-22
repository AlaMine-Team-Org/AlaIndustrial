package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementGate;
import dev.alaindustrial.settlement.SettlementRegistry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The town a machine of an ALA town serves (MOD-858; the reception capsule's rule, MOD-833): the one whose territory
 * held the block when it first looked — the town with the nearest core, where two territories overlap — kept by its
 * number for good. A town that left the registry (a lost save) frees the block to look again. One field of the
 * block entity (ADR-042); the dispatcher and the drone nest hold one each.
 *
 * <p><b>Who breaks the block.</b> In a town that has an owner only that owner does; in an unclaimed town, or with no
 * town, anyone. The client learns the owner from the update tag ({@link #writeUpdate}), so the owner sees the cracks
 * and nobody else does.
 */
public final class TownBinding {

	private static final String KEY_SETTLEMENT = "SettlementId";
	/** Update-tag only: the owner of the town, for the client's breaking prediction. Never saved. */
	private static final String KEY_CLIENT_OWNER = "SettlementOwner";

	private int settlementId;
	@Nullable
	private UUID sentOwner;
	@Nullable
	private UUID clientOwner;

	/** The number of the town, or 0 before the block found one. */
	public int id() {
		return settlementId;
	}

	/**
	 * The town the block at {@code pos} serves: the one it keeps, or — while it has none — the town whose territory
	 * holds it with the nearest core, lit or not. Returns whether the kept number changed, through {@code changed}.
	 */
	public Optional<Settlement> resolve(ServerLevel level, BlockPos pos, Runnable changed) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		if (settlementId > 0) {
			Optional<Settlement> kept = registry.find(settlementId);
			if (kept.isPresent()) {
				return kept;
			}
			settlementId = 0;
			changed.run();
		}
		Settlement nearest = null;
		double best = Double.MAX_VALUE;
		for (Settlement candidate : registry.all()) {
			if (candidate.territoryContains(level.dimension(), pos)) {
				double distance = candidate.corePos().distSqr(pos);
				if (distance < best) {
					best = distance;
					nearest = candidate;
				}
			}
		}
		if (nearest != null) {
			settlementId = nearest.id();
			changed.run();
		}
		return Optional.ofNullable(nearest);
	}

	/**
	 * The town the block serves, looking for one now if it has none yet ({@link #resolve}): what an open path asks
	 * (R98), so a block placed a moment ago in somebody's town is theirs before its first tick. Empty off the server.
	 */
	public Optional<Settlement> resolveNow(@Nullable Level level, BlockPos pos, Runnable changed) {
		return level instanceof ServerLevel serverLevel ? resolve(serverLevel, pos, changed) : Optional.empty();
	}

	/** The town's record, if the block has one and the registry still knows it. */
	public Optional<Settlement> find(@Nullable Level level) {
		if (settlementId <= 0 || !(level instanceof ServerLevel serverLevel)) {
			return Optional.empty();
		}
		return SettlementRegistry.get(serverLevel.getServer()).find(settlementId);
	}

	/** Whether the owner changed since the clients last heard — the caller sends the update tag again. */
	public boolean ownerChanged(Optional<Settlement> town) {
		UUID owner = town.flatMap(Settlement::owner).orElse(null);
		if (Objects.equals(owner, sentOwner)) {
			return false;
		}
		sentOwner = owner;
		return true;
	}

	/** The town's owner: from the registry on the server, from the update tag on a client. */
	public Optional<UUID> owner(@Nullable Level level) {
		return level != null && level.isClientSide() ? Optional.ofNullable(clientOwner)
				: find(level).flatMap(Settlement::owner);
	}

	/** Whether {@code player} controls the block (R95, R98): the town's restorer, or anyone while it has none. */
	public boolean mayControl(@Nullable Level level, Player player) {
		return SettlementGate.standingByRestorer(owner(level), player).controls();
	}

	/** Whether {@code player} may use or read the block at all (R98): not a stranger to its town. */
	public boolean admits(@Nullable Level level, Player player) {
		return SettlementGate.standingByRestorer(owner(level), player).admitted();
	}

	public void save(ValueOutput output) {
		output.putInt(KEY_SETTLEMENT, settlementId);
	}

	public void load(ValueInput input) {
		settlementId = Math.max(0, input.getIntOr(KEY_SETTLEMENT, 0));
		clientOwner = input.read(KEY_CLIENT_OWNER, UUIDUtil.CODEC).orElse(null);
	}

	/** Adds the town's owner to an update tag; it never reaches the save. */
	public void writeUpdate(CompoundTag tag, @Nullable Level level) {
		find(level).flatMap(Settlement::owner).ifPresent(owner -> tag.store(KEY_CLIENT_OWNER, UUIDUtil.CODEC, owner));
	}
}
