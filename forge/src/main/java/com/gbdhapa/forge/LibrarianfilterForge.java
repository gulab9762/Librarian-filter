package com.gbdhapa.forge;

import com.gbdhapa.CommonClass;
import com.gbdhapa.Constants;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class LibrarianfilterForge {

    public LibrarianfilterForge(FMLJavaModLoadingContext context) {
        Constants.LOG.info("Initializing {} Forge mod!", Constants.MOD_NAME);
        CommonClass.init();

        RegisterCommandsEvent.BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("reroll")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Librarian Filter is working!"), false);
                    return 1;
                })
                .then(Commands.literal("config")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("Librarian Filter config is active!"), false);
                            return 1;
                        })
                )
        );
        Constants.LOG.info("Registered /reroll command for {}", Constants.MOD_NAME);
    }
}
