package com.notlucy.donutrecreation.util;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class LogData {

  private static volatile LogData instance;

  private final Logger logger;
  private volatile boolean enabled = true;
  private volatile boolean fineEnabled = false;

  private static final String PREFIX = "[LogData] ";

  public enum LogType {
    INFO(Level.INFO),
    WARNING(Level.WARNING),
    FINE(Level.FINE),
    SEVERE(Level.SEVERE);

    final Level level;

    LogType(Level level) {
      this.level = level;
    }
  }

  private LogData(Logger logger) {
    this.logger = logger;
  }

  public static void init(Plugin plugin) {
    if (instance == null) {
      synchronized (LogData.class) {
        if (instance == null) {
          instance = new LogData(plugin.getLogger());
        }
      }
    }
    instance.reload(plugin);
  }

  public static LogData get() {
    if (instance == null) {
      throw new IllegalStateException("LogData not initialized");
    }
    return instance;
  }

  public void reload(Plugin plugin) {
    this.enabled = plugin.getConfig().getBoolean("logging.enabled", true);
    this.fineEnabled = plugin.getConfig().getBoolean("logging.fine", false);
    logger.setLevel(fineEnabled ? Level.FINE : Level.INFO);
  }

  private boolean shouldLog(LogType type) {
    return enabled && (type != LogType.FINE || fineEnabled);
  }

  public void log(String msg, LogType type) {
    if (!shouldLog(type)) {
      return;
    }
    logger.log(type.level, PREFIX + msg);
  }

  public void log(Supplier<String> msg, LogType type) {
    if (!shouldLog(type)) {
      return;
    }
    logger.log(type.level, PREFIX + msg.get());
  }

  public void info(String msg) {
    log(msg, LogType.INFO);
  }

  public void info(Supplier<String> msg) {
    log(msg, LogType.INFO);
  }

  public void warning(String msg) {
    log(msg, LogType.WARNING);
  }

  public void fine(String msg) {
    log(msg, LogType.FINE);
  }

  public void fine(Supplier<String> msg) {
    log(msg, LogType.FINE);
  }

  public void severe(String msg) {
    log(msg, LogType.SEVERE);
  }
}