package com.summerbuddies.custommissions.reward;

import com.summerbuddies.custommissions.Constants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The environment a {@link Reward} acts in: the server plus the two command channels rewards use. Mirrors
 * StoryKit's {@code SequenceContext} command runners (level 4, output suppressed). {@code {player}} in a
 * template is replaced with the recipient's name.
 */
public final class RewardContext {

    private final MinecraftServer server;

    public RewardContext(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }

    /** Run a command once as the server (level 4, output suppressed). */
    public void runServer(String command) {
        try {
            server.getCommands().performPrefixedCommand(serverSource(), command);
        } catch (Exception e) {
            Constants.LOG.warn("[custommissions] server command failed '{}': {}", command, e.toString());
        }
    }

    /** Run a command as the player (level 4, output suppressed), substituting {@code {player}}. */
    public void runAsPlayer(ServerPlayer player, String template) {
        String command = template.replace("{player}", player.getGameProfile().getName());
        try {
            server.getCommands().performPrefixedCommand(playerSource(player), command);
        } catch (Exception e) {
            Constants.LOG.warn("[custommissions] player command failed '{}': {}", command, e.toString());
        }
    }

    private CommandSourceStack serverSource() {
        return server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
    }

    private CommandSourceStack playerSource(ServerPlayer player) {
        return player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
    }
}
