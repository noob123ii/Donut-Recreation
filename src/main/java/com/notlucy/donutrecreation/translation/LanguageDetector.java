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
              player.sendMessage(Component.text("§7[§bLang§7] Detected: §e" + code
                  + " §7(locale: §8" + locale + "§7)")));
        }
      }
    };
  }
}
