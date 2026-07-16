package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.item.TeleportPoint;
import dev.alaindustrial.item.TeleportPoints;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

/**
 * L2 suite for the point list and the screens' server side (MOD-093).
 *
 * <p>The rules here are what stands between a crafted packet and a player's data: index bounds, the
 * name cap, the list limit, and who may flip a station's privacy. The screens themselves (widgets,
 * layout) are checked by hand in the client — what is tested here is everything a malicious client
 * could aim at.
 */
public class TeleporterGuiGameTest {

	private static final BlockPos STATION = new BlockPos(1, 2, 1);
	private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

	/**
	 * The longest name the feature ever let a player set (MOD-093's original cap). Hard-coded on
	 * purpose — this is the historical floor the wire has to keep clearing, so it must not track any
	 * constant that a future edit could lower.
	 */
	private static final int LEGACY_MAX_NAME_LENGTH = 32;

	private static TeleportPoint point(GameTestHelper helper, int x, String name) {
		return new TeleportPoint(helper.getLevel().dimension(), new BlockPos(x, 64, 0), name);
	}

	/**
	 * @implements TC-TELE-003-SEC01 — index bounds are exact: a client asking for row -1 or row
	 *     "size" gets nothing, which is what keeps a crafted button id harmless.
	 */
	@GameTest
	public void tcTele003Sec01_indexBoundsAreExact(GameTestHelper helper) {
		TeleportPoints points = new TeleportPoints(List.of(point(helper, 1, "a"), point(helper, 2, "b")));
		if (points.isValidIndex(-1) || points.isValidIndex(2)) {
			helper.fail("out-of-range indices must be rejected (-1 and size must both fail)");
		}
		if (!points.isValidIndex(0) || !points.isValidIndex(1)) {
			helper.fail("real indices must be accepted");
		}
		if (points.get(9) != null) {
			helper.fail("get() past the end must be null, not a crash");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-003-SEC02 — a name from a client is clamped to the cap, never stored raw.
	 */
	@GameTest
	public void tcTele003Sec02_renameClampsName(GameTestHelper helper) {
		TeleportPoints points = new TeleportPoints(List.of(point(helper, 1, "a")));
		TeleportPoints renamed = points.renamed(0, "x".repeat(TeleportPoint.MAX_NAME_LENGTH + 20));
		if (renamed.get(0).name().length() != TeleportPoint.MAX_NAME_LENGTH) {
			helper.fail("an oversized name must be clamped, got " + renamed.get(0).name().length());
		}
		// A bad index must be a no-op, not an exception.
		if (points.renamed(7, "nope") != points) {
			helper.fail("renaming a non-existent row must change nothing");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-003-SEC04 — a name already saved at the wire limit still encodes. This is the
	 *     regression guard for a real world-breaker: lowering the input cap to 24 also lowered the wire
	 *     cap, and a remote named with the old 32 characters could then no longer be sent to the
	 *     client — vanilla encodes the player's inventory with this codec, so the encode threw and the
	 *     server dropped the connection on join. The wire limit is a floor over saved data, not a
	 *     design knob; this test fails the moment someone shrinks it again.
	 */
	@GameTest
	public void tcTele003Sec04_wireCarriesLegacyLengthNames(GameTestHelper helper) {
		if (TeleportPoint.NAME_WIRE_LIMIT < LEGACY_MAX_NAME_LENGTH) {
			helper.fail("the wire limit must never sit below " + LEGACY_MAX_NAME_LENGTH
					+ " — names that long exist in saves and could not be sent to a client");
		}
		// Deliberately the hard-coded historical number, NOT NAME_WIRE_LIMIT: a name built from the
		// constant under test would shrink along with it and the test would pass on the very bug it
		// exists to catch (it did, when first written this way).
		String legacy = "x".repeat(LEGACY_MAX_NAME_LENGTH);
		TeleportPoints points = new TeleportPoints(
				List.of(new TeleportPoint(helper.getLevel().dimension(), new BlockPos(1, 64, 1), legacy)));

		ByteBuf buf = Unpooled.buffer();
		try {
			TeleportPoints.STREAM_CODEC.encode(buf, points);
			TeleportPoints decoded = TeleportPoints.STREAM_CODEC.decode(buf);
			if (!legacy.equals(decoded.get(0).name())) {
				helper.fail("a wire-limit name must survive the round trip intact");
			}
		} catch (Exception e) {
			helper.fail("a saved name at the wire limit must still encode, got: " + e);
		} finally {
			buf.release();
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-003-FUN01 — the list is a value: add/remove return new instances and never
	 *     mutate the one already living in an ItemStack component.
	 */
	@GameTest
	public void tcTele003Fun01_listIsImmutable(GameTestHelper helper) {
		TeleportPoints original = new TeleportPoints(List.of(point(helper, 1, "a")));
		TeleportPoints added = original.with(point(helper, 2, "b"));
		if (original.size() != 1 || added.size() != 2) {
			helper.fail("with() must not touch the original: " + original.size() + "/" + added.size());
		}
		TeleportPoints removed = added.without(0);
		if (added.size() != 2 || removed.size() != 1 || !"b".equals(removed.get(0).name())) {
			helper.fail("without() must not touch its receiver, and must drop the right row");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-003-FUN02 — the same station cannot be bound twice, and a station in
	 *     another dimension with the same coordinates is a different station.
	 */
	@GameTest
	public void tcTele003Fun02_dedupeByDimAndPos(GameTestHelper helper) {
		BlockPos pos = new BlockPos(10, 64, 10);
		ResourceKey<Level> here = helper.getLevel().dimension();
		TeleportPoints points = new TeleportPoints(List.of(new TeleportPoint(here, pos, "home")));
		if (points.indexOf(here, pos) != 0) {
			helper.fail("the same dim+pos must be found as already bound");
		}
		if (points.indexOf(here, new BlockPos(11, 64, 10)) != -1) {
			helper.fail("a different position must not match");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-003-FUN03 — the list fills at exactly Config.teleporterMaxPoints.
	 */
	@GameTest
	public void tcTele003Fun03_limitIsTheConfiguredOne(GameTestHelper helper) {
		TeleportPoints points = TeleportPoints.EMPTY;
		for (int i = 0; i < Config.teleporterMaxPoints; i++) {
			if (points.isFull()) {
				helper.fail("list reported full early at " + i + "/" + Config.teleporterMaxPoints);
			}
			points = points.with(point(helper, i, "p" + i));
		}
		if (!points.isFull()) {
			helper.fail("list must be full at exactly " + Config.teleporterMaxPoints);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-003-FUN05 — auto-naming takes the lowest free number, reuses one that a
	 *     delete freed, and does not collide with a point renamed back to the default.
	 */
	@GameTest
	public void tcTele003Fun05_autoNamesTakeTheLowestFreeNumber(GameTestHelper helper) {
		ResourceKey<Level> here = helper.getLevel().dimension();
		TeleportPoints points = TeleportPoints.EMPTY;
		for (int i = 0; i < 3; i++) {
			points = points.with(new TeleportPoint(here, new BlockPos(i, 64, 0), "", points.nextFreeNumber()));
		}
		if (points.get(0).number() != 1 || points.get(1).number() != 2 || points.get(2).number() != 3) {
			helper.fail("fresh bindings must number 1,2,3 in order of binding");
		}

		// Deleting the middle row frees its number for the next binding rather than counting on.
		TeleportPoints afterDelete = points.without(1);
		if (afterDelete.nextFreeNumber() != 2) {
			helper.fail("a freed number must be reused, got " + afterDelete.nextFreeNumber());
		}

		// A named point releases its number too: only unnamed rows show one, so naming row 0 hands 1 back.
		TeleportPoints afterRename = points.renamed(0, "home");
		if (afterRename.nextFreeNumber() != 1) {
			helper.fail("a named row must release its number, got " + afterRename.nextFreeNumber());
		}
		if (!"home".equals(afterRename.get(0).name())) {
			helper.fail("renaming must store the player's name verbatim");
		}

		// Clearing the name must not revive number 1 blindly — nothing else holds it here, so it is 1,
		// but the point is that the number is re-derived rather than restored from the old record.
		TeleportPoints cleared = afterRename.renamed(0, "   ");
		if (!cleared.get(0).name().isEmpty()) {
			helper.fail("a blank rename must clear the name back to auto-naming");
		}
		if (cleared.get(0).number() != 1 || cleared.get(1).number() != 2) {
			helper.fail("re-cleared row must take the lowest free number without colliding, got "
					+ cleared.get(0).number());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-003-SEC03 — only the station's owner may flip its privacy. The screen greys
	 *     the button out, but this is the check that actually holds: a client ignoring the grey-out
	 *     changes nothing.
	 */
	@GameTest
	public void tcTele003Sec03_onlyOwnerTogglesPrivacy(GameTestHelper helper) {
		TeleporterBlockEntity station =
				AlaGameTestHelper.place(helper, STATION, ModBlocks.TELEPORTER, TeleporterBlockEntity.class);
		station.setOwner(STRANGER, "Someone");
		station.setPrivate(true);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		if (station.isOwner(player.getUUID())) {
			helper.fail("a stranger must not read as the owner");
		}
		if (!station.isOwner(STRANGER)) {
			helper.fail("the owner must read as the owner");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-003-FUN04 — the remote's menu reads the live stack from the hand, so what
	 *     the screen lists is whatever the player is actually holding.
	 */
	@GameTest
	public void tcTele003Fun04_menuReadsTheHeldRemote(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack remote = new ItemStack(ModContent.TELEPORTER_REMOTE.get());
		remote.set(ModDataComponents.TELEPORTER_POINTS.get(),
				new TeleportPoints(List.of(point(helper, 5, "home"))));
		player.setItemInHand(InteractionHand.MAIN_HAND, remote);

		TeleporterRemoteMenu menu = new TeleporterRemoteMenu(1, player.getInventory());
		if (menu.points().size() != 1 || !"home".equals(menu.points().get(0).name())) {
			helper.fail("the menu must read the points off the held remote");
		}

		// Empty hand: the menu must go quiet rather than keep editing a stack that is gone.
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (!menu.points().isEmpty()) {
			helper.fail("with no remote in hand the menu must list nothing");
		}
		if (menu.stillValid(player)) {
			helper.fail("the screen must close once the remote leaves the hand");
		}
		helper.succeed();
	}
}
