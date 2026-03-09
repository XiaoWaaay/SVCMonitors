KERNEL_DIR ?= $(KP_DIR)/kernel
CROSS_COMPILE ?= aarch64-linux-gnu-

CC = $(CROSS_COMPILE)gcc
LD = $(CROSS_COMPILE)ld

INCLUDE = -I$(KERNEL_DIR)/include \
          -I$(KERNEL_DIR)/patch/include \
          -I$(KERNEL_DIR)/linux/include \
          -I$(KERNEL_DIR)/linux/arch/arm64/include

CFLAGS = -Wall -O2 -fno-pic -fno-stack-protector \
         -nostdlib -nostdinc -ffreestanding \
         -mgeneral-regs-only \
         $(INCLUDE)

LDFLAGS = -r

TARGET = svc_monitor.kpm

all: $(TARGET)

$(TARGET): src/svc_monitor.c
	$(CC) $(CFLAGS) -c $< -o svc_monitor.o
	$(LD) $(LDFLAGS) svc_monitor.o -o $@
	rm -f svc_monitor.o

clean:
	rm -f $(TARGET) svc_monitor.o

.PHONY: all clean
