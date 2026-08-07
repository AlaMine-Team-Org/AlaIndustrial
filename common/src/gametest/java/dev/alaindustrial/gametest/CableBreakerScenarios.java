package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.PipeBlock;

/**
 * L2 scenarios for the cable breaker (MOD-276): the maintenance switch that cuts a laid line out of
 * the grid without breaking the wire.
 *
 * <p>The rig is a straight run — generator → cable → <b>cable with the breaker</b> → cable → battery
 * box — so the switch sits mid-line with live grid on one side and the load on the other, which is
 * exactly how a player uses it. Assertions are made on the far cable and on the box, never on the
 * breaker's own segment: the point of the feature is what happens <em>downstream</em> of the switch.
 */
public final class CableBreakerScenarios {
	private static final BlockPos GENERATOR = new BlockPos(1, 2, 1);
	private static final BlockPos NEAR_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos BREAKER_CABLE = new BlockPos(3, 2, 1);
	private static final BlockPos FAR_CABLE = new BlockPos(4, 2, 1);
	private static final BlockPos BOX = new BlockPos(5, 2, 1);

	private CableBreakerScenarios() {
	}

	/** Generator → cable → cable → cable → battery box, all zeroed and registered. */
	private static void buildLine(GameTestHelper helper) {
		helper.setBlock(GENERATOR, ModContent.GENERATOR.get());
		helper.setBlock(NEAR_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(BREAKER_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(FAR_CABLE, ModContent.COPPER_CABLE.get());
		// FACING = WEST so the box's input face (MOD-006: input on FACING) meets the cable on its -x side.
		// Placed with the default facing it simply never charges, and every assertion below would read
		// that as "the breaker cut the line".
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		for (BlockPos pos : new BlockPos[] {GENERATOR, NEAR_CABLE, BREAKER_CABLE, FAR_CABLE, BOX}) {
			tick(helper, be(helper, pos));
		}
		if (be(helper, GENERATOR) instanceof GeneratorBlockEntity generator) {
			generator.getEnergyStorage().amount = 0;
			generator.setChanged();
		}
		if (be(helper, BOX) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().amount = 0;
			box.setChanged();
		}
	}

	/**
	 * Fill the generator and run the grid.
	 *
	 * <p>Thirty ticks, not the three the MOD-260 rig gets away with: energy crosses a cable run one
	 * segment buffer at a time (12 EU per copper segment — MOD-070), and this rig is three segments
	 * long, so a handful of ticks leaves the load at zero and every assertion below would read that as
	 * "the breaker cut the line".
	 */
	private static void energize(GameTestHelper helper) {
		if (be(helper, GENERATOR) instanceof GeneratorBlockEntity generator) {
			generator.getEnergyStorage().amount = Config.generatorBuffer;
			generator.setChanged();
		}
		for (int i = 0; i < 30; i++) {
			NetworkManager.tickAll(helper.getLevel());
		}
	}

	private static CableBlockEntity cable(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, CableBlockEntity.class);
	}

	/**
	 * An open breaker starves everything past it, and closing it again brings the line back.
	 *
	 * <p>Both halves are asserted in one scenario on purpose: "the line is dead" proves nothing unless
	 * the same rig is shown carrying energy before and after — a mis-built rig would report a dead line
	 * for the wrong reason and pass a one-sided test.
	 *
	 * @implements TC-BRK-001-NRG01 — an open breaker cuts the line; closing it restores flow.
	 */
	public static void tcBrk001Nrg01_openBreakerCutsTheLine(GameTestHelper helper) {
		buildLine(helper);
		CableBlockEntity breaker = cable(helper, BREAKER_CABLE);
		breaker.setBreakerInstalled(true);

		// Closed (the default on install): energy reaches the far side.
		energize(helper);
		if (!(be(helper, BOX) instanceof BatteryBoxBlockEntity box)) {
			helper.fail("battery box missing from the rig");
			return;
		}
		if (box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("a closed breaker must pass energy; the box never charged");
			return;
		}

		// Open it: the far segment leaves the grid with the breaker's own segment.
		box.getEnergyStorage().amount = 0;
		box.setChanged();
		breaker.setBreakerClosed(false);
		energize(helper);
		if (box.getEnergyStorage().getAmount() != 0) {
			helper.fail("an open breaker still fed the load: " + box.getEnergyStorage().getAmount() + " EU");
			return;
		}

		// Close it again: the segments re-join their neighbours and the load charges once more.
		breaker.setBreakerClosed(true);
		energize(helper);
		if (box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("closing the breaker did not restore the line — the segment never re-joined the grid");
			return;
		}
		helper.succeed();
	}

	/**
	 * The regression for the defect the fifth audit caught: {@code onServerTick} calls
	 * {@code ensureRegistered()} unconditionally every tick, so without a gate inside that method an
	 * open breaker would silently re-register its segment one tick after being thrown, and the switch
	 * would appear to work for exactly one tick.
	 *
	 * <p>Ticking the cables directly is what makes this bite: the scenario above only drives
	 * {@code NetworkManager.tickAll}, which never touches the per-cable tick path.
	 *
	 * @implements TC-BRK-001-STA01 — an open breaker stays open across cable ticks.
	 */
	public static void tcBrk001Sta01_openBreakerSurvivesCableTicks(GameTestHelper helper) {
		buildLine(helper);
		CableBlockEntity breaker = cable(helper, BREAKER_CABLE);
		breaker.setBreakerInstalled(true);
		breaker.setBreakerClosed(false);

		// The per-cable tick path — the one that re-registers a cable on load.
		for (int i = 0; i < 20; i++) {
			tick(helper, breaker);
			tick(helper, cable(helper, FAR_CABLE));
		}
		if (NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(BREAKER_CABLE)) != null) {
			helper.fail("an open breaker's segment re-registered itself with the grid");
			return;
		}
		energize(helper);
		if (be(helper, BOX) instanceof BatteryBoxBlockEntity box && box.getEnergyStorage().getAmount() != 0) {
			helper.fail("energy crossed an open breaker after cable ticks: "
					+ box.getEnergyStorage().getAmount() + " EU");
			return;
		}
		helper.succeed();
	}

	/**
	 * A bare wire past an open breaker must stop being a hazard — that is the entire reason the switch
	 * exists (MOD-260/MOD-269 shock, made safe for planned maintenance).
	 *
	 * <p>The live-line check comes first as the negative control: without it the scenario would pass
	 * just as green on a rig where the far cable was never energized at all.
	 *
	 * @implements TC-BRK-001-SEC01 — an open breaker de-energises the bare wire behind it.
	 */
	public static void tcBrk001Sec01_openBreakerDisarmsTheShock(GameTestHelper helper) {
		buildLine(helper);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		player.getAbilities().invulnerable = false;
		player.setInvulnerable(false);
		player.invulnerableTime = 0;
		// The player is deliberately left where the mock spawned. shouldShockPlayer's reach test is about
		// the insulating stand, not distance, so moving them proves nothing — and snapTo() on a mock
		// player dies in resetPosition() because it has no connection.

		CableBlock bare = (CableBlock) ModContent.COPPER_CABLE.get();
		CableBlockEntity breaker = cable(helper, BREAKER_CABLE);
		breaker.setBreakerInstalled(true);
		energize(helper);
		if (!bare.shouldShockPlayer(helper.getLevel(), helper.absolutePos(FAR_CABLE), player)) {
			helper.fail("negative control failed: the live far segment was not hazardous to begin with");
			return;
		}

		breaker.setBreakerClosed(false);
		// runAfterDelay, not another tickAll loop: isEnergizedForShock compares the last committed
		// transfer against level.getGameTime(), and driving the network by hand inside one server tick
		// never advances that clock — the flag would still read "live" no matter how many times we
		// ticked. Real ticks have to pass for it to age out.
		helper.runAfterDelay(3, () -> {
			player.invulnerableTime = 0;
			if (cable(helper, FAR_CABLE).isEnergizedForShock()) {
				helper.fail("the wire behind an open breaker still reads as energised");
				return;
			}
			if (bare.shouldShockPlayer(helper.getLevel(), helper.absolutePos(FAR_CABLE), player)) {
				helper.fail("a bare wire behind an open breaker still shocks the player");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * An open breaker must leave a <b>visible</b> gap, not a wire that merely looks whole while carrying
	 * nothing: the segment retracts its own six arms, and the neighbours retract theirs toward it.
	 *
	 * <p>This is the blockstate half of the feature (`BREAKER_OPEN`), and it is what the player actually
	 * reads on the wire — the network half is covered by the scenarios above. Closing the switch has to
	 * put every arm back, otherwise a maintenance session would permanently scar the run's look.
	 *
	 * @implements TC-BRK-001-VIS01 — an open breaker gaps the run; closing it re-joins the arms.
	 */
	public static void tcBrk001Vis01_openBreakerGapsTheRun(GameTestHelper helper) {
		buildLine(helper);
		CableBlockEntity breaker = cable(helper, BREAKER_CABLE);
		breaker.setBreakerInstalled(true);

		// Negative control: while closed, the run is drawn joined in both directions.
		if (!armTowards(helper, NEAR_CABLE, Direction.EAST) || !armTowards(helper, BREAKER_CABLE, Direction.WEST)) {
			helper.fail("negative control failed: the closed run was not drawn as connected to begin with");
			return;
		}

		breaker.setBreakerClosed(false);
		for (Direction dir : Direction.values()) {
			if (armTowards(helper, BREAKER_CABLE, dir)) {
				helper.fail("an open breaker's own segment still draws an arm " + dir);
				return;
			}
		}
		if (armTowards(helper, NEAR_CABLE, Direction.EAST) || armTowards(helper, FAR_CABLE, Direction.WEST)) {
			helper.fail("neighbours still draw arms into an open breaker — no visible gap");
			return;
		}

		breaker.setBreakerClosed(true);
		if (!armTowards(helper, BREAKER_CABLE, Direction.WEST) || !armTowards(helper, NEAR_CABLE, Direction.EAST)) {
			helper.fail("closing the breaker did not restore the arms — the run stays visually cut");
			return;
		}
		helper.succeed();
	}

	/** Whether the cable at {@code pos} draws its connection arm toward {@code dir}. */
	private static boolean armTowards(GameTestHelper helper, BlockPos pos, Direction dir) {
		return helper.getBlockState(pos).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir));
	}

	/**
	 * Removing the switch always restores the line, whatever position it was left in. Without this a
	 * player who pried off an open breaker would be left with a dead run and no switch to close.
	 *
	 * @implements TC-BRK-001-FUN01 — removing an open breaker rejoins the segment to the grid.
	 */
	public static void tcBrk001Fun01_removingBreakerRestoresTheLine(GameTestHelper helper) {
		buildLine(helper);
		CableBlockEntity breaker = cable(helper, BREAKER_CABLE);
		breaker.setBreakerInstalled(true);
		breaker.setBreakerClosed(false);
		energize(helper);

		breaker.setBreakerInstalled(false);
		if (breaker.hasBreaker() || breaker.isBreakerOpen()) {
			helper.fail("the breaker was not fully removed");
			return;
		}
		energize(helper);
		if (be(helper, BOX) instanceof BatteryBoxBlockEntity box && box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("removing the breaker left the line dead");
			return;
		}
		helper.succeed();
	}
}
