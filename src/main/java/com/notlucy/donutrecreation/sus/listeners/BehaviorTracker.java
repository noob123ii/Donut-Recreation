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
    registerAntiCheatListeners();
  }

  private void registerAntiCheatListeners() {
    registerVulcanListener();
    registerGrimListener();
    registerMatrixListener();
    registerNCPListener();
    registerAACListener();
  }

  private void registerVulcanListener() {
    try {
      Class.forName("com.github.retrooper.vulcan.VulcanFlagEvent");
      Bukkit.getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onVulcanFlag(org.bukkit.event.Event event) {
          try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            String reason = (String) event.getClass().getMethod("getReason").invoke(event);
            plugin.susFlagManager().flag(player, reason, "vulcan");
            broadcastFlag(player, "Vulcan", reason);
          } catch (Throwable ignored) { }
        }
      }, plugin);
    } catch (ClassNotFoundException ignored) { }
  }

  private void registerGrimListener() {
    try {
      Class.forName("me.grimmreaper42.grimac.GrimPlayer");
      Bukkit.getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onGrimFlag(org.bukkit.event.Event event) {
          try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            String verbose = (String) event.getClass().getMethod("getVerbose").invoke(event);
            plugin.susFlagManager().flag(player, verbose, "grimac");
            broadcastFlag(player, "GrimAC", verbose);
          } catch (Throwable ignored) { }
        }
      }, plugin);
    } catch (ClassNotFoundException ignored) { }
  }

  private void registerMatrixListener() {
    try {
      Class.forName("com.github.Reflact.Matrix.Matrix");
      Bukkit.getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onMatrixFlag(org.bukkit.event.Event event) {
          try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            String hack = (String) event.getClass().getMethod("getHackType").invoke(event);
            plugin.susFlagManager().flag(player, hack, "matrix");
            broadcastFlag(player, "Matrix", hack);
          } catch (Throwable ignored) { }
        }
      }, plugin);
    } catch (ClassNotFoundException ignored) { }
  }

  private void registerNCPListener() {
    try {
      Class.forName("fr.neatmonster.nocheatplus.checks.CheckType");
      Bukkit.getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onNCPViolation(org.bukkit.event.Event event) {
          try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            plugin.susFlagManager().flag(player, "Violation", "ncp");
            broadcastFlag(player, "NoCheatPlus", "Violation");
          } catch (Throwable ignored) { }
        }
      }, plugin);
    } catch (ClassNotFoundException ignored) { }
  }

  private void registerAACListener() {
    try {
      Class.forName("me.maxHenrikDev.AAC.AAC");
      Bukkit.getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onAACFlag(org.bukkit.event.Event event) {
          try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            String violation = (String) event.getClass().getMethod("getViolationLevel").invoke(event);
            plugin.susFlagManager().flag(player, "Violation level " + violation, "aac");
            broadcastFlag(player, "AAC", "Violation level " + violation);
          } catch (Throwable ignored) { }
        }
      }, plugin);
    } catch (ClassNotFoundException ignored) { }
  }

  private void broadcastFlag(Player player, String antiCheat, String reason) {
    var flag = plugin.susFlagManager().getFlag(player.getUniqueId());
    int count = flag != null ? flag.count() : 1;
    String msg = plugin.color("&c[" + antiCheat + "] &f" + player.getName()
        + " &7" + reason + " &c(" + count + " flags)");
    Bukkit.getOnlinePlayers().stream()
        .filter(p -> p.hasPermission("donutrecreation.*"))
        .forEach(op -> op.sendMessage(msg));
    plugin.getLogger().info("[AntiCheat] " + antiCheat + " flagged " + player.getName()
        + ": " + reason + " (" + count + " flags)");
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