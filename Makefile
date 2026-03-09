# 判断系统类型，Windows 和非 Windows 的区分
ifeq ($(OS),Windows_NT)
    RM = del /Q
    SEP = \
    # 修正 Windows 上的 for 循环语法（双百分号 %%）
    FIND_REMOVE = for /r . %%x in (*.o) do del "%%x"
else
    RM = rm -f
    SEP = /
    FIND_REMOVE = find . -name "*.o" -exec rm -f {} +
endif

# 编译器路径设置
TARGET_COMPILE := /opt/homebrew/bin/aarch64-none-elf-
ifndef TARGET_COMPILE
    $(error TARGET_COMPILE not set)
endif

ifndef KP_DIR
    KP_DIR = ./KernelPatch
endif

CC = $(TARGET_COMPILE)gcc
LD = $(TARGET_COMPILE)ld

INCLUDE_DIRS := . include patch/include linux/include linux/arch/arm64/include linux/tools/arch/arm64/include

# 根据系统使用适当的路径分隔符
INCLUDE_FLAGS := $(foreach dir,$(INCLUDE_DIRS),-I$(KP_DIR)$(SEP)kernel$(SEP)$(dir)) -I./src/include

CFLAGS = -Wall -O2 -fno-pic -fno-stack-protector -fno-common \
         -DKPM $(INCLUDE_FLAGS)

# C 文件路径
BASE_SRCS += ./src/main.c
BASE_SRCS += ./src/event_logger.c
BASE_SRCS += ./src/symbol_resolver.c
BASE_SRCS += ./src/caller_resolver.c
BASE_SRCS += ./src/pkg_resolver.c
BASE_SRCS += ./src/maps_cache.c
BASE_SRCS += ./src/syscall_monitor.c
BASE_SRCS += ./src/hook_engine.c
BASE_SRCS += ./src/file_logger.c

SRCS += $(BASE_SRCS)

OBJS := $(SRCS:.c=.o)
OBJS := $(OBJS:.S=.o)

all: module.kpm svc-monitor.kpm

# 构建原始 module.kpm
module.kpm: ${OBJS}
	${LD} -r -o $@ $^
	$(FIND_REMOVE)

# 构建新的 svc-monitor.kpm
svc-monitor.kpm: 
	$(MAKE) -C kpm TARGET_COMPILE=$(TARGET_COMPILE) KP_DIR=$(abspath $(KP_DIR))
	cp kpm/svc-monitor.kpm .

# 构建 Android APK
apk: 
	cd android && ./gradlew assembleDebug

# 推送模块到设备
push: module.kpm svc-monitor.kpm
	adb push module.kpm /sdcard/Download
	adb push svc-monitor.kpm /sdcard/Download

# 推送 APK 到设备
push-apk: apk
	adb install -r android/app/build/outputs/apk/debug/app-debug.apk

%.o: %.c
	${CC} $(CFLAGS) -c -o $@ $<

.PHONY: clean clean-kpm clean-apk

clean:
	$(RM) *.kpm
	$(FIND_REMOVE)
	$(MAKE) -C kpm clean

clean-kpm:
	$(MAKE) -C kpm clean

clean-apk:
	cd android && ./gradlew clean
