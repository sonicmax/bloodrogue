precision lowp float;

varying vec4 v_Color;

uniform float u_Time;
uniform float u_Lighting;
uniform vec2 u_Resolution;
uniform float u_Intensity;

void main() {
	vec2 uv = gl_FragCoord.xy / u_Resolution.xy;
	vec3 gradiant = vec3(smoothstep(1.0, 0.3, clamp(uv.y * 0.2 + 0.8, 0.0, 1.0)));
	gl_FragColor = vec4(gradiant * u_Intensity, v_Color.a * u_Intensity);
}