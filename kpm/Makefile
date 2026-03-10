KERNEL_DIR ?= $(KP_DIR)/kernel
SDK_DIR ?= /Users/bytedance/Library/Android/sdk
NDK_VERSION ?= 27.0.12077973
NDK_DIR ?= $(SDK_DIR)/ndk/$(NDK_VERSION)
TOOLCHAIN ?= $(NDK_DIR)/toolchains/llvm/prebuilt/darwin-x86_64/bin
CROSS_COMPILE ?= aarch64-linux-gnu-

CC ?= $(CROSS_COMPILE)gcc
LD ?= $(CROSS_COMPILE)ld

CC_MACHINE := $(shell $(CC) -dumpmachine 2>/dev/null)
ifneq ($(findstring linux-android,$(CC_MACHINE)),linux-android)
CC := $(TOOLCHAIN)/aarch64-linux-android21-clang
LD := $(CC)
endif

INCLUDE = -I$(KERNEL_DIR)/include \
          -I$(KERNEL_DIR)/patch/include \
          -I$(KERNEL_DIR)/linux/include \
          -I$(KERNEL_DIR)/linux/include/uapi \
          -I$(KERNEL_DIR)/linux/arch/arm64/include \
          -I$(KERNEL_DIR)/linux/tools/arch/arm64/include \
          -I$(KERNEL_DIR)/linux/arch/arm64/include/uapi

CFLAGS = -Wall -O2 -fno-pic -fno-stack-protector \
         -nostdlib -nostdinc -ffreestanding \
         -fno-builtin \
         -fno-builtin-memset -fno-builtin-memcpy -fno-builtin-memmove -fno-builtin-memcmp \
         -mgeneral-regs-only \
         $(INCLUDE)

LDFLAGS = -r

TARGET = svc_monitor.kpm

all: $(TARGET)

$(TARGET): src/svc_monitor.c
	$(CC) $(CFLAGS) -c $< -o svc_monitor.o
	$(LD) -nostdlib $(LDFLAGS) svc_monitor.o -o $@
	rm -f svc_monitor.o

clean:
	rm -f $(TARGET) svc_monitor.o

.PHONY: all clean
