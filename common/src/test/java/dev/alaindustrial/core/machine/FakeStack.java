package dev.alaindustrial.core.machine;

/**
 * A stand-in for {@code ItemStack} on the Minecraft-free L1 classpath (MOD-714): an item, a count and a
 * component string. {@link Ops} mirrors the {@code ItemStack} calls {@link StackOps} names, including
 * the two details the planner depends on — every empty stack counts as empty, but only {@link #EMPTY}
 * is the canonical one, and a copy keeps the components.
 */
record FakeStack(Thing item, int count, String components) {

	/** The canonical empty stack, the counterpart of {@code ItemStack.EMPTY}. */
	static final FakeStack EMPTY = new FakeStack(null, 0, "");

	/** The items of the fake world, each with its own maximum stack size. */
	enum Thing {
		OAK_PLANKS(64), BIRCH_PLANKS(64), SPRUCE_PLANKS(64), STICK(64), COBBLESTONE(64),
		HONEY_BOTTLE(16), GLASS_BOTTLE(64), HAMMER(1);

		final int max;

		Thing(int max) {
			this.max = max;
		}
	}

	static FakeStack of(Thing item, int count) {
		return new FakeStack(item, count, "");
	}

	static FakeStack of(Thing item, int count, String components) {
		return new FakeStack(item, count, components);
	}

	boolean empty() {
		return item == null || count <= 0;
	}

	/** {@link StackOps} over {@link FakeStack}, one method per {@code ItemStack} call. */
	static final class Ops implements StackOps<FakeStack, Thing> {
		static final Ops INSTANCE = new Ops();

		@Override
		public FakeStack empty() {
			return EMPTY;
		}

		@Override
		public boolean isEmpty(FakeStack stack) {
			return stack.empty();
		}

		@Override
		public int count(FakeStack stack) {
			return stack.empty() ? 0 : stack.count;
		}

		@Override
		public int maxStackSize(FakeStack stack) {
			return stack.empty() ? 1 : stack.item.max;
		}

		@Override
		public Thing item(FakeStack stack) {
			return stack.empty() ? null : stack.item;
		}

		@Override
		public boolean sameItem(FakeStack a, FakeStack b) {
			return item(a) == item(b);
		}

		@Override
		public boolean sameItemSameComponents(FakeStack a, FakeStack b) {
			return sameItem(a, b) && a.components.equals(b.components);
		}

		@Override
		public FakeStack copyWithCount(FakeStack stack, int count) {
			return stack.empty() ? EMPTY : new FakeStack(stack.item, count, stack.components);
		}
	}
}
