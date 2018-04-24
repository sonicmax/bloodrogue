precision highp float;

uniform sampler2D u_Texture;

varying vec2 v_texCoord;

void main() {
	vec4 texel = texture2D(u_Texture, v_texCoord);
	// Discard fragment is alpha value is too low.
	// This makes sure that sprite shadows have correct shape
	if (texel.a < 0.5) discard;
}