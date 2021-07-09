package me.jellysquid.mods.sodium.client.struct;

public enum Structs {
    Int(4),
    UInt(4),
    Float(4),
    Vec3(4 * 4),

    /**
     * uint count;
     * uint instanceCount;
     * uint firstIndex;
     * uint baseVertex;
     * uint baseInstance;
     */
    DrawElementsIndirectCommand(20),

    /**
     * uint count;
     * uint firstIndex;
     * int drawn;
     * vec3 pos;
     */
    PreComputeInputs(4 * 4 + Vec3.size),

    /**
     * vec3 lower;
     * vec3 upper;
     */
    ChunkBounds(2 * Vec3.size),

    /**
     * uint size;
     * uint firstIndex;
     * uint baseVertex;
     */
    MeshInfo(3 * 4),

    /**
     * int chunkId;
     * MeshInfo up;
     * MeshInfo down;
     * MeshInfo east;
     * MeshInfo west;
     * MeshInfo south;
     * MeshInfo north;
     * MeshInfo unassigned;
     * ChunkBounds bounds;
     */
    ChunkMeshPass(128), // seems 124 is not a valid stride

    ;
    public final int size;

    Structs(int size) {
        this.size = size;
    }
}
