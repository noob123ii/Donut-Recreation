package com.notlucy.donutrecreation.settings;

import com.notlucy.donutrecreation.DonutRecreation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class SettingsCommand implements CommandExecutor {

  private static final int GUI_SIZE = 54;

  @SuppressWarnings("unused")
  private final DonutRecreation plugin;

  public SettingsCommand(DonutRecreation plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command,
                           String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Player only.");
      return true;
    }
    openGui(player);
    return true;
  }

  void openGui(Player player) {
    Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
        Component.text("Settings"));

    inv.setItem(0, named(Material.OAK_SIGN, "&aOak Sign"));
    inv.setItem(1, named(Material.SPRUCE_SIGN, "&aSpruce Sign"));
    inv.setItem(2, named(Material.BIRCH_SIGN, "&aBirch Sign"));
    inv.setItem(3, named(Material.JUNGLE_SIGN, "&aJungle Sign"));
    inv.setItem(4, named(Material.ACACIA_SIGN, "&aAcacia Sign"));
    inv.setItem(5, named(Material.DARK_OAK_SIGN, "&aDark Oak Sign"));
    inv.setItem(6, named(Material.MANGROVE_SIGN, "&aMangrove Sign"));
    inv.setItem(7, named(Material.CHERRY_SIGN, "&aCherry Sign"));
    inv.setItem(8, named(Material.BAMBOO_SIGN, "&aBamboo Sign"));

    inv.setItem(9, named(Material.AMETHYST_SHARD, "&dAmethyst Shard"));
    inv.setItem(10, named(Material.ARMOR_STAND, "&7Armor Stand"));
    inv.setItem(11, named(Material.CRAFTING_TABLE, "&6Crafting Table"));
    inv.setItem(12, named(Material.ANVIL, "&7Anvil"));
    inv.setItem(13, named(Material.GOLD_INGOT, "&eGold Ingot"));
    inv.setItem(14, named(Material.FURNACE, "&8Furnace"));
    inv.setItem(15, named(Material.GRASS_BLOCK, "&aGrass Block"));

    inv.setItem(18, named(Material.DIAMOND_SWORD, "&bDiamond Sword"));
    inv.setItem(19, named(Material.ENDER_PEARL, "&5Ender Pearl"));
    inv.setItem(20, named(Material.ENDER_EYE, "&5Eye of Ender"));
    inv.setItem(21, named(Material.EMERALD, "&aEmerald"));
    inv.setItem(22, named(Material.SLIME_BLOCK, "&aSlime Block"));
    inv.setItem(23, named(Material.NOTE_BLOCK, "&6Note Block"));
    inv.setItem(24, named(Material.CHEST, "&6Chest"));
    inv.setItem(25, named(Material.BARRIER, "&cBarrier"));
    inv.setItem(26, named(Material.BARRIER, "&cBarrier"));

    inv.setItem(27, named(Material.COMPASS, "&bCompass"));
    inv.setItem(28, named(Material.CLOCK, "&6Clock"));
    inv.setItem(29, named(Material.BOOK, "&fBook"));
    inv.setItem(30, named(Material.PAPER, "&fPaper"));
    inv.setItem(31, named(Material.NAME_TAG, "&fName Tag"));
    inv.setItem(32, named(Material.BELL, "&6Bell"));
    inv.setItem(33, named(Material.BEACON, "&bBeacon"));
    inv.setItem(34, named(Material.ENCHANTING_TABLE, "&dEnchanting Table"));
    inv.setItem(35, named(Material.JUKEBOX, "&6Jukebox"));

    player.openInventory(inv);
  }

  private static ItemStack named(Material mat, String name) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
      item.setItemMeta(meta);
    }
    return item;
  }
}
