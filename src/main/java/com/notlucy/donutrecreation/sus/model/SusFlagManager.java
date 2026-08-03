package com.notlucy.donutrecreation.sus.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class SusFlagManager {
  private final Map<UUID, SusFlag> flags = new ConcurrentHashMap<>();

  public void flag(Player target, String reason) {
    flag(target.getUniqueId(), target.getName(), reason, "custom");
  }

  public void flag(Player target, String reason, String category) {
    flag(target.getUniqueId(), target.getName(), reason, category);
  }

  public void flag(UUID uuid, String name, String reason) {
    flag(uuid, name, reason, "custom");
  }

  public void flag(UUID uuid, String name, String reason, String category) {
    SusFlag last = flags.get(uuid);
    int count = last == null ? 1 : last.count() + 1;
    String cat = (category != null) ? category : "custom";
    flags.put(uuid, new SusFlag(uuid, name, reason, count, Instant.now(), cat));
  }

  public SusFlag getFlag(UUID targetId) {
    return flags.get(targetId);
  }

  public void clear(UUID targetId) {
    flags.remove(targetId);
  }

  public List<SusFlag> allFlags() {
    return new ArrayList<>(flags.values());
  }

  public List<SusFlag> allFlagsSorted() {
    return flags.values().stream()
        .sorted(Comparator.comparing(SusFlag::lastFlagged).reversed())
        .toList();
  }

  public int totalCount() {
    return flags.size();
  }
}
