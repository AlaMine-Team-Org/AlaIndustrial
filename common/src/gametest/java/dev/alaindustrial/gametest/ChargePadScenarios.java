package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ChargePadBlock;
import dev.alaindustrial.block.ChargePadState;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral gametest bodies for the Charging Station (MOD-274, suite TC-PAD-001).
 *
 * <p>The transfer is driven through {@link ChargePadBlockEntity#chargePlayer} rather than by walking a
 * player onto the block, for the same reason the bare cable's shock tests call {@code tryShockPlayer}
 * directly: {@code entityInside} is a one-line vanilla hook, and a mock player is not ticked by the
 * world, so waiting for it would test the harness rather than the station. Numbers come from
 * {@link Config} — the balance source of truth.
 *
 * <p>Three of these cases are regression guards for bugs the design review found <em>before</em> they
 * shipped, each of which would be invisible in play until someone counted EU: charging a worn item at
 * all, charging the Energy Pack despite its no-auto-charge tag, and honouring per-item input rates.
 * Restore any of those three behaviours to the pack's original code and the matching test goes red.
 */
public final class ChargePadScenarios {

	private ChargePadScenarios() {}

	private static final BlockPos PAD = new BlockPos(1, 2, 1);

	/** EU the station hands out in a single tick when nothing else limits it. */
	private static long tickBudget() {
		return Config.chargePadOutputRate;
	}

	private static ItemStack drill(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_DRILL.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static ItemStack pack(long eu) {
		ItemStack stack = new ItemStack(ModContent.ENERGY_PACK.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/** A drained Fluxweave piece for the slot it belongs in — the set the playtest report used. */
	private static ItemStack fluxweave(EquipmentSlot slot) {
		ItemStack stack = new ItemStack(switch (slot) {
			case HEAD -> ModContent.FLUXWEAVE_HELMET.get();
			case CHEST -> ModContent.FLUXWEAVE_CHESTPLATE.get();
			case LEGS -> ModContent.FLUXWEAVE_LEGGINGS.get();
			default -> ModContent.FLUXWEAVE_BOOTS.get();
		});
		ItemEnergy.set(stack, 0L);
		return stack;
	}

	private static ItemStack jetpack(long eu) {
		ItemStack stack = new ItemStack(ModContent.JETPACK.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/** A station placed with {@code eu} already banked. */
	private static ChargePadBlockEntity placePad(GameTestHelper helper, long eu) {
		ChargePadBlockEntity be = AlaGameTestHelper.place(helper, PAD, ModContent.CHARGE_PAD.get(),
				ChargePadBlockEntity.class);
		be.getEnergyStorage().setAmountUntracked(eu);
		return be;
	}

	/**
	 * A mock player standing in the station's own cell.
	 *
	 * <p>{@code makeMockServerPlayer} is required rather than {@code makeMockPlayer}: the station's
	 * guard takes a {@link ServerPlayer}, which is also what the real {@code entityInside} path
	 * delivers. Abilities are forced down because {@link ItemEnergy#free} reads
	 * {@code hasInfiniteMaterials()} — a creative player would silently skip every debit assertion.
	 *
	 * <p>The position is not decoration: the station only serves whoever is standing in its own cell
	 * (see {@code chargePlayer}), and a mock player is created at the world origin. Leaving them there
	 * would make every case below pass or fail for the wrong reason.
	 */
	private static ServerPlayer visitor(GameTestHelper helper, GameType mode) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(mode);
		player.getAbilities().instabuild = false;
		player.getAbilities().invulnerable = false;
		BlockPos abs = helper.absolutePos(PAD);
		// Feet inside the plate's cell — the plate is 4px, so a player on top of it is still "in" it.
		player.setPos(abs.getX() + 0.5, abs.getY() + 0.25, abs.getZ() + 0.5);
		return player;
	}

	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		return visitor(helper, GameType.SURVIVAL);
	}

	private static ChargePadState stateAt(GameTestHelper helper) {
		BlockState state = helper.getBlockState(PAD);
		if (!state.hasProperty(ChargePadBlock.STATE)) {
			helper.fail("charge_pad lost its state property at " + PAD);
		}
		return state.getValue(ChargePadBlock.STATE);
	}

	// ── FUN — functional ─────────────────────────────────────────────────────────────────────────

	/** FUN01: one tick of contact moves one tick's budget into a carried item and debits the station. */
	public static void fun01ChargesCarriedItem(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack tool = drill(0);
		player.getInventory().setItem(0, tool);

		pad.chargePlayer(helper.getLevel(), player);

		long expected = Math.min(tickBudget(), ItemEnergy.inputRate(tool));
		if (ItemEnergy.get(tool) != expected) {
			helper.fail("carried drill must gain " + expected + " EU in one tick, got " + ItemEnergy.get(tool));
		}
		if (pad.getEnergyStorage().getAmount() != Config.chargePadBuffer - expected) {
			helper.fail("the station must pay exactly what it handed out, left "
					+ pad.getEnergyStorage().getAmount());
		}
		if (stateAt(helper) != ChargePadState.CHARGING) {
			helper.fail("indicator must read CHARGING while energy is moving, got " + stateAt(helper));
		}
		helper.succeed();
	}

	/**
	 * FUN02: worn equipment is charged too — the whole reason to stand on the station after a flight.
	 *
	 * <p>Regression guard: the pack's original distribution walked {@code getNonEquipmentItems()} plus
	 * the offhand and never touched armour slots. Drop {@code Policy.includeEquipped} and a jetpack on
	 * the player's back charges to exactly 0 here.
	 */
	public static void fun02ChargesWornEquipment(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		player.setItemSlot(EquipmentSlot.CHEST, jetpack(0));

		pad.chargePlayer(helper.getLevel(), player);

		ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
		long expected = Math.min(tickBudget(), ItemEnergy.inputRate(worn));
		if (ItemEnergy.get(worn) != expected) {
			helper.fail("a worn jetpack must be charged at its own input rate: expected " + expected
					+ " EU, got " + ItemEnergy.get(worn));
		}
		if (pad.getEnergyStorage().getAmount() != Config.chargePadBuffer - expected) {
			helper.fail("the station must pay exactly what the worn item took, left "
					+ pad.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * FUN03: the Energy Pack is charged despite carrying {@code NO_AUTO_CHARGE}.
	 *
	 * <p>Regression guard for the tag's scope. It exists so a worn pack does not charge another pack
	 * (they would ping-pong energy and drain the wearer); a stationary charger cannot ping-pong with
	 * anything, and topping up the pack is the main reason to step on the station. Reuse the pack's own
	 * policy here — {@code Policy.WORN_PACK} — and the pack silently stays empty.
	 */
	public static void fun03ChargesEnergyPackDespiteTag(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack carried = pack(0);
		// Deliberately an ordinary inventory slot, not the chest slot: worn placement would make this
		// case depend on Policy.includeEquipped as well, and a red test would no longer say WHICH rule
		// broke. FUN02 owns the worn path; this one isolates the tag.
		player.getInventory().setItem(0, carried);

		pad.chargePlayer(helper.getLevel(), player);

		long expected = Math.min(tickBudget(), ItemEnergy.inputRate(carried));
		if (ItemEnergy.get(carried) != expected) {
			helper.fail("the station must charge an Energy Pack (expected " + expected + " EU, got "
					+ ItemEnergy.get(carried) + ") — the no_auto_charge tag is about chargers charging "
					+ "chargers, not about a stationary charger");
		}
		helper.succeed();
	}

	/**
	 * FUN04: no single item is fed faster than its own input rate.
	 *
	 * <p>Regression guard: the station's per-tick budget (128 EU) is four times what any powered item
	 * accepts (32 EU/t). The pack's original {@code give} clamped only by free space, so reusing it
	 * unchanged would push the whole 128 into the first item in slot order.
	 */
	public static void fun04RespectsPerItemInputRate(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack tool = drill(0);
		player.getInventory().setItem(0, tool);

		long rate = ItemEnergy.inputRate(tool);
		if (rate >= tickBudget()) {
			helper.fail("test premise broken: the station's budget (" + tickBudget()
					+ ") must exceed one item's input rate (" + rate + ") for this to prove anything");
		}
		pad.chargePlayer(helper.getLevel(), player);

		if (ItemEnergy.get(tool) > rate) {
			helper.fail("an item must never take more than its input rate in one tick: rate " + rate
					+ ", got " + ItemEnergy.get(tool));
		}
		helper.succeed();
	}

	/**
	 * FUN05: the per-tick budget is a TOTAL shared across everything carried, and it is spent in scan
	 * order until it runs out.
	 *
	 * <p>Deliberately overloaded: enough drills that their combined input rate exceeds the station's
	 * output, which is the only arrangement that can observe {@link Config#chargePadOutputRate} at all.
	 * With two items (2 × 32 &lt; 128) the budget never binds, and deleting the output cap entirely
	 * would go unnoticed — the exact hole this case exists to close.
	 */
	public static void fun05BudgetIsSharedAndCapped(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		long rate = ItemEnergy.inputRate(drill(0));
		// Deliberately more items than the budget can serve at a full rate: with 128 EU/t and 32 EU/t
		// per item, anything above four proves the split rather than the order.
		int count = (int) (tickBudget() / rate) + 2;
		ItemStack[] tools = new ItemStack[count];
		for (int i = 0; i < count; i++) {
			tools[i] = drill(0);
			player.getInventory().setItem(i, tools[i]);
		}

		pad.chargePlayer(helper.getLevel(), player);

		long total = 0L;
		long least = Long.MAX_VALUE;
		long most = 0L;
		long expectedShare = tickBudget() / count;
		for (int i = 0; i < count; i++) {
			long got = ItemEnergy.get(tools[i]);
			total += got;
			least = Math.min(least, got);
			most = Math.max(most, got);
			// MOD-334: everything charges together, so NOTHING may be left at zero or starved below its
			// share, and nothing may exceed its own input rate.
			if (got < expectedShare) {
				helper.fail("slot " + i + " was starved: expected at least its share (" + expectedShare
						+ " EU), got " + got);
			}
			if (got > rate) {
				helper.fail("slot " + i + " exceeded its input rate (" + rate + "), got " + got);
			}
		}
		// An even split cannot divide perfectly; the remainder is handed round again and lands on the
		// earliest slots. Bounded by the number of targets, so "even" stays a real claim.
		if (most - least > count) {
			helper.fail("the split must be even: spread between " + least + " and " + most
					+ " EU across " + count + " items");
		}
		if (total != tickBudget()) {
			helper.fail("the station must hand out exactly its per-tick output (" + tickBudget()
					+ " EU) when demand exceeds it, handed out " + total);
		}
		if (pad.getEnergyStorage().getAmount() != Config.chargePadBuffer - tickBudget()) {
			helper.fail("the station must pay exactly its output, left " + pad.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * FUN12: a full set of worn armour charges together, not two pieces at a time.
	 *
	 * <p>The playtest report this task came from, as a test: a player in complete Fluxweave saw the
	 * helmet and chestplate fill first and the leggings and boots only afterwards, because the budget
	 * was handed out in scan order and worn gear sits last in it. Every piece must now move on the same
	 * tick.
	 */
	public static void fun12WholeArmourSetChargesTogether(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		EquipmentSlot[] worn = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (EquipmentSlot slot : worn) {
			player.setItemSlot(slot, fluxweave(slot));
		}
		// Carried items too: they used to win the whole budget before worn gear was even reached.
		player.getInventory().setItem(0, drill(0));
		player.getInventory().setItem(1, drill(0));

		pad.chargePlayer(helper.getLevel(), player);

		for (EquipmentSlot slot : worn) {
			long got = ItemEnergy.get(player.getItemBySlot(slot));
			if (got <= 0) {
				helper.fail("every worn piece must charge on the same tick as the rest — " + slot
						+ " got " + got + " EU");
			}
		}
		if (ItemEnergy.get(player.getInventory().getItem(0)) <= 0
				|| ItemEnergy.get(player.getInventory().getItem(1)) <= 0) {
			helper.fail("carried items must charge alongside the worn set, not instead of it");
		}
		helper.succeed();
	}

	/** FUN06: an empty station charges nothing and says so on the indicator. */
	public static void fun06EmptyStationReportsEmpty(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, 0L);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack tool = drill(0);
		player.getInventory().setItem(0, tool);

		pad.chargePlayer(helper.getLevel(), player);

		if (ItemEnergy.get(tool) != 0) {
			helper.fail("a station with no EU must not charge anything, moved " + ItemEnergy.get(tool));
		}
		if (stateAt(helper) != ChargePadState.EMPTY) {
			helper.fail("indicator must read EMPTY when the buffer is flat, got " + stateAt(helper));
		}
		helper.succeed();
	}

	/** FUN07: a visitor whose gear is already full gets the "you are done" signal, and costs nothing. */
	public static void fun07FullVisitorReportsReady(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		player.getInventory().setItem(0, drill(Config.electricDrillBuffer));

		pad.chargePlayer(helper.getLevel(), player);

		if (pad.getEnergyStorage().getAmount() != Config.chargePadBuffer) {
			helper.fail("a station with nothing to charge must spend nothing, left "
					+ pad.getEnergyStorage().getAmount());
		}
		if (stateAt(helper) != ChargePadState.READY) {
			helper.fail("indicator must read READY for a full visitor, got " + stateAt(helper));
		}
		helper.succeed();
	}

	/**
	 * FUN08: once contact goes stale the station releases the indicator and stops drawing.
	 *
	 * <p>This is the idle-producer guard (MOD-214): a station that stayed "occupied" after its visitor
	 * left would keep a phantom load on the network forever. It also covers the stuck-flag failure the
	 * timestamp design exists to prevent — a player who leaves the world between the two ticks never
	 * gets to clear anything.
	 */
	public static void fun08ReleasesAfterVisitorLeaves(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		player.getInventory().setItem(0, drill(0));

		pad.chargePlayer(helper.getLevel(), player);
		if (stateAt(helper) != ChargePadState.CHARGING) {
			helper.fail("precondition: the station should be charging before the visitor leaves");
		}
		long banked = pad.getEnergyStorage().getAmount();

		// REAL ticks, not AlaGameTestHelper.drive: staleness is measured against the game clock, and
		// calling serverTick in a loop re-runs the block without advancing that clock — the contact
		// would read as "this very tick" forever and the test could never fail. Letting the world tick
		// also means this exercises the block's own registered ticker rather than a direct call.
		helper.startSequence()
				.thenExecuteFor(6, () -> { })
				.thenExecute(() -> {
					if (stateAt(helper) != ChargePadState.IDLE) {
						helper.fail("indicator must fall back to IDLE once contact goes stale, got "
								+ stateAt(helper));
					}
					if (pad.getEnergyStorage().getAmount() != banked) {
						helper.fail("an unoccupied station must not spend EU, went from " + banked
								+ " to " + pad.getEnergyStorage().getAmount());
					}
				})
				.thenSucceed();
	}

	/** FUN09: a spectator drifting through the block charges nothing and costs the station nothing. */
	public static void fun09SpectatorIsIgnored(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		// Standing in the station's cell, exactly like the survival visitor — otherwise this would pass
		// because the spectator is somewhere else, not because the spectator guard works.
		ServerPlayer player = visitor(helper, GameType.SPECTATOR);
		ItemStack tool = drill(0);
		player.getInventory().setItem(0, tool);

		pad.chargePlayer(helper.getLevel(), player);

		if (ItemEnergy.get(tool) != 0) {
			helper.fail("a spectator must not be charged, moved " + ItemEnergy.get(tool));
		}
		if (pad.getEnergyStorage().getAmount() != Config.chargePadBuffer) {
			helper.fail("a spectator must not drain the station, left " + pad.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * FUN10: the block's own {@code entityInside} hook reaches the block entity.
	 *
	 * <p>Every case above calls {@link ChargePadBlockEntity#chargePlayer} directly, which is
	 * deterministic but proves nothing about the wiring: gut {@link ChargePadBlock#entityInside} and
	 * they all stay green while the station is stone dead in game. This one drives the vanilla hook
	 * itself — the same "test the real path, end to end" guard the Energy Pack's inventory-tick case
	 * exists for.
	 */
	public static void fun10EntityInsideHookReachesTheStation(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack tool = drill(0);
		player.getInventory().setItem(0, tool);

		helper.getBlockState(PAD).entityInside(helper.getLevel(), helper.absolutePos(PAD), player,
				InsideBlockEffectApplier.NOOP, true);

		long expected = Math.min(tickBudget(), ItemEnergy.inputRate(tool));
		if (ItemEnergy.get(tool) != expected) {
			helper.fail("the block's entityInside hook must reach the station and charge: expected "
					+ expected + " EU, got " + ItemEnergy.get(tool));
		}
		if (stateAt(helper) != ChargePadState.CHARGING) {
			helper.fail("the hook must also drive the indicator, got " + stateAt(helper));
		}
		helper.succeed();
	}

	/**
	 * FUN11: a player straddling two stations is served by exactly one of them.
	 *
	 * <p>Regression guard. {@code entityInside} fires once per block position the player's box overlaps,
	 * and a 0.6-wide hitbox straddles two cells on a seam. Without the "is this my cell" gate both
	 * stations serve the same player in the same tick, so the carried item takes twice its documented
	 * input rate and the pair hands out twice the documented per-tick output — invisible in play until
	 * someone counts EU, and directly contrary to the 1×1 zone the design settled on.
	 */
	public static void fun11OnlyTheStationUnderfootServes(GameTestHelper helper) {
		ChargePadBlockEntity here = placePad(helper, Config.chargePadBuffer);
		BlockPos neighbourPos = PAD.offset(1, 0, 0);
		ChargePadBlockEntity neighbour = AlaGameTestHelper.place(helper, neighbourPos,
				ModContent.CHARGE_PAD.get(), ChargePadBlockEntity.class);
		neighbour.getEnergyStorage().setAmountUntracked(Config.chargePadBuffer);

		ServerPlayer player = survivalPlayer(helper);
		ItemStack tool = drill(0);
		player.getInventory().setItem(0, tool);

		// Both stations get the hook, exactly as vanilla would deliver it to a straddling player.
		here.chargePlayer(helper.getLevel(), player);
		neighbour.chargePlayer(helper.getLevel(), player);

		long expected = Math.min(tickBudget(), ItemEnergy.inputRate(tool));
		if (ItemEnergy.get(tool) != expected) {
			helper.fail("a straddling player must be served once per tick: expected " + expected
					+ " EU, got " + ItemEnergy.get(tool));
		}
		if (neighbour.getEnergyStorage().getAmount() != Config.chargePadBuffer) {
			helper.fail("the station the player is NOT standing in must spend nothing, left "
					+ neighbour.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	// ── NRG — energy contract ────────────────────────────────────────────────────────────────────

	/**
	 * NRG01: every face accepts energy and no face offers any.
	 *
	 * <p>The station is a pure consumer. Left at the inherited {@code BOTH} its ports would advertise
	 * extraction and the network would enumerate it as a candidate producer.
	 */
	public static void nrg01EveryFaceIsIntakeOnly(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		for (Direction face : Direction.values()) {
			if (!pad.energyRoleForFace(face).canInsert()) {
				helper.fail("face " + face + " must accept energy");
			}
			if (pad.energyRoleForFace(face).canExtract()) {
				helper.fail("face " + face + " must not offer energy — the station never produces any");
			}
		}
		helper.succeed();
	}

	/**
	 * NRG03: the station actually takes EU from a neighbouring source, rather than conjuring it.
	 *
	 * <p>The acceptance criterion "draws from the energy network through the standard EnergyPort" was
	 * previously only backed by the empty-buffer case, which proves the negative ("no EU, no charge")
	 * and nothing about intake. This drives a Battery Box flush against the station and watches the
	 * buffer fill.
	 *
	 * <p>The box's rotation is load-bearing: it emits ONLY from the face opposite its {@code FACING},
	 * so with the station to its east it must face west. Placed with the default state it would emit
	 * into thin air and this test would fail for a reason that has nothing to do with the station.
	 */
	public static void nrg03TakesEnergyFromNeighbour(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, 0L);
		BlockPos boxPos = PAD.offset(-1, 0, 0);
		helper.getLevel().setBlockAndUpdate(helper.absolutePos(boxPos),
				ModContent.BATTERY_BOX.get().defaultBlockState()
						.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity box = helper.getBlockEntity(boxPos, BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery box block entity missing at " + boxPos);
		}
		box.getEnergyStorage().setAmountUntracked(Config.batteryBoxBuffer);

		AlaGameTestHelper.drive(box, helper, 5);

		if (pad.getEnergyStorage().getAmount() <= 0) {
			helper.fail("the station must accept EU from an adjacent source, still holding "
					+ pad.getEnergyStorage().getAmount());
		}
		if (box.getEnergyStorage().getAmount() >= Config.batteryBoxBuffer) {
			helper.fail("the energy must come OUT of the source, not out of thin air — box still at "
					+ box.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/** NRG02: the banked buffer survives a save/load round trip — a station keeps its charge overnight. */
	public static void nrg02BufferPersists(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(PAD);
		ChargePadBlockEntity pad = placePad(helper, 4321L);

		CompoundTag tag = pad.saveCustomOnly(registries);
		ChargePadBlockEntity restored = new ChargePadBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != 4321L) {
			helper.fail("the banked buffer must round-trip through NBT, got "
					+ restored.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}
	// ── MOD-406: paying in batches must not change how much is paid ──────────────────────────────

	/**
	 * Twenty ticks of standing still deliver exactly what the per-tick station delivered, and the
	 * per-item rate ceiling is still honoured in EU per TICK.
	 *
	 * <p>Why this matters beyond arithmetic: the station used to rewrite the charged stack's energy
	 * component on every single tick, and a rewritten stack stops matching the client's copy, so the
	 * slot is re-sent twenty times a second (plus the cursor and crafting grid while a screen is open).
	 * That is what the player felt as a stuttering inventory and a stuttering sound. Batching fixes the
	 * rewrite rate — this test pins that it fixed nothing else.
	 *
	 * <p>The number is chosen so the item's own input rate binds, not the station budget: that is the
	 * ceiling a naive "just call it less often" would quietly divide by the batch size, turning a
	 * performance fix into a nerf.
	 */
	public static void mod406BatchedPayoutMatchesPerTickTotal(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack tool = drill(0);
		player.getInventory().setItem(0, tool);
		long rate = ItemEnergy.inputRate(tool);

		// Two facts, and the first one is the safety property: at NO point may the player hold more than
		// the contact they have actually earned. A batch that paid for ticks the player had not yet
		// stood through would be an energy dupe on a timer, which is the way this optimisation could go
		// wrong without anyone noticing.
		int ticks = 21;
		for (int i = 1; i <= ticks; i++) {
			pad.chargePlayer(helper.getLevel(), player);
			long earnedSoFar = Math.min(rate * i, ItemEnergy.capacity(tool));
			if (ItemEnergy.get(tool) > earnedSoFar) {
				helper.fail("MOD-406: after " + i + " ticks the item holds " + ItemEnergy.get(tool)
						+ " EU but only " + earnedSoFar + " was earned — a batch paid for contact that "
						+ "had not happened yet");
				return;
			}
		}

		// Second fact: nothing is LOST either. 21 ticks is the leading edge plus four whole cadences, so
		// on that boundary the batched total must equal the per-tick total exactly. (Between boundaries
		// up to one cadence is legitimately still owed — it arrives with the next payout.)
		long expected = Math.min(rate * ticks, ItemEnergy.capacity(tool));
		long got = ItemEnergy.get(tool);
		if (got != expected) {
			helper.fail("MOD-406: " + ticks + " ticks of contact delivered " + got + " EU, expected "
					+ expected + " (rate " + rate + "/t). Batching must change the CADENCE of payouts, "
					+ "never the amount — and the per-item rate ceiling is per TICK, not per call.");
			return;
		}
		helper.succeed();
	}

	/**
	 * The batching itself, observed the only way a gametest can see it: how many ticks actually change
	 * the stack. With a per-tick payout all twenty do; batched, only the leading edge and every fifth
	 * tick after it.
	 *
	 * <p>Asserts a ceiling rather than an exact count, so the cadence constant can be retuned without
	 * rewriting the test — but a ceiling far below twenty, so reverting to per-tick payouts reddens it.
	 */
	public static void mod406PayoutsAreBatchedNotPerTick(GameTestHelper helper) {
		ChargePadBlockEntity pad = placePad(helper, Config.chargePadBuffer);
		ServerPlayer player = survivalPlayer(helper);
		ItemStack tool = drill(0);
		player.getInventory().setItem(0, tool);

		int ticks = 20;
		int rewrites = 0;
		long previous = ItemEnergy.get(tool);
		for (int i = 0; i < ticks; i++) {
			pad.chargePlayer(helper.getLevel(), player);
			long now = ItemEnergy.get(tool);
			if (now != previous) {
				rewrites++;
				previous = now;
			}
		}

		// 20 ticks / 5-tick cadence = 4, plus the leading edge = 5. Eight leaves room to retune.
		if (rewrites > 8) {
			helper.fail("MOD-406: the stack was rewritten " + rewrites + " times in " + ticks
					+ " ticks — the station is still paying every tick, and every payout re-sends the "
					+ "slot to the client");
			return;
		}
		if (rewrites == 0) {
			helper.fail("MOD-406: the stack was never charged at all — this test would pass vacuously");
			return;
		}
		helper.succeed();
	}
}
