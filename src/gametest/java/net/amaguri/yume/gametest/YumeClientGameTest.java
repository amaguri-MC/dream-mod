package net.amaguri.yume.gametest;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.amaguri.yume.core.YumeConfig;
import net.amaguri.yume.core.YumeState;
import net.amaguri.yume.director.DreamDirector;
import net.amaguri.yume.schedule.Scene;

/** Plays every dream on a real client in CI; logs with YUME_TEST and saves screenshots. */
public class YumeClientGameTest implements FabricClientGameTest {
	private static void log(String fmt, Object... args) {
		System.out.println("YUME_TEST " + String.format(fmt, args));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(c -> {
					c.setSeed("20261116");
					c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
					c.setAllowCommands(true);
				})
				.create()) {
			var server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			String name = context.computeOnClient(mc -> mc.player.getGameProfile().name());
			server.runOnServer(s -> {
				YumeConfig c = YumeConfig.get();
				c.dreamers.clear();
				c.dreamers.add(name);
				c.operators.clear();
				c.operators.add(name);
				c.povTarget = name;
				c.streamUrl = "https://example.com/test";
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				// camera points near spawn so camera dreams have something to look at
				for (String key : new String[] {"waterfall", "room"}) {
					YumeConfig.Cam cam = new YumeConfig.Cam();
					cam.dimension = "minecraft:overworld";
					cam.x = p.getX() + 6;
					cam.y = p.getY() + 3;
					cam.z = p.getZ() + 6;
					cam.yaw = 135;
					cam.pitch = 15;
					c.cameras.put(key, cam);
				}
			});
			context.takeScreenshot("00_spawn");

			for (Scene scene : new Scene[] {Scene.SHEEP, Scene.DIAMOND, Scene.BALLOON, Scene.HELP, Scene.WATERFALL, Scene.ROOM}) {
				String msg = server.computeOnServer(s -> DreamDirector.playTest(s, scene, s.getPlayerList().getPlayers()));
				log("play %s -> %s", scene, msg);
				for (int t : new int[] {30, 100, 170}) {
					context.waitTicks(t == 30 ? 30 : 70);
					context.takeScreenshot("10_" + scene.name().toLowerCase() + "_" + t);
				}
				context.waitTicks(80);
				checkRestored(context, sp, scene.name());
			}

			server.runCommand("time set 18000");
			for (int day = 1; day <= 9; day++) {
				final int d = day;
				String msg = server.computeOnServer(s -> DreamDirector.startDay(s, d, true));
				log("day %d -> %s", d, msg);
				context.waitTicks(100);
				context.takeScreenshot("20_day" + d);
				context.waitTicks(day == 4 ? 400 : 160);
				long time = server.computeOnServer(s -> s.overworld().getOverworldClockTime() % 24000);
				int seen = server.computeOnServer(s -> YumeState.get().dreamsSeen);
				log("day %d done: dreamsSeen=%d dayTime=%d", d, seen, time);
				checkRestored(context, sp, "day" + d);
			}

			server.runCommand("execute as @p run yume timespeed 4");
			server.runCommand("execute as @p run yume status");
			context.waitTicks(20);
			int speed = server.computeOnServer(s -> YumeState.get().timeSpeed);
			log("timeSpeed=%d", speed);
			context.takeScreenshot("30_after");
		}
	}

	private static void checkRestored(ClientGameTestContext context, TestSingleplayerContext sp, String label) {
		var server = sp.getServer();
		String st = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			return "mode=" + p.gameMode() + " dim=" + p.level().dimension().identifier() + " inDream=" + YumeState.get().inDream.size()
					+ " active=" + DreamDirector.isDreamActive() + " camSelf=" + (p.getCamera() == p);
		});
		log("restored after %s: %s", label, st);
	}
}
