package com.crimsonwarpedcraft.donutpluginrecreation.sus.model;

import java.time.Instant;
import java.util.ArrayList;
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
    flag(target.getUniqueId(), target.getName(), reason);
  }

  public synchronized void flag(UUID uuid, String name, String reason) {
    SusFlag current = flags.get(uuid);
    int count = current == null ? 1 : current.count() + 1;
    flags.put(uuid, new SusFlag(uuid, name, reason, count, Instant.now()));
  }

  public synchronized void clear(UUID targetId) {
    flags.remove(targetId);
  }

  public synchronized List<SusFlag> queuedFlags() {
    return flags.values().stream()
        .sorted(Comparator.comparing(SusFlag::lastFlagged).reversed())
        .limit(45)
        .toList();
  }

  public synchronized List<SusFlag> allFlags() {
    return new ArrayList<>(flags.values());
  }
}
