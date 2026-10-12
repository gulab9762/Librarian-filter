package com.gbdhapa.forge;

import com.gbdhapa.CommonClass;
import com.gbdhapa.Constants;
import com.gbdhapa.LibrarianCommands;
import com.gbdhapa.LibrarianCore;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class LibrarianfilterForge {

    public LibrarianfilterForge(FMLJavaModLoadingContext context) {
        Constants.LOG.info("Initializing {} Forge mod!", Constants.MOD_NAME);
        CommonClass.init();

        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickBlock);
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        InteractionResult result = LibrarianCore.handleRightClick(
                event.getEntity(),
                event.getLevel(),
                event.getPos(),
                event.getHand()
        );

        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        LibrarianCommands.register(event.getDispatcher());
        Constants.LOG.info("Registered /reroll commands for {}", Constants.MOD_NAME);
    }
}
