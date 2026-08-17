package dev.alaindustrial.gametest;

import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.teleporter.TeleportEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * L2 suite for the random jump (MOD-116, TC-TELE-004): fitting the chip, the gate it opens, and the
 * rule that a refused jump costs nothing.
 *
 * <p><b>What is deliberately NOT here: an actual 5000-block jump.</b> It cannot be driven from this
 * lane and pretending otherwise would produce a test that proves nothing. The rigs are 8×8×8 with one
 * chunk of force-loading; {@code GameTestServer} picks a random world origin per run, so "the surface
 * 5000 blocks out" is different every time; the per-test tick budget is shorter than the warmup alone;
 * and the lane's world is flat, so "landed on a safe surface" would be measuring the lane rather than
 * the feature. The search itself is therefore covered at L1 — {@code RtpSiteFinderTest} drives it
 * against a fake world — and what is exercised here is everything decidable from server state: the
 * chip, the policy gate, and the energy accounting.
 */
public final class RtpScenarios {

	private RtpScenarios() {
	}

	private static final BlockPos STATION = new BlockPos(1, 2, 1);
	/** Somewhere inside the rig: {@code executeRtp} only needs an in-bounds destination. */
	private static final BlockPos LANDING = new BlockPos(4, 2, 4);

	private static TeleporterBlockEntity station(GameTestHelper helper) {
		TeleporterBlockEntity station = AlaGameTestHelper.place(helper, STATION,
				ModContent.TELEPORTER.get(), TeleporterBlockEntity.class);
		// Public, so the mock player's own UUID never decides the outcome of a test about the module.
		station.setPrivate(false);
		return station;
	}

	private static TeleportPoint pointAt(GameTestHelper helper, BlockPos rel) {
		return new TeleportPoint(helper.getLevel().dimension(), helper.absolutePos(rel), "home");
	}

	/** Survival on purpose: {@code ItemStack#consume} leaves a creative player's stack alone. */
	private static ServerPlayer playerNearStation(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos near = helper.absolutePos(STATION.offset(3, 0, 0));
		player.snapTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		player.getInventory().clearContent();
		return player;
	}

	/**
	 * The random jump is Overworld-only, and that check runs before every other one — so a lane that
	 * stopped being the Overworld would turn every policy assertion below into a vacuous pass.
	 */
	private static void requireOverworld(GameTestHelper helper) {
		if (helper.getLevel().dimension() != Level.OVERWORLD) {
			helper.fail("this suite assumes the gametest lane runs in the Overworld; it is "
					+ helper.getLevel().dimension());
		}
	}

	/** Shift-right-click the station with whatever is in the player's main hand. */
	private static void useChipOnStation(GameTestHelper helper, ServerPlayer player) {
		BlockPos abs = helper.absolutePos(STATION);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		player.getItemInHand(InteractionHand.MAIN_HAND)
				.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	/** The visual marker's blockstate mirror — see {@link TeleporterBlock#UPGRADED}. */
	private static boolean markerShown(GameTestHelper helper) {
		return helper.getLevel().getBlockState(helper.absolutePos(STATION)).getValue(TeleporterBlock.UPGRADED);
	}

	/**
	 * @implements TC-TELE-004-FUN01 — fitting the chip arms the station, lights its visual marker and
	 *     eats exactly one chip; fitting a second one is refused without eating it.
	 */
	public static void tcTele004Fun01_chipFitsOnceAndIsConsumed(GameTestHelper helper) {
		TeleporterBlockEntity station = station(helper);
		ServerPlayer player = playerNearStation(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.RTP_CHIP.get(), 2));

		if (station.hasRtpModule()) {
			helper.fail("a freshly placed station must not already carry a module");
		}
		if (markerShown(helper)) {
			helper.fail("a freshly placed station must not wear the upgraded marker");
		}
		useChipOnStation(helper, player);

		if (!station.hasRtpModule()) {
			helper.fail("shift-right-clicking the station with a chip did not fit the module");
		}
		// The marker is only a mirror of the module, but a mirror that stops tracking is a lie the
		// player reads off the block, so it is asserted next to the thing it mirrors.
		if (!markerShown(helper)) {
			helper.fail("the module was fitted but the block still looks un-upgraded");
		}
		if (!station.hasRtpModule()) {
			helper.fail("lighting the marker must not have replaced the block entity");
		}
		int left = player.getItemInHand(InteractionHand.MAIN_HAND).getCount();
		if (left != 1) {
			helper.fail("fitting must consume exactly one chip: 2 -> " + left);
		}

		// Second attempt: the station is already armed, so the chip must survive.
		useChipOnStation(helper, player);
		int stillLeft = player.getItemInHand(InteractionHand.MAIN_HAND).getCount();
		if (stillLeft != 1) {
			helper.fail("fitting a chip to an armed station must not consume it: 1 -> " + stillLeft);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-004-NEG01 — without the module the jump is refused, with it the very same
	 *     station allows it.
	 */
	public static void tcTele004Neg01_moduleIsWhatOpensTheGate(GameTestHelper helper) {
		requireOverworld(helper);
		TeleporterBlockEntity station = station(helper);
		station.getEnergyStorage().setAmountUntracked(station.getEnergyStorage().getCapacity());
		ServerPlayer player = playerNearStation(helper);
		TeleportPoint point = pointAt(helper, STATION);

		// Compared with-and-without rather than against a re-derived expectation: the assertion is
		// about the module being the thing that decides, which a single-sided check cannot show.
		TeleportEngine.Denial without = TeleportEngine.checkRtpPolicy(player, point);
		if (without != TeleportEngine.Denial.RTP_NO_MODULE) {
			helper.fail("an un-upgraded station must refuse with RTP_NO_MODULE, got " + without);
		}

		station.setRtpModule(true);
		TeleportEngine.Denial with = TeleportEngine.checkRtpPolicy(player, point);
		if (!with.allowed()) {
			helper.fail("a fitted, charged, public station must allow the jump, got " + with);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-004-NRG01 — a refused random jump does not touch the station's EU.
	 *     The defect this guards against is the one MOD-092 wrote its own version of: energy
	 *     disappearing without the player moving.
	 */
	public static void tcTele004Nrg01_refusedJumpCostsNothing(GameTestHelper helper) {
		requireOverworld(helper);
		TeleporterBlockEntity station = station(helper);
		station.setRtpModule(true);
		long cost = TeleportEngine.rtpCost();
		long tooLittle = cost - 1;
		station.getEnergyStorage().setAmountUntracked(tooLittle);

		ServerPlayer player = playerNearStation(helper);
		Vec3 before = player.position();

		if (TeleportEngine.executeRtp(player, pointAt(helper, STATION), helper.absolutePos(LANDING), cost)) {
			helper.fail("a random jump must not fire when the station cannot pay for it");
		}
		if (station.getEnergyStorage().getAmount() != tooLittle) {
			helper.fail("a refused random jump must not touch the station's EU: " + tooLittle
					+ " -> " + station.getEnergyStorage().getAmount());
		}
		if (player.position().distanceToSqr(before) > 1.0E-6) {
			helper.fail("a refused random jump must not move the player");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-004-NRG02 — a successful random jump moves the player and charges exactly
	 *     the flat price, once.
	 */
	public static void tcTele004Nrg02_chargesExactlyTheFlatPrice(GameTestHelper helper) {
		requireOverworld(helper);
		TeleporterBlockEntity station = station(helper);
		station.setRtpModule(true);
		long capacity = station.getEnergyStorage().getCapacity();
		station.getEnergyStorage().setAmountUntracked(capacity);

		ServerPlayer player = playerNearStation(helper);
		BlockPos landing = helper.absolutePos(LANDING);
		long cost = TeleportEngine.rtpCost();

		if (!TeleportEngine.executeRtp(player, pointAt(helper, STATION), landing, cost)) {
			helper.fail("a fitted, fully charged station must be able to fire a random jump");
		}
		long spent = capacity - station.getEnergyStorage().getAmount();
		if (spent != cost) {
			helper.fail("a random jump must cost exactly " + cost + " EU, spent " + spent);
		}
		if (player.blockPosition().distSqr(landing) > 4) {
			helper.fail("the player did not arrive at the landing spot: " + player.blockPosition()
					+ " vs " + landing);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-004-STA01 — the module is persisted, so a chunk reload does not quietly
	 *     un-upgrade a station the player paid a whole crafting chain for.
	 */
	public static void tcTele004Sta01_moduleSurvivesAReload(GameTestHelper helper) {
		TeleporterBlockEntity station = station(helper);
		station.setRtpModule(true);

		BlockPos abs = helper.absolutePos(STATION);
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = station.saveCustomOnly(registries);
		TeleporterBlockEntity restored = new TeleporterBlockEntity(abs, helper.getLevel().getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (!restored.hasRtpModule()) {
			helper.fail("the module did not survive an NBT round trip");
		}

		// The other direction matters just as much: a station that never had one must not gain one.
		TeleporterBlockEntity plain = new TeleporterBlockEntity(abs, helper.getLevel().getBlockState(abs));
		CompoundTag plainTag = plain.saveCustomOnly(registries);
		TeleporterBlockEntity plainRestored = new TeleporterBlockEntity(abs, helper.getLevel().getBlockState(abs));
		plainRestored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, plainTag));
		if (plainRestored.hasRtpModule()) {
			helper.fail("a station with no module must not load one out of nowhere");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-004-BRK01 — a broken station carries its module on the dropped item, so
	 *     moving a base does not destroy the upgrade. Mirrors TC-TELE-001-BRK07 for EU and privacy.
	 */
	public static void tcTele004Brk01_dropCarriesTheModule(GameTestHelper helper) {
		TeleporterBlockEntity station = station(helper);
		station.setRtpModule(true);

		DataComponentMap map = station.collectComponents();
		if (!Boolean.TRUE.equals(map.get(ModDataComponents.TELEPORTER_RTP_MODULE.get()))) {
			helper.fail("the module flag was not emitted on drop: "
					+ map.get(ModDataComponents.TELEPORTER_RTP_MODULE.get()));
		}

		BlockPos worldPos = station.getBlockPos();
		TeleporterBlockEntity restored = new TeleporterBlockEntity(worldPos,
				helper.getLevel().getBlockState(worldPos));
		restored.applyComponents(map, DataComponentPatch.EMPTY);
		if (!restored.hasRtpModule()) {
			helper.fail("the module was not restored from the dropped item's components");
		}

		// An un-upgraded station must emit nothing at all, or its item would stop stacking with a
		// freshly crafted one.
		TeleporterBlockEntity plain = AlaGameTestHelper.place(helper, LANDING,
				ModContent.TELEPORTER.get(), TeleporterBlockEntity.class);
		if (plain.collectComponents().get(ModDataComponents.TELEPORTER_RTP_MODULE.get()) != null) {
			helper.fail("a station with no module must not write the component at all");
		}
		helper.succeed();
	}
}
