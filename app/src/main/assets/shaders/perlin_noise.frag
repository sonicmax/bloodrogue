precision lowp float;
uniform vec2 u_Resolution;
uniform vec2 u_waveData;
uniform sampler2D u_Texture;

varying vec4 v_Color;
varying vec2 v_texCoord;

vec2 hash(vec2 co) {
    float m = dot(co, vec2(12.9898, 78.233));
    return fract(vec2(sin(m),cos(m))* 43758.5453) * 2. - 1.;
}

float perlinNoise(vec2 uv) {
    vec2 PT  = floor(uv);
    vec2 pt  = fract(uv);

    vec4 grads = vec4(
        dot(hash(PT + vec2(.0, 1.)), pt-vec2(.0, 1.)),   dot(hash(PT + vec2(1., 1.)), pt-vec2(1., 1.)),
        dot(hash(PT + vec2(.0, .0)), pt-vec2(.0, .0)),   dot(hash(PT + vec2(1., .0)), pt-vec2(1., 0.))
    );

    return 5.*mix (mix (grads.z, grads.w, pt.x), mix (grads.x, grads.y, pt.x), pt.y);
}

float fbm(vec2 uv) {
    float finalNoise = 0.;
    finalNoise += .50000*perlinNoise(2.*uv);
    finalNoise += .25000*perlinNoise(4.*uv);
    finalNoise += .12500*perlinNoise(8.*uv);
    finalNoise += .06250*perlinNoise(16.*uv);
    finalNoise += .03125*perlinNoise(32.*uv);

    return finalNoise;
}

void main() {
    vec2 position = gl_FragCoord.xy / u_Resolution.y;
    vec4 noise = vec4( vec3( fbm(3.*position) ), 1.0 );
		noise.x = noise.x + u_waveData.y * sin(u_waveData.x + noise.x + noise.y);
		noise.y = noise.y + u_waveData.y * sin(u_waveData.x + noise.x + noise.y);
		vec4 diffuse = texture2D(u_Texture, v_texCoord) * v_Color;
		gl_FragColor = noise * diffuse;
}