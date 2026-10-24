package dev.wayfarer.core;

import dev.wayfarer.core.progression.Progression;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.logging.Logger;

/** Shared services every module uses. */
public interface Core {
    Logger logger();

    ConfigService config();

    ItemTagService itemTags();

    ChunkDataService chunkData();

    /** Activities, counters and each player's progression record. */
    Progression progression();

    /** The /wayfarer root; modules register their subtrees under their own id while enabling. */
    CommandTree<CommandSourceStack> commands();
}
