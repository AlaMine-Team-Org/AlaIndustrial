package dev.alaindustrial.item.tool.neoforge;

import dev.alaindustrial.item.tool.ElectricShovelItem;
import net.minecraft.world.item.ItemInstance;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * NeoForge {@link ElectricShovelItem}: declares the {@code SHOVEL_FLATTEN} and {@code SHOVEL_DOUSE} item
 * abilities so the shovel can actually make dirt paths and put out campfires on this loader.
 *
 * <h2>Why this class has to exist (MOD-379)</h2>
 * Identical mechanism to the sibling {@link ElectricHoeItemNeoForge}, one item later. NeoForge does not
 * wrap the vanilla shovel interactions — it <b>replaces</b> them. Patched {@code ShovelItem.useOn} never
 * reads the static {@code FLATTENABLES} map itself; it asks the block:
 *
 * <pre>{@code
 * BlockState newState = blockState.getToolModifiedState(context, ItemAbilities.SHOVEL_FLATTEN, false);
 * ...
 * } else if ((updatedState = blockState.getToolModifiedState(context, ItemAbilities.SHOVEL_DOUSE, false)) != null) {
 * }</pre>
 *
 * and {@code IBlockExtension.getToolModifiedState} opens with a hard gate on the <i>held item</i>:
 *
 * <pre>{@code
 * ItemStack itemStack = context.getItemInHand();
 * if (!itemStack.canPerformAction(itemAbility)) return null;
 * }</pre>
 *
 * The default {@code IItemExtension.canPerformAction} answers {@code false} for everything except
 * {@code SWORD_SWEEP}, and vanilla's own {@code ShovelItem} only clears the gate because NeoForge patches
 * it to return {@code ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability)}. Our shovel deliberately
 * does <b>not</b> extend {@code ShovelItem} (that constructor applies {@code Properties.shovel(...)},
 * which sets {@code MAX_DAMAGE} and would give an EU tool a durability bar — see
 * {@link ElectricShovelItem}), so it inherited the {@code false} default.
 *
 * <p><b>Delegation cannot rescue it.</b> {@code ElectricShovelItem.useOn} forwards to
 * {@code Items.DIAMOND_SHOVEL.useOn(context)}, which is correct and sufficient on Fabric — but on
 * NeoForge the gate reads {@code context.getItemInHand()}, and that is still the <i>electric</i> shovel's
 * stack, not the diamond one. Both {@code getToolModifiedState} calls therefore returned {@code null} and
 * {@code useOn} returned {@code PASS}.
 *
 * <p><b>This was a shipped defect, not a new-feature gap.</b> The Electric Shovel (MOD-338) has been
 * unable to path or douse on NeoForge since it was released, and it shipped that way because the item had
 * no gametest on <i>either</i> loader. Proven in the NeoForge gametest server before the fix: with the
 * identical rig, click and face, a vanilla {@code minecraft:diamond_shovel} turned the grass block into a
 * dirt path ({@code shovel_vanilla_paths_same_fixture} green) while
 * {@code alaindustrial:electric_shovel} left {@code minecraft:grass_block} untouched and the lit campfire
 * burning. Suite TC-SHOVEL-001 now runs on both loaders and keeps it that way.
 *
 * <p>Declaring both abilities in one set is not a convenience: {@code DEFAULT_SHOVEL_ACTIONS} is exactly
 * <code>{SHOVEL_FLATTEN, SHOVEL_DOUSE}</code>, and a fix that named only the flatten ability would have
 * left campfire dousing broken — half the item's advertised contract, and the half no path-only test
 * would have caught.
 */
public class ElectricShovelItemNeoForge extends ElectricShovelItem {

	public ElectricShovelItemNeoForge(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
		return ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(itemAbility);
	}
}
