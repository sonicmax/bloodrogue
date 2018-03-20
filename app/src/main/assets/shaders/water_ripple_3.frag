precision highp float;

uniform float u_Time;
uniform sampler2D u_Texture;
uniform vec2 u_waveData;

varying vec4 v_Color;
varying vec2 v_texCoord;

float speed = 0.1;                      
float frequency = 2.0;

vec2 resolution = vec2(1.0, 1.0);             

vec2 shift(vec2 p) {                       
    float d = u_Time * speed;
    vec2 f = frequency * (p + d);
	float a = cos(f.x - f.y) * cos(f.y);
	float b = sin(f.x + f.y) * sin(f.y);
    vec2 q = cos(vec2(a, b));
	
    return q;                                  
}                                             

void main() {
    vec2 r = v_texCoord / resolution;                      
    vec2 p = shift(r);             
    vec2 q = shift(r + 1.0);                        
    float amplitude = 2.0 / resolution.x;
    vec2 s = r + amplitude * (p - q);
    s.y = 1.0 - s.y;
    gl_FragColor = texture2D(u_Texture, s) * v_Color;
	gl_FragColor.a = v_Color.a;
}