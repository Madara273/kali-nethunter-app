#include <jni.h>
#include <vulkan/vulkan.h>

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_offsec_nethunter_VulkanHelper_isVulkanAvailable(jobject) {
    uint32_t instanceLayerCount = 0;
    VkResult result = vkEnumerateInstanceLayerProperties(&instanceLayerCount, nullptr);
    if (result != VK_SUCCESS) {
        return JNI_FALSE;
    }
    return (instanceLayerCount > 0) ? JNI_TRUE : JNI_FALSE;
}
