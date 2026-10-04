package dev.alaindustrial.client.tooltip;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.item.misc.MutationGrades;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.item.energy.BatteryItem;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.energy.PoweredItem;
import dev.alaindustrial.item.energy.PoweredToolTooltip;
import dev.alaindustrial.item.tool.NetworkAnalyzerItem;
import dev.alaindustrial.item.tool.NetworkScanData;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.misc.ShieldingPouchItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import dev.alaindustrial.client.AlaClientConfig;
import org.jspecify.annotations.Nullable;

/**
 * Loader-neutral hover-tooltip content for the mod's block items, its powered items and the Network Analyzer
 * (MOD-022). Each loader's client hooks its own tooltip event and calls {@link #append}: Fabric via
 * {@code ItemTooltipCallback}, NeoForge via {@code ItemTooltipEvent}.
 *
 * <p><b>A block's tooltip is declared by the block</b> (MOD-716, ADR-040): a block implementing
 * {@link HasMachineTooltip} hands over its {@link MachineTooltipSpec}, and this class only renders it — it keeps
 * no list of machines. The four modes ([SHIFT] up/down x the client's {@code showEuNumbers}) are decided by the
 * description, not by four parallel lists here.
 *
 * <p>Client-side code, though it links no client-only type: it reads the player's {@link AlaClientConfig}
 * (always-detailed tooltips, EU numbers shown) and the server's balance through {@link ServerBalance}. The
 * "is shift held" check is passed in as a boolean.
 */
public final class MachineTooltips {
	private MachineTooltips() {
	}

	/** Append Ala Industrial tooltip lines for {@code stack} (machine stats or analyzer reading). */
	public static void append(ItemStack stack, List<Component> lines, boolean shiftDown) {
		// A mutated stack carries its grade regardless of what item it is, so this runs before the
		// mod-item branches below (a graded vanilla ingot would never reach them).
		MutationGrade grade = MutationGrades.get(stack);
		if (grade.isMarked()) {
			// MOD-498 — Rarity#color() is deprecated by NeoForge only. Vanilla's Rarity has color() and
			// nothing else; the replacement NeoForge names, getStyleModifier(), is added by its patch and is
			// absent from the vanilla class this shared body is compiled against for Fabric. color() is the
			// only accessor that exists on both loaders.
			@SuppressWarnings("deprecation")
			ChatFormatting gradeColor = MutationGrades.vanillaRarity(grade).color();
			lines.add(Component.translatable(grade.translationKey()).withStyle(gradeColor));
		}
		// MOD-666: a dyed cable names its colour with vanilla's own "Color: %s" line and colour names,
		// so the line reads the same as dyed leather in every language the game ships.
		net.minecraft.world.item.DyeColor cableColor = stack.get(dev.alaindustrial.registry.ModDataComponents.CABLE_COLOR.get());
		if (cableColor != null) {
			lines.add(Component.translatable("item.color",
					Component.translatable("color.minecraft." + cableColor.getName())).withStyle(ChatFormatting.GRAY));
		}
		boolean detailed = shiftDown || AlaClientConfig.alwaysDetailedTooltips;
		if (stack.getItem() instanceof NetworkAnalyzerItem) {
			addNetworkAnalyzerTooltip(stack, lines, detailed);
			return;
		}
		if (stack.getItem() instanceof ShieldingPouchItem) {
			addShieldingPouchTooltip(stack, lines, detailed);
			return;
		}
		if (stack.getItem() instanceof PouchItem) {
			addPouchTooltip(stack, lines, detailed);
			return;
		}
		if (appendStorageCapacity(stack, lines, detailed)) {
			return;
		}
		if (stack.getItem() instanceof BatteryItem) {
			addBatteryTooltip(stack, lines);
			return;
		}
		// A powered tool or wearable declares its own tooltip (MOD-716, ADR-040).
		if (stack.getItem() instanceof PoweredItem powered) {
			PoweredToolTooltip tool = powered.toolTooltip();
			if (tool != null) {
				addPoweredToolTooltip(stack, tool, lines);
				return;
			}
		}
		// Plain-item components (not BlockItem) — the windmill rotor is the only such item with a
		// tooltip. Its line describes what it does in the wind mill, not standalone stats.
		if (stack.is(ModContent.WINDMILL_ROTOR.get())) {
			addRotorTooltip(lines, detailed);
			return;
		}
		if (stack.is(ModContent.DRILL_COLUMN_MODULE.get())) {
			addColumnModuleTooltip(lines, detailed);
			return;
		}
		if (stack.is(ModContent.CORE_BARREL.get())) {
			lines.add(Component.translatable("tooltip.alaindustrial.core_barrel.role")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		if (stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof HasMachineTooltip owner) {
			for (MachineTooltipSpec.Line line : owner.machineTooltip().lines(detailed, AlaClientConfig.showEuNumbers)) {
				lines.add(render(line));
			}
		}
	}

	/** One described line as a chat component: {@code key(args)} in its colour, or {@code label — key(args)}. */
	static Component render(MachineTooltipSpec.Line line) {
		MutableComponent body = Component.translatable(line.key(), line.argValues());
		if (line.label() == null) {
			return body.withStyle(color(line.tone()));
		}
		return Component.translatable(line.label()).append(" \u2014 ").append(body).withStyle(color(line.tone()));
	}

	private static ChatFormatting color(MachineTooltipSpec.Tone tone) {
		return switch (tone) {
			case GRAY -> ChatFormatting.GRAY;
			case DARK_GRAY -> ChatFormatting.DARK_GRAY;
			case GREEN -> ChatFormatting.GREEN;
			case RED -> ChatFormatting.RED;
			case LIGHT_PURPLE -> ChatFormatting.LIGHT_PURPLE;
			case AQUA -> ChatFormatting.AQUA;
		};
	}

	/**
	 * Tooltip for the Network Analyzer tool. The usage line is always shown; the last scan (stored on the
	 * item as {@link ModDataComponents#NETWORK_SCAN} the moment it is used) is replayed under [SHIFT] so
	 * the reading persists in the inventory after the actionbar message fades (MOD-016). The active mode
	 * (TRAVERSE / STOP_AT_STORAGE, MOD-047) is shown right under the usage line so the player always knows
	 * which behaviour a cable scan will use.
	 */
	private static void addNetworkAnalyzerTooltip(ItemStack stack, List<Component> lines, boolean shiftDown) {
		lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.usage")
				.withStyle(ChatFormatting.GRAY));
		AnalyzerMode mode = stack.get(ModDataComponents.NETWORK_ANALYZER_MODE.get());
		if (mode == null) {
			mode = AnalyzerMode.TRAVERSE;
		}
		lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.mode_label",
						Component.translatable("tooltip.alaindustrial.network_analyzer.mode." + mode.getSerializedName()))
				.withStyle(ChatFormatting.AQUA));
		NetworkScanData scan = stack.get(ModDataComponents.NETWORK_SCAN.get());
		if (scan == null) {
			return;
		}
		if (shiftDown) {
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.last_scan")
					.withStyle(ChatFormatting.AQUA));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.cables", scan.cables())
					.withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.endpoints",
					scan.producers(), scan.consumers(), scan.storage()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.flow",
					scan.supply(), scan.demand()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.moved", scan.moved())
					.withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/**
	 * Tooltip text for the Battery Pouch (MOD-052): usage, EU charge (red DEPLETED at 0 — the lock state
	 * must be readable at a glance), tier. The contents grid and the weight bar are visual — see
	 * {@code PouchClientTooltip} (bundle-style tooltip image, player request).
	 */
	/**
	 * Tooltip text for the Shielding Pouch (MOD-545): how to use it, and — the whole point of the
	 * item — that it stops the radiation of what is inside without standing in for the suit.
	 */
	private static void addShieldingPouchTooltip(ItemStack stack, List<Component> lines, boolean detailed) {
		// Everything the Battery Pouch says — it IS one, plus lead — and then the two lines that are
		// the whole reason this tier exists. The charge keys are the base pouch's on purpose: the text
		// is the same sentence about the same buffer, and a second copy would be one more string for
		// every locale to drift on.
		addPouchTooltip(stack, lines, detailed);
		lines.add(Component.translatable("tooltip.alaindustrial.shielding_pouch.shielded")
				.withStyle(ChatFormatting.GREEN));
		lines.add(Component.translatable("tooltip.alaindustrial.shielding_pouch.not_a_suit")
				.withStyle(ChatFormatting.YELLOW));
	}

	/**
	 * Capacity of the mod's storage blocks, under [SHIFT] (MOD-600).
	 *
	 * <p>A chest tier tells the player nothing about its size until it is placed and opened, and the
	 * ladder now runs from 36 to 108 — a difference worth knowing before you carry one home.
	 *
	 * <p><b>The numbers come from the block entities themselves</b>, never from a literal here: a
	 * tooltip that states a capacity the container does not have is worse than no tooltip, and this is
	 * exactly the kind of second copy that goes stale silently.
	 *
	 * @return whether this stack was one of the storage blocks (and the tooltip is therefore done)
	 */
	private static boolean appendStorageCapacity(ItemStack stack, List<Component> lines, boolean detailed) {
		int slots = storageSlots(stack);
		if (slots <= 0) {
			return false;
		}
		if (!detailed) {
			lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
			return true;
		}
		lines.add(Component.translatable("tooltip.alaindustrial.storage_slots", slots)
				.withStyle(ChatFormatting.GRAY));
		if (stack.is(ModContent.STORAGE_MODULE_ITEM.get())) {
			// A module's number is misleading on its own: touching modules pool into one warehouse.
			lines.add(Component.translatable("tooltip.alaindustrial.storage_module_merges")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.storage_slots_double", slots * 2)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		return true;
	}

	/** Slots of the storage block behind {@code stack}, or 0 when it is not one of ours. */
	private static int storageSlots(ItemStack stack) {
		if (stack.is(ModContent.IRON_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.IronChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.SILVER_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.SilverChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.GOLD_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.GoldChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.ELECTRUM_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.ElectrumChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.DIAMOND_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.DiamondChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.SHIELDING_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.ShieldingChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.STORAGE_MODULE_ITEM.get())) {
			return dev.alaindustrial.block.entity.StorageModuleBlockEntity.CONTAINER_SIZE;
		}
		return 0;
	}

	private static void addPouchTooltip(ItemStack stack, List<Component> lines, boolean detailed) {
		// Two short list lines instead of one long sentence — long single-line tooltips stretch the
		// box, and other locales run even longer (player feedback).
			lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.usage_insert")
					.withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.usage_extract")
				.withStyle(ChatFormatting.GRAY));
		long eu = ItemEnergy.get(stack);
		long cap = ItemEnergy.capacity(stack);
		if (eu <= 0) {
			lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.depleted")
					.withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.charge", eu, cap)
					.withStyle(ChatFormatting.GOLD));
			if (cap > 0 && eu * 10 < cap) {
				lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.low_charge")
						.withStyle(ChatFormatting.RED));
			}
		}
		// No tier line: the Battery Pouch is a tier-less consumer item; its charge state and item
		// bar (LV gold) already convey everything the player needs.
	}

	/**
	 * Tooltip text for the Battery (MOD-083): what it is for, its charge, and — when the player is
	 * holding more than one — what the whole stack is worth.
	 *
	 * <p>The stack line exists because the battery is the only powered item that stacks, and its charge is
	 * stored per item: without it, sixteen full batteries would read as "2 000 EU" and the player would
	 * have no way to see the 32 000 they are actually carrying.
	 */
	private static void addBatteryTooltip(ItemStack stack, List<Component> lines) {
		lines.add(Component.translatable("tooltip.alaindustrial.battery.usage")
				.withStyle(ChatFormatting.GRAY));
		long eu = ItemEnergy.get(stack);
		long cap = ItemEnergy.capacity(stack);
		if (eu <= 0) {
			lines.add(Component.translatable("tooltip.alaindustrial.battery.depleted")
					.withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.battery.charge", eu, cap)
					.withStyle(ChatFormatting.GOLD));
			if (stack.getCount() > 1) {
				lines.add(Component.translatable("tooltip.alaindustrial.battery.stack_total",
						stack.getCount(), ItemEnergy.stackGet(stack)).withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}

	/**
	 * A powered tool's tooltip (MOD-716): the usage line, its state lines, the charge — gold
	 * {@code eu / capacity}, or the red "depleted" line once the item's own rule says it can no longer work —
	 * then the state lines that follow the charge. No [SHIFT] gate: the charge is what a player checks.
	 */
	private static void addPoweredToolTooltip(ItemStack stack, PoweredToolTooltip tooltip, List<Component> lines) {
		lines.add(Component.translatable(tooltip.usageKey(), tooltip.usageValues()).withStyle(ChatFormatting.GRAY));
		addStateLines(stack, tooltip.beforeCharge(), lines);
		long eu = ItemEnergy.get(stack);
		long cap = ItemEnergy.capacity(stack);
		if (tooltip.depleted().test(stack)) {
			lines.add(Component.translatable(tooltip.depletedKey()).withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable(tooltip.chargeKey(), eu, cap).withStyle(ChatFormatting.GOLD));
		}
		addStateLines(stack, tooltip.afterCharge(), lines);
	}

	private static void addStateLines(ItemStack stack,
			List<Function<ItemStack, MachineTooltipSpec.@Nullable Line>> states, List<Component> lines) {
		for (Function<ItemStack, MachineTooltipSpec.@Nullable Line> state : states) {
			MachineTooltipSpec.Line line = state.apply(stack);
			if (line != null) {
				lines.add(render(line));
			}
		}
	}

	/**
	 * Tooltip for the wooden rotor — describes its role in the wind mill: required to generate,
	 * and the base EU/t a T1 wind mill produces at full height. Numbers come from {@link ServerBalance} so
	 * the tooltip stays in sync with the server's balance knobs (the rotor itself carries no stats — it is
	 * a gate, the mill's output depends on height/weather).
	 */
	private static void addRotorTooltip(List<Component> lines, boolean detailed) {
		// Always-on line: what it does.
		lines.add(Component.translatable("tooltip.alaindustrial.rotor_role")
				.withStyle(ChatFormatting.GRAY));
		// Base T1 output at full height (the cap), so a player can compare rotors without placing.
		lines.add(tt("rotor_output", ServerBalance.windMillMaxEuPerTick()));
		if (detailed) {
			lines.add(tier());
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static void addColumnModuleTooltip(List<Component> lines, boolean detailed) {
		lines.add(Component.translatable("tooltip.alaindustrial.drill_column_module.role")
				.withStyle(ChatFormatting.GRAY));
		if (!detailed) {
			lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		lines.add(Component.translatable("tooltip.alaindustrial.drill_column_module.fitting")
				.withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("tooltip.alaindustrial.drill_column_module.toggle")
				.withStyle(ChatFormatting.GRAY));
	}

	private static Component tt(String key, Object value) {
		return Component.translatable("tooltip.alaindustrial." + key, value)
				.withStyle(ChatFormatting.GRAY);
	}

	private static Component tier() {
		return Component.translatable("tooltip.alaindustrial.tier_lv")
				.withStyle(ChatFormatting.GREEN);
	}
}
