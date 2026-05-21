package com.crimsonwarpedcraft.donutpluginrecreation.sus.listeners;

import com.crimsonwarpedcraft.donutpluginrecreation.DonutPluginRecreation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Watches "soft" behaviour signals and pushes them to the sus flag manager so they show up
 * in {@code /sus}. These are heuristics, not bans — they catch base-finding-style behaviour
 * (long elytra flights, sustained mining bursts) and macro-like repetition patterns.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class BehaviorTracker implements Listener {

  private static final long ELYTRA_FLAG_MS = 5L * 60L * 1000L;
  private static final long MINE_WINDOW_MS = 30_000L;
  private static final int MINE_BURST_COUNT = 200;
  private static final int MACRO_REPEAT_THRESHOLD = 100;
  private static final long MACRO_WINDOW_MS = 60_000L;
  private static final long REFLAG_COOLDOWN_MS = 60_000L;

  private final DonutPluginRecreation plugin;
  private final Map<UUID, BehaviorState> states = new HashMap<>();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Plugin instance is shared by Bukkit.")
  public BehaviorTracker(DonutPluginRecreation plugin) {
    this.plugin = plugin;
  }

  public void start() {
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
  }

  private void tick() {
    long now = System.currentTimeMillis();
    for (Player player : Bukkit.getOnlinePlayers()) {
      BehaviorState state = states.computeIfAbsent(player.getUniqueId(), id -> new BehaviorState());
      if (player.isGliding()) {
        state.elytraMs += 1000L;
        if (state.elytraMs >= ELYTRA_FLAG_MS && cooldownReady(state.lastElytraFlag, now)) {
          state.lastElytraFlag = now;
          plugin.susFlagManager().flag(player,
              "Elytra flight: " + (state.elytraMs / 60000L) + "m sustained");
          state.elytraMs = 0;
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    BehaviorState state = states.computeIfAbsent(player.getUniqueId(), id -> new BehaviorState());
    long now = System.currentTimeMillis();
    state.miningTimes.add(now);
    while (!state.miningTimes.isEmpty() && now - state.miningTimes.peekFirst() > MINE_WINDOW_MS) {
      state.miningTimes.pollFirst();
    }
    if (state.miningTimes.size() >= MINE_BURST_COUNT
        && cooldownReady(state.lastMineFlag, now)) {
      state.lastMineFlag = now;
      plugin.susFlagManager().flag(player,
          "Sustained mining: " + state.miningTimes.size() + " blocks in "
              + (MINE_WINDOW_MS / 1000) + "s (possible base-finding)");
    }
    Material mat = event.getBlock().getType();
    countAction(state, "break:" + mat.name(), now, player, "Macroing (break " + mat.name() + ")");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction().toString().startsWith("RIGHT_CLICK")) {
      Player player = event.getPlayer();
      BehaviorState state =
          states.computeIfAbsent(player.getUniqueId(), id -> new BehaviorState());
      long now = System.currentTimeMillis();
      Material hand = player.getInventory().getItemInMainHand().getType();
      countAction(state, "use:" + hand.name(), now, player, "Macroing (use " + hand.name() + ")");
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    states.remove(event.getPlayer().getUniqueId());
  }

  private void countAction(
      BehaviorState state, String key, long now, Player player, String flagMessage) {
    Deque<Long> deque = state.actionWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
    deque.add(now);
    while (!deque.isEmpty() && now - deque.peekFirst() > MACRO_WINDOW_MS) {
      deque.pollFirst();
    }
    Long last = state.lastMacroFlag.get(key);
    if (deque.size() >= MACRO_REPEAT_THRESHOLD
        && (last == null || now - last >= REFLAG_COOLDOWN_MS)) {
      state.lastMacroFlag.put(key, now);
      plugin.susFlagManager().flag(player, flagMessage + " x" + deque.size());
    }
  }

  private static boolean cooldownReady(long last, long now) {
    return last == 0 || now - last >= REFLAG_COOLDOWN_MS;
  }

  private static class BehaviorState {
    long elytraMs;
    long lastElytraFlag;
    long lastMineFlag;
    final Deque<Long> miningTimes = new ArrayDeque<>();
    final Map<String, Deque<Long>> actionWindows = new HashMap<>();
    final Map<String, Long> lastMacroFlag = new HashMap<>();
  }
}
