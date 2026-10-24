package dev.wayfarer.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Checks the resource pack source before it ships: JSON parses, the pack format is 26.3's, every wayfarer: model and
 * texture an item, model or equipment file names exists, textures are PNGs sized in multiples of 16, and every
 * texture is credited. Returns one line per problem; empty means the pack is good.
 */
final class PackValidator {
    static final int FORMAT = 97;
    private static final String NS = "wayfarer:";

    private final Path root;
    private final List<String> problems = new ArrayList<>();

    private PackValidator(Path root) {
        this.root = root;
    }

    static List<String> check(Path root) throws IOException {
        PackValidator v = new PackValidator(root);
        v.run();
        return v.problems;
    }

    private void run() throws IOException {
        for (String required : List.of("pack.mcmeta", "LICENSE.txt", "CREDITS.md")) {
            if (!Files.isRegularFile(root.resolve(required))) {
                problems.add("missing " + required);
            }
        }
        JsonObject meta = json(root.resolve("pack.mcmeta"));
        if (meta != null) {
            JsonObject pack = meta.getAsJsonObject("pack");
            if (pack == null || !pack.has("min_format") || pack.get("min_format").getAsInt() != FORMAT
                    || !pack.has("max_format") || pack.get("max_format").getAsInt() != FORMAT) {
                problems.add("pack.mcmeta must declare min_format and max_format " + FORMAT);
            }
        }
        Path assets = root.resolve("assets/wayfarer");
        for (Path file : files(assets.resolve("items"), ".json")) {
            JsonObject item = json(file);
            if (item != null) {
                walk(item, (key, value) -> {
                    if (key.equals("model")) {
                        model(file, value);
                    }
                });
            }
        }
        for (Path file : files(assets.resolve("models"), ".json")) {
            JsonObject model = json(file);
            if (model == null) {
                continue;
            }
            if (model.has("parent")) {
                model(file, model.get("parent").getAsString());
            }
            if (model.get("textures") instanceof JsonObject textures) {
                textures.entrySet().forEach(e -> {
                    String value = e.getValue().getAsString();
                    if (!value.startsWith("#")) {
                        texture(file, value, "textures/");
                    }
                });
            }
        }
        for (Path file : files(assets.resolve("equipment"), ".json")) {
            JsonObject equipment = json(file);
            if (equipment != null && equipment.get("layers") instanceof JsonObject layers) {
                layers.entrySet().forEach(layer -> layer.getValue().getAsJsonArray().forEach(entry -> {
                    if (entry.getAsJsonObject().has("texture")) {
                        texture(file, entry.getAsJsonObject().get("texture").getAsString(),
                                "textures/entity/equipment/" + layer.getKey() + "/");
                    }
                }));
            }
        }
        // Every other JSON file (lang, atlases, animation .mcmeta) must at least parse.
        for (Path file : files(root.resolve("assets"), "")) {
            String n = file.toString();
            if ((n.endsWith(".json") || n.endsWith(".mcmeta")) && !n.contains("/items/") && !n.contains("/models/")
                    && !n.contains("/equipment/")) {
                json(file);
            }
        }
        java.util.Set<String> credited = credited();
        List<Path> art = new ArrayList<>(files(root.resolve("assets/wayfarer/textures"), ""));
        if (Files.isRegularFile(root.resolve("pack.png"))) {
            art.add(root.resolve("pack.png"));
        }
        for (Path file : art) {
            String relative = name(file);
            if (!credited.contains(relative)) {
                problems.add(relative + " is not credited in CREDITS.md");
            }
        }
        for (Path png : files(root.resolve("assets"), ".png")) {
            String relative = name(png);
            int[] size = pngSize(png);
            if (size == null) {
                problems.add(relative + " is not a PNG");
            } else if (size[0] % 16 != 0 || size[1] % 16 != 0) {
                problems.add(relative + " is " + size[0] + "x" + size[1] + "; sides must be multiples of 16");
            }
        }
    }

    /**
     * Files credited in CREDITS.md: "- `path`" lines under an author's "## " heading (Wayfarer, or a borrowed
     * pack's author with its source). A path listed before any heading has no author and does not count.
     */
    private java.util.Set<String> credited() throws IOException {
        java.util.Set<String> out = new java.util.HashSet<>();
        Path file = root.resolve("CREDITS.md");
        if (!Files.isRegularFile(file)) {
            return out;
        }
        boolean underAuthor = false;
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("## ")) {
                underAuthor = !line.substring(3).isBlank();
            } else if (underAuthor && line.startsWith("- `") && line.indexOf('`', 3) > 3) {
                out.add(line.substring(3, line.indexOf('`', 3)));
            }
        }
        return out;
    }

    /** A model reference: wayfarer: ones must exist; minecraft: ones are vanilla's. */
    private void model(Path from, String ref) {
        if (ref.startsWith(NS) && !Files.isRegularFile(root.resolve("assets/wayfarer/models/" + ref.substring(NS.length()) + ".json"))) {
            problems.add(name(from) + ": model " + ref + " does not exist");
        }
    }

    private void texture(Path from, String ref, String folder) {
        if (ref.startsWith(NS) && !Files.isRegularFile(root.resolve("assets/wayfarer/" + folder + ref.substring(NS.length()) + ".png"))) {
            problems.add(name(from) + ": texture " + ref + " does not exist");
        }
    }

    private JsonObject json(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            problems.add(name(file) + " is not valid JSON: " + e.getMessage());
            return null;
        }
    }

    private List<Path> files(Path dir, String suffix) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    private String name(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** Calls the visitor for every string value in the tree, with its key. */
    private static void walk(JsonElement element, java.util.function.BiConsumer<String, String> visitor) {
        if (element instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                    visitor.accept(e.getKey(), e.getValue().getAsString());
                } else {
                    walk(e.getValue(), visitor);
                }
            }
        } else if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> walk(child, visitor));
        }
    }

    /** Width and height from a PNG's header, or null if it is not a PNG. */
    private static int[] pngSize(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(24);
            if (head.length < 24 || (head[0] & 0xFF) != 0x89 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
                return null;
            }
            return new int[] {readInt(head, 16), readInt(head, 20)};
        }
    }

    private static int readInt(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }
}
