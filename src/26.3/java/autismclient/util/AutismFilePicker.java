package autismclient.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Uses Minecraft's SDL backend; Java AWT is disabled by the game. */
public final class AutismFilePicker {
    private AutismFilePicker() {}

    public static String chooseTextFile() throws ExecutionException {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread()) throw new IllegalStateException("File selection must wait on a worker.");
        CompletableFuture<String> selection = new CompletableFuture<>();
        boolean interrupted = false;
        try (MemoryStack stack = MemoryStack.stackPush();
             SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((unused, files, filter) -> {
                 if (files == 0) {
                     selection.completeExceptionally(new IllegalStateException("Native file chooser unavailable."));
                 } else {
                     long first = MemoryUtil.memGetAddress(files);
                     selection.complete(first == 0 ? null : MemoryUtil.memUTF8(first));
                 }
             })) {
            SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1, stack);
            filters.get(0).name(stack.UTF8("Text files (*.txt)")).pattern(stack.UTF8("txt"));
            client.execute(() -> {
                try {
                    SDLDialog.SDL_ShowOpenFileDialog(callback, 0, client.getWindow().handle(), filters, "", false);
                } catch (Throwable unavailable) {
                    selection.completeExceptionally(unavailable);
                }
            });
            // The native callback and filter memory must survive until the dialog
            // closes. Cancellation cannot release the screen's one-chooser gate early.
            while (true) {
                try { return selection.get(); }
                catch (InterruptedException cancelled) { interrupted = true; }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
