package com.notlucy.donutrecreation.sus.listeners;

import com.notlucy.donutrecreation.DonutRecreation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
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
    hookAntiCheat("Vulcan", "vulcan", "Vulcan", new String[]{
        "me.frep.vulcan.api.event.VulcanFlagEvent",
        "com.github.retrooper.vulcan.VulcanFlagEvent",
        "vg.anticheat.api.event.FlagEvent"},
        new String[]{"getCheck", "getViolations"});
    hookAntiCheat("Matrix", "matrix", "Matrix", new String[]{
        "me.rerere.matrix.api.events.PlayerViolationEvent"},
        new String[]{"getHackType", "getMessage"});
    hookAntiCheat("GrimAC", "grimac", "GrimAC", new String[]{
        "ac.grim.grimac.events.FlagEvent",
        "ac.grim.grimac.api.events.FlagEvent"},
        new String[]{"getCheckName", "getViolations"});
    hookAntiCheat("NoCheatPlus", "ncp", "NoCheatPlus", new String[]{
        "fr.neatmonster.nocheatplus.events.report.SimpleCheckHackEvent"},
        new String[]{"getCheck"});
    hookAntiCheat("AAC", "aac", "AAC", new String[]{
        "me.maxHenrikDev.AAC.events.AACViolationEvent"},
        new String[]{"getCheck", "getViolationLevel"});
  }

  private void hookAntiCheat(String pluginName, String category, String displayName,
      String[] eventClassCandidates, String[] reasonMethods) {
    try {
      Plugin ac = Bukkit.getPluginManager().getPlugin(pluginName);
      if (ac == null) {
        String needle = pluginName.toLowerCase(Locale.ROOT);
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
          String main = p.getDescription().getMain();
          if (main != null && main.toLowerCase(Locale.ROOT).contains(needle)) {
            ac = p;
            break;
          }
        }
      }
      if (ac == null) {
        plugin.getLogger().info("[acsus] " + pluginName + " not installed, hook skipped.");
        return;
      }
      Class<?> eventClass = null;
      for (String candidate : eventClassCandidates) {
        try {
          eventClass = Class.forName(candidate, false, ac.getClass().getClassLoader());
          break;
        } catch (Throwable ignored) { }
      }
      if (eventClass == null) {
        plugin.getLogger().warning("[acsus] " + pluginName
            + " found but none of the known flag event classes exist (tried "
            + eventClassCandidates.length + " names).");
        return;
      }
      final Class<?> expected = eventClass;
      Object handlers;
      try {
        handlers = expected.getMethod("getHandlerList").invoke(null);
      } catch (Throwable t) {
        plugin.getLogger().warning("[acsus] " + pluginName
            + " flag event is not a Bukkit event, hook skipped.");
        return;
      }
      Listener listener = new Listener() { };
      EventExecutor executor = (l, event) -> handleFlag(event, expected, category, displayName, reasonMethods);
      handlers.getClass().getMethod("register", RegisteredListener.class).invoke(
          handlers,
          new RegisteredListener(listener, executor, EventPriority.MONITOR, plugin, false));
      plugin.getLogger().info("[acsus] hooked " + pluginName + " flags -> /acsus ("
          + expected.getName() + ")");
    } catch (Throwable e) {
      plugin.getLogger().warning("[acsus] failed to hook " + pluginName + ": " + e);
    }
  }

  private void handleFlag(org.bukkit.event.Event event, Class<?> expected,
      String category, String displayName, String[] reasonMethods) {
    if (!expected.isInstance(event)) {
      return;
    }
    try {
      Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
      if (player == null) {
        return;
      }
      String reason = extractReason(event, reasonMethods);
      plugin.susFlagManager().flag(player, reason, category);
      if (Bukkit.isPrimaryThread()) {
        broadcastFlag(player, displayName, reason);
      } else {
        Bukkit.getScheduler().runTask(plugin,
            () -> broadcastFlag(player, displayName, reason));
      }
    } catch (Throwable ignored) { }
  }

  private static String extractReason(Object event, String[] methods) {
    StringBuilder sb = new StringBuilder();
    for (String name : methods) {
      try {
        Object value = event.getClass().getMethod(name).invoke(event);
        if (value == null) {
          continue;
        }
        String part;
        if (value instanceof String s) {
          part = s;
        } else {
          try {
            Object checkName = value.getClass().getMethod("getCheckName").invoke(value);
            Object checkType = value.getClass().getMethod("getCheckType").invoke(value);
            Object description = value.getClass().getMethod("getDescription").invoke(value);
            part = checkName + (checkType != null ? " (" + checkType + ")" : "")
                + (description != null ? " - " + description : "");
          } catch (Throwable inner) {
            part = String.valueOf(value);
          }
        }
        if (part != null && !part.isBlank() && sb.indexOf(part) == -1) {
          if (sb.length() > 0) {
            sb.append(" ");
          }
          sb.append(part);
        }
      } catch (Throwable ignored) { }
    }
    return sb.isEmpty() ? "Violation" : sb.toString();
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