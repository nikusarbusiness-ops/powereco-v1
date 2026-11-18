package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AuctionManager {
    public static class Listing {
        public UUID id, seller;
        public String sellerName;
        public ItemStack item;
        public double price;
        public long expires;
    }

    private final CoreEco pl;
    private final File file;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    public AuctionManager(CoreEco pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "auctions.yml");
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ls = y.getConfigurationSection("listings");
        if (ls != null) for (String k : ls.getKeys(false)) {
            try {
                Listing l = new Listing();
                l.id = UUID.fromString(k);
                l.seller = UUID.fromString(ls.getString(k + ".seller"));
                l.sellerName = ls.getString(k + ".sellerName", "?");
                l.item = ls.getItemStack(k + ".item");
                l.price = ls.getDouble(k + ".price");
                l.expires = ls.getLong(k + ".expires");
                if (l.item != null) listings.put(l.id, l);
            } catch (Exception ignored) {}
        }
        ConfigurationSection ps = y.getConfigurationSection("pending");
        if (ps != null) for (String k : ps.getKeys(false)) {
            try {
                List<ItemStack> items = new ArrayList<>();
                for (Object o : ps.getList(k, List.of())) if (o instanceof ItemStack is) items.add(is);
                pending.put(UUID.fromString(k), items);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String p = "listings." + l.id;
            y.set(p + ".seller", l.seller.toString());
            y.set(p + ".sellerName", l.sellerName);
            y.set(p + ".item", l.item);
            y.set(p + ".price", l.price);
            y.set(p + ".expires", l.expires);
        }
        for (Map.Entry<UUID, List<ItemStack>> e : pending.entrySet())
            if (!e.getValue().isEmpty()) y.set("pending." + e.getKey(), e.getValue());
        try { y.save(file); } catch (IOException ex) { pl.getLogger().warning("auctions.yml: " + ex.getMessage()); }
    }

    public List<Listing> all() {
        List<Listing> l = new ArrayList<>(listings.values());
        l.sort(Comparator.comparingLong(a -> a.expires));
        return l;
    }

    public List<Listing> of(UUID seller) {
        List<Listing> r = new ArrayList<>();
        for (Listing l : all()) if (l.seller.equals(seller)) r.add(l);
        return r;
    }

    public Listing get(UUID id) { return listings.get(id); }

    public int maxListings(Player p) { return pl.ranks().ahSlots(p); }

    public void sell(Player p, double price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) { U.msg(p, "&cDu musst ein Item in der Hand halten."); return; }
        double min = pl.getConfig().getDouble("auction.min-price", 1.0);
        if (price < min) { U.msg(p, "&cMindestpreis: &e" + pl.eco().format(min)); return; }
        if (of(p.getUniqueId()).size() >= maxListings(p)) { U.msg(p, "&cDu hast das Limit an Auktionen erreicht (" + maxListings(p) + ")."); return; }
        Listing l = new Listing();
        l.id = UUID.randomUUID();
        l.seller = p.getUniqueId();
        l.sellerName = p.getName();
        l.item = hand.clone();
        l.price = U.round(price);
        l.expires = System.currentTimeMillis() + pl.getConfig().getLong("auction.duration-hours", 48) * 3600_000L;
        listings.put(l.id, l);
        p.getInventory().setItemInMainHand(null);
        U.msg(p, "&aItem im Auction House für &e" + pl.eco().format(l.price) + " &aeingestellt.");
        save();
    }

    public void buy(Player b, UUID id) {
        Listing l = listings.get(id);
        if (l == null) { U.msg(b, "&cDieses Angebot existiert nicht mehr."); return; }
        if (l.seller.equals(b.getUniqueId())) { cancel(b, id); return; }
        if (!pl.eco().withdraw(b.getUniqueId(), l.price)) { U.msg(b, "&cDu hast nicht genug Geld."); return; }
        listings.remove(id);
        double tax = pl.getConfig().getDouble("auction.tax-percent", 5.0);
        double net = U.round(l.price * (1 - tax / 100.0));
        pl.eco().deposit(l.seller, net);
        U.give(b, l.item.clone());
        U.msg(b, "&aGekauft: &e" + l.item.getAmount() + "x " + U.pretty(l.item.getType()) + " &afür &e" + pl.eco().format(l.price));
        Player s = Bukkit.getPlayer(l.seller);
        if (s != null) U.msg(s, "&e" + b.getName() + " &ahat dein Item gekauft! Du erhältst &e" + pl.eco().format(net) + " &7(nach " + tax + "% Gebühr)");
        save();
    }

    public void cancel(Player p, UUID id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller.equals(p.getUniqueId())) return;
        listings.remove(id);
        U.give(p, l.item.clone());
        U.msg(p, "&aAuktion beendet, Item zurückgegeben.");
        save();
    }

    public void addPending(UUID id, ItemStack it) {
        Player on = Bukkit.getPlayer(id);
        if (on != null) { U.give(on, it); return; }
        pending.computeIfAbsent(id, k -> new ArrayList<>()).add(it);
    }

    public void deliver(Player p) {
        List<ItemStack> l = pending.remove(p.getUniqueId());
        if (l == null || l.isEmpty()) return;
        l.forEach(i -> U.give(p, i));
        U.msg(p, "&aDu hast &e" + l.size() + " &aItem(s) erhalten (abgelaufene Auktionen / Orders).");
        save();
    }

    public void tickExpire() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Listing l : new ArrayList<>(listings.values())) {
            if (l.expires <= now) {
                listings.remove(l.id);
                addPending(l.seller, l.item);
                changed = true;
            }
        }
        if (changed) save();
    }
}
