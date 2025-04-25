package com.anticheatsystem.gui;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Menedżer interfejsów GUI dla systemu EsoxiiiAC
 * Odpowiada za tworzenie, otwieranie i obsługę zdarzeń GUI
 */
public class GUIManager implements Listener {

    private final AntiCheatMain plugin;
    
    // Mapowanie otwartych GUI do graczy
    private final Map<UUID, AbstractGUI> openGUIs = new HashMap<>();
    
    // Instancje GUI
    private MainGUI mainGUI;
    private BannedPlayersGUI bannedPlayersGUI;
    private CheckConfigGUI checkConfigGUI;
    
    /**
     * Konstruktor menedżera GUI
     * 
     * @param plugin Główna instancja pluginu
     */
    public GUIManager(AntiCheatMain plugin) {
        this.plugin = plugin;
        
        // Zarejestruj listenery zdarzeń
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Inicjalizuj instancje GUI
        this.mainGUI = new MainGUI(plugin);
        this.bannedPlayersGUI = new BannedPlayersGUI(plugin);
        this.checkConfigGUI = new CheckConfigGUI(plugin);
    }
    
    /**
     * Otwiera główne menu anty-cheata dla gracza
     * 
     * @param player Gracz, dla którego ma być otwarte menu
     */
    public void openMainGUI(Player player) {
        openGUI(player, mainGUI);
    }
    
    /**
     * Otwiera GUI z listą zbanowanych graczy
     * 
     * @param player Gracz, dla którego ma być otwarte menu
     */
    public void openBannedPlayersGUI(Player player) {
        openGUI(player, bannedPlayersGUI);
    }
    
    /**
     * Otwiera GUI konfiguracji sprawdzeń
     * 
     * @param player Gracz, dla którego ma być otwarte menu
     */
    public void openCheckConfigGUI(Player player) {
        openGUI(player, checkConfigGUI);
    }
    
    /**
     * Ogólna metoda do otwierania GUI dla gracza
     * 
     * @param player Gracz
     * @param gui GUI do otwarcia
     */
    private void openGUI(Player player, AbstractGUI gui) {
        // Zapisz informację o otwartym GUI dla gracza
        openGUIs.put(player.getUniqueId(), gui);
        
        // Otwórz GUI
        gui.open(player);
    }
    
    /**
     * Obsługa kliknięć w inwentarzu
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        
        // Sprawdź, czy inwentarz należy do naszego systemu GUI
        if (inventory.getHolder() instanceof AbstractGUI) {
            // Anuluj domyślną akcję kliknięcia (przeniesienie przedmiotu)
            event.setCancelled(true);
            
            // Przekaż zdarzenie do odpowiedniego GUI
            AbstractGUI gui = (AbstractGUI) inventory.getHolder();
            gui.handleClick(event);
        }
    }
    
    /**
     * Obsługa zamknięcia inwentarza
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Usuń wpis z mapy otwartych GUI
        openGUIs.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * Odświeża GUI dla wszystkich graczy, którzy mają je otwarte
     * Przydatne po zmianie konfiguracji
     */
    public void refreshAllGUIs() {
        for (Map.Entry<UUID, AbstractGUI> entry : openGUIs.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                AbstractGUI gui = entry.getValue();
                gui.populate();
                player.updateInventory();
            }
        }
    }
}