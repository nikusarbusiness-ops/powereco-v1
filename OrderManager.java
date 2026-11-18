package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OrderManager {
    public static class Order {
        public UUID id, buyer;
        public String buyerName;
        public Material mat;
        public int remaining;
        public long created;
        public double each;
    }

    private final CoreEco pl;
    private final File file;
    private final Map<UUID, Order> orders = new LinkedHashMap<>();

    public OrderManager(CoreEco pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "orders.yml");
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection s = y.getConfigurationSection("orders");
            if (s != null) for (String k : s.getKeys(false)) {
                try {
                    Order o = new Order();
                    o.id = UUID.fromString(k);
                    o.buyer = UUID.fromString(s.getString(k + ".buyer"));
                    o.buyerName = s.getString(k + ".buyerName", "?");
                    o.mat = Material.valueOf(s.getString(k + ".material"));
                    o.remaining = s.getInt(k + ".remaining");
                    o.each = s.getDouble(k + ".each");
                    o.created = s.getLong(k + ".created");
                    orders.put(o.id, o);
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Order o : orders.values()) {
            String p = "orders." + o.id;
            y.set(p + ".buyer", o.buyer.toString());
            y.set(p + ".buyerName", o.buyerName);
            y.set(p + ".material", o.mat.name());
            y.set(p + ".remaining", o.remaining);
            y.set(p + ".each", o.each);
            y.set(p + ".created", o.created);
        }
        try { y.save(file); } catch (IOException ex) { pl.getLogger().warning("orders.yml: " + ex.getMessage()); }
    }

    public List<Order> all() { return new ArrayList<>(orders.values()); }
    public Order get(UUID id) { return orders.get(id); }

    public List<Order> of(UUID buyer) {
        List<Order> r = new ArrayList<>();
        for (Order o : orders.values()) if (o.buyer.equals(buyer)) r.add(o);
        return r;
    }

    public void create(Player p, Material mat, int amount, double each) {
        if (!mat.isItem() || mat == Material.AIR) { U.msg(p, "&cUngültiges Item."); return; }
        if (amount < 1 || amount > 2304) { U.msg(p, "&cMenge muss zwischen 1 und 2304 liegen."); return; }
        if (each <= 0) { U.msg(p, "&cPreis muss größer als 0 sein."); return; }
        if (of(p.getUniqueId()).size() >= pl.ranks().orderSlots(p)) {
            U.msg(p, "&cDu hast das Order-Limit erreicht (" + pl.ranks().orderSlots(p) + "). Höhere Ränge bekommen mehr Plätze."); return;
        }
        each = U.round(each);
        double total = U.round(each * amount);
        if (!pl.eco().withdraw(p.getUniqueId(), total)) { U.msg(p, "&cDu brauchst &e" + pl.eco().format(total) + "&c."); return; }
        Order o = new Order();
        o.id = UUID.randomUUID();
        o.buyer = p.getUniqueId();
        o.buyerName = p.getName();
        o.mat = mat;
        o.remaining = amount;
        o.each = each;
        o.created = System.currentTimeMillis();
        orders.put(o.id, o);
        U.msg(p, "&aOrder erstellt: &e" + amount + "x " + U.pretty(mat) + " &afür je &e" + pl.eco().format(each) + " &7(" + pl.eco().format(total) + " hinterlegt)");
        save();
    }

    public void fulfill(Player seller, UUID id) {
        Order o = orders.get(id);
        if (o == null) { U.msg(seller, "&cDiese Order existiert nicht mehr."); return; }
        if (o.buyer.equals(seller.getUniqueId())) { U.msg(seller, "&cDas ist deine eigene Order. Abbrechen: &e/orders my"); return; }
        int have = 0;
        for (ItemStack it : seller.getInventory().getStorageContents())
            if (it != null && it.getType() == o.mat && !it.hasItemMeta()) have += it.getAmount();
        int n = Math.min(have, o.remaining);
        if (n <= 0) { U.msg(seller, "&cDu hast keine passenden Items (unverzaubert/ohne Namen)."); return; }
        int left = n;
        ItemStack[] cont = seller.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack it = cont[i];
            if (it == null || it.getType() != o.mat || it.hasItemMeta()) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) cont[i] = null;
            left -= take;
        }
        seller.getInventory().setStorageContents(cont);
        double pay = U.round(n * o.each);
        pl.eco().deposit(seller.getUniqueId(), pay);
        pl.ah().addPending(o.buyer, new ItemStack(o.mat, n));
        o.remaining -= n;
        if (o.remaining <= 0) orders.remove(id);
        U.msg(seller, "&aDu hast &e" + n + "x " + U.pretty(o.mat) + " &ageliefert und &e" + pl.eco().format(pay) + " &aerhalten.");
        Player b = Bukkit.getPlayer(o.buyer);
        if (b != null) U.msg(b, "&e" + seller.getName() + " &ahat &e" + n + "x " + U.pretty(o.mat) + " &afür deine Order geliefert.");
        save();
    }

    public void cancel(Player p, UUID id) {
        Order o = orders.get(id);
        if (o == null || !o.buyer.equals(p.getUniqueId())) return;
        orders.remove(id);
        double refund = U.round(o.remaining * o.each);
        pl.eco().deposit(p.getUniqueId(), refund);
        U.msg(p, "&aOrder abgebrochen, &e" + pl.eco().format(refund) + " &azurückerstattet.");
        save();
    }
}
