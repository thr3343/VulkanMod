package net.vulkanmod.config.gui;

import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.UpdateChecker;
import net.vulkanmod.config.gui.render.GuiRenderer;
import net.vulkanmod.config.gui.util.SearchHelper;
import net.vulkanmod.config.gui.util.VGuiConstants;
import net.vulkanmod.config.gui.widget.VAbstractWidget;
import net.vulkanmod.config.gui.widget.VButtonWidget;
import net.vulkanmod.config.gui.widget.VTextInputWidget;
import net.vulkanmod.config.option.*;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class VOptionScreen extends Screen {
    final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("vulkanmod", "vlogo_transparent.png");

    private final Screen parent;
    private static boolean initialized = false;

    private List<OptionPage> optionPages;
    private OptionPage searchResultsPage;

    private int currentListIdx = 0;
    private boolean isSearchActive = false;

    private int tooltipWidth;

    private VButtonWidget applyButton;
    private VButtonWidget undoButton;

    private VTextInputWidget searchField;

    private final List<VButtonWidget> pageButtons = Lists.newArrayList();
    private final List<VButtonWidget> buttons = Lists.newArrayList();


    public VOptionScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;

        this.optionPages = new ArrayList<>();
    }

    private void addPages() {
        this.optionPages.clear();

        OptionPage page = new OptionPage(
                Component.translatable("vulkanmod.options.pages.video").getString(),
                Options.getVideoOpts()
        );
        this.optionPages.add(page);

        page = new OptionPage(
                Component.translatable("vulkanmod.options.pages.graphics").getString(),
                Options.getGraphicsOpts()
        );
        this.optionPages.add(page);

        page = new OptionPage(
                Component.translatable("vulkanmod.options.pages.optimizations").getString(),
                Options.getOptimizationOpts()
        );
        this.optionPages.add(page);

        page = new OptionPage(
                Component.translatable("vulkanmod.options.pages.other").getString(),
                Options.getOtherOpts()
        );
        this.optionPages.add(page);
    }

    private VTextInputWidget createSearchField() {
        int rightMargin = 10;
        int padding = 10;
        int kofiWidth = Minecraft.getInstance().font.width(Component.translatable("vulkanmod.options.buttons.kofi")) + padding;
        int topBarRight = this.width - kofiWidth - rightMargin;

        if (UpdateChecker.isUpdateAvailable()) {
            int updateWidth = minecraft.font.width(Component.translatable("vulkanmod.options.buttons.update_available")) + padding;
            topBarRight -= updateWidth + VGuiConstants.WIDGET_MARGIN;
        }

        return new VTextInputWidget(
                94, 4,
                topBarRight - 94 - 4, VGuiConstants.WIDGET_HEIGHT,
                Component.translatable("vulkanmod.options.searchFieldPlaceholder"),
                widget -> performSearch(widget.getInput())
        );
    }

    @Override
    protected void init() {
        if (!initialized) {
            OptionRegistry registry = OptionRegistry.get();

            registry.registerPage(
                    "video",
                    Component.translatable("vulkanmod.options.pages.video"),
                    Options.getVideoOpts(),
                    0
            );

            registry.registerPage(
                    "graphics",
                    Component.translatable("vulkanmod.options.pages.graphics"),
                    Options.getGraphicsOpts(),
                    1
            );

            registry.registerPage(
                    "optimizations",
                    Component.translatable("vulkanmod.options.pages.optimizations"),
                    Options.getOptimizationOpts(),
                    2
            );

            registry.registerPage(
                    "other",
                    Component.translatable("vulkanmod.options.pages.other"),
                    Options.getOtherOpts(),
                    3
            );

            initialized = true;
        }

        this.optionPages = OptionRegistry.get().getPages();

        if (this.optionPages.isEmpty()) {
            throw new IllegalStateException("Default Options weren't added!");
        }
        this.captureOriginalState();

        int top = 29;
        int bottom = 60;
        int itemHeight = 20;

        int leftMargin = 94;
        int rightMargin = 3;
        int listWidth = this.width - rightMargin - leftMargin;
        //int leftMargin = MARGIN + 90;
        //int listWidth = Math.min(this.width - leftMargin - MARGIN, 420);
        int listHeight = this.height - top - bottom;

        this.buildLists(leftMargin, top, listWidth, listHeight, itemHeight);

        this.searchField = createSearchField();

        int x = leftMargin + listWidth + 10;
        int width = this.width - x - 10;

        if (width < 200) {
            width = listWidth;
        }

        this.tooltipWidth = width;

        buildPage();

        this.applyButton.active = false;
        this.undoButton.visible = false;
    }

    private void captureOriginalState() {
        for (OptionPage page : this.optionPages) {
            page.captureOriginalState();
        }
    }

    private void undo() {
        for (OptionPage page : this.optionPages) {
            page.resetToOriginalState();
            page.updateOptionStates();
        }

        buildPage();
    }

    private void buildLists(int left, int top, int listWidth, int listHeight, int itemHeight) {
        for (OptionPage page : this.optionPages) {
            page.createList(left, top, listWidth, listHeight, itemHeight);
        }
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            isSearchActive = false;
            this.currentListIdx = 0;
            buildPage();
            return;
        }

        String searchTerm = query.toLowerCase().trim();
        List<OptionBlock> searchResults = new ArrayList<>();

        for (OptionPage page : this.optionPages) {
            List<Option<?>> matchingOptions = new ArrayList<>();

            for (OptionBlock block : page.optionBlocks) {
                for (Option<?> option : block.options()) {
                    boolean matches = false;

                    String optionName = option.getName().getString().toLowerCase();
                    String optionTooltip = option.getTooltip() != null ?
                            option.getTooltip().getString().toLowerCase() : "";
                    String displayedValue = option.getDisplayedValue().getString().toLowerCase();

                    if (optionName.contains(searchTerm) ||
                            optionTooltip.contains(searchTerm) ||
                            displayedValue.contains(searchTerm)) {
                        matches = true;
                    }

                    else if (option instanceof CyclingOption<?> cycling) {
                        if (SearchHelper.matchesAnyValue(cycling, searchTerm)) {
                            matches = true;
                        }
                    }

                    if (matches) {
                        matchingOptions.add(option);
                    }
                }
            }

            if (!matchingOptions.isEmpty()) {
                searchResults.add(new OptionBlock("§l" + page.name,
                        matchingOptions.toArray(new Option<?>[0])));
                searchResults.add(new OptionBlock("", new Option<?>[0]));
            }
        }

        searchResultsPage = new OptionPage(
                "Search Results",
                searchResults.toArray(new OptionBlock[0])
        );

        int top = 29;
        int itemHeight = 20;
        int leftMargin = 94;
        int rightMargin = 3;
        int listWidth = this.width - rightMargin - leftMargin;
        int listHeight = this.height - top - 60;

        searchResultsPage.createList(leftMargin, top, listWidth, listHeight, itemHeight);

        isSearchActive = true;
        buildPage();
    }

    private void buildPage() {
        this.buttons.clear();
        this.pageButtons.clear();

        String savedInput = this.searchField != null ? this.searchField.getInput() : "";
        boolean savedFocused = this.searchField != null && this.searchField.focused;
        boolean savedSelected = this.searchField != null && this.searchField.selected;

        this.clearWidgets();

        int x = 10;
        int y = 36;
        for (int i = 0; i < this.optionPages.size(); ++i) {
            var page = this.optionPages.get(i);
            final int finalIdx = i;
            VButtonWidget widget = new VButtonWidget(x, y, 80, VGuiConstants.WIDGET_HEIGHT, Component.nullToEmpty(page.name), button -> this.setOptionList(finalIdx));
            this.buttons.add(widget);
            this.pageButtons.add(widget);
            this.addWidget(widget);

            y += VGuiConstants.WIDGET_HEIGHT;
        }

        if (!isSearchActive) {
            this.pageButtons.get(this.currentListIdx).setSelected(true);
            VOptionList currentList = this.optionPages.get(this.currentListIdx).getOptionList();
            this.addWidget(currentList);
        } else {
            if (searchResultsPage != null) {
                VOptionList searchList = searchResultsPage.getOptionList();
                this.addWidget(searchList);
                searchResultsPage.updateOptionStates();
            }
        }

        this.addButtonsWithSearchBar();

        this.searchField.setInput(savedInput);
        if (savedFocused) {
            this.searchField.setFocused(true);
            this.searchField.setSelected(savedSelected);
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private void addButtonsWithSearchBar() {
        int rightMargin = 10;
        int padding = 10;
        int buttonWidth = Minecraft.getInstance().font.width(CommonComponents.GUI_DONE) + 2 * padding;
        int x0 = (this.width - buttonWidth - rightMargin);
        int y0 = this.height - VGuiConstants.WIDGET_HEIGHT - 7;

        VButtonWidget doneButton = new VButtonWidget(x0, y0, buttonWidth, VGuiConstants.WIDGET_HEIGHT,
                CommonComponents.GUI_DONE, button -> Minecraft.getInstance().setScreen(this.parent));

        buttonWidth = Minecraft.getInstance().font.width(Component.translatable("vulkanmod.options.buttons.apply")) + 2 * padding;
        x0 -= (buttonWidth + VGuiConstants.WIDGET_MARGIN);
        this.applyButton = new VButtonWidget(x0, y0, buttonWidth, VGuiConstants.WIDGET_HEIGHT,
                Component.translatable("vulkanmod.options.buttons.apply"), button -> this.applyOptions());

        buttonWidth = Minecraft.getInstance().font.width(Component.translatable("vulkanmod.options.buttons.undo")) + 2 * padding;
        x0 -= (buttonWidth + VGuiConstants.WIDGET_MARGIN);
        this.undoButton = new VButtonWidget(x0, y0, buttonWidth, VGuiConstants.WIDGET_HEIGHT,
                Component.translatable("vulkanmod.options.buttons.undo"), button -> undo());

        int kofiWidth = Minecraft.getInstance().font.width(Component.translatable("vulkanmod.options.buttons.kofi")) + padding;

        int kofiX = this.width - kofiWidth - rightMargin;
        VButtonWidget supportButton = new VButtonWidget(kofiX, 4, kofiWidth, VGuiConstants.WIDGET_HEIGHT,
                Component.translatable("vulkanmod.options.buttons.kofi"),
                button -> Util.getPlatform().openUri("https://ko-fi.com/xcollateral"));

        this.buttons.add(this.applyButton);
        this.buttons.add(doneButton);
        this.buttons.add(supportButton);
        this.buttons.add(this.undoButton);

        this.addWidget(this.applyButton);
        this.addWidget(doneButton);
        this.addWidget(supportButton);
        this.addWidget(this.undoButton);
        this.addWidget(this.searchField);

        if (UpdateChecker.isUpdateAvailable()) {
            assert minecraft != null;
            int updateWidth = minecraft.font.width(Component.translatable("vulkanmod.options.buttons.update_available")) + padding;
            var updateButton = new VButtonWidget(
                    kofiX - updateWidth - VGuiConstants.WIDGET_MARGIN, 4,
                    updateWidth, VGuiConstants.WIDGET_HEIGHT,
                    Component.translatable("vulkanmod.options.buttons.update_available").withStyle(ChatFormatting.UNDERLINE),
                    button -> Util.getPlatform().openUri("https://modrinth.com/mod/vulkanmod")
            );
            this.buttons.add(updateButton);
            this.addWidget(updateButton);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        for (GuiEventListener element : this.children()) {
            if (element.mouseClicked(event, bl)) {
                this.setFocused(element);
                if (event.button() == 0) {
                    this.setDragging(true);
                }

                this.updateState();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.setDragging(false);
        this.updateState();
        return this.getChildAt(event.x(), event.y())
                .filter(guiEventListener -> guiEventListener.mouseReleased(event))
                .isPresent();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        GuiRenderer.guiGraphics = guiGraphics;
        VRenderSystem.enableBlend();

        int iconBackgroundColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_BLACK, 0.45f);
        int iconBackgroundWidth = 90;
        int iconBackgroundHeight = (minecraft.font.lineHeight * 4);
        guiGraphics.fill(10, 4, iconBackgroundWidth, iconBackgroundHeight, iconBackgroundColor);

        int size = minecraft.font.lineHeight * 4;
        int iconX = 10 + (iconBackgroundWidth - 10 - size) / 2;
        int iconY = 4 + (iconBackgroundHeight - 4 - size) / 2;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ICON, iconX, iconY, 0f, 0f, size, size, size, size);

        VOptionList currentList;
        if (isSearchActive && searchResultsPage != null) {
            currentList = searchResultsPage.getOptionList();
        } else {
            currentList = this.optionPages.get(this.currentListIdx).getOptionList();
        }

        currentList.updateState(mouseX, mouseY);
        currentList.renderWidget(mouseX, mouseY);

        for (VButtonWidget button : buttons) {
            button.updateState(mouseX, mouseY);
            button.render(mouseX, mouseY);
        }
        searchField.updateState(mouseX, mouseY);
        searchField.render(mouseX, mouseY);

        VAbstractWidget hoveredWidget = null;

        for (var b : buttons) {
            if (b.isMouseOver(mouseX, mouseY)) {
                hoveredWidget = b;
                break;
            }
        }

        if (hoveredWidget == null) {
            hoveredWidget = currentList.getHoveredWidget(mouseX, mouseY);
        }

        if (hoveredWidget != null) {
            List<FormattedCharSequence> tooltip = getWidgetTooltip(hoveredWidget);
            if (tooltip != null) {
                int padding = 3;
                int tooltipWidth = GuiRenderer.getMaxTextWidth(this.font, tooltip);
                int tooltipX = hoveredWidget.getX() + hoveredWidget.getWidth() - tooltipWidth - padding;
                int tooltipY = hoveredWidget.getY() + hoveredWidget.getHeight() + 3 + 1;
                this.renderTooltip(tooltip, tooltipX, tooltipY);
            }
        }
    }

    private void renderTooltip(List<FormattedCharSequence> list, int x, int y) {
        if (list.isEmpty()) return;
        int padding = 3;
        int width = GuiRenderer.getMaxTextWidth(this.font, list);
        int height = list.size() * 10;
        GuiRenderer.fill(x - padding, y - padding, x + width + padding, y + height + padding,
                ColorUtil.ARGB.pack(0.05f, 0.05f, 0.05f, 0.6f));

        GuiRenderer.renderBorder(x - padding, y - padding, x + width + padding, y + height + padding, 1, VGuiConstants.COLOR_RED);

        int yOffset = 0;
        for (var text : list) {
            GuiRenderer.drawString(this.font, text, x, y + yOffset, 0xffffffff);
            yOffset += 10;
        }
    }

    private List<FormattedCharSequence> getWidgetTooltip(VAbstractWidget widget) {
        var tooltip = widget.getTooltip();
        if (tooltip == null)
            return null;

        return this.font.split(tooltip, this.tooltipWidth);
    }

    private void updateState() {
        if (this.applyButton == null | this.undoButton == null) return;
        boolean modified = false;
        for (var page : this.optionPages) {
            modified |= page.optionChanged();
        }

        if (modified) {
            for (var page : this.optionPages) {
                page.optionChanged();
            }
        }

        this.applyButton.active = modified;
        this.undoButton.visible = modified;
    }

    private void setOptionList(int i) {
        this.currentListIdx = i;
        this.isSearchActive = false;

        this.searchField.setInput("");
        this.searchField.setFocused(false);

        this.buildPage();

        this.pageButtons.get(i).setSelected(true);
    }

    private void applyOptions() {
        List<OptionPage> pages = List.copyOf(this.optionPages);
        for (var page : pages) {
            page.applyOptionChanges();
            page.updateOptionStates();
        }

        this.captureOriginalState();

        Initializer.CONFIG.save();
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_L) {
            this.setFocused(searchField);
            searchField.setFocused(true);
            searchField.setSelected(true);

            return true;
        }

        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE && this.isSearchActive) {
            this.isSearchActive = false;
            this.searchField.setInput("");
            this.searchField.setFocused(false);
            this.buildPage();
            this.pageButtons.get(this.currentListIdx).setSelected(true);
            return true;
        }


        if (!this.searchField.focused
                && keyEvent.key() == GLFW.GLFW_KEY_P
                && keyEvent.hasShiftDown()) {
            Minecraft.getInstance().setScreen(new VideoSettingsScreen(this, Minecraft.getInstance(), Minecraft.getInstance().options));

            return false;
        }

        return super.keyPressed(keyEvent);
    }
}