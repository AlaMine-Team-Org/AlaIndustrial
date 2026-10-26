package dev.alaindustrial.block.entity;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.MostHeldKinds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The warehouse shelf (MOD-885, epic MOD-824 R157): a chest of {@value #CONTAINER_SIZE} slots that shows what it
 * holds the most of on its four bin cards.
 *
 * <p><b>Inside it is an ordinary chest.</b> Built on the vanilla {@link BaseContainerBlockEntity} like the storage
 * module and the mod's chests, it opens the vanilla three-row chest window, and both loaders' item capability comes
 * from {@link net.minecraft.world.Container} through {@code BlockCapabilityRoster}, so hoppers, item pipes and later
 * the town's storekeeper put and take like at any chest. It has no energy and no tick.
 *
 * <p><b>The cards are worked out, not remembered.</b> Every change of the contents ({@link #setChanged}) ranks the
 * kinds held ({@link MostHeldKinds}, a kind = same item and same components) and keeps the {@value #CARDS} biggest.
 * Only when that list differs from what the clients were last told does the server send it — in the update tag,
 * which carries the card list and never the contents. The client draws the list as it got it; nothing is stored on
 * disk beyond the slots, and a loaded shelf works its list out again from them.
 */
public class WarehouseShelfBlockEntity extends BaseContainerBlockEntity {

	/** Three rows of nine, like a single chest. */
	public static final int CONTAINER_SIZE = 27;
	/** Bin cards on the shelf's front, so kinds shown at most. */
	public static final int CARDS = 4;

	private static final String KEY_CARDS = "cards";

	private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
	/** The kinds on the cards, biggest first, each a one-item copy; on the client, as the server last sent it. */
	private List<ItemStack> cards = List.of();
	/** Set while {@link #getUpdateTag} writes: the save then carries the cards instead of the slots. */
	private boolean forClient;

	public WarehouseShelfBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.WAREHOUSE_SHELF_BE.get(), pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.alaindustrial.warehouse_shelf");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return CONTAINER_SIZE;
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return ChestMenu.threeRows(syncId, playerInventory, this);
	}

	/** The kinds shown on the cards, biggest first; fewer than {@value #CARDS} when the shelf holds fewer kinds. */
	public List<ItemStack> cards() {
		return cards;
	}

	/** The {@value #CARDS} kinds {@code slots} hold the most of, as one-item copies. */
	public static List<ItemStack> rank(List<ItemStack> slots) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack kind : MostHeldKinds.top(slots, ItemStack::isEmpty, ItemStack::getCount,
				ItemStack::isSameItemSameComponents, CARDS)) {
			out.add(kind.copyWithCount(1));
		}
		return List.copyOf(out);
	}

	// --- change tracking and the client picture ---

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide()) {
			List<ItemStack> now = rank(items);
			if (!sameCards(now, cards)) {
				cards = now;
				BlockState state = getBlockState();
				level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
			}
		}
	}

	private static boolean sameCards(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this,
				(be, access) -> ((WarehouseShelfBlockEntity) be).getUpdateTag(access));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		forClient = true;
		try {
			return saveCustomOnly(provider);
		} finally {
			forClient = false;
		}
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (forClient) {
			output.store(KEY_CARDS, ItemStack.CODEC.listOf(), cards);
		} else {
			ContainerHelper.saveAllItems(output, items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		// The client gets the cards in the update tag; a save on disk has only the slots, the cards follow from them.
		cards = input.read(KEY_CARDS, ItemStack.CODEC.listOf()).map(List::copyOf).orElseGet(() -> rank(items));
	}
}
