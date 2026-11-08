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
import java.util.Map;
import java.util.UUID;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public record SusFlag(
    UUID targetId,
    String targetName,
    String reason,
    int count,
    Instant lastFlagged,
    String category,
    Map<String, Integer> sources) {

  public SusFlag(UUID targetId, String targetName, String reason, int count,
      Instant lastFlagged, String category, Map<String, Integer> sources) {
    this.targetId = targetId;
    this.targetName = targetName;
    this.reason = reason;
    this.count = count;
    this.lastFlagged = lastFlagged;
    this.category = category;
    this.sources = sources == null ? Map.of() : Map.copyOf(sources);
  }

  public SusFlag(UUID targetId, String targetName, String reason, int count,
      Instant lastFlagged, String category) {
    this(targetId, targetName, reason, count, lastFlagged, category, Map.of());
  }

  public SusFlag(UUID targetId, String targetName, String reason, int count, Instant lastFlagged) {
    this(targetId, targetName, reason, count, lastFlagged, "custom", Map.of());
  }

  public String sourceSummary() {
    if (sources.isEmpty()) {
      return "-";
    }
    StringBuilder summary = new StringBuilder();
    sources.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEach(entry -> {
          if (!summary.isEmpty()) {
            summary.append(", ");
          }
          summary.append(entry.getKey()).append('=').append(entry.getValue());
        });
    return summary.toString();
  }
}
