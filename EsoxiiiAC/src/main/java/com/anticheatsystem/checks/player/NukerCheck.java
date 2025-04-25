package com.anticheatsystem.checks.player;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprawdzenie wykrywające graczy używających Nuker hacka (zbyt szybkie kopanie bloków)
 */
public class NukerCheck extends Check {

    // Mapa przechowująca dane o kopaniu bloków przez graczy
    private final Map<UUID, BreakData> breakData = new ConcurrentHashMap<>();
    
    // Minimalny czas między zniszczeniami bloków (w ms)
    private static final long MIN_BREAK_TIME = 190; // Typowe dla dobrego gracza
    
    // Minimalne opóźnienie między zniszczeniami dla różnych typów bloków
    private static final Map<Material, Long> BLOCK_BREAK_TIMES = new HashMap<>();
    
    // Maksymalna liczba zniszczonych bloków w krótkim czasie
    private static final int MAX_BLOCKS_IN_TIME_FRAME = 10;
    
    // Okres czasu do analizy (w ms)
    private static final long TIME_FRAME = 2000;
    
    // Maksymalny kąt między zniszczonymi blokami dla wykrycia multi-break
    private static final double MAX_ANGLE_THRESHOLD = 45.0;
    
    // Inicjalizuj czasy kopania różnych bloków
    static {
        // Miękkie bloki
        BLOCK_BREAK_TIMES.put(Material.DIRT, 200L);
        BLOCK_BREAK_TIMES.put(Material.GRASS_BLOCK, 200L);
        BLOCK_BREAK_TIMES.put(Material.SAND, 200L);
        BLOCK_BREAK_TIMES.put(Material.GRAVEL, 200L);
        BLOCK_BREAK_TIMES.put(Material.CLAY, 250L);
        
        // Drewno i liście
        BLOCK_BREAK_TIMES.put(Material.OAK_LOG, 300L);
        BLOCK_BREAK_TIMES.put(Material.BIRCH_LOG, 300L);
        BLOCK_BREAK_TIMES.put(Material.SPRUCE_LOG, 300L);
        BLOCK_BREAK_TIMES.put(Material.JUNGLE_LOG, 300L);
        BLOCK_BREAK_TIMES.put(Material.ACACIA_LOG, 300L);
        BLOCK_BREAK_TIMES.put(Material.DARK_OAK_LOG, 300L);
        BLOCK_BREAK_TIMES.put(Material.OAK_LEAVES, 200L);
        
        // Kamień i ruda
        BLOCK_BREAK_TIMES.put(Material.STONE, 400L);
        BLOCK_BREAK_TIMES.put(Material.COBBLESTONE, 400L);
        BLOCK_BREAK_TIMES.put(Material.IRON_ORE, 450L);
        BLOCK_BREAK_TIMES.put(Material.GOLD_ORE, 450L);
        BLOCK_BREAK_TIMES.put(Material.DIAMOND_ORE, 500L);
        BLOCK_BREAK_TIMES.put(Material.COAL_ORE, 400L);
        
        // Trudniejsze materiały
        BLOCK_BREAK_TIMES.put(Material.OBSIDIAN, 9000L);
    }
    
    public NukerCheck(AntiCheatMain plugin) {
        super(plugin, "nuker", "player");
    }
    
    /**
     * Sprawdza, czy gracz używa Nuker hacka
     * 
     * @param player Gracz do sprawdzenia
     * @param block Zniszczony blok
     * @param event Wydarzenie zniszczenia bloku
     */
    public void checkNuker(Player player, Block block, BlockBreakEvent event) {
        // Ignoruj graczy w trybie kreatywnym
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Pobierz lub utwórz dane dla gracza
        BreakData data = breakData.computeIfAbsent(playerId, k -> new BreakData());
        
        // Aktualizuj dane
        data.addBlockBreak(currentTime, player.getLocation(), block.getLocation(), block.getType());
        
        // Sprawdź interwał między zniszczeniami
        checkBreakInterval(player, data, block, currentTime);
        
        // Sprawdź wzorce zniszczeń
        checkBreakPattern(player, data);
        
        // Sprawdź zakres zniszczeń (Nuker często kopie w wielu kierunkach naraz)
        checkBreakRadius(player, data);
    }
    
    /**
     * Sprawdza czas między zniszczeniami bloków
     */
    private void checkBreakInterval(Player player, BreakData data, Block block, long currentTime) {
        List<BreakInfo> breakHistory = data.getBreakHistory();
        
        // Jeśli to nie pierwsze zniszczenie
        if (breakHistory.size() >= 2) {
            BreakInfo lastBreak = breakHistory.get(breakHistory.size() - 2);
            long timeDiff = currentTime - lastBreak.time;
            
            // Pobierz minimalny czas kopania dla tego typu bloku
            long minBreakTime = getMinBreakTime(block.getType(), player);
            
            // Jeśli czas między zniszczeniami jest zbyt krótki
            if (timeDiff < minBreakTime) {
                String details = String.format("fast_break: %dms (min: %dms) dla %s", 
                        timeDiff, minBreakTime, block.getType().name());
                flag(player, calculateViolationLevel(timeDiff, minBreakTime), details);
            }
            
            // Sprawdź liczbę zniszczonych bloków w określonym przedziale czasu
            int blocksInTimeFrame = 0;
            for (int i = breakHistory.size() - 1; i >= 0; i--) {
                if (currentTime - breakHistory.get(i).time <= TIME_FRAME) {
                    blocksInTimeFrame++;
                } else {
                    break;
                }
            }
            
            // Sprawdź typ materiału - dla niektórych typów jak liście, zwiększ limit
            int adjustedLimit = MAX_BLOCKS_IN_TIME_FRAME;
            if (block.getType().name().contains("LEAVES")) {
                adjustedLimit *= 2; // Liście można niszczyć szybciej
            }
            
            // Jeśli zniszczono zbyt wiele bloków w krótkim czasie
            if (blocksInTimeFrame > adjustedLimit) {
                String details = String.format("multi_break: %d bloków w %.1f s", 
                        blocksInTimeFrame, TIME_FRAME / 1000.0);
                flag(player, Math.min(blocksInTimeFrame - adjustedLimit + 3, maxViolationsPerCheck), details);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " podejrzany o nuker: " + details);
            }
        }
    }
    
    /**
     * Oblicza minimalny czas zniszczenia bloku, uwzględniając narzędzie i efekty
     */
    private long getMinBreakTime(Material blockType, Player player) {
        // Pobierz bazowy czas zniszczenia
        long baseTime = BLOCK_BREAK_TIMES.getOrDefault(blockType, MIN_BREAK_TIME);
        
        // Sprawdź, czy gracz ma właściwe narzędzie do kopania tego bloku
        ItemStack tool = player.getInventory().getItemInMainHand();
        double toolMultiplier = getToolMultiplier(tool, blockType);
        
        // Sprawdź, czy gracz ma efekt szybkiego kopania
        double hasteMultiplier = 1.0;
        PotionEffect hasteEffect = player.getPotionEffect(PotionEffectType.FAST_DIGGING);
        if (hasteEffect != null) {
            int amplifier = hasteEffect.getAmplifier() + 1; // 0-based do 1-based
            hasteMultiplier = 1.0 - (amplifier * 0.2); // 20% szybciej na poziom
        }
        
        // Oblicz całkowity czas zniszczenia
        long adjustedTime = (long) (baseTime * toolMultiplier * hasteMultiplier);
        
        // Ustal minimalny czas na 50ms dla zapobiegania fałszywym flagom
        return Math.max(adjustedTime, 50);
    }
    
    /**
     * Pobiera mnożnik czasu zniszczenia na podstawie narzędzia
     */
    private double getToolMultiplier(ItemStack tool, Material blockType) {
        if (tool == null) {
            return 1.0; // Bez narzędzia
        }
        
        Material toolType = tool.getType();
        String blockName = blockType.name();
        
        // Sprawdź dopasowanie narzędzia do bloku
        if (blockName.contains("STONE") || blockName.contains("ORE") || blockName.contains("COBBLESTONE")) {
            if (toolType.name().contains("PICKAXE")) {
                if (toolType.name().contains("DIAMOND")) return 0.4;
                if (toolType.name().contains("IRON")) return 0.5;
                if (toolType.name().contains("STONE")) return 0.6;
                if (toolType.name().contains("WOODEN")) return 0.7;
                return 0.6; // Inne kilofy
            }
            return 1.5; // Niewłaściwe narzędzie
        }
        
        if (blockName.contains("DIRT") || blockName.contains("GRASS") || blockName.contains("SAND") || 
                blockName.contains("GRAVEL") || blockName.contains("CLAY")) {
            if (toolType.name().contains("SHOVEL")) {
                if (toolType.name().contains("DIAMOND")) return 0.4;
                if (toolType.name().contains("IRON")) return 0.5;
                if (toolType.name().contains("STONE")) return 0.6;
                if (toolType.name().contains("WOODEN")) return 0.7;
                return 0.6; // Inne łopaty
            }
            return 1.0; // Ręce są ok dla ziemi
        }
        
        if (blockName.contains("LOG") || blockName.contains("PLANK") || blockName.contains("WOOD")) {
            if (toolType.name().contains("AXE")) {
                if (toolType.name().contains("DIAMOND")) return 0.4;
                if (toolType.name().contains("IRON")) return 0.5;
                if (toolType.name().contains("STONE")) return 0.6;
                if (toolType.name().contains("WOODEN")) return 0.7;
                return 0.6; // Inne siekiery
            }
            return 1.2; // Niewłaściwe narzędzie, ale nie tak złe
        }
        
        if (blockName.contains("LEAVES")) {
            if (toolType.name().contains("SHEARS")) {
                return 0.3; // Nożyce są bardzo skuteczne dla liści
            }
            return 0.8; // Liście łatwo niszczyć
        }
        
        // Domyślny mnożnik
        return 1.0;
    }
    
    /**
     * Oblicza poziom naruszenia na podstawie różnicy czasu
     */
    private int calculateViolationLevel(long actualTime, long minTime) {
        if (actualTime <= 0) {
            return maxViolationsPerCheck; // Maksymalne naruszenie dla niemożliwych czasów
        }
        
        // Oblicz, ile razy za szybko zniszczono blok
        double speedFactor = (double) minTime / actualTime;
        
        // Przekształć współczynnik prędkości na poziom naruszenia
        int violationLevel = (int) Math.ceil(Math.min(speedFactor - 1.0, 5.0) * 2);
        
        // Ogranicz do zakresu
        return Math.min(Math.max(violationLevel, 1), maxViolationsPerCheck);
    }
    
    /**
     * Sprawdza wzorce zniszczeń bloków
     */
    private void checkBreakPattern(Player player, BreakData data) {
        List<BreakInfo> breakHistory = data.getBreakHistory();
        
        // Sprawdź tylko jeśli mamy wystarczająco dużo danych
        if (breakHistory.size() < 5) {
            return;
        }
        
        // Sprawdź, czy gracz niszczy bloki w zbyt regularny sposób
        List<Double> breakAngles = new ArrayList<>();
        
        for (int i = 2; i < breakHistory.size(); i++) {
            Location loc1 = breakHistory.get(i-2).blockLocation;
            Location loc2 = breakHistory.get(i-1).blockLocation;
            Location loc3 = breakHistory.get(i).blockLocation;
            
            // Pomiń, jeśli bloki są w różnych światach
            if (!loc1.getWorld().equals(loc2.getWorld()) || !loc2.getWorld().equals(loc3.getWorld())) {
                continue;
            }
            
            // Utwórz wektory między blokami
            org.bukkit.util.Vector vec1 = loc2.toVector().subtract(loc1.toVector());
            org.bukkit.util.Vector vec2 = loc3.toVector().subtract(loc2.toVector());
            
            // Pomiń, jeśli któryś z wektorów ma zerową długość
            if (vec1.lengthSquared() == 0 || vec2.lengthSquared() == 0) {
                continue;
            }
            
            // Oblicz kąt między wektorami
            double angle = Math.toDegrees(Math.acos(
                    vec1.dot(vec2) / (vec1.length() * vec2.length())
            ));
            
            breakAngles.add(angle);
        }
        
        // Jeśli mamy wystarczająco kątów do analizy
        if (breakAngles.size() >= 3) {
            // Oblicz odchylenie standardowe kątów
            double mean = breakAngles.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            double variance = breakAngles.stream()
                    .mapToDouble(a -> Math.pow(a - mean, 2))
                    .average()
                    .orElse(0);
            
            double stdDev = Math.sqrt(variance);
            
            // Jeśli odchylenie standardowe jest bardzo małe (zbyt regularne niszczenie)
            if (stdDev < 5.0 && mean < 10.0) {
                String details = String.format("regular_pattern: śr. kąt=%.2f°, odchylenie=%.2f°", 
                        mean, stdDev);
                flag(player, 4, details);
            }
        }
    }
    
    /**
     * Sprawdza promień zniszczeń (Nuker często kopie w wielu kierunkach naraz)
     */
    private void checkBreakRadius(Player player, BreakData data) {
        List<BreakInfo> recentBreaks = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        // Pobierz zniszczenia z ostatnich 2 sekund
        for (BreakInfo breakInfo : data.getBreakHistory()) {
            if (currentTime - breakInfo.time <= 2000) {
                recentBreaks.add(breakInfo);
            }
        }
        
        // Jeśli mamy co najmniej 3 zniszczenia
        if (recentBreaks.size() >= 3) {
            // Oblicz kąty między kolejnymi zniszczeniami z punktu widzenia gracza
            int suspiciousAngles = 0;
            
            for (int i = 0; i < recentBreaks.size() - 1; i++) {
                for (int j = i + 1; j < recentBreaks.size(); j++) {
                    BreakInfo break1 = recentBreaks.get(i);
                    BreakInfo break2 = recentBreaks.get(j);
                    
                    // Pomiń, jeśli bloki są w różnych światach
                    if (!break1.playerLocation.getWorld().equals(break2.playerLocation.getWorld())) {
                        continue;
                    }
                    
                    // Oblicz wektory od gracza do bloków
                    org.bukkit.util.Vector toBlock1 = break1.blockLocation.toVector()
                            .subtract(break1.playerLocation.toVector());
                    org.bukkit.util.Vector toBlock2 = break2.blockLocation.toVector()
                            .subtract(break2.playerLocation.toVector());
                    
                    // Normalizuj wektory
                    toBlock1.normalize();
                    toBlock2.normalize();
                    
                    // Oblicz kąt między wektorami
                    double angle = Math.toDegrees(Math.acos(toBlock1.dot(toBlock2)));
                    
                    // Jeśli kąt jest zbyt duży, to podejrzane
                    if (angle > MAX_ANGLE_THRESHOLD && break2.time - break1.time < 500) {
                        suspiciousAngles++;
                    }
                }
            }
            
            // Jeśli jest zbyt wiele podejrzanych kątów, to może to być Nuker
            if (suspiciousAngles >= 3) {
                String details = "nuker_radius: " + suspiciousAngles + 
                        " par bloków zniszczonych pod podejrzanymi kątami";
                flag(player, Math.min(suspiciousAngles, maxViolationsPerCheck), details);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " podejrzany o nuker: " + details);
            }
        }
    }
    
    /**
     * Klasa przechowująca informacje o zniszczonym bloku
     */
    private static class BreakInfo {
        final long time;
        final Location playerLocation;
        final Location blockLocation;
        final Material blockType;
        
        BreakInfo(long time, Location playerLocation, Location blockLocation, Material blockType) {
            this.time = time;
            this.playerLocation = playerLocation.clone();
            this.blockLocation = blockLocation.clone();
            this.blockType = blockType;
        }
    }
    
    /**
     * Klasa przechowująca dane o zniszczeniach bloków przez gracza
     */
    private static class BreakData {
        private final List<BreakInfo> breakHistory = new ArrayList<>();
        
        void addBlockBreak(long time, Location playerLocation, Location blockLocation, Material blockType) {
            // Dodaj informacje o zniszczeniu
            breakHistory.add(new BreakInfo(time, playerLocation, blockLocation, blockType));
            
            // Ogranicz rozmiar historii
            while (breakHistory.size() > 50) {
                breakHistory.remove(0);
            }
        }
        
        List<BreakInfo> getBreakHistory() {
            return breakHistory;
        }
    }
}