package autismclient.gui.screen;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.components.PaletteEditBox;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.util.AutismAccount;
import autismclient.util.AutismAccountGenerator;
import autismclient.util.AutismAccountManager;
import autismclient.util.AutismAccountSessionSwitcher;
import autismclient.util.AutismAccountType;
import autismclient.util.AutismBackgroundTasks;
import autismclient.util.AutismConfig;
import autismclient.util.AutismHttp;
import autismclient.util.AutismMicrosoftLogin;
import autismclient.util.AutismTheme;
import autismclient.util.AutismUiIcons;
import autismclient.util.AutismUiScale;
import autismclient.util.tokenimport.TokenRefresher;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.util.UndashedUuid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultiLineEditBox.Builder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.PlayerSkinRenderCache.RenderInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

public class AutismAccountsScreen extends AutismScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private final Screen parent;
   private final List<CompactOverlayButton> buttons = new ArrayList<>();
   private final List<AutismAccountsScreen.AccountRow> accountRows = new ArrayList<>();
   private final Map<String, AutismAccountsScreen.SkinLookup> skinLookups = new LinkedHashMap<>(16, 0.75F, true);
   private final ScrollState savedListScroll = new ScrollState();
   private AutismAccountType categoryFilter = null;
   private EditBox labelField;
   private EditBox tokenField;
   private EditBox searchField;
   private AutismAccount renamingAccount;
   private Model.Simple widePlayerModel;
   private Model.Simple slimPlayerModel;
   private AutismAccountType type = AutismAccountType.Cracked;
   private AutismAccount selectedAccount;
   private String searchQuery = "";
   private String pendingSearchQuery = "";
   private boolean searchDirty;
   private AutismAccountsScreen.Operation operation = AutismAccountsScreen.Operation.NONE;
   private int operationId;
   private long operationStartedAtMs;
   private Future<?> operationTask;
   private CompactOverlayButton cancelOperationButton;
   private CompactOverlayButton checkButton;
   private int savedListScrollOffset;
   private boolean accountScrollbarDragging;
   private int accountScrollbarGrabOffset;
   private boolean previewDragging;
   private float previewRotationX = -5.0F;
   private float previewRotationY = 30.0F;
   private double previewAutoRotationStartTime = Blaze3D.getTime();
   private double lastPreviewMouseX;
   private double lastPreviewMouseY;
   private boolean accountSnapshotDirty = true;
   private long accountSnapshotRevision;
   private List<AutismAccount> cachedAccountSnapshot = List.of();
   private long cachedFilteredRevision = Long.MIN_VALUE;
   private String cachedAccountQuery = "";
   private int cachedAccountFilterMask = Integer.MIN_VALUE;
   private List<AutismAccount> cachedFilteredAccounts = List.of();
   private List<AutismAccount> cachedDisplaySource;
   private List<AutismAccountsScreen.DisplayAccountRow> cachedDisplayAccounts = List.of();
   private int activePopup = 0;
   private int generatorMode;
   private EditBox generatorCountField;
   private EditBox generatorPasswordField;
   private MultiLineEditBox generatorListField;
   private String generatorResult = "";
   private final List<CompactOverlayButton> popupButtons = new ArrayList<>();
   private final List<CompactDropdown> popupDropdowns = new ArrayList<>();
   private volatile boolean generating;

   public AutismAccountsScreen(Screen var1) {
      super(Component.literal("Accounts"));
      this.parent = var1;
   }

   protected void init() {
      this.invalidateAccountSnapshot();
      int var1 = this.panelX();
      byte var2 = 88;
      this.labelField = new PaletteEditBox(this.font, var1 + 18, var2, 256, 18, Component.literal("Username"));
      this.labelField.setHint(Component.literal("Username"));
      this.labelField.setMaxLength(256);
      this.addRenderableWidget(this.labelField);
      this.tokenField = new PaletteEditBox(this.font, var1 + 18, var2, 256, 18, Component.literal("Token"));
      this.tokenField.setHint(Component.literal("Token"));
      this.tokenField.setMaxLength(4096);
      this.addRenderableWidget(this.tokenField);
      this.searchField = new PaletteEditBox(this.font, var1 + 18, 140, 256, 18, Component.literal("Search accounts"));
      this.searchField.setHint(Component.literal("Search nickname..."));
      this.searchField.setMaxLength(64);
      this.searchField.setResponder(var1x -> {
         this.pendingSearchQuery = safeTrim(var1x);
         this.searchDirty = true;
      });
      this.addRenderableWidget(this.searchField);
      this.widePlayerModel = new Model.Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
      this.slimPlayerModel = new Model.Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
      this.generatorCountField = new PaletteEditBox(this.font, 0, 0, 58, 16, Component.literal("Count"));
      this.generatorCountField.setHint(Component.literal("Count"));
      this.generatorCountField.setMaxLength(4);
      this.generatorCountField.setValue("10");
      this.generatorPasswordField = new PaletteEditBox(this.font, 0, 0, 186, 16, Component.literal("Password"));
      this.generatorPasswordField.setHint(Component.literal("Password"));
      this.generatorPasswordField.setMaxLength(16);
      this.generatorPasswordField.setValue(AutismConfig.getGlobal().accountGenSharedPassword);
      this.generatorListField = new Builder()
         .setX(0)
         .setY(0)
         .setPlaceholder(Component.literal("Paste names here, one per line or name:password"))
         .build(this.font, 240, 108, Component.literal("Account name list"));
      this.updateInputVisibility();
      this.rebuildButtons();
   }

   public void tick() {
      super.tick();
      if (this.searchDirty) {
         this.searchDirty = false;
         this.searchQuery = this.pendingSearchQuery;
         this.savedListScrollOffset = 0;
         this.savedListScroll.jumpTo(0, 0);
         this.rebuildButtons();
      }
   }

   private void rebuildButtons() {
      this.buttons.clear();
      this.accountRows.clear();
      this.checkButton = null;
      int var1 = this.panelX();
      int var2 = var1 + 10;
      this.buttons
         .add(
            CompactOverlayButton.create(10, 0, 76, 18, Component.literal("Back"), var1x -> this.onClose())
               .setVariant(CompactOverlayButton.Variant.SECONDARY)
         );
      this.addTokenTabs();
      if (!this.narrowLayout()) {
         byte var3 = 58;
         this.addProviderButton(var2 + 8, var3, AutismAccountType.Cracked, "Cracked");
         this.addProviderButton(var2 + 77, var3, AutismAccountType.TheAltening, "Altening");
         this.addProviderButton(var2 + 146, var3, AutismAccountType.Session, "Session");
         this.addProviderButton(var2 + 215, var3, AutismAccountType.Microsoft, "Microsoft");
         int var4 = this.type == AutismAccountType.Microsoft ? 84 : 114;
         int var5 = this.type == AutismAccountType.Microsoft ? 276 : 116;
         int var6 = this.type == AutismAccountType.Microsoft ? 20 : 18;
         CompactOverlayButton var7 = CompactOverlayButton.create(
               var2 + 8, var4, var5, var6, Component.literal(this.addButtonLabel()), var1x -> this.addAccount()
            )
            .setVariant(CompactOverlayButton.Variant.PRIMARY);
         var7.active = !this.isBusy();
         this.buttons.add(var7);
         if (this.type != AutismAccountType.Microsoft) {
            CompactOverlayButton var8 = CompactOverlayButton.create(var2 + 128, 114, 64, 18, Component.literal("Clear"), var1x -> this.clearFields())
               .setVariant(CompactOverlayButton.Variant.GHOST);
            var8.active = !this.isBusy();
            this.buttons.add(var8);
         }

         int var27 = this.type == AutismAccountType.Microsoft ? var2 + 8 : var2 + 196;
         int var9 = this.type == AutismAccountType.Microsoft ? 114 : 114;
         int var10 = this.type == AutismAccountType.Microsoft ? 276 : 88;
         CompactOverlayButton var11 = CompactOverlayButton.create(var27, var9, var10, 18, Component.literal("Cancel"), var1x -> this.cancelOperation())
            .setVariant(CompactOverlayButton.Variant.DANGER);
         var11.visible = this.shouldShowCancelOperationButton();
         this.cancelOperationButton = var11;
         this.buttons.add(var11);
         AutismAccountType[] var12 = new AutismAccountType[]{
            AutismAccountType.Cracked, AutismAccountType.Microsoft, AutismAccountType.Session, AutismAccountType.TheAltening, AutismAccountType.Generated
         };
         String[] var13 = new String[]{"Cracked", "Microsoft", "Session", "Altening", "Generated"};

         for (int var14 = 0; var14 < var12.length; var14++) {
            this.addFilterButton(var2 + 8 + var14 * 56, 174, var12[var14], var13[var14]);
         }
      }

      if (!this.compactListLayout()) {
         boolean var17 = this.operation == AutismAccountsScreen.Operation.CHECK;
         int var19 = this.rowRight() - 8 - 64;
         CompactOverlayButton var21 = CompactOverlayButton.create(
            var19, this.listTop() + 3, 64, 16, Component.literal(var17 ? "Checking" : "Check"), var1x -> this.checkExpiredAccounts()
         );
         var21.setVariant(CompactOverlayButton.Variant.SECONDARY);
         var21.active = !this.isBusy() && !this.accountSnapshot().isEmpty();
         this.buttons.add(var21);
         this.checkButton = var21;
         CompactOverlayButton var23 = CompactOverlayButton.create(
            var19 - 4 - 96, this.listTop() + 3, 96, 16, Component.literal("Clear Expired"), var1x -> this.clearExpiredAccounts()
         );
         var23.setVariant(CompactOverlayButton.Variant.DANGER);
         var23.active = !this.isBusy() && this.expiredAccountCount() > 0;
         this.buttons.add(var23);
         CompactOverlayButton var25 = CompactOverlayButton.create(
            var23.getX() - 4 - 60, this.listTop() + 3, 60, 16, Component.literal("Generate"), var1x -> this.openGenerator()
         );
         var25.setVariant(CompactOverlayButton.Variant.SECONDARY);
         this.buttons.add(var25);
         CompactOverlayButton var28 = CompactOverlayButton.create(
            var25.getX() - 4 - 64, this.listTop() + 3, 64, 16, Component.literal("Clear List"), var1x -> this.openClearConfirm()
         );
         var28.setVariant(CompactOverlayButton.Variant.DANGER);
         var28.active = !this.accountSnapshot().isEmpty();
         this.buttons.add(var28);
      }

      List var18 = this.displayAccounts();
      if (!this.compactListLayout()) {
         int var20 = this.savedMaxScroll(var18.size());
         this.savedListScrollOffset = quantizeScrollOffset(this.savedListScrollOffset, 24, var20);
         this.savedListScroll.jumpTo(this.savedListScrollOffset, var20);
         int var22 = this.savedListScrollOffset / 24;
         int var24 = this.savedRowsTop() - this.savedListScrollOffset % 24;

         for (int var26 = var22; var26 < var18.size() && var24 + 24 - 3 <= this.savedRowsBottom(); var26++) {
            AutismAccountsScreen.DisplayAccountRow var29 = (AutismAccountsScreen.DisplayAccountRow)var18.get(var26);
            AutismAccount var30 = var29.account();
            if (var24 + 24 - 3 <= this.savedRowsTop()) {
               var24 += 24;
            } else {
               boolean var31 = var29.defaultAccount() ? this.isCurrentDefaultAccount() : this.isCurrentAccount(var30);
               boolean var32 = !var29.defaultAccount() && var30.type == AutismAccountType.Cracked;
               CompactOverlayButton var33 = CompactOverlayButton.create(
                  this.rowRight() - (var29.defaultAccount() ? 62 : (var32 ? 114 : 88)),
                  this.rowButtonY(var24, 16),
                  54,
                  16,
                  Component.literal(var31 ? "Active" : "Login"),
                  var3x -> {
                     if (var29.defaultAccount()) {
                        this.loginDefaultAccount();
                     } else {
                        this.loginAccount(var30);
                     }
                  }
               );
               var33.setVariant(var31 ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.PRIMARY);
               var33.setSelected(var31).setAnimationKey("acct-active:" + (var29.defaultAccount() ? "default" : var30.stableId()));
               var33.active = !this.isBusy() && !var31;
               CompactOverlayButton var34 = null;
               CompactOverlayButton var35 = null;
               CompactOverlayButton var15 = null;
               if (!var29.defaultAccount()) {
                  if (var32) {
                     var15 = CompactOverlayButton.create(
                        this.rowRight() - 54, this.rowButtonY(var24, 16), 20, 16, Component.empty(), var2x -> this.startRename(var30)
                     );
                     var15.setVariant(CompactOverlayButton.Variant.SECONDARY).setIcon(AutismUiIcons.EDIT);
                     var15.active = !this.isBusy();
                  }

                  var34 = CompactOverlayButton.create(
                     this.rowRight() - 28, this.rowButtonY(var24, 16), 20, 16, Component.empty(), var2x -> this.deleteAccount(var30)
                  );
                  var34.setVariant(CompactOverlayButton.Variant.DANGER).setIcon(AutismUiIcons.TRASH);
                  var34.active = !this.isBusy();
               }

               if (hasShareableSessionToken(var30) && !shareableSessionToken(var30).isBlank()) {
                  int var16 = var29.defaultAccount() ? this.rowRight() - 88 : this.rowRight() - (var32 ? 140 : 114);
                  var35 = CompactOverlayButton.create(var16, this.rowButtonY(var24, 16), 20, 16, Component.empty(), var2x -> this.copySessionToken(var30));
                  var35.setVariant(CompactOverlayButton.Variant.SECONDARY);
                  var35.active = !this.isBusy();
               }

               boolean var36 = this.accountRows.size() < 10;
               this.accountRows.add(new AutismAccountsScreen.AccountRow(var30, var24, var33, var34, var35, var15, var29.defaultAccount(), var36));
               var24 += 24;
            }
         }
      }
   }

   private void addProviderButton(int var1, int var2, AutismAccountType var3, String var4) {
      CompactOverlayButton var5 = CompactOverlayButton.create(var1, var2, 66, 18, Component.literal(var4), var2x -> {
         if (!this.isBusy()) {
            this.type = var3;
            this.clearInputFocus();
            this.updateInputVisibility();
            this.rebuildButtons();
         }
      });
      var5.active = !this.isBusy();
      var5.setVariant(this.type == var3 ? CompactOverlayButton.Variant.FILTER_ON : CompactOverlayButton.Variant.SECONDARY);
      this.buttons.add(var5);
   }

   private void addTokenTabs() {
      int x = this.narrowLayout() ? this.listX() : Math.max(96, this.panelX() + 10);
      int y = this.narrowLayout() ? 26 : 0;
      int available = Math.max(8, Math.min(500, this.screenWidth() - x - 10));
      int width = Math.max(2, (available - 4) / 2);
      CompactOverlayButton switcher = CompactOverlayButton.create(x, y, width, 18,
         Component.literal("Session Token Switcher"), button -> {});
      switcher.setVariant(CompactOverlayButton.Variant.FILTER_ON);
      this.buttons.add(switcher);
      CompactOverlayButton importer = CompactOverlayButton.create(x + width + 4, y, width, 18,
         Component.literal("Session Token Importer"), button -> this.openTokenImporter());
      importer.setVariant(CompactOverlayButton.Variant.SECONDARY);
      importer.active = !this.isBusy();
      this.buttons.add(importer);
   }

   private void openTokenImporter() {
      if (!this.isBusy()) {
         this.minecraft.gui.setScreen(new SessionTokenImporterScreen(this));
      }
   }

   private void addFilterButton(int var1, int var2, AutismAccountType var3, String var4) {
      CompactOverlayButton var5 = CompactOverlayButton.create(var1, var2, 50, 16, Component.literal(var4), var2x -> this.toggleFilter(var3));
      var5.setVariant(this.categoryFilter == var3 ? CompactOverlayButton.Variant.FILTER_ON : CompactOverlayButton.Variant.FILTER_OFF);
      this.buttons.add(var5);
   }

   private void addAccount() {
      if (!this.isBusy()) {
         if (this.renamingAccount != null) {
            String var4 = safeTrim(this.labelField.getValue());
            if (var4.isBlank()) {
               this.toast("Enter a username first.", AccountPalette.GOLDEN_YELLOW);
            } else {
               boolean var2 = this.isCurrentAccount(this.renamingAccount);
               if (AutismAccountManager.get().rename(this.renamingAccount.stableId(), var4)) {
                  this.toast("Renamed to " + var4 + ". Password and proxies carried over.", AccountPalette.SUCCESS_TEXT);
                  if (var2) {
                     AutismAccount var3 = AutismAccountManager.get().findById(this.renamingAccount.stableId());
                     if (var3 != null) {
                        AutismAccountManager.get().login(var3);
                     }
                  }

                  this.renamingAccount = null;
                  this.clearFields();
               } else {
                  this.toast("An account with that name already exists.", AccountPalette.GOLDEN_YELLOW);
               }

               this.invalidateAccountSnapshot();
               this.rebuildButtons();
            }
         } else {
            AutismAccount var1 = new AutismAccount();
            var1.type = this.type;
            if (this.type == AutismAccountType.Cracked) {
               var1.label = safeTrim(this.labelField.getValue());
               if (var1.label.isBlank()) {
                  this.toast("Enter a username first.", AccountPalette.GOLDEN_YELLOW);
                  return;
               }

               this.runAdd(var1);
            } else if (this.type == AutismAccountType.Session) {
               var1.token = safeTrim(this.tokenField.getValue());
               if (var1.token.isBlank()) {
                  this.toast("Paste a token first.", AccountPalette.GOLDEN_YELLOW);
                  return;
               }

               this.runAdd(var1);
            } else if (this.type == AutismAccountType.TheAltening) {
               var1.token = safeTrim(this.tokenField.getValue());
               if (var1.token.isBlank()) {
                  this.toast("Paste a token first.", AccountPalette.GOLDEN_YELLOW);
                  return;
               }

               this.runAdd(var1);
            } else if (this.type == AutismAccountType.Microsoft) {
               this.runMicrosoftAdd();
            }
         }
      }
   }

   private void runAdd(AutismAccount var1) {
      int var2 = this.beginOperation(AutismAccountsScreen.Operation.ADD);
      this.operationTask = AutismBackgroundTasks.runTracked("Autism-Account-Add", () -> {
         boolean var3 = var1.fetchInfo();
         if (!this.isCancelled(var2)) {
            if (!var3) {
               this.finishOperation(var2, false, "Couldn't add account.", false);
            } else if (AutismAccountManager.get().contains(var1)) {
               this.finishOperation(var2, false, "Account already exists.", false);
            } else {
               AutismAccountManager.get().add(var1);
               this.selectedAccount = var1;
               boolean var4 = var1.login();
               if (!this.isCancelled(var2)) {
                  if (!var4) {
                     this.finishOperation(var2, true, "Added, but login failed.", true);
                  } else {
                     this.finishOperation(var2, true, "Logged in as " + var1.displayName() + ".", true);
                  }
               }
            }
         }
      });
   }

   private void runMicrosoftAdd() {
      int var1 = this.beginOperation(AutismAccountsScreen.Operation.MICROSOFT);
      AutismMicrosoftLogin.getRefreshToken(var2 -> {
         if (!this.isCancelled(var1)) {
            if (var2 != null && !var2.isBlank()) {
               AutismAccount var3 = new AutismAccount();
               var3.type = AutismAccountType.Microsoft;
               var3.label = var2;
               this.operationTask = AutismBackgroundTasks.runTracked("Autism-Microsoft-Add", () -> {
                  boolean var3x = var3.fetchInfo();
                  if (!this.isCancelled(var1)) {
                     if (!var3x) {
                        this.finishOperation(var1, false, loginFailMessage(var3), false);
                     } else if (AutismAccountManager.get().contains(var3)) {
                        this.finishOperation(var1, false, "Account already exists.", false);
                     } else {
                        boolean var4 = var3.login();
                        if (!this.isCancelled(var1)) {
                           if (!var4) {
                              this.finishOperation(var1, false, loginFailMessage(var3), false);
                           } else {
                              AutismAccountManager.get().add(var3);
                              this.selectedAccount = var3;
                              this.finishOperation(var1, true, "Logged in as " + var3.displayName() + ".", true);
                           }
                        }
                     }
                  }
               });
            } else {
               this.finishOperation(var1, false, "Login cancelled.", false);
            }
         }
      });
   }

   private void loginAccount(AutismAccount var1) {
      if (!this.isBusy() && var1 != null && !this.isCurrentAccount(var1)) {
         this.selectedAccount = var1;
         int var2 = this.beginOperation(AutismAccountsScreen.Operation.LOGIN);
         this.operationTask = AutismBackgroundTasks.runTracked("Autism-Account-Login", () -> {
            boolean var3 = var1.fetchInfo();
            if (!this.isCancelled(var2)) {
               if (!var3) {
                  if (var1.type == AutismAccountType.Microsoft && isExpiredMicrosoftToken(var1)) {
                     this.reloginMicrosoft(var1, var2);
                  } else {
                     this.finishOperation(var2, false, "Token expired.", false);
                  }
               } else {
                  boolean var4 = var1.login();
                  if (!this.isCancelled(var2)) {
                     if (var4) {
                        AutismAccountManager.get().save();
                        this.finishOperation(var2, true, "Logged in as " + var1.displayName() + ".", false);
                     } else {
                        this.finishOperation(var2, false, loginFailMessage(var1), false);
                     }
                  }
               }
            }
         });
      }
   }

   private static boolean isExpiredMicrosoftToken(AutismAccount var0) {
      if (var0 != null && var0.tokenRefresherAuth) {
         return false;
      }
      String var1 = var0 == null ? null : var0.lastError();
      if (var1 != null && !var1.isBlank()) {
         String var2 = var1.toLowerCase(Locale.ROOT);
         return var2.contains("invalid_grant") || var2.contains("expired");
      } else {
         return false;
      }
   }

   private void reloginMicrosoft(AutismAccount var1, int var2) {
      this.minecraft.execute(() -> {
         if (!this.isCancelled(var2)) {
            AutismMicrosoftLogin.getRefreshToken(var3 -> {
               if (!this.isCancelled(var2)) {
                  if (var3 != null && !var3.isBlank()) {
                     var1.label = var3;
                     var1.tokenRefresherAuth = false;
                     this.operationTask = AutismBackgroundTasks.runTracked("Autism-Microsoft-Relogin", () -> {
                        boolean var3x = var1.fetchInfo();
                        if (!this.isCancelled(var2)) {
                           if (!var3x) {
                              this.finishOperation(var2, false, loginFailMessage(var1), false);
                           } else {
                              boolean var4 = var1.login();
                              if (!this.isCancelled(var2)) {
                                 if (var4) {
                                    AutismAccountManager.get().save();
                                    this.finishOperation(var2, true, "Logged in as " + var1.displayName() + ".", false);
                                 } else {
                                    this.finishOperation(var2, false, loginFailMessage(var1), false);
                                 }
                              }
                           }
                        }
                     });
                  } else {
                     this.finishOperation(var2, false, "Token expired.", false);
                  }
               }
            });
         }
      });
   }

   private static String loginFailMessage(AutismAccount var0) {
      String var1 = var0 == null ? "" : var0.lastError();
      return var1 != null && !var1.isBlank() ? "Login failed: " + var1 : "Login failed.";
   }

   private void loginDefaultAccount() {
      if (!this.isBusy() && !this.isCurrentDefaultAccount()) {
         AutismAccount var1 = this.defaultMinecraftAccount();
         if (var1 != null) {
            this.selectedAccount = var1;
            int var2 = this.beginOperation(AutismAccountsScreen.Operation.LOGIN);
            this.operationTask = AutismBackgroundTasks.runTracked("Autism-Default-Account-Login", () -> {
               boolean var3 = AutismAccountSessionSwitcher.setSession(AutismAccountSessionSwitcher.getOriginalUser());
               if (!this.isCancelled(var2)) {
                  if (var3) {
                     this.finishOperation(var2, true, "Logged in as " + var1.displayName() + ".", false);
                  } else {
                     this.finishOperation(var2, false, "Login failed.", false);
                  }
               }
            });
         }
      }
   }

   private void deleteAccount(AutismAccount var1) {
      if (!this.isBusy() && var1 != null) {
         AutismAccountManager.get().remove(var1);
         this.invalidateAccountSnapshot();
         if (var1.equals(this.selectedAccount)) {
            this.selectedAccount = null;
         }

         this.toast("Deleted " + var1.displayName() + ".", AccountPalette.TEXT_SECONDARY);
         this.rebuildButtons();
      }
   }

   private void checkExpiredAccounts() {
      if (!this.isBusy()) {
         ArrayList<AutismAccount> var1 = new ArrayList<>();

         for (AutismAccount var3 : this.accountSnapshot()) {
            if (var3.type == AutismAccountType.Cracked) {
               var3.checkStatus = AutismAccount.CheckStatus.UNKNOWN;
            } else {
               var1.add(var3);
            }
         }

         if (var1.isEmpty()) {
            this.toast("No accounts to check.", AccountPalette.TEXT_SECONDARY);
         } else {
            for (AutismAccount var6 : var1) {
               if (var6.checkStatus == AutismAccount.CheckStatus.UNKNOWN) {
                  var6.checkStatus = AutismAccount.CheckStatus.CHECKING;
               }
            }

            int var5 = this.beginOperation(AutismAccountsScreen.Operation.CHECK);
            this.operationTask = AutismBackgroundTasks.runTracked("Autism-Account-Check-Coordinator", () -> {
               ExecutorService var3x = Executors.newFixedThreadPool(Math.min(6, Math.max(1, var1.size())), var0 -> {
                  Thread var1xx = new Thread(var0, "Autism-Account-Check");
                  var1xx.setDaemon(true);
                  return var1xx;
               });
               AtomicInteger var4 = new AtomicInteger();
               AtomicInteger var5x = new AtomicInteger();
               AtomicInteger retryLater = new AtomicInteger();

               try {
                  ArrayList<Future<?>> var6x = new ArrayList<>();

                  for (AutismAccount var8 : var1) {
                     var6x.add(var3x.submit(() -> {
                        if (!this.isCancelled(var5)) {
                           boolean var5xx;
                           try {
                              var5xx = var8.fetchInfo();
                           } catch (Throwable var7) {
                              var5xx = false;
                           }

                           if (!this.isCancelled(var5)) {
                              boolean transientFailure = !var5xx &&
                                 (var8.tokenImportFailureKind == TokenRefresher.FailureKind.NETWORK
                                    || var8.tokenImportFailureKind == TokenRefresher.FailureKind.RATE_LIMITED);
                              var8.checkStatus = var5xx ? AutismAccount.CheckStatus.VALID
                                 : transientFailure ? AutismAccount.CheckStatus.UNKNOWN : AutismAccount.CheckStatus.EXPIRED;
                              (var5xx ? var4 : transientFailure ? retryLater : var5x).incrementAndGet();
                           }
                        }
                     }));
                  }

                  for (Future var16 : var6x) {
                     try {
                        var16.get();
                     } catch (Exception var13) {
                     }
                  }
               } finally {
                  var3x.shutdownNow();
               }

               if (!this.isCancelled(var5)) {
                  AutismAccountManager.get().save();
                  this.finishOperation(var5, true, var4.get() + " valid, " + var5x.get() + " expired, "
                     + retryLater.get() + " retry later.", false);
               }
            });
         }
      }
   }

   private int expiredAccountCount() {
      int var1 = 0;

      for (AutismAccount var3 : this.accountSnapshot()) {
         if (var3.checkStatus == AutismAccount.CheckStatus.EXPIRED) {
            var1++;
         }
      }

      return var1;
   }

   private void clearExpiredAccounts() {
      if (!this.isBusy()) {
         if (this.selectedAccount != null && this.selectedAccount.checkStatus == AutismAccount.CheckStatus.EXPIRED) {
            this.selectedAccount = null;
         }

         int var1 = AutismAccountManager.get().removeExpired();
         if (var1 == 0) {
            this.toast("No expired accounts.", AccountPalette.TEXT_SECONDARY);
         } else {
            this.invalidateAccountSnapshot();
            this.toast("Cleared " + var1 + " expired.", AccountPalette.TEXT_SECONDARY);
            this.rebuildButtons();
         }
      }
   }

   private static int nameColor(AutismAccount var0, boolean var1, boolean var2) {
      if (var1) {
         return AccountPalette.GOLDEN_YELLOW;
      }

      if (var2) {
         return AccountPalette.TEXT_SECONDARY;
      }

      if (var0 != null) {
         if (var0.checkStatus == AutismAccount.CheckStatus.EXPIRED) {
            return AccountPalette.SPICE_ORANGE;
         }

         if (var0.checkStatus == AutismAccount.CheckStatus.CHECKING) {
            return AccountPalette.GOLDEN_YELLOW;
         }
      }

      return AccountPalette.TEXT;
   }

   private static String checkStatusLabel(AutismAccount.CheckStatus var0) {
      if (var0 == null) {
         return "";
      }

      return switch (var0) {
         case CHECKING -> "CHECK…";
         case VALID -> "VALID";
         case EXPIRED -> "EXPIRED";
         case UNKNOWN -> "";
      };
   }

   private static int checkStatusColor(AutismAccount.CheckStatus var0) {
      if (var0 == null) {
         return AccountPalette.TEXT_SECONDARY;
      }

      return switch (var0) {
         case CHECKING -> AccountPalette.GOLDEN_YELLOW;
         case VALID -> AccountPalette.SUCCESS_TEXT;
         case EXPIRED -> AccountPalette.SPICE_ORANGE;
         case UNKNOWN -> AccountPalette.TEXT_SECONDARY;
      };
   }

   private static boolean hasShareableSessionToken(AutismAccount var0) {
      return var0 != null && (var0.type == AutismAccountType.Microsoft || var0.type == AutismAccountType.Session || var0.type == AutismAccountType.TheAltening);
   }

   private static String shareableSessionToken(AutismAccount var0) {
      if (!hasShareableSessionToken(var0)) {
         return "";
      } else {
         return var0.type == AutismAccountType.TheAltening ? safeTrim(var0.sessionToken) : safeTrim(var0.token);
      }
   }

   private void copySessionToken(AutismAccount var1) {
      if (hasShareableSessionToken(var1)) {
         String var2 = shareableSessionToken(var1);
         if (var2.isBlank()) {
            this.toast("No session token.", AccountPalette.GOLDEN_YELLOW);
         } else if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
            try {
               this.minecraft.keyboardHandler.setClipboard(var2);
            } catch (Exception var4) {
               this.toast("Failed to copy token.", AccountPalette.SPICE_ORANGE);
               return;
            }

            this.toast("Copied session token.", AccountPalette.SUCCESS_TEXT);
         } else {
            this.toast("Clipboard unavailable.", AccountPalette.SPICE_ORANGE);
         }
      }
   }

   private int beginOperation(AutismAccountsScreen.Operation var1) {
      this.operationId++;
      this.operation = var1;
      this.operationStartedAtMs = System.currentTimeMillis();
      this.clearInputFocus();
      this.updateInputVisibility();
      this.rebuildButtons();
      return this.operationId;
   }

   private void finishOperation(int var1, boolean var2, String var3, boolean var4) {
      this.minecraft.execute(() -> {
         if (var1 == this.operationId) {
            this.operation = AutismAccountsScreen.Operation.NONE;
            this.operationStartedAtMs = 0L;
            this.operationTask = null;
            this.toast(var3, var2 ? AccountPalette.SUCCESS_TEXT : AccountPalette.SPICE_ORANGE);
            if (var2 && var4) {
               this.clearFields();
            }

            this.updateInputVisibility();
            this.clearInputFocus();
            this.invalidateAccountSnapshot();
            this.rebuildButtons();
         }
      });
   }

   private void cancelOperation() {
      if (this.isBusy()) {
         this.operationId++;
         Future var1 = this.operationTask;
         if (var1 != null) {
            var1.cancel(true);
         }

         if (this.operation == AutismAccountsScreen.Operation.MICROSOFT) {
            AutismMicrosoftLogin.stopServer();
         }

         if (this.operation == AutismAccountsScreen.Operation.CHECK) {
            for (AutismAccount var3 : this.accountSnapshot()) {
               if (var3.checkStatus == AutismAccount.CheckStatus.CHECKING) {
                  var3.checkStatus = AutismAccount.CheckStatus.UNKNOWN;
               }
            }
         }

         this.operation = AutismAccountsScreen.Operation.NONE;
         this.operationStartedAtMs = 0L;
         this.operationTask = null;
         this.clearInputFocus();
         this.toast("Cancelled.", AccountPalette.GOLDEN_YELLOW);
         this.updateInputVisibility();
         this.rebuildButtons();
      }
   }

   private boolean isCancelled(int var1) {
      return var1 != this.operationId || Thread.currentThread().isInterrupted();
   }

   private boolean isBusy() {
      return this.operation != AutismAccountsScreen.Operation.NONE;
   }

   private boolean shouldShowCancelOperationButton() {
      return this.isBusy() && this.operationStartedAtMs > 0L && System.currentTimeMillis() - this.operationStartedAtMs >= 5000L;
   }

   private void refreshOperationControls() {
      if (this.cancelOperationButton != null) {
         this.cancelOperationButton.visible = this.shouldShowCancelOperationButton();
      }
   }

   private void clearFields() {
      this.renamingAccount = null;
      if (this.labelField != null) {
         this.labelField.setValue("");
      }

      if (this.tokenField != null) {
         this.tokenField.setValue("");
      }
   }

   private void startRename(AutismAccount var1) {
      if (var1 != null && var1.type == AutismAccountType.Cracked && !this.isBusy()) {
         this.renamingAccount = var1;
         this.type = AutismAccountType.Cracked;
         this.clearInputFocus();
         this.updateInputVisibility();
         this.rebuildButtons();
         if (this.labelField != null) {
            this.labelField.setValue(var1.displayName());
            this.labelField.setFocused(true);
            this.setFocused(this.labelField);
            this.labelField.moveCursorToEnd(false);
         }
      }
   }

   private void updateInputVisibility() {
      if (this.labelField != null && this.tokenField != null) {
         boolean var1 = !this.narrowLayout();
         boolean var2 = var1 && this.type == AutismAccountType.Cracked;
         boolean var3 = var1 && (this.type == AutismAccountType.Session || this.type == AutismAccountType.TheAltening);
         this.labelField.setVisible(var2);
         this.tokenField.setVisible(var3);
         this.labelField.active = var2 && !this.isBusy();
         this.tokenField.active = var3 && !this.isBusy();
         if (!var2 || this.isBusy()) {
            this.labelField.setFocused(false);
         }

         if (!var3 || this.isBusy()) {
            this.tokenField.setFocused(false);
         }

         if (this.searchField != null) {
            this.searchField.visible = var1;
            this.searchField.active = var1;
         }

         this.labelField.setHint(Component.literal("Username"));
         this.tokenField.setHint(Component.literal(this.type == AutismAccountType.TheAltening ? "TheAltening token" : "Minecraft access token"));
      }
   }

   public void extractRenderState(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      int var5 = AutismUiScale.toVirtualInt(var2);
      int var6 = AutismUiScale.toVirtualInt(var3);
      this.refreshOperationControls();
      AutismUiScale.pushOverlayScale(var1);

      try {
         UiRenderer.rect(var1, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), AccountPalette.BACKGROUND);
         int var7 = this.panelX();
         int var8 = var7 + 10;
         int var9 = this.previewX();
         if (!this.narrowLayout()) {
            this.drawPanel(var1, var8, 20, 292, 178, AccountPalette.SURFACE);
            this.drawPanel(var1, var9, 20, 200, 178, AccountPalette.SURFACE_RAISED);
         }

         int var10 = this.listPanelHeight();
         this.drawPanel(var1, this.listX(), this.listTop(), this.listWidth(), var10, AccountPalette.SURFACE);
         if (!this.narrowLayout()) {
            this.drawText(var1, "Accounts", var8 + 12, 31, AccountPalette.TEXT, false);
            this.renderPreview(var1, var9, var5, var6, var4);
         }

         List var11 = this.displayAccounts();
         if (this.compactListLayout()) {
            this.drawText(var1, "Window too small.", this.listX() + 12, this.listTop() + 12, AccountPalette.TEXT_SECONDARY, false, Math.max(0, this.listWidth() - 24));
         }

         int var12 = this.savedListScrollOffset / 24;
         String var13 = var11.size() <= this.savedViewportRows()
            ? "Accounts"
            : "Accounts  showing " + (var12 + 1) + "-" + Math.min(var11.size(), var12 + this.savedViewportRows()) + " / " + var11.size();
         int var14 = this.compactListLayout() ? this.listWidth() - 24 : Math.max(20, this.listWidth() - 24 - 328);
         this.drawText(var1, var13, this.listX() + 12, this.listTop() + 10, AccountPalette.TEXT, false, var14);
         if (!this.compactListLayout()) {
            for (AutismAccountsScreen.AccountRow var16 : this.accountRows) {
               this.renderRow(var1, var16, var5, var6);
            }
         }

         if (this.accountSnapshot().isEmpty()) {
            this.drawText(var1, "No extra accounts saved yet.", this.listX() + 12, this.listRowTop() + 24 + 8, AccountPalette.TEXT_SECONDARY, false, this.listWidth() - 24);
         } else if (this.filteredAccounts().isEmpty()) {
            this.drawText(
               var1, "No accounts match the current search or filters.", this.listX() + 12, this.listRowTop() + 24 + 8, AccountPalette.TEXT_SECONDARY, false, this.listWidth() - 24
            );
         }

         for (CompactOverlayButton var22 : this.buttons) {
            CompactOverlayButton.renderStyled(var1, this.font, var22, var5, var6);
         }

         if (!this.compactListLayout()) {
            CompactScrollbar.Metrics var21 = this.accountScrollbarMetrics(var11.size());
            CompactScrollbar.draw(var1, var21, var21.contains(var5, var6), this.accountScrollbarDragging);
         }

         super.extractRenderState(var1, var5, var6, var4);
         if (this.activePopup != 0) {
            this.renderPopup(var1, var5, var6, var4);
         }
      } finally {
         AutismUiScale.popOverlayScale(var1);
      }
   }

   public void removed() {
      super.removed();
   }

   private void renderRow(GuiGraphicsExtractor var1, AutismAccountsScreen.AccountRow var2, int var3, int var4) {
      AutismAccount var5 = var2.account;
      boolean var6 = var2.defaultAccount ? this.isCurrentDefaultAccount() : this.isCurrentAccount(var5);
      boolean var7 = var5.equals(this.selectedAccount);
      int var8 = this.rowX();
      int var9 = var2.y;
      int var10 = this.rowWidth();
      int var11 = var6 ? successColor(AccountPalette.CURRENT_ROW) : (var2.defaultAccount ? AccountPalette.SURFACE_RAISED : (var7 ? outlineColor(AccountPalette.SELECTED_ROW) : AccountPalette.SURFACE));
      UiRenderer.rect(var1, UiBounds.of(var8, var9, var10, this.rowVisualHeight()), var11);
      if (var6) {
         UiRenderer.rect(var1, UiBounds.of(var8, var9, 2, this.rowVisualHeight()), successColor(AccountPalette.GOLDEN_YELLOW));
      } else if (var7) {
         UiRenderer.rect(var1, UiBounds.of(var8, var9, 2, this.rowVisualHeight()), outlineColor(AccountPalette.ELECTRIC_BLUE));
      }

      PlayerSkin var12 = !var2.loadSkin && !var6 && !var7 ? this.fallbackSkin(var5) : this.skinLookup(var5).skin();
      this.drawHead(var1, var12, var8 + 7, var9 + 3, 16);
      String var13 = var5.displayName().isBlank() ? "(unknown)" : var5.displayName();
      String var14 = var2.defaultAccount ? "Default Minecraft" : var5.type.name();
      int var15 = var8 + 31;
      if (this.narrowLayout()) {
         int var22 = Math.max(var15 + 1, var2.loginButton.getX() - 8);
         this.drawText(var1, var13, var15, var9 + 7, nameColor(var5, var6, var2.defaultAccount), false, Math.max(1, var22 - var15));
         CompactOverlayButton.renderStyled(var1, this.font, var2.loginButton, var3, var4);
         if (var2.renameButton != null) {
            CompactOverlayButton.renderStyled(var1, this.font, var2.renameButton, var3, var4);
         }

         if (var2.deleteButton != null) {
            CompactOverlayButton.renderStyled(var1, this.font, var2.deleteButton, var3, var4);
         }

         this.renderShareButton(var1, var2.shareButton, var3, var4);
      } else {
         int var16 = var8 + 228;
         int var17 = var2.defaultAccount ? var2.loginButton.getX() - 70 : var2.loginButton.getX() - 58;
         int var18 = var2.shareButton == null ? var2.loginButton.getX() : var2.shareButton.getX();
         int var19 = Math.max(20, var16 - var15 - 12);
         int var20 = Math.max(20, var17 - var16 - 10);
         this.drawText(var1, var13, var15, var9 + 7, nameColor(var5, var6, var2.defaultAccount), false, var19);
         this.drawText(var1, var14, var16, var9 + 7, var2.defaultAccount ? AccountPalette.TEXT_SECONDARY : AccountPalette.TEXT_SECONDARY, false, var20);
         if (var6) {
            this.drawText(var1, "CURRENT", var17, var9 + 8, AccountPalette.GOLDEN_YELLOW, false, Math.max(1, var18 - var17 - 4));
         } else if (!var2.defaultAccount) {
            String var21 = checkStatusLabel(var5.checkStatus);
            if (!var21.isEmpty()) {
               this.drawText(var1, var21, var17, var9 + 8, checkStatusColor(var5.checkStatus), false, Math.max(1, var18 - var17 - 4));
            }
         }

         CompactOverlayButton.renderStyled(var1, this.font, var2.loginButton, var3, var4);
         if (var2.renameButton != null) {
            CompactOverlayButton.renderStyled(var1, this.font, var2.renameButton, var3, var4);
         }

         if (var2.deleteButton != null) {
            CompactOverlayButton.renderStyled(var1, this.font, var2.deleteButton, var3, var4);
         }

         this.renderShareButton(var1, var2.shareButton, var3, var4);
      }
   }

   private void renderShareButton(GuiGraphicsExtractor var1, CompactOverlayButton var2, int var3, int var4) {
      if (var2 != null) {
         CompactOverlayButton.renderStyled(var1, this.font, var2, var3, var4);
         UiBounds var5 = UiBounds.of(var2.getX(), var2.getY(), var2.getWidth(), var2.getHeight());
         this.drawCopyGlyph(var1, var5, var2.active ? AccountPalette.TEXT : AccountPalette.TEXT_DISABLED);
      }
   }

   private void drawCopyGlyph(GuiGraphicsExtractor var1, UiBounds var2, int var3) {
      byte var4 = 7;
      byte var5 = 7;
      byte var6 = 3;
      int var7 = var2.x() + (var2.width() - (var4 + var6)) / 2;
      int var8 = var2.y() + (var2.height() - (var5 + var6)) / 2;
      UiRenderer.outline(var1, UiBounds.of(var7 + var6, var8, var4, var5), var3);
      UiBounds var9 = UiBounds.of(var7, var8 + var6, var4, var5);
      UiRenderer.rect(var1, var9, AccountPalette.SURFACE_RAISED);
      UiRenderer.outline(var1, var9, var3);
   }

   private void renderPreview(GuiGraphicsExtractor var1, int var2, int var3, int var4, float var5) {
      AutismAccount var6 = this.previewAccount();
      this.drawText(var1, "Skin preview", var2 + 12, 31, AccountPalette.TEXT, false);
      if (var6 == null) {
         this.drawText(var1, "No account selected", var2 + 16, 78, AccountPalette.TEXT_SECONDARY, false, 168);
         this.drawText(var1, "Click a row to preview it.", var2 + 16, 94, AccountPalette.TEXT_SECONDARY, false, 168);
      } else {
         AutismAccountsScreen.SkinLookup var7 = this.skinLookup(var6);
         PlayerSkin var8 = var7.skin();
         int var9 = var2 + 100;
         int var10 = var9 - 111;
         byte var11 = 54;
         int var12 = var9 + 111;
         short var13 = 190;
         this.render3dSkin(var1, var8, var10, var11, var12, var13);
         if (var7.loading()) {
            this.drawText(var1, "Loading skin...", var2 + 14, 178, AccountPalette.GOLDEN_YELLOW, false, 172);
         }

         String var14 = var6.displayName().isBlank() ? "(unknown)" : var6.displayName();
         boolean var15 = this.isDefaultAccount(var6);
         boolean var16 = var15 ? this.isCurrentDefaultAccount() : this.isCurrentAccount(var6);
         this.drawText(var1, var14, var2 + 12, 164, var16 ? AccountPalette.GOLDEN_YELLOW : AccountPalette.TEXT, false, 176);
         this.drawText(var1, var15 ? "Default Minecraft" : var6.type.name(), var2 + 12, 150, var15 ? AccountPalette.TEXT_SECONDARY : AccountPalette.TEXT_SECONDARY, false, 176);
         if (var16) {
            this.drawText(var1, "CURRENT", var2 + 200 - 66, 31, AccountPalette.GOLDEN_YELLOW, false, 54);
         }
      }
   }

   private void render3dSkin(GuiGraphicsExtractor var1, PlayerSkin var2, int var3, int var4, int var5, int var6) {
      if (this.widePlayerModel != null && this.slimPlayerModel != null) {
         Model.Simple var7 = var2.model().name().equalsIgnoreCase("slim") ? this.slimPlayerModel : this.widePlayerModel;
         float var8 = AutismUiScale.getOverlayDrawScale();
         int var9 = Math.round(var3 * var8);
         int var10 = Math.round(var4 * var8);
         int var11 = Math.round(var5 * var8);
         int var12 = Math.round(var6 * var8);
         float var13 = 0.97F * Math.max(1, var12 - var10) / 2.125F;
         var1.skin(var7, var2.body().texturePath(), var13, this.previewRotationX, this.currentPreviewRotationY(), -1.0625F, var9, var10, var11, var12);
      }
   }

   private float currentPreviewRotationY() {
      return this.previewDragging ? this.previewRotationY : this.previewRotationY + (float)((Blaze3D.getTime() - this.previewAutoRotationStartTime) * 18.0);
   }

   private void drawHead(GuiGraphicsExtractor var1, PlayerSkin var2, int var3, int var4, int var5) {
      Identifier var6 = var2.body().texturePath();
      var1.blit(RenderPipelines.GUI_TEXTURED, var6, var3, var4, 8.0F, 8.0F, var5, var5, 8, 8, 64, 64);
      var1.blit(RenderPipelines.GUI_TEXTURED, var6, var3, var4, 40.0F, 8.0F, var5, var5, 8, 8, 64, 64);
   }

   public boolean mouseClicked(MouseButtonEvent var1, boolean var2) {
      MouseButtonEvent var3 = virtualEvent(var1);
      if (var3.button() != InputConstants.MOUSE_BUTTON_LEFT) {
         return super.mouseClicked(var3, var2);
      }

      if (this.activePopup != 0) {
         this.handlePopupMouse(var3, var2);
         return true;
      }

      for (CompactOverlayButton button : this.buttons) {
         if (CompactOverlayButton.fireIfHit(button, var3.x(), var3.y(), var3.button())) {
            return true;
         }
      }

      if (this.compactListLayout()) {
         return super.mouseClicked(var3, var2);
      }

      CompactScrollbar.Metrics var4 = this.accountScrollbarMetrics(this.displayAccounts().size());
      if (var4.hasScroll() && var4.contains(var3.x(), var3.y())) {
         this.accountScrollbarDragging = true;
         this.accountScrollbarGrabOffset = var4.overThumb(var3.x(), var3.y()) ? Math.max(0, (int)Math.round(var3.y()) - var4.thumbY()) : var4.thumbHeight() / 2;
         this.savedListScrollOffset = quantizeScrollOffset(
            CompactScrollbar.scrollFromThumb(var4, var3.y(), this.accountScrollbarGrabOffset), 24, var4.maxScroll()
         );
         this.savedListScroll.jumpTo(this.savedListScrollOffset, var4.maxScroll());
         this.rebuildButtons();
         this.clearInputFocus();
         return true;
      }

      if (this.isInPreview(var3.x(), var3.y())) {
         this.previewRotationY = this.currentPreviewRotationY();
         this.previewAutoRotationStartTime = Blaze3D.getTime();
         this.previewDragging = true;
         this.lastPreviewMouseX = var3.x();
         this.lastPreviewMouseY = var3.y();
         this.clearInputFocus();
         return true;
      }

      for (AutismAccountsScreen.AccountRow var8 : this.accountRows) {
         if (var8.renameButton != null && CompactOverlayButton.fireIfHit(var8.renameButton, var3.x(), var3.y(), var3.button())) {
            return true;
         }

         if (CompactOverlayButton.fireIfHit(var8.deleteButton, var3.x(), var3.y(), var3.button())) {
            return true;
         }

         if (CompactOverlayButton.fireIfHit(var8.shareButton, var3.x(), var3.y(), var3.button())) {
            return true;
         }

         if (CompactOverlayButton.fireIfHit(var8.loginButton, var3.x(), var3.y(), var3.button())) {
            return true;
         }

         if (var3.x() >= this.rowX() && var3.x() < this.rowRight() && var3.y() >= var8.y && var3.y() < var8.y + 24 - 3) {
            this.selectedAccount = var8.account();
            return true;
         }
      }

      this.clearInputFocus();
      return super.mouseClicked(var3, var2);
   }

   private void toggleFilter(AutismAccountType var1) {
      this.categoryFilter = this.categoryFilter == var1 ? null : var1;
      this.savedListScrollOffset = 0;
      this.savedListScroll.jumpTo(0, 0);
      this.rebuildButtons();
   }

   private void openGenerator() {
      this.activePopup = 1;
      this.generatorResult = "";
      this.clearInputFocus();
      this.rebuildPopupButtons();
   }

   private void openClearConfirm() {
      this.activePopup = 2;
      this.clearInputFocus();
      this.rebuildPopupButtons();
   }

   private void closePopup() {
      this.syncGeneratorPasswordToConfig();
      this.activePopup = 0;
      this.generatorResult = "";
      this.popupDropdowns.clear();
      if (this.generatorCountField != null) {
         this.generatorCountField.setFocused(false);
      }

      if (this.generatorPasswordField != null) {
         this.generatorPasswordField.setFocused(false);
      }

      if (this.generatorListField != null) {
         this.generatorListField.setFocused(false);
      }
   }

   private void syncGeneratorPasswordToConfig() {
      if (this.generatorPasswordField != null) {
         AutismConfig var1 = AutismConfig.getGlobal();
         String var2 = this.generatorPasswordField.getValue();
         if (!Objects.equals(var1.accountGenSharedPassword, var2)) {
            var1.accountGenSharedPassword = var2;
            var1.save();
         }
      }
   }

   private int popupX() {
      return Math.max(4, (this.screenWidth() - 280) / 2);
   }

   private int popupY() {
      return Math.max(4, (this.screenHeight() - 210) / 2);
   }

   private void rebuildPopupButtons() {
      this.popupButtons.clear();
      this.popupDropdowns.clear();
      int var1 = this.popupX();
      int var2 = this.popupY();
      if (this.activePopup == 1) {
         CompactOverlayButton var3 = CompactOverlayButton.create(var1 + 12, var2 + 28, 80, 16, Component.literal("Random"), var1x -> {
            this.generatorMode = 0;
            this.generatorResult = "";
            this.rebuildPopupButtons();
         });
         var3.setVariant(this.generatorMode == 0 ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY);
         this.popupButtons.add(var3);
         CompactOverlayButton var4 = CompactOverlayButton.create(var1 + 96, var2 + 28, 80, 16, Component.literal("From List"), var1x -> {
            this.generatorMode = 1;
            this.generatorResult = "";
            this.rebuildPopupButtons();
         });
         var4.setVariant(this.generatorMode == 1 ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY);
         this.popupButtons.add(var4);
         if (this.generatorMode == 0) {
            AutismConfig var5 = AutismConfig.getGlobal();
            this.popupButtons.add(CompactOverlayButton.create(var1 + 12, var2 + 72, 256, 16, Component.literal("Set Password"), var1x -> {
               AutismConfig var2x = AutismConfig.getGlobal();
               var2x.accountGenSetPassword = !var2x.accountGenSetPassword;
               var2x.save();
               this.rebuildPopupButtons();
            }).setToggleState(var5.accountGenSetPassword));
            if (var5.accountGenSetPassword) {
               boolean var6 = "Set For All".equalsIgnoreCase(var5.accountGenPasswordMode);
               this.popupDropdowns.add(new CompactDropdown(var1 + 12, var2 + 94, 256, 16, List.of("Generate", "Set For All"), var6 ? 1 : 0, var1x -> {
                  AutismConfig var2x = AutismConfig.getGlobal();
                  var2x.accountGenPasswordMode = var1x == 1 ? "Set For All" : "Generate";
                  var2x.save();
                  this.rebuildPopupButtons();
               }));
            }

            CompactOverlayButton var9 = CompactOverlayButton.create(
                  var1 + 12, var2 + 150, 90, 18, Component.literal(this.generating ? "Working..." : "Generate"), var1x -> this.runRandomGeneration()
               )
               .setVariant(CompactOverlayButton.Variant.PRIMARY);
            var9.active = !this.generating;
            this.popupButtons.add(var9);
         } else {
            this.popupButtons
               .add(
                  CompactOverlayButton.create(var1 + 12, var2 + 160, 90, 16, Component.literal("Paste"), var1x -> this.pasteListFromClipboard())
                     .setVariant(CompactOverlayButton.Variant.SECONDARY)
               );
            CompactOverlayButton var8 = CompactOverlayButton.create(
                  var1 + 106, var2 + 160, 90, 16, Component.literal(this.generating ? "Working..." : "Add Accounts"), var1x -> this.runListGeneration()
               )
               .setVariant(CompactOverlayButton.Variant.PRIMARY);
            var8.active = !this.generating;
            this.popupButtons.add(var8);
         }

         this.popupButtons
            .add(
               CompactOverlayButton.create(var1 + 198, var2 + 184, 70, 16, Component.literal("Close"), var1x -> this.closePopup())
                  .setVariant(CompactOverlayButton.Variant.SECONDARY)
            );
      } else if (this.activePopup == 2) {
         CompactOverlayButton var7 = CompactOverlayButton.create(var1 + 12, var2 + 74, 90, 18, Component.literal("Clear"), var1x -> this.confirmClear())
            .setVariant(CompactOverlayButton.Variant.DANGER);
         var7.active = this.clearTargetCount() > 0;
         this.popupButtons.add(var7);
         this.popupButtons
            .add(
               CompactOverlayButton.create(var1 + 198, var2 + 74, 70, 18, Component.literal("Cancel"), var1x -> this.closePopup())
                  .setVariant(CompactOverlayButton.Variant.SECONDARY)
            );
      }
   }

   private void runRandomGeneration() {
      if (!this.generating) {
         this.syncGeneratorPasswordToConfig();
         int var1 = 10;

         try {
            var1 = Integer.parseInt(safeTrim(this.generatorCountField.getValue()));
         } catch (NumberFormatException var3) {
         }

         int var2 = var1;
         this.beginGeneration();
         this.runGenerationAsync(
            () -> {
               List var1x = AutismAccountGenerator.randomNames(var2);
               int var2x = AutismAccountGenerator.addGeneratedAccounts(var1x);
               return var1x.isEmpty()
                  ? "Nothing to add."
                  : (var2x >= var1x.size() ? var2x + " accounts added." : var2x + " added, " + (var1x.size() - var2x) + " already existed.");
            }
         );
      }
   }

   private void runListGeneration() {
      if (!this.generating) {
         this.syncGeneratorPasswordToConfig();
         String var1 = this.generatorListField == null ? "" : this.generatorListField.getValue();
         if (var1 != null && !var1.isBlank()) {
            this.beginGeneration();
            this.runGenerationAsync(() -> {
               AutismAccountGenerator.ParseResult var1x = AutismAccountGenerator.parseNameList(var1);
               int var2 = AutismAccountGenerator.addGeneratedAccounts(var1x.names());
               StringBuilder var3 = new StringBuilder("Added ").append(var2).append(" accounts");
               int var4 = var1x.names().size() - var2;
               if (var4 > 0) {
                  var3.append(", ").append(var4).append(" existed");
               }

               if (var1x.skipped() > 0) {
                  var3.append(", ").append(var1x.skipped()).append(" invalid");
               }

               if (var1x.duplicates() > 0) {
                  var3.append(", ").append(var1x.duplicates()).append(" dupes");
               }

               return var3.toString();
            });
         } else {
            this.generatorResult = "Paste a list first.";
         }
      }
   }

   private void beginGeneration() {
      this.generating = true;
      this.generatorResult = "Generating...";
      this.rebuildPopupButtons();
   }

   private void runGenerationAsync(Supplier<String> var1) {
      Thread var2 = new Thread(() -> {
         String var2x;
         try {
            var2x = (String)var1.get();
         } catch (Throwable var5) {
            var2x = "Generation failed.";
         }

         String var3 = var2x;
         Runnable var4 = () -> {
            this.generating = false;
            this.generatorResult = var3;
            this.invalidateAccountSnapshot();
            this.rebuildButtons();
            this.rebuildPopupButtons();
         };
         if (this.minecraft != null) {
            this.minecraft.execute(var4);
         } else {
            var4.run();
         }
      }, "autism-account-gen");
      var2.setDaemon(true);
      var2.start();
   }

   private void pasteListFromClipboard() {
      if (this.minecraft != null && this.generatorListField != null) {
         this.generatorListField.setValue(this.minecraft.keyboardHandler.getClipboard());
      }
   }

   private void confirmClear() {
      int var1 = 0;

      for (AutismAccount var3 : new ArrayList<>(AutismAccountManager.get().all())) {
         if (this.categoryFilter == null || var3.type == this.categoryFilter) {
            AutismAccountManager.get().remove(var3);
            var1++;
         }
      }

      this.closePopup();
      this.toast("Cleared " + var1 + " accounts.", AccountPalette.TEXT_SECONDARY);
      this.invalidateAccountSnapshot();
      this.rebuildButtons();
   }

   private int clearTargetCount() {
      int var1 = 0;

      for (AutismAccount var3 : AutismAccountManager.get().all()) {
         if (this.categoryFilter == null || var3.type == this.categoryFilter) {
            var1++;
         }
      }

      return var1;
   }

   private String clearTargetNames() {
      if (this.categoryFilter != null) {
         return this.categoryFilter.name();
      }

      StringBuilder var1 = new StringBuilder();

      for (AutismAccountType var5 : AutismAccountType.values()) {
         if (var1.length() > 0) {
            var1.append(", ");
         }

         var1.append(var5.name());
      }

      return var1.toString();
   }

   private void renderPopup(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      int var5 = var2;
      int var6 = var3;
      if (CompactDropdown.isMenuOpen(this.popupDropdowns)) {
         var2 = Integer.MIN_VALUE;
         var3 = Integer.MIN_VALUE;
      }

      UiRenderer.rect(var1, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), AccountPalette.MODAL_SCRIM);
      int var7 = this.popupX();
      int var8 = this.popupY();
      if (this.activePopup == 2) {
         this.drawPanel(var1, var7, var8, 280, 100, AccountPalette.SURFACE);
         this.drawText(var1, "Clear Accounts?", var7 + 12, var8 + 10, AccountPalette.TEXT, false);
         this.drawText(var1, this.clearTargetCount() + " accounts will be deleted in:", var7 + 12, var8 + 28, AccountPalette.TEXT_SECONDARY, false, 256);
         this.drawText(var1, this.clearTargetNames(), var7 + 12, var8 + 42, AccountPalette.GOLDEN_YELLOW, false, 256);
         this.drawText(var1, "This cannot be undone.", var7 + 12, var8 + 56, AccountPalette.TEXT_SECONDARY, false, 256);
      } else {
         this.drawPanel(var1, var7, var8, 280, 210, AccountPalette.SURFACE);
         this.drawText(var1, "Generate Accounts", var7 + 12, var8 + 10, AccountPalette.TEXT, false);
         if (this.generatorMode == 0) {
            this.drawText(var1, "Count:", var7 + 12, var8 + 54, AccountPalette.TEXT_SECONDARY, false);
            this.generatorCountField.setX(var7 + 50);
            this.generatorCountField.setY(var8 + 50);
            this.generatorCountField.extractRenderState(var1, var2, var3, var4);
            if (this.passwordFieldVisible()) {
               this.generatorPasswordField.setX(var7 + 12);
               this.generatorPasswordField.setY(var8 + 116);
               this.generatorPasswordField.extractRenderState(var1, var2, var3, var4);
            }

            if (!this.generatorResult.isBlank()) {
               this.drawText(var1, this.generatorResult, var7 + 12, var8 + 172, AccountPalette.SUCCESS_TEXT, false, 180);
            }
         } else {
            this.generatorListField.setX(var7 + 12);
            this.generatorListField.setY(var8 + 48);
            this.generatorListField.setWidth(256);
            this.generatorListField.setHeight(108);
            this.generatorListField.extractRenderState(var1, var2, var3, var4);
            if (!this.generatorResult.isBlank()) {
               this.drawText(var1, this.generatorResult, var7 + 12, var8 + 182, AccountPalette.SUCCESS_TEXT, false, 180);
            }
         }
      }

      for (CompactOverlayButton var10 : this.popupButtons) {
         CompactOverlayButton.renderStyled(var1, this.font, var10, var2, var3);
      }

      CompactDropdown.renderButtons(var1, this.font, this.popupDropdowns, var5, var6);
      CompactDropdown.renderOpenMenu(var1, this.font, this.popupDropdowns, var5, var6);
   }

   private boolean passwordFieldVisible() {
      AutismConfig var1 = AutismConfig.getGlobal();
      return this.activePopup == 1 && this.generatorMode == 0 && var1.accountGenSetPassword && "Set For All".equalsIgnoreCase(var1.accountGenPasswordMode);
   }

   private void handlePopupMouse(MouseButtonEvent var1, boolean var2) {
      if (this.activePopup != 1 || !CompactDropdown.mouseClicked(this.popupDropdowns, var1.x(), var1.y(), var1.button())) {
         for (CompactOverlayButton var4 : this.popupButtons) {
            if (CompactOverlayButton.fireIfHit(var4, var1.x(), var1.y(), var1.button())) {
               return;
            }
         }

         if (this.activePopup == 1) {
            if (this.generatorMode == 0) {
               if (this.passwordFieldVisible() && this.generatorPasswordField != null) {
                  if (this.generatorPasswordField.mouseClicked(var1, var2)) {
                     this.generatorPasswordField.setFocused(true);
                     if (this.generatorCountField != null) {
                        this.generatorCountField.setFocused(false);
                     }

                     return;
                  }

                  this.generatorPasswordField.setFocused(false);
               }

               if (this.generatorCountField != null && this.generatorCountField.mouseClicked(var1, var2)) {
                  this.generatorCountField.setFocused(true);
                  if (this.generatorPasswordField != null) {
                     this.generatorPasswordField.setFocused(false);
                  }

                  return;
               }

               if (this.generatorCountField != null) {
                  this.generatorCountField.setFocused(false);
               }
            } else if (this.generatorMode == 1 && this.generatorListField != null) {
               if (this.generatorListField.mouseClicked(var1, var2)) {
                  this.generatorListField.setFocused(true);
                  return;
               }

               this.generatorListField.setFocused(false);
            }
         }
      }
   }

   // SDL asks the screen whether to enable text input. Popup fields are drawn
   // separately from the normal widget tree, so include their focus explicitly.
   public boolean isInputCaptured() {
      if (this.activePopup == 1) {
         return this.generatorMode == 0
            ? (this.generatorCountField != null && this.generatorCountField.isFocused())
               || (this.passwordFieldVisible() && this.generatorPasswordField != null && this.generatorPasswordField.isFocused())
            : this.generatorListField != null && this.generatorListField.isFocused();
      }
      return (this.labelField != null && this.labelField.isFocused())
         || (this.tokenField != null && this.tokenField.isFocused())
         || (this.searchField != null && this.searchField.isFocused());
   }

   public boolean keyPressed(KeyEvent var1) {
      if (this.activePopup != 0) {
         if (var1.key() == InputConstants.KEY_ESCAPE) {
            this.closePopup();
            return true;
         }

         if (this.activePopup == 1) {
            if (this.generatorMode == 0 && this.passwordFieldVisible() && this.generatorPasswordField != null && this.generatorPasswordField.keyPressed(var1)) {
               return true;
            }

            if (this.generatorMode == 0 && this.generatorCountField != null && this.generatorCountField.keyPressed(var1)) {
               return true;
            }

            if (this.generatorMode == 1 && this.generatorListField != null && this.generatorListField.keyPressed(var1)) {
               return true;
            }
         }

         return true;
      } else {
         return super.keyPressed(var1);
      }
   }

   public boolean charTyped(CharacterEvent var1) {
      if (this.activePopup != 0) {
         if (this.activePopup == 1) {
            if (this.generatorMode == 0 && this.passwordFieldVisible() && this.generatorPasswordField != null && this.generatorPasswordField.charTyped(var1)) {
               return true;
            }

            if (this.generatorMode == 0 && this.generatorCountField != null && this.generatorCountField.charTyped(var1)) {
               return true;
            }

            if (this.generatorMode == 1 && this.generatorListField != null && this.generatorListField.charTyped(var1)) {
               return true;
            }
         }

         return true;
      } else {
         return super.charTyped(var1);
      }
   }

   public boolean mouseReleased(MouseButtonEvent var1) {
      if (this.accountScrollbarDragging) {
         this.accountScrollbarDragging = false;
         return true;
      } else if (this.previewDragging) {
         this.previewAutoRotationStartTime = Blaze3D.getTime();
         this.previewDragging = false;
         return true;
      } else {
         return super.mouseReleased(virtualEvent(var1));
      }
   }

   public boolean mouseDragged(MouseButtonEvent var1, double var2, double var4) {
      MouseButtonEvent var6 = virtualEvent(var1);
      if (this.accountScrollbarDragging) {
         CompactScrollbar.Metrics var7 = this.accountScrollbarMetrics(this.displayAccounts().size());
         this.savedListScrollOffset = quantizeScrollOffset(
            CompactScrollbar.scrollFromThumb(var7, var6.y(), this.accountScrollbarGrabOffset), 24, var7.maxScroll()
         );
         this.savedListScroll.jumpTo(this.savedListScrollOffset, var7.maxScroll());
         this.rebuildButtons();
         return true;
      } else if (this.previewDragging) {
         this.previewRotationX = Math.max(-50.0F, Math.min(50.0F, this.previewRotationX - (float)AutismUiScale.toVirtual(var4) * 2.5F));
         this.previewRotationY = this.previewRotationY + (float)AutismUiScale.toVirtual(var2) * 2.5F;
         this.lastPreviewMouseX = var6.x();
         this.lastPreviewMouseY = var6.y();
         return true;
      } else {
         return super.mouseDragged(var6, AutismUiScale.toVirtual(var2), AutismUiScale.toVirtual(var4));
      }
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      var1 = AutismUiScale.toVirtual(var1);
      var3 = AutismUiScale.toVirtual(var3);
      if (this.activePopup != 0) {
         CompactDropdown.mouseScrolled(this.popupDropdowns, var1, var3, var7);
         return true;
      }

      if (this.compactListLayout()) {
         return super.mouseScrolled(var1, var3, var5, var7);
      }

      if (!(var1 < this.listX())
         && !(var1 >= this.listX() + this.listWidth())
         && !(var3 < this.listTop())
         && !(var3 >= this.listTop() + this.listPanelHeight())) {
         int var9 = this.savedMaxScroll(this.displayAccounts().size());
         if (var9 <= 0) {
            return true;
         }

         int var10 = this.savedListScrollOffset - (int)Math.signum(var7) * 24;
         this.savedListScrollOffset = quantizeScrollOffset(var10, 24, var9);
         this.savedListScroll.setTarget(this.savedListScrollOffset, var9);
         this.rebuildButtons();
         return true;
      } else {
         return super.mouseScrolled(var1, var3, var5, var7);
      }
   }

   public void onClose() {
      this.cancelOperation();
      this.accountScrollbarDragging = false;
      this.pruneSkinLookups();
      this.minecraft.gui.setScreen(this.parent);
   }

   private AutismAccountsScreen.SkinLookup skinLookup(AutismAccount var1) {
      String var2 = this.skinKey(var1);
      synchronized (this.skinLookups) {
         AutismAccountsScreen.SkinLookup var4 = this.skinLookups.computeIfAbsent(var2, var2x -> this.createSkinLookup(var1));
         this.pruneSkinLookups();
         return var4;
      }
   }

   private AutismAccountsScreen.SkinLookup createSkinLookup(AutismAccount var1) {
      UUID var2 = this.accountUuid(var1);
      String var3 = var1 != null && !var1.displayName().isBlank() ? var1.displayName() : "Autism";
      if (var1 != null && var1.type == AutismAccountType.Cracked && !var3.isBlank() && this.minecraft != null) {
         UUID var15 = UUIDUtil.createOfflinePlayerUUID(var3);
         PlayerSkin var19 = DefaultPlayerSkin.get(var15);

         try {
            CompletableFuture<Optional<PlayerSkin>> var20 = CompletableFuture.<Optional<GameProfile>>supplyAsync(() -> this.resolveCrackedSkinProfile(var3), Util.nonCriticalIoPool())
               .thenCompose(var1x -> var1x.map(this.minecraft.getSkinManager()::get).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
            return new AutismAccountsScreen.SkinLookup(() -> {
               try {
                  return var20.getNow(Optional.empty()).orElse(var19);
               } catch (Exception var3x) {
                  return var19;
               }
            }, var20, var19);
         } catch (Exception var10) {
            return new AutismAccountsScreen.SkinLookup(() -> var19, CompletableFuture.completedFuture(Optional.empty()), var19);
         }
      } else if (var1 != null && !var3.isBlank() && this.minecraft != null && this.isDefaultAccount(var1)) {
         UUID var14 = UUIDUtil.createOfflinePlayerUUID(var3);
         PlayerSkin var18 = DefaultPlayerSkin.get(var2 == null ? var14 : var2);
         ResolvableProfile var6 = var2 == null ? ResolvableProfile.createUnresolved(var3) : ResolvableProfile.createUnresolved(var2);

         try {
            RenderInfo var7 = this.minecraft.playerSkinRenderCache().getOrDefault(var6);
            Supplier var8 = this.minecraft.playerSkinRenderCache().createLookup(var6);
            CompletableFuture var9 = this.minecraft.playerSkinRenderCache().lookup(var6);
            return new AutismAccountsScreen.SkinLookup(() -> {
               try {
                  RenderInfo var2x = (RenderInfo)var8.get();
                  return var2x == null ? var18 : var2x.playerSkin();
               } catch (Exception var3x) {
                  return var18;
               }
            }, var9, var7.playerSkin());
         } catch (Exception var11) {
            return new AutismAccountsScreen.SkinLookup(() -> var18, CompletableFuture.completedFuture(Optional.empty()), var18);
         }
      } else {
         if (var2 == null) {
            UUID var13 = UUIDUtil.createOfflinePlayerUUID(var3);
            PlayerSkin var17 = DefaultPlayerSkin.get(var13);
            return new AutismAccountsScreen.SkinLookup(() -> var17, CompletableFuture.completedFuture(Optional.empty()), var17);
         }

         try {
            PlayerSkin var4 = DefaultPlayerSkin.get(var2);
            CompletableFuture<Optional<PlayerSkin>> var16 = CompletableFuture.<GameProfile>supplyAsync(
                  () -> this.minecraft.services().profileResolver().fetchById(var2).orElse(new GameProfile(var2, var3)), Util.nonCriticalIoPool()
               )
               .thenCompose(var1x -> this.minecraft.getSkinManager().get(var1x));
            return new AutismAccountsScreen.SkinLookup(() -> {
               try {
                  return var16.getNow(Optional.empty()).orElse(var4);
               } catch (Exception var3x) {
                  return var4;
               }
            }, var16, var4);
         } catch (Exception var12) {
            PlayerSkin var5 = DefaultPlayerSkin.get(var2);
            return new AutismAccountsScreen.SkinLookup(() -> var5, CompletableFuture.completedFuture(Optional.empty()), var5);
         }
      }
   }

   private PlayerSkin fallbackSkin(AutismAccount var1) {
      String var2 = var1 != null && !var1.displayName().isBlank() ? var1.displayName() : "Autism";
      UUID var3 = this.accountUuid(var1);
      if (var3 == null || var1 != null && var1.type == AutismAccountType.Cracked) {
         var3 = UUIDUtil.createOfflinePlayerUUID(var2);
      }

      return DefaultPlayerSkin.get(var3);
   }

   private Optional<GameProfile> resolveCrackedSkinProfile(String var1) {
      String var2 = safeTrim(var1);
      if (isValidMinecraftUsername(var2) && this.minecraft != null) {
         try {
            Optional<GameProfile> var3 = this.minecraft.services().profileResolver().fetchByName(var2);
            if (var3.isPresent() && ((GameProfile)var3.get()).properties().containsKey("textures")) {
               return var3;
            }

            Optional var4 = var3.flatMap(this::withMojangTextures);
            if (var4.isPresent()) {
               return var4;
            }
         } catch (Exception var5) {
         }

         return this.fetchMojangProfileByName(var2);
      } else {
         return Optional.empty();
      }
   }

   private Optional<GameProfile> fetchMojangProfileByName(String var1) {
      try {
         String var2 = URLEncoder.encode(var1, StandardCharsets.UTF_8);
         JsonObject var3 = AutismHttp.getJsonDirect("https://api.mojang.com/users/profiles/minecraft/" + var2, null);
         if (var3 != null && var3.has("id") && var3.has("name")) {
            UUID var4 = UndashedUuid.fromStringLenient(var3.get("id").getAsString());
            String var5 = var3.get("name").getAsString();
            return this.withMojangTextures(new GameProfile(var4, var5));
         } else {
            return Optional.empty();
         }
      } catch (Exception var6) {
         return Optional.empty();
      }
   }

   private Optional<GameProfile> withMojangTextures(GameProfile var1) {
      if (var1 != null && var1.id() != null) {
         if (var1.properties().containsKey("textures")) {
            return Optional.of((GameProfile)var1);
         }

         try {
            String var2 = UndashedUuid.toString(var1.id());
            JsonObject var3 = AutismHttp.getJsonDirect("https://sessionserver.mojang.com/session/minecraft/profile/" + var2 + "?unsigned=false", null);
            if (var3 == null || !var3.has("properties") || !var3.get("properties").isJsonArray()) {
               return Optional.empty();
            }

            for (JsonElement var6 : var3.getAsJsonArray("properties")) {
               if (var6.isJsonObject()) {
                  JsonObject var7 = var6.getAsJsonObject();
                  String var8 = jsonString(var7, "name");
                  String var9 = jsonString(var7, "value");
                  String var10 = jsonString(var7, "signature");
                  if ("textures".equals(var8) && !var9.isBlank() && !var10.isBlank()) {
                     String var11 = jsonString(var3, "name");
                     GameProfile var12 = new GameProfile(var1.id(), var11.isBlank() ? var1.name() : var11);
                     var12.properties().put("textures", new Property("textures", var9, var10));
                     return Optional.of(var12);
                  }
               }
            }
         } catch (Exception var13) {
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   private static boolean isValidMinecraftUsername(String var0) {
      if (var0 != null && var0.length() >= 3 && var0.length() <= 16) {
         for (int var1 = 0; var1 < var0.length(); var1++) {
            char var2 = var0.charAt(var1);
            if ((var2 < 'A' || var2 > 'Z') && (var2 < 'a' || var2 > 'z') && (var2 < '0' || var2 > '9') && var2 != '_') {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static String jsonString(JsonObject var0, String var1) {
      if (var0 != null && var1 != null && var0.has(var1)) {
         JsonElement var2 = var0.get(var1);
         return var2 != null && var2.isJsonPrimitive() ? var2.getAsString() : "";
      } else {
         return "";
      }
   }

   private String skinKey(AutismAccount var1) {
      return var1 == null
         ? "empty"
         : (this.isDefaultAccount(var1) ? "default" : var1.type.name())
            + ":"
            + safeTrim(var1.username)
            + ":"
            + safeTrim(var1.uuid)
            + ":"
            + safeTrim(var1.label);
   }

   private void pruneSkinLookups() {
      synchronized (this.skinLookups) {
         if (this.skinLookups.size() > 10) {
            List var2 = this.protectedSkinKeys();
            Iterator var3 = this.skinLookups.entrySet().iterator();

            while (this.skinLookups.size() > 10 && var3.hasNext()) {
               Entry var4 = (Entry)var3.next();
               if (!var2.contains(var4.getKey())) {
                  var3.remove();
               }
            }
         }
      }
   }

   private List<String> protectedSkinKeys() {
      ArrayList<String> var1 = new ArrayList<>();
      AutismAccount var2 = this.previewAccount();
      this.addProtectedSkinKey(var1, var2);

      for (AutismAccountsScreen.AccountRow var4 : this.accountRows) {
         this.addProtectedSkinKey(var1, var4.account);
      }

      AutismAccount var5 = this.defaultMinecraftAccount();
      this.addProtectedSkinKey(var1, var5);
      return var1;
   }

   private void addProtectedSkinKey(List<String> var1, AutismAccount var2) {
      if (var1 != null && var2 != null && var1.size() < 10) {
         String var3 = this.skinKey(var2);
         if (!var1.contains(var3)) {
            var1.add(var3);
         }
      }
   }

   private List<AutismAccount> filteredAccounts() {
      List<AutismAccount> var1 = this.accountSnapshot();
      String var2 = normalizeSearch(this.searchQuery);
      int var3 = this.accountFilterMask();
      if (this.cachedFilteredRevision == this.accountSnapshotRevision && var2.equals(this.cachedAccountQuery) && var3 == this.cachedAccountFilterMask) {
         return this.cachedFilteredAccounts;
      }

      this.cachedFilteredRevision = this.accountSnapshotRevision;
      this.cachedAccountQuery = var2;
      this.cachedAccountFilterMask = var3;
      this.cachedDisplaySource = null;
      if (var2.isEmpty() && this.categoryFilter == null) {
         this.cachedFilteredAccounts = var1;
         return this.cachedFilteredAccounts;
      }

      ArrayList var4 = new ArrayList();

      for (AutismAccount var6 : var1) {
         if (var6 != null && (this.categoryFilter == null || var6.type == this.categoryFilter) && (var2.isEmpty() || this.matchesNickname(var6, var2))) {
            var4.add(var6);
         }
      }

      this.cachedFilteredAccounts = List.copyOf(var4);
      return this.cachedFilteredAccounts;
   }

   private List<AutismAccountsScreen.DisplayAccountRow> displayAccounts() {
      List<AutismAccount> var1 = this.filteredAccounts();
      if (this.cachedDisplaySource == var1) {
         return this.cachedDisplayAccounts;
      }

      ArrayList var2 = new ArrayList();
      AutismAccount var3 = this.defaultMinecraftAccount();
      if (var3 != null) {
         var2.add(new AutismAccountsScreen.DisplayAccountRow(var3, true));
      }

      for (AutismAccount var5 : var1) {
         var2.add(new AutismAccountsScreen.DisplayAccountRow(var5, false));
      }

      this.cachedDisplaySource = var1;
      this.cachedDisplayAccounts = List.copyOf(var2);
      return this.cachedDisplayAccounts;
   }

   private List<AutismAccount> accountSnapshot() {
      if (!this.accountSnapshotDirty) {
         return this.cachedAccountSnapshot;
      }

      this.cachedAccountSnapshot = List.copyOf(AutismAccountManager.get().all());
      this.accountSnapshotDirty = false;
      this.accountSnapshotRevision++;
      this.cachedFilteredRevision = Long.MIN_VALUE;
      this.cachedDisplaySource = null;
      return this.cachedAccountSnapshot;
   }

   private void invalidateAccountSnapshot() {
      this.accountSnapshotDirty = true;
   }

   private int accountFilterMask() {
      return this.categoryFilter == null ? -1 : 1 << this.categoryFilter.ordinal();
   }

   private boolean matchesNickname(AutismAccount var1, String var2) {
      String var3 = normalizeSearch(var1 == null ? "" : var1.displayName());
      if (var2.isEmpty()) {
         return true;
      } else {
         return var3.contains(var2) ? true : fuzzyContains(var3, var2);
      }
   }

   private static boolean fuzzyContains(String var0, String var1) {
      if (var0 != null && var1 != null && !var1.isEmpty()) {
         int var2 = 0;
         int var3 = 0;
         int var4 = 0;
         int var5 = Math.max(1, var1.length() / 3);

         while (var2 < var0.length() && var3 < var1.length()) {
            if (var0.charAt(var2) == var1.charAt(var3)) {
               var3++;
            } else if (var3 > 0) {
               var4++;
            }

            if (var4 > var5) {
               return false;
            }

            var2++;
         }

         return var3 == var1.length();
      } else {
         return true;
      }
   }

   private static String normalizeSearch(String var0) {
      return safeTrim(var0).toLowerCase(Locale.ROOT);
   }

   private UUID accountUuid(AutismAccount var1) {
      if (var1 != null && var1.uuid != null && !var1.uuid.isBlank()) {
         try {
            return UndashedUuid.fromStringLenient(var1.uuid);
         } catch (Exception var5) {
            try {
               return UUID.fromString(var1.uuid);
            } catch (Exception var4) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private AutismAccount previewAccount() {
      if (this.selectedAccount != null) {
         return this.selectedAccount;
      }

      AutismAccount var1 = this.defaultMinecraftAccount();
      if (var1 != null && this.isCurrentDefaultAccount()) {
         return var1;
      }

      List<AutismAccount> var2 = this.accountSnapshot();

      for (AutismAccount var4 : var2) {
         if (this.isCurrentAccount(var4)) {
            return var4;
         }
      }

      if (var1 != null) {
         return var1;
      } else {
         return var2.isEmpty() ? null : (AutismAccount)var2.get(0);
      }
   }

   private boolean isCurrentAccount(AutismAccount var1) {
      if (var1 != null && this.minecraft != null && this.minecraft.getUser() != null) {
         UUID var2 = this.accountUuid(var1);
         if (var2 != null) {
            return var2.equals(this.minecraft.getUser().getProfileId());
         }

         String var3 = this.minecraft.getUser().getName();
         String var4 = safeTrim(var1.username);
         if (var4.isBlank()) {
            var4 = safeTrim(var1.label);
         }

         return var3 != null && !var4.isBlank() && var3.equals(var4);
      } else {
         return false;
      }
   }

   private boolean isCurrentDefaultAccount() {
      User var1 = AutismAccountSessionSwitcher.getOriginalUser();
      User var2 = this.minecraft == null ? null : this.minecraft.getUser();
      return var1 != null && var2 != null && var1.getProfileId().equals(var2.getProfileId()) && var1.getName().equals(var2.getName());
   }

   private boolean isDefaultAccount(AutismAccount var1) {
      AutismAccount var2 = this.defaultMinecraftAccount();
      return var2 != null
         && var1 != null
         && var2.uuid.equals(var1.uuid)
         && var2.username.equals(var1.username)
         && var1.label != null
         && var1.label.startsWith("Default Minecraft:");
   }

   private AutismAccount defaultMinecraftAccount() {
      User var1 = AutismAccountSessionSwitcher.getOriginalUser();
      if (var1 == null) {
         return null;
      }

      AutismAccount var2 = new AutismAccount();
      var2.type = safeTrim(var1.getAccessToken()).isBlank() ? AutismAccountType.Cracked : AutismAccountType.Microsoft;
      var2.label = "Default Minecraft: " + var1.getName();
      var2.username = var1.getName();
      var2.uuid = var1.getProfileId().toString();
      var2.token = var1.getAccessToken();
      return var2;
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelWidth(), 12);
   }

   private int panelWidth() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 12, 520);
   }

   private int listX() {
      return this.panelX() + 10;
   }

   private int listWidth() {
      return Math.max(1, this.panelWidth() - 20);
   }

   private int previewX() {
      return this.panelX() + 10 + 292 + 8;
   }

   private boolean isInPreview(double var1, double var3) {
      if (this.narrowLayout()) {
         return false;
      }

      int var5 = this.previewX();
      return var1 >= var5 && var1 < var5 + 200 && var3 >= 20.0 && var3 < 198.0;
   }

   private int listPanelHeight() {
      return Math.max(1, this.screenHeight() - this.listTop() - 12);
   }

   private int listTop() {
      return this.narrowLayout() ? 48 : 204;
   }

   private int listRowTop() {
      return this.listTop() + 22;
   }

   private int savedRowsTop() {
      return this.listRowTop();
   }

   private int savedRowsBottom() {
      return this.listTop() + this.listPanelHeight() - 6;
   }

   private int savedViewportHeight() {
      return Math.max(24, alignViewportHeight(Math.max(1, this.savedRowsBottom() - this.savedRowsTop()), 24));
   }

   private int savedViewportRows() {
      return Math.max(1, this.savedViewportHeight() / 24);
   }

   private int savedMaxScroll(int var1) {
      return Math.max(0, var1 * 24 - this.savedViewportHeight());
   }

   private CompactScrollbar.Metrics accountScrollbarMetrics(int var1) {
      int var2 = Math.max(0, var1) * 24;
      int var3 = this.savedViewportHeight();
      int var4 = this.listX() + this.listWidth() - 8;
      int var5 = this.savedRowsTop();
      int var6 = this.savedViewportHeight();
      return CompactScrollbar.compute(var2, var3, var4, var5, 4, var6, this.savedListScroll.tick(0.0F, this.savedMaxScroll(var1)));
   }

   private int rowX() {
      return this.listX() + 8;
   }

   private int rowRight() {
      return this.listX() + this.listWidth() - 8 - 12;
   }

   private int rowWidth() {
      return Math.max(1, this.rowRight() - this.rowX());
   }

   private int rowVisualHeight() {
      return 22;
   }

   private int rowButtonY(int var1, int var2) {
      return var1 + Math.max(1, (this.rowVisualHeight() - var2) / 2);
   }

   private boolean narrowLayout() {
      return this.panelWidth() < 520 || this.screenHeight() < 248;
   }

   private boolean compactListLayout() {
      return this.listWidth() < 190 || this.listPanelHeight() < 46;
   }

   private static int alignViewportHeight(int var0, int var1) {
      return var1 <= 0 ? Math.max(1, var0) : Math.max(var1, Math.max(1, var0) / var1 * var1);
   }

   private static int quantizeScrollOffset(int var0, int var1, int var2) {
      int var3 = Math.max(0, Math.min(var2, var0));
      if (var1 <= 0) {
         return var3;
      }

      int var4 = Math.round((float)var3 / var1) * var1;
      return Math.max(0, Math.min(var2, var4));
   }

   private void drawText(GuiGraphicsExtractor var1, String var2, int var3, int var4, int var5, boolean var6) {
      this.drawText(var1, var2, var3, var4, var5, var6, Integer.MAX_VALUE);
   }

   private void drawText(GuiGraphicsExtractor var1, String var2, int var3, int var4, int var5, boolean var6, int var7) {
      Font var8 = this.font;
      Identifier var9 = THEME.fontFor(UiTone.BODY);
      String var10 = var2 == null ? "" : var2;
      if (var7 != Integer.MAX_VALUE && !var6) {
         UiText.drawFitted(var1, var8, var10, var9, var5, var3, var4, Math.max(1, var7), false);
      } else {
         if (var7 != Integer.MAX_VALUE) {
            var10 = UiText.trimToWidth(var8, var10, var7, var9, var5);
         }

         int var11 = UiText.width(var8, var10, var9, var5);
         int var12 = var6 ? var3 - var11 / 2 : var3;
         UiText.draw(var1, var8, var10, var9, var5, var12, var4, false);
      }
   }

   private void clearInputFocus() {
      if (this.labelField != null) {
         this.labelField.setFocused(false);
      }

      if (this.tokenField != null) {
         this.tokenField.setFocused(false);
      }

      if (this.searchField != null) {
         this.searchField.setFocused(false);
      }

      this.setFocused(null);
   }

   private String inputLabel() {
      return switch (this.type) {
         case Cracked, Generated -> "Cracked username";
         case TheAltening -> "TheAltening token";
         case Session -> "Session access token";
         case Microsoft -> "";
      };
   }

   private String addButtonLabel() {
      if (this.renamingAccount != null) {
         return "Rename";
      }

      return switch (this.type) {
         case Cracked, Generated -> "Add Cracked";
         case TheAltening -> "Add Altening";
         case Session -> "Add Session";
         case Microsoft -> "Login with Microsoft";
      };
   }

   private static int outlineColor(int var0) {
      return AutismTheme.recolor(var0, AutismTheme.Channel.OUTLINE);
   }

   private static int successColor(int var0) {
      return AutismTheme.recolor(var0, AutismTheme.Channel.SUCCESS);
   }

   private record AccountRow(
      AutismAccount account,
      int y,
      CompactOverlayButton loginButton,
      CompactOverlayButton deleteButton,
      CompactOverlayButton shareButton,
      CompactOverlayButton renameButton,
      boolean defaultAccount,
      boolean loadSkin
   ) {
   }

   private record DisplayAccountRow(AutismAccount account, boolean defaultAccount) {
   }

   private enum Operation {
      NONE,
      ADD,
      LOGIN,
      MICROSOFT,
      CHECK;
   }

   private record SkinLookup(Supplier<PlayerSkin> supplier, CompletableFuture<?> future, PlayerSkin fallback) {
      private PlayerSkin skin() {
         try {
            PlayerSkin var1 = this.supplier.get();
            return var1 == null ? this.fallback : var1;
         } catch (Exception var2) {
            return this.fallback;
         }
      }

      private boolean loading() {
         return this.future != null && !this.future.isDone();
      }
   }
}
