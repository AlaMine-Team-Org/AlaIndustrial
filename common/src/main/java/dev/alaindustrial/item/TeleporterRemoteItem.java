package dev.alaindustrial.item;

import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.teleporter.TeleportEngine;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Teleporter Remote (MOD-093) — the "get me home" item.
 *
 * <p>Shift-right-click a station to add it to the remote's list; right-click in the air to open the
 * list and pick where to go. The <b>target station</b> pays the EU, never the remote — which is why
 * the remote holds no charge and needs none.
 *
 * <p>The remote binds to whoever first uses it and is useless to anyone else: a stolen remote does
 * not lead a thief to your base.
 */
public class TeleporterRemoteItem extends Item {
	public TeleporterRemoteItem(Properties properties) {
		super(properties);
	}

	/**
	 * Add the clicked station to the list.
	 *
	 * <p>Refusals happen here, at the click, rather than at jump time: binding a station the player
	 * may not use would only pay off with a failure later. Re-binding a station already on the list
	 * is a no-op with a word about it, so a player who clicks twice does not end up with two
	 * identical rows to tell apart.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		if (!claimOwnership(stack, player)) {
			return deny(player, TeleportEngine.Denial.NOT_YOUR_REMOTE);
		}
		BlockPos pos = context.getClickedPos();
		if (!(level.getBlockEntity(pos) instanceof TeleporterBlockEntity station)) {
			// Not a station: open the list anyway. The remote's whole job is "get me home", and home is
			// usually asked for from inside a mine, where there is no sky to click at — requiring empty
			// air made the item hard to use exactly where it matters. Safe to do here: vanilla runs the
			// block's own useItemOn first (ServerPlayerGameMode:367), so chests and machines still open
			// normally and this only ever fires on a block with nothing to say.
			openList(player);
			return InteractionResult.SUCCESS;
		}
		if (!station.allowsAccess(player.getUUID())) {
			return deny(player, TeleportEngine.Denial.NO_ACCESS);
		}

		TeleportPoints points = stack.getOrDefault(ModDataComponents.TELEPORTER_POINTS.get(), TeleportPoints.EMPTY);
		if (points.indexOf(level.dimension(), pos) >= 0) {
			player.sendSystemMessage(Component.translatable("alaindustrial.teleporter.already_bound")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.SUCCESS;
		}
		if (points.isFull()) {
			player.sendSystemMessage(Component.translatable("alaindustrial.teleporter.list_full", points.size())
					.withStyle(ChatFormatting.RED), true);
			return InteractionResult.SUCCESS;
		}

		// No name: the point is auto-named "Teleporter N" until the player picks something. Storing an
		// empty name rather than a formatted one is what keeps that default translatable — see
		// TeleportPoint#displayName.
		TeleportPoint point = new TeleportPoint(level.dimension(), pos, "", points.nextFreeNumber());
		stack.set(ModDataComponents.TELEPORTER_POINTS.get(), points.with(point));
		player.sendSystemMessage(Component.translatable("alaindustrial.teleporter.bound", point.displayName())
				.withStyle(ChatFormatting.GREEN), true);
		// A short confirmation chime at the station — binding is otherwise a silent click, and the
		// action-bar line alone is easy to miss while looking at the block. Vanilla's portal-frame
		// fill: a "locked in" click that already reads as teleport-flavoured, so it needs no new asset.
		level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.6f, 1.4f);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Open the list.
	 *
	 * <p>Works from either hand: the menu resolves the stack through {@link #heldRemote}, so an
	 * off-hand remote opens a screen pointed at itself rather than at nothing. Nothing about the stack
	 * travels through the open-packet — that would need a different API on each loader.
	 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		openList(serverPlayer);
		return InteractionResult.SUCCESS;
	}

	/** Claim the remote and show its list; the one path both right-click routes end at. */
	private static void openList(ServerPlayer player) {
		ItemStack stack = heldRemote(player);
		if (stack.isEmpty()) {
			return;
		}
		if (!claimOwnership(stack, player)) {
			deny(player, TeleportEngine.Denial.NOT_YOUR_REMOTE);
			return;
		}
		player.openMenu(new SimpleMenuProvider(
				(syncId, inventory, p) -> new TeleporterRemoteMenu(syncId, inventory),
				Component.translatable("item.alaindustrial.teleporter_remote")));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		TeleportPoints points = stack.getOrDefault(ModDataComponents.TELEPORTER_POINTS.get(), TeleportPoints.EMPTY);
		if (points.isEmpty()) {
			adder.accept(Component.translatable("tooltip.alaindustrial.teleporter_remote.unbound")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		adder.accept(Component.translatable("tooltip.alaindustrial.teleporter_remote.count", points.size())
				.withStyle(ChatFormatting.GRAY));
	}

	/**
	 * The remote the player is holding, main hand first, or empty if neither hand has one.
	 *
	 * <p>The single answer to "which stack is this screen about". Everything that reads the remote —
	 * the menu, the jump's final re-check — goes through here, because they must all agree: a jump
	 * started from the off-hand and then re-checked against the main hand would be refused as somebody
	 * else's remote at the last moment.
	 */
	public static ItemStack heldRemote(Player player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof TeleporterRemoteItem) {
			return main;
		}
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		return off.getItem() instanceof TeleporterRemoteItem ? off : ItemStack.EMPTY;
	}

	/**
	 * Take ownership on first use; afterwards only the owner gets anywhere.
	 *
	 * @return true if this player may use the remote.
	 */
	private static boolean claimOwnership(ItemStack stack, ServerPlayer player) {
		UUID owner = stack.get(ModDataComponents.TELEPORTER_OWNER.get());
		if (owner == null) {
			stack.set(ModDataComponents.TELEPORTER_OWNER.get(), player.getUUID());
			return true;
		}
		return owner.equals(player.getUUID());
	}

	private static InteractionResult deny(ServerPlayer player, TeleportEngine.Denial denial) {
		player.sendSystemMessage(denial.message().copy().withStyle(ChatFormatting.RED), true);
		return InteractionResult.SUCCESS;
	}
}
