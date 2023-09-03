package net.vulkanmod.render.chunk.build;

import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.*;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Queue;


public class TaskDispatcher {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int availableProcessors = Runtime.getRuntime().availableProcessors();
    private int highPriorityQuota = 2;

    private final Queue<Runnable> toUpload = Queues.newLinkedBlockingDeque();
    public final ThreadBuilderPack fixedBuffers;

    //TODO volatile?
    public volatile boolean stopThreads;
    private Thread[] threads = new Thread[Initializer.CONFIG.chunkLoadFactor];
    private int idleThreads;
    private final Queue<ChunkTask> highPriorityTasks = Queues.newConcurrentLinkedQueue();
    private final Queue<ChunkTask> lowPriorityTasks = Queues.newConcurrentLinkedQueue();
    //    public boolean idle=false;
    private int availableJobSlots = threads.length;

    public TaskDispatcher() {
        this.fixedBuffers = new ThreadBuilderPack();
        Arrays.setAll(threads, i -> new Thread(
                () -> runTaskThread(new ThreadBuilderPack())));
        this.stopThreads = true;
    }

    public void resizeThreads(int size) {
        if(size==this.threads.length) return;
        this.threads=new Thread[size];
        Arrays.setAll(threads, i -> new Thread(
                () -> runTaskThread(new ThreadBuilderPack())));
        availableJobSlots=threads.length;
    }

    public void initThreads() {
        if(!this.stopThreads)
            return;

        this.stopThreads = false;


        for(var thread: threads) {;
            LOGGER.info("INVOKE"+ thread.getState());
            thread.start();
        }
    }

    private void runTaskThread(ThreadBuilderPack builderPack) {
        while(!stopThreads) {
            ChunkTask task1 = (this.highPriorityTasks.isEmpty()? this.lowPriorityTasks : this.highPriorityTasks).poll();
            if(task1!=null)
            {
                task1.doTask(builderPack);
            }
            else
            {
                //Avoid busy wait (may cause loading perf overhead.regression)
                //seems to cause deadlock/contention issues with flickering Segment Allocations
                availableJobSlots++;
                synchronized (this){
                    try {
                        Thread.onSpinWait();
                        this.wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public void schedule(ChunkTask chunkTask) {
        if(chunkTask == null)
            return;

        (chunkTask.highPriority ? this.highPriorityTasks : this.lowPriorityTasks).offer(chunkTask);

        //TODO Scale number of launched threads based on available workGroupSlots (to avoid stuttering with Small/incremental chunk loads)
        //Wakeup thread
        synchronized (this) {
            this.notify();
            availableJobSlots++;
//            idle=false;
        }
    }

    public void stopThreads() {
        if(this.stopThreads)
            return;

        this.stopThreads = true;


//        LOGGER.info(String.valueOf(this.threads.getState()));
        for(var thread : threads) {
            LOGGER.info("JOIN"+ thread.getState());
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
        }

    }

    public boolean uploadAllPendingUploads() {

        boolean flag = !this.toUpload.isEmpty();
        while(!this.toUpload.isEmpty()) {
            this.toUpload.poll().run();
        }

        AreaUploadManager.INSTANCE.submitUploads();

        return flag;
    }

    public void scheduleSectionUpdate(RenderSection section, EnumMap<TerrainRenderType, UploadBuffer> uploadBuffers) {
        this.toUpload.add(
                () -> this.doSectionUpdate(section, uploadBuffers)
        );
    }

    private void doSectionUpdate(RenderSection section, EnumMap<TerrainRenderType, UploadBuffer> uploadBuffers) {
        ChunkArea renderArea = section.getChunkArea();
        DrawBuffers drawBuffers = renderArea.getDrawBuffers();

        for(TerrainRenderType renderType : TerrainRenderType.VALUES) {
            UploadBuffer uploadBuffer = uploadBuffers.get(renderType);

            if(uploadBuffer != null) {
                drawBuffers.upload(uploadBuffer, section.getDrawParameters(renderType));
            } else {
                section.getDrawParameters(renderType).reset(renderArea);
            }
        }
    }

    public void scheduleUploadChunkLayer(RenderSection section, TerrainRenderType renderType, UploadBuffer uploadBuffer) {
        this.toUpload.add(
                () -> this.doUploadChunkLayer(section, renderType, uploadBuffer)
        );
    }

    private void doUploadChunkLayer(RenderSection section, TerrainRenderType renderType, UploadBuffer uploadBuffer) {
        ChunkArea renderArea = section.getChunkArea();
        DrawBuffers drawBuffers = renderArea.getDrawBuffers();

        drawBuffers.upload(uploadBuffer, section.getDrawParameters(renderType));
    }

    public int getIdleThreadsCount() {
        return this.availableJobSlots;
    }

    public void clearBatchQueue() {
        while(!this.highPriorityTasks.isEmpty()) {
            ChunkTask chunkTask = this.highPriorityTasks.poll();
            if (chunkTask != null) {
                chunkTask.cancel();
            }
        }

        while(!this.lowPriorityTasks.isEmpty()) {
            ChunkTask chunkTask = this.lowPriorityTasks.poll();
            if (chunkTask != null) {
                chunkTask.cancel();
            }
        }

//        this.toBatchCount = 0;
    }

    public String getStats() {
//        this.toBatchCount = this.highPriorityTasks.size() + this.lowPriorityTasks.size();
//        return String.format("tB: %03d, toUp: %02d, FB: %02d", this.toBatchCount, this.toUpload.size(), this.freeBufferCount);
        return String.format("iT: %d", this.availableJobSlots);
    }
}
