package dev.alaindustrial.core.ceramic;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

/**
 * Splitting a carbon briquette into ceramic plates (MOD-590) — two ways, and what each asks for.
 *
 * <p><b>There is deliberately no crafting recipe.</b> One briquette in a grid and plates out is exactly
 * what this replaced: it asked the player nothing. Plates now come only out of water.
 *
 * <ul>
 *   <li><b>Press</b> ({@link Config#ceramicPlatesFromPress}) — a water source with a piston aimed into
 *       it, plus a briquette and {@link Config#ceramicPressRedstoneCost} redstone dust floating in that
 *       water. Fires on the piston, automatable with a dropper, an observer and a hopper, and the only
 *       path that pays in full.</li>
 *   <li><b>Hand quench</b> ({@link Config#ceramicPlatesFromWater}) — right-click a water source with the
 *       briquette. No build and no redstone, one plate less.</li>
 * </ul>
 *
 * <p>Water is everywhere, so the hand quench is never out of reach. What the press buys is the fourth
 * plate and the ability to run without a player standing there — and four plates make one block, so the
 * choice is worth a quarter of the coal that went into every block.
 *
 * <p>Pure of any piston/mixin types on purpose, so the same code serves the item's right-click path,
 * the piston hook and the gametests.
 */
public final class QuenchPress {

	/** How far around the quench block a floating briquette still counts. */
	private static final double PICKUP_RADIUS = 0.5;

	private QuenchPress() {
	}

	/** Plates one briquette yields when quenched under a piston. */
	public static int pressYield() {
		return Config.ceramicPlatesFromPress;
	}

	/** Plates one briquette yields when quenched by hand in water. */
	public static int waterYield() {
		return Config.ceramicPlatesFromWater;
	}

	/** Whether this position holds a still water source — the only fluid that quenches. */
	public static boolean isQuenchWater(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource();
	}

	/**
	 * Fires the press: quenches as many briquettes as the floating redstone can pay for.
	 *
	 * <p>Called from the piston hook the moment the piston is told to extend — BEFORE the extension
	 * destroys the water and shoves the item entities aside. Reading the world after the piston has
	 * moved would find neither.
	 *
	 * <p>One shot consumes one briquette and {@link Config#ceramicPressRedstoneCost} redstone dust.
	 * Whatever the redstone cannot pay for stays floating: a player who over-feeds the press loses
	 * nothing, he simply fires it again.
	 *
	 * @return how many briquettes were quenched; zero means the press had nothing it could pay for
	 */
	public static int quench(ServerLevel level, BlockPos pos, int platesPerBriquette) {
		if (!isQuenchWater(level, pos)) {
			return 0;
		}
		AABB box = new AABB(pos).inflate(PICKUP_RADIUS);
		List<ItemEntity> briquettes = floating(level, box, ModContent.CARBON_BRIQUETTE.get());
		List<ItemEntity> redstone = floating(level, box, Items.REDSTONE);
		int cost = Math.max(1, Config.ceramicPressRedstoneCost);
		int shots = Math.min(countItems(briquettes), countItems(redstone) / cost);
		if (shots <= 0) {
			return 0;
		}
		take(briquettes, shots);
		take(redstone, shots * cost);
		dropPlates(level, pos, shots * platesPerBriquette);
		steam(level, pos);
		return shots;
	}

	private static List<ItemEntity> floating(ServerLevel level, AABB box, Item wanted) {
		return level.getEntitiesOfClass(ItemEntity.class, box,
				entity -> entity.isAlive() && entity.getItem().is(wanted));
	}

	private static int countItems(List<ItemEntity> entities) {
		return entities.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
	}

	/** Removes exactly {@code wanted} items across these entities, discarding the emptied ones. */
	private static void take(List<ItemEntity> entities, int wanted) {
		int left = wanted;
		for (ItemEntity entity : entities) {
			if (left <= 0) {
				return;
			}
			ItemStack stack = entity.getItem();
			int taken = Math.min(left, stack.getCount());
			left -= taken;
			stack.shrink(taken);
			if (stack.isEmpty()) {
				entity.discard();
			} else {
				entity.setItem(stack);
			}
		}
	}

	/** Drops {@code total} plates at {@code pos}, split into whole stacks. */
	public static void dropPlates(ServerLevel level, BlockPos pos, int total) {
		int left = total;
		while (left > 0) {
			ItemStack stack = new ItemStack(ModContent.CERAMIC_PLATE.get(),
					Math.min(left, ModContent.CERAMIC_PLATE.get().getDefaultMaxStackSize()));
			left -= stack.getCount();
			Block.popResource(level, pos, stack);
		}
	}

	/** The hiss and the cloud: the only feedback the player gets that the press actually fired. */
	public static void steam(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.6f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE,
				pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.02);
	}
}
