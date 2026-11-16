package net.amaguri.yume.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.amaguri.yume.schedule.DreamFilter;

/** Draws the dream look: filter, typed text, and the black fades. */
public final class DreamOverlay {
	private static final int RAMP_TICKS = 6;
	private static final float TEXT_SCALE = 2.5f;
	private static final int[][] OUTLINE = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, -1 } };

	private DreamOverlay() {
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		boolean active = DreamClient.isActive();
		if (!active && !DreamClient.isExiting()) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth();
		int h = g.guiHeight();
		if (!active) {
			black(g, w, h, DreamClient.exitAlpha(partial));
			return;
		}
		float t = DreamClient.elapsed() + partial;
		DreamFilter filter = DreamClient.filter();
		if (filter == DreamFilter.SOFT) {
			soft(g, w, h, t);
		} else if (filter == DreamFilter.WHITE_HAZE) {
			haze(g, w, h, t);
		}
		text(g, w, h, t);
		black(g, w, h, DreamClient.fadeAlpha(t));
	}

	private static void black(GuiGraphicsExtractor g, int w, int h, float alpha) {
		if (alpha <= 0.003f) {
			return;
		}
		g.fill(0, 0, w, h, argb(alpha, 0, 0, 0));
	}

	/** Pastel, slowly shifting soft edges and a faint warm wash. */
	private static void soft(GuiGraphicsExtractor g, int w, int h, float t) {
		g.fill(0, 0, w, h, argb(0.07f, 255, 232, 242));
		frame(g, w, h, 0.34f, 0.28f, t, true);
	}

	/** Heavy white haze, a little colour drained, with a very faint flicker. */
	private static void haze(GuiGraphicsExtractor g, int w, int h, float t) {
		double n = 0.5 * Math.sin(t * 0.83) + 0.5 * Math.sin(t * 2.17 + 1.7);
		float flicker = 1f + 0.035f * (float) n;
		g.fill(0, 0, w, h, argb(0.34f * flicker, 226, 229, 236));
		frame(g, w, h, 0.62f * flicker, 0.40f, t, false);
	}

	/** Bands that get stronger toward the screen edge; each pixel is covered by at most one band. */
	private static void frame(GuiGraphicsExtractor g, int w, int h, float maxAlpha, float reachFrac, float t, boolean pastel) {
		int n = 30;
		float reach = Math.min(w, h) * reachFrac;
		int prev = 0;
		for (int i = 0; i < n; i++) {
			int next = Math.round(reach * (i + 1) / n);
			if (next <= prev) {
				continue;
			}
			float k = 1f - (float) i / n;
			float a = maxAlpha * k * k;
			int color;
			if (pastel) {
				float m = 0.5f + 0.5f * (float) Math.sin(t * 0.05f + i * 0.12f);
				float m2 = 0.5f + 0.5f * (float) Math.sin(t * 0.031f + 2f);
				int r = lerp(255, 214, m);
				int gr = lerp(196, 214, m * 0.6f + m2 * 0.4f);
				int b = lerp(224, 255, m);
				color = argb(a, r, gr, b);
			} else {
				color = argb(a, 240, 242, 247);
			}
			g.fill(prev, prev, w - prev, next, color);
			g.fill(prev, h - next, w - prev, h - prev, color);
			g.fill(prev, next, next, h - next, color);
			g.fill(w - next, next, w - prev, h - next, color);
			prev = next;
		}
	}

	private static void text(GuiGraphicsExtractor g, int w, int h, float t) {
		String s = DreamClient.text();
		if (s.isEmpty()) {
			return;
		}
		int[] at = DreamClient.charAt();
		float typed = t - DreamClient.textDelay();
		if (typed < 0f) {
			return;
		}
		int last = -1;
		for (int i = 0; i < at.length; i++) {
			if (at[i] <= typed) {
				last = i;
			} else {
				break;
			}
		}
		if (last < 0) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		String[] lines = s.split("\n", -1);
		int maxW = 1;
		for (String line : lines) {
			maxW = Math.max(maxW, font.width(line));
		}
		float scale = Math.min(TEXT_SCALE, 0.92f * w / maxW);
		int lineH = font.lineHeight + 4;
		float y0 = h * 0.62f;
		int lineStart = 0;
		g.pose().pushMatrix();
		g.pose().translate(w / 2f, y0);
		g.pose().scale(scale, scale);
		int y = 0;
		for (String line : lines) {
			int lineEnd = lineStart + line.length();
			int shown = Math.max(0, Math.min(line.length(), last + 1 - lineStart));
			if (shown > 0) {
				int x = -font.width(line) / 2;
				boolean newestHere = last < lineEnd;
				String solid = newestHere ? line.substring(0, shown - 1) : line.substring(0, shown);
				float ramp = 1f;
				if (newestHere) {
					ramp = Math.max(0.05f, Math.min(1f, (typed - at[last]) / RAMP_TICKS));
				}
				int cx = x + font.width(solid);
				String tail = newestHere ? line.substring(shown - 1, shown) : "";
				// Dark soft edge first, so the text reads on white too.
				for (int[] o : OUTLINE) {
					if (!solid.isEmpty()) {
						g.text(font, solid, x + o[0], y + o[1], argb(0.45f, 20, 20, 30), false);
					}
					if (!tail.isEmpty()) {
						g.text(font, tail, cx + o[0], y + o[1], argb(0.45f * ramp, 20, 20, 30), false);
					}
				}
				if (!solid.isEmpty()) {
					g.text(font, solid, x, y, argb(0.95f, 255, 255, 255), false);
				}
				if (!tail.isEmpty()) {
					g.text(font, tail, cx, y, argb(0.95f * ramp, 255, 255, 255), false);
				}
			}
			y += lineH;
			lineStart = lineEnd + 1;
		}
		g.pose().popMatrix();
	}

	private static int lerp(int a, int b, float k) {
		return Math.round(a + (b - a) * Math.max(0f, Math.min(1f, k)));
	}

	private static int argb(float a, int r, int g, int b) {
		int ai = Math.max(0, Math.min(255, Math.round(a * 255f)));
		return (ai << 24) | (r << 16) | (g << 8) | b;
	}
}
