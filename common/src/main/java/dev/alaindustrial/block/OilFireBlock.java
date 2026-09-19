package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fire a burning oil cell turns into (MOD-638). It looks like vanilla fire and is in
 * {@code #minecraft:fire}, so everything that reacts to fire — the oil chain reaction in
 * {@link OilLiquidBlock}, splash potions, pathfinding, pistons — reacts to it too.
 *
 * <p><b>Why its own block rather than vanilla fire.</b> Soot may only appear where OIL burnt out, and
 * vanilla fire carries no memory of what it burnt; its natural burnout and a player punching it end
 * in the same {@code removeBlock} call. A block of our own has exactly one place where it goes out on
 * its own — {@link #tick} — so "burnt out by itself" is unambiguous, and every other way it can
 * disappear (punched, doused, flooded, overwritten) never reaches that line.
 *
 * <p>Deliberate differences from vanilla fire, each one a decision recorded in MOD-638:
 * <ul>
 *   <li>It survives anywhere, including over liquid. Vanilla fire over an oil cell is removed the
 *       moment it is placed, which stalled the burn of any lake deeper than one block.</li>
 *   <li>Rain, soul soil and infiniburn floors change nothing: the oil is fuel, and it always burns
 *       out — one chance in {@link #BURNOUT_ODDS} per tick.</li>
 *   <li>It does not spread itself. It seeds ordinary vanilla fire next to blocks that
 *       {@code ignitedByLava()} — the rule vanilla lava uses — so the grass and wood round a burning
 *       lake still catch, and that vanilla fire never leaves soot.</li>
 * </ul>
 *
 * <p>One scheduled tick per cell every {@link #MIN_TICK_DELAY}..{@code +}{@link #TICK_DELAY_SPREAD}
 * ticks, like vanilla fire; no block entity, no world scan.
 */
public class OilFireBlock extends BaseFireBlock {
	public static final MapCodec<OilFireBlock> CODEC = simpleCodec(OilFireBlock::new);

	/** Vanilla fire's own damage per tick for ordinary (non-soul) fire. */
	private static final float FIRE_DAMAGE = 1.0F;

	/** Delay to the next tick: {@code MIN_TICK_DELAY + nextInt(TICK_DELAY_SPREAD)} — vanilla fire's cadence. */
	static final int MIN_TICK_DELAY = 30;
	static final int TICK_DELAY_SPREAD = 10;

	/**
	 * One in this many ticks puts the fire out: ≈ 1.5 s at the least, ≈ 7 s on average — enough to
	 * watch a lake burn, short enough not to hold a pool of fire for minutes. The oil next door catches
	 * within {@code OilLiquidBlock.IGNITE_DELAY_TICKS} (10), well before the first tick here (30+).
	 */
	static final int BURNOUT_ODDS = 4;

	public OilFireBlock(BlockBehaviour.Properties properties) {
		super(properties, FIRE_DAMAGE);
	}

	@Override
	protected MapCodec<? extends BaseFireBlock> codec() {
		return CODEC;
	}

	/** Nothing is "burnable" for this fire: it spreads only through the vanilla fire it seeds. */
	@Override
	protected boolean canBurn(BlockState state) {
		return false;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		level.scheduleTick(pos, this, nextDelay(level.getRandom()));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		seedVanillaFire(level, pos, random);
		if (random.nextInt(BURNOUT_ODDS) == 0) {
			burnOut(level, pos, random);
			return;
		}
		level.scheduleTick(pos, this, nextDelay(random));
	}

	/**
	 * The fire went out on its own: roll for soot at this cell. The layer needs a sturdy top face under
	 * it — over air, liquid or anything not full the fire simply disappears.
	 */
	static void burnOut(ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos below = pos.below();
		boolean floor = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
		if (floor && SootDeposit.deposits(Config.oilSootChance, random.nextDouble())) {
			level.setBlockAndUpdate(pos, ModContent.SOOT_LAYER.get().defaultBlockState());
		} else {
			level.removeBlock(pos, false);
		}
	}

	/**
	 * Set the flammable surroundings alight with ordinary fire, the way lava does: an empty face
	 * neighbour that touches a block which {@code ignitedByLava()} gets vanilla fire. Obeys the
	 * anti-griefing game rule, as the oil chain reaction does.
	 */
	private static void seedVanillaFire(ServerLevel level, BlockPos pos, RandomSource random) {
		for (Direction side : Direction.values()) {
			BlockPos target = pos.relative(side);
			if (!level.isEmptyBlock(target) || !level.canSpreadFireAround(target) || random.nextInt(3) != 0) {
				continue;
			}
			if (touchesFlammable(level, target)) {
				level.setBlockAndUpdate(target, BaseFireBlock.getState(level, target));
			}
		}
	}

	// ignitedByLava() is deprecated only in the NeoForge patch (clean in vanilla); its replacement is a
	// NeoForge-only positional API that does not exist in `common` on Fabric.
	@SuppressWarnings("deprecation")
	private static boolean touchesFlammable(Level level, BlockPos pos) {
		for (Direction side : Direction.values()) {
			if (level.getBlockState(pos.relative(side)).ignitedByLava()) {
				return true;
			}
		}
		return false;
	}

	private static int nextDelay(RandomSource random) {
		return MIN_TICK_DELAY + random.nextInt(TICK_DELAY_SPREAD);
	}
}
