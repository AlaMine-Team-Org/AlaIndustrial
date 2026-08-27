package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
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
public final class MuteChipScenarios {

	private MuteChipScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/** Container index of the mute arm: the first slot appended after the base slots. */
	private static int activeUpgradeSlot(MachineBlockEntity be) {
		return be.upgradeSlotStart() + MachineBlockEntity.ACTIVE_UPGRADE_INDEX;
	}

	/** Panel arm that takes overclockers (MOD-393) — index 1, the top arm. */
	private static final int OVERCLOCK_ARM = 1;

	// --- Overclocker chip (MOD-392) ---

	/**
	 * A macerator with a tier-II chip runs the operation at 0.8² of its length and draws 2² the EU — and
	 * the energy it actually spends on the operation goes UP, which is the whole point of the upgrade.
	 *
	 * <p>Measures the real drain from the buffer rather than trusting the config, so a chip that
	 * shortened the run without charging for it would fail here even if the numbers looked right.
	 */
	public static void overclocker_speedsUpAndCostsMore(GameTestHelper helper) {
		MaceratorBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get(),
				MaceratorBlockEntity.class);
		int plain = be.effectiveDuration(dev.alaindustrial.Config.maceratorDuration);
		int plainDraw = be.effectiveEuPerTick(dev.alaindustrial.Config.machineEuPerTick);

		be.setItem(be.upgradeSlotStart() + OVERCLOCK_ARM,
				new ItemStack(ModContent.OVERCLOCKER_CHIP_II.get()));
		if (be.overclockerCount() != 2) {
			helper.fail("expected tier 2 in effect, got " + be.overclockerCount());
		}
		int fast = be.effectiveDuration(dev.alaindustrial.Config.maceratorDuration);
		int fastDraw = be.effectiveEuPerTick(dev.alaindustrial.Config.machineEuPerTick);

		if (fast >= plain) {
			helper.fail("tier II did not shorten the operation: " + plain + " -> " + fast);
		}
		if (fastDraw <= plainDraw) {
			helper.fail("tier II did not raise the draw: " + plainDraw + " -> " + fastDraw);
		}
		// E_op must RISE — speed bought with energy, not a free lunch (and not merely energy-neutral,
		// which is what globalMachineSpeedMultiplier already does).
		long plainOp = (long) plain * plainDraw;
		long fastOp = (long) fast * fastDraw;
		if (fastOp <= plainOp) {
			helper.fail("energy per operation must grow with the tier: " + plainOp + " -> " + fastOp);
		}
		helper.succeed();
	}

	/**
	 * The cap follows the machine's tier, not a flat number: an alloy smelter draws 8 EU/t, so on LV
	 * (32 EU/t) it can use only two chips however many the player stuffs into the panel. Without this
	 * the third chip would demand 64 EU/t on a tier that can never deliver it, and the machine would
	 * starve while the player believed they had bought speed.
	 */
	public static void overclocker_tierCapsTheChips(GameTestHelper helper) {
		MachineBlockEntity smelter = AlaGameTestHelper.place(helper, POS, ModContent.ALLOY_SMELTER.get());
		if (smelter.overclockerCap() != 2) {
			helper.fail("alloy smelter (8 EU/t on LV) should cap at tier 2, got " + smelter.overclockerCap());
		}
		smelter.setItem(smelter.upgradeSlotStart() + OVERCLOCK_ARM,
				new ItemStack(ModContent.OVERCLOCKER_CHIP_III.get()));
		if (smelter.overclockerCount() != 2) {
			helper.fail("a tier-III chip must still act as tier 2 here, got " + smelter.overclockerCount());
		}
		int draw = smelter.effectiveEuPerTick(dev.alaindustrial.Config.alloySmelterEuPerTick);
		if (draw > dev.alaindustrial.core.energy.EnergyTier.LV.maxVoltage()) {
			helper.fail("capped draw " + draw + " still exceeds the LV tier ceiling");
		}
		helper.succeed();
	}

	/**
	 * A generator has the same four panel slots (every MenuProvider does) but must not be overclockable:
	 * its output is governed by a different knob entirely, so a chip there would be a dead upgrade.
	 */
	public static void overclocker_generatorIsNotOverclockable(GameTestHelper helper) {
		MachineBlockEntity solar = AlaGameTestHelper.place(helper, POS, ModContent.SOLAR_PANEL.get());
		if (solar.supportsOverclock()) {
			helper.fail("a solar panel must not advertise overclocking");
		}
		solar.setItem(solar.upgradeSlotStart() + OVERCLOCK_ARM,
				new ItemStack(ModContent.OVERCLOCKER_CHIP_III.get()));
		if (solar.overclockerCap() != 0 || solar.overclockerCount() != 0) {
			helper.fail("chips must not count in a generator: cap=" + solar.overclockerCap()
					+ " tier=" + solar.overclockerCount());
		}
		helper.succeed();
	}

	/** isMuted() is false empty and with an empty chip, true only with a mute chip in the active slot. */
	public static void muteChip_isMutedReflectsActiveSlot(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get());
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
		// An overclocker is not a mute chip: the panel must not go silent just because SOME upgrade sits
		// in it. (Which arm accepts what is covered by the menu-filter scenario.)
		be.setItem(activeUpgradeSlot(be), ItemStack.EMPTY);
		be.setItem(be.upgradeSlotStart() + OVERCLOCK_ARM,
				new ItemStack(ModContent.OVERCLOCKER_CHIP_I.get()));
		if (be.isMuted()) {
			helper.fail("an overclocker must not silence the machine");
		}
		helper.succeed();
	}

	/** An installed mute chip survives an NBT save/load round-trip (ContainerHelper covers the tail slots). */
	public static void muteChip_persistsAcrossNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.MACERATOR.get());
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
	public static void muteChip_hoppersCannotReachUpgradeSlots(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get());
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
	public static void muteChip_dropsOnBreak(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get());
		be.setItem(activeUpgradeSlot(be), new ItemStack(ModContent.MUTE_CHIP.get()));
		assertChipDropped(helper, be);
	}

	/**
	 * The water mill carries its wheel component before the appended upgrade block and still drops
	 * an installed chip from the correct active-upgrade index.
	 */
	public static void muteChip_dropsFromFormerlySlotlessMachine(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.WATER_MILL.get());
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
	 * Menu-level filter + quick-move (MOD-080, typed per arm in MOD-393): each arm takes only its own
	 * kind of upgrade, the reserved arms take nothing, a mute chip shift-clicked from the inventory
	 * installs exactly one, and a non-chip item never lands in an upgrade slot.
	 */
	public static void muteChip_menuSlotFilterAndQuickMove(GameTestHelper helper) {
		MachineBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get());
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		MaceratorMenu menu = new MaceratorMenu(1, player.getInventory(), be,
				ContainerLevelAccess.create(helper.getLevel(), be.getBlockPos()));

		int base = be.upgradeSlotStart(); // first upgrade slot's menu index too (base slots come first)
		ItemStack mute = new ItemStack(ModContent.MUTE_CHIP.get());
		ItemStack overclocker = new ItemStack(ModContent.OVERCLOCKER_CHIP_I.get());
		ItemStack empty = new ItemStack(ModContent.EMPTY_CHIP.get());

		// Each arm is typed: mute goes left, overclockers go top, the other two take nothing yet. This
		// is what caps overclocking at one chip per machine without a separate rule.
		Slot muteArm = menu.getSlot(base + MachineBlockEntity.ACTIVE_UPGRADE_INDEX);
		Slot overclockArm = menu.getSlot(base + OVERCLOCK_ARM);
		if (!muteArm.mayPlace(mute) || muteArm.mayPlace(overclocker) || muteArm.mayPlace(empty)) {
			helper.fail("mute arm must take the mute chip and nothing else");
		}
		if (!overclockArm.mayPlace(overclocker) || overclockArm.mayPlace(mute)) {
			helper.fail("overclock arm must take an overclocker and nothing else");
		}
		for (int i = 2; i < MachineBlockEntity.UPGRADE_SLOT_COUNT; i++) {
			Slot reserved = menu.getSlot(base + i);
			if (reserved.mayPlace(mute) || reserved.mayPlace(overclocker) || reserved.mayPlace(empty)) {
				helper.fail("reserved arm " + i + " must accept nothing until MOD-286 fills it");
			}
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
	public static void muteChip_craftingRecipesResolve(GameTestHelper helper) {
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
