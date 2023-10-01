package net.vulkanmod.render;



public class Mesh {
    private static final int maxVertexBudget = 98304;
    Meshlet[] meshletAry = new Meshlet[512];

    private int currentVertexLim; //Never Decrement
    private int remLRelVertexLim; //Decreme rem rem used to sceratin ste or orotional relavetsing osfsfetdetemiacy of Candiate splits

    private int hierarchalLevel;
    /*//TODO:  Triangle Bin/Batch Based On DispatchSize (ie.. e.g . l.128 Triangles);
        //TOOD: Coaleasing dispatches (cant just limit contuity to one ChunkSection)
        //TODO EXplait Virual Addressing/Allocation./SUballoctaions so
            *Hopefully we can "Swap" to highr levelBucket list slots, ksokbin maskhierahcallisstkdvskdtrbica allowin SVirtual suballoctaions bfits to be maitained, wihoturnig intciincuring ill suballocation COllisons Derivatve markets2 -. 23-4-56
            allsow allwing merg caidaioenOperation to occur as well
            +We need to copy the neusumoric Section as well Anyway
            +IDeally would liketo miimise vertexFetch latency as well
                Might Matter on Newer GPUs as well
                Advanategs fo Dicthign VirualBuffer/Allocators:
                    . Can COpy nearInstantly wiotu worying r carin about the vertexoffsets anymore,
                    Meginbatchignidverdgencvergece is much esierno Native calls
                    No Fargemntation! (hopefully)

            * Cant Deice weather whether to DO DObules, Quads or Tctrees rn

    */
    void addMeshlet(){};

    void verfState(){};
    void Splt(){};
    void Merge(){};
}