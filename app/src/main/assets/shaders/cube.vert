precision lowp float;

uniform mat4 u_ModelMatrix;
uniform mat4 u_MVMatrix;
uniform mat4 u_MVPMatrix;
uniform mat4 u_NormalMatrix;
uniform mat4 u_ShadowProjMatrix;

attribute vec4 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_texCoord;
attribute vec4 a_Color;

varying vec3 v_Position;
varying vec3 v_Normal;
varying vec2 v_texCoord;
varying vec4 v_Color;
varying vec4 v_ShadowCoord;

void main() {
	v_Color = a_Color;
    v_texCoord = a_texCoord;
	v_Position = vec4(u_MVMatrix * a_Position).xyz;
	v_Normal = vec4(u_NormalMatrix * vec4(a_Normal, 1.0)).xyz;
	v_ShadowCoord = u_ShadowProjMatrix * a_Position;
	gl_Position = u_MVPMatrix * a_Position;
}
