#
# Copyright (C) 2018-2021 ArrowOS
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit common products
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)

# Inherit device configurations
$(call inherit-product, device/xiaomi/elish/device.mk)

# Inherit common LineageOS configurations
$(call inherit-product, vendor/arrow/config/common.mk)

PRODUCT_CHARACTERISTICS := tablet

PRODUCT_BRAND := Xiaomi
PRODUCT_DEVICE := elish
PRODUCT_MANUFACTURER := Xiaomi
PRODUCT_MODEL := M2105K81AC
PRODUCT_NAME := arrow_elish

PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

BUILD_FINGERPRINT := Xiaomi/elish/elish:11/RKQ1.200826.002/V13.0.10.0.RKYCNXM:user/release-keys
