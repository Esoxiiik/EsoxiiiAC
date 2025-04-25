package com.anticheatsystem.checks.combat;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zaawansowany system wykrywania KillAury używający wielu metod detekcji
 */
public class KillAuraCheck extends Check {

    // Mapa przechowująca dane o atakach graczy
    private final Map<UUID, AttackData> attackData = new ConcurrentHashMap<>();
    
    // Mapa przechowująca historię celów dla graczy
    private final Map<UUID, List<TargetInfo>> targetHistory = new ConcurrentHashMap<>();
    
    // Mapa przechowująca poprzedni cel gracza
    private final Map<UUID, UUID> lastTarget = new ConcurrentHashMap<>();
    
    // Mapa przechowująca ostatni czas ataku gracza
    private final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();
    
    // Mapa przechowująca liczbę celów w danym przedziale czasowym
    private final Map<UUID, Integer> targetsInTimeFrame = new ConcurrentHashMap<>();
    
    // Mapa przechowująca czas rozpoczęcia przedziału czasowego dla celów
    private final Map<UUID, Long> targetTimeFrameStart = new ConcurrentHashMap<>();
    
    // Maksymalna liczba celów w krótkim przedziale czasowym (5 sekund)
    private static final int MAX_TARGETS_IN_TIMEFRAME = 5;
    
    // Minimalny czas w ms między zmianami celu (możliwe tylko dla bardzo szybkich graczy)
    private static final long MIN_TARGET_SWITCH_TIME = 500;
    
    // Maksymalny kąt do wykrycia multi-aury (atakowania celów pod różnymi kątami)
    private static final double MAX_ANGLE_DIFFERENCE = 60.0;
    
    // Minimalna liczba ataków do analizy wzorców
    private static final int MIN_ATTACKS_FOR_PATTERN = 10;
    
    // Przedział czasu w ms do analizy czasów między atakami
    private static final long ATTACK_INTERVAL_TIMEFRAME = 5000;
    
    // Maksymalne odchylenie standardowe czasów między atakami (w ms) - zbyt małe oznacza bota
    private static final double MIN_ATTACK_INTERVAL_STDDEV = 35.0;
    
    public KillAuraCheck(AntiCheatMain plugin) {
        super(plugin, "killaura", "combat");
    }
    
    /**
     * Sprawdza atak gracza pod kątem KillAury
     * 
     * @param player Gracz wykonujący atak
     * @param target Cel ataku
     * @param event Wydarzenie ataku
     */
    public void checkKillAura(Player player, Entity target, EntityDamageByEntityEvent event) {
        // Ignoruj graczy w trybie kreatywnym lub obserwatora
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Aktualizuj dane o atakach
        updateAttackData(player, target, currentTime);
        
        // Pobierz lub utwórz historię celów
        List<TargetInfo> history = targetHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Dodaj nowy cel do historii
        TargetInfo newTarget = new TargetInfo(
                targetId, 
                currentTime, 
                player.getLocation(), 
                target.getLocation()
        );
        history.add(newTarget);
        
        // Ogranicz rozmiar historii
        while (history.size() > 20) {
            history.remove(0);
        }
        
        // Pobierz stary cel i zaktualizuj
        UUID oldTargetId = lastTarget.put(playerId, targetId);
        long lastTime = lastAttackTime.getOrDefault(playerId, 0L);
        lastAttackTime.put(playerId, currentTime);
        
        // Różne sprawdzenia na KillAurę
        checkMultiAura(player, history);
        checkSwitchAura(player, targetId, oldTargetId, lastTime, currentTime);
        checkAttackPattern(player);
        checkAimPattern(player, target);
        checkImpossibleAngles(player, target);
        checkHitMissRatio(player);
        checkHitStreak(player); // Sprawdza serię trafień i czas atakowania
    }
    
    /**
     * Aktualizuje dane o atakach gracza
     */
    private void updateAttackData(Player player, Entity target, long time) {
        UUID playerId = player.getUniqueId();
        
        // Pobierz lub utwórz dane o atakach
        AttackData data = attackData.computeIfAbsent(playerId, k -> new AttackData());
        
        // Dodaj czas ataku
        data.addAttackTime(time);
        
        // Dodaj trafienie
        data.addHit();
        
        // Ustaw cel ataku
        data.setTarget(target.getUniqueId());
    }
    
    /**
     * Sprawdza czy gracz atakuje wiele celów w różnych kierunkach (multi-aura)
     */
    private void checkMultiAura(Player player, List<TargetInfo> history) {
        if (history.size() < 3) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        
        // Resetuj licznik celów, jeśli minęło więcej niż 5 sekund od początku przedziału czasowego
        long currentTime = System.currentTimeMillis();
        long timeFrameStart = targetTimeFrameStart.getOrDefault(playerId, 0L);
        
        if (currentTime - timeFrameStart > 5000) {
            targetTimeFrameStart.put(playerId, currentTime);
            targetsInTimeFrame.put(playerId, 1);
        } else {
            // Sprawdź, czy to nowy cel
            boolean isNewTarget = true;
            UUID currentTargetId = history.get(history.size() - 1).targetId;
            
            for (int i = history.size() - 2; i >= Math.max(0, history.size() - 5); i--) {
                if (history.get(i).targetId.equals(currentTargetId)) {
                    isNewTarget = false;
                    break;
                }
            }
            
            // Jeśli to nowy cel, zwiększ licznik
            if (isNewTarget) {
                int count = targetsInTimeFrame.getOrDefault(playerId, 0) + 1;
                targetsInTimeFrame.put(playerId, count);
                
                // Jeśli gracz zaatakował zbyt wiele różnych celów w krótkim czasie
                if (count > MAX_TARGETS_IN_TIMEFRAME) {
                    String details = "multi_aura: " + count + " celów w ciągu 5 sekund";
                    flag(player, Math.min(count - MAX_TARGETS_IN_TIMEFRAME + 2, maxViolationsPerCheck), details);
                }
            }
        }
        
        // Sprawdź kąty między atakami
        checkAnglesBetweenTargets(player, history);
    }
    
    /**
     * Sprawdza kąty między atakami, by wykryć multi-aurę
     */
    private void checkAnglesBetweenTargets(Player player, List<TargetInfo> history) {
        if (history.size() < 3) {
            return;
        }
        
        int suspiciousAngles = 0;
        
        // Analizuj ostatnie ataki
        for (int i = 1; i < Math.min(6, history.size()); i++) {
            TargetInfo current = history.get(history.size() - i);
            
            for (int j = i + 1; j < Math.min(i + 4, history.size()); j++) {
                TargetInfo previous = history.get(history.size() - j);
                
                // Pomiń, jeśli to ten sam cel
                if (current.targetId.equals(previous.targetId)) {
                    continue;
                }
                
                // Pomiń, jeśli minęło zbyt dużo czasu między atakami
                if (current.time - previous.time > 3000) {
                    continue;
                }
                
                // Oblicz kąt między kierunkami do celów
                Vector currentDirection = current.targetLocation.toVector()
                        .subtract(current.playerLocation.toVector())
                        .normalize();
                
                Vector previousDirection = previous.targetLocation.toVector()
                        .subtract(previous.playerLocation.toVector())
                        .normalize();
                
                double angle = Math.toDegrees(Math.acos(
                        currentDirection.dot(previousDirection) / 
                        (currentDirection.length() * previousDirection.length())
                ));
                
                // Jeśli kąt jest zbyt duży, to podejrzane
                if (angle > MAX_ANGLE_DIFFERENCE) {
                    suspiciousAngles++;
                }
            }
        }
        
        // Jeśli jest zbyt wiele podejrzanych kątów, zgłoś naruszenie
        if (suspiciousAngles >= 2) {
            String details = "multi_angle_aura: " + suspiciousAngles + " podejrzanych kątów";
            flag(player, Math.min(suspiciousAngles, maxViolationsPerCheck), details);
        }
    }
    
    /**
     * Sprawdza czy gracz zbyt szybko przełącza cele (switch aura)
     */
    private void checkSwitchAura(Player player, UUID newTargetId, UUID oldTargetId, 
                               long lastTime, long currentTime) {
        // Jeśli to ten sam cel, pomiń
        if (oldTargetId != null && oldTargetId.equals(newTargetId)) {
            return;
        }
        
        // Jeśli to pierwszy atak, pomiń
        if (oldTargetId == null) {
            return;
        }
        
        // Oblicz czas między zmianami celu
        long timeDifference = currentTime - lastTime;
        
        // Jeśli zmiana celu była zbyt szybka
        if (timeDifference < MIN_TARGET_SWITCH_TIME) {
            String details = "switch_aura: zmiana celu w " + timeDifference + "ms";
            flag(player, 3, details);
        }
    }
    
    /**
     * Sprawdza wzorce czasowe ataków, aby wykryć boty i makra
     */
    private void checkAttackPattern(Player player) {
        UUID playerId = player.getUniqueId();
        AttackData data = attackData.get(playerId);
        
        if (data == null || data.getAttackTimes().size() < MIN_ATTACKS_FOR_PATTERN) {
            return;
        }
        
        // Pobierz czasy między atakami
        List<Long> intervals = new ArrayList<>();
        List<Long> attackTimes = data.getAttackTimes();
        
        for (int i = 1; i < attackTimes.size(); i++) {
            long interval = attackTimes.get(i) - attackTimes.get(i - 1);
            
            // Odfiltruj bardzo długie interwały (powyżej 2 sekund)
            if (interval < 2000) {
                intervals.add(interval);
            }
        }
        
        // Jeśli mamy wystarczająco dużo interwałów
        if (intervals.size() >= 5) {
            // Oblicz średnią i odchylenie standardowe
            double mean = intervals.stream().mapToDouble(Long::doubleValue).average().orElse(0);
            
            double variance = intervals.stream()
                    .mapToDouble(i -> Math.pow(i - mean, 2))
                    .average()
                    .orElse(0);
            
            double stdDev = Math.sqrt(variance);
            
            // Jeśli odchylenie standardowe jest zbyt małe (zbyt regularne ataki)
            if (stdDev < MIN_ATTACK_INTERVAL_STDDEV && mean < 500) {
                String details = String.format("bot_pattern: śr=%.2fms, stddev=%.2fms", mean, stdDev);
                flag(player, 5, details);
            }
        }
        
        // Wyczyść stare dane, jeśli minęło zbyt dużo czasu
        long currentTime = System.currentTimeMillis();
        long oldestAllowed = currentTime - ATTACK_INTERVAL_TIMEFRAME;
        
        attackTimes.removeIf(time -> time < oldestAllowed);
    }
    
    /**
     * Sprawdza wzorce celowania, aby wykryć aimboty
     */
    private void checkAimPattern(Player player, Entity target) {
        // Sprawdź, czy cel jest za graczem, ale gracz i tak go trafił
        Location playerLoc = player.getLocation();
        Location targetLoc = target.getLocation();
        
        Vector playerDirection = playerLoc.getDirection().normalize();
        Vector toTarget = targetLoc.toVector().subtract(playerLoc.toVector()).normalize();
        
        // Oblicz kąt między kierunkiem patrzenia gracza a kierunkiem do celu
        double angle = Math.toDegrees(Math.acos(playerDirection.dot(toTarget)));
        
        // Jeśli kąt jest zbyt duży (gracz nie patrzy na cel)
        if (angle > 90) {
            String details = String.format("aim_hack: atak poza polem widzenia (kąt=%.1f°)", angle);
            flag(player, 10, details);
        }
        
        // Sprawdź czy gracz atakuje z dużej odległości
        double distance = playerLoc.distance(targetLoc);
        
        // Maksymalny dozwolony zasięg ataku (uwzględniając lag)
        double maxAllowedDistance = 4.5;
        
        if (distance > maxAllowedDistance) {
            String details = String.format("distance_hack: zasięg=%.2f (max=%.2f)", 
                    distance, maxAllowedDistance);
            flag(player, 7, details);
        }
    }
    
    /**
     * Sprawdza czy gracz atakuje pod niemożliwymi kątami
     */
    private void checkImpossibleAngles(Player player, Entity target) {
        Location playerLoc = player.getLocation();
        Location targetLoc = target.getLocation();
        
        // Sprawdź kąt w pionie (pitch)
        double verticalDifference = targetLoc.getY() - playerLoc.getY();
        double horizontalDistance = Math.sqrt(
                Math.pow(targetLoc.getX() - playerLoc.getX(), 2) + 
                Math.pow(targetLoc.getZ() - playerLoc.getZ(), 2)
        );
        
        // Oblicz wymagany kąt w pionie do trafienia celu
        double requiredPitch = -Math.toDegrees(Math.atan2(verticalDifference, horizontalDistance));
        
        // Pobierz aktualny kąt gracza
        float playerPitch = playerLoc.getPitch();
        
        // Oblicz różnicę między wymaganym a aktualnym kątem
        double pitchDifference = Math.abs(requiredPitch - playerPitch);
        
        // Jeśli różnica jest zbyt duża, to podejrzane
        if (pitchDifference > 35) {
            String details = String.format("impossible_angle: różnica=%.1f°, wymagany=%.1f°, aktualny=%.1f°", 
                    pitchDifference, requiredPitch, playerPitch);
            flag(player, 5, details);
        }
        
        // Sprawdź czy gracz utrzymuje idealnie ten sam kąt patrzenia na cel (AimAssist)
        UUID playerId = player.getUniqueId();
        AttackData data = attackData.computeIfAbsent(playerId, k -> new AttackData());
        data.addAngle(playerLoc.getYaw(), playerLoc.getPitch());
        
        // Sprawdź historię kątów
        if (data.getYawHistory().size() >= 5 && data.getPitchHistory().size() >= 5) {
            // Oblicz zmiany kątów
            List<Float> yawHistory = data.getYawHistory();
            List<Float> pitchHistory = data.getPitchHistory();
            
            // Sprawdź czy zmiany kątów są zaokrąglone (znak AimAssist)
            for (int i = 1; i < yawHistory.size(); i++) {
                float yawChange = Math.abs(yawHistory.get(i) - yawHistory.get(i-1));
                float pitchChange = Math.abs(pitchHistory.get(i) - pitchHistory.get(i-1));
                
                // Sprawdź zaokrąglone zmiany kątów
                if (yawChange > 0 && Math.abs(Math.floor(yawChange) - yawChange) < 0.0000001) {
                    String details = "aim_assist: zaokrąglona zmiana kąta yaw";
                    flag(player, 4, details);
                    break;
                }
                
                // Sprawdź bardzo małe zmiany kąta pitch (typowe dla aim assist)
                if (pitchChange > 0 && pitchChange < 0.02) {
                    String details = String.format("aim_assist: zbyt mała zmiana pitch (%.6f)", pitchChange);
                    flag(player, 3, details);
                    break;
                }
            }
            
            // Sprawdź konsystencje kątów - czy gracz idealnie utrzymuje cel
            int consistentAngles = 0;
            for (int i = 2; i < yawHistory.size(); i++) {
                float yawChange1 = Math.abs(yawHistory.get(i) - yawHistory.get(i-1));
                float yawChange2 = Math.abs(yawHistory.get(i-1) - yawHistory.get(i-2));
                float pitchChange1 = Math.abs(pitchHistory.get(i) - pitchHistory.get(i-1));
                float pitchChange2 = Math.abs(pitchHistory.get(i-1) - pitchHistory.get(i-2));
                
                // Oblicz różnice w zmianach
                float yawDiff = Math.abs(yawChange1 - yawChange2);
                float pitchDiff = Math.abs(pitchChange1 - pitchChange2);
                
                // Jeśli zmiany są zbyt podobne, zwiększ licznik
                if (yawDiff < 0.1 && pitchDiff < 0.1) {
                    consistentAngles++;
                }
            }
            
            // Jeśli zbyt wiele kolejnych zmian kątów jest podobnych, to podejrzane
            if (consistentAngles >= 3) {
                String details = "aim_assist: zbyt konsystentne zmiany kątów";
                flag(player, 5, details);
            }
        }
    }
    
    /**
     * Sprawdza stosunek trafień do chybień (hit/miss ratio)
     */
    private void checkHitMissRatio(Player player) {
        UUID playerId = player.getUniqueId();
        AttackData data = attackData.get(playerId);
        
        if (data == null) {
            return;
        }
        
        int hits = data.getHits();
        int misses = data.getMisses();
        int totalAttempts = hits + misses;
        
        // Jeśli gracz ma zbyt mało ataków, pomiń
        if (totalAttempts < 20) {
            return;
        }
        
        // Oblicz stosunek trafień
        double hitRatio = (double) hits / totalAttempts;
        
        // Jeśli stosunek trafień jest podejrzanie wysoki
        if (hitRatio > 0.95) {
            String details = String.format("hit_ratio: %.2f%% (%d/%d)", 
                    hitRatio * 100, hits, totalAttempts);
            flag(player, 3, details);
        }
    }
    
    /**
     * Sprawdza serię trafień i czas atakowania, aby wykryć killaury
     */
    private void checkHitStreak(Player player) {
        UUID playerId = player.getUniqueId();
        AttackData data = attackData.get(playerId);
        
        if (data == null) {
            return;
        }
        
        // Sprawdź liczbę kolejnych trafień
        int consecutiveHits = data.getConsecutiveHits();
        
        // Jeśli gracz ma dużo trafień w serii
        if (consecutiveHits >= 8) {
            // Oblicz czas trwania serii
            long duration = data.getLastHitTime() - data.getFirstHitTime();
            
            // Oblicz średni czas między trafieniami
            double avgHitTime = (double) duration / (consecutiveHits - 1);
            
            String details = String.format("%d trafień w serii w ciągu %.1f sekund (śr. %.0fms/trafienie)", 
                    consecutiveHits, duration / 1000.0, avgHitTime);
            
            // Jeśli zbyt dużo trafień w zbyt krótkim czasie lub zbyt regularne trafienia
            if (avgHitTime < 200) {
                // Powiadom administratorów o podejrzanym graczu
                plugin.notifyAdmins(ChatColor.RED + "WYKRYTO KILLAURA: " + player.getName() + 
                        " - " + details);
                
                // Zgłoś naruszenie z wysokim poziomem
                flag(player, 8, "killaura: " + details);
            }
            // Sprawdź również, czy gracz idealnie utrzymuje spojrzenie na cel
            else if (consecutiveHits >= 12) {
                // Sprawdź odchylenie standardowe kątów patrzenia
                List<Float> yawHistory = data.getYawHistory();
                List<Float> pitchHistory = data.getPitchHistory();
                
                if (yawHistory.size() >= 10 && pitchHistory.size() >= 10) {
                    // Oblicz średnią i odchylenie standardowe dla kątów
                    double yawMean = yawHistory.stream().mapToDouble(Float::doubleValue).average().orElse(0);
                    double pitchMean = pitchHistory.stream().mapToDouble(Float::doubleValue).average().orElse(0);
                    
                    double yawVariance = yawHistory.stream()
                            .mapToDouble(y -> Math.pow(y - yawMean, 2))
                            .average()
                            .orElse(0);
                    
                    double pitchVariance = pitchHistory.stream()
                            .mapToDouble(p -> Math.pow(p - pitchMean, 2))
                            .average()
                            .orElse(0);
                    
                    double yawStdDev = Math.sqrt(yawVariance);
                    double pitchStdDev = Math.sqrt(pitchVariance);
                    
                    // Jeśli odchylenia są zbyt małe, to podejrzane (zbyt idealne celowanie)
                    if (yawStdDev < 1.5 && pitchStdDev < 1.5) {
                        details += String.format(" - zbyt precyzyjne celowanie (odchylenie: yaw=%.2f, pitch=%.2f)", 
                                yawStdDev, pitchStdDev);
                        
                        // Powiadom administratorów o podejrzanym graczu
                        plugin.notifyAdmins(ChatColor.RED + "WYKRYTO SUSPICIOUS AIM: " + player.getName() + 
                                " - " + details);
                        
                        // Zgłoś naruszenie
                        flag(player, 6, "suspicious_aim: " + details);
                    }
                }
            }
        }
        
        // Sprawdź wzorce czasu między trafieniami (wykrywanie botów)
        List<Long> hitTimes = data.getHitTimes();
        if (hitTimes.size() >= 5) {
            // Oblicz różnice między kolejnymi trafieniami
            List<Long> intervals = new ArrayList<>();
            for (int i = 1; i < hitTimes.size(); i++) {
                intervals.add(hitTimes.get(i) - hitTimes.get(i - 1));
            }
            
            // Oblicz średnią i odchylenie standardowe interwałów
            double mean = intervals.stream().mapToDouble(Long::doubleValue).average().orElse(0);
            double variance = intervals.stream()
                    .mapToDouble(i -> Math.pow(i - mean, 2))
                    .average()
                    .orElse(0);
            double stdDev = Math.sqrt(variance);
            
            // Jeśli interwały są zbyt regularne (bardzo mała zmienność)
            if (stdDev < 15 && mean < 500 && mean > 0) {
                String details = String.format("bot_like_timing: śr=%.2fms, stddev=%.2fms", mean, stdDev);
                flag(player, 7, details);
                
                // Powiadom administratorów o podejrzanym graczu
                plugin.notifyAdmins(ChatColor.RED + "WYKRYTO BOT-LIKE CLICKING: " + player.getName() + 
                        " - " + details);
            }
        }
    }
    
    /**
     * Zgłasza pudło (miss) dla gracza
     */
    public void registerMiss(Player player) {
        UUID playerId = player.getUniqueId();
        AttackData data = attackData.computeIfAbsent(playerId, k -> new AttackData());
        data.addMiss();
    }
    
    /**
     * Klasa przechowująca dane o celu
     */
    private static class TargetInfo {
        final UUID targetId;
        final long time;
        final Location playerLocation;
        final Location targetLocation;
        
        TargetInfo(UUID targetId, long time, Location playerLocation, Location targetLocation) {
            this.targetId = targetId;
            this.time = time;
            this.playerLocation = playerLocation.clone();
            this.targetLocation = targetLocation.clone();
        }
    }
    
    /**
     * Klasa przechowująca dane o atakach gracza
     */
    private static class AttackData {
        private final List<Long> attackTimes = new ArrayList<>();
        private final Map<UUID, Integer> targetsAttacked = new HashMap<>();
        private UUID currentTarget = null;
        private int hits = 0;
        private int misses = 0;
        private final List<Float> yawHistory = new ArrayList<>();
        private final List<Float> pitchHistory = new ArrayList<>();
        private final List<Long> hitTimes = new ArrayList<>(); // Lista czasów trafień
        private long firstHitTime = 0; // Czas pierwszego trafienia w serii
        private long lastHitTime = 0; // Czas ostatniego trafienia w serii
        private int consecutiveHits = 0; // Liczba kolejnych trafień w serii
        
        void addAttackTime(long time) {
            attackTimes.add(time);
            while (attackTimes.size() > 50) {
                attackTimes.remove(0);
            }
        }
        
        void setTarget(UUID targetId) {
            // Jeśli zmienił się cel, resetuj serię trafień
            if (currentTarget != null && !currentTarget.equals(targetId)) {
                resetHitStreak();
            }
            
            currentTarget = targetId;
            targetsAttacked.put(targetId, targetsAttacked.getOrDefault(targetId, 0) + 1);
        }
        
        void addHit() {
            hits++;
            
            // Aktualizuj dane o serii trafień
            long currentTime = System.currentTimeMillis();
            hitTimes.add(currentTime);
            
            // Ograniczenie rozmiaru listy
            while (hitTimes.size() > 30) {
                hitTimes.remove(0);
            }
            
            // Jeśli to pierwsze trafienie w serii
            if (consecutiveHits == 0) {
                firstHitTime = currentTime;
            }
            
            lastHitTime = currentTime;
            consecutiveHits++;
        }
        
        void addMiss() {
            misses++;
            resetHitStreak();
        }
        
        void resetHitStreak() {
            consecutiveHits = 0;
        }
        
        void addAngle(float yaw, float pitch) {
            yawHistory.add(yaw);
            pitchHistory.add(pitch);
            
            // Ogranicz rozmiar historii
            while (yawHistory.size() > 20) {
                yawHistory.remove(0);
            }
            
            while (pitchHistory.size() > 20) {
                pitchHistory.remove(0);
            }
        }
        
        List<Long> getAttackTimes() {
            return attackTimes;
        }
        
        List<Float> getYawHistory() {
            return yawHistory;
        }
        
        List<Float> getPitchHistory() {
            return pitchHistory;
        }
        
        List<Long> getHitTimes() {
            return hitTimes;
        }
        
        int getConsecutiveHits() {
            return consecutiveHits;
        }
        
        long getFirstHitTime() {
            return firstHitTime;
        }
        
        long getLastHitTime() {
            return lastHitTime;
        }
        
        int getHits() {
            return hits;
        }
        
        int getMisses() {
            return misses;
        }
    }
}