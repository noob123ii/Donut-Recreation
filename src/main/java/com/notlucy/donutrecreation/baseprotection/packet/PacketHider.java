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
package com.notlucy.donutrecreation.baseprotection.packet;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.Direction;
import com.github.retrooper.packetevents.protocol.world.PaintingType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAddTransientBlock;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCollectItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDamageEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDebugBlockValue;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDebugChunkValue;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDebugEntityValue;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMovement;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerFacePlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerGameTestHighlightPos;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHurtAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMoveMinecart;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerProjectilePower;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnExperienceOrb;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPainting;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnWeatherEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUseBed;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateEntityNBT;
import com.notlucy.donutrecreation.baseprotection.PlacedBlockHider;
import com.notlucy.donutrecreation.baseprotection.RevealManager;
import com.notlucy.donutrecreation.baseprotection.protection.AmethystProtection;
import com.notlucy.donutrecreation.baseprotection.protection.DeepslateProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockChangeHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.BlockEntityDebugProtection;
import com.notlucy.donutrecreation.baseprotection.renderering.ChunkDataHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.ExplosionDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.MultiBlockChangeHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.ParticleDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.SoundDamper;
import com.notlucy.donutrecreation.baseprotection.renderering.UpdateLightHandler;
import com.notlucy.donutrecreation.baseprotection.renderering.WorldEffectDamper;
import com.notlucy.donutrecreation.util.LogData;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class PacketHider {

  private final RevealManager revealManager;
  private final PlacedBlockHider placedBlockHider;
  private final BlockIdRegistry registry;
  private final PacketAuditManager audit;
  private final AtomicInteger failureCount = new AtomicInteger();
  private final Map<UUID, Map<Integer, PendingSpawn>> pendingSpawns = new ConcurrentHashMap<>();
  private final Map<UUID, Set<Integer>> hiddenEntityIds = new ConcurrentHashMap<>();
  private final Map<PacketType.Play.Server, BiConsumer<PacketSendEvent, Player>> handlers =
      new EnumMap<>(PacketType.Play.Server.class);
  private MultiBlockChangeHandler multiBlockChange;
  private final com.notlucy.donutrecreation.baseprotection.protection.OreVisibility oreVisibility;
  private PacketListenerAbstract listener;

  public PacketHider(RevealManager revealManager) {
    this.revealManager = revealManager;
    this.placedBlockHider = new PlacedBlockHider(revealManager.plugin());
    this.registry = new BlockIdRegistry();
    this.audit = new PacketAuditManager(revealManager.plugin());
    revealManager.setChunkRevealCallback(this::resendSpawnsForChunk);
    DeepslateProtection deepslate = new DeepslateProtection(revealManager, registry);
    AmethystProtection amethyst = new AmethystProtection(revealManager, registry);
    BlockEntityDebugProtection blockEntities = new BlockEntityDebugProtection(revealManager);
    this.oreVisibility = new com.notlucy.donutrecreation.baseprotection.protection.OreVisibility(
        revealManager, registry);

    ChunkDataHandler chunkData = new ChunkDataHandler(revealManager, deepslate, amethyst,
        blockEntities, oreVisibility, placedBlockHider, registry);
    UpdateLightHandler updateLight = new UpdateLightHandler(revealManager);
    BlockChangeHandler blockChange = new BlockChangeHandler(revealManager, deepslate, amethyst,
        oreVisibility, placedBlockHider, registry);
    this.multiBlockChange = new MultiBlockChangeHandler(revealManager, deepslate, amethyst,
        oreVisibility, placedBlockHider, registry);
    SoundDamper sounds = new SoundDamper(revealManager);
    ParticleDamper particles = new ParticleDamper(revealManager);
    ExplosionDamper explosions = new ExplosionDamper(revealManager);
    WorldEffectDamper worldEffects = new WorldEffectDamper(revealManager);

    registerHandler(PacketType.Play.Server.CHUNK_DATA, chunkData::handlePacket);
    registerHandler(PacketType.Play.Server.UPDATE_LIGHT, updateLight::handlePacket);
    registerHandler(PacketType.Play.Server.MULTI_BLOCK_CHANGE, multiBlockChange::handlePacket);
    registerHandler(PacketType.Play.Server.BLOCK_CHANGE, blockChange::handlePacket);
    registerHandler(PacketType.Play.Server.BLOCK_ENTITY_DATA, blockEntities::handleBlockEntityData);
    registerHandler(PacketType.Play.Server.BLOCK_ACTION, this::handleBlockAction);
    registerHandler(PacketType.Play.Server.UPDATE_SIGN, this::handleUpdateSign);
    registerHandler(PacketType.Play.Server.OPEN_SIGN_EDITOR, this::handleOpenSignEditor);
    registerHandler(PacketType.Play.Server.UNLOAD_CHUNK, this::handleUnloadChunk);

    registerHandler(PacketType.Play.Server.SPAWN_ENTITY, this::handleSpawnEntity);
    registerHandler(PacketType.Play.Server.SPAWN_LIVING_ENTITY, this::handleSpawnLivingEntity);
    registerHandler(PacketType.Play.Server.SPAWN_PLAYER, this::handleSpawnPlayer);
    registerHandler(PacketType.Play.Server.SPAWN_EXPERIENCE_ORB, this::handleSpawnExperienceOrb);
    registerHandler(PacketType.Play.Server.SPAWN_PAINTING, this::handleSpawnPainting);
    registerHandler(PacketType.Play.Server.SPAWN_WEATHER_ENTITY, this::handleSpawnWeatherEntity);
    registerHandler(PacketType.Play.Server.DESTROY_ENTITIES, this::handleDestroyEntities);

    registerHandler(PacketType.Play.Server.ENTITY_RELATIVE_MOVE, this::handleEntityMove);
    registerHandler(PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION,
        this::handleEntityMove);
    registerHandler(PacketType.Play.Server.ENTITY_TELEPORT, this::handleEntityMove);
    registerHandler(PacketType.Play.Server.ENTITY_POSITION_SYNC, this::handleEntityMove);
    registerHandler(PacketType.Play.Server.ENTITY_MOVEMENT, this::handleEntityMove);
    registerHandler(PacketType.Play.Server.ENTITY_ROTATION, this::handleEntityMove);
    registerHandler(PacketType.Play.Server.ENTITY_HEAD_LOOK, this::handleEntityMove);

    registerHandler(PacketType.Play.Server.ENTITY_METADATA,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerEntityMetadata(event).getEntityId()));
    registerHandler(PacketType.Play.Server.ENTITY_EQUIPMENT,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerEntityEquipment(event).getEntityId()));
    registerHandler(PacketType.Play.Server.ENTITY_ANIMATION,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerEntityAnimation(event).getEntityId()));
    registerHandler(PacketType.Play.Server.ENTITY_STATUS,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerEntityStatus(event).getEntityId()));
    registerHandler(PacketType.Play.Server.ENTITY_VELOCITY,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerEntityVelocity(event).getEntityId()));
    registerHandler(PacketType.Play.Server.ENTITY_EFFECT,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerEntityEffect(event).getEntityId()));
    registerHandler(PacketType.Play.Server.REMOVE_ENTITY_EFFECT,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerRemoveEntityEffect(event).getEntityId()));
    registerHandler(PacketType.Play.Server.HURT_ANIMATION,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerHurtAnimation(event).getEntityId()));
    registerHandler(PacketType.Play.Server.MOVE_MINECART,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerMoveMinecart(event).getEntityId()));
    registerHandler(PacketType.Play.Server.PROJECTILE_POWER,
        (event, player) -> handleEntityPacket(event, player,
            new WrapperPlayServerProjectilePower(event).getEntityId()));
    registerHandler(PacketType.Play.Server.DAMAGE_EVENT, this::handleDamageEvent);
    registerHandler(PacketType.Play.Server.CAMERA, this::handleCamera);
    registerHandler(PacketType.Play.Server.FACE_PLAYER, this::handleFacePlayer);
    registerHandler(PacketType.Play.Server.USE_BED, this::handleUseBed);
    registerHandler(PacketType.Play.Server.WAYPOINT,
        (event, player) -> event.setCancelled(true));
    registerHandler(PacketType.Play.Server.SCULK_VIBRATION_SIGNAL,
        (event, player) -> event.setCancelled(true));
    registerHandler(PacketType.Play.Server.ATTACH_ENTITY, this::handleAttachEntity);
    registerHandler(PacketType.Play.Server.SET_PASSENGERS, this::handleSetPassengers);
    registerHandler(PacketType.Play.Server.COLLECT_ITEM, this::handleCollectItem);
    registerHandler(PacketType.Play.Server.UPDATE_ENTITY_NBT, this::handleUpdateEntityNbt);

    registerHandler(PacketType.Play.Server.ADD_TRANSIENT_BLOCK, this::handleAddTransientBlock);
    registerHandler(PacketType.Play.Server.DEBUG_BLOCK_VALUE, this::handleDebugBlockValue);
    registerHandler(PacketType.Play.Server.DEBUG_CHUNK_VALUE, this::handleDebugChunkValue);
    registerHandler(PacketType.Play.Server.DEBUG_ENTITY_VALUE, this::handleDebugEntityValue);
    registerHandler(PacketType.Play.Server.GAME_TEST_HIGHLIGHT_POS, this::handleGameTestHighlight);

    registerHandler(PacketType.Play.Server.SOUND_EFFECT, sounds::handlePacket);
    registerHandler(PacketType.Play.Server.ENTITY_SOUND_EFFECT, sounds::handlePacket);
    registerHandler(PacketType.Play.Server.PARTICLE, particles::handlePacket);
    registerHandler(PacketType.Play.Server.EXPLOSION, explosions::handlePacket);
    registerHandler(PacketType.Play.Server.EFFECT, worldEffects::handlePacket);
    registerHandler(PacketType.Play.Server.BLOCK_BREAK_ANIMATION, worldEffects::handlePacket);
  }

  private void registerHandler(
      PacketType.Play.Server type, BiConsumer<PacketSendEvent, Player> handler) {
    handlers.put(type, handler);
  }

  public void register() {
    ClientVersion v = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    registry.rebuild(v);
    LogData.get().info("[hider] cached " + registry.amethystCount()
        + " amethyst states (" + v + ")");
    placedBlockHider.load();
    listener = new Listener();
    PacketEvents.getAPI().getEventManager().registerListener(listener);
    oreVisibility.start();
    audit.start();
    Bukkit.getScheduler().runTask(revealManager.plugin(), () -> {
      try {
        for (World world : Bukkit.getWorlds()) {
          for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            placedBlockHider.scanChunk(chunk);
          }
        }
      } catch (Throwable e) {
        LogData.get().warning("[hider] loaded-chunk container scan failed: " + e);
      }
    });
  }

  public void unregister() {
    placedBlockHider.save();
    audit.stop();
    oreVisibility.stop();
    if (listener != null) {
      PacketEvents.getAPI().getEventManager().unregisterListener(listener);
      listener = null;
    }
  }

  public void clearPlayer(UUID playerId) {
    revealManager.clearHiddenEntityCache(playerId);
    pendingSpawns.remove(playerId);
    hiddenEntityIds.remove(playerId);
  }

  public void reload() {
    audit.reload();
    audit.start();
    ClientVersion v = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    registry.rebuild(v);
    LogData.get().info("[hider] reloaded block registry - "
        + registry.amethystCount() + " amethyst, " + registry.oreCount()
        + " ore states (" + v + ")");
  }

  public PacketAuditManager audit() {
    return audit;
  }

  public RevealManager revealManager() {
    return revealManager;
  }

  public PlacedBlockHider placedBlockHider() {
    return placedBlockHider;
  }

  public void setGhostBlockManager(
      com.notlucy.donutrecreation.spawn.manager.GhostBlockManager gbm) {
    this.multiBlockChange.setGhostBlockManager(gbm);
  }

  private static final class PendingSpawn {
    private Vector3d position;
    private int chunkX;
    private int chunkZ;
    private final Consumer<Player> replay;

    PendingSpawn(Vector3d position, Consumer<Player> replay) {
      this.replay = replay;
      moveTo(position);
    }

    void moveTo(Vector3d newPos) {
      this.position = newPos;
      this.chunkX = chunk((int) Math.floor(newPos.getX()));
      this.chunkZ = chunk((int) Math.floor(newPos.getZ()));
    }
  }

  private final class Listener extends PacketListenerAbstract {
    Listener() {
      super(PacketListenerPriority.MONITOR);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
      if (!(event.getPlayer() instanceof Player player)) {
        return;
      }
      try {
        var type = event.getPacketType();
        var handler = handlers.get(type);
        boolean handled = handler != null;
        if (handler != null) {
          handler.accept(event, player);
        }
        if (!event.isCancelled() && audit.shouldBlock(type, handled)) {
          event.setCancelled(true);
        }
        audit.record(type, handled, event.isCancelled());
      } catch (Throwable error) {
        if (failureCount.incrementAndGet() <= 8) {
          LogData.get().warning("[hider] packet handler error ("
              + event.getPacketType() + "): " + error);
        }
      }
    }
  }

  private static int chunk(int blockCoord) {
    return blockCoord >> 4;
  }

  private boolean isHiddenBlock(Player player, int x, int y, int z) {
    return !revealManager.isPositionVisible(player, x, y, z);
  }

  private boolean isHiddenBlock(Player player, double x, double y, double z) {
    return isHiddenBlock(player,
        (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
  }

  private boolean isHiddenEntity(Player player, int entityId) {
    Set<Integer> set = hiddenEntityIds.get(player.getUniqueId());
    return set != null && set.contains(entityId);
  }

  private void hideSpawn(PacketSendEvent event, Player player, int entityId,
      Vector3d position, Consumer<Player> replay) {
    event.setCancelled(true);
    pendingSpawns
        .computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>())
        .put(entityId, new PendingSpawn(position, replay));
    hiddenEntityIds
        .computeIfAbsent(player.getUniqueId(), key -> ConcurrentHashMap.newKeySet())
        .add(entityId);
  }

  private void handleUnloadChunk(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerUnloadChunk(event);
    int chunkX = packet.getChunkX(0);
    int chunkZ = packet.getChunkZ(0);
    revealManager.recordChunkUnloaded(player.getUniqueId(), chunkX, chunkZ);
    Map<Integer, PendingSpawn> pending = pendingSpawns.get(player.getUniqueId());
    if (pending == null || pending.isEmpty()) {
      return;
    }
    Set<Integer> hiddenIds = hiddenEntityIds.get(player.getUniqueId());
    pending.entrySet().removeIf(entry -> {
      PendingSpawn spawn = entry.getValue();
      if (spawn.chunkX != chunkX || spawn.chunkZ != chunkZ) {
        return false;
      }
      if (hiddenIds != null) {
        hiddenIds.remove(entry.getKey());
      }
      return true;
    });
  }

  private boolean isRealPlayerEntity(int entityId) {
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (online.getEntityId() == entityId) {
        return true;
      }
    }
    return false;
  }

  private void handleSpawnEntity(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerSpawnEntity(event);
    var type = packet.getEntityType();
    var position = packet.getPosition();
    if (type == null || position == null) {
      return;
    }
    if (packet.getEntityId() == player.getEntityId()) {
      return;
    }
    if (!isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      return;
    }
    int entityId = packet.getEntityId();
    Optional<UUID> uuid = packet.getUUID();
    float pitch = packet.getPitch();
    float yaw = packet.getYaw();
    float headYaw = packet.getHeadYaw();
    int spawnData = packet.getData();
    Optional<Vector3d> velocity = packet.getVelocity();
    hideSpawn(event, player, entityId, position, viewer ->
        send(viewer, new WrapperPlayServerSpawnEntity(
            entityId, uuid, type, position, pitch, yaw, headYaw, spawnData, velocity)));
  }

  private void handleSpawnLivingEntity(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerSpawnLivingEntity(event);
    var type = packet.getEntityType();
    var position = packet.getPosition();
    if (type == null || position == null) {
      return;
    }
    if (packet.getEntityId() == player.getEntityId()) {
      return;
    }
    if (!isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      return;
    }
    int entityId = packet.getEntityId();
    UUID uuid = packet.getEntityUUID();
    float yaw = packet.getYaw();
    float pitch = packet.getPitch();
    float headPitch = packet.getHeadPitch();
    var velocity = packet.getVelocity();
    var metadata = packet.getEntityMetadata();
    hideSpawn(event, player, entityId, position, viewer ->
        send(viewer, new WrapperPlayServerSpawnLivingEntity(
            entityId, uuid, type, position, yaw, pitch, headPitch, velocity, metadata)));
  }

  private void handleSpawnPlayer(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerSpawnPlayer(event);
    if (packet.getEntityId() == player.getEntityId()) {
      return;
    }
    var position = packet.getPosition();
    if (position == null || !isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      return;
    }
    int entityId = packet.getEntityId();
    UUID uuid = packet.getUUID();
    float yaw = packet.getYaw();
    float pitch = packet.getPitch();
    var metadata = packet.getEntityMetadata();
    hideSpawn(event, player, entityId, position, viewer ->
        send(viewer, new WrapperPlayServerSpawnPlayer(
            entityId, uuid, position, yaw, pitch, metadata)));
  }

  private void handleSpawnExperienceOrb(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerSpawnExperienceOrb(event);
    double x = packet.getX();
    double y = packet.getY();
    double z = packet.getZ();
    if (!isHiddenBlock(player, x, y, z)) {
      return;
    }
    int entityId = packet.getEntityId();
    short count = packet.getCount();
    hideSpawn(event, player, entityId, new Vector3d(x, y, z), viewer ->
        send(viewer, new WrapperPlayServerSpawnExperienceOrb(
            entityId, x, y, z, count)));
  }

  private void handleSpawnPainting(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerSpawnPainting(event);
    Vector3i position = packet.getPosition();
    if (position == null || !isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      return;
    }
    int entityId = packet.getEntityId();
    UUID uuid = packet.getUUID();
    Optional<PaintingType> type = packet.getType();
    Direction direction = packet.getDirection();
    Vector3d spawnPos = position.toVector3d();
    Consumer<Player> replay = type != null && type.isPresent()
        ? viewer -> send(viewer, new WrapperPlayServerSpawnPainting(
            entityId, uuid, type.get(), position, direction))
        : viewer -> send(viewer, new WrapperPlayServerSpawnPainting(
            entityId, uuid, position, direction));
    hideSpawn(event, player, entityId, spawnPos, replay);
  }

  private void handleSpawnWeatherEntity(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerSpawnWeatherEntity(event);
    double x = packet.getX();
    double y = packet.getY();
    double z = packet.getZ();
    if (!isHiddenBlock(player, x, y, z)) {
      return;
    }
    int entityId = packet.getEntityId();
    byte type = packet.getType();
    hideSpawn(event, player, entityId, new Vector3d(x, y, z), viewer ->
        send(viewer, new WrapperPlayServerSpawnWeatherEntity(
            entityId, type, x, y, z)));
  }

  private void handleDestroyEntities(PacketSendEvent event, Player player) {
    int[] entityIds = new WrapperPlayServerDestroyEntities(event).getEntityIds();
    Map<Integer, PendingSpawn> pending = pendingSpawns.get(player.getUniqueId());
    Set<Integer> hiddenIds = hiddenEntityIds.get(player.getUniqueId());
    for (int entityId : entityIds) {
      if (pending != null) {
        pending.remove(entityId);
      }
      if (hiddenIds != null) {
        hiddenIds.remove(entityId);
      }
    }
  }

  private void resendSpawnsForChunk(Player viewer, int chunkX, int chunkZ) {
    Map<Integer, PendingSpawn> pendingByEntity = pendingSpawns.get(viewer.getUniqueId());
    if (pendingByEntity == null || pendingByEntity.isEmpty()) {
      return;
    }
    Set<Integer> hiddenIds = hiddenEntityIds.get(viewer.getUniqueId());
    pendingByEntity.entrySet().removeIf(entry -> {
      PendingSpawn spawn = entry.getValue();
      if (spawn.chunkX != chunkX || spawn.chunkZ != chunkZ) {
        return false;
      }
      /* Do not replay a spawn that is still inside a hidden band. */
      if (isHiddenBlock(viewer, spawn.position.getX(), spawn.position.getY(), spawn.position.getZ())) {
        return false;
      }
      try {
        spawn.replay.accept(viewer);
      } catch (Throwable error) {
        LogData.get().warning("[hider] failed to resend spawn " + entry.getKey()
            + " for " + viewer.getName() + ": " + error);
        return false;
      }
      if (hiddenIds != null) {
        hiddenIds.remove(entry.getKey());
      }
      return true;
    });
  }

  private void handleEntityMove(PacketSendEvent event, Player player) {
    try {
      var type = event.getPacketType();
      int entityId;
      Vector3d nextPosition = null;
      if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
        var packet = new WrapperPlayServerEntityTeleport(event);
        entityId = packet.getEntityId();
        nextPosition = packet.getPosition();
      } else if (type == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
        var packet = new WrapperPlayServerEntityPositionSync(event);
        entityId = packet.getId();
        var values = packet.getValues();
        nextPosition = values == null ? null : values.getPosition();
      } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
        var packet = new WrapperPlayServerEntityRelativeMove(event);
        entityId = packet.getEntityId();
        nextPosition = pendingPosition(player, entityId,
            packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
      } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
        var packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
        entityId = packet.getEntityId();
        nextPosition = pendingPosition(player, entityId,
            packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
      } else if (type == PacketType.Play.Server.ENTITY_ROTATION) {
        entityId = new WrapperPlayServerEntityRotation(event).getEntityId();
      } else if (type == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
        entityId = new WrapperPlayServerEntityHeadLook(event).getEntityId();
      } else if (type == PacketType.Play.Server.ENTITY_MOVEMENT) {
        entityId = new WrapperPlayServerEntityMovement(event).getEntityId();
      } else {
        return;
      }
      if (!isHiddenEntity(player, entityId)) {
        return;
      }
      if (nextPosition != null) {
        updatePendingPosition(player, entityId, nextPosition);
      }
      event.setCancelled(true);
    } catch (Throwable ignored) {
    }
  }

  private Vector3d pendingPosition(
      Player player, int entityId, double offsetX, double offsetY, double offsetZ) {
    Map<Integer, PendingSpawn> pendingByEntity = pendingSpawns.get(player.getUniqueId());
    if (pendingByEntity == null) {
      return null;
    }
    PendingSpawn spawn = pendingByEntity.get(entityId);
    return spawn == null ? null : spawn.position.add(offsetX, offsetY, offsetZ);
  }

  private void updatePendingPosition(Player player, int entityId, Vector3d position) {
    Map<Integer, PendingSpawn> pendingByEntity = pendingSpawns.get(player.getUniqueId());
    if (pendingByEntity == null) {
      return;
    }
    PendingSpawn spawn = pendingByEntity.get(entityId);
    if (spawn != null) {
      spawn.moveTo(position);
    }
  }

  private void handleEntityPacket(PacketSendEvent event, Player player, int entityId) {
    if (isHiddenEntity(player, entityId)) {
      event.setCancelled(true);
    }
  }

  private void handleDamageEvent(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerDamageEvent(event);
    Vector3d sourcePosition = packet.getSourcePosition();
    if (isHiddenEntity(player, packet.getEntityId())
        || isHiddenEntity(player, packet.getSourceCauseId())
        || isHiddenEntity(player, packet.getSourceDirectId())
        || (sourcePosition != null && isHiddenBlock(player,
            sourcePosition.getX(), sourcePosition.getY(), sourcePosition.getZ()))) {
      event.setCancelled(true);
    }
  }

  private void handleCamera(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerCamera(event);
    if (isHiddenEntity(player, packet.getCameraId())) {
      event.setCancelled(true);
    }
  }

  private void handleFacePlayer(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerFacePlayer(event);
    Vector3d targetPosition = packet.getTargetPosition();
    if (targetPosition != null && isHiddenBlock(player,
        targetPosition.getX(), targetPosition.getY(), targetPosition.getZ())) {
      event.setCancelled(true);
      return;
    }
    var targetEntity = packet.getTargetEntity();
    if (targetEntity != null && isHiddenEntity(player, targetEntity.getEntityId())) {
      event.setCancelled(true);
    }
  }

  private void handleUseBed(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerUseBed(event);
    Vector3i position = packet.getPosition();
    if (position != null && isHiddenBlock(player,
        position.getX(), position.getY(), position.getZ())) {
      event.setCancelled(true);
      return;
    }
    if (isHiddenEntity(player, packet.getEntityId())) {
      event.setCancelled(true);
    }
  }

  private void handleAttachEntity(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerAttachEntity(event);
    if (isHiddenEntity(player, packet.getAttachedId())
        || isHiddenEntity(player, packet.getHoldingId())) {
      event.setCancelled(true);
    }
  }

  private void handleSetPassengers(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerSetPassengers(event);
    if (isHiddenEntity(player, packet.getEntityId())) {
      event.setCancelled(true);
      return;
    }
    for (int passenger : packet.getPassengers()) {
      if (isHiddenEntity(player, passenger)) {
        event.setCancelled(true);
        return;
      }
    }
  }

  private void handleCollectItem(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerCollectItem(event);
    if (isHiddenEntity(player, packet.getCollectedEntityId())
        || isHiddenEntity(player, packet.getCollectorEntityId())) {
      event.setCancelled(true);
    }
  }

  private void handleUpdateEntityNbt(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerUpdateEntityNBT(event);
    if (isHiddenEntity(player, packet.getEntityId())) {
      event.setCancelled(true);
    }
  }

  private void handleAddTransientBlock(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerAddTransientBlock(event);
    Vector3i position = packet.getPosition();
    if (position != null && isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      event.setCancelled(true);
    }
  }

  private void handleDebugBlockValue(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerDebugBlockValue(event);
    Vector3i position = packet.getBlockPos();
    if (position != null && isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      event.setCancelled(true);
    }
  }

  private void handleDebugChunkValue(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerDebugChunkValue(event);
    var position = packet.getChunkPos();
    if (position != null && !revealManager.isChunkRevealed(player, position.getX(), position.getZ())) {
      event.setCancelled(true);
    }
  }

  private void handleDebugEntityValue(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerDebugEntityValue(event);
    if (isHiddenEntity(player, packet.getEntityId())) {
      event.setCancelled(true);
    }
  }

  private void handleGameTestHighlight(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerGameTestHighlightPos(event);
    Vector3i position = packet.getAbsolutePos();
    if (position != null && isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      event.setCancelled(true);
    }
  }

  private void handleOpenSignEditor(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerOpenSignEditor(event);
    Vector3i position = packet.getPosition();
    if (position != null && isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      event.setCancelled(true);
    }
  }

  private void handleUpdateSign(PacketSendEvent event, Player player) {
    Object buffer = event.getByteBuf();
    int readerIndex = ByteBufHelper.readerIndex(buffer);
    try {
      long packed = ByteBufHelper.readLong(buffer);
      int x = (int) (packed >> 38);
      int y = (int) ((packed << 26) >> 52);
      int z = (int) ((packed << 38) >> 38);
      if (isHiddenBlock(player, x, y, z)) {
        event.setCancelled(true);
      }
    } catch (Throwable ignored) {
    } finally {
      ByteBufHelper.readerIndex(buffer, readerIndex);
    }
  }

  private void handleBlockAction(PacketSendEvent event, Player player) {
    var packet = new WrapperPlayServerBlockAction(event);
    Vector3i position = packet.getBlockPosition();
    if (position != null && isHiddenBlock(player, position.getX(), position.getY(), position.getZ())) {
      event.setCancelled(true);
    }
  }

  private static void send(Player player, PacketWrapper<?> packet) {
    PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
  }
}
