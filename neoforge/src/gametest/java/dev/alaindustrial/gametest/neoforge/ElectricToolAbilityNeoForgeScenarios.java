package dev.alaindustrial.gametest.neoforge;

import dev.alaindustrial.gametest.AlaGameTestHelper;
import dev.alaindustrial.registry.ModContent;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * NeoForge-only characterization of the four right-click tools on Minecraft 26.2 (MOD-704, batch 0): the
 * {@code ItemAbility} answers their NeoForge subclasses give other mods, and the side-effect-free probe the
 * hoe asks before its charge gate.
 *
 * <p>Nothing tested the NeoForge subclasses directly before: the hoe and shovel suites see only the
 * outcome of a click. These bodies pin what the batch-2 spike of MOD-704 is about to touch — which class
 * answers {@code canPerformAction}, and how — so a change to that mechanism must leave every answer here as
 * it is. The bodies use the loader's own API ({@code ItemAbility}, {@code getToolModifiedState}), which
 * common code cannot import, so they live in this source set and are registered by
 * {@code LineOnlyRegistrations} of this line only: on 26.3 the mechanism does not exist.
 */
public final class ElectricToolAbilityNeoForgeScenarios {

	private ElectricToolAbilityNeoForgeScenarios() {}

	private static final List<Supplier<Item>> HOES = List.of(ModContent.ELECTRIC_HOE,
			ModContent.ELECTRIC_HOE_DIAMOND_TIP);
	private static final List<Supplier<Item>> SHOVELS = List.of(ModContent.ELECTRIC_SHOVEL,
			ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP);

	/** The plot every probe looks at, with air forced above it. */
	private static final BlockPos PLOT = new BlockPos(1, 2, 1);

	/** Fewer registered abilities than this means the sweep below is reading an empty registry. */
	private static final int MIN_ABILITIES = 10;

	/**
	 * @implements MOD-704-RC04 — on NeoForge 26.2, for every registered {@code ItemAbility}, each electric
	 *     hoe answers {@code canPerformAction} exactly as a vanilla diamond hoe does and exactly
	 *     {@code DEFAULT_HOE_ACTIONS} membership; each electric shovel exactly as a vanilla diamond shovel
	 *     and {@code DEFAULT_SHOVEL_ACTIONS} membership — so other mods asking about our tools get today's
	 *     answers.
	 */
	public static void abilitiesAnswerAsTheVanillaTools(GameTestHelper helper) {
		// ItemAbility.get registers on first use: touching ItemAbilities first fills the registry with every
		// stock ability, so the sweep below sees all of them and not only those someone asked for already.
		Set<ItemAbility> hoeActions = ItemAbilities.DEFAULT_HOE_ACTIONS;
		Collection<ItemAbility> abilities = List.copyOf(ItemAbility.getActions());
		if (abilities.size() < MIN_ABILITIES) {
			helper.fail("only " + abilities.size() + " item abilities registered — the sweep would prove nothing");
			return;
		}
		String problem = sweep(abilities, HOES, new ItemStack(Items.DIAMOND_HOE), hoeActions);
		if (problem == null) {
			problem = sweep(abilities, SHOVELS, new ItemStack(Items.DIAMOND_SHOVEL),
					ItemAbilities.DEFAULT_SHOVEL_ACTIONS);
		}
		if (problem != null) {
			helper.fail(problem);
			return;
		}
		helper.succeed();
	}

	private static String sweep(Collection<ItemAbility> abilities, List<Supplier<Item>> tools, ItemStack vanilla,
			Set<ItemAbility> declared) {
		for (Supplier<Item> tool : tools) {
			ItemStack stack = new ItemStack(tool.get());
			for (ItemAbility ability : abilities) {
				boolean ours = stack.canPerformAction(ability);
				if (ours != declared.contains(ability) || ours != vanilla.canPerformAction(ability)) {
					return stack.getItem() + ".canPerformAction(" + ability.name() + ") = " + ours
							+ ", the declared set says " + declared.contains(ability) + ", a vanilla "
							+ vanilla.getItem() + " says " + vanilla.canPerformAction(ability);
				}
			}
		}
		return null;
	}

	/** Puts {@code state} on the plot (air above) and asks it, through {@code stack} in hand, what it becomes. */
	private static BlockState probe(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockState state,
			Direction face, ItemAbility ability) {
		helper.setBlock(PLOT, state);
		helper.setBlock(PLOT.above(), Blocks.AIR);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = helper.absolutePos(PLOT);
		UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false));
		return helper.getLevel().getBlockState(abs).getToolModifiedState(context, ability, /*simulate*/ true);
	}

	/**
	 * @implements MOD-704-RC05 — on NeoForge 26.2 the simulated tool modification through a held electric
	 *     tool (the probe the hoe asks before its charge gate) answers farmland for dirt from UP and from
	 *     DOWN (this loader's {@code HOE_TILL} copy has no face check — vanilla's rules refuse DOWN), dirt for
	 *     rooted dirt, nothing for stone, a path for grass and an unlit campfire for a lit one — and writes
	 *     nothing: the block stays and no hanging root is dropped.
	 */
	public static void simulatedProbeAnswersAndWritesNothing(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		BlockState lit = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, Boolean.TRUE);
		record Case(Supplier<Item> tool, BlockState state, Direction face, ItemAbility ability, BlockState expected) {}
		List<Case> cases = List.of(
				new Case(ModContent.ELECTRIC_HOE, Blocks.DIRT.defaultBlockState(), Direction.UP, ItemAbilities.HOE_TILL,
						Blocks.FARMLAND.defaultBlockState()),
				new Case(ModContent.ELECTRIC_HOE, Blocks.DIRT.defaultBlockState(), Direction.DOWN,
						ItemAbilities.HOE_TILL, Blocks.FARMLAND.defaultBlockState()),
				new Case(ModContent.ELECTRIC_HOE_DIAMOND_TIP, Blocks.ROOTED_DIRT.defaultBlockState(), Direction.UP,
						ItemAbilities.HOE_TILL, Blocks.DIRT.defaultBlockState()),
				new Case(ModContent.ELECTRIC_HOE, Blocks.STONE.defaultBlockState(), Direction.UP,
						ItemAbilities.HOE_TILL, null),
				new Case(ModContent.ELECTRIC_SHOVEL, Blocks.GRASS_BLOCK.defaultBlockState(), Direction.UP,
						ItemAbilities.SHOVEL_FLATTEN, Blocks.DIRT_PATH.defaultBlockState()),
				new Case(ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP, lit, Direction.UP, ItemAbilities.SHOVEL_DOUSE,
						lit.setValue(CampfireBlock.LIT, Boolean.FALSE)));
		for (Case c : cases) {
			BlockState answer = probe(helper, player, new ItemStack(c.tool().get()), c.state(), c.face(), c.ability());
			String cell = c.tool().get() + " " + c.ability().name() + " on " + c.state() + " from " + c.face();
			if (answer != c.expected()) {
				helper.fail(cell + " answered " + answer + ", expected " + c.expected());
				return;
			}
			if (helper.getBlockState(PLOT) != c.state()) {
				helper.fail(cell + " is a simulation but changed the block to " + helper.getBlockState(PLOT));
				return;
			}
		}
		helper.assertItemEntityNotPresent(Items.HANGING_ROOTS, PLOT, 3.0);
		helper.succeed();
	}
}
