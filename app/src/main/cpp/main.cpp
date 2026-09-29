#include <jni.h>
#include <cerrno>
#include <cstdint>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include "adrenotools/src/hook/kgsl.h"

namespace {

enum TurboResult {
    TURBO_SUCCESS = 0,
    TURBO_KGSL_UNAVAILABLE = 1,
    TURBO_NOT_SUPPORTED = 2,
    TURBO_FAILED = 3,
};

int setTurbo(bool turbo) {
    uint32_t enable = turbo ? 0U : 1U;
    kgsl_device_getproperty prop{
            .type = KGSL_PROP_PWRCTRL,
            .value = reinterpret_cast<void *>(&enable),
            .sizebytes = sizeof(enable),
    };

    const int kgslFd = open("/dev/kgsl-3d0", O_RDWR | O_CLOEXEC);
    if (kgslFd < 0)
        return TURBO_KGSL_UNAVAILABLE;

    const int result = ioctl(kgslFd, IOCTL_KGSL_SETPROPERTY, &prop);
    const int error = errno;
    close(kgslFd);

    if (result == 0)
        return TURBO_SUCCESS;

    // Newer kernels can expose KGSL while omitting the legacy PWRCTRL property.
    if (error == ENOTTY || error == EINVAL || error == EOPNOTSUPP)
        return TURBO_NOT_SUPPORTED;

    return TURBO_FAILED;
}

int setPowerConstraint(uint32_t constraintType, uint32_t constraintLevel) {
    kgsl_device_constraint_pwrlevel pwrlevelData{
            .level = constraintLevel,
    };

    kgsl_device_constraint constraintData{
            .type = constraintType,
            .context_id = 0,
            .data = reinterpret_cast<void *>(&pwrlevelData),
            .size = sizeof(pwrlevelData),
    };

    kgsl_device_getproperty prop{
            .type = KGSL_PROP_PWR_CONSTRAINT,
            .value = reinterpret_cast<void *>(&constraintData),
            .sizebytes = sizeof(constraintData),
    };

    const int kgslFd = open("/dev/kgsl-3d0", O_RDWR | O_CLOEXEC);
    if (kgslFd < 0)
        return TURBO_KGSL_UNAVAILABLE;

    const int result = ioctl(kgslFd, IOCTL_KGSL_SETPROPERTY, &prop);
    const int error = errno;
    close(kgslFd);

    if (result == 0)
        return TURBO_SUCCESS;

    if (error == ENOTTY || error == EINVAL || error == EOPNOTSUPP)
        return TURBO_NOT_SUPPORTED;

    return TURBO_FAILED;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_wintermist_adrenoperformancemanager_MainActivity_EnableTurbo(JNIEnv*, jobject) {
    return setTurbo(true);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wintermist_adrenoperformancemanager_MainActivity_DisableTurbo(JNIEnv*, jobject) {
    return setTurbo(false);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wintermist_adrenoperformancemanager_MainActivity_SetPowerConstraint(
        JNIEnv*, jobject, jint type, jint level) {
    return setPowerConstraint(static_cast<uint32_t>(type), static_cast<uint32_t>(level));
}
