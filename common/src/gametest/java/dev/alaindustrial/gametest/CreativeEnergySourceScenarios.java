package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Scenarios for the Creative Energy Source (MOD-479).
 *
 * <p><b>What makes these tests non-vacuous, and why it needed saying.</b> "The consumer received EU"
 * is green for an ordinary generator, a battery and in fact any source at all — it asserts existence,
 * not inexhaustibility. So does "a cable held charge": the line fills from any generator whether or
 * not the consumer ever draws from it. Every test below therefore either
 *
 * <ul>
 *   <li>pairs its claim with proof that anything happened at all — "the buffer did not fall" is only
 *       worth reading next to "and this much was delivered in the same run"; or
 *   <li>asserts something a finite source cannot do, namely deliver several times its own capacity.
 * </ul>
 *
 * <p>The two "it stops" tests are paired the other way round: they first prove delivery WHILE the
 * source is on, then re-baseline and prove it is zero after. An assertion that a switched-off block
 * delivers nothing passes just as well against a block that was never placed.
 */
public final class CreativeEnergySourceScenarios {

	private CreativeEnergySourceScenarios() {}

	/** Standalone rig position, clear of {@code EnergyLine}'s row. */
	private static final BlockPos SOURCE = new BlockPos(1, 2, 3);

	/** Long enough for the totals to dwarf anything the source can hold, short enough to stay a unit. */
	private static final int RUN_TICKS = 120;

	/**
	 * Ticks for the accumulation test, and the multiple of "what it holds at once" it must exceed.
	 *
	 * <p>The limiter on this rig is the CONSUMER, not the source: a macerator draws around ten EU a
	 * tick, so the total grows at the machine's pace whatever the source is set to. The first attempt
	 * asked for ten times the held charge after 120 ticks and missed by a hair — 1170 EU against 1280 —
	 * which said nothing about the block and everything about the rig. A longer run with a smaller
	 * multiple keeps the claim structurally impossible for a finite source while leaving real margin.
	 */
	private static final int ACCUMULATE_TICKS = 400;
	private static final int ACCUMULATE_MULTIPLE = 8;

	private static CreativeEnergySourceBlockEntity placeSource(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModContent.CREATIVE_ENERGY_SOURCE.get());
		return helper.getBlockEntity(pos, CreativeEnergySourceBlockEntity.class);
	}

	/**
	 * The rig every delivery test uses: the source, three cables and a machine that actually draws.
	 *
	 * <p>A battery box placed flush against the source was the first attempt and it delivered nothing:
	 * a box exposes a mixed face layout, and the face that happened to meet the source was an output
	 * one, which refuses insertion. The failure looked exactly like "the source is broken". Going
	 * through the shared line rig removes that whole class of question — it is the rig the rest of the
	 * energy suite is written against.
	 */
	private static EnergyLine line(GameTestHelper helper) {
		return EnergyLine.in(helper)
				.generator(ModContent.CREATIVE_ENERGY_SOURCE.get(), ItemStack.EMPTY)
				.cables(3)
				.consumer(ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64))
				.build();
	}

	private static CreativeEnergySourceBlockEntity sourceOf(GameTestHelper helper, EnergyLine line) {
		return helper.getBlockEntity(line.sourcePos(), CreativeEnergySourceBlockEntity.class);
	}

	/**
	 * Drives the line one tick at a time, emptying the consumer after each tick and returning everything
	 * it received.
	 *
	 * <p>Emptying is what turns a capped buffer into a running total: left alone, a consumer answers
	 * with its own capacity no matter how much the source can actually deliver, and the test would be
	 * measuring the machine rather than the source.
	 */
	private static long drainAndCount(GameTestHelper helper, EnergyLine line, int ticks) {
		long total = 0;
		for (int i = 0; i < ticks; i++) {
			line.drive(1);
			if (EnergyScenarioSupport.be(helper, line.consumerPos())
					instanceof MachineBlockEntity machine) {
				total += machine.getEnergyStorage().getAmount();
				machine.getEnergyStorage().setAmountUntracked(0);
			}
		}
		return total;
	}

	/**
	 * The buffer is at the configured output at the start of a run and still there at the end, while a
	 * consumer down the line takes power the whole time.
	 *
	 * <p>Both halves are load-bearing. An ordinary generator's buffer swings as it burns and delivers,
	 * so "unchanged" is the claim; "and the macerator charged" is what stops that claim from being true
	 * of a block nobody drew from.
	 */
	public static void bufferHoldsSteadyWhileDelivering(GameTestHelper helper) {
		EnergyLine line = line(helper);
		CreativeEnergySourceBlockEntity source = sourceOf(helper, line);
		long expected = source.getOutputLimit();

		line.drive(RUN_TICKS);
		line.assertConsumerCharged().assertFlowedThroughCable();

		// Read the buffer right after a tick of the SOURCE alone. Reading it at the end of a full line
		// tick measures something else entirely — the network has already drawn from it by then, and the
		// first version of this test failed on exactly that, expecting 128 and finding 126.
		EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, line.sourcePos()));
		long held = source.getEnergyStorage().getAmount();
		if (held != expected) {
			helper.fail("creative source did not refill: expected " + expected
					+ " EU at the start of its tick, found " + held
					+ " — the top-up is what makes it inexhaustible");
			return;
		}
		helper.succeed();
	}

	/**
	 * Over a run the source hands out several times what its own buffer can hold.
	 *
	 * <p>This is the one assertion no finite source can satisfy, which is exactly why it is here: a
	 * battery, a full generator, a mis-wired top-up that only runs once — all of them cap out at one
	 * capacity. The threshold is a multiple of the capacity rather than a number copied from the
	 * block, so it cannot degenerate into comparing a value with itself.
	 */
	public static void deliversManyTimesItsOwnCapacity(GameTestHelper helper) {
		EnergyLine line = line(helper);
		CreativeEnergySourceBlockEntity source = sourceOf(helper, line);

		// The yardstick is what the block ever HOLDS at one moment, not its declared capacity: the
		// buffer is topped to the output setting and never above it, so that is the whole of what a
		// finite source of the same shape could ever hand over before running dry.
		EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, line.sourcePos()));
		long heldAtOnce = source.getEnergyStorage().getAmount();
		long delivered = drainAndCount(helper, line, ACCUMULATE_TICKS);

		if (heldAtOnce <= 0) {
			helper.fail("the source holds nothing after its own tick — the rig is broken");
			return;
		}
		if (delivered <= heldAtOnce * ACCUMULATE_MULTIPLE) {
			helper.fail("creative source delivered " + delivered + " EU over " + ACCUMULATE_TICKS
					+ " ticks while ever holding only " + heldAtOnce + " EU at a time; expected more than "
					+ (heldAtOnce * ACCUMULATE_MULTIPLE)
					+ " — a finite source of that size could not, so the buffer is not refilling");
			return;
		}
		helper.succeed();
	}

	/**
	 * Switching the source off stops delivery — proven against a run that delivered first, so the
	 * assertion cannot pass on an empty rig.
	 */
	public static void switchedOffStopsDelivering(GameTestHelper helper) {
		EnergyLine line = line(helper);
		CreativeEnergySourceBlockEntity source = sourceOf(helper, line);

		long whileOn = drainAndCount(helper, line, 20);
		if (whileOn <= 0) {
			helper.fail("nothing was delivered while the source was ON — the rig is broken, so the"
					+ " OFF half of this test would prove nothing");
			return;
		}

		source.setEnabled(false);
		// The cables are still holding what the source put there; that charge legitimately reaches the
		// consumer after the switch. Let the line empty first, or this measures the wire, not the block.
		drainAndCount(helper, line, 40);

		long whileOff = drainAndCount(helper, line, 20);
		if (whileOff != 0) {
			helper.fail("a switched-off creative source still delivered " + whileOff
					+ " EU after the line had been drained");
			return;
		}
		helper.succeed();
	}

	/**
	 * An output of zero behaves exactly like the switch being off. Same pairing as above: delivery is
	 * proven at a real setting first.
	 *
	 * <p>Worth a test of its own because the two are implemented as one condition, and the reason is
	 * subtle — a face left able to extract while giving nothing keeps the network awake for it and
	 * seeds its flow field, which is a defect the grid paid for once already.
	 */
	public static void zeroOutputStopsDelivering(GameTestHelper helper) {
		EnergyLine line = line(helper);
		CreativeEnergySourceBlockEntity source = sourceOf(helper, line);

		long atDefault = drainAndCount(helper, line, 20);
		if (atDefault <= 0) {
			helper.fail("nothing was delivered at the default output — the rig is broken");
			return;
		}

		source.setOutputLimit(0);
		drainAndCount(helper, line, 40);  // drain what the cables still hold, as above

		long atZero = drainAndCount(helper, line, 20);
		if (atZero != 0) {
			helper.fail("a source dialled to zero still delivered " + atZero
					+ " EU after the line had been drained");
			return;
		}
		if (source.energyRoleForFace(Direction.NORTH).canExtract()) {
			helper.fail("a source dialled to zero still advertises an extractable face — the network"
					+ " will keep waking for a block that gives nothing");
			return;
		}
		helper.succeed();
	}

	/**
	 * The switch and the output survive a save/load round trip.
	 *
	 * <p>Both values are set away from their defaults first: a round-trip test that stores the default
	 * passes against a block that writes nothing at all and simply re-defaults on load.
	 */
	public static void settingsSurviveReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(SOURCE);
		CreativeEnergySourceBlockEntity source = placeSource(helper, SOURCE);

		source.setEnabled(false);
		source.setOutputLimit(288);

		CompoundTag tag = source.saveCustomOnly(registries);
		CreativeEnergySourceBlockEntity restored =
				new CreativeEnergySourceBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.isEnabled() || restored.getOutputLimit() != 288) {
			helper.fail("creative source round-trip mismatch: enabled false->" + restored.isEnabled()
					+ ", output 288->" + restored.getOutputLimit());
			return;
		}
		helper.succeed();
	}

	/**
	 * The charge slot fills a powered item, and no automation can reach it from any side.
	 *
	 * <p>The second half is the one worth having: the slot is the block's only inventory, and a block
	 * with no facing gets every one of its slots exposed on all six faces by default — a hopper under
	 * it would have emptied the player's tool out of a creative-only block.
	 */
	public static void chargeSlotFillsItemAndRefusesAutomation(GameTestHelper helper) {
		CreativeEnergySourceBlockEntity source = placeSource(helper, SOURCE);
		ItemStack tool = new ItemStack(ModContent.BATTERY.get());
		if (ItemEnergy.capacity(tool) <= 0) {
			helper.fail("the rig item holds no EU — pick an item that does, or this proves nothing");
			return;
		}
		ItemEnergy.set(tool, 0);
		source.setItem(CreativeEnergySourceBlockEntity.CHARGE_SLOT, tool);

		for (int i = 0; i < 40; i++) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, SOURCE));
		}

		long charge = ItemEnergy.get(source.getItem(CreativeEnergySourceBlockEntity.CHARGE_SLOT));
		if (charge <= 0) {
			helper.fail("the charge slot did not fill the item after 40 ticks");
			return;
		}
		for (Direction side : Direction.values()) {
			if (source.getSlotsForFace(side).length != 0) {
				helper.fail("the charge slot is exposed to automation on " + side
						+ " — hoppers and pipes must not reach a GUI-only slot");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * No recipe anywhere yields the block, on the running loader's real recipe set.
	 *
	 * <p>The acceptance criterion says "no crafting recipe"; asserting it against the loaded recipes is
	 * the only form of that statement a future edit can break. A comment saying the same thing cannot.
	 */
	public static void noRecipeYieldsTheBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack expected = new ItemStack(ModContent.CREATIVE_ENERGY_SOURCE_ITEM.get());
		// 26.2 dropped Recipe#getResultItem; a recipe's output is read through its display, which needs
		// the level's context map to resolve tags and fuel values into concrete stacks.
		ContextMap context = SlotDisplayContext.fromLevel(level);
		for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
			for (RecipeDisplay display : holder.value().display()) {
				for (ItemStack result : display.result().resolveForStacks(context)) {
					if (!result.isEmpty() && ItemStack.isSameItem(result, expected)) {
						helper.fail("recipe " + holder.id() + " yields the creative energy source;"
								+ " the block must be unobtainable outside the creative tab");
						return;
					}
				}
			}
		}
		helper.succeed();
	}
}
