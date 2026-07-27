package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.LitMachineBlock;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.IncubatorStatus;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.item.misc.MutationGrades;
import dev.alaindustrial.menu.IncubatorMenu;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * L2 coverage for the incubator (MOD-118): the multiblock gate, the chip-driven mode, the uranium
 * charge, the probabilistic outcome and the glass accounting of the dome.
 *
 * <p>Loader-neutral bodies — the Fabric {@code IncubatorGameTest} and the NeoForge
 * {@code NeoForgeGameTests} lane run these very methods, so both loaders exercise the same machine.
 *
 * <p>The dice are made deterministic by pinning the relevant {@link Config} chances for the duration
 * of a test and restoring them afterwards — the same idea as MOD-130's forced equip plan, so the
 * assertions test the machine rather than the player's luck.
 */
public final class IncubatorScenarios {

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final int AMPLE_EU = 64_000;
	/** Longer than a transform op (300 ticks) plus margin. */
	private static final int DRIVE_TICKS = 420;

	private IncubatorScenarios() {
	}

	private static IncubatorBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModContent.INCUBATOR.get(), IncubatorBlockEntity.class);
	}

	/**
	 * A transform job: lapis becomes redstone. Transform recipes state no {@code chance} of their own,
	 * so the mode's Config default applies — which is what {@link #withCertainSuccess} can pin. (The
	 * duplicate recipes carry an explicit per-class chance and would ignore it.)
	 */
	private static void loadTransformJob(IncubatorBlockEntity be) {
		be.getEnergyStorage().amount = AMPLE_EU;
		be.setItem(IncubatorBlockEntity.CHIP_SLOT, new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()));
		be.setItem(IncubatorBlockEntity.FUEL_SLOT, new ItemStack(ModContent.URANIUM_INGOT.get(), 8));
		be.setItem(IncubatorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAPIS_LAZULI, 8));
	}

	/**
	 * Runs {@code body} with the transform mutation guaranteed to succeed and never to yield slag, so
	 * the outcome is deterministic. Values are restored even when the assertion fails.
	 */
	private static void withCertainSuccess(Runnable body) {
		// Grades are pinned to common as well: a rare result does not stack with a common one, so a
		// lucky roll would legitimately halt the machine on a full output slot and make a multi-attempt
		// test flaky. That behaviour is asserted separately in reg01.
		withPinnedDice(1.0, 0.0, 0.0, 0.0, 0.0, body);
	}

	/**
	 * Pins every die of the outcome for the duration of {@code body}: the transform success chance, the
	 * slag share and the three grade shares. The cap is opened to 1.0 so a pinned chance is not clipped.
	 */
	private static void withPinnedDice(double success, double slag, double rare, double epic,
			double legendary, Runnable body) {
		double oldChance = Config.mutationChanceTransform;
		double oldSlag = Config.mutationSlagChance;
		double oldCap = Config.mutationChanceCap;
		double oldRare = Config.mutationGradeRare;
		double oldEpic = Config.mutationGradeEpic;
		double oldLegendary = Config.mutationGradeLegendary;
		try {
			Config.mutationChanceTransform = success;
			Config.mutationSlagChance = slag;
			Config.mutationChanceCap = 1.0;
			Config.mutationGradeRare = rare;
			Config.mutationGradeEpic = epic;
			Config.mutationGradeLegendary = legendary;
			body.run();
		} finally {
			Config.mutationChanceTransform = oldChance;
			Config.mutationSlagChance = oldSlag;
			Config.mutationChanceCap = oldCap;
			Config.mutationGradeRare = oldRare;
			Config.mutationGradeEpic = oldEpic;
			Config.mutationGradeLegendary = oldLegendary;
		}
	}

	// ── the multiblock ─────────────────────────────────────────────────────────────────────────────

	/** Placing glass over the base assembles the multiblock: the glass becomes the dome. */
	public static void fun01GlassOnTopFormsTheDome(GameTestHelper helper) {
		place(helper);
		helper.setBlock(POS.above(), Blocks.GLASS);
		BlockPos abs = helper.absolutePos(POS.above());
		if (!helper.getLevel().getBlockState(abs).is(ModContent.INCUBATOR_DOME.get())) {
			helper.fail("glass placed on the incubator did not become the dome");
		}
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(POS))
				instanceof IncubatorBlockEntity be) || !be.isFormed()) {
			helper.fail("the incubator did not report itself as formed after the dome appeared");
		}
		helper.succeed();
	}

	/** Removing the dome unforms the structure and hands the exact glass back as an item. */
	public static void fun02RemovingTheDomeReturnsTheOriginalGlass(GameTestHelper helper) {
		place(helper);
		helper.setBlock(POS.above(), Blocks.STAINED_GLASS.lime());
		helper.setBlock(POS.above(), Blocks.AIR);
		if (helper.getLevel().getBlockEntity(helper.absolutePos(POS))
				instanceof IncubatorBlockEntity be && be.isFormed()) {
			helper.fail("the incubator still reports itself as formed after the dome was removed");
		}
		// The dome has an empty loot table, so the glass can only come back from the removal hook.
		helper.assertItemEntityPresent(Blocks.STAINED_GLASS.lime().asItem(), POS.above(), 2.0);
		helper.succeed();
	}

	/**
	 * The dome is destroyed by something that is not a player — an explosion, a fluid. Those funnel
	 * through {@code Level#destroyBlock} with no player, the path that used to lose the glass outright
	 * because the return lived in {@code playerWillDestroy} alone.
	 *
	 * <p>Not {@code /setblock}, though: that one places with side effects skipped and no neighbour
	 * update, so it reaches neither removal hook and takes the glass with it — as it does a chest's
	 * contents in vanilla. The distinction is worth stating, because the comment here used to claim
	 * the command was covered.
	 */
	public static void fun05NonPlayerBreakStillReturnsTheGlass(GameTestHelper helper) {
		place(helper);
		helper.setBlock(POS.above(), Blocks.STAINED_GLASS.lime());
		helper.destroyBlock(POS.above());
		helper.assertItemEntityPresent(Blocks.STAINED_GLASS.lime().asItem(), POS.above(), 2.0);
		helper.succeed();
	}

	/**
	 * Breaking the base hands the coloured glass back as a block, not as plain glass. The block entity
	 * holds the only record of which glass was used, and it is gone by the time the block's own removal
	 * hook runs — so the hand-back has to happen earlier, from the block entity itself.
	 */
	public static void fun06BreakingTheBaseKeepsTheColouredGlass(GameTestHelper helper) {
		place(helper);
		helper.setBlock(POS.above(), Blocks.STAINED_GLASS.lime());
		helper.destroyBlock(POS);
		BlockState above = helper.getLevel().getBlockState(helper.absolutePos(POS.above()));
		if (!above.is(Blocks.STAINED_GLASS.lime())) {
			helper.fail("breaking the base left " + above.getBlock()
					+ " on top instead of the lime stained glass the dome was made of");
		}
		// Handed back as a block, so nothing may be dropped on top of it.
		helper.assertItemEntityNotPresent(Blocks.STAINED_GLASS.lime().asItem(), POS.above(), 2.0);
		helper.succeed();
	}

	/** A creative break drops nothing, as everywhere in vanilla. */
	public static void neg03CreativeBreakDropsNoGlass(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		player.getAbilities().instabuild = true;
		place(helper);
		helper.setBlock(POS.above(), Blocks.STAINED_GLASS.lime());
		player.gameMode.destroyBlock(helper.absolutePos(POS.above()));
		helper.assertItemEntityNotPresent(Blocks.STAINED_GLASS.lime().asItem(), POS.above(), 2.0);
		helper.succeed();
	}

	/**
	 * Right-clicking the dome opens the machine below it. The dome is the half a player actually sees
	 * and reaches for, and until this shipped it was dead to the touch — the click did nothing at all.
	 */
	public static void fun08ClickingTheDomeOpensTheMenu(GameTestHelper helper) {
		place(helper);
		helper.setBlock(POS.above(), Blocks.GLASS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);

		BlockPos abs = helper.absolutePos(POS.above());
		// The real click path — ServerPlayerGameMode routing, exactly what a right-click does — rather
		// than calling the block's hook directly, which would prove nothing about reachability.
		InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));

		if (!result.consumesAction()) {
			helper.fail("right-clicking the dome did nothing (" + result + ")");
			return;
		}
		if (!(player.containerMenu instanceof IncubatorMenu)) {
			helper.fail("clicking the dome opened " + player.containerMenu + " instead of the incubator");
		}
		helper.succeed();
	}

	/**
	 * A piston must not take the dome: it is half of a multiblock and the glass it was made of is
	 * remembered by the base below, so moving it away would strand both halves.
	 */
	public static void con01DomeBlocksPistons(GameTestHelper helper) {
		place(helper);
		helper.setBlock(POS.above(), Blocks.GLASS);
		BlockState dome = helper.getLevel().getBlockState(helper.absolutePos(POS.above()));
		if (dome.getPistonPushReaction() != PushReaction.BLOCK) {
			helper.fail("a piston can move the dome (push reaction " + dome.getPistonPushReaction() + ")");
		}
		helper.succeed();
	}

	private static void expectStatus(GameTestHelper helper, IncubatorBlockEntity be,
			IncubatorStatus expected) {
		AlaGameTestHelper.drive(be, helper, 3);
		if (be.status() != expected) {
			helper.fail("expected status " + expected + ", got " + be.status());
		}
	}

	/**
	 * The machine names every reason it is idle (MOD-234). Each of these was a silent stall: the screen
	 * printed the mode name and the player was left guessing. The worst was a blocked result slot — a
	 * held outcome with nowhere to go looks exactly like "duplication worked once and then broke".
	 */
	public static void reg05StatusExplainsEveryStall(GameTestHelper helper) {
		IncubatorBlockEntity be = place(helper);
		expectStatus(helper, be, IncubatorStatus.NO_DOME);

		helper.setBlock(POS.above(), Blocks.GLASS);
		expectStatus(helper, be, IncubatorStatus.NO_CHIP);

		be.setItem(IncubatorBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()));
		expectStatus(helper, be, IncubatorStatus.NO_INPUT);

		// A stick has no transform recipe — the exact case the owner hit with an ingot.
		be.setItem(IncubatorBlockEntity.INPUT_SLOT, new ItemStack(Items.STICK, 4));
		expectStatus(helper, be, IncubatorStatus.NO_RECIPE);

		be.setItem(IncubatorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAPIS_LAZULI, 4));
		expectStatus(helper, be, IncubatorStatus.NO_FUEL);

		// Fuel and power in place, but the result has nowhere to land: lapis transforms into redstone
		// and the slot already holds a full stack of it.
		be.getEnergyStorage().amount = AMPLE_EU;
		be.setItem(IncubatorBlockEntity.FUEL_SLOT, new ItemStack(ModContent.URANIUM_INGOT.get(), 4));
		be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.REDSTONE, 64));
		expectStatus(helper, be, IncubatorStatus.OUTPUT_BLOCKED);
		helper.succeed();
	}

	/**
	 * A pipe may take the result out of the incubator and put it straight back into its own input
	 * (MOD-234) — that is how a player automates repeat duplication. The network used to refuse any
	 * pair of endpoints on the same block, so this build did nothing at all.
	 *
	 * <p>Faces are chosen away from {@code FACING} (NORTH by default), which is inert to automation.
	 */
	public static void reg06PipeReturnsTheResultToTheInput(GameTestHelper helper) {
		IncubatorBlockEntity be = place(helper);
		helper.setBlock(POS.above(), Blocks.GLASS);
		be.setItem(IncubatorBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get()));
		be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.DIAMOND, 8));

		BlockPos drain = POS.west();
		BlockPos corner = POS.west().south();
		BlockPos feed = POS.south();
		for (BlockPos pos : new BlockPos[] { drain, corner, feed }) {
			helper.setBlock(pos, ModContent.ITEM_PIPE.get());
		}
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(drain))
						instanceof ItemPipeBlockEntity out)
				|| !(helper.getLevel().getBlockEntity(helper.absolutePos(feed))
						instanceof ItemPipeBlockEntity in)) {
			helper.fail("the pipe rig did not build");
			return;
		}
		out.setFaceMode(Direction.EAST, PipeFaceMode.EXTRACT);   // pulls from the machine's west side
		in.setFaceMode(Direction.NORTH, PipeFaceMode.INSERT);    // pushes into its south side
		for (BlockPos pos : new BlockPos[] { drain, corner, feed }) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(pos))
					instanceof ItemPipeBlockEntity pipe) {
				pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());
			}
		}
		ItemNetworkManager.tickAll(helper.getLevel());

		ItemStack fed = be.getItem(IncubatorBlockEntity.INPUT_SLOT);
		if (fed.isEmpty() || !fed.is(Items.DIAMOND)) {
			helper.fail("the pipe did not return the result into the input of the same machine (input: "
					+ fed + ")");
		}
		if (be.getItem(IncubatorBlockEntity.OUTPUT_SLOT).getCount() != 8 - fed.getCount()) {
			helper.fail("the diamonds were duplicated or lost in transit");
		}
		helper.succeed();
	}

	/**
	 * The client-side menu must expose every data channel the block entity publishes (MOD-234).
	 *
	 * <p>The width lives in two places by necessity: the block entity answers with its own
	 * {@code ContainerData}, while a client that opens the screen builds a {@code SimpleContainerData}
	 * of its own. They were two separate literals, so adding the status channel to one crashed the
	 * screen on open — {@code ArrayIndexOutOfBoundsException} on the render thread, in a build whose
	 * 561 server-side tests were all green, because none of them ever touched the client constructor.
	 * This walks every accessor through that constructor; a channel added to one side and not the other
	 * throws here instead of in front of the player.
	 */
	public static void reg07ClientMenuCoversEveryDataChannel(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		IncubatorMenu menu = new IncubatorMenu(1, player.getInventory());
		// Reading the top channel is the actual regression: it is the one the old width omitted.
		menu.getStatus();
		menu.getMode();
		menu.getCharge();
		menu.isFormed();
		helper.succeed();
	}

	/**
	 * The build every player makes first: a pipe on a side face of the incubator draining the result
	 * and the ash into a chest (owner report, 2026-07-26). Asserted on a face that is <b>not</b>
	 * {@code FACING} — the front is inert to automation by design — so a failure here means the machine
	 * refuses automation outright rather than the player having picked the wrong side.
	 */
	public static void reg08PipeDrainsProductsIntoAChest(GameTestHelper helper) {
		IncubatorBlockEntity be = place(helper);
		helper.setBlock(POS.above(), Blocks.GLASS);
		be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.DIAMOND, 2));
		be.setItem(IncubatorBlockEntity.ASH_SLOT,
				new ItemStack(ModContent.DEPLETED_URANIUM.get(), 2));

		BlockPos drain = POS.east();
		BlockPos link = POS.east().south();
		BlockPos chestPos = POS.east().south().south();
		helper.setBlock(drain, ModContent.ITEM_PIPE.get());
		helper.setBlock(link, ModContent.ITEM_PIPE.get());
		helper.setBlock(chestPos, Blocks.CHEST);

		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(drain))
						instanceof ItemPipeBlockEntity out)
				|| !(helper.getLevel().getBlockEntity(helper.absolutePos(link))
						instanceof ItemPipeBlockEntity via)
				|| !(helper.getLevel().getBlockEntity(helper.absolutePos(chestPos))
						instanceof Container chest)) {
			helper.fail("the drain rig did not build");
			return;
		}
		out.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);   // touches the machine's east face
		via.setFaceMode(Direction.SOUTH, PipeFaceMode.INSERT);   // touches the chest
		out.serverTick(helper.getLevel(), out.getBlockPos(), out.getBlockState());
		via.serverTick(helper.getLevel(), via.getBlockPos(), via.getBlockState());
		// One interval serves the target with a single batch, so the result and the ash cannot both
		// arrive on the same tick — the throughput gate is the point of the interval. Driving one tick
		// and asserting "something arrived" is what let the first draft of this test pass while the ash
		// had not moved at all.
		for (int tick = 0; tick < Config.itemPipeTransferIntervalTicks * 4 + 4; tick++) {
			ItemNetworkManager.tickAll(helper.getLevel());
		}

		// Counted per item, not as a total: "something arrived" would pass with only the ash moved,
		// and the result slot is the one the player actually watches.
		int diamonds = 0;
		int ash = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.is(Items.DIAMOND)) {
				diamonds += stack.getCount();
			} else if (stack.is(ModContent.DEPLETED_URANIUM.get())) {
				ash += stack.getCount();
			}
		}
		if (diamonds == 0) {
			helper.fail("the pipe drained no result out of the incubator (result slot: "
					+ be.getItem(IncubatorBlockEntity.OUTPUT_SLOT) + ")");
		}
		if (ash == 0) {
			helper.fail("the pipe drained no ash out of the incubator (ash slot: "
					+ be.getItem(IncubatorBlockEntity.ASH_SLOT) + ")");
		}
		if (diamonds + be.getItem(IncubatorBlockEntity.OUTPUT_SLOT).getCount() != 2
				|| ash + be.getItem(IncubatorBlockEntity.ASH_SLOT).getCount() != 2) {
			helper.fail("items were duplicated or lost in transit: diamonds=" + diamonds
					+ " ash=" + ash);
		}
		helper.succeed();
	}

	// ── running the machine ────────────────────────────────────────────────────────────────────────

	/**
	 * The ingot commits the moment it lands in an assembled machine: the charge fills at once, with
	 * no chip and no input — the filling pips are the player's receipt that the uranium is spent
	 * (owner acceptance, 2026-07-26). An unassembled base must NOT take the ingot: without the dome
	 * the chamber is open and nothing is being irradiated. Fails on the pre-polish machine, which
	 * only pulled fuel once a full cycle could start.
	 */
	public static void fun10IngotCommitsOnInsertion(GameTestHelper helper) {
		IncubatorBlockEntity be = place(helper);
		be.setItem(IncubatorBlockEntity.FUEL_SLOT, new ItemStack(ModContent.URANIUM_INGOT.get(), 1));

		// No dome yet: the open base holds the ingot untouched.
		AlaGameTestHelper.drive(be, helper, 5);
		if (be.getItem(IncubatorBlockEntity.FUEL_SLOT).isEmpty() || be.charge() != 0) {
			helper.fail("an unassembled base took the ingot (charge " + be.charge() + ")");
		}

		// The dome closes the chamber: the ingot converts into a full charge on the next tick,
		// with the chip slot and the input slot both empty and no EU in the buffer.
		helper.setBlock(POS.above(), Blocks.GLASS);
		AlaGameTestHelper.drive(be, helper, 5);
		if (!be.getItem(IncubatorBlockEntity.FUEL_SLOT).isEmpty()) {
			helper.fail("the ingot was not committed on insertion — no chip and no input must still charge");
		}
		if (be.charge() != Math.max(1, Config.mutationAttemptsPerIngot)) {
			helper.fail("expected a full charge of " + Config.mutationAttemptsPerIngot
					+ ", got " + be.charge());
		}
		if (!be.getItem(IncubatorBlockEntity.ASH_SLOT).isEmpty()) {
			helper.fail("ash appeared before any attempt was spent");
		}
		if (!be.getItem(IncubatorBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("an output appeared with no input to mutate");
		}
		helper.succeed();
	}

	/** Without the dome the machine refuses to run, however much fuel and power it holds. */
	public static void neg01NoDomeNoOutput(GameTestHelper helper) {
		withCertainSuccess(() -> {
			IncubatorBlockEntity be = place(helper);
			loadTransformJob(be);
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS);
			if (!be.getItem(IncubatorBlockEntity.OUTPUT_SLOT).isEmpty()) {
				helper.fail("the incubator produced an output without its dome");
			}
			helper.succeed();
		});
	}

	/** With no chip there is no mode, so nothing runs even with the dome in place. */
	public static void neg02NoChipNoOutput(GameTestHelper helper) {
		withCertainSuccess(() -> {
			IncubatorBlockEntity be = place(helper);
			helper.setBlock(POS.above(), Blocks.GLASS);
			be.getEnergyStorage().amount = AMPLE_EU;
			be.setItem(IncubatorBlockEntity.FUEL_SLOT, new ItemStack(ModContent.URANIUM_INGOT.get(), 8));
			// Lapis on purpose: it HAS a transform recipe. With a diamond — which has none — the test
			// passed whether the chip gate worked or not, because there was nothing to make either way.
			be.setItem(IncubatorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAPIS_LAZULI, 8));
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS);
			if (!be.getItem(IncubatorBlockEntity.OUTPUT_SLOT).isEmpty()) {
				helper.fail("the incubator produced an output with no mutation chip inserted");
			}
			helper.succeed();
		});
	}

	/** A complete rig mutates the input: lapis in, redstone out. */
	public static void fun03TransformYieldsTheOtherMineral(GameTestHelper helper) {
		withCertainSuccess(() -> {
			IncubatorBlockEntity be = place(helper);
			helper.setBlock(POS.above(), Blocks.GLASS);
			loadTransformJob(be);
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS);
			ItemStack out = be.getItem(IncubatorBlockEntity.OUTPUT_SLOT);
			if (out.isEmpty() || !out.is(Items.REDSTONE)) {
				helper.fail("expected redstone from a lapis transform, got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "x " + out.getItem()));
			}
			helper.succeed();
		});
	}

	/**
	 * The uranium is a charge, not a per-operation cost: one ingot is pulled once and powers
	 * {@code mutationAttemptsPerIngot} attempts before it drops into the ash slot.
	 */
	public static void fun04OneIngotPowersSeveralAttempts(GameTestHelper helper) {
		withCertainSuccess(() -> {
			IncubatorBlockEntity be = place(helper);
			helper.setBlock(POS.above(), Blocks.GLASS);
			be.getEnergyStorage().amount = AMPLE_EU;
			be.setItem(IncubatorBlockEntity.CHIP_SLOT,
					new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()));
			be.setItem(IncubatorBlockEntity.FUEL_SLOT, new ItemStack(ModContent.URANIUM_INGOT.get(), 1));
			be.setItem(IncubatorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAPIS_LAZULI, 8));

			// One attempt: the single ingot is consumed and the charge covers further attempts.
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS);
			if (!be.getItem(IncubatorBlockEntity.FUEL_SLOT).isEmpty()) {
				helper.fail("the ingot was not taken from the fuel slot on the first attempt");
			}
			if (be.charge() != Config.mutationAttemptsPerIngot - 1) {
				helper.fail("expected " + (Config.mutationAttemptsPerIngot - 1)
						+ " attempts left on the ingot, got " + be.charge());
			}
			if (!be.getItem(IncubatorBlockEntity.ASH_SLOT).isEmpty()) {
				helper.fail("ash appeared before the ingot was spent");
			}

			// Drive the rest of the charge: the last attempt drops the ash.
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS * Config.mutationAttemptsPerIngot);
			ItemStack ash = be.getItem(IncubatorBlockEntity.ASH_SLOT);
			if (ash.isEmpty() || !ash.is(ModContent.DEPLETED_URANIUM.get())) {
				helper.fail("a spent ingot did not leave depleted uranium in the ash slot"
						+ " (charge left: " + be.charge() + ")");
			}
			helper.succeed();
		});
	}

	/**
	 * Automation takes the products — the result and the ash — through any face it can see, and never
	 * the chip or the uranium. Pulling the chip used to be allowed so a hopper could switch modes; it
	 * cost more than it was worth (MOD-234).
	 */
	public static void fun07AutomationTakesOnlyTheProducts(GameTestHelper helper) {
		IncubatorBlockEntity be = place(helper);
		ItemStack chip = new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get());
		ItemStack fuel = new ItemStack(ModContent.URANIUM_INGOT.get());
		be.setItem(IncubatorBlockEntity.CHIP_SLOT, chip);
		be.setItem(IncubatorBlockEntity.FUEL_SLOT, fuel);
		be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.REDSTONE));
		be.setItem(IncubatorBlockEntity.ASH_SLOT, new ItemStack(ModContent.DEPLETED_URANIUM.get()));

		// Products leave through any face automation can see.
		for (Direction side : Direction.values()) {
			if (!be.canTakeItemThroughFace(IncubatorBlockEntity.OUTPUT_SLOT,
					be.getItem(IncubatorBlockEntity.OUTPUT_SLOT), side)) {
				helper.fail("the result cannot be pulled through " + side);
			}
			if (!be.canTakeItemThroughFace(IncubatorBlockEntity.ASH_SLOT,
					be.getItem(IncubatorBlockEntity.ASH_SLOT), side)) {
				helper.fail("the ash cannot be pulled through " + side);
			}
			// The chip and the uranium are the machine's own supplies: a drain hooked to any face used
			// to walk off with the chip and stall the incubator silently (MOD-234).
			if (be.canTakeItemThroughFace(IncubatorBlockEntity.CHIP_SLOT, chip, side)) {
				helper.fail("automation can steal the chip through " + side + " and stall the machine");
			}
			if (be.canTakeItemThroughFace(IncubatorBlockEntity.FUEL_SLOT, fuel, side)) {
				helper.fail("automation can steal the uranium through " + side);
			}
		}
		if (be.canPlaceItemThroughFace(IncubatorBlockEntity.CHIP_SLOT, chip, Direction.UP)) {
			helper.fail("automation stuffed a second chip into an occupied chip slot");
		}
		helper.succeed();
	}

	// ── the output slot never eats a result ────────────────────────────────────────────────────────

	/**
	 * A graded result does not stack with a plain one, so the machine must refuse to start rather than
	 * consume the input for a result the slot cannot hold. Before the outcome was rolled up front, the
	 * pre-check tested an ungraded result, the graded one then failed to merge, and the item was
	 * destroyed together with the input and the irradiation charge.
	 */
	public static void reg01GradedResultNeverDestroysTheOutput(GameTestHelper helper) {
		// Every success is legendary, so the result can never merge with the plain redstone below.
		withPinnedDice(1.0, 0.0, 0.0, 0.0, 1.0, () -> {
			IncubatorBlockEntity be = place(helper);
			helper.setBlock(POS.above(), Blocks.GLASS);
			loadTransformJob(be);
			be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.REDSTONE, 1));

			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS * 2);

			ItemStack blocked = be.getItem(IncubatorBlockEntity.OUTPUT_SLOT);
			if (blocked.getCount() != 1 || MutationGrades.get(blocked) != MutationGrade.COMMON) {
				helper.fail("the blocked output slot changed: " + blocked.getCount() + "x "
						+ blocked.getItem() + " grade " + MutationGrades.get(blocked));
			}
			if (be.getItem(IncubatorBlockEntity.INPUT_SLOT).getCount() != 8) {
				helper.fail("the input was consumed for a result that had nowhere to go ("
						+ be.getItem(IncubatorBlockEntity.INPUT_SLOT).getCount() + " left of 8)");
			}
			// An ingot is loaded at the start of the cycle — that is not the same as spending an attempt.
			// What must not happen is a burned attempt: the loaded ingot keeps its full charge, and no
			// ash appears, until the result actually lands.
			if (be.charge() != Config.mutationAttemptsPerIngot) {
				helper.fail("an irradiation attempt was burned on a result that had nowhere to go (charge "
						+ be.charge() + " of " + Config.mutationAttemptsPerIngot + ")");
			}
			if (!be.getItem(IncubatorBlockEntity.ASH_SLOT).isEmpty()) {
				helper.fail("ash appeared while the outcome was still held");
			}

			// Free the slot and the very same pending result lands.
			be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS);
			ItemStack out = be.getItem(IncubatorBlockEntity.OUTPUT_SLOT);
			if (out.isEmpty() || !out.is(Items.REDSTONE)) {
				helper.fail("the freed slot did not receive the result");
				return;
			}
			if (MutationGrades.get(out) != MutationGrade.LEGENDARY) {
				helper.fail("expected a legendary result, got grade " + MutationGrades.get(out));
			}
			if (be.getItem(IncubatorBlockEntity.INPUT_SLOT).getCount() != 7) {
				helper.fail("exactly one input should have been consumed, "
						+ (8 - be.getItem(IncubatorBlockEntity.INPUT_SLOT).getCount()) + " were");
			}
			helper.succeed();
		});
	}

	/**
	 * The dice are thrown at the end of a paid cycle, never at the start. An earlier version rolled up
	 * front, which handed the player two free things at once: pulling the input and putting it back
	 * discarded an unwanted outcome at no cost, and the lit block state — visible from outside, no GUI
	 * needed — was a function of that hidden roll, because only a miss "fits" a full output slot.
	 *
	 * <p>Every attempt is pinned to a legendary success and one plain redstone is left in the output.
	 * A plain result would merge there, so the roll-independent gate lets the machine run; only the
	 * legendary it actually rolls cannot be placed. Under the old order the machine would have sat
	 * dark at zero progress from the first tick, having already decided in secret. Here it works and
	 * stays lit through the whole cycle, and only stops at the very end — which is the proof that the
	 * roll happens there and that the lit state is not a window into it.
	 */
	public static void reg03NoFreeRerollBeforeTheCycleIsPaid(GameTestHelper helper) {
		withPinnedDice(1.0, 0.0, 0.0, 0.0, 1.0, () -> {
			IncubatorBlockEntity be = place(helper);
			helper.setBlock(POS.above(), Blocks.GLASS);
			loadTransformJob(be);
			be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.REDSTONE, 1));

			// Half a cycle in.
			AlaGameTestHelper.drive(be, helper, 150);
			// Channel 2 of the shared machine data is the progress counter.
			if (be.getDataAccess().get(2) == 0) {
				helper.fail("the machine refused to start, so it had already rolled its outcome — "
						+ "the roll must not happen before the cycle is paid for");
				return;
			}
			if (!be.getBlockState().getValue(LitMachineBlock.LIT)) {
				helper.fail("the machine is not lit while working, so the lit state still leaks the roll");
			}

			// Finish the cycle: the outcome now exists and cannot be placed, so it is held.
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS);
			if (be.getItem(IncubatorBlockEntity.INPUT_SLOT).getCount() != 8) {
				helper.fail("the input was consumed for a result that had nowhere to go");
			}

			// Pulling the input and putting it back must not buy a new outcome: it costs a whole cycle.
			ItemStack input = be.getItem(IncubatorBlockEntity.INPUT_SLOT);
			be.setItem(IncubatorBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
			AlaGameTestHelper.drive(be, helper, 5);
			be.setItem(IncubatorBlockEntity.INPUT_SLOT, input);
			// Checked before the machine ticks again: the paid progress is gone, so buying a new outcome
			// costs another full cycle rather than a pull-and-replace.
			if (be.getDataAccess().get(2) != 0) {
				helper.fail("swapping the input did not restart the cycle, so the paid progress survived "
						+ "a scrubbed outcome");
			}

			// Free the slot and let the fresh cycle run: the pinned dice still give a legendary, so what
			// this proves is that the result lands rather than being destroyed.
			be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS);
			ItemStack out = be.getItem(IncubatorBlockEntity.OUTPUT_SLOT);
			if (out.isEmpty() || MutationGrades.get(out) != MutationGrade.LEGENDARY) {
				helper.fail("expected the legendary result once the slot was free, got " + out
						+ " grade " + MutationGrades.get(out));
			}
			helper.succeed();
		});
	}

	/**
	 * Taking the result out of the output slot is what awards the incubator's three advancements, and
	 * this drives the whole chain: the real menu, the real slot, the shipped advancement JSON.
	 *
	 * <p>One legendary irradiated diamond satisfies all three at once — it is a mutation, it is one of
	 * the four {@code create} outputs (matched through the {@code mutation_created} tag), and it is
	 * legendary — so a single take proves the trigger fires and that both predicates read what they
	 * are supposed to read.
	 */
	public static void fun09TakingTheResultAwardsTheAdvancements(GameTestHelper helper) {
		IncubatorBlockEntity be = place(helper);
		helper.setBlock(POS.above(), Blocks.GLASS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);

		ItemStack prize = new ItemStack(ModContent.IRRADIATED_DIAMOND.get());
		MutationGrades.set(prize, MutationGrade.LEGENDARY);
		be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, prize);

		player.openMenu(be);
		if (!(player.containerMenu instanceof IncubatorMenu menu)) {
			helper.fail("could not open the incubator menu for the mock player");
			return;
		}
		// Slot order is the one addMachineSlots creates: chip, fuel, input, result, ash.
		menu.getSlot(3).onTake(player, menu.getSlot(3).getItem().copy());

		for (String id : new String[] {"first_mutation", "first_creation", "legendary_mutation"}) {
			AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
					.get(Industrialization.id(id));
			if (advancement == null) {
				helper.fail("advancement alaindustrial:" + id + " is not loaded — the JSON is missing "
						+ "or failed to parse");
				return;
			}
			if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
				helper.fail("taking a legendary create result did not award alaindustrial:" + id);
				return;
			}
		}
		helper.succeed();
	}

	/** A hopper under the machine collects results and ash, and must not walk off with the chip. */
	public static void reg04HopperBelowCannotStealTheChip(GameTestHelper helper) {
		IncubatorBlockEntity be = place(helper);
		ItemStack chip = new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get());
		be.setItem(IncubatorBlockEntity.CHIP_SLOT, chip);
		if (be.canTakeItemThroughFace(IncubatorBlockEntity.CHIP_SLOT, chip, Direction.DOWN)) {
			helper.fail("a hopper under the incubator can pull the chip out and stall the machine");
		}
		// A side face used to be allowed here, for automated mode switching. It cost more than it was
		// worth: any pipe or chest draining the machine took the chip with it (MOD-234).
		if (be.canTakeItemThroughFace(IncubatorBlockEntity.CHIP_SLOT, chip, Direction.NORTH)) {
			helper.fail("a pipe on the side can pull the chip out and stall the machine");
		}
		ItemStack ash = new ItemStack(ModContent.DEPLETED_URANIUM.get());
		if (!be.canTakeItemThroughFace(IncubatorBlockEntity.ASH_SLOT, ash, Direction.DOWN)) {
			helper.fail("the ash slot stopped emptying downwards");
		}
		helper.succeed();
	}

	/**
	 * Slag is an outcome like any other and shares the output slot, so it is held to the same rule: no
	 * room, no operation. It used to be missing from the pre-check entirely and vanished silently.
	 */
	public static void reg02SlagNeverDestroysTheOutput(GameTestHelper helper) {
		// Never succeeds, always slags.
		withPinnedDice(0.0, 1.0, 0.0, 0.0, 0.0, () -> {
			IncubatorBlockEntity be = place(helper);
			helper.setBlock(POS.above(), Blocks.GLASS);
			loadTransformJob(be);
			be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.REDSTONE, 1));

			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS * 2);

			if (be.getItem(IncubatorBlockEntity.INPUT_SLOT).getCount() != 8) {
				helper.fail("the input was consumed for slag that had nowhere to go");
			}
			if (be.getItem(IncubatorBlockEntity.OUTPUT_SLOT).getCount() != 1) {
				helper.fail("the blocked output slot changed");
			}

			be.setItem(IncubatorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
			AlaGameTestHelper.drive(be, helper, DRIVE_TICKS);
			ItemStack out = be.getItem(IncubatorBlockEntity.OUTPUT_SLOT);
			if (out.isEmpty() || !out.is(ModContent.IRRADIATED_SLAG.get())) {
				helper.fail("expected irradiated slag in the freed slot, got "
						+ (out.isEmpty() ? "empty" : out.getItem().toString()));
			}
			helper.succeed();
		});
	}
}
