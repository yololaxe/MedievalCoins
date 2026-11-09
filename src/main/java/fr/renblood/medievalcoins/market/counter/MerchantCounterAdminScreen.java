package fr.renblood.medievalcoins.market.counter;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.service.MarketMoneyService;
import fr.renblood.medievalcoins.network.MerchantCounterUpdateMessage;
import fr.renblood.medievalcoins.network.MerchantCounterHistoryRequestMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;

public class MerchantCounterAdminScreen extends AbstractContainerScreen<MerchantCounterAdminMenu> implements MarketHistoryReceiver {
    private static final int SCREEN_WIDTH = 680;
    private static final int CONTENT_WIDTH = 470;
    private static final int CONTENT_OFFSET = (SCREEN_WIDTH - CONTENT_WIDTH) / 2;
    private static final String[] TYPES = {"SHOP", "CITY_MARKET", "TEMPORARY_MARKET", "GUILD_COUNTER", "PLAYER_MARKET"};
    private static final String[] OWNER_TYPES = {"ADMIN", "PLAYER", "CITY", "GUILD"};
    private final List<MarketCityData> cities = new ArrayList<>();
    private final List<CounterItemConfigData> items = new ArrayList<>();
    private boolean itemsPage, addItemPage, historyPage, historyBackendUnavailable;
    private int typeIndex, ownerTypeIndex, cityIndex, selectedItem = -1, itemOffset, referenceOffset, selectedReference = -1;
    private boolean active, buy, sell, itemBuy, itemSell, itemAuto, itemActive;
    private String itemStockMode = "UNLIMITED";
    private String status = "";
    private EditBox name, ownerId, ownerName, itemName, manualBuy, manualSell, stockQuantity, modifier, referenceSearch;
    private String referenceQuery = "";
    private String draftName, draftOwnerId, draftOwnerName;
    private List<ReferenceItemData> referenceCatalog;
    private List<ReferenceItemData> matchingReferences = List.of();
    private Button addSelectedReferenceButton;
    private String historyQuery = "", historyAction = "TOUT";
    private int historyPageIndex;
    private EditBox historySearch;
    private final List<MarketTransactionData> history = new ArrayList<>();

    public MerchantCounterAdminScreen(MerchantCounterAdminMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SCREEN_WIDTH;
        imageHeight = 350;
        MerchantCounterData data = menu.getData();
        draftName = data.name(); draftOwnerId = data.ownerId(); draftOwnerName = data.ownerName();
        typeIndex = indexOf(TYPES, data.type());
        ownerTypeIndex = indexOf(OWNER_TYPES, data.ownerType());
        cities.add(new MarketCityData("", "Aucune ville"));
        cities.addAll(data.availableCities());
        cityIndex = cityIndex(cities, data.cityId(), data.cityName());
        items.addAll(data.configuredItemsData());
        active = data.active(); buy = data.buyModeEnabled(); sell = data.sellModeEnabled();
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        if (historyPage) initHistoryPage(); else if (addItemPage) initAddItemPage(); else if (itemsPage) initItemsPage(); else initGeneralPage();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box) {
            if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
            if (box.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void initGeneralPage() {
        name = field(130, 48, 310, draftName);
        name.setResponder(value -> draftName = value);
        ownerId = field(130, 120, 310, draftOwnerId);
        ownerId.setResponder(value -> draftOwnerId = value);
        ownerName = field(130, 144, 310, draftOwnerName);
        ownerName.setResponder(value -> draftOwnerName = value);
        addButton(130, 72, 310, cityLabel(), b -> { cityIndex = (cityIndex + 1) % cities.size(); b.setMessage(Component.literal(cityLabel())); });
        addButton(130, 96, 310, TYPES[typeIndex], b -> { typeIndex = (typeIndex + 1) % TYPES.length; b.setMessage(Component.literal(TYPES[typeIndex])); });
        addButton(130, 168, 310, OWNER_TYPES[ownerTypeIndex], b -> { ownerTypeIndex = (ownerTypeIndex + 1) % OWNER_TYPES.length; b.setMessage(Component.literal(OWNER_TYPES[ownerTypeIndex])); });
        addButton(25, 210, 125, activeLabel(), b -> { active = !active; b.setMessage(Component.literal(activeLabel())); });
        addButton(172, 210, 125, buyLabel(), b -> { buy = !buy; b.setMessage(Component.literal(buyLabel())); });
        Button sellMode = addButton(320, 210, 125, sellLabel(), b -> { sell = !sell; b.setMessage(Component.literal(sellLabel())); });
        sellMode.active = !"PLAYER_MARKET".equals(TYPES[typeIndex]);
        addButton(25, 255, 150, text("gui.medieval_coins.market.admin.items", items.size()), b -> { itemsPage = true; init(); });
        addButton(185, 255, 125, text("gui.medieval_coins.market.owner.tab.history"), b -> openHistory());
        addButton(320, 255, 125, text("gui.medieval_coins.market.owner.save"), b -> save());
        addButton(320, 285, 125, text("gui.medieval_coins.market.close"), b -> onClose());
    }

    private void initHistoryPage() {
        historySearch = field(20, 45, 285, historyQuery);
        historySearch.setHint(t("gui.medieval_coins.market.admin.history_search"));
        historySearch.setResponder(value -> { historyQuery = value; historyPageIndex = 0; });
        addButton(315, 45, 130, text("gui.medieval_coins.market.owner.filter", historyAction), b -> {
            historyAction = "TOUT".equals(historyAction) ? "BUY" : "BUY".equals(historyAction) ? "SELL" : "TOUT";
            historyPageIndex = 0; init();
        });
        Button previous = addButton(20, 310, 45, "<", b -> { historyPageIndex--; init(); });
        Button next = addButton(70, 310, 45, ">", b -> { historyPageIndex++; init(); });
        previous.active = historyPageIndex > 0;
        next.active = (historyPageIndex + 1) * 10 < filteredHistory().size();
        addButton(180, 310, 120, text("gui.medieval_coins.market.owner.refresh"), b -> requestHistory());
        addButton(325, 310, 120, text("gui.medieval_coins.market.admin.back"), b -> { historyPage = false; init(); });
        setInitialFocus(historySearch);
    }

    private void openHistory() {
        historyPage = true;
        requestHistory();
        init();
    }

    private void requestHistory() {
        status = text("gui.medieval_coins.market.admin.loading");
        MedievalCoin.PACKET_HANDLER.sendToServer(new MerchantCounterHistoryRequestMessage(menu.getPos()));
    }

    @Override
    public void receiveHistory(List<MarketTransactionData> transactions, boolean backendUnavailable) {
        history.clear(); history.addAll(transactions); historyBackendUnavailable = backendUnavailable;
        status = history.isEmpty() ? text("gui.medieval_coins.market.admin.no_transactions") : "";
        init();
    }

    private List<MarketTransactionData> filteredHistory() {
        String query = historyQuery.trim().toLowerCase(java.util.Locale.ROOT);
        return history.stream().filter(value -> "TOUT".equals(historyAction) || historyAction.equals(value.action()))
                .filter(value -> query.isEmpty() || value.playerName().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || value.itemDisplayName().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || value.itemId().toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
    }

    private void initItemsPage() {
        itemOffset = Math.min(itemOffset, Math.max(0, items.size() - 1) / 7 * 7);
        for (int i = 0; i < Math.min(items.size() - itemOffset, 7); i++) {
            int index = itemOffset + i;
            addButton(20, 62 + i * 25, 190, items.get(index).displayName(), b -> selectItem(index));
        }
        Button previous = addButton(20, 238, 90, text("gui.medieval_coins.market.admin.previous"), b -> { itemOffset = Math.max(0, itemOffset - 7); init(); });
        Button next = addButton(120, 238, 90, text("gui.medieval_coins.market.admin.next"), b -> { itemOffset = Math.min(Math.max(0, items.size() - 1), itemOffset + 7); init(); });
        previous.active = itemOffset > 0;
        next.active = itemOffset + 7 < items.size();
        Button add = addButton(20, 274, 190, text("gui.medieval_coins.market.admin.add_item"), b -> {
            addItemPage = true;
            selectedReference = -1;
            init();
        });
        add.active = !"PLAYER_MARKET".equals(TYPES[typeIndex]);

        itemName = field(300, 62, 145, selectedItem >= 0 ? items.get(selectedItem).displayName() : "");
        manualBuy = field(300, 158, 145, selectedLong(CounterItemConfigData::manualBuyPrice));
        manualSell = field(300, 182, 145, selectedLong(CounterItemConfigData::manualSellPrice));
        stockQuantity = field(300, 230, 145, selectedInt(CounterItemConfigData::stockQuantity));
        modifier = field(300, 254, 145, selectedItem >= 0 ? Double.toString(items.get(selectedItem).localModifier()) : "1.0");
        addButton(230, 86, 102, itemBuyLabel(), b -> { itemBuy = !itemBuy; b.setMessage(Component.literal(itemBuyLabel())); });
        addButton(343, 86, 102, itemSellLabel(), b -> { itemSell = !itemSell; b.setMessage(Component.literal(itemSellLabel())); });
        addButton(230, 110, 215, itemAutoLabel(), b -> { itemAuto = !itemAuto; b.setMessage(Component.literal(itemAutoLabel())); });
        addButton(230, 134, 215, itemActiveLabel(), b -> { itemActive = !itemActive; b.setMessage(Component.literal(itemActiveLabel())); });
        addButton(300, 206, 145, text("gui.medieval_coins.market.admin.stock_mode", itemStockMode), b -> {
            itemStockMode = "LIMITED".equals(itemStockMode) ? "UNLIMITED" : "LIMITED";
            b.setMessage(t("gui.medieval_coins.market.admin.stock_mode", itemStockMode));
        });
        Button apply = addButton(230, 286, 102, text("gui.medieval_coins.market.owner.apply"), b -> applyItem());
        Button remove = addButton(343, 286, 102, text("gui.medieval_coins.market.admin.remove"), b -> removeItem());
        apply.active = remove.active = selectedItem >= 0;
        addButton(20, 318, 120, text("gui.medieval_coins.market.admin.back"), b -> { itemsPage = false; init(); });
        addButton(175, 318, 120, text("gui.medieval_coins.market.owner.save"), b -> save());
        addButton(325, 318, 120, text("gui.medieval_coins.market.close"), b -> onClose());
    }

    private void initAddItemPage() {
        referenceSearch = field(20, 52, 425, referenceQuery);
        referenceSearch.setHint(t("gui.medieval_coins.market.admin.reference_search"));
        referenceSearch.setResponder(value -> {
            if (!value.equals(referenceQuery)) {
                referenceQuery = value;
                referenceOffset = 0;
                selectedReference = -1;
                init();
            }
        });
        matchingReferences = searchReferences();
        List<ReferenceItemData> filtered = matchingReferences;
        for (int i = 0; i < Math.min(8, Math.max(0, filtered.size() - referenceOffset)); i++) {
            int index = referenceOffset + i;
            ReferenceItemData ref = filtered.get(index);
            String price = ref.referencePrice() == null ? "prix ?" : MarketMoneyService.formatMoney(ref.referencePrice());
            addButton(20, 82 + i * 25, 425,
                    ref.displayName() + " | " + ref.itemId() + " | " + ref.category() + " | " + price,
                    b -> {
                        selectedReference = index;
                        addSelectedReferenceButton.active = true;
                    });
        }
        Button previous = addButton(20, 286, 95, text("gui.medieval_coins.market.admin.previous"), b -> { referenceOffset = Math.max(0, referenceOffset - 8); init(); });
        Button next = addButton(125, 286, 95, text("gui.medieval_coins.market.admin.next"), b -> { referenceOffset += 8; init(); });
        previous.active = referenceOffset > 0;
        next.active = referenceOffset + 8 < filtered.size() && referenceOffset + 8 < 30;
        addSelectedReferenceButton = addButton(230, 286, 215,
                text("gui.medieval_coins.market.admin.add_selected"), b -> addSelectedReference());
        addSelectedReferenceButton.active = selectedReference >= 0 && selectedReference < filtered.size();
        addButton(20, 318, 130, text("gui.medieval_coins.market.admin.back"), b -> { addItemPage = false; init(); });
        addButton(170, 318, 130, text("gui.medieval_coins.market.admin.cancel"), b -> { addItemPage = false; selectedReference = -1; init(); });
        addButton(320, 318, 125, text("gui.medieval_coins.market.close"), b -> onClose());
        setInitialFocus(referenceSearch);
    }

    private void selectItem(int index) {
        selectedItem = index;
        CounterItemConfigData item = items.get(index);
        itemBuy = item.buyEnabled(); itemSell = item.sellEnabled(); itemAuto = item.autoPrice(); itemActive = item.active();
        itemStockMode = item.stockMode();
        init();
    }

    private void addSelectedReference() {
        List<ReferenceItemData> references = matchingReferences;
        if (selectedReference < 0 || selectedReference >= references.size() || "PLAYER_MARKET".equals(TYPES[typeIndex])) return;
        ReferenceItemData ref = references.get(selectedReference);
        for (int i = 0; i < items.size(); i++) if (items.get(i).itemId().equals(ref.itemId())) {
            status = text("gui.medieval_coins.market.admin.already_configured");
            addItemPage = false;
            selectItem(i);
            return;
        }
        items.add(new CounterItemConfigData(ref.itemId(), ref.displayName(), true, false,
                ref.referencePrice() != null && ref.referencePrice() > 0,
                null, null, "UNLIMITED", null, 1.0D, true));
        status = text(ref.referencePrice() != null && ref.referencePrice() > 0
                ? "gui.medieval_coins.market.admin.item_added" : "gui.medieval_coins.market.admin.buy_price_required");
        itemOffset = (items.size() - 1) / 7 * 7;
        addItemPage = false;
        selectItem(items.size() - 1);
    }

    private List<ReferenceItemData> searchReferences() {
        if (referenceCatalog == null) {
            var catalog = new LinkedHashMap<String, ReferenceItemData>();
            for (var entry : ForgeRegistries.ITEMS.getEntries()) {
                var item = entry.getValue();
                if (item == Items.AIR) continue;
                var id = entry.getKey().location();
                catalog.put(id.toString(), new ReferenceItemData(id.toString(),
                        item.getDescription().getString(), id.getNamespace(), null));
            }
            for (var reference : menu.getData().referenceItems()) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(reference.itemId());
                if (id != null && catalog.containsKey(id.toString())) {
                    catalog.put(id.toString(), new ReferenceItemData(id.toString(), reference.displayName(),
                            reference.category(), reference.referencePrice()));
                }
            }
            referenceCatalog = catalog.values().stream()
                    .sorted(Comparator.comparing(ReferenceItemData::displayName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(ReferenceItemData::itemId))
                    .toList();
        }
        String query = referenceQuery.trim().toLowerCase(Locale.ROOT);
        return referenceCatalog.stream()
                .filter(ref -> query.isEmpty()
                        || ref.displayName().toLowerCase(Locale.ROOT).contains(query)
                        || ref.itemId().contains(query)
                        || ref.category().toLowerCase(Locale.ROOT).contains(query))
                .limit(30).toList();
    }

    private void applyItem() {
        if (selectedItem < 0) return;
        Long buyPrice = parseLong(manualBuy.getValue());
        Long sellPrice = parseLong(manualSell.getValue());
        Integer stock = parseInt(stockQuantity.getValue());
        Double localModifier = parseDouble(modifier.getValue());
        if (!itemAuto && (buyPrice == null || buyPrice < 1L)) { status = text("gui.medieval_coins.market.admin.buy_price_required"); return; }
        if (sellPrice != null && sellPrice < 1L) { status = text("gui.medieval_coins.market.admin.sell_price_min"); return; }
        if (itemSell && !itemAuto && sellPrice == null) { status = text("gui.medieval_coins.market.admin.sell_price_required"); return; }
        if ("LIMITED".equals(itemStockMode) && (stock == null || stock < 0)) { status = text("gui.medieval_coins.market.admin.stock_required"); return; }
        CounterItemConfigData old = items.get(selectedItem);
        items.set(selectedItem, new CounterItemConfigData(old.itemId(), itemName.getValue(), itemBuy, itemSell, itemAuto,
                buyPrice, sellPrice, itemStockMode, "LIMITED".equals(itemStockMode) ? stock : null,
                localModifier == null ? 1.0D : localModifier, itemActive));
        status = text("gui.medieval_coins.market.admin.item_modified");
        init();
    }

    private void removeItem() {
        if (selectedItem < 0) return;
        items.remove(selectedItem);
        selectedItem = -1;
        status = text("gui.medieval_coins.market.admin.item_removed");
        init();
    }

    private void save() {
        if (!"PLAYER_MARKET".equals(TYPES[typeIndex])) {
            for (int index = 0; index < items.size(); index++) {
                var item = items.get(index);
                if (!item.autoPrice() && item.manualBuyPrice() == null) {
                    status = text("gui.medieval_coins.market.admin.buy_price_required");
                    itemsPage = true;
                    itemOffset = index / 7 * 7;
                    selectItem(index);
                    return;
                }
            }
        }
        MerchantCounterData old = menu.getData();
        MarketCityData city = cities.get(cityIndex);
        boolean savedSell = "PLAYER_MARKET".equals(TYPES[typeIndex]) ? false : sell;
        MerchantCounterData update = new MerchantCounterData(old.counterId(), draftName,
                TYPES[typeIndex], city.cityId(), "Aucune ville".equals(city.cityName()) ? "" : city.cityName(),
                OWNER_TYPES[ownerTypeIndex], draftOwnerId,
                draftOwnerName, active, buy, savedSell, old.position(), items.size(), old.pendingOwnerBalanceInIron(),
                old.breadPrice(), old.ironPrice(), old.woodPrice(), old.purchasableItems(), old.sellableItems(), old.availableCities(),
                List.copyOf(items), old.referenceItems(), old.playerMarketSlots());
        MedievalCoin.PACKET_HANDLER.sendToServer(new MerchantCounterUpdateMessage(menu.getPos(), true, update));
        status = "Modifications enregistrées et synchronisation lancée.";
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0161B22);
        graphics.fill(leftPos + 5, topPos + 5, leftPos + imageWidth - 5, topPos + imageHeight - 5, 0xFF283440);
        graphics.fill(leftPos + CONTENT_OFFSET - 10, topPos + 32, leftPos + CONTENT_OFFSET + CONTENT_WIDTH + 10, topPos + imageHeight - 8, 0xFF1E2933);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(CONTENT_OFFSET, 0, 0);
        graphics.drawCenteredString(font, historyPage ? t("gui.medieval_coins.market.admin.title.history") : addItemPage
                ? t("gui.medieval_coins.market.admin.title.add_item") : itemsPage
                ? t("gui.medieval_coins.market.admin.title.items") : t("gui.medieval_coins.market.admin.title.general"),
                CONTENT_WIDTH / 2, 12, 0xFFF2D49B);
        if (historyPage) {
            renderHistory(graphics);
            graphics.pose().popPose();
            return;
        }
        if (addItemPage) {
            List<ReferenceItemData> filtered = matchingReferences;
            if (filtered.size() >= 30) {
                graphics.drawCenteredString(font, t("gui.medieval_coins.market.admin.refine_search"), CONTENT_WIDTH / 2, 270, 0xFFFFD37F);
            }
            if (selectedReference >= 0 && selectedReference < filtered.size()) {
                ReferenceItemData ref = filtered.get(selectedReference);
                graphics.drawString(font, t("gui.medieval_coins.market.admin.selection", ref.displayName()), 230, 270, 0xFFF2D49B, false);
                graphics.drawString(font, ref.category() + " | " + (ref.referencePrice() == null ? text("gui.medieval_coins.market.admin.price_unavailable") : MarketMoneyService.formatMoney(ref.referencePrice())), 230, 279, 0xFFB8C7D9, false);
            }
            graphics.pose().popPose();
            return;
        }
        if (!itemsPage) {
            label(graphics, text("gui.medieval_coins.market.owner.name"), 53); label(graphics, text("gui.medieval_coins.market.admin.city"), 77); label(graphics, text("gui.medieval_coins.market.admin.type"), 101);
            label(graphics, text("gui.medieval_coins.market.admin.owner_id"), 125); label(graphics, text("gui.medieval_coins.market.admin.owner_name"), 149); label(graphics, text("gui.medieval_coins.market.admin.owner_type"), 173);
            graphics.drawString(font, t("gui.medieval_coins.market.admin.position", menu.getData().position()), 25, 238, 0xFFB8C7D9, false);
        } else {
            graphics.drawString(font, t("gui.medieval_coins.market.admin.configured_items"), 20, 44, 0xFFF2D49B, false);
            graphics.drawString(font, t("gui.medieval_coins.market.admin.edit"), 230, 44, 0xFFF2D49B, false);
            if (selectedItem >= 0) {
                CounterItemConfigData item = items.get(selectedItem);
                graphics.drawString(font, item.itemId(), 230, 51, 0xFFB8C7D9, false);
                Long buyPrice = item.autoPrice() ? currentPrice(item.itemId(), false) : item.manualBuyPrice();
                Long sellPrice = item.autoPrice() ? currentPrice(item.itemId(), true) : item.manualSellPrice();
                graphics.drawString(font, t("gui.medieval_coins.market.admin.current_buy", buyPrice == null ? text("gui.medieval_coins.market.admin.unavailable") : MarketMoneyService.formatMoney(buyPrice)), 230, 269, buyPrice == null ? 0xFFFF8080 : 0xFFF2D49B, false);
                graphics.drawString(font, t("gui.medieval_coins.market.admin.current_buyback", sellPrice == null ? text("gui.medieval_coins.market.admin.unavailable") : MarketMoneyService.formatMoney(sellPrice)), 230, 278, sellPrice == null ? 0xFFFF8080 : 0xFFF2D49B, false);
            }
            graphics.drawString(font, t("gui.medieval_coins.market.admin.display_name"), 230, 67, 0xFFFFFFFF, false);
            graphics.drawString(font, t("gui.medieval_coins.market.admin.player_buy_price"), 230, 163, 0xFFFFFFFF, false);
            graphics.drawString(font, t("gui.medieval_coins.market.admin.player_sell_price"), 230, 187, 0xFFFFFFFF, false);
            graphics.drawString(font, t("gui.medieval_coins.market.quantity"), 230, 235, 0xFFFFFFFF, false);
            graphics.drawString(font, t("gui.medieval_coins.market.admin.modifier"), 230, 259, 0xFFFFFFFF, false);
        }
        graphics.drawCenteredString(font, status, CONTENT_WIDTH / 2, itemsPage ? 337 : 292, 0xFFFFD37F);
        graphics.pose().popPose();
    }

    private Long currentPrice(String itemId, boolean selling) {
        return (selling ? fr.renblood.medievalcoins.market.service.MarketPriceService
                .getSellPrice(menu.getData().cityId(), menu.getData().type(), itemId)
                : fr.renblood.medievalcoins.market.service.MarketPriceService
                .getBuyPrice(menu.getData().cityId(), menu.getData().type(), itemId)).orElse(null);
    }

    private void renderHistory(GuiGraphics graphics) {
        List<MarketTransactionData> values = filteredHistory();
        int start = historyPageIndex * 10;
        for (int i = 0; i < Math.min(10, Math.max(0, values.size() - start)); i++) {
            MarketTransactionData value = values.get(start + i);
            int y = 75 + i * 22;
            String action = text("SELL".equals(value.action()) ? "gui.medieval_coins.market.admin.history_sell" : "gui.medieval_coins.market.admin.history_buy");
            graphics.drawString(font, shortDate(value.createdAt()) + " | " + value.playerName() + " | " + action
                    + " | " + value.quantity() + " x " + value.itemDisplayName(), 20, y,
                    value.pending() ? 0xFFFFD37F : 0xFFFFFFFF, false);
            graphics.drawString(font, "Total : " + MarketMoneyService.formatMoney(value.totalPrice()) + " | "
                    + (value.ownerGain() < 0 ? "Coût : " : "Gain : ") + MarketMoneyService.formatMoney(Math.abs(value.ownerGain()))
                    + (value.pending() ? " | En attente de synchronisation" : ""), 20, y + 10, 0xFFB8C7D9, false);
        }
        if (historyBackendUnavailable) graphics.drawCenteredString(font,
                t("gui.medieval_coins.market.admin.history_unavailable"), CONTENT_WIDTH / 2, 292, 0xFFFF8080);
        else if (values.isEmpty()) graphics.drawCenteredString(font, status, CONTENT_WIDTH / 2, 180, 0xFFFFD37F);
    }

    private static String shortDate(String value) {
        String formatted = value == null ? "" : value.replace('T', ' ').replace("Z", "");
        return formatted.substring(0, Math.min(16, formatted.length()));
    }

    private EditBox field(int x, int y, int width, String value) {
        EditBox box = addRenderableWidget(new EditBox(font, leftPos + CONTENT_OFFSET + x, topPos + y, width, 18, Component.empty()));
        box.setValue(value == null ? "" : value); return box;
    }
    private Button addButton(int x, int y, int width, String text, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(text), action).bounds(leftPos + CONTENT_OFFSET + x, topPos + y, width, 20).build());
    }
    private void label(GuiGraphics graphics, String text, int y) { graphics.drawString(font, text, 25, y, 0xFFFFFFFF, false); }
    private String cityLabel() { return cities.get(cityIndex).cityName(); }
    private String activeLabel() { return text("gui.medieval_coins.market.admin.active", yes(active)); }
    private String buyLabel() { return text("gui.medieval_coins.market.admin.player_purchase", yes(buy)); }
    private String sellLabel() { return text("gui.medieval_coins.market.admin.player_sale", yes(sell)); }
    private String itemBuyLabel() { return text("gui.medieval_coins.market.admin.player_purchase", yes(itemBuy)); }
    private String itemSellLabel() { return text("gui.medieval_coins.market.admin.player_sale", yes(itemSell)); }
    private String itemAutoLabel() { return text("gui.medieval_coins.market.admin.auto_price", yes(itemAuto)); }
    private String itemActiveLabel() { return text("gui.medieval_coins.market.admin.item_active", yes(itemActive)); }
    private String selectedLong(java.util.function.Function<CounterItemConfigData, Long> fn) { return selectedItem < 0 || fn.apply(items.get(selectedItem)) == null ? "" : fn.apply(items.get(selectedItem)).toString(); }
    private String selectedInt(java.util.function.Function<CounterItemConfigData, Integer> fn) { return selectedItem < 0 || fn.apply(items.get(selectedItem)) == null ? "" : fn.apply(items.get(selectedItem)).toString(); }
    private static Long parseLong(String value) { try { return value.isBlank() ? null : Long.parseLong(value); } catch (Exception e) { return null; } }
    private static Integer parseInt(String value) { try { return value.isBlank() ? null : Integer.parseInt(value); } catch (Exception e) { return null; } }
    private static Double parseDouble(String value) { try { return value.isBlank() ? null : Double.parseDouble(value); } catch (Exception e) { return null; } }
    private static int indexOf(String[] values, String value) { for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i; return 0; }
    private static int cityIndex(List<MarketCityData> cities, String id, String name) { for (int i = 1; i < cities.size(); i++) if (cities.get(i).cityId().equalsIgnoreCase(id) || cities.get(i).cityName().equalsIgnoreCase(name)) return i; return 0; }
    private static String yes(boolean value) { return text(value ? "gui.medieval_coins.market.yes" : "gui.medieval_coins.market.no"); }
    private static Component t(String key, Object... args) { return Component.translatable(key, args); }
    private static String text(String key, Object... args) { return t(key, args).getString(); }
}
