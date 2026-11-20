# LeeGarUpdater integration makefile.
#
# Include it ONCE from your device tree (BoardConfig.mk is recommended):
#
#   include packages/apps/LeeGarUpdater/LeeGarUpdater.mk
#
# This single include:
#   1. adds LeeGarUpdater to PRODUCT_PACKAGES (it replaces the stock
#      Updater/SystemUpdater modules via the Soong `overrides`
#      mechanism in app/Android.bp);
#   2. registers this project's private sepolicy directory so that the
#      file_contexts for /data/lgu_updates are merged into the build.
#
# The init rc (app/etc/init.leegarupdater.rc) and the priv-app
# allowlists travel with the app module itself via Soong `required`
# deps and need no make integration.

ifndef LGU_MK_INCLUDED
LGU_MK_INCLUDED := true

# NOTE: the path is hardcoded (not $(call my-dir)) on purpose: this file is
# pulled in via -include from product makefiles, where my-dir expands to
# an empty string under kati (this produced a stray "/sepolicy/private"
# entry and crashed Soong globbing). In-tree precedent:
# PixelExtraParts/device.mk hardcodes its path the same way.
LGU_PATH := packages/apps/LeeGarUpdater

# 1. Install the app.
PRODUCT_PACKAGES += LeeGarUpdater

# 2. OTA download directory contexts (sepolicy/private/file_contexts).
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(LGU_PATH)/sepolicy/private

endif # LGU_MK_INCLUDED
