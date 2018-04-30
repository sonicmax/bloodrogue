precision highp float;

uniform sampler2D u_Texture;
uniform sampler2D u_NormalMap;
uniform sampler2D u_SkyGradientWithSun;
uniform sampler2D u_SkyGradient;
uniform mat4 u_NormalMatrix;
uniform vec3 u_ViewPos;
uniform vec3 u_SunPos;
uniform vec3 u_SunPosModel;
uniform float u_TimeOfDay;

varying vec3 v_Position;
varying vec3 v_Normal;
varying vec2 v_texCoord;
varying vec4 v_Color;
varying vec4 v_ShadowCoord;
varying vec4 v_ModelPosition;
varying float v_ShadowDistance;
varying float v_SunVisibility;
varying vec3 v_SunDirection;

const float unlitThreshold = 0.3;
 
vec3 getSampledNormal(vec4 texel) {
	vec3 normal = vec3(texel.r * 2.0 - 1.0, texel.b, texel.g * 2.0 - 1.0);
	return normalize(normal);
}

vec3 sampleSkyColour() {
	// Sample our sky gradient texture using time of day (x axis) and altitude (y axis)
	vec3 positionNormal = normalize(v_Position);
	float altitude = -positionNormal.y;	
	vec2 texCoords = vec2(u_TimeOfDay, altitude);
	vec3 skyWithSunColour = texture2D(u_SkyGradientWithSun, texCoords).rgb;
	vec3 skyColour = texture2D(u_SkyGradient, texCoords).rgb;
	
	float sunProximity = dot(v_SunDirection, positionNormal);
	
	return mix(skyColour, skyWithSunColour, sunProximity * 0.5 + 0.5);
}
 
void main() {	
	vec4 texel = texture2D(u_Texture, v_texCoord);
	
	// Todo: figure out better way to handle alpha blending so we don't have to discard fragments
	if (texel.a == 0.0) discard;
	
	// vec3 surfaceToLight = normalize(u_SunPos - v_Position);
	// vec3 surfaceToCamera = normalize(u_ViewPos - v_Position);
	
	// Basic colours for fragment. 
	vec3 materialColour = texel.rgb;
	const float ambientCoefficient = 1.0;
	vec3 ambientColour = ambientCoefficient * materialColour;
	
	// We need to sample the skybox textures to determine colour of unlighted portion of moon.
	ambientColour = (texel.r + texel.g + texel.b <= unlitThreshold) ? sampleSkyColour() : ambientColour;
	
	// Use normal map to determine lighting for fragment
	vec4 normalTexel = texture2D(u_NormalMap, v_texCoord);
	vec3 worldNormal = getSampledNormal(normalTexel);
	vec3 eyeNormal = vec3(u_NormalMatrix * vec4(worldNormal, 1.0));		
	
	float diffuseCoefficient = max(0.0, dot(v_SunDirection, eyeNormal));
	vec3 diffuseComponent = ambientColour * diffuseCoefficient;
	
	vec3 finalColour = ambientColour + diffuseComponent;
	
	gl_FragColor = vec4(finalColour, texel.a);
}
