package dev.andre.modelanchor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class AttachmentStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelAnchor");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("modelanchor.json");
    private static final Path MODELS_DIR = FabricLoader.getInstance().getConfigDir().resolve("modelanchor/models");
    private static List<Attachment> attachments = new ArrayList<>();

    private AttachmentStore() {
    }

    public static synchronized void load() {
        try {
            Files.createDirectories(MODELS_DIR);
            if (Files.exists(CONFIG_FILE)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                    Document document = GSON.fromJson(reader, Document.class);
                    attachments = document == null || document.attachments == null
                            ? new ArrayList<>() : deepCopy(document.attachments);
                }
            } else {
                attachments = new ArrayList<>();
                save();
            }
        } catch (IOException | JsonParseException exception) {
            LOGGER.error("Could not read ModelAnchor settings; starting with an empty list.", exception);
            attachments = new ArrayList<>();
        }
    }

    public static synchronized List<Attachment> snapshot() {
        return deepCopy(attachments);
    }

    public static synchronized void replaceAll(List<Attachment> changed) {
        attachments = deepCopy(changed);
        save();
        ObjMeshCache.preload(attachments);
    }

    public static synchronized void add(Attachment attachment) {
        attachments.add(attachment.copy());
        save();
        ObjMeshCache.preload(attachments);
    }

    public static synchronized void remove(int index) {
        if (index < 0 || index >= attachments.size()) return;
        attachments.remove(index);
        save();
        ObjMeshCache.preload(attachments);
    }

    public static Path modelsDirectory() {
        return MODELS_DIR;
    }

    public static Path importObj(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("Choose an existing OBJ file.");
        }
        if (!source.getFileName().toString().toLowerCase().endsWith(".obj")) {
            throw new IOException("The selected file must end in .obj.");
        }
        long bytes = Files.size(source);
        if (bytes > 32L * 1024L * 1024L) {
            throw new IOException("OBJ files are limited to 32 MiB in this version.");
        }

        Files.createDirectories(MODELS_DIR);
        String base = source.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        String stem = base.toLowerCase().endsWith(".obj") ? base.substring(0, base.length() - 4) : base;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path destinationFolder = MODELS_DIR.resolve(stem + "-" + stamp);
        Files.createDirectories(destinationFolder);
        Path destination = destinationFolder.resolve(base);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

        // Preserve the local material-library files referenced by "mtllib".
        try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("mtllib ")) continue;
                String[] names = trimmed.substring(7).trim().split("\\s+");
                for (String name : names) {
                    Path mtlSource = source.getParent().resolve(name).normalize();
                    if (mtlSource.startsWith(source.getParent()) && Files.isRegularFile(mtlSource)
                            && Files.size(mtlSource) <= 5L * 1024L * 1024L) {
                        Files.copy(mtlSource, destinationFolder.resolve(mtlSource.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        // Parse once during import so broken files are reported in the editor instead of during rendering.
        ObjMesh mesh = ObjMesh.load(destination);
        ObjMeshCache.put(destination.toString(), mesh);
        return destination;
    }

    private static List<Attachment> deepCopy(List<Attachment> source) {
        List<Attachment> copy = new ArrayList<>();
        if (source != null) {
            for (Attachment attachment : source) {
                if (attachment != null) copy.add(attachment.copy());
            }
        }
        return copy;
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Document document = new Document();
            document.attachments = deepCopy(attachments);
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(document, writer);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save ModelAnchor settings.", exception);
        }
    }

    private static final class Document {
        int schemaVersion = 1;
        List<Attachment> attachments = new ArrayList<>();
    }
}
