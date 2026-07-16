package dev.alaindustrial.item;

import dev.alaindustrial.registry.ModSounds;
import dev.alaindustrial.registry.ModTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Scythe (MOD-068) — a hand tool for mass-clearing decorative foliage and harvesting mature crops.
 * Right-clicking a block ({@code useOn}) breaks every target block in a flat {@code width × depth × 3}
 * box in front of the player, dropping vanilla loot exactly as if the player had mined each block by
 * hand. Six material tiers (wood → netherite + tempered iron) scale the area and the per-use block cap;
 * see {@link Profile} and {@code docs/blocks/items/scythe.md}.
 *
 * <h2>Two modes (MOD-098)</h2>
 * <ul>
 * <li><b>Decor mode</b> — plain right-click (no shift): clears decorative foliage
 * ({@link ModTags.Blocks#SCYTHE_HARVESTABLE} — leaves, grass, flowers, vines, …). Crops are
 * <b>not</b> in that tag, so they survive a foliage sweep — this is the crop-protection promise.</li>
 * <li><b>Crop mode</b> — shift + right-click: harvests <b>only mature</b> crops
 * ({@link ModTags.Blocks#SCYTHE_CROPS} — {@code #minecraft:crops} plus sweet berry bush, cactus and
 * sugar cane). Immature crops ({@code age < max}) are skipped so young plants keep growing; the scythe
 * acts as an AOE sickle. Melon/pumpkin stems are never harvested (they are not a pickable crop — see
 * {@link #isCropTarget}); cactus/sugar cane are only taken above their base block so the crop regrows.
 * Decorative foliage is untouched in this mode.</li>
 * </ul>
 *
 * <h2>Durability (MOD-098)</h2>
 * Every <b>actually-broken</b> block costs exactly 1 durability in either mode, regardless of the
 * block's hardness — grass and flowers wear the tool the same as leaves. That is a deliberate change
 * from the vanilla tool rule (which charged 0 on hardness-0 blocks). It is implemented by suppressing
 * the vanilla {@code Item.mineBlock} durability ({@link #mineBlock} is a no-op) and charging the damage
 * explicitly in {@link #useOn}, once per block that {@code destroyBlock} actually removed. Creative
 * ({@code instabuild}) spends nothing, same as today.
 *
 * <p><b>Not a {@code HoeItem}.</b> The item carries the data-driven {@code minecraft:tool} component
 * via {@code Item.Properties.hoe(material, …)} (durability, mining speed, enchantability, attack
 * profile), but it must not till dirt on right-click, so it extends {@link Item} and owns its own
 * {@code useOn}.
 *
 * <p><b>Break path.</b> On the server the AOE runs through
 * {@code ServerPlayer.gameMode.destroyBlock(pos)} — the canonical "player mined a block" path. That
 * gives tool-aware loot (Fortune/Silk Touch apply), correct double-plant handling
 * ({@code playerWillDestroy} removes the other half without a duplicate drop), creative parity
 * ({@code preventsBlockDrops} → no drop, and {@code mineBlock} no longer wears the tool), adventure/
 * spawn-protection ({@code blockActionRestricted}), and — the reason it is preferred over
 * {@code Level.destroyBlock} — it is where both loaders' block-break events fire, so protection mods
 * can veto each block. See MOD-068 and {@code docs/blocks/items/scythe.md}.
 */
public class ScytheItem extends Item {

	/** Vertical reach of the AOE box: the clicked layer plus one above and one below. */
	private static final int HEIGHT_RADIUS = 1;

	/**
	 * Mature age for a sweet berry bush: at {@code age >= 2} the bush is ripe (vanilla drops berries on
	 * interaction/growth at age 2 and 3, never at 0/1), so 2+ counts as "ready to harvest".
	 */
	private static final int SWEET_BERRY_RIPE_AGE = 2;

	private final Profile profile;

	public ScytheItem(Profile profile, Properties properties) {
		super(properties);
		this.profile = profile;
	}

	/**
	 * Per-tier AOE parameters.
	 *
	 * @param width     the perpendicular span (must be odd so the box has a centre line)
	 * @param depth     how far the box extends forward along the player's facing
	 * @param maxBlocks the cap on how many blocks a single use may break
	 */
	public record Profile(int width, int depth, int maxBlocks) {
		public Profile {
			if (width < 1 || width % 2 == 0) {
				throw new IllegalArgumentException("scythe width must be a positive odd number, got " + width);
			}
			if (depth < 1) {
				throw new IllegalArgumentException("scythe depth must be >= 1, got " + depth);
			}
			if (maxBlocks < 1) {
				throw new IllegalArgumentException("scythe maxBlocks must be >= 1, got " + maxBlocks);
			}
		}
	}

	public Profile profile() {
		return this.profile;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		// Off-hand is reserved for normal interaction; the AOE only ever fires from the main hand.
		// Shift is NOT a guard here — it switches to crop-harvest mode (MOD-098).
		if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		ItemStack stack = context.getItemInHand();
		// Respect build/use restrictions at the interaction target; the per-position break path
		// enforces protection for every other block in the area.
		if (!player.mayBuild() || !player.mayUseItemAt(clicked, context.getClickedFace(), stack)) {
			return InteractionResult.PASS;
		}

		boolean cropMode = context.isSecondaryUseActive(); // shift + right-click → harvest mature crops
		List<BlockPos> area = area(clicked, context.getHorizontalDirection());

		// Client side predicts the swing: succeed only if at least one target is present, so a click
		// on bare ground / stone falls through to normal interaction instead of eating the swing.
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			for (BlockPos pos : area) {
				BlockState state = level.getBlockState(pos);
				if (cropMode ? isCropTarget(level, pos, state) : isDecorTarget(state)) {
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		}

		int broken = 0;
		for (BlockPos pos : area) {
			if (broken >= profile.maxBlocks() || stack.isEmpty()) {
				break; // hit the per-use cap, or the tool broke mid-swing.
			}
			// Re-check per position: a neighbour update from a previous break (e.g. the upper half of a
			// tall plant, or a sugar-cane/cactus column collapsing) may have already cleared this spot,
			// and we must not spend durability on air.
			BlockState state = level.getBlockState(pos);
			if (!(cropMode ? isCropTarget(level, pos, state) : isDecorTarget(state))) {
				continue;
			}
			if (serverPlayer.gameMode.destroyBlock(pos)) {
				broken++;
				// Explicit durability: 1 per actually-broken block in either mode, independent of hardness
				// (MOD-098). Creative (instabuild) spends nothing, mirroring vanilla's creative parity.
				// hurtAndBreak(hand) is the canonical hand-tool path: it converts to the ServerLevel/
				// ServerPlayer overload, applies Unbreaking, and on the final point plays the break
				// sound + advancement via the hand's equipment slot.
				if (!serverPlayer.getAbilities().instabuild) {
					stack.hurtAndBreak(1, serverPlayer, context.getHand());
				}
			}
		}
		if (broken > 0) {
			// One swing sound per successful use (not per block), broadcast to nearby clients.
			level.playSound(null, clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5,
					ModSounds.SCYTHE_SWING.get(), SoundSource.PLAYERS, 0.8f,
					0.9f + level.getRandom().nextFloat() * 0.2f);
			// A small sweep flourish over the cleared area — a scattering of the vanilla sweep-attack
			// swipe, spread across the box so it reads as a wide horizontal cut.
			int puffs = Math.min(3 + broken / 2, 12);
			((ServerLevel) level).sendParticles(ParticleTypes.SWEEP_ATTACK,
					clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5,
					puffs, profile.width() / 2.0, 0.3, profile.depth() / 2.0, 0.0);
		}
		return broken > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/**
	 * Suppresses the vanilla durability path. {@code Item.mineBlock} (called by
	 * {@code ItemStack.mineBlock}, in turn called by {@code ServerPlayerGameMode.destroyBlock}) charges
	 * {@code tool.damagePerBlock} on blocks with non-zero hardness — so vanilla rules would make grass
	 * free and leaves cost 1. MOD-098 wants a flat 1-per-actually-broken-block in both modes, charged
	 * explicitly in {@link #useOn}. Returning {@code true} here without delegating keeps the
	 * {@code Stats.ITEM_USED} award (handled by {@code ItemStack.mineBlock}) and only drops the damage.
	 */
	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		return true;
	}

	/**
	 * Decor-mode target: any non-air block in {@link ModTags.Blocks#SCYTHE_HARVESTABLE}. Crops are not
	 * in that tag, so they are protected from a plain right-click (the crop-protection promise).
	 */
	private static boolean isDecorTarget(BlockState state) {
		return !state.isAir() && state.is(ModTags.Blocks.SCYTHE_HARVESTABLE);
	}

	/**
	 * Crop-mode target: a <b>mature</b> crop in {@link ModTags.Blocks#SCYTHE_CROPS}. Immature crops are
	 * left to keep growing, and decorative foliage is untouched (it is not in the crops tag).
	 *
	 * <p>The tag pulls {@code #minecraft:crops}, whose 26.2 membership is broader than {@link CropBlock}:
	 * wheat/carrot/potato/beetroot/torchflower_crop are {@code CropBlock}, but {@code melon_stem}/
	 * {@code pumpkin_stem} are {@code StemBlock} and {@code pitcher_crop} is a {@code DoublePlantBlock}.
	 * Each type needs its own maturity rule, and stems are deliberately never harvested (they are not a
	 * crop you pick — they spawn a fruit on a neighbour and keep growing), matching MOD-098 decision 2.
	 *
	 * <p>Cactus / sugar cane grow as a vertical column from a single base block on the ground. Only the
	 * blocks <b>above the base</b> are harvested — i.e. this block is a target only when the block below
	 * it is the same type (it is part of the stalk, not the root). That leaves the base alive to regrow,
	 * exactly like hand-picking the top of a cane/cactus (MOD-098 decision 1).
	 *
	 * @param level the level, to read the block below for the cactus/cane base check
	 * @param pos   the candidate position
	 * @param state the candidate state
	 */
	private static boolean isCropTarget(Level level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.is(ModTags.Blocks.SCYTHE_CROPS)) {
			return false;
		}
		Block block = state.getBlock();
		// CropBlock covers wheat, carrots, potatoes, beetroots, torchflower_crop (all its subclasses).
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		// Sweet berry bush: ripe from age 2 (drops berries); age 0/1 left to grow.
		if (block instanceof SweetBerryBushBlock) {
			return state.getValue(SweetBerryBushBlock.AGE) >= SWEET_BERRY_RIPE_AGE;
		}
		// Cactus / sugar cane: harvest only above the base. The base block sits on ground with nothing
		// of the same type beneath it; any same-typed block above the base is the pickable stalk.
		if (isStalkBlock(block)) {
			return level.getBlockState(pos.below()).is(block);
		}
		// Melon/pumpkin stems (StemBlock) are never harvested: they are not a pickable crop — they grow
		// a fruit on a neighbour and keep growing. Explicit guard before the AGE fallback, because a
		// ripe stem DOES carry AGE at max and would otherwise be caught there (MOD-098 decision 2).
		if (block instanceof StemBlock) {
			return false;
		}
		// Pitcher crop (DoublePlantBlock, not CropBlock): mature at its max AGE. Same for any other
		// future crop that carries a vanilla AGE property — harvest at the property's top value, so new
		// crops added to the tag by Mojang or mods work without a code change.
		IntegerProperty age = findAgeProperty(state);
		if (age != null) {
			int max = age.getPossibleValues().stream().max(Integer::compare).orElse(0);
			return state.getValue(age) >= max;
		}
		// Unknown tagged block with no AGE rule and no specific handler: never auto-harvest. A block we
		// cannot prove ripe is safer left untouched than broken blind.
		return false;
	}

	/** {@code true} for the cactus / sugar cane columns that regrow from a base (the stalk crops). */
	private static boolean isStalkBlock(Block block) {
		return block == net.minecraft.world.level.block.Blocks.CACTUS
				|| block == net.minecraft.world.level.block.Blocks.SUGAR_CANE;
	}

	/**
	 * The block's {@code AGE} {@link IntegerProperty} if it has one (most growing blocks do), else
	 * {@code null}. Used as the maturity fallback for tagged crops that are not {@link CropBlock} and
	 * not handled by a specific branch (e.g. {@code pitcher_crop}).
	 */
	private static IntegerProperty findAgeProperty(BlockState state) {
		for (var property : state.getProperties()) {
			if (property instanceof IntegerProperty ip && "age".equals(property.getName())) {
				return ip;
			}
		}
		return null;
	}

	/**
	 * The candidate positions for one use: a flat {@code width × depth × 3} box whose near-bottom
	 * corner is anchored at the clicked block. Depth runs forward along {@code facing}, width along the
	 * perpendicular horizontal axis (centred), height spans one block above and below the clicked
	 * layer. Positions are sorted by distance from the clicked block so durability spend and the
	 * per-use cap are deterministic (the clicked block always breaks first).
	 */
	private List<BlockPos> area(BlockPos clicked, Direction facing) {
		Direction side = facing.getClockWise();
		int halfWidth = (profile.width() - 1) / 2;
		List<BlockPos> positions = new ArrayList<>(profile.width() * profile.depth() * (HEIGHT_RADIUS * 2 + 1));
		for (int d = 0; d < profile.depth(); d++) {
			for (int w = -halfWidth; w <= halfWidth; w++) {
				for (int y = -HEIGHT_RADIUS; y <= HEIGHT_RADIUS; y++) {
					positions.add(clicked.relative(facing, d).relative(side, w).above(y));
				}
			}
		}
		positions.sort(Comparator.comparingInt(pos -> pos.distManhattan(clicked)));
		return positions;
	}
}
