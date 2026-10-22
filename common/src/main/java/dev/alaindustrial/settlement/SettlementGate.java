package dev.alaindustrial.settlement;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.chat.ModChat;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The world's side of {@link SettlementAccess} (owner's round 3, point 3; epic decision R98): asks the rule for a
 * player and a town, and tells a stranger, in chat, that the town does not know them. Every open path of a town's
 * machines (the settlement core, the reception capsule, the town dispatcher, the drone nests), every menu's
 * {@code stillValid} and every request handler that reads a town's data goes through here, so the rule has one door.
 *
 * <p>An empty town (a machine that found none, a core of no town) is open to everybody, as a town nobody has woken.
 * An operator in creative is an inspector ({@link Player#canUseGameMasterBlocks}): admitted to look, logged once per
 * opening, controlling nothing.
 */
public final class SettlementGate {

	/** The chat line of a stranger who tries a town's machine: the core does not recognise them. */
	public static final String STRANGER_MESSAGE_KEY = "settlement.alaindustrial.access.stranger";

	private SettlementGate() {
	}

	/** What {@code player} is to {@code town}; an empty town is one nobody has woken. */
	public static SettlementAccess.Standing standing(Optional<Settlement> town, Player player) {
		return standingByRestorer(town.flatMap(Settlement::owner), player);
	}

	/** What {@code player} is to a town woken by {@code restorer}; the client reads the restorer from an update tag. */
	public static SettlementAccess.Standing standingByRestorer(Optional<UUID> restorer, Player player) {
		return SettlementAccess.standing(restorer, player.getUUID(), player.canUseGameMasterBlocks());
	}

	/** Whether {@code player} may open and read the machines of {@code town}. */
	public static boolean admits(Optional<Settlement> town, Player player) {
		return standing(town, player).admitted();
	}

	/** Whether {@code player} may change anything in {@code town}: its members, or anybody before it is woken. */
	public static boolean controls(Optional<Settlement> town, Player player) {
		return standing(town, player).controls();
	}

	/**
	 * Server side, on opening one of the town's machines: whether {@code player} may come in. A stranger hears why not
	 * in chat and is refused; an inspector's look is written to the log.
	 */
	public static boolean admitOrTell(Optional<Settlement> town, Player player) {
		SettlementAccess.Standing standing = standing(town, player);
		if (!standing.admitted()) {
			tellStranger(player);
			return false;
		}
		if (standing == SettlementAccess.Standing.INSPECTOR) {
			// INFO on purpose: an operator looking into a player's town is worth a line for the server's owner.
			Industrialization.LOGGER.info("[ala-towns] operator {} inspects settlement {}",
					player.getGameProfile().name(), town.map(Settlement::id).orElse(0));
		}
		return true;
	}

	/** Tells {@code player} in chat that the town does not know them; nothing on a client. */
	public static void tellStranger(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(ModChat.line(Component.translatable(STRANGER_MESSAGE_KEY)), false);
		}
	}
}
