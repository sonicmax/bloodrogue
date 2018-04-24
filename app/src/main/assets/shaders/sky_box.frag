precision highp float;

uniform sampler2D u_SkyGradientWithSun;
uniform sampler2D u_SkyGradient;
uniform float u_TimeOfDay;

varying vec3 v_Position;
varying vec3 v_SunNormal;

const float dayThreshold = 1.5;

float hash(float n) {
	return fract((1.0 + sin(n)) * 415.92653);
}

float noise3d(vec3 position) {
	float x = floor((400.0 * position.x) + 0.5);
	float y = floor((400.0 * position.y) + 0.5);
	float z = floor((400.0 * position.z) + 0.5);
	
	float xhash = hash(x * 37.0);
	float yhash = hash(y * 57.0);
	float zhash = hash(z * 67.0);
	
	return fract(xhash + yhash + zhash);
}

void main() {
	vec3 positionNormal = normalize(v_Position);
	float sunProximity = dot(v_SunNormal, positionNormal);
	
	// Add gradient to sky depending on position of sun.
	//float proximity = dot(positionNormal, v_SunNormal);
	//vec3 finalColour = mix(u_SkyColour, u_LightColour, proximity);
	
	// Sample our sky gradient texture using time of day (x axis) and altitude (y axis)
	float altitude = -positionNormal.y;
	// Stretch slightly to fix issues with horizon
	altitude = (altitude - 0.1) / 1.1;
	vec2 texCoords = vec2(u_TimeOfDay, altitude);
	vec3 skyWithSunColour = texture2D(u_SkyGradientWithSun, texCoords).rgb;
	vec3 skyColour = texture2D(u_SkyGradient, texCoords).rgb;
	vec3 finalColour = mix(skyColour, skyWithSunColour, sunProximity * 0.5 + 0.5);
	
	float combinedColourValue = finalColour.r + finalColour.g + finalColour.b;
	
	// If combined value of colour channels is less than day threshold, we should render stars
	if (combinedColourValue < dayThreshold) {
		// Generate a random value between 0 and 1
		float starIntensity = noise3d(positionNormal);
		float threshold = 0.99;
		
		// Apply a threshold to keep only the brightest areas
		if (starIntensity >= threshold) {
			starIntensity = pow((starIntensity - threshold) / (1.0 - threshold), 6.0) * (-v_SunNormal.y + 0.1);
			finalColour += vec3(starIntensity);
		}	
	}
	
	gl_FragColor = vec4(finalColour, 1.0);
}