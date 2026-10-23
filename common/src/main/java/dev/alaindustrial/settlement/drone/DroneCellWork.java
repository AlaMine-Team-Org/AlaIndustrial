package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.settlement.drone.ConstructionDroneWork.BatteryPay;
import dev.alaindustrial.settlement.drone.ConstructionDroneWork.Context;
import dev.alaindustrial.settlement.drone.DroneCellRules.Action;
import dev.alaindustrial.settlement.drone.DroneCellRules.Job;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.WorkJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * What a builder drone does to one cell, and what it reads off it, apart from its job loop
 * ({@link ConstructionDroneWork}, MOD-858): taking a block away quietly, the halves of a two-cell block, a falling
 * block with nothing under it, a creature in the way, and the shortages a foundation or a road names.
 */
final class DroneCellWork {

	private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** The ticks a falling block waits before it falls, as vanilla's own placement schedules it. */
	private static final int FALLING_DELAY = 2;

	private DroneCellWork() {
	}

	/**
	 * Whether cell {@code i} is the half of a two-cell block its other half pays for, and that other half is still to
	 * be placed by this job in the same stage (R2-4): then the drone skips it here and puts it together with the
	 * paying half. Otherwise — the pair already stands, or lies in another stage — the half is placed on its own.
	 */
	static boolean comesWithItsPair(ServerLevel level, BuildingPlan plan,
			Optional<BuildMaterials.StagedPlan> staged, int i) {
		if (!PairedCells.freeHalf(plan, i)) {
			return false;
		}
		int j = PairedCells.partnerIndex(plan, i).getAsInt();
		if (staged.isPresent() && !staged.get().stage(j).equals(staged.get().stage(i))) {
			return false;
		}
		BlockPos other = plan.pos(j);
		if (!level.isLoaded(other)) {
			return false;
		}
		return DroneCellRules.decide(Job.BUILD, DroneCellSurvey.survey(plan, j, level.getBlockState(other)))
				== Action.PLACE;
	}

	/**
	 * Takes the block at {@code pos} away, quietly as every drone change is — and wakes a falling block standing on it
	 * (owner's round 1 of 0.1.219): sand or gravel the clearing leaves in place falls as it would for a player, rather
	 * than hanging in the air. Its tick is the one a placed falling block schedules for itself.
	 */
	static void takeAway(ServerLevel level, BlockPos pos) {
		takeOtherHalf(level, pos, level.getBlockState(pos));
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
		DroneWires.changed(level, pos);
		BlockPos above = pos.above();
		BlockState over = level.getBlockState(above);
		if (over.getBlock() instanceof FallingBlock falling) {
			level.scheduleTick(above, falling, FALLING_DELAY);
		}
	}

	/**
	 * Takes the other half of a two-block plant at {@code pos} away with it (owner's round 4 of 0.1.219, R4-5): the
	 * drone's quiet change tells no neighbour, so a tall flower's top would hang in the air. Litter only — a door's
	 * halves are the plan's business ({@link PairedCells}).
	 */
	static void takeOtherHalf(ServerLevel level, BlockPos pos, BlockState world) {
		if (!DroneCellSurvey.litter(world) || !world.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return;
		}
		DoubleBlockHalf half = world.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
		BlockPos other = pos.relative(half.getDirectionToOther());
		if (level.getBlockState(other).is(world.getBlock())) {
			level.setBlock(other, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
		}
	}

	/**
	 * Whether {@code want} is a falling block with nothing under {@code pos} to hold it (owner's round 1 of 0.1.219):
	 * the drone puts no sand on air — it waits for the cell under it, which a building job, bottom up, puts first.
	 */
	static boolean unsupportedFalling(ServerLevel level, BlockPos pos, BlockState want) {
		return want.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(pos.below()));
	}

	/** «No materials: Earth Fill ×N» — the earth the foundation under a house still takes (R3-5). */
	static BlockReason foundationShortage(Context ctx, BuildingPlan plan) {
		int wanted = DroneCellSurvey.foundationWanted(ctx.level(), plan);
		BuildStageRules.Need need = new BuildStageRules.Need(SiteWork.FILL_NAME, Math.max(1, wanted));
		ctx.dispatcher().journal().missing(ctx.now(), BuildMaterials.describe(need.item()), need.count());
		return BuildMaterials.reason(List.of(need));
	}

	/**
	 * «No materials: Town Road ×N, Earth Fill ×M» — what the whole road still takes beyond what the buffer holds,
	 * noted in the journal too; at least the one item the slot in hand wants.
	 */
	static BlockReason roadShortage(Context ctx, List<BlockPos> course, RoadWork.Kind kind) {
		RoadWork.Bill bill = RoadWork.bill(ctx.level(), course);
		DispatcherBuffer buffer = ctx.dispatcher().buffer();
		List<BuildStageRules.Need> missing = new ArrayList<>();
		int roads = bill.roads() - buffer.count(RoadWork.ROAD_ITEM);
		if (roads > 0) {
			missing.add(new BuildStageRules.Need(RoadWork.ROAD_ITEM, roads));
		}
		int fills = bill.fills() - RoadWork.fillHeld(buffer);
		if (fills > 0) {
			missing.add(new BuildStageRules.Need(SiteWork.FILL_NAME, fills));
		}
		if (missing.isEmpty()) {
			missing.add(new BuildStageRules.Need(kind == RoadWork.Kind.FILL ? SiteWork.FILL_NAME : RoadWork.ROAD_ITEM,
					1));
		}
		missing.forEach(need -> ctx.dispatcher().journal().missing(ctx.now(), BuildMaterials.describe(need.item()),
				need.count()));
		return BuildMaterials.reason(missing);
	}

	/** Whether a living creature stands in the cell at {@code pos}: the drone passes it and tries again later. */
	static boolean creatureIn(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty();
	}

	/** Pays {@code blocks} blocks' price: the core first, the battery for the rest (R94). */
	static void pay(Context ctx, BatteryPay battery, int blocks) {
		long price = DroneNestRules.blockPrice() * blocks;
		long fromCore = ctx.core().map(core -> core.giveToDrones(price)).orElse(0L);
		battery.spend(price - fromCore);
	}

	/**
	 * A clearing is done: the town's road plan hears its ground moved — the ring's terrain and rubble went (R3-5) — and
	 * a first clearing is news for the restorer.
	 */
	static void cleared(Context ctx, WorkJob held) {
		TownRoadNetwork.groundMoved(ctx.level(), ctx.town());
		if (!held.repair()) {
			HallBuildNews.cleared(ctx.level(), ctx.town(), held.building());
		}
	}
}
