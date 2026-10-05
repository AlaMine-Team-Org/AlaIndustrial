package dev.alaindustrial.gametest;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * The reactor scenarios' rigs (MOD-713, TST-2): the smallest sealed room, the bare rig and the bare pile, and the
 * ways a scenario ticks a controller by hand — one place for every reactor scenario class, so a rig one mechanic
 * needs is not rebuilt beside the scenarios of another.
 *
 * <p>The room is the smallest the scan accepts: a 5x5x5 shell around a 3x3x3 interior, with the controller
 * standing in the middle of the west wall and looking in.
 */
final class ReactorRig {

	private ReactorRig() {}

	/** Shell spans 0…4 on every axis; the interior is 1…3. */
	static final int SHELL_MAX = 4;

	/** Middle of the west wall, the one face a controller can occupy in a room this size. */
	static final BlockPos CONTROLLER = new BlockPos(0, 2, 2);

	/** Interior floor, in the middle. */
	static final BlockPos COLUMN = new BlockPos(2, 1, 2);

	/** Interior cell in front of the controller — where a lever hangs on the wall from the inside. */
	static final BlockPos LEVER = new BlockPos(1, 2, 2);

	/** The smallest room the scan accepts, with the controller in the middle of the west wall. */
	static void buildRoom(GameTestHelper helper) {
		for (int x = 0; x <= SHELL_MAX; x++) {
			for (int y = 0; y <= SHELL_MAX; y++) {
				for (int z = 0; z <= SHELL_MAX; z++) {
					boolean shell = x == 0 || y == 0 || z == 0
							|| x == SHELL_MAX || y == SHELL_MAX || z == SHELL_MAX;
					BlockPos at = new BlockPos(x, y, z);
					if (!shell) {
						helper.setBlock(at, Blocks.AIR.defaultBlockState());
					} else if (!at.equals(CONTROLLER)) {
						helper.setBlock(at, ModContent.REACTOR_CASING.get().defaultBlockState());
					}
				}
			}
		}
		// WEST, not EAST: RoomValidator scans INWARD along facing.getOpposite(), so the property names
		// the way the controller's face looks OUT of the room. Getting this backwards is why the first
		// run of this scenario reported ROOM_UNBOUNDED — the scan walked away from the shell.
		helper.setBlock(CONTROLLER, ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
	}

	static ReactorControllerBlockEntity controller(GameTestHelper helper) {
		ReactorControllerBlockEntity brain =
				helper.getBlockEntity(CONTROLLER, ReactorControllerBlockEntity.class);
		if (brain == null) {
			helper.fail("reactor controller has no block entity");
			throw new IllegalStateException("unreachable");
		}
		return brain;
	}

	static FuelRodAssemblyBlockEntity placeColumn(GameTestHelper helper) {
		return placeColumnAt(helper, COLUMN);
	}

	static FuelRodAssemblyBlockEntity placeColumnAt(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState());
		FuelRodAssemblyBlockEntity column = helper.getBlockEntity(at, FuelRodAssemblyBlockEntity.class);
		if (column == null) {
			helper.fail("reactor column has no block entity at " + at);
			throw new IllegalStateException("unreachable");
		}
		return column;
	}

	/**
	 * Ticks the reactor with something actually drawing from it.
	 *
	 * <p>Without a load the buffer fills in about seven hundred ticks and the reactor stops — correctly,
	 * since idling costs no uranium — and a long run then measures a machine that spent most of it
	 * switched off. Any scenario about heat has to keep the buffer open.
	 */
	static void driveUnderLoad(GameTestHelper helper, ReactorControllerBlockEntity brain,
			int ticks) {
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		for (int i = 0; i < ticks; i++) {
			brain.serverTick(helper.getLevel(), absolute, helper.getBlockState(CONTROLLER));
			brain.getEnergyStorage().setAmountUntracked(0);
		}
	}

	/**
	 * Ticks the controller by hand. The room is rescanned on its own timer inside that tick, so the
	 * count has to clear {@code ReactorConfig.reactorScanIntervalTicks} for the shell to be recognised at all.
	 */
	static void drive(GameTestHelper helper, ReactorControllerBlockEntity brain, int ticks) {
		driveAt(helper, brain, CONTROLLER, ticks);
	}

	/**
	 * Drives a controller standing somewhere OTHER than the room rig's own wall slot.
	 *
	 * <p>The position has to be passed rather than assumed. {@link #drive} used to read the block state
	 * at {@link #CONTROLLER} unconditionally, so a bare-mode rig — which puts its controller in open
	 * ground, nowhere near that cell — handed the tick a state of {@code minecraft:air} and the scan
	 * died asking air which way it was facing.
	 */
	static void driveAt(GameTestHelper helper, ReactorControllerBlockEntity brain, BlockPos at,
			int ticks) {
		BlockPos absolute = helper.absolutePos(at);
		for (int i = 0; i < ticks; i++) {
			brain.serverTick(helper.getLevel(), absolute, helper.getBlockState(at));
		}
	}

	/** Ticks the room with its column topped up, so the core stays far from the meltdown line. */
	static void driveCooled(GameTestHelper helper, ReactorControllerBlockEntity brain,
			FuelRodAssemblyBlockEntity column, int ticks) {
		for (int i = 0; i < ticks; i++) {
			column.setTank(true, column.waterTank.capacity);
			driveUnderLoad(helper, brain, 1);
		}
	}

	// --- MOD-469: the bare rig ---

	/**
	 * Where the bare-mode rig stands, well inside the 8-block test structure.
	 *
	 * <p><b>Every radius in these scenarios is turned right down, and that is not tidiness.</b> The
	 * gametest grid puts neighbouring structures roughly thirteen blocks apart but only guarantees one
	 * block of cleared padding around each, so a scan that reaches out at the shipped radius of 8 would
	 * be reading — and melting — inside somebody else's test. The lesson is
	 * {@code wide-radius-scan-gametest-crosses-into-neighbours}, and it has bitten this repo before.
	 */
	static final BlockPos BARE_CONTROLLER = new BlockPos(3, 2, 3);
	static final BlockPos BARE_RACK = new BlockPos(3, 3, 3);
	static final BlockPos BARE_SIGNAL = new BlockPos(3, 1, 3);

	/**
	 * A wire on the controller's own east face.
	 *
	 * <p>A bare reactor has no shell, so it has no {@code reactor_outlet} either — its power leaves
	 * through the controller's own faces, every one of which publishes {@code OUT} except the screen.
	 * That claim is the whole "bare mode is a real generator" promise and it is worth a wire rather than
	 * a comment: MOD-468 shipped a reactor that produced, showed a figure, filled a buffer and could not
	 * be plugged into anything.
	 */
	static final BlockPos BARE_CABLE = new BlockPos(4, 2, 3);

	/** Melt reach used by the bare scenarios: one block, so the hazard cannot leave the rig. */
	static final int TEST_MELT_RADIUS = 1;

	/** The controller's west face, where the meltproof lever hangs. Inside the melt cube on purpose. */
	static final BlockPos BARE_LEVER = new BlockPos(2, 2, 3);

	/** Three more racks stacked on the bare rig's own, taking the pile past its equilibrium. */
	static final BlockPos[] BARE_EXTRA_RACKS = {
		new BlockPos(3, 4, 3), new BlockPos(2, 3, 3), new BlockPos(4, 3, 3),
	};

	/**
	 * A controller, a rack, a signal and a solid block of scenery to eat — no room anywhere.
	 *
	 * <p>The melt cube is packed with stone rather than left mostly empty so the picker cannot spend all
	 * of its attempts on air. A rig where the hazard only <em>usually</em> fires is a flaky test, and this
	 * repo has paid for those before.
	 */
	static ReactorControllerBlockEntity buildBareRig(GameTestHelper helper) {
		// Packed around the RACK, because that is what the hazard now radiates from (MOD-469). The
		// controller and the cable sit inside this cube deliberately — the controller to prove it is
		// exempt, the cable to prove the player's wiring is not. The redstone block is one block BELOW
		// the cube on purpose: it powers the reactor and must not be eaten mid-test.
		forEachMeltCell(helper, (x, y, z) ->
				helper.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState()));
		// No shell of any kind: the controller stands in the open, which is precisely the state the
		// scan must recognise. Facing is irrelevant here — bare mode never walks a wall.
		helper.setBlock(BARE_CONTROLLER, ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
		helper.setBlock(BARE_RACK, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState());
		helper.setBlock(BARE_SIGNAL, Blocks.REDSTONE_BLOCK.defaultBlockState());
		helper.setBlock(BARE_CABLE, ModContent.COPPER_CABLE.get().defaultBlockState());

		FuelRodAssemblyBlockEntity rack =
				helper.getBlockEntity(BARE_RACK, FuelRodAssemblyBlockEntity.class);
		if (rack == null) {
			helper.fail("bare fuel rack has no block entity");
			throw new IllegalStateException("unreachable");
		}
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			rack.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		ReactorControllerBlockEntity brain =
				helper.getBlockEntity(BARE_CONTROLLER, ReactorControllerBlockEntity.class);
		if (brain == null) {
			helper.fail("bare reactor controller has no block entity");
			throw new IllegalStateException("unreachable");
		}
		return brain;
	}

	/** Every cell the bare hazard can reach in this rig — the cube around the RACK, not the controller. */
	static void forEachMeltCell(GameTestHelper helper, CellAction action) {
		for (int x = BARE_RACK.getX() - TEST_MELT_RADIUS; x <= BARE_RACK.getX() + TEST_MELT_RADIUS; x++) {
			for (int y = BARE_RACK.getY() - TEST_MELT_RADIUS; y <= BARE_RACK.getY() + TEST_MELT_RADIUS; y++) {
				for (int z = BARE_RACK.getZ() - TEST_MELT_RADIUS; z <= BARE_RACK.getZ() + TEST_MELT_RADIUS; z++) {
					action.at(x, y, z);
				}
			}
		}
	}

	@FunctionalInterface
	interface CellAction {
		void at(int x, int y, int z);
	}

	/** Total durability spent across a rack's rods — the ledger the lava-farm pin watches. */
	static int totalDamage(List<ItemStack> stacks) {
		int damage = 0;
		for (ItemStack stack : stacks) {
			damage += stack.getDamageValue();
		}
		return damage;
	}

	// --- MOD-713: the bare pile of the characterisation scenarios ---

	/** The bare pile: the bare rig's own rack and the three extra ones — four racks of four rods. */
	static final List<BlockPos> BARE_PILE_RACKS =
			List.of(BARE_RACK, BARE_EXTRA_RACKS[0], BARE_EXTRA_RACKS[1], BARE_EXTRA_RACKS[2]);

	/**
	 * The two knobs the bare pile needs, held for one synchronous call: the walk reaches the four racks and no
	 * further (the shipped radius reaches into the neighbouring tests), and the scenery hazard never draws a victim
	 * from the shared random source.
	 */
	static ConfigOverrides barePileOverrides() {
		return ConfigOverrides.sync().set("reactorBareSearchRadius", 2).set("reactorBareMeltRadius", 0);
	}

	/** The bare pile: a controller in the open, four racks of four rods on it, a redstone block under it. */
	static ReactorControllerBlockEntity buildBarePile(GameTestHelper helper) {
		helper.setBlock(BARE_CONTROLLER, ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
		for (BlockPos at : BARE_PILE_RACKS) {
			fuel(placeColumnAt(helper, at));
		}
		helper.setBlock(BARE_SIGNAL, Blocks.REDSTONE_BLOCK.defaultBlockState());
		ReactorControllerBlockEntity brain =
				helper.getBlockEntity(BARE_CONTROLLER, ReactorControllerBlockEntity.class);
		if (brain == null) {
			helper.fail("the bare pile's controller has no block entity");
			throw new IllegalStateException("unreachable");
		}
		return brain;
	}

	/** Ticks a controller standing at {@link #BARE_CONTROLLER} with its buffer drained after every tick. */
	static void driveBareUnderLoad(GameTestHelper helper, ReactorControllerBlockEntity brain, int ticks) {
		BlockPos absolute = helper.absolutePos(BARE_CONTROLLER);
		for (int i = 0; i < ticks; i++) {
			brain.serverTick(helper.getLevel(), absolute, helper.getBlockState(BARE_CONTROLLER));
			brain.getEnergyStorage().setAmountUntracked(0);
		}
	}

	/** Racks four fresh rods. */
	static void fuel(FuelRodAssemblyBlockEntity column) {
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
	}
}
