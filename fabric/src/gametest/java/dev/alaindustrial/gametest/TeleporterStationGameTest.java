package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * L2 functional suite for the Teleporter station (MOD-091) — the HV anchor, without the jump
 * (MOD-092) or the GUI (MOD-093). Covers what this task actually ships: HV intake on five faces
 * with an inert front, the oversized buffer, owner/privacy state and how it survives save/load and
 * break → place.
 */
public class TeleporterStationGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	private static TeleporterBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModBlocks.TELEPORTER, TeleporterBlockEntity.class);
	}

	/**
	 * @implements TC-TELE-001-FUN01 — the station accepts EU and never emits it: the jump fund is
	 *     spent by the jump alone (MOD-092), so the network must not be able to drain it back out.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcTele001Fun01_acceptsButNeverEmits(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		if (!station.getEnergyStorage().supportsInsertion()) {
			helper.fail("teleporter must accept energy (maxInsert > 0)");
		}
		if (station.getEnergyStorage().supportsExtraction()) {
			helper.fail("teleporter must NOT emit energy — the network could drain the jump fund");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-001-FUN02 — the buffer is Config.teleporterBuffer (500 000 EU by default),
	 *     which is what makes the station usable while its chunk is unloaded (~25–50 jumps banked).
	 */
	@GameTest
	public void tcTele001Fun02_bufferMatchesConfig(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		if (station.getEnergyStorage().getCapacity() != Config.teleporterBuffer) {
			helper.fail("teleporter buffer " + station.getEnergyStorage().getCapacity()
					+ " != Config.teleporterBuffer " + Config.teleporterBuffer);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-001-FUN03 — HV tier: the per-tick intake ceiling is the HV voltage.
	 */
	@GameTest
	public void tcTele001Fun03_hvIntakeRate(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		if (station.getTier() != EnergyTier.HV) {
			helper.fail("teleporter must be HV, got " + station.getTier());
		}
		if (station.getEnergyStorage().maxInsert != EnergyTier.HV.maxVoltage()) {
			helper.fail("teleporter intake " + station.getEnergyStorage().maxInsert
					+ " != HV " + EnergyTier.HV.maxVoltage());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-001-NRG03 — five working faces accept EU; the FACING front is inert, so no
	 *     cable draws a misleading arm into the station's facade.
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcTele001Nrg03_frontFaceInert(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		Direction facing = station.getBlockState().getValue(HorizontalMachineBlock.FACING);
		if (station.energyRoleForFace(facing) != EnergyRole.NONE) {
			helper.fail("FACING front must be energy-inert, got " + station.energyRoleForFace(facing));
		}
		if (station.energyPort(facing) != null) {
			helper.fail("FACING front must expose no energy port");
		}
		for (Direction dir : Direction.values()) {
			if (dir == facing) {
				continue;
			}
			if (station.energyRoleForFace(dir) != EnergyRole.IN) {
				helper.fail("working face " + dir + " must accept EU (IN), got " + station.energyRoleForFace(dir));
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-001-PER01 — owner, owner name, privacy flag and EU all survive an NBT
	 *     round-trip (what a relog does).
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcTele001Per01_stateSurvivesNbt(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		station.setOwner(OWNER, "Steve");
		station.setPrivate(false);
		station.getEnergyStorage().amount = 123_456L;

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = station.saveCustomOnly(registries);
		// POS is test-relative; the BE constructor needs the real world state, so go through the
		// station's own absolute BlockPos (relative POS would read air out in the world).
		BlockPos worldPos = station.getBlockPos();
		TeleporterBlockEntity restored = new TeleporterBlockEntity(worldPos,
				helper.getLevel().getBlockState(worldPos));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (!OWNER.equals(restored.getOwner())) {
			helper.fail("owner lost on NBT round-trip: " + restored.getOwner());
		}
		if (!"Steve".equals(restored.getOwnerName())) {
			helper.fail("owner name lost on NBT round-trip: " + restored.getOwnerName());
		}
		if (restored.isPrivate()) {
			helper.fail("privacy flag lost on NBT round-trip — public station came back private");
		}
		if (restored.getEnergyStorage().getAmount() != 123_456L) {
			helper.fail("EU lost on NBT round-trip: " + restored.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-001-PER02 — a fresh station is private and unowned by default: privacy is
	 *     opt-out, never opt-in.
	 */
	@GameTest
	public void tcTele001Per02_defaultsToPrivate(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		if (!station.isPrivate()) {
			helper.fail("a fresh station must default to private");
		}
		if (station.getOwner() != null) {
			helper.fail("a station placed by no player must have no owner, got " + station.getOwner());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-001-BRK07 — the drop carries EU and the privacy flag, but NOT the owner:
	 *     ownership is re-assigned to whoever places the block (battery-box semantics), so a gifted
	 *     or looted station becomes its new holder's.
	 * @covers R-BRK-07
	 */
	@GameTest
	public void tcTele001Brk07_dropCarriesEnergyAndPrivacyButNotOwner(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		station.setOwner(OWNER, "Steve");
		station.setPrivate(false);
		station.getEnergyStorage().amount = 42_000L;

		DataComponentMap map = station.collectComponents();
		Long carried = map.get(ModDataComponents.STORED_ENERGY.get());
		if (carried == null || carried != 42_000L) {
			helper.fail("STORED_ENERGY not emitted on drop: " + carried);
		}
		if (!Boolean.FALSE.equals(map.get(ModDataComponents.TELEPORTER_PRIVATE.get()))) {
			helper.fail("privacy flag not emitted on drop: " + map.get(ModDataComponents.TELEPORTER_PRIVATE.get()));
		}

		BlockPos worldPos = station.getBlockPos();
		TeleporterBlockEntity restored = new TeleporterBlockEntity(worldPos,
				helper.getLevel().getBlockState(worldPos));
		restored.applyComponents(map, DataComponentPatch.EMPTY);
		if (restored.getEnergyStorage().getAmount() != 42_000L) {
			helper.fail("EU not restored from component: " + restored.getEnergyStorage().getAmount());
		}
		if (restored.isPrivate()) {
			helper.fail("public flag not restored from component");
		}
		if (restored.getOwner() != null) {
			helper.fail("owner must NOT ride the item — it is re-assigned on place, got " + restored.getOwner());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-001-SEC01 — access control: a private station admits only its owner; a
	 *     public one admits anyone. This is the gate MOD-092's bind/jump will call.
	 */
	@GameTest
	public void tcTele001Sec01_privacyGate(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		station.setOwner(OWNER, "Steve");

		station.setPrivate(true);
		if (!station.allowsAccess(OWNER)) {
			helper.fail("a private station must admit its owner");
		}
		if (station.allowsAccess(STRANGER)) {
			helper.fail("a private station must not admit a stranger");
		}

		station.setPrivate(false);
		if (!station.allowsAccess(STRANGER)) {
			helper.fail("a public station must admit anyone");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-001-CON01 — the station IS a storage sink, so the network serves working
	 *     machines first and banks the jump fund from the surplus.
	 */
	@GameTest
	public void tcTele001Con01_isAStorageSink(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		if (!station.isEnergyStorageSink()) {
			helper.fail("teleporter must be a storage sink — as a machine its 512 EU/t demand would "
					+ "out-weigh every 32 EU/t machine in the proportional split and starve the base");
		}
		helper.succeed();
	}

	/**
	 * The regression this suite exists for: the station must not starve the rest of the base.
	 *
	 * <p>The station is the mod's only HV consumer (512 EU/t) among LV machines (32 EU/t). The
	 * network splits supply proportionally to demand, so if the station were served in the machine
	 * class it would win a 512:32 split and take ~94 % of a shared grid — a player who plugs in a
	 * teleporter would watch every macerator stall. Being a storage sink is what prevents it, and
	 * this test drives the real network to prove it: one generator, one macerator and one station on
	 * a shared cable, the macerator must still be fed.
	 *
	 * @implements TC-TELE-001-CON02 — a station on a shared grid does not starve a working machine.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcTele001Con02_doesNotStarveMachines(GameTestHelper helper) {
		// generator -> cable -> {macerator east, teleporter south}: one supply, two consumers, two
		// classes. The station is placed FACING=SOUTH so the cable meets its northern face — its
		// FACING front is energy-inert, and a default-facing station here would simply not connect.
		BlockPos genPos = new BlockPos(1, 2, 1);
		BlockPos cablePos = new BlockPos(2, 2, 1);
		BlockPos macPos = new BlockPos(3, 2, 1);
		BlockPos telePos = new BlockPos(2, 2, 2);

		helper.setBlock(genPos, ModBlocks.GENERATOR);
		helper.setBlock(cablePos, ModBlocks.COPPER_CABLE);
		helper.setBlock(macPos, ModBlocks.MACERATOR);
		helper.setBlock(telePos, ModBlocks.TELEPORTER.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));

		GeneratorBlockEntity gen = helper.getBlockEntity(genPos, GeneratorBlockEntity.class);
		MachineBlockEntity mac = helper.getBlockEntity(macPos, MachineBlockEntity.class);
		TeleporterBlockEntity station = helper.getBlockEntity(telePos, TeleporterBlockEntity.class);
		CableBlockEntity cable = helper.getBlockEntity(cablePos, CableBlockEntity.class);
		// Fuel the generator rather than writing its buffer directly: a raw `amount = …` write skips
		// the EnergyBuffer commit hook that wakes the network, and NetworkManager would just skip a
		// sleeping net (the grid would look dead and the test would prove nothing).
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));

		// Drive generator + cable + the network (same pattern as NetworkGameTest#drive): the cable
		// registers its network from its own tick, the generator produces from its own.
		//
		// 300 ticks is chosen from the real numbers, not guessed: the generator makes 8 EU/t
		// (fuelEuPerTick) and the macerator spends 2 EU/t while filling its own 800 EU buffer, so it
		// saturates after ~133 ticks and only then does surplus reach the station. Until that point
		// the machine legitimately takes everything and the station — a sink — waits. That ordering
		// IS the feature under test, so the window has to cover both halves of it.
		for (int i = 0; i < 300; i++) {
			gen.serverTick(helper.getLevel(), genPos, helper.getLevel().getBlockState(gen.getBlockPos()));
			cable.serverTick(helper.getLevel(), cable.getBlockPos(),
					helper.getLevel().getBlockState(cable.getBlockPos()));
			NetworkManager.tickAll(helper.getLevel());
			mac.serverTick(helper.getLevel(), macPos, helper.getLevel().getBlockState(mac.getBlockPos()));
		}

		long macEu = mac.getEnergyStorage().getAmount();
		long stationEu = station.getEnergyStorage().getAmount();
		if (macEu <= 0) {
			helper.fail("the macerator got nothing: the station starved it (station took " + stationEu + " EU)");
		}
		if (stationEu <= 0) {
			helper.fail("the station never charged from the surplus (macerator holds " + macEu + " EU) — "
					+ "a sink must still fill once the machines are fed, or the jump fund never builds");
		}
		helper.succeed();
	}
}
