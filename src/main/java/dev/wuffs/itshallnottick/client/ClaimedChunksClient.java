package dev.wuffs.itshallnottick.client;

import dev.wuffs.itshallnottick.network.ChunkDimPos;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

import java.util.ArrayList;
import java.util.List;

public class ClaimedChunksClient {
    private static ClaimedChunksClient instance;

    private final List<ChunkDimPos> claimedChunkList = new ArrayList<>();
    private final Object2BooleanMap<ChunkDimPos> claimedCache = new Object2BooleanOpenHashMap<>();

    public static ClaimedChunksClient getInstance() {
        if (instance == null) {
            instance = new ClaimedChunksClient();
        }

        return instance;
    }

    public List<ChunkDimPos> getClaimedChunks() {
        return this.getClaimedChunks();
    }

    public boolean isChunkClaimed(ChunkDimPos pos) {
        return claimedCache.computeIfAbsent(pos, key -> claimedChunkList.stream().anyMatch(a -> a.equals(pos)));
    }

    // TODO: make this system better by diffing the lists instead of just updating the entire list
    public void removeChunk(ChunkDimPos pos) {
        this.claimedChunkList.remove(pos);
        this.claimedCache.removeBoolean(pos);
    }

    // TODO: make this system better by diffing the lists instead of just updating the entire list
    public void addChunk(ChunkDimPos pos) {
        this.claimedChunkList.add(pos);
        this.claimedCache.put(pos, true);
    }

    public void updateChunks(List<ChunkDimPos> chunks) {
        this.claimedChunkList.clear();
        this.claimedCache.clear();

        this.claimedChunkList.addAll(chunks);
    }
}
