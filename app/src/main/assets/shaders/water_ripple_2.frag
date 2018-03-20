precision highp float;

uniform float u_Time;
uniform sampler2D u_Texture;
uniform vec2 u_waveData;

varying vec4 v_Color;
varying vec2 v_texCoord;

vec3 sunDirection = normalize(vec3(0.0, -1.0, 0.0));
vec3 sunColor = vec3(1.0, 0.8, 0.7);

vec4 getLayers(vec2 uv) {
    vec2 uv0 = uv + vec2(u_Time/17.0, u_Time/29.0);
    vec2 uv1 = uv - vec2(u_Time/101.0, u_Time/97.0);
    vec2 uv2 = uv - vec2(u_Time/109.0, u_Time/-113.0);
    vec4 layers = texture2D(u_Texture, uv0) + texture2D(u_Texture, uv1) + texture2D(u_Texture, uv2);
    return layers;
}

void sunLight(const vec3 surfaceNormal, const vec3 eyeDirection, float shiny, float spec, float diffuse, inout vec3 diffuseColor, inout vec3 specularColor){
    vec3 reflection = normalize(reflect(-sunDirection, surfaceNormal));
    float direction = max(0.0, dot(eyeDirection, reflection));
    specularColor += pow(direction, shiny)*sunColor*spec;
    diffuseColor += max(dot(sunDirection, surfaceNormal),0.0)*sunColor*diffuse;
}

void main() {
	vec2 uv = v_texCoord.xy;
    vec4 layers = getLayers(uv);
    vec3 surfaceNormal = normalize(layers.xzy *vec3(2.0, 1.0, 2.0));

    vec3 diffuse = vec3(0.3);
    vec3 specular = vec3(0.0);

    vec3 worldToEye = vec3(0.0, 1.0, 0.0);
    vec3 eyeDirection = normalize(worldToEye);
    sunLight(surfaceNormal, eyeDirection, 100.0, 1.5, 0.5, diffuse, specular);

	
    gl_FragColor = vec4((diffuse+specular), 1.0);  
}