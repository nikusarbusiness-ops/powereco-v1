package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BountyManager implements Listener {
    private final CoreEco pl;
    private final File file;
    private final Map<UUID, Double> bounties = new LinkedHashMap<>();

    public BountyManager(CoreEco pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "bounties.yml");
        if (file.exists()) {
            ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("bounties");
            if (s != null) for (String k : s.getKeys(false)) {
                try { bounties.put(UUID.fromString(k), s.getDouble(k)); } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        bounties.forEach((k, v) -> y.set("bounties." + k, v));
        try { y.save(file); } catch (IOException ex) { pl.getLogger().warning("bounties.yml: " + ex.getMessage()); }
    }

    public double get(UUID id) { return bounties.getOrDefault(id, 0.0); }
    public Map<UUID, Double> all() { return bounties; }

    public void add(Player p, UUID target, String targetName, double amount) {
        amount = U.round(amount);
        double min = pl.getConfig().getDouble("bounty.min-amount", 100.0);
        if (target.equals(p.getUniqueId())) { U.msg(p, "&cDu kannst kein Kopfgeld auf dich selbst setzen."); return; }
        if (amount < min) { U.msg(p, "&cMindest-Kopfgeld: &e" + pl.eco().format(min)); return; }
        if (!pl.eco().withdraw(p.getUniqueId(), amount)) { U.msg(p, "&cDu hast nicht genug Geld."); return; }
        bounties.merge(target, amount, Double::sum);
        Bukkit.broadcastMessage(U.P + U.c("&e" + p.getName() + " &7hat &e" + pl.eco().format(amount) + " &7Kopfgeld auf &c" + targetName
                + " &7gesetzt! &8(Gesamt: &e" + pl.eco().format(get(target)) + "&8)"));
        save();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        double b = get(victim.getUniqueId());
        if (b <= 0 || killer == null || killer.equals(victim)) return;
        bounties.remove(victim.getUniqueId());
        pl.eco().deposit(killer.getUniqueId(), b);
        Bukkit.broadcastMessage(U.P + U.c("&e" + killer.getName() + " &7hat das Kopfgeld auf &c" + victim.getName()
                + " &7eingelöst und &e" + pl.eco().format(b) + " &7erhalten!"));
        save();
    }
}
