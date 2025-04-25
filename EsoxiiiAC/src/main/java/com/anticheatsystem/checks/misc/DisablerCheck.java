package com.anticheatsystem.checks.misc;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprawdzenie wykrywające graczy próbujących wyłączyć lub obejść system anti-cheat
 */
public class DisablerCheck extends Check {

    // Mapa przechowująca dane o pakietach gracza
    private final Map<UUID, DisablerData> playerData = new ConcurrentHashMap<>();
    
    // Maksymalna dopuszczalna liczba pakietów w odstępie czasu
    private static final int MAX_PACKETS_PER_SECOND = 50;
    
    // Maksymalna dopuszczalna liczba niekompletnych pakietów
    private static final int MAX_INCOMPLETE_PACKETS = 5;
    
    // Okres czasu do analizy (w ms)
    private static final long ANALYSIS_TIMEFRAME = 1000;
    
    // Cooldown między flagami
    private static final long FLAG_COOLDOWN = 5000;
    
    // Typy pakietów, które mogą być używane do ataków na anty-cheat
    private enum PacketType {
        POSITION, // Pakiety pozycji
        FLYING, // Pakiety latania
        ABILITIES, // Pakiety umiejętności
        TRANSACTION, // Pakiety transakcji
        KEEP_ALIVE, // Pakiety utrzymania połączenia
        CUSTOM, // Niestandardowe pakiety
        WINDOW, // Pakiety okien
        ARM_ANIMATION, // Pakiety animacji ręki
        BLOCK_DIG, // Pakiety kopania
        BLOCK_PLACE, // Pakiety stawiania bloków
        USE_ENTITY, // Pakiety użycia encji
        LOOK // Pakiety obrotu
    }
    
    public DisablerCheck(AntiCheatMain plugin) {
        super(plugin, "disabler", "misc");
    }
    
    /**
     * Rejestruje pakiet od gracza
     * 
     * @param player Gracz wysyłający pakiet
     * @param packetType Typ pakietu
     * @param isComplete Czy pakiet jest kompletny
     */
    public void registerPacket(Player player, String packetType, boolean isComplete) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Pobierz lub utwórz dane gracza
        DisablerData data = playerData.computeIfAbsent(playerId, k -> new DisablerData());
        
        // Dodaj pakiet
        data.addPacket(currentTime, getPacketTypeFromName(packetType), isComplete);
        
        // Sprawdź pakiety
        checkPacketFlow(player, data, currentTime);
    }
    
    /**
     * Konwertuje nazwę pakietu na enum
     */
    private PacketType getPacketTypeFromName(String packetName) {
        packetName = packetName.toLowerCase();
        
        if (packetName.contains("position")) {
            return PacketType.POSITION;
        } else if (packetName.contains("flying")) {
            return PacketType.FLYING;
        } else if (packetName.contains("abilities")) {
            return PacketType.ABILITIES;
        } else if (packetName.contains("transaction")) {
            return PacketType.TRANSACTION;
        } else if (packetName.contains("keep_alive")) {
            return PacketType.KEEP_ALIVE;
        } else if (packetName.contains("window")) {
            return PacketType.WINDOW;
        } else if (packetName.contains("arm") || packetName.contains("animation")) {
            return PacketType.ARM_ANIMATION;
        } else if (packetName.contains("dig") || packetName.contains("mining")) {
            return PacketType.BLOCK_DIG;
        } else if (packetName.contains("place") || packetName.contains("block_place")) {
            return PacketType.BLOCK_PLACE;
        } else if (packetName.contains("use") || packetName.contains("entity")) {
            return PacketType.USE_ENTITY;
        } else if (packetName.contains("look")) {
            return PacketType.LOOK;
        } else {
            return PacketType.CUSTOM;
        }
    }
    
    /**
     * Analizuje przepływ pakietów, aby wykryć próby wyłączenia anti-cheata
     */
    private void checkPacketFlow(Player player, DisablerData data, long currentTime) {
        // Analiza liczby pakietów
        int packetsInTimeFrame = 0;
        int incompletePackets = 0;
        Map<PacketType, Integer> packetCounts = new HashMap<>();
        
        // Analizuj pakiety z ostatniego okna czasowego
        for (PacketInfo packet : data.getPackets()) {
            if (currentTime - packet.time <= ANALYSIS_TIMEFRAME) {
                packetsInTimeFrame++;
                
                // Zlicz niekompletne pakiety
                if (!packet.isComplete) {
                    incompletePackets++;
                }
                
                // Zlicz pakiety według typu
                packetCounts.put(packet.type, packetCounts.getOrDefault(packet.type, 0) + 1);
            }
        }
        
        // Sprawdź, czy liczba pakietów jest zbyt duża
        if (packetsInTimeFrame > MAX_PACKETS_PER_SECOND) {
            // Unikaj zbyt częstego flagowania
            if (currentTime - data.getLastFlagTime() > FLAG_COOLDOWN) {
                String details = String.format("packet_flood: %d pakietów w %d ms (max: %d)", 
                        packetsInTimeFrame, ANALYSIS_TIMEFRAME, MAX_PACKETS_PER_SECOND);
                flag(player, Math.min((packetsInTimeFrame - MAX_PACKETS_PER_SECOND) / 10 + 1, maxViolationsPerCheck), details);
                
                // Aktualizuj czas ostatniej flagi
                data.setLastFlagTime(currentTime);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " prawdopodobnie używa disabler (packet flood): " + details);
            }
        }
        
        // Sprawdź niekompletne pakiety (często używane do ataków na anty-cheat)
        if (incompletePackets > MAX_INCOMPLETE_PACKETS) {
            // Unikaj zbyt częstego flagowania
            if (currentTime - data.getLastFlagTime() > FLAG_COOLDOWN) {
                String details = String.format("incomplete_packets: %d niekompletnych pakietów w %d ms", 
                        incompletePackets, ANALYSIS_TIMEFRAME);
                flag(player, Math.min(incompletePackets, maxViolationsPerCheck), details);
                
                // Aktualizuj czas ostatniej flagi
                data.setLastFlagTime(currentTime);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " prawdopodobnie używa disabler (incomplete packets): " + details);
            }
        }
        
        // Sprawdź niezbalansowane pakiety (np. zbyt wiele flying, zbyt mało position)
        if (packetCounts.getOrDefault(PacketType.FLYING, 0) > 20 && 
                packetCounts.getOrDefault(PacketType.POSITION, 0) < 5) {
            // Unikaj zbyt częstego flagowania
            if (currentTime - data.getLastFlagTime() > FLAG_COOLDOWN) {
                String details = String.format("unbalanced_packets: Flying=%d, Position=%d", 
                        packetCounts.getOrDefault(PacketType.FLYING, 0),
                        packetCounts.getOrDefault(PacketType.POSITION, 0));
                flag(player, 5, details);
                
                // Aktualizuj czas ostatniej flagi
                data.setLastFlagTime(currentTime);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " prawdopodobnie używa disabler (unbalanced packets): " + details);
            }
        }
        
        // Sprawdź sekwencje pakietów charakterystyczne dla disablerów
        if (isDisablerSequence(data.getPackets(), currentTime)) {
            // Unikaj zbyt częstego flagowania
            if (currentTime - data.getLastFlagTime() > FLAG_COOLDOWN) {
                String details = "disabler_sequence: wykryto sekwencję pakietów charakterystyczną dla disablera";
                flag(player, 10, details);
                
                // Aktualizuj czas ostatniej flagi
                data.setLastFlagTime(currentTime);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " używa disabler: " + details);
            }
        }
        
        // Wyczyść stare pakiety
        data.removePacketsOlderThan(currentTime - 10000); // 10 sekund
    }
    
    /**
     * Sprawdza, czy sekwencja pakietów jest charakterystyczna dla disablera
     */
    private boolean isDisablerSequence(List<PacketInfo> packets, long currentTime) {
        // Potrzebujemy co najmniej 10 pakietów do analizy
        if (packets.size() < 10) {
            return false;
        }
        
        // Pobierz pakiety z ostatniego okna czasowego
        List<PacketInfo> recentPackets = new ArrayList<>();
        for (PacketInfo packet : packets) {
            if (currentTime - packet.time <= 2000) { // ostatnie 2 sekundy
                recentPackets.add(packet);
            }
        }
        
        // Sprawdź charakterystyczne sekwencje dla znanych disablerów
        
        // Sekwencja 1: Wiele transakcji, po których następuje paczkowanie KeepAlive
        boolean hasTransactionBurst = false;
        boolean hasKeepAliveBurst = false;
        int consecutiveTransactions = 0;
        int consecutiveKeepAlives = 0;
        
        for (int i = 0; i < recentPackets.size(); i++) {
            if (recentPackets.get(i).type == PacketType.TRANSACTION) {
                consecutiveTransactions++;
                consecutiveKeepAlives = 0;
            } else if (recentPackets.get(i).type == PacketType.KEEP_ALIVE) {
                consecutiveKeepAlives++;
                consecutiveTransactions = 0;
            } else {
                if (consecutiveTransactions >= 3) {
                    hasTransactionBurst = true;
                }
                if (consecutiveKeepAlives >= 2) {
                    hasKeepAliveBurst = true;
                }
                consecutiveTransactions = 0;
                consecutiveKeepAlives = 0;
            }
        }
        
        if (hasTransactionBurst && hasKeepAliveBurst) {
            return true;
        }
        
        // Sekwencja 2: Przeplecione pakiety pozycji i umiejętności
        int posAbilityAlternations = 0;
        
        for (int i = 1; i < recentPackets.size(); i++) {
            if ((recentPackets.get(i-1).type == PacketType.POSITION && 
                 recentPackets.get(i).type == PacketType.ABILITIES) ||
                (recentPackets.get(i-1).type == PacketType.ABILITIES && 
                 recentPackets.get(i).type == PacketType.POSITION)) {
                posAbilityAlternations++;
            }
        }
        
        if (posAbilityAlternations >= 4) {
            return true;
        }
        
        // Sekwencja 3: Zbyt wiele pakietów okien w krótkim czasie
        int windowCount = 0;
        for (PacketInfo packet : recentPackets) {
            if (packet.type == PacketType.WINDOW) {
                windowCount++;
            }
        }
        
        if (windowCount >= 10) {
            return true;
        }
        
        // Nie wykryto znanych sekwencji disablera
        return false;
    }
    
    /**
     * Klasa przechowująca informacje o pakiecie
     */
    private static class PacketInfo {
        final long time;
        final PacketType type;
        final boolean isComplete;
        
        PacketInfo(long time, PacketType type, boolean isComplete) {
            this.time = time;
            this.type = type;
            this.isComplete = isComplete;
        }
    }
    
    /**
     * Klasa przechowująca dane o pakietach gracza
     */
    private static class DisablerData {
        private final List<PacketInfo> packets = new ArrayList<>();
        private long lastFlagTime = 0;
        
        void addPacket(long time, PacketType type, boolean isComplete) {
            packets.add(new PacketInfo(time, type, isComplete));
            
            // Ogranicz rozmiar listy
            while (packets.size() > 500) {
                packets.remove(0);
            }
        }
        
        void removePacketsOlderThan(long time) {
            packets.removeIf(packet -> packet.time < time);
        }
        
        List<PacketInfo> getPackets() {
            return packets;
        }
        
        long getLastFlagTime() {
            return lastFlagTime;
        }
        
        void setLastFlagTime(long lastFlagTime) {
            this.lastFlagTime = lastFlagTime;
        }
    }
}