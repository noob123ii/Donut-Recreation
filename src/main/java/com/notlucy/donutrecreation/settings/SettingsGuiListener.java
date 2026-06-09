package com.notlucy.donutrecreation.settings;

import com.notlucy.donutrecreation.util.LogData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class SettingsGuiListener implements Listener {

  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryClick(InventoryClickEvent event) {
    InventoryView view = event.getView();
    if (!isSettingsGui(view)) {
      return;
    }
    event.setCancelled(true);

    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (event.getCurrentItem() == null) {
      return;
    }
    int slot = event.getRawSlot();
    if (slot < 0 || slot >= event.getInventory().getSize()) {
      return;
    }

    LogData.get().fine(() -> "[settings] " + player.getName()
        + " clicked slot " + slot);
    player.sendMessage(Component.text("You clicked slot " + slot
        + " (functionality coming soon)"));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (isSettingsGui(event.getView())) {
      event.setCancelled(true);
    }
  }

  private static boolean isSettingsGui(InventoryView view) {
    Component title = view.title();
    return Component.text("Settings").equals(title);
  }
}
