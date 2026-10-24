#version 330

#moj_import <zazahook:rect_coverage.glsl>

in vec4 vertexColor;
in vec2 localPosition;
flat in vec2 rectSize;
flat in float cornerRadius;

out vec4 fragColor;

void main() {
    float outer = rectCoverage(localPosition, rectSize, cornerRadius, 15);
    vec2 innerSize = rectSize - 2.0;
    float inner = 0.0;
    if (innerSize.x > 0.0 && innerSize.y > 0.0) {
        inner = rectCoverage(localPosition, innerSize, max(cornerRadius - 1.0, 0.0), 15);
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * clamp(outer - inner, 0.0, 1.0));
}
