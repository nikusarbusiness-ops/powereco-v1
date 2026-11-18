package de.coreeco;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** PowerEco - Hauptklasse. */
public final class CoreEco extends JavaPlugin implements Listener {
    private EconomyManager eco;
    private AuctionManager ah;
    private OrderManager orders;
    private BountyManager bounties;
    private RankManager ranks;
    private ShopManager shop;
    private SellMenu sellMenu;
    private HologramManager holograms;
    private GuiManager gui;

    public EconomyManager eco() { return eco; }
    public AuctionManager ah() { return ah; }
    public OrderManager orders() { return orders; }
    public BountyManager bounties() { return bounties; }
    public RankManager ranks() { return ranks; }
    public ShopManager shop() { return shop; }
    public SellMenu sellMenu() { return sellMenu; }
    public HologramManager holograms() { return holograms; }
    public GuiManager gui() { return gui; }

    /** Übernimmt Daten aus plugins/CoreEco (alter Plugin-Name), falls PowerEco noch keine hat. */
    private void migrateOldData() {
        File dest = getDataFolder();
        File old = new File(dest.getParentFile(), "CoreEco");
        if (!old.isDirectory() || new File(dest, "balances.yml").exists()) return;
        File[] files = old.listFiles();
        if (files == null) return;
        dest.mkdirs();
        for (File f : files) {
            if (!f.isFile() || f.getName().startsWith("shop.yml")) continue;
            try { Files.copy(f.toPath(), new File(dest, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ex) { getLogger().warning("Konnte " + f.getName() + " nicht übernehmen: " + ex.getMessage()); }
        }
        getLogger().info("Daten aus plugins/CoreEco wurden nach plugins/PowerEco übernommen.");
    }

    @Override
    public void onEnable() {
        migrateOldData();
        saveDefaultConfig();
        if (!getConfig().contains("ranks", true)) {       // alte Config ohne Rang-Bereich: Standardwerte ergänzen
            getConfig().options().copyDefaults(true);
            saveConfig();
        }
        eco = new EconomyManager(this);
        ah = new AuctionManager(this);
        orders = new OrderManager(this);
        bounties = new BountyManager(this);
        ranks = new RankManager(this);
        shop = new ShopManager(this);
        sellMenu = new SellMenu(this);
        holograms = new HologramManager(this);
        gui = new GuiManager(this);

        CoreCommands cc = new CoreCommands(this);
        for (String c : new String[]{"bal", "pay", "eco", "baltop", "ah", "orders", "bounty", "shop", "sell"}) {
            PluginCommand pc = getCommand(c);
            if (pc != null) { pc.setExecutor(cc); pc.setTabCompleter(cc); }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(sellMenu, this);
        Bukkit.getPluginManager().registerEvents(bounties, this);

        // Optionale Integrationen (Vault -> viele Plugins; PlaceholderAPI -> SimpleScore, Scoreboards, Chat usw.)
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try { new VaultHook(this).register(); getLogger().info("Vault-Economy registriert."); }
            catch (Throwable t) { getLogger().warning("Vault-Hook fehlgeschlagen: " + t.getMessage()); }
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new PapiHook(this, "powereco").register();
                new PapiHook(this, "coreeco").register();   // alte Platzhalter funktionieren weiter
                getLogger().info("PlaceholderAPI-Expansion registriert (%powereco_...%).");
            } catch (Throwable t) { getLogger().warning("PlaceholderAPI-Hook fehlgeschlagen: " + t.getMessage()); }
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null)
            getLogger().info("LuckPerms erkannt: Ränge (config.yml -> ranks) und powereco.ah.limit.<Zahl> werden unterstützt.");

        getLogger().info(shop.categories().size() + " Shop-Kategorien geladen, " + shop.pricedItems() + " Items mit Preis.");

        Bukkit.getScheduler().runTaskTimer(this, () -> ah.tickExpire(), 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, 20L * 300, 20L * 300);
        Bukkit.getScheduler().runTaskLater(this, () -> holograms.refresh(), 40L);
        Bukkit.getScheduler().runTaskTimer(this, () -> holograms.refresh(), 20L * 60, 20L * 30);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers())      // Verkaufsmenü schließen -> Items gehen zurück an den Spieler
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Gui g && g.type == Gui.Type.SELL) p.closeInventory();
        saveAll();
    }

    private void saveAll() {
        if (eco != null) eco.save();
        if (ah != null) ah.save();
        if (orders != null) orders.save();
        if (bounties != null) bounties.save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        eco.ensure(p.getUniqueId(), p.getName());
        Bukkit.getScheduler().runTaskLater(this, () -> ah.deliver(p), 40L);
    }
}
