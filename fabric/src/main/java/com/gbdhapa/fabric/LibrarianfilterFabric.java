package com.gbdhapa.fabric;

import com.gbdhapa.CommonClass;
import com.gbdhapa.Constants;
import com.gbdhapa.LibrarianCommands;
import com.gbdhapa.LibrarianCore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public class LibrarianfilterFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Constants.LOG.info("Initializing {} Fabric mod!", Constants.MOD_NAME);
        CommonClass.init();

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            return LibrarianCore.handleRightClick(player, world, hitResult.getBlockPos(), hand);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LibrarianCommands.register(dispatcher);
            Constants.LOG.info("Registered /reroll commands for {}", Constants.MOD_NAME);
        });
    }
}