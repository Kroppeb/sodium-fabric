#version 430

// #define supportsEarlyFragmentTests
// #define secondPass
// #define secondPass2
// #define useColor


layout(binding = 0) uniform atomic_uint[2] _counter;

#define counter _counter[1];


#ifdef useColor
out vec4 fragColor;
in vec4 color;
#endif

#ifdef supportsEarlyFragmentTests
// enable early fragment test
layout(early_fragment_tests) in;
#endif

#ifdef secondPass

struct Input {
    uint count;
    uint firstIndex;
    int drawn;
    vec3 pos;
};

layout(binding = 0) writeonly restrict buffer inputDataBuffer {
    Input[] inputs;
};

flat in int chunkId;

    #endif




void main() {
    #ifdef useColor
    //#ifndef secondPass2
    //fragColor = vec4(mod(vec3(.9852 * chunkId, .35496 * chunkId, .1265 * chunkId), 1.0), 1.0);
    //#else
    fragColor = color;
    //#endif
    #endif

    #ifdef secondPass
    inputs[chunkId].drawn = 1;
    #endif
}

