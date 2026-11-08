/*
 * Copyright (C) 2026 Notlucy and Donut Recreation contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.notlucy.donutrecreation.sus.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    String cat = (category != null) ? category : "custom";
    flags.compute(uuid, (id, last) -> {
      Map<String, Integer> sources = last == null
          ? new HashMap<>() : new HashMap<>(last.sources());
      sources.merge(cat, 1, Integer::sum);
      return new SusFlag(id, name, reason, last == null ? 1 : last.count() + 1,
          Instant.now(), cat, sources);
    });
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
