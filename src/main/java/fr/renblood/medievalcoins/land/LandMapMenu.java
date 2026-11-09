package fr.renblood.medievalcoins.land;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.regex.Pattern;

public class LandMapMenu extends AbstractContainerMenu {
    public static final int MAX_COLUMNS = 9;
    public static final int MAX_ROWS = 6;
    private static final int SLOT_SIZE = 18;
    private static final int START_X = 8;
    private static final int START_Y = 18;
    private static final Pattern HEX_COLOR = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    private final SimpleContainer container;
    private final int columns;
    private final int rows;

    public LandMapMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, readColumns(buffer), readRows(buffer), null);
    }

    public LandMapMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        this(containerId, playerInventory, computeColumns(), computeRows(), player);
    }

    private LandMapMenu(int containerId, Inventory playerInventory, int columns, int rows, ServerPlayer player) {
        super(MedievalCoinsModMenus.LAND_MAP.get(), containerId);
        this.columns = clamp(columns, 1, MAX_COLUMNS);
        this.rows = clamp(rows, 1, MAX_ROWS);
        this.container = new SimpleContainer(this.columns * this.rows);

        for (int row = 0; row < this.rows; row++) {
            for (int col = 0; col < this.columns; col++) {
                int index = col + row * this.columns;
                this.addSlot(new LandMapSlot(this.container, index, START_X + col * SLOT_SIZE, START_Y + row * SLOT_SIZE));
            }
        }

        if (player != null) {
            fillForPlayer(player);
        }
    }

    public static void writeOpenData(FriendlyByteBuf buffer) {
        buffer.writeVarInt(computeColumns());
        buffer.writeVarInt(computeRows());
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void fillForPlayer(ServerPlayer player) {
        ChunkPos center = player.chunkPosition();
        int radiusX = Math.min(LandConfig.get().getMapRadiusX(), (columns - 1) / 2);
        int radiusZ = Math.min(LandConfig.get().getMapRadiusZ(), (rows - 1) / 2);

        int minChunkX = center.x - radiusX;
        int minChunkZ = center.z - radiusZ;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int chunkX = minChunkX + col;
                int chunkZ = minChunkZ + row;
                boolean currentChunk = chunkX == center.x && chunkZ == center.z;
                Optional<LandPlot> plot = LandRegistry.findAt(player.level().dimension(), chunkX, chunkZ);
                container.setItem(col + row * columns, createChunkStack(chunkX, chunkZ, center.x, center.z, plot, currentChunk));
            }
        }
    }

    private ItemStack createChunkStack(int chunkX, int chunkZ, int centerX, int centerZ, Optional<LandPlot> plot, boolean currentChunk) {
        Item item = plot.map(value -> itemFor(value.getOwnerType())).orElse(Items.BLACK_STAINED_GLASS_PANE);
        Optional<Item> colorItem = plot.map(LandPlot::getColor)
                .filter(LandMapMenu::isHexColor)
                .map(LandMapMenu::colorItemFor);
        ItemStack stack = new ItemStack(colorItem.orElse(item));
        if (currentChunk) {
            stack.setCount(2);
        }

        Component name = plot
                .map(value -> Component.literal((currentChunk ? "> " : "") + value.getDisplayName())
                        .withStyle(currentChunk ? ChatFormatting.GOLD : ChatFormatting.WHITE))
                .orElse(Component.literal((currentChunk ? "> " : "") + "Chunk non attribue")
                        .withStyle(currentChunk ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        stack.setHoverName(name);
        addTooltip(stack, chunkX, chunkZ, centerX, centerZ, plot);
        return stack;
    }

    private void addTooltip(ItemStack stack, int chunkX, int chunkZ, int centerX, int centerZ, Optional<LandPlot> plot) {
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Chunk " + chunkX + "," + chunkZ)
                .withStyle(ChatFormatting.GRAY))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Position relative : " + relative(chunkX - centerX, chunkZ - centerZ))
                .withStyle(ChatFormatting.DARK_GRAY))));

        if (plot.isPresent()) {
            LandPlot landPlot = plot.get();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Parcelle : " + landPlot.getDisplayName())
                    .withStyle(ChatFormatting.WHITE))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Ville : " + valueOrDash(landPlot.getCity()))
                    .withStyle(ChatFormatting.BLUE))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Proprietaire : " + valueOrDash(landPlot.getOwner()))
                    .withStyle(ChatFormatting.YELLOW))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Type : " + formatOwnerType(landPlot.getOwnerType()))
                    .withStyle(ChatFormatting.AQUA))));
            if (!landPlot.getColor().isBlank()) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Couleur : " + landPlot.getColor().replace("#", "").toUpperCase())
                        .withStyle(ChatFormatting.GRAY))));
            }
            if (!landPlot.getDescription().isBlank()) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(landPlot.getDescription())
                        .withStyle(ChatFormatting.DARK_GRAY))));
            }
        } else {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Non attribue")
                    .withStyle(ChatFormatting.DARK_GRAY))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Utilisez /land add-current-chunk <id>")
                    .withStyle(ChatFormatting.GRAY))));
        }

        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    private static Item itemFor(LandOwnerType ownerType) {
        return switch (ownerType) {
            case PLAYER -> Items.OAK_SIGN;
            case CITY -> Items.BLUE_BANNER;
            case GUILD -> Items.YELLOW_BANNER;
            case FAMILY -> Items.RED_BANNER;
            case RELIGIOUS -> Items.PURPLE_BANNER;
            case KINGDOM -> Items.GOLD_BLOCK;
            case PUBLIC -> Items.GRASS_BLOCK;
            case UNKNOWN, SERVER -> Items.GRAY_CONCRETE;
        };
    }

    private static Item colorItemFor(String color) {
        String normalized = color.replace("#", "");
        int red = Integer.parseInt(normalized.substring(0, 2), 16);
        int green = Integer.parseInt(normalized.substring(2, 4), 16);
        int blue = Integer.parseInt(normalized.substring(4, 6), 16);

        if (red > 210 && green > 210 && blue > 210) {
            return Items.WHITE_CONCRETE;
        }
        if (red < 45 && green < 45 && blue < 45) {
            return Items.BLACK_CONCRETE;
        }
        if (red > 170 && green > 170 && blue < 90) {
            return Items.YELLOW_CONCRETE;
        }
        if (red > 180 && green > 90 && blue < 80) {
            return Items.ORANGE_CONCRETE;
        }
        if (red > 150 && blue > 150 && green < 120) {
            return Items.MAGENTA_CONCRETE;
        }
        if (red > 150 && green < 100 && blue < 100) {
            return Items.RED_CONCRETE;
        }
        if (blue > 150 && red < 130) {
            return Items.BLUE_CONCRETE;
        }
        if (green > 130 && red < 150 && blue < 150) {
            return Items.LIME_CONCRETE;
        }
        if (green > 110 && blue > 110) {
            return Items.CYAN_CONCRETE;
        }
        if (red > 120 && green > 80 && blue > 60) {
            return Items.BROWN_CONCRETE;
        }
        return Items.GRAY_CONCRETE;
    }

    private static boolean isHexColor(String color) {
        return color != null && HEX_COLOR.matcher(color).matches();
    }

    private static String relative(int deltaX, int deltaZ) {
        String eastWest = deltaX == 0 ? "centre X" : Math.abs(deltaX) + (deltaX > 0 ? " E" : " O");
        String northSouth = deltaZ == 0 ? "centre Z" : Math.abs(deltaZ) + (deltaZ > 0 ? " S" : " N");
        return eastWest + " / " + northSouth;
    }

    private static String formatOwnerType(LandOwnerType ownerType) {
        String lower = ownerType.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static int computeColumns() {
        return 1 + Math.min(LandConfig.get().getMapRadiusX(), 4) * 2;
    }

    private static int computeRows() {
        return 1 + Math.min(LandConfig.get().getMapRadiusZ(), 2) * 2;
    }

    private static int readColumns(FriendlyByteBuf buffer) {
        return buffer.readVarInt();
    }

    private static int readRows(FriendlyByteBuf buffer) {
        return buffer.readVarInt();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
