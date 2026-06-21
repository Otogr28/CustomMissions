package com.summerbuddies.custommissions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.ai.DailyIngestTask;
import com.summerbuddies.custommissions.ai.MissionContextExporter;
import com.summerbuddies.custommissions.ai.WorldStateExporter;
import com.summerbuddies.custommissions.event.MissionEvent;
import com.summerbuddies.custommissions.event.MissionEventBus;
import com.summerbuddies.custommissions.mission.LoadResult;
import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionLocation;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.objective.Objective;
import com.summerbuddies.custommissions.state.LoreState;
import com.summerbuddies.custommissions.state.MissionTracker;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import com.summerbuddies.custommissions.state.PlayerMissions;
import com.summerbuddies.custommissions.waypoint.MarkerFallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Registers and implements {@code /mission ...}. Player-facing subcommands (list/progress/accept/abandon
 * and {@code signal}) are permission 0; administrative ones (reload/assign/complete/daily/lorestage/flag/
 * waypoint/export) require permission 2. {@code signal} is intentionally open so KubeJS/EasyNPC can emit it.
 */
@Mod.EventBusSubscriber(modid = Constants.MODID)
public final class MissionCommand {

    private MissionCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mission");

        // ---- player-facing (perm 0) ----
        root.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));

        root.then(Commands.literal("progress").executes(ctx -> progress(ctx.getSource())));

        root.then(Commands.literal("accept")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(availableIds(c.getSource()), b))
                        .executes(ctx -> accept(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("abandon")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(activeIds(c.getSource()), b))
                        .executes(ctx -> abandon(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));

        // ---- signal (perm 0: KubeJS/EasyNPC emit it) ----
        root.then(Commands.literal("signal")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> signalSelf(ctx.getSource(), StringArgumentType.getString(ctx, "name"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> signalSelf(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        IntegerArgumentType.getInteger(ctx, "count")))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> signal(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                EntityArgument.getPlayers(ctx, "players")))))));

        // ---- admin (perm 2) ----
        root.then(Commands.literal("reload").requires(op()).executes(ctx -> reload(ctx.getSource())));

        root.then(Commands.literal("assign").requires(op())
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(MissionManager.ids(), b))
                                .executes(ctx -> assign(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "id"))))));

        root.then(Commands.literal("complete").requires(op())
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(MissionManager.ids(), b))
                                .executes(ctx -> forceComplete(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "id"))))));

        root.then(Commands.literal("daily").requires(op())
                .then(Commands.literal("regen")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> regen(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"))))));

        root.then(Commands.literal("lorestage").requires(op())
                .then(Commands.literal("get").executes(ctx -> loreGet(ctx.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("chapter", IntegerArgumentType.integer(0))
                                .then(Commands.argument("stage", IntegerArgumentType.integer(0))
                                        .executes(ctx -> loreSet(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "chapter"),
                                                IntegerArgumentType.getInteger(ctx, "stage")))))));

        root.then(Commands.literal("flag").requires(op())
                .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> flag(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> flag(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false)))));

        root.then(Commands.literal("waypoint").requires(op())
                .then(Commands.literal("debug")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> waypointDebug(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "players"))))));

        root.then(Commands.literal("export").requires(op())
                .then(Commands.literal("world").executes(ctx -> exportWorld(ctx.getSource())))
                .then(Commands.literal("player")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> exportPlayers(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "players"))))));

        dispatcher.register(root);
    }

    // ---- player-facing -------------------------------------------------------------------------

    private static int list(CommandSourceStack src) {
        ServerPlayer self = playerOrFail(src);
        if (self == null) return 0;
        PlayerMissions pm = PlayerMissionStore.get(self);
        src.sendSuccess(() -> Component.literal("— Your missions —").withStyle(ChatFormatting.GOLD), false);

        for (String id : pm.activeIds()) {
            Mission m = MissionManager.get(id);
            int[] c = pm.counters(id);
            String label = m != null ? m.title() : id;
            int done = m != null && c != null ? doneCount(m, c) : 0;
            int total = m != null ? m.objectives().size() : 0;
            src.sendSuccess(() -> Component.literal("  ● " + label + "  (" + done + "/" + total + ")")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        List<Mission> available = MissionTracker.available(self);
        for (Mission m : available) {
            src.sendSuccess(() -> Component.literal("  ○ " + m.title() + "  [/mission accept " + m.id() + "]")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        src.sendSuccess(() -> Component.literal("  completed: " + pm.completedIds().size())
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int progress(CommandSourceStack src) {
        ServerPlayer self = playerOrFail(src);
        if (self == null) return 0;
        PlayerMissions pm = PlayerMissionStore.get(self);
        if (pm.activeIds().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No active missions.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (String id : pm.activeIds()) {
            Mission m = MissionManager.get(id);
            int[] c = pm.counters(id);
            if (m == null || c == null) continue;
            src.sendSuccess(() -> Component.literal(m.title()).withStyle(ChatFormatting.GOLD), false);
            for (int i = 0; i < m.objectives().size(); i++) {
                Objective o = m.objectives().get(i);
                int have = i < c.length ? c[i] : 0;
                boolean done = have >= o.required();
                String line = "  " + (done ? "[x] " : "[ ] ") + o.describe() + "  " + have + "/" + o.required();
                src.sendSuccess(() -> Component.literal(line)
                        .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
            }
        }
        return 1;
    }

    private static int accept(CommandSourceStack src, String id) {
        ServerPlayer self = playerOrFail(src);
        if (self == null) return 0;
        Mission m = MissionManager.get(id);
        if (m == null) {
            src.sendFailure(Component.literal("Unknown mission: " + id));
            return 0;
        }
        if (m.isDaily()) {
            src.sendFailure(Component.literal("Daily missions are assigned automatically."));
            return 0;
        }
        LoreState ls = LoreState.get(self.serverLevel());
        PlayerMissions pm = PlayerMissionStore.get(self);
        if (!m.prerequisites().met(ls.stage(), ls.flags(), pm.completedIds(),
                self.serverLevel().dimension().location())) {
            src.sendFailure(Component.literal("You don't meet the prerequisites for " + m.title() + "."));
            return 0;
        }
        if (!MissionTracker.assign(self, m)) {
            src.sendFailure(Component.literal("Already active or completed."));
            return 0;
        }
        return 1;
    }

    private static int abandon(CommandSourceStack src, String id) {
        ServerPlayer self = playerOrFail(src);
        if (self == null) return 0;
        if (MissionTracker.abandon(self, id)) {
            src.sendSuccess(() -> Component.literal("Abandoned " + id + ".").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        src.sendFailure(Component.literal("That mission isn't active."));
        return 0;
    }

    // ---- signal ----------------------------------------------------------------------------------

    private static int signalSelf(CommandSourceStack src, String name, int count) {
        ServerPlayer self = src.getEntity() instanceof ServerPlayer p ? p : null;
        if (self == null) {
            src.sendFailure(Component.literal("Specify players: /mission signal " + name + " " + count + " <players>"));
            return 0;
        }
        return signal(src, name, count, List.of(self));
    }

    private static int signal(CommandSourceStack src, String name, int count, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            MissionEventBus.dispatch(p, new MissionEvent.Signal(name, count));
        }
        int n = players.size();
        src.sendSuccess(() -> Component.literal("Signal '" + name + "' x" + count + " -> " + n + " player(s)."), false);
        return n;
    }

    // ---- admin -----------------------------------------------------------------------------------

    private static int reload(CommandSourceStack src) {
        LoadResult r = MissionManager.reload();
        src.sendSuccess(() -> Component.literal("CustomMissions: " + r.summary())
                .withStyle(r.warnings.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        int shown = 0;
        for (String w : r.warnings) {
            if (shown++ >= 8) {
                src.sendSystemMessage(Component.literal("  … " + (r.warnings.size() - 8) + " more (see log)")
                        .withStyle(ChatFormatting.GRAY));
                break;
            }
            src.sendSystemMessage(Component.literal("  ! " + w).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int assign(CommandSourceStack src, ServerPlayer player, String id) {
        Mission m = MissionManager.get(id);
        if (m == null) {
            src.sendFailure(Component.literal("Unknown mission: " + id));
            return 0;
        }
        boolean ok = MissionTracker.assign(player, m);
        src.sendSuccess(() -> Component.literal((ok ? "Assigned " : "Could not assign ") + id + " to "
                + player.getGameProfile().getName()).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return ok ? 1 : 0;
    }

    private static int forceComplete(CommandSourceStack src, ServerPlayer player, String id) {
        Mission m = MissionManager.get(id);
        if (m == null) {
            src.sendFailure(Component.literal("Unknown mission: " + id));
            return 0;
        }
        MissionTracker.complete(player, m);
        src.sendSuccess(() -> Component.literal("Completed " + id + " for " + player.getGameProfile().getName())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int regen(CommandSourceStack src, Collection<ServerPlayer> players) {
        int total = 0;
        for (ServerPlayer p : players) {
            total += DailyIngestTask.ingest(p);
        }
        int n = total;
        src.sendSuccess(() -> Component.literal("Ingested " + n + " daily mission(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return total;
    }

    private static int loreGet(CommandSourceStack src) {
        LoreState ls = LoreState.get(src.getServer().overworld());
        src.sendSuccess(() -> Component.literal("Lore: chapter " + ls.chapter() + ", stage " + ls.stage())
                .withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("  flags: " + ls.flags()).withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal("  gates: " + ls.unlockedGates()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int loreSet(CommandSourceStack src, int chapter, int stage) {
        LoreState ls = LoreState.get(src.getServer().overworld());
        ls.setChapter(chapter);
        ls.setStage(stage);
        src.sendSuccess(() -> Component.literal("Lore set to chapter " + chapter + ", stage " + stage)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int flag(CommandSourceStack src, String name, boolean add) {
        LoreState ls = LoreState.get(src.getServer().overworld());
        boolean changed = add ? ls.addFlag(name) : ls.removeFlag(name);
        src.sendSuccess(() -> Component.literal((add ? "Added" : "Removed") + " flag '" + name + "'"
                + (changed ? "" : " (no change)")).withStyle(ChatFormatting.GREEN), true);
        return changed ? 1 : 0;
    }

    private static int waypointDebug(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            MissionLocation loc = new MissionLocation(p.serverLevel().dimension().location(),
                    p.blockPosition().getX(), p.blockPosition().getY(), p.blockPosition().getZ(),
                    4, "Debug", MissionLocation.DEFAULT_RGB);
            MarkerFallback.show(p, "debug", loc);
        }
        src.sendSuccess(() -> Component.literal("Pushed a debug marker.").withStyle(ChatFormatting.GREEN), false);
        return players.size();
    }

    private static int exportWorld(CommandSourceStack src) {
        WorldStateExporter.export(src.getServer());
        src.sendSuccess(() -> Component.literal("Exported world_state.json").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int exportPlayers(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            MissionContextExporter.export(p);
        }
        src.sendSuccess(() -> Component.literal("Exported context for " + players.size() + " player(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static java.util.function.Predicate<CommandSourceStack> op() {
        return src -> src.hasPermission(2);
    }

    @Nullable
    private static ServerPlayer playerOrFail(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer p) {
            return p;
        }
        src.sendFailure(Component.literal("This command is for players."));
        return null;
    }

    private static int doneCount(Mission m, int[] counters) {
        int done = 0;
        for (int i = 0; i < m.objectives().size(); i++) {
            if (i < counters.length && counters[i] >= m.objectives().get(i).required()) {
                done++;
            }
        }
        return done;
    }

    private static Collection<String> activeIds(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer p) {
            return PlayerMissionStore.get(p).activeIds();
        }
        return MissionManager.ids();
    }

    private static Collection<String> availableIds(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer p) {
            return MissionTracker.available(p).stream().map(Mission::id).toList();
        }
        return MissionManager.ids();
    }
}
