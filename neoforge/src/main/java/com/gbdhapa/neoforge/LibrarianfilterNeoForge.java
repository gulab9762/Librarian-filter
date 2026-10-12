package com.gbdhapa.neoforge;

import com.gbdhapa.CommonClass;
import com.gbdhapa.Constants;
import com.gbdhapa.LibrarianCommands;
import com.gbdhapa.LibrarianCore;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(Constants.MOD_ID)
public class LibrarianfilterNeoForge {

    public LibrarianfilterNeoForge(IEventBus modEventBus) {
        Constants.LOG.info("Initializing {} NeoForge mod!", Constants.MOD_NAME);
        CommonClass.init();

        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
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

        event.setCancellationResult(result);
        if (result.consumesAction()) {
            event.setCanceled(true);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        LibrarianCommands.register(event.getDispatcher());
        Constants.LOG.info("Registered /reroll commands for {}", Constants.MOD_NAME);
    }
}