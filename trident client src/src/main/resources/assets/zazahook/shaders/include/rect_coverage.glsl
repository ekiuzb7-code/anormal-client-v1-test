#version 330

const int CORNER_MASK = 15;

float cornerRadiusAt(vec2 position, vec2 rectSize, float cornerRadius, int corners) {
    bool right = position.x > rectSize.x * 0.5;
    bool bottom = position.y > rectSize.y * 0.5;
    int corner = right ? (bottom ? 4 : 2) : (bottom ? 8 : 1);
    return (corners & corner) != 0 ? cornerRadius : 0.0;
}

float boxDistance(vec2 position, vec2 rectSize, float radius) {
    vec2 halfSize = rectSize * 0.5;
    vec2 q = abs(position - halfSize) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float rectDistance(vec2 localPosition, vec2 rectSize, float cornerRadius, int corners) {
    return boxDistance(localPosition, rectSize, cornerRadiusAt(localPosition, rectSize, cornerRadius, corners & CORNER_MASK));
}

float rectCoverage(vec2 localPosition, vec2 rectSize, float cornerRadius, int corners) {
    vec2 dx = dFdx(localPosition);
    vec2 dy = dFdy(localPosition);
    float distanceToEdge = rectDistance(localPosition, rectSize, cornerRadius, corners);
    float filterWidth = max(2.0 * length(vec2(dFdx(distanceToEdge), dFdy(distanceToEdge))), 0.0001);
    float footprint = max(length(dx) + length(dy), 0.0001) + filterWidth;
    float coverage = distanceToEdge < 0.0 ? 1.0 : 0.0;
    if (abs(distanceToEdge) < footprint) {
        coverage = 0.0;
        for (int y = 0; y < 4; ++y) {
            for (int x = 0; x < 4; ++x) {
                vec2 offset = (float(x) - 1.5) * 0.25 * dx + (float(y) - 1.5) * 0.25 * dy;
                float d = rectDistance(localPosition + offset, rectSize, cornerRadius, corners);
                float t = clamp(0.5 - 0.5 * d / filterWidth, 0.0, 1.0);
                coverage += t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            }
        }
        coverage *= 0.0625;
    }
    return coverage;
}
