package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Kok sagyz (MOD-537) — the rubber dandelion: the land-based alternative to the oil→polymerizer
 * chain, and the mod's first crop that grows <b>down</b> instead of up.
 *
 * <p><b>A perennial column.</b> The player sees a flower, but the plant is three blocks tall: the
 * flower above ground, a root block one deep ({@code kok_sagyz_root[tip=false]}) and a root tip two
 * deep ({@code tip=true}). <b>Only the flower can be killed.</b> Digging either root leaves the
 * plant standing and refills the hole with dirt, and the flower grows the root back — the tip pays
 * the root item, the upper root pays seeds only (owner round 7). Breaking the FLOWER ends the plant
 * (its loot table drops seeds with a chance), and what is already underground <b>stays there</b>
 * (round 5): the root outlives the flower and can still be dug out, it just has nothing left to
 * regrow it. See {@link KokSagyzRootBlock}.
 *
 * <p><b>Any single block of ground is enough.</b> The plant takes root in whatever it was placed
 * on — dirt, grass, sand, farmland or any modded soil that tags itself {@code #supports_crops}
 * (the same tag the garden drone and rich-soil compat ride, MOD-538). The root then adapts to
 * what is underneath: two blocks of soil grow a two-deep column, a single block over stone grows
 * one tip right under the flower. Shallow ground still farms — it just pays less per dig.
 *
 * <p><b>No block entity, on purpose.</b> Like the cotton trellis (MOD-280), growth rides the vanilla
 * {@code randomTick} — a field costs nothing to tick, and the config knobs are chance divisors, not
 * timers. Farmland (or the mod's own root) grows at the base rate; anything wilder applies
 * {@link Config#kokSagyzWildGrowthDivisor} on top, so a tended plantation outpaces a roadside
 * specimen without the wild one being impossible.
 */
public class KokSagyzBlock extends BushBlock {

	// BushBlock declares codec() as MapCodec<BushBlock> (concrete class, no wildcard), so the field
	// carries the supertype; the factory below is still this class.
	public static final MapCodec<BushBlock> CODEC = simpleCodec(KokSagyzBlock::new);

	/** Bare rosette — freshly planted, nothing to dig yet. */
	public static final int AGE_ROSETTE = 0;
	/** A bud has formed: the plant is established but carries no flower yet. */
	public static final int AGE_BUD = 1;
	/** The yellow flower is open — the stage the plant is named for. */
	public static final int AGE_FLOWER = 2;
	/** Seed head: mature. Only now does the root start growing downward. */
	public static final int AGE_MATURE = 3;

	public static final IntegerProperty AGE = IntegerProperty.create("age", AGE_ROSETTE, AGE_MATURE);

	/** Same light floor vanilla crops use — see the cotton trellis. */
	private static final int MIN_LIGHT = 9;

	public KokSagyzBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AGE, AGE_ROSETTE));
	}

	@Override
	public MapCodec<BushBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	/**
	 * Any block the root can grow into or the flower can be planted on: {@code #dirt},
	 * {@code #sand}, {@code #supports_crops} (farmland plus any modded soil that wants to host
	 * crops — the datapack seam every rule here follows), and the four grassy surfaces vanilla
	 * keeps OUT of {@code #dirt}. Kept as one predicate so planting, growth and the root's
	 * collapse all agree on what "ground" means.
	 *
	 * <p><b>Do not shorten this back to {@code #dirt} alone.</b> In 26.2 that tag is exactly
	 * {@code dirt, coarse_dirt, rooted_dirt} (verified against the 26.2 jar) — grass_block,
	 * podzol, mycelium and mud are NOT in it, and grass is the surface every real plain has. The
	 * shortened version type-checks, passes a rig floored with plain dirt, and refuses the entire
	 * overworld: MOD-537 lost two evenings to wild flowers that died the instant worldgen placed
	 * them and seeds that would not plant on the ground under the player's feet.
	 */
	public static boolean isSoil(BlockState state) {
		return state.is(BlockTags.DIRT)
				|| state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
				|| state.is(Blocks.MYCELIUM) || state.is(Blocks.MUD)
				|| state.is(BlockTags.SAND)
				|| state.is(BlockTags.SUPPORTS_CROPS);
	}

	/** Never replace a foreign block entity: its data is not part of a BlockState. */
	public static boolean isRootableSoil(BlockState state) {
		return isSoil(state) && !state.hasBlockEntity();
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return isSoil(state);
	}

	/**
	 * One block of ground: the flower sits on soil, or on its own root (an established plant —
	 * either the upper root of a deep column or the lone tip of a shallow one).
	 */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(ModContent.KOK_SAGYZ_ROOT.get()) || isSoil(below);
	}

	/**
	 * Losing the ground is checked NEXT tick, not on the spot (round 7). Digging a root out from
	 * under the flower goes AIR first and dirt after: {@code ServerPlayerGameMode.destroyBlock}
	 * removes the block — which runs the neighbour updates immediately — and only then calls
	 * {@link KokSagyzRootBlock#playerDestroy}, where the hole is refilled. An instant death here
	 * therefore killed the plant during that gap, every single time, even though the ground was back
	 * a moment later; the flower has to outlive its own harvest, so the verdict is deferred by a
	 * tick and taken in {@link #tick} against whatever is actually there by then.
	 *
	 * <p>The deferral only works if this method does NOT fall through to the superclass:
	 * {@code VegetationBlock.updateShape} (verified against the 26.2 sources) turns a failed
	 * {@code canSurvive} into {@code Blocks.AIR} immediately, for a neighbour change in ANY
	 * direction — so scheduling a tick and then delegating would schedule a verdict on a block that
	 * is already gone.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (!canSurvive(state, level, pos)) {
			// Deliberately NOT delegating: VegetationBlock.updateShape answers a failed canSurvive
			// with Blocks.AIR on the spot, which is the very instant death this defers.
			tickAccess.scheduleTick(pos, this, 1);
			return state;
		}
		return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
	}

	/** The deferred verdict from {@link #updateShape}: still no ground, so the plant dies for real. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!canSurvive(state, level, pos)) {
			// Ordinary destroy path, so the loot table pays its seed chance exactly as it would for
			// a plant broken by hand.
			level.destroyBlock(pos, true);
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(ModContent.KOK_SAGYZ_SEEDS.get());
	}

	// --- trampling (MOD-584) ---

	/**
	 * Landing on the flower knocks it back one growth stage — the plant is trodden down, not
	 * killed. It never drops below the rosette and never touches what is already underground: the
	 * root keeps whatever depth it reached, the flower simply has to mature again before it can
	 * grow deeper.
	 *
	 * <p><b>Walking through is safe; coming down on it is not.</b> The flower has no collision, so
	 * an entity standing "on" it is really standing on the ground below and is inside this block
	 * every tick it stands there — trampling on plain {@code entityInside} would flatten a whole
	 * plantation as the player crossed it, and would fire three or four times in the single jump
	 * that is supposed to cost one stage. The test is therefore that the entity crossed the block's
	 * TOP face downward during this tick, which happens exactly once per descent and not at all
	 * while walking at ground level.
	 *
	 * <p>Sneaking spares the plant, the way sneaking spares a turtle egg — and it is the same key
	 * that opens the root inspection, so leaning in to read a plant can never damage it.
	 */
	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier, boolean isInside) {
		trample(state, level, pos, entity);
	}

	/** Public for the shared Fabric/NeoForge GameTest body; normal play enters via {@link #entityInside}. */
	public static boolean trample(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (!Config.kokSagyzTrampling || !(level instanceof ServerLevel server)
				|| !(entity instanceof LivingEntity) || entity.isSteppingCarefully()) {
			return false;
		}
		int age = state.getValue(AGE);
		double top = pos.getY() + 1.0;
		if (age <= AGE_ROSETTE || entity.yOld < top || entity.getY() >= top) {
			return false;
		}
		server.setBlock(pos, state.setValue(AGE, age - 1), Block.UPDATE_CLIENTS);
		server.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		return true;
	}

	// --- growth (random tick) ---

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getRawBrightness(pos, 0) < MIN_LIGHT) {
			return;
		}
		int age = state.getValue(AGE);
		if (age < AGE_MATURE) {
			if (takesStep(random, growthDivisor(age), level, pos)) {
				level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
			}
			return;
		}
		// AGE_MATURE: the root grows one block down, if it still has somewhere to reach.
		if (takesStep(random, rootDivisor(level, pos), level, pos)) {
			growRoot(level, pos);
		}
	}

	/**
	 * The base growth rate on tended ground (farmland below, or the plant's own root — the tip
	 * grows through the root block it is part of). Anything else carries the wild multiplier, so a
	 * self-seeded roadside plant is slower than the plantation it escaped from.
	 */
	public static int growthDivisor(int age) {
		return Math.max(1, switch (age) {
			case AGE_ROSETTE -> Config.kokSagyzStage1Divisor;
			case AGE_BUD -> Config.kokSagyzStage2Divisor;
			default -> Config.kokSagyzStage3Divisor;
		});
	}

	/**
	 * One growth roll, ground included. Written as "1 in {@code divisor × percent}, needing under
	 * 100" rather than as a multiplied divisor so the ground factor stays exact: rounding a divisor
	 * of 1 by a factor of 0.75 would land back on 1 and quietly drop the bonus for the first stage.
	 */
	private static boolean takesStep(RandomSource random, int divisor, LevelReader level, BlockPos pos) {
		return random.nextInt(divisor * groundPercent(level, pos)) < 100;
	}

	/**
	 * What the ground under the flower does to the rate, in percent of the ordinary time. Sand is
	 * the plant's element and the only ground that differs; tilling is deliberately worth nothing.
	 *
	 * <p>The medium is read through the plant's own root: once a segment stands under the flower it
	 * reports the ground it replaced, so a column sunk in sand keeps the sand rate all the way down
	 * instead of reverting to the default the moment it takes root.
	 */
	public static int groundPercent(LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		BlockState medium = below.is(ModContent.KOK_SAGYZ_ROOT.get())
				? KokSagyzRootBlockEntity.soilAt(level, pos.below()) : below;
		return medium.is(BlockTags.SAND) ? Math.clamp(Config.kokSagyzSandGrowthPercent, 1, 100) : 100;
	}

	/**
	 * The price of the next underground step, chosen by WHAT it will create rather than by how deep
	 * it lands. Shallow ground mints its harvestable tip on the very first step, so pricing by step
	 * order would let a one-block plot out-yield a full column; this way the tip always costs the
	 * tip and depth only decides how soon the first one arrives.
	 */
	public static int rootDivisor(LevelReader level, BlockPos pos) {
		return Math.max(1, nextRootIsTip(level, pos)
				? Config.kokSagyzRootTipDivisor : Config.kokSagyzRootUpperDivisor);
	}

	/**
	 * Whether the next root {@link #growRoot} would place is the harvestable tip. Mirrors that
	 * method's branch: soil under the flower grows the tip straight away unless there is somewhere
	 * deeper to go, and the only other growable case is the tip landing two blocks down.
	 */
	public static boolean nextRootIsTip(LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		if (isRootableSoil(below)) {
			BlockState twoDown = level.getBlockState(pos.below(2));
			return !(isRootableSoil(twoDown) || twoDown.is(ModContent.KOK_SAGYZ_ROOT.get()));
		}
		return true;
	}


	/**
	 * Whether the column can still go one block deeper: either the flower sits on soil (no root
	 * yet) or on the upper root over soil (no tip yet). A full column — root over tip, or a root
	 * that has hit stone — is done growing.
	 */
	public static boolean canGrowRoot(LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		if (isRootableSoil(below)) {
			return true;
		}
		return below.is(ModContent.KOK_SAGYZ_ROOT.get())
				&& !below.getValue(KokSagyzRootBlock.TIP)
				&& isRootableSoil(level.getBlockState(pos.below(2)));
	}

	/**
	 * One block of root downward: soil below the flower becomes the upper root, or soil under an
	 * existing upper root becomes the tip. Returns whether anything grew — bone meal reads it to
	 * know whether it had an effect.
	 */
	public static boolean growRoot(ServerLevel level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		if (isRootableSoil(below)) {
			BlockState root = ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState();
			// Adaptive depth: soil under the soil grows a two-deep column; a lone block of ground
			// over stone grows the tip right under the flower — one dig instead of two. An existing
			// root two down counts as depth as well, or regrowing a dug-out upper root would stack a
			// second TIP on top of the one still in the ground (round 7: digging the upper root no
			// longer kills the plant, so that regrowth is now an everyday event, not an edge case).
			BlockState twoDown = level.getBlockState(pos.below(2));
			boolean deep = isRootableSoil(twoDown) || twoDown.is(ModContent.KOK_SAGYZ_ROOT.get());
			level.setBlockAndUpdate(pos.below(), root.setValue(KokSagyzRootBlock.TIP, !deep));
			if (level.getBlockEntity(pos.below()) instanceof KokSagyzRootBlockEntity entity) {
				entity.setSoil(below);
			}
			return true;
		}
		if (below.is(ModContent.KOK_SAGYZ_ROOT.get()) && !below.getValue(KokSagyzRootBlock.TIP)
				&& isRootableSoil(level.getBlockState(pos.below(2)))) {
			BlockState original = level.getBlockState(pos.below(2));
			BlockState tip = ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState();
			level.setBlockAndUpdate(pos.below(2), tip.setValue(KokSagyzRootBlock.TIP, true));
			if (level.getBlockEntity(pos.below(2)) instanceof KokSagyzRootBlockEntity entity) {
				entity.setSoil(original);
			}
			return true;
		}
		return false;
	}

	// --- bone meal (also the sprinkler's hook) ---

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
		return state.getValue(AGE) < AGE_MATURE || canGrowRoot(level, pos);
	}

	/**
	 * One step of growth, no chance roll: a stage of age, or one block of root. Meal (and the
	 * sprinkler) buys speed, not exemption — the light floor and the "somewhere to grow" checks
	 * still apply.
	 */
	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
		int age = state.getValue(AGE);
		if (age < AGE_MATURE) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
			return;
		}
		growRoot(level, pos);
	}
}
