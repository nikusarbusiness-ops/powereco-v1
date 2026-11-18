package de.coreeco;

import org.bukkit.Material;

/** Kategorien, Sortierungen und der Ansichts-Zustand (Filter/Suche) pro Spieler. */
public final class Filters {
    private Filters() {}

    public enum Cat {
        ALL("Alle", Material.NETHER_STAR),
        BLOCKS("Blöcke", Material.GRASS_BLOCK),
        TOOLS("Werkzeuge", Material.IRON_PICKAXE),
        COMBAT("Kampf & Rüstung", Material.DIAMOND_SWORD),
        FOOD("Essen", Material.COOKED_BEEF),
        POTIONS("Tränke", Material.POTION),
        MISC("Sonstiges", Material.ENDER_PEARL);

        public final String label;
        public final Material icon;
        Cat(String label, Material icon) { this.label = label; this.icon = icon; }

        public static Cat of(Material m) {
            String n = m.name();
            if (n.equals("POTION") || n.equals("SPLASH_POTION") || n.equals("LINGERING_POTION") || n.equals("TIPPED_ARROW")) return POTIONS;
            if (n.endsWith("_SWORD") || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                    || n.endsWith("_BOOTS") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("SHIELD")
                    || n.equals("MACE") || n.equals("ARROW") || n.equals("ELYTRA") || n.equals("TOTEM_OF_UNDYING")) return COMBAT;
            if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_AXE")
                    || n.equals("FISHING_ROD") || n.equals("SHEARS") || n.equals("FLINT_AND_STEEL")) return TOOLS;
            if (m.isEdible()) return FOOD;
            if (m.isBlock()) return BLOCKS;
            return MISC;
        }

        public boolean matches(Material m) { return this == ALL || of(m) == this; }
    }

    public enum Sort {
        NEWEST("Neueste zuerst"),
        ENDING("Endet bald"),
        PRICE_LOW("Preis: niedrig → hoch"),
        PRICE_HIGH("Preis: hoch → niedrig"),
        AMOUNT("Menge: viel → wenig");

        public final String label;
        Sort(String label) { this.label = label; }

        public static final Sort[] AH_CYCLE = {NEWEST, PRICE_LOW, PRICE_HIGH, ENDING};
        public static final Sort[] ORDER_CYCLE = {PRICE_HIGH, PRICE_LOW, AMOUNT, NEWEST};
    }

    public static class View {
        public Sort sort;
        public Cat cat = Cat.ALL;
        public String search = null;
        public View(Sort s) { this.sort = s; }
    }
}
