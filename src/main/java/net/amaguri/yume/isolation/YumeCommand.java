package net.amaguri.yume.isolation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import net.amaguri.yume.core.Groups;
import net.amaguri.yume.core.YumeConfig;
import net.amaguri.yume.core.YumeState;
import net.amaguri.yume.director.DreamDirector;
import net.amaguri.yume.schedule.RevealStage;
import net.amaguri.yume.schedule.Scene;

/**
 * /yume: for the people in config.operators (and the console) only. Not even offered in tab completion to anyone else.
 * Every reply goes to the sender only.
 */
final class YumeCommand {
	private static final List<String> SCENE_NAMES = sceneNames();
	private static final List<String> STAGE_NAMES = List.of("auto", "hidden", "silhouette", "visible", "finale");
	private static final List<String> CAMERA_NAMES = List.of("waterfall", "room");

	private static final SuggestionProvider<CommandSourceStack> SCENES = (ctx, b) -> SharedSuggestionProvider.suggest(SCENE_NAMES, b);
	private static final SuggestionProvider<CommandSourceStack> STAGES = (ctx, b) -> SharedSuggestionProvider.suggest(STAGE_NAMES, b);
	private static final SuggestionProvider<CommandSourceStack> CAMERAS = (ctx, b) -> SharedSuggestionProvider.suggest(CAMERA_NAMES, b);

	private static final List<String> SIDE_NAMES = List.of("dreamer", "lost", "none");
	private static final List<String> CAST_NAMES = List.of("sheep", "miner", "balloon", "help", "pov");
	private static final SuggestionProvider<CommandSourceStack> SIDES = (ctx, b) -> SharedSuggestionProvider.suggest(SIDE_NAMES, b);
	private static final SuggestionProvider<CommandSourceStack> CASTS = (ctx, b) -> SharedSuggestionProvider.suggest(CAST_NAMES, b);
	private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b);

	private YumeCommand() {
	}

	private static List<String> sceneNames() {
		List<String> out = new ArrayList<>();
		for (Scene s : Scene.values()) {
			if (s != Scene.NONE) out.add(s.name().toLowerCase(Locale.ROOT));
		}
		return out;
	}

	static boolean allowed(CommandSourceStack source) {
		ServerPlayer p = source.getPlayer();
		if (p != null) {
			// config.operators, or a server operator (op) as a fallback so the host can always set things up
			return Groups.isOperator(p) || source.getServer().getPlayerList().isOp(p.nameAndId());
		}
		return source.getEntity() == null;
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("yume")
				.requires(YumeCommand::allowed)
				.then(Commands.literal("status").executes(ctx -> reply(ctx, Isolation.status(ctx.getSource().getServer()))))
				.then(Commands.literal("day")
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 999)).executes(YumeCommand::day)))
				.then(Commands.literal("next").executes(YumeCommand::next))
				.then(Commands.literal("play")
						.then(Commands.argument("scene", StringArgumentType.word()).suggests(SCENES)
								.executes(ctx -> play(ctx, null))
								.then(Commands.argument("targets", EntityArgument.players())
										.executes(ctx -> play(ctx, EntityArgument.getPlayers(ctx, "targets"))))))
				.then(Commands.literal("abort").executes(YumeCommand::abort))
				.then(Commands.literal("stage")
						.then(Commands.argument("mode", StringArgumentType.word()).suggests(STAGES).executes(YumeCommand::stage)))
				.then(Commands.literal("finale").executes(YumeCommand::finale))
				.then(Commands.literal("url")
						.executes(YumeCommand::urlShow)
						.then(Commands.argument("text", StringArgumentType.greedyString()).executes(YumeCommand::urlSet)))
				.then(Commands.literal("camera")
						.then(Commands.literal("set")
								.then(Commands.argument("name", StringArgumentType.word()).suggests(CAMERAS).executes(YumeCommand::cameraSet))))
				.then(Commands.literal("room")
						.then(Commands.literal("pos1").executes(ctx -> roomPos(ctx, true)))
						.then(Commands.literal("pos2").executes(ctx -> roomPos(ctx, false))))
				.then(Commands.literal("gift").executes(YumeCommand::gift))
				.then(Commands.literal("spy")
						.then(Commands.literal("on").executes(ctx -> spy(ctx, true)))
						.then(Commands.literal("off").executes(ctx -> spy(ctx, false))))
				.then(Commands.literal("timespeed")
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 1000)).executes(YumeCommand::timeSpeed)))
				.then(Commands.literal("member")
						.then(Commands.argument("side", StringArgumentType.word()).suggests(SIDES)
								.then(Commands.argument("player", StringArgumentType.word()).suggests(NAMES).executes(YumeCommand::member))))
				.then(Commands.literal("operator")
						.then(Commands.literal("add")
								.then(Commands.argument("player", StringArgumentType.word()).suggests(NAMES).executes(ctx -> operator(ctx, true))))
						.then(Commands.literal("remove")
								.then(Commands.argument("player", StringArgumentType.word()).suggests(NAMES).executes(ctx -> operator(ctx, false)))))
				.then(Commands.literal("cast")
						.then(Commands.argument("role", StringArgumentType.word()).suggests(CASTS)
								.then(Commands.argument("player", StringArgumentType.word()).suggests(NAMES).executes(YumeCommand::cast))))
				.then(Commands.literal("seconds")
						.then(Commands.argument("n", IntegerArgumentType.integer(3, 120)).executes(YumeCommand::seconds)))
				.then(Commands.literal("members").executes(YumeCommand::members))
				.then(Commands.literal("reload").executes(YumeCommand::reload)));
	}

	// ---- helpers ----

	/** Reply to the sender only (never broadcast to other operators). */
	private static int reply(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendFailure(Component.literal(text));
		return 0;
	}

	private static MinecraftServer server(CommandContext<CommandSourceStack> ctx) {
		return ctx.getSource().getServer();
	}

	// ---- subcommands ----

	private static int day(CommandContext<CommandSourceStack> ctx) {
		int n = IntegerArgumentType.getInteger(ctx, "n");
		YumeState.get().dreamsSeen = n - 1;
		YumeState.save(server(ctx));
		Isolation.sync(server(ctx));
		return reply(ctx, "次に見る夢を " + n + " 日目にしました（段階: " + Groups.stage() + "）");
	}

	private static int next(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = server(ctx);
		String result = DreamDirector.startDay(server, YumeState.get().dreamsSeen + 1, true);
		Isolation.sync(server);
		return reply(ctx, result);
	}

	private static int play(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
		String name = StringArgumentType.getString(ctx, "scene");
		Scene scene = null;
		for (Scene s : Scene.values()) {
			if (s != Scene.NONE && s.name().equalsIgnoreCase(name)) scene = s;
		}
		if (scene == null) return fail(ctx, "場面が見つかりません: " + name + "（" + String.join(" / ", SCENE_NAMES) + "）");
		Collection<ServerPlayer> viewers = targets;
		if (viewers == null) {
			ServerPlayer self = ctx.getSource().getPlayer();
			if (self == null) return fail(ctx, "対象のプレイヤーを指定してください");
			viewers = List.of(self);
		}
		if (viewers.isEmpty()) return fail(ctx, "対象のプレイヤーがいません");
		return reply(ctx, DreamDirector.playTest(server(ctx), scene, viewers));
	}

	private static int abort(CommandContext<CommandSourceStack> ctx) {
		DreamDirector.abortAll(server(ctx));
		return reply(ctx, "再生中の夢をすべて終わらせました");
	}

	private static int stage(CommandContext<CommandSourceStack> ctx) {
		String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(Locale.ROOT);
		YumeState state = YumeState.get();
		switch (mode) {
			case "auto" -> state.stageOverride = null;
			case "hidden" -> state.stageOverride = RevealStage.HIDDEN;
			case "silhouette" -> state.stageOverride = RevealStage.SILHOUETTE;
			case "visible" -> state.stageOverride = RevealStage.VISIBLE;
			case "finale" -> state.stageOverride = RevealStage.FINALE;
			default -> {
				return fail(ctx, "auto / hidden / silhouette / visible / finale のどれかを指定してください");
			}
		}
		YumeState.save(server(ctx));
		Isolation.sync(server(ctx));
		return reply(ctx, "段階: " + Groups.stage() + (state.stageOverride == null ? "（自動）" : "（固定）"));
	}

	private static int finale(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = server(ctx);
		DreamDirector.finale(server);
		Isolation.sync(server);
		return reply(ctx, "終幕にしました。すべての隔離を解除しました");
	}

	private static int urlShow(CommandContext<CommandSourceStack> ctx) {
		String url = YumeConfig.get().streamUrl;
		return reply(ctx, url == null || url.isBlank() ? "URL は未設定です" : "URL: " + url);
	}

	private static int urlSet(CommandContext<CommandSourceStack> ctx) {
		String text = StringArgumentType.getString(ctx, "text").trim();
		YumeConfig.get().streamUrl = text;
		YumeConfig.save();
		return reply(ctx, "URL を保存しました: " + text);
	}

	private static int cameraSet(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT);
		if (!CAMERA_NAMES.contains(name)) return fail(ctx, "waterfall か room を指定してください");
		ServerPlayer p = ctx.getSource().getPlayer();
		if (p == null) return fail(ctx, "プレイヤーとして実行してください");
		// The camera sees the world from exactly where the player's eyes are now.
		Vec3 eye = p.getEyePosition();
		YumeConfig.Cam cam = new YumeConfig.Cam();
		cam.dimension = p.level().dimension().identifier().toString();
		cam.x = eye.x;
		cam.y = eye.y;
		cam.z = eye.z;
		cam.yaw = p.getYRot();
		cam.pitch = p.getXRot();
		YumeConfig.get().cameras.put(name, cam);
		YumeConfig.save();
		net.amaguri.yume.director.DreamDirector.refreshCameraChunks(ctx.getSource().getServer());
		return reply(ctx, String.format(Locale.ROOT, "カメラ %s を保存しました（%s  %.2f %.2f %.2f  向き %.1f / %.1f）",
				name, cam.dimension, cam.x, cam.y, cam.z, cam.yaw, cam.pitch));
	}

	private static int roomPos(CommandContext<CommandSourceStack> ctx, boolean first) {
		ServerPlayer p = ctx.getSource().getPlayer();
		if (p == null) return fail(ctx, "プレイヤーとして実行してください");
		BlockPos pos = p.blockPosition();
		YumeConfig.Box box = YumeConfig.get().room;
		box.dimension = p.level().dimension().identifier().toString();
		if (first) {
			box.x1 = pos.getX();
			box.y1 = pos.getY();
			box.z1 = pos.getZ();
		} else {
			box.x2 = pos.getX();
			box.y2 = pos.getY();
			box.z2 = pos.getZ();
		}
		YumeConfig.save();
		return reply(ctx, "部屋の" + (first ? "pos1" : "pos2") + " を " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
				+ "（" + box.dimension + "）にしました");
	}

	private static int gift(CommandContext<CommandSourceStack> ctx) {
		int n = Isolation.giftLost(server(ctx));
		return reply(ctx, n + " 人に贈り物を渡しました");
	}

	private static int spy(CommandContext<CommandSourceStack> ctx, boolean on) {
		ServerPlayer p = ctx.getSource().getPlayer();
		if (p == null) return fail(ctx, "プレイヤーとして実行してください");
		YumeState.get().spy.put(p.getUUID().toString(), on);
		YumeState.save(server(ctx));
		return reply(ctx, "向こう側のチャットを" + (on ? "受け取ります" : "受け取りません"));
	}

	private static int timeSpeed(CommandContext<CommandSourceStack> ctx) {
		int n = IntegerArgumentType.getInteger(ctx, "n");
		YumeState.get().timeSpeed = n;
		YumeState.save(server(ctx));
		return reply(ctx, "時間の速さを ×" + n + " にしました");
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = server(ctx);
		YumeConfig.load(server);
		net.amaguri.yume.director.DreamDirector.refreshCameraChunks(server);
		Isolation.sync(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			server.getCommands().sendCommands(p);
		}
		return reply(ctx, "設定を読み込み直しました");
	}

	// ---- in-game setup (no need to edit config/yume.json by hand) ----

	private static void removeIgnoreCase(List<String> list, String name) {
		list.removeIf(x -> x != null && x.equalsIgnoreCase(name));
	}

	private static void afterMembershipChange(MinecraftServer server) {
		YumeConfig.save();
		Isolation.sync(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			server.getCommands().sendCommands(p);
		}
	}

	private static int member(CommandContext<CommandSourceStack> ctx) {
		String side = StringArgumentType.getString(ctx, "side").toLowerCase(Locale.ROOT);
		String name = StringArgumentType.getString(ctx, "player");
		YumeConfig c = YumeConfig.get();
		removeIgnoreCase(c.dreamers, name);
		removeIgnoreCase(c.lost, name);
		switch (side) {
			case "dreamer" -> c.dreamers.add(name);
			case "lost" -> c.lost.add(name);
			case "none" -> { }
			default -> {
				return fail(ctx, "dreamer / lost / none のどれかを指定してください");
			}
		}
		afterMembershipChange(server(ctx));
		return reply(ctx, name + " → " + side + "（寝る側 " + c.dreamers.size() + "人 / 部屋側 " + c.lost.size() + "人）");
	}

	private static int operator(CommandContext<CommandSourceStack> ctx, boolean add) {
		String name = StringArgumentType.getString(ctx, "player");
		YumeConfig c = YumeConfig.get();
		removeIgnoreCase(c.operators, name);
		if (add) c.operators.add(name);
		afterMembershipChange(server(ctx));
		return reply(ctx, name + (add ? " を仕掛け人に追加しました" : " を仕掛け人から外しました"));
	}

	private static int cast(CommandContext<CommandSourceStack> ctx) {
		String role = StringArgumentType.getString(ctx, "role").toLowerCase(Locale.ROOT);
		String name = StringArgumentType.getString(ctx, "player");
		YumeConfig c = YumeConfig.get();
		switch (role) {
			case "sheep" -> c.cast.sheepRider = name;
			case "miner" -> c.cast.miner = name;
			case "balloon" -> c.cast.balloon = name;
			case "help" -> c.cast.helpFigure = name;
			case "pov" -> c.povTarget = name;
			default -> {
				return fail(ctx, "sheep / miner / balloon / help / pov のどれかを指定してください");
			}
		}
		YumeConfig.save();
		return reply(ctx, role + " の役を " + name + " にしました");
	}

	private static int seconds(CommandContext<CommandSourceStack> ctx) {
		int n = IntegerArgumentType.getInteger(ctx, "n");
		YumeConfig.get().dreamSeconds = n;
		YumeConfig.save();
		return reply(ctx, "夢の長さを " + n + " 秒にしました");
	}

	private static int members(CommandContext<CommandSourceStack> ctx) {
		YumeConfig c = YumeConfig.get();
		return reply(ctx, "寝る側: " + c.dreamers + "\n部屋側: " + c.lost + "\n仕掛け人: " + c.operators
				+ "\n配役 sheep=" + c.cast.sheepRider + " miner=" + c.cast.miner + " balloon=" + c.cast.balloon
				+ " help=" + c.cast.helpFigure + " pov=" + c.povTarget + "\n夢の長さ " + c.dreamSeconds + "秒");
	}
}
