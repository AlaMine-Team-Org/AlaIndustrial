package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

/**
 * L2 suite for the machine upgrade slots + mute chip (MOD-080). Exercises the common code shared by
 * both loaders: the four upgrade slots appended to every GUI machine, {@code isMuted()}, NBT
 * persistence of an installed chip, hopper exclusion, drop-on-break (including a formerly slotless
 * machine), and the {@link MaceratorMenu} slot filter / quick-move behaviour.
 */
public class MuteChipGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/** Container index of the active (mute) upgrade slot: the first slot appended after the base slots. */
	private static int activeUpgradeSlot(MachineBlockEntity be) {
		return be.upgradeSlotStart() + MachineBlockEntity.ACTIVE_UPGRADE_INDEX;
	}

	/** isMuted() is false empty and with an empty chip, true only with a mute chip in the active slot. */
	@GameTest
	public void muteChip_isMutedReflectsActiveSlot(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModBlocks.MACERATOR);
		if (be.isMuted()) {
			helper.fail("machine reports muted with no chip installed");
		}
		be.setItem(activeUpgradeSlot(be), new ItemStack(ModContent.EMPTY_CHIP.get()));
		if (be.isMuted()) {
			helper.fail("empty chip must not mute");
		}
		be.setItem(activeUpgradeSlot(be), new ItemStack(ModContent.MUTE_CHIP.get()));
		if (!be.isMuted()) {
			helper.fail("mute chip in the active slot must mute");
		}
		helper.succeed();
	}

	/** An installed mute chip survives an NBT save/load round-trip (ContainerHelper covers the tail slots). */
	@GameTest
	public void muteChip_persistsAcrossNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.MACERATOR);
		MaceratorBlockEntity src = helper.getBlockEntity(POS, MaceratorBlockEntity.class);
		src.setItem(activeUpgradeSlot(src), new ItemStack(ModContent.MUTE_CHIP.get()));

		CompoundTag tag = src.saveCustomOnly(registries);
		MaceratorBlockEntity restored = new MaceratorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (!restored.isMuted()) {
			helper.fail("mute chip lost across the NBT round-trip");
		}
		helper.succeed();
	}

	/** Hoppers/pipes cannot see the upgrade slots: they are absent from getSlotsForFace and both face checks. */
	@GameTest
	public void muteChip_hoppersCannotReachUpgradeSlots(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModBlocks.MACERATOR);
		int slot = activeUpgradeSlot(be);
		for (Direction d : Direction.values()) {
			for (int s : be.getSlotsForFace(d)) {
				if (s >= be.upgradeSlotStart()) {
					helper.fail("getSlotsForFace(" + d + ") exposes upgrade slot " + s);
				}
			}
		}
		ItemStack chip = new ItemStack(ModContent.MUTE_CHIP.get());
		if (be.canPlaceItemThroughFace(slot, chip, Direction.NORTH)) {
			helper.fail("automation can insert into an upgrade slot");
		}
		if (be.canTakeItemThroughFace(slot, chip, Direction.NORTH)) {
			helper.fail("automation can extract from an upgrade slot");
		}
		helper.succeed();
	}

	/** Breaking a machine drops its installed chip exactly once. */
	@GameTest
	public void muteChip_dropsOnBreak(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModBlocks.MACERATOR);
		be.setItem(activeUpgradeSlot(be), new ItemStack(ModContent.MUTE_CHIP.get()));
		assertChipDropped(helper, be);
	}

	/**
	 * A machine that had no slots before MOD-080 (the water mill, base size 0) now carries upgrade slots
	 * and drops an installed chip on break — proving the size-0 → size-4 transition is handled.
	 */
	@GameTest
	public void muteChip_dropsFromFormerlySlotlessMachine(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModBlocks.WATER_MILL);
		if (!be.hasUpgradeSlots()) {
			helper.fail("water mill did not receive upgrade slots");
		}
		be.setItem(activeUpgradeSlot(be), new ItemStack(ModContent.MUTE_CHIP.get()));
		assertChipDropped(helper, be);
	}

	private static void assertChipDropped(GameTestHelper helper, MachineBlockEntity be) {
		BlockPos abs = be.getBlockPos();
		Containers.dropContents(helper.getLevel(), abs, be);
		AABB box = new AABB(abs.getX() - 2, abs.getY() - 2, abs.getZ() - 2,
				abs.getX() + 3, abs.getY() + 3, abs.getZ() + 3);
		int dropped = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (e.getItem().is(ModContent.MUTE_CHIP.get())) {
				dropped += e.getItem().getCount();
			}
		}
		if (dropped != 1) {
			helper.fail("expected exactly 1 mute chip dropped, got " + dropped);
		}
		helper.succeed();
	}

	/**
	 * Menu-level filter + quick-move (MOD-080): the active upgrade slot accepts only a mute chip, the
	 * locked slots accept nothing, a mute chip shift-clicked from the inventory installs into the active
	 * slot (exactly one), and a non-chip item never lands in an upgrade slot.
	 */
	@GameTest
	public void muteChip_menuSlotFilterAndQuickMove(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModBlocks.MACERATOR);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MaceratorMenu menu = new MaceratorMenu(1, player.getInventory(), be,
				ContainerLevelAccess.create(helper.getLevel(), be.getBlockPos()));

		int base = be.upgradeSlotStart(); // first upgrade slot's menu index too (base slots come first)
		Slot active = menu.getSlot(base);
		Slot locked = menu.getSlot(base + 1);
		ItemStack mute = new ItemStack(ModContent.MUTE_CHIP.get());
		ItemStack empty = new ItemStack(ModContent.EMPTY_CHIP.get());

		if (!active.mayPlace(mute)) {
			helper.fail("active upgrade slot rejects a mute chip");
		}
		if (active.mayPlace(empty)) {
			helper.fail("active upgrade slot wrongly accepts an empty chip");
		}
		if (locked.mayPlace(mute)) {
			helper.fail("a locked upgrade slot wrongly accepts a mute chip");
		}

		// Shift-clicking a mute chip from the inventory installs exactly one into the active slot (#10).
		int firstInvSlot = base + MachineBlockEntity.UPGRADE_SLOT_COUNT;
		menu.getSlot(firstInvSlot).set(new ItemStack(ModContent.MUTE_CHIP.get(), 3));
		menu.quickMoveStack(player, firstInvSlot);
		ItemStack installed = menu.getSlot(base).getItem();
		if (!installed.is(ModContent.MUTE_CHIP.get()) || installed.getCount() != 1) {
			helper.fail("shift-click did not install exactly one mute chip: " + installed);
		}

		// A non-chip item shift-clicked from the inventory must NOT disturb the upgrade slot.
		int secondInvSlot = firstInvSlot + 1;
		menu.getSlot(secondInvSlot).set(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
		menu.quickMoveStack(player, secondInvSlot);
		ItemStack afterNonChip = menu.getSlot(base).getItem();
		if (!afterNonChip.is(ModContent.MUTE_CHIP.get()) || afterNonChip.getCount() != 1) {
			helper.fail("a non-chip item disturbed the upgrade slot: " + afterNonChip);
		}
		helper.succeed();
	}

	/**
	 * Both chip crafting recipes resolve and yield the right item (guards against a silently skipped
	 * recipe): empty_chip = glass/copper_coil/electronic_circuit; mute_chip = any-wool + empty_chip.
	 */
	@GameTest
	public void muteChip_craftingRecipesResolve(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager recipes = level.getServer().getRecipeManager();

		ItemStack glass = new ItemStack(Items.GLASS);
		ItemStack coil = new ItemStack(ModContent.COPPER_COIL.get());
		ItemStack circuit = new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get());
		CraftingInput emptyInput = CraftingInput.of(3, 3, List.of(
				glass, glass, glass,
				coil, circuit, coil,
				glass, glass, glass));
		RecipeHolder<CraftingRecipe> emptyRecipe =
				recipes.getRecipeFor(RecipeType.CRAFTING, emptyInput, level).orElse(null);
		if (emptyRecipe == null) {
			helper.fail("empty_chip crafting recipe did not resolve");
			return;
		}
		ItemStack emptyOut = emptyRecipe.value().assemble(emptyInput);
		if (!emptyOut.is(ModContent.EMPTY_CHIP.get())) {
			helper.fail("empty_chip recipe produced " + emptyOut + " (expected empty_chip)");
		}

		ItemStack emptyChip = new ItemStack(ModContent.EMPTY_CHIP.get());
		// Any wool works (recipe uses the #minecraft:wool tag); white wool has no flat Items constant in
		// 26.2 (colours live in a ColorCollection), so look it up by id.
		ItemStack wool = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_wool")));
		CraftingInput muteInput = CraftingInput.of(3, 3, List.of(
				wool, wool, wool,
				wool, emptyChip, wool,
				wool, wool, wool));
		RecipeHolder<CraftingRecipe> muteRecipe =
				recipes.getRecipeFor(RecipeType.CRAFTING, muteInput, level).orElse(null);
		if (muteRecipe == null) {
			helper.fail("mute_chip crafting recipe did not resolve");
			return;
		}
		ItemStack muteOut = muteRecipe.value().assemble(muteInput);
		if (!muteOut.is(ModContent.MUTE_CHIP.get())) {
			helper.fail("mute_chip recipe produced " + muteOut + " (expected mute_chip)");
		}
		helper.succeed();
	}
}
