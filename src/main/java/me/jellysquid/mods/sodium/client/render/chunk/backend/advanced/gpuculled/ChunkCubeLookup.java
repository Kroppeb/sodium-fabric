package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.gpuculled;

final class ChunkCubeLookup {
    private static final int[] OCCLUSION_START = new int[1 << 6];
    private static final int[] OCCLUSION_LENGTH = new int[1 << 6];
    static final short[] OCCLUSION_DATA = new short[(1 * 0 + 6 * 1 + 15 * 2 + 20 * 3 + 15 * 4 + 6 * 5 + 1 * 6) * 6 * 2];

    static {
        // 6 * 2 triangles version
        int q = 0;
        for (int i = 0; i < 1 << 6; i++) {
            OCCLUSION_START[i] = q;

            short side = 0;
            for (int remaining = i; remaining > 0; remaining >>= 1) {

                // 0   1
                //
                // 2   3

                if ((remaining & 1) != 0) {
                    OCCLUSION_DATA[q++] = side;
                    OCCLUSION_DATA[q++] = (short)(side + 1);
                    OCCLUSION_DATA[q++] = (short)(side + 3);

                    OCCLUSION_DATA[q++] = (short)(side + 3);
                    OCCLUSION_DATA[q++] = (short)(side + 2);
                    OCCLUSION_DATA[q++] = side;


                    /*
                    // TODO: remove this? : inside faces
                    OCCLUSION_DATA[q++] = side;
                    OCCLUSION_DATA[q++] = (short)(side + 2);
                    OCCLUSION_DATA[q++] = (short)(side + 3);

                    OCCLUSION_DATA[q++] = (short)(side + 3);
                    OCCLUSION_DATA[q++] = (short)(side + 1);
                    OCCLUSION_DATA[q++] = side;
                     */
                }
                side += 4;
            }

            OCCLUSION_LENGTH[i] = q - OCCLUSION_START[i];
        }
    }

    static int lookupCount(int i){
        return OCCLUSION_LENGTH[i];
    }

    static int lookupStart(int i){
        return OCCLUSION_START[i];
    }
}
