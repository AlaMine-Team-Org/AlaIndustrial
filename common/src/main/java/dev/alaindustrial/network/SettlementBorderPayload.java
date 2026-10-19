package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.SettlementTerritory;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * S2C: a settlement's border for the "show borders" button of its core's screen (MOD-843) — sent to exactly ONE
 * player, the one looking at the screen. Same shape as {@link ZonePreviewPayload}: the server states the town's own
 * territory and stays stateless about who is looking at what; which borders a player sees is client state.
 *
 * <ul>
 *   <li>{@code toggle = true} — the player pressed the button: show the border if hidden, hide it if shown.</li>
 *   <li>{@code toggle = false} — the player just opened the core's screen: nothing is shown or hidden, the client
 *       only learns which town the open screen belongs to, so the button can show its state (a client menu has no
 *       block position of its own).</li>
 * </ul>
 *
 * @param core      the town's core position — the key of the shown border and the height the shell stands at
 *                  ({@code Settlement.corePos}), which for a second core (R43) is the other core, not the one whose
 *                  screen is open
 * @param territory the town's territory ({@code Settlement.territory()}, MOD-848: a structure's town stretches over
 *                  its buildings), or empty for a core of no town (R53): there is no border to show
 */
public record SettlementBorderPayload(ResourceKey<Level> dimension, BlockPos core,
		Optional<SettlementTerritory.Area> territory, boolean toggle) implements CustomPacketPayload {

	public static final Type<SettlementBorderPayload> TYPE = new Type<>(Industrialization.id("settlement_border"));

	/** The territory's four inclusive bounds; a world coordinate fits a var-int. */
	private static final StreamCodec<RegistryFriendlyByteBuf, SettlementTerritory.Area> AREA_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, SettlementTerritory.Area::minX,
					ByteBufCodecs.VAR_INT, SettlementTerritory.Area::minZ,
					ByteBufCodecs.VAR_INT, SettlementTerritory.Area::maxX,
					ByteBufCodecs.VAR_INT, SettlementTerritory.Area::maxZ,
					SettlementTerritory.Area::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, SettlementBorderPayload> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), SettlementBorderPayload::dimension,
			BlockPos.STREAM_CODEC, SettlementBorderPayload::core,
			ByteBufCodecs.optional(AREA_CODEC), SettlementBorderPayload::territory,
			ByteBufCodecs.BOOL, SettlementBorderPayload::toggle,
			SettlementBorderPayload::new);

	/** Send {@code player} the border of the town whose core is at {@code core}. */
	public static void send(ServerPlayer player, ResourceKey<Level> dimension, BlockPos core,
			Optional<SettlementTerritory.Area> territory, boolean toggle) {
		// The opened screen announces the border on its own, not as a reply to a press, so it must ask first:
		// NeoForge throws for a connection that never negotiated the channel, which every gametest mock is.
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (!dispatcher.canSendToPlayer(player, TYPE)) {
			return;
		}
		dispatcher.sendToPlayer(player, new SettlementBorderPayload(dimension, core, territory, toggle));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
