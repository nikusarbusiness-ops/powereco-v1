package de.coreeco;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Shop + /sell. Kategorien, Preise und Verkaufswerte stehen in plugins/PowerEco/shop.yml (/shop reload). */
public class ShopManager {
    private static final int CONFIG_VERSION = 3;

    public static final class Item {
        public final Material mat;
        public final double price;
        Item(Material mat, double price) { this.mat = mat; this.price = price; }
    }

    public static final class Category {
        public final String id;
        public final String name;
        public final Material icon;
        public final List<Item> items = new ArrayList<>();
        Category(String id, String name, Material icon) { this.id = id; this.name = name; this.icon = icon; }
    }

    private final CoreEco pl;
    private final File file;
    private final Map<String, Category> cats = new LinkedHashMap<>();
    private final Map<Material, Double> sellMap = new HashMap<>();

    public ShopManager(CoreEco pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "shop.yml");
        if (file.exists() && YamlConfiguration.loadConfiguration(file).getInt("config-version", 1) < CONFIG_VERSION) {
            File old = new File(pl.getDataFolder(), "shop.yml.old");
            if (old.exists()) old.delete();
            file.renameTo(old);
            pl.getLogger().info("Alte shop.yml wurde als shop.yml.old gesichert, der neue Standard-Shop wird erstellt.");
        }
        if (!file.exists()) writeDefaults();
        load();
    }

    public Collection<Category> categories() { return cats.values(); }
    public Category cat(String id) { return cats.get(id); }
    public Double sellPrice(Material m) { return sellMap.get(m); }
    public int pricedItems() { return sellMap.size(); }

    /** Verkaufswert pro Stück inkl. Rang-Multiplikator (0, wenn nicht verkaufbar). */
    public double sellEach(Material m, double mult) {
        Double v = sellMap.get(m);
        return v == null ? 0 : v * mult;
    }

    /** "Stack"-Menge beim Shift-Klick: voller Stack, bei nicht stapelbaren Items 64 Stück. */
    public int stackSize(Material m) {
        int ms = m.getMaxStackSize();
        return ms > 1 ? ms : 64;
    }

    public Category find(String in) {
        String s = in.toLowerCase(Locale.ROOT);
        for (Category c : cats.values()) {
            if (c.id.equalsIgnoreCase(s)) return c;
            if (org.bukkit.ChatColor.stripColor(U.c(c.name)).toLowerCase(Locale.ROOT).contains(s)) return c;
        }
        return null;
    }

    public void reload() { load(); }

    private void load() {
        cats.clear();
        sellMap.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        double ratio = Math.max(0, y.getDouble("sell-ratio", 0.4));

        ConfigurationSection root = y.getConfigurationSection("categories");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection cs = root.getConfigurationSection(id);
                if (cs == null) continue;
                Material icon = Material.matchMaterial(cs.getString("icon", "CHEST"));
                if (icon == null) icon = Material.CHEST;
                Category c = new Category(id, cs.getString("name", id), icon);
                ConfigurationSection is = cs.getConfigurationSection("items");
                if (is != null) {
                    for (String key : is.getKeys(false)) {
                        Material m = Material.matchMaterial(key);
                        double price = is.getDouble(key);
                        if (m == null || !m.isItem() || m.isLegacy() || price <= 0) {
                            pl.getLogger().warning("shop.yml: Eintrag '" + key + "' in '" + id + "' ist ungültig und wird übersprungen.");
                            continue;
                        }
                        c.items.add(new Item(m, U.round(price)));
                    }
                }
                if (!c.items.isEmpty()) cats.put(id, c);
            }
        }

        // Verkaufswerte: erst die Extra-Liste "sell:", dann alle Shop-Items automatisch mit sell-ratio
        ConfigurationSection ss = y.getConfigurationSection("sell");
        if (ss != null) {
            for (String key : ss.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                double v = ss.getDouble(key);
                if (m == null || !m.isItem() || m.isLegacy() || v <= 0) {
                    pl.getLogger().warning("shop.yml: Verkaufs-Eintrag '" + key + "' ist ungültig und wird übersprungen.");
                    continue;
                }
                sellMap.put(m, U.round(v));
            }
        }
        for (Category c : cats.values())
            for (Item it : c.items) {
                double v = U.round(it.price * ratio);
                if (v > 0) sellMap.putIfAbsent(it.mat, v);
            }
    }

    // ---------- Kaufen ----------

    public void buy(Player p, Item it, int amount) {
        EconomyManager eco = pl.eco();
        double total = U.round(it.price * amount);
        if (!eco.withdraw(p.getUniqueId(), total)) {
            U.msg(p, "&cDafür brauchst du &e" + eco.format(total) + "&c. Du hast nur &e" + eco.format(eco.get(p.getUniqueId())) + "&c.");
            return;
        }
        U.give(p, new ItemStack(it.mat, amount));
        U.msg(p, "Gekauft: &e" + amount + "x " + U.pretty(it.mat) + " &7für &e" + eco.format(total));
    }

    // ---------- Verkaufen (/sell) ----------

    /** Nur "saubere" Items ohne Verzauberung, Namen, Haltbarkeitsschaden usw. werden angenommen. */
    private boolean sellable(ItemStack it) {
        return it != null && it.getType() != Material.AIR && !it.hasItemMeta() && sellMap.containsKey(it.getType());
    }

    private static String mult(double m) {
        return m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(Math.round(m * 100) / 100.0);
    }

    public void sellHand(Player p, Integer limit) {
        EconomyManager eco = pl.eco();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) { U.msg(p, "&cDu hast nichts in der Hand."); return; }
        if (!sellable(hand)) {
            U.msg(p, hand.hasItemMeta()
                    ? "&cDieses Item hat Besonderheiten (Name, Verzauberung, Schaden ...) und kann nicht verkauft werden."
                    : "&cDieses Item kann nicht verkauft werden.");
            return;
        }
        double each = sellEach(hand.getType(), pl.ranks().sellMultiplier(p));
        int amount = limit == null ? hand.getAmount() : Math.min(limit, hand.getAmount());
        double total = U.round(each * amount);
        Material type = hand.getType();
        if (amount >= hand.getAmount()) p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else hand.setAmount(hand.getAmount() - amount);
        eco.deposit(p.getUniqueId(), total);
        U.msg(p, "Verkauft: &e" + amount + "x " + U.pretty(type) + " &7für &a+" + eco.format(total));
    }

    public void sellAll(Player p) {
        EconomyManager eco = pl.eco();
        double mult = pl.ranks().sellMultiplier(p);
        PlayerInventory inv = p.getInventory();
        Map<Material, Integer> sold = new LinkedHashMap<>();
        double total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (!sellable(it)) continue;
            int n = it.getAmount();
            total += sellEach(it.getType(), mult) * n;
            sold.merge(it.getType(), n, Integer::sum);
            inv.setItem(i, null);
        }
        if (sold.isEmpty()) { U.msg(p, "&cIn deinem Inventar ist nichts, was sich verkaufen lässt."); return; }
        total = U.round(total);
        eco.deposit(p.getUniqueId(), total);
        report(p, sold, total);
    }

    /** Verkaufs-Zusammenfassung im Chat. */
    public void report(Player p, Map<Material, Integer> sold, double total) {
        EconomyManager eco = pl.eco();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<Material, Integer> e : sold.entrySet()) {
            if (shown == 5) { sb.append("&7und &f").append(sold.size() - 5).append(" weitere"); break; }
            sb.append("&f").append(e.getValue()).append("x ").append(U.pretty(e.getKey())).append("&7, ");
            shown++;
        }
        String list = sb.toString();
        if (list.endsWith("&7, ")) list = list.substring(0, list.length() - 4);
        double m = pl.ranks().sellMultiplier(p);
        U.msg(p, "Verkauft: " + list);
        U.msg(p, "Gesamt: &a+" + eco.format(total) + (Math.abs(m - 1.0) > 0.0001 ? " &8(&dx" + mult(m) + " Rang-Bonus&8)" : "")
                + " &7| Neues Guthaben: &e" + eco.format(eco.get(p.getUniqueId())));
    }

    public void worth(Player p) {
        EconomyManager eco = pl.eco();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) { U.msg(p, "&cDu hast nichts in der Hand."); return; }
        if (!sellMap.containsKey(hand.getType())) { U.msg(p, "&cDieses Item kann nicht verkauft werden."); return; }
        double each = sellEach(hand.getType(), pl.ranks().sellMultiplier(p));
        U.msg(p, "&e" + U.pretty(hand.getType()) + "&7: &e" + eco.format(each) + " &7pro Stück"
                + (hand.getAmount() > 1 ? " &8(&7dein Stack: &e" + eco.format(each * hand.getAmount()) + "&8)" : "")
                + (hand.hasItemMeta() ? " &c- mit Besonderheiten nicht verkaufbar" : ""));
    }

    // ---------- Standard-Katalog ----------
    // Skala: Startguthaben 1000. Eine Million ist auf dem Server richtig viel Geld.
    // Verkauf bringt automatisch 40 % des Shop-Preises (sell-ratio), damit Kaufen+Verkaufen keine Gelddruckmaschine ist.
    // Keine Rüstung im Shop.

    private void def(YamlConfiguration y, String id, String name, Material icon, String spec) {
        String base = "categories." + id;
        y.set(base + ".name", name);
        y.set(base + ".icon", icon.name());
        for (String part : spec.split(",")) {
            String[] kv = part.trim().split("=");
            if (kv.length == 2) y.set(base + ".items." + kv[0].trim(), Double.parseDouble(kv[1].trim()));
        }
    }

    private void writeDefaults() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "PowerEco Shop - Preise gelten pro Stück.",
                "Format: MATERIAL: Preis   (Namen: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html)",
                "sell-ratio: Anteil des Shop-Preises, den /sell auszahlt (0.4 = 40 %).",
                "sell: Zusätzliche Items, die nur verkauft (nicht gekauft) werden können, mit festem Verkaufspreis.",
                "Rang-Multiplikatoren (config.yml -> ranks) wirken zusätzlich auf alle Verkaufspreise.",
                "Nach Änderungen: /shop reload"));
        y.set("config-version", CONFIG_VERSION);
        y.set("sell-ratio", 0.4);

        def(y, "pvp", "&c&lPvP", Material.ENDER_PEARL,
                "ENDER_PEARL=25,TOTEM_OF_UNDYING=800,SHIELD=60,END_CRYSTAL=150,GLOWSTONE=8,OBSIDIAN=12,"
              + "RESPAWN_ANCHOR=220,ARROW=2,SPECTRAL_ARROW=5,BOW=40,CROSSBOW=60,FIREWORK_ROCKET=8,"
              + "TNT=30,WIND_CHARGE=12,COBWEB=20,EXPERIENCE_BOTTLE=15");

        def(y, "nether", "&4&lNether", Material.SOUL_SAND,
                "QUARTZ=4,QUARTZ_BLOCK=14,SOUL_SAND=3,SOUL_SOIL=3,NETHERRACK=1,NETHER_BRICKS=3,RED_NETHER_BRICKS=5,"
              + "NETHER_WART=6,NETHER_WART_BLOCK=20,MAGMA_BLOCK=5,BLACKSTONE=2,POLISHED_BLACKSTONE=3,GILDED_BLACKSTONE=10,"
              + "BASALT=2,SMOOTH_BASALT=3,CRIMSON_STEM=4,WARPED_STEM=4,CRIMSON_NYLIUM=6,WARPED_NYLIUM=6,SHROOMLIGHT=15,"
              + "GLOWSTONE_DUST=2,BLAZE_ROD=20,BLAZE_POWDER=10,MAGMA_CREAM=12,GHAST_TEAR=60,CRYING_OBSIDIAN=40,"
              + "SOUL_TORCH=3,SOUL_LANTERN=8,WEEPING_VINES=3,TWISTING_VINES=3,NETHER_STAR=5000");

        def(y, "food", "&a&lEssen", Material.COOKED_BEEF,
                "COOKED_BEEF=6,COOKED_PORKCHOP=6,COOKED_CHICKEN=5,COOKED_MUTTON=5,COOKED_RABBIT=5,COOKED_SALMON=5,"
              + "COOKED_COD=4,BREAD=3,BAKED_POTATO=3,APPLE=4,GOLDEN_CARROT=12,PUMPKIN_PIE=6,COOKIE=2,GOLDEN_APPLE=100,"
              + "ENCHANTED_GOLDEN_APPLE=3000,CAKE=20,MUSHROOM_STEW=5,RABBIT_STEW=10,BEETROOT_SOUP=5,HONEY_BOTTLE=8,"
              + "MELON_SLICE=2,SWEET_BERRIES=2,GLOW_BERRIES=3,DRIED_KELP=1,MILK_BUCKET=10");

        def(y, "blocks", "&6&lBlöcke", Material.GRASS_BLOCK,
                "COBBLESTONE=1,STONE=2,DEEPSLATE=2,COBBLED_DEEPSLATE=2,ANDESITE=2,DIORITE=2,GRANITE=2,TUFF=2,CALCITE=4,"
              + "DRIPSTONE_BLOCK=4,DIRT=1,GRASS_BLOCK=3,MUD=2,MOSS_BLOCK=6,SAND=2,RED_SAND=2,GRAVEL=2,CLAY=4,TERRACOTTA=5,"
              + "OAK_LOG=4,SPRUCE_LOG=4,BIRCH_LOG=4,JUNGLE_LOG=4,ACACIA_LOG=4,DARK_OAK_LOG=4,MANGROVE_LOG=5,CHERRY_LOG=5,"
              + "OAK_PLANKS=1,SPRUCE_PLANKS=1,BIRCH_PLANKS=1,GLASS=4,WHITE_WOOL=6,BRICKS=8,STONE_BRICKS=3,SANDSTONE=4,"
              + "PRISMARINE=12,SEA_LANTERN=35,END_STONE=8,PURPUR_BLOCK=12,BOOKSHELF=60,SNOW_BLOCK=3,ICE=4,PACKED_ICE=10,"
              + "BLUE_ICE=40,AMETHYST_BLOCK=40,SPONGE=120,LANTERN=15,TORCH=2,CRAFTING_TABLE=4,FURNACE=6,CHEST=8,BARREL=8");

        def(y, "resources", "&e&lErze & Ressourcen", Material.DIAMOND,
                "COAL=6,CHARCOAL=6,RAW_COPPER=5,COPPER_INGOT=6,RAW_IRON=12,IRON_INGOT=18,IRON_NUGGET=2,RAW_GOLD=22,"
              + "GOLD_INGOT=30,GOLD_NUGGET=3,LAPIS_LAZULI=8,REDSTONE=6,EMERALD=80,DIAMOND=160,AMETHYST_SHARD=10,"
              + "ANCIENT_DEBRIS=350,NETHERITE_SCRAP=400,NETHERITE_INGOT=1800,COAL_BLOCK=54,COPPER_BLOCK=54,IRON_BLOCK=162,"
              + "GOLD_BLOCK=270,LAPIS_BLOCK=72,REDSTONE_BLOCK=54,EMERALD_BLOCK=720,DIAMOND_BLOCK=1440,SLIME_BALL=8,BONE=3,"
              + "STRING=3,GUNPOWDER=6,FEATHER=3,LEATHER=6,PHANTOM_MEMBRANE=20,PRISMARINE_SHARD=6,PRISMARINE_CRYSTALS=8,"
              + "INK_SAC=4,GLOW_INK_SAC=8,SPIDER_EYE=4,ROTTEN_FLESH=1,SHULKER_SHELL=300");

        def(y, "farming", "&2&lFarming", Material.WHEAT,
                "WHEAT=2,WHEAT_SEEDS=1,CARROT=2,POTATO=2,BEETROOT=2,SUGAR_CANE=2,SUGAR=3,PUMPKIN=4,MELON=8,BAMBOO=1,"
              + "CACTUS=2,KELP=1,COCOA_BEANS=3,EGG=3,HAY_BLOCK=18,BONE_MEAL=3,BROWN_MUSHROOM=3,RED_MUSHROOM=3,"
              + "OAK_SAPLING=3,SPRUCE_SAPLING=3,BIRCH_SAPLING=3,PUMPKIN_SEEDS=2,MELON_SEEDS=2,BEETROOT_SEEDS=1");

        def(y, "redstone", "&c&lRedstone", Material.REDSTONE_TORCH,
                "REPEATER=15,COMPARATOR=40,OBSERVER=25,PISTON=30,STICKY_PISTON=40,HOPPER=60,DROPPER=20,DISPENSER=25,"
              + "TARGET=20,LEVER=2,STONE_BUTTON=2,STONE_PRESSURE_PLATE=3,REDSTONE_TORCH=6,REDSTONE_LAMP=20,"
              + "DAYLIGHT_DETECTOR=40,TRIPWIRE_HOOK=8,SLIME_BLOCK=60,HONEY_BLOCK=30,RAIL=3,POWERED_RAIL=25,"
              + "DETECTOR_RAIL=12,ACTIVATOR_RAIL=12,NOTE_BLOCK=15,LIGHTNING_ROD=30");

        def(y, "misc", "&d&lSonstiges", Material.ENCHANTING_TABLE,
                "ENCHANTING_TABLE=500,ANVIL=150,BREWING_STAND=60,GRINDSTONE=25,BOOK=15,ENDER_CHEST=220,SHULKER_BOX=400,"
              + "BEACON=12000,NAME_TAG=100,SADDLE=200,LEAD=30,ENDER_EYE=40,SPYGLASS=30,COMPASS=20,CLOCK=20,BUCKET=15,"
              + "WATER_BUCKET=17,LAVA_BUCKET=22,FISHING_ROD=15,SHEARS=15,FLINT_AND_STEEL=15,MINECART=25");

        // Nur verkaufbar (nicht im Shop kaufbar)
        String[][] extra = {
                {"BEEF", "1.5"}, {"PORKCHOP", "1.5"}, {"CHICKEN", "1"}, {"MUTTON", "1"}, {"RABBIT", "1"},
                {"COD", "1"}, {"SALMON", "1.5"}, {"POISONOUS_POTATO", "1"}, {"TROPICAL_FISH", "2"}, {"PUFFERFISH", "3"}
        };
        for (String[] e : extra) y.set("sell." + e[0], Double.parseDouble(e[1]));

        try { pl.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException e) { pl.getLogger().warning("Konnte shop.yml nicht erstellen: " + e.getMessage()); }
    }
}
