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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scythe (MOD-068) — a hand tool for mass-clearing decorative foliage. Right-clicking a block
 * ({@code useOn}) breaks every {@link ModTags.Blocks#SCYTHE_HARVESTABLE} block in a flat
 * {@code width × depth × 3} box in front of the player, dropping vanilla loot exactly as if the
 * player had mined each block by hand. Six material tiers (wood → netherite + tempered iron) scale
 * the area and the per-use block cap; see {@link Profile} and {@code docs/blocks/items/scythe.md}.
 *
 * <p><b>Not a {@code HoeItem}.</b> The item carries the data-driven {@code minecraft:tool} component
 * via {@code Item.Properties.hoe(material, …)} (durability, mining speed, enchantability, attack
 * profile), but it must not till dirt on right-click, so it extends {@link Item} and owns its own
 * {@code useOn}.
 *
 * <p><b>Break path.</b> On the server the AOE runs through
 * {@code ServerPlayer.gameMode.destroyBlock(pos)} — the canonical "player mined a block" path. That
 * gives tool-aware loot (Fortune/Silk Touch apply), correct double-plant handling
 * ({@code playerWillDestroy} removes the other half without a duplicate drop), automatic durability
 * (one point per broken block, via the tool component), creative parity ({@code preventsBlockDrops}
 * → no drop, no durability), adventure/spawn-protection ({@code blockActionRestricted}), and — the
 * reason it is preferred over {@code Level.destroyBlock} — it is where both loaders' block-break
 * events fire, so protection mods can veto each block. See MOD-068 and
 * {@code docs/blocks/items/scythe.md}.
 */
public class ScytheItem extends Item {

	/** Vertical reach of the AOE box: the clicked layer plus one above and one below. */
	private static final int HEIGHT_RADIUS = 1;

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
		// Secondary use (shift) and off-hand are reserved for normal interaction — no AOE.
		if (player == null || context.isSecondaryUseActive() || context.getHand() != InteractionHand.MAIN_HAND) {
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

		List<BlockPos> area = area(clicked, context.getHorizontalDirection());

		// Client side predicts the swing: succeed only if at least one target is present, so a click
		// on bare ground / stone falls through to normal interaction instead of eating the swing.
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			for (BlockPos pos : area) {
				if (isHarvestable(level.getBlockState(pos))) {
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
			// tall plant) may have already cleared this spot, and we must not spend durability on air.
			if (!isHarvestable(level.getBlockState(pos))) {
				continue;
			}
			if (serverPlayer.gameMode.destroyBlock(pos)) {
				broken++;
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

	private static boolean isHarvestable(BlockState state) {
		return !state.isAir() && state.is(ModTags.Blocks.SCYTHE_HARVESTABLE);
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
