package dev.andre.modelanchor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ObjMeshCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelAnchor/MeshCache");
    private static final Map<String, ObjMesh> CACHE = new ConcurrentHashMap<>();

    private ObjMeshCache() {
    }

    public static void put(String path, ObjMesh mesh) {
        CACHE.put(path, mesh);
    }

    public static ObjMesh get(String path) {
        if (path == null || path.isBlank()) return null;
        ObjMesh cached = CACHE.get(path);
        if (cached != null) return cached;
        try {
            ObjMesh loaded = ObjMesh.load(Path.of(path));
            CACHE.put(path, loaded);
            return loaded;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not load OBJ '{}': {}", path, exception.getMessage());
            return null;
        }
    }

    public static void preload(List<Attachment> attachments) {
        CACHE.clear();
        for (Attachment attachment : attachments) {
            if (attachment != null && attachment.enabled && attachment.path != null && !attachment.path.isBlank()) {
                get(attachment.path);
            }
        }
    }
}
