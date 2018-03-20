precision lowp float;

varying vec4 v_Color;

uniform float u_Time;
uniform float u_Lighting;
uniform float u_Intensity;
uniform vec2 u_Resolution;

float generateSnow(vec2 uv, float scale) {
	uv += u_Time / scale;
	uv.x += sin(uv.y + u_Time / 2.0);
	uv.y += u_Time / scale;
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
	vec2 uv= (gl_FragCoord.xy * 2.0 - u_Resolution.xy) / min(u_Resolution.x, u_Resolution.y);
	
	float fog = smoothstep(1.0, 0.3, clamp(uv.y * 0.2 + 0.8, 0.0, 1.0)) / 2.0;
	
	fog += generateSnow(uv, 10.0);
	fog += generateSnow(uv, 8.0);
	fog += generateSnow(uv, 6.0);
	fog += generateSnow(uv, 5.0);
	
	float lighting = max(u_Lighting, 0.5);
	
	vec3 finalColour = vec3(fog) * lighting;
	
	gl_FragColor = vec4(finalColour, v_Color.a * u_Intensity);
}