package com.notlucy.donutrecreation.staffmode;

import com.notlucy.donutrecreation.spawn.manager.SkinStore;
import java.util.UUID;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public record TestBot(int entityId, UUID uuid, String name, SkinStore.SkinRecord skin) {}
