package dev.alaindustrial.block;

import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.item.fluid.FluidTankBlockItem;
import dev.alaindustrial.item.fluid.FluidTankContents;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * The water a cable wash takes from the player's hand (MOD-898, MOD-912): what counts as water, how much a
 * container can give, and what it turns into once it gave.
 *
 * <p>A plain click costs {@link #SEGMENT_MB} for the clicked segment, a Shift wash of the whole run
 * {@link #RUN_MB}. A container gives exactly the amount asked or nothing, so the two prices also sort the
 * containers: a bucket or a capsule can give only its whole bucket and pays it even for one segment, a
 * water bottle holds one segment's worth and never washes a run, and a tank or a foreign canister pays by
 * the amount.
 *
 * <p>The sources, in the order they are asked:
 * <ol>
 * <li>a vanilla water bottle — water as potion contents, not a fluid container, so it is read here on
 *     both loaders alike; it becomes an empty glass bottle;</li>
 * <li>the mod's portable tank — read through its own {@code fluid_tank_contents} component: the tank item
 *     exposes no item fluid capability, and adding one would change how machine slots and other mods'
 *     pipes treat it;</li>
 * <li>anything the loader's item fluid API can take water out of ({@link ItemFluidBridge}) — the vanilla
 *     bucket, the mod's capsule and other mods' containers, with no list of items;</li>
 * <li>the vanilla water bucket by hand, for when no bridge is installed.</li>
 * </ol>
 */
final class CableWashWater {

	/** Water a plain click spends on the one segment it washes: a bottle's worth. */
	static final long SEGMENT_MB = 250L;

	/** Water a Shift wash of the whole run spends: one bucket. */
	static final long RUN_MB = FluidAmounts.BUCKET;

	private enum Kind {
		BOTTLE, TANK, FLUID_API, BUCKET
	}

	private final Kind kind;
	private final Player player;
	private final InteractionHand hand;

	private CableWashWater(Kind kind, Player player, InteractionHand hand) {
		this.kind = kind;
		this.player = player;
		this.hand = hand;
	}

	/** The water source in {@code hand}, or {@code null} when the hand holds none. */
	static @Nullable CableWashWater inHand(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.isEmpty()) {
			return null;
		}
		if (isWaterBottle(stack)) {
			return new CableWashWater(Kind.BOTTLE, player, hand);
		}
		if (stack.getItem() instanceof FluidTankBlockItem) {
			return tankWater(stack) > 0 ? new CableWashWater(Kind.TANK, player, hand) : null;
		}
		ItemFluidBridge bridge = ItemFluidBridge.get();
		if (bridge.takeFromHand(player, hand, Fluids.WATER, SEGMENT_MB, true)
				|| bridge.takeFromHand(player, hand, Fluids.WATER, RUN_MB, true)) {
			return new CableWashWater(Kind.FLUID_API, player, hand);
		}
		return stack.is(Items.WATER_BUCKET) ? new CableWashWater(Kind.BUCKET, player, hand) : null;
	}

	/**
	 * Whether a click with this water on a cable of the mod is taken even when it washes nothing. A bucket
	 * would pour beside the line and a bottle would be drunk or turn the ground to mud, so they always take
	 * it; the tank is a block, and a click that washes nothing must still be able to place it.
	 */
	boolean takesIdleClick() {
		return kind != Kind.TANK;
	}

	/** Whether the container in hand can give exactly {@code mb} of water now; changes nothing. */
	boolean canGive(long mb) {
		ItemStack stack = player.getItemInHand(hand);
		return switch (kind) {
			case BOTTLE -> mb == SEGMENT_MB && isWaterBottle(stack);
			case TANK -> tankWater(stack) >= mb;
			case FLUID_API -> ItemFluidBridge.get().takeFromHand(player, hand, Fluids.WATER, mb, true);
			case BUCKET -> mb == RUN_MB && stack.is(Items.WATER_BUCKET);
		};
	}

	/** Take {@code mb} of water out of the hand, swapping the container for what it becomes; server side. */
	boolean take(long mb) {
		if (!canGive(mb)) {
			return false;
		}
		ItemStack stack = player.getItemInHand(hand);
		switch (kind) {
			case BOTTLE -> player.setItemInHand(hand,
					ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
			case TANK -> player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, drained(stack, mb)));
			case FLUID_API -> {
				return ItemFluidBridge.get().takeFromHand(player, hand, Fluids.WATER, mb, false);
			}
			case BUCKET -> player.setItemInHand(hand,
					ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
		}
		return true;
	}

	/** The sound the container makes as it gives its water. */
	SoundEvent sound() {
		return kind == Kind.BOTTLE ? SoundEvents.BOTTLE_EMPTY : SoundEvents.BUCKET_EMPTY;
	}

	private static boolean isWaterBottle(ItemStack stack) {
		return stack.is(Items.POTION)
				&& stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
	}

	/** Millibuckets of water in a portable tank stack; 0 for an empty tank or one holding another fluid. */
	private static long tankWater(ItemStack stack) {
		FluidTankContents contents = stack.get(ModDataComponents.FLUID_TANK_CONTENTS.get());
		return contents != null && contents.fluid().value() == Fluids.WATER ? contents.amount() : 0L;
	}

	/**
	 * One tank of {@code stack} with {@code mb} of its water gone. An emptied tank loses the contents and the
	 * stack size of one a filled tank carries, so it stacks with freshly crafted ones, as an emptied tank
	 * block drops (FluidTankBlockEntity#collectImplicitComponents).
	 */
	private static ItemStack drained(ItemStack stack, long mb) {
		ItemStack one = stack.copyWithCount(1);
		FluidTankContents contents = one.get(ModDataComponents.FLUID_TANK_CONTENTS.get());
		long left = contents == null ? 0L : contents.amount() - mb;
		if (left > 0) {
			one.set(ModDataComponents.FLUID_TANK_CONTENTS.get(), new FluidTankContents(contents.fluid(), left));
		} else {
			one.remove(ModDataComponents.FLUID_TANK_CONTENTS.get());
			one.remove(DataComponents.MAX_STACK_SIZE);
		}
		return one;
	}
}
