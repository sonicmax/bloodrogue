precision highp float;

uniform mat4 u_MVPMatrix;

attribute vec4 a_ShadowPosition;
attribute vec2 a_texCoord;

varying vec2 v_texCoord;

void main() {
	v_texCoord = a_texCoord;
	gl_Position = u_MVPMatrix * a_ShadowPosition;
}