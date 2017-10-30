precision mediump float;

varying vec4 v_Color;
varying vec2 v_texCoord;

uniform sampler2D s_texture;

void main() {
		vec4 diffuse = texture2D(s_texture, v_texCoord);
		gl_FragColor = diffuse * v_Color;
}