#version 430

/* configure */

// #define useTriangleStrip
// #define doubleSided

// #define useAtomicCounter
// #define useMultiAtomicCounter
// #define useNoBranchFrustumCheck

#ifdef __SODIUM__ATOMICS__USE_SSBO
#else
#ifdef __SODIUM__ATOMICS__USE_AC

//#define useAtomicCounter

#else

#error No AtomicSystem has been selected

#endif
#endif


#define multiAtomicCounterSize (8) /* minimum value for GL_MAX_COMPUTE_ATOMIC_COUNTERS */

/* internal calculations */

#ifdef doubleSided
#define sideMultiplier 2
#else
#define sideMultiplier 1
#endif

#ifdef useTriangleStrip
#define defaultCount (6 * 2 + 2)
#define defaultIndex (0 /* TODO: FIGURE OUT */)
#else
#define defaultCount (6 * 6 * sideMultiplier)
#define defaultIndex ((1 * 0 + 6 * 1 + 15 * 2 + 20 * 3 + 15 * 4 + 6 * 5 /*+ 1 * 6*/) * 6 * sideMultiplier)
#endif

#define margin (-sqrt(3) * 8)
#define oneOverMargin (1 / (margin + 1e-4))

/* interal fancy stuff */

#define inputData inputs[inputIndex]
#define firstCommand firstPassCommands[outputIndex]
#define secondCommand secondPassCommands[outputIndex]

uniform vec4[6] frustum;
uniform vec3 camaraPos;// could be applied to the frustum in cpu land

layout(local_size_x = 1) in;

struct DrawElementsIndirectCommand {
    uint  count;// depends on shape
    uint  instanceCount;// 1 if visible, 0 if not
    uint  firstIndex;// used to select shape
    uint  baseVertex;// always 0?
    uint  baseInstance;// used to offset the coordinates
};

struct Inputs {
    uint count;
    uint firstIndex;
    uint baseInstance;// is this always needed
    vec3 pos;
};

layout(binding = 0) readonly restrict buffer inputDataBuffer {
    Inputs[] inputs;
};

layout(binding = 1) writeonly restrict buffer firstPassBuffer {
    DrawElementsIndirectCommand[] firstPassCommands;
};

layout(binding = 2) writeonly restrict buffer secondPassBuffer{
    DrawElementsIndirectCommand[] secondPassCommands;
};

uint calculateInputIndex(){
    return gl_GlobalInvocationID.x;
}

    #ifndef useAtomicCounter

    #define pushStart// push start;
    #define pushEnd// push end;
    #define getInstanceCount visible

uint calculateOutputIndex(){
    return gl_GlobalInvocationID.x;
}

    #else

    #define pushStart if(visible != 0){ // push start
    #define pushEnd }// push end
    #define getInstanceCount 1

    #ifdef useMultiAtomicCounter

layout(binding = 0) uniform atomic_uint[multiAtomicCounterSize] counter;

uint calculateOutputIndex(){
    return atomicCounterIncrement(counter[gl_LocalInvocationID.x]);
}
    #else

layout(binding = 0) uniform atomic_uint counter;

uint calculateOutputIndex(){
    return atomicCounterIncrement(counter);
}
    #endif
    #endif


    #ifdef useNoBranchFrustumCheck
uint insideFrustum(vec3 pos) {
    uint f0 = min(uint(dot(frustum[0], vec4(pos, 1)) * oneOverMargin), 1);
    uint f1 = min(uint(dot(frustum[1], vec4(pos, 1)) * oneOverMargin), 1);
    uint f2 = min(uint(dot(frustum[2], vec4(pos, 1)) * oneOverMargin), 1);
    uint f3 = min(uint(dot(frustum[3], vec4(pos, 1)) * oneOverMargin), 1);
    uint f4 = min(uint(dot(frustum[4], vec4(pos, 1)) * oneOverMargin), 1);
    uint f5 = min(uint(dot(frustum[5], vec4(pos, 1)) * oneOverMargin), 1);
    return f0 * f1 * f2 * f3 * f4 * f5;
}
    #else
uint insideFrustum(vec3 pos){
    if (dot(frustum[0], vec4(pos, 1)) <= margin) return 0;
    if (dot(frustum[1], vec4(pos, 1)) <= margin) return 0;
    if (dot(frustum[2], vec4(pos, 1)) <= margin) return 0;
    if (dot(frustum[3], vec4(pos, 1)) <= margin) return 0;
    if (dot(frustum[4], vec4(pos, 1)) <= margin) return 0;
    if (dot(frustum[5], vec4(pos, 1)) <= margin) return 0;
    return 1;
}
    #endif

void main()
{
    uint inputIndex = calculateInputIndex();

    uint count = inputData.count;
    uint firstIndex = inputData.firstIndex;
    uint baseInstance = inputData.baseInstance;
    vec3 pos = inputData.pos;

    uint visible = insideFrustum(pos - camaraPos);

    pushStart

uint outputIndex = calculateOutputIndex();

firstCommand.count = count;
firstCommand.instanceCount = getInstanceCount;
firstCommand.firstIndex = firstIndex;
firstCommand.baseVertex = 0;
firstCommand.baseInstance = baseInstance;

secondCommand.count = defaultCount;
secondCommand.instanceCount = getInstanceCount;
secondCommand.firstIndex = defaultIndex;
secondCommand.baseVertex = 0;
secondCommand.baseInstance = baseInstance;

pushEnd
}