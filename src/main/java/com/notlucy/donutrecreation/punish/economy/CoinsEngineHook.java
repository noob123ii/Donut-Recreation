package com.notlucy.donutrecreation.punish.economy;

import com.notlucy.donutrecreation.util.LogData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class CoinsEngineHook {

  private static final String API_CLASS = "su.nightexpress.coinsengine.api.CoinsEngineAPI";
  private static boolean resolved;
  private static boolean available;
  private static Method getCurrencies;
  private static Method getCurrency;
  private static Method getBalance;
  private static Method setBalance;
  private static Method currencyId;

  private CoinsEngineHook() {
  }

  private static void resolve() {
    if (resolved) {
      return;
    }
    try {
      Class<?> api = Class.forName(API_CLASS);
      Class<?> currency = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");
      getCurrencies = api.getMethod("getCurrencies");
      getCurrency = api.getMethod("getCurrency", String.class);
      getBalance = api.getMethod("getBalance", Player.class, currency);
      setBalance = api.getMethod("setBalance", Player.class, currency, double.class);
      currencyId = currency.getMethod("getId");
      available = true;
      resolved = true;
      LogData.get().info("[eco] CoinsEngine hook loaded, currencies: " + currencyIds());
    } catch (Throwable e) {
      available = false;
      LogData.get().info("[eco] CoinsEngine not present, skipping economy wipe");
    }
  }

  public static List<Object> currencies() {
    resolve();
    if (!available) {
      return List.of();
    }
    try {
      Object result = getCurrencies.invoke(null);
      if (result instanceof Iterable<?> iterable) {
        List<Object> out = new ArrayList<>();
        for (Object currency : iterable) {
          out.add(currency);
        }
        return out;
      }
    } catch (Throwable ignored) {
    }
    return List.of();
  }

  private static List<String> currencyIds() {
    List<String> out = new ArrayList<>();
    try {
      Object result = getCurrencies.invoke(null);
      if (result instanceof Iterable<?> iterable) {
        for (Object currency : iterable) {
          out.add(idOf(currency));
        }
      }
    } catch (Throwable ignored) {
    }
    return out;
  }

  public static double balanceOf(Player player, Object currency) {
    resolve();
    if (!available || player == null || currency == null) {
      return 0.0;
    }
    try {
      Object value = getBalance.invoke(null, player, currency);
      return value instanceof Number number ? number.doubleValue() : 0.0;
    } catch (Throwable ignored) {
      return 0.0;
    }
  }

  public static void setBalance(Player player, Object currency, double amount) {
    resolve();
    if (!available || player == null || currency == null) {
      return;
    }
    try {
      setBalance.invoke(null, player, currency, amount);
    } catch (Throwable ignored) {
    }
  }

  public static Map<String, Double> snapshot(Player player) {
    Map<String, Double> out = new LinkedHashMap<>();
    for (Object currency : currencies()) {
      out.put(idOf(currency), balanceOf(player, currency));
    }
    return out;
  }

  public static void restore(Player player, Map<String, Double> amounts) {
    if (amounts == null || player == null) {
      return;
    }
    for (Map.Entry<String, Double> entry : amounts.entrySet()) {
      Object currency = findCurrency(entry.getKey());
      if (currency != null) {
        setBalance(player, currency, entry.getValue());
      }
    }
  }

  public static void resetToNewPlayer(Player player, Map<String, Double> defaults) {
    if (player == null) {
      return;
    }
    Map<String, Double> lookup = new LinkedHashMap<>();
    if (defaults != null) {
      for (Map.Entry<String, Double> entry : defaults.entrySet()) {
        if (entry.getKey() != null) {
          lookup.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
      }
    }
    for (Object currency : currencies()) {
      String id = idOf(currency);
      Double amount = lookup.get(id.toLowerCase(Locale.ROOT));
      double value = amount != null ? amount : 0.0;
      setBalance(player, currency, value);
      LogData.get().info("[eco] wipe reset " + player.getName() + ": " + id + " -> " + value);
    }
    for (Map.Entry<String, Double> entry : lookup.entrySet()) {
      if (findCurrency(entry.getKey()) == null) {
        LogData.get().info("[eco] wipe: configured currency '" + entry.getKey()
            + "' not present in CoinsEngine, skipped");
      }
    }
  }

  private static Object findCurrency(String id) {
    if (id == null) {
      return null;
    }
    resolve();
    if (available && getCurrency != null) {
      try {
        return getCurrency.invoke(null, id);
      } catch (Throwable ignored) {
      }
    }
    for (Object currency : currencies()) {
      if (id.equals(idOf(currency))) {
        return currency;
      }
    }
    return null;
  }

  private static String idOf(Object currency) {
    try {
      Object id = currencyId.invoke(currency);
      return id != null ? id.toString() : "";
    } catch (Throwable ignored) {
      return "";
    }
  }
}
