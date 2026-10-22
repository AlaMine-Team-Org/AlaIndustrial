package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.WorkQueue.Outcome;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 for a town's work queue (MOD-857, epic decisions R79, R85, R91): raising, taking, giving back with progress,
 * blocking with a reason, starting again, calling off, finishing, and the rules of who may take what.
 */
class WorkQueueTest {

	private static final Set<WorkJobType> ALL = Set.of(WorkJobType.values());
	private static final String DRONE_A = "drone:1,2,3";
	private static final String DRONE_B = "drone:4,5,6";

	@Test
	void aRaisedJobIsQueuedAndASecondOfTheSameKindIsRefused() {
		WorkQueue queue = new WorkQueue();
		WorkJob job = queue.raise(WorkJobType.CLEAR, 0).orElseThrow();
		assertEquals(WorkJobStatus.QUEUED, job.status());
		assertEquals(1, job.id());
		assertTrue(queue.raise(WorkJobType.CLEAR, 0).isEmpty(), "two open clearing jobs on one building");
		assertTrue(queue.raise(WorkJobType.BUILD, 0).isPresent(), "a building job beside the clearing one");
		assertTrue(queue.raise(WorkJobType.CLEAR, 1).isPresent(), "a clearing job on another building");
	}

	@Test
	void takeNextHandsOutTheOldestQueuedJobAndTheTakerKeepsIt() {
		WorkQueue queue = new WorkQueue();
		queue.raise(WorkJobType.CLEAR, 0);
		queue.raise(WorkJobType.CLEAR, 1);
		WorkJob taken = queue.takeNext(DRONE_A, ALL).orElseThrow();
		assertEquals(1, taken.id());
		assertEquals(WorkJobStatus.TAKEN, taken.status());
		assertEquals(DRONE_A, taken.taker());
		assertEquals(taken, queue.takeNext(DRONE_A, ALL).orElseThrow(), "a taker asking again gets its own job");
		assertEquals(2, queue.takeNext(DRONE_B, ALL).orElseThrow().id());
		assertTrue(queue.takeNext("drone:7,8,9", ALL).isEmpty(), "nothing left to take");
	}

	@Test
	void takeNextFiltersByType() {
		WorkQueue queue = new WorkQueue();
		queue.raise(WorkJobType.CLEAR, 0);
		assertTrue(queue.takeNext(DRONE_A, Set.of(WorkJobType.BUILD)).isEmpty());
		assertTrue(queue.takeNext(DRONE_A, Set.of(WorkJobType.CLEAR)).isPresent());
	}

	@Test
	void oneTakerPerBuilding() {
		WorkQueue queue = new WorkQueue();
		WorkJob clear = queue.raise(WorkJobType.CLEAR, 0).orElseThrow();
		queue.raise(WorkJobType.CLEAR, 1);
		assertEquals(clear.id(), queue.takeNext(DRONE_A, ALL).orElseThrow().id());
		queue.complete(clear.id(), DRONE_A);
		WorkJob build = queue.raise(WorkJobType.BUILD, 1).orElseThrow();
		assertEquals(2, queue.takeNext(DRONE_A, ALL).orElseThrow().id());
		assertEquals(Outcome.BUSY, queue.take(build.id(), DRONE_B), "another drone already works on building 1");
	}

	@Test
	void theBuildingJobWaitsForItsBuildingsDebris() {
		WorkQueue queue = new WorkQueue();
		WorkJob build = queue.raise(WorkJobType.BUILD, 0).orElseThrow();
		WorkJob clear = queue.raise(WorkJobType.CLEAR, 0).orElseThrow();
		assertEquals(clear.id(), queue.takeNext(DRONE_A, ALL).orElseThrow().id(),
				"the older building job must wait for the clearing one");
		assertEquals(Outcome.OK, queue.complete(clear.id(), DRONE_A));
		assertEquals(build.id(), queue.takeNext(DRONE_A, ALL).orElseThrow().id());
	}

	@Test
	void aJobGivenBackKeepsItsProgressForTheNextTaker() {
		WorkQueue queue = new WorkQueue();
		WorkJob job = queue.raise(WorkJobType.BUILD, 0).orElseThrow();
		queue.takeNext(DRONE_A, ALL);
		assertEquals(Outcome.OK, queue.progress(job.id(), DRONE_A, 40));
		assertEquals(40, queue.job(job.id()).orElseThrow().progress());
		assertEquals(Outcome.OK, queue.giveBack(job.id(), DRONE_A, 57));
		WorkJob back = queue.job(job.id()).orElseThrow();
		assertEquals(WorkJobStatus.QUEUED, back.status());
		assertEquals("", back.taker());
		assertEquals(57, back.progress());
		WorkJob again = queue.takeNext(DRONE_B, ALL).orElseThrow();
		assertEquals(57, again.progress(), "the next taker goes on from the progress");
	}

	@Test
	void onlyTheTakerReportsGivesBackBlocksOrFinishes() {
		WorkQueue queue = new WorkQueue();
		WorkJob job = queue.raise(WorkJobType.CLEAR, 0).orElseThrow();
		assertEquals(Outcome.NOT_YOURS, queue.progress(job.id(), DRONE_A, 1), "nobody took it yet");
		queue.takeNext(DRONE_A, ALL);
		assertEquals(Outcome.NOT_YOURS, queue.progress(job.id(), DRONE_B, 1));
		assertEquals(Outcome.NOT_YOURS, queue.giveBack(job.id(), DRONE_B, 1));
		assertEquals(Outcome.NOT_YOURS, queue.block(job.id(), DRONE_B, BlockReason.of(BlockReason.NO_PLAN), 1));
		assertEquals(Outcome.NOT_YOURS, queue.complete(job.id(), DRONE_B));
		assertEquals(Outcome.UNKNOWN_JOB, queue.complete(99, DRONE_A));
		assertEquals(WorkJobStatus.TAKEN, queue.job(job.id()).orElseThrow().status());
	}

	@Test
	void aBlockedJobCarriesItsReasonAndWaitsUntilStartedAgain() {
		WorkQueue queue = new WorkQueue();
		WorkJob job = queue.raise(WorkJobType.BUILD, 0).orElseThrow();
		queue.takeNext(DRONE_A, ALL);
		BlockReason reason = BlockReason.noMaterials(List.of("12", "block.minecraft.bricks"));
		assertEquals(Outcome.OK, queue.block(job.id(), DRONE_A, reason, 33));
		WorkJob blocked = queue.job(job.id()).orElseThrow();
		assertEquals(WorkJobStatus.BLOCKED, blocked.status());
		assertEquals(Optional.of(reason), blocked.reason());
		assertEquals("", blocked.taker(), "a blocked job is nobody's");
		assertEquals(33, blocked.progress());
		assertTrue(queue.takeNext(DRONE_B, ALL).isEmpty(), "takeNext never hands out a blocked job");
		assertEquals(Outcome.OK, queue.unblock(job.id()));
		WorkJob queued = queue.job(job.id()).orElseThrow();
		assertEquals(WorkJobStatus.QUEUED, queued.status());
		assertTrue(queued.reason().isEmpty(), "starting again drops the reason");
		assertEquals(33, queued.progress());
		assertEquals(Outcome.WRONG_STATUS, queue.unblock(job.id()), "only a blocked job starts again");
	}

	@Test
	void aBlockedJobMayBeTakenByNumber() {
		WorkQueue queue = new WorkQueue();
		WorkJob job = queue.raise(WorkJobType.BUILD, 0).orElseThrow();
		queue.takeNext(DRONE_A, ALL);
		queue.block(job.id(), DRONE_A, BlockReason.of(BlockReason.CHUNK_NOT_LOADED), 5);
		assertEquals(Outcome.OK, queue.take(job.id(), DRONE_A));
		WorkJob retaken = queue.job(job.id()).orElseThrow();
		assertEquals(WorkJobStatus.TAKEN, retaken.status());
		assertTrue(retaken.reason().isEmpty());
		assertEquals(5, retaken.progress());
	}

	@Test
	void oneJobPerTaker() {
		WorkQueue queue = new WorkQueue();
		queue.raise(WorkJobType.CLEAR, 0);
		WorkJob other = queue.raise(WorkJobType.CLEAR, 1).orElseThrow();
		queue.takeNext(DRONE_A, ALL);
		assertEquals(Outcome.BUSY, queue.take(other.id(), DRONE_A));
	}

	@Test
	void aCancelledJobTellsItsTakerOnItsNextCall() {
		WorkQueue queue = new WorkQueue();
		WorkJob job = queue.raise(WorkJobType.CLEAR, 0).orElseThrow();
		queue.takeNext(DRONE_A, ALL);
		assertEquals(Outcome.OK, queue.cancel(job.id()));
		assertEquals(WorkJobStatus.CANCELLED, queue.job(job.id()).orElseThrow().status());
		assertEquals(Outcome.NOT_YOURS, queue.progress(job.id(), DRONE_A, 9));
		assertEquals(Outcome.WRONG_STATUS, queue.cancel(job.id()), "a closed job cannot be called off twice");
		assertTrue(queue.raise(WorkJobType.CLEAR, 0).isPresent(), "a cancelled job no longer blocks a new one");
		assertTrue(queue.heldBy(DRONE_A).isEmpty());
	}

	@Test
	void aGoneTakersJobsReturnWithTheirProgress() {
		WorkQueue queue = new WorkQueue();
		WorkJob job = queue.raise(WorkJobType.CLEAR, 0).orElseThrow();
		queue.takeNext(DRONE_A, ALL);
		queue.progress(job.id(), DRONE_A, 12);
		assertEquals(1, queue.releaseTaker(DRONE_A));
		WorkJob back = queue.job(job.id()).orElseThrow();
		assertEquals(WorkJobStatus.QUEUED, back.status());
		assertEquals(12, back.progress());
		assertEquals(0, queue.releaseTaker(DRONE_A));
	}

	@Test
	void historyIsTrimmedToItsLimitOldestFirst() {
		WorkQueue queue = new WorkQueue();
		for (int i = 0; i < WorkQueue.HISTORY + 4; i++) {
			int id = queue.raise(WorkJobType.CLEAR, i).orElseThrow().id();
			queue.cancel(id);
		}
		WorkJob open = queue.raise(WorkJobType.BUILD, 0).orElseThrow();
		assertEquals(WorkQueue.HISTORY + 1, queue.jobs().size());
		assertTrue(queue.job(1).isEmpty(), "the oldest history went first");
		assertTrue(queue.job(open.id()).isPresent(), "an open job is never trimmed");
		assertEquals(WorkQueue.HISTORY + 6, queue.nextId(), "numbers are never reused");
	}

	@Test
	void everyChangeCallsTheHookAndNoChangeDoesNot() {
		WorkQueue queue = new WorkQueue();
		int[] calls = {0};
		queue.onChange(() -> calls[0]++);
		WorkJob job = queue.raise(WorkJobType.CLEAR, 0).orElseThrow();
		assertEquals(1, calls[0]);
		queue.takeNext(DRONE_A, ALL);
		assertEquals(2, calls[0]);
		queue.progress(job.id(), DRONE_A, 0);
		assertEquals(2, calls[0], "reporting the same progress changes nothing");
		queue.progress(job.id(), DRONE_B, 4);
		assertEquals(2, calls[0], "a refused call changes nothing");
	}

	@Test
	void aSavedQueueReadsBackInNumberOrderWithItsNextNumber() {
		WorkJob second = new WorkJob(7, WorkJobType.BUILD, 0, WorkJobStatus.QUEUED, "", 3, Optional.empty());
		WorkJob first = new WorkJob(2, WorkJobType.CLEAR, 0, WorkJobStatus.DONE, "", 0, Optional.empty());
		WorkJob duplicate = new WorkJob(7, WorkJobType.CLEAR, 1, WorkJobStatus.QUEUED, "", 0, Optional.empty());
		WorkQueue queue = new WorkQueue(List.of(second, first, duplicate), 1);
		assertEquals(List.of(first, second), queue.jobs());
		assertEquals(8, queue.nextId(), "the next number follows the highest saved");
		assertFalse(queue.raise(WorkJobType.CLEAR, 2).map(j -> j.id() <= 7).orElse(true));
	}

	@Test
	void aJobOnlyHasATakerWhileTakenAndAReasonWhileBlocked() {
		WorkJob queued = new WorkJob(1, WorkJobType.CLEAR, 0, WorkJobStatus.QUEUED, "drone", 0,
				Optional.of(BlockReason.of(BlockReason.NO_ENERGY)));
		assertEquals("", queued.taker());
		assertTrue(queued.reason().isEmpty());
	}

	@Test
	void takeBlockedTakesUpAStoppedJobWhoeverStoppedItOnceTheBuildingIsFree() {
		WorkQueue queue = new WorkQueue();
		WorkJob build = queue.raise(WorkJobType.BUILD, 0).orElseThrow();
		queue.takeNext(DRONE_A, ALL);
		assertEquals(Outcome.OK, queue.block(build.id(), DRONE_A, BlockReason.noMaterials(List.of("4*torch")), 7));
		assertTrue(queue.takeNext(DRONE_B, ALL).isEmpty(), "control: takeNext hands out queued jobs only");
		WorkJob clear = queue.raise(WorkJobType.CLEAR, 0).orElseThrow();
		assertTrue(queue.takeBlocked(DRONE_B, ALL).isEmpty(), "the debris first: no building while a clearing is open");
		queue.takeNext(DRONE_B, ALL);
		assertTrue(queue.takeBlocked(DRONE_B, ALL).isEmpty(), "a taker holding a job takes no other");
		queue.complete(clear.id(), DRONE_B);
		WorkJob again = queue.takeBlocked(DRONE_B, ALL).orElseThrow();
		assertEquals(build.id(), again.id());
		assertEquals(WorkJobStatus.TAKEN, again.status());
		assertEquals(DRONE_B, again.taker());
		assertEquals(7, again.progress(), "the progress survives");
		assertTrue(again.reason().isEmpty(), "the old reason goes");
		assertTrue(queue.takeBlocked(DRONE_A, ALL).isEmpty(), "nothing else is stopped");
	}

	@Test
	void aRepairStaysARepairThroughEveryStep() {
		WorkQueue queue = new WorkQueue();
		WorkJob repair = queue.raise(WorkJobType.BUILD, 0, true).orElseThrow();
		assertTrue(repair.repair());
		assertFalse(queue.raise(WorkJobType.BUILD, 1).orElseThrow().repair(), "a plain raise is no repair");
		queue.takeNext(DRONE_A, ALL);
		queue.block(repair.id(), DRONE_A, BlockReason.of(BlockReason.NO_ENERGY), 4);
		queue.unblock(repair.id());
		queue.take(repair.id(), DRONE_A);
		queue.complete(repair.id(), DRONE_A);
		WorkJob done = queue.job(repair.id()).orElseThrow();
		assertEquals(WorkJobStatus.DONE, done.status());
		assertTrue(done.repair(), "blocking, taking and finishing keep the mark");
		WorkQueue back = new WorkQueue(queue.jobs(), queue.nextId());
		assertTrue(back.job(repair.id()).orElseThrow().repair(), "the mark goes through a copy of the queue");
	}
}
