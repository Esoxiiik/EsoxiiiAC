package com.anticheatsystem.checks.player;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Sprawdzenie wykrywające graczy używających XRay
 */
public class XRayCheck extends Check {

    // Mapa gracza -> mapa materiału -> liczba wykopanych
    private final Map<UUID, Map<Material, Integer>> playerMiningStats = new HashMap<>();
    
    // Mapa gracza -> całkowita liczba wykopanych bloków
    private final Map<UUID, Integer> totalMined = new HashMap<>();
    
    // Mapa gracza -> czas ostatniego resetu statystyk
    private final Map<UUID, Long> lastReset = new HashMap<>();
    
    // Cenne materiały, które są częściej wykopywane przy użyciu XRay
    private final List<Material> valuableMaterials = Arrays.asList(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.ANCIENT_DEBRIS,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE
    );
    
    // Minimalny stosunek cennych rud do całkowitej liczby wykopanych bloków
    private static final double MIN_RATIO_THRESHOLD = 0.25; // 25%
    
    // Minimalna liczba wykopanych bloków do analizy
    private static final int MIN_BLOCKS_TO_CHECK = 30;
    
    // Czas resetu statystyk (w milisekundach)
    private static final long RESET_TIME = 1800000; // 30 minut
    
    public XRayCheck(AntiCheatMain plugin) {
        super(plugin, "xray", "player");
    }
    
    /**
     * Sprawdza, czy gracz używa XRay
     * 
     * @param player Gracz do sprawdzenia
     * @param block Wykopany blok
     */
    public void checkXRay(Player player, Block block) {
        // Ignoruj graczy w trybie kreatywnym
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        Material material = block.getType();
        
        // Sprawdź, czy minął czas resetu
        long currentTime = System.currentTimeMillis();
        if (!lastReset.containsKey(playerId) || 
                currentTime - lastReset.get(playerId) > RESET_TIME) {
            // Resetuj statystyki
            playerMiningStats.put(playerId, new HashMap<>());
            totalMined.put(playerId, 0);
            lastReset.put(playerId, currentTime);
        }
        
        // Pobierz lub utwórz statystyki dla gracza
        Map<Material, Integer> miningStats = playerMiningStats.computeIfAbsent(playerId, k -> new HashMap<>());
        
        // Zwiększ licznik dla tego materiału
        miningStats.put(material, miningStats.getOrDefault(material, 0) + 1);
        
        // Zwiększ licznik całkowitej liczby wykopanych bloków
        int total = totalMined.getOrDefault(playerId, 0) + 1;
        totalMined.put(playerId, total);
        
        // Analizuj tylko, jeśli gracz wykopał wystarczająco dużo bloków
        if (total >= MIN_BLOCKS_TO_CHECK) {
            analyzeXRayPattern(player, miningStats, total);
        }
    }
    
    /**
     * Analizuje wzorzec wydobycia pod kątem użycia XRay
     */
    private void analyzeXRayPattern(Player player, Map<Material, Integer> miningStats, int totalBlocks) {
        // Oblicz całkowitą liczbę wykopanych cennych rud
        int valuableCount = 0;
        
        for (Material material : valuableMaterials) {
            valuableCount += miningStats.getOrDefault(material, 0);
        }
        
        // Oblicz stosunek cennych rud do całkowitej liczby wykopanych bloków
        double ratio = (double) valuableCount / totalBlocks;
        
        // Jeśli stosunek jest zbyt wysoki, prawdopodobnie gracz używa XRay
        if (ratio > MIN_RATIO_THRESHOLD) {
            String details = String.format("diamond=%d, emerald=%d, gold=%d, ratio=%.2f%%", 
                    miningStats.getOrDefault(Material.DIAMOND_ORE, 0) + miningStats.getOrDefault(Material.DEEPSLATE_DIAMOND_ORE, 0),
                    miningStats.getOrDefault(Material.EMERALD_ORE, 0) + miningStats.getOrDefault(Material.DEEPSLATE_EMERALD_ORE, 0),
                    miningStats.getOrDefault(Material.GOLD_ORE, 0) + miningStats.getOrDefault(Material.DEEPSLATE_GOLD_ORE, 0),
                    ratio * 100);
            
            // Ustal poziom naruszenia na podstawie przekroczenia
            int violationLevel = (int) Math.ceil((ratio - MIN_RATIO_THRESHOLD) * 20);
            
            // Ogranicz do maksymalnej wartości dla tego sprawdzenia
            violationLevel = Math.min(violationLevel, maxViolationsPerCheck);
            
            flag(player, violationLevel, details);
        }
    }
}