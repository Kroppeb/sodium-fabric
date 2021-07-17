#version 430

/* configure */

// #define useTriangleStrip
// #define doubleSided

// #define useNoBranchFrustumCheck


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

layout(local_size_x = 1) in;

struct DrawElementsIndirectCommand {
    uint  count;// depends on shape
    uint  instanceCount;// 1 if visible, 0 if not
    uint  firstIndex;// used to select shape
    uint  baseVertex;// always 0?
    uint  baseInstance;// used to offset the coordinates
};

struct Input {
    uint count;
    uint firstIndex;
    int drawn;
    vec3 pos;
};

layout(std430, binding = 0) readonly restrict buffer inputDataBuffer {
    Input[] inputs;
};

layout(std430, binding = 1) writeonly restrict buffer firstPassBuffer {
    DrawElementsIndirectCommand[] firstPassCommands;
};

layout(std430, binding = 2) writeonly restrict buffer secondPassBuffer{
    DrawElementsIndirectCommand[] secondPassCommands;
};




layout(binding = 0) uniform atomic_uint counter;

uint calculateOutputIndex(){
    return atomicCounterIncrement(counter);
}


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
    uint chunkId = gl_GlobalInvocationID.x;

    Input inp = inputs[chunkId];


    uint visible = insideFrustum(inp.pos);

    if (visible != 0){

        uint outputIndex = calculateOutputIndex();

        firstCommand.count = inp.count;
        firstCommand.instanceCount = 1;
        firstCommand.firstIndex = inp.firstIndex;
        firstCommand.baseVertex = 0;
        firstCommand.baseInstance = chunkId;

        secondCommand.count = defaultCount;
        secondCommand.instanceCount = 1;
        secondCommand.firstIndex = defaultIndex;
        secondCommand.baseVertex = 0;
        secondCommand.baseInstance = chunkId;

    }
}