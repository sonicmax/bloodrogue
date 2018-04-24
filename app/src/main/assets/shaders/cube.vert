precision highp float;

uniform mat4 u_ModelMatrix;
uniform mat4 u_MVMatrix;
uniform mat4 u_MVPMatrix;
uniform mat4 u_NormalMatrix;
uniform mat4 u_LightMvpMatrix;
uniform vec3 u_ViewPos;
uniform vec3 u_SunPos;
uniform vec3 u_SunPosModel;

attribute vec4 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_texCoord;
attribute vec4 a_Color;

varying vec3 v_Position;
varying vec3 v_Normal;
varying vec2 v_texCoord;
varying vec4 v_Color;
varying vec4 v_ShadowCoord;
varying vec4 v_ModelPosition;
varying float v_ShadowDistance;
varying vec3 v_SunDirection;
varying float v_SunVisibility;

const float shadowDistance = 350.0;
const float shadowTransition = 10.0;

void main() {
	v_Color = a_Color;
    v_texCoord = a_texCoord;
	v_ModelPosition = a_Position;
	v_Position = vec3(u_MVMatrix * a_Position);
	v_Normal = vec3(u_NormalMatrix * vec4(a_Normal, 0.0));
	v_ShadowCoord = u_LightMvpMatrix * a_Position;
	gl_Position = u_MVPMatrix * a_Position;
	
	// We want to fade shadows out as fragments reach the edge of the depth map view frustum.
	float distance = distance(u_ViewPos, v_Position) - (shadowDistance - shadowTransition);
	distance = distance / shadowDistance;
	v_ShadowDistance = clamp(1.0 - distance, 0.0, 1.0);
	
	// We use normal of sun position for directional lighting calculations
	v_SunDirection = normalize(u_SunPos);
	
	// Determine extent that sun is visible in sky.
	
	// Sun begins to be visible when y position > -0.2, and is fully visible at 0.2.
	// Sun visibility is clamped to range of [0, 1]
	float sunRadius = 0.2;
	float visibleRange = 0.4;
	float visibility = (u_SunPosModel.y + sunRadius) / visibleRange;
	v_SunVisibility = clamp(visibility, 0.0, 1.0);
}
