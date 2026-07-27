package net.vulkanmod.vulkan.shader.converter;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

public record SpirvShader(
		String name,
		@Nullable ByteBuffer spirv,
		List<SpvUniformBuffer> uniformBuffers,
		List<SpvSampler> samplers,
		List<SpvVariable> outputs,
		List<SpvVariable> inputs
) implements AutoCloseable {

	public static final SpirvShader INVALID = new SpirvShader(
			"invalid", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
	);

	public static SpirvShader createFromSpirv(final String filename, final ByteBuffer spirv) throws ShaderCompileException {
		List<SpvUniformBuffer> uniformBuffers = new ArrayList<>();
		List<SpvSampler> samplers = new ArrayList<>();
		List<SpvVariable> outputs = new ArrayList<>();
		List<SpvVariable> inputs = new ArrayList<>();

		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pointer = stack.callocPointer(1);
			checkResult(Spvc.spvc_context_create(pointer), "Couldn't create spvc context");
			long context = pointer.get(0);

			try {
				checkResult(Spvc.spvc_context_parse_spirv(context, spirv.asIntBuffer(), spirv.remaining() / 4, pointer), "Couldn't parse spirv");
				long ir = pointer.get(0);
				checkResult(Spvc.spvc_context_create_compiler(context, 0, ir, 1, pointer), "Couldn't create compiler");
				long compiler = pointer.get(0);
				checkResult(Spvc.spvc_compiler_create_shader_resources(compiler, pointer), "Couldn't create resource list");
				long spvcResources = pointer.get(0);

				uniformBuffers.addAll(extractUniformBuffers(compiler, spvcResources, stack, spirv));
				samplers.addAll(extractSamplers(compiler, spvcResources, stack, spirv));
				outputs.addAll(extractVariables(compiler, spvcResources, stack, spirv,
				                                Spvc.SPVC_RESOURCE_TYPE_STAGE_OUTPUT));
				inputs.addAll(extractVariables(compiler, spvcResources, stack, spirv,
				                               Spvc.SPVC_RESOURCE_TYPE_STAGE_INPUT));
			} finally {
				Spvc.spvc_context_destroy(context);
			}
		}

		return new SpirvShader(filename, spirv, uniformBuffers, samplers, outputs, inputs);
	}

	private static List<SpirvShader.SpvUniformBuffer> extractUniformBuffers(long compiler, long resources,
	                                                                         MemoryStack stack, ByteBuffer spirv) throws ShaderCompileException {
		List<SpirvShader.SpvUniformBuffer> result = new ArrayList<>();
		PointerBuffer pointer = stack.callocPointer(1);
		PointerBuffer countPointer = stack.callocPointer(1);
		IntBuffer intReturnBuffer = stack.callocInt(1);

		long list = getResourceList(resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, pointer, countPointer);
		int count = (int) countPointer.get(0);
		var resourcesList = SpvcReflectedResource.create(list, count);

		for (int i = 0; i < count; i++) {
			var ub = resourcesList.get(i);
			String name = ub.nameString();
			int bindingOffset = getDecorationOffset(compiler, ub, Spv.SpvDecorationBinding, intReturnBuffer);

			long type = Spvc.spvc_compiler_get_type_handle(compiler, ub.base_type_id());
			PointerBuffer sizePtr = stack.callocPointer(1);
			checkResult(Spvc.spvc_compiler_get_declared_struct_size(compiler, type, sizePtr),
			            "Couldn't retrieve struct size for '%s'".formatted(name));

			result.add(new SpirvShader.SpvUniformBuffer(spirv, name, (int) sizePtr.get(0), bindingOffset));
		}
		return result;
	}

	private static List<SpirvShader.SpvSampler> extractSamplers(long compiler, long resources,
	                                                             MemoryStack stack, ByteBuffer spirv) throws ShaderCompileException {
		List<SpirvShader.SpvSampler> result = new ArrayList<>();
		PointerBuffer pointer = stack.callocPointer(1);
		PointerBuffer countPointer = stack.callocPointer(1);
		IntBuffer intReturnBuffer = stack.callocInt(1);

		long list = getResourceList(resources, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE, pointer, countPointer);
		int count = (int) countPointer.get(0);
		var resourcesList = SpvcReflectedResource.create(list, count);

		for (int i = 0; i < count; i++) {
			var resource = resourcesList.get(i);
			String name = resource.nameString();
			int bindingOffset = getDecorationOffset(compiler, resource, Spv.SpvDecorationBinding, intReturnBuffer);
			long typeHandle = Spvc.spvc_compiler_get_type_handle(compiler, resource.type_id());
			int dimension = Spvc.spvc_type_get_image_dimension(typeHandle);
			result.add(new SpirvShader.SpvSampler(spirv, name, bindingOffset, dimension));
		}
		return result;
	}

	private static List<SpirvShader.SpvVariable> extractVariables(long compiler, long resources, MemoryStack stack,
	                                                               ByteBuffer spirv, int resourceType) throws ShaderCompileException {
		List<SpirvShader.SpvVariable> result = new ArrayList<>();
		PointerBuffer pointer = stack.callocPointer(1);
		PointerBuffer countPointer = stack.callocPointer(1);
		IntBuffer intReturnBuffer = stack.callocInt(1);

		long list = getResourceList(resources, resourceType, pointer, countPointer);
		int count = (int) countPointer.get(0);
		var resourcesList = SpvcReflectedResource.create(list, count);

		for (int i = 0; i < count; i++) {
			var resource = resourcesList.get(i);
			String name = resource.nameString();
			int locationOffset = getDecorationOffset(compiler, resource, Spv.SpvDecorationLocation, intReturnBuffer);
			result.add(new SpirvShader.SpvVariable(spirv, name, locationOffset));
		}
		return result;
	}

	private static long getResourceList(long resources, int type, PointerBuffer pointer, PointerBuffer countPointer)
			throws ShaderCompileException {
		checkResult(Spvc.spvc_resources_get_resource_list_for_type(resources, type, pointer, countPointer),
		            "Couldn't list resources of type " + type);
		return pointer.get(0);
	}

	@Override
	public void close() {
		MemoryUtil.memFree(this.spirv);
	}

	@Nullable SpvUniformBuffer getUniformBuffer(final String name) {
		for (SpvUniformBuffer ubo : this.uniformBuffers) {
			if (ubo.name().equals(name)) {
				return ubo;
			}
		}

		return null;
	}

	@Nullable SpvSampler getSampler(final String name) {
		for (SpvSampler sampler : this.samplers) {
			if (sampler.name().equals(name)) {
				return sampler;
			}
		}

		return null;
	}

	@Nullable SpvVariable getInputVariable(final String name) {
		for (SpvVariable variable : this.inputs) {
			if (variable.name().equals(name)) {
				return variable;
			}
		}

		return null;
	}

	private static void checkResult(final int result, final String message) throws ShaderCompileException {
		if (result != Spvc.SPVC_SUCCESS) {
			String name = switch (result) {
				case Spvc.SPVC_ERROR_INVALID_ARGUMENT -> "SPVC_ERROR_INVALID_ARGUMENT";
				case Spvc.SPVC_ERROR_OUT_OF_MEMORY -> "SPVC_ERROR_OUT_OF_MEMORY";
				case Spvc.SPVC_ERROR_UNSUPPORTED_SPIRV -> "SPVC_ERROR_UNSUPPORTED_SPIRV";
				case Spvc.SPVC_ERROR_INVALID_SPIRV -> "SPVC_ERROR_INVALID_SPIRV";
				default -> Integer.toString(result);
			};
			throw new ShaderCompileException(message + " (" + name + ")");
		}
	}

	private static int getDecorationOffset(final long compiler, final SpvcReflectedResource resource, final int decoration, final IntBuffer returnBuffer) throws ShaderCompileException {
		if (!Spvc.spvc_compiler_get_binary_offset_for_decoration(compiler, resource.id(), decoration, returnBuffer)) {
			throw new ShaderCompileException("Couldn't find byte offset for location decoration of " + resource.nameString());
		} else {
			return returnBuffer.get(0);
		}
	}

	public record SpvVariable(ByteBuffer spirv, String name, int locationOffset) {
		public int getLocation() {
			return spirv.getInt(locationOffset * 4);
		}

		public void setLocation(int location) {
			spirv.putInt(locationOffset * 4, location);
		}
	}

	public static abstract class SpvBinding {
		protected final ByteBuffer spirv;
		protected final String name;
		protected final int bindingOffset;

		public SpvBinding(ByteBuffer spirv, String name, int bindingOffset) {
			this.spirv = spirv;
			this.name = name;
			this.bindingOffset = bindingOffset;
		}

		public int getBinding() {
			return spirv.getInt(bindingOffset * 4);
		}

		public void setBinding(int binding) {
			spirv.putInt(bindingOffset * 4, binding);
		}

		public ByteBuffer spirv() {
			return spirv;
		}

		public String name() {
			return name;
		}

		public int bindingOffset() {
			return bindingOffset;
		}

	}

    public static final class SpvUniformBuffer extends SpvBinding {
        private final int size;

        public SpvUniformBuffer(ByteBuffer spirv, String name, int size, int bindingOffset) {
            super(spirv, name, bindingOffset);

            this.size = size;
        }

        public int size() {
            return size;
        }

        @Override
        public String toString() {
            return "SpvUniformBuffer[" +
                   "spirv=" + spirv + ", " +
                   "name=" + name + ", " +
                   "size=" + size + ", " +
                   "bindingOffset=" + bindingOffset + ']';
        }

    }

    public static final class SpvSampler extends SpvBinding {
        private final int dimensions;

        public SpvSampler(ByteBuffer spirv, String name, int bindingOffset, int dimensions) {
			super(spirv, name, bindingOffset);

            this.dimensions = dimensions;
        }

        public int dimensions() {
            return dimensions;
        }

        @Override
        public String toString() {
            return "SpvSampler[" +
                   "spirv=" + spirv + ", " +
                   "name=" + name + ", " +
                   "bindingOffset=" + bindingOffset + ", " +
                   "dimensions=" + dimensions + ']';
        }

    }
}
