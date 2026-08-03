package com.notlucy.donutrecreation.sus.listeners;

import com.notlucy.donutrecreation.DonutRecreation;
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

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class BehaviorTracker implements Listener {

  private static final long ELYTRA_MS = 5L * 60L * 1000L;
  private static final long MINE_WINDOW_MS = 30_000L;
  private static final int MINE_BURST = 200;
  private static final int REPEAT_LIMIT = 100;
  private static final long MACRO_WINDOW_MS = 60_000L;
  private static final long RE_COOLDOWN_MS = 60_000L;

  private final DonutRecreation plugin;
  private final Map<UUID, State> states = new HashMap<>();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Plugin instance is shared by Bukkit.")
  public BehaviorTracker(DonutRecreation plugin) {
    this.plugin = plugin;
  }

  public void start() {
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
  }

  private void tick() {
    long now = System.currentTimeMillis();
    for (Player player : Bukkit.getOnlinePlayers()) {
      State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
      if (player.isGliding()) {
        state.elytraMs += 1000L;
        if (state.elytraMs >= ELYTRA_MS && cooldownReady(state.elytraAt, now)) {
          state.elytraAt = now;
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
    State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
    long now = System.currentTimeMillis();
    state.mines.add(now);
    while (!state.mines.isEmpty() && now - state.mines.peekFirst() > MINE_WINDOW_MS) {
      state.mines.pollFirst();
    }
    if (state.mines.size() >= MINE_BURST && cooldownReady(state.mineAt, now)) {
      state.mineAt = now;
      plugin.susFlagManager().flag(player,
          "Sustained mining: " + state.mines.size() + " blocks in "
              + (MINE_WINDOW_MS / 1000) + "s (possible base-finding)");
    }
    Material mat = event.getBlock().getType();
    track(state, "break:" + mat.name(), now, player, "Macroing (break " + mat.name() + ")");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction().toString().startsWith("RIGHT_CLICK")) {
      Player player = event.getPlayer();
      State state =
          states.computeIfAbsent(player.getUniqueId(), id -> new State());
      long now = System.currentTimeMillis();
      Material hand = player.getInventory().getItemInMainHand().getType();
      track(state, "use:" + hand.name(), now, player, "Macroing (use " + hand.name() + ")");
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    states.remove(event.getPlayer().getUniqueId());
  }

  private void track(
      State state, String key, long now, Player player, String msg) {
    Deque<Long> q = state.windows.computeIfAbsent(key, k -> new ArrayDeque<>());
    q.add(now);
    while (!q.isEmpty() && now - q.peekFirst() > MACRO_WINDOW_MS) {
      q.pollFirst();
    }
    Long last = state.macroAt.get(key);
    if (q.size() >= REPEAT_LIMIT
        && (last == null || now - last >= RE_COOLDOWN_MS)) {
      state.macroAt.put(key, now);
      plugin.susFlagManager().flag(player, msg + " x" + q.size());
    }
  }

  private static boolean cooldownReady(long last, long now) {
    return last == 0 || now - last >= RE_COOLDOWN_MS;
  }

  private static class State {
    long elytraMs;
    long elytraAt;
    long mineAt;
    final Deque<Long> mines = new ArrayDeque<>();
    final Map<String, Deque<Long>> windows = new HashMap<>();
    final Map<String, Long> macroAt = new HashMap<>();
  }
}