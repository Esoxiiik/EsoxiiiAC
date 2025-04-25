package com.anticheatsystem.checks.scaffold;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Bazowa klasa dla wszystkich sprawdzeń dotyczących scaffolda.
 * Sprawdzenia te wykrywają nieludzkie umiejętności stawiania bloków
 * (tzw. scaffolding), często używane przy speedbridgingu.
 */
public abstract class ScaffoldCheck extends Check {

    protected String friendlyName;
    
    /**
     * Konstruktor dla sprawdzenia scaffold
     * 
     * @param plugin Instancja głównego pluginu
     * @param subType Podtyp sprawdzenia (np. "A", "B", itp.)
     * @param friendlyName Przyjazna nazwa
     */
    public ScaffoldCheck(AntiCheatMain plugin, String subType, String friendlyName) {
        super(plugin, "scaffold." + subType, "scaffold");
        this.friendlyName = friendlyName;
    }
    
    /**
     * Oblicza kąt między dwoma kierunkami
     * 
     * @param yaw1 Pierwszy kąt yaw (w stopniach)
     * @param yaw2 Drugi kąt yaw (w stopniach)
     * @return Kąt między dwoma kierunkami (0-180 stopni)
     */
    protected float getYawDifference(float yaw1, float yaw2) {
        float angle = Math.abs(yaw1 - yaw2) % 360.0f;
        return angle > 180.0f ? 360.0f - angle : angle;
    }
    
    /**
     * Sprawdza, czy lokalizacja jest w obszarze widoczności gracza
     * 
     * @param player Gracz
     * @param location Lokalizacja do sprawdzenia
     * @return true jeśli lokalizacja jest w polu widzenia gracza
     */
    protected boolean isLocationInPlayerView(Player player, Location location) {
        Location eyeLocation = player.getEyeLocation();
        
        // Wektor od oczu gracza do lokalizacji
        double dx = location.getX() - eyeLocation.getX();
        double dy = location.getY() - eyeLocation.getY();
        double dz = location.getZ() - eyeLocation.getZ();
        
        // Oblicz kierunek, w którym patrzy gracz (w radianach)
        double yaw = Math.toRadians(-eyeLocation.getYaw() - 90);
        double pitch = Math.toRadians(-eyeLocation.getPitch());
        
        // Wektor kierunku gracza
        double dirX = Math.cos(yaw) * Math.cos(pitch);
        double dirY = Math.sin(pitch);
        double dirZ = Math.sin(yaw) * Math.cos(pitch);
        
        // Normalizacja wektorów
        double lenPlayer = Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        double lenBlock = Math.sqrt(dx*dx + dy*dy + dz*dz);
        
        dirX /= lenPlayer;
        dirY /= lenPlayer;
        dirZ /= lenPlayer;
        
        dx /= lenBlock;
        dy /= lenBlock;
        dz /= lenBlock;
        
        // Iloczyn skalarny (dot product) - miara podobieństwa kierunków
        double dot = dirX*dx + dirY*dy + dirZ*dz;
        
        // Kąt między wektorami
        double angle = Math.acos(dot);
        
        // Konwersja na stopnie
        angle = Math.toDegrees(angle);
        
        // Sprawdź czy kąt jest w dopuszczalnym zakresie (pole widzenia)
        return angle <= 70.0; // Typowe pole widzenia to około 70 stopni
    }
    
    /**
     * Zwraca przyjazną nazwę sprawdzenia
     */
    public String getFriendlyName() {
        return this.friendlyName;
    }
}