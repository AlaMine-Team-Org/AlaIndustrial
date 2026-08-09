package dev.alaindustrial.item.tool.neoforge;

import dev.alaindustrial.item.tool.ElectricHoeItem;
import net.minecraft.world.item.ItemInstance;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * NeoForge {@link ElectricHoeItem}: declares the {@code HOE_TILL} item ability so the hoe can actually
 * till on this loader.
 *
 * <h2>Why this class has to exist (MOD-378)</h2>
 * NeoForge does not merely wrap vanilla tilling — it <b>replaces</b> it. Patched
 * {@code HoeItem.useOn} never touches the vanilla {@code TILLABLES} map (NeoForge deprecates it and
 * patches it out of the flow); it asks the block instead:
 *
 * <pre>{@code
 * BlockState toolModifiedState = level.getBlockState(pos)
 *         .getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
 * if (logicPair == null) return InteractionResult.PASS;
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
 * {@code SWORD_SWEEP}, and vanilla's own {@code HoeItem} only passes the gate because NeoForge patches
 * it to return {@code ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability)}. Our hoe deliberately does
 * <b>not</b> extend {@code HoeItem} (that constructor applies {@code Properties.hoe(...)}, which sets
 * {@code MAX_DAMAGE} and would give an EU tool a durability bar — see
 * {@link ElectricHoeItem}), so it inherited the {@code false} default and silently could not till.
 *
 * <p><b>This was a shipped defect, not a new-feature gap.</b> The base Electric Hoe (MOD-342) has been
 * unable to till on NeoForge since it was released; it went unnoticed because tilling was never covered
 * by a gametest on the NeoForge lane, and the Fabric lane has no such gate. Proven in-world in the
 * NeoForge gametest server: a vanilla {@code minecraft:diamond_hoe} clicked on the same dirt block in
 * the same rig returned {@code Success} and produced farmland, while a fully charged
 * {@code alaindustrial:electric_hoe} returned {@code Pass} and left the dirt untouched. TC-HOE-001-FUN02
 * and FUN03 now run on both loaders and keep it that way.
 *
 * <p>Declaring the ability (rather than re-implementing tilling in a
 * {@code BlockToolModificationEvent} handler) is the loader-intended fix and the cheap one: vanilla then
 * computes the resulting state itself, so blocks other mods add to the tillable set keep working, and the
 * shared {@code common/} logic — including the upgrade's irrigation in
 * {@code ElectricHoeDiamondTipItem.useOn} — needs no NeoForge-specific branch at all.
 *
 * <p>The sibling {@code ElectricShovelItem} had the same defect through the same mechanism
 * ({@code ShovelItem.useOn} gates on {@code SHOVEL_FLATTEN}/{@code SHOVEL_DOUSE}). It was fixed the same
 * way in MOD-379 — see {@link ElectricShovelItemNeoForge}. That the defect recurred at all is why
 * {@code docs/tools/arch_check.py} now fails the build when an item whose {@code useOn} delegates to a
 * vanilla tool is registered on NeoForge without such a subclass: a javadoc note like this one is not a
 * gate, and it did not stop the second occurrence.
 */
public class ElectricHoeItemNeoForge extends ElectricHoeItem {

	public ElectricHoeItemNeoForge(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
		return ItemAbilities.DEFAULT_HOE_ACTIONS.contains(itemAbility);
	}
}
