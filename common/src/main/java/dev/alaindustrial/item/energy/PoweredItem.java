package dev.alaindustrial.item.energy;

import dev.alaindustrial.item.ToolConfig;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * An item that owns an EU buffer (MOD-707). {@link ItemEnergy} asks the item itself for its capacity and
 * intake instead of walking an {@code instanceof} ladder, and both loaders derive the item energy
 * capability from this interface ({@code ItemCapabilityRoster.energyItems()}), so a new powered item is
 * one class implementing this interface plus its {@code ContentManifest.ITEMS} entry — {@code ItemEnergy}
 * and the loader entry points are not touched.
 *
 * <p>A subclass inherits the answers of its parent and overrides only what differs (the netherite drill
 * answers its own tier and nothing else), so correctness no longer depends on branch order.
 *
 * <p>Implementing this interface is a promise of a positive buffer: the gametest guard
 * {@code PoweredItemCatalog} checks every item of the roster for {@code capacity > 0}, independently.
 */
public interface PoweredItem {

	/**
	 * Max EU one item holds by this side's own balance ({@link ItemBuffers#live()}); read live, so a config reload
	 * is honoured. The server's logic asks this form — charging, spending, the clamp. Not overridden: an item
	 * answers {@link #energyCapacity(ItemStack, ItemBuffers)}.
	 */
	default long energyCapacity(ItemStack stack) {
		return energyCapacity(stack, ItemBuffers.live());
	}

	/**
	 * Max EU one item holds when the buffers are {@code buffers} (MOD-790): the item's own buffer, picked out of
	 * them and read from nothing else. The one place that says which knob is this item's; the client passes the
	 * server's numbers, the server its own.
	 */
	long energyCapacity(ItemStack stack, ItemBuffers buffers);

	/** Max EU/tick the item accepts in a charge slot or from a foreign charger. */
	long energyInputRate(ItemStack stack);

	/**
	 * Called by {@link ItemEnergy#set} after every write of the charge, with the clamped value — the one
	 * place charge changes, so a model, attribute or flag that follows the charge is refreshed here.
	 */
	default void onChargeChanged(ItemStack stack, long charge) {
	}

	/**
	 * This item's hover tooltip as a powered tool (MOD-716, ADR-040), or null when its tooltip is written
	 * elsewhere (the pouches and the battery have their own). Runs on the client: numbers are read through
	 * {@code ServerBalance}.
	 */
	default @Nullable PoweredToolTooltip toolTooltip() {
		return null;
	}

	/**
	 * How this item warns its owner when the charge runs low (MOD-652), or null when it does not.
	 * {@link ItemEnergy#spend} asks it, so the list of warning items is the list of overrides — no id table.
	 */
	default @Nullable LowChargeProfile lowChargeProfile() {
		return null;
	}

	/**
	 * The price of this item's main action — the charge under which it stops working as a powered item
	 * (a drill's {@code euPerBlock}, a saber's hit, a bow's shot), or 0 when it has none. A tool refuses an action
	 * it cannot afford and so never spends down to zero; the low-charge warning (MOD-652) reads this as the
	 * level at which it is "depleted", the same comparison the tool's own gate makes.
	 */
	default long actionCost() {
		return 0L;
	}

	/** The shapes a low-charge warning takes; the thresholds themselves are {@code ToolConfig} knobs. */
	enum LowChargeProfile {
		/**
		 * A tool, bow or sabre: one warning on the crossing, then a repeat every
		 * {@code ToolConfig.lowChargeWarnRepeatSeconds} while it stays low and keeps spending, and a signal of
		 * its own once the charge no longer pays for one more action ({@link PoweredItem#actionCost}).
		 */
		TOOL,
		/**
		 * The jetpack: it burns its last tenth in seconds, so it repeats every second and gives a signal of its
		 * own when the buffer hits zero.
		 */
		JETPACK;

		/** Ticks between repeats of the jetpack warning. */
		static final int JETPACK_REPEAT_TICKS = 20;

		/**
		 * The charge under which {@code item} counts as depleted: the jetpack's is 1 (it burns to a clean zero),
		 * a tool's is the price of its main action.
		 */
		public long depletedBelow(PoweredItem item) {
			return this == JETPACK ? 1L : item.actionCost();
		}

		/** The shortest gap between repeats of this profile's warning, in ticks. */
		public long repeatTicks() {
			return this == JETPACK ? JETPACK_REPEAT_TICKS : ToolConfig.lowChargeWarnRepeatSeconds * 20L;
		}
	}
}
