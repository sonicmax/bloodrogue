precision highp float;

uniform sampler2D u_Texture;

varying vec2 v_texCoord;

void main() {
	vec4 texel = texture2D(u_Texture, v_texCoord);
	if (texel.a < 0.5) discard;
}