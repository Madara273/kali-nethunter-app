#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstdlib>
#include <cstring>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <termios.h>
#include <cerrno>
#include <signal.h>
#include <android/log.h>
#ifdef __linux__
#include <linux/limits.h>
#endif

#define LOG_TAG "NHPTY"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static int open_pty_master(char *slaveName, size_t slaveLen) {
#if defined(__ANDROID__) || defined(__linux__)
    int master = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (master < 0) return -1;
    if (grantpt(master) < 0 || unlockpt(master) < 0) {
        LOGE("grantpt/unlockpt failed: %s", strerror(errno));
        close(master);
        return -1;
    }
    char *sn = ptsname(master);
    if (!sn) {
        LOGE("ptsname failed: %s", strerror(errno));
        close(master);
        return -1;
    }
    strncpy(slaveName, sn, slaveLen - 1);
    slaveName[slaveLen - 1] = '\0';
    return master;
#else
    (void)slaveName; (void)slaveLen;
    errno = ENOSYS;
    return -1;
#endif
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_offsec_nethunter_pty_PtyNative_openPtyShell(JNIEnv *env, jclass) {
    char slave[PATH_MAX];
    int master = open_pty_master(slave, sizeof(slave));
    if (master < 0) {
        LOGE("Failed opening PTY master");
        return nullptr;
    }
    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master);
        return nullptr;
    }
    if (pid == 0) {
        setsid();
        int slaveFd = open(slave, O_RDWR);
        if (slaveFd < 0) _exit(127);
#ifdef TIOCSCTTY
        ioctl(slaveFd, TIOCSCTTY, 0);
#endif
        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        if (slaveFd > 2) close(slaveFd);
        close(master);
        const char *shell = "su";
        execlp(shell, shell, "-mm", (char*)nullptr);
        LOGE("execlp failed: %s", strerror(errno));
        _exit(127);
    }
    jintArray arr = env->NewIntArray(2);
    if (arr == nullptr) {
        LOGE("Failed to allocate jintArray");
        kill(pid, SIGKILL);
        close(master);
        return nullptr;
    }
    jint vals[2];
    vals[0] = master;
    vals[1] = (jint)pid;
    env->SetIntArrayRegion(arr, 0, 2, vals);
    return arr;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_offsec_nethunter_pty_PtyNative_openPtyShellExec(JNIEnv *env, jclass, jstring jcmd) {
    if (jcmd == nullptr) return nullptr;
    const char *cmd = env->GetStringUTFChars(jcmd, nullptr);
    if (cmd == nullptr) {
        LOGE("Failed to get UTF chars from jstring");
        return nullptr;
    }
    char slave[PATH_MAX];
    int master = open_pty_master(slave, sizeof(slave));
    if (master < 0) {
        LOGE("Failed opening PTY master for exec shell");
        env->ReleaseStringUTFChars(jcmd, cmd);
        return nullptr;
    }
    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master);
        env->ReleaseStringUTFChars(jcmd, cmd);
        return nullptr;
    }
    if (pid == 0) {
        setsid();
        int slaveFd = open(slave, O_RDWR);
        if (slaveFd < 0) _exit(127);
#ifdef TIOCSCTTY
        ioctl(slaveFd, TIOCSCTTY, 0);
#endif
        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        if (slaveFd > 2) close(slaveFd);
        close(master);
        execlp("su", "su", "-mm", "-c", cmd, (char*)nullptr);
        LOGE("execlp failed: %s", strerror(errno));
        _exit(127);
    }
    env->ReleaseStringUTFChars(jcmd, cmd);
    jintArray arr = env->NewIntArray(2);
    if (arr == nullptr) {
        LOGE("Failed to allocate jintArray");
        kill(pid, SIGKILL);
        close(master);
        return nullptr;
    }
    jint vals[2];
    vals[0] = master;
    vals[1] = (jint)pid;
    env->SetIntArrayRegion(arr, 0, 2, vals);
    return arr;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_offsec_nethunter_pty_PtyNative_setWindowSize(JNIEnv *, jclass, jint fd, jint cols, jint rows) {
#ifdef TIOCSWINSZ
    struct winsize ws{};
    ws.ws_col = (unsigned short)cols;
    ws.ws_row = (unsigned short)rows;
    return ioctl(fd, TIOCSWINSZ, &ws);
#else
    (void)fd; (void)cols; (void)rows; return -1;
#endif
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_offsec_nethunter_pty_PtyNative_closeFd(JNIEnv *, jclass, jint fd) {
    return close(fd);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_offsec_nethunter_pty_PtyNative_killChild(JNIEnv *, jclass, jint pid, jint signal) {
    return kill((pid_t)pid, signal);
}