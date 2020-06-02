LOCAL_PATH:= $(call my-dir)

# MAP API module

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, lib/mapapi)
LOCAL_MODULE := bluetooth_qti.mapsapi
include $(BUILD_STATIC_JAVA_LIBRARY)

# Bluetooth APK

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src) \
        $(call all-java-files-under, ../../../bluetooth_ext/packages_apps_bluetooth_ext/src)

LOCAL_PACKAGE_NAME := BluetoothQti
LOCAL_OVERRIDES_PACKAGES := Bluetooth
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_CERTIFICATE := platform
LOCAL_USE_AAPT2 := true
LOCAL_JNI_SHARED_LIBRARIES := libbluetooth_qti_jni

LOCAL_JAVA_LIBRARIES := javax.obex telephony-common services.net
LOCAL_STATIC_JAVA_LIBRARIES := \
        com.android.vcard \
        bluetooth_qti.mapsapi \
        sap-api-java-static \
        services.net \
        libprotobuf-java-lite \
        bluetooth-protos-lite \

LOCAL_STATIC_ANDROID_LIBRARIES := \
        androidx.core_core \
        androidx.legacy_legacy-support-v4 \
        androidx.lifecycle_lifecycle-livedata \
        androidx.room_room-runtime \

LOCAL_ANNOTATION_PROCESSORS := \
        bt-androidx-annotation-nodeps \
        bt-androidx-room-common-nodeps \
        bt-androidx-room-compiler-nodeps \
        bt-androidx-room-migration-nodeps \
        bt-antlr4-nodeps \
        bt-apache-commons-codec-nodeps \
        bt-auto-common-nodeps \
        bt-javapoet-nodeps \
        bt-kotlin-metadata-nodeps \
        bt-sqlite-jdbc-nodeps \
        bt-jetbrain-nodeps \
        guava-21.0 \
        kotlin-stdlib

LOCAL_ANNOTATION_PROCESSOR_CLASSES := \
        androidx.room.RoomProcessor

LOCAL_STATIC_JAVA_LIBRARIES += com.android.emailcommon
LOCAL_PROTOC_OPTIMIZE_TYPE := micro

LOCAL_REQUIRED_MODULES := libbluetooth_qti
LOCAL_PROGUARD_ENABLED := disabled
include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
