precision lowp float;

varying vec4 v_Color;

uniform float u_Time;
uniform float u_Lighting;
uniform float u_Intensity;
uniform vec2 u_Resolution;

float generateRain(vec2 uv, float scale) {
	uv += u_Time / scale;
	uv.y += u_Time * 12.0 / scale;
	uv *= scale;
	
	vec2 fl = floor(uv);
	vec2 fr = fract(uv);
	
	float intensity = 1.0;
	float density = 6.0;
	
	mat2 displacementMatrix = mat2(7.0, 3.0, 6.0, 5.0);
	
	vec2 p = intensity * sin(density * fract(sin((fl + scale) * displacementMatrix))) - fr;
	
	float d = min(length(p), 3.0);
	
	float k = smoothstep(0.0, d, sin(fr.x + fr.y) * 0.01);
	
	return k * u_Intensity;
}

void main() {
	vec2 uv = gl_FragCoord.xy / u_Resolution.xy;
	
	float rain = 0.0;
	
	rain += generateRain(uv, 12.0);
	rain += generateRain(uv, 8.0);
	rain += generateRain(uv, 6.0);
	rain += generateRain(uv, 5.0);
	
	vec3 rainColour = vec3(0.55, 0.67, 0.86);
	
	float lighting = max(u_Lighting, 0.5);
	
	vec3 finalColour = vec3(rain) * rainColour * lighting;
	
	gl_FragColor = vec4(finalColour, v_Color.a * u_Intensity);
}