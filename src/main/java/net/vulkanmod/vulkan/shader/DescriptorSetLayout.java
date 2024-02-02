package net.vulkanmod.vulkan.shader;

import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.vulkan.DeviceManager;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorSetLayout {

    //Exposes the internal structure of the vkDescriptorSetLayout handle/layout
    final long vkDescriptorSetLayout;

    final int descriptorCount;
//Defaults:
    //Vert UBO==0
    //Frag UBO==1
    //sampler0==2
    //sampler1==3
    //sampler2==4,3,2
    @Override
    public boolean equals(Object obj) {
        //Like RenderPasses, Handles do not have to match: only the bindings
        //Allowing for some abstraction.Modularity like RenderPasses as well

        if(getClass() != obj.getClass()) return false;

        DescriptorSetLayout that = (DescriptorSetLayout)obj;
        //TODO; Allow matching/Equivalency checks for mismatched Layout Binding lengths
        DescriptorSetLayoutBinding[] defsBindings = that.descritprDefsBindings;
        if(this.descritprDefsBindings.length!=defsBindings.length) return false;
        for (int i = 0; i < defsBindings.length; i++) {
            var a = defsBindings[i];
            if (a!=this.descritprDefsBindings[i])
            {
                return false;
            }
        }
        return true;


    }

    //    private final StaticQueue<DescriptorSetLayoutBinding> UniformDescriptors = new StaticQueue<>(4);
    private final DescriptorSetLayoutBinding[] descritprDefsBindings;


    public DescriptorSetLayout(DescriptorSetLayoutBinding... descritprDefsBindings) {
        this.vkDescriptorSetLayout = createDescriptorSet();
        this.descritprDefsBindings=descritprDefsBindings;
        descriptorCount = descritprDefsBindings.length;
    }

    private long createDescriptorSet(DescriptorSetLayoutBinding... descritprDefsBindings) {
        try (MemoryStack stack = stackPush()) {


            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.malloc(descritprDefsBindings.length, stack);
            int i=0;
            for (VkDescriptorSetLayoutBinding descriptorDesc : bindings) {
                var samplerLayoutBinding = descritprDefsBindings[i++];
                descriptorDesc.set(samplerLayoutBinding.binding(),
                        samplerLayoutBinding.descriptorType(),
                        samplerLayoutBinding.descriptorCount(),
                        samplerLayoutBinding.stageFlags(),
                        null);
            }

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack);
            layoutInfo.sType$Default();
            layoutInfo.pBindings(bindings);

            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

            if (vkCreateDescriptorSetLayout(DeviceManager.device, layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout");
            }

            return pDescriptorSetLayout.get(0);
        }
    }


    public DescriptorSetLayoutBinding getBinding(int i)
    {
        return this.descritprDefsBindings[i];
    }
}
