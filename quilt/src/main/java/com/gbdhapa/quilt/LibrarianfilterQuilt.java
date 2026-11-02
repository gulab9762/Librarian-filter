package com.gbdhapa.quilt;

import com.gbdhapa.LibrarianCommands;
import com.gbdhapa.LibrarianCore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public class LibrarianfilterQuilt implements ModInitializer {
    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            return LibrarianCore.handleRightClick(player, world, hitResult.getBlockPos(), hand);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LibrarianCommands.register(dispatcher);
        });
    }
}

