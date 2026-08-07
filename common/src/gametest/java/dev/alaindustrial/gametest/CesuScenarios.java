package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral gametest bodies for the Reinforced Energy Storage (MOD-351).
 *
 * <p>Only the two behaviours that are genuinely new at this tier are covered here; everything else —
 * single-axis IO, storage-sink priority, the cascade, charge-through-break — is inherited unchanged
 * from the Battery Box and already has its own suites. Re-testing it would duplicate coverage without
 * adding a way to fail.
 *
 * <p><b>CESU-01 is the regression guard for a bug that actually shipped to the dev client.</b> A
 * vanilla {@code DataSlot} serialises as a signed 16-bit short, and this block's 100 000 EU buffer
 * arrived client-side as −31 072, so the GUI read "0 / −31072 EU (0%)". Set
 * {@link CesuBlockEntity#SYNC_SCALE} back to 1 and this test goes red immediately — that is what makes
 * it a guard rather than decoration.
 */
public final class CesuScenarios {

	private CesuScenarios() {}

	private static final BlockPos STORE = new BlockPos(1, 2, 1);

	/** Place a store and return its block entity, or null (after failing the test) if it did not stick. */
	private static CesuBlockEntity place(GameTestHelper helper) {
		BlockState state = ModContent.CESU.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH);
		helper.setBlock(STORE, state);
		// 26.2 GameTestHelper#getBlockEntity is typed and asserts presence itself; check the raw level
		// first so a missing BE fails with our message instead of an assertion from the harness.
		if (helper.getLevel().getBlockEntity(helper.absolutePos(STORE)) == null) {
			helper.fail("cesu block entity missing at " + STORE);
			return null;
		}
		return helper.getBlockEntity(STORE, CesuBlockEntity.class);
	}

	/**
	 * CESU-01 — every sync channel survives the 16-bit wire, at an empty AND a full buffer.
	 *
	 * <p>Checks the full-buffer case explicitly: capacity is constant, but stored EU only exceeds a
	 * short once the block is actually full, which is exactly the state the player saw broken.
	 */
	public static void cesu01SyncChannelsFitShort(GameTestHelper helper) {
		CesuBlockEntity be = place(helper);
		if (be == null) {
			return;
		}
		for (long charge : new long[] { 0L, Config.cesuBuffer / 2L, Config.cesuBuffer }) {
			be.getEnergyStorage().amount = charge;
			ContainerData data = be.getDataAccess();
			for (int channel = 0; channel < data.getCount(); channel++) {
				int value = data.get(channel);
				if ((short) value != value) {
					helper.fail("sync-channel-overflow: charge=" + charge + " channel=" + channel
							+ " value=" + value + " arrives as " + (short) value + " (short-encoded wire)");
					return;
				}
			}
		}
		helper.succeed();
	}

	/**
	 * CESU-02 — the scaled channels still describe the real buffer.
	 *
	 * <p>Without this, CESU-01 could be satisfied by sending zeroes: a channel that is always 0 fits a
	 * short perfectly and tells the player nothing. This pins the round trip (raw EU → scaled channel →
	 * ×scale) to within one scale step.
	 */
	public static void cesu02ScaledChannelsMatchBuffer(GameTestHelper helper) {
		CesuBlockEntity be = place(helper);
		if (be == null) {
			return;
		}
		long charge = Config.cesuBuffer * 3L / 4L;
		be.getEnergyStorage().amount = charge;
		ContainerData data = be.getDataAccess();

		long readCapacity = (long) data.get(CesuBlockEntity.DATA_CAPACITY_SCALED) * CesuBlockEntity.SYNC_SCALE;
		long readEnergy = (long) data.get(CesuBlockEntity.DATA_ENERGY_SCALED) * CesuBlockEntity.SYNC_SCALE;

		if (Math.abs(readCapacity - Config.cesuBuffer) >= CesuBlockEntity.SYNC_SCALE) {
			helper.fail("capacity channel wrong: read " + readCapacity + ", expected ~" + Config.cesuBuffer);
			return;
		}
		if (Math.abs(readEnergy - charge) >= CesuBlockEntity.SYNC_SCALE) {
			helper.fail("energy channel wrong: read " + readEnergy + ", expected ~" + charge);
			return;
		}
		helper.succeed();
	}

	/**
	 * CESU-03 — the discharge slot drains a powered item into the buffer.
	 *
	 * <p>The one piece of machinery the Battery Box does not have. Also pins the rate cap: a single tick
	 * may move at most the MV ceiling, so a full Energy Pack cannot teleport its whole charge in one go.
	 */
	public static void cesu03DischargeSlotFillsBuffer(GameTestHelper helper) {
		CesuBlockEntity be = place(helper);
		if (be == null) {
			return;
		}
		be.getEnergyStorage().amount = 0;

		ItemStack pack = new ItemStack(ModContent.ENERGY_PACK.get());
		long packCharge = ItemEnergy.capacity(pack);
		if (packCharge <= 0) {
			helper.fail("energy pack reports no capacity — test cannot prove anything");
			return;
		}
		ItemEnergy.set(pack, packCharge);
		be.setItem(CesuBlockEntity.DISCHARGE_SLOT, pack);

		long before = be.getEnergyStorage().amount;
		// One server tick of the block's own logic.
		be.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState());
		long moved = be.getEnergyStorage().amount - before;

		if (moved <= 0) {
			helper.fail("discharge slot moved no EU: buffer still " + be.getEnergyStorage().amount);
			return;
		}
		if (moved > dev.alaindustrial.core.energy.EnergyTier.MV.maxVoltage()) {
			helper.fail("discharge exceeded the MV ceiling in one tick: moved " + moved
					+ " > " + dev.alaindustrial.core.energy.EnergyTier.MV.maxVoltage());
			return;
		}
		long left = ItemEnergy.get(be.getItem(CesuBlockEntity.DISCHARGE_SLOT));
		if (left != packCharge - moved) {
			helper.fail("EU not conserved: item lost " + (packCharge - left) + " but buffer gained " + moved);
			return;
		}
		helper.succeed();
	}
}
