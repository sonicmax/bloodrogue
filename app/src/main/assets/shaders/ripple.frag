precision lowp float;

uniform float u_Time;
uniform sampler2D u_Texture;

void main(void) {
	vec2 resolution = vec2(16.0, 16.0);
	vec2 cPos = -1.0 + 2.0 * gl_FragCoord.xy / resolution.xy;
	float cLength = length(cPos);
	
	vec2 uv = gl_FragCoord.xy / resolution.xy + (cPos / cLength) * cos(cLength * 12.0 - time * 4.0) * 0.03;
	vec3 col = texture2D(u_Texture, uv).xyz;

	gl_FragColor = vec4(col, 1.0);
}