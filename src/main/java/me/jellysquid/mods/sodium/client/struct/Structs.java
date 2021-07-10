package me.jellysquid.mods.sodium.client.struct;

public enum Structs {
    Int(4),
    UInt(4),
    Float(4),
    Vec3(4 * 4),

    /**
     * {@code uint count;}<br/>
     * {@code uint instanceCount;}<br/>
     * {@code uint firstIndex;}<br/>
     * {@code uint baseVertex;}<br/>
     * {@code uint baseInstance;}<br/>
     */
    DrawElementsIndirectCommand(20),

    /**
     * {@code uint count;} <br/>
     * {@code uint firstIndex;} <br/>
     * {@code int drawn;} <br/>
     * {@code vec3 pos;} <br/>
     */
    PreComputeInputs(4 * 4 + Vec3.size),

    /**
     * {@code vec3 lower;} <br/>
     * {@code vec3 upper;} <br/>
     */
    ChunkBounds(2 * Vec3.size),

    /**
     * {@code uint size;} <br/>
     * {@code uint firstIndex;} <br/>
     * {@code uint baseVertex;} <br/>
     */
    MeshInfo(3 * 4),

    /**
     * {@code int chunkId;} <br/>
     * {@link Structs#MeshInfo} {@code up;} <br/>
     * {@link Structs#MeshInfo} {@code down;} <br/>
     * {@link Structs#MeshInfo} {@code east;} <br/>
     * {@link Structs#MeshInfo} {@code west;} <br/>
     * {@link Structs#MeshInfo} {@code south;} <br/>
     * {@link Structs#MeshInfo} {@code north;} <br/>
     * {@link Structs#MeshInfo} {@code unassigned;} <br/>
     * {@link Structs#ChunkBounds} {@code bounds;} <br/>
     */
    ChunkMeshPass(128), // seems 124 is not a valid stride

    ;
    public final int size;

    Structs(int size) {
        this.size = size;
    }
}
