
cmake_minimum_required(VERSION 3.13)

project(mnn_tts_SDK VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(BUILD_BERTVITS2 "Build BertVit2 TTS " ON)
option(BUILD_PIPER "Build PIPER TTS " ON)
option(BUILD_SUPERTONIC "Build Supertonic TTS " ON)

include_directories(
	${CMAKE_CURRENT_LIST_DIR}/include
	${CMAKE_CURRENT_LIST_DIR}/third_party/include/nlohmann
)

if(BUILD_BERTVITS2)
  include_directories(
  	${CMAKE_CURRENT_LIST_DIR}/include/bertvits2
  )
endif()

if(BUILD_PIPER)
  include_directories(
  	${CMAKE_CURRENT_LIST_DIR}/include/piper
    ${CMAKE_CURRENT_LIST_DIR}/third_party/piper/espeak-ng/src/include/espeak-ng/
    ${CMAKE_CURRENT_LIST_DIR}/third_party/piper/espeak-ng/src/include/espeak/
    ${CMAKE_CURRENT_LIST_DIR}/third_party/piper/espeak-ng/src/include/
  )
endif()

if(BUILD_SUPERTONIC)
  include_directories(
  	${CMAKE_CURRENT_LIST_DIR}/include/supertonic
  )
endif()

set(SHARED_SOURCE_FILES
  ${CMAKE_CURRENT_LIST_DIR}/src/mnn_tts_config.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/mnn_tts_sdk.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/mnn_tts_logger.cpp
)

set(BERTVITS2_SOURCE_FILES
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/an_to_cn.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/utils.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/mnn_bertvits2_tts_impl.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/text_preprocessor.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/pinyin.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/chinese_g2p.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/chinese_bert.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/english_bert.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/english_g2p.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/tone_adjuster.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/tts_generator.cpp
  ${CMAKE_CURRENT_LIST_DIR}/src/bertvits2/word_spliter.cpp
)

if(BUILD_PIPER)
  set(PIPER_SOURCE_FILES
    ${CMAKE_CURRENT_LIST_DIR}/src/piper/audio_generator.cpp
    ${CMAKE_CURRENT_LIST_DIR}/src/piper/mnn_piper_tts_impl.cpp
    ${CMAKE_CURRENT_LIST_DIR}/src/piper/phoneme_ids.cpp
  )

  # Build espeak-ng as a static library for Android
  # Disable features not needed on Android (no audio output, no async, no tests)
  set(BUILD_SHARED_LIBS_SAVED ${BUILD_SHARED_LIBS})
  set(BUILD_SHARED_LIBS OFF)
  set(COMPILE_INTONATIONS OFF CACHE BOOL "" FORCE)
  set(ENABLE_TESTS OFF CACHE BOOL "" FORCE)
  set(USE_ASYNC OFF CACHE BOOL "" FORCE)
  set(USE_MBROLA OFF CACHE BOOL "" FORCE)
  set(USE_LIBSONIC OFF CACHE BOOL "" FORCE)
  set(USE_LIBPCAUDIO OFF CACHE BOOL "" FORCE)
  set(USE_KLATT OFF CACHE BOOL "" FORCE)
  set(USE_SPEECHPLAYER OFF CACHE BOOL "" FORCE)
  add_subdirectory(
    ${CMAKE_CURRENT_LIST_DIR}/third_party/piper/espeak-ng
    ${CMAKE_BINARY_DIR}/espeak-ng
    EXCLUDE_FROM_ALL
  )
  set(BUILD_SHARED_LIBS ${BUILD_SHARED_LIBS_SAVED})
endif()

if(BUILD_SUPERTONIC)
  set(SUPERTONIC_SOURCE_FILES
    ${CMAKE_CURRENT_LIST_DIR}/src/supertonic/mnn_supertonic_tts_impl.cpp
  )
endif()

add_library(${PROJECT_NAME} SHARED
  ${PIPER_SOURCE_FILES}
  ${BERTVITS2_SOURCE_FILES}
  ${SUPERTONIC_SOURCE_FILES}
  ${SHARED_SOURCE_FILES}
)

if(BUILD_PIPER)
  target_link_libraries(${PROJECT_NAME} log MNN espeak-ng)
else()
  target_link_libraries(${PROJECT_NAME} log MNN)
endif()
