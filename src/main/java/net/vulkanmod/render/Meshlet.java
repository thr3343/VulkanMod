package net.vulkanmod.render;

public class Meshlet {
    //TOOD: COuld cal tis ChunkMEshletSegemnt or MeshletChunkSugment or anytohrer combination,but want to move away from the cocept of rendering/CHunks/Segemnst e.g.
    int parentChynk;
    int indexCount;
    int vertexCount;
    //Aga we don;t care whee the chunk setcionbbegins or ends anymore, we decide were he drawcall beins/e,d/mi/max determined e.g.
    int SubGroupIndex;

}