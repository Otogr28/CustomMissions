package com.summerbuddies.custommissions.reward;

import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.bridge.CompanionsBridge;
import com.summerbuddies.custommissions.bridge.RealmGatesBridge;
import com.summerbuddies.custommissions.bridge.StoryKitBridge;
import com.summerbuddies.custommissions.state.LoreState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Immutable record implementations of every {@link Reward} type. Built by {@link RewardFactory}. */
public final class Rewards {

    private Rewards() {}

    /** Give an item stack (leftover that doesn't fit is dropped at the player's feet). */
    public record Item(ResourceLocation item, int count) implements Reward {
        @Override public RewardType type() { return RewardType.ITEM; }
        @Override public String describe() { return count + "x " + item; }
        @Override public void grant(ServerPlayer player, RewardContext ctx) {
            net.minecraft.world.item.Item it = ForgeRegistries.ITEMS.getValue(item);
            if (it == null || it == Items.AIR) {
                Constants.LOG.warn("[custommissions] item reward: unknown item '{}'", item);
                return;
            }
            ItemStack stack = new ItemStack(it, Math.max(1, count));
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    /** Give experience points. */
    public record Xp(int amount) implements Reward {
        @Override public RewardType type() { return RewardType.XP; }
        @Override public String describe() { return amount + " XP"; }
        @Override public void grant(ServerPlayer player, RewardContext ctx) {
            player.giveExperiencePoints(Math.max(0, amount));
        }
    }

    /** Run an arbitrary command, as the server or as the player; {@code {player}} is substituted. */
    public record Command(String command, boolean asPlayer) implements Reward {
        @Override public RewardType type() { return RewardType.COMMAND; }
        @Override public String describe() { return "/" + command; }
        @Override public void grant(ServerPlayer player, RewardContext ctx) {
            if (asPlayer) {
                ctx.runAsPlayer(player, command);
            } else {
                ctx.runServer(command.replace("{player}", player.getGameProfile().getName()));
            }
        }
    }

    /** Play a StoryKit cutscene (no-op without StoryKit). */
    public record Cutscene(String script) implements Reward {
        @Override public RewardType type() { return RewardType.CUTSCENE; }
        @Override public String describe() { return "cutscene " + script; }
        @Override public void grant(ServerPlayer player, RewardContext ctx) {
            StoryKitBridge.playCutscene(ctx, player, script);
        }
    }

    /** Unlock a Realm Gates dimension for the player (no-op without Realm Gates). */
    public record UnlockGate(ResourceLocation gate) implements Reward {
        @Override public RewardType type() { return RewardType.UNLOCK_GATE; }
        @Override public String describe() { return "unlock " + gate; }
        @Override public void grant(ServerPlayer player, RewardContext ctx) {
            RealmGatesBridge.unlockGate(ctx, player, gate);
        }
    }

    /** Grant a Custom Companions companion (no-op without Custom Companions). */
    public record Companion(String companion) implements Reward {
        @Override public RewardType type() { return RewardType.COMPANION; }
        @Override public String describe() { return "companion " + companion; }
        @Override public void grant(ServerPlayer player, RewardContext ctx) {
            CompanionsBridge.grantCompanion(ctx, player, companion);
        }
    }

    /** Advance the server-wide lore stage (to an explicit value, or +1 when {@code to} is null). */
    public record LoreStageAdvance(@Nullable Integer to) implements Reward {
        @Override public RewardType type() { return RewardType.LORE_STAGE_ADVANCE; }
        @Override public String describe() { return "lore stage " + (to == null ? "+1" : "-> " + to); }
        @Override public void grant(ServerPlayer player, RewardContext ctx) {
            LoreState ls = LoreState.get(player.serverLevel());
            if (to == null) {
                ls.advanceStage();
            } else {
                ls.setStage(to);
            }
        }
    }
}
