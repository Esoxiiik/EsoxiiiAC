package com.anticheatsystem.checks.fly;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Bazowa klasa dla wszystkich sprawdzeń dotyczących latania.
 * Sprawdzenia w tej kategorii wykrywają nieautoryzowane latanie,
 * modyfikacje grawitacji lub inne nietypowe ruchy w pionie.
 */
public abstract class FlyCheck extends Check {

    protected String friendlyName;

    /**
     * Konstruktor dla sprawdzenia fly
     * 
     * @param plugin Instancja głównego pluginu
     * @param subType Podtyp sprawdzenia (np. "A", "B", itp.)
     * @param friendlyName Przyjazna nazwa
     */
    public FlyCheck(AntiCheatMain plugin, String subType, String friendlyName) {
        super(plugin, "fly." + subType, "fly");
        this.friendlyName = friendlyName;
    }
    
    /**
     * Metoda pomocnicza do sprawdzenia, czy gracz powinien podlegać sprawdzeniu
     * 
     * @param player Gracz do sprawdzenia
     * @return true jeśli gracz powinien być sprawdzany, false w przeciwnym razie
     */
    protected boolean shouldCheck(Player player) {
        // Ignoruj graczy w trybie kreatywnym lub spectator
        if (player.getAllowFlight() || !player.getGameMode().toString().equals("SURVIVAL")) {
            return false;
        }
        
        // Ignoruj graczy z efektem LEVITATION
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)) {
            return false;
        }
        
        // Ignoruj graczy w pojazdach
        if (player.isInsideVehicle()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Sprawdza, czy lokalizacja jest na ziemi
     * 
     * @param location Lokalizacja do sprawdzenia
     * @return true jeśli lokalizacja jest na ziemi (bloku), false w przeciwnym razie
     */
    protected boolean isOnGround(Location location) {
        // Sprawdź, czy blok pod graczem jest pełny
        return !location.clone().subtract(0, 0.1, 0).getBlock().isEmpty();
    }
    
    /**
     * Zwraca przyjazną nazwę sprawdzenia
     */
    public String getFriendlyName() {
        return this.friendlyName;
    }
}