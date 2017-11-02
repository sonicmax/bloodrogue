precision lowp float;

varying vec4 v_Color;
varying vec2 v_texCoord;

uniform sampler2D u_Texture;

void main() {
		vec4 diffuse = texture2D(u_Texture, v_texCoord);
		gl_FragColor = diffuse * v_Color;
}