package autismclient.util;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Native chooser stays open until selection/cancellation, including worker cancellation. */
public final class AutismFilePicker {
    private AutismFilePicker() {}

    public static String chooseTextFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.txt")).flip();
            return TinyFileDialogs.tinyfd_openFileDialog("Import Minecraft tokens", "", filters,
                "Text files (*.txt)", false);
        }
    }
}
