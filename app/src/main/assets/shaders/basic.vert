uniform mat4 u_MVPMatrix;

attribute vec4 a_Position;
attribute vec4 a_Color;
attribute vec2 a_texCoord;

varying vec2 v_texCoord;
varying vec4 v_Color;

void main() {
		gl_Position = u_MVPMatrix * a_Position;
		v_texCoord = a_texCoord;
		v_Color = a_Color;
}