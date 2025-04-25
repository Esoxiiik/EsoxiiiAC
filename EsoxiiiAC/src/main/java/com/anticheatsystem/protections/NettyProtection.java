package com.anticheatsystem.protections;

import com.anticheatsystem.AntiCheatMain;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.DecoderException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Ochrona przed Netty Crasher - wykrywa i blokuje szkodliwe pakiety
 * które mogą powodować crash serwera poprzez exploity w protokole Minecraft
 */
public class NettyProtection implements Listener {

    private final AntiCheatMain plugin;
    
    // Limity dla różnych typów pakietów
    private static final int MAX_STRING_LENGTH = 32767;
    private static final int MAX_ARRAY_SIZE = 32767;
    private static final int MAX_BOOK_SIZE = 100000; // Maksymalny rozmiar danych książki w bajtach
    private static final int MAX_PACKET_SIZE = 2097152; // 2MB
    
    // Liczniki niebezpiecznych pakietów per gracz
    private final Map<UUID, Integer> maliciousPacketCount = new ConcurrentHashMap<>();
    
    // Mapa adresów IP graczy, którzy zostali wyrzuceni za ataki
    private final Map<String, Long> kickedAddresses = new ConcurrentHashMap<>();
    
    // Czas blokady (w milisekundach)
    private static final long KICK_TIMEOUT = 300000; // 5 minut
    
    // Maksymalna liczba niebezpiecznych pakietów przed wyrzuceniem
    private static final int MAX_MALICIOUS_PACKETS = 3;
    
    public NettyProtection(AntiCheatMain plugin) {
        this.plugin = plugin;
        
        // Uruchom zadanie czyszczące liczniki
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Wyczyść liczniki niebezpiecznych pakietów
            maliciousPacketCount.clear();
            
            // Usuń przedawnione wpisy z listy kickedAddresses
            long currentTime = System.currentTimeMillis();
            kickedAddresses.entrySet().removeIf(entry -> currentTime - entry.getValue() > KICK_TIMEOUT);
        }, 12000L, 12000L); // co 10 minut
        
        // Zarejestruj handler do monitorowania połączeń
        try {
            setupConnectionMonitoring();
        } catch (Exception e) {
            plugin.getPluginLogger().log(Level.SEVERE, "Nie można skonfigurować monitorowania połączeń Netty", e);
        }
    }
    
    /**
     * Konfiguruje monitorowanie pakietów sieciowych
     */
    private void setupConnectionMonitoring() throws Exception {
        try {
            // Uzyskaj dostęp do ServerConnection w klasie MinecraftServer
            Class<?> minecraftServerClass = getMinecraftServerClass();
            Object minecraftServer = getMinecraftServerInstance();
            
            if (minecraftServer == null) {
                throw new IllegalStateException("Nie można uzyskać instancji MinecraftServer");
            }
            
            Class<?> serverConnectionClass = getServerConnectionClass();
            Object serverConnection = null;
            
            // Znajdź pole serverConnection w MinecraftServer
            for (Field field : minecraftServerClass.getDeclaredFields()) {
                if (serverConnectionClass.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    serverConnection = field.get(minecraftServer);
                    break;
                }
            }
            
            if (serverConnection == null) {
                throw new IllegalStateException("Nie można uzyskać ServerConnection");
            }
            
            // Dodaj nasz handler do pipeline Netty
            addChannelInboundHandler(serverConnection);
            
        } catch (Exception e) {
            plugin.getPluginLogger().log(Level.SEVERE, "Błąd podczas konfigurowania monitorowania Netty", e);
            throw e;
        }
    }
    
    /**
     * Dodaje nasz handler do listy handlerów Netty
     */
    private void addChannelInboundHandler(Object serverConnection) throws Exception {
        // Pobierz pole channelFuture z ServerConnection
        Field channelFutureField = null;
        
        for (Field field : serverConnection.getClass().getDeclaredFields()) {
            if (field.getType().getName().contains("List") || field.getType().getName().contains("Collection")) {
                field.setAccessible(true);
                channelFutureField = field;
                break;
            }
        }
        
        if (channelFutureField == null) {
            throw new IllegalStateException("Nie znaleziono pola channelFuture");
        }
        
        // Uzyskaj listę ChannelFuture
        Iterable<?> channelFutures = (Iterable<?>) channelFutureField.get(serverConnection);
        
        for (Object channelFuture : channelFutures) {
            Field channelField = null;
            
            // Znajdź pole channel
            for (Field field : channelFuture.getClass().getDeclaredFields()) {
                if (field.getType().getName().contains("Channel")) {
                    field.setAccessible(true);
                    channelField = field;
                    break;
                }
            }
            
            if (channelField == null) {
                continue;
            }
            
            // Uzyskaj Channel
            Channel channel = (Channel) channelField.get(channelFuture);
            
            // Dodaj nasz handler
            if (channel != null) {
                channel.pipeline().addFirst("esoxiiiac_netty_protection", new NettyPacketValidator());
                plugin.getPluginLogger().info("Dodano handler ochrony przed Netty Crasher");
            }
        }
    }
    
    /**
     * Pobiera klasę MinecraftServer
     */
    private Class<?> getMinecraftServerClass() throws ClassNotFoundException {
        // Próbuj różne wersje
        String[] possibleClassNames = {
                "net.minecraft.server.MinecraftServer",
                "net.minecraft.server.v1_16_R3.MinecraftServer",
                "net.minecraft.server.v1_17_R1.MinecraftServer",
                "net.minecraft.server.v1_18_R1.MinecraftServer",
                "net.minecraft.server.v1_19_R1.MinecraftServer"
        };
        
        for (String className : possibleClassNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                // Próbuj następną wersję
            }
        }
        
        // Jeśli doszliśmy tutaj, to nie znaleźliśmy żadnej z klas
        throw new ClassNotFoundException("Nie można znaleźć klasy MinecraftServer");
    }
    
    /**
     * Pobiera klasę ServerConnection
     */
    private Class<?> getServerConnectionClass() throws ClassNotFoundException {
        // Próbuj różne wersje
        String[] possibleClassNames = {
                "net.minecraft.server.ServerConnection",
                "net.minecraft.server.network.ServerConnection",
                "net.minecraft.server.v1_16_R3.ServerConnection",
                "net.minecraft.server.v1_17_R1.ServerConnection",
                "net.minecraft.server.v1_18_R1.ServerConnection",
                "net.minecraft.server.v1_19_R1.ServerConnection",
                "net.minecraft.server.dedicated.DedicatedServerConnection"
        };
        
        for (String className : possibleClassNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                // Próbuj następną wersję
            }
        }
        
        // Jeśli doszliśmy tutaj, to nie znaleźliśmy żadnej z klas
        throw new ClassNotFoundException("Nie można znaleźć klasy ServerConnection");
    }
    
    /**
     * Pobiera instancję MinecraftServer
     */
    private Object getMinecraftServerInstance() {
        try {
            // Pobierz singleton MinecraftServer przez odbicie
            Class<?> minecraftServerClass = getMinecraftServerClass();
            Method getServerMethod = null;
            
            // Spróbuj różne metody które mogą zwrócić instancję serwera
            for (Method method : minecraftServerClass.getDeclaredMethods()) {
                if (method.getReturnType().equals(minecraftServerClass) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    getServerMethod = method;
                    break;
                }
            }
            
            if (getServerMethod != null) {
                return getServerMethod.invoke(null);
            }
            
            // Spróbuj alternative: pobierz CraftServer, który ma referencję do MinecraftServer
            Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Object craftServer = Bukkit.getServer();
            
            // Pobierz getter getServer lub console
            for (Method method : craftServerClass.getDeclaredMethods()) {
                if (method.getReturnType().equals(minecraftServerClass) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method.invoke(craftServer);
                }
            }
            
            // Spróbuj przez pola
            for (Field field : craftServerClass.getDeclaredFields()) {
                if (field.getType().equals(minecraftServerClass)) {
                    field.setAccessible(true);
                    return field.get(craftServer);
                }
            }
        } catch (Exception e) {
            plugin.getPluginLogger().log(Level.SEVERE, "Nie można uzyskać instancji MinecraftServer", e);
        }
        
        return null;
    }
    
    /**
     * Dodaje handler do kanału gracza, gdy się połączy
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        try {
            // Sprawdź, czy adres IP gracza jest na liście zablokowanych
            SocketAddress socketAddress = getPlayerSocketAddress(player);
            
            if (socketAddress instanceof InetSocketAddress) {
                String address = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
                
                if (kickedAddresses.containsKey(address)) {
                    long lastKickTime = kickedAddresses.get(address);
                    long currentTime = System.currentTimeMillis();
                    
                    // Jeśli nie minął jeszcze czas blokady, wyrzuć gracza
                    if (currentTime - lastKickTime < KICK_TIMEOUT) {
                        player.kickPlayer("§cWykryto próbę ataku sieciowego. Spróbuj ponownie za kilka minut.");
                        plugin.getPluginLogger().warning("Gracz " + player.getName() + " (IP: " + address + 
                                ") został wyrzucony - adres IP jest na liście zablokowanych");
                        return;
                    } else {
                        // Czas blokady minął, usuń adres z listy
                        kickedAddresses.remove(address);
                    }
                }
            }
            
            // Dodaj handler do kanału gracza
            injectPlayer(player);
        } catch (Exception e) {
            plugin.getPluginLogger().log(Level.WARNING, "Nie można dodać handlera Netty dla gracza " + 
                    player.getName() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Usuwa handler z kanału gracza, gdy się rozłączy
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Usuń licznik niebezpiecznych pakietów dla tego gracza
        maliciousPacketCount.remove(playerId);
        
        try {
            // Usuń handler z kanału gracza
            removePlayer(player);
        } catch (Exception e) {
            plugin.getPluginLogger().log(Level.WARNING, "Nie można usunąć handlera Netty dla gracza " + 
                    player.getName() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Pobiera adres IP gracza
     */
    private SocketAddress getPlayerSocketAddress(Player player) throws Exception {
        Object playerConnection = getConnection(player);
        
        if (playerConnection == null) {
            throw new IllegalStateException("Nie można pobrać połączenia gracza");
        }
        
        // Próbuj znaleźć pole, które zawiera adres sieciowy
        for (Field field : playerConnection.getClass().getDeclaredFields()) {
            if (SocketAddress.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return (SocketAddress) field.get(playerConnection);
            }
        }
        
        // Spróbuj pobrać networkManager, a następnie adres z niego
        Field networkManagerField = null;
        
        for (Field field : playerConnection.getClass().getDeclaredFields()) {
            if (field.getType().getSimpleName().contains("NetworkManager")) {
                field.setAccessible(true);
                networkManagerField = field;
                break;
            }
        }
        
        if (networkManagerField != null) {
            Object networkManager = networkManagerField.get(playerConnection);
            
            // Znajdź pole z SocketAddress w NetworkManager
            for (Field field : networkManager.getClass().getDeclaredFields()) {
                if (SocketAddress.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (SocketAddress) field.get(networkManager);
                }
            }
        }
        
        throw new IllegalStateException("Nie można pobrać adresu socketu dla gracza");
    }
    
    /**
     * Dodaje handler przechwytujący pakiety do kanału gracza
     */
    private void injectPlayer(Player player) throws Exception {
        Channel channel = getChannel(player);
        
        if (channel == null) {
            throw new IllegalStateException("Nie można pobrać kanału dla gracza " + player.getName());
        }
        
        // Sprawdź, czy handler już istnieje
        if (channel.pipeline().get("esoxiiiac_player_protection") != null) {
            return;
        }
        
        // Dodaj custom handler
        channel.pipeline().addBefore("packet_handler", "esoxiiiac_player_protection", 
                new PlayerNettyHandler(player));
    }
    
    /**
     * Usuwa handler przechwytujący pakiety z kanału gracza
     */
    private void removePlayer(Player player) throws Exception {
        Channel channel = getChannel(player);
        
        if (channel == null) {
            throw new IllegalStateException("Nie można pobrać kanału dla gracza " + player.getName());
        }
        
        if (channel.pipeline().get("esoxiiiac_player_protection") != null) {
            channel.pipeline().remove("esoxiiiac_player_protection");
        }
    }
    
    /**
     * Pobiera kanał sieciowy dla danego gracza
     */
    private Channel getChannel(Player player) throws Exception {
        Object playerConnection = getConnection(player);
        
        if (playerConnection == null) {
            return null;
        }
        
        // Spróbuj pobrać pole networkManager
        Field networkManagerField = null;
        
        for (Field field : playerConnection.getClass().getDeclaredFields()) {
            if (field.getType().getSimpleName().contains("NetworkManager")) {
                field.setAccessible(true);
                networkManagerField = field;
                break;
            }
        }
        
        if (networkManagerField != null) {
            Object networkManager = networkManagerField.get(playerConnection);
            
            // Próbuj znaleźć pole channel
            for (Field field : networkManager.getClass().getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (Channel) field.get(networkManager);
                }
            }
        }
        
        // Alternatywna metoda, bezpośrednio szukaj pola channel w playerConnection
        for (Field field : playerConnection.getClass().getDeclaredFields()) {
            if (Channel.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return (Channel) field.get(playerConnection);
            }
        }
        
        throw new IllegalStateException("Nie można znaleźć pola channel dla gracza " + player.getName());
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
        
        // Szukaj pola zawierającego połączenie
        for (Field field : entityPlayer.getClass().getDeclaredFields()) {
            if (field.getType().getSimpleName().contains("PlayerConnection")) {
                field.setAccessible(true);
                return field.get(entityPlayer);
            }
        }
        
        // Alternatywna metoda dla nowszych wersji
        for (Field field : entityPlayer.getClass().getDeclaredFields()) {
            if (field.getType().getSimpleName().contains("Connection") || 
                    field.getType().getSimpleName().contains("ServerGamePacket")) {
                field.setAccessible(true);
                return field.get(entityPlayer);
            }
        }
        
        throw new IllegalStateException("Nie można znaleźć pola połączenia dla gracza " + player.getName());
    }
    
    /**
     * Handler weryfikujący pakiety dla pojedynczego gracza
     */
    private class PlayerNettyHandler extends ChannelDuplexHandler {
        private final Player player;
        private final UUID playerId;
        
        public PlayerNettyHandler(Player player) {
            this.player = player;
            this.playerId = player.getUniqueId();
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // Pomiń, jeśli gracz ma uprawnienia do omijania
            if (player.hasPermission("esoxiiiac.bypass.packets") || player.hasPermission("anticheat.bypass.packets")) {
                super.channelRead(ctx, msg);
                return;
            }
            
            try {
                String packetName = msg.getClass().getSimpleName();
                ByteBuf buf = null;
                
                // Pobierz ByteBuf z pakietu, jeśli jest dostępny
                for (Field field : msg.getClass().getDeclaredFields()) {
                    if (ByteBuf.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        buf = (ByteBuf) field.get(msg);
                        break;
                    }
                }
                
                // Sprawdź rozmiar pakietu, jeśli dostępny
                if (buf != null && buf.readableBytes() > MAX_PACKET_SIZE) {
                    handleMaliciousPacket("zbyt_duży_pakiet: " + packetName + " (" + buf.readableBytes() + " bajtów)");
                    return; // Nie przekazuj pakietu dalej
                }
                
                // Sprawdź znane typy podatnych pakietów
                if (packetName.contains("Custom") || packetName.contains("CustomPayload")) {
                    // Sprawdź zawartość pakietu CustomPayload
                    String channel = null;
                    
                    // Spróbuj uzyskać nazwę kanału
                    for (Field field : msg.getClass().getDeclaredFields()) {
                        if (field.getType() == String.class || 
                                field.getType().getSimpleName().equals("MinecraftKey")) {
                            field.setAccessible(true);
                            Object value = field.get(msg);
                            channel = value.toString();
                            break;
                        }
                    }
                    
                    // Sprawdź niebezpieczne kanały
                    if (channel != null && (
                            channel.contains("MC|BEdit") || 
                            channel.contains("MC|BSign") ||
                            channel.contains("minecraft:book") ||
                            channel.contains("minecraft:brand") && buf != null && buf.readableBytes() > 100)) {
                        
                        handleMaliciousPacket("podejrzany_custom_payload: " + channel);
                        return; // Nie przekazuj pakietu dalej
                    }
                } 
                else if (packetName.contains("Book") || packetName.contains("Sign")) {
                    // Sprawdź pakiety z książkami i tabliczkami
                    // Sprawdź zawartość znakową
                    for (Field field : msg.getClass().getDeclaredFields()) {
                        if (field.getType() == String.class || field.getType() == String[].class) {
                            field.setAccessible(true);
                            Object value = field.get(msg);
                            
                            if (value instanceof String) {
                                String str = (String) value;
                                if (str.length() > MAX_STRING_LENGTH) {
                                    handleMaliciousPacket("zbyt_długi_string: " + str.length() + " znaków");
                                    return; // Nie przekazuj pakietu dalej
                                }
                            } 
                            else if (value instanceof String[]) {
                                String[] arr = (String[]) value;
                                if (arr.length > MAX_ARRAY_SIZE) {
                                    handleMaliciousPacket("zbyt_duża_tablica: " + arr.length + " elementów");
                                    return; // Nie przekazuj pakietu dalej
                                }
                                
                                // Sprawdź każdy string w tablicy
                                for (String str : arr) {
                                    if (str != null && str.length() > MAX_STRING_LENGTH) {
                                        handleMaliciousPacket("zbyt_długi_string_w_tablicy: " + str.length() + " znaków");
                                        return; // Nie przekazuj pakietu dalej
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Jeśli wszystko jest w porządku, przekaż pakiet dalej
                super.channelRead(ctx, msg);
            } catch (Exception e) {
                // Jeśli wystąpił wyjątek podczas sprawdzania pakietu, zarejestruj go i porzuć pakiet
                plugin.getPluginLogger().log(Level.WARNING, "Błąd podczas sprawdzania pakietu od gracza " + 
                        player.getName() + ": " + e.getMessage(), e);
                
                // Nie przekazuj pakietu dalej, jeśli wystąpił wyjątek
                if (e instanceof DecoderException && e.getMessage().contains("over size")) {
                    handleMaliciousPacket("zbyt_duży_pakiet_exception: " + e.getMessage());
                }
            }
        }
        
        /**
         * Obsługuje wykryte złośliwe pakiety
         */
        private void handleMaliciousPacket(String details) {
            // Zwiększ licznik złośliwych pakietów
            int count = maliciousPacketCount.getOrDefault(playerId, 0) + 1;
            maliciousPacketCount.put(playerId, count);
            
            // Loguj wykrycie
            plugin.getPluginLogger().warning("Wykryto potencjalnie złośliwy pakiet od gracza " + 
                    player.getName() + ": " + details + " (naruszenie " + count + "/" + MAX_MALICIOUS_PACKETS + ")");
            
            // Powiadom administratorów
            plugin.notifyAdmins("Wykryto podejrzany pakiet od gracza " + player.getName() + ": " + details);
            
            // Jeśli przekroczono limit złośliwych pakietów
            if (count >= MAX_MALICIOUS_PACKETS) {
                // Pobierz adres IP gracza i dodaj go do listy zablokowanych
                try {
                    SocketAddress socketAddress = getPlayerSocketAddress(player);
                    
                    if (socketAddress instanceof InetSocketAddress) {
                        String address = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
                        kickedAddresses.put(address, System.currentTimeMillis());
                    }
                } catch (Exception e) {
                    plugin.getPluginLogger().log(Level.WARNING, "Nie można pobrać adresu IP gracza " + 
                            player.getName(), e);
                }
                
                // Wyrzuć gracza
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.kickPlayer("§cWykryto próbę ataku sieciowego. Skontaktuj się z administracją serwera.");
                    plugin.getPluginLogger().warning("Gracz " + player.getName() + " został wyrzucony za wysyłanie złośliwych pakietów");
                });
            }
        }
    }
    
    /**
     * Handler globalny sprawdzający wszystkie przychodzące pakiety przed dekodowaniem
     */
    private class NettyPacketValidator extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                if (msg instanceof ByteBuf) {
                    ByteBuf buf = (ByteBuf) msg;
                    
                    // Sprawdź rozmiar pakietu
                    if (buf.readableBytes() > MAX_PACKET_SIZE) {
                        String remoteAddress = ctx.channel().remoteAddress().toString();
                        plugin.getPluginLogger().warning("Odrzucono zbyt duży pakiet (" + buf.readableBytes() + 
                                " bajtów) od " + remoteAddress);
                        
                        // Po prostu porzuć pakiet, nie przekazuj go dalej
                        return;
                    } 
                    // Sprawdź inne kryteria dla ByteBuf
                    else if (buf.readableBytes() > 256) { // Tylko sprawdzaj większe pakiety
                        // Tutaj możesz dodać bardziej zaawansowane sprawdzanie zawartości pakietu
                        // Przykładowo, możesz szukać znanych wzorców ataków
                    }
                }
                
                // Jeśli wszystko jest w porządku, przekaż pakiet dalej
                super.channelRead(ctx, msg);
            } catch (Exception e) {
                // Jeśli wystąpił wyjątek, zarejestruj go i pozwól, by został obsłużony normalnie
                plugin.getPluginLogger().log(Level.WARNING, "Błąd podczas walidacji pakietu: " + e.getMessage(), e);
                throw e;
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            // Zarejestruj wyjątek
            String remoteAddress = ctx.channel().remoteAddress().toString();
            plugin.getPluginLogger().log(Level.WARNING, "Wyjątek w połączeniu z " + remoteAddress + 
                    ": " + cause.getMessage(), cause);
            
            // Przekaż wyjątek dalej
            super.exceptionCaught(ctx, cause);
        }
    }
}