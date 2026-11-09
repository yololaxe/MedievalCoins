package fr.renblood.medievalcoins.market.counter;

import fr.renblood.medievalcoins.init.BlockEntityInit;
import fr.renblood.medievalcoins.market.config.MarketConfig;
import fr.renblood.medievalcoins.market.model.MarketPriceDto;
import fr.renblood.medievalcoins.market.model.MerchantCounterDto;
import fr.renblood.medievalcoins.market.service.MarketPriceService;
import fr.renblood.medievalcoins.market.service.MerchantCounterLocalStore;
import fr.renblood.medievalcoins.market.service.MerchantCounterRegistry;
import fr.renblood.medievalcoins.market.service.MerchantCounterSyncService;
import fr.renblood.medievalcoins.market.service.PlayerMarketSecurityService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.world.item.ItemStack;

public class MerchantCounterBlockEntity extends BlockEntity {
    public static final int PLAYER_MARKET_SLOTS = 54;
    private String counterId = UUID.randomUUID().toString();
    private String name = "Comptoir marchand";
    private String type = "PLAYER_MARKET";
    private String cityId = "";
    private String cityName = "";
    private String ownerType = "PLAYER";
    private String ownerId = "";
    private String ownerName = "";
    private boolean active = true;
    private boolean buyModeEnabled = true;
    private boolean sellModeEnabled = false;
    private boolean autoPrice = true;
    private boolean pendingSync;
    private boolean pendingCreate;
    private String stockMode = "UNLIMITED";
    private final Map<String, Integer> stockQuantities = new HashMap<>();
    private final Map<String, CounterItemConfigData> configuredItems = new java.util.LinkedHashMap<>();
    private final java.util.Set<String> removedItemIds = new java.util.LinkedHashSet<>();
    private boolean itemsPendingSync;
    private long syncRevision;

    public long getSyncRevision() { return syncRevision; }
    private long pendingOwnerBalanceInIron;
    private boolean commerceStockMirror=true;
    private final Map<Integer,Integer> commerceStock=new HashMap<>();
    private final Map<Integer, PlayerMarketSlotData> commerceHolds = new HashMap<>();

    public int getReservedStock(int slot) { return commerceHolds.containsKey(slot) ? commerceHolds.get(slot).reserved() : 0; }
    public int getIncomingStock(int slot) { return commerceHolds.containsKey(slot) ? commerceHolds.get(slot).incoming() : 0; }
    public boolean hasStockConflict(int slot) { return commerceHolds.containsKey(slot) && commerceHolds.get(slot).conflict(); }

    public boolean setCommerceHolds(int slot, int reserved, int incoming, String receipt, boolean conflict) {
        var previous = commerceHolds.get(slot);
        var holds = new PlayerMarketSlotData(slot, ItemStack.EMPTY, 0, false, reserved, incoming, receipt, conflict);
        if (holds.equals(previous) || previous == null && reserved == 0 && incoming == 0 && receipt.isEmpty() && !conflict) return false;
        if (reserved == 0 && incoming == 0 && receipt.isEmpty() && !conflict) commerceHolds.remove(slot);
        else commerceHolds.put(slot, holds);
        return true;
    }
    public boolean isCommerceStockMirror(){return commerceStockMirror;}
    public int getCommerceStock(int slot){return commerceStock.getOrDefault(slot,0);}
    public void setCommerceSlot(int slot,ItemStack template,int stock){
        if(slot<0||slot>=PLAYER_MARKET_SLOTS||!commerceStockMirror)return;
        ItemStack previous = playerMarketInventory.getStackInSlot(slot);
        if (stock > 0 && !previous.isEmpty() && !ItemStack.isSameItemSameTags(previous, template)) {
            playerSlotPrices.remove(slot);
            playerEnabledSlots.remove(slot);
        }
        template=template.copy();template.setCount(1);playerMarketInventory.setStackInSlot(slot,template);
        commerceStock.put(slot, stock);
        setChanged();
    }
    public void setCommerceOfficialStock(String itemId,int stock){
        CounterItemConfigData item=configuredItems.get(itemId);
        if(item==null||!"LIMITED".equals(item.stockMode()))return;
        configuredItems.put(itemId,new CounterItemConfigData(item.itemId(),item.displayName(),item.buyEnabled(),
            item.sellEnabled(),item.autoPrice(),item.manualBuyPrice(),item.manualSellPrice(),item.stockMode(),
            stock,item.localModifier(),item.active()));changed();
    }
    private final Map<Integer, Long> playerSlotPrices = new HashMap<>();
    private final java.util.Set<Integer> playerEnabledSlots = new java.util.HashSet<>();
    private final java.util.Set<Integer> transactionLockedSlots = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ItemStackHandler playerMarketInventory = new ItemStackHandler(PLAYER_MARKET_SLOTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            super.deserializeNBT(nbt);
            if (getSlots() >= PLAYER_MARKET_SLOTS) return;
            java.util.ArrayList<ItemStack> previous = new java.util.ArrayList<>();
            for (int slot = 0; slot < getSlots(); slot++) previous.add(getStackInSlot(slot).copy());
            setSize(PLAYER_MARKET_SLOTS);
            for (int slot = 0; slot < previous.size(); slot++) setStackInSlot(slot, previous.get(slot));
        }
    };
    private boolean transactionInProgress;
    private long transactionRevision;
    private long lastOwnerInteractionAt = System.currentTimeMillis();
    private long lastTransactionAt = System.currentTimeMillis();

    public MerchantCounterBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityInit.MERCHANT_COUNTER.get(), pos, state);
    }

    public void assignOwner(UUID id, String playerName) {
        if (!ownerId.isBlank()) return;
        ownerType = "PLAYER";
        ownerId = id.toString();
        ownerName = playerName;
        type = "PLAYER_MARKET";
        autoPrice = false;
        buyModeEnabled = true;
        sellModeEnabled = false;
        active = !MarketConfig.load().requireAdminApprovalForPlayerMarkets;
        touchOwnerInteraction();
        changed();
    }

    public void assignAdmin(String adminName) {
        if (!ownerId.isBlank()) return;
        ownerType = "ADMIN";
        ownerId = "server";
        ownerName = clean(adminName, "Administration");
        type = "SHOP";
        autoPrice = true;
        buyModeEnabled = true;
        sellModeEnabled = true;
        active = true;
        touchOwnerInteraction();
        changed();
    }

    public boolean isOwner(UUID id) {
        return "PLAYER".equals(ownerType) && ownerId.equals(id.toString());
    }

    public MerchantCounterData snapshot() {
        List<MarketPriceDto> cityPrices = MarketPriceService.getPricesForCity(cityId, cityName);
        return new MerchantCounterData(counterId, name, type, cityId, cityName, ownerType, ownerId, ownerName,
                active, buyModeEnabled, sellModeEnabled, worldPosition.toShortString(),
                isPlayerMarket() ? playerMarketItems().size() : configuredItems.size(), pendingOwnerBalanceInIron,
                findPrice(cityPrices, "bread", true), findPrice(cityPrices, "iron", false),
                findPrice(cityPrices, "wood", false), purchasableItems(), sellableItems(), MarketPriceService.getAvailableCities(),
                List.copyOf(configuredItems.values()), MarketPriceService.getReferenceItems(), playerMarketSlots());
    }

    private List<PlayerMarketSlotData> playerMarketSlots() {
        java.util.ArrayList<PlayerMarketSlotData> result=new java.util.ArrayList<>();
        for(int slot=0;slot<PLAYER_MARKET_SLOTS;slot++){
            ItemStack display=playerMarketInventory.getStackInSlot(slot).copy();
            if(commerceStockMirror)display.setCount(getCommerceStock(slot));
            var holds = commerceHolds.get(slot);
            result.add(new PlayerMarketSlotData(slot, display, getPlayerSlotPrice(slot), isPlayerSlotEnabled(slot),
                    getReservedStock(slot), getIncomingStock(slot), holds == null ? "" : holds.receipt(), hasStockConflict(slot)));
        }return result;
    }

    public List<MerchantCounterItemData> purchasableItems() {
        if (!active || !buyModeEnabled) return List.of();
        if (isPlayerMarket()) return playerMarketItems();
        return configuredItems.values().stream()
                .filter(item -> item.active() && item.buyEnabled())
                .filter(item -> ResourceLocation.tryParse(item.itemId()) != null
                        && ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(item.itemId())))
                .map(item -> new MerchantCounterItemData(item.itemId(), item.itemId(), item.displayName(), resolvedBuyPrice(item),
                        item.stockQuantity() == null ? 0 : item.stockQuantity(), "LIMITED".equals(item.stockMode())))
                .filter(item -> item.unitPrice() >= 1L)
                .toList();
    }

    private List<MerchantCounterItemData> playerMarketItems() {
        java.util.ArrayList<MerchantCounterItemData> result = new java.util.ArrayList<>();
        for (int slot = 0; slot < PLAYER_MARKET_SLOTS; slot++) {
            ItemStack stack = playerMarketInventory.getStackInSlot(slot);
            Long price = playerSlotPrices.get(slot);
            if (stack.isEmpty() || price == null || price < 1 || !playerEnabledSlots.contains(slot) || hasStockConflict(slot)) continue;
            int stock = commerceStockMirror ? getCommerceStock(slot) : stack.getCount();
            if (stock <= 0) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null) result.add(new MerchantCounterItemData("player_slot:" + slot, id.toString(),
                    stack.getHoverName().getString(), price, stock, true));
        }
        return result;
    }

    public List<MerchantCounterItemData> sellableItems() {
        if (!active || !sellModeEnabled || !isOfficial()) return List.of();
        return configuredItems.values().stream()
                .filter(item -> item.active() && item.sellEnabled())
                .filter(item -> ResourceLocation.tryParse(item.itemId()) != null
                        && ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(item.itemId())))
                .map(item -> new MerchantCounterItemData(item.itemId(), item.itemId(), item.displayName(), resolvedSellPrice(item),
                        item.stockQuantity() == null ? 0 : item.stockQuantity(), "LIMITED".equals(item.stockMode())))
                .filter(item -> item.unitPrice() >= 1L)
                .toList();
    }

    private long resolvedBuyPrice(CounterItemConfigData item) {
        if (!item.autoPrice()) return item.manualBuyPrice() == null ? 0L : item.manualBuyPrice();
        return MarketPriceService.getBuyPrice(cityId, type, item.itemId())
                .map(price -> Math.max(1L, Math.round(price * Math.max(0.01D, item.localModifier()))))
                .orElse(0L);
    }

    private long resolvedSellPrice(CounterItemConfigData item) {
        if (!item.autoPrice()) return item.manualSellPrice() == null ? 0L : item.manualSellPrice();
        return MarketPriceService.getSellPrice(cityId, type, item.itemId())
                .map(price -> Math.max(1L, Math.round(price * Math.max(0.01D, item.localModifier()))))
                .orElse(0L);
    }

    private static Long findPrice(List<MarketPriceDto> prices, String item, boolean bread) {
        for (MarketPriceDto price : prices) {
            String itemId = value(price.itemId);
            if (item.equalsIgnoreCase(itemId) || itemId.toLowerCase().endsWith(":" + item)) {
                return price.buyPrice != null ? price.buyPrice
                        : price.calculatedPrice != null ? price.calculatedPrice : price.sellPrice;
            }
            Integer legacy = bread ? price.bread : "iron".equals(item) ? price.iron : price.wood;
            if (legacy != null) return legacy.longValue();
        }
        return null;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public void applyOwnerUpdate(String name, boolean active, boolean sellModeEnabled, String cityId, String cityName) {
        this.name = clean(name, "Comptoir marchand");
        this.active = active;
        this.sellModeEnabled = isPlayerMarket() ? false : sellModeEnabled;
        for (MarketCityData city : MarketPriceService.getAvailableCities()) {
            if (city.cityId().equalsIgnoreCase(cityId) || city.cityName().equalsIgnoreCase(cityName)) {
                this.cityId = city.cityId();
                this.cityName = city.cityName();
                break;
            }
        }
        touchOwnerInteraction();
        changed();
        MerchantCounterSyncService.counterModified(this);
    }

    public void applyAdminUpdate(MerchantCounterData data) {
        name = clean(data.name(), "Comptoir marchand");
        type = allowed(data.type(), new String[]{"SHOP", "CITY_MARKET", "TEMPORARY_MARKET", "GUILD_COUNTER", "PLAYER_MARKET"}, "PLAYER_MARKET");
        cityId = clean(data.cityId(), "");
        cityName = clean(data.cityName(), "");
        ownerType = allowed(data.ownerType(), new String[]{"ADMIN", "PLAYER", "CITY", "GUILD"}, "ADMIN");
        ownerId = clean(data.ownerId(), "");
        ownerName = clean(data.ownerName(), "");
        active = data.active();
        buyModeEnabled = data.buyModeEnabled();
        sellModeEnabled = !"PLAYER_MARKET".equals(type) && data.sellModeEnabled();
        if ("PLAYER_MARKET".equals(type)) autoPrice = false;
        applyConfiguredItems(data.configuredItemsData());
        touchOwnerInteraction();
        changed();
        MerchantCounterSyncService.counterModified(this);
        MerchantCounterSyncService.itemsModified(this);
    }

    private void applyConfiguredItems(List<CounterItemConfigData> items) {
        java.util.Set<String> previous = new java.util.LinkedHashSet<>(configuredItems.keySet());
        configuredItems.clear();
        if (!"PLAYER_MARKET".equals(type)) {
            for (CounterItemConfigData item : items) {
                CounterItemConfigData clean = cleanItem(item);
                if (clean != null) configuredItems.put(clean.itemId(), clean);
            }
        }
        previous.removeAll(configuredItems.keySet());
        removedItemIds.addAll(previous);
        removedItemIds.removeAll(configuredItems.keySet());
        itemsPendingSync = true;
    }

    private CounterItemConfigData cleanItem(CounterItemConfigData item) {
        if (item == null || ResourceLocation.tryParse(item.itemId()) == null) return null;
        if (!item.autoPrice() && (item.manualBuyPrice() == null || item.manualBuyPrice() < 1L)) return null;
        if (item.manualSellPrice() != null && item.manualSellPrice() < 1L) return null;
        if (item.sellEnabled() && !item.autoPrice() && item.manualSellPrice() == null) return null;
        if ("LIMITED".equals(item.stockMode()) && (item.stockQuantity() == null || item.stockQuantity() < 0)) return null;
        return new CounterItemConfigData(item.itemId(), clean(item.displayName(), item.itemId()), item.buyEnabled(),
                item.sellEnabled(), item.autoPrice(), item.manualBuyPrice(), item.manualSellPrice(),
                "LIMITED".equals(item.stockMode()) ? "LIMITED" : "UNLIMITED",
                "LIMITED".equals(item.stockMode()) ? item.stockQuantity() : null,
                Math.max(0.01D, item.localModifier()), item.active());
    }

    private void changed() {
        syncRevision++;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        if (level != null && !level.isClientSide) {
            MerchantCounterLocalStore.remember(toDto(), pendingSync, pendingCreate);
        }
    }

    public void markPending(boolean create) {
        pendingSync = true;
        pendingCreate = create || pendingCreate;
        changed();
    }

    public void applyBackendSynced(MerchantCounterDto data) {
        String oldId = counterId;
        apply(data);
        pendingSync = false;
        pendingCreate = false;
        changed();
        if (!oldId.equals(counterId)) MerchantCounterRegistry.idChanged(oldId, this);
        MerchantCounterLocalStore.markSynced(oldId, toDto());
    }

    public void applyUploadSynced(MerchantCounterDto data, MerchantCounterDto sent, boolean created) {
        String oldId = counterId;
        if (created) {
            if (data.counterId != null && !data.counterId.isBlank()) counterId = data.counterId;
            pendingCreate = false;
        }
        if (sameConfiguration(toDto(), sent)) {
            applyBackendSynced(data);
        } else {
            // The acknowledgement belongs to an older edit. Keep the newer draft dirty.
            pendingSync = true;
            changed();
        }
        if (!oldId.equals(counterId)) {
            MerchantCounterRegistry.idChanged(oldId, this);
            MerchantCounterLocalStore.remove(oldId);
        }
    }

    public void applyBackendValue(MerchantCounterDto data) {
        apply(data);
        changed();
    }

    private void apply(MerchantCounterDto data) {
        if (data.counterId != null && !data.counterId.isBlank()) counterId = data.counterId;
        name = clean(data.name, name);
        type = allowed(data.type, new String[]{"SHOP", "CITY_MARKET", "TEMPORARY_MARKET", "GUILD_COUNTER", "PLAYER_MARKET"}, type);
        cityId = clean(data.cityId, "");
        cityName = clean(data.cityName, "");
        ownerType = allowed(data.ownerType, new String[]{"ADMIN", "PLAYER", "CITY", "GUILD"}, ownerType);
        ownerId = clean(data.ownerId, "");
        ownerName = clean(data.ownerName, "");
        active = data.active;
        buyModeEnabled = data.buyModeEnabled;
        sellModeEnabled = data.sellModeEnabled;
        autoPrice = data.autoPrice;
        if ("PLAYER_MARKET".equals(type)) autoPrice = false;
        if (data.lastOwnerInteractionAt != null && data.lastOwnerInteractionAt > 0) lastOwnerInteractionAt = data.lastOwnerInteractionAt;
        if (data.lastTransactionAt != null && data.lastTransactionAt > 0) lastTransactionAt = data.lastTransactionAt;
    }

    public MerchantCounterDto toDto() {
        MerchantCounterDto data = new MerchantCounterDto();
        data.counterId = counterId;
        data.name = name;
        data.type = type;
        data.cityId = cityId;
        data.cityName = cityName;
        data.ownerType = ownerType;
        data.ownerId = ownerId;
        data.ownerName = ownerName;
        data.active = active;
        data.buyModeEnabled = buyModeEnabled;
        data.sellModeEnabled = sellModeEnabled;
        data.autoPrice = isPlayerMarket() ? false : autoPrice;
        data.dimension = level == null ? "" : level.dimension().location().toString();
        data.x = worldPosition.getX();
        data.y = worldPosition.getY();
        data.z = worldPosition.getZ();
        data.lastOwnerInteractionAt = lastOwnerInteractionAt;
        data.lastTransactionAt = lastTransactionAt;
        return data;
    }

    public String getCounterId() { return counterId; }
    public String getCityId() { return cityId; }
    public String getCityName() { return cityName; }
    public String getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public String getCounterType() { return type; }
    public boolean isAutoPrice() { return autoPrice; }
    public boolean isPendingSync() { return pendingSync; }
    public boolean isPendingCreate() { return pendingCreate; }
    public boolean isOfficial() { return !"PLAYER_MARKET".equals(type); }
    public boolean isPlayerMarket() { return "PLAYER_MARKET".equals(type); }
    public boolean isActive() { return active; }
    public boolean isBuyModeEnabled() { return buyModeEnabled; }
    public boolean isSellModeEnabled() { return sellModeEnabled; }
    public boolean hasOwner() { return ownerId != null && !ownerId.isBlank(); }
    public ItemStackHandler getPlayerMarketInventory() { return playerMarketInventory; }
    public long getPendingOwnerBalanceInIron() { return pendingOwnerBalanceInIron; }
    public long getLastOwnerInteractionAt() { return lastOwnerInteractionAt; }
    public long getLastTransactionAt() { return lastTransactionAt; }
    public long getPlayerSlotPrice(int slot) { return playerSlotPrices.getOrDefault(slot, 0L); }
    public boolean isPlayerSlotEnabled(int slot) { return playerEnabledSlots.contains(slot); }
    public boolean isTransactionInProgress() { return transactionInProgress; }
    public long getTransactionRevision() { return transactionRevision; }
    public boolean beginTransaction(int slot) {
        if (slot < -1 || slot >= PLAYER_MARKET_SLOTS) return false;
        synchronized (this) {
            if (transactionInProgress || slot >= 0 && transactionLockedSlots.contains(slot)) return false;
            transactionInProgress = true;
            transactionRevision++;
            if (slot >= 0) transactionLockedSlots.add(slot);
            return true;
        }
    }
    public void endTransaction(int slot) {
        synchronized (this) {
            transactionInProgress = false;
            transactionRevision++;
            if (slot >= 0) transactionLockedSlots.remove(slot);
        }
    }
    public void touchOwnerInteraction() {
        lastOwnerInteractionAt = System.currentTimeMillis();
    }
    public void touchTransaction() {
        lastTransactionAt = System.currentTimeMillis();
    }
    public void configurePlayerSlot(int slot, long price, boolean enabled) {
        if (!isPlayerMarket() || slot < 0 || slot >= PLAYER_MARKET_SLOTS) return;
        ItemStack stack = playerMarketInventory.getStackInSlot(slot);
        PlayerMarketSecurityService.ValidationResult validation = PlayerMarketSecurityService.validateSaleConfiguration(this, slot, stack, price);
        if (!validation.allowed()) return;
        if (price >= 1) playerSlotPrices.put(slot, price); else playerSlotPrices.remove(slot);
        if (enabled && price >= 1 && !playerMarketInventory.getStackInSlot(slot).isEmpty()) playerEnabledSlots.add(slot);
        else playerEnabledSlots.remove(slot);
        touchOwnerInteraction();
        changed();
    }
    public boolean configurePlayerItemType(int sourceSlot, long price, boolean enabled) {
        if (!isPlayerMarket() || sourceSlot < 0 || sourceSlot >= PLAYER_MARKET_SLOTS) return false;
        ItemStack source = playerMarketInventory.getStackInSlot(sourceSlot);
        if (source.isEmpty()) return false;
        PlayerMarketSecurityService.ValidationResult validation =
                PlayerMarketSecurityService.validateSaleConfiguration(this, sourceSlot, source, price);
        if (!validation.allowed()) return false;
        boolean configured = false;
        for (int slot = 0; slot < PLAYER_MARKET_SLOTS; slot++) {
            ItemStack stack = playerMarketInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(source, stack)) {
                if (price >= 1) playerSlotPrices.put(slot, price); else playerSlotPrices.remove(slot);
                if (enabled && price >= 1) playerEnabledSlots.add(slot); else playerEnabledSlots.remove(slot);
                configured = true;
            }
        }
        if (configured) {
            touchOwnerInteraction();
            changed();
        }
        return configured;
    }
    public ItemStack playerMarketStack(int slot) { return playerMarketInventory.getStackInSlot(slot); }
    public ItemStack extractPlayerMarketStack(int slot, int quantity) {
        ItemStack extracted = playerMarketInventory.extractItem(slot, quantity, false);
        if (playerMarketInventory.getStackInSlot(slot).isEmpty()) playerEnabledSlots.remove(slot);
        touchTransaction();
        changed();
        return extracted;
    }
    public void restorePlayerMarketStack(int slot, ItemStack stack) {
        playerMarketInventory.insertItem(slot, stack, false);
        changed();
    }
    public void addOwnerBalance(long amount) {
        pendingOwnerBalanceInIron = amount > Long.MAX_VALUE - pendingOwnerBalanceInIron
                ? Long.MAX_VALUE : pendingOwnerBalanceInIron + amount;
        touchTransaction();
        changed();
    }
    public boolean claimOwnerBalance(net.minecraft.server.level.ServerPlayer owner) {
        return fr.renblood.medievalcoins.market.service.MarketCommerceService.claim(owner,this);
    }
    public boolean hasStock(String itemId, int quantity) {
        CounterItemConfigData item = configuredItems.get(itemId);
        return item != null && (!"LIMITED".equals(item.stockMode())
                || item.stockQuantity() != null && item.stockQuantity() >= quantity);
    }
    public boolean isLimitedStock() { return "LIMITED".equals(stockMode); }
    public void reduceStock(String itemId, int quantity) {
        CounterItemConfigData item = configuredItems.get(itemId);
        if (item == null || !"LIMITED".equals(item.stockMode())) return;
        int remaining = Math.max(0, (item.stockQuantity() == null ? 0 : item.stockQuantity()) - quantity);
        configuredItems.put(itemId, new CounterItemConfigData(item.itemId(), item.displayName(), item.buyEnabled(),
                item.sellEnabled(), item.autoPrice(), item.manualBuyPrice(), item.manualSellPrice(), item.stockMode(),
                remaining, item.localModifier(), item.active()));
        itemsPendingSync = true;
        changed();
        MerchantCounterSyncService.itemsModified(this);
    }
    public void increaseStock(String itemId, int quantity) {
        CounterItemConfigData item = configuredItems.get(itemId);
        if (item == null || quantity < 1) return;
        int updated = (int) Math.min(Integer.MAX_VALUE,
                (long) (item.stockQuantity() == null ? 0 : item.stockQuantity()) + quantity);
        configuredItems.put(itemId, new CounterItemConfigData(item.itemId(), item.displayName(), item.buyEnabled(),
                item.sellEnabled(), item.autoPrice(), item.manualBuyPrice(), item.manualSellPrice(), "LIMITED",
                updated, item.localModifier(), item.active()));
        itemsPendingSync = true;
        changed();
        MerchantCounterSyncService.itemsModified(this);
    }
    public List<CounterItemConfigData> getConfiguredItems() { return List.copyOf(configuredItems.values()); }
    public CounterItemConfigData getConfiguredItem(String itemId) { return configuredItems.get(itemId); }
    public boolean clearPlayerSlotForAdmin(int slot) {
        if (!isPlayerMarket() || slot < 0 || slot >= PLAYER_MARKET_SLOTS || playerMarketInventory.getStackInSlot(slot).isEmpty()) return false;
        playerMarketInventory.setStackInSlot(slot, ItemStack.EMPTY);
        playerSlotPrices.remove(slot);
        playerEnabledSlots.remove(slot);
        touchOwnerInteraction();
        changed();
        return true;
    }
    public boolean removeConfiguredItemForAdmin(String itemId) {
        if (isPlayerMarket()) return false;
        CounterItemConfigData removed = configuredItems.remove(itemId);
        if (removed == null) return false;
        removedItemIds.add(itemId);
        itemsPendingSync = true;
        touchOwnerInteraction();
        changed();
        MerchantCounterSyncService.itemsModified(this);
        return true;
    }
    public java.util.Set<String> getRemovedItemIds() { return java.util.Set.copyOf(removedItemIds); }
    public boolean isItemsPendingSync() { return itemsPendingSync; }
    public void markItemsSynced() { itemsPendingSync = false; removedItemIds.clear(); changed(); }
    public void applyBackendItems(List<CounterItemConfigData> items) {
        if (itemsPendingSync) return;
        configuredItems.clear();
        for (CounterItemConfigData item : items) {
            CounterItemConfigData clean = cleanItem(item);
            if (clean != null) configuredItems.put(clean.itemId(), clean);
        }
        changed();
    }

    private static boolean sameConfiguration(MerchantCounterDto first, MerchantCounterDto second) {
        return java.util.Objects.equals(first.name, second.name)
                && java.util.Objects.equals(first.type, second.type)
                && java.util.Objects.equals(first.cityId, second.cityId)
                && java.util.Objects.equals(first.cityName, second.cityName)
                && java.util.Objects.equals(first.ownerType, second.ownerType)
                && java.util.Objects.equals(first.ownerId, second.ownerId)
                && java.util.Objects.equals(first.ownerName, second.ownerName)
                && first.active == second.active
                && first.buyModeEnabled == second.buyModeEnabled
                && first.sellModeEnabled == second.sellModeEnabled
                && first.autoPrice == second.autoPrice;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) MerchantCounterRegistry.register(this);
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) MerchantCounterRegistry.unregister(this);
        super.setRemoved();
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed.substring(0, Math.min(trimmed.length(), 100));
    }

    private static String allowed(String value, String[] values, String fallback) {
        for (String candidate : values) if (candidate.equals(value)) return candidate;
        return fallback;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("CounterId", counterId);
        tag.putString("Name", name);
        tag.putString("Type", type);
        tag.putString("CityId", cityId);
        tag.putString("CityName", cityName);
        tag.putString("OwnerType", ownerType);
        tag.putString("OwnerId", ownerId);
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("Active", active);
        tag.putBoolean("BuyModeEnabled", buyModeEnabled);
        tag.putBoolean("SellModeEnabled", sellModeEnabled);
        tag.putBoolean("AutoPrice", autoPrice);
        tag.putBoolean("PendingSync", pendingSync);
        tag.putBoolean("PendingCreate", pendingCreate);
        tag.putString("StockMode", stockMode);
        CompoundTag stocks = new CompoundTag();
        stockQuantities.forEach(stocks::putInt);
        tag.put("StockQuantities", stocks);
        tag.putBoolean("ItemsPendingSync", itemsPendingSync);
        tag.putLong("PendingOwnerBalanceInIron", pendingOwnerBalanceInIron);
        tag.putBoolean("CommerceStockMirror",commerceStockMirror);
        CompoundTag commerce=new CompoundTag();commerceStock.forEach((slot,count)->commerce.putInt(slot.toString(),count));tag.put("CommerceStock",commerce);
        tag.putLong("LastOwnerInteractionAt", lastOwnerInteractionAt);
        tag.putLong("LastTransactionAt", lastTransactionAt);
        tag.put("PlayerMarketInventory", playerMarketInventory.serializeNBT());
        CompoundTag playerPrices = new CompoundTag();
        playerSlotPrices.forEach((slot, price) -> playerPrices.putLong(Integer.toString(slot), price));
        tag.put("PlayerSlotPrices", playerPrices);
        ListTag enabledSlots = new ListTag();
        playerEnabledSlots.forEach(slot -> enabledSlots.add(net.minecraft.nbt.IntTag.valueOf(slot)));
        tag.put("PlayerEnabledSlots", enabledSlots);
        ListTag removedList = new ListTag();
        removedItemIds.forEach(id -> removedList.add(net.minecraft.nbt.StringTag.valueOf(id)));
        tag.put("RemovedItemIds", removedList);
        ListTag itemList = new ListTag();
        for (CounterItemConfigData item : configuredItems.values()) {
            CompoundTag value = new CompoundTag();
            value.putString("ItemId", item.itemId());
            value.putString("DisplayName", item.displayName());
            value.putBoolean("BuyEnabled", item.buyEnabled());
            value.putBoolean("SellEnabled", item.sellEnabled());
            value.putBoolean("AutoPrice", item.autoPrice());
            if (item.manualBuyPrice() != null) value.putLong("ManualBuyPrice", item.manualBuyPrice());
            if (item.manualSellPrice() != null) value.putLong("ManualSellPrice", item.manualSellPrice());
            value.putString("StockMode", item.stockMode());
            if (item.stockQuantity() != null) value.putInt("StockQuantity", item.stockQuantity());
            value.putDouble("LocalModifier", item.localModifier());
            value.putBoolean("Active", item.active());
            itemList.add(value);
        }
        tag.put("ConfiguredItems", itemList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        counterId = tag.contains("CounterId") ? tag.getString("CounterId") : counterId;
        name = tag.contains("Name") ? tag.getString("Name") : name;
        type = tag.contains("Type") ? tag.getString("Type") : type;
        cityId = tag.getString("CityId");
        cityName = tag.getString("CityName");
        ownerType = tag.contains("OwnerType") ? tag.getString("OwnerType") : ownerType;
        ownerId = tag.getString("OwnerId");
        ownerName = tag.getString("OwnerName");
        active = !tag.contains("Active") || tag.getBoolean("Active");
        buyModeEnabled = !tag.contains("BuyModeEnabled") || tag.getBoolean("BuyModeEnabled");
        sellModeEnabled = tag.getBoolean("SellModeEnabled");
        autoPrice = !tag.contains("AutoPrice") || tag.getBoolean("AutoPrice");
        pendingSync = tag.getBoolean("PendingSync");
        pendingCreate = tag.getBoolean("PendingCreate");
        stockMode = tag.contains("StockMode") ? tag.getString("StockMode") : "UNLIMITED";
        stockQuantities.clear();
        CompoundTag stocks = tag.getCompound("StockQuantities");
        for (String key : stocks.getAllKeys()) stockQuantities.put(key, stocks.getInt(key));
        itemsPendingSync = tag.getBoolean("ItemsPendingSync");
        pendingOwnerBalanceInIron = tag.getLong("PendingOwnerBalanceInIron");
        commerceStockMirror=tag.getBoolean("CommerceStockMirror");commerceStock.clear();
        CompoundTag commerce=tag.getCompound("CommerceStock");
        for(String slot:commerce.getAllKeys())try{commerceStock.put(Integer.parseInt(slot),commerce.getInt(slot));}catch(NumberFormatException ignored){}
        lastOwnerInteractionAt = tag.contains("LastOwnerInteractionAt") ? tag.getLong("LastOwnerInteractionAt") : System.currentTimeMillis();
        lastTransactionAt = tag.contains("LastTransactionAt") ? tag.getLong("LastTransactionAt") : lastOwnerInteractionAt;
        if (tag.contains("PlayerMarketInventory")) playerMarketInventory.deserializeNBT(tag.getCompound("PlayerMarketInventory"));
        playerSlotPrices.clear();
        CompoundTag playerPrices = tag.getCompound("PlayerSlotPrices");
        for (String key : playerPrices.getAllKeys()) try { playerSlotPrices.put(Integer.parseInt(key), playerPrices.getLong(key)); } catch (NumberFormatException ignored) {}
        playerEnabledSlots.clear();
        for (Tag raw : tag.getList("PlayerEnabledSlots", Tag.TAG_INT)) playerEnabledSlots.add(((net.minecraft.nbt.IntTag) raw).getAsInt());
        removedItemIds.clear();
        for (Tag raw : tag.getList("RemovedItemIds", Tag.TAG_STRING)) removedItemIds.add(raw.getAsString());
        configuredItems.clear();
        ListTag itemList = tag.getList("ConfiguredItems", Tag.TAG_COMPOUND);
        for (Tag raw : itemList) {
            CompoundTag value = (CompoundTag) raw;
            CounterItemConfigData item = new CounterItemConfigData(value.getString("ItemId"), value.getString("DisplayName"),
                    value.getBoolean("BuyEnabled"), value.getBoolean("SellEnabled"), value.getBoolean("AutoPrice"),
                    value.contains("ManualBuyPrice") ? value.getLong("ManualBuyPrice") : null,
                    value.contains("ManualSellPrice") ? value.getLong("ManualSellPrice") : null,
                    value.getString("StockMode"), value.contains("StockQuantity") ? value.getInt("StockQuantity") : null,
                    value.contains("LocalModifier") ? value.getDouble("LocalModifier") : 1.0D, value.getBoolean("Active"));
            configuredItems.put(item.itemId(), item);
        }
    }

    private static boolean hasInventorySpace(net.minecraft.server.level.ServerPlayer player, ItemStack stack) {
        int needed = stack.getCount();
        for (ItemStack current : player.getInventory().items) {
            if (current.isEmpty()) needed -= stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(current, stack)) needed -= current.getMaxStackSize() - current.getCount();
            if (needed <= 0) return true;
        }
        return false;
    }
}
