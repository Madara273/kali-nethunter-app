// native-lib.cpp
#include <jni.h>
#include <vulkan/vulkan.h>

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_offsec_nethunter_VulkanHelper_isVulkanAvailable(JNIEnv* env, jobject /* this */) {
    uint32_t instanceLayerCount = 0;
    VkResult result = vkEnumerateInstanceLayerProperties(&instanceLayerCount, nullptr);
    return (result == VK_SUCCESS && instanceLayerCount > 0) ? JNI_TRUE : JNI_FALSE;
}
