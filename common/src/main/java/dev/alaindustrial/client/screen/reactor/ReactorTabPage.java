package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.client.screen.tabs.TabPage;

/**
 * One tab of the reactor controller's screen (MOD-617).
 *
 * <p>The contract is the shared {@link TabPage} (MOD-628). The reactor's own frame around it is the panel, the
 * tab strip, the status chip and the countdown bar.
 */
public interface ReactorTabPage extends TabPage {
}
