package fr.renblood.medievalcoins.market.counter;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.service.MarketMoneyService;
import fr.renblood.medievalcoins.market.service.MarketPriceService;
import fr.renblood.medievalcoins.network.MerchantCounterHistoryRequestMessage;
import fr.renblood.medievalcoins.network.MerchantCounterUpdateMessage;
import fr.renblood.medievalcoins.network.PlayerMarketOwnerActionMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import fr.renblood.medievalcoins.client.economy.AdaptiveEconomyScreen;
import fr.renblood.medievalcoins.client.economy.EconomyUi;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MerchantCounterOwnerScreen extends AdaptiveEconomyScreen<MerchantCounterOwnerMenu> implements MarketHistoryReceiver {
    private static final int WIDTH = 680;
    private static final int HEIGHT = 350;
    private static final int STOCK_X = 259;
    private static final int STOCK_Y = 68;
    private static final int PLAYER_INV_X = 259;
    private static final int PLAYER_INV_Y = 205;
    private static final int PRICE_PAGE_SIZE = 12;

    private enum Page {
        GENERAL, STOCK, PRICES, HISTORY
    }

    private EditBox name;
    private EditBox historySearch;
    private EditBox itemPrice;
    private Page page = Page.GENERAL;
    private static String lastCounter="";
    private static Page lastPage=Page.GENERAL;
    private static int lastStockSlot;
    private boolean active;
    private boolean selectedItemEnabled;
    private boolean historyBackendUnavailable;
    private String historyAction = "TOUT";
    private String historyQuery = "";
    private String status = "";
    private int historyPageIndex;
    private int pricePageIndex;
    private int selectedPriceIndex;
    private int selectedStockSlot;
    private long stockSelection;
    private Button sendStock;
    private Button copyReceipt;
    private int cityIndex;
    private final List<MarketCityData> cities = new ArrayList<>();
    private final List<MarketTransactionData> history = new ArrayList<>();

    public MerchantCounterOwnerScreen(MerchantCounterOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        if(lastCounter.equals(menu.getData().counterId())){page=lastPage;selectedStockSlot=lastStockSlot;}
        else{lastCounter=menu.getData().counterId();lastPage=Page.GENERAL;lastStockSlot=0;}
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        active = menu.getData().active();
        cities.add(new MarketCityData("", "Aucune ville"));
        cities.addAll(menu.getData().availableCities());
        cityIndex = findCityIndex(menu.getData().cityId(), menu.getData().cityName());
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        addNavigation();
        switch (page) {
            case GENERAL -> initGeneral();
            case STOCK -> initStock();
            case PRICES -> initPrices();
            case HISTORY -> initHistory();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box) {
            if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
            if (box.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addNavigation() {
        int tabWidth = 145;
        int gap = 8;
        int x = leftPos + 28;
        addTab(t("gui.medieval_coins.market.owner.tab.general"), Page.GENERAL, x, tabWidth);
        if (menu.isPlayerMarket()) {
            x += tabWidth + gap;
            addTab(t("gui.medieval_coins.market.owner.tab.stock"), Page.STOCK, x, tabWidth);
            x += tabWidth + gap;
            addTab(t("gui.medieval_coins.market.owner.tab.prices"), Page.PRICES, x, tabWidth);
        }
        x += tabWidth + gap;
        addTab(t("gui.medieval_coins.market.owner.tab.history"), Page.HISTORY, x, tabWidth);
    }

    private void addTab(Component label, Page target, int x, int width) {
        Button button = addRenderableWidget(Button.builder(label, b -> openPage(target))
                .bounds(x, topPos + 28, width, 20).build());
        button.active = page != target;
    }

    private void openPage(Page target) {
        page = target;lastPage=target;
        status = "";
        if (target == Page.HISTORY) requestHistory();
        init();
    }

    private void initGeneral() {
        name = addRenderableWidget(new EditBox(font, leftPos + 170, topPos + 82, 330, 18, t("gui.medieval_coins.market.owner.name")));
        name.setValue(menu.getData().name());

        addRenderableWidget(Button.builder(Component.literal(cityLabel()), b -> {
            cityIndex = (cityIndex + 1) % cities.size();
            b.setMessage(Component.literal(cityLabel()));
        }).bounds(leftPos + 170, topPos + 114, 330, 20).build()).active = cities.size() > 1;

        addRenderableWidget(Button.builder(activeLabel(), b -> {
            active = !active;
            b.setMessage(activeLabel());
        }).bounds(leftPos + 170, topPos + 164, 330, 20).build());
        addRenderableWidget(Button.builder(t("gui.medieval_coins.market.owner.claim"), b -> ownerAction("CLAIM", 0, 0, false))
                .bounds(leftPos + 170, topPos + 204, 155, 20).build());
        addRenderableWidget(Button.builder(t("gui.medieval_coins.market.owner.save"), b -> save())
                .bounds(leftPos + 345, topPos + 204, 155, 20).build());
        addRenderableWidget(Button.builder(t("gui.medieval_coins.market.close"), b -> onClose())
                .bounds(leftPos + 285, topPos + 302, 110, 20).build());
        setInitialFocus(name);
    }

    private void initStock() {
        sendStock = addRenderableWidget(Button.builder(Component.empty(), button -> {
            ownerAction("DEPOSIT_BATCH", 0, stockSelection, false);
            stockSelection = 0;
            updateStockSelection();
        }).bounds(leftPos + 30, topPos + 113, 200, 20).build());
        copyReceipt = addRenderableWidget(Button.builder(Component.literal("Copier le reçu"),
                button -> minecraft.keyboardHandler.setClipboard(selectedStock().receipt()))
                .bounds(leftPos + 30, topPos + 224, 200, 20).build());
        updateStockSelection();
        addRenderableWidget(Button.builder(Component.literal("Déposer l’objet en main"),
                button -> ownerAction("DEPOSIT", selectedStockSlot, 0, false))
                .bounds(leftPos + 30, topPos + 146, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reprendre le stock"),
                button -> ownerAction("WITHDRAW", selectedStockSlot, 0, false))
                .bounds(leftPos + 30, topPos + 172, 200, 20).build());
        addRenderableWidget(Button.builder(t("gui.medieval_coins.market.close"), b -> onClose())
                .bounds(leftPos + 285, topPos + 318, 110, 20).build());
    }

    private PlayerMarketSlotData selectedStock() {
        return menu.getData().playerMarketSlots().stream().filter(slot -> slot.slot() == selectedStockSlot).findFirst()
                .orElse(new PlayerMarketSlotData(selectedStockSlot, ItemStack.EMPTY, 0, false));
    }

    private void updateStockSelection() {
        int count = Long.bitCount(stockSelection);
        sendStock.active = count > 0;
        copyReceipt.active = !selectedStock().receipt().isEmpty();
        sendStock.setMessage(Component.literal(count == 0 ? "Envoyer la sélection" : "Envoyer " + count + " pile(s)"));
    }

    private void initPrices() {
        List<PriceEntry> entries = priceEntries();
        int maxPage = Math.max(0, (entries.size() - 1) / PRICE_PAGE_SIZE);
        pricePageIndex = Math.min(pricePageIndex, maxPage);
        selectedPriceIndex = Math.min(selectedPriceIndex, Math.max(0, entries.size() - 1));

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            pricePageIndex--;
            selectedPriceIndex = pricePageIndex * PRICE_PAGE_SIZE;
            init();
        }).bounds(leftPos + 30, topPos + 299, 35, 20).build());
        previous.active = pricePageIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            pricePageIndex++;
            selectedPriceIndex = pricePageIndex * PRICE_PAGE_SIZE;
            init();
        }).bounds(leftPos + 70, topPos + 299, 35, 20).build());
        next.active = pricePageIndex < maxPage;

        if (entries.isEmpty()) return;
        PriceEntry selected = entries.get(selectedPriceIndex);
        selectedItemEnabled = selected.price() < 1 || selected.enabled();
        itemPrice = addRenderableWidget(new EditBox(font, leftPos + 360, topPos + 299, 100, 18, t("gui.medieval_coins.market.owner.price")));
        itemPrice.setValue(selected.price() < 1 ? "" : Long.toString(selected.price()));
        itemPrice.setFilter(value -> value.isEmpty() || value.matches("\\d{0,12}"));
        itemPrice.setHint(t("gui.medieval_coins.market.owner.price_hint"));

        addRenderableWidget(Button.builder(priceStateLabel(), b -> {
            selectedItemEnabled = !selectedItemEnabled;
            b.setMessage(priceStateLabel());
        }).bounds(leftPos + 470, topPos + 299, 80, 20).build());
        addRenderableWidget(Button.builder(t("gui.medieval_coins.market.owner.apply"), b -> applySelectedPrice(selected))
                .bounds(leftPos + 560, topPos + 299, 90, 20).build());
        setInitialFocus(itemPrice);
    }

    private void applySelectedPrice(PriceEntry selected) {
        long price = parsePrice();
        if (price < 1) {
            status = "Prix minimum : 1 fer.";
            return;
        }
        ownerAction("CONFIGURE_TYPE", selected.representativeSlot(), price, selectedItemEnabled);
    }

    private void initHistory() {
        historySearch = addRenderableWidget(new EditBox(font, leftPos + 30, topPos + 68, 440, 18, t("gui.medieval_coins.market.search")));
        historySearch.setHint(t("gui.medieval_coins.market.owner.history_hint"));
        historySearch.setValue(historyQuery);
        historySearch.setResponder(value -> {
            historyQuery = value;
            historyPageIndex = 0;
        });
        addRenderableWidget(Button.builder(t("gui.medieval_coins.market.owner.filter", historyAction), b -> {
            historyAction = "TOUT".equals(historyAction) ? "BUY" : "BUY".equals(historyAction) ? "SELL" : "TOUT";
            historyPageIndex = 0;
            init();
        }).bounds(leftPos + 490, topPos + 68, 160, 18).build());
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            historyPageIndex--;
            init();
        }).bounds(leftPos + 30, topPos + 310, 35, 20).build());
        previous.active = historyPageIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            historyPageIndex++;
            init();
        }).bounds(leftPos + 70, topPos + 310, 35, 20).build());
        next.active = (historyPageIndex + 1) * 8 < filteredHistory().size();
        addRenderableWidget(Button.builder(t("gui.medieval_coins.market.owner.refresh"), b -> requestHistory())
                .bounds(leftPos + 490, topPos + 310, 160, 20).build());
        setInitialFocus(historySearch);
    }

    private void ownerAction(String action, int slot, long price, boolean enabled) {
        MedievalCoin.PACKET_HANDLER.sendToServer(new PlayerMarketOwnerActionMessage(menu.getPos(), action, slot, price, enabled));
    }

    private long parsePrice() {
        try {
            return Long.parseLong(itemPrice.getValue());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void requestHistory() {
        status = "Chargement...";
        MedievalCoin.PACKET_HANDLER.sendToServer(new MerchantCounterHistoryRequestMessage(menu.getPos()));
    }

    private void save() {
        MerchantCounterData old = menu.getData();
        MarketCityData city = cities.get(cityIndex);
        MerchantCounterData update = new MerchantCounterData(old.counterId(), name.getValue(), old.type(),
                city.cityId(), city.cityName(), old.ownerType(), old.ownerId(), old.ownerName(), active,
                old.buyModeEnabled(), false, old.position(), old.configuredItems(), old.pendingOwnerBalanceInIron(),
                old.breadPrice(), old.ironPrice(), old.woodPrice(), old.purchasableItems(), old.sellableItems(),
                old.availableCities(), old.configuredItemsData(), old.referenceItems(), old.playerMarketSlots());
        MedievalCoin.PACKET_HANDLER.sendToServer(new MerchantCounterUpdateMessage(menu.getPos(), false, update));
        status = "Modifications enregistrees.";
    }

    @Override
    public void receiveHistory(List<MarketTransactionData> transactions, boolean backendUnavailable) {
        history.clear();
        history.addAll(transactions);
        historyBackendUnavailable = backendUnavailable;
        status = history.isEmpty() ? "Aucune transaction enregistree." : "";
        init();
    }

    private List<MarketTransactionData> filteredHistory() {
        String query = historyQuery.trim().toLowerCase(Locale.ROOT);
        return history.stream().filter(value -> "TOUT".equals(historyAction) || historyAction.equals(value.action()))
                .filter(value -> query.isEmpty() || value.playerName().toLowerCase(Locale.ROOT).contains(query)
                        || value.itemDisplayName().toLowerCase(Locale.ROOT).contains(query)
                        || value.itemId().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private List<PriceEntry> priceEntries() {
        Map<String, MutablePriceEntry> grouped = new LinkedHashMap<>();
        List<PlayerMarketSlotData> configuredSlots = menu.getData().playerMarketSlots();
        for (int slotIndex = 0; slotIndex < menu.playerMarketSlots(); slotIndex++) {
            ItemStack stack = slotStack(slotIndex);
            if (stack.isEmpty()) continue;
            PlayerMarketSlotData configured = slotIndex < configuredSlots.size()
                    ? configuredSlots.get(slotIndex)
                    : new PlayerMarketSlotData(slotIndex, ItemStack.EMPTY, 0L, false);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            String itemId = id == null ? stack.getHoverName().getString() : id.toString();
            int representativeSlot = slotIndex;
            MutablePriceEntry entry = grouped.computeIfAbsent(itemId,
                    ignored -> new MutablePriceEntry(itemId, representativeSlot, stack.copy(), configured.price(), configured.enabled()));
            entry.totalCount += stack.getCount();
            entry.enabled |= configured.enabled();
            if (entry.price < 1 && configured.price() > 0) entry.price = configured.price();
        }
        return grouped.values().stream().map(MutablePriceEntry::freeze).toList();
    }

    private Long referencePrice(String itemId) {
        return MarketPriceService.getBuyPrice(cities.get(cityIndex).cityId(), "", itemId)
                .orElseGet(() -> menu.getData().referenceItems().stream()
                        .filter(reference -> reference.itemId().equalsIgnoreCase(itemId))
                        .map(ReferenceItemData::referencePrice).filter(java.util.Objects::nonNull).findFirst().orElse(null));
    }

    private Component activeLabel() {
        return t(active ? "gui.medieval_coins.market.owner.counter_active" : "gui.medieval_coins.market.owner.counter_inactive");
    }

    private Component priceStateLabel() {
        return t(selectedItemEnabled ? "gui.medieval_coins.market.owner.on_sale" : "gui.medieval_coins.market.owner.paused");
    }

    private String cityLabel() {
        return t("gui.medieval_coins.market.owner.city", cities.get(cityIndex).cityName()).getString();
    }

    @Override
    public void renderLogical(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.renderLogical(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE201A15);
        graphics.fill(leftPos + 5, topPos + 5, leftPos + imageWidth - 5, topPos + imageHeight - 5, 0xFF3A2B20);
        graphics.fill(leftPos + 20, topPos + 58, leftPos + imageWidth - 20, topPos + 290, 0xFF241A15);
        if (page == Page.STOCK) renderStockBackground(graphics);
        if (page == Page.PRICES) renderPriceCardBackgrounds(graphics);
    }

    private void renderStockBackground(GuiGraphics graphics) {
        drawSlotGrid(graphics, STOCK_X, STOCK_Y, 9, 6);
        drawSlotGrid(graphics, PLAYER_INV_X, PLAYER_INV_Y, 9, 3);
        drawSlotGrid(graphics, PLAYER_INV_X, PLAYER_INV_Y + 58, 9, 1);
        for (int slot = 0; slot < 36; slot++) {
            if ((stockSelection & (1L << slot)) == 0) continue;
            int x = leftPos + PLAYER_INV_X + slot % 9 * 18;
            int y = topPos + PLAYER_INV_Y + (slot < 9 ? 58 : (slot / 9 - 1) * 18);
            graphics.renderOutline(x - 1, y - 1, 18, 18, 0xFFD6A35E);
            graphics.fill(x, y, x + 16, y + 16, 0xFF51402B);
        }
    }

    private void drawSlotGrid(GuiGraphics graphics, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int slotX = leftPos + x + column * 18 - 1;
                int slotY = topPos + y + row * 18 - 1;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF6B513B);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF17120E);
            }
        }
    }

    private void renderPriceCardBackgrounds(GuiGraphics graphics) {
        List<PriceEntry> entries = priceEntries();
        int start = pricePageIndex * PRICE_PAGE_SIZE;
        for (int index = start; index < Math.min(entries.size(), start + PRICE_PAGE_SIZE); index++) {
            int local = index - start;
            int x = 30 + (local % 4) * 157;
            int y = 70 + (local / 4) * 66;
            boolean selected = index == selectedPriceIndex;
            int border = selected ? 0xFFD6A35E : entries.get(index).enabled() ? 0xFF6D8654 : 0xFF5A4636;
            graphics.fill(leftPos + x, topPos + y, leftPos + x + 145, topPos + y + 56, border);
            graphics.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + 144, topPos + y + 55, 0xFF19130F);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, pageTitle(), imageWidth / 2, 10, 0xFFF2D49B);
        switch (page) {
            case GENERAL -> renderGeneral(graphics);
            case STOCK -> renderStock(graphics);
            case PRICES -> renderPrices(graphics);
            case HISTORY -> renderHistory(graphics);
        }
        graphics.drawCenteredString(font, status, imageWidth / 2, 338, 0xFFFFD37F);
    }

    private Component pageTitle() {
        return switch (page) {
            case GENERAL -> t("gui.medieval_coins.market.owner.title.general");
            case STOCK -> t("gui.medieval_coins.market.owner.title.stock", menu.playerMarketSlots());
            case PRICES -> t("gui.medieval_coins.market.owner.title.prices");
            case HISTORY -> t("gui.medieval_coins.market.owner.title.history");
        };
    }

    private void renderGeneral(GuiGraphics graphics) {
        MerchantCounterData data = menu.getData();
        graphics.drawString(font, t("gui.medieval_coins.market.owner.counter_name"), 40, 85, 0xFFF2D49B, false);
        graphics.drawString(font, t("gui.medieval_coins.market.owner.reference_city"), 40, 119, 0xFFF2D49B, false);
        graphics.drawString(font, t("gui.medieval_coins.market.owner.reference_city_help"), 170, 141, 0xFFB8C7D9, false);
        graphics.drawString(font, t("gui.medieval_coins.market.owner.owner", data.ownerName()), 170, 248, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.literal("Caisse confirmée : consultez Mes finances sur le site"), 170, 265, 0xFFF2D49B, false);
    }

    private void renderStock(GuiGraphics graphics) {
        graphics.drawString(font, t("gui.medieval_coins.market.owner.counter_stock"), STOCK_X, 57, 0xFFF2D49B, false);
        graphics.drawString(font, t("gui.medieval_coins.market.owner.player_inventory"), PLAYER_INV_X, 194, 0xFFF2D49B, false);
        var selected = selectedStock();
        String state = selected.conflict() ? "À vérifier avec l’équipe" : selected.reserved() + selected.incoming() > 0
                ? selected.reserved() + " réservé(s) · " + selected.incoming() + " en dépôt"
                : "Emplacement disponible";
        graphics.drawString(font, Component.literal("Case " + (selectedStockSlot + 1)), 30, 76, 0xFFF2D49B, false);
        graphics.drawString(font, Component.literal(state), 30, 92, 0xFFB8C7D9, false);
        graphics.drawString(font, Component.literal("Clique sur les piles de ton inventaire, puis envoie la sélection."),
                30, 300, 0xFFB8C7D9, false);
    }

    private void renderPrices(GuiGraphics graphics) {
        List<PriceEntry> entries = priceEntries();
        int start = pricePageIndex * PRICE_PAGE_SIZE;
        for (int index = start; index < Math.min(entries.size(), start + PRICE_PAGE_SIZE); index++) {
            PriceEntry entry = entries.get(index);
            int local = index - start;
            int x = 30 + (local % 4) * 157;
            int y = 70 + (local / 4) * 66;
            graphics.renderItem(entry.stack(), x + 7, y + 8);
            graphics.drawString(font, trim(entry.stack().getHoverName().getString(), 16), x + 29, y + 7, 0xFFFFFFFF, false);
            Component stock = t("gui.medieval_coins.market.stock", entry.totalCount());
            if (entry.price() > 0 && !entry.enabled()) {
                stock = stock.copy().append(" · ").append(t("gui.medieval_coins.market.owner.paused"));
            }
            graphics.drawString(font, stock, x + 29, y + 19, 0xFFB8C7D9, false);
            graphics.drawString(font, entry.price() < 1 ? t("gui.medieval_coins.market.owner.undefined_price")
                            : t("gui.medieval_coins.market.owner.sale_price", MarketMoneyService.formatMoney(entry.price())),
                    x + 7, y + 34, entry.price() < 1 ? 0xFFE08B6D : 0xFFF2D49B, false);
            Long reference = referencePrice(entry.itemId());
            graphics.drawString(font, reference == null ? t("gui.medieval_coins.market.owner.reference_unavailable")
                            : t("gui.medieval_coins.market.owner.reference_price", MarketMoneyService.formatMoney(reference)),
                    x + 7, y + 45, reference == null ? 0xFF8D7B69 : 0xFF9BC477, false);
        }
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, t("gui.medieval_coins.market.owner.empty_stock"), imageWidth / 2, 160, 0xFFB8C7D9);
        } else {
            graphics.drawString(font, t("gui.medieval_coins.market.page", pricePageIndex + 1, Math.max(1, (entries.size() + PRICE_PAGE_SIZE - 1) / PRICE_PAGE_SIZE)),
                    120, 305, 0xFFB8C7D9, false);
            graphics.drawString(font, t("gui.medieval_coins.market.owner.apply_type_price"), 180, 305, 0xFFFFFFFF, false);
            long typedPrice = parsePrice();
            long ironPerBronze = MarketMoneyService.BRONZE_COIN_VALUE;
            Component conversion = typedPrice > 0
                    ? t("gui.medieval_coins.market.owner.price_conversion", typedPrice / ironPerBronze, typedPrice % ironPerBronze)
                    : t("gui.medieval_coins.market.owner.price_conversion_rate", ironPerBronze);
            graphics.drawString(font, conversion, 360, 323, 0xFFB4ADA4, false);
        }
        graphics.drawString(font, t("gui.medieval_coins.market.owner.city", cities.get(cityIndex).cityName()), 30, 270, 0xFFF2D49B, false);
    }

    private void renderHistory(GuiGraphics graphics) {
        List<MarketTransactionData> values = filteredHistory();
        int start = historyPageIndex * 8;
        for (int i = 0; i < Math.min(8, Math.max(0, values.size() - start)); i++) {
            MarketTransactionData value = values.get(start + i);
            graphics.drawString(font, shortDate(value.createdAt()) + " | " + value.playerName() + " "
                            + ("SELL".equals(value.action()) ? "a vendu " : "a achete ") + value.quantity() + " x " + value.itemDisplayName(),
                    30, 98 + i * 22, value.pending() ? 0xFFFFD37F : 0xFFFFFFFF, false);
            graphics.drawString(font, ownerLine(value), 30, 108 + i * 22, 0xFFB8C7D9, false);
        }
        if (historyBackendUnavailable) {
            graphics.drawCenteredString(font, t("gui.medieval_coins.market.owner.history_unavailable"), imageWidth / 2, 285, 0xFFFF8080);
        } else if (values.isEmpty()) {
            graphics.drawCenteredString(font, status, imageWidth / 2, 150, 0xFFFFD37F);
        }
    }

    public boolean medievalCoins$showInventorySlots() {
        return page == Page.STOCK;
    }

    @Override
    public boolean logicalClick(double mouseX, double mouseY, int button) {
        if (page == Page.STOCK && button == 0) {
            int slot = inventorySlotAt(mouseX, mouseY);
            if (slot >= 0) {
                int menuSlot = menu.playerMarketSlots() + (slot + 27) % 36;
                if (menu.getSlot(menuSlot).hasItem()) {
                    stockSelection ^= 1L << slot;
                    updateStockSelection();
                }
                return true;
            }
        }
        if(page==Page.STOCK&&mouseX>=leftPos+STOCK_X&&mouseX<leftPos+STOCK_X+162&&mouseY>=topPos+STOCK_Y&&mouseY<topPos+STOCK_Y+108){
            selectedStockSlot=((int)(mouseY-topPos-STOCK_Y)/18)*9+(int)(mouseX-leftPos-STOCK_X)/18;
            lastStockSlot=selectedStockSlot; updateStockSelection(); status="Emplacement "+(selectedStockSlot+1)+" sélectionné";return true;
        }
        if (page == Page.PRICES) {
            int clicked = priceCardAt(mouseX, mouseY);
            if (clicked >= 0 && clicked != selectedPriceIndex) {
                selectedPriceIndex = clicked;
                init();
                return true;
            }
        }
        return super.logicalClick(mouseX, mouseY, button);
    }

    private int inventorySlotAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - PLAYER_INV_X;
        double y = mouseY - topPos - PLAYER_INV_Y;
        if (x < 0 || x >= 162 || y < 0 || y >= 76) return -1;
        int column = (int) x / 18;
        if (y >= 58) return column;
        return y < 54 ? 9 + (int) y / 18 * 9 + column : -1;
    }

    private int priceCardAt(double mouseX, double mouseY) {
        int start = pricePageIndex * PRICE_PAGE_SIZE;
        int count = Math.min(PRICE_PAGE_SIZE, Math.max(0, priceEntries().size() - start));
        for (int local = 0; local < count; local++) {
            int x = leftPos + 30 + (local % 4) * 157;
            int y = topPos + 70 + (local / 4) * 66;
            if (mouseX >= x && mouseX < x + 145 && mouseY >= y && mouseY < y + 56) return start + local;
        }
        return -1;
    }

    private int findCityIndex(String cityId, String cityName) {
        for (int index = 1; index < cities.size(); index++) {
            MarketCityData city = cities.get(index);
            if (city.cityId().equalsIgnoreCase(cityId) || city.cityName().equalsIgnoreCase(cityName)) return index;
        }
        return 0;
    }

    private ItemStack slotStack(int slot) {
        if (slot < 0 || slot >= menu.playerMarketSlots() || slot >= menu.slots.size()) return ItemStack.EMPTY;
        return menu.slots.get(slot).getItem();
    }

    private String ownerLine(MarketTransactionData value) {
        return "Total : " + MarketMoneyService.formatMoney(value.totalPrice()) + " | "
                + (value.ownerGain() < 0 ? "Cout : " : "Gain : ") + MarketMoneyService.formatMoney(Math.abs(value.ownerGain()))
                + (value.pending() ? " | En attente" : "");
    }

    private static String shortDate(String value) {
        String formatted = value == null ? "" : value.replace('T', ' ').replace("Z", "");
        return formatted.substring(0, Math.min(16, formatted.length()));
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static Component t(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private record PriceEntry(String itemId, int representativeSlot, ItemStack stack, int totalCount, long price, boolean enabled) {
    }

    private static final class MutablePriceEntry {
        private final String itemId;
        private final int representativeSlot;
        private final ItemStack stack;
        private int totalCount;
        private long price;
        private boolean enabled;

        private MutablePriceEntry(String itemId, int representativeSlot, ItemStack stack, long price, boolean enabled) {
            this.itemId = itemId;
            this.representativeSlot = representativeSlot;
            this.stack = stack;
            this.price = price;
            this.enabled = enabled;
        }

        private PriceEntry freeze() {
            return new PriceEntry(itemId, representativeSlot, stack, totalCount, price, enabled);
        }
    }
    @Override protected String economyResource(){return "market:"+menu.getData().counterId();}
}
