precision highp float;

uniform float u_Near;
uniform float u_Far;
uniform sampler2D u_Texture;

varying vec2 v_texCoord;

const float zoomFactor = 1.1;

float linearizeDepth(float depth) {
	float z = depth * 2.0 - 1.0;
	return (2.0 * u_Near * u_Far) / (u_Far + u_Near - z * (u_Far - u_Near));	
}

void main() {
	// Output the depth value to fragment. Will appear as white/grey/black texture
	float depthValue = texture2D(u_Texture, v_texCoord).r;
    gl_FragColor = vec4(vec3(depthValue), 1.0);
}