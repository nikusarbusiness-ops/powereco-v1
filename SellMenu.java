package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** /sell: Items in das Menü legen, unten steht Confirm + Gesamtbetrag. Zu den Items wird der Wert als Lore angezeigt. */
public class SellMenu implements Listener {
    private static final int SLOTS = 45; // Slots 0-44 = Ablagefläche, 45-53 = Leiste
    private static final Set<ClickType> BLOCKED = EnumSet.of(ClickType.DROP, ClickType.CONTROL_DROP,
            ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.MIDDLE, ClickType.CREATIVE,
            ClickType.WINDOW_BORDER_LEFT, ClickType.WINDOW_BORDER_RIGHT, ClickType.UNKNOWN);

    private final CoreEco pl;
    private final NamespacedKey mark;

    public SellMenu(CoreEco pl) {
        this.pl = pl;
        this.mark = new NamespacedKey(pl, "sellmark");
    }

    public void open(Player p) {
        Gui gui = new Gui(Gui.Type.SELL, 0, 54, U.c("&6&lVerkaufen &8- &7Items reinlegen"));
        Inventory inv = gui.getInventory();
        for (int i = SLOTS; i < 54; i++) inv.setItem(i, U.named(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        update(gui, p);
        p.openInventory(inv);
    }

    // ---------- Hilfen ----------

    private boolean marked(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        return m != null && m.getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
    }

    /** Verkaufbar = hat einen Preis und ist ein "sauberes" Item (nur unsere eigene Wert-Lore ist erlaubt). */
    private boolean valid(ItemStack it) {
        return it != null && it.getType() != Material.AIR
                && pl.shop().sellPrice(it.getType()) != null
                && (!it.hasItemMeta() || marked(it));
    }

    private ItemStack plain(ItemStack it) {
        return marked(it) ? new ItemStack(it.getType(), it.getAmount()) : it;
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0);
    }

    private ItemStack decorate(Material m, int amount, double mult) {
        EconomyManager eco = pl.eco();
        double each = pl.shop().sellEach(m, mult);
        ItemStack it = new ItemStack(m, amount);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        List<String> lore = new ArrayList<>();
        lore.add(U.c("&8&m                    "));
        lore.add(U.c("&7Wert: &a" + eco.format(each) + " &7pro Stück"));
        if (amount > 1) lore.add(U.c("&7Stack: &a" + eco.format(each * amount)));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    /** Entfernt unsere Wert-Lore von Items, die beim Rausziehen im Inventar oder auf dem Cursor landen. */
    private void clean(Player p) {
        PlayerInventory pi = p.getInventory();
        ItemStack[] contents = pi.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() != Material.AIR && marked(it)) {
                contents[i] = new ItemStack(it.getType(), it.getAmount());
                changed = true;
            }
        }
        if (changed) pi.setContents(contents);
        ItemStack cur = p.getItemOnCursor();
        if (cur != null && cur.getType() != Material.AIR && marked(cur))
            p.setItemOnCursor(new ItemStack(cur.getType(), cur.getAmount()));
    }

    private void schedule(Player p, Gui gui) {
        Bukkit.getScheduler().runTask(pl, () -> refresh(p, gui));
    }

    private void refresh(Player p, Gui gui) {
        if (p.getOpenInventory().getTopInventory().getHolder() != gui) return;
        Inventory inv = gui.getInventory();
        double mult = pl.ranks().sellMultiplier(p);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (valid(it)) inv.setItem(i, decorate(it.getType(), it.getAmount(), mult));
        }
        clean(p);
        update(gui, p);
    }

    private void update(Gui gui, Player p) {
        Inventory inv = gui.getInventory();
        EconomyManager eco = pl.eco();
        double mult = pl.ranks().sellMultiplier(p);
        Map<Material, Integer> counts = new LinkedHashMap<>();
        Map<Material, Double> values = new HashMap<>();
        double total = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (!valid(it)) continue;
            double v = pl.shop().sellEach(it.getType(), mult) * it.getAmount();
            total += v;
            counts.merge(it.getType(), it.getAmount(), Integer::sum);
            values.merge(it.getType(), v, Double::sum);
        }
        total = U.round(total);
        ItemStack btn;
        if (counts.isEmpty()) {
            btn = U.named(Material.RED_STAINED_GLASS_PANE, "&c&lNichts zum Verkaufen", List.of(
                    "&7Lege Items in die obere Fläche.",
                    "&7Es werden nur Items ohne Namen,",
                    "&7Verzauberung oder Schaden angenommen."));
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("&7Du bekommst: &a+" + eco.format(total));
            if (Math.abs(mult - 1.0) > 0.0001) lore.add("&7Sell-Multiplikator: &dx" + trim(mult));
            lore.add("&8&m                    ");
            int shown = 0;
            for (Map.Entry<Material, Integer> en : counts.entrySet()) {
                if (shown == 8) { lore.add("&7... und &f" + (counts.size() - 8) + " weitere"); break; }
                lore.add("&f" + en.getValue() + "x " + U.pretty(en.getKey()) + " &8- &a" + eco.format(U.round(values.get(en.getKey()))));
                shown++;
            }
            lore.add("");
            lore.add("&aKlick: &fVerkaufen");
            lore.add("&8Nicht verkaufbare Items bekommst du zurück.");
            btn = U.named(Material.LIME_STAINED_GLASS_PANE, "&a&lConfirm &8- &a+" + eco.format(total), lore);
        }
        for (int i = 48; i <= 50; i++) inv.setItem(i, btn.clone());
    }

    private void confirm(Player p, Gui gui) {
        Inventory inv = gui.getInventory();
        double mult = pl.ranks().sellMultiplier(p);
        Map<Material, Integer> sold = new LinkedHashMap<>();
        List<ItemStack> back = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
            inv.setItem(i, null);
            if (valid(it)) {
                total += pl.shop().sellEach(it.getType(), mult) * it.getAmount();
                sold.merge(it.getType(), it.getAmount(), Integer::sum);
            } else back.add(plain(it));
        }
        for (ItemStack b : back) U.give(p, b);
        if (sold.isEmpty()) {
            U.msg(p, "&cIm Menü liegt nichts Verkaufbares.");
        } else {
            total = U.round(total);
            pl.eco().deposit(p.getUniqueId(), total);
            pl.shop().report(p, sold, total);
        }
        Bukkit.getScheduler().runTask(pl, () -> p.closeInventory());
    }

    // ---------- Events ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui gui) || gui.type != Gui.Type.SELL) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || BLOCKED.contains(e.getClick())) { e.setCancelled(true); return; }
        int raw = e.getRawSlot();
        if (e.getClickedInventory() == e.getInventory() && raw >= SLOTS) {
            e.setCancelled(true);
            if (raw >= 48 && raw <= 50) confirm(p, gui);
            return;
        }
        schedule(p, gui);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui gui) || gui.type != Gui.Type.SELL) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        for (int slot : e.getRawSlots()) {
            if (slot >= SLOTS && slot < 54) { e.setCancelled(true); return; }
        }
        schedule(p, gui);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui gui) || gui.type != Gui.Type.SELL) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = gui.getInventory();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
            inv.setItem(i, null);
            U.give(p, plain(it));
        }
        Bukkit.getScheduler().runTask(pl, () -> clean(p));
    }
}
