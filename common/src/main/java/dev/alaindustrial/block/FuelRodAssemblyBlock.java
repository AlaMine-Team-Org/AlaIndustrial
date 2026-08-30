package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.core.structure.RoomValidator;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModSounds;
import dev.alaindustrial.sound.MachineHum;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The fuel assembly (MOD-468, stage 2): a transparent rack that stands on the reactor floor and is
 * filled with uranium rods by hand, one at a time.
 *
 * <p><b>Why a rack rather than a slot.</b> A machine you load through a menu is a machine you stop
 * looking at. Here the fuel is part of the room: an empty assembly and a full one are told apart from
 * across the floor, "top up the reactor" is something done in the world, and a half-loaded core is
 * visible at a glance. The blockstate carries {@link #RODS} 0…{@value #MAX_RODS} and the model draws
 * exactly that many rods inside the casing.
 *
 * <p><b>Two-step fuel is deliberate.</b> {@code refined_uranium} — which the mod has produced since
 * MOD-424 and which no recipe has ever consumed — becomes a {@code uranium_fuel_rod}, and only rods go
 * into the rack. That is what turns the long ore → dust → shavings → refined chain into something with
 * an end, instead of a fifth intermediate nobody asked for.
 *
 * <p>Interaction is the obvious one: right-click with a rod to insert, right-click empty-handed to
 * pull the last one back out. No menu at all — a container with four identical items in it would be a
 * GUI for nothing.
 */
public class FuelRodAssemblyBlock extends BaseEntityBlock implements MachineHumProvider {

	public static final MapCodec<FuelRodAssemblyBlock> CODEC = simpleCodec(FuelRodAssemblyBlock::new);

	/** How many rods one assembly holds. Four reads clearly at 16 px and keeps the maths in round numbers. */
	public static final int MAX_RODS = 4;

	/** Rods currently racked. Drives the model, so the fill level is visible without opening anything. */
	public static final IntegerProperty RODS = IntegerProperty.create("rods", 0, MAX_RODS);

	/**
	 * Coolant level, 0…{@value #WATER_LEVELS}, in quarters of the column's tank. Drives its own multipart
	 * model, for the same reason {@link #RODS} does: the state a player has to react to is the state they
	 * should be able to see from across the floor. A column going dry is the reactor's first warning, and
	 * it arrives long before the heat gauge does.
	 *
	 * <p>Quarters rather than millibuckets: the blockstate is replicated to every client in range, and a
	 * per-mB property would resend the whole column table several times a second for a bar four pixels
	 * tall.
	 */
	public static final int WATER_LEVELS = 4;
	public static final IntegerProperty WATER = IntegerProperty.create("water", 0, WATER_LEVELS);

	/**
	 * How many of the racked rods still carry uranium. {@link #RODS} counts everything standing in the
	 * rack, spent casings included, and the model draws the difference in a dead grey.
	 *
	 * <p><b>Both are needed, and the first version had only one.</b> With {@code RODS} counting fuel
	 * alone, a worked-out column reported zero: the casings inside became invisible AND untouchable,
	 * because the interaction code read the blockstate to decide whether there was anything to take
	 * out. A player could neither empty nor refill the column — only break it.
	 */
	public static final IntegerProperty FUELLED = IntegerProperty.create("fuelled", 0, MAX_RODS);

	/**
	 * Whether another assembly stands directly above / below. Stacked racks drop the cap and the base
	 * plate between them and become one continuous column — the same trick a cable or a pipe uses to
	 * stop looking like a row of separate boxes. Cosmetic only: a lone rack is closed on both ends.
	 */
	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty DOWN = BooleanProperty.create("down");

	/**
	 * Whether this column is one of the room's <em>voiced</em> racks — a running reactor's drone plays
	 * from it (MOD-472). Set by the controller, never by the column itself.
	 *
	 * <p><b>Why the column cannot work this out alone.</b> "The reactor is running" is the conjunction of
	 * a sealed shell, a redstone signal, a throttle off zero and fuel in the racks; the column knows only
	 * the last of those, and the first three live on the controller and never reach the client. A rack
	 * humming because it merely holds fuel would go on humming through a scram, which is the opposite of
	 * what the sound is for.
	 *
	 * <p><b>Why a blockstate and not a synced field.</b> The column is a plain {@code BlockEntity} with no
	 * update packet at all, and giving it one would ship its whole four-stack inventory to every client
	 * in range for the sake of one bit. A blockstate is already replicated, already survives a chunk
	 * round-trip, and is the input the hum system is built to read — the same route {@code lit} takes for
	 * every other machine. The controller paints it exactly the way it already paints {@code formed}
	 * across the shell.
	 *
	 * <p><b>Why only some columns carry it.</b> A minimum-size room packed solid holds 27 racks and a
	 * large one holds hundreds; the client has about 25 static sound channels in total, and identical
	 * copies of one sample sum at roughly +6 dB per doubling. The controller therefore voices at most
	 * {@code VOICED_COLUMNS} of them and leaves the rest silent.
	 */
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	/**
	 * A rack, not a full cube: 12×14×12, so the room reads as machinery rather than as a filled box.
	 *
	 * <p>Three shapes, because a stacked rack is drawn taller than a lone one. The connector pieces fill
	 * the gap between racks so the column has no seam, and the outline has to follow: with one fixed
	 * 14-high shape the selection box of a stacked assembly would float two pixels below the geometry
	 * the player can see.
	 */
	/**
	 * Loudness heard through the containment — a quarter of the open-room figure, not silence.
	 *
	 * <p>Audible on purpose: a running reactor should be something the player can hear from outside the
	 * building, or the shell reads as a mute button rather than as shielding. Kept well above the
	 * engine's zero-volume trap (see {@code MachineHumSoundInstance}) so the loop keeps running quietly
	 * instead of failing to start over and over.
	 */
	private static final float MUFFLED_VOLUME = 0.055f;

	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);
	private static final VoxelShape SHAPE_UP = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
	private static final VoxelShape SHAPE_DOWN = Block.box(2.0, -0.0, 2.0, 14.0, 14.0, 14.0);
	private static final VoxelShape SHAPE_BOTH = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

	public FuelRodAssemblyBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(RODS, 0).setValue(FUELLED, 0).setValue(WATER, 0)
				.setValue(UP, false).setValue(DOWN, false).setValue(ACTIVE, false));
	}

	@Override
	protected MapCodec<? extends FuelRodAssemblyBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(RODS, FUELLED, WATER, UP, DOWN, ACTIVE);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, CollisionContext context) {
		boolean up = state.getValue(UP);
		boolean down = state.getValue(DOWN);
		if (up && down) {
			return SHAPE_BOTH;
		}
		return up ? SHAPE_UP : down ? SHAPE_DOWN : SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FuelRodAssemblyBlockEntity(pos, state);
	}

	/**
	 * A ticker on the CLIENT only (MOD-472) — the loop manager needs a per-tick call, and nothing else
	 * here does.
	 *
	 * <p>Deliberately not {@code humMachineTicker}: that one hands back a live ticker on the server too,
	 * and this block entity is built on the promise that it never ticks there
	 * ({@code FuelRodAssemblyBlockEntity}'s class javadoc — the reactor is one machine, so a room with
	 * thirty racks costs one block entity's worth of work, not thirty). Returning {@code null} on the
	 * server keeps that promise exactly while still giving the client its hum.
	 */
	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (!level.isClientSide()) {
			return null;
		}
		MachineHum.ClientHook hook = MachineHum.CLIENT;
		return hook == null ? null : (lvl, pos, st, be) -> hook.tick(lvl, pos, st);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.REACTOR_HUM;
	}

	/**
	 * Quiet per column, because a room speaks with several at once.
	 *
	 * <p>0.22 is the mills' figure — the value the mod already uses for a block players line up in rows —
	 * and it is chosen the same way here. At the controller's cap of voiced columns the copies sum to
	 * roughly 9-10 dB above one of them, which lands a reactor hall a little above a single working
	 * machine: loud enough to be the biggest thing in the base, short of a wall of sound.
	 */
	@Override
	public float humVolume() {
		return 0.22f;
	}

	/**
	 * The column drones only when the controller says the room is running, never merely because it holds
	 * fuel. See {@link #ACTIVE}.
	 */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return state.getValue(ACTIVE);
	}

	/**
	 * Full loudness inside the containment, muffled outside it (MOD-472).
	 *
	 * <p><b>The shell is what muffles, and the check asks exactly that.</b> Minecraft's sound engine has
	 * no occlusion of any kind — every other loop in this mod is heard through a wall as clearly as
	 * across open ground — so a sealed reactor sounding sealed has to be done here. The test is a single
	 * collision trace from the listener to this rack: if it stops on a block that belongs to a formed
	 * reactor shell, the listener is on the far side of the containment.
	 *
	 * <p>Reading the shell specifically, rather than "anything solid", is what makes it behave:
	 * <ul>
	 *   <li>stood inside, a rack behind another rack is still at full volume — a column is not a wall;</li>
	 *   <li>stood outside, the casing muffles it, and so does the reactor glass, which has a collision
	 *       box despite being see-through;</li>
	 *   <li>with the airlock open the doorway itself is empty, so a trace through it reaches the rack and
	 *       the room leaks at full volume — through the opening only, which is the behaviour the shell
	 *       earns by being shut;</li>
	 *   <li>a reactor built with no room around it is never muffled, because there is no shell to hit.</li>
	 * </ul>
	 *
	 * <p>One trace per voiced column per tick, and only while a loop is actually playing. The result is
	 * a step of roughly twelve decibels, so the caller eases between the two rather than switching — a
	 * listener walking past a window would otherwise make the drone stutter tick by tick as the trace
	 * catches the frame and misses it again.
	 */
	@Override
	public float humVolume(Level level, BlockPos pos, BlockState state, Vec3 listener) {
		return shellStandsBetween(level, pos, listener) ? MUFFLED_VOLUME : humVolume();
	}

	/**
	 * Whether a formed shell block interrupts the straight line from {@code listener} to this rack.
	 *
	 * <p>{@code Block.COLLIDER} rather than the visual shape, because that is the difference a player can
	 * see and reason about: reactor glass is see-through but has a collision box, so it holds the sound
	 * in, while an open doorway is empty and lets it out. The swung-open door panel still has a collider
	 * of its own — it is a thin slab turned against the wall — so a trace that clips its edge does count
	 * as shell. That is why the easing above matters at a doorway rather than only at a window.
	 */
	private static boolean shellStandsBetween(Level level, BlockPos pos, Vec3 listener) {
		Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		BlockHitResult hit = level.clip(new ClipContext(listener, target,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		// RoomValidator owns the definition of "a shell block that is currently assembled" — the same
		// one its painter uses. Re-deciding it here would mean a second place to update the day a new
		// shell block is added, with nothing to catch the two drifting apart.
		return RoomValidator.isFormedShell(level.getBlockState(hit.getBlockPos()));
	}

	/** Joins up with whatever is already there the moment the rack is placed. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	/**
	 * Re-joins when a neighbour appears or goes away. {@code updateShape} rather than
	 * {@code neighborChanged}: it is the hook vanilla calls for exactly this — a block adjusting its own
	 * shape to its surroundings — and it runs during world edits and structure placement too.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
			RandomSource random) {
		if (directionToNeighbour == Direction.UP) {
			return state.setValue(UP, neighbourState.is(this));
		}
		if (directionToNeighbour == Direction.DOWN) {
			return state.setValue(DOWN, neighbourState.is(this));
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
				neighbourState, random);
	}

	private BlockState connect(BlockState state, LevelReader level, BlockPos pos) {
		return state
				.setValue(UP, level.getBlockState(pos.above()).is(this))
				.setValue(DOWN, level.getBlockState(pos.below()).is(this));
	}

	/**
	 * Empty hand pulls the top rod out. Loading happens in {@code useItemOn} on the item side, so a
	 * player holding anything else — a pickaxe, a torch — is not silently consumed here.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		// Asks the rack, never the blockstate. Gating on a property that counted only fuel is exactly
		// what made a column full of spent casings impossible to empty.
		if (level.getBlockEntity(pos) instanceof FuelRodAssemblyBlockEntity assembly) {
			ItemStack removed = assembly.removeRod();
			if (!removed.isEmpty()) {
				if (!player.getInventory().add(removed)) {
					player.drop(removed, false);
				}
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7f, 1.2f);
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	/**
	 * Gives everything racked back when the column is destroyed — fuelled rods with their remaining
	 * charge, and spent casings too.
	 *
	 * <p><b>This lived in {@code affectNeighborsAfterRemoval} from the day it was written, and it never
	 * worked.</b> In 26.2 {@code LevelChunk.setBlockState} detaches the block entity BEFORE calling that
	 * hook, so {@code level.getBlockEntity(pos)} inside it came back null and every rod a player had
	 * racked was destroyed silently — by a pickaxe, by a creeper, by a piston, by anything. Nothing
	 * caught it because the loot table returns the rack itself, so a break always looked like it had
	 * worked, and no scenario had ever broken a LOADED rack. MOD-471 needed the uranium to survive into
	 * the crater, wrote the test that breaks one, and the test went red on the first run.
	 *
	 * <p>{@code preRemoveSideEffects} is the hook that runs while the block entity is still attached,
	 * and it fires on every removal path rather than only the player's — which is exactly the property
	 * the original comment wanted. The incubator next door already documents the same ordering; this is
	 * the same fix applied where it should have been in the first place.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	/** A rod in hand goes into the rack; anything else falls through to normal placement. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModContent.URANIUM_FUEL_ROD.get())
				&& !stack.is(ModContent.EMPTY_FUEL_ROD.get())) {
			// TRY_WITH_EMPTY_HAND, not PASS: in 26.2 a PASS here does NOT fall through to
			// useWithoutItem, so pulling a rod out with a pickaxe in hand would silently do nothing.
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof FuelRodAssemblyBlockEntity assembly
				&& assembly.insertRod(stack.copyWithCount(1))) {
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.8f, 1.4f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.CONSUME;
	}
}
