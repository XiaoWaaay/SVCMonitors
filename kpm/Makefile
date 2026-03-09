KPATCH_DIR ?= ../KernelPatch

CROSS_COMPILE ?= aarch64-none-elf-
CC = $(CROSS_COMPILE)gcc
LD = $(CROSS_COMPILE)ld

INCLUDE = -I$(KPATCH_DIR)/kernel/linux/include \
          -I$(KPATCH_DIR)/kernel/linux/include/uapi \
          -I$(KPATCH_DIR)/kernel/linux/arch/arm64/include \
          -I$(KPATCH_DIR)/kernel/linux/arch/arm64/include/uapi \
          -I$(KPATCH_DIR)/kernel/linux/tools/arch/arm64/include \
          -I$(KPATCH_DIR)/kernel/include \
          -I$(KPATCH_DIR)/kernel/include/uapi \
          -I$(KPATCH_DIR)/kernel/patch/include

CFLAGS = -Wall -O2 \
         -fno-pic -fno-PIE \
         -fno-stack-protector \
         -nostdlib -nostdinc \
         -ffreestanding -fno-builtin \
         -fno-tree-loop-distribute-patterns \
         -fno-builtin-memset -fno-builtin-memcpy -fno-builtin-memmove -fno-builtin-memcmp \
         -mgeneral-regs-only \
         -DKPM_MODULE \
         $(INCLUDE)

LDFLAGS = -r

TARGET = svc-monitor.kpm

all: $(TARGET)

$(TARGET): src/svc_monitor.c
	$(CC) $(CFLAGS) -c $< -o svc_monitor.o
	$(LD) $(LDFLAGS) svc_monitor.o -o $@
	rm -f svc_monitor.o

clean:
	rm -f $(TARGET) svc_monitor.o

.PHONY: all clean
