package dev.alaindustrial.item.tool;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Electric Bow (MOD-363) — the EU line's ranged weapon, after the Electric Saber (MOD-149). Same
 * contract as the rest of the line: it runs on EU instead of durability, never breaks, and when it
 * runs flat it degrades to a plain bow rather than switching off.
 *
 * <p>Arrows are still ammunition. EU buys a better shot, not free arrows — a bow that fired on energy
 * alone would be a different weapon with a different economy, and it was decided against.
 *
 * <p>And a flat bow is NOT a plain bow. It never breaks, so if running out of charge left a working
 * vanilla bow in the hand, the line's cheapest-to-maintain ranged weapon would be one that never needs
 * charging at all. Instead a flat bow still draws and still spends the arrow, but the arrow leaves at
 * {@link #FLAT_LAUNCH_SPEED} whatever the draw — it drops about a block in front of the archer. The
 * charge is what makes it a weapon.
 *
 * <h2>Two states, both decided by the charge</h2>
 * <ul>
 * <li><b>Charged</b> (at least {@link Config#electricBowEuPerShot}) — full draw in
 * {@link #LIVE_DRAW_TICKS} ticks instead of 20, the arrow leaves {@link #LIVE_VELOCITY_MULTIPLIER}
 * times faster, and its spread is {@link #LIVE_SPREAD_MULTIPLIER} of vanilla's. One shot costs
 * {@link Config#electricBowEuPerShot}.</li>
 * <li><b>Flat</b> — the draw animation still plays and the arrow is still spent, but it leaves at
 * {@link #FLAT_LAUNCH_SPEED}, never crits, and lands at the archer's feet. Nothing to pay, nothing to
 * gain.</li>
 * </ul>
 * There is deliberately no on/off switch: the saber's shift-right-click would collide with how people
 * shoot (crouching to aim), and a bow that is useless when flat has no reason to be switched off.
 *
 * <h2>Why only velocity, and not a damage multiplier</h2>
 * Vanilla computes an arrow's damage as {@code ceil(speed × baseDamage)}. Speed therefore already
 * carries the damage bonus (a full live shot is 7 against vanilla's 6), and the range and the flatter
 * trajectory come with it. A separate damage number would be a second knob for the same effect.
 *
 * <h2>How the faster draw reaches vanilla's code</h2>
 * {@code BowItem.releaseUsing} turns held ticks into power over a fixed 20-tick draw. A live bow scales
 * the ticks it reports up by {@code 20 / LIVE_DRAW_TICKS} before handing over, so everything vanilla does
 * with them — the minimum-power refusal, the crit at full draw, NeoForge's arrow-loose event — keeps
 * working on the shorter draw without being written out again here. Velocity and spread are applied
 * one step later, in {@link #shoot}, the one place that sees both the weapon and the numbers.
 *
 * <h2>What the client reads</h2>
 * The server decides from the charge. The client — item model, draw frames, FOV zoom, the first-person
 * pull — reads {@link ModDataComponents#ELECTRIC_BOW_CHARGED}, which {@link #refreshCharged} keeps in
 * step from {@link ItemEnergy#set}, the single point a powered item's charge ever changes. So the
 * picture and the shot agree even when the server's config differs from the player's.
 */
public class ElectricBowItem extends BowItem {

	/** Ticks to a full draw while charged — 0.8 s against vanilla's {@link BowItem#MAX_DRAW_DURATION}. */
	public static final int LIVE_DRAW_TICKS = 16;
	/** Arrow launch speed while charged: 3.0 → 3.45 blocks per tick at full draw. */
	public static final float LIVE_VELOCITY_MULTIPLIER = 1.15f;
	/** Arrow spread while charged: half of vanilla's, so twice as tight a group. */
	public static final float LIVE_SPREAD_MULTIPLIER = 0.5f;
	/**
	 * Launch speed of every arrow a flat bow fires, blocks per tick, whatever the draw: a third of the
	 * weakest shot vanilla allows (power 0.1 × 3.0). From eye height it lands about 0.9 blocks ahead and
	 * hits for 1.
	 */
	public static final float FLAT_LAUNCH_SPEED = 0.1f;
	/** Enchantability — the diamond value ({@code ToolMaterial.DIAMOND.enchantmentValue}), like the line. */
	private static final int ENCHANT_VALUE = 10;

	public ElectricBowItem(Properties properties) {
		super(properties);
	}

	/**
	 * The bow's item properties, applied identically by both loaders (Fabric adds {@code setId},
	 * NeoForge supplies the id from its deferred key — the only difference).
	 *
	 * <p>{@code stacksTo(1)} is explicit because we skip {@code durability(...)}, which is where a vanilla
	 * bow's max stack size of 1 normally comes from. No {@code MAX_DAMAGE} means vanilla's
	 * {@code hurtAndBreak} after each shot is a no-op ({@code processDurabilityChange} returns 0 for an
	 * undamageable stack) — there is nothing to wear down.
	 */
	public static Properties electricBowProperties(Properties props) {
		return props.stacksTo(1).enchantable(ENCHANT_VALUE);
	}

	// --- the charge-driven layer -------------------------------------------------------------------

	/** Whether this stack would fire a powered shot right now — the server's decision. */
	public static boolean isLive(ItemStack stack) {
		return stack.getItem() instanceof ElectricBowItem && ItemEnergy.get(stack) >= Config.electricBowEuPerShot;
	}

	/**
	 * Whether the stack shows as charged — the synced mirror of {@link #isLive}. This is what the client
	 * reads for everything it draws; see the class javadoc.
	 */
	public static boolean showsCharged(ItemStack stack) {
		return stack.getItem() instanceof ElectricBowItem && stack.has(ModDataComponents.ELECTRIC_BOW_CHARGED.get());
	}

	/** Ticks to a full draw for this stack as the client sees it: the live draw, or vanilla's. */
	public static int drawTicks(ItemStack stack) {
		return showsCharged(stack) ? LIVE_DRAW_TICKS : MAX_DRAW_DURATION;
	}

	/**
	 * Point the {@code electric_bow_charged} flag at the charge. Called from {@link ItemEnergy#set} and
	 * from {@link #inventoryTick}; writes only when the state flips, so a bow draining shot by shot
	 * touches the component once — on the shot that takes it under the price.
	 */
	public static void refreshCharged(ItemStack stack, long eu) {
		if (!(stack.getItem() instanceof ElectricBowItem)) {
			return;
		}
		boolean live = eu >= Config.electricBowEuPerShot;
		boolean shown = stack.has(ModDataComponents.ELECTRIC_BOW_CHARGED.get());
		if (live && !shown) {
			stack.set(ModDataComponents.ELECTRIC_BOW_CHARGED.get(), Unit.INSTANCE);
		} else if (!live && shown) {
			stack.remove(ModDataComponents.ELECTRIC_BOW_CHARGED.get());
		}
	}

	// --- the shot ----------------------------------------------------------------------------------

	/**
	 * A live bow reports its held ticks to vanilla scaled by {@code 20 / LIVE_DRAW_TICKS}, so a 16-tick
	 * draw arrives as the 20 ticks vanilla calls full. Rounded down: a partial draw never rounds up into
	 * a crit. A flat bow's draw passes straight through; its shot is weakened in {@link #shoot}.
	 */
	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		if (isLive(stack)) {
			int useDuration = this.getUseDuration(stack, entity);
			int held = useDuration - remainingTime;
			int asVanilla = held * MAX_DRAW_DURATION / LIVE_DRAW_TICKS;
			remainingTime = useDuration - asVanilla;
		}
		return super.releaseUsing(stack, level, entity, remainingTime);
	}

	/**
	 * The launch: a live bow sends every arrow faster and straighter, then pays for the shot; a flat one
	 * lets it fall out at {@link #FLAT_LAUNCH_SPEED} with no crit. Reached only server-side, only after
	 * vanilla found an arrow and a draw strong enough to fire — so a missing arrow or an under-drawn
	 * release costs nothing. EU is spent once per release even if an enchantment splits it into several
	 * arrows. {@link ItemEnergy#spend} drops the debit for a player with infinite materials (MOD-081), like
	 * every other powered item.
	 */
	@Override
	protected void shoot(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon,
			List<ItemStack> projectiles, float power, float uncertainty, boolean isCrit,
			@Nullable LivingEntity targetOverride) {
		if (!isLive(weapon)) {
			super.shoot(level, shooter, hand, weapon, projectiles, FLAT_LAUNCH_SPEED, uncertainty, false,
					targetOverride);
			return;
		}
		super.shoot(level, shooter, hand, weapon, projectiles, power * LIVE_VELOCITY_MULTIPLIER,
				uncertainty * LIVE_SPREAD_MULTIPLIER, isCrit, targetOverride);
		ItemEnergy.spend(weapon, Config.electricBowEuPerShot, shooter);
	}

	// --- self-heal: a bow can arrive charged without ever passing through ItemEnergy.set ---

	/**
	 * Keeps the charged flag honest for stacks this class never saw charged: {@code /give} with a
	 * {@code pouch_energy} component, a loot table, a datapack recipe — and a server whose price was
	 * changed in the config since the flag was written. Only equipment slots are checked (both hands, for
	 * a bow drawn from the off hand); every other inventory slot passes {@code null}.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (slot == null) {
			return;
		}
		refreshCharged(stack, ItemEnergy.get(stack));
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
