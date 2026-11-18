package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyManager {
    private final CoreEco pl;
    private final File file;
    private final Map<UUID, Double> bal = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));

    public EconomyManager(CoreEco pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "balances.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                bal.put(id, s.getDouble(k + ".balance"));
                String n = s.getString(k + ".name");
                if (n != null) names.put(id, n);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Double> e : bal.entrySet()) {
            y.set("players." + e.getKey() + ".balance", e.getValue());
            String n = names.get(e.getKey());
            if (n != null) y.set("players." + e.getKey() + ".name", n);
        }
        try { y.save(file); } catch (IOException ex) { pl.getLogger().warning("Konnte balances.yml nicht speichern: " + ex.getMessage()); }
    }

    public void ensure(UUID id, String name) {
        if (name != null) names.put(id, name);
        bal.putIfAbsent(id, U.round(pl.getConfig().getDouble("start-balance", 1000.0)));
    }

    public double get(UUID id) { return bal.getOrDefault(id, 0.0); }
    public boolean exists(UUID id) { return bal.containsKey(id); }
    public void set(UUID id, double v) { bal.put(id, U.round(Math.max(0, v))); }
    public void deposit(UUID id, double v) { bal.put(id, U.round(get(id) + v)); }

    public boolean withdraw(UUID id, double v) {
        v = U.round(v);
        if (get(id) + 0.0001 < v) return false;
        bal.put(id, U.round(Math.max(0, get(id) - v)));
        return true;
    }

    public boolean has(UUID id, double v) { return get(id) + 0.0001 >= v; }

    public String format(double v) { return pl.getConfig().getString("currency.symbol", "$") + df.format(v); }

    public String formatShort(double v) { return pl.getConfig().getString("currency.symbol", "$") + U.compact(v); }

    public String name(UUID id) {
        String n = names.get(id);
        if (n != null) return n;
        n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : "Unbekannt";
    }

    public OfflinePlayer find(String name) {
        OfflinePlayer on = Bukkit.getPlayerExact(name);
        if (on != null) return on;
        for (Map.Entry<UUID, String> e : names.entrySet())
            if (e.getValue().equalsIgnoreCase(name)) return Bukkit.getOfflinePlayer(e.getKey());
        return null;
    }

    public List<Map.Entry<UUID, Double>> top() {
        List<Map.Entry<UUID, Double>> l = new ArrayList<>(bal.entrySet());
        l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return l;
    }

    public int rank(UUID id) {
        List<Map.Entry<UUID, Double>> l = top();
        for (int i = 0; i < l.size(); i++) if (l.get(i).getKey().equals(id)) return i + 1;
        return -1;
    }

    public Collection<String> knownNames() { return names.values(); }
}
