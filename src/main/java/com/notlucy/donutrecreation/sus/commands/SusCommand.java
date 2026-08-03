package com.notlucy.donutrecreation.sus.commands;

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.sus.model.SusFlag;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class SusCommand implements CommandExecutor, Listener {
  private static final int GUI_SIZE = 54;
  private static final int HEADS_PER_PAGE = 45;
  private static final int PREV_SLOT = 45;
  private static final int BACK_SLOT = 48;
  private static final int REFRESH_SLOT = 49;
  private static final int NEXT_SLOT = 53;
  private static final String CATEGORY_CUSTOM = "custom";
  private static final List<String> ANTI_CHEAT_PLUGINS = List.of(
      "Matrix", "Vulcan", "GrimAC", "Spartan", "AntiAura", "Negativity", "Themis", "Kauri", "Warden");

  private final DonutRecreation plugin;
  private final NamespacedKey catKey;
  private final NamespacedKey targetKey;
  private final NamespacedKey refreshKey;
  private final NamespacedKey backKey;
  private final NamespacedKey prevKey;
  private final NamespacedKey nextKey;

  private final ConcurrentMap<UUID, String> viewCat = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Integer> viewPage = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Long> lastUse = new ConcurrentHashMap<>();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Plugin instance is shared by Bukkit.")
  public SusCommand(DonutRecreation plugin) {
    this.plugin = plugin;
    this.catKey = new NamespacedKey(plugin, "ac_category");
    this.targetKey = new NamespacedKey(plugin, "ac_target");
    this.refreshKey = new NamespacedKey(plugin, "ac_refresh");
    this.backKey = new NamespacedKey(plugin, "ac_back");
    this.prevKey = new NamespacedKey(plugin, "ac_prev");
    this.nextKey = new NamespacedKey(plugin, "ac_next");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.hasPermission("donutrecreation.*")) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }
    if (!(sender instanceof Player reporter)) {
      sender.sendMessage(plugin.message("messages.player-only"));
      return true;
    }
    if (args.length == 0) {
      openCategories(reporter);
      return true;
    }

    long cooldownMs = Math.max(0L, plugin.getConfig().getLong("sus.cooldown-ms", 3000L));
    if (cooldownMs > 0) {
      long now = System.currentTimeMillis();
      Long prev = lastUse.get(reporter.getUniqueId());
      if (prev != null && now - prev < cooldownMs) {
        long secondsLeft = (cooldownMs - (now - prev) + 999L) / 1000L;
        reporter.sendMessage(plugin.color("&cSlow down \u2014 try again in " + secondsLeft + "s."));
        return true;
      }
      lastUse.put(reporter.getUniqueId(), now);
    }

    Player target = Bukkit.getPlayerExact(args[0]);
    if (target == null) {
      reporter.sendMessage(plugin.message("messages.player-not-found"));
      return true;
    }
    String reason = args.length >= 2
        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
        : "Manual /ACSus by " + reporter.getName();
    plugin.susFlagManager().flag(target, reason, CATEGORY_CUSTOM);
    reporter.sendMessage(plugin.color(
        plugin.message("messages.sus-sent").replace("%target%", target.getName())));
    String alert = plugin.message("messages.sus-alert")
        .replace("%reporter%", reporter.getName())
        .replace("%target%", target.getName())
        .replace("%reason%", reason);
    Bukkit.getOnlinePlayers().stream()
        .filter(p -> p.hasPermission("donutrecreation.*"))
        .forEach(op -> op.sendMessage(alert));
    return true;
  }

  private List<String> categories() {
    List<String> cats = new ArrayList<>();
    cats.add(CATEGORY_CUSTOM);
    for (String name : ANTI_CHEAT_PLUGINS) {
      if (Bukkit.getPluginManager().getPlugin(name) != null) {
        cats.add(name.toLowerCase(Locale.ROOT));
      }
    }
    return cats;
  }

  private String catName(String cat) {
    return switch (cat) {
      case CATEGORY_CUSTOM -> "Custom";
      case "matrix" -> "Matrix";
      case "vulcan" -> "Vulcan";
      case "grimac" -> "GrimAC";
      case "spartan" -> "Spartan";
      case "antiaura" -> "AntiAura";
      case "negativity" -> "Negativity";
      case "themis" -> "Themis";
      case "kauri" -> "Kauri";
      case "warden" -> "Warden";
      default -> cat.substring(0, 1).toUpperCase(Locale.ROOT) + cat.substring(1);
    };
  }

  private Material catIcon(String cat) {
    return switch (cat) {
      case CATEGORY_CUSTOM -> Material.BOOK;
      case "matrix" -> Material.REDSTONE;
      case "vulcan" -> Material.FIRE_CHARGE;
      case "grimac" -> Material.WITHER_SKELETON_SKULL;
      case "spartan" -> Material.SHIELD;
      case "antiaura" -> Material.ENDER_EYE;
      case "negativity" -> Material.BARRIER;
      case "themis" -> Material.TURTLE_SCUTE;
      case "kauri" -> Material.NAUTILUS_SHELL;
      case "warden" -> Material.ECHO_SHARD;
      default -> Material.COMPASS;
    };
  }

  private List<String> catLore(String cat) {
    int count = plugin.susFlagManager().countByCategory(cat);
    List<String> lore = new ArrayList<>();
    lore.add(plugin.color("&7Queued flags: &f" + count));
    if (CATEGORY_CUSTOM.equals(cat)) {
      lore.add(plugin.color("&7Macro, base-finding, elytra, manual /acsus"));
    } else {
      lore.add(plugin.color("&7Flags from &f" + catName(cat)));
    }
    lore.add(plugin.color("&eClick to view"));
    return lore;
  }

  private void openCategories(Player viewer) {
    viewPage.remove(viewer.getUniqueId());
    viewCat.remove(viewer.getUniqueId());

    Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
        plugin.color("&8Anti Cheat Sus"));

    List<String> cats = categories();
    int slot = 0;
    for (String cat : cats) {
      if (slot >= GUI_SIZE) break;
      inv.setItem(slot, catItem(cat));
      slot += 2;
      if (slot % 9 == 0) slot++;
    }

    inv.setItem(REFRESH_SLOT, refreshItem());
    viewer.openInventory(inv);
  }

  private ItemStack catItem(String cat) {
    ItemStack item = new ItemStack(catIcon(cat));
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(plugin.color("&6&l" + catName(cat)));
    meta.setLore(catLore(cat));
    meta.getPersistentDataContainer().set(catKey, PersistentDataType.STRING, cat);
    item.setItemMeta(meta);
    return item;
  }

  private void openFlags(Player viewer, String cat, int page) {
    List<SusFlag> flags = plugin.susFlagManager().queuedFlags(cat);
    int total = flags.size();
    int pages = Math.max(1, (total + HEADS_PER_PAGE - 1) / HEADS_PER_PAGE);
    int current = Math.max(0, Math.min(page, pages - 1));
    viewCat.put(viewer.getUniqueId(), cat);
    viewPage.put(viewer.getUniqueId(), current);

    Inventory inv = Bukkit.createInventory(
        null, GUI_SIZE, listTitle(cat, total, current, pages));
    int start = current * HEADS_PER_PAGE;
    int end = Math.min(start + HEADS_PER_PAGE, total);
    for (int i = start; i < end; i++) {
      inv.setItem(i - start, headItem(flags.get(i)));
    }
    inv.setItem(BACK_SLOT, navItem(
        Material.ARROW, "&aBack to categories", "&7Return to Anti Cheat Sus", backKey));
    if (current > 0) {
      inv.setItem(PREV_SLOT, navItem(
          Material.ARROW, "&aPrevious page",
          "&7Page " + current + " / " + pages, prevKey));
    }
    inv.setItem(REFRESH_SLOT, refreshItem());
    if (current < pages - 1) {
      inv.setItem(NEXT_SLOT, navItem(
          Material.ARROW, "&aNext page",
          "&7Page " + (current + 2) + " / " + pages, nextKey));
    }
    viewer.openInventory(inv);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player viewer)) return;
    String title = ChatColor.stripColor(event.getView().getTitle());
    if (title == null) return;

    boolean isCatView = "Anti Cheat Sus".equals(title);
    boolean isListView = title.startsWith("Anti Cheat Sus - ");

    if (!isCatView && !isListView) return;

    event.setCancelled(true);
    ItemStack item = event.getCurrentItem();
    if (item == null || !item.hasItemMeta()) return;

    ItemMeta meta = item.getItemMeta();
    var pdc = meta.getPersistentDataContainer();

    if (pdc.has(refreshKey, PersistentDataType.BYTE)) {
      if (isCatView) {
        openCategories(viewer);
      } else {
        String cat = viewCat.getOrDefault(viewer.getUniqueId(), CATEGORY_CUSTOM);
        openFlags(viewer, cat, viewPage.getOrDefault(viewer.getUniqueId(), 0));
      }
      playAlert(viewer);
      return;
    }

    if (pdc.has(backKey, PersistentDataType.BYTE)) {
      openCategories(viewer);
      return;
    }

    if (pdc.has(prevKey, PersistentDataType.BYTE)) {
      String cat = viewCat.getOrDefault(viewer.getUniqueId(), CATEGORY_CUSTOM);
      int page = viewPage.getOrDefault(viewer.getUniqueId(), 0);
      openFlags(viewer, cat, Math.max(0, page - 1));
      return;
    }

    if (pdc.has(nextKey, PersistentDataType.BYTE)) {
      String cat = viewCat.getOrDefault(viewer.getUniqueId(), CATEGORY_CUSTOM);
      int page = viewPage.getOrDefault(viewer.getUniqueId(), 0);
      openFlags(viewer, cat, page + 1);
      return;
    }

    if (isCatView) {
      String cat = pdc.get(catKey, PersistentDataType.STRING);
      if (cat != null) {
        openFlags(viewer, cat, 0);
        return;
      }
    }

    if (isListView) {
      String targetUuid = pdc.get(targetKey, PersistentDataType.STRING);
      if (targetUuid == null) return;

      Player target;
      try {
        target = Bukkit.getPlayer(UUID.fromString(targetUuid));
      } catch (IllegalArgumentException e) {
        return;
      }
      if (target == null) {
        viewer.sendMessage(plugin.message("messages.player-not-found"));
        return;
      }

      viewer.closeInventory();
      viewer.setGameMode(GameMode.SPECTATOR);
      viewer.teleport(target.getLocation());
      viewer.setSpectatorTarget(target);
      plugin.susFlagManager().clear(target.getUniqueId());
      viewer.sendMessage(plugin.color("&aNow spectating &f" + target.getName() + "&a."));
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryDrag(InventoryDragEvent event) {
    String title = ChatColor.stripColor(event.getView().getTitle());
    if (title != null && (title.equals("Anti Cheat Sus") || title.startsWith("Anti Cheat Sus - "))) {
      event.setCancelled(true);
    }
  }

  private ItemStack headItem(SusFlag flag) {
    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
    SkullMeta meta = (SkullMeta) item.getItemMeta();
    Player target = Bukkit.getPlayer(flag.targetId());
    if (target != null) {
      meta.setOwningPlayer(target);
    }
    meta.setDisplayName(plugin.color("&e" + flag.targetName() + "'s Head"));
    meta.setLore(Arrays.asList(
        plugin.color("&dFlags: &f" + flag.count()),
        plugin.color("&dLast flag: &f" + flag.reason()),
        plugin.color("&dCategory: &f" + catName(flag.category())),
        plugin.color("&dLeft click: &fSpectate player"),
        plugin.color("&dClicking clears this queued record")));
    meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, flag.targetId().toString());
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack refreshItem() {
    ItemStack item = new ItemStack(Material.NETHER_STAR);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(plugin.color("&eRefresh"));
    meta.setLore(Collections.singletonList(plugin.color("&7Click to reload flags.")));
    meta.getPersistentDataContainer().set(refreshKey, PersistentDataType.BYTE, (byte) 1);
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack navItem(Material material, String name, String lore, NamespacedKey key) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(plugin.color(name));
    meta.setLore(Collections.singletonList(plugin.color(lore)));
    meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    item.setItemMeta(meta);
    return item;
  }

  private String listTitle(String cat, int total, int page, int pages) {
    String title = "Anti Cheat Sus - " + catName(cat) + " (" + total + ")";
    if (pages > 1) {
      title = title + " " + (page + 1) + "/" + pages;
    }
    return plugin.color("&8" + title);
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