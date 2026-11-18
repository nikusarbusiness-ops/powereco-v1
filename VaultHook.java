package de.coreeco;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class VaultHook implements Economy {
    private final CoreEco pl;

    public VaultHook(CoreEco pl) { this.pl = pl; }

    public void register() {
        Bukkit.getServicesManager().register(Economy.class, this, pl, ServicePriority.Highest);
    }

    @SuppressWarnings("deprecation")
    private UUID id(String name) {
        OfflinePlayer o = pl.eco().find(name);
        return (o != null ? o : Bukkit.getOfflinePlayer(name)).getUniqueId();
    }

    private UUID id(OfflinePlayer p) { pl.eco().ensure(p.getUniqueId(), p.getName()); return p.getUniqueId(); }

    private EconomyResponse dep(UUID u, double a) {
        if (a < 0) return new EconomyResponse(0, pl.eco().get(u), EconomyResponse.ResponseType.FAILURE, "Negativer Betrag");
        pl.eco().deposit(u, a);
        return new EconomyResponse(a, pl.eco().get(u), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse wd(UUID u, double a) {
        if (a < 0) return new EconomyResponse(0, pl.eco().get(u), EconomyResponse.ResponseType.FAILURE, "Negativer Betrag");
        if (!pl.eco().withdraw(u, a)) return new EconomyResponse(0, pl.eco().get(u), EconomyResponse.ResponseType.FAILURE, "Nicht genug Geld");
        return new EconomyResponse(a, pl.eco().get(u), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse noBank() { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Keine Bank-Unterstützung"); }

    @Override public boolean isEnabled() { return pl.isEnabled(); }
    @Override public String getName() { return "PowerEco"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double v) { return pl.eco().format(v); }
    @Override public String currencyNamePlural() { return pl.getConfig().getString("currency.plural", "Coins"); }
    @Override public String currencyNameSingular() { return pl.getConfig().getString("currency.singular", "Coin"); }

    @Override public boolean hasAccount(String n) { return true; }
    @Override public boolean hasAccount(OfflinePlayer p) { return true; }
    @Override public boolean hasAccount(String n, String w) { return true; }
    @Override public boolean hasAccount(OfflinePlayer p, String w) { return true; }

    @Override public double getBalance(String n) { return pl.eco().get(id(n)); }
    @Override public double getBalance(OfflinePlayer p) { return pl.eco().get(id(p)); }
    @Override public double getBalance(String n, String w) { return getBalance(n); }
    @Override public double getBalance(OfflinePlayer p, String w) { return getBalance(p); }

    @Override public boolean has(String n, double a) { return pl.eco().has(id(n), a); }
    @Override public boolean has(OfflinePlayer p, double a) { return pl.eco().has(id(p), a); }
    @Override public boolean has(String n, String w, double a) { return has(n, a); }
    @Override public boolean has(OfflinePlayer p, String w, double a) { return has(p, a); }

    @Override public EconomyResponse withdrawPlayer(String n, double a) { return wd(id(n), a); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, double a) { return wd(id(p), a); }
    @Override public EconomyResponse withdrawPlayer(String n, String w, double a) { return wd(id(n), a); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, String w, double a) { return wd(id(p), a); }

    @Override public EconomyResponse depositPlayer(String n, double a) { return dep(id(n), a); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer p, double a) { return dep(id(p), a); }
    @Override public EconomyResponse depositPlayer(String n, String w, double a) { return dep(id(n), a); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer p, String w, double a) { return dep(id(p), a); }

    @Override public EconomyResponse createBank(String n, String p) { return noBank(); }
    @Override public EconomyResponse createBank(String n, OfflinePlayer p) { return noBank(); }
    @Override public EconomyResponse deleteBank(String n) { return noBank(); }
    @Override public EconomyResponse bankBalance(String n) { return noBank(); }
    @Override public EconomyResponse bankHas(String n, double a) { return noBank(); }
    @Override public EconomyResponse bankWithdraw(String n, double a) { return noBank(); }
    @Override public EconomyResponse bankDeposit(String n, double a) { return noBank(); }
    @Override public EconomyResponse isBankOwner(String n, String p) { return noBank(); }
    @Override public EconomyResponse isBankOwner(String n, OfflinePlayer p) { return noBank(); }
    @Override public EconomyResponse isBankMember(String n, String p) { return noBank(); }
    @Override public EconomyResponse isBankMember(String n, OfflinePlayer p) { return noBank(); }
    @Override public List<String> getBanks() { return Collections.emptyList(); }

    @Override public boolean createPlayerAccount(String n) { pl.eco().ensure(id(n), n); return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer p) { id(p); return true; }
    @Override public boolean createPlayerAccount(String n, String w) { return createPlayerAccount(n); }
    @Override public boolean createPlayerAccount(OfflinePlayer p, String w) { return createPlayerAccount(p); }
}
