package com.notlucy.donutrecreation.translation;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.NBTType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.translation.model.SignedText;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class TranslationListener implements Listener {
  private static final int SIGN_RADIUS_SQ = 100 * 100;

  private final DonutRecreation plugin;
  private final TranslationManager manager;
  private final LanguageManager lang;
  public final PacketListenerAbstract packetListener;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Plugin shared by Bukkit.")
  public TranslationListener(DonutRecreation plugin, TranslationManager manager, LanguageManager lang) {
    this.plugin = plugin;
    this.manager = manager;
    this.lang = lang;
    this.packetListener = new PacketListenerAbstract() {
      @Override
      public void onPacketSend(PacketSendEvent event) {
        TranslationListener.this.onPacketSend(event);
      }
    };
    lang.setOnChange((uuid, langCode) -> {
      Player p = Bukkit.getPlayer(uuid);
      if (p != null && p.isOnline()) refreshSigns(p);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSignChange(SignChangeEvent event) {
    Player player = event.getPlayer();
    Location loc = event.getBlock().getLocation();
    String[] lines = event.getLines();
    SignedText signed = manager.getSignText(loc);
    if (signed == null) {
      signed = new SignedText(lines);
      manager.putSignText(loc, signed);
    }
    String playerLang = lang.getLang(player.getUniqueId());
    if (!"en".equals(playerLang)) {
      String[] playerLines = manager.translateLines(lines, playerLang);
      for (int i = 0; i < Math.min(playerLines.length, lines.length); i++) {
        event.setLine(i, playerLines[i]);
      }
    }
    for (String code : manager.supportedLanguages()) {
      if ("en".equals(code) || code.equals(playerLang)) continue;
      String[] trans = manager.translateLines(lines, code);
      signed.putTranslation(code, trans);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncPlayerChatEvent event) {
    if (event.isCancelled()) return;
    String message = event.getMessage();
    Player sender = event.getPlayer();
    String senderLang = lang.getLang(sender.getUniqueId());
    event.setCancelled(true);
    for (Player recipient : Bukkit.getOnlinePlayers()) {
      String recipientLang = lang.getLang(recipient.getUniqueId());
      String display;
      if (senderLang.equals(recipientLang)) {
        display = message;
      } else {
        display = manager.translate(message, senderLang, recipientLang);
      }
      recipient.sendMessage(Component.text("<" + sender.getName() + "> " + display));
    }
  }

  private void onPacketSend(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.BLOCK_ENTITY_DATA) return;
    if (!(event.getPlayer() instanceof Player viewer)) return;
    WrapperPlayServerBlockEntityData data = new WrapperPlayServerBlockEntityData(event);
    Vector3i pos = data.getPosition();
    Location loc = new Location(viewer.getWorld(), pos.x, pos.y, pos.z);
    if (viewer.getLocation().distanceSquared(loc) > SIGN_RADIUS_SQ) return;
    String viewerLang = lang.getLang(viewer.getUniqueId());
    if ("en".equals(viewerLang)) return;
    NBTCompound nbt = data.getNBT();
    if (nbt == null) return;
    manager.putSignNbt(loc, nbt);
    SignedText signed = manager.getSignText(loc);
    if (signed == null) return;
    String[] translatedLines = signed.getTranslation(viewerLang);
    event.getTasksAfterSend().add(() -> {
      try {
        NBTCompound copy = nbt.copy();
        applySignText(copy, translatedLines);
        sendBlockEntity(viewer, pos, copy, data.getBlockEntityType());
      } catch (Throwable ignored) { }
    });
  }

  public void refreshSigns(Player player) {
    String langCode = lang.getLang(player.getUniqueId());
    if ("en".equals(langCode)) return;
    for (var entry : manager.allSignTexts().entrySet()) {
      Location loc = entry.getKey();
      if (!loc.getWorld().equals(player.getWorld())) continue;
      if (player.getLocation().distanceSquared(loc) > SIGN_RADIUS_SQ) continue;
      String[] trans = entry.getValue().getTranslation(langCode);
      NBTCompound template = manager.getSignNbt(loc);
      if (template != null) {
        NBTCompound copy = template.copy();
        applySignText(copy, trans);
        sendBlockEntity(player,
            new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()),
            copy, signTypeFor(loc));
      } else {
        sendSignTranslation(player, loc, trans);
      }
    }
  }

  private void sendSignTranslation(Player player, Location loc, String[] lines) {
    try {
      BlockEntityType type = signTypeFor(loc);
      if (type == null) return;
      NBTCompound nbt = buildSignNbt(lines);
      WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(
          new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()),
          type, nbt);
      PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    } catch (Throwable ignored) { }
  }

  private void sendBlockEntity(Player player, Vector3i pos, NBTCompound nbt, BlockEntityType type) {
    try {
      WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(pos, type, nbt);
      PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    } catch (Throwable ignored) { }
  }

  private static NBTCompound buildSignNbt(String[] lines) {
    NBTCompound nbt = new NBTCompound();
    NBTCompound frontText = new NBTCompound();
    NBTList<NBTCompound> messages = new NBTList<>(NBTType.COMPOUND, 4);
    for (int i = 0; i < 4; i++) {
      NBTCompound comp = new NBTCompound();
      String text = (i < lines.length && lines[i] != null) ? lines[i] : "";
      comp.setTag("text", new NBTString(text));
      messages.addTag(comp);
    }
    frontText.setTag("messages", messages);
    frontText.setTag("is_waxed", new NBTByte((byte) 0));
    nbt.setTag("front_text", frontText);
    NBTCompound backText = new NBTCompound();
    NBTList<NBTCompound> backMessages = new NBTList<>(NBTType.COMPOUND, 4);
    for (int i = 0; i < 4; i++) {
      NBTCompound comp = new NBTCompound();
      comp.setTag("text", new NBTString(""));
      backMessages.addTag(comp);
    }
    backText.setTag("messages", backMessages);
    backText.setTag("is_waxed", new NBTByte((byte) 0));
    nbt.setTag("back_text", backText);
    return nbt;
  }

  private static BlockEntityType signTypeFor(Location loc) {
    Material mat = loc.getBlock().getType();
    String name = mat.name().toLowerCase(Locale.ROOT);
    if (name.contains("hanging") || name.contains("wall_hanging")) {
      return BlockEntityTypes.HANGING_SIGN;
    }
    return BlockEntityTypes.SIGN;
  }

  private static void applySignText(NBTCompound nbt, String[] lines) {
    NBTCompound frontText = nbt.getCompoundTagOrNull("front_text");
    if (frontText == null) frontText = nbt.getCompoundTagOrNull("FrontText");
    if (frontText == null) return;
    NBTList<NBTString> strMessages = frontText.getStringListTagOrNull("messages");
    if (strMessages == null) strMessages = frontText.getStringListTagOrNull("Messages");
    if (strMessages != null && strMessages.size() >= 4) {
      applyTextToStringList(strMessages, lines);
      return;
    }
    NBTList<NBTCompound> compMessages = frontText.getCompoundListTagOrNull("messages");
    if (compMessages == null) compMessages = frontText.getCompoundListTagOrNull("Messages");
    if (compMessages != null && compMessages.size() >= 4) {
      applyTextToComponentList(compMessages, lines);
      return;
    }
  }

  private static void applyTextToStringList(NBTList<NBTString> messages, String[] lines) {
    for (int i = 0; i < 4 && i < lines.length; i++) {
      messages.setTag(i, new NBTString(lines[i] != null ? lines[i] : ""));
    }
  }

  private static void applyTextToComponentList(NBTList<NBTCompound> messages, String[] lines) {
    for (int i = 0; i < 4 && i < lines.length; i++) {
      NBTCompound comp = new NBTCompound();
      comp.setTag("text", new NBTString(lines[i] != null ? lines[i] : ""));
      messages.setTag(i, comp);
    }
  }
}