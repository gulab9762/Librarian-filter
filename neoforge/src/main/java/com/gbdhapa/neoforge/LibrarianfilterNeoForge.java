package com.gbdhapa.neoforge;

import com.gbdhapa.LibrarianCommands;
import com.gbdhapa.LibrarianCore;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod("librarian_filter")
public class LibrarianfilterNeoForge {
    public LibrarianfilterNeoForge(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = LibrarianCore.handleRightClick(event.getEntity(), event.getLevel(), event.getPos(), event.getHand());
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        LibrarianCommands.register(event.getDispatcher());
    }
}
