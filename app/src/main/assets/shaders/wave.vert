precision lowp float;

uniform mat4 u_MVPMatrix;
uniform vec2 u_waveData;
attribute vec4 a_Position;
attribute vec4 a_Color;
attribute vec2 a_texCoord;
varying vec4 v_Color;
varying vec2 v_texCoord;
varying vec4 v_newPos;

void main() {
		v_texCoord = a_texCoord;
		v_Color = a_Color;
		v_newPos = vec4(
				a_Position.x + u_waveData.y * sin(u_waveData.x + a_Position.x + a_Position.y),
				a_Position.y + u_waveData.y * sin(u_waveData.x + a_Position.x + a_Position.y),
				a_Position.z,
				a_Position.w);
		gl_Position = u_MVPMatrix * v_newPos;
}