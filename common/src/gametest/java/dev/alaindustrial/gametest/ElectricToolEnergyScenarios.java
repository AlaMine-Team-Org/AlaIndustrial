package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral gametest bodies for the <b>base</b> EU contract shared by the Electric Chainsaw, the
 * Electric Shovel and the Electric Hoe (MOD-364). Six forms, written once and replayed against three
 * tools; the Fabric suites ({@code ElectricChainsawGameTest} and friends) and the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}) both run the SAME bodies.
 *
 * <h2>Why this file exists</h2>
 * The three tools were <b>not</b> untested when MOD-364 was written up — {@link ElectricChainsawScenarios},
 * {@link ElectricShovelScenarios} and {@link ElectricHoeScenarios} already covered the features their own
 * tasks shipped (the diamond-tipped upgrades, dirt paths, campfire dousing, the flat-hoe refusal paths).
 * What none of them touched was the contract every one of these tools is built on, and which the
 * {@link ElectricDrillScenarios} etalon has covered since MOD-079: charging in a Battery Box slot, a drain
 * of exactly one {@code euPerBlock} per block, no drain below that cost, free instant-break blocks, the
 * strict {@code 1.0f} hand-speed collapse, and charge persistence. A grep for
 * {@code electric{Chainsaw,Shovel,Hoe}EuPerBlock} across the three gametest source sets returned nothing
 * at all, which is the measurable form of that gap.
 *
 * <h2>Why the tool is a parameter and not a copy</h2>
 * The six forms are identical in shape across the three tools because the production code is: each item
 * gates {@code getDestroySpeed} on {@code ItemEnergy.get(stack) >= cost} and drains inside
 * {@code mineBlock}. Three copies would mean the fourth tool of the line adds a fourth copy. What is
 * <i>not</i> shared is {@code useOn} — the chainsaw does not override it, the shovel's is free, and the
 * hoe's is charged — so tilling stays as its own bodies in {@link ElectricHoeScenarios}.
 *
 * <h2>How a substituted tool is caught</h2>
 * Parameterisation trades the risk of copy-pasted bodies for the risk of a copy-pasted <i>argument</i>: a
 * {@code CHAINSAW} case wired into the shovel's registrations would pass green and prove nothing about the
 * shovel, and neither {@code gametest_check} (class ↔ entrypoint) nor {@code arch_check} (delegation
 * count) nor {@code gen_test_coverage} (annotation count) can see that. So there is no table of cases
 * here: each {@link ToolCase} is declared <b>once</b>, as a constant inside the suite named after that
 * tool, and the wrappers call a no-argument forwarder from that same file. Writing the wrong item into a
 * case then means writing {@code ModContent.ELECTRIC_CHAINSAW} inside {@code ElectricShovelScenarios} —
 * and {@link #energyCaseRosterIsHonest} fails on exactly that, along with every other way a case can lie
 * about the tool it describes.
 *
 * <p>That guard reads the <i>declarations</i>, though, and the forwarders are what actually pick a case.
 * The constants are {@code public} and the package is one, so
 * {@code drainOnMineBlock(helper, ElectricChainsawScenarios.ENERGY)} written inside the shovel's suite
 * would compile, pass the roster guard untouched, and run the chainsaw twice under the shovel's test
 * names. Closing that is a text rule rather than a runtime one, because at runtime the substituted case
 * is indistinguishable from an honest one: {@code arch_check.py}'s
 * {@code tool-case-referenced-by-simple-name} allows the constant to be addressed only by its bare local
 * name, and permits the qualified {@code <X>Scenarios.ENERGY} spelling in THIS file alone — where the
 * roster above has to name every owner explicitly.
 *
 * <p>Numbers are read through suppliers, never captured into constants: {@code Config} fields are mutable
 * {@code public static int} with a runtime reload, so a value frozen in a static initialiser would be the
 * config-shadow family of bug (MOD-070) wearing a test's clothes.
 */
public final class ElectricToolEnergyScenarios {

	private ElectricToolEnergyScenarios() {}

	/** The Battery Box in the charging form. Same cell the drill etalon uses. */
	private static final BlockPos BOX = new BlockPos(1, 2, 1);
	/** Solid ground under {@link #TARGET}, so a fixture that needs support (a snow layer, a torch) survives. */
	private static final BlockPos SUPPORT = new BlockPos(1, 2, 3);
	/** The cell every mined fixture is placed in. */
	private static final BlockPos TARGET = new BlockPos(1, 3, 3);

	/**
	 * The zero-hardness fixture, shared by all three tools. {@code Blocks.TORCH} is {@code .instabreak()}
	 * in the 26.2 sources, i.e. hardness exactly {@code 0.0} — which is what the drain gate reads. It is
	 * deliberately not "a leafy block": the gate is {@code state.getDestroySpeed(...) != 0.0f} and has
	 * nothing to do with block tags.
	 */
	private static final Block FREE_BLOCK = Blocks.TORCH;

	/** Number of tools on the line that share this contract — the floor guarding a vacuous roster sweep. */
	private static final int EXPECTED_TOOL_CASES = 3;

	/**
	 * One tool's slice of the shared contract. Everything that can change at runtime is a supplier, so a
	 * case describes <i>where to look</i> rather than <i>what the value was when the class loaded</i>.
	 *
	 * @param name       registry path of the tool, used in every failure message so a red run names the
	 *                   tool rather than only the form
	 * @param itemRef    the tool itself ({@code ModContent} handles are unbound until registration)
	 * @param euPerBlockRef  EU drained per block broken at powered speed
	 * @param bufferRef  the tool's whole EU buffer
	 * @param inputRateRef  max EU/t the tool accepts in a charge slot
	 * @param profileRef the tool's own domain — a block in its {@code mineable} tag, of ordinary hardness
	 * @param foreignRef a block outside its domain, the negative half of the drops assertions
	 * @param softRef    a block in its domain with tiny but NON-zero hardness (snow 0.1, leaves 0.2, moss
	 *                   0.1) — the fixture that proves "free" means hardness 0.0 and not "feels flimsy"
	 * @param toolSpeed  mining speed the charged tool reports on its own domain
	 */
	public record ToolCase(
			String name,
			Supplier<Item> itemRef,
			IntSupplier euPerBlockRef,
			IntSupplier bufferRef,
			IntSupplier inputRateRef,
			Supplier<Block> profileRef,
			Supplier<Block> foreignRef,
			Supplier<Block> softRef,
			float toolSpeed) {

		public Item item() {
			return itemRef.get();
		}

		public long euPerBlock() {
			return euPerBlockRef.getAsInt();
		}

		public long buffer() {
			return bufferRef.getAsInt();
		}

		public long inputRate() {
			return inputRateRef.getAsInt();
		}

		public Block profile() {
			return profileRef.get();
		}

		public Block foreign() {
			return foreignRef.get();
		}

		public Block soft() {
			return softRef.get();
		}

		/** A stack of this tool holding {@code eu}. */
		public ItemStack stack(long eu) {
			ItemStack stack = new ItemStack(item());
			ItemEnergy.set(stack, eu);
			return stack;
		}
	}

	// ── the six shared forms ─────────────────────────────────────────────────────────────────────────

	/**
	 * Charging: the tool is accepted by the Battery Box charge slot — by the menu's client-side
	 * {@code mayPlace} <i>and</i> by the server-side {@code canPlaceItem}, which are two separate filters —
	 * and one tick of the box moves {@code min(LV ceiling, the tool's own intake)} EU into it.
	 *
	 * <p>Both filters read {@code ItemEnergy.capacity(stack) > 0}, so a tool that lost its
	 * {@code ItemEnergy} branch is rejected by every charger in the mod; that is the failure this form is
	 * really about, and it is why the slot assertions come before the arithmetic.
	 *
	 * <p>The {@code min(...)} formula is kept even though all three tools currently set
	 * {@code inputRate == 32 == EnergyTier.LV.maxVoltage()}, which makes it an identity today: it will
	 * still be the right expectation the day one of the two moves, and the identity is recorded here so
	 * nobody mistakes this for a proof that the clamp works.
	 */
	public static void chargeInBatteryBox(GameTestHelper helper, ToolCase tool) {
		BatteryBoxBlockEntity box = placeBox(helper, tool);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BatteryBoxMenu menu = new BatteryBoxMenu(0, player.getInventory(), box, ContainerLevelAccess.NULL);
		Slot slot = menu.slots.get(0);
		if (!slot.mayPlace(tool.stack(0))) {
			helper.fail(tool.name() + ": the Battery Box charge slot must accept it (client prediction)");
		}
		if (!box.canPlaceItem(BatteryBoxBlockEntity.CHARGE_SLOT, tool.stack(0))) {
			helper.fail(tool.name() + ": the server-side charge-slot filter must accept it too");
		}

		box.getEnergyStorage().setAmountUntracked(box.getEnergyStorage().getCapacity());
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, tool.stack(0));
		tickBox(helper, box);

		long expected = Math.min(EnergyTier.LV.maxVoltage(), tool.inputRate());
		long gained = ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (gained != expected) {
			helper.fail(tool.name() + ": one tick in the charge slot must move min(LV ceiling, its intake) = "
					+ expected + " EU, got " + gained);
		}
		helper.succeed();
	}

	/** Breaking a block of the tool's own domain drains exactly one {@code euPerBlock}, no more and no less. */
	public static void drainOnMineBlock(GameTestHelper helper, ToolCase tool) {
		ServerLevel level = helper.getLevel();
		BlockState state = placeFixture(helper, tool, tool.profile());
		requireBreakable(helper, tool, state, "the profile block");

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long buffer = tool.buffer();
		ItemStack stack = tool.stack(buffer);

		stack.getItem().mineBlock(stack, level, state, helper.absolutePos(TARGET), player);

		long expected = buffer - tool.euPerBlock();
		if (ItemEnergy.get(stack) != expected) {
			helper.fail(tool.name() + ": breaking one " + blockName(tool.profile()) + " must drain exactly "
					+ tool.euPerBlock() + " EU (" + buffer + " → " + expected + "), left " + ItemEnergy.get(stack));
		}
		helper.succeed();
	}

	/**
	 * One EU below the per-block cost the tool breaks the block for free and at exactly hand speed. Both
	 * halves belong together: "spends nothing" alone would also be true of a tool that had stopped working,
	 * and "runs at 1.0f" alone would also be true of a tool that charged for the privilege.
	 *
	 * <p>The speed is compared with {@code !=} rather than {@code >}: {@code Player.getDestroySpeed} adds
	 * the Efficiency bonus only above {@code 1.0F}, so anything even slightly higher hands a dead tool its
	 * enchantment back. The block under it is the tool's own <b>profile</b> block, never a foreign one —
	 * a charged tool answers {@code 1.0f} on a foreign block too, so that fixture would make the assertion
	 * true no matter what the code did.
	 */
	public static void noDrainBelowCost(GameTestHelper helper, ToolCase tool) {
		ServerLevel level = helper.getLevel();
		BlockState state = placeFixture(helper, tool, tool.profile());
		requireBreakable(helper, tool, state, "the profile block");

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long below = tool.euPerBlock() - 1;
		ItemStack stack = tool.stack(below);

		stack.getItem().mineBlock(stack, level, state, helper.absolutePos(TARGET), player);

		if (ItemEnergy.get(stack) != below) {
			helper.fail(tool.name() + ": below the per-block cost it must spend nothing, charge went "
					+ below + " → " + ItemEnergy.get(stack));
		}
		float speed = tool.item().getDestroySpeed(stack, state);
		if (speed != 1.0f) {
			helper.fail(tool.name() + ": below the per-block cost it must run at EXACTLY hand speed 1.0 on "
					+ blockName(tool.profile()) + " (anything above revives Efficiency), got " + speed);
		}
		helper.succeed();
	}

	/**
	 * A zero-hardness block costs nothing, and a merely <i>soft</i> one costs full price.
	 *
	 * <p>The two halves are one assertion. The free half on its own is the classic vacuous pass: if the
	 * fixture failed to place and the cell were air, {@code getDestroySpeed} would also be {@code 0.0f} and
	 * the tool would also spend nothing — so the block is asserted present and its hardness is asserted
	 * to be exactly {@code 0.0f} before anything is concluded from it. The paid half pins the wording both
	 * {@link dev.alaindustrial.item.tool.ElectricChainsawItem} and
	 * {@link dev.alaindustrial.item.tool.ElectricShovelItem} carry in their javadoc since MOD-389 — leaves
	 * (0.2) and snow layers (0.1) are NOT free — which until now was a claim in a comment with no test
	 * behind it, and had already been wrong once.
	 */
	public static void zeroHardnessFreeSoftBlockCosts(GameTestHelper helper, ToolCase tool) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long buffer = tool.buffer();

		BlockState free = placeFixture(helper, tool, FREE_BLOCK);
		float freeHardness = free.getDestroySpeed(level, helper.absolutePos(TARGET));
		if (freeHardness != 0.0f) {
			helper.fail(tool.name() + ": fixture error — " + blockName(FREE_BLOCK)
					+ " must have hardness exactly 0.0 for this form to mean anything, got " + freeHardness);
		}
		ItemStack onFree = tool.stack(buffer);
		onFree.getItem().mineBlock(onFree, level, free, helper.absolutePos(TARGET), player);
		if (ItemEnergy.get(onFree) != buffer) {
			helper.fail(tool.name() + ": a zero-hardness block (" + blockName(FREE_BLOCK)
					+ ") must cost nothing, charge went " + buffer + " → " + ItemEnergy.get(onFree));
		}

		BlockState soft = placeFixture(helper, tool, tool.soft());
		float softHardness = soft.getDestroySpeed(level, helper.absolutePos(TARGET));
		if (softHardness <= 0.0f) {
			helper.fail(tool.name() + ": fixture error — " + blockName(tool.soft())
					+ " must have NON-zero hardness for the paid half, got " + softHardness);
		}
		ItemStack onSoft = tool.stack(buffer);
		onSoft.getItem().mineBlock(onSoft, level, soft, helper.absolutePos(TARGET), player);
		long expected = buffer - tool.euPerBlock();
		if (ItemEnergy.get(onSoft) != expected) {
			helper.fail(tool.name() + ": " + blockName(tool.soft()) + " (hardness " + softHardness
					+ ") is soft but not free — it must cost the full " + tool.euPerBlock() + " EU ("
					+ buffer + " → " + expected + "), left " + ItemEnergy.get(onSoft));
		}
		helper.succeed();
	}

	/**
	 * Speed and drops across the charge boundary. Three readings on the tool's own profile block —
	 * full buffer, exactly one block's worth of EU, and one EU below that — pin the boundary from both
	 * sides: an off-by-one in the {@code >=} gate moves exactly one of the last two.
	 *
	 * <p>Drops are asserted four ways: correct on the profile block and on the soft domain block, NOT
	 * correct on a foreign one, and still correct when the tool is flat — the mining tier lives in the
	 * {@code TOOL} component, so a discharged tool is slow, never useless. The soft-block reading is also
	 * where the chainsaw's third {@code Tool.Rule} ({@code #minecraft:leaves} at full speed, an addition
	 * vanilla does not make) is checked on the BASE tool for the first time; until now only the
	 * diamond-tipped upgrade was.
	 */
	public static void speedAndDrops(GameTestHelper helper, ToolCase tool) {
		Item item = tool.item();
		BlockState profile = tool.profile().defaultBlockState();
		BlockState foreign = tool.foreign().defaultBlockState();
		BlockState soft = tool.soft().defaultBlockState();
		long cost = tool.euPerBlock();

		float charged = item.getDestroySpeed(tool.stack(tool.buffer()), profile);
		if (charged != tool.toolSpeed()) {
			helper.fail(tool.name() + ": a charged tool must work " + blockName(tool.profile()) + " at "
					+ tool.toolSpeed() + ", got " + charged);
		}
		float atCost = item.getDestroySpeed(tool.stack(cost), profile);
		if (atCost != tool.toolSpeed()) {
			helper.fail(tool.name() + ": with exactly one block's worth of EU (" + cost
					+ ") it must still run at " + tool.toolSpeed() + ", got " + atCost);
		}
		float belowCost = item.getDestroySpeed(tool.stack(cost - 1), profile);
		if (belowCost != 1.0f) {
			helper.fail(tool.name() + ": one EU below the cost (" + (cost - 1)
					+ ") it must collapse to EXACTLY hand speed 1.0, got " + belowCost);
		}
		float softSpeed = item.getDestroySpeed(tool.stack(tool.buffer()), soft);
		if (softSpeed != tool.toolSpeed()) {
			helper.fail(tool.name() + ": " + blockName(tool.soft()) + " is in its domain too and must run at "
					+ tool.toolSpeed() + ", got " + softSpeed);
		}

		assertCorrect(helper, tool, tool.stack(tool.buffer()), profile, blockName(tool.profile()), true);
		assertCorrect(helper, tool, tool.stack(tool.buffer()), soft, blockName(tool.soft()), true);
		assertCorrect(helper, tool, tool.stack(tool.buffer()), foreign, blockName(tool.foreign()), false);
		assertCorrect(helper, tool, tool.stack(0), profile, blockName(tool.profile()) + " (flat tool)", true);
		helper.succeed();
	}

	/** Charge survives a stack copy, 0 EU removes the component again, and a write clamps at capacity. */
	public static void chargeRoundTrip(GameTestHelper helper, ToolCase tool) {
		long some = tool.euPerBlock() + 7;
		ItemStack stack = tool.stack(some);
		ItemStack copy = stack.copy();
		if (ItemEnergy.get(copy) != some) {
			helper.fail(tool.name() + ": a copied stack must keep its charge (" + some + "), got "
					+ ItemEnergy.get(copy));
		}

		ItemEnergy.set(stack, 0);
		if (!ItemStack.matches(stack, new ItemStack(tool.item()))) {
			helper.fail(tool.name() + ": a drained tool must be component-identical to a freshly crafted one");
		}

		ItemEnergy.set(stack, tool.buffer() + 5000);
		if (ItemEnergy.get(stack) != tool.buffer()) {
			helper.fail(tool.name() + ": the buffer must clamp at capacity " + tool.buffer() + ", got "
					+ ItemEnergy.get(stack));
		}
		helper.succeed();
	}

	// ── the roster guard ─────────────────────────────────────────────────────────────────────────────

	/**
	 * The guard that makes the other eighteen tests mean what their names say.
	 *
	 * <p>Every parameterised body is only as honest as the {@link ToolCase} it is handed, and nothing in
	 * the build can see a case that describes the wrong tool: the test still runs, still passes, and still
	 * counts towards {@code COVERAGE.md}. This body is the oracle for that — it walks the three cases and
	 * fails if any of them lies, in any of the ways a case can:
	 *
	 * <ul>
	 * <li>the case declared by a suite is not the case of the tool that suite is named for (the copy-paste
	 *     this whole design is arranged to make visible);</li>
	 * <li>the case's {@code name} is not the tool's real registry path, so a failure message would blame
	 *     the wrong item;</li>
	 * <li>its buffer or intake disagrees with {@link ItemEnergy} — two independent {@code instanceof}
	 *     cascades that have drifted apart before;</li>
	 * <li>its declared speed, profile block or foreign block disagrees with the tool's real {@code TOOL}
	 *     component, which would make {@link #speedAndDrops} assert a fiction;</li>
	 * <li>the numbers are too small for the boundary forms to be boundaries at all
	 *     ({@code euPerBlock - 1} has to be a real, reachable charge below a real cost);</li>
	 * <li>two cases share an item or a profile block, which is what a roster collapsed by copy-paste looks
	 *     like from the outside.</li>
	 * </ul>
	 *
	 * <p>The size floor at the end is the {@code MIN_POWERED_ITEMS} trick from {@link PoweredItemCatalog}:
	 * a sweep over an accidentally-empty roster passes green and proves nothing.
	 */
	public static void energyCaseRosterIsHonest(GameTestHelper helper) {
		record Suite(String owner, ToolCase declared, Item expected) {}

		List<Suite> roster = List.of(
				new Suite("ElectricChainsawScenarios", ElectricChainsawScenarios.ENERGY,
						ModContent.ELECTRIC_CHAINSAW.get()),
				new Suite("ElectricShovelScenarios", ElectricShovelScenarios.ENERGY,
						ModContent.ELECTRIC_SHOVEL.get()),
				new Suite("ElectricHoeScenarios", ElectricHoeScenarios.ENERGY,
						ModContent.ELECTRIC_HOE.get()));

		if (roster.size() != EXPECTED_TOOL_CASES) {
			helper.fail("the shared EU contract covers " + EXPECTED_TOOL_CASES + " tools, the roster holds "
					+ roster.size() + " — a sweep over a shrunken roster proves nothing");
		}

		ServerLevel level = helper.getLevel();
		BlockPos probe = helper.absolutePos(TARGET);
		for (Suite suite : roster) {
			ToolCase tool = suite.declared();
			String where = suite.owner() + " declares ToolCase '" + tool.name() + "'";

			if (tool.item() != suite.expected()) {
				helper.fail(where + " for " + idOf(tool.item()) + ", but that suite is the suite of "
						+ idOf(suite.expected()) + " — every parameterised body in it would silently test "
						+ "the wrong tool and still pass");
			}
			String path = BuiltInRegistries.ITEM.getKey(tool.item()).getPath();
			if (!tool.name().equals(path)) {
				helper.fail(where + ", but the item's registry path is '" + path
						+ "' — every failure message from this case would blame the wrong item");
			}

			ItemStack fresh = new ItemStack(tool.item());
			if (ItemEnergy.capacity(fresh) != tool.buffer()) {
				helper.fail(where + " with buffer " + tool.buffer() + ", ItemEnergy.capacity says "
						+ ItemEnergy.capacity(fresh));
			}
			if (ItemEnergy.inputRate(fresh) != tool.inputRate()) {
				helper.fail(where + " with intake " + tool.inputRate() + ", ItemEnergy.inputRate says "
						+ ItemEnergy.inputRate(fresh));
			}
			if (tool.euPerBlock() < 2) {
				helper.fail(where + " with euPerBlock " + tool.euPerBlock()
						+ " — the boundary forms need at least 2, or 'one EU below the cost' is not a "
						+ "reachable charge");
			}
			if (tool.buffer() <= tool.euPerBlock()) {
				helper.fail(where + " with buffer " + tool.buffer() + " ≤ euPerBlock " + tool.euPerBlock()
						+ " — a full buffer must be able to pay for a block and stay positive");
			}

			ItemStack charged = tool.stack(tool.buffer());
			BlockState profile = tool.profile().defaultBlockState();
			float speed = tool.item().getDestroySpeed(charged, profile);
			if (speed != tool.toolSpeed()) {
				helper.fail(where + " with speed " + tool.toolSpeed() + " on " + blockName(tool.profile())
						+ ", the TOOL component reports " + speed);
			}
			if (!charged.isCorrectToolForDrops(profile)) {
				helper.fail(where + " with " + blockName(tool.profile())
						+ " as its profile block, but the tool is not correct-for-drops there");
			}
			if (charged.isCorrectToolForDrops(tool.foreign().defaultBlockState())) {
				helper.fail(where + " with " + blockName(tool.foreign())
						+ " as its FOREIGN block, but the tool is correct-for-drops there — the negative "
						+ "half of the drops assertions would be vacuous");
			}
			if (!charged.isCorrectToolForDrops(tool.soft().defaultBlockState())) {
				helper.fail(where + " with " + blockName(tool.soft())
						+ " as its soft domain block, but the tool is not correct-for-drops there");
			}
			float softHardness = tool.soft().defaultBlockState().getDestroySpeed(level, probe);
			if (softHardness <= 0.0f) {
				helper.fail(where + " with " + blockName(tool.soft()) + " as its soft block, but its hardness "
						+ "is " + softHardness + " — the paid half of the zero-hardness form needs a "
						+ "non-zero one");
			}
		}

		for (int i = 0; i < roster.size(); i++) {
			for (int j = i + 1; j < roster.size(); j++) {
				ToolCase a = roster.get(i).declared();
				ToolCase b = roster.get(j).declared();
				if (a.item() == b.item()) {
					helper.fail(roster.get(i).owner() + " and " + roster.get(j).owner()
							+ " declare the same tool (" + idOf(a.item()) + ") — one of the two lines of "
							+ "coverage is a duplicate of the other");
				}
				if (a.profile() == b.profile()) {
					helper.fail(roster.get(i).owner() + " and " + roster.get(j).owner()
							+ " share the profile block " + blockName(a.profile())
							+ " — the domains of two different tools must not be the same fixture");
				}
			}
		}
		helper.succeed();
	}

	// ── helpers ──────────────────────────────────────────────────────────────────────────────────────

	private static BatteryBoxBlockEntity placeBox(GameTestHelper helper, ToolCase tool) {
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get());
		BatteryBoxBlockEntity be = helper.getBlockEntity(BOX, BatteryBoxBlockEntity.class);
		if (be == null) {
			helper.fail(tool.name() + ": fixture error — the battery_box block entity is missing");
		}
		return be;
	}

	private static void tickBox(GameTestHelper helper, BatteryBoxBlockEntity be) {
		be.serverTick(helper.getLevel(), be.getBlockPos(), helper.getLevel().getBlockState(be.getBlockPos()));
	}

	/**
	 * Puts {@code block} at {@link #TARGET} on solid ground and asserts it is actually there before the
	 * caller measures anything.
	 *
	 * <p>The pre-assert is the point of this helper. A fixture that does not survive placement — a snow
	 * layer with nothing beneath it, a torch with no support — leaves an AIR cell whose
	 * {@code getDestroySpeed} is {@code 0.0f}, which is indistinguishable from "this block is free" and
	 * would turn the free half of {@link #zeroHardnessFreeSoftBlockCosts} into a test that cannot fail.
	 */
	private static BlockState placeFixture(GameTestHelper helper, ToolCase tool, Block block) {
		helper.setBlock(SUPPORT, Blocks.STONE);
		helper.setBlock(TARGET, block);
		if (!helper.getBlockState(TARGET).is(block)) {
			helper.fail(tool.name() + ": fixture error — " + blockName(block) + " did not survive placement "
					+ "at the target cell, found " + blockName(helper.getBlockState(TARGET).getBlock()));
		}
		return helper.getLevel().getBlockState(helper.absolutePos(TARGET));
	}

	/** Fails if the fixture is instant-break, which would make a drain assertion vacuous. */
	private static void requireBreakable(GameTestHelper helper, ToolCase tool, BlockState state, String what) {
		float hardness = state.getDestroySpeed(helper.getLevel(), helper.absolutePos(TARGET));
		if (hardness <= 0.0f) {
			helper.fail(tool.name() + ": fixture error — " + what + " (" + blockName(state.getBlock())
					+ ") has hardness " + hardness + ", so no drain could ever be charged for it");
		}
	}

	private static void assertCorrect(GameTestHelper helper, ToolCase tool, ItemStack stack, BlockState state,
			String what, boolean expected) {
		if (stack.isCorrectToolForDrops(state) != expected) {
			helper.fail(tool.name() + ": isCorrectToolForDrops(" + what + ") must be " + expected);
		}
	}

	private static String blockName(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	private static String idOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
