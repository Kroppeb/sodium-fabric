#version 460

layout(local_size_x = 1) in;

struct DrawElementsIndirectCommand {
    uint  count;// meshsize
    uint  instanceCount;// should be 1
    uint  firstIndex;// mesh index offset
    uint  baseVertex;// mesh vertex offset
    uint  baseInstance;// used to get the coordinate offset
};

struct ChunkBounds {
    vec3 lower;
    vec3 upper;
};

struct ChunkStatus {
    int status;
    vec3 chunkPos;
};

struct MeshInfo {
    uint size;
    uint firstIndex;
    uint baseVertex;
};

struct ChunkMeshPass{
    int chunkId;
    ChunkBounds bounds;
    MeshInfo up;
    MeshInfo down;
    MeshInfo east;
    MeshInfo west;
    MeshInfo south;
    MeshInfo north;
    MeshInfo unassigned;
};

struct Input {
    uint count;
    uint firstIndex;
    int drawn;
    vec3 pos;
};

layout(binding = 0) readonly restrict buffer inputDataBuffer {
    Input[] inputs;
};

// we can reuse the buffer from pass 1 or 2
layout(binding = 1) writeonly restrict buffer DEICBufferBuffer{
    DrawElementsIndirectCommand[] DEICBuffer;
};


layout(binding = 3) readonly restrict buffer meshInfoBufferBuffer {
    ChunkMeshPass[] meshInfoBuffer;
};


layout(binding = 0) uniform atomic_uint counter;


uniform vec3 camaraPos;

void addCall(MeshInfo info){
    DrawElementsIndirectCommand command;
    command.count = info.size;
    command.instanceCount = 1;
    command.firstIndex = info.firstIndex;
    command.baseVertex = info.baseVertex;
    command.baseInstance = 0;// TODO

    DEICBuffer[atomicCounterIncrement(counter)] = command;
}

void main()
{
    ChunkMeshPass pass = meshInfoBuffer[gl_GlobalInvocationID.x];
    Input status = inputs[pass.chunkId];
    ChunkBounds bounds = pass.bounds;

    if (status.drawn == 0) return;
    if (pass.unassigned.size > 0){
        addCall(pass.unassigned);
    }

    if (camaraPos.y > bounds.lower.y) {
        addCall(pass.up);
    }

    if (camaraPos.y < bounds.upper.y) {
        addCall(pass.down);
    }

    if (camaraPos.x > bounds.lower.x) {
        addCall(pass.east);
    }

    if (camaraPos.x < bounds.upper.x) {
        addCall(pass.west);
    }

    if (camaraPos.z > bounds.lower.z) {
        addCall(pass.south);
    }

    if (camaraPos.z < bounds.upper.z) {
        addCall(pass.north);
    }
}