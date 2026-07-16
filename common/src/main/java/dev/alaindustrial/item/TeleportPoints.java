package dev.alaindustrial.item;

import com.mojang.serialization.Codec;
import dev.alaindustrial.Config;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The stations a remote knows (MOD-093) — an immutable list, plus the rules for changing it.
 *
 * <p>MOD-092 shipped a single bound point; this replaces it with a named list. Every mutation
 * returns a new instance rather than editing in place: these live inside an {@code ItemStack} data
 * component, and components must be treated as values (two remotes can legitimately share one).
 *
 * <p>The rules live here, next to the data, so the menu, the item and the payload handler cannot
 * each invent their own idea of "is this index valid" — the server calls exactly these.
 */
public record TeleportPoints(List<TeleportPoint> points) {
	public static final TeleportPoints EMPTY = new TeleportPoints(List.of());

	public static final Codec<TeleportPoints> CODEC =
			TeleportPoint.CODEC.listOf().xmap(TeleportPoints::new, TeleportPoints::points);

	public static final StreamCodec<ByteBuf, TeleportPoints> STREAM_CODEC =
			TeleportPoint.STREAM_CODEC.apply(ByteBufCodecs.list()).map(TeleportPoints::new, TeleportPoints::points);

	public TeleportPoints(List<TeleportPoint> points) {
		this.points = List.copyOf(points);
	}

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	/** True when {@code index} addresses a real point — the one check every server path must make. */
	public boolean isValidIndex(int index) {
		return index >= 0 && index < points.size();
	}

	@Nullable
	public TeleportPoint get(int index) {
		return isValidIndex(index) ? points.get(index) : null;
	}

	/** Index of the station at this exact spot, or -1 — how a re-bind is recognised. */
	public int indexOf(ResourceKey<Level> dim, BlockPos pos) {
		for (int i = 0; i < points.size(); i++) {
			TeleportPoint point = points.get(i);
			if (point.dim() == dim && point.pos().equals(pos)) {
				return i;
			}
		}
		return -1;
	}

	public boolean isFull() {
		return points.size() >= Math.max(1, Config.teleporterMaxPoints);
	}

	/** Add a point. The caller must have checked {@link #isFull()} and {@link #indexOf}. */
	public TeleportPoints with(TeleportPoint point) {
		List<TeleportPoint> next = new ArrayList<>(points);
		next.add(point);
		return new TeleportPoints(next);
	}

	/** Drop the point at {@code index}; an out-of-range index changes nothing. */
	public TeleportPoints without(int index) {
		if (!isValidIndex(index)) {
			return this;
		}
		List<TeleportPoint> next = new ArrayList<>(points);
		next.remove(index);
		return new TeleportPoints(next);
	}

	/** Rename the point at {@code index}; the name is clamped, an out-of-range index changes nothing. */
	public TeleportPoints renamed(int index, String name) {
		if (!isValidIndex(index)) {
			return this;
		}
		String clamped = TeleportPoint.clampName(name);
		List<TeleportPoint> next = new ArrayList<>(points);
		TeleportPoint old = next.get(index);
		// Clearing the name hands the point back to auto-naming. It gets a fresh number rather than
		// its old one back: while it was named, a later binding may well have taken that number.
		int number = clamped.isEmpty() ? nextFreeNumber(index) : old.number();
		next.set(index, new TeleportPoint(old.dim(), old.pos(), clamped, number));
		return new TeleportPoints(next);
	}

	/** The number a fresh binding should get — the lowest no auto-named point is showing. */
	public int nextFreeNumber() {
		return nextFreeNumber(-1);
	}

	/**
	 * Lowest free number, skipping {@code ignoreIndex} (the point being renumbered itself).
	 *
	 * <p>Numbers are per-remote and reused once freed, so deleting "Teleporter 2" hands 2 to the next
	 * binding instead of counting on forever. Only unnamed points hold a number, so a renamed point
	 * releases its own. The list is capped at {@link Config#teleporterMaxPoints}, which bounds this.
	 */
	private int nextFreeNumber(int ignoreIndex) {
		for (int candidate = 1; ; candidate++) {
			boolean taken = false;
			for (int i = 0; i < points.size(); i++) {
				TeleportPoint point = points.get(i);
				if (i != ignoreIndex && point.name().isEmpty() && point.number() == candidate) {
					taken = true;
					break;
				}
			}
			if (!taken) {
				return candidate;
			}
		}
	}
}
