package dev.alaindustrial.gametest;

import dev.alaindustrial.block.SettlementCoreBlock;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.SettlementCoreMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.SettlementTier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametests of the settlement core (MOD-831, suite TC-SETTLE-001), replayed by both loaders through
 * the roster (ADR-038): no energy — dark and no territory; energy from the network — lit, holding a territory of its
 * tier's radius; energy gone — dark after exactly {@code settlementCoreGraceTicks} unpaid ticks; an MV packet taken
 * (R44); the owner is the first player to open the lit core, a second one cannot take the town, and the owner
 * survives a save; only the owner breaks the core, which drops as a plain core, and any core placed inside the
 * territory of a town without its core continues it, while a town that has its core takes no second one (R43); a
 * player's core outside every territory founds no town and is broken by whoever placed it (R53).
 *
 * <p><b>Each case reads its own town.</b> The registry is server-global and every rig's core registers a town of
 * its own, so a case asks the record of ITS core ({@link SettlementCoreBlockEntity#settlement()}) rather than "which
 * town holds this block": an LV territory is 65 blocks wide and reaches into the neighbouring rigs.
 *
 * <p>The core is driven by its own {@code serverTick} ({@link AlaGameTestHelper#drive}) — deterministic, no waiting.
 * A test that sets the buffer by hand wakes the core first: a dark core sleeps, and only a committed delivery wakes it.
 *
 * <p><b>A player's core reads the territory it lands in</b> (R43), and the neighbouring rigs' towns reach into this
 * one. So a case that places a core by hand does it two blocks from its own town's core, which then decides as the
 * nearest; the territory's edge is checked on the registry alone, far from every rig ({@link #FAR}).
 */
public final class SettlementCoreScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Fun01UnpoweredCoreIsDark,
						"settlement_core_unpowered_is_dark").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Fun02NetworkPowerLightsCoreAndHoldsTerritory,
						"settlement_core_network_power_holds_territory").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Fun03LostPowerDarkensCoreAfterGrace,
						"settlement_core_lost_power_darkens_after_grace").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Fun04CoreTakesThePacketOfTheTierAbove,
						"settlement_core_takes_mv_packet").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Own01FirstOpenerOfLitCoreOwnsTown,
						"settlement_core_first_opener_owns_town").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Own02OwnerSurvivesSave,
						"settlement_core_owner_survives_save").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Reg01CopiedCoreGetsItsOwnTown,
						"settlement_core_copied_core_gets_own_town").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Reg02CopyInsideItsTownIsADarkSecondCore,
						"settlement_core_copy_inside_town_is_second_core").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Brk01RemovedCoreLeavesTownDark,
						"settlement_core_removed_core_leaves_town_dark").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Brk02OnlyTheOwnerWithAPickaxeBreaksTheCore,
						"settlement_core_only_owner_with_pickaxe_breaks").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Brk03AnyCoreContinuesATownWithoutItsCore,
						"settlement_core_any_core_continues_coreless_town").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Brk04PlayerCoreContinuesOnlyInsideTheTerritory,
						"settlement_core_player_core_continues_only_inside_territory").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Brk05PlayerCannotPlaceASecondCore,
						"settlement_core_player_cannot_place_second_core").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Brk06PlayerCoreOutsideEveryTerritoryFoundsNoTown,
						"settlement_core_player_core_outside_founds_no_town").ticks(40),
				RosterEntry.of(SettlementCoreScenarios::tcSettle001Rec01CoreRecipeIsTheLateStageCraft,
						"settlement_core_recipe_late_stage").ticks(20));

		private Roster() {}
	}

	private SettlementCoreScenarios() {}

	private static final BlockPos CORE = new BlockPos(1, 2, 1);
	/** A spot no rig reaches: the registry's own rules are checked there without the neighbours' towns. */
	private static final BlockPos FAR = new BlockPos(0, 64, 100_000);

	private static SettlementCoreBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, CORE, ModContent.SETTLEMENT_CORE.get(), SettlementCoreBlockEntity.class);
	}

	/** Fill the buffer by hand and wake the core, so its next tick sees the charge. */
	private static void charge(SettlementCoreBlockEntity core, long amount) {
		core.getEnergyStorage().setAmountUntracked(amount);
		core.wake();
	}

	/** The core's town, or a failed test naming when it was expected. */
	private static Settlement town(GameTestHelper helper, SettlementCoreBlockEntity core, String when) {
		Optional<Settlement> town = core.settlement();
		if (town.isEmpty()) {
			helper.fail("no settlement record " + when + " (core id " + core.settlementId() + ")");
			throw new IllegalStateException("unreachable: helper.fail throws");
		}
		return town.get();
	}

	private static boolean litState(GameTestHelper helper) {
		return helper.getLevel().getBlockState(helper.absolutePos(CORE)).getValue(SettlementCoreBlock.LIT);
	}

	/**
	 * @implements TC-SETTLE-001-FUN01 — a core with no energy registers its town on its first tick but stays dark:
	 *     not lit, no light, no territory, not even over its own block.
	 */
	public static void tcSettle001Fun01UnpoweredCoreIsDark(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		AlaGameTestHelper.drive(core, helper, 20);
		Settlement settlement = town(helper, core, "after twenty unpowered ticks");
		if (core.isActive() || settlement.active() || litState(helper)) {
			helper.fail("an unpowered core must be dark: core " + core.isActive() + ", record " + settlement.active()
					+ ", lit " + litState(helper));
			return;
		}
		if (settlement.holds(helper.getLevel().dimension(), helper.absolutePos(CORE))) {
			helper.fail("a dark core holds no territory, not even its own column");
			return;
		}
		if (settlement.tier() != SettlementTier.LV || settlement.owner().isPresent()) {
			helper.fail("a found core starts unclaimed at LV: " + settlement);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-FUN02 — EU arriving through a real line (creative source → cables → core) lights the
	 *     core; its town then holds a square column of the LV radius around it: the edge inside, one block past it not.
	 */
	public static void tcSettle001Fun02NetworkPowerLightsCoreAndHoldsTerritory(GameTestHelper helper) {
		EnergyLine line = EnergyLine.in(helper)
				.generator(ModContent.CREATIVE_ENERGY_SOURCE.get(), ItemStack.EMPTY)
				.cables(2)
				.consumer(ModContent.SETTLEMENT_CORE.get(), ItemStack.EMPTY)
				.build();
		SettlementCoreBlockEntity core = helper.getBlockEntity(line.consumerPos(), SettlementCoreBlockEntity.class);
		line.drive(40);
		if (!core.isActive()) {
			helper.fail("forty ticks on a live line did not light the core; buffer "
					+ core.getEnergyStorage().getAmount());
			return;
		}
		ServerLevel level = helper.getLevel();
		Settlement settlement = town(helper, core, "on a live line");
		BlockPos at = core.getBlockPos();
		int radius = SettlementTier.LV.radius();
		if (!settlement.active() || !litState(helper, line.consumerPos())) {
			helper.fail("the record and the block must say lit too: record " + settlement.active());
			return;
		}
		if (settlement.radius() != radius) {
			helper.fail("an LV town's radius must be the LV knob " + radius + ", got " + settlement.radius());
			return;
		}
		if (!settlement.holds(level.dimension(), at.offset(radius, 40, -radius))
				|| !settlement.holds(level.dimension(), at.offset(-radius, -40, radius))) {
			helper.fail("the corners of the territory, at any height, must be inside");
			return;
		}
		if (settlement.holds(level.dimension(), at.offset(radius + 1, 0, 0))
				|| settlement.holds(level.dimension(), at.offset(0, 0, -radius - 1))) {
			helper.fail("one block past the radius must be outside");
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-FUN04 — an LV core takes the packet of the tier above (epic decision R44): its intake
	 *     is the MV packet, and over a real line that carries more than an LV packet (creative source at the MV preset
	 *     → electrum cables → core) it gains more in five ticks than five LV packets could bring.
	 */
	public static void tcSettle001Fun04CoreTakesThePacketOfTheTierAbove(GameTestHelper helper) {
		EnergyLine line = EnergyLine.in(helper)
				.generator(ModContent.CREATIVE_ENERGY_SOURCE.get(), ItemStack.EMPTY)
				.cables(ModContent.ELECTRUM_CABLE.get(), 2)
				.consumer(ModContent.SETTLEMENT_CORE.get(), ItemStack.EMPTY)
				.build();
		SettlementCoreBlockEntity core = helper.getBlockEntity(line.consumerPos(), SettlementCoreBlockEntity.class);
		long intake = core.getEnergyStorage().maxInsert;
		if (intake != EnergyTier.MV.maxVoltage() || SettlementTier.LV.intakeTier() != EnergyTier.MV) {
			helper.fail("an LV core must take an MV packet (" + EnergyTier.MV.maxVoltage() + " EU/t), its intake is "
					+ intake);
			return;
		}
		line.drive(4);
		long before = core.getEnergyStorage().getAmount();
		line.drive(5);
		long gained = core.getEnergyStorage().getAmount() - before;
		long fiveLvPackets = 5 * EnergyTier.LV.maxVoltage();
		if (gained <= fiveLvPackets) {
			helper.fail("five ticks on a line carrying more than an LV packet brought " + gained
					+ " EU, no more than five LV packets (" + fiveLvPackets + ")");
			return;
		}
		helper.succeed();
	}

	private static boolean litState(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getBlockState(helper.absolutePos(relative)).getValue(SettlementCoreBlock.LIT);
	}

	/**
	 * @implements TC-SETTLE-001-FUN03 — a lit core whose energy is gone (empty buffer, nothing arriving) holds its
	 *     territory for {@code settlementCoreGraceTicks - 1} more ticks and goes dark on the next one: the block, the
	 *     record and the territory all at once.
	 */
	public static void tcSettle001Fun03LostPowerDarkensCoreAfterGrace(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		charge(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		if (!core.isActive()) {
			helper.fail("control: a full buffer did not light the core");
			return;
		}
		charge(core, 0);
		int grace = SettlementConfig.settlementCoreGraceTicks;
		AlaGameTestHelper.drive(core, helper, grace - 1);
		if (!core.isActive() || !town(helper, core, "inside the grace").active()) {
			helper.fail("the core went dark before its " + grace + " ticks of grace ran out");
			return;
		}
		AlaGameTestHelper.drive(core, helper, 1);
		Settlement settlement = town(helper, core, "after the grace");
		if (core.isActive() || settlement.active() || litState(helper)) {
			helper.fail("the core is still lit " + grace + " ticks after its energy was gone");
			return;
		}
		if (settlement.holds(helper.getLevel().dimension(), helper.absolutePos(CORE))) {
			helper.fail("a dark core's town still holds territory");
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-OWN01 — opening a dark core claims nothing; the first player to open the lit core owns
	 *     the town; a second player opening it, or claiming outright, does not take it over.
	 */
	public static void tcSettle001Own01FirstOpenerOfLitCoreOwnsTown(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		AlaGameTestHelper.drive(core, helper, 1);
		ServerPlayer first = AlaGameTestHelper.mockPlayerInLevel(helper);
		ServerPlayer second = AlaGameTestHelper.mockPlayerInLevel(helper);
		if (first.getUUID().equals(second.getUUID())) {
			helper.fail("control: the two mock players share a UUID, so this case proves nothing");
			return;
		}
		open(core, first);
		if (town(helper, core, "after opening the dark core").owner().isPresent()) {
			helper.fail("opening a dark core must not claim the town");
			return;
		}
		charge(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		open(core, first);
		if (!town(helper, core, "after the first opening").isOwnedBy(first.getUUID())) {
			helper.fail("the first player to open the lit core is not its owner: " + town(helper, core, "now"));
			return;
		}
		open(core, second);
		SettlementRegistry.ClaimResult direct = core.claim(second.getUUID(), "second");
		Settlement settlement = town(helper, core, "after the second player");
		if (direct != SettlementRegistry.ClaimResult.OWNED_BY_ANOTHER || !settlement.isOwnedBy(first.getUUID())) {
			helper.fail("a second player took the town over: claim " + direct + ", record " + settlement);
			return;
		}
		if (core.claim(first.getUUID(), "first") != SettlementRegistry.ClaimResult.ALREADY_YOURS) {
			helper.fail("the owner claiming again must change nothing");
			return;
		}
		helper.succeed();
	}

	/** The server's path of a right-click: the core builds the menu for {@code player}. */
	private static void open(SettlementCoreBlockEntity core, ServerPlayer player) {
		Inventory inventory = player.getInventory();
		core.createMenu(0, inventory, player);
	}

	/**
	 * @implements TC-SETTLE-001-OWN02 — a claimed town comes back from a save with its owner: the registry through
	 *     its own codec (the path {@code SavedData} takes on a world restart), and the core through its block-entity
	 *     tag, which keeps the town's number and whether it was lit.
	 */
	public static void tcSettle001Own02OwnerSurvivesSave(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		charge(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		UUID owner = UUID.nameUUIDFromBytes("settlement-owner".getBytes(StandardCharsets.UTF_8));
		if (core.claim(owner, "Owner") != SettlementRegistry.ClaimResult.CLAIMED) {
			helper.fail("control: the lit core could not be claimed");
			return;
		}
		SettlementRegistry registry = SettlementRegistry.get(helper.getLevel().getServer());
		if (!registry.isDirty()) {
			helper.fail("a claim must mark the registry for saving");
			return;
		}
		Tag saved = SettlementRegistry.CODEC.encodeStart(NbtOps.INSTANCE, registry).getOrThrow();
		SettlementRegistry reloaded = SettlementRegistry.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		Optional<Settlement> before = registry.find(core.settlementId());
		Optional<Settlement> after = reloaded.find(core.settlementId());
		if (after.isEmpty() || !after.equals(before) || !after.get().isOwnedBy(owner)
				|| !"Owner".equals(after.get().ownerName())) {
			helper.fail("the town did not come back whole: before " + before + ", after " + after);
			return;
		}

		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = core.saveWithoutMetadata(registries);
		SettlementCoreBlockEntity restored = SaveFormatTestSupport.load(
				SaveFormatTestSupport.fresh(SettlementCoreBlockEntity::new, ModContent.SETTLEMENT_CORE.get()),
				registries, tag);
		if (restored.settlementId() != core.settlementId() || !restored.isActive()) {
			helper.fail("the core's tag lost its town or its lit state: " + tag);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-REG01 — a core carrying the number of a town whose core still stands elsewhere (a
	 *     structure template saved from a placed core, a {@code /clone}), standing outside that town's territory, gets
	 *     a town of its own instead of sharing one.
	 */
	public static void tcSettle001Reg01CopiedCoreGetsItsOwnTown(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		AlaGameTestHelper.drive(core, helper, 1);
		SettlementRegistry registry = SettlementRegistry.get(helper.getLevel().getServer());
		ServerLevel level = helper.getLevel();
		// Its own stretch of the far strip, half of FAR away from the one BRK04 uses.
		BlockPos elsewhere = FAR.offset(helper.absolutePos(CORE).getX(), 0, helper.absolutePos(CORE).getZ() - 50_000);
		SettlementRegistry.Placement copy = registry.place(level.dimension(), elsewhere, core.settlementId());
		if (copy.settlementId() == core.settlementId() || !copy.home()) {
			helper.fail("a copied core shares town " + copy + " with the original");
			return;
		}
		if (!registry.place(level.dimension(), elsewhere, copy.settlementId()).equals(copy)
				|| registry.place(level.dimension(), helper.absolutePos(CORE), core.settlementId()).settlementId()
						!= core.settlementId()) {
			helper.fail("asking again must give each core the same town");
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-REG02 — a copy of a standing core (its data cloned, as {@code /clone} does) inside the
	 *     town's territory is a second core (R43): dark with a full buffer, the town's number, no town of its own, the
	 *     town's record where it was. Once the town is without its core, the copy continues it from its own spot.
	 */
	public static void tcSettle001Reg02CopyInsideItsTownIsADarkSecondCore(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		AlaGameTestHelper.drive(core, helper, 1);
		int id = core.settlementId();
		ServerLevel level = helper.getLevel();
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());

		BlockPos copyAt = CORE.east(3);
		SettlementCoreBlockEntity copy = place(helper, copyAt);
		SaveFormatTestSupport.load(copy, level.registryAccess(), core.saveWithoutMetadata(level.registryAccess()));
		charge(copy, copy.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(copy, helper, 5);
		Settlement town = registry.find(id).orElseThrow();
		if (copy.settlementId() != id || !copy.isMisplaced() || copy.isActive() || litState(helper, copyAt)) {
			helper.fail("a copy inside its town must be a dark second core of town " + id + ": id "
					+ copy.settlementId() + ", second " + copy.isMisplaced() + ", lit " + copy.isActive());
			return;
		}
		if (registry.findAt(level.dimension(), helper.absolutePos(copyAt)).isPresent()
				|| !town.corePlaced() || !town.corePos().equals(helper.absolutePos(CORE))) {
			helper.fail("a second core must found no town and leave its town's record alone: " + town);
			return;
		}

		helper.setBlock(CORE, Blocks.AIR);
		copy.wake(); // a dark core looks again within its idle sleep; the test does not wait for it
		AlaGameTestHelper.drive(copy, helper, 1);
		town = registry.find(id).orElseThrow();
		if (copy.settlementId() != id || copy.isMisplaced() || !copy.isActive()
				|| !town.corePos().equals(helper.absolutePos(copyAt)) || !town.corePlaced()) {
			helper.fail("with the town's core gone, the copy must continue town " + id + " from its spot: " + town
					+ ", second " + copy.isMisplaced() + ", lit " + copy.isActive());
			return;
		}
		helper.succeed();
	}

	private static SettlementCoreBlockEntity place(GameTestHelper helper, BlockPos relative) {
		return AlaGameTestHelper.place(helper, relative, ModContent.SETTLEMENT_CORE.get(),
				SettlementCoreBlockEntity.class);
	}

	/**
	 * {@code player} places a core item from the creative tab at {@code relative} the way the server does a
	 * right-click, aimed at the empty cell itself (air gives way to the block).
	 */
	private static InteractionResult placeByHand(GameTestHelper helper, ServerPlayer player, BlockPos relative,
			ItemStack stack) {
		BlockPos at = helper.absolutePos(relative);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
				hit);
	}

	/**
	 * @implements TC-SETTLE-001-BRK01 — a core removed from the world leaves its town in place but dark, with its
	 *     owner (epic decision R36: hibernation while the core is away, nothing lost).
	 */
	public static void tcSettle001Brk01RemovedCoreLeavesTownDark(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		charge(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		UUID owner = UUID.nameUUIDFromBytes("settlement-brk01".getBytes(StandardCharsets.UTF_8));
		if (core.claim(owner, "Owner") != SettlementRegistry.ClaimResult.CLAIMED) {
			helper.fail("control: the lit core could not be claimed");
			return;
		}
		int id = core.settlementId();
		helper.setBlock(CORE, Blocks.AIR);
		Optional<Settlement> town = SettlementRegistry.get(helper.getLevel().getServer()).find(id);
		if (town.isEmpty()) {
			helper.fail("town " + id + " was forgotten with its core");
			return;
		}
		if (town.get().corePlaced() || town.get().active() || !town.get().isOwnedBy(owner)) {
			helper.fail("a town whose core is away must be dark, marked as missing its core and keep its owner: "
					+ town.get());
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-BRK02 — only the town's owner breaks the core, and only with a pickaxe: the block is
	 *     unbreakable by hardness (so area tools skip it), a stranger with a pickaxe makes no progress, the owner
	 *     makes none bare-handed, and an unclaimed core is nobody's to break.
	 */
	public static void tcSettle001Brk02OnlyTheOwnerWithAPickaxeBreaksTheCore(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(CORE);
		ServerPlayer owner = AlaGameTestHelper.mockPlayerInLevel(helper);
		ServerPlayer stranger = AlaGameTestHelper.mockPlayerInLevel(helper);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
		AlaGameTestHelper.drive(core, helper, 1);
		if (level.getBlockState(at).getDestroySpeed(level, at) >= 0) {
			helper.fail("the core must be unbreakable by hardness, so tools that skip such blocks skip it");
			return;
		}
		if (level.getBlockState(at).getDestroyProgress(owner, level, at) != 0.0F) {
			helper.fail("an unclaimed core must be nobody's to break");
			return;
		}
		charge(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		open(core, owner);
		BlockState state = level.getBlockState(at);
		if (!core.settlement().map(town -> town.isOwnedBy(owner.getUUID())).orElse(false)) {
			helper.fail("control: the owner did not claim the town");
			return;
		}
		if (state.getDestroyProgress(owner, level, at) <= 0.0F) {
			helper.fail("the owner with a wooden pickaxe must be able to break the core");
			return;
		}
		if (state.getDestroyProgress(stranger, level, at) != 0.0F) {
			helper.fail("a stranger with a netherite pickaxe made progress on the core");
			return;
		}
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (state.getDestroyProgress(owner, level, at) != 0.0F) {
			helper.fail("the owner bare-handed made progress on the core");
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-BRK03 — the owner breaks the core with a pickaxe; it drops as a plain core with no town
	 *     number, the same item the creative tab gives (R43). A new core from the tab, placed by hand two blocks away,
	 *     continues the same town — same number, same owner, its new spot recorded — and lights on power. An item from
	 *     an older save that still carries a number gives it to no core.
	 */
	public static void tcSettle001Brk03AnyCoreContinuesATownWithoutItsCore(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		charge(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		open(core, owner);
		int id = core.settlementId();
		BlockPos at = helper.absolutePos(CORE);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
		if (!owner.gameMode.destroyBlock(at)) {
			helper.fail("the owner could not break the core");
			return;
		}
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2),
				drop -> drop.getItem().is(ModContent.SETTLEMENT_CORE_ITEM.get()));
		if (drops.size() != 1) {
			helper.fail("the broken core must drop exactly one core item, found " + drops.size());
			return;
		}
		ItemStack dropped = drops.get(0).getItem().copy();
		drops.forEach(ItemEntity::discard);
		ItemStack fromTab = new ItemStack(ModContent.SETTLEMENT_CORE_ITEM.get());
		if (dropped.has(ModDataComponents.SETTLEMENT_ID.get())
				|| !ItemStack.isSameItemSameComponents(dropped, fromTab)) {
			helper.fail("the broken core must drop a plain core, the creative tab's own: "
					+ dropped.getComponentsPatch());
			return;
		}

		BlockPos moved = CORE.east(2);
		InteractionResult result = placeByHand(helper, owner, moved, fromTab);
		if (!result.consumesAction() || !(helper.getLevel().getBlockEntity(helper.absolutePos(moved))
				instanceof SettlementCoreBlockEntity placed)) {
			helper.fail("the owner could not place a core from the tab inside the territory: " + result);
			return;
		}
		charge(placed, placed.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(placed, helper, 1);
		Settlement town = town(helper, placed, "after placing a new core");
		if (placed.settlementId() != id || placed.isMisplaced() || !placed.isActive()) {
			helper.fail("a core placed inside the territory of a town without its core must continue town " + id
					+ " and light: id " + placed.settlementId() + ", second " + placed.isMisplaced() + ", lit "
					+ placed.isActive());
			return;
		}
		if (!town.isOwnedBy(owner.getUUID()) || !town.corePos().equals(helper.absolutePos(moved)) || !town.active()) {
			helper.fail("the town must keep its owner and record the core's new spot: " + town);
			return;
		}

		BlockPos probe = CORE.east(4);
		SettlementCoreBlockEntity old = place(helper, probe);
		ItemStack olderSave = new ItemStack(ModContent.SETTLEMENT_CORE_ITEM.get());
		olderSave.set(ModDataComponents.SETTLEMENT_ID.get(), id);
		old.applyComponentsFromItemStack(olderSave);
		if (old.settlementId() != 0) {
			helper.fail("a core must not take a town number from its item, it took " + old.settlementId());
			return;
		}
		helper.setBlock(probe, Blocks.AIR);
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-BRK04 — a player's core continues a town without its core only inside that town's
	 *     territory: on its edge it does; one block past it the town keeps sleeping where it was and the core belongs
	 *     to no town (R53); a core appearing there with the town's number in its data — a copy — is a town of its own
	 *     and does not move the town either. Checked on the registry, far from every rig ({@link #FAR}).
	 */
	public static void tcSettle001Brk04PlayerCoreContinuesOnlyInsideTheTerritory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		int radius = SettlementTier.LV.radius();
		BlockPos home = FAR.offset(helper.absolutePos(CORE).getX(), 0, helper.absolutePos(CORE).getZ());
		int id = registry.place(level.dimension(), home, 0).settlementId();
		registry.coreRemoved(level.dimension(), home);

		BlockPos outside = home.offset(radius + 1, 0, 0);
		SettlementRegistry.Placement placedOutside = registry.adopt(level.dimension(), outside);
		SettlementRegistry.Placement carriedOutside = registry.place(level.dimension(), outside.east(), id);
		Settlement town = registry.find(id).orElseThrow();
		if (placedOutside.settlementId() == id || carriedOutside.settlementId() == id || town.corePlaced()
				|| !town.corePos().equals(home)) {
			helper.fail("a core one block past the territory must not continue town " + id + ": " + placedOutside
					+ ", " + carriedOutside + ", town " + town);
			return;
		}
		if (!placedOutside.equals(SettlementRegistry.Placement.NONE) || !carriedOutside.home()) {
			helper.fail("past the territory a player's core is of no town, a copy a town of its own: "
					+ placedOutside + ", " + carriedOutside);
			return;
		}

		// The far side from the copy just placed past the east edge, whose own territory reaches back over it.
		BlockPos edge = home.offset(-radius, 0, radius);
		SettlementRegistry.Placement onEdge = registry.adopt(level.dimension(), edge);
		town = registry.find(id).orElseThrow();
		if (onEdge.settlementId() != id || !onEdge.home() || !town.corePlaced() || !town.corePos().equals(edge)) {
			helper.fail("a core on the edge of the territory must continue town " + id + ": " + onEdge + ", " + town);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-BRK05 — a player cannot place a core inside the territory of a town whose core stands
	 *     (R43): the click places nothing, the core stays in the hand, no town is recorded there and the town's record
	 *     is untouched — even for an item from an older save that still carries that town's number.
	 */
	public static void tcSettle001Brk05PlayerCannotPlaceASecondCore(GameTestHelper helper) {
		SettlementCoreBlockEntity core = place(helper);
		AlaGameTestHelper.drive(core, helper, 1);
		int id = core.settlementId();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		BlockPos second = CORE.east(2);
		ItemStack olderSave = new ItemStack(ModContent.SETTLEMENT_CORE_ITEM.get());
		olderSave.set(ModDataComponents.SETTLEMENT_ID.get(), id);
		for (ItemStack stack : List.of(new ItemStack(ModContent.SETTLEMENT_CORE_ITEM.get()), olderSave)) {
			InteractionResult result = placeByHand(helper, player, second, stack);
			if (result.consumesAction() || !level.getBlockState(helper.absolutePos(second)).isAir()) {
				helper.fail("a second core was placed in a town that has its core: " + result);
				return;
			}
			ItemStack hand = player.getMainHandItem();
			if (!hand.is(ModContent.SETTLEMENT_CORE_ITEM.get()) || hand.getCount() != 1) {
				helper.fail("a refused core must stay in the hand, holding " + hand);
				return;
			}
		}
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Settlement town = town(helper, core, "after the refused placements");
		if (registry.findAt(level.dimension(), helper.absolutePos(second)).isPresent() || !town.corePlaced()
				|| !town.corePos().equals(helper.absolutePos(CORE))) {
			helper.fail("a refused core must found no town and leave the town's record alone: " + town);
			return;
		}
		if (registry.coreAlreadyStands(level.dimension(), helper.absolutePos(second)).map(Settlement::id).orElse(0)
				!= id) {
			helper.fail("the registry must name town " + id + " as the one whose core already stands here");
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-BRK06 — a player's core placed outside every territory founds no town (epic decision
	 *     R53): no town is recorded there, it stays dark with a full buffer and its screen says it is of no town; only
	 *     the player who placed it breaks it (any pickaxe), a stranger cannot. A core of no town whose placer is
	 *     unknown — copied data — stays of no town on its own ticks, and anyone with a pickaxe breaks it. The
	 *     neighbouring rigs' territories reach into this one, so the LV radius is one block for this synchronous call.
	 */
	public static void tcSettle001Brk06PlayerCoreOutsideEveryTerritoryFoundsNoTown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		ServerPlayer placer = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer stranger = AlaGameTestHelper.survivalPlayer(helper);
		BlockPos at = helper.absolutePos(CORE);
		try (ConfigOverrides config = ConfigOverrides.sync()) {
			config.set("settlementRadiusLv", 1);
			InteractionResult result = placeByHand(helper, placer, CORE,
					new ItemStack(ModContent.SETTLEMENT_CORE_ITEM.get()));
			if (!result.consumesAction() || !(level.getBlockEntity(at) instanceof SettlementCoreBlockEntity core)) {
				helper.fail("the player could not place a core outside every territory: " + result);
				return;
			}
			charge(core, core.getEnergyStorage().getCapacity());
			AlaGameTestHelper.drive(core, helper, 5);
			if (!core.isUnbound() || core.settlementId() != 0 || core.isActive() || litState(helper)
					|| registry.findAt(level.dimension(), at).isPresent()) {
				helper.fail("a player's core outside every territory must found no town and stay dark: of no town "
						+ core.isUnbound() + ", id " + core.settlementId() + ", lit " + core.isActive());
				return;
			}
			SettlementCoreMenu menu = (SettlementCoreMenu) core.createMenu(0, placer.getInventory(), placer);
			if (!menu.isUnbound() || menu.isMisplaced()) {
				helper.fail("the screen of a core of no town must say so");
				return;
			}
			placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
			stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
			BlockState state = level.getBlockState(at);
			if (state.getDestroyProgress(stranger, level, at) != 0.0F
					|| state.getDestroyProgress(placer, level, at) <= 0.0F) {
				helper.fail("only the player who placed a core of no town may break it");
				return;
			}
			if (!placer.gameMode.destroyBlock(at)) {
				helper.fail("the player who placed the core could not break it");
				return;
			}
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2),
					drop -> drop.getItem().is(ModContent.SETTLEMENT_CORE_ITEM.get()));
			if (drops.size() != 1) {
				helper.fail("the broken core of no town must drop exactly one core item, found " + drops.size());
				return;
			}
			drops.forEach(ItemEntity::discard);

			BlockPos copyAt = CORE.east(2);
			SettlementCoreBlockEntity copy = place(helper, copyAt);
			CompoundTag tag = copy.saveWithoutMetadata(level.registryAccess());
			tag.putBoolean("Unbound", true);
			SaveFormatTestSupport.load(copy, level.registryAccess(), tag);
			charge(copy, copy.getEnergyStorage().getCapacity());
			AlaGameTestHelper.drive(copy, helper, 2);
			BlockPos copyPos = helper.absolutePos(copyAt);
			if (!copy.isUnbound() || copy.settlementId() != 0 || copy.isActive()
					|| registry.findAt(level.dimension(), copyPos).isPresent()) {
				helper.fail("a core of no town must stay of no town on its own ticks: id " + copy.settlementId());
				return;
			}
			if (level.getBlockState(copyPos).getDestroyProgress(stranger, level, copyPos) <= 0.0F) {
				helper.fail("a core of no town whose placer is unknown must be anybody's to break");
				return;
			}
			helper.setBlock(copyAt, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SETTLE-001-REC01 — the core is crafted (MOD-842): the late-stage grid KRK / CAC / BEB (charged
	 * resonant crystals, reactor circuit, resonance coils, advanced machine casing, reinforced bearings, a CESU)
	 * gives exactly one core, and the same grid with a plain machine casing where the CESU goes gives nothing — the
	 * CESU is what makes the core dearer than the workstation built from the same row of parts.
	 */
	public static void tcSettle001Rec01CoreRecipeIsTheLateStageCraft(GameTestHelper helper) {
		ItemStack crystal = new ItemStack(ModContent.RESONANT_CRYSTAL.get());
		ItemStack circuit = new ItemStack(ModContent.REACTOR_CIRCUIT.get());
		ItemStack coil = new ItemStack(ModContent.RESONANCE_COIL.get());
		ItemStack casing = new ItemStack(ModContent.ADVANCED_MACHINE_CASING_ITEM.get());
		ItemStack bearing = new ItemStack(ModContent.REINFORCED_BEARING.get());
		ItemStack cesu = new ItemStack(ModContent.CESU_ITEM.get());
		ItemStack crafted = craft(helper,
				List.of(crystal, circuit, crystal, coil, casing, coil, bearing, cesu, bearing));
		if (!crafted.is(ModContent.SETTLEMENT_CORE_ITEM.get()) || crafted.getCount() != 1) {
			helper.fail("the core's grid must give exactly one settlement core, gave " + crafted);
			return;
		}
		ItemStack cheaper = craft(helper,
				List.of(crystal, circuit, crystal, coil, casing, coil, bearing, casing, bearing));
		if (cheaper.is(ModContent.SETTLEMENT_CORE_ITEM.get())) {
			helper.fail("the core must not be crafted without its CESU");
			return;
		}
		helper.succeed();
	}

	/** What a 3x3 crafting grid gives, or an empty stack when no recipe matches. */
	private static ItemStack craft(GameTestHelper helper, List<ItemStack> grid) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, grid);
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
				.map((RecipeHolder<CraftingRecipe> recipe) -> recipe.value().assemble(input))
				.orElse(ItemStack.EMPTY);
	}
}
