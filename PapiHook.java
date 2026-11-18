package de.coreeco;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Placeholders (Kennung "powereco", zusätzlich die alte Kennung "coreeco"):
 *  %powereco_balance%, %powereco_balance_formatted%, %powereco_balance_short%, %powereco_rank%,
 *  %powereco_bounty%, %powereco_bounty_short%, %powereco_sell_multiplier%,
 *  %powereco_top_1_name%, %powereco_top_1_balance%, %powereco_top_1_balance_short% */
public class PapiHook extends PlaceholderExpansion {
    private final CoreEco pl;
    private final String id;

    public PapiHook(CoreEco pl, String id) { this.pl = pl; this.id = id; }

    @Override public String getIdentifier() { return id; }
    @Override public String getAuthor() { return "PowerEco"; }
    @Override public String getVersion() { return pl.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer p, String params) {
        EconomyManager eco = pl.eco();
        String s = params.toLowerCase();
        if (s.startsWith("top_")) {
            String[] sp = s.split("_", 3);
            if (sp.length != 3) return "";
            try {
                int n = Integer.parseInt(sp[1]);
                List<Map.Entry<UUID, Double>> top = eco.top();
                if (n < 1 || n > top.size()) return "-";
                UUID who = top.get(n - 1).getKey();
                double val = top.get(n - 1).getValue();
                return switch (sp[2]) {
                    case "name" -> eco.name(who);
                    case "balance" -> eco.format(val);
                    case "balance_short" -> U.compact(val);
                    default -> "";
                };
            } catch (NumberFormatException e) { return ""; }
        }
        if (p == null) return "";
        UUID uid = p.getUniqueId();
        return switch (s) {
            case "balance" -> String.valueOf(eco.get(uid));
            case "balance_formatted" -> eco.format(eco.get(uid));
            case "balance_short" -> U.compact(eco.get(uid));
            case "rank" -> String.valueOf(eco.rank(uid));
            case "bounty" -> eco.format(pl.bounties().get(uid));
            case "bounty_short" -> U.compact(pl.bounties().get(uid));
            case "sell_multiplier" -> {
                Player online = p.getPlayer();
                yield online == null ? "1" : String.valueOf(pl.ranks().sellMultiplier(online));
            }
            default -> null;
        };
    }
}
