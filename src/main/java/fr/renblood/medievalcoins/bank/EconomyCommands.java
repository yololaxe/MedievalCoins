package fr.renblood.medievalcoins.bank;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.MedievalCoinsAPI;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.renblood.medievalcoins.network.ApiExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mod.EventBusSubscriber(modid=MedievalCoin.MODID)
public final class EconomyCommands {
    private static final Map<String, String> KNOWN_BUDGETS = Map.of(
            "npc-contracts", "Contrats des marchands", "inn-customers", "Budget des voyageurs",
            "treasury:npc", "Trésorerie des PNJ");

    private static CompletableFuture<Suggestions> suggestBudgets(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        return suggestTargets(context, builder, "", KNOWN_BUDGETS, false);
    }

    private static CompletableFuture<Suggestions> suggestOwned(CommandContext<CommandSourceStack> context,
                                                               SuggestionsBuilder builder) {
        return suggestTargets(context, builder, "&owned=1", Map.of(), false);
    }

    private static CompletableFuture<Suggestions> suggestOffers(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder) {
        String scope = "&owned=1&stock_business=" + URLEncoder.encode(businessId(context), StandardCharsets.UTF_8);
        return suggestTargets(context, builder, scope, Map.of(), true);
    }

    private static CompletableFuture<Suggestions> suggestTargets(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder, String scope, Map<String, String> defaults, boolean quoted) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            return CompletableFuture.completedFuture(buildSuggestions(builder, defaults, quoted));
        }
        String prefix = completionPrefix(builder, quoted);
        String uuid = player.getStringUUID();
        try {
            return ApiExecutor.supply(() -> {
                Map<String, String> targets = new LinkedHashMap<>(defaults);
                try {
                    String route = "commerce/funding-targets?prefix="
                            + URLEncoder.encode(prefix, StandardCharsets.UTF_8) + scope;
                    var rows = CommerceProtocol.get(uuid, route).getAsJsonArray("targets");
                    for (var value : rows) {
                        JsonObject row = value.getAsJsonObject();
                        targets.put(row.get("business_id").getAsString(), row.get("label").getAsString());
                    }
                } catch (Exception ignored) {
                    // Built-in budget names are available even when the backend is down.
                }
                return buildSuggestions(builder, targets, quoted);
            });
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            return CompletableFuture.completedFuture(buildSuggestions(builder, defaults, quoted));
        }
    }

    private static String completionPrefix(SuggestionsBuilder builder, boolean quoted) {
        String prefix = builder.getRemainingLowerCase();
        return quoted && prefix.startsWith("\"") ? prefix.substring(1) : prefix;
    }

    private static Suggestions buildSuggestions(SuggestionsBuilder builder, Map<String, String> targets, boolean quoted) {
        String prefix = completionPrefix(builder, quoted);
        targets.forEach((id, label) -> {
            if (id.startsWith(prefix)) {
                builder.suggest(quoted ? StringArgumentType.escapeIfRequired(id) : id, Component.literal(label));
            }
        });
        return builder.build();
    }

    private static String businessId(CommandContext<CommandSourceStack> context) {
        var id = ResourceLocationArgument.getId(context, "etablissement");
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    private static int setBudgetMode(CommandContext<CommandSourceStack> context, boolean unlimited)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var server = player.getServer();
        String business = businessId(context);
        String uuid = player.getStringUUID();
        ApiExecutor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("unlimited", unlimited);
                body.addProperty("idempotency_key", UUID.randomUUID().toString());
                String route = "admin/businesses/" + URLEncoder.encode(business, StandardCharsets.UTF_8) + "/budget-mode";
                CommerceProtocol.post(uuid, route, body);
                server.execute(() -> {
                    String mode = unlimited ? "rachat illimité · le complément est créé à chaque paiement confirmé"
                            : "rachat limité au disponible de la caisse";
                    context.getSource().sendSuccess(() -> Component.literal(business + " · " + mode)
                            .withStyle(net.minecraft.ChatFormatting.GREEN), false);
                });
            } catch (Exception failure) {
                server.execute(() -> context.getSource().sendFailure(Component.literal(
                        "Impossible de changer le budget de " + business + " : " + failure.getMessage())));
            }
        });
        return 1;
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        var economy = Commands.literal("economie")
            .then(Commands.literal("reprendre").executes(ctx->{BankService.recover(ctx.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("diagnostic").requires(source->source.hasPermission(2)).executes(ctx->{ctx.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(EconomyTimings.snapshot()),false);return 1;}))
            .then(Commands.literal("caisse").then(Commands.argument("etablissement", ResourceLocationArgument.id()).suggests(EconomyCommands::suggestOwned).executes(ctx->{
                MedievalCoinsAPI.claimCommerce(ctx.getSource().getPlayerOrException(),UUID.randomUUID().toString(),businessId(ctx));return 1;
            })))
            .then(Commands.literal("budget").requires(s->s.hasPermission(2))
                .then(Commands.argument("etablissement", ResourceLocationArgument.id())
                    .suggests(EconomyCommands::suggestBudgets)
                    .then(Commands.literal("illimite").executes(ctx -> setBudgetMode(ctx, true)))
                    .then(Commands.literal("limite").executes(ctx -> setBudgetMode(ctx, false)))
                    .then(Commands.argument("fer",com.mojang.brigadier.arguments.LongArgumentType.longArg(1)).executes(ctx->{
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String amount = Long.toString(com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "fer"));
                    String business = businessId(ctx);
                    CommerceService.settle(player,UUID.randomUUID(),()->{
                        JsonObject body=new JsonObject();body.addProperty("action","FUND");body.addProperty("business_id",business);body.addProperty("amount",amount);body.addProperty("payment","BANK");return body;
                    },()->true,()->{});return 1;
                }))))
            .then(Commands.literal("stock").then(Commands.argument("etablissement", ResourceLocationArgument.id()).suggests(EconomyCommands::suggestOwned)
                .then(Commands.argument("offre", StringArgumentType.string()).suggests(EconomyCommands::suggestOffers).then(Commands.argument("quantite",IntegerArgumentType.integer(1,64)).executes(ctx->{
                    ServerPlayer player=ctx.getSource().getPlayerOrException();String business=businessId(ctx),offer=StringArgumentType.getString(ctx,"offre");
                    int count=IntegerArgumentType.getInteger(ctx,"quantite");JsonObject[] accepted=new JsonObject[1];
                    CommerceService.settle(player,UUID.randomUUID(),()->{
                        accepted[0] = CommerceProtocol.findOffer(player.getStringUUID(), business, offer);
                        if(accepted[0]==null)throw new IllegalStateException("Offre introuvable");
                        return CommerceProtocol.request(business,offer,accepted[0].get("version").getAsString(),"STOCK_OUT",0,count,"BANK");
                    },()->CommerceInventory.mutate(player,()->{
                        try{ItemStack stack=ItemStack.of(TagParser.parseTag(accepted[0].get("item_nbt").getAsString()));
                            if(stack.isEmpty())return false;int left=count;
                            while(left>0){ItemStack part=stack.copy();int n=Math.min(left,stack.getMaxStackSize());part.setCount(n);if(!player.getInventory().add(part))return false;left-=n;}return true;
                        }catch(Exception failure){throw new IllegalStateException(failure);}
                    }),()->{});return 1;
                })))));
        event.getDispatcher().register(Commands.literal("mc").then(economy));
        event.getDispatcher().register(economy);
    }
}
