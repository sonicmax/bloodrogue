precision highp float;

uniform sampler2D u_Texture;
uniform sampler2D u_DepthMap;
uniform vec3 u_ViewPos;
uniform vec3 u_SunPos;
uniform vec3 u_SunPosModel;
uniform float u_StartFadeDistance;
uniform float u_EndFadeDistance;
uniform float u_Far;
uniform float u_ClippingPlane;
uniform int u_CheckBackFace;
uniform sampler2D u_SkyGradientWithSun;
uniform sampler2D u_SkyGradient;
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
 
float calculateShadow() {
	float sampledDepth = texture2D(u_DepthMap, v_ShadowCoord.xy).r;
	
	// Subtract a small bias from the current depth to help prevent shadow acne.
	const float depthBias = 0.00045;
	float currentDepth = v_ShadowCoord.z - depthBias;
	
	// Shadows will become lighter towards end of sampling range.
	return currentDepth > sampledDepth ? (1.0 - (1.0 * v_ShadowDistance)) * v_SunVisibility : v_SunVisibility;
}

float calculateDiffuse() {
	return max(0.0, dot(v_SunDirection, v_Normal));
}

float calculateSpriteDiffuse() {
	// To light sprites correctly from both sides, we need to reverse the normal for back facing triangles.
	// This should only be used when it's likely that we will see both faces (ie. not for terrain meshes)
	vec3 normal = gl_FrontFacing ? v_Normal : -v_Normal;
	float coefficient = max(0.0, dot(v_SunDirection, normal));
	
	// Set a minimum brightness for sides not facing light when other side is lighted
	const float minSpriteDiffuse = 0.4;
	return max(1.0 - coefficient, minSpriteDiffuse);
}

float calculateDistanceFactor(float distance) {
	// Distance that terrain colour begins to desaturate
	const float aerialPerspectiveStart = 500.0;	
	
	// Beyond this distance, terrain colour will be fully desaturated
	const float aerialPerspectiveEnd = 5000.0;	
	
	if (distance <= aerialPerspectiveStart) return 0.0;
    if (distance >= aerialPerspectiveEnd) return 1.0;
	
    return 1.0 - (aerialPerspectiveEnd - distance) / (aerialPerspectiveEnd - aerialPerspectiveStart);
}

vec3 desaturate(vec3 colour, float amount) {
	vec3 weightedValues = vec3(0.3, 0.59, 0.11);
    vec3 scaledColour = colour * weightedValues;
    vec3 luminance = vec3(scaledColour.r + scaledColour.g + scaledColour.b);
    return mix(colour, luminance, amount);
}

vec3 sampleSkyColour() {
	float altitude = 0.9;
	vec2 texCoords = vec2(u_TimeOfDay, altitude);
	vec3 skyWithSunColour = texture2D(u_SkyGradientWithSun, texCoords).rgb;
	vec3 skyColour = texture2D(u_SkyGradient, texCoords).rgb;
	
	float sunProximity = dot(v_SunDirection, normalize(v_Position));
	
	return mix(skyColour, skyWithSunColour, sunProximity * 0.5 + 0.5);
}
 
void main() {
	// Discard fragments outside of clipping plane (used for reflections)
	if (v_ModelPosition.y < u_ClippingPlane) discard;
	
	vec4 texel = texture2D(u_Texture, v_texCoord);
	
	// Todo: figure out better way to handle alpha blending so we don't have to discard fragments
	if (texel.a < 0.5) discard;
	
	// vec3 surfaceToLight = normalize(u_SunPos - v_Position);
	// vec3 surfaceToCamera = normalize(u_ViewPos - v_Position);
	float distanceFromEye = distance(u_ViewPos, v_Position);
	float distanceFactor = calculateDistanceFactor(distanceFromEye);
	
	// Basic colours for fragment
	vec3 materialColour = texel.rgb;
	const float ambientIntensity = 0.3;
	vec3 ambientColour = materialColour * ambientIntensity;
	
	// Check whether we need to consider back facing triangles in our lighting
	float diffuseCoefficient = (u_CheckBackFace == 1) ? calculateSpriteDiffuse() : calculateDiffuse();
	vec3 diffuseComponent = materialColour * diffuseCoefficient;
	
	// Cast shadows if fragment is contained in depth map, otherwise use sun visibility to determine extent of lighting
	float shadowFactor = v_ShadowDistance > 0.0 ? calculateShadow() : v_SunVisibility;
	vec3 lighting = diffuseComponent * shadowFactor;
	
	// Now determine effects of aerial perspective on fragment colour:
	
	// As terrain becomes more distant, we should reduce colour saturation
	vec3 desaturatedColour = desaturate(ambientColour + lighting, distanceFactor);
	
	// Colour of distant fragment should be influenced by sky colour at horizon
	vec3 skyColour = sampleSkyColour();
	
	vec3 distantColour = mix(desaturatedColour, skyColour, 0.5);
	
	// Mix colours depending on distance to get final colour for fragment
	vec3 finalColour = mix(ambientColour + lighting, distantColour, distanceFactor);
	
	gl_FragColor = vec4(finalColour, texel.a);
 }
