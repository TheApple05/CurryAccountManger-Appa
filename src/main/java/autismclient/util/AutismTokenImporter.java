package autismclient.util;

import autismclient.util.tokenimport.TokenImportService;
import autismclient.util.tokenimport.TokenInputParser;
import autismclient.util.tokenimport.TokenRefresher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Connects the embedded Token-refresher adapter to the existing encrypted account store. */
public final class AutismTokenImporter {
    public static final int MAX_INPUT_CHARS = TokenInputParser.MAX_INPUT_CHARS;
    private AutismTokenImporter() {}

    public static String readFile(Path path) throws IOException { return TokenInputParser.readFile(path); }

    public static TokenImportService.Summary importText(String text, BooleanSupplier cancelled,
                                                       Consumer<TokenImportService.Summary> progress) {
        return new TokenImportService(new TokenRefresher(), AutismTokenImporter::storeVerified)
                .importText(text, cancelled, progress);
    }

    public static TokenImportService.StoreResult storeVerified(TokenRefresher.VerifiedAccount verified, BooleanSupplier cancelled) {
        return AutismAccountManager.get().addVerifiedImport(verified, cancelled);
    }
}
