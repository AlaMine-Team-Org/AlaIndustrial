package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity.Channel;
import dev.alaindustrial.core.guide.ArchiveRecord;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.ReceptionCapsuleRules;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Material;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the reception capsule (MOD-833): one slot per material of the price, the player's inventory, and the
 * channels that tell the screen why the capsule stands still and how much is missing — computed on the server
 * ({@link ReceptionCapsuleBlockEntity.Channel}). No upgrade block, matching the {@code NoUpgradePanel} marker.
 */
public class ReceptionCapsuleMenu extends MachineMenu {

	/**
	 * Item position of the first material slot; the next ones follow every {@link #SLOT_STEP} pixels DOWN — one row
	 * per material, so each row has room for the material's name and its count (MOD-844). Must match the atlas
	 * generator {@code tools/gen_reception_capsule_gui.py}.
	 */
	public static final int SLOTS_X = 35;
	public static final int SLOTS_Y = 63;
	public static final int SLOT_STEP = 19;

	/** The window is taller than the standard 166 (MOD-844); the player's inventory moves down with it. */
	public static final int IMAGE_HEIGHT = 234;
	private static final int INVENTORY_Y = 152;
	private static final int HOTBAR_Y = 210;

	/** Button id of the choice between a new worker and the town's dead who wait to come back (R39). */
	public static final int BUTTON_NEXT_CHOICE = 0;

	/** Server side: the capsule the choice button turns; null on the client. */
	@Nullable
	private final ReceptionCapsuleBlockEntity capsule;

	/** Server side. */
	public ReceptionCapsuleMenu(int syncId, Inventory playerInventory, ReceptionCapsuleBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.RECEPTION_CAPSULE_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.RECEPTION_CAPSULE.get());
		this.capsule = be;
	}

	/** Client side: the three material slots and no upgrade block. */
	public ReceptionCapsuleMenu(int syncId, Inventory playerInventory) {
		super(ModContent.RECEPTION_CAPSULE_MENU.get(), syncId, playerInventory,
				clientStub(ReceptionCapsuleBlockEntity.SLOT_COUNT, ReceptionCapsuleBlockEntity.DATA_COUNT),
				ModContent.RECEPTION_CAPSULE.get());
		this.capsule = null;
	}

	/** The choice button: the capsule moves to the next choice of whom to restore (R39). */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != BUTTON_NEXT_CHOICE || capsule == null || !(player instanceof ServerPlayer)) {
			return super.clickMenuButton(player, buttonId);
		}
		capsule.nextChoice();
		return true;
	}

	/**
	 * One slot per material, each taking only its own — the predicate the block entity enforces for hoppers and
	 * pipes, held here for the hand (neither layer covers the other).
	 */
	@Override
	protected void addMachineSlots() {
		Container container = machine;
		for (Material material : Material.values()) {
			int slot = ReceptionCapsuleBlockEntity.slotOf(material);
			addSlot(new Slot(container, slot, SLOTS_X, SLOTS_Y + slot * SLOT_STEP) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.is(ReceptionCapsuleBlockEntity.itemOf(material));
				}
			});
		}
	}

	@Override
	protected int playerInventoryY() {
		return INVENTORY_Y;
	}

	@Override
	protected int hotbarY() {
		return HOTBAR_Y;
	}

	/** EU in the buffer: two channels joined (see {@link ReceptionCapsuleBlockEntity.Channel}). */
	@Override
	public int getEnergy() {
		return (int) Math.min(Integer.MAX_VALUE, ReceptionCapsuleBlockEntity.join(channel(Channel.ENERGY_HI),
				channel(Channel.ENERGY)));
	}

	/** The buffer's size: two channels joined. */
	@Override
	public int getCapacity() {
		return (int) Math.min(Integer.MAX_VALUE, ReceptionCapsuleBlockEntity.join(channel(Channel.CAPACITY_HI),
				channel(Channel.CAPACITY)));
	}

	/** EU the next worker costs. */
	public long getEuPrice() {
		return ReceptionCapsuleBlockEntity.join(channel(Channel.PRICE_HI), channel(Channel.PRICE_LO));
	}

	/** EU of the price the buffer does not hold yet. */
	public long getEuMissing() {
		return ReceptionCapsuleRules.euMissing(getEnergy(), getEuPrice());
	}

	/** Why the capsule stands still, or {@code READY}. */
	public ReceptionCapsuleRules.Verdict getVerdict() {
		return ReceptionCapsuleRules.Verdict.byOrdinal(channel(Channel.VERDICT));
	}

	/** The town's living workers. */
	public int getLiving() {
		return channel(Channel.LIVING);
	}

	/** How many workers the town's tier allows. */
	public int getLimit() {
		return channel(Channel.LIMIT);
	}

	/** How many of {@code material} the next worker costs. */
	public int getNeed(Material material) {
		return channel(switch (material) {
			case CIRCUIT -> Channel.NEED_CIRCUITS;
			case RUBBER -> Channel.NEED_RUBBER;
			case COPPER_PLATE -> Channel.NEED_COPPER_PLATES;
		});
	}

	/** How many of the town's dead wait to come back (R39); 0 with permadeath on. */
	public int getAwaiting() {
		return channel(Channel.AWAITING);
	}

	/**
	 * The dead worker chosen to come back, as {@code <letter>-<NNN>}, or empty — a new worker. Built by hand from
	 * the letter and the digits the channels carry, as the archive writes it (no locale's digits).
	 */
	public Optional<String> getChoice() {
		int letter = channel(Channel.CHOICE_LETTER);
		if (letter <= 0 || letter > ArchiveRecord.ALPHABET.length()) {
			return Optional.empty();
		}
		int digits = Math.max(0, Math.min(999, channel(Channel.CHOICE_DIGITS)));
		return Optional.of(new String(new char[] {ArchiveRecord.ALPHABET.charAt(letter - 1), '-',
				(char) ('0' + digits / 100), (char) ('0' + digits / 10 % 10), (char) ('0' + digits % 10)}));
	}

	/** How many of {@code material} lie in its slot. */
	public int getHave(Material material) {
		ItemStack stack = machine.getItem(ReceptionCapsuleBlockEntity.slotOf(material));
		return stack.is(ReceptionCapsuleBlockEntity.itemOf(material)) ? stack.getCount() : 0;
	}
}
