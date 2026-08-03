package com.notlucy.donutrecreation.baseprotection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class OreCacheStorage {
  private final Path baseDir;
  private final ExecutorService io;
  private final Map<UUID, WorldCache> worldCaches;
  private static final long MAX_FILE_SIZE = 1_073_741_824L;
  private static final String FILE_PREFIX = "orecache_";
  private static final String FILE_SUFFIX = ".dat";

  public OreCacheStorage(Path dataFolder) {
    this.baseDir = dataFolder.resolve("AntiXray");
    this.io = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "OreCacheStorage-IO");
      t.setDaemon(true);
      return t;
    });
    this.worldCaches = new ConcurrentHashMap<>();
    ensureDirectories();
  }

  private void ensureDirectories() {
    try {
      Files.createDirectories(baseDir);
      Files.createDirectories(baseDir.resolve("Overworld"));
      Files.createDirectories(baseDir.resolve("Nether"));
    } catch (IOException e) {
      Bukkit.getLogger().log(Level.WARNING, "Failed to create AntiXray directories", e);
    }
  }

  private Path getWorldDir(UUID worldUid) {
    World world = Bukkit.getWorld(worldUid);
    if (world != null) {
      String envName = switch (world.getEnvironment()) {
        case NORMAL -> "Overworld";
        case NETHER -> "Nether";
        case THE_END -> "End";
        default -> world.getEnvironment().name();
      };
      return baseDir.resolve(envName);
    }
    return baseDir.resolve("Other");
  }

  public void saveChunkOres(UUID worldUid, int chunkX, int chunkZ, Set<Long> orePositions, long worldSeed) {
    io.execute(() -> {
      try {
        WorldCache cache = getWorldCache(worldUid);
        cache.saveChunk(chunkX, chunkZ, orePositions, worldSeed);
      } catch (Exception e) {
        Bukkit.getLogger().log(Level.WARNING, "Failed to save ore cache for chunk " + chunkX + "," + chunkZ, e);
      }
    });
  }

  public CompletableFuture<ChunkData> loadChunkOres(UUID worldUid, int chunkX, int chunkZ) {
    CompletableFuture<ChunkData> future = new CompletableFuture<>();
    io.execute(() -> {
      try {
        ChunkData data = getWorldCache(worldUid).loadChunk(chunkX, chunkZ);
        future.complete(data);
      } catch (Exception e) {
        Bukkit.getLogger().log(Level.WARNING, "Failed to load ore cache for chunk " + chunkX + "," + chunkZ, e);
        future.complete(new ChunkData(Collections.emptySet(), -1));
      }
    });
    return future;
  }

  public static class ChunkData {
    public final Set<Long> orePositions;
    public final long worldSeed;

    public ChunkData(Set<Long> orePositions, long worldSeed) {
      this.orePositions = orePositions;
      this.worldSeed = worldSeed;
    }
  }

  public void removeChunkOres(UUID worldUid, int chunkX, int chunkZ) {
    io.execute(() -> {
      try {
        WorldCache cache = getWorldCache(worldUid);
        cache.removeChunk(chunkX, chunkZ);
      } catch (Exception e) {
        Bukkit.getLogger().log(Level.WARNING, "Failed to remove ore cache for chunk " + chunkX + "," + chunkZ, e);
      }
    });
  }

  public void clearWorld(UUID worldUid) {
    io.execute(() -> {
      try {
        WorldCache cache = getWorldCache(worldUid);
        cache.clearAll();
        worldCaches.remove(worldUid);
      } catch (Exception e) {
        Bukkit.getLogger().log(Level.WARNING, "Failed to clear ore cache for world " + worldUid, e);
      }
    });
  }

  public void clearMemoryCache() {
    for (WorldCache cache : worldCaches.values()) {
      cache.clearMemory();
    }
  }

  private WorldCache getWorldCache(UUID worldUid) {
    return worldCaches.computeIfAbsent(worldUid, uid -> new WorldCache(getWorldDir(uid)));
  }

  public void shutdown() {
    io.shutdown();
    try {
      if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
        io.shutdownNow();
      }
    } catch (InterruptedException e) {
      io.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private static class WorldCache {
    private final Path worldDir;
    private final Map<Long, Set<Long>> memCache;
    private final Map<Path, FileHandle> handles;
    private int fileIdx;

    WorldCache(Path worldDir) {
      this.worldDir = worldDir;
      this.memCache = new ConcurrentHashMap<>();
      this.handles = new ConcurrentHashMap<>();
      this.fileIdx = highestIndex();
    }

    private int highestIndex() {
      try (var stream = Files.list(worldDir)) {
        return stream
            .filter(p -> p.getFileName().toString().startsWith(FILE_PREFIX))
            .filter(p -> p.getFileName().toString().endsWith(FILE_SUFFIX))
            .mapToInt(p -> {
              String name = p.getFileName().toString();
              String numStr = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
              try {
                return Integer.parseInt(numStr);
              } catch (NumberFormatException e) {
                return 0;
              }
            })
            .max()
            .orElse(0);
      } catch (IOException e) {
        return 0;
      }
    }

    private long chunkKey(int chunkX, int chunkZ) {
      return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private Path curPath() {
      return worldDir.resolve(FILE_PREFIX + fileIdx + FILE_SUFFIX);
    }

    private Path nextPath() {
      fileIdx++;
      return worldDir.resolve(FILE_PREFIX + fileIdx + FILE_SUFFIX);
    }

    private FileHandle curHandle() throws IOException {
      Path currentPath = curPath();
      FileHandle handle = handles.get(currentPath);
      if (handle == null || handle.isFull()) {
        handle = new FileHandle(nextPath());
        handles.put(handle.getPath(), handle);
      }
      return handle;
    }

    void saveChunk(int chunkX, int chunkZ, Set<Long> orePositions, long worldSeed) throws IOException {
      long key = chunkKey(chunkX, chunkZ);
      memCache.put(key, new HashSet<>(orePositions));

      FileHandle handle = curHandle();
      handle.writeChunk(key, orePositions, worldSeed);
    }

    ChunkData loadChunk(int chunkX, int chunkZ) throws IOException {
      long key = chunkKey(chunkX, chunkZ);

      Set<Long> cached = memCache.get(key);
      if (cached != null) {
        return new ChunkData(new HashSet<>(cached), 0);
      }

      try (var stream = Files.list(worldDir)) {
        for (Path file : stream.toList()) {
          if (file.getFileName().toString().startsWith(FILE_PREFIX)
              && file.getFileName().toString().endsWith(FILE_SUFFIX)) {
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
              while (dis.available() > 0) {
                long fileKey = dis.readLong();
                int count = dis.readInt();
                long storedSeed = dis.readLong();
                if (fileKey == key) {
                  Set<Long> ores = new HashSet<>(count);
                  for (int i = 0; i < count; i++) {
                    ores.add(dis.readLong());
                  }
                  memCache.put(key, ores);
                  return new ChunkData(ores, storedSeed);
                } else {
                  for (int i = 0; i < count; i++) {
                    dis.readLong();
                  }
                }
              }
            }
          }
        }
      }
      return new ChunkData(Collections.emptySet(), -1);
    }

    void removeChunk(int chunkX, int chunkZ) throws IOException {
      long key = chunkKey(chunkX, chunkZ);
      memCache.remove(key);
    }

    void clearAll() throws IOException {
      memCache.clear();
      for (FileHandle handle : handles.values()) {
        handle.close();
      }
      handles.clear();

      try (var stream = Files.list(worldDir)) {
        for (Path file : stream.toList()) {
          if (file.getFileName().toString().startsWith(FILE_PREFIX)
              && file.getFileName().toString().endsWith(FILE_SUFFIX)) {
            Files.delete(file);
          }
        }
      }
      fileIdx = 0;
    }

    void clearMemory() {
      memCache.clear();
    }
  }

  private static class FileHandle implements Closeable {
    private final Path path;
    private DataOutputStream dos;
    private long bytesWritten;

    FileHandle(Path path) throws IOException {
      this.path = path;
      this.dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)));
      this.bytesWritten = Files.exists(path) ? Files.size(path) : 0;
    }

    Path getPath() {
      return path;
    }

    boolean isFull() {
      return bytesWritten >= MAX_FILE_SIZE;
    }

    void writeChunk(long chunkKey, Set<Long> orePositions, long worldSeed) throws IOException {
      dos.writeLong(chunkKey);
      dos.writeInt(orePositions.size());
      dos.writeLong(worldSeed);
      for (long pos : orePositions) {
        dos.writeLong(pos);
      }
      dos.flush();
      bytesWritten += 8 + 4 + 8 + (orePositions.size() * 8L);
    }

    @Override
    public void close() throws IOException {
      if (dos != null) {
        dos.close();
        dos = null;
      }
    }
  }
}
