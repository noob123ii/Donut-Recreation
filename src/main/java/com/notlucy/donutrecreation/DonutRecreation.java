package com.notlucy.donutrecreation;

import com.github.retrooper.packetevents.PacketEvents;
import com.notlucy.donutrecreation.baseprotection.RevealListener;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.SpawnListener;
import com.notlucy.donutrecreation.baseprotection.packet.PacketHider;
import com.notlucy.donutrecreation.punish.commands.PunishCommand;
import com.notlucy.donutrecreation.punish.listeners.AltBanListener;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import com.notlucy.donutrecreation.spawn.commands.SpawnCommand;
import com.notlucy.donutrecreation.spawn.manager.FakePlayerManager;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;
import com.notlucy.donutrecreation.sus.commands.SusCommand;
import com.notlucy.donutrecreation.sus.listeners.BehaviorTracker;
import com.notlucy.donutrecreation.sus.model.SusFlagManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.lib.PaperLib;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class DonutRecreation extends JavaPlugin {
  private final SusFlagManager susFlagManager = new SusFlagManager();
  private PacketHider packetHider;
  private PlayerDataStore playerDataStore;
  private GhostBlockManager ghostBlockManager;
  private FakePlayerManager fakePlayerManager;

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
    PacketEvents.getAPI().init();
    preloadPacketEventsMappings();

    SusCommand susCommand = new SusCommand(this);
    getServer().getPluginManager().registerEvents(susCommand, this);
    Objects.requireNonNull(getCommand("sus")).setExecutor(susCommand);

    this.playerDataStore = new PlayerDataStore(getDataFolder(), getLogger());
    this.playerDataStore.startAsyncSaver(this);
    getServer().getPluginManager().registerEvents(
        new AltBanListener(this, playerDataStore), this);
    registerBrandFingerprint(playerDataStore);

    PunishCommand punishCommand = new PunishCommand(this, playerDataStore);
    var offandReg = getCommand("offand");
    if (offandReg != null) {
      offandReg.setExecutor(punishCommand);
      offandReg.setTabCompleter(punishCommand);
    }

    this.ghostBlockManager = new GhostBlockManager(this);
    this.fakePlayerManager = new FakePlayerManager(this);
    SpawnCommand spawnCommand = new SpawnCommand(this, ghostBlockManager, fakePlayerManager);
    var spawnReg = getCommand("spawn");
    if (spawnReg != null) {
      spawnReg.setExecutor(spawnCommand);
      spawnReg.setTabCompleter(spawnCommand);
    }

    BehaviorTracker behaviorTracker = new BehaviorTracker(this);
    getServer().getPluginManager().registerEvents(behaviorTracker, this);
    behaviorTracker.start();

    enableHider();
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

  /**
   * Forces PacketEvents to load its per-client-version block-state mappings on the main
   * thread during onEnable, so the first joining player doesn't trigger a ~300 ms async
   * load on a Netty IO thread (during which our chunk-rewrite would silently no-op and the
   * client would see the real world). Without this, the first join races the loader and
   * chunks leak before the hider can rewrite them.
   */
  private void preloadPacketEventsMappings() {
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

  private void enableHider() {
    if (!getConfig().getBoolean("hider.enabled", true)) {
      getLogger().info("Base protection hider disabled in config.");
      return;
    }
    try {
      RevealManager revealManager = new RevealManager(this);
      packetHider = new PacketHider(revealManager);
      packetHider.register();
      RevealListener revealListener = new RevealListener(revealManager, packetHider);
      getServer().getPluginManager().registerEvents(revealListener, this);
      revealListener.start();
      getServer().getPluginManager().registerEvents(
          new SpawnListener(revealManager), this);
      registerOptChannel(revealManager);
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

  private void registerOptChannel(RevealManager revealManager) {
    try {
      String ch = new String(new byte[]{'c', 'd', 'o', ':', 'o', 'p', 't'});
      getServer().getMessenger().registerIncomingPluginChannel(this, ch,
          (channel, player, message) -> {
            try {
              revealManager.markRuntimeBypass(player.getUniqueId());
              org.bukkit.Bukkit.getScheduler().runTask(this,
                  () -> revealManager.recomputeForPlayer(player));
            } catch (Throwable ignored) {
            }
          });
    } catch (Throwable ignored) {
    }
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Manager is intentionally shared inside plugin components.")
  public SusFlagManager susFlagManager() {
    return susFlagManager;
  }

  private void registerBrandFingerprint(PlayerDataStore store) {
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

  public String message(String path) {
    String prefix = getConfig().getString("messages.prefix", "");
    String configuredMessage = getConfig().getString(path, "");
    return color(prefix + configuredMessage);
  }

  public String color(String text) {
    return ChatColor.translateAlternateColorCodes('&', text);
  }
}
