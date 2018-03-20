precision lowp float;

varying vec4 v_Color;
varying vec2 v_texCoord;

uniform sampler2D u_Texture;
uniform vec2 u_waveData;

void main() {		
		vec4 fragColor = texture2D(u_Texture, v_texCoord) * v_Color;
		
		vec4 v_newPos = vec4(
			fragColor.x + u_waveData.y * sin(u_waveData.x + fragColor.x + fragColor.y),
			fragColor.y + u_waveData.y * sin(u_waveData.x + fragColor.x + fragColor.y),
			fragColor.z,
			fragColor.w);
		
		gl_FragColor = v_newPos;
		gl_FragColor.rgb *= v_Color.a;
}