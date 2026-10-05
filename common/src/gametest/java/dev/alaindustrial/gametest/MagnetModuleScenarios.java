package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.module.ItemModules;
import dev.alaindustrial.item.tool.MagnetFilter;
import dev.alaindustrial.item.tool.MagnetItem;
import dev.alaindustrial.item.tool.MagnetStuckTracker;
import dev.alaindustrial.menu.MagnetMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for the electromagnet's module slots and filter (MOD-592, suite
 * TC-MAGNET-003). Wrapped by the Fabric {@code MagnetGameTest} suite and registered on the NeoForge lane
 * in {@code NeoForgeGameTests}.
 *
 * <p>Pulls are driven through {@link MagnetItem#pullSingle} on DETACHED item entities, for the reason
 * {@link MagnetScenarios} gives: a world scan on a shared gametest server sees other tests' drops.
 */
public final class MagnetModuleScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MagnetModuleScenarios::fun01FilterDecidesWhatIsPulled,
								"magnet_tc_magnet003_fun01_filter_decides_what_is_pulled")
						.fabricId("MagnetGameTest", "tcMagnet003Fun01_filterDecidesWhatIsPulled").ticks(20, 40),
				RosterEntry.of(MagnetModuleScenarios::fun02ModAndCategoryMatching,
								"magnet_tc_magnet003_fun02_mod_and_category_matching")
						.fabricId("MagnetGameTest", "tcMagnet003Fun02_modAndCategoryMatching").ticks(20, 40),
				RosterEntry.of(MagnetModuleScenarios::fun03StuckDropIsReleased,
								"magnet_tc_magnet003_fun03_stuck_drop_is_released")
						.fabricId("MagnetGameTest", "tcMagnet003Fun03_stuckDropIsReleased").ticks(100),
				RosterEntry.of(MagnetModuleScenarios::fun04ScreenSlotsAndButtons,
								"magnet_tc_magnet003_fun04_screen_slots_and_buttons")
						.fabricId("MagnetGameTest", "tcMagnet003Fun04_screenSlotsAndButtons").ticks(20, 40),
				RosterEntry.of(MagnetModuleScenarios::fun05DestroyedMagnetDropsModules,
								"magnet_tc_magnet003_fun05_destroyed_magnet_drops_modules")
						.fabricId("MagnetGameTest", "tcMagnet003Fun05_destroyedMagnetDropsModules").ticks(20, 40),
				RosterEntry.of(MagnetModuleScenarios::per01SettingsSurviveASave,
								"magnet_tc_magnet003_per01_settings_survive_a_save")
						.fabricId("MagnetGameTest", "tcMagnet003Per01_settingsSurviveASave").ticks(20, 40));

		private Roster() {}
	}

	private MagnetModuleScenarios() {}

	private static ItemStack magnet(Item grade) {
		ItemStack stack = new ItemStack(grade);
		ItemEnergy.set(stack, ItemEnergy.capacity(stack));
		return stack;
	}

	private static ItemStack filterModule(MagnetFilter filter) {
		ItemStack module = new ItemStack(ModContent.MAGNET_FILTER_MODULE.get());
		MagnetFilter.set(module, filter);
		return module;
	}

	private static void fit(ItemStack magnet, ItemStack module) {
		ItemModules.set(magnet, ItemModules.of(magnet).with(0, module));
	}

	private static ItemEntity drop(GameTestHelper helper, ServerPlayer player, ItemStack what) {
		Vec3 p = player.position();
		ItemEntity item = new ItemEntity(helper.getLevel(), p.x + 2.5, p.y + 0.5, p.z, what);
		item.setDeltaMovement(Vec3.ZERO);
		item.setPickUpDelay(0);
		return item;
	}

	/** Pull {@code what} once; fail with {@code label} unless the answer and the EU spent are as expected. */
	private static void expectPull(GameTestHelper helper, ServerPlayer player, ItemStack magnet, ItemStack what,
			boolean expected, String label) {
		long before = ItemEnergy.get(magnet);
		boolean pulled = MagnetItem.pullSingle(magnet, player, drop(helper, player, what));
		if (pulled != expected) {
			helper.fail(label + ": expected " + (expected ? "a pull" : "no pull") + " of " + what.getItem());
		}
		long spent = before - ItemEnergy.get(magnet);
		long cost = MagnetItem.tierOf(magnet).euPerItem();
		if (spent != (expected ? cost : 0)) {
			helper.fail(label + ": spent " + spent + " EU, expected " + (expected ? cost : 0));
		}
	}

	private static MagnetFilter filterWith(ItemStack sample, boolean allowList, MagnetFilter.Match match) {
		return MagnetFilter.EMPTY.withCell(0, sample).withAllowList(allowList).withMatch(match);
	}

	/**
	 * TC-MAGNET-003-FUN01 — without a module the magnet pulls everything; with a filter in "only these"
	 *     it pulls only the samples; in "everything except these" everything but the samples. A refused
	 *     item costs no EU.
	 *
	 * @implements TC-MAGNET-003-FUN01 — no module pulls all; the filter's two lists decide.
	 */
	public static void fun01FilterDecidesWhatIsPulled(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemStack iron = new ItemStack(Items.IRON_INGOT);
		ItemStack gold = new ItemStack(Items.GOLD_INGOT);

		ItemStack bare = magnet(ModContent.ELECTROMAGNET.get());
		expectPull(helper, player, bare, iron, true, "no module");
		expectPull(helper, player, bare, gold, true, "no module");

		ItemStack allow = magnet(ModContent.ELECTROMAGNET.get());
		fit(allow, filterModule(filterWith(iron, true, MagnetFilter.Match.ITEM)));
		expectPull(helper, player, allow, iron, true, "only these, sample");
		expectPull(helper, player, allow, gold, false, "only these, not a sample");

		ItemStack deny = magnet(ModContent.ELECTROMAGNET.get());
		fit(deny, filterModule(filterWith(iron, false, MagnetFilter.Match.ITEM)));
		expectPull(helper, player, deny, iron, false, "all except these, sample");
		expectPull(helper, player, deny, gold, true, "all except these, not a sample");

		ItemStack fresh = magnet(ModContent.ELECTROMAGNET.get());
		fit(fresh, filterModule(MagnetFilter.EMPTY));
		expectPull(helper, player, fresh, gold, true, "a fresh, empty filter must change nothing");
		helper.succeed();
	}

	/**
	 * TC-MAGNET-003-FUN02 — "by mod" matches every item of the sample's mod; "by category" matches every
	 *     item of the chosen tag, and a cell with no tag chosen falls back to its own item.
	 *
	 * @implements TC-MAGNET-003-FUN02 — by mod and by category.
	 */
	public static void fun02ModAndCategoryMatching(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemStack byMod = magnet(ModContent.ELECTROMAGNET_ADVANCED.get());
		fit(byMod, filterModule(filterWith(new ItemStack(Items.STONE), true, MagnetFilter.Match.MOD)));
		expectPull(helper, player, byMod, new ItemStack(Items.IRON_INGOT), true, "by mod, same mod");
		expectPull(helper, player, byMod, new ItemStack(ModContent.MODULE_BLANK.get()), false, "by mod, other mod");

		MagnetFilter noTag = filterWith(new ItemStack(Items.OAK_SAPLING), true, MagnetFilter.Match.TAG);
		ItemStack byItemFallback = magnet(ModContent.ELECTROMAGNET_ADVANCED.get());
		fit(byItemFallback, filterModule(noTag));
		expectPull(helper, player, byItemFallback, new ItemStack(Items.OAK_SAPLING), true, "no tag chosen, own item");
		expectPull(helper, player, byItemFallback, new ItemStack(Items.BIRCH_SAPLING), false, "no tag chosen, other item");

		// Walk the sapling's categories until the saplings tag is chosen, the way Shift + click does.
		String saplings = ItemTags.SAPLINGS.location().toString();
		MagnetFilter walked = noTag;
		for (int i = 0; i < 64 && !walked.cell(0).tag().equals(saplings); i++) {
			walked = walked.withNextTag(0);
		}
		if (!walked.cell(0).tag().equals(saplings)) {
			helper.fail("cycling the oak sapling's categories never reached " + saplings);
		}
		ItemStack byTag = magnet(ModContent.ELECTROMAGNET_ADVANCED.get());
		fit(byTag, filterModule(walked));
		expectPull(helper, player, byTag, new ItemStack(Items.BIRCH_SAPLING), true, "by category, same tag");
		expectPull(helper, player, byTag, new ItemStack(Items.IRON_INGOT), false, "by category, outside the tag");
		helper.succeed();
	}

	/**
	 * TC-MAGNET-003-FUN03 — a drop that does not come closer is let go: after
	 *     {@link MagnetStuckTracker#STALL_PULLS} pulls with no progress the magnet stops paying for it for
	 *     {@link MagnetStuckTracker#RELEASE_TICKS} ticks, then tries again.
	 *
	 * @implements TC-MAGNET-003-FUN03 — a drop that does not approach is let go.
	 */
	public static void fun03StuckDropIsReleased(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemStack magnet = magnet(ModContent.ELECTROMAGNET.get());
		// Detached: it never moves, so every pull is a pull without progress.
		ItemEntity stuck = drop(helper, player, new ItemStack(Items.COBBLESTONE));
		int pulls = 0;
		while (pulls < 100 && MagnetItem.pullSingle(magnet, player, stuck)) {
			pulls++;
		}
		if (pulls != MagnetStuckTracker.STALL_PULLS + 1) {
			helper.fail("a stuck drop must be released after " + (MagnetStuckTracker.STALL_PULLS + 1)
					+ " pulls, got " + pulls);
		}
		long charged = ToolConfig.magnetBuffer - (long) pulls * ToolConfig.magnetEuPerItem;
		if (ItemEnergy.get(magnet) != charged) {
			helper.fail("a released drop must cost nothing more: " + ItemEnergy.get(magnet) + " EU, expected " + charged);
		}
		helper.runAfterDelay(MagnetStuckTracker.RELEASE_TICKS + 1, () -> {
			if (!MagnetItem.pullSingle(magnet, player, stuck)) {
				helper.fail("after the release the magnet must try the drop again");
			}
			helper.succeed();
		});
	}

	/**
	 * TC-MAGNET-003-FUN04 — the screen: the grade decides the active module slots, one filter per magnet,
	 *     a fitted module is written onto the magnet at once, cells and buttons edit the filter, and the
	 *     magnet cannot be moved from under the screen.
	 *
	 * @implements TC-MAGNET-003-FUN04 — module slots, one filter, locked magnet.
	 */
	public static void fun04ScreenSlotsAndButtons(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.setSelectedSlot(0);

		inventory.setItem(0, magnet(ModContent.ELECTROMAGNET.get()));
		MagnetMenu basic = new MagnetMenu(1, inventory);
		if (basic.activeModuleSlots() != 1 || basic.slots.get(1).isActive()) {
			helper.fail("the basic magnet must show exactly one module slot");
		}

		ItemStack advanced = magnet(ModContent.ELECTROMAGNET_ADVANCED.get());
		inventory.setItem(0, advanced);
		MagnetMenu menu = new MagnetMenu(2, inventory);
		if (menu.activeModuleSlots() != 3) {
			helper.fail("the advanced magnet must show three module slots");
		}
		Slot first = menu.slots.get(MagnetMenu.MODULE_SLOT_START);
		Slot second = menu.slots.get(MagnetMenu.MODULE_SLOT_START + 1);
		ItemStack module = filterModule(MagnetFilter.EMPTY);
		if (!first.mayPlace(module) || first.mayPlace(new ItemStack(Items.DIRT))) {
			helper.fail("a module slot must take a filter and nothing else");
		}
		first.setByPlayer(module);
		if (!(ItemModules.of(advanced).get(0).getItem() == ModContent.MAGNET_FILTER_MODULE.get())) {
			helper.fail("a fitted module must be written onto the magnet at once");
		}
		if (second.mayPlace(filterModule(MagnetFilter.EMPTY))) {
			helper.fail("a second filter must be refused");
		}

		menu.setCarried(new ItemStack(Items.IRON_INGOT));
		menu.clickMenuButton(player, MagnetMenu.BUTTON_CELL + 3);
		menu.setCarried(ItemStack.EMPTY);
		menu.clickMenuButton(player, MagnetMenu.BUTTON_LIST_MODE);
		menu.clickMenuButton(player, MagnetMenu.BUTTON_MATCH);
		MagnetFilter written = MagnetItem.filterOf(advanced);
		if (written == null || !written.cell(3).item().equals("minecraft:iron_ingot")
				|| !written.allowList() || written.match() != MagnetFilter.Match.MOD) {
			helper.fail("cell and buttons must edit the fitted filter on the magnet, got " + written);
		}
		if (menu.slots.get(MagnetMenu.CELL_SLOT_START + 3).getItem().getItem() != Items.IRON_INGOT) {
			helper.fail("cell 3 must show its sample");
		}

		Slot held = menu.slots.get(MagnetMenu.PLAYER_SLOT_START + 27);
		if (held.mayPickup(player) || held.mayPlace(new ItemStack(Items.DIRT))) {
			helper.fail("the magnet's own slot must be locked");
		}
		menu.clicked(MagnetMenu.MODULE_SLOT_START, 0, ContainerInput.SWAP, player);
		menu.clicked(MagnetMenu.PLAYER_SLOT_START + 27, 1, ContainerInput.SWAP, player);
		menu.clicked(MagnetMenu.PLAYER_SLOT_START + 27, 0, ContainerInput.THROW, player);
		if (inventory.getItem(0) != advanced || !menu.stillValid(player)) {
			helper.fail("swapping or throwing must not move the magnet from under its screen");
		}

		ItemStack taken = first.remove(1);
		if (!ItemModules.of(advanced).isEmpty() || !MagnetFilter.of(taken).cell(3).item().equals("minecraft:iron_ingot")) {
			helper.fail("a removed filter must leave the magnet and keep its own settings");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-003-PER01 — filter and module lists survive a save; a sample from an item this build
	 *     does not know loads, shows empty and matches nothing.
	 *
	 * @implements TC-MAGNET-003-PER01 — filter and modules round-trip; unknown ids load.
	 */
	public static void per01SettingsSurviveASave(GameTestHelper helper) {
		RegistryAccess access = helper.getLevel().registryAccess();
		RegistryOps<Tag> ops = access.createSerializationContext(NbtOps.INSTANCE);
		MagnetFilter filter = filterWith(new ItemStack(Items.IRON_INGOT), true, MagnetFilter.Match.TAG)
				.withNextTag(0).withCell(5, new ItemStack(Items.STONE));
		Tag saved = MagnetFilter.CODEC.encodeStart(ops, filter).getOrThrow();
		MagnetFilter loaded = MagnetFilter.CODEC.parse(ops, saved).getOrThrow();
		if (!loaded.equals(filter)) {
			helper.fail("the filter must round-trip, got " + loaded + " for " + filter);
		}

		ItemStack magnet = magnet(ModContent.ELECTROMAGNET_ADVANCED.get());
		fit(magnet, filterModule(filter));
		ItemModules modules = ItemModules.of(magnet);
		ItemModules back = ItemModules.CODEC.parse(ops, ItemModules.CODEC.encodeStart(ops, modules).getOrThrow())
				.getOrThrow();
		if (!back.equals(modules) || !MagnetFilter.of(back.get(0)).equals(filter)) {
			helper.fail("the fitted modules must round-trip with their settings");
		}

		MagnetFilter foreign = new MagnetFilter(List.of(new MagnetFilter.Cell("nosuchmod:gizmo", "")), true,
				MagnetFilter.Match.ITEM);
		MagnetFilter foreignBack = MagnetFilter.CODEC.parse(ops, MagnetFilter.CODEC.encodeStart(ops, foreign).getOrThrow())
				.getOrThrow();
		if (!foreignBack.cell(0).stack().isEmpty() || foreignBack.passes(new ItemStack(Items.IRON_INGOT))) {
			helper.fail("an unknown sample must load, show empty and match nothing");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-003-FUN05 — a magnet destroyed as a drop gives its modules back.
	 *
	 * @implements TC-MAGNET-003-FUN05 — a destroyed magnet drops its modules.
	 */
	public static void fun05DestroyedMagnetDropsModules(GameTestHelper helper) {
		ItemStack magnet = magnet(ModContent.ELECTROMAGNET.get());
		fit(magnet, filterModule(MagnetFilter.EMPTY));
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.5, 1.5));
		ItemEntity entity = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, magnet);
		helper.getLevel().addFreshEntity(entity);
		magnet.getItem().onDestroyed(entity);
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(2.0),
				e -> e.getItem().getItem() == ModContent.MAGNET_FILTER_MODULE.get());
		if (dropped.isEmpty()) {
			helper.fail("the destroyed magnet must drop its filter module");
		}
		dropped.forEach(ItemEntity::discard);
		entity.discard();
		helper.succeed();
	}
}
