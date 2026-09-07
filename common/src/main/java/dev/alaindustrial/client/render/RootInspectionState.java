package dev.alaindustrial.client.render;

/** Frame-owned immutable data; never fetch a live level from the drawing callback. */
public interface RootInspectionState {
	RootInspection.Frame alaindustrial$roots();
	void alaindustrial$roots(RootInspection.Frame frame);
}
