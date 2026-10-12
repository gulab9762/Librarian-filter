package com.gbdhapa.forge;

import com.gbdhapa.CommonClass;
import com.gbdhapa.Constants;
import com.gbdhapa.LibrarianCommands;
import com.gbdhapa.LibrarianCore;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class LibrarianfilterForge {

    public LibrarianfilterForge(FMLJavaModLoadingContext context) {
        Constants.LOG.info("Initializing {} Forge mod!", Constants.MOD_NAME);
        CommonClass.init();

        RegisterCommandsEvent.BUS.addListener(this::registerCommands);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(this::onRightClickBlock);
    }

    private boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return false;
        }

        InteractionResult result = LibrarianCore.handleRightClick(
                event.getEntity(),
                event.getLevel(),
                event.getPos(),
                event.getHand()
        );

        event.setCancellationResult(result);
        return result.consumesAction();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        LibrarianCommands.register(event.getDispatcher());
        Constants.LOG.info("Registered /reroll commands for {}", Constants.MOD_NAME);
    }
}