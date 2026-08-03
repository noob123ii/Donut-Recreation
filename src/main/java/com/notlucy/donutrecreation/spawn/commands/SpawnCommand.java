package com.notlucy.donutrecreation.spawn.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.AmethystCluster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.spawn.manager.FakeEntityManager;
import com.notlucy.donutrecreation.spawn.manager.FakePlayerManager;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;
import com.notlucy.donutrecreation.spawn.manager.StashManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class SpawnCommand implements CommandExecutor, TabCompleter {

  private static final long FIVE_MIN_TICKS = 6000L;
  private static final long TEN_SEC_TICKS = 200L;
  private static final long ONE_HOUR_TICKS = 20L * 60 * 60;
  private static final int STASH_BROADCAST_RADIUS = 64;
  private static final List<String> SUBCOMMANDS = List.of(
      "stash", "spawner", "player", "bedrockspawner");
  private static final String[] REALISTIC_NAMES = {
      "xXShadowXx", "DarkSlayer", "Herobrine", "CreeperHugger",
      "DiamondDude", "NoobMaster69", "PVPKing", "EnderFox",
      "Steve", "Alex", "Notch", "Dream", "Techno", "Philza"
  };

  private final DonutRecreation plugin;
  private final GhostBlockManager ghosts;
  private final FakePlayerManager npcs;
  private final FakeEntityManager fakeEntities;
  private final StashManager stashes;
  private final RevealManager revealManager;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Plugin and managers are shared by Bukkit.")
  public SpawnCommand(DonutRecreation plugin,
                       GhostBlockManager ghosts,
                       FakePlayerManager npcs,
                       FakeEntityManager fakeEntities,
                       StashManager stashes,
                       RevealManager revealManager) {
    this.plugin = plugin;
    this.ghosts = ghosts;
    this.npcs = npcs;
    this.fakeEntities = fakeEntities;
    this.stashes = stashes;
    this.revealManager = revealManager;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.hasPermission("donutrecreation.*")) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }
    if (!plugin.isStaffModeActive()) {
      sender.sendMessage(plugin.color("&cEnable staff mode with /staffmode to use that command."));
      return true;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.message("messages.player-only"));
      return true;
    }
    if (args.length < 1) {
      player.sendMessage(plugin.color("&cUsage: /spawn <"
          + String.join("|", SUBCOMMANDS) + ">"));
      return true;
    }
    String sub = args[0].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "stash" -> {
        String name = args.length > 1 ? args[1] : null;
        spawnFakeStash(player, name);
      }
      case "spawner" -> spawnFakeSpawner(player);
      case "player" -> spawnFakePlayer(player);
      case "bedrockspawner" -> spawnFakeBedrockSpawner(player);
      default -> player.sendMessage(plugin.color("&cUnknown decoy. Valid: &f"
          + String.join(", ", SUBCOMMANDS)));
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                     String[] args) {
    if (!sender.hasPermission("donutrecreation.*")) {
      return List.of();
    }
    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      for (String s : SUBCOMMANDS) {
        if (s.startsWith(prefix)) {
          out.add(s);
        }
      }
      return out;
    }
    if (args.length == 2 && args[0].equalsIgnoreCase("stash") && stashes != null) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      for (String name : stashes.getTemplateNames()) {
        if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
          out.add(name);
        }
      }
      return out;
    }
    return List.of();
  }

  private void spawnFakeStash(Player player, String name) {
    if (stashes == null || stashes.isEmpty()) {
      player.sendMessage(plugin.color(
          "&cNo stash templates found. Place .yml files in plugins/"
              + plugin.getName() + "/Stashs/"));
      return;
    }
    StashManager.StashTemplate template;
    if (name != null && !name.isEmpty()) {
      template = stashes.getByName(name);
      if (template == null) {
        player.sendMessage(plugin.color("&cStash template '&f" + name + "&c' not found."));
        return;
      }
    } else {
      template = stashes.pickRandom();
      if (template == null) {
        player.sendMessage(plugin.color("&cFailed to pick a stash template."));
        return;
      }
    }
    Location origin = player.getLocation().getBlock().getLocation();
    List<GhostBlockManager.GhostBlock> ghostList = template.toGhostBlocks(origin);
    long id = ghosts.broadcast(ghostList, FIVE_MIN_TICKS, STASH_BROADCAST_RADIUS,
        () -> fakeEntities.despawnAllFor(player));
    int viewerCount = 0;
    for (var p : Bukkit.getOnlinePlayers()) {
      if (!p.getWorld().equals(origin.getWorld())) continue;
      if (p.getLocation().distanceSquared(origin) <= (double) STASH_BROADCAST_RADIUS * STASH_BROADCAST_RADIUS) {
        viewerCount++;
      }
    }
    player.sendMessage(plugin.color(
        "&aFake stash '&f" + template.name + "&a' spawned (&f"
            + ghostList.size() + "&a blocks, group #" + id
            + "&a, visible to &f" + viewerCount + " &aplayers, reverts in 5min)."));
  }

  private void spawnFakeSpawner(Player player) {
    Location loc = player.getLocation().getBlock().getLocation();
    BlockData spawner = Material.SPAWNER.createBlockData();
    long id = ghosts.send(player,
        List.of(new GhostBlockManager.GhostBlock(loc, spawner)),
        FIVE_MIN_TICKS,
        null);
    player.sendMessage(plugin.color(
        "&aFake skeleton spawner placed at &f" + format(loc) + "&a (group #"
            + id + "&a, reverts in 5min)."));
  }

  private void spawnFakePlayer(Player player) {
    Location target = playerFacingLocation(player, 2.0);
    String fakeName = "Steve_" + (1000 + ThreadLocalRandom.current().nextInt(9000));
    int npcId = npcs.spawn(target, fakeName, TEN_SEC_TICKS);
    if (npcId != -1) {
      player.sendMessage(plugin.color("&aFake player &f" + fakeName
          + " &aspawned at &f" + format(target) + "&a (despawns in 10s)."));
    } else {
      player.sendMessage(plugin.color(
          "&cFailed to spawn fake player (PacketEvents wrappers unavailable)."));
    }
  }

  private void spawnFakeBedrockSpawner(Player player) {
    Hole hole = findDeepslatePair(player.getLocation(), 32);
    if (hole == null) {
      player.sendMessage(plugin.color(
          "&cNo 2x1 deepslate found at y=-63 within 32 blocks."));
      return;
    }
    World world = hole.center.getWorld();
    int y = hole.center.getBlockY();

    Location playerLoc = player.getLocation().getBlock().getLocation();
    Location spawnerLoc;
    Location npcLoc;
    Location budLoc;
    if (hole.alongZ) {
      spawnerLoc = new Location(world, playerLoc.getX(), y, playerLoc.getZ() - 1);
      npcLoc = new Location(world, playerLoc.getX() + 0.5, y, playerLoc.getZ() + 0.5);
      budLoc = new Location(world, playerLoc.getX(), y, playerLoc.getZ() + 1);
      npcLoc.setYaw(180f);
    } else {
      spawnerLoc = new Location(world, playerLoc.getX() - 1, y, playerLoc.getZ());
      npcLoc = new Location(world, playerLoc.getX() + 0.5, y, playerLoc.getZ() + 0.5);
      budLoc = new Location(world, playerLoc.getX() + 1, y, playerLoc.getZ());
      npcLoc.setYaw(90f);
    }
    npcLoc.setPitch(0f);

    BlockData spawner = Material.SPAWNER.createBlockData();
    BlockData bud = Material.SMALL_AMETHYST_BUD.createBlockData();
    if (bud instanceof AmethystCluster) {
      ((AmethystCluster) bud).setFacing(hole.budFacing);
    }
    BlockData obsidian = Material.OBSIDIAN.createBlockData();

    List<GhostBlockManager.GhostBlock> blocks = new ArrayList<>();
    blocks.add(new GhostBlockManager.GhostBlock(spawnerLoc, spawner));
    blocks.add(new GhostBlockManager.GhostBlock(budLoc, bud));
    int npcX = npcLoc.getBlockX();
    int npcY = npcLoc.getBlockY();
    int npcZ = npcLoc.getBlockZ();
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        if (dx == 0 && dz == 0) {
          continue;
        }
        Location obsLoc = new Location(world, npcX + dx, npcY, npcZ + dz);
        if (world.getBlockAt(obsLoc).getType() != Material.BEDROCK) {
          blocks.add(new GhostBlockManager.GhostBlock(obsLoc, obsidian));
        }
      }
    }

    String fakeName = REALISTIC_NAMES[
        ThreadLocalRandom.current().nextInt(REALISTIC_NAMES.length)];
    npcs.spawn(npcLoc, fakeName, ONE_HOUR_TICKS, true);

    if (revealManager != null) {
      revealManager.forceRevealGeodeChunk(player, npcLoc.getBlockX() >> 4, npcLoc.getBlockZ() >> 4);
    }

    long blockGroupId = ghosts.send(player, blocks, FIVE_MIN_TICKS, null);
    ghosts.setRevertOnInteract(blockGroupId, true);

    player.sendMessage(plugin.color(
        "&aFake bedrock spawner set up at &f" + format(npcLoc)
            + " &a(group #" + blockGroupId
            + ", spawner @ " + format(spawnerLoc)
            + ", bud @ " + format(npcLoc.getBlock().getLocation())
            + ", NPC " + fakeName + " crawling)."));
  }

  private static final class Hole {
    final Location center;
    final boolean alongZ;
    final BlockFace budFacing;

    Hole(Location center, boolean alongZ, BlockFace budFacing) {
      this.center = center;
      this.alongZ = alongZ;
      this.budFacing = budFacing;
    }
  }

  private Hole findDeepslatePair(Location origin, int radius) {
    World world = origin.getWorld();
    int ox = origin.getBlockX();
    int oz = origin.getBlockZ();
    int targetY = -63;
    int best = Integer.MAX_VALUE;
    Hole bestHole = null;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        int x = ox + dx;
        int z = oz + dz;
        int distSq = dx * dx + dz * dz;
        if (distSq >= best) {
          continue;
        }
        if (isDeepslatePairZ(world, x, targetY, z)) {
          best = distSq;
          bestHole = new Hole(new Location(world, x, targetY, z), true, BlockFace.NORTH);
        }
        if (isDeepslatePairX(world, x, targetY, z)) {
          best = distSq;
          bestHole = new Hole(new Location(world, x, targetY, z), false, BlockFace.WEST);
        }
      }
    }
    return bestHole;
  }

  private boolean isDeepslatePairZ(World world, int x, int y, int z) {
    return world.getBlockAt(x, y, z).getType() == Material.DEEPSLATE
        && world.getBlockAt(x, y, z + 1).getType() == Material.DEEPSLATE;
  }

  private boolean isDeepslatePairX(World world, int x, int y, int z) {
    return world.getBlockAt(x, y, z).getType() == Material.DEEPSLATE
        && world.getBlockAt(x + 1, y, z).getType() == Material.DEEPSLATE;
  }

  private Location playerFacingLocation(Player player, double distance) {
    Location eye = player.getEyeLocation();
    var direction = eye.getDirection().setY(0).normalize();
    Location target = eye.clone().add(direction.multiply(distance));
    target.setY(player.getLocation().getY());
    target.setYaw(player.getLocation().getYaw() + 180f);
    target.setPitch(0f);
    return target;
  }

  private String format(Location loc) {
    return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
  }
}