precision lowp float;

uniform float u_Time;
uniform sampler2D u_Texture;
uniform vec2 u_waveData;

varying vec4 v_Color;
varying vec2 v_texCoord;

void main() {
	vec2 resolution = vec2(1.0, 1.0);
	vec2 cPos = v_texCoord.xy / resolution.xy;
	float cLength = length(cPos);

	vec2 uv = v_texCoord.xy / resolution.xy + (cPos / cLength) * cos(cLength * 12.0 - u_Time * 4.0) * 0.03;

	gl_FragColor = texture2D(u_Texture, uv) * v_Color;	
	gl_FragColor.rgb *= v_Color.a;
	gl_FragColor.a = 0.5;
}