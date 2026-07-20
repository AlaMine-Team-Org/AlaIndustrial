package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Electromagnet (MOD-132) — a powered item that sits in <em>any</em> inventory slot and draws loose
 * {@link ItemEntity ItemEntities} in the world toward the carrier, so a miner's drops flow to them
 * instead of being collected by hand. It is the pickup-side convenience counterpart of the mod's
 * other EU items (Pouch MOD-052, Energy Pack MOD-065, Electric Drill MOD-079).
 *
 * <h2>Energy</h2>
 * Charge lives in the shared {@code pouch_energy} component through {@link ItemEnergy}; the magnet
 * registers its own capacity/input-rate there, so the Battery Box charge slot and a worn Energy Pack
 * top it up with no changes on their side. Each item actually nudged costs
 * {@link Config#magnetEuPerItem} EU — an idle scan (nothing in range) is free, which is the honest
 * tariff every reference tech mod converges on. At 0 EU the magnet does nothing (like a flat drill
 * mines at hand speed); a creative/spectating owner pays nothing (the rule lives in
 * {@link ItemEnergy#spend}, MOD-081).
 *
 * <h2>Tick model</h2>
 * {@link #inventoryTick} in 26.2 is server-only ({@code ServerLevel} argument; the {@code isClientSide}
 * gate sits above in {@code ItemStack.inventoryTick}) and fires for a stack in <em>any</em> slot —
 * hotbar, main inventory and the off-hand (26.2 ticks the off-hand through the equipment path). The
 * magnet does not filter by {@code slot}, so it works wherever the player keeps it. The scan is
 * throttled to once every {@link Config#magnetScanIntervalTicks} ticks and capped at
 * {@link #MAX_ITEMS_PER_SCAN} entities per pass so a chest-worth of drops cannot lag the server.
 *
 * <h2>What it will not pull (MVP safety)</h2>
 * <ul>
 * <li>Items still on their pickup delay — {@link ItemEntity#hasPickUpDelay()} — which covers a fresh
 * {@code Q}-drop (so the magnet does not instantly suck back what the player just threw) and the
 * "never pick up" sentinel ({@code setNeverPickUp}, delay 32767).</li>
 * <li>Items sitting in lava — pulling them would only drag them through the fire to the player.</li>
 * </ul>
 * The vanilla pickup path is untouched: the magnet only sets each item's velocity straight at the
 * player's body centre every tick (a seek, so it flies in from any direction — up, down or sideways),
 * and {@code ItemEntity.playerTouch} collects the item once it reaches the player and its delay has
 * elapsed.
 *
 * <h2>Toggle</h2>
 * Shift-right-click flips an on/off flag stored in {@link ModDataComponents#MAGNET_ENABLED} (absent =
 * on, so a freshly crafted magnet works out of the box; disabling stores {@code false}). A player can
 * therefore carry the magnet switched off next to farms/sorters without it hoovering their items — the
 * single most-requested magnet ergonomic across mods.
 */
public class MagnetItem extends Item {

	/** Below this distance (blocks) the item is already on the player; leave the pickup to vanilla. */
	private static final double PICKUP_SNAP = 0.2;
	/** Max seek speed toward the player (blocks/tick). Fast enough to feel like a real magnet — the item
	 * flies in — while still under the vanilla pickup window so it does not tunnel straight through. */
	private static final double SEEK_SPEED = 0.55;
	/** Seek gain: velocity toward the player is {@code min(SEEK_SPEED, dist × this)}, so far items move at
	 * full speed and the pull eases as the item arrives (no jitter on top of the player). */
	private static final double SEEK_GAIN = 0.45;
	/** Hard cap on entities handled per scan, so a mass drop (blown-up chest) cannot stall the tick. */
	private static final int MAX_ITEMS_PER_SCAN = 64;

	public MagnetItem(Properties properties) {
		super(properties);
	}

	// --- passive pull: every magnetScanIntervalTicks, draw nearby drops toward the carrier ---

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (!(entity instanceof Player player)) {
			return;
		}
		if (level.getGameTime() % Config.magnetScanIntervalTicks == 0) {
			magnetStep(stack, player, level);
		}
	}

	/**
	 * One scan+pull step — what {@link #inventoryTick} applies once per interval. Off, flat or with
	 * nothing pullable in range it writes nothing (an idle magnet costs no EU and no stack resync).
	 * Separated from the game-time gate so gametests can drive it deterministically. Returns the number
	 * of items actually nudged this step.
	 */
	public static int magnetStep(ItemStack stack, Player player, ServerLevel level) {
		if (!isEnabled(stack) || ItemEnergy.get(stack) <= 0) {
			return 0;
		}
		double range = Config.magnetRange;
		Vec3 target = pullTarget(player);
		AABB box = player.getBoundingBox().inflate(range);
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
				item -> canPull(item, target, range * range));
		boolean free = ItemEnergy.free(player);
		int pulled = 0;
		for (ItemEntity item : items) {
			if (pulled >= MAX_ITEMS_PER_SCAN) {
				break;
			}
			// Buffer exhausted — the magnet stalls (a per-item tariff, an idle scan is free).
			if (!free && ItemEnergy.get(stack) < Config.magnetEuPerItem) {
				break;
			}
			if (pullSingle(stack, player, item)) {
				pulled++;
			}
		}
		return pulled;
	}

	/**
	 * Pull one specific item — the per-item core of {@link #magnetStep}, factored out so it is the single
	 * source of truth for "should this item be pulled, and what does it cost". Applies the full gate:
	 * enabled, charged (or a creative/free owner), the item passes {@link #canPull}, and there is enough
	 * EU; on success it nudges the item and spends {@link Config#magnetEuPerItem}. Returns whether it
	 * pulled.
	 *
	 * <p>Exposed so gametests can drive it deterministically on a <em>detached</em> {@link ItemEntity}
	 * (never added to the world) — the live {@link #magnetStep} finds its targets with a world scan, which
	 * in a shared gametest server would bleed one test's dropped items into another's radius.
	 */
	public static boolean pullSingle(ItemStack magnet, Player player, ItemEntity item) {
		if (!isEnabled(magnet) || ItemEnergy.get(magnet) <= 0) {
			return false;
		}
		if (!ItemEnergy.free(player) && ItemEnergy.get(magnet) < Config.magnetEuPerItem) {
			return false;
		}
		double range = Config.magnetRange;
		Vec3 target = pullTarget(player);
		if (!canPull(item, target, range * range) || !applyPull(item, target, range)) {
			return false;
		}
		ItemEnergy.spend(magnet, Config.magnetEuPerItem, player);
		return true;
	}

	/** The point pulled items are drawn toward — the player's body centre (matches {@code ExperienceOrb}). */
	private static Vec3 pullTarget(Player player) {
		return new Vec3(player.getX(), player.getY() + player.getEyeHeight() / 2.0, player.getZ());
	}

	/**
	 * Whether a loose item may be pulled: alive, past its pickup delay (so a fresh {@code Q}-drop and the
	 * never-pickup sentinel are left alone), not sitting in lava, and inside the spherical range (the
	 * broad-phase {@link AABB} is a cube — this trims the corners).
	 */
	private static boolean canPull(ItemEntity item, Vec3 target, double rangeSqr) {
		return item.isAlive() && !item.hasPickUpDelay() && !item.isInLava()
				&& item.distanceToSqr(target.x, target.y, target.z) <= rangeSqr;
	}

	/**
	 * Seek {@code target}: <em>set</em> the item's velocity straight at the player's body centre at
	 * {@code min(SEEK_SPEED, dist × SEEK_GAIN)} blocks/tick. Setting (not adding) the velocity each tick
	 * makes the pull immediate and reliable in every direction — an item below the player rises to it,
	 * one above sinks — because it overrides gravity and ground friction rather than fighting them (the
	 * accumulate-and-drag XP-orb model was far too weak on grounded drops, which read as "not pulling").
	 * Returns whether it moved the item (one already on the player is left to the vanilla pickup).
	 */
	private static boolean applyPull(ItemEntity item, Vec3 target, double range) {
		Vec3 delta = new Vec3(target.x - item.getX(), target.y - item.getY(), target.z - item.getZ());
		double dist = delta.length();
		if (dist < PICKUP_SNAP) {
			return false;
		}
		double speed = Math.min(SEEK_SPEED, dist * SEEK_GAIN);
		item.setDeltaMovement(delta.scale(speed / dist)); // unit direction × speed
		return true;
	}

	// --- toggle: shift-right-click flips the on/off flag (absent component = on) ---

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		// Only shift-right-click toggles; a plain right-click passes through (no interference).
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		boolean nowEnabled = !isEnabled(stack);
		if (level instanceof ServerLevel) {
			setEnabled(stack, nowEnabled);
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
						Component.translatable(nowEnabled
								? "item.alaindustrial.electromagnet.toggled_on"
								: "item.alaindustrial.electromagnet.toggled_off")
								.withStyle(nowEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY),
						true);
			}
		}
		// A metallic electrical click — the copper-bulb on/off sound fits a powered iron device far better
		// than the cloth-bundle click; plays as the local player's own prediction.
		player.playSound(nowEnabled ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
				0.7F, nowEnabled ? 1.15F : 0.9F);
		return InteractionResult.SUCCESS;
	}

	/** On/off state: an absent component reads as on, so a crafted-fresh magnet works out of the box. */
	public static boolean isEnabled(ItemStack stack) {
		Boolean value = stack.get(ModDataComponents.MAGNET_ENABLED.get());
		return value == null || value;
	}

	/** Write the on/off flag; on (the default) removes the component so stacks stay component-identical. */
	public static void setEnabled(ItemStack stack, boolean enabled) {
		if (enabled) {
			stack.remove(ModDataComponents.MAGNET_ENABLED.get());
		} else {
			stack.set(ModDataComponents.MAGNET_ENABLED.get(), false);
		}
	}

	// --- tooltip: short lines — a flavor line, on/off state, the EU charge, and the tariff/range ---

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("item.alaindustrial.electromagnet.flavor")
				.withStyle(ChatFormatting.GRAY));
		boolean enabled = isEnabled(stack);
		adder.accept(Component.translatable(enabled
				? "item.alaindustrial.electromagnet.state_on"
				: "item.alaindustrial.electromagnet.state_off")
				.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		adder.accept(Component.translatable("item.alaindustrial.electromagnet.charge",
				ItemEnergy.get(stack), ItemEnergy.capacity(stack)).withStyle(ChatFormatting.GOLD));
		adder.accept(Component.translatable("item.alaindustrial.electromagnet.desc",
				Config.magnetRange, Config.magnetEuPerItem).withStyle(ChatFormatting.DARK_GRAY));
	}

	// --- item bar shows the EU charge in the LV tier colour (numbers are in the tooltip) ---

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		long capacity = ItemEnergy.capacity(stack);
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(MAX_BAR_WIDTH, MAX_BAR_WIDTH * ItemEnergy.get(stack) / capacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyTier.LV.color();
	}
}
