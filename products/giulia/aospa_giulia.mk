#
# Copyright (C) 2025 Paranoid Android
#
# SPDX-License-Identifier: Apache-2.0
#

ifeq (aospa_giulia,$(TARGET_PRODUCT))

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit from avalon device
$(call inherit-product, device/oneplus/giulia/device.mk)

# Inherit from the AOSPA configuration.
$(call inherit-product, vendor/aospa/target/product/aospa-target.mk)

# Android 17 blocks workloadclassifier's background service start. Remove it
# after the common device/qcom products have added it.
PRODUCT_PACKAGES := $(filter-out workloadclassifier,$(PRODUCT_PACKAGES))

PRODUCT_NAME := aospa_giulia
PRODUCT_DEVICE := giulia
PRODUCT_MANUFACTURER := OnePlus
PRODUCT_BRAND := OnePlus
PRODUCT_MODEL := CPH2691

PRODUCT_GMS_CLIENTID_BASE := android-oneplus

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="qssi_64-user 16 BP2A.250605.015 1786543506192 release-keys" \
    BuildFingerprint=OnePlus/CPH2691IN/OP5D3BL1:16/UKQ1.231108.001/V.R4T3.3237bb8-cff9dc-d57a61:user/release-keys \
    DeviceName=OP5D3BL1 \
    DeviceProduct=CPH2691 \
    SystemDevice=OP5D3BL1 \
    SystemName=CPH2691

PRODUCT_DEFAULT_DEV_CERTIFICATE := vendor/aospa-priv/security

# Boot animation resolution.
TARGET_BOOT_ANIMATION_RES := 1080

TARGET_USES_BLUR := true

endif
