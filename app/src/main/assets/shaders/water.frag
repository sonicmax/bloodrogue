precision highp float;

uniform sampler2D u_ReflectiveTexture;
uniform sampler2D u_DuDvMap;
uniform sampler2D u_NormalMap;

uniform vec3 u_EyePos;
uniform vec3 u_SunPos;
uniform vec3 u_EyeModelPos;
uniform vec3 u_SunModelPos;
uniform vec3 u_LightColour;
uniform float u_Time;

uniform mat4 u_SkyBoxMvpMatrix;
uniform mat4 u_MVMatrix;
uniform mat4 u_MVPMatrix;
uniform mat4 u_NormalMatrix;

varying vec4 v_ClipSpace;
varying vec4 v_Position;
varying vec4 v_PositionInViewSpace;
varying vec3 v_Normal;
varying vec3 v_WorldNormal;
varying vec3 v_EyeNormal;
varying vec4 v_Color;
varying vec2 v_DuDvCoords;

const float waveStrength = 0.02;
const vec3 shallowWaterColour = vec3(0.0, 0.4, 0.5);
const vec3 deepWaterColour = vec3(0.01, 0.05, 0.24);
const float waterMixFactor = 0.1;
const float minimumWaterDiffuse = 0.2;
const float ambientLightLevel = 0.5;
const float waterAlpha = 0.7;

vec3 getSampledNormal(vec4 texel) {
	vec3 normal = vec3(texel.r * 2.0 - 1.0, texel.b, texel.g * 2.0 - 1.0);
	return normalize(normal);
}

void main() {
	// Calculate normals used for lighting
	vec3 surfaceToCamera = normalize(u_EyePos - v_PositionInViewSpace.xyz);
	vec3 surfaceToLight = normalize(u_SunPos - v_PositionInViewSpace.xyz);
	vec3 directionalSurfaceToLight = normalize(u_SunPos);
    vec3 surfaceToCameraModelSpace = normalize(u_EyeModelPos - v_Position.xyz);
	
	// Find coordinate of reflective texture to display in fragment.
	// We have to convert the clipspace coords from range of [-1, 1] to [0, 1]
	vec2 reflectTexCoords = ((v_ClipSpace.xy / v_ClipSpace.w) / 2.0) + 0.5;
	
	// Distort texture coords using DuDv map + time uniform
	vec4 sampledDuDv = texture2D(u_DuDvMap, vec2(v_DuDvCoords.x + u_Time, v_DuDvCoords.y));
	vec2 distortedTexCoords = sampledDuDv.rg * 0.1;
	distortedTexCoords = v_DuDvCoords + vec2(distortedTexCoords.x, distortedTexCoords.y + u_Time);
	vec2 totalDistortion = (texture2D(u_DuDvMap, distortedTexCoords).rg * 2.0 - 1.0) * waveStrength;
	reflectTexCoords += totalDistortion;
	reflectTexCoords = clamp(reflectTexCoords, 0.001, 0.999);
	
	// Sample reflective texture using distorted coords.
    vec3 reflectionColour = texture2D(u_ReflectiveTexture, reflectTexCoords).rgb;
	
	// Decide the ratio that we should display reflective/refractive texture depending on viewing angle
	// float fresnel = dot(surfaceToCameraModelSpace, vec3(0.0, 1.0, 0.0));	
	
	vec3 ambientComponent = reflectionColour * ambientLightLevel;

	// Sample normal map to obtain surface normals for water
	vec4 normalTexel = texture2D(u_NormalMap, distortedTexCoords);
	vec3 worldNormal = getSampledNormal(normalTexel);
	vec3 eyeNormal = vec3(u_NormalMatrix * vec4(worldNormal, 1.0));		
	
	// Apply diffuse/specular lighting
	float diffuseCoefficient = max(dot(surfaceToLight, eyeNormal), minimumWaterDiffuse);;
	
	vec3 halfwayDir = normalize(surfaceToLight + surfaceToCamera);
	float shine = 64.0;
	float cosAngle = max(0.0, dot(halfwayDir, eyeNormal));
	float specularCoefficient = pow(cosAngle, shine);
	
	vec3 diffuseComponent = ambientComponent * diffuseCoefficient;
	vec3 specularComponent = vec3(1.0) * specularCoefficient;
	
	// Todo: better way to calculate this?
	vec3 finalColour = ambientComponent + diffuseComponent + specularComponent;
	
	// Mix with our sea colour to give the water a slight tint
	finalColour = mix(finalColour, deepWaterColour, waterMixFactor);
	
	gl_FragColor = vec4(finalColour, waterAlpha);
}
