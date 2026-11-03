package dev.alaindustrial.item.misc;

import dev.alaindustrial.block.ReactorAntennaMastBlock;
import dev.alaindustrial.block.entity.ReactorAntennaBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.reactor.ReactorTabletLink;
import dev.alaindustrial.client.screen.reactor.ReactorConsole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.EnergyBar;
import dev.alaindustrial.item.energy.ItemBuffers;
import dev.alaindustrial.item.energy.PoweredItem;
import dev.alaindustrial.item.energy.PoweredToolTooltip;
import dev.alaindustrial.menu.ReactorTabletMenu;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Reactor tablet, tier 1 (MOD-941) — a view of one reactor from across the base.
 *
 * <p>Shift-right-click a reactor antenna to bind the tablet to that antenna's reactor; right-click anywhere to open
 * the reactor's panel, view-only ({@link ReactorTabletMenu}). It hears the reactor within
 * {@code reactorTabletRange} blocks of a powered antenna, in the same dimension; anywhere else the panel opens to
 * interference and says why.
 *
 * <p><b>The tablet runs on its own charge.</b> It spends a little EU while its screen is open and more while the
 * reactor runs hot, so tier 1 is a device the player keeps charged; a later antenna upgrade may charge it over the
 * air (MOD-947). It binds to one reactor; a second shift-click rebinds it.
 */
public class ReactorTabletItem extends Item implements PoweredItem {

	public ReactorTabletItem(Properties properties) {
		super(properties);
	}

	/**
	 * Binds the tablet to the reactor whose antenna was clicked, or opens the panel when the block is not an
	 * antenna — a tablet is used from inside a base, where there is rarely open sky to click at. Vanilla runs the
	 * block's own use first, so machines and chests still open normally.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)
				|| !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = context.getClickedPos();
		// The boom is the part of the antenna the player sees first: a click on it is a click on the antenna.
		BlockState clickedState = level.getBlockState(clicked);
		BlockPos antennaPos = clickedState.getBlock() instanceof ReactorAntennaMastBlock
				? ReactorAntennaMastBlock.antennaOf(clicked, clickedState) : clicked;
		if (!player.isSecondaryUseActive()
				|| !(level.getBlockEntity(antennaPos) instanceof ReactorAntennaBlockEntity antenna)) {
			openPanel(player);
			return InteractionResult.SUCCESS;
		}
		BlockPos controller = antenna.linkedController(level.getGameTime());
		if (controller == null) {
			player.sendSystemMessage(Component.translatable("alaindustrial.reactor_tablet.antenna_unlinked")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResult.SUCCESS;
		}
		context.getItemInHand().set(ModDataComponents.REACTOR_TABLET_LINK.get(),
				GlobalPos.of(level.dimension(), controller));
		player.sendSystemMessage(Component.translatable("alaindustrial.reactor_tablet.bound",
				controller.getX(), controller.getY(), controller.getZ()).withStyle(ChatFormatting.GREEN), true);
		// The same "locked in" click the teleporter remote plays when it binds a station.
		level.playSound(null, clicked, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.6f, 1.4f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			openPanel(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	private static void openPanel(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((syncId, inventory, p) -> ReactorTabletMenu.open(syncId, inventory),
				Component.translatable("item.alaindustrial.reactor_tablet")));
	}

	/** Ticks between two alarm checks: once a second, like the tablet's charge debit. */
	private static final int ALARM_CHECK_TICKS = 20;

	/**
	 * Marks the tablet while the reactor it hears sounds its red alarm, so the lamps on the item pulse red in the
	 * hotbar and in hand. "Hears" is the screen's own link — a powered antenna in range, a charged tablet — and
	 * "alarm" the panel's own rule ({@link ReactorConsole#isAlarm}), so the item never disagrees with the screen.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (entity instanceof Player player && level.getGameTime() % ALARM_CHECK_TICKS == 0L) {
			refreshAlarm(stack, player);
		}
	}

	/** One alarm check: sets the mark while the reactor this tablet hears is in alarm, clears it otherwise. */
	public static void refreshAlarm(ItemStack stack, Player player) {
		ReactorTabletLink.Result link = ReactorTabletLink.resolve(player, stack);
		ReactorControllerBlockEntity controller = link.controller();
		boolean alarm = link.state() == ReactorTabletLink.State.LINKED && controller != null
				&& ReactorConsole.isAlarm(ReactorConsole.readout(controller.getDataAccess()::get));
		if (alarm != stack.has(ModDataComponents.REACTOR_TABLET_ALARM.get())) {
			if (alarm) {
				stack.set(ModDataComponents.REACTOR_TABLET_ALARM.get(), Unit.INSTANCE);
			} else {
				stack.remove(ModDataComponents.REACTOR_TABLET_ALARM.get());
			}
		}
	}

	/**
	 * The tablet the player holds, main hand first, or empty. The one answer to "which tablet is this screen about",
	 * so the menu, the link and the debit all read the same stack.
	 */
	public static ItemStack heldTablet(Player player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof ReactorTabletItem) {
			return main;
		}
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		return off.getItem() instanceof ReactorTabletItem ? off : ItemStack.EMPTY;
	}

	// --- charge: the item bar in the LV colour, numbers in the tooltip ---

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

	@Override
	public long energyCapacity(ItemStack stack, ItemBuffers buffers) {
		return buffers.reactorTabletBuffer();
	}

	@Override
	public long energyInputRate(ItemStack stack) {
		return ToolConfig.reactorTabletInputRate;
	}

	/** What it does, which reactor it is bound to, then the charge. */
	@Override
	public PoweredToolTooltip toolTooltip() {
		return PoweredToolTooltip.of("reactor_tablet", List.of()).withBeforeCharge(ReactorTabletItem::boundLine)
				.withBeforeCharge(ReactorTabletItem::bindHint);
	}

	private static MachineTooltipSpec.Line boundLine(ItemStack stack) {
		GlobalPos bound = stack.get(ModDataComponents.REACTOR_TABLET_LINK.get());
		if (bound == null) {
			return MachineTooltipSpec.text("tooltip.alaindustrial.reactor_tablet.unbound",
					MachineTooltipSpec.Tone.GRAY);
		}
		BlockPos pos = bound.pos();
		return new MachineTooltipSpec.Line("tooltip.alaindustrial.reactor_tablet.bound",
				List.of(pos::getX, pos::getY, pos::getZ), MachineTooltipSpec.Tone.GRAY,
				MachineTooltipSpec.Shown.ALWAYS, null);
	}

	/** How to bind, on a line of its own under "Not bound"; nothing once the tablet is bound. */
	private static MachineTooltipSpec.@Nullable Line bindHint(ItemStack stack) {
		return stack.has(ModDataComponents.REACTOR_TABLET_LINK.get()) ? null
				: MachineTooltipSpec.text("tooltip.alaindustrial.reactor_tablet.unbound_hint",
						MachineTooltipSpec.Tone.DARK_GRAY);
	}
}
