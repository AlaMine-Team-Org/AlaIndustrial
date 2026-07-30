package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import dev.alaindustrial.loot.Trellis;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cotton trellis (MOD-280) — the mod's first crop, and the alternative to farming spiders for the fibre
 * the Fluxweave chain wants (MOD-127): a player who prefers agriculture gets there through a field
 * instead of a mob grinder.
 *
 * <p><b>A perennial on a two-block support.</b> The player places the trellis (two blocks tall, the
 * vanilla tall-plant pattern) on farmland and then right-clicks a seed onto it. What follows is
 * deliberately unlike wheat: the plant goes through one long <em>rooting</em> phase
 * ({@link #AGE_EMPTY}+1 → {@link #AGE_ROOTED}) while it climbs the support, and from then on it stays
 * alive forever, cycling through a much shorter <em>fruiting</em> phase
 * ({@link #AGE_MATURE} → {@link #MAX_AGE}). Picking a ripe plant does not break it: the cotton drops,
 * the age falls back to {@link #AGE_MATURE}, and the bush ripens again. Growing one costs patience
 * once, not every harvest.
 *
 * <p><b>Water gates growth, it never kills.</b> Both phases advance only while the farmland below the
 * lower half still holds moisture. Dry soil <em>pauses</em> the plant — the stage is never lost and the
 * bush never dies — so a player who leaves for a while comes back to a field that simply did not
 * progress. That is a deliberate departure from vanilla, where trampled or unwatered crops punish you.
 *
 * <p><b>No block entity, on purpose.</b> Growth rides the vanilla {@code randomTick} (hence
 * {@code randomTicks()} in this block's manifest entry — without it this class would never tick). A
 * hundred-plant field therefore adds zero ticking objects, which a per-plant block entity could not
 * claim. The cost is that growth is probabilistic: the config knobs are <em>chance divisors</em>, not
 * timers, so a stage takes "roughly this long" rather than exactly.
 *
 * <p>Extending {@link DoublePlantBlock} is what keeps the two halves honest: it owns the pairing rules
 * (placing the upper half, refusing to be placed without headroom, tearing both halves down together,
 * dropping only once when either half is broken) and this class only adds the crop on top. The one
 * thing it cannot do for us is {@link #AGE}, which lives on both halves and is written to both by
 * {@link #setAge} so the model never disagrees between top and bottom.
 */
public class TrellisBlock extends DoublePlantBlock implements BonemealableBlock {

	public static final MapCodec<TrellisBlock> CODEC = simpleCodec(TrellisBlock::new);

	/** Bare trellis: a support with nothing planted on it. A seed moves it to {@code AGE_EMPTY + 1}. */
	public static final int AGE_EMPTY = 0;

	/** Last rooting stage — the plant has finished climbing and is about to become productive. */
	public static final int AGE_ROOTED = 3;

	/**
	 * Grown, but carrying no cotton. This is where a harvest returns to, <b>not</b> {@link #AGE_EMPTY}:
	 * resetting to zero would throw away the rooting the player waited through and turn every pick into
	 * a fresh planting. The rooting phase is a one-time investment by design.
	 */
	public static final int AGE_MATURE = 4;

	/** Ripe: the cotton can be picked. */
	public static final int MAX_AGE = 6;

	public static final IntegerProperty AGE = IntegerProperty.create("age", AGE_EMPTY, MAX_AGE);

	/** Same light floor vanilla crops use — a cellar farm needs lighting, not just water. */
	private static final int MIN_LIGHT = 9;

	/**
	 * A slim column: the support is a post, and the leaves are drawn well inside the block. Kept as one
	 * box (the same simplification the incubator dome makes) so clicks to plant or pick never slip
	 * between the model's stems.
	 */
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

	public TrellisBlock(Properties properties) {
		super(properties);
		// Set both properties explicitly rather than relying on what the superclass registered: HALF must
		// default to LOWER (an item always places the bottom half) and AGE to the bare support.
		registerDefaultState(stateDefinition.any()
				.setValue(HALF, DoubleBlockHalf.LOWER)
				.setValue(AGE, AGE_EMPTY));
	}

	@Override
	public MapCodec<? extends DoublePlantBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(AGE);
	}

	/**
	 * Farmland only (via {@code #minecraft:supports_crops}, so a datapack can widen it), not the
	 * {@code #supports_vegetation} default {@link net.minecraft.world.level.block.VegetationBlock} would
	 * give us: the rooting phase reads the moisture of the block underneath, and only farmland has it.
	 */
	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.SUPPORTS_CROPS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	// --- growth (random tick, lower half only) ---

	/**
	 * Ticks only on the lower half — that half owns the soil check, and letting the upper half tick too
	 * would advance the plant twice as fast for free. A bare support and a ripe bush have nothing to do,
	 * so they drop out of the random-tick work entirely.
	 */
	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		int age = state.getValue(AGE);
		return state.getValue(HALF) == DoubleBlockHalf.LOWER && age > AGE_EMPTY && age < MAX_AGE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!isRandomlyTicking(state)) {
			return;
		}
		if (!canGrowAt(level, pos)) {
			// Dry or dark: pause. The stage is deliberately left untouched — see the class docs.
			return;
		}
		if (random.nextInt(growthChanceDivisor(state.getValue(AGE))) != 0) {
			return;
		}
		grow(level, pos, state);
	}

	/**
	 * Whether the plant may advance a stage here: moist farmland below and enough light. Split out of
	 * {@link #randomTick} so the gametests can assert the "paused on dry soil" rule directly instead of
	 * waiting on a random roll.
	 */
	public static boolean canGrowAt(ServerLevel level, BlockPos lowerPos) {
		return isMoist(level, lowerPos) && level.getRawBrightness(lowerPos, 0) >= MIN_LIGHT;
	}

	/** Moisture of the farmland under the lower half; anything that has no moisture reads as dry. */
	private static boolean isMoist(LevelReader level, BlockPos lowerPos) {
		BlockState soil = level.getBlockState(lowerPos.below());
		return soil.hasProperty(FarmlandBlock.MOISTURE) && soil.getValue(FarmlandBlock.MOISTURE) > 0;
	}

	/**
	 * One stage forward. Rooting is the slow half of the plant's life and fruiting the fast one, so the
	 * two phases read different config knobs.
	 *
	 * <p>The divisor is clamped to at least 1 before it reaches {@code RandomSource.nextInt}: a config
	 * file holding {@code 0} would otherwise crash the chunk that ticks the plant — the same shape of bug
	 * a zero divisor caused in MOD-169.
	 */
	private static int growthChanceDivisor(int age) {
		int configured = age <= AGE_ROOTED
				? Config.cottonRootingChanceDivisor
				: Config.cottonFruitingChanceDivisor;
		return Math.max(1, configured);
	}

	/** Advance the plant one stage, capped at {@link #MAX_AGE}. */
	public static void grow(Level level, BlockPos lowerPos, BlockState lowerState) {
		setAge(level, lowerPos, lowerState, lowerState.getValue(AGE) + 1);
	}

	/**
	 * Write {@code age} onto <b>both</b> halves. The upper half carries the same value purely so its model
	 * matches; only the lower half is ever read for logic. Writing one half and not the other is the one
	 * way this block can visibly desync, so every age change goes through here.
	 */
	private static void setAge(Level level, BlockPos lowerPos, BlockState lowerState, int age) {
		int clamped = Mth.clamp(age, AGE_EMPTY, MAX_AGE);
		if (clamped == lowerState.getValue(AGE)) {
			return;
		}
		level.setBlock(lowerPos, lowerState.setValue(AGE, clamped), Block.UPDATE_CLIENTS);
		BlockPos upperPos = lowerPos.above();
		BlockState upper = level.getBlockState(upperPos);
		if (upper.is(lowerState.getBlock()) && upper.getValue(HALF) == DoubleBlockHalf.UPPER) {
			level.setBlock(upperPos, upper.setValue(AGE, clamped), Block.UPDATE_CLIENTS);
		}
	}

	// --- interaction: plant a seed, pick the cotton ---

	/**
	 * The lower half's position, whichever half was clicked. Both halves accept planting and picking:
	 * having to aim at the bottom block of a two-tall plant is the kind of fiddliness players read as a
	 * bug (the incubator dome forwards clicks to its base for the same reason).
	 */
	private static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	/**
	 * Right-clicking a seed onto a bare trellis plants it. A trellis that already carries a plant bows out
	 * <em>before</em> anything is consumed — misclicking a full field must never quietly eat the player's
	 * seeds.
	 *
	 * <p>Bowing out returns {@link InteractionResult#TRY_WITH_EMPTY_HAND}, not {@code PASS}, and the
	 * difference is load-bearing: {@code ServerPlayerGameMode.useItemOn} only falls through to
	 * {@link #useWithoutItem} when this method returns a {@code TryEmptyHandInteraction}. With {@code PASS}
	 * the harvest was unreachable while the player still had seeds in hand — the exact bug reported after
	 * the first play test.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModContent.COTTON_SEEDS.get())) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		BlockPos base = lowerPos(state, pos);
		BlockState baseState = level.getBlockState(base);
		if (!baseState.is(this) || baseState.getValue(AGE) != AGE_EMPTY) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			setAge(level, base, baseState, AGE_EMPTY + 1);
			level.playSound(null, base, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
			// consume() already respects creative — the stack is left alone for a player with infinite items.
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Picking a ripe plant. The cotton comes out of {@link Trellis#HARVEST_TABLE} — the same
	 * mechanism vanilla uses for berries and glow berries — and the block is left standing, its age wound
	 * back to {@link #AGE_MATURE} so it ripens again. An unripe plant is not touched at all, so spamming
	 * right-click on a field cannot rush it.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		BlockPos base = lowerPos(state, pos);
		BlockState baseState = level.getBlockState(base);
		if (!baseState.is(this) || baseState.getValue(AGE) != MAX_AGE) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			dropFromBlockInteractLootTable(server, Trellis.HARVEST_TABLE, baseState, null,
					player.getMainHandItem(), player, (dropLevel, drop) -> popResource(dropLevel, base, drop));
			server.playSound(null, base, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0f, 0.9f);
			setAge(server, base, baseState, AGE_MATURE);
		}
		return InteractionResult.SUCCESS;
	}

	// --- bone meal ---

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
		BlockState baseState = level.getBlockState(lowerPos(state, pos));
		if (!baseState.is(this)) {
			return false;
		}
		int age = baseState.getValue(AGE);
		return age > AGE_EMPTY && age < MAX_AGE;
	}

	/**
	 * Bone meal obeys the same soil and light gate as ordinary growth. Without this it was a way around
	 * the whole watering mechanic: a player could run a permanent cotton farm on dry, unlit farmland by
	 * spamming meal, while both specs promise that <em>both</em> phases only advance when the ground is
	 * moist and lit. Meal buys speed, not exemption.
	 */
	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
		return level instanceof ServerLevel server && canGrowAt(server, lowerPos(state, pos));
	}

	/**
	 * Bone meal buys exactly one stage — it does not jump the plant to ripe. Rooting can be hurried along
	 * with enough meal, but the fruiting cycle still has to be waited out at least a stage at a time, so
	 * bone meal cannot turn the field into an on-demand cotton dispenser.
	 */
	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
		BlockPos base = lowerPos(state, pos);
		BlockState baseState = level.getBlockState(base);
		if (!canGrowAt(level, base)) {
			return;
		}
		if (baseState.is(this) && baseState.getValue(AGE) > AGE_EMPTY && baseState.getValue(AGE) < MAX_AGE) {
			grow(level, base, baseState);
		}
	}
}
