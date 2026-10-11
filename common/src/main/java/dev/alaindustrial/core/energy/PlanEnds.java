package dev.alaindustrial.core.energy;

import java.util.ArrayList;
import java.util.List;

/**
 * The endpoints of one tick's line plan ({@link LinePlanner}, MOD-789): a receiver and a generator host as graph
 * nodes, with the cables each touches and the graph edges that join them. Minecraft-free.
 */
final class PlanEnds {

	/** The order of the rule: what a sink of each kind takes, and when. */
	static final int MACHINE = 0;
	static final int STORE = 1;
	static final int TAKER = 2;

	/** At most one cable per face of a block. */
	private static final int FACES = 6;

	private PlanEnds() {
	}

	/**
	 * One receiver: several endpoints over one buffer are one (ADR-027 — a multiblock's cells), with one packet a
	 * tick between them. {@link #kind}: {@link #MACHINE} (a need it can tell), {@link #STORE} (a storage sink, or a
	 * consumer of unknown need — the rule treats it as one), {@link #TAKER} (a sink drawing the generators' surplus
	 * apart within a budget, MOD-731/756).
	 */
	static final class Sink<P> {
		final EnergyLineDistributor.LiveConsumer<P> consumer;
		final int kind;
		final long room;
		final long need;
		/** A storage sink (counted into storage in the telemetry), not a consumer of unknown need served as one. */
		final boolean storage;
		final int[] cables = new int[FACES];
		final int[] edges = new int[FACES];
		/** What the routing of the machines' needs alone brings it through each of its cables. */
		final long[] needFlow = new long[FACES];
		/** What it drew out of each of its cables this tick, so far (the kernel's two draws). */
		final long[] taken = new long[FACES];
		int cableCount;
		int distance = Integer.MAX_VALUE / 2;
		long planned;

		Sink(EnergyLineDistributor.LiveConsumer<P> consumer, int kind, long room, long need, boolean storage) {
			this.consumer = consumer;
			this.kind = kind;
			this.room = room;
			this.need = need;
			this.storage = storage;
		}

		boolean machine() {
			return kind == MACHINE;
		}

		boolean taker() {
			return kind == TAKER;
		}

		void touch(int cable, int producerDistance) {
			for (int i = 0; i < cableCount; i++) {
				if (cables[i] == cable) {
					return;
				}
			}
			if (cableCount < cables.length) {
				cables[cableCount++] = cable;
				distance = Math.min(distance, producerDistance);
			}
		}
	}

	/** One generator host: one packet a tick between all the cells that lend its port (MOD-608). */
	static final class Source<P> {
		final EnergyLineDistributor.LiveProducer<P> producer;
		final List<EnergyLineDistributor.LiveProducer<P>> cells = new ArrayList<>(1);
		final int[] cables = new int[FACES * 4];
		final int[] faces = new int[FACES * 4];
		final int[] cellOf = new int[FACES * 4];
		final int[] edges = new int[FACES * 4];
		int cableCount;
		long available;

		Source(EnergyLineDistributor.LiveProducer<P> producer, long available) {
			this.producer = producer;
			this.available = available;
		}

		/** The slot of {@code cable} among the cables this host feeds, or -1. */
		int slotOf(int cable) {
			for (int i = 0; i < cableCount; i++) {
				if (cables[i] == cable) {
					return i;
				}
			}
			return -1;
		}

		void touch(int cable, int face, int cell) {
			if (cableCount < cables.length) {
				cables[cableCount] = cable;
				faces[cableCount] = face;
				cellOf[cableCount] = cell;
				cableCount++;
			}
		}
	}
}
