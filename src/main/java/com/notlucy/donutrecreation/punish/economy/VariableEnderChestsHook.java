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
package com.notlucy.donutrecreation.punish.economy;

import com.notlucy.donutrecreation.util.LogData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class VariableEnderChestsHook {

  private static final String MAIN_CLASS = "me.saif.betterenderchests.VariableEnderChests";
  private static boolean resolved;
  private static boolean available;
  private static Method getApi;
  private static Method getEnderChest;
  private static Method getContents;
  private static Method setContents;
  private static Method clearContents;

  private VariableEnderChestsHook() {
  }

  private static void resolve() {
    if (resolved) {
      return;
    }
    resolved = true;
    try {
      Class<?> main = Class.forName(MAIN_CLASS);
      Class<?> apiClass = Class.forName("me.saif.betterenderchests.VariableEnderChestAPI");
      Class<?> enderChest = Class.forName("me.saif.betterenderchests.enderchest.EnderChest");
      getApi = main.getMethod("getAPI");
      getEnderChest = apiClass.getMethod("getEnderChest", Player.class);
      getContents = enderChest.getMethod("getContents");
      setContents = enderChest.getMethod("setContents", ItemStack[].class);
      clearContents = enderChest.getMethod("clearContents");
      available = true;
      LogData.get().info("[eco] VariableEnderChests hook loaded");
    } catch (Throwable e) {
      available = false;
      LogData.get().info("[eco] VariableEnderChests not present, skipping custom ender chest");
    }
  }

  public static List<ItemStack> contentsOf(Player player) {
    resolve();
    if (!available || player == null) {
      return List.of();
    }
    try {
      Object chest = getEnderChest.invoke(getApi.invoke(null), player);
      if (chest == null) {
        return List.of();
      }
      ItemStack[] contents = (ItemStack[]) getContents.invoke(chest);
      if (contents == null) {
        return List.of();
      }
      List<ItemStack> out = new ArrayList<>(contents.length);
      for (ItemStack item : contents) {
        out.add(item == null ? null : item.clone());
      }
      return out;
    } catch (Throwable e) {
      LogData.get().warning("[eco] VariableEnderChests read failed: " + e);
      return List.of();
    }
  }

  public static void restore(Player player, List<ItemStack> contents) {
    resolve();
    if (!available || player == null || contents == null) {
      return;
    }
    try {
      Object chest = getEnderChest.invoke(getApi.invoke(null), player);
      if (chest == null) {
        return;
      }
      setContents.invoke(chest, (Object) contents.toArray(new ItemStack[0]));
    } catch (Throwable e) {
      LogData.get().warning("[eco] VariableEnderChests restore failed: " + e);
    }
  }

  public static void clear(Player player) {
    resolve();
    if (!available || player == null) {
      return;
    }
    try {
      Object chest = getEnderChest.invoke(getApi.invoke(null), player);
      if (chest == null) {
        return;
      }
      clearContents.invoke(chest);
    } catch (Throwable e) {
      LogData.get().warning("[eco] VariableEnderChests clear failed: " + e);
    }
  }
}
