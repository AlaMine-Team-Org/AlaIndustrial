package dev.alaindustrial.block;

import dev.alaindustrial.Config;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The in-world block form of oil (MOD-238). A plain {@link LiquidBlock} plus one mechanic of its
 * own: oil is flammable, gated by {@link Config#oilBurns} (default {@code true}).
 *
 * <p>Ignition is deliberately NOT the vanilla flammable-block map (that path is per-loader and
 * covers solid blocks, not liquids). Instead: any neighbour change or placement near an actual
 * FIRE block ({@link BlockTags#FIRE} — fire / soul fire, searched over {@link #IGNITION_OFFSETS})
 * schedules a short block tick; when the tick fires and the igniter is still there, the oil block
 * becomes fire. Lighting a cell also schedules its own oil neighbours, so the burn spreads across the
 * pool as a chain reaction, one {@link #IGNITE_DELAY_TICKS} step per block and at most
 * {@link #MAX_IGNITIONS_PER_TICK} cells per tick. With {@code oilBurns=false} nothing is ever
 * scheduled and oil is inert. The spread also obeys the vanilla anti-griefing game
 * rule {@code fire_spread_radius_around_player} — see {@link #scheduleIgnitionCheck}.
 *
 * <p><b>Lava is deliberately NOT an igniter (MOD-238 audit).</b> Worldgen puts oil lakes in the same
 * Y band as vanilla lava lakes, and {@code ProtoChunk.setBlockState} never calls {@code onPlace}: a
 * deposit generated flush against lava would sit inert until the player's first neighbour update and
 * then burn away in a single chain reaction — the deposit disappears the moment it is found. Real
 * lava does not set fuel alight directly either; it spawns fire in the air around it, and that fire
 * is what ignites. Scoping the trigger to {@link BlockTags#FIRE} keeps the player-visible mechanic
 * ("set it alight → it burns") and makes worldgen contact with lava harmless.
 */
public class OilLiquidBlock extends LiquidBlock {
	/**
	 * Ticks between "saw an igniter" and catching fire — also the per-block spread cadence.
	 *
	 * <p><b>Must stay BELOW the fluid tick delay ({@link dev.alaindustrial.fluid.OilFluid#TICK_DELAY},
	 * see {@code OilFluid#getTickDelay}).</b> Burning
	 * a cell replaces it with fire, and fire is a replaceable block: the neighbouring oil re-floods
	 * that cell on its own next fluid tick. At the original 30 the re-flood always won — the fire was
	 * doused before the next cell's ignition tick fired, so the "chain reaction across the pool" was
	 * only ever observable next to a permanent igniter the oil could not displace. At 10 the ignition
	 * wins the race and the burn genuinely walks the pool, one cell per half second.
	 */
	private static final int IGNITE_DELAY_TICKS = 10;

	/**
	 * How many oil cells may catch fire across the server in a single tick (MOD-250, closing the
	 * open question in MOD-244).
	 *
	 * <p>Once ignition spread through diagonals (see {@link #IGNITION_OFFSETS}) a pool burns as a
	 * genuine front rather than a thin line, and the front of a large lake is hundreds of cells wide.
	 * Each ignition is a {@code setBlockAndUpdate} plus a light update, so an uncapped front is a
	 * visible stutter. Cells over the budget are not skipped — they re-schedule and catch on a later
	 * tick, so the pool still burns out completely, just no faster than this.
	 *
	 * <p>The budget is deliberately global rather than per level: every level advances its game time
	 * on the same server tick, and the thing being protected (the server thread) is shared too.
	 */
	private static final int MAX_IGNITIONS_PER_TICK = 64;

	/** Game time the {@link #ignitionsThisTick} counter belongs to; server thread only. */
	private static long ignitionBudgetTick = Long.MIN_VALUE;

	/** Cells ignited so far during {@link #ignitionBudgetTick}; server thread only. */
	private static int ignitionsThisTick;

	/**
	 * Offsets searched for an igniter: the six faces plus the twelve edge diagonals, but not the eight
	 * corners of the cube.
	 *
	 * <p><b>Faces alone were not enough (MOD-250).</b> With a fire below and to one side of a source —
	 * a diagonal — the source could never see it: only the flowing oil running past the fire burnt, the
	 * source immediately replaced it, and the result was a fire that burnt forever next to oil that
	 * never went away. Crude is meant to be volatile; a flame beside it sets it off even when it does
	 * not touch a face.
	 *
	 * <p>Corners are left out on purpose. They are the only offsets that can reach around a solid block
	 * built as a firebreak, and taking them out keeps a one-block wall an honest wall.
	 */
	private static final Vec3i[] IGNITION_OFFSETS = edgeNeighbourhood();

	private static Vec3i[] edgeNeighbourhood() {
		List<Vec3i> offsets = new ArrayList<>(18);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
					if (distance != 0 && distance != 3) {
						offsets.add(new Vec3i(dx, dy, dz));
					}
				}
			}
		}
		return offsets.toArray(new Vec3i[0]);
	}

	public OilLiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
		super(fluid, properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		scheduleIgnitionCheck(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		scheduleIgnitionCheck(level, pos);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (Config.oilBurns && level.canSpreadFireAround(pos) && hasIgnitionSource(level, pos)) {
			if (claimIgnitionBudget(level)) {
				ignite(level, pos);
			} else {
				// Over budget for this tick: come back rather than drop the ignition, or the far side of
				// a big lake would silently stop burning.
				level.scheduleTick(pos, this, IGNITE_DELAY_TICKS);
			}
			return;
		}
		super.tick(state, level, pos, random);
	}

	/**
	 * Take one slot out of this tick's ignition budget, resetting it when the game time moves on.
	 *
	 * <p>Server thread only — block ticks all run there, and every level's game time advances on the
	 * same server tick, so one counter is enough for the whole server.
	 */
	private static boolean claimIgnitionBudget(ServerLevel level) {
		long now = level.getGameTime();
		if (now != ignitionBudgetTick) {
			ignitionBudgetTick = now;
			ignitionsThisTick = 0;
		}
		if (ignitionsThisTick >= MAX_IGNITIONS_PER_TICK) {
			return false;
		}
		ignitionsThisTick++;
		return true;
	}

	/**
	 * Turn this oil cell into fire right now. Shared by the scheduled-tick path above and the
	 * flint-and-steel interaction below so both burn the pool through exactly the same mechanic: the
	 * block update the replacement sends makes every adjacent oil cell schedule its own ignition
	 * tick, which is the chain reaction.
	 *
	 * <p>Even if fire cannot survive at this cell it is placed anyway: it either burns out on its own
	 * next fire tick or spreads first — both acceptable; the oil block itself is consumed either way.
	 */
	public static void ignite(Level level, BlockPos pos) {
		level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
		scheduleNeighbourIgnition(level, pos);
	}

	/**
	 * Wake every oil cell around a freshly lit one so the burn front matches the detection radius.
	 *
	 * <p>Vanilla block updates only reach the six faces, so a diagonal neighbour would never be asked
	 * to look — {@link #IGNITION_OFFSETS} would widen what a cell sees while nothing ever told it to
	 * look. Scheduling the diagonals explicitly is what makes the front symmetric. Duplicate
	 * {@code scheduleTick}s for the same cell and tick collapse in vanilla's tick list, so the cell
	 * reached from two burning neighbours is not scheduled twice.
	 */
	private static void scheduleNeighbourIgnition(Level level, BlockPos pos) {
		if (!Config.oilBurns || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (Vec3i offset : IGNITION_OFFSETS) {
			cursor.set(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
			BlockState state = level.getBlockState(cursor);
			if (state.getBlock() instanceof OilLiquidBlock oil && serverLevel.canSpreadFireAround(cursor)) {
				level.scheduleTick(cursor.immutable(), oil, IGNITE_DELAY_TICKS);
			}
		}
	}

	/**
	 * Light an oil cell with a flint and steel (MOD-238 audit fix). Called from each loader's early
	 * block-use seam (Fabric {@code UseBlockCallback}, NeoForge {@code PlayerInteractEvent
	 * .RightClickBlock}) — the same neutral-helper pattern as {@code VanillaBucketDeposit}.
	 *
	 * <p><b>Why not {@code useItemOn} on this block.</b> {@link LiquidBlock#getShape} returns
	 * {@code Shapes.empty()}, so the crosshair's OUTLINE raytrace passes straight through oil: an oil
	 * cell is never the block the player interacts with, and its {@code useItemOn} would never run.
	 * The click lands on the solid block behind the oil, and vanilla {@code FlintAndSteelItem#useOn}
	 * then tries to place fire at the offset cell — which is the oil block, so
	 * {@code BaseFireBlock.canBePlacedAt} fails on its {@code !state.isAir()} guard and the
	 * interaction returns FAIL. Intercepting the interaction before vanilla sees it is the only place
	 * that reaches the oil at all.
	 *
	 * @return {@link InteractionResult#SUCCESS} when the click was ours (oil lit), otherwise
	 *         {@link InteractionResult#PASS} so vanilla handles it exactly as before — which for
	 *         {@code oilBurns=false} means the usual FAIL, i.e. oil that cannot be set alight.
	 */
	public static InteractionResult tryLight(Level level, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (player == null || hit == null || !Config.oilBurns) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.FLINT_AND_STEEL)) {
			return InteractionResult.PASS;
		}
		// The oil is the cell the fire WOULD have gone into: one step out of the clicked face.
		BlockPos target = hit.getBlockPos().relative(hit.getDirection());
		if (!(level.getBlockState(target).getBlock() instanceof OilLiquidBlock)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		// playSound(null, ...) so the acting player hears it too: the client branch above returns
		// before it could play the sound locally the way vanilla's both-sides item code does.
		level.playSound(null, target, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F,
				level.getRandom().nextFloat() * 0.4F + 0.8F);
		ignite(level, target);
		level.gameEvent(player, GameEvent.BLOCK_PLACE, target);
		// No-ops in creative (ItemStack#processDurabilityChange bails on hasInfiniteMaterials).
		stack.hurtAndBreak(1, player, hand);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Schedule the ignition check for this cell, unless an admin has turned fire spread off.
	 *
	 * <p>The gate is {@link ServerLevel#canSpreadFireAround} — the same anti-griefing switch vanilla
	 * fire obeys (game rule {@code fire_spread_radius_around_player}: {@code -1} unlimited, otherwise
	 * the radius around a player within which fire may spread; default 128). Without it a server that
	 * had disabled fire spread would still watch an oil lake eat itself, which is precisely the
	 * scenario the rule exists to prevent. Deliberately NOT applied to {@link #tryLight}: a player
	 * striking a flint and steel is a deliberate act, and vanilla lets that place fire regardless of
	 * the rule too — the rule governs SPREAD, and spread is exactly this scheduled path.
	 */
	private void scheduleIgnitionCheck(Level level, BlockPos pos) {
		if (Config.oilBurns && level instanceof ServerLevel serverLevel
				&& serverLevel.canSpreadFireAround(pos) && hasIgnitionSource(level, pos)) {
			level.scheduleTick(pos, this, IGNITE_DELAY_TICKS);
		}
	}

	/** True when any cell of {@link #IGNITION_OFFSETS} holds an actual fire block (fire / soul fire). */
	private static boolean hasIgnitionSource(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (Vec3i offset : IGNITION_OFFSETS) {
			cursor.set(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
			if (level.getBlockState(cursor).is(BlockTags.FIRE)) {
				return true;
			}
		}
		return false;
	}
}
