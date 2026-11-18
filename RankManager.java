package de.coreeco;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Locale;

/** Rang-Boni: mehr AH-Plätze, mehr Order-Plätze und ein Verkaufs-Multiplikator (siehe "ranks" in der config.yml). */
public class RankManager {
    private final CoreEco pl;

    public RankManager(CoreEco pl) { this.pl = pl; }

    private boolean in(Player p, String rank) {
        return p.hasPermission("group." + rank) || p.hasPermission("powereco.rank." + rank);
    }

    /** Hoechster Zahlenwert aus Permissions wie powereco.ah.limit.25 (alte coreeco.-Nodes gehen weiterhin). */
    private double permMax(Player p, String sub, double base) {
        double max = base;
        for (PermissionAttachmentInfo pai : p.getEffectivePermissions()) {
            if (!pai.getValue()) continue;
            String perm = pai.getPermission().toLowerCase(Locale.ROOT);
            for (String root : new String[]{"powereco.", "coreeco."}) {
                String pre = root + sub;
                if (perm.startsWith(pre)) {
                    try { max = Math.max(max, Double.parseDouble(perm.substring(pre.length()))); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return max;
    }

    private double fromRanks(Player p, String key, double base) {
        double max = base;
        ConfigurationSection rs = pl.getConfig().getConfigurationSection("ranks");
        if (rs != null)
            for (String rank : rs.getKeys(false))
                if (in(p, rank)) max = Math.max(max, rs.getDouble(rank + "." + key, 0));
        return max;
    }

    public int ahSlots(Player p) {
        double base = pl.getConfig().getInt("auction.default-max-listings", 10);
        return (int) permMax(p, "ah.limit.", fromRanks(p, "ah-slots", base));
    }

    public int orderSlots(Player p) {
        double base = pl.getConfig().getInt("orders.max-per-player", 5);
        return (int) permMax(p, "orders.limit.", fromRanks(p, "order-slots", base));
    }

    public double sellMultiplier(Player p) {
        double base = Math.max(0.1, pl.getConfig().getDouble("sell.default-multiplier", 1.0));
        return permMax(p, "sell.multiplier.", fromRanks(p, "sell-multiplier", base));
    }
}
