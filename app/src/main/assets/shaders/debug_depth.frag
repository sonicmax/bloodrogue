precision highp float;

uniform float u_Near;
uniform float u_Far;
uniform sampler2D u_DepthMap;

varying vec2 v_texCoord;

float linearizeDepth(float depth) {
	float z = depth * 2.0 - 1.0;
	return (2.0 * u_Near * u_Far) / (u_Far + u_Near - z * (u_Far - u_Near));	
}

void main() {
	float depthValue = texture2D(u_DepthMap, v_texCoord).r;
    gl_FragColor = vec4(vec3(linearizeDepth(depthValue) / 100.0), 1.0);
}