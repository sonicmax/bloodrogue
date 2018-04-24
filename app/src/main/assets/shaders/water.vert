precision highp float;

uniform mat4 u_MVMatrix;
uniform mat4 u_MVPMatrix;

attribute vec4 a_Position;
attribute vec3 a_Normal;

varying vec4 v_Position;
varying vec4 v_PositionInViewSpace;
varying vec4 v_Color;
varying vec3 v_Normal;
varying vec4 v_ClipSpace;
varying vec2 v_DuDvCoords;
varying vec3 v_SurfaceToCamera;
varying vec3 v_SurfaceToLight;
varying vec3 v_DirectionalSurfaceToLight;
varying vec3 v_SurfaceToCameraModelSpace;

// Water quad is quite large, so we want a large value for tiling
const float tiling = 128.0;

void main() {
	v_Position = a_Position;
	// Convert coords from [-1, 1] to [0, 1] for texture map, then divide for tiling.
	v_DuDvCoords = (v_Position.xz / 2.0 + 0.5) / tiling;
	v_PositionInViewSpace = u_MVMatrix * a_Position;
	v_Normal = a_Normal;
	v_ClipSpace = u_MVPMatrix * a_Position;
	gl_Position = v_ClipSpace;
}
