package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.structure.ReactorZone;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The reactor's core, stack by stack, for the controller's «Core» tab (MOD-620).
 *
 * <p>Sent only to a player with that controller's screen open, at most once a second and only when it changed —
 * see {@code ReactorControllerMenu#broadcastChanges}. Its own packet rather than menu channels: a channel is a
 * signed short, and a room is up to a few hundred stacks of several numbers each.
 *
 * <p>Positions travel as offsets, never as world coordinates: the stacks from the zone's north-west corner, and
 * that corner from the controller. The corner's offset is what lets the tab keep the stack a player picked when a
 * bare pile loses its west-most rack and the corner moves.
 *
 * @param originDx the zone's west edge, in blocks east of the controller
 * @param originDz the zone's north edge, in blocks south of the controller
 * @param width    zone extent east-west, in stacks; 0 when there is no zone to show
 * @param depth    zone extent north-south
 * @param stacks   the stacks, north to south and then west to east, at most {@link ReactorZone#MAX_STACKS}
 */
public record ReactorZonePayload(int containerId, int originDx, int originDz, int width, int depth,
		List<ReactorZone.Stack> stacks) implements CustomPacketPayload {

	public static final Type<ReactorZonePayload> TYPE = new Type<>(Industrialization.id("reactor_zone"));

	/** A stack's tanks and faults (MOD-621), its own codec so the stack's stays within the twelve fields a composite takes. */
	private static final StreamCodec<ByteBuf, ReactorZone.Coolant> COOLANT_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, ReactorZone.Coolant::water,
			ByteBufCodecs.VAR_LONG, ReactorZone.Coolant::waterCapacity,
			ByteBufCodecs.VAR_LONG, ReactorZone.Coolant::steam,
			ByteBufCodecs.VAR_LONG, ReactorZone.Coolant::steamCapacity,
			ByteBufCodecs.BOOL, ReactorZone.Coolant::dry,
			ByteBufCodecs.BOOL, ReactorZone.Coolant::blocked,
			ReactorZone.Coolant::new);

	private static final StreamCodec<ByteBuf, ReactorZone.Stack> STACK_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ReactorZone.Stack::x,
			ByteBufCodecs.VAR_INT, ReactorZone.Stack::z,
			ByteBufCodecs.VAR_INT, ReactorZone.Stack::columns,
			ByteBufCodecs.VAR_INT, ReactorZone.Stack::fuelledRods,
			ByteBufCodecs.VAR_INT, ReactorZone.Stack::spentRods,
			ByteBufCodecs.VAR_INT, ReactorZone.Stack::averageWearPermille,
			ByteBufCodecs.VAR_INT, ReactorZone.Stack::worstWearPermille,
			ByteBufCodecs.VAR_LONG, ReactorZone.Stack::remainingEu,
			ByteBufCodecs.VAR_INT, ReactorZone.Stack::neighbours,
			COOLANT_CODEC, ReactorZone.Stack::coolant,
			ReactorZone.Stack::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, ReactorZonePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ReactorZonePayload::containerId,
			ByteBufCodecs.VAR_INT, ReactorZonePayload::originDx,
			ByteBufCodecs.VAR_INT, ReactorZonePayload::originDz,
			ByteBufCodecs.VAR_INT, ReactorZonePayload::width,
			ByteBufCodecs.VAR_INT, ReactorZonePayload::depth,
			STACK_CODEC.apply(ByteBufCodecs.list(ReactorZone.MAX_STACKS)), ReactorZonePayload::stacks,
			ReactorZonePayload::new);

	public ReactorZonePayload {
		stacks = List.copyOf(stacks);
	}

	@Override
	public Type<ReactorZonePayload> type() {
		return TYPE;
	}
}
