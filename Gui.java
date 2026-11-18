package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Gui implements InventoryHolder {
    public enum Type { AH, MYAH, ORDERS, MYORDERS, BALTOP, SHOP_MAIN, SHOP_CAT, SELL }

    public final Type type;
    public final int page;
    public final Map<Integer, UUID> slots = new HashMap<>();
    public final Map<Integer, String> keys = new HashMap<>();
    public final Map<Integer, ShopManager.Item> shopItems = new HashMap<>();
    public String cat;
    private final Inventory inv;

    public Gui(Type type, int page, int size, String title) {
        this.type = type;
        this.page = page;
        this.inv = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
