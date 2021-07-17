#import <sodium:include/fog.glsl>


// INPUTS
in vec3 a_Pos; // The position of the vertex around the model origin
in vec4 a_Color; // The color of the vertex
in vec2 a_TexCoord; // The block texture coordinate of the vertex
in vec2 a_LightCoord; // The light texture coordinate of the vertex
in vec3 a_ChunkOffset; // The light texture coordinate of the vertex

// UNIFORMS
uniform mat4 u_ModelViewProjectionMatrix;

uniform float u_ModelScale;
uniform float u_ModelOffset;

uniform float u_TextureScale;


// OUTPUTS
out vec4 v_Color;
out vec2 v_TexCoord;
out vec2 v_LightCoord;

#ifdef USE_FOG
out float v_FragDistance;
#endif

vec3 getVertexPosition() {
    vec3 vertexPosition = a_Pos * u_ModelScale + u_ModelOffset + a_ChunkOffset;

    return vertexPosition;
}