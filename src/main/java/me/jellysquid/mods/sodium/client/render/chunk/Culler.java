package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.render.Camera;

public interface Culler {
    void setCullerInteractor(RegionCuller.CullerInteractor cullerInteractor);

    void setup(Camera camera);

    void iterateChunks(Camera camera, FrustumExtended frustum, int frame, boolean spectator);
}
