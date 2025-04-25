package com.anticheatsystem.checks.badpackets;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.entity.Player;

/**
 * Klasa bazowa dla wszystkich sprawdzeń dotyczących nieprawidłowych pakietów (BadPackets)
 * Sprawdzenia tego typu wykrywają nietypowe sekwencje pakietów, które mogą wskazywać
 * na używanie cheatów przez gracza.
 */
public abstract class BadPacketsCheck extends Check {

    protected String friendlyName; // Przyjazna nazwa tego sprawdzenia

    /**
     * Konstruktor dla sprawdzenia BadPackets
     * 
     * @param plugin Instancja głównego pluginu
     * @param subType Podtyp sprawdzenia (np. "A", "B", itp.)
     * @param friendlyName Przyjazna nazwa (np. "Timer", "KillAura")
     */
    public BadPacketsCheck(AntiCheatMain plugin, String subType, String friendlyName) {
        super(plugin, "badpackets." + subType, "badpackets");
        this.friendlyName = friendlyName;
    }
    
    /**
     * Pobiera przyjazną nazwę tego sprawdzenia
     * 
     * @return Przyjazna nazwa sprawdzenia
     */
    public String getFriendlyName() {
        return this.friendlyName;
    }
}