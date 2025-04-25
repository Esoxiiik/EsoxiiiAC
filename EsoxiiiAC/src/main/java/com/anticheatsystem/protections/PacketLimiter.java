package com.anticheatsystem.protections;

import com.anticheatsystem.AntiCheatMain;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System ograniczania liczby pakietów przesyłanych przez graczy
 * Używa biblioteki Netty do interceptowania i limitowania ruchu sieciowego
 */
public class PacketLimiter implements Listener {

    private final AntiCheatMain plugin;
    
    // Limity pakietów
    private static final int MAX_PACKETS_PER_SECOND = 300;
    private static final int MAX_POSITION_PACKETS_PER_SECOND = 60;
    private static final int MAX_INTERACTION_PACKETS_PER_SECOND = 40;
    private static final int MAX_COMBAT_PACKETS_PER_SECOND = 30;
    
    // Liczniki pakietów dla graczy
    private final Map<UUID, PacketCounter> packetCounters = new ConcurrentHashMap<>();
    
    // Ostatni czas wysłania ostrzeżenia
    private final Map<UUID, Long> lastWarningTime = new ConcurrentHashMap<>();
    
    public PacketLimiter(AntiCheatMain plugin) {
        this.plugin = plugin;
        
        // Uruchom zadanie czyszczące liczniki co sekundę
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (PacketCounter counter : packetCounters.values()) {
                counter.resetCounters();
            }
        }, 20L, 20L);
    }
    
    /**
     * Dodaj handler do kanału gracza, gdy się połączy
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Utwórz licznik pakietów dla tego gracza
        packetCounters.put(playerId, new PacketCounter());
        
        // Dodaj handler do kanału gracza
        try {
            injectPlayer(player);
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Nie można dodać handlera pakietów dla gracza " + 
                    player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Usuń handler z kanału gracza, gdy się rozłączy
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Usuń licznik pakietów dla tego gracza
        packetCounters.remove(playerId);
        
        // Usuń handler z kanału gracza
        try {
            removePlayer(player);
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Nie można usunąć handlera pakietów dla gracza " + 
                    player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Dodaje handler przechwytujący pakiety do kanału gracza
     */
    private void injectPlayer(Player player) throws Exception {
        Channel channel = getChannel(player);
        
        if (channel == null) {
            throw new IllegalStateException("Nie można pobrać kanału gracza");
        }
        
        // Sprawdź, czy handler już istnieje
        if (channel.pipeline().get("packet_limiter") != null) {
            return;
        }
        
        // Dodaj custom handler
        channel.pipeline().addBefore("packet_handler", "packet_limiter", 
                new PacketHandler(player));
    }
    
    /**
     * Usuwa handler przechwytujący pakiety z kanału gracza
     */
    private void removePlayer(Player player) throws Exception {
        Channel channel = getChannel(player);
        
        if (channel == null) {
            throw new IllegalStateException("Nie można pobrać kanału gracza");
        }
        
        if (channel.pipeline().get("packet_limiter") != null) {
            channel.pipeline().remove("packet_limiter");
        }
    }
    
    /**
     * Pobiera kanał sieciowy dla danego gracza
     */
    private Channel getChannel(Player player) throws Exception {
        // Użyj refleksji do pobrania kanału Netty
        Object playerConnection = getConnection(player);
        
        if (playerConnection == null) {
            return null;
        }
        
        try {
            // Spróbuj pobrać pole networkManager
            Field networkManagerField = playerConnection.getClass().getDeclaredField("networkManager");
            networkManagerField.setAccessible(true);
            Object networkManager = networkManagerField.get(playerConnection);
            
            // Pobierz pole channel
            Field channelField = networkManager.getClass().getDeclaredField("channel");
            channelField.setAccessible(true);
            return (Channel) channelField.get(networkManager);
        } catch (Exception e) {
            // Alternatywna metoda, jeśli pierwsza zawiedzie
            Field channelField = findChannelField(playerConnection.getClass());
            
            if (channelField != null) {
                channelField.setAccessible(true);
                return (Channel) channelField.get(playerConnection);
            }
            
            throw new IllegalStateException("Nie można pobrać pola channel", e);
        }
    }
    
    /**
     * Rekurencyjnie poszukuje pola typu Channel w klasie
     */
    private Field findChannelField(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (Channel.class.isAssignableFrom(field.getType())) {
                return field;
            }
        }
        
        // Sprawdź klasę nadrzędną
        if (clazz.getSuperclass() != null) {
            return findChannelField(clazz.getSuperclass());
        }
        
        return null;
    }
    
    /**
     * Pobiera obiekt połączenia dla danego gracza
     */
    private Object getConnection(Player player) throws Exception {
        // Pobierz klasę CraftPlayer
        Class<?> craftPlayerClass = player.getClass();
        
        // Pobierz metodę getHandle
        Method getHandleMethod = craftPlayerClass.getMethod("getHandle");
        Object entityPlayer = getHandleMethod.invoke(player);
        
        // Spróbuj pobrać pole playerConnection lub b (w nowszych wersjach)
        try {
            Field playerConnectionField = entityPlayer.getClass().getDeclaredField("playerConnection");
            playerConnectionField.setAccessible(true);
            return playerConnectionField.get(entityPlayer);
        } catch (NoSuchFieldException e) {
            // Spróbuj alternatywnej nazwy pola w nowszych wersjach
            try {
                Field bField = entityPlayer.getClass().getDeclaredField("b");
                bField.setAccessible(true);
                return bField.get(entityPlayer);
            } catch (NoSuchFieldException e2) {
                // Przeszukaj wszystkie pola klasy
                for (Field field : entityPlayer.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object obj = field.get(entityPlayer);
                    if (obj != null && obj.getClass().getSimpleName().toLowerCase().contains("connection")) {
                        return obj;
                    }
                }
                throw new IllegalStateException("Nie można znaleźć pola połączenia");
            }
        }
    }
    
    /**
     * Handler przechwytujący pakiety
     */
    private class PacketHandler extends ChannelDuplexHandler {
        private final Player player;
        private final UUID playerId;
        
        public PacketHandler(Player player) {
            this.player = player;
            this.playerId = player.getUniqueId();
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // Pomiń, jeśli gracz ma uprawnienia do omijania
            if (player.hasPermission("anticheat.bypass.packets")) {
                super.channelRead(ctx, msg);
                return;
            }
            
            // Pobierz licznik pakietów dla gracza
            PacketCounter counter = packetCounters.get(playerId);
            
            if (counter == null) {
                // Jeśli z jakiegoś powodu nie ma licznika, dodaj nowy
                counter = new PacketCounter();
                packetCounters.put(playerId, counter);
            }
            
            // Pobierz nazwę klasy pakietu
            String packetName = msg.getClass().getSimpleName();
            
            // Zwiększ odpowiedni licznik
            counter.totalPackets++;
            
            // Sprawdź typ pakietu
            if (packetName.contains("Position") || packetName.contains("Look") || packetName.contains("Flying")) {
                counter.positionPackets++;
                
                if (counter.positionPackets > MAX_POSITION_PACKETS_PER_SECOND) {
                    handleExcessivePackets("ruch", counter.positionPackets);
                    return; // Porzuć pakiet
                }
            } else if (packetName.contains("BlockPlace") || packetName.contains("BlockDig") || 
                    packetName.contains("UseItem") || packetName.contains("UseEntity")) {
                counter.interactionPackets++;
                
                if (counter.interactionPackets > MAX_INTERACTION_PACKETS_PER_SECOND) {
                    handleExcessivePackets("interakcję", counter.interactionPackets);
                    return; // Porzuć pakiet
                }
            } else if (packetName.contains("Arm") || packetName.contains("Attack")) {
                counter.combatPackets++;
                
                if (counter.combatPackets > MAX_COMBAT_PACKETS_PER_SECOND) {
                    handleExcessivePackets("walkę", counter.combatPackets);
                    return; // Porzuć pakiet
                }
            }
            
            // Sprawdź całkowitą liczbę pakietów
            if (counter.totalPackets > MAX_PACKETS_PER_SECOND) {
                handleExcessivePackets("wszystkie", counter.totalPackets);
                return; // Porzuć pakiet
            }
            
            // Przekaż pakiet dalej
            super.channelRead(ctx, msg);
        }
        
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            // Przekazujemy wszystkie pakiety wychodzące (od serwera do klienta)
            super.write(ctx, msg, promise);
        }
        
        /**
         * Obsługuje nadmierną liczbę pakietów od gracza
         */
        private void handleExcessivePackets(String packetType, int count) {
            // Wyślij ostrzeżenie nie częściej niż co 5 sekund
            long currentTime = System.currentTimeMillis();
            long lastTime = lastWarningTime.getOrDefault(playerId, 0L);
            
            if (currentTime - lastTime > 5000) {
                lastWarningTime.put(playerId, currentTime);
                
                // Powiadom administratorów
                plugin.notifyAdmins(player.getName() + " wysyła zbyt wiele pakietów (" + 
                        packetType + "): " + count + " - możliwy atak DDoS lub cheat");
                
                // Zapisz do dziennika
                plugin.getPluginLogger().warning("Gracz " + player.getName() + " wysyła " + 
                        count + " pakietów (" + packetType + ") na sekundę");
            }
        }
    }
    
    /**
     * Klasa przechowująca liczniki pakietów dla gracza
     */
    private static class PacketCounter {
        int totalPackets = 0;
        int positionPackets = 0;
        int interactionPackets = 0;
        int combatPackets = 0;
        
        /**
         * Resetuje wszystkie liczniki
         */
        void resetCounters() {
            totalPackets = 0;
            positionPackets = 0;
            interactionPackets = 0;
            combatPackets = 0;
        }
    }
}