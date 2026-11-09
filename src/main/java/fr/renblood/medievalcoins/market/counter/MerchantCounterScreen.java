package fr.renblood.medievalcoins.market.counter;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.market.service.MarketMoneyService;
import fr.renblood.medievalcoins.network.MerchantCounterPurchaseMessage;
import fr.renblood.medievalcoins.network.MerchantCounterSellMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import fr.renblood.medievalcoins.client.economy.AdaptiveEconomyScreen;
import fr.renblood.medievalcoins.client.economy.EconomyUi;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

public class MerchantCounterScreen extends AdaptiveEconomyScreen<MerchantCounterMenu> {
    private static final int PAGE_SIZE = 8;
    private static final int WIDTH = 680;
    private static final int HEIGHT = 350;
    private EditBox search;
    private EditBox quantity;
    private String query = "";
    private int page;
    private int selected;
    private boolean selling;

    public MerchantCounterScreen(MerchantCounterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        List<MerchantCounterItemData> filtered = filteredItems();
        int maxPage = Math.max(0, (filtered.size() - 1) / PAGE_SIZE);
        page = Math.min(page, maxPage);
        selected = Math.min(selected, Math.max(0, filtered.size() - 1));

        if (menu.getData().type().equals("PLAYER_MARKET")) selling = false;
        Button buyTab = addRenderableWidget(Button.builder(t("gui.medieval_coins.market.buy"), b -> switchMode(false))
                .bounds(leftPos + 30, topPos + 42, menu.getData().type().equals("PLAYER_MARKET") ? 390 : 190, 20).build());
        buyTab.active = selling;
        if (!menu.getData().type().equals("PLAYER_MARKET")) {
            Button sellTab = addRenderableWidget(Button.builder(t("gui.medieval_coins.market.sell"), b -> switchMode(true))
                    .bounds(leftPos + 230, topPos + 42, 190, 20).build());
            sellTab.active = !selling && menu.getData().sellModeEnabled();
        }

        search = addRenderableWidget(new EditBox(font, leftPos + 30, topPos + 70, 390, 20, t("gui.medieval_coins.market.search")));
        search.setHint(t("gui.medieval_coins.market.search_hint"));
        search.setValue(query);
        search.setResponder(value -> {
            if (!value.equals(query)) {
                query = value;
                page = 0;
                selected = 0;
                init();
            }
        });

        int start = page * PAGE_SIZE;
        for (int i = 0; i < Math.min(PAGE_SIZE, filtered.size() - start); i++) {
            int index = start + i;
            MerchantCounterItemData item = filtered.get(index);
            String detail = selling ? " | " + t("gui.medieval_coins.market.owned", ownedClean(item.itemId())).getString()
                    : item.limitedStock() ? " | " + t("gui.medieval_coins.market.stock", item.stockQuantity()).getString() : "";
            addRenderableWidget(Button.builder(Component.literal(item.displayName() + " | "
                            + MarketMoneyService.formatMoney(item.unitPrice()) + detail), b -> {
                        selected = index;
                        init();
                    }).bounds(leftPos + 30, topPos + 98 + i * 25, 300, 21).build());
            addRenderableWidget(Button.builder(Component.literal("Choisir"),b->{selected=index;init();})
                    .bounds(leftPos + 340, topPos + 98 + i * 25, 80, 21).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            selected = Math.max(0, page * PAGE_SIZE);
            init();
        }).bounds(leftPos + 30, topPos + 310, 35, 20).build());
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            selected = page * PAGE_SIZE;
            init();
        }).bounds(leftPos + 385, topPos + 310, 35, 20).build());
        previous.active = page > 0;
        next.active = page < maxPage;

        quantity = addRenderableWidget(new EditBox(font, leftPos + 526, topPos + 184, 60, 20, t("gui.medieval_coins.market.quantity")));
        quantity.setValue("1");
        quantity.setFilter(value -> value.isEmpty() || value.matches("\\d{0,3}"));
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeQuantity(-1))
                .bounds(leftPos + 490, topPos + 184, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeQuantity(1))
                .bounds(leftPos + 594, topPos + 184, 28, 20).build());
        addRenderableWidget(Button.builder(selling ? t("gui.medieval_coins.market.sell_selected") : t("gui.medieval_coins.market.buy_selected"), b -> {
            if (!filtered.isEmpty()) transact(filtered.get(selected));
        }).bounds(leftPos + 470, topPos + 240, 172, 22).build());
        addRenderableWidget(Button.builder(t("gui.medieval_coins.market.close"), b -> onClose())
                .bounds(leftPos + 500, topPos + 294, 112, 20).build());
        for(int i=0;i<3;i++){
            final int amount=new int[]{1,16,64}[i];
            addRenderableWidget(Button.builder(Component.literal(Integer.toString(amount)),b->{int maximum=filtered.isEmpty()?64:maxQuantity(filtered.get(selected));quantity.setValue(Integer.toString(Math.min(amount,maximum)));}).bounds(leftPos+470+i*56,topPos+267,50,18).build());
        }
        setInitialFocus(search);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box) {
            if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
            if (box.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void transact(MerchantCounterItemData item) {
        if(EconomyUi.pending())return;
        if(!selling&&item.limitedStock()&&item.stockQuantity()<1)return;
        fr.renblood.medievalcoins.client.economy.EconomyHud.localPending();
        int count = boundedQuantity(item);
        if (count < 1) return;
        if (selling) MedievalCoin.PACKET_HANDLER.sendToServer(new MerchantCounterSellMessage(menu.getPos(), item.itemId(), count, item.unitPrice()));
        else MedievalCoin.PACKET_HANDLER.sendToServer(new MerchantCounterPurchaseMessage(menu.getPos(), item.purchaseKey(), count, item.unitPrice()));
    }

    private void switchMode(boolean value) {
        selling = value;
        query = "";
        page = 0;
        selected = 0;
        init();
    }

    private void changeQuantity(int change) {
        List<MerchantCounterItemData> filtered = filteredItems();
        int max = filtered.isEmpty() ? 64 : maxQuantity(filtered.get(selected));
        quantity.setValue(Integer.toString(Math.max(1, Math.min(max, parseQuantity() + change))));
    }

    private int boundedQuantity(MerchantCounterItemData item) {
        return Math.max(1, Math.min(maxQuantity(item), parseQuantity()));
    }

    private int maxQuantity(MerchantCounterItemData item) {
        if (selling) return Math.max(1, Math.min(64, ownedClean(item.itemId())));
        return item.limitedStock() ? Math.max(1, item.stockQuantity()) : 64;
    }

    private List<MerchantCounterItemData> cachedItems=List.of();
    private String cachedQuery="";private boolean cachedSelling;private long cachedAt;
    private List<MerchantCounterItemData> filteredItems() {
        long now=net.minecraft.Util.getMillis();
        if(now-cachedAt<250&&cachedQuery.equals(query)&&cachedSelling==selling)return cachedItems;
        cachedAt=now;cachedQuery=query;cachedSelling=selling;
        String filter = query.trim().toLowerCase(Locale.ROOT);
        List<MerchantCounterItemData> source = selling ? menu.getData().sellableItems() : menu.getData().purchasableItems();
        cachedItems=source.stream()
                .filter(item -> !selling || ownedClean(item.itemId()) > 0)
                .filter(item -> filter.isEmpty() || item.displayName().toLowerCase(Locale.ROOT).contains(filter)
                        || item.itemId().toLowerCase(Locale.ROOT).contains(filter))
                .toList();
        return cachedItems;
    }

    @Override
    public void renderLogical(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.renderLogical(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF015100D);
        graphics.fill(leftPos + 5, topPos + 5, leftPos + imageWidth - 5, topPos + imageHeight - 5, 0xFF342319);
        graphics.fill(leftPos + 450, topPos + 60, leftPos + 660, topPos + 310, 0xFF241A15);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MerchantCounterData data = menu.getData();
        graphics.drawCenteredString(font, data.name(), imageWidth / 2, 12, 0xFFF2D49B);
        graphics.drawCenteredString(font, display(data.cityName(), data.cityId()) + " | " + data.type(), imageWidth / 2, 27, 0xFFB9A995);
        graphics.drawString(font, selling ? t("gui.medieval_coins.market.your_sale") : t("gui.medieval_coins.market.your_purchase"), 470, 50, 0xFFF2D49B, false);
        List<MerchantCounterItemData> filtered = filteredItems();
        if (filtered.isEmpty()) {
            Component message = selling && !data.sellModeEnabled() ? t("gui.medieval_coins.market.no_buyback")
                    : selling ? t("gui.medieval_coins.market.no_accepted_items") : t("gui.medieval_coins.market.no_results");
            graphics.drawString(font, message, 30, 105, 0xFFFF8080, false);
            return;
        }
        MerchantCounterItemData item = filtered.get(Math.min(selected, filtered.size() - 1));
        graphics.drawString(font, item.displayName(), 470, 75, 0xFFFFFFFF, false);
        graphics.drawString(font, trim(item.itemId(), 26), 470, 88, 0xFFB9A995, false);
        graphics.drawString(font, selling ? t("gui.medieval_coins.market.buyback_price", MarketMoneyService.formatMoney(item.unitPrice()))
                : t("gui.medieval_coins.market.price", MarketMoneyService.formatMoney(item.unitPrice())), 470, 115, 0xFFF2D49B, false);
        graphics.drawString(font, selling ? t("gui.medieval_coins.market.owned", ownedClean(item.itemId()))
                : item.limitedStock() ? t("gui.medieval_coins.market.stock", item.stockQuantity()) : t("gui.medieval_coins.market.unlimited_stock"), 470, 135, 0xFFFFFFFF, false);
        graphics.drawString(font, selling ? t("gui.medieval_coins.market.coin_payment")
                : t("gui.medieval_coins.market.balance", MarketMoneyService.formatMoney(clientBalance())), 470, 155, 0xFFFFFFFF, false);
        graphics.drawString(font, t("gui.medieval_coins.market.quantity"), 470, 174, 0xFFB9A995, false);
        long total;
        try{total=Math.multiplyExact(item.unitPrice(),boundedQuantity(item));}catch(ArithmeticException overflow){graphics.drawString(font,"Montant hors limites",470,218,EconomyUi.ERROR,false);return;}
        graphics.drawString(font, selling ? t("gui.medieval_coins.market.total_received", MarketMoneyService.formatMoney(total))
                : t("gui.medieval_coins.market.total", MarketMoneyService.formatMoney(total)), 470, 218, 0xFFF2D49B, false);
        graphics.drawString(font, t("gui.medieval_coins.market.page", page + 1, Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE)),
                170, 316, 0xFFB9A995, false);
    }

    private long clientBalance() {
        Inventory inventory = minecraft.player.getInventory();
        return inventory.items.stream().filter(stack->!stack.hasTag()).mapToLong(stack->{
            long value=stack.is(Coins.IRON_COIN.get())?1:stack.is(Coins.BRONZE_COIN.get())?64:stack.is(Coins.SILVER_COIN.get())?4096:stack.is(Coins.GOLD_COIN.get())?262144:0;
            return value*stack.getCount();}).sum();
    }

    private int ownedClean(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || minecraft == null || minecraft.player == null) return 0;
        return minecraft.player.getInventory().items.stream()
                .filter(stack -> stack.is(item) && !stack.hasTag()).mapToInt(ItemStack::getCount).sum();
    }

    private int parseQuantity() {
        try {
            return Integer.parseInt(quantity.getValue());
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static Component t(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String display(String name, String id) {
        return !name.isBlank() ? name : !id.isBlank() ? id : Component.translatable("gui.medieval_coins.market.undefined_city").getString();
    }
    @Override protected String economyResource(){return "market:"+menu.getData().counterId();}
}
