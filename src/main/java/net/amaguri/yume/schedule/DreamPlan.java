package net.amaguri.yume.schedule;

/**
 * Everything about the dream of one day. CONTRACT FILE.
 *
 * @param day            1-based day number (the n-th dream)
 * @param dreamerScene   what the dreamers see
 * @param lostScene      what the trapped group sees (POV_STREAM runs right AFTER the dreamers' dream ends)
 * @param stage          reveal stage while this dream plays
 * @param voice          connect both groups' voice while this dream plays
 * @param giftLost       give config.giftItems to the trapped group when this dream starts
 * @param text           overlay text for the dreamers, typed letter by letter ("" = none); '\n' = new line
 * @param textDelayTicks ticks after the dream starts before the text begins
 * @param filter         screen look for the dreamers
 */
public record DreamPlan(int day, Scene dreamerScene, Scene lostScene, RevealStage stage, boolean voice,
		boolean giftLost, String text, int textDelayTicks, DreamFilter filter) {
}
