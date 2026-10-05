package dev.alaindustrial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An immutable copy of the server's client-visible knobs (MOD-695): what a dedicated server tells its
 * clients about its own balance, so a tooltip or a screen shows the server's numbers instead of the
 * ones in the player's local {@code config/alaindustrial.json}.
 *
 * <p><b>A copy, never the live fields.</b> In single player the client and the integrated server share
 * one JVM and one {@link Config}; a client that wrote the received numbers into {@code Config.*} would
 * overwrite the server's balance from the render thread, and the fields carry no {@code volatile}. So
 * the snapshot is a value object the client keeps beside {@code Config}, and applying one never touches
 * a static field of that class.
 *
 * <p><b>The wire format is plain Java, not a Minecraft codec,</b> so the L1 suite — which has no
 * Minecraft jar — can round-trip every knob. {@code ConfigSyncPayload} carries the encoded bytes as one
 * bounded byte array. Layout: the {@link #FORMAT} int, the entry count, then per entry the knob name,
 * a one-byte type tag and the value. Knobs travel by NAME, so a client of a slightly different build
 * reads the knobs it knows and keeps its local value for any it does not; a different {@link #FORMAT}
 * is refused whole.
 *
 * <p>Minecraft-free on purpose; lives beside {@link Config} because it reads the knob registry.
 */
public final class KnobSnapshot {

	/**
	 * Layout version of {@link #encode()}. Raise it when the byte layout changes; a client that receives
	 * any other value keeps its local numbers instead of guessing at the bytes (MOD-695).
	 */
	public static final int FORMAT = 1;

	/** Most entries a decoder accepts — far above the 459 knobs {@code Config} has, far below a DoS. */
	static final int MAX_ENTRIES = 4096;

	private static final byte INT = 'I';
	private static final byte FLOAT = 'F';
	private static final byte DOUBLE = 'D';
	private static final byte BOOLEAN = 'Z';

	private final Map<String, Object> values;

	private KnobSnapshot(Map<String, Object> values) {
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	/** The server's current client-visible balance: every knob flagged {@code clientVisible}, live. */
	public static KnobSnapshot capture() {
		return new KnobSnapshot(Config.REGISTRY.clientVisibleValues());
	}

	/**
	 * A snapshot of exactly these values — for tests and for a decoder. Each value must be an
	 * {@code Integer}, {@code Float}, {@code Double} or {@code Boolean}; anything else is a programming
	 * error and throws.
	 */
	public static KnobSnapshot of(Map<String, ?> values) {
		Map<String, Object> copy = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			tagOf(value);
			copy.put(key, value);
		});
		return new KnobSnapshot(copy);
	}

	/** The knob names this snapshot carries, in the order they were captured. */
	public Set<String> keys() {
		return values.keySet();
	}

	/** The boxed value of {@code key}, or {@code null} when the snapshot does not carry it. */
	public @Nullable Object get(String key) {
		return values.get(key);
	}

	/** {@code key} as an int, or {@code local} when the snapshot lacks it or holds another type. */
	public int intValue(String key, int local) {
		return values.get(key) instanceof Integer v ? v : local;
	}

	/** {@code key} as a float, or {@code local} when the snapshot lacks it or holds another type. */
	public float floatValue(String key, float local) {
		return values.get(key) instanceof Float v ? v : local;
	}

	/** {@code key} as a double, or {@code local} when the snapshot lacks it or holds another type. */
	public double doubleValue(String key, double local) {
		return values.get(key) instanceof Double v ? v : local;
	}

	/** {@code key} as a boolean, or {@code local} when the snapshot lacks it or holds another type. */
	public boolean booleanValue(String key, boolean local) {
		return values.get(key) instanceof Boolean v ? v : local;
	}

	/** The wire form (see the class comment for the layout). */
	public byte[] encode() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(FORMAT);
			out.writeInt(values.size());
			for (Map.Entry<String, Object> entry : values.entrySet()) {
				out.writeUTF(entry.getKey());
				Object value = entry.getValue();
				byte tag = tagOf(value);
				out.writeByte(tag);
				switch (tag) {
					case INT -> out.writeInt((Integer) value);
					case FLOAT -> out.writeFloat((Float) value);
					case DOUBLE -> out.writeDouble((Double) value);
					default -> out.writeBoolean((Boolean) value);
				}
			}
		} catch (IOException e) {
			// A ByteArrayOutputStream does not throw; reaching this is a JDK contract break, not input.
			throw new IllegalStateException("[config-sync] encoding a knob snapshot failed", e);
		}
		return bytes.toByteArray();
	}

	/**
	 * The snapshot in {@code data}, or empty when the bytes are not one this build can read: another
	 * {@link #FORMAT}, a truncated or over-long body, an unknown type tag. Never throws — a client that
	 * cannot read the server's balance keeps showing its own, as it did before MOD-695.
	 */
	public static Optional<KnobSnapshot> decode(byte @Nullable [] data) {
		if (data == null) {
			return Optional.empty();
		}
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			if (in.readInt() != FORMAT) {
				return Optional.empty();
			}
			int count = in.readInt();
			if (count < 0 || count > MAX_ENTRIES) {
				return Optional.empty();
			}
			Map<String, Object> read = new LinkedHashMap<>();
			for (int i = 0; i < count; i++) {
				String key = in.readUTF();
				byte tag = in.readByte();
				Object value = switch (tag) {
					case INT -> in.readInt();
					case FLOAT -> in.readFloat();
					case DOUBLE -> in.readDouble();
					case BOOLEAN -> in.readBoolean();
					default -> null;
				};
				if (value == null) {
					return Optional.empty();
				}
				read.put(key, value);
			}
			return in.available() == 0 ? Optional.of(new KnobSnapshot(read)) : Optional.empty();
		} catch (IOException e) {
			// Truncated input (EOFException) or a malformed name: refuse the snapshot, keep local values.
			return Optional.empty();
		}
	}

	private static byte tagOf(Object value) {
		if (value instanceof Integer) {
			return INT;
		}
		if (value instanceof Float) {
			return FLOAT;
		}
		if (value instanceof Double) {
			return DOUBLE;
		}
		if (value instanceof Boolean) {
			return BOOLEAN;
		}
		throw new IllegalArgumentException("a knob value must be an int, float, double or boolean, got " + value);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof KnobSnapshot that && values.equals(that.values);
	}

	@Override
	public int hashCode() {
		return values.hashCode();
	}

	@Override
	public String toString() {
		return "KnobSnapshot" + values;
	}
}
