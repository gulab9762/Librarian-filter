package com.gbdhapa;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import java.util.Map;

@Mod(modid = "librarian_filter", name = "Easy Villager Trade Reroll", version = "1.0.1", useMetadata = true)
public class LibrarianFilter1122 {
    public static SimpleNetworkWrapper NETWORK;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("librarian_filter");
        NETWORK.registerMessage(RerollMessageHandler.class, RerollMessage.class, 0, Side.SERVER);
    }

    public static class RerollMessage implements IMessage {
        private int entityId;
        private String targetEnch;

        public RerollMessage() {}

        public RerollMessage(int entityId, String targetEnch) {
            this.entityId = entityId;
            this.targetEnch = targetEnch;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.entityId = buf.readInt();
            int len = buf.readInt();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            this.targetEnch = new String(bytes);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(entityId);
            byte[] bytes = targetEnch.getBytes();
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    public static class RerollMessageHandler implements IMessageHandler<RerollMessage, IMessage> {
        @Override
        public IMessage onMessage(RerollMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                net.minecraft.entity.Entity entity = player.world.getEntityByID(message.entityId);
                if (entity instanceof EntityVillager) {
                    EntityVillager villager = (EntityVillager) entity;
                    
                    // Check if player has traded with this villager (optional safety, skip for simple reroll)
                    int attempts = 0;
                    Method populateBuyingList = null;
                    try {
                        populateBuyingList = ReflectionHelper.findMethod(EntityVillager.class, "populateBuyingList", "func_70950_c");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (populateBuyingList != null) {
                        while (attempts < 2000) {
                            MerchantRecipeList list = villager.getRecipes(player);
                            list.clear();
                            try {
                                populateBuyingList.invoke(villager);
                            } catch (Exception e) {
                                e.printStackTrace();
                                break;
                            }

                            // If query is empty, just reroll once
                            if (message.targetEnch == null || message.targetEnch.trim().isEmpty()) {
                                break;
                            }

                            // Check if desired enchantment is present in the trades
                            boolean found = false;
                            for (MerchantRecipe recipe : list) {
                                ItemStack sellStack = recipe.getItemToSell();
                                if (sellStack.getItem() == Items.ENCHANTED_BOOK) {
                                    Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(sellStack);
                                    for (Enchantment ench : map.keySet()) {
                                        if (ench.getRegistryName() != null) {
                                            String path = ench.getRegistryName().getPath();
                                            if (path.toLowerCase().contains(message.targetEnch.toLowerCase().trim())) {
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (found) break;
                            }

                            if (found) break;
                            attempts++;
                        }
                    }

                    // Sync the new trades to the client by reopening or updating the merchant container
                    player.sendAllContents(player.openContainer, player.openContainer.getInventory());
                }
            });
            return null;
        }
    }
}
