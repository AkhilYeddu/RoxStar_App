if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "C:/Users/akhil/.gradle/caches/8.13/transforms/79359ee50a416191f1d35b8d0bf68adc/transformed/oboe-1.8.0/prefab/modules/oboe/libs/android.x86_64/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/akhil/.gradle/caches/8.13/transforms/79359ee50a416191f1d35b8d0bf68adc/transformed/oboe-1.8.0/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

