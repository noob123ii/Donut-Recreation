package com.crimsonwarpedcraft.donutpluginrecreation.sus.commands;

import com.crimsonwarpedcraft.donutpluginrecreation.DonutPluginRecreation;
import com.crimsonwarpedcraft.donutpluginrecreation.sus.model.SusFlag;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * {@code /sus} — opens the suspicion review GUI, or manually flags a player so they appear
 * in the GUI for all reviewing operators. Flags are throttled by a per-player cooldown.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class SusCommand implements CommandExecutor, Listener {
  private static final int GUI_SIZE = 54;
  private static final int HEADS_PER_PAGE = 45;
  private static final int PREV_SLOT = 45;
  private static final int REFRESH_SLOT = 49;
  private static final int NEXT_SLOT = 53;

  private final DonutPluginRecreation plugin;
  private final NamespacedKey targetKey;
  private final NamespacedKey refreshKey;
  private final NamespacedKey prevKey;
  private final NamespacedKey nextKey;

  private final ConcurrentMap<UUID, Integer> viewerPage = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Long> lastSusUseAt = new ConcurrentHashMap<>();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Plugin instance is shared by Bukkit.")
  public SusCommand(DonutPluginRecreation plugin) {
    this.plugin = plugin;
    this.targetKey = new NamespacedKey(plugin, "sus_target");
    this.refreshKey = new NamespacedKey(plugin, "sus_refresh");
    this.prevKey = new NamespacedKey(plugin, "sus_prev");
    this.nextKey = new NamespacedKey(plugin, "sus_next");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.isOp()) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }
    if (!(sender instanceof Player reporter)) {
      sender.sendMessage(plugin.message("messages.player-only"));
      return true;
    }
    if (args.length == 0) {
      openGui(reporter, 0);
      return true;
    }

    long cooldownMs = Math.max(0L, plugin.getConfig().getLong("sus.cooldown-ms", 3000L));
    if (cooldownMs > 0) {
      long now = System.currentTimeMillis();
      Long previous = lastSusUseAt.get(reporter.getUniqueId());
      if (previous != null && now - previous < cooldownMs) {
        long secondsLeft = (cooldownMs - (now - previous) + 999L) / 1000L;
        reporter.sendMessage(plugin.color("&cSlow down — try again in " + secondsLeft + "s."));
        return true;
      }
      lastSusUseAt.put(reporter.getUniqueId(), now);
    }

    Player target = Bukkit.getPlayerExact(args[0]);
    if (target == null) {
      reporter.sendMessage(plugin.message("messages.player-not-found"));
      return true;
    }
    String reason = args.length >= 2
        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
        : "Manual /sus by " + reporter.getName();
    plugin.susFlagManager().flag(target, reason);
    reporter.sendMessage(plugin.color(
        plugin.message("messages.sus-sent").replace("%target%", target.getName())));
    String alert = plugin.message("messages.sus-alert")
        .replace("%reporter%", reporter.getName())
        .replace("%target%", target.getName())
        .replace("%reason%", reason);
    Bukkit.getOnlinePlayers().stream()
        .filter(CommandSender::isOp)
        .forEach(op -> op.sendMessage(alert));
    return true;
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player reporter)) {
      return;
    }
    if (!isSusView(event.getView().getTitle())) {
      return;
    }

    event.setCancelled(true);
    ItemStack item = event.getCurrentItem();
    if (item == null || !item.hasItemMeta()) {
      return;
    }

    ItemMeta meta = item.getItemMeta();
    var pdc = meta.getPersistentDataContainer();
    if (pdc.has(refreshKey, PersistentDataType.BYTE)) {
      openGui(reporter, viewerPage.getOrDefault(reporter.getUniqueId(), 0));
      playAlert(reporter);
      return;
    }
    if (pdc.has(prevKey, PersistentDataType.BYTE)) {
      int currentPage = viewerPage.getOrDefault(reporter.getUniqueId(), 0);
      openGui(reporter, Math.max(0, currentPage - 1));
      return;
    }
    if (pdc.has(nextKey, PersistentDataType.BYTE)) {
      int currentPage = viewerPage.getOrDefault(reporter.getUniqueId(), 0);
      openGui(reporter, currentPage + 1);
      return;
    }

    String targetUuid = pdc.get(targetKey, PersistentDataType.STRING);
    if (targetUuid == null) {
      return;
    }

    Player target;
    try {
      target = Bukkit.getPlayer(UUID.fromString(targetUuid));
    } catch (IllegalArgumentException invalidUuid) {
      return;
    }
    if (target == null) {
      reporter.sendMessage(plugin.message("messages.player-not-found"));
      return;
    }

    reporter.closeInventory();
    reporter.setGameMode(GameMode.SPECTATOR);
    reporter.teleport(target.getLocation());
    reporter.setSpectatorTarget(target);
    plugin.susFlagManager().clear(target.getUniqueId());
    reporter.sendMessage(plugin.color("&aNow spectating &f" + target.getName() + "&a."));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (isSusView(event.getView().getTitle())) {
      event.setCancelled(true);
    }
  }

  private void openGui(Player reporter, int page) {
    List<SusFlag> all = plugin.susFlagManager().queuedFlags();
    int total = all.size();
    int pageCount = Math.max(1, (total + HEADS_PER_PAGE - 1) / HEADS_PER_PAGE);
    int boundedPage = Math.max(0, Math.min(page, pageCount - 1));
    viewerPage.put(reporter.getUniqueId(), boundedPage);

    Inventory inventory = Bukkit.createInventory(
        null, GUI_SIZE, guiTitle(total, boundedPage, pageCount));
    int start = boundedPage * HEADS_PER_PAGE;
    int end = Math.min(start + HEADS_PER_PAGE, total);
    for (int i = start; i < end; i++) {
      inventory.setItem(i - start, createHead(all.get(i)));
    }
    if (boundedPage > 0) {
      inventory.setItem(PREV_SLOT, createNavItem(
          Material.ARROW, "&aPrevious page",
          "&7Page " + boundedPage + " / " + pageCount, prevKey));
    }
    inventory.setItem(REFRESH_SLOT, createRefreshItem(total));
    if (boundedPage < pageCount - 1) {
      inventory.setItem(NEXT_SLOT, createNavItem(
          Material.ARROW, "&aNext page",
          "&7Page " + (boundedPage + 2) + " / " + pageCount, nextKey));
    }
    reporter.openInventory(inventory);
  }

  private boolean isSusView(String viewTitle) {
    if (viewTitle == null) {
      return false;
    }
    String stripped = ChatColor.stripColor(viewTitle);
    return stripped != null && stripped.startsWith("Sus ");
  }

  private ItemStack createHead(SusFlag flag) {
    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
    SkullMeta meta = (SkullMeta) item.getItemMeta();
    Player onlineTarget = Bukkit.getPlayer(flag.targetId());
    if (onlineTarget != null) {
      meta.setOwningPlayer(onlineTarget);
    }
    meta.setDisplayName(plugin.color("&e" + flag.targetName() + "'s Head"));
    meta.setLore(Arrays.asList(
        plugin.color("&dFlags: &f" + flag.count()),
        plugin.color("&dLast flag: &f" + flag.reason()),
        plugin.color("&dLeft click: &fSpectate player"),
        plugin.color("&dClicking clears this queued sus record")));
    meta.getPersistentDataContainer().set(
        targetKey,
        PersistentDataType.STRING,
        flag.targetId().toString());
    if (!item.setItemMeta(meta)) {
      throw new IllegalStateException("Could not apply player head meta");
    }
    return item;
  }

  private ItemStack createRefreshItem(int total) {
    ItemStack item = new ItemStack(Material.NETHER_STAR);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(plugin.color("&eRefresh queued flags"));
    meta.setLore(Arrays.asList(
        plugin.color("&7Click to reload the newest anti-freecam flags."),
        plugin.color("&7Queued: &f" + total)));
    meta.getPersistentDataContainer().set(refreshKey, PersistentDataType.BYTE, (byte) 1);
    if (!item.setItemMeta(meta)) {
      throw new IllegalStateException("Could not apply refresh item meta");
    }
    return item;
  }

  private ItemStack createNavItem(Material material, String name, String lore, NamespacedKey key) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(plugin.color(name));
    meta.setLore(Collections.singletonList(plugin.color(lore)));
    meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    if (!item.setItemMeta(meta)) {
      throw new IllegalStateException("Could not apply nav item meta");
    }
    return item;
  }

  private String guiTitle(int total, int page, int pageCount) {
    String base = "Sus (" + total + ")";
    if (pageCount > 1) {
      base = base + " " + (page + 1) + "/" + pageCount;
    }
    return plugin.color("&8" + base);
  }

  private void playAlert(Player player) {
    String soundName = plugin.getConfig().getString("sus.alert-sound");
    Sound sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    if (soundName != null) {
      try {
        sound = Sound.valueOf(soundName);
      } catch (IllegalArgumentException ignored) {
        sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
      }
    }
    player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
  }
}
