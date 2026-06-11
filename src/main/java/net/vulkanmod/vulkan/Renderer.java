package net.vulkanmod.vulkan;

import com.mojang.blaze3d.opengl.GlStateManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.VkGlFramebuffer;
import net.vulkanmod.mixin.window.WindowAccessor;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.buffer.UploadManager;
import net.vulkanmod.render.profiling.Profiler;
import net.vulkanmod.render.texture.ImageUploadHelper;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.pass.DefaultMainPass;
import net.vulkanmod.vulkan.pass.MainPass;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.shader.layout.PushConstants;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.util.VUtil;
import net.vulkanmod.vulkan.util.VkResult;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.vulkanmod.vulkan.Vulkan.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class Renderer {
    private static Renderer INSTANCE;

    private static VkDevice device;

    private static boolean swapChainUpdate = false;
    private static final boolean sync2 = DeviceManager.checkExt(KHRSynchronization2.VK_KHR_SYNCHRONIZATION_2_EXTENSION_NAME);

    public static void initRenderer() {
        INSTANCE = new Renderer();
        INSTANCE.init();
    }

    public static Renderer getInstance() {
        return INSTANCE;
    }

    public static Drawer getDrawer() {
        return INSTANCE.drawer;
    }

    public static int getCurrentFrame() {
        return currentFrame;
    }

    public static int getCurrentImage() {
        return imageIndex;
    }

    private final Set<Pipeline> usedPipelines = new ObjectOpenHashSet<>();
    private Pipeline boundPipeline;
    private long boundPipelineHandle;

    private Drawer drawer;

    private SwapChain swapChain;

    private int framesNum;
    private List<VkCommandBuffer> mainCommandBuffers;
    private ArrayList<Long> imageAvailableSemaphores;
    private ArrayList<Long> renderFinishedSemaphores;
    private long inFlightSubmits;
    private List<CommandPool.CommandBuffer> transferCbs;

    private Framebuffer boundFramebuffer;
    private RenderPass boundRenderPass;

    private static int currentFrame = 0;
    private static int imageIndex;
    private static int lastReset = -1;
    private VkCommandBuffer currentCmdBuffer;
    private boolean recordingCmds = false;
    int recursion = 0;

    MainPass mainPass;

    private final List<Runnable> onResizeCallbacks = new ObjectArrayList<>();

    public Renderer() {
        device = Vulkan.getVkDevice();
        framesNum = Initializer.CONFIG.frameQueueSize;
    }

    public static void setLineWidth(float width) {
        if (INSTANCE.boundFramebuffer == null) {
            return;
        }
        vkCmdSetLineWidth(INSTANCE.currentCmdBuffer, width);
    }

    private void init() {
        MemoryManager.createInstance(Renderer.getFramesNum());
        Vulkan.createStagingBuffers();

        swapChain = new SwapChain();
        mainPass = DefaultMainPass.create();

        drawer = new Drawer();
        drawer.createResources(framesNum);

        Uniforms.setupDefaultUniforms();
        PipelineManager.init();
        UploadManager.createInstance();

        allocateCommandBuffers();
        createSyncObjects();
    }

    private void allocateCommandBuffers() {
        if (mainCommandBuffers != null) {
            mainCommandBuffers.forEach(commandBuffer -> vkFreeCommandBuffers(device, Vulkan.getCommandPool(), commandBuffer));
        }

        mainCommandBuffers = new ArrayList<>(framesNum);

        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(getCommandPool());
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(framesNum);

            PointerBuffer pCommandBuffers = stack.mallocPointer(framesNum);

            int vkResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers);
            if (vkResult != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers: %s".formatted(VkResult.decode(vkResult)));
            }

            for (int i = 0; i < framesNum; i++) {
                mainCommandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), device));
            }
        }

        if (transferCbs != null) {
            transferCbs.forEach(commandBuffer -> {
                vkResetCommandBuffer(commandBuffer.handle, 0);
                commandBuffer.reset();
            });
        }

        transferCbs = new ArrayList<>(framesNum);

        for (int i = 0; i < framesNum; i++) {
            transferCbs.add(DeviceManager.getTransferQueue().getCommandPool().getCommandBuffer());
        }
    }

    private void createSyncObjects() {
        // Render finished semaphore are signaled only after vkQueuePresentKHR has finished execution,
        // only vkAcquireNextImageKHR can guarantee that, hence we need as many semaphores as swapchain images
        int swapChainImages = swapChain.getImagesNum();
        renderFinishedSemaphores = new ArrayList<>(swapChainImages);

        imageAvailableSemaphores = new ArrayList<>(framesNum);

        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
            LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);

            for (int i = 0; i < framesNum; i++) {

                if (vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS) {

                    throw new RuntimeException("Failed to create synchronization objects for the frame: " + i);
                }

                imageAvailableSemaphores.add(pImageAvailableSemaphore.get(0));
            }

            for (int i = 0; i < swapChain.getImagesNum(); ++i) {
                if (vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS) {

                    throw new RuntimeException("Failed to create synchronization objects for the image: " + i);
                }

                renderFinishedSemaphores.add(pRenderFinishedSemaphore.get(0));
            }
        }
    }

    public void preInitFrame() {
        Profiler p = Profiler.getMainProfiler();
        p.pop();
        p.round();
        p.push("Frame_ops");

        drawer.resetBuffers(currentFrame);

        WorldRenderer.getInstance().uploadSections();
        UploadManager.INSTANCE.submitUploads();
    }

    public void beginFrame() {
        this.recursion++;

        if (swapChainUpdate && recursion <= 1) {
            recreateSwapChain();
            swapChainUpdate = false;
        }

        // In case this is a recursive call end prev frame
        if (this.recursion > 1) {
            this.endFrame();
        }

        Profiler p = Profiler.getMainProfiler();
        p.pop();
        p.push("Frame_fence");

        DeviceManager.getGraphicsQueue().waitSubmits(inFlightSubmits);
        // Uses Graphics Timeline as a substitute for inFlightFences
        // Aggregates frame fences and Graphics Queue fences together as one

        p.pop();
        p.push("Begin_rendering");

        submitUploads();

        MemoryManager.getInstance().initFrame(currentFrame);
        drawer.setCurrentFrame(currentFrame);
        Vulkan.getStagingBuffers().beginFrame(currentFrame);

        this.preInitFrame();

        DeviceManager.resetSubmitted();
        resetDescriptors();

        currentCmdBuffer = mainCommandBuffers.get(currentFrame);
        // Cmd implicit reset by RenderPass begin
        try (MemoryStack stack = stackPush()) {
            // Check is swapchain has images before acquiring
            if (swapChain.hasImages()) {
                IntBuffer pImageIndex = stack.mallocInt(1);
                long semaphore = imageAvailableSemaphores.get(currentFrame);

                int vkResult = vkAcquireNextImageKHR(device, swapChain.getId(), VUtil.UINT64_MAX,
                                                     semaphore, VK_NULL_HANDLE, pImageIndex);

                if (vkResult == VK_SUBOPTIMAL_KHR || vkResult == VK_ERROR_OUT_OF_DATE_KHR || swapChainUpdate) {
                    swapChainUpdate = true;
                }
                else if (vkResult != VK_SUCCESS) {
                    throw new RuntimeException("Cannot acquire next swap chain image: %s".formatted(VkResult.decode(vkResult)));
                }

                imageIndex = pImageIndex.get(0);
                swapChain.setAcquired(true);
            }

            this.beginMainRenderPass(stack);
        }

        p.pop();
    }

    private void beginMainRenderPass(MemoryStack stack) {
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
        beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

        VkCommandBuffer commandBuffer = currentCmdBuffer;

        int vkResult = vkBeginCommandBuffer(commandBuffer, beginInfo);
        if (vkResult != VK_SUCCESS) {
            throw new RuntimeException("Failed to begin recording command buffer: %s".formatted(VkResult.decode(vkResult)));
        }

        recordingCmds = true;
        mainPass.begin(commandBuffer, stack);

        resetDynamicState(commandBuffer);
    }

    public void endFrame() {
        if (!recordingCmds)
            return;

        if (this.recursion == 0) {
            return;
        }
        this.recursion--;

        Profiler p = Profiler.getMainProfiler();
        p.push("End_rendering");

        mainPass.end(currentCmdBuffer);

        submitUploads();
        DeviceManager.getGraphicsQueue().executePendingCmds();
        getStagingBuffer().reset();

        submitFrame();
        recordingCmds = false;
        this.boundRenderPass = null;
        this.boundFramebuffer = null;

        p.pop();
        p.push("Post_rendering");
    }

    private void submitFrame() {
        try (MemoryStack stack = stackPush()) {
            // macOS Compat: needed for 1.21.11; due to LWJGL 3.3.3 using outdated MVK version (N/A to 26.1+)
            inFlightSubmits = sync2 ? getSubmitFence2(stack) : getSubmitFence(stack);

            if (swapChain.isAcquired()) {
                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
                presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);

                presentInfo.pWaitSemaphores(stack.longs(renderFinishedSemaphores.get(imageIndex)));

                presentInfo.swapchainCount(1);
                presentInfo.pSwapchains(stack.longs(swapChain.getId()));

                presentInfo.pImageIndices(stack.ints(imageIndex));

                final int vkResult = vkQueuePresentKHR(DeviceManager.getPresentQueue().vkQueue(), presentInfo);

                if (vkResult == VK_ERROR_OUT_OF_DATE_KHR || vkResult == VK_SUBOPTIMAL_KHR || swapChainUpdate) {
                    swapChainUpdate = true;
                    return;
                } else if (vkResult != VK_SUCCESS) {
                    throw new RuntimeException("Failed to present rendered frame: %s".formatted(VkResult.decode(vkResult)));
                }
            }

            Vulkan.getStagingBuffers().endFrame(currentFrame);

            currentFrame = (currentFrame + 1) % framesNum;
            swapChain.setAcquired(false);
        }
    }

    /**
     * Fallback path for macOS compat: N/A if LWJGL 3.3.4+ or later is available (Sync2 support for MVK was added in 3.3.4)
     * (Only used on 1.21.11 on macOS; N/A once 1.21.11 support is dropped)
     */
    @Deprecated
    private long getSubmitFence(MemoryStack stack) {
        Queue graphicsQueue = DeviceManager.getGraphicsQueue();
        Queue transferQueue = DeviceManager.getTransferQueue();
        final long submitFence = graphicsQueue.submitFence();

        final var waitPresent = swapChain.isAcquired();

        final var waitVal = waitPresent ? stack.longs(0, graphicsQueue.submitFence(), transferQueue.submitFence())
                : stack.longs(graphicsQueue.submitFence(), transferQueue.submitFence());

        final var signalVal = waitPresent ? stack.longs(0, graphicsQueue.submitFenceAdd())
                : stack.longs(graphicsQueue.submitFenceAdd());

        var mainSemaphoreSubmitInfo = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                .sType$Default()
                .pWaitSemaphoreValues(waitVal)
                .pSignalSemaphoreValues(signalVal);

        final var waitSemaphores =  waitPresent ? stack.longs(imageAvailableSemaphores.get(currentFrame), graphicsQueue.getQueueSemaphore(), transferQueue.getQueueSemaphore())
                : stack.longs(graphicsQueue.getQueueSemaphore(), transferQueue.getQueueSemaphore());

        final var waitStages = waitPresent ? stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_VERTEX_SHADER_BIT)
                : stack.ints(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_VERTEX_SHADER_BIT);

        var submitInfo = VkSubmitInfo.calloc(stack)
                .sType$Default()
                .pNext(mainSemaphoreSubmitInfo)
                .waitSemaphoreCount(waitPresent ? 3 : 2)
                .pWaitSemaphores(waitSemaphores)
                .pWaitDstStageMask(waitStages) // Frag stage bit used for Animation + LightMap passes
                .pSignalSemaphores(stack.longs(renderFinishedSemaphores.get(imageIndex), graphicsQueue.getQueueSemaphore()))
                .pCommandBuffers(stack.pointers(currentCmdBuffer));

        final int vkResult;
        if ((vkResult = vkQueueSubmit(graphicsQueue.vkQueue(), submitInfo, 0)) != VK_SUCCESS) {
            throw new RuntimeException("Failed to submit draw command buffer: %s".formatted(VkResult.decode(vkResult)));
        }
        return submitFence;
    }

    /**
     *  Default path if Sync2 supported: reduced sync overhead over the Non Sync2 Path
     */
    private long getSubmitFence2(MemoryStack stack) {

        Queue graphicsQueue = DeviceManager.getGraphicsQueue();
        Queue transferQueue = DeviceManager.getTransferQueue();
        final long submitFence = graphicsQueue.submitFence();

        var commandBufferSubmitInfo = VkCommandBufferSubmitInfo.calloc(1, stack)
                .sType$Default()
                .commandBuffer(currentCmdBuffer);

        // Optimize out Host-Side Sync: Wait on "GPU Sync" instead of Host-side
        // i.e. Sync is offloaded to the driver instead of the CPU

        final var waitPresent = swapChain.isAcquired();

        var waitSemaphoreSubmitInfo = VkSemaphoreSubmitInfo.calloc(waitPresent ? 3 : 2, stack);
            waitSemaphoreSubmitInfo.get().sType$Default()
                    .semaphore(graphicsQueue.getQueueSemaphore())
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT) // Animation Pass completion (i.e. LightMap Sampler Pass)
                    .value(graphicsQueue.submitFence());

            waitSemaphoreSubmitInfo.get().sType$Default()
                    .semaphore(transferQueue.getQueueSemaphore())
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT) // Only wait for writes to Vertex Stage UBOs specifically (waits for all other transfers optimized out)
                    .value(transferQueue.submitFence());

        var mainSemaphoreSubmitInfo = VkSemaphoreSubmitInfo.calloc(waitPresent ? 2 : 1, stack);
            mainSemaphoreSubmitInfo.get().sType$Default()
                    .semaphore(graphicsQueue.getQueueSemaphore())
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_NONE)
                    .value(graphicsQueue.submitFenceAdd()); // Submit to graphics queue


        if (waitPresent) {
            waitSemaphoreSubmitInfo.get().sType$Default()
                    .semaphore(imageAvailableSemaphores.get(currentFrame))
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT) // Attachment operations + Transition from present
                    .value(0);

            mainSemaphoreSubmitInfo.get().sType$Default()
                    .semaphore(renderFinishedSemaphores.get(imageIndex))
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT) // Transition to present
                    .value(0);
        }

        var submitInfo = VkSubmitInfo2.calloc(1, stack)
                .sType$Default()
                .pWaitSemaphoreInfos(waitSemaphoreSubmitInfo.rewind())
                .pSignalSemaphoreInfos(mainSemaphoreSubmitInfo.rewind())
                .pCommandBufferInfos(commandBufferSubmitInfo);

        final int vkResult;
        if ((vkResult = KHRSynchronization2.vkQueueSubmit2KHR(graphicsQueue.vkQueue(), submitInfo, 0)) != VK_SUCCESS) {
            throw new RuntimeException("Failed to submit draw command buffer: %s".formatted(VkResult.decode(vkResult)));
        }
        return submitFence;
    }

    /**
     * Called in case draw results are needed before the end of the frame
     */
    public void flushCmds() {
        if (!this.recordingCmds)
            return;

        try (MemoryStack stack = stackPush()) {
            int vkResult;

            this.endRenderPass(currentCmdBuffer);
            vkEndCommandBuffer(currentCmdBuffer);

            final var graphicsQueue = DeviceManager.getGraphicsQueue();
            final var transferQueue = DeviceManager.getTransferQueue();

            graphicsQueue.executePendingCmds();

            var commandBufferSubmitInfo = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(currentCmdBuffer);

            var waitSemaphoreSubmitInfo = VkSemaphoreSubmitInfo.calloc(2, stack);
            waitSemaphoreSubmitInfo.get(0).sType$Default()
                    .semaphore(graphicsQueue.getQueueSemaphore())
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                    .value(graphicsQueue.submitFence());

            waitSemaphoreSubmitInfo.get(1).sType$Default()
                    .semaphore(transferQueue.getQueueSemaphore())
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                    .value(transferQueue.submitFence());

            var mainSemaphoreSubmitInfo = VkSemaphoreSubmitInfo.calloc(1, stack);
            mainSemaphoreSubmitInfo.get(0).sType$Default()
                    .semaphore(graphicsQueue.getQueueSemaphore())
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                    .value(graphicsQueue.submitFenceAdd());

            var submitInfo = VkSubmitInfo2.calloc(1, stack)
                    .sType$Default()
                    .pWaitSemaphoreInfos(waitSemaphoreSubmitInfo)
                    .pSignalSemaphoreInfos(mainSemaphoreSubmitInfo)
                    .pCommandBufferInfos(commandBufferSubmitInfo);

            submitUploads();
            getStagingBuffer().reset();

            if ((vkResult = KHRSynchronization2.vkQueueSubmit2KHR(graphicsQueue.vkQueue(), submitInfo, 0)) != VK_SUCCESS) {
                throw new RuntimeException("Failed to submit draw command buffer: %s".formatted(VkResult.decode(vkResult)));
            }

            graphicsQueue.waitSubmits();
            transferQueue.waitSubmits();

            this.beginMainRenderPass(stack);
        }
    }

    // Synchronization fences optimized out and merged into vkQueueSubmit2 submit Barrier
    // (excluding async transfers to avoid upload desync)
    public void submitUploads() {
        var transferCb = transferCbs.get(currentFrame);

        if (transferCb.isRecording()) {
            final var transferQueue = DeviceManager.getTransferQueue();

            // Selective Host sync; only wait if chunk uploads scheduled
            // (Allows optimizing out sync overhead if no chunk uploads are pending)

            if (UploadManager.INSTANCE.hasSubmit()) {
                transferQueue.waitSubmits();
            }
            transferQueue.addPending(transferCb, VK13.VK_PIPELINE_STAGE_2_COPY_BIT); // Ensure writes are finished before shader reads + main graphics execution (Sync Hazard Fixes)
            transferQueue.executePendingCmds();

            transferCbs.set(currentFrame, transferQueue.getCommandPool().getCommandBuffer());
        }

        ImageUploadHelper.INSTANCE.submitCommands();
    }

    public void endRenderPass() {
        endRenderPass(currentCmdBuffer);
    }

    public void endRenderPass(VkCommandBuffer commandBuffer) {
        if (!recordingCmds || this.boundFramebuffer == null)
            return;

        this.boundRenderPass.endRenderPass(commandBuffer);

        this.boundRenderPass = null;
        this.boundFramebuffer = null;

        VkGlFramebuffer.resetBoundFramebuffer();
    }

    public boolean beginRenderPass(RenderPass renderPass, Framebuffer framebuffer) {
        if (!recordingCmds) {
            this.beginFrame();

            recordingCmds = true;
        }

        if (this.boundFramebuffer != framebuffer) {
            this.endRenderPass(currentCmdBuffer);

            try (MemoryStack stack = stackPush()) {
                framebuffer.beginRenderPass(currentCmdBuffer, renderPass, stack);
            }

            this.boundFramebuffer = framebuffer;
            this.boundRenderPass = renderPass;

            Renderer.setViewportState(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
            Renderer.setScissor(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
        }

        return true;
    }

    public void addUsedPipeline(Pipeline pipeline) {
        usedPipelines.add(pipeline);
    }

    public void removeUsedPipeline(Pipeline pipeline) {
        usedPipelines.remove(pipeline);
    }

    private void resetDescriptors() {
        for (Pipeline pipeline : usedPipelines) {
            pipeline.resetDescriptorPool(currentFrame);
        }

        usedPipelines.clear();
        boundPipeline = null;
        boundPipelineHandle = 0;
    }

    @SuppressWarnings("UnreachableCode")
    private void recreateSwapChain() {
        submitUploads();
        getStagingBuffer().reset();
        Vulkan.waitIdle();

        mainCommandBuffers.forEach(commandBuffer -> vkResetCommandBuffer(commandBuffer, 0));
        recordingCmds = false;

        swapChain.recreate();

        //Semaphores need to be recreated in order to make them unsignaled
        destroySyncObjects();

        int newFramesNum = Initializer.CONFIG.frameQueueSize;

        if (framesNum != newFramesNum) {
            UploadManager.INSTANCE.submitUploads();

            framesNum = newFramesNum;
            MemoryManager.getInstance().freeAllBuffers();
            MemoryManager.createInstance(newFramesNum);
            createStagingBuffers();
            allocateCommandBuffers();

            Pipeline.recreateDescriptorSets(framesNum);

            drawer.createResources(framesNum);
        }

        createSyncObjects();
        this.mainPass.onResize();

        this.onResizeCallbacks.forEach(Runnable::run);
        ((WindowAccessor) (Object) Minecraft.getInstance().getWindow()).getEventHandler().resizeDisplay();

        currentFrame = 0;
    }

    public void cleanUpResources() {
        WorldRenderer.getInstance().cleanUp();
        destroySyncObjects();

        drawer.cleanUpResources();
        mainPass.cleanUp();
        swapChain.cleanUp();

        PipelineManager.destroyPipelines();
        VTextureSelector.getWhiteTexture().free();
    }

    private void destroySyncObjects() {
        for (int i = 0; i < framesNum; ++i) {
            vkDestroySemaphore(device, imageAvailableSemaphores.get(i), null);
        }

        for (int i = 0; i < swapChain.getImagesNum(); ++i) {
            vkDestroySemaphore(device, renderFinishedSemaphores.get(i), null);
        }
    }

    public void addOnResizeCallback(Runnable runnable) {
        this.onResizeCallbacks.add(runnable);
    }

    public void bindGraphicsPipeline(GraphicsPipeline pipeline) {
        VkCommandBuffer commandBuffer = currentCmdBuffer;

        PipelineState currentState = PipelineState.getCurrentPipelineState(boundRenderPass);
        final long handle = pipeline.getHandle(currentState);

        if (boundPipelineHandle == handle) {
            return;
        }

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, handle);
        boundPipelineHandle = handle;
        boundPipeline = pipeline;
        addUsedPipeline(pipeline);
    }

    public void uploadAndBindUBOs(Pipeline pipeline) {
        VkCommandBuffer commandBuffer = currentCmdBuffer;
        pipeline.bindDescriptorSets(commandBuffer, currentFrame);
    }

    public void pushConstants(Pipeline pipeline) {
        VkCommandBuffer commandBuffer = currentCmdBuffer;

        PushConstants pushConstants = pipeline.getPushConstants();

        try (MemoryStack stack = stackPush()) {
            ByteBuffer buffer = stack.malloc(pushConstants.getSize());
            long ptr = MemoryUtil.memAddress0(buffer);
            pushConstants.update(ptr);

            nvkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants.getSize(), ptr);
        }

    }

    public Pipeline getBoundPipeline() {
        return boundPipeline;
    }

    public void setBoundFramebuffer(Framebuffer framebuffer) {
        this.boundFramebuffer = framebuffer;
    }

    public Framebuffer getBoundFramebuffer() {
        return boundFramebuffer;
    }

    public void setBoundRenderPass(RenderPass boundRenderPass) {
        this.boundRenderPass = boundRenderPass;
    }

    public RenderPass getBoundRenderPass() {
        return boundRenderPass;
    }

    public void setMainPass(MainPass mainPass) {
        this.mainPass = mainPass;
    }

    public MainPass getMainPass() {
        return this.mainPass;
    }

    public SwapChain getSwapChain() {
        return swapChain;
    }

    public CommandPool.CommandBuffer getTransferCb() {
        return transferCbs.get(currentFrame);
    }

    private static void resetDynamicState(VkCommandBuffer commandBuffer) {
        vkCmdSetDepthBias(commandBuffer, 0.0F, 0.0F, 0.0F);

        vkCmdSetLineWidth(commandBuffer, 1.0F);
    }

    public static void setDepthBias(float constant, float slope) {
        VkCommandBuffer commandBuffer = INSTANCE.currentCmdBuffer;

        vkCmdSetDepthBias(commandBuffer, constant, 0.0f, slope);
    }

    public static void clearAttachments(int attachments) {
        clearAttachments(INSTANCE.currentCmdBuffer, attachments);
    }

    public static void clearAttachments(VkCommandBuffer commandBuffer, int attachments) {
        Framebuffer framebuffer = Renderer.getInstance().boundFramebuffer;
        if (framebuffer == null)
            return;

        clearAttachments(commandBuffer, attachments, framebuffer.getWidth(), framebuffer.getHeight());
    }

    public static void clearAttachments(int attachments, int width, int height) {
        clearAttachments(INSTANCE.currentCmdBuffer, attachments, width , height);
    }

    public static void clearAttachments(int attachments, int x, int y, int width, int height) {
        clearAttachments(INSTANCE.currentCmdBuffer, attachments, x, y, width , height);
    }

    public static void clearAttachments(VkCommandBuffer commandBuffer, int attachments, int width, int height) {
        clearAttachments(commandBuffer, attachments, 0, 0, width, height);
    }

    public static void clearAttachments(VkCommandBuffer commandBuffer, int attachments, int x, int y, int width, int height) {
        try (MemoryStack stack = stackPush()) {
            //ClearValues have to be different for each attachment to clear,
            //it seems it uses the same buffer: color and depth values override themselves
            VkClearValue colorValue = VkClearValue.calloc(stack);
            colorValue.color().float32(VRenderSystem.clearColor);

            VkClearValue depthValue = VkClearValue.calloc(stack);
            depthValue.depthStencil().set(VRenderSystem.clearDepthValue, 0); //Use fast depth clears if possible

            int attachmentsCount = attachments == (GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT) ? 2 : 1;
            final VkClearAttachment.Buffer pAttachments = VkClearAttachment.malloc(attachmentsCount, stack);
            switch (attachments) {
                case GL_DEPTH_BUFFER_BIT -> {

                    VkClearAttachment clearDepth = pAttachments.get(0);
                    clearDepth.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                    clearDepth.colorAttachment(0);
                    clearDepth.clearValue(depthValue);
                }
                case GL_COLOR_BUFFER_BIT -> {

                    VkClearAttachment clearColor = pAttachments.get(0);
                    clearColor.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                    clearColor.colorAttachment(0);
                    clearColor.clearValue(colorValue);
                }
                case GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT -> {

                    VkClearAttachment clearColor = pAttachments.get(0);
                    clearColor.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                    clearColor.colorAttachment(0);
                    clearColor.clearValue(colorValue);

                    VkClearAttachment clearDepth = pAttachments.get(1);
                    clearDepth.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                    clearDepth.colorAttachment(0);
                    clearDepth.clearValue(depthValue);
                }
                default -> throw new RuntimeException("unexpected value");
            }

            //Rect to clear
            VkRect2D renderArea = VkRect2D.malloc(stack);
            renderArea.offset().set(x, y);
            renderArea.extent().set(width, height);

            VkClearRect.Buffer pRect = VkClearRect.malloc(1, stack);
            pRect.rect(renderArea);
            pRect.baseArrayLayer(0);
            pRect.layerCount(1);

//            vkCmdClearAttachments(commandBuffer, pAttachments, pRect);
        }
    }

    public static void setInvertedViewport(int x, int y, int width, int height) {
        setViewportState(x, y + height, width, -height);
    }

    public static void resetViewport() {
        Framebuffer framebuffer = INSTANCE.getMainPass().getMainFramebuffer();

        if (framebuffer == null) {
            return;
        }

        int width = framebuffer.getWidth();
        int height = framebuffer.getHeight();

        if (width > 0 && height > 0) {
            setViewportState(0, 0, width, height);
        }
    }

    public static void setViewportState(int x, int y, int width, int height) {
        GlStateManager._viewport(x, y, width, height);
    }

    public static void setViewport(int x, int y, int width, int height) {
        try (MemoryStack stack = stackPush()) {
            setViewport(x, y, width, height, stack);
        }
    }

    public static void setViewport(int x, int y, int width, int height, MemoryStack stack) {
        if (!INSTANCE.recordingCmds)
            return;

        VkViewport.Buffer viewport = VkViewport.malloc(1, stack);
        viewport.x(x);
        viewport.y(height + y);
        viewport.width(width);
        viewport.height(-height);
        viewport.minDepth(0.0f);
        viewport.maxDepth(1.0f);

        vkCmdSetViewport(INSTANCE.currentCmdBuffer, 0, viewport);
    }

    public static void setScissor(int x, int y, int width, int height) {
        if (!INSTANCE.recordingCmds || INSTANCE.boundFramebuffer == null)
            return;

        try (MemoryStack stack = stackPush()) {
            Framebuffer framebuffer = INSTANCE.boundFramebuffer;
            int framebufferHeight = framebuffer.getHeight();

            x = Math.max(0, x);
            width = Math.min(width, framebuffer.getWidth());

            VkRect2D.Buffer scissor = VkRect2D.malloc(1, stack);
            scissor.offset().set(x, framebufferHeight - (y + height));
            scissor.extent().set(width, height);

            vkCmdSetScissor(INSTANCE.currentCmdBuffer, 0, scissor);
        }
    }

    public static void resetScissor() {
        if (INSTANCE.boundFramebuffer == null)
            return;

        try (MemoryStack stack = stackPush()) {
            VkRect2D.Buffer scissor = INSTANCE.boundFramebuffer.scissor(stack);
            vkCmdSetScissor(INSTANCE.currentCmdBuffer, 0, scissor);
        }
    }

    public static void pushDebugSection(String s) {
        if (Vulkan.ENABLE_VALIDATION_LAYERS) {
            VkCommandBuffer commandBuffer = INSTANCE.currentCmdBuffer;

            try (MemoryStack stack = stackPush()) {
                VkDebugUtilsLabelEXT markerInfo = VkDebugUtilsLabelEXT.calloc(stack);
                markerInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT);
                ByteBuffer string = stack.UTF8(s);
                markerInfo.pLabelName(string);
                vkCmdBeginDebugUtilsLabelEXT(commandBuffer, markerInfo);
            }
        }
    }

    public static void popDebugSection() {
        if (Vulkan.ENABLE_VALIDATION_LAYERS) {
            VkCommandBuffer commandBuffer = INSTANCE.currentCmdBuffer;

            vkCmdEndDebugUtilsLabelEXT(commandBuffer);
        }
    }

    public static void popPushDebugSection(String s) {
        popDebugSection();
        pushDebugSection(s);
    }

    public static int getFramesNum() {
        return INSTANCE.framesNum;
    }

    public static VkCommandBuffer getCommandBuffer() {
        return INSTANCE.currentCmdBuffer;
    }

    public static boolean isRecording() {
        return INSTANCE.recordingCmds;
    }

    public static void scheduleSwapChainUpdate() {
        swapChainUpdate = true;
    }
}
