package com.anticheatsystem.protections;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * System ochrony przed atakami typu DoS i crash
 */
public class AntiCrash implements Listener {

    private final AntiCheatMain plugin;
    
    // Limity dla różnych działań
    private static final int MAX_ENTITIES_PER_CHUNK = 50;
    private static final int MAX_REDSTONE_PER_SECOND = 100;
    private static final int MAX_DROPS_PER_SECOND = 15;
    private static final int MAX_BOOK_PAGE_LENGTH = 500;
    private static final int MAX_REPEATING_CHARS = 200;
    
    // Mapy do śledzenia aktywności
    private final Map<UUID, Integer> dropCounter = new ConcurrentHashMap<>();
    private final Map<String, Integer> redstoneCounter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWarningTime = new ConcurrentHashMap<>();
    
    // Wzorce dla niebezpiecznych komend
    private final Pattern dangerousCommands = Pattern.compile(
            "/(?:execute|fill|clone|setblock|summon|tp|teleport).*", 
            Pattern.CASE_INSENSITIVE);
    
    public AntiCrash(AntiCheatMain plugin) {
        this.plugin = plugin;
        
        // Uruchom zadanie czyszczące liczniki
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            dropCounter.clear();
            redstoneCounter.clear();
        }, 20L, 20L); // Co sekundę
    }
    
    /**
     * Blokuje upuszczanie zbyt wielu przedmiotów przez gracza
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Pomiń, jeśli gracz ma uprawnienia do omijania
        if (player.hasPermission("anticheat.bypass.crash")) {
            return;
        }
        
        // Zwiększ licznik upuszczonych przedmiotów
        int drops = dropCounter.getOrDefault(playerId, 0) + 1;
        dropCounter.put(playerId, drops);
        
        // Jeśli gracz upuścił zbyt wiele przedmiotów
        if (drops > MAX_DROPS_PER_SECOND) {
            event.setCancelled(true);
            
            // Wyślij ostrzeżenie (maksymalnie raz na 5 sekund)
            long currentTime = System.currentTimeMillis();
            long lastTime = lastWarningTime.getOrDefault(playerId, 0L);
            
            if (currentTime - lastTime > 5000) {
                player.sendMessage(ChatColor.RED + "Upuszczasz przedmioty zbyt szybko!");
                lastWarningTime.put(playerId, currentTime);
                
                // Zgłoś administratorom
                plugin.notifyAdmins(player.getName() + " prawdopodobnie próbuje zrzucić zbyt wiele przedmiotów " +
                        "(anti-crash) - " + drops + " przedmiotów/sekundę");
            }
        }
    }
    
    /**
     * Ogranicza aktywność redstone, aby zapobiec atakom lag-machine
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        // Pobierz identyfikator chunka
        String chunkKey = event.getBlock().getWorld().getName() + "," + 
                (event.getBlock().getX() >> 4) + "," + (event.getBlock().getZ() >> 4);
        
        // Zwiększ licznik aktywności redstone dla tego chunka
        int count = redstoneCounter.getOrDefault(chunkKey, 0) + 1;
        redstoneCounter.put(chunkKey, count);
        
        // Jeśli przekroczono limit
        if (count > MAX_REDSTONE_PER_SECOND) {
            event.setNewCurrent(0); // Wyłącz redstone
            
            // Loguj tylko raz na sekundę dla danego chunka
            if (count == MAX_REDSTONE_PER_SECOND + 1) {
                plugin.getPluginLogger().warning("Wykryto nadmierną aktywność redstone w chunku " + 
                        chunkKey + " - możliwa próba lag-machine");
            }
        }
    }
    
    /**
     * Ogranicza liczbę bytów per chunk, aby zapobiec server crashom
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        // Pobierz chunk, w którym spawnuje się byt
        int entityCount = 0;
        
        // Policz byty w tym chunku
        for (Entity entity : event.getLocation().getChunk().getEntities()) {
            entityCount++;
        }
        
        // Jeśli przekroczono limit bytów dla chunka
        if (entityCount > MAX_ENTITIES_PER_CHUNK) {
            event.setCancelled(true);
            
            // Jeśli to upuszczony przedmiot i jest ich dużo, wyślij ostrzeżenie
            if (event.getEntity() instanceof Item) {
                plugin.getPluginLogger().warning("Zablokowano spawn przedmiotu w chunku " + 
                        event.getLocation().getChunk().getX() + "," + event.getLocation().getChunk().getZ() + 
                        " - osiągnięto limit bytów (" + entityCount + ")");
            }
        }
    }
    
    /**
     * Ogranicza spawnienie zbyt wielu mobów naraz
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Sprawdź, czy spawner tworzy zbyt wiele mobów
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            int mobCount = 0;
            
            // Policz moby w pobliżu (16 bloków)
            for (Entity entity : event.getLocation().getWorld().getNearbyEntities(
                    event.getLocation(), 16, 16, 16)) {
                if (entity.getType() == event.getEntityType()) {
                    mobCount++;
                    
                    // Jeśli jest ich zbyt wiele
                    if (mobCount > 20) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }
    
    /**
     * Blokuje niebezpieczne komendy, które mogą powodować lagi/crashe
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage();
        
        // Pomiń, jeśli gracz ma uprawnienia do omijania
        if (player.hasPermission("anticheat.bypass.crash")) {
            return;
        }
        
        // Sprawdź czy komenda jest potencjalnie niebezpieczna
        if (dangerousCommands.matcher(command).matches()) {
            // Sprawdź czy zawiera potencjalnie niebezpieczne parametry
            if (command.contains("@e") || command.contains("@a") || 
                command.contains("fill") && command.contains("~") && command.contains("~10000")) {
                
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Ta komenda została zablokowana ze względów bezpieczeństwa.");
                
                // Zgłoś administratorom
                plugin.notifyAdmins(player.getName() + " próbował użyć potencjalnie niebezpiecznej komendy: " + 
                        command);
            }
        }
    }
    
    /**
     * Sprawdza książki pod kątem zbyt dużych zawartości
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // Pomiń, jeśli gracz ma uprawnienia do omijania
        if (player.hasPermission("anticheat.bypass.crash")) {
            return;
        }
        
        // Sprawdź, czy kliknięty przedmiot to książka
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getItemMeta() instanceof BookMeta) {
            BookMeta meta = (BookMeta) item.getItemMeta();
            
            // Sprawdź czy książka ma zbyt długie strony
            if (meta.hasPages()) {
                for (String page : meta.getPages()) {
                    // Sprawdź długość strony
                    if (page.length() > MAX_BOOK_PAGE_LENGTH) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "Ta książka jest zbyt długa i została zablokowana.");
                        
                        // Zgłoś administratorom
                        plugin.notifyAdmins(player.getName() + " próbował użyć podejrzanej książki o długości " + 
                                page.length() + " znaków");
                        return;
                    }
                    
                    // Sprawdź powtarzające się znaki
                    checkForRepeatingChars(page, player, event);
                }
            }
        }
    }
    
    /**
     * Sprawdza, czy tekst zawiera zbyt wiele powtarzających się znaków
     */
    private void checkForRepeatingChars(String text, Player player, InventoryClickEvent event) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        char lastChar = text.charAt(0);
        int repeatCount = 1;
        int maxRepeat = 1;
        
        for (int i = 1; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            
            if (currentChar == lastChar) {
                repeatCount++;
                maxRepeat = Math.max(maxRepeat, repeatCount);
                
                if (maxRepeat > MAX_REPEATING_CHARS) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Ta książka zawiera podejrzane powtarzające się znaki.");
                    
                    // Zgłoś administratorom
                    plugin.notifyAdmins(player.getName() + " próbował użyć podejrzanej książki z " + 
                            maxRepeat + " powtórzeniami znaku '" + lastChar + "'");
                    return;
                }
            } else {
                repeatCount = 1;
                lastChar = currentChar;
            }
        }
    }
    
    /**
     * Blokuje niepożądane dyspenserów, które mogą być używane do exploitów
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDispense(BlockDispenseEvent event) {
        // Sprawdź, czy w chunku jest już dużo bytów
        int entityCount = event.getBlock().getChunk().getEntities().length;
        
        if (entityCount > MAX_ENTITIES_PER_CHUNK - 5) {
            event.setCancelled(true);
            
            plugin.getPluginLogger().warning("Zablokowano dyspenser w chunku " + 
                    event.getBlock().getChunk().getX() + "," + event.getBlock().getChunk().getZ() + 
                    " - zbyt wiele bytów w chunku (" + entityCount + ")");
        }
    }
}