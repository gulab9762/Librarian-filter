package com.gbdhapa;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PackTest {
    @Test
    public void testPacks() {
        PackRepository repo = ServerPacksSource.createPackRepository(null);
        repo.reload();
        for (String id : repo.getAvailableIds()) {
            System.out.println("PACK: " + id);
        }
    }
}
