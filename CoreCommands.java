package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class CoreCommands implements CommandExecutor, TabCompleter {
    private final CoreEco pl;

    public CoreCommands(CoreEco pl) { this.pl = pl; }

    private Double parse(CommandSender s, String in) {
        Double d = U.parseAmount(in);
        if (d == null) U.msg(s, "&cUngültiger Betrag: &e" + in + " &7(erlaubt: 1500, 1.5k, 2m)");
        return d;
    }

    private Player needPlayer(CommandSender s) {
        if (s instanceof Player p) return p;
        U.msg(s, "&cDieser Befehl ist nur für Spieler.");
        return null;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        EconomyManager eco = pl.eco();
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "bal" -> {
                if (a.length >= 1 && (a[0].equalsIgnoreCase("liste") || a[0].equalsIgnoreCase("listedelete"))) {
                    handleHolo(s, HologramManager.Type.BAL, a);
                    return true;
                }
                if (a.length == 0) {
                    Player p = needPlayer(s); if (p == null) return true;
                    U.msg(p, "Dein Guthaben: &e" + eco.format(eco.get(p.getUniqueId())));
                } else {
                    if (!s.hasPermission("powereco.balance.others")) { U.msg(s, "&cKeine Berechtigung."); return true; }
                    OfflinePlayer t = eco.find(a[0]);
                    if (t == null || !eco.exists(t.getUniqueId())) { U.msg(s, "&cSpieler nicht gefunden."); return true; }
                    U.msg(s, "&e" + eco.name(t.getUniqueId()) + "&7 hat &e" + eco.format(eco.get(t.getUniqueId())));
                }
            }
            case "pay" -> {
                Player p = needPlayer(s); if (p == null) return true;
                if (a.length < 2) { U.msg(p, "&cNutzung: /pay <Spieler> <Betrag>"); return true; }
                OfflinePlayer t = eco.find(a[0]);
                if (t == null || !eco.exists(t.getUniqueId())) { U.msg(p, "&cSpieler nicht gefunden."); return true; }
                if (t.getUniqueId().equals(p.getUniqueId())) { U.msg(p, "&cDu kannst dir nicht selbst Geld senden."); return true; }
                Double amt = parse(p, a[1]); if (amt == null) return true;
                if (!eco.withdraw(p.getUniqueId(), amt)) { U.msg(p, "&cDu hast nicht genug Geld."); return true; }
                eco.deposit(t.getUniqueId(), amt);
                U.msg(p, "Du hast &e" + eco.format(amt) + " &7an &e" + eco.name(t.getUniqueId()) + " &7gesendet.");
                if (t.isOnline()) U.msg(t.getPlayer(), "&e" + p.getName() + " &7hat dir &e" + eco.format(amt) + " &7gesendet.");
            }
            case "eco" -> {
                if (a.length < 3) { U.msg(s, "&cNutzung: /eco <give|take|set> <Spieler> <Betrag>"); return true; }
                OfflinePlayer t = eco.find(a[1]);
                if (t == null || !eco.exists(t.getUniqueId())) { U.msg(s, "&cSpieler nicht gefunden."); return true; }
                Double amt = parse(s, a[2]); if (amt == null) return true;
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "give" -> eco.deposit(t.getUniqueId(), amt);
                    case "take" -> eco.set(t.getUniqueId(), eco.get(t.getUniqueId()) - amt);
                    case "set" -> eco.set(t.getUniqueId(), amt);
                    default -> { U.msg(s, "&cNutzung: /eco <give|take|set> <Spieler> <Betrag>"); return true; }
                }
                U.msg(s, "&e" + eco.name(t.getUniqueId()) + " &7hat jetzt &e" + eco.format(eco.get(t.getUniqueId())));
            }
            case "baltop" -> {
                if (s instanceof Player p) pl.gui().open(p, Gui.Type.BALTOP, 0);
                else {
                    int i = 1;
                    for (Map.Entry<UUID, Double> e : eco.top()) {
                        U.msg(s, "#" + i + " &e" + eco.name(e.getKey()) + " &7- &e" + eco.format(e.getValue()));
                        if (++i > 10) break;
                    }
                }
            }
            case "ah" -> {
                Player p = needPlayer(s); if (p == null) return true;
                if (a.length == 0) { pl.gui().open(p, Gui.Type.AH, 0); return true; }
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "sell" -> {
                        if (a.length < 2) { U.msg(p, "&cNutzung: /ah sell <Preis>"); return true; }
                        Double price = parse(p, a[1]); if (price == null) return true;
                        pl.ah().sell(p, price);
                    }
                    case "my", "mine", "meine" -> pl.gui().open(p, Gui.Type.MYAH, 0);
                    default -> U.msg(p, "&cNutzung: /ah, /ah sell <Preis>, /ah my");
                }
            }
            case "orders" -> {
                Player p = needPlayer(s); if (p == null) return true;
                if (a.length == 0) { pl.gui().open(p, Gui.Type.ORDERS, 0); return true; }
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "my", "mine", "meine" -> pl.gui().open(p, Gui.Type.MYORDERS, 0);
                    case "create", "add" -> {
                        if (a.length < 4) { U.msg(p, "&cNutzung: /orders create <Item> <Menge> <Preis pro Stück>"); return true; }
                        Material m = Material.matchMaterial(a[1]);
                        if (m == null) { U.msg(p, "&cUnbekanntes Item: &e" + a[1]); return true; }
                        int amount;
                        try { amount = Integer.parseInt(a[2]); } catch (NumberFormatException e) { U.msg(p, "&cUngültige Menge."); return true; }
                        Double each = parse(p, a[3]); if (each == null) return true;
                        pl.orders().create(p, m, amount, each);
                    }
                    default -> U.msg(p, "&cNutzung: /orders, /orders create <Item> <Menge> <Preis/Stück>, /orders my");
                }
            }
            case "shop" -> {
                if (a.length >= 1 && a[0].equalsIgnoreCase("reload")) {
                    if (!s.hasPermission("powereco.admin")) { U.msg(s, "&cKeine Berechtigung."); return true; }
                    pl.shop().reload();
                    U.msg(s, "&aShop neu geladen &7(" + pl.shop().categories().size() + " Kategorien).");
                    return true;
                }
                Player p = needPlayer(s); if (p == null) return true;
                if (pl.shop().categories().isEmpty()) { U.msg(p, "&cDer Shop ist leer. Prüfe die shop.yml."); return true; }
                if (a.length >= 1) {
                    ShopManager.Category c = pl.shop().find(a[0]);
                    if (c == null) { U.msg(p, "&cKategorie nicht gefunden: &e" + a[0]); return true; }
                    pl.gui().openShop(p, c.id, 0);
                } else pl.gui().open(p, Gui.Type.SHOP_MAIN, 0);
            }
            case "sell" -> {
                Player p = needPlayer(s); if (p == null) return true;
                if (a.length == 0) { pl.sellMenu().open(p); return true; }
                String sub = a[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "hand" -> {
                        Integer lim = null;
                        if (a.length >= 2) {
                            try { lim = Integer.parseInt(a[1]); } catch (NumberFormatException ex) { lim = -1; }
                            if (lim < 1) { U.msg(p, "&cUngültige Menge."); return true; }
                        }
                        pl.shop().sellHand(p, lim);
                    }
                    case "all", "inv", "alles" -> pl.shop().sellAll(p);
                    case "worth", "wert" -> pl.shop().worth(p);
                    default -> U.msg(p, "&cNutzung: /sell (Menü) | /sell hand [Menge] | /sell all | /sell worth");
                }
            }
            case "bounty" -> {
                if (a.length >= 1 && a[0].equalsIgnoreCase("add")) {
                    Player p = needPlayer(s); if (p == null) return true;
                    if (a.length < 3) { U.msg(p, "&cNutzung: /bounty add <Spieler> <Geld>"); return true; }
                    OfflinePlayer t = eco.find(a[1]);
                    if (t == null || !eco.exists(t.getUniqueId())) { U.msg(p, "&cSpieler nicht gefunden."); return true; }
                    Double amt = parse(p, a[2]); if (amt == null) return true;
                    pl.bounties().add(p, t.getUniqueId(), eco.name(t.getUniqueId()), amt);
                } else if (a.length >= 1 && a[0].equalsIgnoreCase("list")) {
                    if (pl.bounties().all().isEmpty()) { U.msg(s, "Aktuell gibt es keine Kopfgelder."); return true; }
                    pl.bounties().all().entrySet().stream()
                            .sorted((x, y) -> Double.compare(y.getValue(), x.getValue())).limit(15)
                            .forEach(e -> U.msg(s, "&c" + eco.name(e.getKey()) + " &7- &e" + eco.format(e.getValue())));
                } else if (a.length >= 1 && (a[0].equalsIgnoreCase("liste") || a[0].equalsIgnoreCase("listedelete"))) {
                    handleHolo(s, HologramManager.Type.BOUNTY, a);
                } else if (a.length == 0) {
                    U.msg(s, "&cNutzung: /bounty add <Spieler> <Geld> | /bounty list | /bounty search [Spieler] | /bounty liste");
                } else {
                    // Suche: /bounty search [Name], /bounty me oder direkt /bounty <Name> - auch der eigene Name geht
                    boolean explicit = a[0].equalsIgnoreCase("search") || a[0].equalsIgnoreCase("suche");
                    String q = explicit ? (a.length >= 2 ? a[1] : "me") : a[0];
                    OfflinePlayer t;
                    if (q.equalsIgnoreCase("me")) {
                        Player self = needPlayer(s); if (self == null) return true;
                        t = self;
                    } else t = eco.find(q);
                    if (t == null || !eco.exists(t.getUniqueId())) { U.msg(s, "&cSpieler nicht gefunden."); return true; }
                    double b = pl.bounties().get(t.getUniqueId());
                    String who = eco.name(t.getUniqueId());
                    if (b <= 0) { U.msg(s, "&e" + who + " &7hat kein Kopfgeld."); return true; }
                    List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(pl.bounties().all().entrySet());
                    sorted.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
                    int place = 1;
                    for (Map.Entry<UUID, Double> en : sorted) { if (en.getKey().equals(t.getUniqueId())) break; place++; }
                    U.msg(s, "&e" + who + " &7hat ein Kopfgeld von &c" + eco.format(b) + " &8(Platz #" + place + " von " + sorted.size() + ")");
                }
            }
        }
        return true;
    }

    private void handleHolo(CommandSender s, HologramManager.Type type, String[] a) {
        Player p = needPlayer(s); if (p == null) return;
        if (!p.hasPermission("powereco.hologram")) { U.msg(p, "&cKeine Berechtigung."); return; }
        if (a[0].equalsIgnoreCase("liste")) { pl.holograms().create(p, type); return; }
        boolean all = a.length >= 2 && a[1].equalsIgnoreCase("all");
        int n = pl.holograms().delete(p, type, all);
        U.msg(p, n > 0 ? "&a" + n + " Hologramm(e) entfernt."
                : "&cKein Hologramm dieser Art im Umkreis von 20 Blöcken. &7Mit &e" + "listedelete all" + " &7entfernst du alle.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> r = new ArrayList<>();
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            switch (n) {
                case "bal" -> { r.addAll(pl.eco().knownNames()); if (s.hasPermission("powereco.hologram")) r.addAll(List.of("liste", "listedelete")); }
                case "pay" -> r.addAll(pl.eco().knownNames());
                case "eco" -> r.addAll(List.of("give", "take", "set"));
                case "ah" -> r.addAll(List.of("sell", "my"));
                case "sell" -> r.addAll(List.of("hand", "all", "worth"));
                case "orders" -> r.addAll(List.of("create", "my"));
                case "bounty" -> { r.addAll(List.of("add", "list", "search", "me")); if (s.hasPermission("powereco.hologram")) r.addAll(List.of("liste", "listedelete")); }
                case "shop" -> { for (ShopManager.Category c : pl.shop().categories()) r.add(c.id); if (s.hasPermission("powereco.admin")) r.add("reload"); }
            }
        } else if (a.length == 2) {
            if (n.equals("eco") || (n.equals("bounty") && a[0].equalsIgnoreCase("add"))) r.addAll(pl.eco().knownNames());
            if (n.equals("orders") && a[0].equalsIgnoreCase("create"))
                for (Material m : Material.values()) if (m.isItem() && !m.isLegacy()) r.add(m.name().toLowerCase(Locale.ROOT));
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return r;
    }
}
