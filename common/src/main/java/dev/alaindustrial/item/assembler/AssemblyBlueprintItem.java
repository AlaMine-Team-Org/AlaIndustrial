package dev.alaindustrial.item.assembler;

import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Assembly Blueprint (MOD-275) — one item with two states. Blank out of the crafting grid, it becomes
 * a recorded blueprint once the Assembler writes a 3×3 layout onto it as a
 * {@link BlueprintPattern} component.
 *
 * <p>One item rather than two: the states differ only by a component, and the player moves the same
 * object between the grid and the machine. A recorded blueprint does not stack — two items that look
 * alike but hold different layouts is exactly the confusion that ends with the wrong recipe running —
 * so {@link #record(ItemStack, BlueprintPattern) record} pins the recorded stack to 1, while blanks keep stacking.
 */
public class AssemblyBlueprintItem extends Item {
	/** Blanks stack; a recorded blueprint never does. */
	public static final int BLANK_STACK_SIZE = 16;

	public AssemblyBlueprintItem(Properties properties) {
		super(properties);
	}

	/** The layout recorded on this stack, or {@link BlueprintPattern#EMPTY} for a blank. */
	public static BlueprintPattern patternOf(ItemStack stack) {
		BlueprintPattern pattern = stack.get(ModDataComponents.BLUEPRINT_PATTERN.get());
		return pattern == null ? BlueprintPattern.EMPTY : pattern;
	}

	/** True once a layout has been written — the item is a blueprint rather than a blank. */
	public static boolean isRecorded(ItemStack stack) {
		return !patternOf(stack).isBlank();
	}

	/**
	 * What this blueprint makes, as the machine resolved it when the blueprint was written, or empty
	 * when nothing was cached (see {@link ModDataComponents#BLUEPRINT_RESULT} for why it is a cache).
	 *
	 * <p>The component holds an immutable {@link ItemStackTemplate} (a data-component value must be
	 * immutable and value-comparable — see {@link ModDataComponents#BLUEPRINT_RESULT}); every call builds
	 * a fresh {@link ItemStack} from it, so a render path can never scribble on the stored value.
	 */
	public static ItemStack resultOf(ItemStack stack) {
		ItemStackTemplate cached = stack.get(ModDataComponents.BLUEPRINT_RESULT.get());
		return cached == null ? ItemStack.EMPTY : cached.create();
	}

	/**
	 * Whether this blueprint is allowed to substitute ingredients (MOD-275). Absent component = off:
	 * substitution ignores item components, so it stays opt-in per blueprint.
	 */
	public static boolean substitutes(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(ModDataComponents.BLUEPRINT_SUBSTITUTE.get()));
	}

	/**
	 * Flip the substitution flag on a recorded blueprint and report the new state.
	 *
	 * <p>Turning it off <b>removes</b> the component rather than storing {@code false}, so a blueprint
	 * that was never touched and one that was switched off again are the same item — otherwise two
	 * blueprints of the same recipe would stop comparing equal for no reason a player can see.
	 * Blanks are refused: there is no recipe for the flag to apply to.
	 *
	 * @return the state after the flip, or {@code false} when nothing was changed
	 */
	public static boolean toggleSubstitution(ItemStack stack) {
		if (!isRecorded(stack)) {
			return false;
		}
		boolean next = !substitutes(stack);
		if (next) {
			stack.set(ModDataComponents.BLUEPRINT_SUBSTITUTE.get(), Boolean.TRUE);
		} else {
			stack.remove(ModDataComponents.BLUEPRINT_SUBSTITUTE.get());
		}
		return next;
	}

	/**
	 * Stamp a recorded layout onto a blank, pinning the stack to 1 on the way.
	 *
	 * <p>In 26.2 the maximum stack size is a data component on the stack, not a method on the item, so
	 * "blanks stack, blueprints do not" is written here rather than overridden: {@code max_stack_size}
	 * goes onto the recorded stack alongside the layout. Two blueprints that look alike but hold
	 * different layouts would otherwise merge — and the machine would quietly run the wrong recipe.
	 *
	 * <p>Layout only — no cached result, so the item can say it holds a recipe but not which one. Used
	 * where the caller has no {@code ServerLevel} to resolve one with; the machine's "Write" button uses
	 * {@link #record(ItemStack, BlueprintPattern, ItemStack)} instead.
	 */
	public static ItemStack record(ItemStack blank, BlueprintPattern pattern) {
		return record(blank, pattern, ItemStack.EMPTY);
	}

	/**
	 * Stamp a layout onto a blank together with what it makes.
	 *
	 * <p>The result rides along purely so the tooltip and the machine window can name it: the client has
	 * no way to solve a crafting recipe in 26.2, and the machine itself keeps re-solving the layout on
	 * every operation rather than trusting this. An empty {@code result} leaves the component off
	 * entirely — {@code ItemStackTemplate.fromNonEmptyStack} throws on an empty stack, and "no result
	 * known" is exactly what an absent component means.
	 */
	public static ItemStack record(ItemStack blank, BlueprintPattern pattern, ItemStack result) {
		ItemStack recorded = blank.copyWithCount(1);
		recorded.set(ModDataComponents.BLUEPRINT_PATTERN.get(), pattern);
		recorded.set(DataComponents.MAX_STACK_SIZE, 1);
		if (!result.isEmpty()) {
			recorded.set(ModDataComponents.BLUEPRINT_RESULT.get(), ItemStackTemplate.fromNonEmptyStack(result));
		}
		return recorded;
	}

	/**
	 * "Blank — write it in the Assembler", or what the recorded blueprint makes: <i>Blueprint: Stick
	 * ×4</i>.
	 *
	 * <p>Naming the product is the whole point — a row of blueprints that all read "recipe recorded" is
	 * a row of identical items the player has to insert one by one to tell apart. It falls back to that
	 * generic line only when there is genuinely nothing to name: a blueprint written before the result
	 * was cached at all.
	 */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		if (!isRecorded(stack)) {
			adder.accept(Component.translatable("tooltip.alaindustrial.assembly_blueprint.blank")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		ItemStack result = resultOf(stack);
		if (result.isEmpty()) {
			adder.accept(Component.translatable("tooltip.alaindustrial.assembly_blueprint.recorded")
					.withStyle(ChatFormatting.AQUA));
		} else {
			adder.accept(Component.translatable("tooltip.alaindustrial.assembly_blueprint.result",
					result.getHoverName(), result.getCount()).withStyle(ChatFormatting.AQUA));
		}
		// Only when it is on: an extra grey line under every blueprint in the chest would cost more than
		// it tells, and off is the default state anyway.
		if (substitutes(stack)) {
			adder.accept(Component.translatable("tooltip.alaindustrial.assembly_blueprint.substitute")
					.withStyle(ChatFormatting.GRAY));
		}
	}
}
