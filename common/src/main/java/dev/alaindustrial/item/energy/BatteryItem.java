package dev.alaindustrial.item.energy;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Battery (MOD-083) — the mod's first EU carrier, and the only powered item that <b>stacks</b>.
 *
 * <p>Charge lives in the shared {@code pouch_energy} component through {@link ItemEnergy}, and it is
 * charge <b>per item</b>: a stack of {@link #MAX_STACK} batteries at 2 000 EU each carries 32 000 EU.
 * That single decision is what gives the item the behaviour players know from IC2's RE-Battery, for
 * free, out of vanilla's own stacking rule (stacks merge only when their components are equal):
 *
 * <ul>
 *   <li>batteries drained to zero stack with each other and with freshly crafted ones — because
 *       {@link ItemEnergy#set} <em>removes</em> the component at 0 EU, so a dead battery is
 *       component-identical to a new one;</li>
 *   <li>equally charged batteries stack with each other;</li>
 *   <li>batteries at different charges do not stack, and that is the honest signal that they hold
 *       different amounts of energy.</li>
 * </ul>
 *
 * <p><b>Why the stack size is 16.</b> Energy moved into or out of a stack has to divide evenly by
 * {@code count}, or every transfer would round energy into or out of existence. A charge slot moves at
 * most the LV ceiling (32 EU/t), and {@code 32 / 16 = 2} — so even a full stack takes a whole number
 * of EU per item per tick. At 64 the same slot would move {@code 32 / 64 = 0} and a full stack would
 * simply never charge.
 *
 * <p>The item is deliberately <b>not</b> a charger: it does not top up tools by itself while it sits
 * in the inventory (that is the worn Energy Pack's job). It gives its charge back in a discharge slot
 * (Battery Box, CESU) or by an explicit right-click into the item in the other hand.
 */
public class BatteryItem extends Item {
	/** Stack size, matching IC2's RE-Battery — and the number that keeps stack transfers exact. */
	public static final int MAX_STACK = 16;

	public BatteryItem(Properties properties) {
		super(properties);
	}

	// --- right-click: pour charge into the item in the other hand ---

	/**
	 * Hands {@link Config#batteryTransferPerUse} EU to whatever powered item the player holds in their
	 * other hand. This is the manual counterpart of a discharge slot: no ticking, no background
	 * transfer, no loop to guard against — energy moves only when the player asks for it.
	 *
	 * <p>Both stacks must be single items. Charge is per item, so pouring "out of a stack of six" has no
	 * defined meaning: either all six pay (and the player loses five times what they expected) or one
	 * does (and the stack would have to split). Refusing with a message is the honest third option.
	 *
	 * <p>The move goes through {@link ItemEnergy#add}, never {@link ItemEnergy#spend}: spending carries
	 * the creative guard (EU is tool wear, and creative does not wear tools down), which is right for an
	 * item being <em>used</em> and wrong for a transfer the player explicitly asked for. Same rule the
	 * CESU discharge slot already follows.
	 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack battery = player.getItemInHand(hand);
		ItemStack target = player.getItemInHand(
				hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (target.isEmpty() || ItemEnergy.capacity(target) <= 0) {
			// Nothing to pour into: stay out of the way entirely (no sound, no message, no cooldown).
			return InteractionResult.PASS;
		}
		if (battery.getCount() != 1 || target.getCount() != 1) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
						Component.translatable("item.alaindustrial.battery.stack_blocked")
								.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResult.FAIL;
		}
		long move = Math.min(Math.min(ItemEnergy.get(battery), ItemEnergy.room(target)),
				Config.batteryTransferPerUse);
		if (move <= 0) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
						Component.translatable("item.alaindustrial.battery.nothing_to_move")
								.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel) {
			ItemEnergy.add(battery, -move);
			ItemEnergy.add(target, move);
		}
		// A metallic electrical click, the same vocabulary the electromagnet uses for a powered device;
		// plays as the local player's own prediction.
		player.playSound(SoundEvents.COPPER_BULB_TURN_ON, 0.7F, 1.2F);
		return InteractionResult.SUCCESS;
	}

	// --- item bar: the charge OF ONE battery, in the LV tier colour ---

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return ItemEnergy.get(stack) > 0;
	}

	/**
	 * The bar shows the charge of a single battery, not of the stack. Every battery in a stack holds the
	 * same charge (that is what makes them stack at all), so one bar describes all of them.
	 */
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
