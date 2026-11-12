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

    private int centerX, centerZ;
    private String dimension="";
    private boolean administrator;
    private boolean optionsTruncated;
    public boolean optionsTruncated(){return optionsTruncated;}
    private java.util.List<PlotChoice> choices=java.util.List.of();
    private long nextActionTick;
    public record PlotChoice(String id,String name){}
    public boolean isAdministrator(){return administrator;}
    public java.util.List<PlotChoice> choices(){return choices;}
    public net.minecraft.nbt.CompoundTag cell(int index){if(index<0||index>=slots.size())return new net.minecraft.nbt.CompoundTag();var stack=slots.get(index).getItem();return stack.isEmpty()||!stack.hasTag()?new net.minecraft.nbt.CompoundTag():stack.getTag().getCompound("Land");}
    public int centerX(){var tag=cell(columns/2+(rows/2)*columns);return tag.contains("x")?tag.getInt("x"):centerX;}
    public int centerZ(){var tag=cell(columns/2+(rows/2)*columns);return tag.contains("z")?tag.getInt("z"):centerZ;}
    public String dimension(){return dimension;}
    private final SimpleContainer container;
    private final int columns;
    private final int rows;

    public LandMapMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, readColumns(buffer), readRows(buffer), null);
        centerX=buffer.readInt();centerZ=buffer.readInt();dimension=buffer.readUtf(128);administrator=buffer.readBoolean();
        int count=buffer.readVarInt();if(count<0||count>2048)throw new IllegalArgumentException("Invalid plot count");
        var list=new java.util.ArrayList<PlotChoice>();for(int i=0;i<count;i++)list.add(new PlotChoice(buffer.readUtf(128),buffer.readUtf(128)));choices=java.util.List.copyOf(list);optionsTruncated=buffer.readBoolean();
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
            centerX=player.chunkPosition().x;centerZ=player.chunkPosition().z;dimension=player.level().dimension().location().toString();administrator=LandConfig.canAdmin(player.createCommandSourceStack());
            choices=options(player);fillForPlayer(player);
        }
    }

    public static void writeOpenData(FriendlyByteBuf buffer, ServerPlayer player) {
        buffer.writeVarInt(computeColumns());
        buffer.writeVarInt(computeRows());
        buffer.writeInt(player.chunkPosition().x);buffer.writeInt(player.chunkPosition().z);buffer.writeUtf(player.level().dimension().location().toString(),128);
        boolean admin=LandConfig.canAdmin(player.createCommandSourceStack());buffer.writeBoolean(admin);
        var options=admin?options(player):java.util.List.<PlotChoice>of();buffer.writeVarInt(options.size());for(var option:options){buffer.writeUtf(option.id(),128);buffer.writeUtf(option.name(),128);}buffer.writeBoolean(admin&&options.size()<LandRegistry.size());
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().dimension().location().toString().equals(dimension);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void fillForPlayer(ServerPlayer player) {
        var knowledge=LandDiscoveryData.get(player.server);
        knowledge.visit(player.getUUID(),dimension,player.chunkPosition().x,player.chunkPosition().z);
        boolean admin=LandConfig.canAdmin(player.createCommandSourceStack());
        ChunkPos center = new ChunkPos(centerX,centerZ);
        int radiusX = Math.min(LandConfig.get().getMapRadiusX(), (columns - 1) / 2);
        int radiusZ = Math.min(LandConfig.get().getMapRadiusZ(), (rows - 1) / 2);

        int minChunkX = center.x - radiusX;
        int minChunkZ = center.z - radiusZ;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int chunkX = minChunkX + col;
                int chunkZ = minChunkZ + row;
                boolean currentChunk = chunkX == player.chunkPosition().x && chunkZ == player.chunkPosition().z;
                Optional<LandPlot> plot = LandRegistry.findAt(player.level().dimension(), chunkX, chunkZ);
                boolean known=admin||knowledge.known(player.getUUID(),dimension,chunkX,chunkZ);
                ItemStack stack=createChunkStack(chunkX,chunkZ,center.x,center.z,known?plot:Optional.empty(),currentChunk);
                stack.getOrCreateTagElement("Land").putBoolean("known",known);
                if(!known){stack.setHoverName(Component.literal("Territoire inexploré"));stack.getOrCreateTagElement("display").remove("Lore");}
                container.setItem(col + row * columns,stack);
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
        var tag=stack.getOrCreateTagElement("Land");tag.putInt("x",chunkX);tag.putInt("z",chunkZ);
        plot.ifPresent(value->{tag.putString("id",value.getId());tag.putString("name",value.getDisplayName());tag.putString("owner",value.getOwner());tag.putString("type",value.getOwnerType().name());tag.putString("city",value.getCity());tag.putString("description",value.getDescription());tag.putInt("color",LandMapRules.color(value.getColor(),value.getOwnerType().name()));});
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

    private static java.util.List<PlotChoice> options(ServerPlayer player){
        var result=new java.util.ArrayList<PlotChoice>();int bytes=0;
        for(var plot:LandRegistry.getPlots()){
            if(plot.getId().length()>128||plot.getDisplayName().length()>128||!plot.getDimension().isBlank()&&!plot.getDimension().equalsIgnoreCase(player.level().dimension().location().toString()))continue;
            int cost=plot.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+plot.getDisplayName().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+8;
            if(bytes+cost>24000||result.size()>=2048)break;
            result.add(new PlotChoice(plot.getId(),plot.getDisplayName()));bytes+=cost;
        }
        return java.util.List.copyOf(result);
    }
    public void handle(ServerPlayer player,LandMapAction action){
        long tick=player.level().getGameTime();if(!stillValid(player)||!LandConfig.get().isEnableLandSystem())return;if(action.action()<=1){if(tick<nextActionTick)return;nextActionTick=tick+4;}
        if(action.action()==0){
            int x=action.x()==99?player.chunkPosition().x:centerX+Integer.signum(action.x());
            int z=action.x()==99?player.chunkPosition().z:centerZ+Integer.signum(action.z());
            if(!LandMapRules.nearby(x,z,player.chunkPosition().x,player.chunkPosition().z))return;
            centerX=x;centerZ=z;fillForPlayer(player);broadcastChanges();return;
        }
        if(action.action()==1){fillForPlayer(player);broadcastChanges();return;}
        if(!LandConfig.canAdmin(player.createCommandSourceStack())){player.sendSystemMessage(Component.literal("L’attribution des terrains est réservée aux administrateurs."));return;}
        if(action.action()!=2&&action.action()!=3)return;
        if(!LandMapRules.visible(action.x(),action.z(),centerX,centerZ,columns,rows)||!LandMapRules.nearby(action.x(),action.z(),player.chunkPosition().x,player.chunkPosition().z)){player.sendSystemMessage(Component.literal("Ce chunk n’est plus dans la carte affichée. Actualise le cadastre."));return;}
        try{
            var plots=LandStorage.loadForEdit();
            LandClaimEditor.apply(plots,dimension,action.x(),action.z(),action.target(),action.expected(),action.action()==3);
            LandStorage.saveAndReload(plots);LandPlayerTracker.clear();fillForPlayer(player);broadcastChanges();player.sendSystemMessage(Component.literal(action.action()==2?"Chunk attribué. Le cadastre est à jour.":"Attribution retirée. La parcelle et ses autres chunks sont conservés.").withStyle(net.minecraft.ChatFormatting.GREEN));
        }catch(Exception failure){player.sendSystemMessage(Component.literal("Cadastre : "+failure.getMessage()).withStyle(net.minecraft.ChatFormatting.RED));}
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
