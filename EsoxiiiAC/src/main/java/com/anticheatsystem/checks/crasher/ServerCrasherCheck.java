package com.anticheatsystem.checks.crasher;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.entity.Player;

/**
 * Bazowa klasa dla sprawdzeń wykrywających próby crashowania serwera.
 * Tego typu sprawdzenia mają zwykle niski próg tolerancji i wysokie kary,
 * ponieważ są rzadko wywoływane przez przypadek.
 */
public abstract class ServerCrasherCheck extends Check {

    protected String friendlyName;

    /**
     * Konstruktor dla sprawdzenia ServerCrasher
     * 
     * @param plugin Instancja głównego pluginu
     * @param subType Podtyp sprawdzenia (np. "A", "B", itp.)
     * @param friendlyName Przyjazna nazwa
     */
    public ServerCrasherCheck(AntiCheatMain plugin, String subType, String friendlyName) {
        super(plugin, "servercrasher." + subType, "crasher");
        this.friendlyName = friendlyName;
    }
    
    /**
     * Blokuje gracza przy wykryciu próby crashowania serwera - 
     * bezpośrednio wyrzuca z serwera i może banować
     * 
     * @param player Gracz do zablokowania
     * @param details Szczegóły dotyczące wykrytego naruszenia
     */
    protected void blockPlayer(Player player, String details) {
        // Flaga naruszenie
        flag(player, 10, details);
        
        // Kicknij gracza
        player.kickPlayer("§cWykryto próbę crashowania serwera!");
        
        // Zapisz informację w logach
        plugin.getLogger().warning("Wykryto próbę crashowania serwera przez gracza " + 
                player.getName() + ": " + details);
        
        // Tutaj można dodać logikę banowania, jeśli jest zaimplementowana
    }
    
    /**
     * Zwraca przyjazną nazwę sprawdzenia
     */
    public String getFriendlyName() {
        return this.friendlyName;
    }
}