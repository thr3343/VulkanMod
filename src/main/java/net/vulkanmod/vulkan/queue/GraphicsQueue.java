package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class GraphicsQueue extends Queue {
    public static GraphicsQueue INSTANCE;

    private static CommandPool.CommandBuffer currentCmdBuffer;

    public GraphicsQueue(MemoryStack stack, int familyIndex) {
        super(stack, familyIndex);
    }

    public void startRecording() {
        currentCmdBuffer = beginCommands();
    }

    public void endRecordingAndSubmit() {
        submitCommands(currentCmdBuffer);
        Synchronization.INSTANCE.addCommandBuffer(currentCmdBuffer);

        currentCmdBuffer = null;
    }

    public CommandPool.CommandBuffer getCommandBuffer() {
        if (currentCmdBuffer != null) {
            return currentCmdBuffer;
        } else {
            return beginCommands();
        }
    }
    //true if submitted
    public boolean endIfNeeded(CommandPool.CommandBuffer commandBuffer) {
        if (currentCmdBuffer != null) {
            submitCommands(commandBuffer);
        }
        return currentCmdBuffer != null;
    }

}
