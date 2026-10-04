package dev.alaindustrial.compat;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Whether a stack is fuel and how long it burns in one of the mod's fuel-fired machines (the fuel
 * generator and the iron furnace) — a version facade (MOD-703, ADR-036): every Minecraft line has a twin
 * of this class with the same signatures, and only the bodies differ, so the two machines' sources are
 * the same on every line.
 *
 * <p><b>This twin: Minecraft 26.3.</b> Until 26.3 the answer was a per-level lookup table,
 * {@code FuelValues}, and both machines simply called {@code level.fuelValues().burnDuration(stack)}. That
 * table is gone: fuel is now the {@code minecraft:cooking_fuel} data component on the stack, and the burn
 * time inside it is a {@link ResolvableInt} — which may be a plain number OR a reference into the
 * {@code context_int_provider} registry, as every vanilla fuel's is. Resolving a reference needs a
 * {@link LootContext}, so the question is no longer a one-liner, and building that context in two
 * machines would be the same twelve lines twice. Whether a stack is fuel at all is a property of the
 * stack here, so the {@code level} argument of {@link #isFuel} and {@link #isFuelOrUnknown} is unused.
 *
 * <p>The context is assembled exactly as {@code BaseContainerBlockEntity#getLootContext} assembles it —
 * the same one the vanilla furnace resolves its own fuel through — so a datapack that makes a fuel's burn
 * time depend on the block, the block entity or the position gets the same inputs from our machines as it
 * does from a vanilla one.
 */
public final class FurnaceFuel {

	private FurnaceFuel() {
	}

	/**
	 * Whether the stack is fuel at all, asked by a slot predicate that has a level. 26.3: the stack's
	 * {@code cooking_fuel} component — it needs no context and answers the same on the client, which is
	 * what the vanilla furnace's own {@code canPlaceItem} does.
	 */
	public static boolean isFuel(Level level, ItemStack stack) {
		return stack.has(DataComponents.COOKING_FUEL);
	}

	/**
	 * Whether a fuel slot should take the stack when the machine may have no level yet (client side, before
	 * placement). 26.3: the same component check as {@link #isFuel}, level or not.
	 */
	public static boolean isFuelOrUnknown(@Nullable Level level, ItemStack stack) {
		return stack.has(DataComponents.COOKING_FUEL);
	}

	/**
	 * Burn time in ticks, or {@code 0} for anything that is not fuel. {@code machine} supplies the block,
	 * the block entity and the position the datapack may key on; it must be the {@link Container} the fuel
	 * sits in, which both callers are.
	 */
	public static <T extends BlockEntity & Container> int burnDuration(ServerLevel level, T machine,
			ItemStack stack) {
		return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime,
				lootContext(level, machine), 0);
	}

	private static <T extends BlockEntity & Container> LootContext lootContext(ServerLevel level, T machine) {
		return new LootContext.Builder(
				new LootParams.Builder(level)
						.withParameter(LootContextParams.BLOCK_STATE, machine.getBlockState())
						.withParameter(LootContextParams.BLOCK_ENTITY, machine)
						.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(machine.getBlockPos()))
						.withParameter(LootContextParams.CONTAINER, machine)
						.create(LootContextParamSets.CONTAINER_PROCESS))
				.create(Optional.empty());
	}
}
