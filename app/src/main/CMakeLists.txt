cmake_minimum_required(VERSION 3.4.1)

project(taoavatar)

set(PROJECT_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cpp/project/include)
set(COMMON_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cpp/common)
set(CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cpp)
set(JNI_DIR ${CMAKE_CURRENT_SOURCE_DIR}/jni)
set (THIRD_PARTY_INCLUDES_DIR ${CMAKE_CURRENT_LIST_DIR}/cpp/third_party/)
set (A2BS_DROID_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cpp/a2bs_droid)
set (LLM_DROID_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cpp/llm_droid)

include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CPP_DIR}/3rd_party
    ${CPP_DIR}/3rd_party/glm
    ${CPP_DIR}/audio2bs/include
    ${CPP_DIR}
    ${JNI_DIR}
    ${PROJECT_INCLUDE_DIR}
    ${A2BS_DROID_DIR}
    ${COMMON_DIR})


FILE(GLOB A2BS_DROID_SRC
        ${A2BS_DROID_DIR}/*.cpp)

FILE(GLOB CPP_SRCS
        ${CPP_DIR}/*.cpp
        ${COMMON_DIR}/*.cpp
        ${A2BS_DROID_SRC}
        ${LLM_DROID_DIR}/*.cpp)

FILE(GLOB JNI_SRCS
        ${JNI_DIR}/*.cpp)


set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall \
                     -DVK_USE_PLATFORM_ANDROID_KHR")

set(lib_path ${CMAKE_CURRENT_SOURCE_DIR}/libs)

add_library(MNN
        SHARED
        IMPORTED)
set(MNN_INCLUDE_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/libs/MNN/include)
set_target_properties(MNN PROPERTIES INTERFACE_INCLUDE_DIRECTORIES
        "${MNN_INCLUDE_ROOT};${MNN_INCLUDE_ROOT}/audio;${MNN_INCLUDE_ROOT}/llm"
)
set_target_properties(MNN
        PROPERTIES IMPORTED_LOCATION
        ${lib_path}/MNN/lib/${ANDROID_ABI}/libMNN.so)

add_library(nnrruntime
        SHARED
        IMPORTED)

set_target_properties(nnrruntime
        PROPERTIES IMPORTED_LOCATION
        ${lib_path}/NNR/lib/${ANDROID_ABI}/libnnrruntime.so)
set_target_properties(nnrruntime PROPERTIES INTERFACE_INCLUDE_DIRECTORIES ${lib_path}/NNR/include/)



list(APPEND MNN_LIBRARIES
        MNN
)

add_subdirectory(cpp/a2bs)


add_library(${CMAKE_PROJECT_NAME} SHARED ${CPP_SRCS} ${JNI_SRCS}
)


get_property(PROJECT_SOURCES TARGET ${CMAKE_PROJECT_NAME} PROPERTY SOURCES)

message(STATUS "All source files in project:")
foreach(src_file IN LISTS PROJECT_SOURCES)
    message(STATUS "${src_file}")
endforeach()


target_link_libraries(${CMAKE_PROJECT_NAME}
    log
    android
    MNN
    nnrruntime
    mnn_a2bs
    )
