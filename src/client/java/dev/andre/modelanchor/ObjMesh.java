package dev.andre.modelanchor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal, defensive Wavefront OBJ reader. MTL diffuse colors are supported; texture maps are not yet sampled. */
public final class ObjMesh {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelAnchor/OBJ");
    private static final int MAX_VERTICES = 1_000_000;
    private static final int MAX_TRIANGLES = 500_000;
    private final List<MeshVertex> triangles;

    private ObjMesh(List<MeshVertex> triangles) {
        this.triangles = List.copyOf(triangles);
    }

    public int triangleCount() {
        return triangles.size() / 3;
    }

    public static ObjMesh load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("OBJ file not found: " + file);
        if (Files.size(file) > 32L * 1024L * 1024L) throw new IOException("OBJ exceeds the 32 MiB import limit.");

        List<Vec3> positions = new ArrayList<>();
        List<Vec2> uvs = new ArrayList<>();
        List<Vec3> normals = new ArrayList<>();
        List<RawTriangle> rawTriangles = new ArrayList<>();
        Map<String, float[]> materialColors = new HashMap<>();
        String activeMaterial = "";

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] token = line.split("\\s+");
                try {
                    switch (token[0]) {
                        case "v" -> {
                            if (token.length < 4) continue;
                            positions.add(new Vec3(parseFloat(token[1]), parseFloat(token[2]), parseFloat(token[3])));
                            if (positions.size() > MAX_VERTICES) throw new IOException("OBJ exceeds the vertex limit.");
                        }
                        case "vt" -> {
                            if (token.length < 3) continue;
                            uvs.add(new Vec2(parseFloat(token[1]), parseFloat(token[2])));
                        }
                        case "vn" -> {
                            if (token.length < 4) continue;
                            normals.add(normalize(new Vec3(parseFloat(token[1]), parseFloat(token[2]), parseFloat(token[3]))));
                        }
                        case "mtllib" -> {
                            for (int i = 1; i < token.length; i++) {
                                Path mtl = file.getParent().resolve(token[i]).normalize();
                                if (mtl.startsWith(file.getParent()) && Files.isRegularFile(mtl)
                                        && Files.size(mtl) <= 5L * 1024L * 1024L) {
                                    readMaterials(mtl, materialColors);
                                }
                            }
                        }
                        case "usemtl" -> activeMaterial = token.length > 1 ? token[1] : "";
                        case "f" -> {
                            if (token.length < 4) continue;
                            List<FaceIndex> polygon = new ArrayList<>();
                            for (int i = 1; i < token.length; i++) {
                                polygon.add(parseFaceIndex(token[i], positions.size(), uvs.size(), normals.size()));
                            }
                            for (int i = 1; i < polygon.size() - 1; i++) {
                                rawTriangles.add(new RawTriangle(polygon.get(0), polygon.get(i), polygon.get(i + 1), activeMaterial));
                                if (rawTriangles.size() > MAX_TRIANGLES) throw new IOException("OBJ exceeds the triangle limit.");
                            }
                        }
                        default -> {
                            // Object/group/smoothing declarations and other OBJ extensions are safely ignored.
                        }
                    }
                } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                    LOGGER.debug("Skipping malformed OBJ line {} in {}", lineNumber, file.getFileName());
                }
            }
        }

        if (positions.isEmpty() || rawTriangles.isEmpty()) {
            throw new IOException("No triangle geometry was found. Make sure the OBJ contains v and f lines.");
        }

        List<MeshVertex> result = new ArrayList<>(rawTriangles.size() * 3);
        for (RawTriangle triangle : rawTriangles) {
            Vec3 p0 = positions.get(triangle.a.position);
            Vec3 p1 = positions.get(triangle.b.position);
            Vec3 p2 = positions.get(triangle.c.position);
            Vec3 faceNormal = normalize(cross(subtract(p1, p0), subtract(p2, p0)));
            float[] color = materialColors.getOrDefault(triangle.material, new float[]{1.0f, 1.0f, 1.0f});
            result.add(makeVertex(triangle.a, positions, uvs, normals, faceNormal, color));
            result.add(makeVertex(triangle.b, positions, uvs, normals, faceNormal, color));
            result.add(makeVertex(triangle.c, positions, uvs, normals, faceNormal, color));
        }
        return new ObjMesh(result);
    }

    public void emit(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        for (MeshVertex vertex : triangles) {
            buffer.addVertex(pose, vertex.x, vertex.y, vertex.z)
                    .setColor(toByte(vertex.r), toByte(vertex.g), toByte(vertex.b), 255)
                    .setUv(vertex.u, vertex.v)
                    .setOverlay(0)
                    .setLight(light)
                    .setNormal(pose, vertex.nx, vertex.ny, vertex.nz);
        }
    }

    private static MeshVertex makeVertex(FaceIndex index, List<Vec3> positions, List<Vec2> uvs,
                                         List<Vec3> normals, Vec3 faceNormal, float[] color) {
        Vec3 p = positions.get(index.position);
        Vec2 uv = index.uv >= 0 && index.uv < uvs.size() ? uvs.get(index.uv) : new Vec2(0, 0);
        Vec3 n = index.normal >= 0 && index.normal < normals.size() ? normals.get(index.normal) : faceNormal;
        return new MeshVertex(p.x, p.y, p.z, uv.x, 1.0f - uv.y, n.x, n.y, n.z,
                clamp(color[0]), clamp(color[1]), clamp(color[2]));
    }

    private static FaceIndex parseFaceIndex(String text, int positions, int uvs, int normals) {
        String[] fields = text.split("/", -1);
        int p = resolveIndex(fields[0], positions);
        int uv = fields.length > 1 && !fields[1].isEmpty() ? resolveIndex(fields[1], uvs) : -1;
        int n = fields.length > 2 && !fields[2].isEmpty() ? resolveIndex(fields[2], normals) : -1;
        return new FaceIndex(p, uv, n);
    }

    private static int resolveIndex(String text, int size) {
        int raw = Integer.parseInt(text);
        if (raw == 0) throw new IllegalArgumentException("OBJ indices are 1-based.");
        int index = raw > 0 ? raw - 1 : size + raw;
        if (index < 0 || index >= size) throw new IllegalArgumentException("Index is out of bounds.");
        return index;
    }

    private static void readMaterials(Path file, Map<String, float[]> colors) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String active = "";
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] token = line.split("\\s+");
                if (token[0].equals("newmtl") && token.length > 1) {
                    active = token[1];
                    colors.putIfAbsent(active, new float[]{1, 1, 1});
                } else if (token[0].equals("Kd") && token.length >= 4 && !active.isEmpty()) {
                    colors.put(active, new float[]{clamp(parseFloat(token[1])), clamp(parseFloat(token[2])), clamp(parseFloat(token[3]))});
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.warn("Could not read material file {}: {}", file.getFileName(), exception.getMessage());
        }
    }

    private static int toByte(float value) {
        return Math.max(0, Math.min(255, Math.round(clamp(value) * 255.0f)));
    }

    private static float parseFloat(String value) {
        float parsed = Float.parseFloat(value);
        if (!Float.isFinite(parsed)) throw new IllegalArgumentException("Non-finite vertex value.");
        return parsed;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static Vec3 subtract(Vec3 a, Vec3 b) {
        return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }

    private static Vec3 normalize(Vec3 v) {
        double length = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        if (length < 1.0e-8) return new Vec3(0, 1, 0);
        return new Vec3((float) (v.x / length), (float) (v.y / length), (float) (v.z / length));
    }

    private record Vec2(float x, float y) {}
    private record Vec3(float x, float y, float z) {}
    private record FaceIndex(int position, int uv, int normal) {}
    private record RawTriangle(FaceIndex a, FaceIndex b, FaceIndex c, String material) {}
    private record MeshVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz,
                              float r, float g, float b) {}
}
