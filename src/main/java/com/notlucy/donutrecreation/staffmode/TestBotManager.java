package com.notlucy.donutrecreation.staffmode;

import com.notlucy.donutrecreation.spawn.manager.FakePlayerManager;
import com.notlucy.donutrecreation.spawn.manager.SkinStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class TestBotManager implements FakePlayerManager.NpcHideState {

  public static final String BOT_NAME = "TestBot";

  private static final long TTL_TICKS = 30L * 60L * 20L;
  private static final int UNKNOWN_ENTITY_ID = Integer.MIN_VALUE;

  private final FakePlayerManager fakePlayers;
  private final HideManager hideManager;
  private final SkinStore skins;
  private final Map<UUID, TestBot> bots = new ConcurrentHashMap<>();
  private final Map<Integer, TestBot> botsById = new ConcurrentHashMap<>();

  public TestBotManager(FakePlayerManager fakePlayers, HideManager hideManager,
      SkinStore skins) {
    this.fakePlayers = fakePlayers;
    this.hideManager = hideManager;
    this.skins = skins;
    this.fakePlayers.setDespawnHook(this::onNpcDespawned);
  }

  private void onNpcDespawned(int entityId) {
    TestBot bot = botsById.remove(entityId);
    if (bot == null) {
      return;
    }
    bots.remove(bot.uuid());
    hideManager.unregisterExtraTarget(bot);
  }

  public boolean spawnTestBot(Player requester) {
    SkinStore.SkinRecord skin = skins.random();
    if (skin == null) {
      skin = SkinStore.liveOf(requester);
    }
    if (skin == null) {
      return false;
    }
    UUID botUuid = UUID.randomUUID();
    SkinStore.SkinRecord botSkin =
        new SkinStore.SkinRecord(botUuid, BOT_NAME, skin.texture(), skin.signature());
    TestBot placeholder = new TestBot(UNKNOWN_ENTITY_ID, botUuid, BOT_NAME, botSkin);
    bots.put(botUuid, placeholder);
    int entityId = fakePlayers.spawn(requester.getLocation(), botSkin,
        TTL_TICKS, FakePlayerManager.Pose.STANDING);
    if (!fakePlayers.isActiveNpc(entityId)) {
      bots.remove(botUuid);
      return false;
    }
    TestBot bot = new TestBot(entityId, botUuid, BOT_NAME, botSkin);
    bots.put(botUuid, bot);
    botsById.put(entityId, bot);
    hideManager.registerExtraTarget(bot);
    return true;
  }

  public void respawnBotFor(Player viewer, UUID botUuid) {
    TestBot bot = bots.get(botUuid);
    if (bot != null) {
      fakePlayers.respawnFor(viewer, bot.entityId());
    }
  }

  public int count() {
    return bots.size();
  }

  public void clearAll() {
    for (TestBot bot : List.copyOf(bots.values())) {
      fakePlayers.despawn(bot.entityId());
    }
  }

  @Override
  public boolean hideName(UUID viewerUuid, UUID npcUuid) {
    return bots.containsKey(npcUuid) && hideManager.isHidingName(viewerUuid);
  }

  @Override
  public boolean hideSkin(UUID viewerUuid, UUID npcUuid) {
    return bots.containsKey(npcUuid) && hideManager.isHidingSkin(viewerUuid);
  }
}
