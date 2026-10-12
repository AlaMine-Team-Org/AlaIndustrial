package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.VacuumCollectorBlock;
import dev.alaindustrial.block.entity.VacuumCollectorBlockEntity;
import dev.alaindustrial.block.entity.VacuumCollectorBlockEntity.Action;
import dev.alaindustrial.block.entity.VacuumCollectorBlockEntity.Status;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametests of the Vacuum Collector, tier I (MOD-802, suite TC-VACCOL-001), run by both loaders
 * from the roster.
 *
 * <p><b>Two kinds of test, like the electromagnet's.</b> What the collector does to ONE drop is driven through
 * {@link VacuumCollectorBlockEntity#collectSingle} on a <em>detached</em> {@link ItemEntity} — never added to
 * the world, so a neighbouring test's drops cannot interfere. What a pulse chooses (the nearest, at most a
 * pulse's worth, paid once) needs the world scan, so those tests spawn drops beside the block and keep the
 * collector's buffer at exactly the energy the pulses under test need: with nothing left to pay with, a
 * collector cannot reach into another test's arena and take its drops.
 */
public final class VacuumCollectorScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(VacuumCollectorScenarios::takesATouchingDrop, "mod802_takes_a_touching_drop"),
				RosterEntry.of(VacuumCollectorScenarios::pullsADropInReach, "mod802_pulls_a_drop_in_reach"),
				RosterEntry.of(VacuumCollectorScenarios::reachIsACube, "mod802_reach_is_a_cube"),
				RosterEntry.of(VacuumCollectorScenarios::leavesADropOnItsDelay, "mod802_leaves_a_drop_on_its_delay"),
				RosterEntry.of(VacuumCollectorScenarios::fullBufferTakesNothing, "mod802_full_buffer_takes_nothing"),
				RosterEntry.of(VacuumCollectorScenarios::partlyFreeBufferLeavesTheRest,
						"mod802_partly_free_buffer_leaves_the_rest"),
				RosterEntry.of(VacuumCollectorScenarios::twoCollectorsShareNoDrop,
						"mod802_two_collectors_share_no_drop"),
				RosterEntry.of(VacuumCollectorScenarios::nothingWithoutEnergy, "mod802_nothing_without_energy")
						.ticks(20, 40),
				RosterEntry.of(VacuumCollectorScenarios::onePulseActsOnTheNearestFour,
						"mod802_one_pulse_acts_on_the_nearest_four").ticks(20, 40),
				RosterEntry.of(VacuumCollectorScenarios::pulsesFiveTicksApart, "mod802_pulses_five_ticks_apart")
						.ticks(60, 60),
				RosterEntry.of(VacuumCollectorScenarios::pipePullsTheBufferOut, "mod802_pipe_pulls_the_buffer_out"),
				RosterEntry.of(VacuumCollectorScenarios::pipeOnASideGetsNothing, "mod802_pipe_on_a_side_gets_nothing"),
				RosterEntry.of(VacuumCollectorScenarios::cablesOnlyOnTopAndBottom,
						"mod802_cables_only_on_top_and_bottom"),
				RosterEntry.of(VacuumCollectorScenarios::bufferSurvivesReload, "mod802_buffer_survives_reload"),
				RosterEntry.of(VacuumCollectorScenarios::nothingGoesIn, "mod802_nothing_goes_in"));

		private Roster() {}
	}

	private VacuumCollectorScenarios() {}

	private static final BlockPos COLLECTOR = new BlockPos(2, 2, 2);

	private static VacuumCollectorBlockEntity place(GameTestHelper helper, BlockPos pos) {
		return AlaGameTestHelper.place(helper, pos, ModContent.VACUUM_COLLECTOR.get(),
				VacuumCollectorBlockEntity.class);
	}

	private static long pulseCost() {
		return (long) Math.max(1, Config.vacuumCollectorEuPerTick) * Math.max(1, Config.vacuumCollectorPulseTicks);
	}

	/** A detached drop of {@code stack}, {@code (dx, dy, dz)} from the collector's centre, still, past its delay. */
	private static ItemEntity detached(GameTestHelper helper, VacuumCollectorBlockEntity be, double dx, double dy,
			double dz, ItemStack stack) {
		Vec3 c = Vec3.atCenterOf(be.getBlockPos());
		ItemEntity item = new ItemEntity(helper.getLevel(), c.x + dx, c.y + dy, c.z + dz, stack);
		item.setDeltaMovement(Vec3.ZERO);
		item.setPickUpDelay(0);
		return item;
	}

	/** A drop added to the world, resting on the collector's lid, touching it. */
	private static ItemEntity spawnOnLid(GameTestHelper helper, VacuumCollectorBlockEntity be, double dx, double dz,
			ItemStack stack) {
		Vec3 c = Vec3.atCenterOf(be.getBlockPos());
		ItemEntity item = new ItemEntity(helper.getLevel(), c.x + dx, c.y + 0.5, c.z + dz, stack);
		item.setDeltaMovement(Vec3.ZERO);
		item.setPickUpDelay(0);
		helper.getLevel().addFreshEntity(item);
		return item;
	}

	/** Eight different items: drops of one item lying side by side merge into one entity on their own. */
	private static final List<Item> DISTINCT = List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
			Items.COAL, Items.DIAMOND, Items.EMERALD, Items.REDSTONE, Items.LAPIS_LAZULI);

	/** Every item in the buffer, whatever it is. */
	private static int total(Container container) {
		int n = 0;
		for (int slot = 0; slot < VacuumCollectorBlockEntity.SLOT_COUNT; slot++) {
			n += container.getItem(slot).getCount();
		}
		return n;
	}

	private static int count(Container container, Item item) {
		int n = 0;
		for (int slot = 0; slot < VacuumCollectorBlockEntity.SLOT_COUNT; slot++) {
			if (container.getItem(slot).is(item)) {
				n += container.getItem(slot).getCount();
			}
		}
		return n;
	}

	/**
	 * @implements TC-VACCOL-001-FUN01 — a drop touching the block is taken whole: its items are in the buffer
	 *     and the entity is gone.
	 */
	public static void takesATouchingDrop(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		ItemEntity drop = detached(helper, be, 0.6, -0.3, 0.0, new ItemStack(Items.IRON_INGOT, 10));

		Action action = be.collectSingle(drop);

		helper.assertTrue(action == Action.TAKEN, "a drop resting against a face must be taken, got " + action);
		helper.assertTrue(count(be, Items.IRON_INGOT) == 10, "all ten ingots must be in the buffer");
		helper.assertFalse(drop.isAlive(), "an emptied drop must be gone");
		helper.succeed();
	}

	/**
	 * @implements TC-VACCOL-001-FUN02 — a drop further out is set moving towards the block, not teleported:
	 *     its position is unchanged, its velocity points at the centre, and the buffer is untouched.
	 */
	public static void pullsADropInReach(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		ItemEntity drop = detached(helper, be, 3.0, -0.4, 0.0, new ItemStack(Items.IRON_INGOT, 4));
		Vec3 before = drop.position();

		Action action = be.collectSingle(drop);

		helper.assertTrue(action == Action.PULLED, "a drop three blocks out must be pulled, got " + action);
		helper.assertTrue(drop.position().equals(before), "a pull moves the drop by physics, never by teleport");
		helper.assertTrue(drop.getDeltaMovement().x < 0.0, "the pull must point at the block (-x), got "
				+ drop.getDeltaMovement());
		helper.assertTrue(be.isEmpty(), "a pulled drop is not in the buffer yet");
		helper.succeed();
	}

	/**
	 * @implements TC-VACCOL-001-FUN03 — the reach is a cube around the centre: a corner of the cube is in
	 *     reach (a sphere would refuse it), one block past a face is not.
	 */
	public static void reachIsACube(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		double r = Config.vacuumCollectorRadius;
		ItemEntity corner = detached(helper, be, r - 0.1, r - 0.1, r - 0.1, new ItemStack(Items.IRON_INGOT));
		ItemEntity beyond = detached(helper, be, r + 0.4, 0.0, 0.0, new ItemStack(Items.IRON_INGOT));

		helper.assertTrue(be.collectSingle(corner) == Action.PULLED, "a corner of the cube must be in reach");
		helper.assertTrue(be.collectSingle(beyond) == Action.NONE, "a drop past the cube's face must be left alone");
		helper.assertTrue(beyond.getDeltaMovement().equals(Vec3.ZERO), "a drop out of reach must not move");
		helper.succeed();
	}

	/**
	 * @implements TC-VACCOL-001-NEG01 — a drop on its pickup delay is left alone, and the collector changes
	 *     neither the delay nor the drop's age.
	 */
	public static void leavesADropOnItsDelay(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		ItemEntity drop = detached(helper, be, 0.6, -0.3, 0.0, new ItemStack(Items.IRON_INGOT, 3));
		drop.setPickUpDelay(40);
		int age = drop.getAge();

		Action action = be.collectSingle(drop);

		helper.assertTrue(action == Action.NONE, "a drop on its pickup delay must not be taken, got " + action);
		helper.assertTrue(drop.hasPickUpDelay(), "the delay must be left as it was");
		helper.assertTrue(drop.getAge() == age, "the drop's age (its lifetime) must not change");
		helper.assertTrue(drop.isAlive() && drop.getItem().getCount() == 3, "the drop must stay whole");
		helper.assertTrue(be.isEmpty(), "nothing may reach the buffer");
		helper.succeed();
	}

	/** @implements TC-VACCOL-001-NEG02 — with no room for the drop, the collector neither takes nor pulls it. */
	public static void fullBufferTakesNothing(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		for (int slot = 0; slot < VacuumCollectorBlockEntity.SLOT_COUNT; slot++) {
			be.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		ItemEntity touching = detached(helper, be, 0.6, -0.3, 0.0, new ItemStack(Items.IRON_INGOT, 5));
		ItemEntity far = detached(helper, be, 3.0, -0.4, 0.0, new ItemStack(Items.IRON_INGOT, 5));

		helper.assertTrue(be.collectSingle(touching) == Action.NONE, "a full buffer must take nothing");
		helper.assertTrue(be.collectSingle(far) == Action.NONE, "a full buffer must not pull either");
		helper.assertTrue(touching.isAlive() && touching.getItem().getCount() == 5, "the drop must stay whole");
		helper.assertTrue(far.getDeltaMovement().equals(Vec3.ZERO), "a drop it cannot take must not move");
		helper.succeed();
	}

	/**
	 * @implements TC-VACCOL-001-FUN04 — with room for part of a drop, exactly that part is taken and the rest
	 *     stays in the world as the same entity: no item is created or lost.
	 */
	public static void partlyFreeBufferLeavesTheRest(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		for (int slot = 0; slot < VacuumCollectorBlockEntity.SLOT_COUNT - 1; slot++) {
			be.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		be.setItem(VacuumCollectorBlockEntity.SLOT_COUNT - 1, new ItemStack(Items.IRON_INGOT, 60));
		ItemEntity drop = detached(helper, be, 0.6, -0.3, 0.0, new ItemStack(Items.IRON_INGOT, 10));

		Action action = be.collectSingle(drop);

		helper.assertTrue(action == Action.TAKEN, "a drop that partly fits must be taken in part, got " + action);
		helper.assertTrue(count(be, Items.IRON_INGOT) == 64, "the last slot must be topped up to 64, got "
				+ count(be, Items.IRON_INGOT));
		helper.assertTrue(drop.isAlive() && drop.getItem().getCount() == 6,
				"the six ingots that did not fit must stay in the same drop, got " + drop.getItem());
		helper.assertTrue(count(be, Items.IRON_INGOT) + drop.getItem().getCount() == 70,
				"seventy ingots before and after");
		helper.succeed();
	}

	/**
	 * @implements TC-VACCOL-001-FUN05 — two collectors side by side: a drop touching both is taken by one,
	 *     whole, and the other gets nothing — no item is counted twice.
	 */
	public static void twoCollectorsShareNoDrop(GameTestHelper helper) {
		VacuumCollectorBlockEntity a = place(helper, COLLECTOR);
		VacuumCollectorBlockEntity b = place(helper, COLLECTOR.east());
		ItemEntity drop = detached(helper, a, 0.5, 0.6, 0.0, new ItemStack(Items.IRON_INGOT, 8));

		Action first = a.collectSingle(drop);
		Action second = b.collectSingle(drop);

		helper.assertTrue(first == Action.TAKEN && second == Action.NONE,
				"the first collector takes the drop, the second finds nothing; got " + first + ", " + second);
		helper.assertTrue(count(a, Items.IRON_INGOT) == 8 && b.isEmpty(), "eight ingots, all in the first collector");
		helper.succeed();
	}

	/** @implements TC-VACCOL-001-NEG03 — with no energy a pulse does nothing: the drops stay, the buffer is empty. */
	public static void nothingWithoutEnergy(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		be.getEnergyStorage().setAmountUntracked(0);
		ItemEntity drop = spawnOnLid(helper, be, 0.0, 0.0, new ItemStack(Items.IRON_INGOT, 3));
		helper.runAfterDelay(2, () -> {
			Status status = be.pulse(helper.getLevel());
			helper.assertTrue(status == Status.NO_POWER, "an unpowered collector must report NO_POWER, got " + status);
			helper.assertTrue(drop.isAlive() && drop.getItem().getCount() == 3, "the drop must stay");
			helper.assertTrue(be.isEmpty(), "nothing may reach the buffer");
			helper.assertBlockProperty(COLLECTOR, VacuumCollectorBlock.LIT, false);
			helper.succeed();
		});
	}

	/**
	 * @implements TC-VACCOL-001-FUN06 — one pulse acts on the four nearest drops in reach and on no other,
	 *     and pays for the pulse once.
	 */
	public static void onePulseActsOnTheNearestFour(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		be.getEnergyStorage().setAmountUntracked(0);
		List<ItemEntity> drops = new ArrayList<>();
		Vec3 c = Vec3.atCenterOf(be.getBlockPos());
		for (int i = 0; i < 5; i++) {
			ItemEntity item = new ItemEntity(helper.getLevel(), c.x + 1.5 + 0.7 * i, c.y - 0.4, c.z,
					new ItemStack(DISTINCT.get(i)));
			item.setDeltaMovement(Vec3.ZERO);
			item.setPickUpDelay(0);
			item.setNoGravity(true);
			helper.getLevel().addFreshEntity(item);
			drops.add(item);
		}
		helper.runAfterDelay(2, () -> {
			for (ItemEntity item : drops) {
				item.setDeltaMovement(Vec3.ZERO);
			}
			be.getEnergyStorage().setAmountUntracked(pulseCost());
			Status status = be.pulse(helper.getLevel());

			helper.assertTrue(status == Status.WORKING, "a paid pulse with drops in reach must work, got " + status);
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(drops.get(i).getDeltaMovement().x < 0.0, "drop " + i + " is among the nearest four"
						+ " and must be pulled, got " + drops.get(i).getDeltaMovement());
			}
			helper.assertTrue(drops.get(4).getDeltaMovement().equals(Vec3.ZERO),
					"the fifth, farthest drop must wait for the next pulse");
			helper.assertTrue(be.getEnergyStorage().getAmount() == 0, "one pulse costs euPerTick × period, once");
			for (ItemEntity item : drops) {
				item.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * @implements TC-VACCOL-001-FUN07 — left to the world's ticks, the collector takes four touching drops a
	 *     pulse, and the next four exactly one pulse period later.
	 */
	public static void pulsesFiveTicksApart(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		be.getEnergyStorage().setAmountUntracked(0);
		for (int i = 0; i < 8; i++) {
			double dx = (i % 4) * 0.25 - 0.375;
			double dz = (i / 4) * 0.5 - 0.25;
			spawnOnLid(helper, be, dx, dz, new ItemStack(DISTINCT.get(i)));
		}
		long[] firstFour = {-1};
		helper.runAfterDelay(2, () -> be.getEnergyStorage().setAmountUntracked(2 * pulseCost()));
		helper.onEachTick(() -> {
			int n = total(be);
			long now = helper.getTick();
			if (n > 0 && firstFour[0] < 0) {
				helper.assertTrue(n == 4, "the first pulse must take four drops, took " + n);
				firstFour[0] = now;
			}
			if (n == 8) {
				long period = now - firstFour[0];
				helper.assertTrue(period == Config.vacuumCollectorPulseTicks,
						"the next four must come one pulse period later, came after " + period + " ticks");
				helper.succeed();
			}
		});
	}

	/**
	 * @implements TC-VACCOL-001-FUN08 — a plain item pipe on the lid carries the buffer into a chest, with no
	 *     face of the pipe configured; one under the floor joins too.
	 */
	public static void pipePullsTheBufferOut(GameTestHelper helper) {
		BlockPos pipePos = COLLECTOR.above();
		BlockPos chestPos = pipePos.above();
		helper.setBlock(pipePos, ModContent.ITEM_PIPE.get());
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		be.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
		ItemPipeBlockEntity pipe = helper.getBlockEntity(pipePos, ItemPipeBlockEntity.class);
		pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());

		helper.assertTrue(ItemPipeBlock.shouldConnectTo(helper.getLevel(), helper.absolutePos(pipePos), Direction.DOWN),
				"a pipe on the lid must join the collector");
		helper.assertTrue(ItemPipeBlock.hasEndpointCandidate(helper.getLevel(), helper.absolutePos(COLLECTOR.below()),
				Direction.UP), "a pipe under the floor must find the collector too");
		ItemNetworkManager.tickAll(helper.getLevel());

		int moved = count(chest(helper, chestPos), Items.IRON_INGOT);
		helper.assertTrue(moved > 0, "the pipe must carry ingots out of the collector into the chest");
		helper.assertTrue(moved + count(be, Items.IRON_INGOT) == 16, "sixteen ingots before and after");
		helper.succeed();
	}

	/**
	 * @implements TC-VACCOL-001-NEG05 — the sides are the funnels: a plain pipe beside the collector does not
	 *     join it and carries nothing out of it.
	 */
	public static void pipeOnASideGetsNothing(GameTestHelper helper) {
		BlockPos pipePos = COLLECTOR.east();
		BlockPos chestPos = pipePos.east();
		helper.setBlock(pipePos, ModContent.ITEM_PIPE.get());
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		be.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
		ItemPipeBlockEntity pipe = helper.getBlockEntity(pipePos, ItemPipeBlockEntity.class);
		pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());

		helper.assertFalse(
				ItemPipeBlock.shouldConnectTo(helper.getLevel(), helper.absolutePos(pipePos), Direction.WEST),
				"a pipe beside the collector must not join a funnel");
		ItemNetworkManager.tickAll(helper.getLevel());

		helper.assertTrue(count(chest(helper, chestPos), Items.IRON_INGOT) == 0, "nothing may leave through a side");
		helper.assertTrue(count(be, Items.IRON_INGOT) == 16, "all sixteen ingots stay in the collector");
		helper.succeed();
	}

	/**
	 * @implements TC-VACCOL-001-NEG06 — energy comes in through the lid and the floor only: a cable beside
	 *     the collector draws no arm to it and finds no port there, one on the lid does.
	 */
	public static void cablesOnlyOnTopAndBottom(GameTestHelper helper) {
		BlockPos side = COLLECTOR.east();
		BlockPos top = COLLECTOR.above();
		helper.setBlock(side, ModContent.COPPER_CABLE.get());
		helper.setBlock(top, ModContent.COPPER_CABLE.get());
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		BlockPos abs = be.getBlockPos();

		helper.assertFalse(helper.getBlockState(side).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST)),
				"a cable beside the collector must not draw an arm to a funnel");
		helper.assertTrue(helper.getBlockState(top).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.DOWN)),
				"a cable on the lid must connect");
		for (Direction face : Direction.values()) {
			boolean vertical = face.getAxis().isVertical();
			helper.assertTrue((be.energyRoleForFace(face) == EnergyRole.IN) == vertical,
					"face " + face + ": energy role must be IN only on the lid and the floor");
			helper.assertTrue((EnergyLookup.get().find(helper.getLevel(), abs, face) != null) == vertical,
					"face " + face + ": an energy port only on the lid and the floor");
		}
		helper.succeed();
	}

	private static Container chest(GameTestHelper helper, BlockPos pos) {
		return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	/** @implements TC-VACCOL-001-PER01 — the buffer is saved and loaded with the block, slot for slot. */
	public static void bufferSurvivesReload(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		VacuumCollectorBlockEntity source = SaveFormatTestSupport.fresh(VacuumCollectorBlockEntity::new,
				ModContent.VACUUM_COLLECTOR.get());
		source.setItem(0, new ItemStack(Items.IRON_INGOT, 17));
		source.setItem(8, new ItemStack(Items.WHEAT_SEEDS, 64));

		CompoundTag saved = source.saveWithoutMetadata(registries);
		VacuumCollectorBlockEntity restored = SaveFormatTestSupport.load(SaveFormatTestSupport.fresh(
				VacuumCollectorBlockEntity::new, ModContent.VACUUM_COLLECTOR.get()), registries, saved);

		for (int slot = 0; slot < VacuumCollectorBlockEntity.SLOT_COUNT; slot++) {
			helper.assertTrue(ItemStack.matches(source.getItem(slot), restored.getItem(slot)),
					"slot " + slot + " held " + source.getItem(slot) + " and came back as " + restored.getItem(slot));
		}
		helper.assertTrue(saved.equals(restored.saveWithoutMetadata(registries)), "the re-save must be the same tag");
		helper.succeed();
	}

	/**
	 * @implements TC-VACCOL-001-NEG04 — nothing goes in from outside: no slot takes an item by hand or through
	 *     any face; the lid and the floor give all nine slots out, the four sides expose none.
	 */
	public static void nothingGoesIn(GameTestHelper helper) {
		VacuumCollectorBlockEntity be = place(helper, COLLECTOR);
		ItemStack stack = new ItemStack(Items.IRON_INGOT);
		for (Direction face : Direction.values()) {
			int[] slots = be.getSlotsForFace(face);
			int expected = face.getAxis().isVertical() ? VacuumCollectorBlockEntity.SLOT_COUNT : 0;
			helper.assertTrue(slots.length == expected,
					"face " + face + " must expose " + expected + " buffer slots, got " + slots.length);
			for (int slot : slots) {
				helper.assertFalse(be.canPlaceItemThroughFace(slot, stack, face),
						"slot " + slot + " must refuse an item through " + face);
				helper.assertTrue(be.canTakeItemThroughFace(slot, stack, face),
						"slot " + slot + " must give its item out through " + face);
			}
		}
		helper.assertFalse(be.canPlaceItem(0, stack), "the buffer takes nothing by hand either");
		helper.succeed();
	}
}
