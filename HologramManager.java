package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Hologramme (TextDisplay) mit den Top 10 der reichsten Spieler bzw. der höchsten Kopfgelder. */
public class HologramManager {
    public enum Type { BAL, BOUNTY }

    private static final class Holo {
        String id;
        Type type;
        String world;
        double x, y, z;
        UUID entity;
    }

    private final CoreEco pl;
    private final File file;
    private final List<Holo> list = new ArrayList<>();

    public HologramManager(CoreEco pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "holograms.yml");
        if (!file.exists()) return;
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("holograms");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Holo h = new Holo();
                h.id = id;
                h.type = Type.valueOf(s.getString("type", "BAL"));
                h.world = s.getString("world", "world");
                h.x = s.getDouble("x");
                h.y = s.getDouble("y");
                h.z = s.getDouble("z");
                String eu = s.getString("entity");
                if (eu != null) h.entity = UUID.fromString(eu);
                list.add(h);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Holo h : list) {
            String b = "holograms." + h.id;
            y.set(b + ".type", h.type.name());
            y.set(b + ".world", h.world);
            y.set(b + ".x", h.x);
            y.set(b + ".y", h.y);
            y.set(b + ".z", h.z);
            if (h.entity != null) y.set(b + ".entity", h.entity.toString());
        }
        try { y.save(file); } catch (IOException ex) { pl.getLogger().warning("holograms.yml: " + ex.getMessage()); }
    }

    private String tag(Holo h) { return "powereco_holo_" + h.id; }

    private String text(Type t) {
        EconomyManager eco = pl.eco();
        List<String> l = new ArrayList<>();
        if (t == Type.BAL) {
            l.add("&6&lTop 10 &8| &e&lReichste Spieler");
            l.add("&8&m                         ");
            List<Map.Entry<UUID, Double>> top = eco.top();
            for (int i = 0; i < 10; i++) {
                if (i < top.size())
                    l.add("&e#" + (i + 1) + " &f" + eco.name(top.get(i).getKey()) + " &8- &a" + eco.formatShort(top.get(i).getValue()));
                else l.add("&e#" + (i + 1) + " &8-");
            }
        } else {
            l.add("&c&lTop 10 &8| &4&lKopfgelder");
            l.add("&8&m                         ");
            List<Map.Entry<UUID, Double>> top = new ArrayList<>(pl.bounties().all().entrySet());
            top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < 10; i++) {
                if (i < top.size())
                    l.add("&c#" + (i + 1) + " &f" + eco.name(top.get(i).getKey()) + " &8- &e" + eco.formatShort(top.get(i).getValue()));
                else l.add("&c#" + (i + 1) + " &8-");
            }
        }
        return U.c(String.join("\n", l));
    }

    /** Findet oder erzeugt das Display-Entity eines Hologramms (nur wenn der Chunk geladen ist). */
    private TextDisplay ensure(Holo h) {
        World w = Bukkit.getWorld(h.world);
        if (w == null) return null;
        Location loc = new Location(w, h.x, h.y, h.z);
        if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return null;
        if (h.entity != null) {
            Entity known = Bukkit.getEntity(h.entity);
            if (known instanceof TextDisplay kd && kd.isValid()) return kd;
        }
        for (Entity near : w.getNearbyEntities(loc, 2, 2, 2)) {
            if (near instanceof TextDisplay nd && near.getScoreboardTags().contains(tag(h))) {
                h.entity = nd.getUniqueId();
                return nd;
            }
        }
        TextDisplay created = w.spawn(loc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.addScoreboardTag(tag(h));
        });
        h.entity = created.getUniqueId();
        save();
        return created;
    }

    public synchronized void refresh() {
        for (Holo h : list) {
            TextDisplay td = ensure(h);
            if (td != null) td.setText(text(h.type));
        }
    }

    public synchronized void create(Player p, Type type) {
        Location l = p.getLocation().add(0, 2.5, 0);
        Holo h = new Holo();
        h.id = UUID.randomUUID().toString().substring(0, 8);
        h.type = type;
        h.world = l.getWorld().getName();
        h.x = l.getX();
        h.y = l.getY();
        h.z = l.getZ();
        list.add(h);
        TextDisplay td = ensure(h);
        if (td != null) td.setText(text(type));
        save();
        U.msg(p, "&aHologramm erstellt. &7Es aktualisiert sich alle 30 Sekunden. Entfernen mit &e/"
                + (type == Type.BAL ? "bal" : "bounty") + " listedelete");
    }

    private void kill(Holo h) {
        World w = Bukkit.getWorld(h.world);
        if (w == null) return;
        Location loc = new Location(w, h.x, h.y, h.z);
        loc.getChunk().load();
        if (h.entity != null) {
            Entity e = Bukkit.getEntity(h.entity);
            if (e != null) e.remove();
        }
        for (Entity e : w.getNearbyEntities(loc, 2, 2, 2))
            if (e.getScoreboardTags().contains(tag(h))) e.remove();
    }

    /** Entfernt das nächste Hologramm dieser Art (max. 20 Blöcke) oder mit all=true alle. Liefert die Anzahl. */
    public synchronized int delete(Player p, Type type, boolean all) {
        int removed = 0;
        if (all) {
            for (Iterator<Holo> it = list.iterator(); it.hasNext(); ) {
                Holo h = it.next();
                if (h.type != type) continue;
                kill(h);
                it.remove();
                removed++;
            }
        } else {
            Holo best = null;
            double bestD = 20 * 20;
            for (Holo h : list) {
                if (h.type != type || !h.world.equals(p.getWorld().getName())) continue;
                double d = p.getLocation().distanceSquared(new Location(p.getWorld(), h.x, h.y, h.z));
                if (d <= bestD) { bestD = d; best = h; }
            }
            if (best != null) { kill(best); list.remove(best); removed = 1; }
        }
        if (removed > 0) save();
        return removed;
    }
}
