package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.core.teleport.RemoteLog;
import dev.alaindustrial.item.teleport.RemoteLogCodecs;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.item.teleport.TeleporterRemoteItem;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.teleporter.TeleportEngine;
import dev.alaindustrial.teleporter.TeleportLogs;
import dev.alaindustrial.teleporter.TeleportWarmupManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * L2 suite for the remote's log (MOD-631): every place the server writes a line, what a line carries, the read mark,
 * and that the log is part of the item.
 *
 * <p><b>The end of a warmup is not driven here.</b> Waiting it out would race {@code TeleportWarmupManager.clearAll()},
 * which another suite calls at its start, so the jump and its end-of-warmup refusal are covered through the writer they
 * call ({@link TeleportLogs}) and by the manual client pass, as the jump suite already does. Cancelling is driven for
 * real: it happens in the same call that starts the warmup.
 */
public final class TeleporterLogScenarios {

	private TeleporterLogScenarios() {
	}

	private static final BlockPos STATION = new BlockPos(1, 2, 1);

	/** An assembled station, as the jump suite builds it: a jump to a loose one is refused before the price. */
	private static TeleporterBlockEntity station(GameTestHelper helper) {
		TeleporterBlockEntity station =
				AlaGameTestHelper.place(helper, STATION, ModContent.TELEPORTER.get(), TeleporterBlockEntity.class);
		helper.setBlock(STATION.above(), Blocks.GLASS);
		helper.setBlock(STATION.above(2), Blocks.GLASS);
		TeleporterBlock.tryAssemble(helper.getLevel(), helper.absolutePos(STATION));
		return station;
	}

	private static TeleportPoint pointAt(GameTestHelper helper, String name) {
		return new TeleportPoint(helper.getLevel().dimension(), helper.absolutePos(STATION), name);
	}

	/** A player a few blocks from the station, a remote with {@code points} in the main hand and nothing else. */
	private static ServerPlayer playerWithRemote(GameTestHelper helper, TeleportPoint... points) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		BlockPos near = helper.absolutePos(STATION.offset(3, 0, 0));
		player.snapTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		player.getInventory().clearContent();
		ItemStack remote = new ItemStack(ModContent.TELEPORTER_REMOTE.get());
		remote.set(ModDataComponents.TELEPORTER_POINTS.get(), new TeleportPoints(List.of(points)));
		player.setItemInHand(InteractionHand.MAIN_HAND, remote);
		return player;
	}

	private static ItemStack held(ServerPlayer player) {
		return TeleporterRemoteItem.heldRemote(player);
	}

	private static RemoteLog log(ServerPlayer player) {
		return logOf(held(player));
	}

	private static RemoteLog logOf(ItemStack remote) {
		return remote.getOrDefault(ModDataComponents.TELEPORTER_LOG.get(), RemoteLog.EMPTY);
	}

	private static RemoteLog.Entry newest(GameTestHelper helper, ServerPlayer player) {
		List<RemoteLog.Entry> entries = log(player).newestFirst(null);
		if (entries.isEmpty()) {
			helper.fail("the remote's log is empty");
		}
		return entries.get(0);
	}

	/**
	 * @implements TC-TELE-007-FUN01 — binding a station with shift-right-click writes a line into the remote clicked
	 *     with: the station, how many are bound now and the most a remote holds.
	 */
	public static void tcTele007Fun01_bindingWritesALine(GameTestHelper helper) {
		station(helper);
		ServerPlayer player = playerWithRemote(helper);
		BlockPos abs = helper.absolutePos(STATION);
		player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
		RemoteLog log = log(player);
		if (log.entries().size() != 1) {
			helper.fail("binding must write exactly one line, the log holds " + log.entries().size());
		}
		RemoteLog.Entry entry = log.entries().get(0);
		if (entry.kind() != RemoteLog.Kind.BOUND || entry.a() != 1 || entry.b() != Math.max(1, Config.teleporterMaxPoints)
				|| entry.station().number() != 1 || !entry.station().name().isEmpty()) {
			helper.fail("expected 'Bound Teleporter 1 (1 / max)', got " + entry);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-007-FUN02 — a rename writes the old and the new name, a rename to the same name writes
	 *     nothing, and a delete writes the station that went.
	 */
	public static void tcTele007Fun02_renameAndDeleteWriteLines(GameTestHelper helper) {
		ServerPlayer player = playerWithRemote(helper, pointAt(helper, "home"), pointAt(helper, "mine"));
		TeleporterRemoteMenu menu = new TeleporterRemoteMenu(0, player.getInventory());
		menu.rename(player, 0, "base");
		menu.rename(player, 0, "base");
		if (!menu.clickMenuButton(player, TeleporterRemoteMenu.buttonId(TeleporterRemoteMenu.Action.DELETE, 1))) {
			helper.fail("the menu did not take the delete press");
		}
		List<RemoteLog.Entry> entries = log(player).entries();
		if (entries.size() != 2) {
			helper.fail("expected a rename and a delete, got " + entries);
		}
		RemoteLog.Entry renamed = entries.get(0);
		if (renamed.kind() != RemoteLog.Kind.RENAMED || !renamed.station().name().equals("home")
				|| !renamed.renamedTo().name().equals("base")) {
			helper.fail("expected 'Renamed home → base', got " + renamed);
		}
		RemoteLog.Entry deleted = entries.get(1);
		if (deleted.kind() != RemoteLog.Kind.DELETED || !deleted.station().name().equals("mine")) {
			helper.fail("expected 'Deleted mine', got " + deleted);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-007-FUN03 — a warmup broken by a hit or by walking away writes a grey line naming the kind of
	 *     jump into the remote the jump was started with — even when the player has put it in a pocket meanwhile — and
	 *     neither line lights the tab's badge. Fabric lane only: a cancelled warmup also clears its screen effect with
	 *     the mod's own packet, which NeoForge will not send to a mock player.
	 */
	public static void tcTele007Fun03_cancellationsWriteGreyLines(GameTestHelper helper) {
		TeleportPoint home = pointAt(helper, "home");
		ServerPlayer player = playerWithRemote(helper, home);
		TeleportWarmupManager.start(player, home);
		ItemStack pocketed = held(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.getInventory().setItem(20, pocketed);
		TeleportWarmupManager.cancelHurt(player);
		List<RemoteLog.Entry> lines = logOf(pocketed).newestFirst(null);
		if (lines.isEmpty() || lines.get(0).kind() != RemoteLog.Kind.CANCELLED_HURT || lines.get(0).random()) {
			helper.fail("a hit must write 'cancelled: you took damage' into the remote the jump started with, got " + lines);
		}
		if (TeleportWarmupManager.isWarming(player)) {
			helper.fail("the hit must also end the warmup");
		}
		player.getInventory().setItem(20, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.MAIN_HAND, pocketed);

		TeleportWarmupManager.startRtp(player, home, helper.absolutePos(STATION.above(5)));
		Vec3 away = player.position().add(Config.teleporterWarmupCancelRadius + 3, 0, 0);
		player.snapTo(away.x, away.y, away.z);
		TeleportWarmupManager.tickAll(helper.getLevel().getServer());
		RemoteLog.Entry moved = newest(helper, player);
		if (moved.kind() != RemoteLog.Kind.CANCELLED_MOVED || !moved.random()) {
			helper.fail("walking away must write 'cancelled: you moved' for a random jump, got " + moved);
		}
		if (log(player).hasRefusalAfter(0)) {
			helper.fail("a cancellation is the player's own doing and must not light the badge");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-007-FUN04 — the lines a jump and a refusal write carry their numbers: the exact price, the
	 *     landing column, the seconds left; a refusal the log does not name writes nothing.
	 */
	public static void tcTele007Fun04_jumpAndRefusalLinesCarryTheirNumbers(GameTestHelper helper) {
		TeleportPoint home = pointAt(helper, "home");
		ServerPlayer player = playerWithRemote(helper, home);
		TeleportLogs.jumped(player, held(player), home, 14_135);
		RemoteLog.Entry jumped = newest(helper, player);
		if (jumped.kind() != RemoteLog.Kind.JUMPED || jumped.a() != 14_135 || jumped.random()) {
			helper.fail("a jump must be written with its exact price, got " + jumped);
		}
		TeleportLogs.randomJumped(player, held(player), home, new BlockPos(2_310, 70, -880), 50_000);
		RemoteLog.Entry random = newest(helper, player);
		if (random.kind() != RemoteLog.Kind.RANDOM_JUMPED || random.a() != 2_310 || random.b() != -880
				|| random.c() != 50_000 || !random.random()) {
			helper.fail("a random jump must be written with its landing X and Z and its price, got " + random);
		}
		if (log(player).hasRefusalAfter(0)) {
			helper.fail("jumps must not light the badge");
		}
		int before = log(player).entries().size();
		TeleportLogs.refused(player, held(player), home, false, TeleportEngine.Denial.ALREADY_WARMING);
		if (log(player).entries().size() != before) {
			helper.fail("a second press mid-warmup is not a refusal worth a line");
		}
		TeleportLogs.refused(player, held(player), home, false, TeleportEngine.Denial.NOT_ENOUGH_EU);
		if (newest(helper, player).kind() != RemoteLog.Kind.REFUSED_NO_POWER) {
			helper.fail("an out-of-power refusal must be written as such, got " + newest(helper, player));
		}
		TeleportLogs.refusedCooldown(player, held(player), home, true, 38);
		RemoteLog.Entry cooldown = newest(helper, player);
		if (cooldown.kind() != RemoteLog.Kind.REFUSED_COOLDOWN || cooldown.a() != 38 || !cooldown.random()) {
			helper.fail("a recharge refusal must carry the seconds left, got " + cooldown);
		}
		if (!log(player).hasRefusalAfter(log(player).seenSeq())) {
			helper.fail("an unread refusal must light the badge");
		}
		for (TeleportEngine.Denial denial : TeleportEngine.Denial.values()) {
			RemoteLog.Kind kind = TeleportLogs.kindOf(denial);
			if (kind != null && !kind.isRefusal()) {
				helper.fail(denial + " is written as " + kind + ", which is not a refusal");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-007-FUN05 — a refusal repeated for the same station within ten seconds is one line, and the
	 *     fifty-first line pushes the oldest out of the remote.
	 */
	public static void tcTele007Fun05_repeatsMergeAndTheOldestGoes(GameTestHelper helper) {
		TeleportPoint home = pointAt(helper, "home");
		ServerPlayer player = playerWithRemote(helper, home);
		TeleportLogs.refused(player, held(player), home, false, TeleportEngine.Denial.NOT_FORMED);
		TeleportLogs.refused(player, held(player), home, false, TeleportEngine.Denial.NOT_FORMED);
		if (log(player).entries().size() != 1) {
			helper.fail("a burst of the same refusal must stay one line, got " + log(player).entries().size());
		}
		for (int i = 0; i < RemoteLog.CAPACITY; i++) {
			TeleportLogs.jumped(player, held(player), home, i);
		}
		RemoteLog log = log(player);
		if (log.entries().size() != RemoteLog.CAPACITY) {
			helper.fail("the remote keeps " + RemoteLog.CAPACITY + " lines, it holds " + log.entries().size());
		}
		if (log.entries().get(0).kind() != RemoteLog.Kind.JUMPED) {
			helper.fail("the refusal was the oldest line and had to go first");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-007-FUN06 — the screen's "read up to N" press moves the remote's read mark, puts the badge
	 *     out, and a forged number never runs past the newest line.
	 */
	public static void tcTele007Fun06_readMarkClearsTheBadge(GameTestHelper helper) {
		TeleportPoint home = pointAt(helper, "home");
		ServerPlayer player = playerWithRemote(helper, home);
		TeleportLogs.refused(player, held(player), home, false, TeleportEngine.Denial.NO_ACCESS);
		TeleportLogs.jumped(player, held(player), home, 100);
		int newest = log(player).newestSeq();
		TeleporterRemoteMenu menu = new TeleporterRemoteMenu(0, player.getInventory());
		if (!menu.clickMenuButton(player, TeleportLogs.SEEN_BUTTON + newest + 1_000)) {
			helper.fail("the menu did not take the read mark");
		}
		RemoteLog log = log(player);
		if (log.seenSeq() != newest) {
			helper.fail("a forged read mark must stop at the newest line " + newest + ", got " + log.seenSeq());
		}
		if (log.hasRefusalAfter(log.seenSeq())) {
			helper.fail("a read refusal must not light the badge");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-007-NEG01 — with no remote to write into nothing is written and nothing fails, and a remote
	 *     the event was not made with is left alone.
	 */
	public static void tcTele007Neg01_noRemoteWritesNothing(GameTestHelper helper) {
		TeleportPoint home = pointAt(helper, "home");
		ServerPlayer player = playerWithRemote(helper, home);
		ItemStack pocketed = held(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.getInventory().setItem(20, pocketed);
		TeleportLogs.jumped(player, ItemStack.EMPTY, home, 10);
		TeleportLogs.refused(player, ItemStack.EMPTY, home, true, TeleportEngine.Denial.RTP_NO_MODULE);
		TeleportLogs.cancelled(player, ItemStack.EMPTY, home, false, true);
		TeleportLogs.jumped(player, held(player), home, 10);
		if (pocketed.has(ModDataComponents.TELEPORTER_LOG.get())) {
			helper.fail("a remote the event was not made with must not be written to");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-007-STA01 — the log is part of the item: it survives the item being saved and loaded, and
	 *     the wire, unchanged.
	 */
	public static void tcTele007Sta01_logTravelsWithTheItem(GameTestHelper helper) {
		TeleportPoint home = pointAt(helper, "home");
		ServerPlayer player = playerWithRemote(helper, home);
		TeleportLogs.jumped(player, held(player), home, 7_160);
		TeleportLogs.refused(player, held(player), home, true, TeleportEngine.Denial.RTP_NO_SAFE_SPOT);
		TeleportLogs.renamed(player, held(player), home, pointAt(helper, "Sky island"));
		ItemStack remote = held(player);
		RemoteLog written = log(player);

		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, remote).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		if (!written.equals(loaded.get(ModDataComponents.TELEPORTER_LOG.get()))) {
			helper.fail("the log changed on a save and load: " + loaded.get(ModDataComponents.TELEPORTER_LOG.get()));
		}

		ByteBuf buf = Unpooled.buffer();
		try {
			RemoteLogCodecs.STREAM_CODEC.encode(buf, written);
			if (!written.equals(RemoteLogCodecs.STREAM_CODEC.decode(buf))) {
				helper.fail("the log changed on the wire");
			}
		} finally {
			buf.release();
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-007-FUN07 — a press the server refuses is written with the station it was about. Fabric lane
	 *     only: the refusal also goes out as the mod's notice packet, which NeoForge will not send to a mock player.
	 */
	public static void tcTele007Fun07_refusedPressWritesALine(GameTestHelper helper) {
		TeleporterBlockEntity station = station(helper);
		station.getEnergyStorage().setAmountUntracked(0);
		ServerPlayer player = playerWithRemote(helper, pointAt(helper, "home"));
		TeleporterRemoteMenu menu = new TeleporterRemoteMenu(0, player.getInventory());
		menu.clickMenuButton(player, TeleporterRemoteMenu.buttonId(TeleporterRemoteMenu.Action.TELEPORT, 0));
		RemoteLog.Entry entry = newest(helper, player);
		if (entry.kind() != RemoteLog.Kind.REFUSED_NO_POWER || !entry.station().name().equals("home") || entry.random()) {
			helper.fail("expected 'Refused: home is out of power', got " + entry);
		}
		if (TeleportWarmupManager.isWarming(player)) {
			TeleportWarmupManager.cancel(player);
			helper.fail("a refused press must not start a warmup");
		}
		helper.succeed();
	}
}
