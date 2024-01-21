#version 460


#pragma shader_stage (compute)


layout (constant_id = 0) const uint X = 32;
layout (constant_id = 1) const uint Y = 32;
layout (constant_id = 2) const uint CnkAreas = 8;

layout (constant_id = 3) const uint MaxCnkAreas = 32;


layout (local_size_x = 32, local_size_y = 32, local_size_z = 32) in;

//May have to make this Count Buffer

//Contruct.generate DrawCalls instead of using a refercne/Master Buffer
//..ay onyl be viable with constanr VertexCOunt.IndexOffet Though.hoever



struct VkDrawIndexedIndirectCommand {
    uint   indexCount,
    instanceCount,
    firstIndex,
    vertexOffset,
    firstInstance;

}; VkDrawIndexedIndirectCommand;

struct drwCllInstance
{
    vec3 position; //AND for SubPos Relative to CnkArea
    int index;
    VkDrawIndexedIndirectCommand indirectDrwCmd;
};


layout(binding = 0) uniform readonly UniformBufferObject {
    drwCllInstance dummy[CnkAreas*512];
};

layout(push_constant) uniform pushConstant {
    vec3 camPos;
    vec3 lookDir;

    vec3 nx;
    vec3 px;
    vec3 ny;
    vec3 py;
    vec3 nz;
    vec3 pz;
}vFrust;



//BDA...
layout(binding = 1) buffer writeonly IndirectDrws {
    VkDrawIndexedIndirectCommand xys[MaxCnkAreas];
}indirectCmds ;



//BDA...
layout(binding = 2) buffer restrict writeonly IndirectDrwsBuffer {
    uint drawCnt[MaxCnkAreas];
}sx;



bool checkFrustumState(drwCllInstance drwCallInst)
{
    vec3 minAABB = drwCallInst.position;
    vec3 maxAABB = drwCallInst.position+16;
    bvec3 visVec;
    vec3 camPos =vFrust.camPos;
    vec3 nx = vFrust.nx;
    bvec3 vFrustXY=lessThan(nx, -(vFrust.nz));
//Might be able to preCompute MinMax
    vec3 minMaxAABB  = max(maxAABB, min(minAABB, vec3(0)));

    //ivec3 tst=vFrustXY?minAABB : maxAABB;



    visVec=lessThan(fma(minMaxAABB, nx,  camPos), vFrust.nz);

    return any(visVec);

}




const VkDrawIndexedIndirectCommand null = VkDrawIndexedIndirectCommand(0,0,0,0,0);
void main() {

    uint x = gl_GlobalInvocationID.x >> 9;
    uint y = gl_GlobalInvocationID.y >> 9;
    uint z = gl_GlobalInvocationID.z >> 9;

    uvec3 drawIndex = gl_GlobalInvocationID <<uvec3(6, 4, 0);

    uint xyz  = atomicAdd(sx.drawCnt[0], 1u);


    uint subCllIndx = (gl_GlobalInvocationID.x*512) + (gl_GlobalInvocationID.y) & 511;


   // uint xyz = drawIndex.x+drawIndex.y+drawIndex.z;

    if(checkFrustumState(dummy[xyz]))
    {
        indirectCmds.xys[xyz]= dummy[xyz].indirectDrwCmd;
    };



}







