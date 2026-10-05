package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.machine.FakeStack.Thing;
import dev.alaindustrial.core.machine.ScratchInventory.Reserved;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * jqwik invariants of {@link ScratchInventory} (MOD-714), over random scratch lists of up to nine
 * slots drawn from a few items and two component variants — few enough that merges, full stacks and
 * refusals all happen often.
 *
 * <ul>
 *   <li>{@code insertInto} returning {@code false} leaves the list exactly as it was;</li>
 *   <li>{@code insertInto} returning {@code true} adds exactly the stack's count of that item and those
 *       components, touches no other kind of stack, and leaves no slot above its maximum;</li>
 *   <li>{@code findAndTake} takes exactly one unit of the wanted item, or finds none and changes nothing.</li>
 * </ul>
 */
class ScratchInventoryPropertyTest {

	private static final FakeStack.Ops OPS = FakeStack.Ops.INSTANCE;
	private static final List<Thing> ITEMS =
			List.of(Thing.OAK_PLANKS, Thing.STICK, Thing.HONEY_BOTTLE, Thing.HAMMER);

	@Provide
	Arbitrary<FakeStack> stack() {
		Arbitrary<Thing> item = Arbitraries.of(ITEMS);
		Arbitrary<String> components = Arbitraries.of("", "named");
		Arbitrary<Integer> fraction = Arbitraries.integers().between(1, 64);
		return Combinators.combine(item, components, fraction)
				.as((thing, tag, n) -> FakeStack.of(thing, 1 + (n - 1) % thing.max, tag));
	}

	@Provide
	Arbitrary<List<FakeStack>> scratch() {
		Arbitrary<FakeStack> slot = Arbitraries.oneOf(Arbitraries.just(FakeStack.EMPTY), stack());
		return slot.list().ofMinSize(1).ofMaxSize(9).map(ArrayList::new);
	}

	private static int total(List<FakeStack> scratch, FakeStack kind) {
		int sum = 0;
		for (FakeStack s : scratch) {
			if (!s.empty() && OPS.sameItemSameComponents(s, kind)) {
				sum += s.count();
			}
		}
		return sum;
	}

	private static int totalOfItem(List<FakeStack> scratch, Thing item) {
		int sum = 0;
		for (FakeStack s : scratch) {
			if (!s.empty() && s.item() == item) {
				sum += s.count();
			}
		}
		return sum;
	}

	@Property
	void insertIntoKeepsItsWord(@ForAll("scratch") List<FakeStack> scratch, @ForAll("stack") FakeStack stack) {
		List<FakeStack> before = List.copyOf(scratch);
		boolean fitted = ScratchInventory.insertInto(OPS, scratch, stack);
		if (!fitted) {
			assertEquals(before, scratch, "a refused insert must leave the scratch untouched");
			return;
		}
		assertEquals(before.size(), scratch.size());
		assertEquals(total(before, stack) + stack.count(), total(scratch, stack),
				"a successful insert adds exactly the stack's count");
		for (int i = 0; i < scratch.size(); i++) {
			FakeStack now = scratch.get(i);
			assertTrue(now.empty() || now.count() <= now.item().max, "slot " + i + " over its maximum: " + now);
			FakeStack was = before.get(i);
			boolean sameKind = !now.empty() && OPS.sameItemSameComponents(now, stack);
			if (!sameKind) {
				assertEquals(was, now, "slot " + i + " holds another kind of stack and must not change");
			}
		}
	}

	@Property
	void findAndTakeTakesExactlyOne(@ForAll("scratch") List<FakeStack> scratch, @ForAll("stack") FakeStack want) {
		List<FakeStack> before = List.copyOf(scratch);
		Optional<Reserved<FakeStack>> taken = ScratchInventory.findAndTake(OPS, scratch, want);
		if (taken.isEmpty()) {
			assertEquals(0, totalOfItem(before, want.item()), "nothing taken although the item is there");
			assertEquals(before, scratch);
			return;
		}
		Reserved<FakeStack> reserved = taken.get();
		assertEquals(1, reserved.stack().count());
		assertEquals(want.item(), reserved.stack().item());
		assertEquals(totalOfItem(before, want.item()) - 1, totalOfItem(scratch, want.item()),
				"exactly one unit leaves the warehouse");
		for (int i = 0; i < reserved.slot(); i++) {
			assertFalse(!before.get(i).empty() && before.get(i).item() == want.item(),
					"a lower slot " + i + " held the item and was passed over");
		}
	}
}
