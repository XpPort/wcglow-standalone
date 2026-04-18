#version 120

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

void main(){
    vec4 center = texture2D(DiffuseSampler, texCoord);
    vec4 left = texture2D(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture2D(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up = texture2D(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down = texture2D(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y));

    float leftDiff = abs(center.a - left.a);
    float rightDiff = abs(center.a - right.a);
    float upDiff = abs(center.a - up.a);
    float downDiff = abs(center.a - down.a);
    float edge = clamp(leftDiff + rightDiff + upDiff + downDiff, 0.0, 1.0);

    vec3 glowColor = vec3(0.12, 1.0, 0.12) * edge;
    gl_FragColor = vec4(glowColor, edge);
}
