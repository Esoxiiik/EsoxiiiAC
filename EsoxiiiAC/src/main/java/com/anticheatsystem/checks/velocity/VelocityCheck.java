package com.anticheatsystem.checks.velocity;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Bazowa klasa dla wszystkich sprawdzeń dotyczących modyfikacji odbicia (velocity).
 * Sprawdzenia te wykrywają, gdy gracz ignoruje lub modyfikuje odbicie otrzymane
 * po uderzeniu, upadku, itd.
 */
public abstract class VelocityCheck extends Check {

    protected String friendlyName;
    
    /**
     * Konstruktor dla sprawdzenia velocity
     * 
     * @param plugin Instancja głównego pluginu
     * @param subType Podtyp sprawdzenia (np. "A", "B", itp.)
     * @param friendlyName Przyjazna nazwa
     */
    public VelocityCheck(AntiCheatMain plugin, String subType, String friendlyName) {
        super(plugin, "velocity." + subType, "velocity");
        this.friendlyName = friendlyName;
    }
    
    /**
     * Metoda pomocnicza do śledzenia odbicia gracza
     * 
     * @param player Gracz
     * @param velocity Wektor odbicia
     */
    protected void trackVelocity(Player player, Vector velocity) {
        // W pełnej implementacji należałoby zapisać to odbicie w jakimś menedżerze danych gracza
        // Dla uproszczenia implementacji, w tej wersji będziemy używać metadanych Bukkita
        player.setMetadata("esoxiiiac.lastvelocity", new org.bukkit.metadata.FixedMetadataValue(plugin, velocity));
        player.setMetadata("esoxiiiac.velocitytick", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
    }
    
    /**
     * Pobiera ostatnie odbicie gracza
     * 
     * @param player Gracz
     * @return Wektor odbicia lub null jeśli brak
     */
    protected Vector getLastVelocity(Player player) {
        if (!player.hasMetadata("esoxiiiac.lastvelocity")) {
            return null;
        }
        
        return (Vector) player.getMetadata("esoxiiiac.lastvelocity").get(0).value();
    }
    
    /**
     * Pobiera tick ostatniego odbicia
     * 
     * @param player Gracz
     * @return Timestamp ostatniego odbicia lub 0 jeśli brak
     */
    protected long getLastVelocityTick(Player player) {
        if (!player.hasMetadata("esoxiiiac.velocitytick")) {
            return 0;
        }
        
        return (long) player.getMetadata("esoxiiiac.velocitytick").get(0).value();
    }
    
    /**
     * Sprawdza, czy odbicie powinno być nadal brane pod uwagę
     * 
     * @param player Gracz
     * @return true jeśli odbicie jest nadal aktualne
     */
    protected boolean hasActiveVelocity(Player player) {
        long lastTick = getLastVelocityTick(player);
        if (lastTick == 0) return false;
        
        // Szacujemy czas ważności odbicia na podstawie pingu
        long timeout = 1000 + player.getPing() * 2; // 1 sekunda + 2x ping
        return System.currentTimeMillis() - lastTick < timeout;
    }
    
    /**
     * Zwraca przyjazną nazwę sprawdzenia
     */
    public String getFriendlyName() {
        return this.friendlyName;
    }
}