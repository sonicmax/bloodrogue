precision highp float;

uniform mat4 u_MVPMatrix;
uniform vec3 u_SunPosition;

attribute vec4 a_Position;

varying vec3 v_Position;
varying vec3 v_SunNormal;

void main() {
  v_Position = a_Position.xyz;
  v_SunNormal = normalize(u_SunPosition);
  gl_Position = u_MVPMatrix * a_Position;
}