package net.vulkanmod.vulkan.framebuffer;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.render.util.MathUtil;
import net.vulkanmod.vulkan.DeviceManager;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.vulkanmod.vulkan.DeviceManager.device;
import static net.vulkanmod.vulkan.Vulkan.*;
import static net.vulkanmod.vulkan.util.VUtil.UINT32_MAX;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class SwapChain extends Framebuffer {
    private static final int DEFAULT_IMAGE_COUNT = 3;

    //Necessary until tearing-control-unstable-v1 is fully implemented on all GPU Drivers for Wayland
    //(As Immediate Mode (and by extension Screen tearing) doesn't exist on most Wayland installations currently)
    //Try to use Mailbox if possible (in case FreeSync/G-Sync needs it)
    private static final int defUncappedMode = checkPresentMode(VK_PRESENT_MODE_MAILBOX_KHR, VK_PRESENT_MODE_IMMEDIATE_KHR);

    private RenderPass mainRenderPass;
    private long[] mainFBOs;

    private Long2ReferenceOpenHashMap<long[]> FBO_map = new Long2ReferenceOpenHashMap<>();

    private long swapChainId = VK_NULL_HANDLE;
    private List<VulkanImage> swapChainImages;
    private VkExtent2D extent2D;
    public boolean isBGRAformat;
    private boolean vsync = false;

    private int[] glIds;

    public SwapChain() {
        this.attachmentCount = 2;

        this.depthFormat = Vulkan.getDefaultDepthFormat();
        createSwapChain();
    }

    public void recreateSwapChain() {
        Synchronization.INSTANCE.waitFences();

        if(this.depthAttachment != null) {
            this.depthAttachment.free();
            this.depthAttachment = null;
        }

        if(!DYNAMIC_RENDERING && this.mainFBOs != null) {
//            this.renderPass.cleanUp();
            Arrays.stream(this.mainFBOs).forEach(id -> vkDestroyFramebuffer(getDevice(), id, null));
            this.mainFBOs = null;

            this.FBO_map.forEach((pass, framebuffers) -> Arrays.stream(framebuffers).forEach(id -> vkDestroyFramebuffer(getDevice(), id, null)));
            this.FBO_map.clear();
        }

        createSwapChain();
    }

    public void createSwapChain() {

        try(MemoryStack stack = stackPush()) {
            VkDevice device = Vulkan.getDevice();
            DeviceManager.SurfaceProperties surfaceProperties = DeviceManager.querySurfaceProperties(device.getPhysicalDevice(), stack);

            VkSurfaceFormatKHR surfaceFormat = getFormat(surfaceProperties.formats);
            int presentMode = getPresentMode(surfaceProperties.presentModes);
            VkExtent2D extent = getExtent(surfaceProperties.capabilities);

            if(extent.width() == 0 && extent.height() == 0) {
                if(this.swapChainId != VK_NULL_HANDLE) {
                    this.swapChainImages.forEach(image -> vkDestroyImageView(device, image.getImageView(), null));
                    vkDestroySwapchainKHR(device, this.swapChainId, null);
                    this.swapChainId = VK_NULL_HANDLE;
                }

                this.width = 0;
                this.height = 0;
                return;
            }

            //minImageCount depends on driver: Mesa/RADV needs a min of 4, but most other drivers are at least 2 or 3
            //TODO using FIFO present mode with image num > 2 introduces (unnecessary) input lag
            int requestedImages = Math.max(DEFAULT_IMAGE_COUNT, surfaceProperties.capabilities.minImageCount());

            IntBuffer imageCount = stack.ints(requestedImages);

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
            createInfo.surface(Vulkan.getSurface());

            // Image settings
            this.format = surfaceFormat.format();
            this.extent2D = VkExtent2D.create().set(extent);

            createInfo.minImageCount(requestedImages);
            createInfo.imageFormat(this.format);
            createInfo.imageColorSpace(surfaceFormat.colorSpace());
            createInfo.imageExtent(extent);
            createInfo.imageArrayLayers(1);
//            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);

            Queue.QueueFamilyIndices indices = Queue.getQueueFamilies();

            if(!indices.graphicsFamily.equals(indices.presentFamily)) {
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
                createInfo.pQueueFamilyIndices(stack.ints(indices.graphicsFamily, indices.presentFamily));
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            createInfo.preTransform(surfaceProperties.capabilities.currentTransform());
            createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
            createInfo.presentMode(presentMode);
            createInfo.clipped(true);

            createInfo.oldSwapchain(this.swapChainId);

            LongBuffer pSwapChain = stack.longs(VK_NULL_HANDLE);

            if(vkCreateSwapchainKHR(device, createInfo, null, pSwapChain) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create swap chain");
            }

            if(this.swapChainId != VK_NULL_HANDLE) {
                this.swapChainImages.forEach(iamge -> vkDestroyImageView(device, iamge.getImageView(), null));
                vkDestroySwapchainKHR(device, this.swapChainId, null);
            }

            this.swapChainId = pSwapChain.get(0);

            vkGetSwapchainImagesKHR(device, this.swapChainId, imageCount, null);

            LongBuffer pSwapchainImages = stack.mallocLong(imageCount.get(0));

            vkGetSwapchainImagesKHR(device, this.swapChainId, imageCount, pSwapchainImages);

            this.swapChainImages = new ArrayList<>(imageCount.get(0));

            this.width = extent2D.width();
            this.height = extent2D.height();

            for(int i = 0; i < pSwapchainImages.capacity(); i++) {
                long imageId = pSwapchainImages.get(i);
                long imageView = VulkanImage.createImageView(imageId, this.format, VK_IMAGE_ASPECT_COLOR_BIT, 1);

                this.swapChainImages.add(new VulkanImage(imageId, this.format, 1, this.width, this.height, 4, 0, imageView));
            }

            createGlIds();

            createDepthResources();

            //RenderPass
            if(this.mainRenderPass == null)
                createRenderPass();

            if(!DYNAMIC_RENDERING)
                this.mainFBOs = createFramebuffers(this.mainRenderPass);

        }
    }

    private void createGlIds() {
        this.glIds = new int[this.swapChainImages.size()];

        for (int i = 0; i < this.swapChainImages.size(); i++) {
            int id = GlTexture.genTextureId();
            this.glIds[i] = id;
            GlTexture.bindIdToImage(id, this.swapChainImages.get(i));
        }
    }

    public int getColorAttachmentGlId() {
        return this.glIds[Renderer.getCurrentImage()];
    }

    private void createRenderPass() {
        this.hasColorAttachment = true;
        this.hasDepthAttachment = true;

        RenderPass.Builder builder = RenderPass.builder(this);
//        builder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        builder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        builder.getColorAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_STORE);
        builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_STORE);

        this.mainRenderPass = builder.build();
    }

    private long[] createFramebuffers(RenderPass renderPass) {

        try(MemoryStack stack = MemoryStack.stackPush()) {

            long[] framebuffers = new long[this.swapChainImages.size()];

            for(int i = 0; i < this.swapChainImages.size(); ++i) {

//                LongBuffer attachments = stack.longs(imageViews.get(i), depthAttachment.getImageView());
                LongBuffer attachments = stack.longs(this.swapChainImages.get(i).getImageView(), this.depthAttachment.getImageView());

                //attachments = stack.mallocLong(1);
                LongBuffer pFramebuffer = stack.mallocLong(1);

                VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
                framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
                framebufferInfo.renderPass(renderPass.getId());
                framebufferInfo.width(this.width);
                framebufferInfo.height(this.height);
                framebufferInfo.layers(1);
                framebufferInfo.pAttachments(attachments);

                if(vkCreateFramebuffer(Vulkan.getDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create framebuffer");
                }

                framebuffers[i] = pFramebuffer.get(0);
            }

            return framebuffers;
        }
    }

    public void beginRenderPass(VkCommandBuffer commandBuffer, RenderPass renderPass, MemoryStack stack) {
        if(!DYNAMIC_RENDERING) {
            long[] framebufferId = this.FBO_map.computeIfAbsent(renderPass.id, renderPass1 -> createFramebuffers(renderPass));
            renderPass.beginRenderPass(commandBuffer, framebufferId[Renderer.getCurrentImage()], stack);
        }
        else
            renderPass.beginDynamicRendering(commandBuffer, stack);

        Renderer.getInstance().setBoundRenderPass(renderPass);
        Renderer.getInstance().setBoundFramebuffer(this);
    }

    public void beginRenderPass(VkCommandBuffer commandBuffer, MemoryStack stack) {
        if(DYNAMIC_RENDERING) {
//            this.colorAttachmentLayout(stack, commandBuffer, Drawer.getCurrentFrame());
//            beginDynamicRendering(commandBuffer, stack);

            this.mainRenderPass.beginDynamicRendering(commandBuffer, stack);
        }
        else {
            this.mainRenderPass.beginRenderPass(commandBuffer, this.mainFBOs[Renderer.getCurrentImage()], stack);
        }

        Renderer.getInstance().setBoundRenderPass(this.mainRenderPass);
        Renderer.getInstance().setBoundFramebuffer(this);
    }

    public void colorAttachmentLayout(MemoryStack stack, VkCommandBuffer commandBuffer, int imageIdx) {
        VulkanImage image = this.swapChainImages.get(imageIdx);

        if (image.getCurrentLayout() == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            return;

        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        barrier.oldLayout(image.getCurrentLayout());
        barrier.newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        barrier.image(image.getId());
//            barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);

        barrier.subresourceRange().baseMipLevel(0);
        barrier.subresourceRange().levelCount(1);
        barrier.subresourceRange().baseArrayLayer(0);
        barrier.subresourceRange().layerCount(1);

        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);

        barrier.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

        vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,  // srcStageMask
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, // dstStageMask
                0,
                null,
                null,
                barrier// pImageMemoryBarriers
        );

        image.setCurrentLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
    }

    public void presentLayout(MemoryStack stack, VkCommandBuffer commandBuffer, int imageIdx) {
        VulkanImage image = this.swapChainImages.get(imageIdx);

        if (image.getCurrentLayout() == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            return;

        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        barrier.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        barrier.dstAccessMask(0);
        barrier.oldLayout(image.getCurrentLayout());
        barrier.newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        barrier.image(image.getId());

        barrier.subresourceRange().baseMipLevel(0);
        barrier.subresourceRange().levelCount(1);
        barrier.subresourceRange().baseArrayLayer(0);
        barrier.subresourceRange().layerCount(1);

        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);

        vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,  // srcStageMask
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, // dstStageMask
                0,
                null,
                null,
                barrier// pImageMemoryBarriers
        );

        image.setCurrentLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
    }

    public void readOnlyLayout(MemoryStack stack, VkCommandBuffer commandBuffer, int imageIdx) {
        VulkanImage image = this.swapChainImages.get(imageIdx);

        if (image.getCurrentLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            return;

        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        barrier.oldLayout(image.getCurrentLayout());
        barrier.newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        barrier.image(image.getId());
//            barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);

        barrier.subresourceRange().baseMipLevel(0);
        barrier.subresourceRange().levelCount(1);
        barrier.subresourceRange().baseArrayLayer(0);
        barrier.subresourceRange().layerCount(1);

        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);

        barrier.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

        vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,  // srcStageMask
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, // dstStageMask
                0,
                null,
                null,
                barrier// pImageMemoryBarriers
        );

        image.setCurrentLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    public void cleanUp() {
        VkDevice device = Vulkan.getDevice();

        this.mainRenderPass.cleanUp();

        if(!DYNAMIC_RENDERING) {
            Arrays.stream(this.mainFBOs).forEach(id -> vkDestroyFramebuffer(device, id, null));
        }

        vkDestroySwapchainKHR(device, this.swapChainId, null);
        this.swapChainImages.forEach(image -> vkDestroyImageView(device, image.getImageView(), null));

        this.depthAttachment.free();
    }

    private void createDepthResources() {
        this.depthAttachment = VulkanImage.createDepthImage(depthFormat, this.width, this.height,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                false, false);
    }

    public long getId() {
        return this.swapChainId;
    }

    public List<VulkanImage> getImages() {
        return this.swapChainImages;
    }

    public long getImageId(int i) {
        return this.swapChainImages.get(i).getId();
    }

    public VkExtent2D getExtent() {
        return this.extent2D;
    }

    public VulkanImage getColorAttachment() {
        return this.swapChainImages.get(Renderer.getCurrentImage());
    }

    public long getImageView(int i) { return this.swapChainImages.get(i).getImageView(); }

    private VkSurfaceFormatKHR getFormat(VkSurfaceFormatKHR.Buffer availableFormats) {
        List<VkSurfaceFormatKHR> list = availableFormats.stream().toList();

        VkSurfaceFormatKHR format = list.get(0);

        for (VkSurfaceFormatKHR availableFormat : list) {
            if (availableFormat.format() == VK_FORMAT_R8G8B8A8_UNORM && availableFormat.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                return availableFormat;

            if (availableFormat.format() == VK_FORMAT_B8G8R8A8_UNORM && availableFormat.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                format = availableFormat;
            }
        }

        if(format.format() == VK_FORMAT_B8G8R8A8_UNORM)
            isBGRAformat = true;
        return format;
    }

    private int getPresentMode(IntBuffer availablePresentModes) {
        int requestedMode = vsync ? VK_PRESENT_MODE_FIFO_KHR : defUncappedMode;

        //fifo mode is the only mode that has to be supported
        if(requestedMode == VK_PRESENT_MODE_FIFO_KHR)
            return VK_PRESENT_MODE_FIFO_KHR;

        for(int i = 0;i < availablePresentModes.capacity();i++) {
            if(availablePresentModes.get(i) == requestedMode) {
                return requestedMode;
            }
        }

        Initializer.LOGGER.warn("Requested mode not supported: using fallback VK_PRESENT_MODE_FIFO_KHR");
        return VK_PRESENT_MODE_FIFO_KHR;

    }

    private static VkExtent2D getExtent(VkSurfaceCapabilitiesKHR capabilities) {

        if(capabilities.currentExtent().width() != UINT32_MAX) {
            return capabilities.currentExtent();
        }

        //Fallback
        IntBuffer width = stackGet().ints(0);
        IntBuffer height = stackGet().ints(0);

        glfwGetFramebufferSize(window, width, height);

        VkExtent2D actualExtent = VkExtent2D.mallocStack().set(width.get(0), height.get(0));

        VkExtent2D minExtent = capabilities.minImageExtent();
        VkExtent2D maxExtent = capabilities.maxImageExtent();

        actualExtent.width(MathUtil.clamp(minExtent.width(), maxExtent.width(), actualExtent.width()));
        actualExtent.height(MathUtil.clamp(minExtent.height(), maxExtent.height(), actualExtent.height()));

        return actualExtent;
    }

    private static int checkPresentMode(int... requestedModes) {
        try(MemoryStack stack = MemoryStack.stackPush())
        {
            var a = DeviceManager.querySurfaceProperties(device.getPhysicalDevice(), stack).presentModes;
            for(int dMode : requestedModes) {
                for (int i = 0; i < a.capacity(); i++) {
                    if (a.get(i) == dMode) {
                        return dMode;
                    }
                }
            }
            return VK_PRESENT_MODE_FIFO_KHR; //If None of the request modes exist/are supported by Driver
        }
    }

    public boolean isVsync() {
        return this.vsync;
    }

    public void setVsync(boolean vsync) {
        this.vsync = vsync;
    }

    public RenderPass getMainRenderPass() {
        return this.mainRenderPass;
    }
    public int getImagesNum() { return this.swapChainImages.size(); }
}
