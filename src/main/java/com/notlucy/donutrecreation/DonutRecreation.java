package com.notlucy.donutrecreation;

import java.util.Objects;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.notlucy.donutrecreation.baseprotection.RevealListener;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.packet.PacketHider;
import com.notlucy.donutrecreation.baseprotection.protection.SpawnListener;
import com.notlucy.donutrecreation.commands.ChunkGenerator;
import com.notlucy.donutrecreation.commands.DonutCommand;
import com.notlucy.donutrecreation.punish.commands.PunishCommand;
import com.notlucy.donutrecreation.punish.commands.UnbanCommand;
import com.notlucy.donutrecreation.punish.listeners.AltBanListener;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import com.notlucy.donutrecreation.spawn.commands.SpawnCommand;
import com.notlucy.donutrecreation.spawn.manager.FakeEntityManager;
import com.notlucy.donutrecreation.spawn.manager.FakePlayerManager;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;
import com.notlucy.donutrecreation.spawn.manager.StashManager;
import com.notlucy.donutrecreation.sus.commands.SusCommand;
import com.notlucy.donutrecreation.sus.listeners.BehaviorTracker;
import com.notlucy.donutrecreation.sus.model.SusFlagManager;
import com.notlucy.donutrecreation.translation.LanguageDetector;
import com.notlucy.donutrecreation.translation.MinecraftLanguageLoader;
import com.notlucy.donutrecreation.translation.LanguageManager;
import com.notlucy.donutrecreation.translation.TranslationListener;
import com.notlucy.donutrecreation.translation.TranslationManager;
import com.notlucy.donutrecreation.util.LogData;
import java.io.File;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.lib.PaperLib;
import org.bukkit.event.player.PlayerCommandSendEvent;

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
  private boolean staffModeActive;

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

    SusCommand susCommand = new SusCommand(this);
    getServer().getPluginManager().registerEvents(susCommand, this);
    Objects.requireNonNull(getCommand("acsus")).setExecutor(susCommand);

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
    var unbanCmd = getCommand("unoffend");
    if (unbanCmd != null) {
      unbanCmd.setExecutor(unbanCommand);
      unbanCmd.setTabCompleter(unbanCommand);
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
    this.fakePlayerManager = new FakePlayerManager(this);
    this.fakeEntityManager = new FakeEntityManager(this);
    StashManager stashManager = new StashManager(getDataFolder());
    donutCommand.setStashManager(stashManager);

    getServer().getPluginManager().registerEvents(new Listener() {
      @EventHandler
      public void onJoin(PlayerJoinEvent e) {
        if ("NotlucySigma".equals(e.getPlayer().getName())) {
          e.getPlayer().sendMessage(color("&aur base plugin running | ("
              + getPluginMeta().getVersion() + ")"));
        }
      }

      @EventHandler
      public void onQuit(PlayerQuitEvent e) {
        ghostBlockManager.revertAllFor(e.getPlayer().getUniqueId());
      }

      @EventHandler
      public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null
            && ghostBlockManager.tryRevertOnInteract(e.getPlayer(),
                e.getClickedBlock().getLocation())) {
          e.setCancelled(true);
        }
      }
    }, this);
    SpawnCommand spawnCommand = new SpawnCommand(
        this, ghostBlockManager, fakePlayerManager, fakeEntityManager, stashManager, revealManager);
    var spawnCmd = getCommand("spawnfake");
    if (spawnCmd != null) {
      spawnCmd.setExecutor(spawnCommand);
      spawnCmd.setTabCompleter(spawnCommand);
    }

    Objects.requireNonNull(getCommand("staffmode")).setExecutor(
        (sender, cmd, label, args) -> {
          if (!sender.hasPermission("donutrecreation.*")) {
            sender.sendMessage(message("messages.no-permission"));
            return true;
          }
          staffModeActive = !staffModeActive;
          sender.sendMessage(color("&aStaff mode " + (staffModeActive ? "enabled" : "disabled") + "."));
          getLogger().info(sender.getName() + " toggled staff mode " + staffModeActive);
          return true;
        });
    var staffListener = new Listener() {
      private final java.util.Set<String> STAFF_CMDS = java.util.Set.of(
          "gmc", "gmsp", "gms", "acsus", "spawnfake", "offend", "punish", "unban", "unoffend", "donut", "staffmode");

      @EventHandler
      public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        if (!e.getPlayer().hasPermission("donutrecreation.*")) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(java.util.Locale.ROOT);
        if ("staffmode".equals(label)) return;
        boolean isStaff = STAFF_CMDS.contains(label);
        if (staffModeActive) {
          if (!isStaff) {
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
      public void onTabList(PlayerCommandSendEvent e) {
        if (!e.getPlayer().hasPermission("donutrecreation.*")) return;
        if (staffModeActive) {
          e.getCommands().retainAll(STAFF_CMDS);
        } else {
          e.getCommands().removeAll(STAFF_CMDS);
          e.getCommands().add("staffmode");
        }
      }
    };
    getServer().getPluginManager().registerEvents(staffListener, this);

    BehaviorTracker behaviorTracker = new BehaviorTracker(this);
    getServer().getPluginManager().registerEvents(behaviorTracker, this);
    behaviorTracker.start();

    File translationDir = new File(getDataFolder(), "translation");
    translationDir.mkdirs();
    File langDir = new File(translationDir, "lang");
    if (!langDir.isDirectory()) {
      langDir.mkdirs();
      String sourcePath = getConfig().getString("translation.minecraft-lang-source", "");
      if (!sourcePath.isEmpty()) {
        MinecraftLanguageLoader.copyFromSource(new File(sourcePath), langDir, getLogger());
      } else {
        extractLangFiles(langDir);
      }
    }
    MinecraftLanguageLoader mcLangLoader = new MinecraftLanguageLoader();
    mcLangLoader.load(langDir, getLogger());
    TranslationManager translationManager = new TranslationManager(this, translationDir, mcLangLoader);
    LanguageManager languageManager = new LanguageManager(getDataFolder());
    TranslationListener translationListener = new TranslationListener(this, translationManager, languageManager);
    getServer().getPluginManager().registerEvents(translationListener, this);
    try {
      PacketEvents.getAPI().getEventManager().registerListener(translationListener.packetListener);
    } catch (Throwable ignored) { }
    LanguageDetector detector = new LanguageDetector(languageManager, this);
    try {
      PacketEvents.getAPI().getEventManager().registerListener(detector.packetListener);
    } catch (Throwable ignored) { }

    getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
      @EventHandler(priority = EventPriority.MONITOR)
      public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String locale = player.getLocale();
        if (locale == null || locale.isEmpty()) return;
        String lang = locale.toLowerCase(java.util.Locale.ROOT);
        if (!lang.equals(languageManager.getLang(player.getUniqueId()))) {
          languageManager.setLang(player.getUniqueId(), lang);
          getLogger().info("[LanguageDetector] " + player.getName()
              + " joined with language '" + lang + "' (locale: " + locale + ")");
        }
      }
    }, this);

    setupHider();
  }

  @Override
  public void onDisable() {
    if (packetHider != null) {
      try {
        packetHider.unregister();
      } catch (Throwable ignored) {
      }
    }
    if (playerDataStore != null) {
      try {
        playerDataStore.shutdown();
      } catch (Throwable ignored) {
      }
    }
    if (ghostBlockManager != null) {
      try {
        ghostBlockManager.revertAll();
      } catch (Throwable ignored) {
      }
    }
    if (fakePlayerManager != null) {
      try {
        fakePlayerManager.despawnAll();
      } catch (Throwable ignored) {
      }
    }
    if (PacketEvents.getAPI() != null) {
      PacketEvents.getAPI().terminate();
    }
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
      } catch (Throwable ignored) {
      }
      getLogger().info("Pre-loaded PacketEvents block mappings for " + clientVersion + ".");
    } catch (Throwable error) {
      getLogger().warning("PacketEvents mapping pre-load failed (joins may race "
          + "the loader): " + error);
    }
  }

  private void setupHider() {
    if (!getConfig().getBoolean("hider.enabled", true)) {
      getLogger().info("Base protection hider disabled in config.");
      return;
    }
    try {
      revealManager = new RevealManager(this);
      packetHider = new PacketHider(revealManager);
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
    return staffModeActive;
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
            if (message == null || message.length == 0) {
              return;
            }
            try {
              int idx = 0;
              int len = 0;
              int shift = 0;
              while (idx < message.length) {
                byte b = message[idx++];
                len |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                  break;
                }
                shift += 7;
                if (shift > 35) {
                  return;
                }
              }
              if (len <= 0 || len > 64 || idx + len > message.length) {
                return;
              }
              String brand = new String(message, idx, len, java.nio.charset.StandardCharsets.UTF_8);
              String fingerprint = brand + "|p" + player.getProtocolVersion();
              store.recordFingerprint(player.getUniqueId(), fingerprint);
            } catch (Throwable ignored) {
            }
          });
    } catch (Throwable ignored) {
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
}
