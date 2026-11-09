
package com.example.mod.spotify;

import com.sun.jna.Platform;
import com.example.mod.spotify.LinuxSpotify;
import com.example.mod.spotify.MacSpotify;
import com.example.mod.spotify.MediaSession;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public interface PlayerSource {
    public MediaSession.Snapshot poll();

    public boolean togglePlayPause();

    public boolean skipNext();

    public boolean skipPrevious();

    public String lastError();

    public boolean isUnsupported();

    public void close();

    public static boolean supported() {
        return Platform.isWindows() || Platform.isMac() || Platform.isLinux();
    }

    public static PlayerSource spotify() {
        if (Platform.isMac()) {
            return new MacSpotify();
        }
        if (Platform.isLinux()) {
            return new LinuxSpotify();
        }
        return MediaSession.spotify();
    }
}
