package com.anticheatsystem.config;

import org.bukkit.ChatColor;

/**
 * Przechowuje akcję, która zostanie wykonana, gdy liczba naruszeń przekroczy określony próg
 */
public class ActionStep {

    private final int violations;
    private final String action;
    
    /**
     * Tworzy nowy krok akcji
     * 
     * @param violations Liczba naruszeń wymagana do wykonania tej akcji
     * @param action Akcja do wykonania
     */
    public ActionStep(int violations, String action) {
        this.violations = violations;
        this.action = action;
    }
    
    /**
     * Pobiera liczbę naruszeń wymaganą do wykonania tej akcji
     */
    public int getViolations() {
        return violations;
    }
    
    /**
     * Pobiera akcję do wykonania
     */
    public String getAction() {
        return action;
    }
    
    /**
     * Tworzy komendę do wykonania, zastępując zmienne
     * 
     * @param playerName Nazwa gracza, dla którego tworzona jest komenda
     * @return Komenda gotowa do wykonania
     */
    public String createCommand(String playerName) {
        String result = action;
        
        // Jeśli akcja zaczyna się od "cmd:", to usuń ten prefiks
        if (result.startsWith("cmd:")) {
            result = result.substring(4);
        }
        
        // Zastąp zmienne
        result = result
                .replace("%player%", playerName)
                .replace("&", "§");
        
        return result;
    }
    
    /**
     * Sprawdza, czy akcja jest komendą
     */
    public boolean isCommand() {
        return action.startsWith("cmd:");
    }
    
    /**
     * Sprawdza, czy akcja jest ostrzeżeniem dla gracza
     */
    public boolean isWarn() {
        return action.startsWith("warn ");
    }
    
    /**
     * Sprawdza, czy akcja jest ogłoszeniem
     */
    public boolean isBroadcast() {
        return action.startsWith("broadcast ");
    }
    
    /**
     * Pobiera wiadomość dla akcji typu ostrzeżenie lub ogłoszenie
     */
    public String getMessage() {
        String result = action;
        
        // Usuń prefiks
        if (isWarn()) {
            result = result.substring(5);
        } else if (isBroadcast()) {
            result = result.substring(10);
        }
        
        // Przetwórz kolory
        return ChatColor.translateAlternateColorCodes('&', result);
    }
}