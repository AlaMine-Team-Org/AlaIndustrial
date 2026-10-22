package dev.alaindustrial.settlement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who may open, use and read an ALA town's machines (owner's round 3, point 3; epic MOD-824, decision R98): the
 * settlement core, the reception capsule, the town dispatcher, the drone nests and everything that shows the town's
 * data. The one rule every block, menu and request handler of a town asks; nothing here touches the world.
 *
 * <p><b>The rule.</b> A town nobody has woken yet is open to everybody, so the first player who opens its lit core
 * still wakes it and becomes its restorer (R37). A woken town is open to its members: today the restorer alone (the
 * registry's {@code owner} field, kept under that name so old saves load), tomorrow the members a clan stage adds:
 * they plug into {@code members} and change nothing else. Everybody else is a stranger and is turned away.
 *
 * <p><b>Inspectors.</b> An operator in creative ({@code Player.canUseGameMasterBlocks}) may open and read every machine
 * of a woken town, as support needs to look at a player's town, but controls nothing there: the plan, the buffer, the
 * start, the capsule's choice and the workers' cards stay the members'. An operator can rewrite the town with commands
 * anyway; the screens only stop an inspection from changing the town by a slip of the hand. The caller decides who is
 * an inspector; this class only says what that means.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks the rule directly ({@code SettlementAccessTest}).
 */
public final class SettlementAccess {

	private SettlementAccess() {
	}

	/** What a player is to a town. */
	public enum Standing {
		/** Nobody has woken the town yet: everybody may come in, and the first to open the lit core wakes it. */
		UNCLAIMED,
		/** The restorer, or a member a later stage adds. */
		MEMBER,
		/** An operator in creative, looking in. */
		INSPECTOR,
		/** Anybody else: the town's machines do not answer them. */
		STRANGER;

		/** Whether a player of this standing may open the town's machines and read them. */
		public boolean admitted() {
			return this != STRANGER;
		}

		/** Whether a player of this standing may change anything: put and take, press, start, rename. */
		public boolean controls() {
			return this == UNCLAIMED || this == MEMBER;
		}
	}

	/** What {@code player} is to a town woken by {@code restorer} (empty: not woken yet), with no other members. */
	public static Standing standing(Optional<UUID> restorer, UUID player, boolean inspector) {
		return standing(restorer, List.of(), player, inspector);
	}

	/**
	 * What {@code player} is to a town woken by {@code restorer} (empty: not woken yet) whose other members are
	 * {@code members}, when the caller counts them as an {@code inspector} or not.
	 */
	public static Standing standing(Optional<UUID> restorer, Collection<UUID> members, UUID player,
			boolean inspector) {
		if (restorer.isEmpty()) {
			return Standing.UNCLAIMED;
		}
		if (restorer.get().equals(player) || members.contains(player)) {
			return Standing.MEMBER;
		}
		return inspector ? Standing.INSPECTOR : Standing.STRANGER;
	}

	/** Whether {@code player} may open and read the machines of a town woken by {@code restorer}. */
	public static boolean admits(Optional<UUID> restorer, UUID player, boolean inspector) {
		return standing(restorer, player, inspector).admitted();
	}

	/** Whether {@code player} may change anything in a town woken by {@code restorer}: members, or anyone before. */
	public static boolean controls(Optional<UUID> restorer, UUID player) {
		return standing(restorer, player, false).controls();
	}
}
