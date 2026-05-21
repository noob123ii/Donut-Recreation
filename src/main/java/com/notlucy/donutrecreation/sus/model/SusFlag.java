package com.notlucy.donutrecreation.sus.model;

import java.time.Instant;
import java.util.UUID;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public record SusFlag(
    UUID targetId,
    String targetName,
    String reason,
    int count,
    Instant lastFlagged) {
}
