package com.notlucy.donutrecreation.spawn.commands;

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.spawn.manager.FakeEntityManager;
import com.notlucy.donutrecreation.spawn.manager.FakePlayerManager;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;
import com.notlucy.donutrecreation.spawn.manager.StashManager;
import com.notlucy.donutrecreation.util.LogData;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.AmethystCluster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /spawn <decoy>} — spawns ephemeral ghost decoys visible only to the issuing player.
 *
 * <ul>
 *   <li>{@code fakestash} — 6x6x4 hollow ghost room with double chests inside (5 min)</li>
 *   <li>{@code fakespawner} — single ghost skeleton spawner where the player is looking
 *       (5 min)</li>
 *   <li>{@code fakeplayer} — animated NPC spawned in front of the player; despawns after
 *       10 seconds (or 5 minutes when called via {@code fakebedrockspawner})</li>
 *   <li>{@code fakebedrockspawner} — locates the nearest 2x1 deepslate at y=-63, replaces
 *       all nearby deepslate within a 3-block radius with ghost obsidian, then places a
 *       ghost spawner + small amethyst bud + a long-lived fake player (5 min)</li>
 * </ul>
 *
 * <p>All blocks are ghost blocks (client-only); the underlying world is never modified.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class SpawnCommand implements CommandExecutor, TabCompleter {

  private static final long FIVE_MIN_TICKS = 6000L;
  private static final long TEN_SEC_TICKS = 200L;
  private static final long ONE_HOUR_TICKS = 20L * 60 * 60;
  private static final List<String> SUBCOMMANDS = List.of(
      "fakestash", "fakespawner", "fakeplayer", "fakebedrockspawner");
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

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Plugin and managers are shared by Bukkit.")
  public SpawnCommand(DonutRecreation plugin,
                       GhostBlockManager ghosts,
                       FakePlayerManager npcs,
                       FakeEntityManager fakeEntities,
                       StashManager stashes) {
    this.plugin = plugin;
    this.ghosts = ghosts;
    this.npcs = npcs;
    this.fakeEntities = fakeEntities;
    this.stashes = stashes;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.isOp()) {
      sender.sendMessage(plugin.message("messages.no-permission"));
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
      case "fakestash" -> spawnFakeStash(player);
      case "fakespawner" -> spawnFakeSpawner(player);
      case "fakeplayer" -> spawnFakePlayer(player);
      case "fakebedrockspawner" -> spawnFakeBedrockSpawner(player);
      default -> player.sendMessage(plugin.color("&cUnknown decoy. Valid: &f"
          + String.join(", ", SUBCOMMANDS)));
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                     String[] args) {
    if (!sender.isOp() || args.length != 1) {
      return List.of();
    }
    String prefix = args[0].toLowerCase(Locale.ROOT);
    List<String> out = new ArrayList<>();
    for (String s : SUBCOMMANDS) {
      if (s.startsWith(prefix)) {
        out.add(s);
      }
    }
    return out;
  }

  private void spawnFakeStash(Player player) {
    if (stashes == null || stashes.isEmpty()) {
      player.sendMessage(plugin.color(
          "&cNo stash templates found. Place .yml files in plugins/"
              + plugin.getName() + "/Stashs/"));
      return;
    }
    StashManager.StashTemplate template = stashes.pickRandom();
    if (template == null) {
      player.sendMessage(plugin.color("&cFailed to pick a stash template."));
      return;
    }
    Location origin = player.getLocation().getBlock().getLocation();
    List<GhostBlockManager.GhostBlock> ghostList = template.toGhostBlocks(origin);
    long id = ghosts.send(player, ghostList, FIVE_MIN_TICKS,
        () -> fakeEntities.despawnAllFor(player));
    spawnFakeEntities(player, template, origin);
    player.sendMessage(plugin.color(
        "&aFake stash '&f" + template.name + "&a' spawned (&f"
            + ghostList.size() + "&a blocks, group #" + id
            + "&a, reverts in 5min)."));
  }

  private void spawnFakeEntities(Player viewer,
                                  StashManager.StashTemplate template,
                                  Location origin) {
    int ox = origin.getBlockX();
    int oy = origin.getBlockY();
    int oz = origin.getBlockZ();
    for (StashManager.StashEntity e : template.entities) {
      Location bloc = new Location(origin.getWorld(), ox + e.x, oy + e.y, oz + e.z);
      switch (e.type) {
        case "armor_stand" -> {
          Location loc = new Location(origin.getWorld(),
              ox + e.x + 0.5, oy + e.y, oz + e.z + 0.5);
          fakeEntities.spawnArmorStand(viewer, loc, e.rotationYaw, FIVE_MIN_TICKS);
        }
        case "item_frame" -> {
          fakeEntities.spawnItemFrame(viewer, bloc, e.item, e.facing, FIVE_MIN_TICKS);
        }
        case "glow_item_frame" -> {
          fakeEntities.spawnGlowItemFrame(viewer, bloc, e.item, e.facing, FIVE_MIN_TICKS);
        }
        default -> LogData.get().fine("[stash] unknown entity type: " + e.type);
      }
    }
  }

  private void spawnFakeSpawner(Player player) {
    Block target = player.getTargetBlockExact(8);
    Location loc = target != null
        ? target.getRelative(BlockFace.UP).getLocation()
        : player.getLocation().getBlock().getLocation();
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
    int x = hole.center.getBlockX();
    int y = hole.center.getBlockY();
    int z = hole.center.getBlockZ();

    Location spawnerLoc;
    Location budLoc;
    Location npcLoc;
    if (hole.alongZ) {
      spawnerLoc = new Location(world, x, y, z - 1);
      budLoc = new Location(world, x, y, z + 2);
      npcLoc = new Location(world, x + 0.5, y, z + 1.0);
      npcLoc.setYaw(180f);
    } else {
      spawnerLoc = new Location(world, x - 1, y, z);
      budLoc = new Location(world, x + 2, y, z);
      npcLoc = new Location(world, x + 1.0, y, z + 0.5);
      npcLoc.setYaw(90f);
    }
    npcLoc.setPitch(0f);

    BlockData spawner = Material.SPAWNER.createBlockData();
    BlockData bud = Material.SMALL_AMETHYST_BUD.createBlockData();
    if (bud instanceof AmethystCluster) {
      ((AmethystCluster) bud).setFacing(hole.budFacing);
    }

    List<GhostBlockManager.GhostBlock> blocks = new ArrayList<>();
    if (world.getBlockAt(spawnerLoc).getType() != Material.BEDROCK) {
      blocks.add(new GhostBlockManager.GhostBlock(spawnerLoc, spawner));
    }
    if (world.getBlockAt(budLoc).getType() != Material.BEDROCK) {
      blocks.add(new GhostBlockManager.GhostBlock(budLoc, bud));
    }

    String fakeName = REALISTIC_NAMES[
        ThreadLocalRandom.current().nextInt(REALISTIC_NAMES.length)];
    npcs.spawn(npcLoc, fakeName, ONE_HOUR_TICKS, true);

    long blockGroupId = ghosts.send(player, blocks, FIVE_MIN_TICKS, null);
    ghosts.setRevertOnInteract(blockGroupId, true);

    player.sendMessage(plugin.color(
        "&aFake bedrock spawner set up at &f" + format(npcLoc)
            + " &a(group #" + blockGroupId
            + ", spawner @ " + format(spawnerLoc)
            + ", bud @ " + format(budLoc)
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

  /**
   * Searches around {@code origin} at y=-63 for a 2x1 deepslate pair.
   * Returns the nearest valid pair or null.
   */
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
