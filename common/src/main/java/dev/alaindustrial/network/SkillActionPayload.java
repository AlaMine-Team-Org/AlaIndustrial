package dev.alaindustrial.network;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.WorkstationPart;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillPoints;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.skill.SkillStore;
import dev.alaindustrial.stats.PlayerStatsStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Buy one skill (MOD-483) — the client-to-server half of the upgrade tree, and the whole of it.
 *
 * <p><b>There is no wipe.</b> A paid reset existed while the tree was being built, as a way to test the
 * fork rule quickly; the owner removed it (2026-09-06) because it was scaffolding, not a mechanic. A
 * hard fork the player can undo for energy is not a hard fork, and leaving a handler nobody calls is an
 * invitation to re-enable it by accident. If a deliberate way to rebuild ever arrives it comes with its
 * own decision, its own price and its own packet.
 *
 * <p>There is no server-to-client half: both loaders already mirror a player's attachment to its own
 * client whenever it is written (Fabric {@code syncWith(targetOnly())} / NeoForge
 * {@code sync(holder == player)}), so the new state arrives on its own and the screen simply re-reads
 * {@code SkillClientCache}.
 *
 * <p><b>Nothing here is trusted.</b> The screen decides what to draw; this handler decides what is
 * true. It re-derives the player's points from their career stats, re-checks the fork rule against the
 * stored build, and re-reads the station from the world — a packet naming a station the player is
 * nowhere near, or one that was broken between click and arrival, changes nothing.
 */
public record SkillActionPayload(BlockPos station, String branch, String slot)
		implements CustomPacketPayload {

	public static final Type<SkillActionPayload> TYPE = new Type<>(Industrialization.id("skill_action"));

	/**
	 * How far from the station a purchase is still accepted, squared. Generous next to the ~5-block
	 * reach a player actually has: the check exists to refuse a packet sent from across the world, not
	 * to fight the client over half a block while the player walks backwards out of an open screen.
	 */
	private static final double MAX_DISTANCE_SQ = 64.0;

	/** Longest branch/slot string the codec will decode — anything longer never reaches the handler. */
	private static final int MAX_KEY_LENGTH = 32;

	public static final StreamCodec<RegistryFriendlyByteBuf, SkillActionPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SkillActionPayload::station,
			ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH), SkillActionPayload::branch,
			ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH), SkillActionPayload::slot,
			SkillActionPayload::new);

	/** A purchase request for one node — the only request there is. */
	public static SkillActionPayload buy(BlockPos station, SkillBranch branch, SkillSlot slot) {
		return new SkillActionPayload(station, branch.key(), slot.name());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Apply the request, server-side, or do nothing at all. Never reports failure to the client. */
	public static void handle(SkillActionPayload payload, ServerPlayer player) {
		WorkstationBlockEntity station = stationFor(payload, player);
		if (station == null) {
			return;
		}
		// A dead station teaches nothing. The screen refuses to open without power, but the screen is
		// the client's business: a packet can still arrive from one that was open when the power died.
		if (station.getEnergyStorage().getAmount() <= 0) {
			return;
		}
		applyBuy(payload, player, station);
	}

	/**
	 * The assembled, in-reach Workstation this request names, or {@code null}.
	 *
	 * <p>Checks the lower half specifically: that is the half that owns the block entity and the energy
	 * buffer, and it is the position the screen was opened from.
	 */
	private static WorkstationBlockEntity stationFor(SkillActionPayload payload, ServerPlayer player) {
		BlockPos pos = payload.station();
		if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQ) {
			return null;
		}
		// isLoaded before getBlockState: a packet naming an unloaded chunk must not load it.
		if (!player.level().isLoaded(pos)) {
			return null;
		}
		BlockState state = player.level().getBlockState(pos);
		if (!(state.getBlock() instanceof WorkstationBlock)
				|| state.getValue(WorkstationBlock.PART) != WorkstationPart.LOWER) {
			return null;
		}
		BlockEntity be = player.level().getBlockEntity(pos);
		return be instanceof WorkstationBlockEntity workstation ? workstation : null;
	}

	/**
	 * Buy one node if every rule allows it, and charge the station for teaching it.
	 *
	 * <p>The EU price is flat (owner, 2026-09-05): Ala-Fragments already scale with how deep a node
	 * sits, so charging more energy for a deeper one would price the same decision twice.
	 *
	 * <p>Charged BEFORE the skill is stored, and only if the whole price was actually paid — a buffer
	 * that drains between check and spend must not hand out a half-priced skill. Rule failures stay
	 * silent (the screen greyed the node out already), but an empty buffer is told to the player: the
	 * node looked available, so silence would read as a broken button.
	 */
	private static void applyBuy(SkillActionPayload payload, ServerPlayer player,
			WorkstationBlockEntity station) {
		SkillBranch branch = SkillBranch.byKey(payload.branch());
		SkillSlot slot = SkillSlot.byKey(payload.slot());
		if (branch == null || slot == null) {
			return;
		}
		int points = SkillPoints.earned(PlayerStatsStore.get(player));
		SkillBuild build = SkillStore.build(player);
		if (!build.canBuy(branch, slot, points)) {
			return;
		}
		long price = Math.max(0, Config.workstationSkillPurchaseEu);
		if (price > 0) {
			if (station.getEnergyStorage().getAmount() < price
					|| station.getEnergyStorage().drainInternal(price) < price) {
				player.sendSystemMessage(Component.translatable(
						"message.alaindustrial.workstation.no_energy_for_skill", price), true);
				return;
			}
			station.setChanged();
		}
		SkillStore.set(player, new PlayerSkills(build.with(branch, slot)));
	}
}
