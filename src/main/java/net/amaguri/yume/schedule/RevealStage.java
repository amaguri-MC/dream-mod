package net.amaguri.yume.schedule;

/** How much of the trapped group the dreamers can perceive. CONTRACT FILE. */
public enum RevealStage {
	/** Trapped players are not rendered at all for dreamers; no voice. */
	HIDDEN,
	/** Trapped players render as solid black silhouettes for dreamers (no name tag, no armor); no voice. */
	SILHOUETTE,
	/** Trapped players render normally during dreams and voice is connected during dreams. Tab/chat still separated. */
	VISIBLE,
	/** The secret is over: nothing is hidden or separated any more. */
	FINALE
}
