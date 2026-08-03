package com.notlucy.donutrecreation.commands;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class ChunkGenerator {

  private final DonutRecreation plugin;

  public ChunkGenerator(DonutRecreation plugin) {
    this.plugin = plugin;
  }

  public void generateChunks(World world, int border, Player player) {
    if (border <= 0) {
      player.sendMessage(plugin.color("&cBorder must be greater than 0."));
      return;
    }

    int chunks = (border + 15) / 16;
    int min = -chunks;
    int max = chunks;
    long total = (long) (max - min + 1) * (max - min + 1);

    player.sendMessage(plugin.color("&aStarting chunk generation for world: " + world.getName()));
    player.sendMessage(plugin.color("&eBorder: " + border + " blocks (" + chunks + " chunks)"));
    player.sendMessage(plugin.color("&eTotal chunks to generate: " + total));
    player.sendMessage(plugin.color("&eThis will pre-generate chunks to prevent lag during gameplay."));

    LogData.get().info("[chunk] Starting generation for world " + world.getName()
        + " with border " + border + " blocks (" + total + " chunks)");

    new Task(world, min, max, player, total).runTaskTimer(plugin, 0L, 1L);
  }

  private class Task extends BukkitRunnable {
    private final World world;
    private final int min;
    private final int max;
    private final Player player;
    private final long total;

    private int x;
    private int z;
    private long count;
    private long lastReport;
    private long lastCount;

    Task(World world, int min, int max, Player player, long total) {
      this.world = world;
      this.min = min;
      this.max = max;
      this.player = player;
      this.total = total;

      this.x = min;
      this.z = min;
      this.count = 0L;
      this.lastReport = System.currentTimeMillis();
      this.lastCount = 0L;
    }

    @Override
    public void run() {
      if (!player.isOnline()) {
        cancel();
        LogData.get().info("[chunk] Generation cancelled - player went offline");
        return;
      }

      long deadline = System.nanoTime() + 35L * 1_000_000L;
      int cap = 250;
      int done = 0;

      while (x <= max && System.nanoTime() < deadline && done < cap) {
        world.getChunkAt(x, z);

        count++;
        done++;

        z++;
        if (z > max) {
          z = min;
          x++;
        }
      }

      long now = System.currentTimeMillis();
      if (now - lastReport >= 5000L) {
        long sinceLast = count - lastCount;
        double seconds = (now - lastReport) / 1000.0;
        double perSecond = sinceLast / Math.max(seconds, 0.001);
        double progress = (count * 100.0) / total;
        double eta = (total - count) / Math.max(perSecond, 0.1);

        player.sendMessage(plugin.color(
            "&eProgress: " + String.format("%.2f", progress) + "% | "
                + count + "/" + total + " chunks | "
                + String.format("%.1f", perSecond) + " chunks/sec | "
                + "ETA: " + formatTime(eta)));

        lastReport = now;
        lastCount = count;
      }

      if (x > max) {
        cancel();
        player.sendMessage(plugin.color("&aChunk generation complete! Generated " + count + " chunks."));
        LogData.get().info("[chunk] Generation complete for world " + world.getName()
            + " - generated " + count + " chunks");
      }
    }

    private String formatTime(double seconds) {
      if (seconds < 60.0) {
        return String.format("%.0fs", seconds);
      }

      long minutes = (long) (seconds / 60.0);
      if (minutes < 60) {
        return minutes + "m " + String.format("%.0fs", seconds % 60.0);
      }

      long hours = minutes / 60;
      return hours + "h " + (minutes % 60) + "m";
    }
  }
}