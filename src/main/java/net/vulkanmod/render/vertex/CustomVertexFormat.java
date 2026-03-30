package net.vulkanmod.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public class CustomVertexFormat {
    public static final VertexFormatElement ELEMENT_POSITION_INT16 = new VertexFormatElement(0, 0, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.POSITION, 4);
    public static final VertexFormatElement ELEMENT_COLOR_UINT = new VertexFormatElement(1, 0, VertexFormatElement.Type.UINT, VertexFormatElement.Usage.COLOR, 1);
    public static final VertexFormatElement ELEMENT_UV0_UINT16 = new VertexFormatElement(2, 0, VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 2);

    private static float POSITION_OFFSET = 4.0f;

    public static final VertexFormat COMPRESSED_TERRAIN =
            VertexFormat.builder()
                        .add("Position", ELEMENT_POSITION_INT16)
                        .add("UV0", ELEMENT_UV0_UINT16)
                        .add("Color", ELEMENT_COLOR_UINT)
                        .build();

    public static final VertexFormat TERRAIN =
            VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("UV2", VertexFormatElement.UV2)
                        .add("Normal", VertexFormatElement.NORMAL)
                        .padding(1)
                        .build();

    public static final VertexFormat NONE = VertexFormat.builder().build();

    public static void setPositionOffset(float positionOffset) {
        POSITION_OFFSET = positionOffset;
    }

    public static float getPositionOffset() {
        return POSITION_OFFSET;
    }
}
