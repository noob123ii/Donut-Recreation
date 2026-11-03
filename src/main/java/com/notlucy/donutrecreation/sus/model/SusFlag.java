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
