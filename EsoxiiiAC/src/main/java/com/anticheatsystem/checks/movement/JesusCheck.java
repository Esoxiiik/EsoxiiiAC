package com.anticheatsystem.checks.movement;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprawdzenie wykrywające graczy używających Jesus hacka (chodzenie po wodzie)
 */
public class JesusCheck extends Check {

    // Mapa przechowująca dane o poruszaniu się graczy po płynach
    private final Map<UUID, LiquidData> liquidMovementData = new ConcurrentHashMap<>();
    
    // Minimalna liczba próbek do analizy
    private static final int MIN_SAMPLES = 5;
    
    // Maksymalny czas spędzony na płynie bez tonięcia (w ms)
    private static final long MAX_LIQUID_TIME = 2000;
    
    // Minimalny czas między flagami
    private static final long FLAG_COOLDOWN = 3000;
    
    public JesusCheck(AntiCheatMain plugin) {
        super(plugin, "jesus", "movement");
    }
    
    /**
     * Sprawdza, czy gracz używa Jesus hacka (chodzenie po wodzie/lawie)
     * 
     * @param player Gracz do sprawdzenia
     * @param from Poprzednia lokalizacja
     * @param to Nowa lokalizacja
     * @param event Wydarzenie poruszania się
     */
    public void checkJesus(Player player, Location from, Location to, PlayerMoveEvent event) {
        // Ignoruj graczy w trybie kreatywnym lub obserwatora
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        // Ignoruj graczy z efektem lewitacji
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || 
                player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return;
        }
        
        // Ignoruj graczy, którzy aktualnie lecą
        if (player.isFlying() || player.getAllowFlight()) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Sprawdź, czy gracz jest nad płynem (woda lub lawa)
        Block blockAt = to.getBlock();
        Block blockBelow = to.clone().subtract(0, 0.3, 0).getBlock();
        
        boolean isOnLiquid = isLiquid(blockAt) || isLiquid(blockBelow);
        
        // Pobierz lub utwórz dane dla gracza
        LiquidData data = liquidMovementData.computeIfAbsent(playerId, k -> new LiquidData());
        
        // Jeśli gracz jest nad płynem
        if (isOnLiquid) {
            // Jeśli to pierwszy kontakt z płynem
            if (!data.isOnLiquid()) {
                data.setOnLiquid(true);
                data.setLiquidStartTime(currentTime);
            }
            
            // Dodaj próbkę ruchu
            double verticalSpeed = to.getY() - from.getY();
            data.addSample(currentTime, verticalSpeed, to.getY());
            
            // Sprawdź, czy gracz stoi na płynie zbyt długo
            if (currentTime - data.getLiquidStartTime() > MAX_LIQUID_TIME) {
                checkLiquidMovement(player, data);
            }
        } else {
            // Gracz nie jest już nad płynem, resetuj flagę
            data.setOnLiquid(false);
        }
    }
    
    /**
     * Sprawdza, czy blok jest płynem (woda lub lawa)
     */
    private boolean isLiquid(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.LAVA;
    }
    
    /**
     * Analizuje ruch gracza po płynie
     */
    private void checkLiquidMovement(Player player, LiquidData data) {
        List<Sample> samples = data.getSamples();
        
        // Sprawdź tylko jeśli mamy wystarczająco dużo próbek
        if (samples.size() < MIN_SAMPLES) {
            return;
        }
        
        // Sprawdź, czy gracz nie tonie (typowe dla Jesus hacka)
        boolean sinking = false;
        double avgVerticalSpeed = 0;
        int stationarySamples = 0;
        
        for (Sample sample : samples) {
            // Jeśli gracz opada, to normalne zachowanie
            if (sample.verticalSpeed < -0.01) {
                sinking = true;
            }
            
            // Aktualizuj średnią prędkość
            avgVerticalSpeed += sample.verticalSpeed;
            
            // Licz próbki, gdzie gracz prawie nie porusza się w pionie
            if (Math.abs(sample.verticalSpeed) < 0.001) {
                stationarySamples++;
            }
        }
        
        avgVerticalSpeed /= samples.size();
        
        // Jeśli gracz nie tonie i nie jest w pobliżu brzegu lub innej konstrukcji
        if (!sinking && !isNearSolid(player.getLocation())) {
            // Sprawdź, czy gracz utrzymuje stabilną wysokość (typowe dla Jesus hacka)
            double heightVariance = calculateHeightVariance(samples);
            
            // Jeśli wariancja wysokości jest bardzo mała (gracz utrzymuje stałą wysokość)
            if (heightVariance < 0.001 && stationarySamples >= MIN_SAMPLES / 2) {
                // Sprawdź cooldown flagowania
                long lastFlagTime = data.getLastFlagTime();
                long currentTime = System.currentTimeMillis();
                
                if (currentTime - lastFlagTime > FLAG_COOLDOWN) {
                    String details = String.format("jesus: stabilna wysokość na płynie (wariancja=%.5f, próbki=%d)", 
                            heightVariance, samples.size());
                    flag(player, 5, details);
                    
                    // Aktualizuj czas ostatniej flagi
                    data.setLastFlagTime(currentTime);
                    
                    // Powiadom administratorów
                    plugin.notifyAdmins("Gracz " + player.getName() + 
                            " prawdopodobnie używa Jesus hack: " + details);
                }
            }
        }
        
        // Wyczyść stare próbki
        long oldestAllowed = System.currentTimeMillis() - 5000; // ostatnie 5 sekund
        data.removeSamplesOlderThan(oldestAllowed);
    }
    
    /**
     * Oblicza wariancję wysokości z próbek
     */
    private double calculateHeightVariance(List<Sample> samples) {
        // Oblicz średnią wysokość
        double meanHeight = samples.stream()
                .mapToDouble(s -> s.y)
                .average()
                .orElse(0);
        
        // Oblicz wariancję
        return samples.stream()
                .mapToDouble(s -> Math.pow(s.y - meanHeight, 2))
                .average()
                .orElse(0);
    }
    
    /**
     * Sprawdza, czy gracz jest w pobliżu stałych bloków
     */
    private boolean isNearSolid(Location location) {
        // Sprawdź bloki wokół gracza
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = location.clone().add(x, y, z).getBlock();
                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Klasa przechowująca próbkę ruchu
     */
    private static class Sample {
        final long time;
        final double verticalSpeed;
        final double y;
        
        Sample(long time, double verticalSpeed, double y) {
            this.time = time;
            this.verticalSpeed = verticalSpeed;
            this.y = y;
        }
    }
    
    /**
     * Klasa przechowująca dane o ruchu po płynie
     */
    private static class LiquidData {
        private boolean onLiquid = false;
        private long liquidStartTime = 0;
        private long lastFlagTime = 0;
        private final List<Sample> samples = new ArrayList<>();
        
        void addSample(long time, double verticalSpeed, double y) {
            samples.add(new Sample(time, verticalSpeed, y));
            
            // Ogranicz rozmiar listy
            while (samples.size() > 100) {
                samples.remove(0);
            }
        }
        
        void removeSamplesOlderThan(long time) {
            samples.removeIf(sample -> sample.time < time);
        }
        
        List<Sample> getSamples() {
            return samples;
        }
        
        boolean isOnLiquid() {
            return onLiquid;
        }
        
        void setOnLiquid(boolean onLiquid) {
            this.onLiquid = onLiquid;
        }
        
        long getLiquidStartTime() {
            return liquidStartTime;
        }
        
        void setLiquidStartTime(long liquidStartTime) {
            this.liquidStartTime = liquidStartTime;
        }
        
        long getLastFlagTime() {
            return lastFlagTime;
        }
        
        void setLastFlagTime(long lastFlagTime) {
            this.lastFlagTime = lastFlagTime;
        }
    }
}