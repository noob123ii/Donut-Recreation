package com.notlucy.donutrecreation.sus.listeners;

import com.notlucy.donutrecreation.DonutRecreation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class BehaviorTracker implements Listener {

  private static final long ELYTRA_MS = 5L * 60L * 1000L;
  private static final long MINE_WINDOW_MS = 30_000L;
  private static final int MINE_BURST = 200;
  private static final int REPEAT_LIMIT = 100;
  private static final long MACRO_WINDOW_MS = 60_000L;
  private static final long RE_COOLDOWN_MS = 60_000L;
  private static final long DIG_TIME_WINDOW_MS = 10_000L;
  private static final int MIN_DIG_STREAK = 5;
  private static final long FAST_GAP_MS = 250L;
  private static final int FAST_GAP_MIN = 8;

  private final DonutRecreation plugin;
  private final Map<UUID, State> states = new HashMap<>();
  private final Set<String> hookedEventClasses = ConcurrentHashMap.newKeySet();
  private final Set<String> hookedPlugins = ConcurrentHashMap.newKeySet();
  private static final String[] DEFAULT_REASON_METHODS = {
      "getCheckName", "getCheck", "getHackType", "getType", "getMessage",
      "getDescription", "getViolations", "getViolationLevel"
  };

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Plugin instance is shared blockY Bukkit.")
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
    registerAdditionalAntiCheats();
    hookCustomAntiCheats();
    discoverAntiCheatEvents();
    plugin.getLogger().info("[acsus] anti-cheat hooks registered: "
        + hookedPlugins.size() + " plugin(s)");
  }

  private void registerAdditionalAntiCheats() {
    hookAntiCheat("Spartan", "spartan", "Spartan", new String[]{
        "me.vagdedes.spartan.api.PlayerViolationEvent",
        "me.vagdedes.spartan.api.event.PlayerViolationEvent"},
        new String[]{"getViolation", "getCheck", "getHackType", "getMessage"});
    hookAntiCheat("Verus", "verus", "Verus", new String[]{
        "me.verus.api.event.FlagEvent",
        "me.verus.api.event.PlayerViolationEvent"},
        new String[]{"getCheck", "getCheckName", "getViolation", "getMessage"});
    hookAntiCheat("Intave", "intave", "Intave", new String[]{
        "de.jpx3.intave.api.event.IntaveFlagEvent",
        "de.jpx3.intave.api.event.AsyncFlagEvent",
        "de.jpx3.intave.api.event.FlagEvent"},
        new String[]{"getCheck", "getCheckName", "getViolation", "getMessage"});
    hookAntiCheat("Polar", "polar", "Polar", new String[]{
        "top.polar.api.event.anticheat.FlagEvent",
        "top.polar.api.event.anticheat.PlayerFlagEvent"},
        new String[]{"getCheck", "getCheckName", "getViolation", "getMessage"});
    hookAntiCheat("Karhu", "karhu", "Karhu", new String[]{
        "cc.funkemunky.api.events.impl.FlagEvent",
        "cc.funkemunky.api.events.impl.PlayerFlagEvent"},
        new String[]{"getCheck", "getCheckName", "getViolation", "getMessage"});
    hookAntiCheat("Kauri", "kauri", "Kauri", new String[]{
        "me.kauri.api.event.FlagEvent",
        "me.kauri.api.events.FlagEvent"},
        DEFAULT_REASON_METHODS);
    hookAntiCheat("Reflex", "reflex", "Reflex", new String[]{
        "me.reflex.api.event.FlagEvent"},
        DEFAULT_REASON_METHODS);
    hookAntiCheat("Horizon", "horizon", "Horizon", new String[]{
        "dev.horizon.api.event.FlagEvent"},
        DEFAULT_REASON_METHODS);
    hookAntiCheat("Themis", "themis", "Themis", new String[]{
        "me.themis.api.event.FlagEvent"},
        DEFAULT_REASON_METHODS);
    hookAntiCheat("Medusa", "medusa", "Medusa", new String[]{
        "me.medusa.api.event.FlagEvent"},
        DEFAULT_REASON_METHODS);
    hookAntiCheat("Artemis", "artemis", "Artemis", new String[]{
        "me.artemis.api.event.FlagEvent"},
        DEFAULT_REASON_METHODS);
    hookAntiCheat("Dusk", "dusk", "Dusk", new String[]{
        "me.dusk.api.event.FlagEvent"},
        DEFAULT_REASON_METHODS);
    hookAntiCheat("Echo", "echo", "Echo", new String[]{
        "me.echo.api.event.FlagEvent"},
        DEFAULT_REASON_METHODS);
    hookAntiCheat("Errata", "errata", "Errata", new String[]{
        "me.errata.api.event.FlagEvent"},
        DEFAULT_REASON_METHODS);
  }

  private void hookCustomAntiCheats() {
    var section = plugin.getConfig().getConfigurationSection("acsus.custom-anticheats");
    if (section == null) {
      return;
    }
    for (String key : section.getKeys(false)) {
      var entry = section.getConfigurationSection(key);
      if (entry == null) {
        continue;
      }
      String eventClassName = entry.getString("event-class");
      if (eventClassName == null || eventClassName.isBlank()) {
        continue;
      }
      Class<?> eventClass = findClass(eventClassName);
      if (eventClass == null) {
        plugin.getLogger().warning("[acsus] custom anti-cheat " + key
            + " event class not found: " + eventClassName);
        continue;
      }
      List<String> configuredMethods = entry.getStringList("reason-methods");
      String[] methods = configuredMethods.isEmpty()
          ? DEFAULT_REASON_METHODS : configuredMethods.toArray(new String[0]);
      String category = entry.getString("category", key)
          .toLowerCase(Locale.ROOT);
      String displayName = entry.getString("name", key);
      if (registerEventHook(plugin, key, displayName, category, eventClass, methods)) {
        plugin.getLogger().info("[acsus] hooked custom anti-cheat " + displayName);
      }
    }
  }

  private static Class<?> findClass(String name) {
    try {
      return Class.forName(name, false, BehaviorTracker.class.getClassLoader());
    } catch (Throwable ignored) {
    }
    for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
      try {
        return Class.forName(name, false, candidate.getClass().getClassLoader());
      } catch (Throwable ignored) {
      }
    }
    return null;
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
      hookedPlugins.add(pluginName.toLowerCase(Locale.ROOT));
      hookedEventClasses.add(expected.getName());
    } catch (Throwable e) {
      plugin.getLogger().warning("[acsus] failed to hook " + pluginName + ": " + e);
    }
  }

  private void discoverAntiCheatEvents() {
    for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
      if (candidate == plugin) {
        continue;
      }
      if (hookedPlugins.contains(candidate.getName().toLowerCase(Locale.ROOT))) {
        continue;
      }
      try {
        scanPlugin(candidate);
      } catch (Throwable error) {
        plugin.getLogger().fine("[acsus] discovery skipped " + candidate.getName()
            + ": " + error.getMessage());
      }
    }
  }

  private void scanPlugin(Plugin candidate) throws Exception {
    var source = candidate.getClass().getProtectionDomain().getCodeSource();
    if (source == null) {
      return;
    }
    File file = new File(source.getLocation().toURI());
    if (file.isFile() && file.getName().endsWith(".jar")) {
      try (JarFile jar = new JarFile(file)) {
        var entries = jar.entries();
        int scanned = 0;
        while (entries.hasMoreElements() && scanned < 400) {
          JarEntry entry = entries.nextElement();
          String className = candidateClass(entry.getName());
          if (className != null) {
            scanned++;
            tryDiscoveredEvent(candidate, className);
          }
        }
      }
    } else if (file.isDirectory()) {
      scanClassDirectory(candidate, file, file, 0);
    }
  }

  private void scanClassDirectory(Plugin candidate, File root, File current, int depth) {
    if (depth > 8) {
      return;
    }
    File[] files = current.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        scanClassDirectory(candidate, root, file, depth + 1);
        continue;
      }
      String relative = root.toURI().relativize(file.toURI()).getPath();
      String className = candidateClass(relative);
      if (className != null) {
        tryDiscoveredEvent(candidate, className);
      }
    }
  }

  private static String candidateClass(String entryName) {
    if (!entryName.endsWith(".class")) {
      return null;
    }
    String lower = entryName.toLowerCase(Locale.ROOT);
    if (!lower.contains("flag") && !lower.contains("violation")
        && !lower.contains("alert") && !lower.contains("detection")
        && !lower.contains("punish") && !lower.contains("check")) {
      return null;
    }
    return entryName.substring(0, entryName.length() - 6).replace('/', '.');
  }

  private void tryDiscoveredEvent(Plugin candidate, String className) {
    if (hookedPlugins.contains(candidate.getName().toLowerCase(Locale.ROOT))) {
      return;
    }
    Class<?> eventClass = findClass(className);
    if (eventClass == null) {
      return;
    }
    try {
      eventClass.getMethod("getHandlerList");
      eventClass.getMethod("getPlayer");
    } catch (Throwable notAnEvent) {
      return;
    }
    String category = candidate.getName().toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]", "");
    registerEventHook(candidate, candidate.getName(), candidate.getName(),
        category, eventClass, DEFAULT_REASON_METHODS);
  }


  private boolean registerEventHook(Plugin owner, String pluginName,
      String displayName, String category, Class<?> eventClass, String[] reasonMethods) {
    try {
      if (!org.bukkit.event.Event.class.isAssignableFrom(eventClass)) {
        return false;
      }
      if (!hookedEventClasses.add(eventClass.getName())) {
        return true;
      }
      Object handlers = eventClass.getMethod("getHandlerList").invoke(null);
      Listener listener = new Listener() { };
      EventExecutor executor = (l, event) ->
          handleFlag(event, eventClass, category, displayName, reasonMethods);
      handlers.getClass().getMethod("register", RegisteredListener.class).invoke(
          handlers,
          new RegisteredListener(listener, executor, EventPriority.MONITOR, owner, false));
      hookedPlugins.add(pluginName.toLowerCase(Locale.ROOT));
      plugin.getLogger().info("[acsus] hooked " + displayName + " flags -> /acsus ("
          + eventClass.getName() + ")");
      return true;
    } catch (Throwable error) {
      return false;
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
        if (value instanceof String text) {
          part = text;
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
    Block block = event.getBlock();

    state.mines.add(now);
    while (!state.mines.isEmpty() && now - state.mines.peekFirst() > MINE_WINDOW_MS) {
      state.mines.pollFirst();
    }
    if (state.mines.size() >= MINE_BURST && cooldownReady(state.mineAt, now)) {
      state.mineAt = now;
      flagAndBroadcast(player,
          "Sustained mining: " + state.mines.size() + " blocks in "
              + (MINE_WINDOW_MS / 1000) + "s (possible base-finding)", "mining");
    }
    int floorY = plugin.getConfig().getInt("hider.hide-below-y", 0);
    int blockY = block.getY();
    if (blockY <= floorY && cooldownReady(state.baseFindAt, now)) {
      state.baseFindAt = now;
      flagAndBroadcast(player,
          "Base-finding: broke a block below the hide line (y=" + blockY + ")", "basefinding");
    }
    trackDig(state, player, block, now);
    Material mat = block.getType();
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
    if (q.size() >= REPEAT_LIMIT && cooldownReady(state.macroAt, now, last)) {
      state.macroAt.put(key, now);
      flagAndBroadcast(player, msg + " x" + q.size(), "macro");
    }
    Long prev = state.lastActionAt.get(key);
    int streak = (prev != null && now - prev <= FAST_GAP_MS)
        ? state.fastStreak.getOrDefault(key, 0) + 1 : 0;
    state.fastStreak.put(key, streak);
    state.lastActionAt.put(key, now);
    if (streak >= FAST_GAP_MIN && cooldownReady(state.macroAt, now, last)) {
      state.macroAt.put(key, now);
      flagAndBroadcast(player, msg + " (sub-" + FAST_GAP_MS + "ms x" + streak + ")", "macro");
    }
  }

  private void trackDig(State state, Player player, Block block, long now) {
    int blockX = block.getX();
    int blockY = block.getY();
    int blockZ = block.getZ();
    if (state.digX == blockX && state.digZ == blockZ && blockY == state.digY - 1
        && now - state.digAt <= DIG_TIME_WINDOW_MS) {
      state.digStreak++;
    } else {
      state.digStreak = 1;
    }
    state.digX = blockX;
    state.digY = blockY;
    state.digZ = blockZ;
    state.digAt = now;
    if (state.digStreak >= MIN_DIG_STREAK && cooldownReady(state.baseFindAt, now)) {
      state.baseFindAt = now;
      flagAndBroadcast(player,
          "Base-finding: straight-down dig (" + state.digStreak + " blocks)", "basefinding");
    }
  }

  private void flagAndBroadcast(Player player, String reason, String category) {
    plugin.susFlagManager().flag(player, reason, category);
    broadcastFlag(player, "Sus", reason);
  }

  private static boolean cooldownReady(long last, long now) {
    return last == 0 || now - last >= RE_COOLDOWN_MS;
  }

  private static boolean cooldownReady(Map<String, Long> at, long now, Long last) {
    return last == null || now - last >= RE_COOLDOWN_MS;
  }

  private static class State {
    long elytraMs;
    long elytraAt;
    long mineAt;
    long baseFindAt;
    int digStreak;
    int digX;
    int digY;
    int digZ;
    long digAt;
    final Deque<Long> mines = new ArrayDeque<>();
    final Map<String, Deque<Long>> windows = new HashMap<>();
    final Map<String, Long> macroAt = new HashMap<>();
    final Map<String, Long> lastActionAt = new HashMap<>();
    final Map<String, Integer> fastStreak = new HashMap<>();
  }
}
