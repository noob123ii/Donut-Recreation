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
    int ox = origin.getBlockX() + 1;
    int oy = origin.getBlockY();
    int oz = origin.getBlockZ() + 1;
    World world = player.getWorld();
    BlockData stone = Material.STONE.createBlockData();
    BlockData air = Material.AIR.createBlockData();
    List<GhostBlockManager.GhostBlock> ghostList = new ArrayList<>(6 * 6 * 4);

    for (int dx = 0; dx < 6; dx++) {
      for (int dz = 0; dz < 6; dz++) {
        for (int dy = 0; dy < 4; dy++) {
          boolean shell = (dx == 0 || dx == 5 || dz == 0 || dz == 5
              || dy == 0 || dy == 3);
          Location loc = new Location(world, ox + dx, oy + dy, oz + dz);
          ghostList.add(new GhostBlockManager.GhostBlock(loc, shell ? stone : air));
        }
      }
    }

    Chest leftChest = (Chest) Material.CHEST.createBlockData();
    leftChest.setType(Chest.Type.LEFT);
    leftChest.setFacing(BlockFace.NORTH);
    Chest rightChest = (Chest) Material.CHEST.createBlockData();
    rightChest.setType(Chest.Type.RIGHT);
    rightChest.setFacing(BlockFace.NORTH);
    Location leftLoc = new Location(world, ox + 2, oy + 1, oz + 2);
    Location rightLoc = new Location(world, ox + 3, oy + 1, oz + 2);
    ghostList.add(new GhostBlockManager.GhostBlock(leftLoc, leftChest));
    ghostList.add(new GhostBlockManager.GhostBlock(rightLoc, rightChest));

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
    boolean ok = npcs.spawn(target, fakeName, TEN_SEC_TICKS);
    if (ok) {
      player.sendMessage(plugin.color("&aFake player &f" + fakeName
          + " &aspawned at &f" + format(target) + "&a (despawns in 10s)."));
    } else {
      player.sendMessage(plugin.color(
          "&cFailed to spawn fake player (PacketEvents wrappers unavailable)."));
    }
  }

  private void spawnFakeBedrockSpawner(Player player) {
    Location slot = findNearestSurfaceHole(player.getLocation(), 32);
    if (slot == null) {
      player.sendMessage(plugin.color(
          "&cNo standable 1x2 slot found near you at y=63 within 32 blocks."));
      return;
    }
    World world = slot.getWorld();
    int x = slot.getBlockX();
    int y = slot.getBlockY();
    int z = slot.getBlockZ();

    BlockData spawner = Material.SPAWNER.createBlockData();
    BlockData bud = Material.SMALL_AMETHYST_BUD.createBlockData();

    Location spawnerLoc = new Location(world, x, y, z - 1);
    Location budLoc = new Location(world, x, y, z + 1);
    Location npcLoc = new Location(world, x + 0.5, y, z + 0.5);
    npcLoc.setYaw(0f);
    npcLoc.setPitch(0f);

    List<GhostBlockManager.GhostBlock> blocks = List.of(
        new GhostBlockManager.GhostBlock(spawnerLoc, spawner),
        new GhostBlockManager.GhostBlock(budLoc, bud));

    long blockGroupId = ghosts.send(player, blocks, FIVE_MIN_TICKS, null);

    String fakeName = "Player_" + (1000 + (int) (Math.random() * 9000));
    npcs.spawn(npcLoc, fakeName, FIVE_MIN_TICKS);

    player.sendMessage(plugin.color(
        "&aFake bedrock spawner set up at &f" + format(npcLoc)
            + " &a(group #" + blockGroupId
            + ", spawner @ " + format(spawnerLoc)
            + ", bud @ " + format(budLoc)
            + ", NPC " + fakeName + ", reverts in 5min)."));
  }

  /**
   * Searches a square of {@code radius} blocks around {@code origin} for a 1-wide,
   * 2-tall standable slot whose feet block sits at y=63: feet and head must be air,
   * the block immediately below the feet must be solid, and both north and south
   * neighbours must also be air so the spawner and bud can be placed on either side
   * of the NPC.
   */
  private Location findNearestSurfaceHole(Location origin, int radius) {
    World world = origin.getWorld();
    int ox = origin.getBlockX();
    int oz = origin.getBlockZ();
    int feetY = 63;
    int best = Integer.MAX_VALUE;
    Location bestLoc = null;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        int x = ox + dx;
        int z = oz + dz;
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);
        Block floor = world.getBlockAt(x, feetY - 1, z);
        if (!feet.getType().isAir() || !head.getType().isAir()) {
          continue;
        }
        if (!floor.getType().isSolid()) {
          continue;
        }
        Block north = world.getBlockAt(x, feetY, z - 1);
        Block south = world.getBlockAt(x, feetY, z + 1);
        if (!north.getType().isAir() || !south.getType().isAir()) {
          continue;
        }
        int distSq = dx * dx + dz * dz;
        if (distSq < best) {
          best = distSq;
          bestLoc = new Location(world, x, feetY, z);
        }
      }
    }
    return bestLoc;
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
