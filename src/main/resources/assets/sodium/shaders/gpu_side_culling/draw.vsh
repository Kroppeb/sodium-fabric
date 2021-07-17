#version 460

// #define secondPass
// #define useColor

layout(location = 0) in vec3 a_Pos;// The position of the vertex

// The model translation for this draw call.
layout(location = 1) in vec4 d_ModelOffset;

/*#ifdef secondPass
layout(location = 2) in int in_chunkId;
#endif*/

uniform mat4 u_ModelViewProjectionMatrix;

#ifdef useColor
uniform vec3 camaraPos;
out vec4 color;
#endif

#ifdef secondPass
flat out int chunkId;
#endif

void main() {
    vec3 pos = a_Pos  + d_ModelOffset.xyz;

    // Transform the vertex position into model-view-projection space
    gl_Position = u_ModelViewProjectionMatrix * vec4(pos, 1.0);

    #ifdef useColor
    vec3 colorPos = d_ModelOffset.xyz + camaraPos;
    color = vec4(
    mod(colorPos.xyz * .5922 + colorPos.zzx * .1587 + colorPos.yzy * .98 - colorPos.yxx * .06 + vec3(.315, .761, .9841), vec3(1.0)),
    1
    );
    #endif

    #ifdef secondPass
    chunkId = gl_BaseInstance;
    #endif
}

