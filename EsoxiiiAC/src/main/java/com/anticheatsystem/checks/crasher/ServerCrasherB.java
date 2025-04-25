package com.anticheatsystem.checks.crasher;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.entity.Player;

/**
 * Sprawdzenie wykrywające próby crashowania serwera przez nadmierną ilość
 * pakietów ruchu ręką, stawiania bloków i zmiany trzymanego przedmiotu.
 */
public class ServerCrasherB extends ServerCrasherCheck {

    private int places = 0;
    private long lastSwitch = 0;
    private int swings = 0;
    private int switches = 0;

    public ServerCrasherB(AntiCheatMain plugin) {
        super(plugin, "B", "Server Crasher");
    }

    /**
     * Resetuje liczniki po otrzymaniu pakietu ruchu
     */
    public void handleFlyingPacket() {
        this.swings = this.places = 0;
    }

    /**
     * Obsługa naruszenia o określonym typie i ilości
     * 
     * @param player Gracz
     * @param type Typ naruszenia (1=swings, 2=places, 3=switches)
     * @param amount Ilość wykrytych akcji
     */
    public void handle(Player player, int type, int amount) {
        if (plugin.getConfigManager().isSchemEnabled()) {
            String details = String.format("Type: %s Amount: %s", type, amount);
            flag(player, 10, details);
            blockPlayer(player, details);
        }
    }

    /**
     * Obsługuje pakiet wymachiwania ręką
     * 
     * @param player Gracz, który wysłał pakiet
     */
    public void handleArmAnimation(Player player) {
        this.swings += 1;
        if (this.swings > 200) {
            this.handle(player, 1, this.swings);
        }
    }

    /**
     * Obsługuje pakiet stawiania bloku
     * 
     * @param player Gracz, który wysłał pakiet
     */
    public void handleBlockPlace(Player player) {
        this.places += 1;
        if (this.places > 200) {
            this.handle(player, 2, this.places);
        }
    }

    /**
     * Obsługuje pakiet zmiany trzymanego przedmiotu
     * 
     * @param player Gracz, który wysłał pakiet
     */
    public void handleHeldItemSlot(Player player) {
        long time = System.currentTimeMillis() - this.lastSwitch;
        if (time > 100L) {
            this.switches = 0;
            this.lastSwitch = System.currentTimeMillis();
        }
        
        if ((this.switches += 1) > 400) {
            this.handle(player, 3, this.switches);
        }
    }
}