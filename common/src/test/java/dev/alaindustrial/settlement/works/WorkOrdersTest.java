package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 of the town's orders (round 2 of 0.1.219, Q1–Q3): which buildings are orders, in which order, and who goes
 * first.
 */
class WorkOrdersTest {

	@Test
	void ordersAreTheBuildingsWithOpenWorkInTheOwnersOrder() {
		WorkQueue queue = new WorkQueue();
		queue.raise(WorkJobType.CLEAR, 4);
		queue.raise(WorkJobType.BUILD, 2);
		queue.raise(WorkJobType.BUILD, 7);
		// The owner put 7 first; 4 and 2 were never placed: they follow by their oldest job; 9 has no work.
		assertEquals(List.of(7, 4, 2), WorkOrders.effective(List.of(7, 9), queue.jobs()));
	}

	@Test
	void aFinishedOrderLeavesAndNothingTakesItsPlaceByItself() {
		WorkQueue queue = new WorkQueue();
		WorkJob first = queue.raise(WorkJobType.BUILD, 1).orElseThrow();
		queue.take(first.id(), "drone");
		queue.complete(first.id(), "drone");
		assertEquals(List.of(), WorkOrders.effective(List.of(1, 2), queue.jobs()));
	}

	@Test
	void aFreeDroneTakesTheFirstOrderNobodyWorksOn() {
		WorkQueue queue = new WorkQueue();
		queue.raise(WorkJobType.BUILD, 1);
		queue.raise(WorkJobType.BUILD, 2);
		queue.raise(WorkJobType.BUILD, 3);
		List<Integer> orders = List.of(3, 1, 2);
		Set<WorkJobType> all = Set.of(WorkJobType.values());
		assertEquals(3, queue.takeNext("a", all, b -> WorkOrders.rank(orders, b)).orElseThrow().building());
		assertEquals(1, queue.takeNext("b", all, b -> WorkOrders.rank(orders, b)).orElseThrow().building());
	}

	@Test
	void movingKeepsTheOrderWithinTheList() {
		assertEquals(List.of(2, 1, 3), WorkOrders.move(List.of(1, 2, 3), 2, -1));
		assertEquals(List.of(2, 3, 1), WorkOrders.move(List.of(1, 2, 3), 1, 9));
		assertEquals(List.of(1, 2, 3), WorkOrders.move(List.of(1, 2, 3), 5, -1));
		assertEquals(WorkOrders.NO_ORDER, WorkOrders.rank(List.of(1), 5));
	}
}
