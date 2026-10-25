package dev.wayfarer.core;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Dialog screens for every module (the Journal, the shop, animal crates): a title, body parts and a grid of buttons.
 * A button's action runs on the main thread and either shows the next screen or calls {@link #close}. The screen stays up
 * until the server does one of those (the client's "waiting for server" screen is avoided on purpose: it ignores the
 * close packet, so closing would hang for 5 seconds). Buttons work for 10 minutes after the screen opens.
 */
public final class Menus {
    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(10)).build();

    /** One button: its label, the tooltip on hover (may be empty), its width in pixels and what it does. */
    public record Button(Component label, Component tooltip, int width, Consumer<Player> click) {
        public Button(Component label, Component tooltip, Consumer<Player> click) {
            this(label, tooltip, 150, click);
        }
    }

    /** Clicks closer together than this from one player are one click: the screen stays up until the server answers. */
    private static final long DEBOUNCE_MS = 300;
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();

    private Menus() {
    }

    /** A paragraph of text, 300 pixels wide. */
    public static DialogBody text(Component text) {
        return DialogBody.plainMessage(text, 300);
    }

    /** An item drawn with its tooltip on hover, and a line beside it. */
    public static DialogBody item(ItemStack item, Component description) {
        return DialogBody.item(item).description(DialogBody.plainMessage(description, 200)).showTooltip(true).build();
    }

    /** Shows the screen; {@code exit} is the button at the bottom (Close, Back). */
    public static void show(Player player, Component title, List<? extends DialogBody> body, List<Button> buttons,
            int columns, Button exit) {
        // A multi-action dialog needs at least one button; with none, the exit button alone is a notice.
        DialogType type = buttons.isEmpty() ? DialogType.notice(button(exit))
                : DialogType.multiAction(buttons.stream().map(Menus::button).toList())
                        .columns(Math.max(1, columns)).exitAction(button(exit)).build();
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE).body(body).build())
                .type(type));
        player.showDialog(dialog);
    }

    /** Closes whatever screen the player has open. */
    public static void close(Player player) {
        player.closeDialog();
    }

    private static boolean repeated(Player player) {
        long now = System.currentTimeMillis();
        Long last = LAST_CLICK.put(player.getUniqueId(), now);
        return last != null && now - last < DEBOUNCE_MS;
    }

    private static ActionButton button(Button b) {
        return ActionButton.builder(b.label()).tooltip(b.tooltip()).width(b.width())
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && !repeated(p)) {
                        b.click().accept(p);
                    }
                }, OPTIONS)).build();
    }
}
