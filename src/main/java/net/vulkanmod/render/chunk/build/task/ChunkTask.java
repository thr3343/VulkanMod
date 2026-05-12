package net.vulkanmod.render.chunk.build.task;

import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.RenderRegion;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import org.joml.Vector3d;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ChunkTask {
    public static final boolean BENCH = true;

    protected static TaskDispatcher taskDispatcher;

    public static BuildTask createBuildTask(RenderSection renderSection, RenderRegion renderRegion, Vector3d cameraPos, boolean highPriority) {
        return new BuildTask(renderSection, renderRegion, cameraPos, highPriority);
    }

    public static SortTransparencyTask createSortTask(RenderSection renderSection, Vector3d cameraPos) {
        return new SortTransparencyTask(renderSection, cameraPos);
    }

    protected final RenderSection section;
    protected final Vector3d cameraPos;
    protected AtomicBoolean cancelled = new AtomicBoolean(false);
    public boolean highPriority = false;

    ChunkTask(RenderSection renderSection, Vector3d cameraPos) {
        this.section = renderSection;
        this.cameraPos = cameraPos;
    }

    public abstract String name();

    public abstract Result runTask(BuilderResources builderResources);

    public void cancel() {
        this.cancelled.set(true);
    }

    public static void setTaskDispatcher(TaskDispatcher dispatcher) {
        taskDispatcher = dispatcher;
    }
    
    public enum Result {
        CANCELLED,
        SUCCESSFUL
    }
}
