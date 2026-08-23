package dev.alaindustrial.junit;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * MOD-486 — halts the ephemeral server BEFORE FML is torn down from under it.
 *
 * <p>Shutdown order of the {@code :neoforge:test} lane without this extension:
 *
 * <ol>
 *   <li>the JUnit root store closes → {@code EphemeralTestServerProvider} calls
 *       {@code JUnitServer.stopServer()} FROM A FOREIGN THREAD and then {@code LogManager.shutdown()};
 *       the server thread keeps ticking, because {@code stopServer()} never sets
 *       {@code running = false} — {@link MinecraftServer#halt(boolean)} does;</li>
 *   <li>the session closes → {@code net.neoforged.fml.junit.JUnitService} clears {@code ModLoader};</li>
 *   <li>the next server tick reaches {@code GameTestHooks.isGametestEnabled()} →
 *       {@code FMLLoader.getCurrent()} and gets {@code IllegalStateException: There is no current
 *       FML Loader}; Minecraft's crash path then calls {@code stopServer()} a second time.</li>
 * </ol>
 *
 * <p>So EVERY run, green ones included, ended in an unhandled server crash as a matter of course,
 * and the JVM exit was a race. It was not the cause of the random failures (checked in MOD-484: the
 * same lines appear in green runs), but a lane that always crashes on exit hides real shutdown
 * problems.
 *
 * <p><b>Why a Jupiter extension and not a {@code TestExecutionListener}.</b> A platform listener is
 * loaded by the launcher's own ServiceLoader — that is, NOT by FML's transforming class loader — and
 * the lane dies before the first test: {@code Classes were loaded on the wrong class-loader}. A
 * Jupiter extension is loaded by the engine, which already lives inside FML — verified.
 *
 * <p><b>Why the order is right.</b> The resource goes into the ROOT store on the first
 * {@code beforeAll}, i.e. EARLIER than {@code EphemeralTestServerProvider} creates the server (it
 * does so while resolving the test parameter). The root store closes resources in reverse order, so
 * ours closes LAST: the server has been asked to stop while the session is still open and FML alive.
 *
 * <p>The extension NEVER throws and waits for the thread with a ceiling: breaking the end of a run
 * is worse than leaving it as it was. It is wired ONLY through {@code @ExtendWith} on the test class
 * itself — neither Jupiter's extension auto-detection nor a platform {@code TestExecutionListener}
 * will do (see the paragraph above), so the {@code ephemeral-server-tests-halt-it} rule in
 * {@code docs/tools/arch_check.py} keeps a new test from forgetting the annotation.
 */
public final class StopEphemeralServerBeforeFmlTeardown implements BeforeAllCallback {

	/** Ceiling on waiting for the server thread. It never takes this long — this guards against a hang. */
	private static final long JOIN_TIMEOUT_MS = 30_000L;

	private static final ExtensionContext.Namespace NAMESPACE =
			ExtensionContext.Namespace.create(StopEphemeralServerBeforeFmlTeardown.class);

	@Override
	public void beforeAll(ExtensionContext context) {
		context.getRoot()
				.getStore(NAMESPACE)
				.getOrComputeIfAbsent("halt-on-close", key -> (ExtensionContext.Store.CloseableResource) Halt::stopAndJoin);
	}

	/** Split out so the lambda above reads as "what to close" rather than "how". */
	private static final class Halt {
		private Halt() {
		}

		static void stopAndJoin() {
			try {
				MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
				if (server == null) {
					return; // no server (or it is already gone) — nothing to halt
				}
				server.halt(false);
				Thread serverThread = server.getRunningThread();
				if (serverThread != null && serverThread.isAlive()) {
					serverThread.join(JOIN_TIMEOUT_MS);
				}
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			} catch (Throwable ignored) {
				// Cleanup may not fail the run: every test has already finished by this point.
			}
		}
	}
}
