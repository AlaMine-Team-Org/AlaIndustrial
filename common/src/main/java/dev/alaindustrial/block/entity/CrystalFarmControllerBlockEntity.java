package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CrystalFarmControllerBlock;
import dev.alaindustrial.block.CrystalSeedbedBlock;
import dev.alaindustrial.block.SprinklerBlock;
import dev.alaindustrial.core.crystal.CrystalGrowth;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.structure.CrystalFarmRoom;
import dev.alaindustrial.core.structure.RoomFill;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The brain of a crystal greenhouse (MOD-505): it seals the room, shows that it is sealed, and grows
 * everything inside it.
 *
 * <p><b>Why growth lives here and not in the seedbeds.</b> The cotton trellis grows on vanilla
 * {@code randomTick} and needs no block entity, which is the right shape for a plant scattered
 * across a field. A greenhouse is the opposite case: the crystals only grow <em>because</em> a room
 * encloses them, and the room already has exactly one ticking object — this one. Driving the beds
 * from here means a farm of a hundred beds still adds a single ticker, the water and power bonuses
 * are read once for the whole room instead of being rediscovered per block, and "growth requires a
 * sealed room" is true by construction rather than by a check that could be forgotten.
 *
 * <p><b>What grows is vanilla's own amethyst.</b> The bud chain is
 * {@code small → medium → large → cluster}, placed and advanced as the real blocks, so a greenhouse
 * harvest is indistinguishable from a geode one — including Fortune, which works because vanilla's
 * loot table is the one doing the work.
 *
 * <p><b>Two cadences, deliberately different.</b> The room is re-scanned often (a hole should grey
 * the floor out quickly), while growth is attempted rarely — a crystal takes an hour or two unaided,
 * so rolling for it more than a few times a minute would be wasted work.
 */
public class CrystalFarmControllerBlockEntity extends EnergyBlockEntity {

	/** Where the room's seedbeds were on the last successful scan. Rebuilt every scan, never saved. */
	private final List<BlockPos> seedbeds = new ArrayList<>();

	private RoomFill.Status status = RoomFill.Status.CONTROLLER_NOT_IN_WALL;
	private int scanCooldown;
	private int growthCooldown;

	/** Interior volume of the sealed room in blocks, or zero when it is not sealed. */
	private int volume;

	/** Offset of the offending block from this controller, for the chat report. */
	private int faultDx;
	private int faultDy;
	private int faultDz;

	/** Whether the last scan found water inside the room — the free half of the growth bonus. */
	private boolean hasWater;
	/** Sprinklers standing in the interior (MOD-525) — the third growth axis. */
	private final List<BlockPos> sprinklers = new ArrayList<>();

	/** Whether {@link #faultDx} and friends point at a real block, rather than at nothing in particular. */
	private boolean knowsFault;

	/** Whether the remembered footprint currently wears the sealed look. */
	private boolean sealedPaint;

	/**
	 * The exact shell of the last sealed scan, as flat {@code x, y, z} triples — the only thing that
	 * can say where a broken greenhouse broke.
	 *
	 * <p>Memory only, never saved. A room whose chunk reloaded while it was open simply shows no
	 * fault particles until it seals once more, which is a fair trade against writing a couple of
	 * thousand coordinates into every controller's NBT.
	 */
	private int[] sealedShell = new int[0];

	/**
	 * The box this controller last sealed, or an empty one if it never has.
	 *
	 * <p><b>This is what lets a greenhouse come apart.</b> A failed scan measures nothing, so a sweep
	 * driven by the scan result could only ever switch the floor ON — punch a hole in a finished room
	 * and every block would stay seamless. Remembering the sealed box gives the controller something
	 * to clear. Kept in NBT because a chunk can unload while the room is whole and reload after a
	 * creeper has opened it.
	 */
	private int boxMinX;
	private int boxMinY;
	private int boxMinZ;
	private int boxMaxX = Integer.MIN_VALUE;
	private int boxMaxY = Integer.MIN_VALUE;
	private int boxMaxZ = Integer.MIN_VALUE;

	public CrystalFarmControllerBlockEntity(BlockPos pos, BlockState state) {
		// A greenhouse is low-tech: LV in, a small buffer, and nothing ever leaves. Power is optional
		// here — it only ever buys speed — so the buffer only has to smooth the boost.
		super(ModContent.CRYSTAL_FARM_CONTROLLER_BE.get(), pos, state, EnergyTier.LV,
				Config.crystalFarmBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	/** Re-arms the scan for the next tick — called when a neighbour changes or the block is placed. */
	public void requestScan() {
		scanCooldown = 0;
		wake();
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = Math.max(1, Config.crystalFarmScanIntervalTicks);
			rescan(level, pos, state);
		}
		if (growthCooldown > 0) {
			growthCooldown--;
		} else {
			growthCooldown = Math.max(1, Config.crystalFarmGrowthIntervalTicks);
			runGrowth(level);
		}
		// Never sleep: the periodic scan is the only thing that notices a room being taken apart out of
		// neighbour range, and a sleeping controller would leave a breached greenhouse looking sealed.
		return 0;
	}

	// --- the room ------------------------------------------------------------------------------

	private void rescan(Level level, BlockPos pos, BlockState state) {
		RoomFill.Result result = CrystalFarmRoom.scan(level, pos,
				state.getValue(CrystalFarmControllerBlock.FACING),
				Math.max(1, Config.crystalFarmRoomMinCells),
				Math.max(1, Config.crystalFarmRoomMaxCells),
				Math.max(1, Config.crystalFarmRoomMaxSpan));
		status = result.status();
		faultDx = result.x() - pos.getX();
		faultDy = result.y() - pos.getY();
		faultDz = result.z() - pos.getZ();

		boolean formed = result.sealed();
		boolean wasFormed = state.getValue(CrystalFarmControllerBlock.FORMED);
		volume = formed ? result.volume() : 0;

		seedbeds.clear();
		sprinklers.clear();
		hasWater = false;
		// Found BEFORE anything is repainted, and while the last sealed footprint is still known: it is
		// the only thing that can say where the hole is (see faultPosition).
		BlockPos fault = formed ? null : faultPosition(level, result);
		if (formed) {
			// Clear FIRST, paint second. The room may have been resealed in a different shape — a wing
			// added, a dome raised — and blocks that dropped out of the new shell must lose the sealed
			// look. Doing it the other way round would wipe the flags this very scan just wrote, since
			// the old footprint overlaps most of the new one.
			if (boxChanges(result)) {
				clearPaint(level);
			}
			// The whole shell wears the flag, not just this block: that is what makes a finished
			// greenhouse read as one surface instead of a stack of crates.
			CrystalFarmRoom.applyFormed(level, result.shell(), result.shellIsEdge());
			sealedPaint = true;
			// Kept so the next failed scan can name the block that went missing.
			sealedShell = result.shell();
			rememberBox(result);
			collectInterior(level, result);
		} else {
			clearPaint(level);
		}

		if (wasFormed != formed) {
			level.setBlock(pos, state.setValue(CrystalFarmControllerBlock.FORMED, formed),
					Block.UPDATE_ALL);
		}
		if (level instanceof ServerLevel server) {
			if (formed) {
				if (!wasFormed) {
					announceAssembly(server);
				}
			} else if (fault != null) {
				markProblem(server, fault, wasFormed);
			}
		}
	}

	/**
	 * Where to send the player, or {@code null} if there is nowhere honest to send them.
	 *
	 * <p>For an unsealed room the fill's own position is useless — it is wherever the flood ran out of
	 * leash, typically open ground far from the building (playtest five). The shell of the room this
	 * controller last sealed is asked instead, which finds the missing block itself. A greenhouse
	 * that has never closed has no such shell, and then no particles are shown at all.
	 */
	@Nullable
	private BlockPos faultPosition(Level level, RoomFill.Result result) {
		BlockPos fault = null;
		if (status == RoomFill.Status.UNSEALED) {
			fault = CrystalFarmRoom.findBreach(level, sealedShell);
		} else if (status == RoomFill.Status.CONTROLLER_NOT_IN_WALL
				|| status == RoomFill.Status.SECOND_CONTROLLER) {
			// These two name a real block outright: the controller itself, or a rival brain.
			fault = new BlockPos(result.x(), result.y(), result.z());
		}
		knowsFault = fault != null;
		if (fault != null) {
			// The chat report follows the particles rather than the fill: pointing the two at different
			// places would be worse than either alone.
			faultDx = fault.getX() - getBlockPos().getX();
			faultDy = fault.getY() - getBlockPos().getY();
			faultDz = fault.getZ() - getBlockPos().getZ();
		}
		return fault;
	}

	/**
	 * Marks the problem in the world. The chat line names the coordinates, but nobody finds a missing
	 * block in a 14³ shell by reading a number off a line — they walk to the smoke.
	 *
	 * <p>Loud once, then quiet: a greenhouse that just came apart plays a short note, because the
	 * player is usually looking elsewhere when a creeper opens their wall. After that it is only the
	 * particles — repeating the sound every two seconds would turn a helpful cue into a nuisance.
	 */
	private void markProblem(ServerLevel level, BlockPos where, boolean wasFormed) {
		level.sendParticles(ParticleTypes.SMOKE,
				where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5,
				16, 0.3, 0.3, 0.3, 0.01);
		if (wasFormed) {
			level.playSound(null, where, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.4f);
		}
	}

	/**
	 * The moment the greenhouse closes: one chime and a ring of particles along the seam.
	 *
	 * <p>Fired only on the transition, never on the periodic re-scan that finds the room still sealed
	 * — a greenhouse that chimed every two seconds would be unbearable to stand next to.
	 */
	private void announceAssembly(ServerLevel level) {
		level.playSound(null, getBlockPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
				0.9f, 1.2f);
		// Along the floor of the remembered footprint. A ring rather than the whole volume: a cloud
		// filling a dome would hide the very structure it is meant to point at.
		for (int x = boxMinX; x <= boxMaxX; x++) {
			for (int z = boxMinZ; z <= boxMaxZ; z++) {
				boolean rim = x == boxMinX || x == boxMaxX || z == boxMinZ || z == boxMaxZ;
				if (!rim) {
					continue;
				}
				level.sendParticles(ParticleTypes.END_ROD, x + 0.5, boxMinY + 0.1, z + 0.5,
						1, 0.0, 0.02, 0.0, 0.01);
			}
		}
	}

	/**
	 * One walk of the interior, answering both questions the growth loop needs: where the seedbeds
	 * are, and whether the room holds water. Two passes would read the same 1700-odd blocks twice.
	 */
	private void collectInterior(Level level, RoomFill.Result room) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int[] cells = room.interior();
		for (int i = 0; i < cells.length; i += 3) {
			cursor.set(cells[i], cells[i + 1], cells[i + 2]);
			BlockState state = level.getBlockState(cursor);
			if (state.getBlock() instanceof CrystalSeedbedBlock) {
				BlockPos bed = cursor.immutable();
				seedbeds.add(bed);
				// Tell the bed it is being looked after, so it can say so when the player feeds it.
				if (!state.getValue(CrystalSeedbedBlock.TENDED)) {
					level.setBlock(bed, state.setValue(CrystalSeedbedBlock.TENDED, true), 2);
				}
			} else if (state.getBlock() instanceof SprinklerBlock) {
				// MOD-525: a sprinkler indoors is the room's third speed-up. Its own aura cannot reach
				// the beds — they are not bonemealable and have no block entity — so the controller
				// drives it instead, charging solution per delivered crystal.
				sprinklers.add(cursor.immutable());
			} else if (!hasWater && state.getFluidState().is(FluidTags.WATER)) {
				// Fluid state rather than block identity, so a waterlogged block counts too — the
				// requirement is "water somewhere in the room", not a particular block.
				hasWater = true;
			}
		}
	}

	private boolean hasRememberedBox() {
		return boxMaxX >= boxMinX && boxMaxY >= boxMinY && boxMaxZ >= boxMinZ;
	}

	/** Whether the sealed footprint moved since the last scan. */
	private boolean boxChanges(RoomFill.Result result) {
		return hasRememberedBox()
				&& (boxMinX != result.minX() || boxMinY != result.minY() || boxMinZ != result.minZ()
				|| boxMaxX != result.maxX() || boxMaxY != result.maxY() || boxMaxZ != result.maxZ());
	}

	private void rememberBox(RoomFill.Result result) {
		boxMinX = result.minX();
		boxMinY = result.minY();
		boxMinZ = result.minZ();
		boxMaxX = result.maxX();
		boxMaxY = result.maxY();
		boxMaxZ = result.maxZ();
		setChanged();
	}

	/**
	 * Takes the sealed look off the last known footprint, and <b>keeps the footprint</b>.
	 *
	 * <p>Forgetting the box here is what an earlier version did, and it quietly disabled the whole
	 * breach hunt: the box is the only record of where the room stood, and {@link #faultPosition}
	 * needs it to find the missing block. It is overwritten when a room seals again, which is the
	 * only moment a new footprint actually exists.
	 *
	 * <p>Guarded by {@link #sealedPaint} so a greenhouse that has been open for a while does not
	 * re-sweep its whole volume every two seconds for nothing.
	 */
	private void clearPaint(Level level) {
		if (!sealedPaint || !hasRememberedBox()) {
			return;
		}
		CrystalFarmRoom.clearFormed(level, boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ);
		sealedPaint = false;
		setChanged();
	}

	/**
	 * Greys the shell out however this controller is being removed — mined, blown up, {@code /setblock},
	 * a neighbouring mod.
	 *
	 * <p>This hook rather than the block's own removal callback, and rather than the player-only one
	 * it used to hang off: it is the last moment the block entity — and with it the box it remembers —
	 * is still attached. An audit found the hole in the player-only version: a greenhouse whose brain
	 * was blown up by a creeper kept its seamless shell and its tended seedbeds <em>forever</em>, with
	 * nothing left in the world that owned either, and the player went on feeding shards into beds
	 * that could never bud and were never going to say so. The incubator hands its dome back through
	 * this same hook, for this same reason.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			clearPaint(level);
		}
		super.preRemoveSideEffects(pos, state);
	}

	// --- growth --------------------------------------------------------------------------------

	/**
	 * One growth attempt for every seedbed the room holds.
	 *
	 * <p>Each bed rolls its own die: a shared roll would make a whole greenhouse advance in lockstep,
	 * which reads as a machine cycling rather than as things growing.
	 */
	private void runGrowth(Level level) {
		if (status != RoomFill.Status.SEALED || seedbeds.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (BlockPos bed : seedbeds) {
			// Asked PER BED, not once for the batch. An audit caught the batch version: a nearly empty
			// buffer let every bed in the room roll at the boosted rate while paying for one of them,
			// because drainInternal quietly clamps to whatever is left. A farm running on fumes ran as
			// if fully powered.
			boolean powered = energy.getAmount() >= euPerGrowth();
			// Asked per bed for the same reason `powered` is: one charged sprinkler must not hand the
			// whole room a boost it can only pay for once.
			SprinklerBlockEntity sprinkler = readySprinkler(level);
			int divisor = CrystalGrowth.effectiveChanceDivisor(Config.crystalFarmGrowthChanceDivisor,
					hasWater, powered, sprinkler != null, Config.crystalFarmWaterSpeedup,
					Config.crystalFarmPowerSpeedup, Config.crystalFarmSprinklerSpeedup);
			if (random.nextInt(divisor) != 0) {
				continue;
			}
			if (tryGrow(level, bed, random)) {
				// Paid for on delivery, not per attempt: energy and solution buy crystals, not dice rolls.
				if (powered) {
					energy.drainInternal(euPerGrowth());
				}
				if (sprinkler != null) {
					sprinkler.drawForGrowth();
				}
			}
		}
	}

	private long euPerGrowth() {
		return Math.max(0, Config.crystalFarmEuPerGrowth);
	}

	/**
	 * The first sprinkler in the room that could pay for one growth event, or {@code null}.
	 *
	 * <p>Re-read from the world rather than cached: the list is rebuilt only on the scan cadence, and
	 * in between a player may have mined one or drained it. A stale block entity here would hand out a
	 * boost nobody paid for.
	 */
	private SprinklerBlockEntity readySprinkler(Level level) {
		for (BlockPos pos : sprinklers) {
			if (level.getBlockEntity(pos) instanceof SprinklerBlockEntity sprinkler
					&& sprinkler.canServeGrowth()) {
				return sprinkler;
			}
		}
		return null;
	}

	/**
	 * One growth event on one seedbed: either a new bud on a free face, or one already there getting
	 * bigger. Which of the two is decided by the face the die picked, exactly as vanilla's budding
	 * amethyst does it — that is what makes a bed fill up unevenly and look grown rather than
	 * assembled.
	 *
	 * @return whether anything actually changed
	 */
	private boolean tryGrow(Level level, BlockPos bedPos, RandomSource random) {
		BlockState bed = level.getBlockState(bedPos);
		if (!(bed.getBlock() instanceof CrystalSeedbedBlock)) {
			return false; // mined between scans; the next scan drops it from the list
		}
		Direction face = Direction.values()[random.nextInt(Direction.values().length)];
		BlockPos budPos = bedPos.relative(face);
		BlockState target = level.getBlockState(budPos);

		Block next = nextStage(target.getBlock());
		if (next != null) {
			// Carry FACING and WATERLOGGED across: the bud keeps its orientation, and one growing in a
			// flooded greenhouse must not delete the water it stands in.
			level.setBlock(budPos, next.defaultBlockState()
					.setValue(AmethystClusterBlock.FACING, target.getValue(AmethystClusterBlock.FACING))
					.setValue(AmethystClusterBlock.WATERLOGGED,
							target.getValue(AmethystClusterBlock.WATERLOGGED)),
					Block.UPDATE_ALL);
			playGrowthSound(level, budPos);
			return true;
		}

		// canClusterGrowAtState is vanilla's own answer to "may a bud start here" — air or a water
		// source. Reusing it keeps the greenhouse honest against whatever the game already allows.
		if (BuddingAmethystBlock.canClusterGrowAtState(target) && CrystalSeedbedBlock.canBud(bed)) {
			level.setBlock(budPos, Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
					.setValue(AmethystClusterBlock.FACING, face)
					.setValue(AmethystClusterBlock.WATERLOGGED,
							target.getFluidState().is(FluidTags.WATER)),
					Block.UPDATE_ALL);
			level.setBlock(bedPos, CrystalSeedbedBlock.spendCharge(bed), Block.UPDATE_ALL);
			playGrowthSound(level, budPos);
			return true;
		}
		return false;
	}

	/** The next block in vanilla's bud chain, or {@code null} if this is not a bud that can grow. */
	private static Block nextStage(Block block) {
		if (block == Blocks.SMALL_AMETHYST_BUD) {
			return Blocks.MEDIUM_AMETHYST_BUD;
		}
		if (block == Blocks.MEDIUM_AMETHYST_BUD) {
			return Blocks.LARGE_AMETHYST_BUD;
		}
		if (block == Blocks.LARGE_AMETHYST_BUD) {
			return Blocks.AMETHYST_CLUSTER;
		}
		// A finished cluster is done: it waits to be picked rather than growing further.
		return null;
	}

	/**
	 * A quiet chime on every stage, not just the last one. It is the only cue that a farm nobody is
	 * watching is still working, and it is deliberately soft: a greenhouse of twenty beds must stay
	 * pleasant to stand in.
	 */
	private void playGrowthSound(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.25f, 1.0f);
	}

	// --- persistence ---------------------------------------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("BoxMinX", boxMinX);
		output.putInt("BoxMinY", boxMinY);
		output.putInt("BoxMinZ", boxMinZ);
		output.putInt("BoxMaxX", boxMaxX);
		output.putInt("BoxMaxY", boxMaxY);
		output.putInt("BoxMaxZ", boxMaxZ);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		boxMinX = input.getIntOr("BoxMinX", 0);
		boxMinY = input.getIntOr("BoxMinY", 0);
		boxMinZ = input.getIntOr("BoxMinZ", 0);
		boxMaxX = input.getIntOr("BoxMaxX", Integer.MIN_VALUE);
		boxMaxY = input.getIntOr("BoxMaxY", Integer.MIN_VALUE);
		boxMaxZ = input.getIntOr("BoxMaxZ", Integer.MIN_VALUE);
		// A remembered box IS the record that the paint went on: it is written at the same moment and
		// never survives without it. Without this line the flag came back false after a chunk reload,
		// and a greenhouse taken apart while unloaded — by /fill, a command block, a piston — could
		// never be cleaned up, because clearPaint bows out on it (found by audit).
		sealedPaint = hasRememberedBox();
	}

	// --- report --------------------------------------------------------------------------------

	/**
	 * What the panel says when the player clicks it: either the room's size and what is in it, or
	 * what is wrong and <em>where</em>. Coordinates are absolute, because hunting a one-block hole in
	 * a 14³ shell by eye is frustration rather than gameplay.
	 *
	 * <p><b>Colour carries the verdict, the words carry the detail (MOD-522).</b> Green means the room
	 * is closed and red means it is not, so the answer arrives before the sentence is read; the
	 * numbers and coordinates are lifted out of the running text in white because they are what the
	 * player actually goes looking for. Nothing here is a new string — every colour is applied to an
	 * argument of the same lang keys, so the twenty translations keep working untouched. The
	 * {@code [Ala Industrial]} tag is added by the sender, not here: this component also goes to a
	 * gametest, and a tag belongs to the surface rather than to the report.
	 */
	public Component describeStatus(BlockPos pos) {
		if (status == RoomFill.Status.SEALED) {
			return Component.translatable("message.alaindustrial.crystal_farm.formed",
					value(volume), value(seedbeds.size()),
					Component.translatable(hasWater
							? "message.alaindustrial.crystal_farm.water_yes"
							: "message.alaindustrial.crystal_farm.water_no")
							// Water is the free half of the growth bonus, so its absence is the one thing on a
							// perfectly good greenhouse worth flagging — yellow, the palette's "look here".
							.withStyle(hasWater ? ChatFormatting.AQUA : ChatFormatting.YELLOW))
					.withStyle(ChatFormatting.GREEN);
		}
		if (!knowsFault) {
			// No coordinates rather than wrong ones. A player told to look at a spot with nothing wrong
			// with it searches it anyway, and then distrusts the panel for the rest of the build.
			return Component.translatable("message.alaindustrial.crystal_farm." + statusKey() + ".vague")
					.withStyle(ChatFormatting.RED);
		}
		return Component.translatable("message.alaindustrial.crystal_farm." + statusKey(),
				value(pos.getX() + faultDx), value(pos.getY() + faultDy), value(pos.getZ() + faultDz))
				.withStyle(ChatFormatting.RED);
	}

	/**
	 * A number the player will act on — a size to compare, a coordinate to fly to — in white against
	 * the sentence around it.
	 *
	 * <p>Passing a styled component rather than the bare {@code int} is what keeps the colour on the
	 * number instead of on the whole line: vanilla wraps a plain argument in an unstyled literal that
	 * inherits the parent's colour.
	 */
	private static Component value(int number) {
		return Component.literal(String.valueOf(number)).withStyle(ChatFormatting.WHITE);
	}

	private String statusKey() {
		return switch (status) {
			case SEALED -> "formed";
			case CONTROLLER_NOT_IN_WALL -> "not_in_wall";
			case UNSEALED -> "unsealed";
			case TOO_SMALL -> "too_small";
			case SECOND_CONTROLLER -> "second_controller";
		};
	}
}
