package net.amaguri.yume.schedule;

/**
 * The day-by-day plan. Pure Java (no Minecraft classes) so it can be checked with plain javac.
 * CONTRACT FILE (owned by the lead).
 */
public final class DreamSchedule {
	private DreamSchedule() {
	}

	public static DreamPlan planFor(int day) {
		return switch (day) {
			case 1 -> new DreamPlan(1, Scene.SHEEP, Scene.SHEEP, RevealStage.HIDDEN, false, false, "", 0, DreamFilter.SOFT);
			case 2 -> new DreamPlan(2, Scene.DIAMOND, Scene.DIAMOND, RevealStage.HIDDEN, false, false, "", 0, DreamFilter.SOFT);
			case 3 -> new DreamPlan(3, Scene.BALLOON, Scene.BALLOON, RevealStage.HIDDEN, false, false, "", 0, DreamFilter.SOFT);
			case 4 -> new DreamPlan(4, Scene.HELP, Scene.POV_STREAM, RevealStage.HIDDEN, false, false,
					"たす…けて……\nここから…だして……", 40, DreamFilter.WHITE_HAZE);
			case 5 -> new DreamPlan(5, Scene.WATERFALL, Scene.NONE, RevealStage.HIDDEN, false, false,
					"たす…け…", 80, DreamFilter.WHITE_HAZE);
			case 6 -> new DreamPlan(6, Scene.ROOM, Scene.NONE, RevealStage.SILHOUETTE, false, false, "", 0, DreamFilter.WHITE_HAZE);
			case 7 -> new DreamPlan(7, Scene.ROOM, Scene.NONE, RevealStage.SILHOUETTE, false, true, "", 0, DreamFilter.WHITE_HAZE);
			case 8 -> new DreamPlan(8, Scene.ROOM, Scene.NONE, RevealStage.VISIBLE, true, false, "", 0, DreamFilter.CLEAR);
			default -> day < 1
					? planFor(1)
					: new DreamPlan(day, Scene.ROOM, Scene.NONE, RevealStage.VISIBLE, true, false, "", 0, DreamFilter.CLEAR);
		};
	}

	/** Stage that applies while the next dream (day dreamsSeen + 1) is pending or playing. */
	public static RevealStage stageAfter(int dreamsSeen) {
		return planFor(dreamsSeen + 1).stage();
	}
}
