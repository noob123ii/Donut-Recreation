package com.notlucy.donutrecreation.sus.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class SusFlagManager {
  private final Map<UUID, SusFlag> flags = new LinkedHashMap<>();

  public void flag(Player target, String reason) {
    flag(target.getUniqueId(), target.getName(), reason, "custom");
  }

  public void flag(Player target, String reason, String category) {
    flag(target.getUniqueId(), target.getName(), reason, category);
  }

  public synchronized void flag(UUID uuid, String name, String reason) {
    flag(uuid, name, reason, "custom");
  }

  public synchronized void flag(UUID uuid, String name, String reason, String category) {
    SusFlag last = flags.get(uuid);
    int count = last == null ? 1 : last.count() + 1;
    String cat = (category != null) ? category : "custom";
    flags.put(uuid, new SusFlag(uuid, name, reason, count, Instant.now(), cat));
  }

  public synchronized void clear(UUID targetId) {
    flags.remove(targetId);
  }

  public synchronized List<SusFlag> queuedFlags(String category) {
    return flags.values().stream()
        .filter(f -> category == null || category.equals(f.category()))
        .sorted(Comparator.comparing(SusFlag::lastFlagged).reversed())
        .limit(45)
        .toList();
  }

  public synchronized int countByCategory(String category) {
    return (int) flags.values().stream()
        .filter(f -> category.equals(f.category()))
        .count();
  }
}
