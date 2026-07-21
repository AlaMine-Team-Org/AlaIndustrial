package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.neoforge.NeoForgeEnergyPort;
import dev.alaindustrial.core.neoforge.NeoForgeFluidPort;
import dev.alaindustrial.core.neoforge.TankAsResourceHandler;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-028 — NeoForge RUNTIME fluid tests, headless, the fluid counterpart of
 * {@link NeoForgeEnergyRuntimeTest}.
 *
 * <p><b>Why this test exists.</b> {@code CoreFluidScenarios.sourceToPumpToGeoToEu} (the only NeoForge fluid
 * gametest) places two of our own {@code FluidPortHost} blocks (pump + geothermal generator) next to each
 * other. {@code NeoForgeFluidLookup.find} takes a host-direct shortcut for any block that implements
 * {@link dev.alaindustrial.core.fluid.FluidPortHost} ({@code host.fluidPort(side)}), bypassing
 * {@code Capabilities.Fluid.BLOCK} entirely — so that gametest never routes through
 * {@link TankAsResourceHandler} or {@link NeoForgeFluidPort}, the actual capability adapters a real
 * foreign/other-mod fluid tank would be exposed through or read as. (Fabric has no such shortcut —
 * {@code FabricFluidLookup.find} always calls {@code FluidStorage.SIDED.find}, so the Fabric equivalent
 * scenario DOES exercise {@code FabricFluidPort}/{@code TankAsFluidStorage}.) This test drives the two
 * NeoForge fluid adapter classes directly, inside a real {@code Transaction}, closing that coverage gap.
 *
 * <p>Mirrors {@link NeoForgeEnergyRuntimeTest}'s pattern and rationale for using
 * {@link EphemeralTestServerProvider} (registry/capability context boots; no ticking world — world
 * placement + capability-registry round-tripping belongs to the gametest server, not this passive JUnit
 * server. The adapter classes are exercised directly instead, which is exactly the seam this test targets).
 *
 * <p>Every NeoForge/MC symbol here is verified against the decompiled 26.2.0.8-beta sources:
 * {@code ResourceHandler<FluidResource>.insert/extract(int, FluidResource, int, TransactionContext)},
 * {@code getAmountAsLong/getCapacityAsLong}, {@code Transaction.openRoot()/commit()/close()}.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class NeoForgeFluidRuntimeTest {

	private static final FluidHolder LAVA = FluidHolder.of(Fluids.LAVA);

	/** A bare tank standing in for a foreign mod's fluid storage, published as a capability would be. */
	private static FluidTank foreignTank(long capacity) {
		return new FluidTank(capacity, fluid -> true, fluid -> true, () -> {});
	}

	/**
	 * MOD-111: the portable tank publishes its actual shared core tank through the NeoForge adapter,
	 * with the configured 8000 mB capacity and transactional rollback.
	 */
	@Test
	void portableTankCapabilityUsesConfiguredCapacityAndRollsBack(MinecraftServer server) {
		assertNotNull(server, "ephemeral MinecraftServer was not injected");

		FluidTankBlockEntity tank = new FluidTankBlockEntity(new BlockPos(8, 64, 0),
				ModContent.FLUID_TANK.get().defaultBlockState());
		ResourceHandler<FluidResource> handler = TankAsResourceHandler.of(tank.fluidPort(null));
		assertNotNull(handler, "portable tank must publish a NeoForge fluid handler");
		assertEquals(Config.fluidTankCapacity,
				handler.getCapacityAsLong(0, FluidResource.of(Fluids.WATER)),
				"handler capacity must match Config.fluidTankCapacity");

		try (Transaction tx = Transaction.openRoot()) {
			int inserted = handler.insert(0, FluidResource.of(Fluids.WATER),
					(int) FluidAmounts.BUCKET, tx);
			assertEquals((int) FluidAmounts.BUCKET, inserted);
			assertEquals(FluidAmounts.BUCKET, tank.fluidTank.amount,
					"mid-transaction state must reflect the insert");
			// No commit: close must restore both amount and fluid identity.
		}
		assertEquals(0L, tank.fluidTank.amount, "uncommitted portable-tank insert must roll back");
		assertTrue(tank.fluidTank.fluid.isEmpty(), "rolled-back empty tank must not retain a fluid identity");
	}

	/**
	 * Real BE stand-in: the pump's own tank (a {@link FluidPort}) is published as a
	 * {@code ResourceHandler<FluidResource>} via {@link TankAsResourceHandler} — exactly what
	 * {@code RegisterCapabilitiesEvent} does for {@code Capabilities.Fluid.BLOCK} — then a SEPARATE
	 * "foreign" tank pulls exactly 1 bucket (1000 mB) from it through {@link NeoForgeFluidPort}, inside a
	 * real committed {@link Transaction}. This is the exact capability round-trip a player placing the pump
	 * next to another mod's lava tank would hit, and which {@code sourceToPumpToGeoToEu}'s host-shortcut
	 * never reaches.
	 */
	@Test
	void foreignHandlerExtractsExactlyOneBucketFromPump(MinecraftServer server) {
		assertNotNull(server, "ephemeral MinecraftServer was not injected");

		Block pumpBlock = ModContent.PUMP.get();
		BlockState pumpState = pumpBlock.defaultBlockState();
		PumpBlockEntity pump = new PumpBlockEntity(new BlockPos(0, 64, 0), pumpState);

		// Seed the pump's tank with 2 buckets of lava directly (bypassing acquireLava — this test targets
		// the capability adapter, not the acquisition tick logic already covered by tcPump001Fun03/04).
		pump.fluidTank.fluid = LAVA;
		pump.fluidTank.amount = FluidAmounts.BUCKET * 2;

		// Publish the pump's tank exactly as RegisterCapabilitiesEvent -> Capabilities.Fluid.BLOCK would.
		ResourceHandler<FluidResource> pumpHandler = TankAsResourceHandler.of(pump.fluidPort(null));
		assertNotNull(pumpHandler, "TankAsResourceHandler.of must not be null for a non-null port");
		assertEquals(FluidAmounts.BUCKET * 2, pumpHandler.getAmountAsLong(0),
				"published handler must read the pump's real tank amount");
		assertEquals(PumpBlockEntity.TANK_CAPACITY, pumpHandler.getCapacityAsLong(0, FluidResource.of(Fluids.LAVA)),
				"published handler must report the pump's real tank capacity");

		// Read the published capability back through the OTHER adapter direction — NeoForgeFluidPort — the
		// same class a foreign block reading capability at Capabilities.Fluid.BLOCK would receive.
		FluidPort viaCapability = NeoForgeFluidPort.of(pumpHandler);
		assertNotNull(viaCapability, "NeoForgeFluidPort.of must not be null for a non-null handler");

		FluidTank foreign = foreignTank(FluidAmounts.BUCKET * 4);
		long moved;
		try (Transaction tx = Transaction.openRoot()) {
			moved = viaCapability.extract(LAVA, FluidAmounts.BUCKET, NeoForgeEnergyPort.wrap(tx));
			long inserted = foreign.insert(LAVA, moved, NeoForgeEnergyPort.wrap(tx));
			assertEquals(moved, inserted, "foreign tank must accept everything the capability adapter gave up");
			tx.commit();
		}

		assertEquals(FluidAmounts.BUCKET, moved,
				"expected exactly 1 bucket (1000 mB) to move through the NeoForge capability adapter");
		assertEquals(FluidAmounts.BUCKET, foreign.amount, "foreign tank must gain exactly 1 bucket");
		assertEquals(FluidAmounts.BUCKET, pump.fluidTank.amount,
				"pump tank (read directly, not via capability) must drop by exactly 1 bucket");
		assertEquals(FluidAmounts.BUCKET, pumpHandler.getAmountAsLong(0),
				"pump tank (re-read via the SAME published capability handler) must reflect the committed extract");
	}

	/**
	 * Insert direction + unit pass-through: a foreign handler inserts exactly 1 bucket into the pump's
	 * published capability, mirroring a player-placed foreign lava source feeding the pump. Confirms
	 * {@link NeoForgeFluidPort#insert} and {@link TankAsResourceHandler#insert} both pass mB through 1:1 (no
	 * conversion on NeoForge, per the MOD-028 unit decision) and that the round trip is transactional.
	 */
	@Test
	void capabilityInsertMovesExactlyOneBucketIntoPump(MinecraftServer server) {
		assertNotNull(server, "ephemeral MinecraftServer was not injected");

		PumpBlockEntity pump = new PumpBlockEntity(new BlockPos(1, 64, 0), ModContent.PUMP.get().defaultBlockState());
		assertEquals(0L, pump.fluidTank.amount, "fresh pump tank should start empty");

		ResourceHandler<FluidResource> pumpHandler = TankAsResourceHandler.of(pump.fluidPort(null));
		FluidPort viaCapability = NeoForgeFluidPort.of(pumpHandler);

		FluidTank foreignSource = foreignTank(FluidAmounts.BUCKET * 4);
		foreignSource.fluid = LAVA;
		foreignSource.amount = FluidAmounts.BUCKET * 3;

		long moved;
		try (Transaction tx = Transaction.openRoot()) {
			long extracted = foreignSource.extract(LAVA, FluidAmounts.BUCKET,
					NeoForgeEnergyPort.wrap(tx));
			moved = viaCapability.insert(LAVA, extracted, NeoForgeEnergyPort.wrap(tx));
			tx.commit();
		}

		assertEquals(FluidAmounts.BUCKET, moved, "expected exactly 1 bucket to move into the pump via the capability");
		assertEquals(FluidAmounts.BUCKET, pump.fluidTank.amount, "pump tank must gain exactly 1 bucket");
		assertEquals(FluidAmounts.BUCKET * 2, foreignSource.amount, "foreign source must drop by exactly 1 bucket");
	}

	/**
	 * Uncommitted transaction rolls back through the SAME capability adapter chain (not just the bare
	 * {@code FluidTank} the energy runtime test already covers) — proving {@link TankAsResourceHandler}
	 * enlists the underlying tank in the NeoForge snapshot journal via {@code NeoForgeEnergyPort.wrap}
	 * rather than mutating state outside the transaction.
	 */
	@Test
	void uncommittedCapabilityExtractRollsBack(MinecraftServer server) {
		assertNotNull(server, "ephemeral MinecraftServer was not injected");

		PumpBlockEntity pump = new PumpBlockEntity(new BlockPos(2, 64, 0), ModContent.PUMP.get().defaultBlockState());
		pump.fluidTank.fluid = LAVA;
		pump.fluidTank.amount = FluidAmounts.BUCKET;

		ResourceHandler<FluidResource> pumpHandler = TankAsResourceHandler.of(pump.fluidPort(null));

		try (Transaction tx = Transaction.openRoot()) {
			int extracted = pumpHandler.extract(0, FluidResource.of(Fluids.LAVA), (int) FluidAmounts.BUCKET, tx);
			assertEquals((int) FluidAmounts.BUCKET, extracted, "extract should report the full bucket mid-transaction");
			assertEquals(0L, pumpHandler.getAmountAsLong(0), "mid-transaction read must reflect the uncommitted extract");
			// no tx.commit()
		}
		assertEquals(FluidAmounts.BUCKET, pump.fluidTank.amount,
				"uncommitted capability extract must roll the pump's tank back to its pre-transaction amount");
		// The tank's `fluid` identity must ALSO survive a full-drain-then-rollback: extract() no longer
		// pre-clears `fluid` when amount hits 0 (it clears at the commit/rollback terminal instead), so a
		// rolled-back full drain restores both amount AND fluid. Before the FluidTank fix this read EMPTY —
		// the tank reported amount>0 with fluid==EMPTY and became invisible to capability readers.
		assertTrue(pump.fluidTank.fluid.is(Fluids.LAVA),
				"rolled-back full drain must restore the fluid identity, not leave it EMPTY");
	}

	/** {@code of(null)} on both adapter directions yields no capability, mirroring the energy adapter contract. */
	@Test
	void ofNullYieldsNoCapability() {
		assertNull(TankAsResourceHandler.of(null), "TankAsResourceHandler.of(null) must be null");
		assertNull(NeoForgeFluidPort.of(null), "NeoForgeFluidPort.of(null) must be null");
	}
}
