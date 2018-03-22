precision highp float;

uniform vec3 u_lightPos;
uniform sampler2D u_Texture;
uniform sampler2D u_ShadowTexture;
uniform vec3 u_lightColour;
uniform vec3 u_ViewPos;

varying vec3 v_Position;
varying vec3 v_Normal;
varying vec2 v_texCoord;
varying vec4 v_Color;
varying vec4 v_ShadowCoord;
 
float calculateShadow(){
	vec3 projCoords = v_ShadowCoord.xyz / v_ShadowCoord.w;
	vec3 projCoordsWithBiasMatrix = projCoords * 0.5 + 0.5;
	
	float closestDepth = texture2D(u_ShadowTexture, projCoordsWithBiasMatrix.xy).r;
	float bias = 0.0005 * tan(acos(dot(v_Normal, u_lightPos)));
	bias = clamp(bias, 0.0, 0.01);
	float currentDepth = projCoordsWithBiasMatrix.z - bias;
	float shadow = currentDepth > closestDepth ? 1.0 : 0.0;
	
	shadow = v_ShadowCoord.w > 0.0 ? shadow : 1.0;

	return shadow;
}
 
 void main() {
	vec4 texel = texture2D(u_Texture, v_texCoord);
	
	if (texel.a < 0.5) discard;
	
	vec3 ambient = texel.rgb * 0.1;

	vec3 lightVec = u_lightPos - v_Position;
	lightVec = normalize(lightVec);
	
	float diff = max(dot(lightVec, v_Normal), 0.0);
	vec3 diffuse = diff * u_lightColour;
	
	vec3 viewDir = u_ViewPos - v_Position;
	viewDir = normalize(viewDir);
	
	float spec = 0.0;
	
	vec3 halfwayDir = lightVec + viewDir;  
	halfwayDir = normalize(halfwayDir);
	
	spec = pow(max(dot(v_Normal, halfwayDir), 0.0), 64.0);
	vec3 specular = spec * u_lightColour;
	
	float shadow = (diff > 0.01) ? calculateShadow() : 1.0;
	
	vec3 lighting = (ambient + (1.0 - shadow) * (diffuse + specular)) * texel.rgb;    
	
	gl_FragColor = vec4(lighting, texel.a);
 }
