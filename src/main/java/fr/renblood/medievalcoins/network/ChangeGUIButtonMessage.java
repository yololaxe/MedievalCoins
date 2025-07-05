// src/main/java/fr/renblood/medievalcoins/network/ChangeGUIButtonMessage.java
package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.inventory.banker.ChangeGUIMenu;
import fr.renblood.medievalcoins.procedures.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChangeGUIButtonMessage {
	private final int buttonID;
	private final int x, y, z;

	public ChangeGUIButtonMessage(FriendlyByteBuf buf) {
		this.buttonID = buf.readInt();
		this.x        = buf.readInt();
		this.y        = buf.readInt();
		this.z        = buf.readInt();
	}

	public ChangeGUIButtonMessage(int buttonID, int x, int y, int z) {
		this.buttonID = buttonID;
		this.x        = x;
		this.y        = y;
		this.z        = z;
	}

	public static void buffer(ChangeGUIButtonMessage msg, FriendlyByteBuf buf) {
		buf.writeInt(msg.buttonID);
		buf.writeInt(msg.x);
		buf.writeInt(msg.y);
		buf.writeInt(msg.z);
	}

	public static void handler(ChangeGUIButtonMessage msg, Supplier<NetworkEvent.Context> ctxSup) {
		NetworkEvent.Context ctx = ctxSup.get();
		ctx.enqueueWork(() -> {
			ServerPlayer sender = ctx.getSender();
			if (sender == null) return;

			// On s'assure que le joueur a bien la GUI ouverte et on récupère le conteneur serveur
			if (!(sender.containerMenu instanceof ChangeGUIMenu menu)) return;

			Level world = sender.level();
			BlockPos pos = new BlockPos(msg.x, msg.y, msg.z);
			if (!world.hasChunkAt(pos)) return;

			// 1) Exécution de la logique de conversion
			switch (msg.buttonID) {
				case 0 -> CtoBProcedure.execute(sender);
				case 1 -> BtoSProcedure.execute(sender);
				case 2 -> StoGProcedure.execute(sender);
				case 3 -> BtoCProcedure.execute(sender);
				case 4 -> StoBProcedure.execute(sender);
				case 5 -> GtoSProcedure.execute(sender);
			}

			// 2) On rafraîchit le conteneur en cours pour mettre à jour les slots clients
			menu.broadcastChanges();
		});
		ctx.setPacketHandled(true);
	}
}
