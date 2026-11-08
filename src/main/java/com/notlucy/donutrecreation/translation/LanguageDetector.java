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
package com.notlucy.donutrecreation.translation;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class LanguageDetector {
  public final PacketListenerAbstract packetListener;
  private final LanguageManager lang;
  private final Plugin plugin;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Manager shared by design.")
  public LanguageDetector(LanguageManager lang, Plugin plugin) {
    this.lang = lang;
    this.plugin = plugin;
    this.packetListener = new PacketListenerAbstract() {
      @Override
      public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLIENT_SETTINGS) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        WrapperPlayClientSettings wrapper = new WrapperPlayClientSettings(event);
        String locale = wrapper.getLocale();
        if (locale == null || locale.isEmpty()) return;
        String code = locale.toLowerCase(Locale.ROOT);
        if (!code.equals(lang.getLang(player.getUniqueId()))) {
          lang.setLang(player.getUniqueId(), code);
          Bukkit.getLogger().info("[LanguageDetector] " + player.getName()
              + " language changed to '" + code + "' (locale: " + locale + ")");
          Bukkit.getScheduler().runTask(plugin, () ->
              player.sendMessage(Component.text("Â§7[Â§bLangÂ§7] Detected: Â§e" + code
                  + " Â§7(locale: Â§8" + locale + "Â§7)")));
        }
      }
    };
  }
}
