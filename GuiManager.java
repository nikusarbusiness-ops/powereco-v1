package de.coreeco;

import de.coreeco.Filters.Cat;
import de.coreeco.Filters.Sort;
import de.coreeco.Filters.View;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager implements Listener {
    private static final int PER_PAGE = 45;
    private final CoreEco pl;
    private final Map<UUID, View> ahViews = new HashMap<>();
    private final Map<UUID, View> ordViews = new HashMap<>();
    private final Map<UUID, String> prompts = new ConcurrentHashMap<>();

    public GuiManager(CoreEco pl) { this.pl = pl; }

    private View ahView(UUID id) { return ahViews.computeIfAbsent(id, k -> new View(Sort.NEWEST)); }
    private View ordView(UUID id) { return ordViews.computeIfAbsent(id, k -> new View(Sort.PRICE_HIGH)); }
    private ItemStack pane() { return U.named(Material.GRAY_STAINED_GLASS_PANE, " ", null); }

    public void open(Player p, Gui.Type type, int page) {
        if (type == Gui.Type.SHOP_MAIN) openShopMain(p);
        else if (type == Gui.Type.SHOP_CAT) openShopMain(p);
        else if (type == Gui.Type.BALTOP) openBaltop(p);
        else openList(p, type, page);
    }

    // ---------- Suche / Filter-Helfer ----------

    private boolean matchesSearch(String search, String... fields) {
        if (search == null || search.isBlank()) return true;
        String s = search.toLowerCase(Locale.ROOT);
        for (String f : fields) if (f != null && f.toLowerCase(Locale.ROOT).contains(s)) return true;
        return false;
    }

    private String stripName(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        return m != null && m.hasDisplayName() ? org.bukkit.ChatColor.stripColor(m.getDisplayName()) : null;
    }

    private ItemStack filterButton(View v) {
        List<String> lore = new ArrayList<>();
        lore.add("&8&m                    ");
        for (Cat c : Cat.values()) lore.add((c == v.cat ? "&a▶ &f" : "&7  ") + c.label);
        lore.add("");
        lore.add("&eLinksklick: &7nächste  &eRechtsklick: &7vorherige");
        return U.named(v.cat.icon, "&6Filter: &f" + v.cat.label, lore);
    }

    private ItemStack sortButton(View v, Sort[] cycle) {
        List<String> lore = new ArrayList<>();
        lore.add("&8&m                    ");
        for (Sort s : cycle) lore.add((s == v.sort ? "&a▶ &f" : "&7  ") + s.label);
        lore.add("");
        lore.add("&eLinksklick: &7nächste  &eRechtsklick: &7vorherige");
        return U.named(Material.HOPPER, "&6Sortierung: &f" + v.sort.label, lore);
    }

    private ItemStack searchButton(View v) {
        List<String> lore = new ArrayList<>();
        lore.add("&8&m                    ");
        lore.add(v.search == null ? "&7Aktiv: &fKeine Suche" : "&7Aktiv: &a" + v.search);
        lore.add("");
        lore.add("&eLinksklick: &7Suchbegriff eingeben");
        lore.add("&eRechtsklick: &7Suche löschen");
        return U.named(Material.OAK_SIGN, "&6Suche", lore);
    }

    // ---------- Listen-GUIs ----------

    private void openList(Player p, Gui.Type type, int page) {
        EconomyManager eco = pl.eco();
        boolean isAh = type == Gui.Type.AH || type == Gui.Type.MYAH;
        boolean mine = type == Gui.Type.MYAH || type == Gui.Type.MYORDERS;
        View v = mine ? null : (isAh ? ahView(p.getUniqueId()) : ordView(p.getUniqueId()));

        List<UUID> ids = new ArrayList<>();
        Map<UUID, ItemStack> icons = new HashMap<>();
        String title;

        if (isAh) {
            title = mine ? "&6Meine Auktionen" : "&6Auction House";
            List<AuctionManager.Listing> list = new ArrayList<>();
            for (AuctionManager.Listing l : pl.ah().all()) {
                if (mine) { if (!l.seller.equals(p.getUniqueId())) continue; }
                else {
                    if (!v.cat.matches(l.item.getType())) continue;
                    if (!matchesSearch(v.search, U.pretty(l.item.getType()), l.item.getType().name(), stripName(l.item), l.sellerName)) continue;
                }
                list.add(l);
            }
            Sort sort = mine ? Sort.ENDING : v.sort;
            switch (sort) {
                case PRICE_LOW -> list.sort(Comparator.comparingDouble(l -> l.price));
                case PRICE_HIGH -> list.sort((a, b) -> Double.compare(b.price, a.price));
                case NEWEST -> list.sort((a, b) -> Long.compare(b.expires, a.expires));
                default -> list.sort(Comparator.comparingLong(l -> l.expires));
            }
            for (AuctionManager.Listing l : list) {
                ItemStack it = l.item.clone();
                ItemMeta m = it.getItemMeta();
                List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
                lore.add(U.c("&8&m                    "));
                lore.add(U.c("&7Preis: &e" + eco.format(l.price)));
                lore.add(U.c("&7Verkäufer: &f" + l.sellerName));
                lore.add(U.c("&7Läuft ab in: &f" + U.time(l.expires - System.currentTimeMillis())));
                lore.add(U.c(l.seller.equals(p.getUniqueId()) ? "&cKlick: Auktion beenden" : "&aKlick: Kaufen"));
                m.setLore(lore);
                it.setItemMeta(m);
                ids.add(l.id);
                icons.put(l.id, it);
            }
        } else {
            title = mine ? "&6Meine Orders" : "&6Orders";
            List<OrderManager.Order> list = new ArrayList<>();
            for (OrderManager.Order o : pl.orders().all()) {
                if (mine) { if (!o.buyer.equals(p.getUniqueId())) continue; }
                else {
                    if (!v.cat.matches(o.mat)) continue;
                    if (!matchesSearch(v.search, U.pretty(o.mat), o.mat.name(), o.buyerName)) continue;
                }
                list.add(o);
            }
            Sort sort = mine ? Sort.NEWEST : v.sort;
            switch (sort) {
                case PRICE_LOW -> list.sort(Comparator.comparingDouble(o -> o.each));
                case AMOUNT -> list.sort((a, b) -> Integer.compare(b.remaining, a.remaining));
                case NEWEST -> list.sort((a, b) -> Long.compare(b.created, a.created));
                default -> list.sort((a, b) -> Double.compare(b.each, a.each));
            }
            for (OrderManager.Order o : list) {
                boolean own = o.buyer.equals(p.getUniqueId());
                ItemStack it = U.named(o.mat, "&e" + U.pretty(o.mat), List.of(
                        "&8&m                    ",
                        "&7Gesucht: &f" + o.remaining + "x",
                        "&7Preis pro Stück: &e" + eco.format(o.each),
                        "&7Gesamt: &e" + eco.format(o.each * o.remaining),
                        "&7Besteller: &f" + o.buyerName,
                        own ? "&cKlick: Order abbrechen" : "&aKlick: Items liefern"));
                ids.add(o.id);
                icons.put(o.id, it);
            }
        }

        int maxPage = Math.max(0, (ids.size() - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage));
        Gui gui = new Gui(type, page, 54, U.c(title + " &8(" + (page + 1) + "/" + (maxPage + 1) + ")"));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= ids.size()) break;
            gui.getInventory().setItem(i, icons.get(ids.get(idx)));
            gui.slots.put(i, ids.get(idx));
        }
        if (ids.isEmpty())
            gui.getInventory().setItem(22, U.named(Material.BARRIER, "&cKeine Einträge gefunden",
                    List.of(mine ? "&7Du hast hier noch nichts." : "&7Ändere Filter oder Suche.")));

        for (int i = 45; i < 54; i++) gui.getInventory().setItem(i, pane());
        if (page > 0) gui.getInventory().setItem(45, U.named(Material.ARROW, "&e« Vorherige Seite", null));
        if (page < maxPage) gui.getInventory().setItem(53, U.named(Material.ARROW, "&eNächste Seite »", null));
        ItemStack bal = U.named(Material.GOLD_INGOT, "&6Dein Guthaben", List.of("&e" + eco.format(eco.get(p.getUniqueId())), "", "&7Klick: aktualisieren"));
        gui.getInventory().setItem(49, bal);

        if (mine) {
            gui.getInventory().setItem(52, U.named(Material.BARRIER, "&c« Zurück", null));
        } else {
            gui.getInventory().setItem(46, sortButton(v, isAh ? Sort.AH_CYCLE : Sort.ORDER_CYCLE));
            gui.getInventory().setItem(47, filterButton(v));
            gui.getInventory().setItem(48, searchButton(v));
            if (isAh) {
                gui.getInventory().setItem(50, U.named(Material.EMERALD, "&aItem verkaufen",
                        List.of("&7Halte das Item in der Hand und", "&7klicke hier, dann den Preis im Chat.", "&8Beispiel: 1500, 2.5k, 1m")));
                gui.getInventory().setItem(51, U.named(Material.CHEST, "&6Meine Auktionen", List.of("&7Eigene Angebote verwalten")));
                gui.getInventory().setItem(52, U.named(Material.WRITABLE_BOOK, "&6Zu den Orders", List.of("&7Items bestellen / liefern")));
            } else {
                gui.getInventory().setItem(50, U.named(Material.EMERALD, "&aNeue Order",
                        List.of("&7Klicke, dann im Chat:", "&f<Item> <Menge> <Preis pro Stück>", "&8Beispiel: diamond 64 150")));
                gui.getInventory().setItem(51, U.named(Material.CHEST, "&6Meine Orders", List.of("&7Eigene Orders verwalten")));
                gui.getInventory().setItem(52, U.named(Material.GOLD_BLOCK, "&6Zum Auction House", List.of("&7Items kaufen / verkaufen")));
            }
        }
        p.openInventory(gui.getInventory());
    }


    // ---------- Shop ----------

    private ItemStack balanceItem(Player p) {
        EconomyManager eco = pl.eco();
        return U.named(Material.GOLD_INGOT, "&6Dein Guthaben", List.of("&e" + eco.format(eco.get(p.getUniqueId()))));
    }

    private void openShopMain(Player p) {
        Gui gui = new Gui(Gui.Type.SHOP_MAIN, 0, 36, U.c("&6&lShop &8- &7Kategorien"));
        for (int i = 0; i < 36; i++) gui.getInventory().setItem(i, U.named(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        int[] pos = pl.shop().categories().size() <= 3 ? new int[]{11, 13, 15} : new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        for (ShopManager.Category c : pl.shop().categories()) {
            if (i >= pos.length) break;
            gui.getInventory().setItem(pos[i], U.named(c.icon, c.name, List.of(
                    "&8&m                    ", "&7Items: &f" + c.items.size(), "", "&aKlick: öffnen")));
            gui.keys.put(pos[i], c.id);
            i++;
        }
        gui.getInventory().setItem(31, balanceItem(p));
        gui.getInventory().setItem(33, U.named(Material.HOPPER, "&aVerkaufen", List.of(
                "&8&m                    ", "&7Öffnet das Verkaufsmenü:", "&7Items reinlegen und bestätigen.", "", "&aKlick: /sell")));
        p.openInventory(gui.getInventory());
    }

    public void openShop(Player p, String catId, int page) {
        ShopManager.Category c = pl.shop().cat(catId);
        if (c == null) { openShopMain(p); return; }
        EconomyManager eco = pl.eco();
        int maxPage = Math.max(0, (c.items.size() - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage));
        Gui gui = new Gui(Gui.Type.SHOP_CAT, page, 54, U.c(c.name + " &8(" + (page + 1) + "/" + (maxPage + 1) + ")"));
        gui.cat = catId;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= c.items.size()) break;
            ShopManager.Item si = c.items.get(idx);
            int max = pl.shop().stackSize(si.mat);
            ItemStack it = new ItemStack(si.mat);
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                List<String> lore = new ArrayList<>();
                lore.add(U.c("&8&m                    "));
                lore.add(U.c("&7Preis: &e" + eco.format(si.price) + " &7pro Stück"));
                Double sp = pl.shop().sellPrice(si.mat);
                if (sp != null) lore.add(U.c("&7Verkaufen: &e" + eco.format(sp * pl.ranks().sellMultiplier(p)) + " &7pro Stück"));
                if (max > 1) lore.add(U.c("&7Stack (" + max + "x): &e" + eco.format(si.price * max)));
                lore.add("");
                lore.add(U.c("&aLinksklick: &f1x kaufen"));
                if (max > 1) lore.add(U.c("&aShift+Linksklick: &f" + max + "x kaufen"));
                m.setLore(lore);
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                it.setItemMeta(m);
            }
            gui.getInventory().setItem(i, it);
            gui.shopItems.put(i, si);
        }
        for (int i = 45; i < 54; i++) gui.getInventory().setItem(i, pane());
        if (page > 0) gui.getInventory().setItem(45, U.named(Material.ARROW, "&e« Vorherige Seite", null));
        if (page < maxPage) gui.getInventory().setItem(53, U.named(Material.ARROW, "&eNächste Seite »", null));
        gui.getInventory().setItem(48, U.named(Material.BARRIER, "&c« Zurück zu den Kategorien", null));
        gui.getInventory().setItem(49, balanceItem(p));
        p.openInventory(gui.getInventory());
    }

    private void handleShopClick(Player p, Gui gui, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        ItemStack cur = e.getCurrentItem();
        if (gui.type == Gui.Type.SHOP_MAIN) {
            if (slot == 33) { Bukkit.getScheduler().runTask(pl, () -> pl.sellMenu().open(p)); return; }
            String id = gui.keys.get(slot);
            if (id != null) Bukkit.getScheduler().runTask(pl, () -> openShop(p, id, 0));
            return;
        }
        boolean arrow = cur != null && cur.getType() == Material.ARROW;
        if (slot == 45 && arrow) { Bukkit.getScheduler().runTask(pl, () -> openShop(p, gui.cat, gui.page - 1)); return; }
        if (slot == 53 && arrow) { Bukkit.getScheduler().runTask(pl, () -> openShop(p, gui.cat, gui.page + 1)); return; }
        if (slot == 48) { Bukkit.getScheduler().runTask(pl, () -> openShopMain(p)); return; }
        if (slot == 49) { gui.getInventory().setItem(49, balanceItem(p)); return; }
        ShopManager.Item si = gui.shopItems.get(slot);
        if (si == null) return;
        ClickType ct = e.getClick();
        if (ct != ClickType.LEFT && ct != ClickType.SHIFT_LEFT) return;
        int amount = ct == ClickType.SHIFT_LEFT ? pl.shop().stackSize(si.mat) : 1;
        pl.shop().buy(p, si, amount);
        gui.getInventory().setItem(49, balanceItem(p));
    }

    // ---------- Baltop ----------

    private void openBaltop(Player p) {
        EconomyManager eco = pl.eco();
        Gui gui = new Gui(Gui.Type.BALTOP, 0, 45, U.c("&6&lBalTop &8- &7Die Reichsten"));
        for (int i = 0; i < 45; i++) gui.getInventory().setItem(i, U.named(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        int[] slots = {4, 12, 14, 19, 20, 21, 22, 23, 24, 25};
        String[] colors = {"&6&l", "&f&l", "&c&l", "&e", "&e", "&e", "&e", "&e", "&e", "&e"};
        List<Map.Entry<UUID, Double>> top = eco.top();
        for (int i = 0; i < slots.length && i < top.size(); i++) {
            UUID id = top.get(i).getKey();
            OfflinePlayer off = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(off);
            sm.setDisplayName(U.c(colors[i] + "#" + (i + 1) + " &f" + eco.name(id)));
            sm.setLore(List.of(U.c("&7Guthaben: &e" + eco.format(top.get(i).getValue()))));
            head.setItemMeta(sm);
            gui.getInventory().setItem(slots[i], head);
        }
        int rank = eco.rank(p.getUniqueId());
        gui.getInventory().setItem(40, U.named(Material.GOLD_INGOT, "&6Dein Konto", List.of(
                "&7Guthaben: &e" + eco.format(eco.get(p.getUniqueId())),
                "&7Platz: &f" + (rank > 0 ? "#" + rank : "-"))));
        p.openInventory(gui.getInventory());
    }

    // ---------- Klicks ----------

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g && g.type != Gui.Type.SELL) e.setCancelled(true);
    }

    private <T> T cycle(T[] arr, T cur, int dir) {
        int i = Arrays.asList(arr).indexOf(cur);
        if (i < 0) i = 0;
        return arr[Math.floorMod(i + dir, arr.length)];
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui gui)) return;
        if (gui.type == Gui.Type.SELL) return;   // wird vom SellMenu behandelt
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (gui.type == Gui.Type.SHOP_MAIN || gui.type == Gui.Type.SHOP_CAT) { handleShopClick(p, gui, e); return; }
        if (gui.type == Gui.Type.BALTOP) return;

        int slot = e.getRawSlot();
        boolean right = e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT;
        boolean isAh = gui.type == Gui.Type.AH || gui.type == Gui.Type.MYAH;
        boolean mine = gui.type == Gui.Type.MYAH || gui.type == Gui.Type.MYORDERS;
        ItemStack cur = e.getCurrentItem();

        if (slot == 45 && cur != null && cur.getType() == Material.ARROW) { reopen(p, gui.type, gui.page - 1); return; }
        if (slot == 53 && cur != null && cur.getType() == Material.ARROW) { reopen(p, gui.type, gui.page + 1); return; }
        if (slot == 49) { reopen(p, gui.type, gui.page); return; }

        if (slot >= 45) {
            if (mine) {
                if (slot == 52) reopen(p, isAh ? Gui.Type.AH : Gui.Type.ORDERS, 0);
                return;
            }
            View v = isAh ? ahView(p.getUniqueId()) : ordView(p.getUniqueId());
            switch (slot) {
                case 46 -> { v.sort = cycle(isAh ? Sort.AH_CYCLE : Sort.ORDER_CYCLE, v.sort, right ? -1 : 1); reopen(p, gui.type, 0); }
                case 47 -> { v.cat = cycle(Cat.values(), v.cat, right ? -1 : 1); reopen(p, gui.type, 0); }
                case 48 -> {
                    if (right) { v.search = null; reopen(p, gui.type, 0); }
                    else prompt(p, isAh ? "ah_search" : "ord_search", "&7Schreibe deinen &eSuchbegriff &7in den Chat &8(z.B. diamond) &7- oder &ccancel&7.");
                }
                case 50 -> {
                    if (isAh) {
                        if (p.getInventory().getItemInMainHand().getType() == Material.AIR) {
                            U.msg(p, "&cHalte erst das Item in der Hand, das du verkaufen willst.");
                            return;
                        }
                        prompt(p, "ah_sell", "&7Schreibe den &eVerkaufspreis &7in den Chat &8(z.B. 1500, 2.5k, 1m) &7- oder &ccancel&7.");
                    } else {
                        prompt(p, "ord_new", "&7Schreibe: &e<Item> <Menge> <Preis pro Stück> &8(z.B. diamond 64 150) &7- oder &ccancel&7.");
                    }
                }
                case 51 -> reopen(p, isAh ? Gui.Type.MYAH : Gui.Type.MYORDERS, 0);
                case 52 -> reopen(p, isAh ? Gui.Type.ORDERS : Gui.Type.AH, 0);
                default -> {}
            }
            return;
        }

        UUID id = gui.slots.get(slot);
        if (id == null) return;
        switch (gui.type) {
            case AH -> pl.ah().buy(p, id);
            case MYAH -> pl.ah().cancel(p, id);
            case ORDERS -> {
                OrderManager.Order o = pl.orders().get(id);
                if (o != null && o.buyer.equals(p.getUniqueId())) pl.orders().cancel(p, id);
                else pl.orders().fulfill(p, id);
            }
            case MYORDERS -> pl.orders().cancel(p, id);
            default -> {}
        }
        reopen(p, gui.type, gui.page);
    }

    private void reopen(Player p, Gui.Type t, int page) {
        Bukkit.getScheduler().runTask(pl, () -> open(p, t, page));
    }

    // ---------- Chat-Eingaben (Suche, Preis, neue Order) ----------

    private void prompt(Player p, String kind, String text) {
        prompts.put(p.getUniqueId(), kind);
        Bukkit.getScheduler().runTask(pl, () -> { p.closeInventory(); U.msg(p, text); });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String kind = prompts.remove(p.getUniqueId());
        if (kind == null) return;
        e.setCancelled(true);
        String msg = e.getMessage().trim();
        Bukkit.getScheduler().runTask(pl, () -> handlePrompt(p, kind, msg));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { prompts.remove(e.getPlayer().getUniqueId()); }

    private void handlePrompt(Player p, String kind, String msg) {
        boolean isAh = kind.startsWith("ah");
        Gui.Type back = isAh ? Gui.Type.AH : Gui.Type.ORDERS;
        if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("abbrechen")) {
            U.msg(p, "&7Abgebrochen.");
            open(p, back, 0);
            return;
        }
        switch (kind) {
            case "ah_search" -> ahView(p.getUniqueId()).search = msg;
            case "ord_search" -> ordView(p.getUniqueId()).search = msg;
            case "ah_sell" -> {
                Double price = U.parseAmount(msg);
                if (price == null) U.msg(p, "&cUngültiger Preis: &e" + msg);
                else pl.ah().sell(p, price);
            }
            case "ord_new" -> {
                String[] a = msg.split("\\s+");
                Material m = a.length >= 3 ? Material.matchMaterial(a[0]) : null;
                Double each = a.length >= 3 ? U.parseAmount(a[2]) : null;
                int amount = -1;
                try { if (a.length >= 3) amount = Integer.parseInt(a[1]); } catch (NumberFormatException ignored) {}
                if (m == null || each == null || amount < 1) U.msg(p, "&cFormat: &e<Item> <Menge> <Preis pro Stück> &7(z.B. diamond 64 150)");
                else pl.orders().create(p, m, amount, each);
            }
            default -> {}
        }
        open(p, back, 0);
    }
}
