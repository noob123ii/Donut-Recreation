package com.notlucy.donutrecreation.spawn.commands;

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.spawn.manager.FakePlayerManager;
import com.notlucy.donutrecreation.spawn.manager.GhostBlockManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.AmethystCluster;
import org.bukkit.block.data.type.Chest;
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
 *   <li>{@code fakebedrockspawner} — locates the nearest 2x1 surface hole at y=63, places
 *       a ghost spawner + small amethyst bud + a long-lived fake player (5 min)</li>
 * </ul>
 *
 * <p>All blocks are ghost blocks (client-only); the underlying world is never modified.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class SpawnCommand implements CommandExecutor, TabCompleter {

  private static final long FIVE_MIN_TICKS = 6000L;
  private static final long TEN_SEC_TICKS = 200L;
  private static final List<String> SUBCOMMANDS = List.of(
      "fakestash", "fakespawner", "fakeplayer", "fakebedrockspawner");

  private final DonutRecreation plugin;
  private final GhostBlockManager ghosts;
  private final FakePlayerManager npcs;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Plugin and managers are shared by Bukkit.")
  public SpawnCommand(DonutRecreation plugin,
                       GhostBlockManager ghosts,
                       FakePlayerManager npcs) {
    this.plugin = plugin;
    this.ghosts = ghosts;
    this.npcs = npcs;
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
    Location origin = player.getLocation().getBlock().getLocation();
    int ox = origin.getBlockX();
    int oy = origin.getBlockY() - 2;
    int oz = origin.getBlockZ();
    World world = player.getWorld();
    BlockData obsidian = Material.OBSIDIAN.createBlockData();
    BlockData stoneBricks = Material.STONE_BRICKS.createBlockData();
    BlockData air = Material.AIR.createBlockData();
    List<GhostBlockManager.GhostBlock> ghostList = new ArrayList<>();

    int w = 9;
    int d = 7;
    int h = 5;
    for (int dx = 0; dx < w; dx++) {
      for (int dz = 0; dz < d; dz++) {
        for (int dy = 0; dy < h; dy++) {
          boolean wall = (dx == 0 || dx == w - 1 || dz == 0 || dz == d - 1
              || dy == 0 || dy == h - 1);
          Location loc = new Location(world, ox + dx, oy + dy, oz + dz);
          if (wall) {
            ghostList.add(new GhostBlockManager.GhostBlock(loc, obsidian));
          } else if (dy == 1) {
            ghostList.add(new GhostBlockManager.GhostBlock(loc, stoneBricks));
          } else {
            ghostList.add(new GhostBlockManager.GhostBlock(loc, air));
          }
        }
      }
    }

    int chestZ = oz + 1;
    int chestY = oy + 2;
    for (int i = 0; i < 4; i++) {
      Chest left = (Chest) Material.CHEST.createBlockData();
      left.setType(Chest.Type.LEFT);
      left.setFacing(BlockFace.SOUTH);
      Chest right = (Chest) Material.CHEST.createBlockData();
      right.setType(Chest.Type.RIGHT);
      right.setFacing(BlockFace.SOUTH);
      int cx = ox + 2 + i * 2;
      ghostList.add(new GhostBlockManager.GhostBlock(
          new Location(world, cx, chestY, chestZ), left));
      ghostList.add(new GhostBlockManager.GhostBlock(
          new Location(world, cx + 1, chestY, chestZ), right));
    }

    ghostList.add(new GhostBlockManager.GhostBlock(
        new Location(world, ox + 1, oy + 2, oz + 3),
        Material.CRAFTING_TABLE.createBlockData()));
    ghostList.add(new GhostBlockManager.GhostBlock(
        new Location(world, ox + 1, oy + 2, oz + 4),
        Material.FURNACE.createBlockData()));
    ghostList.add(new GhostBlockManager.GhostBlock(
        new Location(world, ox + 1, oy + 2, oz + 5),
        Material.ANVIL.createBlockData()));
    ghostList.add(new GhostBlockManager.GhostBlock(
        new Location(world, ox + w - 2, oy + 2, oz + 3),
        Material.ENCHANTING_TABLE.createBlockData()));

    long id = ghosts.send(player, ghostList, FIVE_MIN_TICKS, null);
    player.sendMessage(plugin.color(
        "&aFake stash spawned (&f" + ghostList.size() + "&a blocks, group #"
            + id + "&a, reverts in 5min)."));
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
    String fakeName = "Steve_" + (1000 + (int) (Math.random() * 9000));
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
    Hole hole = findBedrockHole(player.getLocation(), 32);
    if (hole == null) {
      player.sendMessage(plugin.color(
          "&cNo enclosed 2-1 bedrock tunnel found within 32 blocks."));
      return;
    }
    World world = hole.center.getWorld();
    int x = hole.center.getBlockX();
    int y = hole.center.getBlockY();
    int z = hole.center.getBlockZ();

    Location spawnerLoc;
    Location budLoc;
    Location npcLoc = new Location(world, x + 0.5, y, z + 0.5);
    if (hole.alongZ) {
      spawnerLoc = new Location(world, x, y, z - 1);
      budLoc = new Location(world, x, y, z + 1);
      npcLoc.setYaw(180f);
    } else {
      spawnerLoc = new Location(world, x - 1, y, z);
      budLoc = new Location(world, x + 1, y, z);
      npcLoc.setYaw(90f);
    }
    npcLoc.setPitch(0f);

    BlockData spawner = Material.SPAWNER.createBlockData();
    BlockData bud = Material.SMALL_AMETHYST_BUD.createBlockData();
    if (bud instanceof AmethystCluster) {
      ((AmethystCluster) bud).setFacing(hole.budFacing);
    }

    List<GhostBlockManager.GhostBlock> blocks = List.of(
        new GhostBlockManager.GhostBlock(spawnerLoc, spawner),
        new GhostBlockManager.GhostBlock(budLoc, bud));

    String fakeName = "Player_" + (1000 + (int) (Math.random() * 9000));
    int npcId = npcs.spawn(npcLoc, fakeName, FIVE_MIN_TICKS, true);

    long blockGroupId = ghosts.send(player, blocks, FIVE_MIN_TICKS, () -> {
      if (npcId != -1) {
        npcs.despawn(npcId);
      }
    });
    ghosts.setRevertOnInteract(blockGroupId, true);

    player.sendMessage(plugin.color(
        "&aFake bedrock spawner set up at &f" + format(npcLoc)
            + " &a(group #" + blockGroupId
            + ", spawner @ " + format(spawnerLoc)
            + ", bud @ " + format(budLoc)
            + ", NPC " + fakeName + " crawling, reverts in 5min)."));
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
   * Searches around {@code origin} for a 1-block tall, 3-block long tunnel
   * completely enclosed in bedrock where the two end walls are deepslate.
   * Returns the nearest valid tunnel or null.
   */
  private Hole findBedrockHole(Location origin, int radius) {
    World world = origin.getWorld();
    int ox = origin.getBlockX();
    int oy = origin.getBlockY();
    int oz = origin.getBlockZ();
    int best = Integer.MAX_VALUE;
    Hole bestHole = null;
    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
          int x = ox + dx;
          int y = oy + dy;
          int z = oz + dz;
          int distSq = dx * dx + dy * dy + dz * dz;
          if (distSq >= best) {
            continue;
          }
          if (isValidBedrockTunnelZ(world, x, y, z)) {
            best = distSq;
            bestHole = new Hole(new Location(world, x, y, z), true, BlockFace.NORTH);
          }
          if (isValidBedrockTunnelX(world, x, y, z)) {
            best = distSq;
            bestHole = new Hole(new Location(world, x, y, z), false, BlockFace.WEST);
          }
        }
      }
    }
    return bestHole;
  }

  private boolean isValidBedrockTunnelZ(World world, int x, int y, int z) {
    if (!world.getBlockAt(x, y, z).getType().isAir()) {
      return false;
    }
    if (world.getBlockAt(x, y, z - 1).getType() != Material.DEEPSLATE) {
      return false;
    }
    if (world.getBlockAt(x, y, z + 1).getType() != Material.DEEPSLATE) {
      return false;
    }
    for (int dz = -1; dz <= 1; dz++) {
      if (world.getBlockAt(x, y - 1, z + dz).getType() != Material.BEDROCK) {
        return false;
      }
      if (world.getBlockAt(x, y + 1, z + dz).getType() != Material.BEDROCK) {
        return false;
      }
    }
    for (int dz = -1; dz <= 1; dz++) {
      if (world.getBlockAt(x - 1, y, z + dz).getType() != Material.BEDROCK) {
        return false;
      }
      if (world.getBlockAt(x + 1, y, z + dz).getType() != Material.BEDROCK) {
        return false;
      }
    }
    if (world.getBlockAt(x, y, z - 2).getType() != Material.BEDROCK) {
      return false;
    }
    if (world.getBlockAt(x, y, z + 2).getType() != Material.BEDROCK) {
      return false;
    }
    return true;
  }

  private boolean isValidBedrockTunnelX(World world, int x, int y, int z) {
    if (!world.getBlockAt(x, y, z).getType().isAir()) {
      return false;
    }
    if (world.getBlockAt(x - 1, y, z).getType() != Material.DEEPSLATE) {
      return false;
    }
    if (world.getBlockAt(x + 1, y, z).getType() != Material.DEEPSLATE) {
      return false;
    }
    for (int dx = -1; dx <= 1; dx++) {
      if (world.getBlockAt(x + dx, y - 1, z).getType() != Material.BEDROCK) {
        return false;
      }
      if (world.getBlockAt(x + dx, y + 1, z).getType() != Material.BEDROCK) {
        return false;
      }
    }
    for (int dx = -1; dx <= 1; dx++) {
      if (world.getBlockAt(x + dx, y, z - 1).getType() != Material.BEDROCK) {
        return false;
      }
      if (world.getBlockAt(x + dx, y, z + 1).getType() != Material.BEDROCK) {
        return false;
      }
    }
    if (world.getBlockAt(x - 2, y, z).getType() != Material.BEDROCK) {
      return false;
    }
    if (world.getBlockAt(x + 2, y, z).getType() != Material.BEDROCK) {
      return false;
    }
    return true;
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
