#version 430

// #define supportsEarlyFragmentTests
// #define secondPass
// #define secondPass2
// #define useColor


#ifdef __SODIUM__ATOMICS__USE_SSBO

#define inlineAtomicCounter

#else
#ifdef __SODIUM__ATOMICS__USE_AC

layout(binding = 0) uniform atomic_uint counter;

#else

#error No AtomicSystem has been selected

#endif
#endif

#ifdef useColor
out vec4 fragColor;
in vec4 color;
#endif

#ifdef supportsEarlyFragmentTests
// enable early fragment test
layout(early_fragment_tests) in;
#endif

#ifdef secondPass

#define FLAG_IN_MEMORY 1
#define FLAG_DRAWN 2

struct ChunkStatus {
// 0b0000_0001: in memory
// 0b0000_0010: drawn this frame
    int status;
    uint count;
    uint firstIndex;
    uint baseInstance;
};

layout(binding = 4) restrict buffer inputDataBuffer {
#ifdef inlineAtomicCounter
    uint nextIndex;
    #endif
    ChunkStatus[] chunkStatusList;
};

struct DrawElementsIndirectCommand {
    uint  count;// depends on shape
    uint  instanceCount;// 1 if visible, 0 if not
    uint  firstIndex;// used to select shape
    uint  baseVertex;// always 0?
    uint  baseInstance;// used to offset the coordinates
};

layout(binding = 3) writeonly restrict buffer drawCommandBuffer {
    DrawElementsIndirectCommand[] drawCommands;
};

flat in int chunkId;// TODO get chunkID
#define chunkStatus chunkStatusList[chunkId]


#ifdef inlineAtomicCounter
uint calculateOutputIndex(){
    return atomicAdd(nextIndex, 1);
}
    #else
uint calculateOutputIndex(){
    return atomicCounterIncrement(counter);
}
    #endif

    #define drawCommand drawCommands[drawIndex]

    #endif





void main() {
    #ifdef useColor
    #ifdef secondPass2
    fragColor = color / 2;
    #else
    fragColor = color;
    #endif
    #endif

    #ifdef secondPass
    int oldStatus  = atomicOr(chunkStatus.status, FLAG_DRAWN);
    if ((oldStatus & (FLAG_IN_MEMORY | FLAG_DRAWN)) == FLAG_IN_MEMORY){
        uint drawIndex = calculateOutputIndex();

        drawCommand.count = chunkStatus.count;
        drawCommand.instanceCount = 1;
        drawCommand.firstIndex = chunkStatus.firstIndex;
        drawCommand.baseVertex = 0;
        drawCommand.baseInstance = chunkStatus.baseInstance;
    }
        #endif
}

