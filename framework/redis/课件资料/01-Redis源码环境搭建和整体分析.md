# 一、环境搭建

## 1.1 软件介绍

### Cygwin（64位）

Cygwin是一个在windows平台上运行的类UNIX模拟环境，可以让我们在windows下模拟Linux下的命令。

我们主要使用Cygwin的编译器。

### Clion（2022.1）

*CLion*是Jetbrains公司旗下新推出的一款专为开发C/C++所设计的跨平台IDE。

CLion 支持 GCC、clang、MinGW、Cygwin 编译器以及 GDB 调试器。提供对 Cmake 支持：包含自动处理 Cmake changes 和 Cmake Targets，更新新创建的 C/C++ 档案以及 Cmake Cache 编辑器。

## 1.2 软件安装

### 1>安装配置cygwin

1.下载[https://cygwin.com/setup-x86_64.exe](https://links.jianshu.com/go?to=https%3A%2F%2Fcygwin.com%2Fsetup-x86_64.exe) （64位）版本或者[https://cygwin.com/setup-x86.exe](https://links.jianshu.com/go?to=https%3A%2F%2Fcygwin.com%2Fsetup-x86.exe) （32位版本）

2.点击下一步，这一步一定要选择Direct Connection

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/23a574dc7138419a8015dcfb30871cdf.png)

3.添加网易的镜像站

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/8ab4489bef7d4220ac39b8f7b2a95d38.png)

我们需要安装组件：wget、 gcc-core、gcc-g++、cmake、make、gdb、binutils。以下是我测试通过的版本号：

* wget  1.21.3-1
* gcc-core 11.3.0.1
* gcc-g++ 11.3.0.1
* make 4.3-1
* cmake 3.23.2-1
  （Redis是默认采用make的方式安装，我们在CLion中要改成Cmake的方式 创建CMakeLists.txt）
* gdb 7.9.1-1 (8.3版本无法启动)
* binutils 2.39-1

### 2>加入path

将C:\cygwin64\bin加入环境变量的path中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/3578512b15e849bd8d40e40f1922fd9b.png)

### 3>安装apt-cyg

apt-cyg是一个命令行软件包管理器，我们主要用来安装dos2unix。

GitHub下载脚本：[https://github.com/transcode-open/apt-cyg](https://links.jianshu.com/go?to=https%3A%2F%2Fgithub.com%2Ftranscode-open%2Fapt-cyg)

复制apt-cyg，粘贴到cygwin的安装目录的bin目录下。

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/a9aafd2b69b044cf81837b6c2d468fcc.png)

打开Cygwin，输入命令：

```
##该命令后面会用到 
apt-cyg install dos2unix
```

### 4>下载Clion2022.1

Clion的官方下载地址为：[https://www.jetbrains.com/clion/download/#section=windows]()

选择 Other Versions

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/bb3e1129f1d14756bd10c062bae366e6.png)

安装简体中文语言包并配置主题

打开Clion，新建c项目，选择File--settings--plugins

搜索chinese，安装简体中文语言包

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/105ffdf53f3f467796efbe0cfc717ae6.png)

设置主题

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/80920329f0e6408dab4d6023ddaad8c3.png)

选择Cygwin编译器

文件--设置--构建--工具链，选择Cygwin，并设为默认

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/69ba7c366a7b4e19850b3c05f9a62df6.png)

源文件(c) --->编译中间代码 win: obj  linux : o

obj或o文件进行链接 形成可执行文件 win exe

## 1.3 将Redis源码导入Clion并运行

### 1>下载Redis6.2源码

redis官方源码地址为： [https://github.com/redis/redis](https://github.com/redis/redis)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/5d9deed14cae4722aa42b38409cf50a6.png)

### 2>导入Redis项目

打开项目，选择Redis6.2的文件夹，选择信任项目，导入项目。

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/6479f3439a8a47dfb27c47c6814b9c0e.png)

### 3>编写CMakeLists.txt

1、\redis-6.2\

```c
cmake_minimum_required(VERSION 3.0 FATAL_ERROR)
project(redis VERSION 6.0)
#set(CMAKE_INSTALL_PREFIX "${CMAKE_BINARY_DIR}/")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/../src)
message(CMAKE_RUNTIME_OUTPUT_DIRECTORY is:${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
#if (NOT CMAKE_BUILD_TYPE)
message(STATUS "No build type defined; defaulting to 'Debug'")
set(CMAKE_BUILD_TYPE "Debug" CACHE STRING
        "The type of build. Possible values are: Debug, Release,
RelWithDebInfo and MinSizeRel.")

#endif()
message(STATUS "Host is: ${CMAKE_HOST_SYSTEM}. Build target is:
${CMAKE_SYSTEM}")
get_filename_component(REDIS_ROOT "${CMAKE_CURRENT_SOURCE_DIR}" ABSOLUTE)
message(STATUS "Project root directory is: ${REDIS_ROOT}")
# Just for debugging when handling a new platform.
if (false)
    message("C++ compiler supports these language features:")
    foreach (i ${CMAKE_CXX_COMPILE_FEATURES})
        message(" ${i}")
    endforeach ()
endif ()
message(STATUS "Generating release.h...")
execute_process(
        COMMAND sh -c ./mkreleasehdr.sh
        WORKING_DIRECTORY ${REDIS_ROOT}/src/
)
add_subdirectory(deps)
add_subdirectory(src/modules)
set(SRC_SERVER_TMP
        src/crcspeed.c
        src/crcspeed.h
        src/sha256.c
        src/sha256.h
        src/connection.c
        src/connection.h
        src/acl.c
        src/timeout.c
        src/tracking.c
        src/tls.c
        src/adlist.c
        src/ae.c
        src/anet.c
        # windows屏蔽掉下面两个文件，mac系统不需要屏蔽，这两个是mac环境多路复用的库
        # /usr/local/include/event.h
        # src/ae_kqueue.c
        src/mt19937-64.c
        src/mt19937-64.h
        src/monotonic.c
        src/monotonic.h
        src/dict.c
        src/sds.c
        src/zmalloc.c
        src/lzf_c.c
        src/lzf_d.c
        src/pqsort.c
        src/zipmap.c
        src/sha1.c
        src/ziplist.c
        src/release.c
        src/networking.c
        src/util.c
        src/object.c
        src/db.c
        src/replication.c
        src/rdb.c
        src/t_string.c
        src/t_list.c
        src/t_set.c
        src/t_zset.c
        src/evict.c
        src/defrag.c
        src/module.c
        src/quicklist.c
        src/expire.c
        src/childinfo.c
        src/redis-check-aof.c
        src/redis-check-rdb.c
        src/lazyfree.c
        src/geohash.c
        src/rax.c
        src/geohash_helper.c
        src/siphash.c
        src/geo.c
        src/t_hash.c
        src/config.c
        src/aof.c
        src/pubsub.c
        src/multi.c
        src/debug.c
        src/sort.c
        src/intset.c
        src/syncio.c
        src/cluster.c
        src/crc16.c
        src/endianconv.c
        src/slowlog.c
        src/scripting.c
        src/bio.c
        src/rio.c
        src/rand.c
        src/memtest.c
        src/crc64.c
        src/bitops.c
        src/sentinel.c
        src/notify.c
        src/setproctitle.c
        src/blocked.c
        src/hyperloglog.c
        src/latency.c
        src/sparkline.c
        src/t_stream.c
        src/lolwut.c
        src/lolwut.h
        src/lolwut5.c
        src/lolwut6.c
        src/listpack.c
        src/localtime.c
        src/gopher.c
        )
set(SRC_SERVER src/server.c ${SRC_SERVER_TMP})
set(SRC_CLI
        src/anet.c
        src/sds.c
        src/adlist.c
        src/redis-cli.c
        src/zmalloc.c
        src/release.c
        src/ae.c
        src/crc64.c
        src/crc16.c
        src/dict.c
        src/siphash.c
        )
if (${CMAKE_SYSTEM_NAME} MATCHES "Linux")
    # better not to work with jemalloc
endif()
set(EXECUTABLE_OUTPUT_PATH src)
add_executable(redis-server ${SRC_SERVER})
add_executable(redis-cli ${SRC_CLI})
set_property(TARGET redis-server PROPERTY C_STANDARD 99)
set_property(TARGET redis-server PROPERTY CXX_STANDARD 11)
set_property(TARGET redis-server PROPERTY CXX_STANDARD_REQUIRED ON)
set_property(TARGET redis-cli PROPERTY C_STANDARD 99)
set_property(TARGET redis-cli PROPERTY CXX_STANDARD 11)
set_property(TARGET redis-cli PROPERTY CXX_STANDARD_REQUIRED ON)
target_include_directories(redis-server
        PRIVATE ${REDIS_ROOT}/deps/hiredis
        PRIVATE ${REDIS_ROOT}/deps/linenoise
        PRIVATE ${REDIS_ROOT}/deps/lua/src
        )
target_include_directories(redis-cli
        PRIVATE ${REDIS_ROOT}/deps/hiredis
        PRIVATE ${REDIS_ROOT}/deps/linenoise
        PRIVATE ${REDIS_ROOT}/deps/lua/src
        )
target_link_libraries(redis-server
        PRIVATE pthread
        PRIVATE m
        PRIVATE lua
        PRIVATE linenoise
        PRIVATE hiredis
        )
target_link_libraries(redis-cli
        PRIVATE pthread
        PRIVATE m
        PRIVATE linenoise
        PRIVATE hiredis
        )
link_directories(deps/hiredis/ deps/linenoise/ diredeps/lua/src)
install(TARGETS redis-server
        RUNTIME DESTINATION bin
        )
#set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -c")
```

2、\redis-6.2\deps\linenoise\

```c
add_library(linenoise linenoise.c)
```

3、\redis-6.2\deps\hiredis\

```c
CMAKE_MINIMUM_REQUIRED(VERSION 3.4.0)
INCLUDE(GNUInstallDirs)
PROJECT(hiredis)

OPTION(ENABLE_SSL "Build hiredis_ssl for SSL support" OFF)
OPTION(DISABLE_TESTS "If tests should be compiled or not" OFF)
OPTION(ENABLE_SSL_TESTS, "Should we test SSL connections" OFF)

MACRO(getVersionBit name)
    SET(VERSION_REGEX "^#define ${name} (.+)$")
    FILE(STRINGS "${CMAKE_CURRENT_SOURCE_DIR}/hiredis.h"
            VERSION_BIT REGEX ${VERSION_REGEX})
    STRING(REGEX REPLACE ${VERSION_REGEX} "\\1" ${name} "${VERSION_BIT}")
ENDMACRO(getVersionBit)

getVersionBit(HIREDIS_MAJOR)
getVersionBit(HIREDIS_MINOR)
getVersionBit(HIREDIS_PATCH)
getVersionBit(HIREDIS_SONAME)
SET(VERSION "${HIREDIS_MAJOR}.${HIREDIS_MINOR}.${HIREDIS_PATCH}")
MESSAGE("Detected version: ${VERSION}")

PROJECT(hiredis VERSION "${VERSION}")

SET(ENABLE_EXAMPLES OFF CACHE BOOL "Enable building hiredis examples")

SET(hiredis_sources
        alloc.c
        async.c
        dict.c
        hiredis.c
        net.c
        read.c
        sds.c
        sockcompat.c)

SET(hiredis_sources ${hiredis_sources})

IF(WIN32)
    ADD_COMPILE_DEFINITIONS(_CRT_SECURE_NO_WARNINGS WIN32_LEAN_AND_MEAN)
ENDIF()

ADD_LIBRARY(hiredis SHARED ${hiredis_sources})

SET_TARGET_PROPERTIES(hiredis
        PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS TRUE
        VERSION "${HIREDIS_SONAME}")
IF(WIN32 OR MINGW)
    TARGET_LINK_LIBRARIES(hiredis PRIVATE ws2_32)
ENDIF()

TARGET_INCLUDE_DIRECTORIES(hiredis PUBLIC $<INSTALL_INTERFACE:.> $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>)

CONFIGURE_FILE(hiredis.pc.in hiredis.pc @ONLY)

INSTALL(TARGETS hiredis
        EXPORT hiredis-targets
        RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
        LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
        ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR})

INSTALL(FILES hiredis.h read.h sds.h async.h alloc.h
        DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/hiredis)

INSTALL(DIRECTORY adapters
        DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/hiredis)

INSTALL(FILES ${CMAKE_CURRENT_BINARY_DIR}/hiredis.pc
        DESTINATION ${CMAKE_INSTALL_LIBDIR}/pkgconfig)

export(EXPORT hiredis-targets
        FILE "${CMAKE_CURRENT_BINARY_DIR}/hiredis-targets.cmake"
        NAMESPACE hiredis::)

SET(CMAKE_CONF_INSTALL_DIR share/hiredis)
SET(INCLUDE_INSTALL_DIR include)
include(CMakePackageConfigHelpers)
configure_package_config_file(hiredis-config.cmake.in ${CMAKE_CURRENT_BINARY_DIR}/hiredis-config.cmake
        INSTALL_DESTINATION ${CMAKE_CONF_INSTALL_DIR}
        PATH_VARS INCLUDE_INSTALL_DIR)

INSTALL(EXPORT hiredis-targets
        FILE hiredis-targets.cmake
        NAMESPACE hiredis::
        DESTINATION ${CMAKE_CONF_INSTALL_DIR})

INSTALL(FILES ${CMAKE_CURRENT_BINARY_DIR}/hiredis-config.cmake
        DESTINATION ${CMAKE_CONF_INSTALL_DIR})


IF(ENABLE_SSL)
    IF (NOT OPENSSL_ROOT_DIR)
        IF (APPLE)
            SET(OPENSSL_ROOT_DIR "/usr/local/opt/openssl")
        ENDIF()
    ENDIF()
    FIND_PACKAGE(OpenSSL REQUIRED)
    SET(hiredis_ssl_sources
            ssl.c)
    ADD_LIBRARY(hiredis_ssl SHARED
            ${hiredis_ssl_sources})

    IF (APPLE)
        SET_PROPERTY(TARGET hiredis_ssl PROPERTY LINK_FLAGS "-Wl,-undefined -Wl,dynamic_lookup")
    ENDIF()

    SET_TARGET_PROPERTIES(hiredis_ssl
            PROPERTIES
            WINDOWS_EXPORT_ALL_SYMBOLS TRUE
            VERSION "${HIREDIS_SONAME}")

    TARGET_INCLUDE_DIRECTORIES(hiredis_ssl PRIVATE "${OPENSSL_INCLUDE_DIR}")
    TARGET_LINK_LIBRARIES(hiredis_ssl PRIVATE ${OPENSSL_LIBRARIES})
    IF (WIN32 OR MINGW)
        TARGET_LINK_LIBRARIES(hiredis_ssl PRIVATE hiredis)
    ENDIF()
    CONFIGURE_FILE(hiredis_ssl.pc.in hiredis_ssl.pc @ONLY)

    INSTALL(TARGETS hiredis_ssl
            EXPORT hiredis_ssl-targets
            RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
            ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR})

    INSTALL(FILES hiredis_ssl.h
            DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/hiredis)

    INSTALL(FILES ${CMAKE_CURRENT_BINARY_DIR}/hiredis_ssl.pc
            DESTINATION ${CMAKE_INSTALL_LIBDIR}/pkgconfig)

    export(EXPORT hiredis_ssl-targets
            FILE "${CMAKE_CURRENT_BINARY_DIR}/hiredis_ssl-targets.cmake"
            NAMESPACE hiredis::)

    SET(CMAKE_CONF_INSTALL_DIR share/hiredis_ssl)
    configure_package_config_file(hiredis_ssl-config.cmake.in ${CMAKE_CURRENT_BINARY_DIR}/hiredis_ssl-config.cmake
            INSTALL_DESTINATION ${CMAKE_CONF_INSTALL_DIR}
            PATH_VARS INCLUDE_INSTALL_DIR)

    INSTALL(EXPORT hiredis_ssl-targets
            FILE hiredis_ssl-targets.cmake
            NAMESPACE hiredis::
            DESTINATION ${CMAKE_CONF_INSTALL_DIR})

    INSTALL(FILES ${CMAKE_CURRENT_BINARY_DIR}/hiredis_ssl-config.cmake
            DESTINATION ${CMAKE_CONF_INSTALL_DIR})
ENDIF()

IF(NOT DISABLE_TESTS)
    ENABLE_TESTING()
    ADD_EXECUTABLE(hiredis-test test.c)
    IF(ENABLE_SSL_TESTS)
        ADD_DEFINITIONS(-DHIREDIS_TEST_SSL=1)
        TARGET_LINK_LIBRARIES(hiredis-test hiredis hiredis_ssl)
    ELSE()
        TARGET_LINK_LIBRARIES(hiredis-test hiredis)
    ENDIF()
    ADD_TEST(NAME hiredis-test
            COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/test.sh)
ENDIF()

# Add examples
IF(ENABLE_EXAMPLES)
    ADD_SUBDIRECTORY(examples)
ENDIF(ENABLE_EXAMPLES)
```

4、\redis-6.2\src\modules\

```c
cmake_minimum_required(VERSION 3.9)
set(CMAKE_BUILD_TYPE "Debug")

add_library(helloworld SHARED helloworld.c)
set_target_properties(helloworld PROPERTIES PREFIX "" SUFFIX ".so")

add_library(hellotype SHARED hellotype.c)
set_target_properties(hellotype PROPERTIES PREFIX "" SUFFIX ".so")

add_library(helloblock SHARED helloblock.c)
set_target_properties(helloblock PROPERTIES PREFIX "" SUFFIX ".so")

```

5、\redis-6.2\deps\lua\

```c
set(LUA_SRC
        src/lauxlib.c
        src/liolib.c
        src/lopcodes.c
        src/lstate.c
        src/lobject.c
        src/print.c
        src/lmathlib.c
        src/loadlib.c
        src/lvm.c
        src/lfunc.c
        src/lstrlib.c
        src/lua.c
        src/linit.c
        src/lstring.c
        src/lundump.c
        src/luac.c
        src/ltable.c
        src/ldump.c
        src/loslib.c
        src/lgc.c
        src/lzio.c
        src/ldblib.c
        src/strbuf.c
        src/lmem.c
        src/lcode.c
        src/ltablib.c
        src/lua_struct.c
        src/lapi.c
        src/lbaselib.c
        src/lua_cmsgpack.c
        src/ldebug.c
        src/lparser.c
        src/lua_cjson.c
        src/fpconv.c
        src/lua_bit.c
        src/llex.c
        src/ltm.c
        src/ldo.c
        )
add_library(lua STATIC ${LUA_SRC})
```

6、\redis-6.2\deps\

```c
add_subdirectory(hiredis)
add_subdirectory(linenoise)
add_subdirectory(lua)
```

7、\redis-6.2\deps\hiredis\examples\

```c
INCLUDE(FindPkgConfig)
# Check for GLib

PKG_CHECK_MODULES(GLIB2 glib-2.0)
if (GLIB2_FOUND)
    INCLUDE_DIRECTORIES(${GLIB2_INCLUDE_DIRS})
    LINK_DIRECTORIES(${GLIB2_LIBRARY_DIRS})
    ADD_EXECUTABLE(example-glib example-glib.c)
    TARGET_LINK_LIBRARIES(example-glib hiredis ${GLIB2_LIBRARIES})
ENDIF(GLIB2_FOUND)

FIND_PATH(LIBEV ev.h
    HINTS /usr/local /usr/opt/local
    ENV LIBEV_INCLUDE_DIR)

if (LIBEV)
    # Just compile and link with libev
    ADD_EXECUTABLE(example-libev example-libev.c)
    TARGET_LINK_LIBRARIES(example-libev hiredis ev)
ENDIF()

FIND_PATH(LIBEVENT event.h)
if (LIBEVENT)
    ADD_EXECUTABLE(example-libevent example-libevent)
    TARGET_LINK_LIBRARIES(example-libevent hiredis event)
ENDIF()

FIND_PATH(LIBUV uv.h)
IF (LIBUV)
    ADD_EXECUTABLE(example-libuv example-libuv.c)
    TARGET_LINK_LIBRARIES(example-libuv hiredis uv)
ENDIF()

IF (APPLE)
    FIND_LIBRARY(CF CoreFoundation)
    ADD_EXECUTABLE(example-macosx example-macosx.c)
    TARGET_LINK_LIBRARIES(example-macosx hiredis ${CF})
ENDIF()

IF (ENABLE_SSL)
    ADD_EXECUTABLE(example-ssl example-ssl.c)
    TARGET_LINK_LIBRARIES(example-ssl hiredis hiredis_ssl)
ENDIF()

ADD_EXECUTABLE(example example.c)
TARGET_LINK_LIBRARIES(example hiredis)

ADD_EXECUTABLE(example-push example-push.c)
TARGET_LINK_LIBRARIES(example-push hiredis)

```

下载redis源码之后，在CLion中选择 文件 -> 打开，选择下载好的源码文件夹，CLion会自动识别项目。

*注意：一定要选择 `CMake`项目，否则以 `Makefile`方式加载项目进入后，添加 `CMakeLists`文件是不能构建的，此时右键 `CMakeLists.txt`没有 `Load CMake Project`的选项，如果加载的方式错了，则需要退出 `CLion`，然后把 `redis`根目录下的 `.idea`文件全部删掉，再重新加载。*

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/051d8d07b2094a7184368dc13e0bf80c.png)

### 4>执行mkreleasehdr.sh脚本

在编译之前，需要在Cygwin64 Terminal上执行 mkreleasehdr.sh脚本(\redis-6.2\src)，由于该脚本格式是win格式，所以需要执行dos2unix命令转换，转换完之后再执行。

```
##转换格式 
dos2unix mkreleasehdr.sh 
##执行mkreleasehdr.sh 
./mkreleasehdr.sh
```

### 5>删除debug.c的两个函数

```
void dumpX86Calls(void *addr, size_t len)
void dumpCodeAroundEIP(void *eip)
```

### 6>重新加载CMake项目

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/ea60d0a03503439887f05686d22cf456.png)

找到server的main函数 添加一行打印

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/7d3ba22bd1cf45dbaa95d77b3b9f0b57.png)![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/d6628cbeedde4f69b7bfd0da98f26f8d.png)

### 8>启动RedisServer

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/6c7228c693d640bebecc8b5f60f62a55.png)

运行显示

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/4fc3c0c777a742f6b2a16da8b4f1c26a.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/ba59a3699ded453f97f8df39430551a2.png)

# 二、Redis6.2源码整体结构

## 2.1、核心设计

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/1b8db7e9cc2d46fd8debfb39557f3d84.png)

## 2.2、源码概述

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/e29fda518fc24c9b8f7f26a2758d48af.png)

# 三、TCP通信

## 3.1、Client/Server

### 1>请求响应模式

* 串行
  一次请求对应一次响应

  ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/cb650d1ff7d34cd6864bbb2a13b2620f.png)

  ```
  set name zhf
  ok

  ```
* 双工

  多次请求对应多个响应

  ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/48d91d4b0dc04a78945c2bc253d061c3.png)

  pipeline 管道批量处理
* 原子化批量操作

  事务

  ```
  multi
  set name xx
  set age xx
  exec 
  ```
* 发布订阅

  pub/sub
* 脚本批量执行

  lua脚本执行

  原子性： lua脚本>事务>pipeline

### 2>Server

server.c

#### **main函数**

1. 初始化服务器配置

   ```
   initServerConfig();
   ```
2. 初始化ACL

   ```
   ACLInit();
   ```
3. 初始化模组

   ```
   moduleInitModulesSystem();
   ```
4. 初始化服务器

   ```
   initServer();
   ```
5. 从持久化文件中恢复读取数据

   ```
   loadDataFromDisk();
   ```
6. 启动事件处理

   ```
   aeMain(server.el);
   ```

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/9ceca0e4205d456daf6d6eb4c5ee20c5.png)

#### **initServer**

1. 设置全局信号
2. 初始化Server的属性
3. 创建事件处理对象eventLoop
4. 给数据库分配内存
5. 打开监听端口，监听请求
6. 初始化数据库，创建字典
7. 创建时间事件和文件事件
8. 注册事件处理器

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/9de15d5bd588435db0385fad59c33c06.png)

src/redis-cli.c  (command line interface)

deps/hiredis/hiredis.c

### 3>Client

#### main函数

1. 配置参数初始化: config
2. 初始化用户接口：parseOptions
3. 根据配置启动相应的模式
4. 与服务器建立连接：cliConnect(0)
5. 发送请求，接收响应：repl()

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/b941c0889921411ebb2f69b4e43952a4.png)

#### 建立连接

```
redis-cli.c--->hiredis.c---->net.c
```

1. 调用redisConnect
   ```
   redisConnect
   ```
2. 与服务端创建TCP连接

```
hiredis.c
```

```
redisConnectWithOptions--->redisContextConnectBindTcp
```

#### 发送/接收消息

1. 初始化帮助信息
2. 使用linenoise接收输入命令
3. 判断是否需要过滤掉重复的参数
4. 发送命令给服务器端
5. 接收服务器端返回的结果

## 3.2、RESP3通信协议

#### RESP3

RESP3是RESP v2的更新版本

**RESP2的类型**

* 数组：N个其他类型的有序集合
* Blob string：二进制安全字符串
* 简单字符串：节省空间的非二进制安全字符串
* 简单错误：一个节省空间的非二进制安全错误代码和消息
* 数字：有符号64位范围内的整数

**RESP3引入的类型**

* Null：替换RESP v2*-1和$-1 单个Null值
* Double：浮点数
* Boolean: true or false
* Blob error：二进制安全错误代码和消息
* Verbatim string：一个二进制安全字符串
* Map:键值对的有序集合

#### 协议生成

在repl模式下

1. linenoise 的prompt 提示输入命令
2. 调用fgets从标准输入中读取字符串
3. 将 line 中的字符串分割成几个不同的参数（cliSplitArgs ）
4. 如果是exit、quit等命令，则直接执行
5. 如果不是将命令和参数传入issueCommandRepeat
6. 最终调用redisFormatSdsCommandArgv生成RESP3格式的字符串数组

# 四、本章小结

## 1、源码环境搭建

Cygwin+CLion

引入Redis6.2

## 2、整体介绍

核心设计

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1661567454028/b61fbf854ee143cb92697cae158e70fa.png)

## 3、Server的启动

* main()
* initServer()

## 4、Client的启动

* main()
* cliConnect()
* repl()
* 协议的生成()
