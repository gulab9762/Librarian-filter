package net.gbdhapa;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public class RerollCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("elt")
                    .then(Commands.literal("setup")
                            .executes(context -> executeSetup(context.getSource()))
                    )
            );
        });
    }

    private static int executeSetup(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());

        // Build 3x3 glass room
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) == 2 || Math.abs(z) == 2 || y == 2) {
                        level.setBlockAndUpdate(center.offset(x, y, z), Blocks.GLASS.defaultBlockState());
                    } else {
                        level.setBlockAndUpdate(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        // Place Lectern
        BlockPos lecternPos = center.offset(1, 0, 0);
        level.setBlockAndUpdate(lecternPos, Blocks.LECTERN.defaultBlockState());

        // Place Wall Sign
        BlockPos signPos = center.offset(0, 1, 0);
        net.minecraft.world.level.block.state.BlockState signState = Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, net.minecraft.core.Direction.WEST);
        level.setBlockAndUpdate(signPos, signState);

        if (level.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
            signEntity.updateText(sign -> sign.setMessage(0, net.minecraft.network.chat.Component.literal("mending 1 30")), true);
        }

        // Spawn villager
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        level.addFreshEntity(villager);

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Testing room setup complete!"), false);
        return 1;
    }
}
