package dev.alaindustrial.item.tool;

import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.EnergyBar;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.energy.PoweredItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The base of the four electric mining tools — {@link ElectricDrillItem}, {@link ElectricChainsawItem},
 * {@link ElectricShovelItem}, {@link ElectricHoeItem} — and so of every tier of them (MOD-707). It holds
 * what the line shares and nothing else: the EU mining contract, the charge bar and the energy numbers read
 * from the tool's {@link ElectricToolTier}. What makes a tool itself stays in its class: the hand-built
 * {@code TOOL} component in its {@code Properties} builder, the drill's column bore and torch, the shovel's
 * and the hoe's right-click conversion, the tips' Silk Touch mode, the tooltip.
 *
 * <h2>The contract — {@link ElectricMiningRule}, applied here only</h2>
 * <ul>
 * <li>{@link #getDestroySpeed}: the {@code TOOL} component's speed while the tool affords one block
 * ({@link ElectricToolTier#euPerBlock}); otherwise exactly {@link ElectricMiningRule#HAND_SPEED}. The
 * mining tier and the drops come from the {@code TOOL} component either way, so a flat tool still mines
 * what it mined and keeps the drops — it is just slow.</li>
 * <li>{@link #mineBlock}: a block of non-zero hardness broken on the server is billed through
 * {@link #payForBlock} — by default one {@code euPerBlock}, and only when the tool affords it, so a block
 * broken at hand speed is free. Creative is dropped inside {@link ItemEnergy#spend} (MOD-081).</li>
 * </ul>
 * Both are {@code final}: a tool that needs another price overrides {@link #payForBlock}, as the drill does
 * for its column, and cannot fork the gate around it.
 *
 * <p>Same source on both Minecraft lines. A loader's own subclass of a tool (the NeoForge hoe and shovel on
 * 26.2, which answer {@code ItemAbility} queries) extends the tool, not this base, and overrides none of it.
 */
public abstract class ElectricMiningToolItem extends Item implements PoweredItem {

	protected ElectricMiningToolItem(Properties properties) {
		super(properties);
	}

	/** The energy numbers of this tool's tier; a higher tier of the same line overrides it. */
	protected abstract ElectricToolTier toolTier();

	// --- mining: full speed while charged, hand speed when flat (drops kept either way) ---

	@Override
	public final float getDestroySpeed(ItemStack stack, BlockState state) {
		if (ElectricMiningRule.affords(ItemEnergy.get(stack), toolTier().euPerBlock())) {
			return super.getDestroySpeed(stack, state);
		}
		return ElectricMiningRule.HAND_SPEED;
	}

	@Override
	public final boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		if (ElectricMiningRule.billsBlock(level.isClientSide(), state.getDestroySpeed(level, pos))) {
			payForBlock(stack, level, state, pos, owner);
		}
		return super.mineBlock(stack, level, state, pos, owner);
	}

	/**
	 * Bills a block {@link #mineBlock} has decided is billable (server side, non-zero hardness): one
	 * {@code euPerBlock} of this tool's tier, if the tool affords it.
	 */
	protected void payForBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		spendIfAffordable(stack, toolTier().euPerBlock(), owner);
	}

	/** Spends {@code cost} when the stack affords it, nothing otherwise — the spend half of the rule. */
	protected static void spendIfAffordable(ItemStack stack, int cost, LivingEntity owner) {
		if (ElectricMiningRule.affords(ItemEnergy.get(stack), cost)) {
			ItemEnergy.spend(stack, cost, owner);
		}
	}

	// --- item bar shows the EU charge in the LV tier colour (numbers are in the tooltip) ---

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return EnergyBar.width(stack, MAX_BAR_WIDTH);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyBar.color(EnergyTier.LV);
	}

	/** This item's EU buffer, read by {@code ItemEnergy.capacity} through {@link PoweredItem}. */
	@Override
	public long energyCapacity(ItemStack stack) {
		return toolTier().buffer();
	}

	@Override
	public long energyInputRate(ItemStack stack) {
		return toolTier().inputRate();
	}
}
