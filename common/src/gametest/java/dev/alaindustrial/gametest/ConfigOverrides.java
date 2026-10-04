package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.config.KnobEntry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;

/**
 * The one way a gametest changes a balance knob (MOD-710, CFG-3): every override has an owner, and
 * every override is put back.
 *
 * <p><b>Two shapes.</b>
 * <ul>
 *   <li>{@link #sync()} — an override that lives inside ONE synchronous call, restored by
 *       {@link #close()}: {@code try (ConfigOverrides o = ConfigOverrides.sync()) { o.set("cableBuffer", 4); … }}.
 *       Nothing else ticks while the call runs, so no other scenario can observe the value. This is
 *       the default.</li>
 *   <li>{@link #forTest(GameTestHelper)} — an override that stays for the rest of the test, across
 *       ticks. It is restored when the test ENDS — passed, failed or timed out — by a
 *       {@link GameTestListener} attached to the test's own {@link GameTestInfo}; {@link #close()} may
 *       restore it earlier.</li>
 * </ul>
 *
 * <p><b>Keys are knob names</b> (the JSON key = the field name), resolved through
 * {@link Config#REGISTRY}. A knob that moves to another holder (ADR-034) keeps its key, so a scenario
 * written against this class does not change when the knob moves. A write goes through the knob's
 * declared {@link Field} — the same field a config reload commits into — so {@code KnobRegistry} and
 * the client-sync snapshot see exactly the value the scenario set.
 *
 * <p><b>Owners.</b> A key is held by one handle at a time, process-wide. A second handle that tries to
 * capture it is refused with an {@link IllegalStateException} naming the first owner — two scenarios
 * fighting over one knob fail loudly instead of restoring each other's values in the wrong order.
 *
 * <p><b>Restore target: the value at the key's FIRST capture in this process</b> (scope decision 3),
 * not the compiled default: a run directory with an edited {@code alaindustrial.json} gets back the value
 * its batch started with. With one writer this is exactly the old save/try/finally.
 *
 * <p><b>What this does NOT catch: a reader.</b> A {@code forTest} override is visible to every scenario
 * ticking in the same gametest batch for as long as it lasts (the MOD-469 incident was such a reader).
 * The owner registry stops a second WRITER, never a reader. Hence {@code sync()} by default; a
 * multi-tick {@code forTest} scenario runs in its own environment ({@code alaindustrial:config_overrides},
 * see the MOD-710 research note on the separate-batch spike).
 */
public final class ConfigOverrides implements AutoCloseable {

	/** Who holds each key right now. */
	private static final Map<String, ConfigOverrides> OWNERS = new HashMap<>();
	/** Each key's value at its first capture in this process — what every restore writes back. */
	private static final Map<String, Object> FIRST_CAPTURE = new HashMap<>();
	/** The open {@code forTest} handle of each running test, so helpers of one test share it. */
	private static final Map<GameTestInfo, ConfigOverrides> BY_TEST = new IdentityHashMap<>();
	/** {@code GameTestHelper}'s private {@code GameTestInfo} field, found by type once. */
	private static Field testInfoField;

	private final String owner;
	private final GameTestInfo test;
	/** Keys this handle holds, with the value it last wrote (to spot a foreign writer on restore). */
	private final Map<String, Object> held = new LinkedHashMap<>();
	private final GameTestListener endListener = new EndListener();
	private boolean closed;

	private ConfigOverrides(String owner, GameTestInfo test) {
		this.owner = owner;
		this.test = test;
	}

	/**
	 * A handle for overrides inside one synchronous call; use it in try-with-resources. The owner name
	 * is the calling method ({@code Class.method}), which is what a refused rival reads.
	 */
	public static ConfigOverrides sync() {
		String caller = StackWalker.getInstance()
				.walk(frames -> frames.skip(1).findFirst()
						.map(f -> simpleName(f.getClassName()) + "." + f.getMethodName())
						.orElse("<unknown caller>"));
		return new ConfigOverrides("sync " + caller, null);
	}

	/**
	 * The handle of the running test {@code helper} belongs to: every key it captures is restored when
	 * that test ends, however it ends. Calling it again in the same test returns the same handle, so
	 * two helpers of one scenario never refuse each other. The owner name is the test id.
	 */
	public static synchronized ConfigOverrides forTest(GameTestHelper helper) {
		GameTestInfo info = testInfo(helper);
		ConfigOverrides existing = BY_TEST.get(info);
		if (existing != null) {
			return existing;
		}
		ConfigOverrides handle = new ConfigOverrides("test " + info.id(), info);
		info.addListener(handle.endListener);
		BY_TEST.put(info, handle);
		return handle;
	}

	/** The current owner of {@code key}, if any handle holds it. */
	public static synchronized Optional<String> ownerOf(String key) {
		ConfigOverrides holder = OWNERS.get(key);
		return holder == null ? Optional.empty() : Optional.of(holder.owner);
	}

	/** This handle's owner name, as a refused rival's error message quotes it. */
	public String owner() {
		return owner;
	}

	/** Set an {@code int} knob (or, widening like a Java assignment, a {@code float}/{@code double} one). */
	public ConfigOverrides set(String key, int value) {
		return write(key, value, field -> field.setInt(null, value));
	}

	/** Set a {@code float} knob (or, widening, a {@code double} one). */
	public ConfigOverrides set(String key, float value) {
		return write(key, value, field -> field.setFloat(null, value));
	}

	/** Set a {@code double} knob. */
	public ConfigOverrides set(String key, double value) {
		return write(key, value, field -> field.setDouble(null, value));
	}

	/** Set a {@code boolean} knob. */
	public ConfigOverrides set(String key, boolean value) {
		return write(key, value, field -> field.setBoolean(null, value));
	}

	/**
	 * Restore every key this handle captured to its first-capture value and release it. Idempotent; a
	 * closed handle refuses further writes.
	 */
	@Override
	public void close() {
		synchronized (ConfigOverrides.class) {
			if (closed) {
				return;
			}
			closed = true;
			if (test != null) {
				BY_TEST.remove(test);
			}
			List<String> failures = new ArrayList<>();
			for (Map.Entry<String, Object> entry : held.entrySet()) {
				String key = entry.getKey();
				try {
					Field field = knob(key);
					Object live = field.get(null);
					if (!live.equals(entry.getValue())) {
						Industrialization.LOGGER.warn("[ConfigOverrides] {} found {} = {} at restore, not the {} it"
								+ " set: something wrote the knob outside ConfigOverrides", owner, key, live, entry.getValue());
					}
					field.set(null, FIRST_CAPTURE.get(key));
				} catch (ReflectiveOperationException | RuntimeException e) {
					failures.add(key + ": " + e);
				} finally {
					OWNERS.remove(key, this);
				}
			}
			held.clear();
			if (!failures.isEmpty()) {
				throw new IllegalStateException(owner + " could not restore " + String.join(", ", failures));
			}
		}
	}

	/** The listener {@link #forTest} attached to its test (self-tests check it is really there). */
	GameTestListener endListener() {
		return endListener;
	}

	/**
	 * The {@link GameTestInfo} behind {@code helper}. {@code GameTestHelper} keeps it in its one private
	 * field of that type ({@code testInfo}, 26.3, verified with {@code javap -p} on both the Fabric and
	 * the NeoForge-patched class) and offers no getter, so the field is located BY TYPE: a rename does
	 * not break it, a second such field fails loudly.
	 */
	static synchronized GameTestInfo testInfo(GameTestHelper helper) {
		try {
			if (testInfoField == null) {
				Field found = null;
				for (Field field : GameTestHelper.class.getDeclaredFields()) {
					if (field.getType() == GameTestInfo.class) {
						if (found != null) {
							throw new IllegalStateException("GameTestHelper has two GameTestInfo fields: "
									+ found.getName() + ", " + field.getName());
						}
						found = field;
					}
				}
				if (found == null) {
					throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
				}
				found.setAccessible(true);
				testInfoField = found;
			}
			return Objects.requireNonNull((GameTestInfo) testInfoField.get(helper), "helper's GameTestInfo");
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw new IllegalStateException("ConfigOverrides.forTest cannot reach the test behind its helper,"
					+ " so it could not restore the knob when the test ends; refusing instead", e);
		}
	}

	private ConfigOverrides write(String key, Object value, FieldWrite how) {
		synchronized (ConfigOverrides.class) {
			if (closed) {
				throw new IllegalStateException(owner + " is closed (or its test has ended); it cannot set " + key);
			}
			Field field = knob(key);
			ConfigOverrides holder = OWNERS.get(key);
			if (holder != null && holder != this) {
				throw new IllegalStateException(owner + " cannot override " + key + ": it is already held by "
						+ holder.owner);
			}
			try {
				if (holder == null) {
					Object live = field.get(null);
					Object first = FIRST_CAPTURE.putIfAbsent(key, live);
					if (first != null && !first.equals(live)) {
						Industrialization.LOGGER.warn("[ConfigOverrides] {} captures {} = {}, but its first capture"
								+ " in this run saw {}: a write outside ConfigOverrides leaked; restoring to {}",
								owner, key, live, first, first);
					}
					OWNERS.put(key, this);
				}
				how.apply(field);
				held.put(key, field.get(null));
			} catch (IllegalArgumentException e) {
				if (holder == null) {
					OWNERS.remove(key, this);
				}
				throw new IllegalArgumentException("knob " + key + " is a " + field.getType() + "; "
						+ value.getClass().getSimpleName() + " " + value + " does not fit it", e);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("knob " + key + " is not writable", e);
			}
			return this;
		}
	}

	/** The declared field of the knob called {@code key}; an unknown key is a typo and throws. */
	private static Field knob(String key) {
		for (KnobEntry entry : Config.REGISTRY.entries()) {
			if (entry.key().equals(key)) {
				return entry.field();
			}
		}
		throw new IllegalArgumentException("no @Knob named '" + key + "' in any registered holder");
	}

	private static String simpleName(String className) {
		return className.substring(className.lastIndexOf('.') + 1);
	}

	@FunctionalInterface
	private interface FieldWrite {
		void apply(Field field) throws IllegalAccessException;
	}

	/**
	 * Restores the handle when its test ends. 26.3 {@code GameTestInfo#tick} calls every listener's
	 * {@code testPassed} or {@code testFailed} once the test is done; a timeout is a failure
	 * ({@code GameTestTimeoutException}), dispatched on the tick after the deadline. Nothing is thrown
	 * back into that dispatch: the test is already decided, and an exception there would stop the
	 * gametest server instead of naming the knob.
	 */
	private final class EndListener implements GameTestListener {
		@Override
		public void testStructureLoaded(GameTestInfo info) {
		}

		@Override
		public void testPassed(GameTestInfo info, GameTestRunner runner) {
			restoreAtEnd(info);
		}

		@Override
		public void testFailed(GameTestInfo info, GameTestRunner runner) {
			restoreAtEnd(info);
		}

		private void restoreAtEnd(GameTestInfo info) {
			try {
				close();
			} catch (RuntimeException e) {
				Industrialization.LOGGER.error("[ConfigOverrides] restoring the knobs of {} after it ended failed",
						info, e);
			}
		}

		@Override
		public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
		}
	}
}
