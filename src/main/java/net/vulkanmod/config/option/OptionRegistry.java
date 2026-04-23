package net.vulkanmod.config.option;

import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.OptionBlock;

import java.util.*;

public final class OptionRegistry {

    private static final OptionRegistry INSTANCE = new OptionRegistry();

    private final Map<String, OptionPage> pagesById = new HashMap<>();
    private final List<OptionPage> pages = new ArrayList<>();

    private OptionRegistry() {}

    public static OptionRegistry get() {
        return INSTANCE;
    }

    public synchronized void registerPage(
            String id,
            Component title,
            OptionBlock[] blocks,
            int order
    ) {
        if (pagesById.containsKey(id)) {
            throw new IllegalStateException("Option page already registered: " + id);
        }

        OptionPage page = new OptionPage(title.getString(), blocks);
        page.setOrder(order);

        pagesById.put(id, page);
        pages.add(page);

        pages.sort(Comparator.comparingInt(OptionPage::getOrder));
    }

    public List<OptionPage> getPages() {
        return Collections.unmodifiableList(pages);
    }

    public synchronized void unregister(String id) {
        OptionPage page = pagesById.remove(id);
        if (page != null) {
            pages.remove(page);
        }
    }

    public boolean isRegistered(String id) {
        return pagesById.containsKey(id);
    }
}