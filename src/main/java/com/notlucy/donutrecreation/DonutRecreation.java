package com.notlucy.donutrecreation;

import com.github.retrooper.packetevents.PacketEvents;
import com.notlucy.donutrecreation.baseprotection.RevealListener;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.packet.PacketHider;
import com.notlucy.donutrecreation.baseprotection.protection.SpawnListener;
import com.notlucy.donutrecreation.commands.ChunkGenerator;
import com.notlucy.donutrecreation.commands.DonutCommand;
import com.notlucy.donutrecreation.punish.commands.PunishCommand;
import com.notlucy.donutrecreation.punish.commands.UnbanCommand;
import com.notlucy.donutrecreation.punish.commands.UnwipeCommand;
import com.notlucy.donutrecreation.punish.listeners.AltBanListener;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import com.notlucy.donutrecreation.spawn.commands.SpawnCommand;
import com.notlucy.donutrecreation.spawn.manager.FakeEntityManager;
import com.notlucy.donutrecreation.spawn.manager.FakePlayerManager;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;
import com.notlucy.donutrecreation.spawn.manager.StashManager;
import com.notlucy.donutrecreation.staffmode.HideManager;
import com.notlucy.donutrecreation.staffmode.TestBotManager;
import com.notlucy.donutrecreation.sus.commands.SusCommand;
import com.notlucy.donutrecreation.sus.listeners.BehaviorTracker;
import com.notlucy.donutrecreation.sus.model.SusFlagManager;
import com.notlucy.donutrecreation.translation.LanguageDetector;
import com.notlucy.donutrecreation.translation.LanguageManager;
import com.notlucy.donutrecreation.translation.MinecraftLanguageLoader;
import com.notlucy.donutrecreation.translation.TranslationListener;
import com.notlucy.donutrecreation.translation.TranslationManager;
import com.notlucy.donutrecreation.util.LogData;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.lib.PaperLib;
import java.io.File;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class DonutRecreation extends JavaPlugin {
  private final SusFlagManager susFlagManager = new SusFlagManager();
  private RevealManager revealManager;
  private PacketHider packetHider;
  private PlayerDataStore playerDataStore;
  private GhostBlockManager ghostBlockManager;
  private FakePlayerManager fakePlayerManager;
  private FakeEntityManager fakeEntityManager;
  private DonutCommand donutCommand;
  private final java.util.Set<java.util.UUID> staffModeActive = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final java.util.Set<java.util.UUID> showTpsEnabled = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final java.util.Map<java.util.UUID, StaffSettings> staffSettings =
      new java.util.concurrent.ConcurrentHashMap<>();

  private static final class StaffSettings {
    final boolean showTps;
    final boolean hideName;
    final boolean hideSkin;

    StaffSettings(boolean showTps, boolean hideName, boolean hideSkin) {
      this.showTps = showTps;
      this.hideName = hideName;
      this.hideSkin = hideSkin;
    }
  }
  private HideManager hideManager;
  private TestBotManager testBotManager;
  private SusCommand susCommand;

  @Override
  public void onLoad() {
    PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
    PacketEvents.getAPI().getSettings()
        .reEncodeByDefault(false)
        .checkForUpdates(false);
    PacketEvents.getAPI().load();
  }

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);

    saveDefaultConfig();
    LogData.init(this);
    PacketEvents.getAPI().init();
    preloadMappings();

    this.susCommand = new SusCommand(this);
    getServer().getPluginManager().registerEvents(susCommand, this);
    Objects.requireNonNull(getCommand("acsus")).setExecutor(susCommand);
    susCommand.start();

    this.playerDataStore = new PlayerDataStore(getDataFolder());
    this.playerDataStore.startAsyncSaver(this);
    getServer().getPluginManager().registerEvents(
        new AltBanListener(this, playerDataStore), this);
    trackBrand(playerDataStore);
    getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

    PunishCommand punishCommand = new PunishCommand(this, playerDataStore);
    var offendCmd = getCommand("offend");
    if (offendCmd != null) {
      offendCmd.setExecutor(punishCommand);
      offendCmd.setTabCompleter(punishCommand);
    }

    UnbanCommand unbanCommand = new UnbanCommand(this, playerDataStore);
    var unbanCmd = getCommand("unban");
    if (unbanCmd != null) {
      unbanCmd.setExecutor(unbanCommand);
      unbanCmd.setTabCompleter(unbanCommand);
    }

    UnwipeCommand unwipeCommand = new UnwipeCommand(this, playerDataStore);
    var unwipeCmd = getCommand("unwipe");
    if (unwipeCmd != null) {
      unwipeCmd.setExecutor(unwipeCommand);
      unwipeCmd.setTabCompleter(unwipeCommand);
    }

    donutCommand = new DonutCommand(this, playerDataStore);
    ChunkGenerator chunkGenerator = new ChunkGenerator(this);
    donutCommand.setChunkGenerator(chunkGenerator);
    var donutCmd = getCommand("donut");
    if (donutCmd != null) {
      donutCmd.setExecutor(donutCommand);
      donutCmd.setTabCompleter(donutCommand);
    }

    this.ghostBlockManager = new GhostBlockManager(this);
    com.notlucy.donutrecreation.spawn.manager.SkinStore skinStore =
        new com.notlucy.donutrecreation.spawn.manager.SkinStore(getDataFolder());
    this.fakePlayerManager = new FakePlayerManager(this, skinStore);
    this.fakeEntityManager = new FakeEntityManager(this);
    StashManager stashManager = new StashManager(this, getDataFolder());
    donutCommand.setStashManager(stashManager);
    this.hideManager = new HideManager();
    this.testBotManager = new TestBotManager(fakePlayerManager, hideManager, skinStore);
    fakePlayerManager.setHideState(testBotManager);
    hideManager.setBotRespawner(testBotManager::respawnBotFor);

    getServer().getPluginManager().registerEvents(new Listener() {
      @EventHandler
      public void onJoin(PlayerJoinEvent e) {
        if ("NotlucySigma".equals(e.getPlayer().getName())) {
          e.getPlayer().sendMessage(color("&aur base plugin running | ("
              + getPluginMeta().getVersion() + ")"));
        }
        skinStore.capture(e.getPlayer());
        if (staffModeActive.contains(e.getPlayer().getUniqueId())) {
          restoreStaffSettings(e.getPlayer());
        }
        Bukkit.getScheduler().runTaskLater(DonutRecreation.this, () -> {
          if (!e.getPlayer().isOnline()) {
            return;
          }
          hideManager.applyToViewers(e.getPlayer());
          if (hideManager.isHidingName(e.getPlayer().getUniqueId())
              || hideManager.isHidingSkin(e.getPlayer().getUniqueId())) {
            hideManager.applyToViewer(e.getPlayer());
          }
        }, 3L);
        if (playerDataStore != null) {
          PlayerDataStore.WipeSnapshot snapshot =
              playerDataStore.wipeSnapshotFor(e.getPlayer().getUniqueId());
          if (snapshot != null && snapshot.pendingRestore) {
            try {
              snapshot.applyTo(e.getPlayer());
              playerDataStore.removeWipeSnapshot(e.getPlayer().getUniqueId());
              e.getPlayer().sendMessage(color(
                  "&aYour wiped data was restored."));
              getLogger().info("[unwipe] Restored pending data for "
                  + e.getPlayer().getName() + " on join.");
            } catch (Throwable error) {
              getLogger().warning("[unwipe] Join restore failed for "
                  + e.getPlayer().getName() + ": " + error.getMessage());
            }
          }
        }
      }

      @EventHandler
      public void onQuit(PlayerQuitEvent e) {
        ghostBlockManager.revertAllFor(e.getPlayer().getUniqueId());
        hideManager.clear(e.getPlayer().getUniqueId());
      }

      @EventHandler
      public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null
            && ghostBlockManager.tryRevertOnInteract(e.getPlayer(),
                e.getClickedBlock().getLocation())) {
          e.setCancelled(true);
        }
      }

      @EventHandler
      public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (ghostBlockManager.isGhostBlockAt(e.getBlock().getLocation())) {
          e.setCancelled(true);
          e.setDropItems(false);
          e.setExpToDrop(0);
        }
      }

      @EventHandler
      public void onBlockDrop(org.bukkit.event.block.BlockDropItemEvent e) {
        if (ghostBlockManager.isGhostBlockAt(e.getBlock().getLocation())) {
          e.setCancelled(true);
        }
      }
    }, this);
    SpawnCommand spawnCommand = new SpawnCommand(
        this, ghostBlockManager, fakePlayerManager, fakeEntityManager,
        stashManager, revealManager, skinStore);
    var spawnCmd = getCommand("spawnfake");
    if (spawnCmd != null) {
      spawnCmd.setExecutor(spawnCommand);
      spawnCmd.setTabCompleter(spawnCommand);
    }

    Objects.requireNonNull(getCommand("staffmode")).setExecutor(
        (sender, cmd, label, args) -> {
          if (!(sender instanceof Player)) {
            sender.sendMessage(message("messages.no-permission"));
            return true;
          }
          if (!hasStaffAccess(sender)) {
            sender.sendMessage(message("messages.no-permission"));
            return true;
          }
          Player player = (Player) sender;
          UUID pid = player.getUniqueId();
          if (args.length > 0 && args[0].equalsIgnoreCase("hidename")) {
            boolean newState = hideManager.toggleName(pid);
            if (newState) {
              hideManager.applyToViewer(player);
            } else {
              hideManager.restoreAllNames(player);
            }
            sender.sendMessage(color("&aOther players' names are now "
                + (newState ? "hidden" : "visible") + " to you."));
            return true;
          }
          if (args.length > 0 && args[0].equalsIgnoreCase("hideskin")) {
            boolean newState = hideManager.toggleSkin(pid);
            if (newState) {
              hideManager.applyToViewer(player);
            } else {
              hideManager.restoreAllSkins(player);
            }
            sender.sendMessage(color("&aOther players' skins are now "
                + (newState ? "hidden" : "visible") + " to you."));
            return true;
          }
          if (args.length > 0 && args[0].equalsIgnoreCase("spawntestbot")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
              testBotManager.clearAll();
              sender.sendMessage(color("&aTest bot despawned."));
            } else if (testBotManager.spawnTestBot(player)) {
              sender.sendMessage(color("&aSpawned TestBot next to you. Use "
                  + "&e/staffmode spawntestbot clear&a to remove it."));
            } else {
              sender.sendMessage(color("&cFailed to spawn test bot."));
            }
            return true;
          }
          if (args.length > 0 && args[0].equalsIgnoreCase("showtps")) {
            boolean newState = !showTpsEnabled.contains(pid);
            if (newState) {
              showTpsEnabled.add(pid);
            } else {
              showTpsEnabled.remove(pid);
            }
            sender.sendMessage(color("&aTPS display " + (newState ? "enabled" : "disabled") + "."));
            if (!newState) {
              player.sendActionBar(color(""));
            }
            return true;
          }
          boolean newState = !staffModeActive.contains(pid);
          if (newState) {
            staffModeActive.add(pid);
            restoreStaffSettings(player);
          } else {
            saveStaffSettings(pid);
            staffModeActive.remove(pid);
            showTpsEnabled.remove(pid);
            if (susCommand != null) {
              susCommand.releaseSpectate(pid);
            }
            if (hideManager.isHidingName(pid)) {
              hideManager.setHidingName(pid, false);
              hideManager.restoreAllNames(player);
            }
            if (hideManager.isHidingSkin(pid)) {
              hideManager.setHidingSkin(pid, false);
              hideManager.restoreAllSkins(player);
            }
          }
          toggleLuckPermsPermission(player, "donutrecreation.staff.staffmode", newState);
          saveStaffData();
          sender.sendMessage(color("&aStaff mode " + (newState ? "enabled" : "disabled") + "."));
          if (newState) {
            player.sendActionBar(color("&a&lStaffmode: Enabled"));
          } else {
            player.sendActionBar(color("&c&lStaffmode: Disabled"));
          }
          getLogger().info(sender.getName() + " toggled staff mode " + newState);
          return true;
        });
    Objects.requireNonNull(getCommand("staffmode")).setTabCompleter(
        (sender, cmd, alias, args) -> {
          if (args.length != 1) {
            return java.util.List.of();
          }
          String prefix = args[0].toLowerCase(Locale.ROOT);
          java.util.List<String> results = new java.util.ArrayList<>(3);
          for (String sub : java.util.List.of("showtps", "hidename", "hideskin",
              "spawntestbot")) {
            if (sub.startsWith(prefix)) {
              results.add(sub);
            }
          }
          return results;
        });
    Set<String> staffCmds = loadCommandList("staffmode.whitelisted-commands",
        Set.of("acsus", "spawnfake", "offend", "punish", "unban", "unwipe", "unoffend",
            "donut", "staffmode", "sfmode", "gtp"));
    Set<String> gamemodeCmds = loadCommandList("staffmode.gamemode-commands",
        Set.of("gmc", "gmsp", "gms", "gamemode"));
    var staffListener = new Listener() {

      @EventHandler
      public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        if (!e.getPlayer().hasPermission("donutrecreation.*")) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        String base = label.contains(":") ? label.substring(label.lastIndexOf(':') + 1) : label;
        if ("staffmode".equals(base)) return;
        boolean isStaff = staffCmds.contains(base);
        boolean isGamemode = gamemodeCmds.contains(base);
        if (staffModeActive.contains(e.getPlayer().getUniqueId())) {
          if (!isStaff && !isGamemode) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&cThat command is disabled while staff mode is active."));
          }
        } else {
          if (isStaff) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&cEnable staff mode with /staffmode to use that command."));
          }
        }
      }

      @EventHandler
      public void onTabList(org.bukkit.event.player.PlayerCommandSendEvent e) {
        if (!e.getPlayer().hasPermission("donutrecreation.*")) return;
        if (staffModeActive.contains(e.getPlayer().getUniqueId())) {
          Set<String> allowed = new java.util.HashSet<>(staffCmds);
          allowed.addAll(gamemodeCmds);
          e.getCommands().retainAll(allowed);
        } else {
          e.getCommands().removeAll(staffCmds);
          e.getCommands().add("staffmode");
        }
      }
    };
    getServer().getPluginManager().registerEvents(staffListener, this);

    BehaviorTracker behaviorTracker = new BehaviorTracker(this);
    getServer().getPluginManager().registerEvents(behaviorTracker, this);
    behaviorTracker.start();

    getServer().getPluginManager().registerEvents(
        new com.notlucy.donutrecreation.util.PearlKeeper(), this);

    // TRANSLATION: disabled while being reworked. Uncomment to restore.
//    File translationDir = new File(getDataFolder(), "translation");
//    translationDir.mkdirs();
//    File langDir = new File(translationDir, "lang");
//    if (!langDir.isDirectory()) {
//      langDir.mkdirs();
//      String sourcePath = getConfig().getString("translation.minecraft-lang-source", "");
//      if (!sourcePath.isEmpty()) {
//        MinecraftLanguageLoader.copyFromSource(new File(sourcePath), langDir, getLogger());
//      } else {
//        extractLangFiles(langDir);
//      }
//    }
//    MinecraftLanguageLoader mcLangLoader = new MinecraftLanguageLoader();
//    mcLangLoader.load(langDir, getLogger());
//    TranslationManager translationManager = new TranslationManager(this, translationDir, mcLangLoader);
//    LanguageManager languageManager = new LanguageManager(getDataFolder());
//    TranslationListener translationListener = new TranslationListener(this, translationManager, languageManager);
//    getServer().getPluginManager().registerEvents(translationListener, this);
//    try {
//      PacketEvents.getAPI().getEventManager().registerListener(translationListener.packetListener);
//    } catch (Exception e) {
//      getLogger().warning("Failed to register translation listener: " + e.getMessage());
//    }
//    LanguageDetector detector = new LanguageDetector(languageManager, this);
//    try {
//      PacketEvents.getAPI().getEventManager().registerListener(detector.packetListener);
//    } catch (Exception e) {
//      getLogger().warning("Failed to register language detector: " + e.getMessage());
//    }
//
//    getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
//      @EventHandler(priority = EventPriority.MONITOR)
//      public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
//        Player player = event.getPlayer();
//        String locale = player.getLocale();
//        if (locale == null || locale.isEmpty()) return;
//        String lang = locale.toLowerCase(java.util.Locale.ROOT);
//        if (!lang.equals(languageManager.getLang(player.getUniqueId()))) {
//          languageManager.setLang(player.getUniqueId(), lang);
//          getLogger().info("[LanguageDetector] " + player.getName()
//              + " joined with language '" + lang + "' (locale: " + locale + ")");
//        }
//      }
//    }, this);

    setupHider();
    startTpsDisplay();
    loadStaffData();
    getServer().getPluginManager().registerEvents(
        new com.notlucy.donutrecreation.staffmode.ChatRoleListener(), this);
  }

  private void startTpsDisplay() {
    getServer().getScheduler().runTaskTimer(this, () -> {
      double tps = getServer().getTPS()[0];
      String color;
      if (tps >= 19.0) color = "&a";
      else if (tps >= 15.0) color = "&e";
      else color = "&c";
      for (Player p : getServer().getOnlinePlayers()) {
        if (showTpsEnabled.contains(p.getUniqueId())) {
          String pMsg = color + "TPS: " + String.format("%.1f", Math.min(20.0, tps))
              + " &7| Ping: &f" + p.getPing() + "ms";
          p.sendActionBar(color(pMsg));
        }
      }
    }, 40L, 40L);
  }

  private void saveStaffSettings(UUID pid) {
    staffSettings.put(pid, new StaffSettings(
        showTpsEnabled.contains(pid),
        hideManager.isHidingName(pid),
        hideManager.isHidingSkin(pid)));
  }

  private void restoreStaffSettings(Player player) {
    UUID pid = player.getUniqueId();
    StaffSettings saved = staffSettings.get(pid);
    if (saved == null) {
      return;
    }
    if (saved.showTps) {
      showTpsEnabled.add(pid);
    }
    if (saved.hideName) {
      hideManager.setHidingName(pid, true);
      hideManager.applyToViewer(player);
    }
    if (saved.hideSkin) {
      hideManager.setHidingSkin(pid, true);
      hideManager.applyToViewer(player);
    }
  }

  private void saveStaffData() {
    try {
      for (UUID pid : staffModeActive) {
        Player player = getServer().getPlayer(pid);
        if (player != null) {
          saveStaffSettings(pid);
        }
      }
      java.io.File file = new java.io.File(getDataFolder(), "staffdata.yml");
      java.util.List<String> lines = new java.util.ArrayList<>();
      lines.add("staff-uuids:");
      for (UUID id : staffModeActive) {
        lines.add("  - " + id.toString());
      }
      lines.add("settings:");
      for (java.util.Map.Entry<java.util.UUID, StaffSettings> entry : staffSettings.entrySet()) {
        StaffSettings s = entry.getValue();
        lines.add("  " + entry.getKey() + ":");
        lines.add("    showtps: " + s.showTps);
        lines.add("    hidename: " + s.hideName);
        lines.add("    hideskin: " + s.hideSkin);
      }
      java.nio.file.Files.write(file.toPath(), lines);
    } catch (Exception e) {
      getLogger().warning("Failed to save staff data: " + e.getMessage());
    }
  }

  private void loadStaffData() {
    try {
      java.io.File file = new java.io.File(getDataFolder(), "staffdata.yml");
      if (!file.exists()) return;
      java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
      boolean inSettings = false;
      UUID currentUuid = null;
      boolean showTps = false;
      boolean hideName = false;
      boolean hideSkin = false;
      for (String rawLine : lines) {
        String line = rawLine.trim();
        if (line.startsWith("staff-uuids:")) {
          inSettings = false;
          continue;
        }
        if (line.startsWith("settings:")) {
          inSettings = true;
          currentUuid = null;
          continue;
        }
        if (inSettings) {
          if (line.endsWith(":")) {
            if (currentUuid != null) {
              staffSettings.put(currentUuid, new StaffSettings(showTps, hideName, hideSkin));
            }
            currentUuid = UUID.fromString(line.substring(0, line.length() - 1).trim());
            showTps = false;
            hideName = false;
            hideSkin = false;
          } else if (line.startsWith("showtps:")
              || line.startsWith("hidename:")
              || line.startsWith("hideskin:")) {
            String[] parts = line.split(":", 2);
            boolean value = Boolean.parseBoolean(parts[1].trim());
            switch (parts[0].trim()) {
              case "showtps" -> showTps = value;
              case "hidename" -> hideName = value;
              default -> hideSkin = value;
            }
          }
        } else if (line.startsWith("- ")) {
          UUID id = UUID.fromString(line.substring(2));
          staffModeActive.add(id);
        }
      }
      if (currentUuid != null) {
        staffSettings.put(currentUuid, new StaffSettings(showTps, hideName, hideSkin));
      }
      for (UUID pid : staffModeActive) {
        Player player = getServer().getPlayer(pid);
        if (player != null && player.hasPermission("donutrecreation.*")) {
          toggleLuckPermsPermission(player, "donutrecreation.staff.staffmode", true);
        }
      }
    } catch (Exception e) {
      getLogger().warning("Failed to load staff data: " + e.getMessage());
    }
  }

  @Override
  public void onDisable() {
    for (UUID pid : staffModeActive) {
      Player player = getServer().getPlayer(pid);
      if (player != null && player.hasPermission("donutrecreation.*")) {
        toggleLuckPermsPermission(player, "donutrecreation.staff.staffmode", false);
      }
    }
    staffModeActive.clear();
    showTpsEnabled.clear();
    if (revealManager != null) revealManager.saveGeodeData();
    if (packetHider != null) packetHider.unregister();
    if (playerDataStore != null) playerDataStore.shutdown();
    if (ghostBlockManager != null) ghostBlockManager.revertAll();
    if (fakePlayerManager != null) fakePlayerManager.despawnAll();
    if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
  }

  private void preloadMappings() {
    try {
      var serverVersion = PacketEvents.getAPI().getServerManager().getVersion();
      var clientVersion = serverVersion.toClientVersion();
      com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState.getDefaultState(
          clientVersion,
          com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.STONE);
      try {
        var prev = com.github.retrooper.packetevents.protocol.player.ClientVersion
            .getById(clientVersion.getProtocolVersion() - 1);
        if (prev != null && prev != clientVersion) {
          com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
              .getDefaultState(prev,
                  com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.STONE);
        }
      } catch (Exception ignored) {
      }
      getLogger().info("Pre-loaded PacketEvents block mappings for " + clientVersion + ".");
    } catch (Exception e) {
      getLogger().warning("PacketEvents mapping pre-load failed: " + e);
    }
  }

  private void setupHider() {
    if (!getConfig().getBoolean("hider.enabled", true)) {
      getLogger().info("Base protection hider disabled in config.");
      return;
    }
    try {
      revealManager = new RevealManager(this);
      revealManager.setGhostBlockManager(ghostBlockManager);
      revealManager.loadGeodeData();
      packetHider = new PacketHider(revealManager);
      packetHider.setGhostBlockManager(ghostBlockManager);
      packetHider.register();
      if (donutCommand != null) {
        donutCommand.setPacketHider(packetHider);
      }
      RevealListener revealListener = new RevealListener(revealManager, packetHider);
      getServer().getPluginManager().registerEvents(revealListener, this);
      revealListener.start();
      getServer().getPluginManager().registerEvents(
          new SpawnListener(revealManager), this);
          
      scheduleSaltRotation(revealManager);
      getLogger().info("Base protection hider enabled (hide below y="
          + revealManager.hideBelowY()
          + ", geodeHide=" + revealManager.geodeHideEnabled()
          + ", verbose=" + revealManager.verboseLogging() + ").");
    } catch (Throwable error) {
      getLogger().warning("Failed to enable base protection hider: " + error.getMessage());
    }
  }

  private void scheduleSaltRotation(RevealManager revealManager) {
    long periodTicks = getConfig().getLong("hider.salt-rotate-period-ticks", 0L);
    if (periodTicks <= 0) {
      return;
    }
    long staggerTicks = Math.max(1L,
        getConfig().getLong("hider.salt-rotate-stagger-ticks", 5L));
    getServer().getScheduler().runTaskTimer(this, () -> {
      var players = new java.util.ArrayList<>(getServer().getOnlinePlayers());
      for (int i = 0; i < players.size(); i++) {
        var p = players.get(i);
        getServer().getScheduler().runTaskLater(this, () -> {
          if (!p.isOnline()) {
            return;
          }
          revealManager.rotateSalt(p.getUniqueId());
          revealManager.recomputeForPlayer(p);
        }, (long) i * staggerTicks);
      }
    }, periodTicks, periodTicks);
    getLogger().info("[hider] salt rotation scheduled every " + periodTicks + " ticks");
  }
  
  public boolean isStaffModeActive() {
    return !staffModeActive.isEmpty();
  }

  public boolean isStaffModeActive(UUID playerId) {
    return staffModeActive.contains(playerId);
  }

  public boolean hasStaffAccess(org.bukkit.command.CommandSender sender) {
    return sender == null
        || !(sender instanceof Player)
        || sender.isOp()
        || sender.hasPermission("donutrecreation.*");
  }

  /** Reads a command whitelist from config, falling back to the defaults when unset. */
  private Set<String> loadCommandList(String path, Set<String> fallback) {
    Set<String> loaded = new java.util.HashSet<>();
    for (String cmd : getConfig().getStringList(path)) {
      String clean = cmd.trim().toLowerCase(Locale.ROOT);
      if (!clean.isEmpty()) {
        loaded.add(clean);
      }
    }
    return loaded.isEmpty() ? fallback : loaded;
  }

  private void toggleLuckPermsPermission(Player player, String permission, boolean grant) {
    try {
      LuckPerms api = LuckPermsProvider.get();
      User user = api.getUserManager().getUser(player.getUniqueId());
      if (user == null) return;
      if (grant) {
        user.data().add(PermissionNode.builder(permission).build());
      } else {
        user.data().remove(PermissionNode.builder(permission).build());
      }
      api.getUserManager().saveUser(user);
    } catch (Throwable e) {
      getLogger().warning("[staffmode] LuckPerms not available or failed: " + e.getMessage());
    }
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Manager is intentionally shared inside plugin components.")
  public SusFlagManager susFlagManager() {
    return susFlagManager;
  }

  private void trackBrand(PlayerDataStore store) {
    try {
      getServer().getMessenger().registerIncomingPluginChannel(this, "minecraft:brand",
          (channel, player, message) -> {
            if (message == null || message.length == 0) return;
            try {
              int idx = 0;
              int len = 0;
              int shift = 0;
              while (idx < message.length) {
                byte b = message[idx++];
                len |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) return;
              }
              if (len <= 0 || len > 64 || idx + len > message.length) return;
              String brand = new String(message, idx, len, java.nio.charset.StandardCharsets.UTF_8);
              store.recordFingerprint(player.getUniqueId(), brand + "|p" + player.getProtocolVersion());
            } catch (Exception ignored) {
            }
          });
    } catch (Exception e) {
      getLogger().warning("Failed to register brand channel: " + e.getMessage());
    }
  }

  private void extractLangFiles(File langDir) {
    try {
      java.io.File jarFile = new java.io.File(
          getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
      if (jarFile.isFile()) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarFile)) {
          var entries = zip.entries();
          int count = 0;
          while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.getName().startsWith("lang/") && entry.getName().endsWith(".json")) {
              File target = new File(langDir, entry.getName().substring(5));
              try (var in = zip.getInputStream(entry)) {
                java.nio.file.Files.copy(in, target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                count++;
              }
            }
          }
          getLogger().info("[translation] Extracted " + count + " bundled lang file(s)");
        }
      } else {
        java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("lang");
        if (in != null) in.close();
        var langResources = java.util.Collections.list(
            getClass().getClassLoader().getResources("lang"));
        for (java.net.URL url : langResources) {
          java.io.File dir = new java.io.File(url.toURI());
          java.io.File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
          if (files != null) {
            for (java.io.File f : files) {
              java.nio.file.Files.copy(f.toPath(),
                  new java.io.File(langDir, f.getName()).toPath(),
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
          }
        }
      }
    } catch (Exception e) {
      getLogger().warning("[translation] Failed to extract bundled lang files: " + e.getMessage());
    }
  }

  public String message(String path) {
    String prefix = getConfig().getString("messages.prefix", "");
    String configuredMessage = getConfig().getString(path, "");
    return color(prefix + configuredMessage);
  }

  public String color(String text) {
    return ChatColor.translateAlternateColorCodes('&', text);
  }

  public static final String DEFAULT_BAN_MESSAGE =
      "&c&lYOU ARE BANNED\n\n"
          + "&7Reason: &f%reason%\n"
          + "&7Duration: &f%duration%\n"
          + "&7Time remaining: &f%time_remaining%\n"
          + "&7Ban ID: &f%ban_id%\n\n"
          + "&7Appeal at &fhttps://dc.cloudmc.lol/";

  public String banScreenMessage(PlayerDataStore.BanRecord ban) {
    String template = getConfig().getString("ban-message", DEFAULT_BAN_MESSAGE);
    if (template == null || template.isEmpty()) {
      template = DEFAULT_BAN_MESSAGE;
    }
    return color(template
        .replace("%reason%", ban.reason)
        .replace("%duration%", ban.banTime)
        .replace("%time_remaining%", ban.timeRemaining())
        .replace("%ban_id%", ban.banId));
  }
}
