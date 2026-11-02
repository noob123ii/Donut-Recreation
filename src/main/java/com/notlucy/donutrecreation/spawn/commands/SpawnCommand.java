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
      "stash", "spawner", "player", "bedrockspawner", "clear");
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
  private final com.notlucy.donutrecreation.spawn.manager.SkinStore skins;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Plugin and managers are shared by Bukkit.")
  public SpawnCommand(DonutRecreation plugin,
                       GhostBlockManager ghosts,
                       FakePlayerManager npcs,
                       FakeEntityManager fakeEntities,
                       StashManager stashes,
                       RevealManager revealManager,
                       com.notlucy.donutrecreation.spawn.manager.SkinStore skins) {
    this.plugin = plugin;
    this.ghosts = ghosts;
    this.npcs = npcs;
    this.fakeEntities = fakeEntities;
    this.stashes = stashes;
    this.revealManager = revealManager;
    this.skins = skins;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!plugin.hasStaffAccess(sender)) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.message("messages.player-only"));
      return true;
    }
    if (!plugin.isStaffModeActive(player.getUniqueId())) {
      sender.sendMessage(plugin.color("&cEnable staff mode with /staffmode to use that command."));
      return true;
    }
    if (args.length < 1) {
      player.sendMessage(plugin.color("&cUsage: /spawn <"
          + String.join("|", SUBCOMMANDS) + ">"));
      return true;
    }
    String subcommand = args[0].toLowerCase(Locale.ROOT);
    switch (subcommand) {
      case "stash" -> {
        String name = args.length > 1 ? args[1] : null;
        spawnFakeStash(player, name);
      }
      case "spawner" -> spawnFakeSpawner(player);
      case "player" -> spawnFakePlayer(player);
      case "bedrockspawner" -> spawnFakeBedrockSpawner(player);
      case "clear" -> {
        ghosts.revertAllFor(player.getUniqueId());
        fakeEntities.despawnAllFor(player);
        npcs.despawnAll();
        player.sendMessage(plugin.color(
            "&aCleared all your spawned ghost blocks, fake players and decoys."));
      }
      default -> player.sendMessage(plugin.color("&cUnknown decoy. Valid: &f"
          + String.join(", ", SUBCOMMANDS)));
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                     String[] args) {
    if (!plugin.hasStaffAccess(sender)) {
      return List.of();
    }
    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      for (String subcommandName : SUBCOMMANDS) {
        if (subcommandName.startsWith(prefix)) {
          out.add(subcommandName);
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
    List<GhostBlockManager.GhostBlock> ghostList =
        template.toGhostBlocks(origin, player.getLocation().getYaw());
    long groupId = ghosts.broadcast(ghostList, FIVE_MIN_TICKS, STASH_BROADCAST_RADIUS,
        () -> fakeEntities.despawnAllFor(player));
    int viewerCount = 0;
    for (var onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (!onlinePlayer.getWorld().equals(origin.getWorld())) continue;
      if (onlinePlayer.getLocation().distanceSquared(origin) <= (double) STASH_BROADCAST_RADIUS * STASH_BROADCAST_RADIUS) {
        viewerCount++;
      }
    }
    player.sendMessage(plugin.color(
        "&aFake stash '&f" + template.name + "&a' spawned (&f"
            + ghostList.size() + "&a blocks, group #" + groupId
            + "&a, visible to &f" + viewerCount + " &aplayers, reverts in 5min)."));
  }

  private void spawnFakeSpawner(Player player) {
    Location location = player.getLocation().getBlock().getLocation();
    BlockData spawner = Material.SPAWNER.createBlockData();
    long groupId = ghosts.send(player,
        List.of(new GhostBlockManager.GhostBlock(location, spawner)),
        FIVE_MIN_TICKS,
        null);
    player.sendMessage(plugin.color(
        "&aFake skeleton spawner placed at &f" + format(location) + "&a (group #"
            + groupId + "&a, reverts in 5min)."));
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

  private static final int BEDROCK_STYLE_BUD = 0;
  private static final int BEDROCK_STYLE_SINGLE = 1;
  private static final int BEDROCK_STYLE_DOUBLE = 2;
  private static final int BEDROCK_STYLE_AROUND = 3;

  private void spawnFakeBedrockSpawner(Player player) {
    Hole hole = findDeepslatePair(player.getLocation(), 32);
    if (hole == null) {
      player.sendMessage(plugin.color(
          "&cNo 2x1 deepslate found at centerY=-63 within 32 blocks."));
      return;
    }
    World world = hole.center.getWorld();
    int centerY = hole.center.getBlockY();

    Location playerLoc = player.getLocation().getBlock().getLocation();
    Location spawnerLocation;
    Location npcLocation;
    Location budLoc;
    if (hole.alongZ) {
      spawnerLocation = new Location(world, playerLoc.getX(), centerY, playerLoc.getZ() - 1);
      npcLocation = new Location(world, playerLoc.getX() + 0.5, centerY, playerLoc.getZ() + 0.5);
      budLoc = new Location(world, playerLoc.getX(), centerY, playerLoc.getZ() + 1);
      npcLocation.setYaw(180f);
    } else {
      spawnerLocation = new Location(world, playerLoc.getX() - 1, centerY, playerLoc.getZ());
      npcLocation = new Location(world, playerLoc.getX() + 0.5, centerY, playerLoc.getZ() + 0.5);
      budLoc = new Location(world, playerLoc.getX() + 1, centerY, playerLoc.getZ());
      npcLocation.setYaw(90f);
    }
    npcLocation.setPitch(0f);

    BlockData spawner = Material.SPAWNER.createBlockData();
    BlockData budBlock = Material.SMALL_AMETHYST_BUD.createBlockData();
    if (budBlock instanceof AmethystCluster) {
      ((AmethystCluster) budBlock).setFacing(hole.budFacing);
    }
    BlockData obsidian = Material.OBSIDIAN.createBlockData();
    BlockData airBlock = Material.AIR.createBlockData();

    int npcX = npcLocation.getBlockX();
    int npcY = npcLocation.getBlockY();
    int npcZ = npcLocation.getBlockZ();

    List<GhostBlockManager.GhostBlock> blocks = new ArrayList<>();
    blocks.add(new GhostBlockManager.GhostBlock(spawnerLocation, spawner));
    blocks.add(new GhostBlockManager.GhostBlock(budLoc, budBlock));
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        if (offsetX == 0 && offsetZ == 0) {
          continue;
        }
        Location obsLoc = new Location(world, npcX + offsetX, npcY, npcZ + offsetZ);
        if (world.getBlockAt(obsLoc).getType() != Material.BEDROCK) {
          blocks.add(new GhostBlockManager.GhostBlock(obsLoc, obsidian));
        }
      }
    }

    int npcFeetY = npcLocation.getBlockY();
    int npcHeadY = npcFeetY + 1;
    blocks.add(new GhostBlockManager.GhostBlock(
        new Location(world, npcLocation.getX(), npcFeetY, npcLocation.getZ()), airBlock));
    blocks.add(new GhostBlockManager.GhostBlock(
        new Location(world, npcLocation.getX(), npcHeadY, npcLocation.getZ()), airBlock));

    String fakeName = REALISTIC_NAMES[
        ThreadLocalRandom.current().nextInt(REALISTIC_NAMES.length)];
    npcs.spawn(npcLocation, fakeName, ONE_HOUR_TICKS, true);

    if (revealManager != null) {
      revealManager.forceGeodeVisible(player, npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4);
    }

    long blockGroupId = ghosts.broadcast(blocks, FIVE_MIN_TICKS, STASH_BROADCAST_RADIUS, null);
    ghosts.setRevertOnInteract(blockGroupId, true);

    player.sendMessage(plugin.color(
        "&aFake bedrock spawner set up at &f" + format(npcLocation)
            + " &a(group #" + blockGroupId
            + ", spawner @ " + format(spawnerLocation)
            + ", budBlock @ " + format(budLoc)
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
    int originX = origin.getBlockX();
    int originZ = origin.getBlockZ();
    int targetY = -63;
    int best = Integer.MAX_VALUE;
    Hole bestHole = null;
    for (int offsetX = -radius; offsetX <= radius; offsetX++) {
      for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
        int x = originX + offsetX;
        int z = originZ + offsetZ;
        int distSq = offsetX * offsetX + offsetZ * offsetZ;
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

  private boolean isDeepslatePairZ(World world, int x, int centerY, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4)
        || !world.isChunkLoaded(x >> 4, (z + 1) >> 4)) {
      return false;
    }
    return world.getBlockAt(x, centerY, z).getType() == Material.DEEPSLATE
        && world.getBlockAt(x, centerY, z + 1).getType() == Material.DEEPSLATE;
  }

  private boolean isDeepslatePairX(World world, int x, int centerY, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4)
        || !world.isChunkLoaded((x + 1) >> 4, z >> 4)) {
      return false;
    }
    return world.getBlockAt(x, centerY, z).getType() == Material.DEEPSLATE
        && world.getBlockAt(x + 1, centerY, z).getType() == Material.DEEPSLATE;
  }

  private Location playerFacingLocation(Player player, double distance) {
    Location eyeLocation = player.getEyeLocation();
    var direction = eyeLocation.getDirection().setY(0).normalize();
    Location target = eyeLocation.clone().add(direction.multiply(distance));
    target.setY(player.getLocation().getY());
    target.setYaw(player.getLocation().getYaw() + 180f);
    target.setPitch(0f);
    return target;
  }

  private String format(Location location) {
    return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
  }
}
