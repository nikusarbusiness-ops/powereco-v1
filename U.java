package de.coreeco;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

public final class U {
    private U() {}
    public static String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    public static final String P = c("&8[&6PowerEco&8] &7");
    public static void msg(CommandSender s, String m) { s.sendMessage(P + c(m)); }
    public static double round(double d) { return Math.round(d * 100.0) / 100.0; }

    public static String time(long ms) {
        long m = Math.max(0, ms) / 60000L, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        return Math.max(1, m) + "m";
    }

    public static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase(Locale.ROOT).split("_"))
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    public static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    public static ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(c(name));
        if (lore != null) meta.setLore(lore.stream().map(U::c).toList());
        it.setItemMeta(meta);
        return it;
    }

    /** Kurzform: 3768 -> 3.7k, 47834 -> 47k, 1234567 -> 1.2m (abgeschnitten, nie aufgerundet). */
    public static String compact(double v) {
        String[] suf = {"", "k", "m", "b", "t"};
        double x = Math.max(0, v);
        int i = 0;
        while (x >= 1000 && i < suf.length - 1) { x /= 1000; i++; }
        if (i == 0) {
            String s = String.format(Locale.US, "%.2f", Math.floor(x * 100) / 100.0);
            if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
            return s;
        }
        if (x < 10) {
            x = Math.floor(x * 10) / 10.0;
            return (x == Math.floor(x) ? String.valueOf((long) x) : String.valueOf(x)) + suf[i];
        }
        return String.valueOf((long) Math.floor(x)) + suf[i];
    }

    /** Parst Betraege wie 1500, 1.5k, 2m, 1,5b. Liefert null bei Fehler. */
    public static Double parseAmount(String in) {
        try {
            String t = in.trim().toLowerCase(Locale.ROOT).replace(',', '.');
            double mul = 1;
            if (t.endsWith("k")) { mul = 1_000; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("m")) { mul = 1_000_000; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("b")) { mul = 1_000_000_000; t = t.substring(0, t.length() - 1); }
            double d = Double.parseDouble(t) * mul;
            if (Double.isNaN(d) || Double.isInfinite(d) || d <= 0 || d > 1_000_000_000_000.0) return null;
            return round(d);
        } catch (NumberFormatException e) { return null; }
    }
}
