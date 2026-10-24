package dev.wayfarer.alloys;

/** What the server knows by name, so alloys.yml can be checked without server types. */
public interface AlloyVocabulary {
    /** A lower-case vanilla item key such as iron_ingot. */
    boolean isItem(String key);

    /** A lower-case vanilla block key such as magma_block. */
    boolean isBlock(String key);

    /** A lower-case attribute key such as luck or water_movement_efficiency. */
    boolean isAttribute(String key);

    /** Accepts everything; for tests that only check structure. */
    AlloyVocabulary ANY = new AlloyVocabulary() {
        @Override
        public boolean isItem(String key) {
            return true;
        }

        @Override
        public boolean isBlock(String key) {
            return true;
        }

        @Override
        public boolean isAttribute(String key) {
            return true;
        }
    };
}
