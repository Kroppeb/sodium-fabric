package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.struct;

public enum Structs {
    Int(4),
    UInt(4),
    Float(4),
    Vec3Struct(4 * 4),

    /**
     * {@code uint count;}<br/>
     * {@code uint instanceCount;}<br/>
     * {@code uint firstIndex;}<br/>
     * {@code uint baseVertex;}<br/>
     * {@code uint baseInstance;}<br/>
     */
    DrawElementsIndirectCommandStruct(20),

    /**
     * {@code uint count;} <br/>
     * {@code uint firstIndex;} <br/>
     * {@code int drawn;} <br/>
     * {@code vec3 pos;} <br/>
     */
    PreComputeInputsStruct(4 * 4 + Vec3Struct.size),

    /**
     * {@code vec3 lower;} <br/>
     * {@code vec3 upper;} <br/>
     */
    ChunkBoundsStruct(2 * Vec3Struct.size),

    /**
     * {@code uint size;} <br/>
     * {@code uint firstIndex;} <br/>
     * {@code uint baseVertex;} <br/>
     */
    MeshInfoStruct(3 * 4),

    /**
     * {@code int chunkId;} <br/>
     * {@link Structs#MeshInfoStruct} {@code up;} <br/>
     * {@link Structs#MeshInfoStruct} {@code down;} <br/>
     * {@link Structs#MeshInfoStruct} {@code east;} <br/>
     * {@link Structs#MeshInfoStruct} {@code west;} <br/>
     * {@link Structs#MeshInfoStruct} {@code south;} <br/>
     * {@link Structs#MeshInfoStruct} {@code north;} <br/>
     * {@link Structs#MeshInfoStruct} {@code unassigned;} <br/>
     * {@link Structs#ChunkBoundsStruct} {@code bounds;} <br/>
     */
    ChunkMeshPassStruct(128), // seems 124 is not a valid stride

    ;
    public final int size;

    Structs(int size) {
        this.size = size;
    }
}
