package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.render.chunk.SectionCuller;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.render.Camera;

public interface Culler {
    void setCullerInteractor(SectionCuller.CullerInteractor cullerInteractor);

    void setFrustumChecker(SectionCuller.FrustumChecker frustumChecker);

    void setup(Camera camera);

    void iterateChunks(Camera camera, FrustumExtended frustum, int frame, boolean spectator);

    SectionListener getListener();

    boolean isSectionVisible(int x, int y, int z, int currentFrame);
}
