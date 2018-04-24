precision highp float;

uniform mat4 u_MVPMatrix;

attribute vec4 a_Position;
attribute vec4 a_Colour;

varying vec4 v_Colour;

void main() {
	v_Colour = a_Colour;
	gl_Position = u_MVPMatrix * a_Position;
}