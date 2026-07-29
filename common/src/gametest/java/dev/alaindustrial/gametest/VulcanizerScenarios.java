package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.core.heat.WorldHeatSources;
import dev.alaindustrial.registry.ModContent;
import java.util.Arrays;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral MOD-258 world scenarios. Fabric and NeoForge wrappers execute these same bodies, so
 * recipe registration, block entities and demand-driven heater behaviour cannot drift by loader.
 */
public final class VulcanizerScenarios {
	private static final BlockPos MACHINE = new BlockPos(1, 2, 1);
	private static final BlockPos HEAT = MACHINE.below();
	private static final long AMPLE_EU = 800L;

	private VulcanizerScenarios() {
	}

	private static VulcanizerBlockEntity placeMachine(GameTestHelper helper) {
		helper.setBlock(MACHINE, ModContent.VULCANIZER.get());
		VulcanizerBlockEntity be = helper.getBlockEntity(MACHINE, VulcanizerBlockEntity.class);
		if (be == null) {
			helper.fail("vulcanizer block entity missing after placement");
		}
		return be;
	}

	private static void stock(VulcanizerBlockEntity be, int count) {
		be.setItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT, new ItemStack(ModContent.RAW_RUBBER.get(), count));
		be.setItem(VulcanizerBlockEntity.SULFUR_SLOT, new ItemStack(ModContent.SULFUR_DUST.get(), count));
	}

	private static int operationTicks() {
		return Config.scaledDuration(Config.vulcanizerDuration) + 2;
	}

	private static void drive(MachineBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
	}

	private static void passiveHeat(GameTestHelper helper, Block block) {
		if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
			helper.setBlock(HEAT, block.defaultBlockState().setValue(CampfireBlock.LIT, true));
		} else {
			helper.setBlock(HEAT, block);
		}
	}

	private static ElectricHeaterBlockEntity poweredHeater(GameTestHelper helper, long energy) {
		helper.setBlock(HEAT, ModContent.ELECTRIC_HEATER.get());
		ElectricHeaterBlockEntity heater = helper.getBlockEntity(HEAT, ElectricHeaterBlockEntity.class);
		if (heater == null) {
			helper.fail("electric-heater block entity missing after placement");
		}
		heater.getEnergyStorage().amount = energy;
		return heater;
	}

	private static void assertOutput(GameTestHelper helper, VulcanizerBlockEntity be, int count) {
		ItemStack output = be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT);
		if (!output.is(ModContent.RUBBER.get()) || output.getCount() != count) {
			helper.fail("expected " + count + " rubber, got " + output);
		}
	}

	/** Campfire, lava and powered electric heat produce x1, x2 and x3 without extra ingredients. */
	public static void fun01HeatLevelsScaleOutput(GameTestHelper helper) {
		for (int expected = 1; expected <= 3; expected++) {
			helper.setBlock(MACHINE, Blocks.AIR);
			helper.setBlock(HEAT, Blocks.AIR);
			if (expected == 1) {
				passiveHeat(helper, Blocks.CAMPFIRE);
			} else if (expected == 2) {
				passiveHeat(helper, Blocks.LAVA);
			} else {
				poweredHeater(helper, AMPLE_EU);
			}
			VulcanizerBlockEntity be = placeMachine(helper);
			be.getEnergyStorage().amount = AMPLE_EU;
			stock(be, 2);

			drive(be, helper, operationTicks());

			assertOutput(helper, be, expected);
			if (be.getItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT).getCount() != 1
					|| be.getItem(VulcanizerBlockEntity.SULFUR_SLOT).getCount() != 1) {
				helper.fail("heat x" + expected + " must consume exactly one raw rubber and one sulfur");
				return;
			}
		}
		helper.succeed();
	}

	/** The adapter recognizes every shipped passive source, including both campfires. */
	public static void fun02AllPassiveHeatSourcesResolve(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		Block[] sources = {
				Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.LAVA,
				Blocks.MAGMA_BLOCK, Blocks.LAVA_CAULDRON
		};
		HeatSource[] expected = {
				HeatSource.CAMPFIRE, HeatSource.CAMPFIRE, HeatSource.LAVA,
				HeatSource.MAGMA, HeatSource.LAVA_CAULDRON
		};
		for (int i = 0; i < sources.length; i++) {
			passiveHeat(helper, sources[i]);
			be.onHeatNeighbourChanged();
			if (be.heatSource() != expected[i]) {
				helper.fail(sources[i] + " resolved as " + be.heatSource() + ", expected " + expected[i]);
				return;
			}
		}
		helper.succeed();
	}

	/** Unlit campfires and underfunded electric heaters are not usable heat sources. */
	public static void neg03InactiveHeatSourcesResolveAsNone(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		helper.setBlock(HEAT, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		be.onHeatNeighbourChanged();
		if (be.heatSource() != HeatSource.NONE) {
			helper.fail("an unlit campfire resolved as " + be.heatSource());
			return;
		}

		int cost = Config.electricHeaterEuPerTickEffective();
		poweredHeater(helper, cost - 1L);
		be.onHeatNeighbourChanged();
		if (be.heatSource() != HeatSource.NONE) {
			helper.fail("an underfunded electric heater resolved as " + be.heatSource());
			return;
		}
		helper.succeed();
	}

	/** With no heat, neither progress, EU nor ingredients move. */
	public static void neg01NoHeatNoWork(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().amount = AMPLE_EU;
		stock(be, 1);

		drive(be, helper, 3);

		if (be.getDataAccess().get(2) != 0 || be.getEnergyStorage().getAmount() != AMPLE_EU
				|| be.getItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT).getCount() != 1
				|| be.getItem(VulcanizerBlockEntity.SULFUR_SLOT).getCount() != 1) {
			helper.fail("unheated vulcanizer changed progress, energy or inputs");
			return;
		}
		helper.succeed();
	}

	/** With no EU, passive heat cannot produce or consume ingredients. */
	public static void neg02NoPowerNoWork(GameTestHelper helper) {
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity be = placeMachine(helper);
		stock(be, 1);

		drive(be, helper, operationTicks());

		if (be.getDataAccess().get(2) != 0 || !be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT).isEmpty()
				|| be.getItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT).getCount() != 1
				|| be.getItem(VulcanizerBlockEntity.SULFUR_SLOT).getCount() != 1) {
			helper.fail("unpowered vulcanizer progressed, produced, or consumed inputs");
			return;
		}
		helper.succeed();
	}

	/** A full output freezes the operation before either the machine or electric heater spends EU. */
	public static void con01OutputJamFreezesBothConsumers(GameTestHelper helper) {
		ElectricHeaterBlockEntity heater = poweredHeater(helper, AMPLE_EU);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().amount = AMPLE_EU;
		stock(be, 1);
		be.setItem(VulcanizerBlockEntity.OUTPUT_SLOT, new ItemStack(ModContent.RUBBER.get(), 64));

		drive(be, helper, 3);

		if (be.getDataAccess().get(2) != 0 || be.getEnergyStorage().getAmount() != AMPLE_EU
				|| heater.getEnergyStorage().getAmount() != AMPLE_EU) {
			helper.fail("blocked output spent progress or EU: progress=" + be.getDataAccess().get(2)
					+ " machine=" + be.getEnergyStorage().getAmount()
					+ " heater=" + heater.getEnergyStorage().getAmount());
			return;
		}
		helper.succeed();
	}

	/** The heater pays exactly one configured tariff only when the Vulcanizer advances. */
	public static void con02HeaterIsDemandDriven(GameTestHelper helper) {
		ElectricHeaterBlockEntity heater = poweredHeater(helper, AMPLE_EU);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().amount = AMPLE_EU;
		stock(be, 1);
		int cost = Config.electricHeaterEuPerTickEffective();

		drive(be, helper, 1);
		if (be.getDataAccess().get(2) != 1 || heater.getEnergyStorage().getAmount() != AMPLE_EU - cost) {
			helper.fail("one useful tick must drain exactly " + cost + " heater EU");
			return;
		}

		be.setItem(VulcanizerBlockEntity.OUTPUT_SLOT, new ItemStack(ModContent.RUBBER.get(), 64));
		long idleEnergy = heater.getEnergyStorage().getAmount();
		drive(be, helper, 3);
		drive(heater, helper, 3);
		if (heater.getEnergyStorage().getAmount() != idleEnergy) {
			helper.fail("idle/jammed heater consumed EU: " + idleEnergy + " -> "
					+ heater.getEnergyStorage().getAmount());
			return;
		}
		helper.succeed();
	}

	/** The electric heater accepts the exact tariff, commits it once, and rejects a second draw. */
	public static void con03HeaterTariffIsAtomicAtThreshold(GameTestHelper helper) {
		int cost = Config.electricHeaterEuPerTickEffective();
		ElectricHeaterBlockEntity heater = poweredHeater(helper, cost);
		if (WorldHeatSources.resolve(helper.getLevel(), helper.absolutePos(MACHINE))
				!= HeatSource.ELECTRIC_HEATER) {
			helper.fail("heater with the exact tariff was not discoverable");
			return;
		}
		if (!heater.consumeHeatTick() || heater.getEnergyStorage().getAmount() != 0L) {
			helper.fail("heater did not atomically consume the exact tariff");
			return;
		}
		if (heater.consumeHeatTick() || heater.getEnergyStorage().getAmount() != 0L) {
			helper.fail("empty heater accepted a second heat draw");
			return;
		}
		helper.succeed();
	}

	/** Raising heat restarts the batch at zero and completes with the new tier. */
	public static void reg01HeatUpgradeRestartsCycle(GameTestHelper helper) {
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().amount = AMPLE_EU;
		stock(be, 1);
		drive(be, helper, 1);
		if (be.cycleHeatLevel() != 1) {
			helper.fail("first campfire tick did not snapshot heat level 1");
			return;
		}

		poweredHeater(helper, AMPLE_EU);
		be.onHeatNeighbourChanged();
		if (be.getDataAccess().get(2) != 0 || be.cycleHeatLevel() != 0) {
			helper.fail("heat upgrade did not restart the in-flight cycle");
			return;
		}
		drive(be, helper, operationTicks());

		assertOutput(helper, be, 3);
		helper.succeed();
	}

	/** Dropping from electric heat to lava restarts and completes at x2 without replacing the machine. */
	public static void reg03HeatDowngradeRestartsCycle(GameTestHelper helper) {
		poweredHeater(helper, AMPLE_EU);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().amount = AMPLE_EU;
		stock(be, 1);
		drive(be, helper, 1);
		if (be.getDataAccess().get(2) != 1 || be.cycleHeatLevel() != 3) {
			helper.fail("electric heat did not start a tier-3 cycle");
			return;
		}

		passiveHeat(helper, Blocks.LAVA);
		be.onHeatNeighbourChanged();
		if (be.getDataAccess().get(2) != 0 || be.cycleHeatLevel() != 0) {
			helper.fail("heat downgrade did not restart the in-flight cycle");
			return;
		}
		drive(be, helper, operationTicks());
		assertOutput(helper, be, 2);
		helper.succeed();
	}

	/** Both input slots have strict predicates and the bottom face is entirely reserved for heat. */
	public static void reg02AutomationKeepsInputsSeparated(GameTestHelper helper) {
		VulcanizerBlockEntity be = placeMachine(helper);
		ItemStack raw = new ItemStack(ModContent.RAW_RUBBER.get());
		ItemStack sulfur = new ItemStack(ModContent.SULFUR_DUST.get());
		ItemStack rubber = new ItemStack(ModContent.RUBBER.get());

		if (!be.canPlaceItemThroughFace(VulcanizerBlockEntity.RAW_RUBBER_SLOT, raw, Direction.UP)
				|| be.canPlaceItemThroughFace(VulcanizerBlockEntity.RAW_RUBBER_SLOT, sulfur, Direction.UP)
				|| !be.canPlaceItemThroughFace(VulcanizerBlockEntity.SULFUR_SLOT, sulfur, Direction.UP)
				|| be.canPlaceItemThroughFace(VulcanizerBlockEntity.SULFUR_SLOT, raw, Direction.UP)
				|| be.canPlaceItemThroughFace(VulcanizerBlockEntity.OUTPUT_SLOT, rubber, Direction.UP)) {
			helper.fail("vulcanizer input/output slot predicates are not separated");
			return;
		}
		if (be.getSlotsForFace(Direction.DOWN).length != 0
				|| be.canTakeItemThroughFace(VulcanizerBlockEntity.OUTPUT_SLOT, rubber, Direction.DOWN)) {
			helper.fail("the heat-facing bottom must expose no automation slots");
			return;
		}
		if (!Arrays.stream(be.getSlotsForFace(Direction.UP))
				.anyMatch(slot -> slot == VulcanizerBlockEntity.RAW_RUBBER_SLOT)) {
			helper.fail("top face does not expose the raw-rubber input");
			return;
		}
		helper.succeed();
	}

	/** Energy, both inputs, progress and the active heat tier survive serialization. */
	public static void sta01RoundTripPreservesInFlightCycle(GameTestHelper helper) {
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity src = placeMachine(helper);
		src.getEnergyStorage().amount = 321L;
		stock(src, 3);
		drive(src, helper, 1);

		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = src.saveCustomOnly(registries);
		VulcanizerBlockEntity restored = new VulcanizerBlockEntity(
				helper.absolutePos(MACHINE), helper.getLevel().getBlockState(helper.absolutePos(MACHINE)));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != 321L - Config.machineEuPerTickEffective()
				|| restored.getDataAccess().get(2) != 1 || restored.cycleHeatLevel() != 1
				|| restored.getItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT).getCount() != 3
				|| restored.getItem(VulcanizerBlockEntity.SULFUR_SLOT).getCount() != 3) {
			helper.fail("in-flight vulcanizer cycle did not round-trip");
			return;
		}
		helper.succeed();
	}

	/** The existing inventory trigger still awards rubber_production for machine-made rubber. */
	public static void fun03RubberProductionAdvancement(GameTestHelper helper) {
		passiveHeat(helper, Blocks.CAMPFIRE);
		VulcanizerBlockEntity be = placeMachine(helper);
		be.getEnergyStorage().amount = AMPLE_EU;
		stock(be, 1);
		drive(be, helper, operationTicks());
		assertOutput(helper, be, 1);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
				.get(Industrialization.id("rubber_production"));
		if (advancement == null) {
			helper.fail("advancement alaindustrial:rubber_production is not loaded");
			return;
		}
		if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
			helper.fail("rubber_production was awarded before the player received rubber");
			return;
		}
		player.getInventory().add(be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT).copy());
		player.inventoryMenu.broadcastChanges();
		if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
			helper.fail("receiving Vulcanizer rubber did not award rubber_production");
			return;
		}
		helper.succeed();
	}
}
