# Redis
## 整体架构设计

![](.\images\redis核心设计.png)

<img src=".\images\redis01.jpg" style="zoom: 33%;" />

## 数据类型

<img src=".\images\基本数据类型.png" style="zoom: 67%;" />

## 数据结构

### redisObject

```c
typedef struct redisObject {
    unsigned type: 4;
    unsigned encoding: 4;
    unsigned lru: LRU_BITS; /* LRU time (relative to global lru_clock) or
                            * LFU data (least significant 8 bits frequency
                            * and most significant 16 bits access time). */
    int refcount;
    void *ptr;
} robj;
```

**createSharedObjects**

共享对象

### sds

sds是构成String类型的数据结构

#### 对比char * 字符数组的优势

**原始char*字符数组**

char*字符数组是一块连续的内存空间，以此存放了每个字符，而**字符数组的结尾位置就用“\0”表示，意思是指字符串的结束。** 

> C 语言标准库中字符串的操作函数，就会通过检查字符数组中是否有“\0”，来判断字符串是否结束。比如，strlen 函数就是一种字符串操作函数，它可以返回一个字符串的长度。这个函数会遍历字符数组中的每一个字符，并进行计数，**直到检查的字符为“\0”**。时间复杂度为O(N)，并且无法存储二进制数据。
>
> strcat 函数和 strlen 函数类似，复杂度都很高，也都需要先通过**遍历字符串才能得到目标字符串的末尾**。然后对于 strcat 函数来说，还要再遍历源字符串才能完成追加。另外，它在把源字符串追加到目标字符串末尾时，**还需要确认目标字符串具有足够的可用空间**，否则就无法追加。

<img src=".\images\sds01.jpg" style="zoom: 33%;" />

**sds**

SDS 结构里包含了一个字符数组 buf[]，用来保存实际数据。同时，SDS 结构里还包含了三个元数据，分别是**字符数组现有长度 len**、**分配给字符数组的空间长度 alloc**，以及 **SDS 类型 flags**。

<img src=".\images\sds02.jpg" style="zoom:33%;" />

- 在字符数组的基础上，增加了字符数组长度和分配空间大小等元数据。使得获取长度的时间复杂度为O(1)
- SDS 不通过字符串中的“\0”字符判断字符串结束，而是直接将其作为二进制数据处理，可以用来保存图片等二进制数据。
- SDS 中是通过设计不同 SDS 类型来表示不同大小的字符串，来节省sdshdr头部数据的内存。
- **attribute** ((**packed**))不会使sdshdr结构体按照8字节倍数对齐，节省内存。
- SDS 把目标字符串的**空间检查和扩容封装在了 sdsMakeRoomFor 函数中**，并且在涉及字符串空间变化的操作中，如追加、复制等，会直接调用该函数。







```c
//sds.h

#ifndef __SDS_H
#define __SDS_H

#define SDS_MAX_PREALLOC (1024*1024)
extern const char *SDS_NOINIT;

//把char * 取别名为sds
typedef char *sds;



//sdshdr5 从不使用，我们只是直接访问 flags 字节。但是，此处将记录类型 sdshdr5 字符串的布局。
/* Note: sdshdr5 is never used, we just access the flags byte directly.
 * However is here to document the layout of type 5 SDS strings. */
struct __attribute__ ((__packed__)) sdshdr5 {
    unsigned char flags; /* 3 lsb of type, and 5 msb of string length */
    char buf[];
};

//以下是sdshdr8、sdshdr16、sdshdr32、sdshdr64的结构体 
//后面的数字则分别代表此结构体所能容纳的最大字符串为 2^8、2^16...字节
struct __attribute__ ((__packed__)) sdshdr8 {
    //已经使用的长度 unit8为8个位 也就是一个字节
    uint8_t len; /* used */
    //总共分配的长度 排除sdshdr头部的长度
    uint8_t alloc; /* excluding the header and null terminator */
    //sdshdr类型 只用了3位 剩余5位保留
    unsigned char flags; /* 3 lsb of type, 5 unused bits */
    //因为c中char为1字节 这里就是字节数组 实际存储数据的
    char buf[];
};

struct __attribute__ ((__packed__)) sdshdr16 {
    uint16_t len; /* used */
    uint16_t alloc; /* excluding the header and null terminator */
    unsigned char flags; /* 3 lsb of type, 5 unused bits */
    char buf[];
};

struct __attribute__ ((__packed__)) sdshdr32 {
    uint32_t len; /* used */
    uint32_t alloc; /* excluding the header and null terminator */
    unsigned char flags; /* 3 lsb of type, 5 unused bits */
    char buf[];
};

struct __attribute__ ((__packed__)) sdshdr64 {
    uint64_t len; /* used */
    uint64_t alloc; /* excluding the header and null terminator */
    unsigned char flags; /* 3 lsb of type, 5 unused bits */
    char buf[];
};

//sdshdr的类型

#define SDS_TYPE_5  0
#define SDS_TYPE_8  1
#define SDS_TYPE_16 2
#define SDS_TYPE_32 3
#define SDS_TYPE_64 4
//计算类型的掩码
#define SDS_TYPE_MASK 7
#define SDS_TYPE_BITS 3
//计算sdshdr基地址的函数 通过sds地址-sdshdr的结构体大小 = sdshdr的基地址
#define SDS_HDR_VAR(T,s) struct sdshdr##T *sh = (void*)((s)-(sizeof(struct sdshdr##T)));
#define SDS_HDR(T,s) ((struct sdshdr##T *)((s)-(sizeof(struct sdshdr##T))))
#define SDS_TYPE_5_LEN(f) ((f)>>SDS_TYPE_BITS)

//获得sdshdr的len
static inline size_t sdslen(const sds s) {
    unsigned char flags = s[-1];
    switch (flags & SDS_TYPE_MASK) {
        case SDS_TYPE_5:
            return SDS_TYPE_5_LEN(flags);
        case SDS_TYPE_8:
            return SDS_HDR(8, s)->len;
        case SDS_TYPE_16:
            return SDS_HDR(16, s)->len;
        case SDS_TYPE_32:
            return SDS_HDR(32, s)->len;
        case SDS_TYPE_64:
            return SDS_HDR(64, s)->len;
    }
    return 0;
}

//获得sdshdr的可用长度
static inline size_t sdsavail(const sds s) {
    unsigned char flags = s[-1];
    switch (flags & SDS_TYPE_MASK) {
        case SDS_TYPE_5: {
            return 0;
        }
        case SDS_TYPE_8: {
            SDS_HDR_VAR(8, s);
            return sh->alloc - sh->len;
        }
        case SDS_TYPE_16: {
            SDS_HDR_VAR(16, s);
            return sh->alloc - sh->len;
        }
        case SDS_TYPE_32: {
            SDS_HDR_VAR(32, s);
            return sh->alloc - sh->len;
        }
        case SDS_TYPE_64: {
            SDS_HDR_VAR(64, s);
            return sh->alloc - sh->len;
        }
    }
    return 0;
}

//设置sdshdr的长度
static inline void sdssetlen(sds s, size_t newlen) {
    unsigned char flags = s[-1];
    switch (flags & SDS_TYPE_MASK) {
        case SDS_TYPE_5: {
            unsigned char *fp = ((unsigned char *) s) - 1;
            *fp = SDS_TYPE_5 | (newlen << SDS_TYPE_BITS);
        }
        break;
        case SDS_TYPE_8:
            SDS_HDR(8, s)->len = newlen;
            break;
        case SDS_TYPE_16:
            SDS_HDR(16, s)->len = newlen;
            break;
        case SDS_TYPE_32:
            SDS_HDR(32, s)->len = newlen;
            break;
        case SDS_TYPE_64:
            SDS_HDR(64, s)->len = newlen;
            break;
    }
}

//增加sdshdr的长度
static inline void sdsinclen(sds s, size_t inc) {
    unsigned char flags = s[-1];
    switch (flags & SDS_TYPE_MASK) {
        case SDS_TYPE_5: {
            unsigned char *fp = ((unsigned char *) s) - 1;
            unsigned char newlen = SDS_TYPE_5_LEN(flags) + inc;
            *fp = SDS_TYPE_5 | (newlen << SDS_TYPE_BITS);
        }
        break;
        case SDS_TYPE_8:
            SDS_HDR(8, s)->len += inc;
            break;
        case SDS_TYPE_16:
            SDS_HDR(16, s)->len += inc;
            break;
        case SDS_TYPE_32:
            SDS_HDR(32, s)->len += inc;
            break;
        case SDS_TYPE_64:
            SDS_HDR(64, s)->len += inc;
            break;
    }
}

/* sdsalloc() = sdsavail() + sdslen() */
static inline size_t sdsalloc(const sds s) {
    unsigned char flags = s[-1];
    switch (flags & SDS_TYPE_MASK) {
        case SDS_TYPE_5:
            return SDS_TYPE_5_LEN(flags);
        case SDS_TYPE_8:
            return SDS_HDR(8, s)->alloc;
        case SDS_TYPE_16:
            return SDS_HDR(16, s)->alloc;
        case SDS_TYPE_32:
            return SDS_HDR(32, s)->alloc;
        case SDS_TYPE_64:
            return SDS_HDR(64, s)->alloc;
    }
    return 0;
}

//设置sdshdr的alloc
static inline void sdssetalloc(sds s, size_t newlen) {
    unsigned char flags = s[-1];
    switch (flags & SDS_TYPE_MASK) {
        case SDS_TYPE_5:
            /* Nothing to do, this type has no total allocation info. */
            break;
        case SDS_TYPE_8:
            SDS_HDR(8, s)->alloc = newlen;
            break;
        case SDS_TYPE_16:
            SDS_HDR(16, s)->alloc = newlen;
            break;
        case SDS_TYPE_32:
            SDS_HDR(32, s)->alloc = newlen;
            break;
        case SDS_TYPE_64:
            SDS_HDR(64, s)->alloc = newlen;
            break;
    }
}


```











#### sds创建

<img src=".\images\sds04.jpg" style="zoom: 33%;" />

**嵌入式字符串**

在进行**createEmbeddedStringObject** 函数分配的时候，不采用上面的**createRawStringObject**的两次分配方式，通过zmalloc(sizeof(robj) + sizeof(struct sdshdr8) + len + 1); 进行一次内存分配，避免内存碎片和两次内存分配的开销

<img src=".\images\sds03.jpg" style="zoom: 33%;" />

```c
//object.c


/* Create a string object with EMBSTR encoding if it is smaller than
 * OBJ_ENCODING_EMBSTR_SIZE_LIMIT, otherwise the RAW encoding is
 * used.
 *
 * The current limit of 44 is chosen so that the biggest string object
 * we allocate as EMBSTR will still fit into the 64 byte arena of jemalloc. */
#define OBJ_ENCODING_EMBSTR_SIZE_LIMIT 44

//创建一个String类型的robj
robj *createStringObject(const char *ptr, size_t len) {
    //如果字符串长度小于44 那么使用embstr编码
    if (len <= OBJ_ENCODING_EMBSTR_SIZE_LIMIT)
        return createEmbeddedStringObject(ptr, len);
    else
        //否则使用raw编码
        return createRawStringObject(ptr, len);
}


//以embstr编码创建对象
/* Create a string object with encoding OBJ_ENCODING_EMBSTR, that is
 * an object where the sds string is actually an unmodifiable string
 * allocated in the same chunk as the object itself. */
robj *createEmbeddedStringObject(const char *ptr, size_t len) {
    //分配内存 大小为robj的结构体大小+sdshdr8的结构体大小 + len + 1
    // + 1 是最后还会放一个"\0"
    //只分配一次的嵌入式字符串
    robj *o = zmalloc(sizeof(robj) + sizeof(struct sdshdr8) + len + 1);
    // o是robj类型 +1也就是 o + sizeof(robj) 取到sdshdr8的基地址
    struct sdshdr8 *sh = (void *) (o + 1);

    //设置robj的类型为String
    o->type = OBJ_STRING;
    //设置编码为embstr
    o->encoding = OBJ_ENCODING_EMBSTR;
    //指针指向sdshdr的buf实际存储数据的地址
    o->ptr = sh + 1;
    //引用计数设置为1
    o->refcount = 1;
    //@todo LRU
    if (server.maxmemory_policy & MAXMEMORY_FLAG_LFU) {
        o->lru = (LFUGetTimeInMinutes() << 8) | LFU_INIT_VAL;
    } else {
        o->lru = LRU_CLOCK();
    }

    //设置sdshdr的长度
    sh->len = len;
    sh->alloc = len;
    //设置类型
    sh->flags = SDS_TYPE_8;
    //不初始化的直接末尾加\0
    if (ptr == SDS_NOINIT)
        sh->buf[len] = '\0';
    else if (ptr) {
        //设置数据
        memcpy(sh->buf, ptr, len);
        sh->buf[len] = '\0';
    } else {
        //初始化全为0
        memset(sh->buf, 0, len + 1);
    }
    return o;
}


//创建raw编码的字符串
/* Create a string object with encoding OBJ_ENCODING_RAW, that is a plain
 * string object where o->ptr points to a proper sds string. */
robj *createRawStringObject(const char *ptr, size_t len) {
    //创建robj对象 sdsnewlen是实际创建sds的方法
    return createObject(OBJ_STRING, sdsnewlen(ptr, len));
}


//创建对象 
robj *createObject(int type, void *ptr) {
    //分配robj的大小
    robj *o = zmalloc(sizeof(*o));
    //设置type、encoding和指针、引用计数
    o->type = type;
    o->encoding = OBJ_ENCODING_RAW;
    o->ptr = ptr;
    o->refcount = 1;

    //@todo LRU
    /* Set the LRU to the current lruclock (minutes resolution), or
     * alternatively the LFU counter. */
    if (server.maxmemory_policy & MAXMEMORY_FLAG_LFU) {
        o->lru = (LFUGetTimeInMinutes() << 8) | LFU_INIT_VAL;
    } else {
        o->lru = LRU_CLOCK();
    }
    return o;
}



//尝试将一个String类型的robj的编码改成int节省内存
/* Try to encode a string object in order to save space */
robj *tryObjectEncoding(robj *o) {
    long value;
    //获取sds的地址
    sds s = o->ptr;
    size_t len;

    /* Make sure this is a string object, the only type we encode
     * in this function. Other types use encoded memory efficient
     * representations but are handled by the commands implementing
     * the type. */
    //确保是一个String类型
    serverAssertWithInfo(NULL, o, o->type == OBJ_STRING);

    /* We try some specialized encoding only for objects that are
     * RAW or EMBSTR encoded, in other words objects that are still
     * in represented by an actually array of chars. */
    //确定编码是embstr或者raw
    if (!sdsEncodedObject(o)) return o;

    /* It's not safe to encode shared objects: shared objects can be shared
     * everywhere in the "object space" of Redis and may end in places where
     * they are not handled. We handle them only as values in the keyspace. */
    //确定引用计数为1
    if (o->refcount > 1) return o;

    /* Check if we can represent this string as a long integer.
     * Note that we are sure that a string larger than 20 chars is not
     * representable as a 32 nor 64 bit integer. */
    //获取sds的长度
    len = sdslen(s);
    //如果长度<=20并且转化成long long数字类型成功
    if (len <= 20 && string2l(s, len, &value)) {
        /* This object is encodable as a long. Try to use a shared object.
         * Note that we avoid using shared integers when maxmemory is used
         * because every object needs to have a private LRU field for the LRU
         * algorithm to work well. */
        //尝试使用共享整数
        if ((server.maxmemory == 0 ||
             !(server.maxmemory_policy & MAXMEMORY_FLAG_NO_SHARED_INTEGERS)) &&
            value >= 0 &&
            value < OBJ_SHARED_INTEGERS) {
            //减少引用计数来释放原来的空间
            decrRefCount(o);
            //增加共享整数的引用计数
            incrRefCount(shared.integers[value]);
            //返回共享整数
            return shared.integers[value];
        } else {
            //不使用共享整数
            //编码是raw
            if (o->encoding == OBJ_ENCODING_RAW) {
                //释放原空间
                sdsfree(o->ptr);
                //编码改成int
                o->encoding = OBJ_ENCODING_INT;
                //指针指向value
                o->ptr = (void *) value;
                return o;
             //编码是embstr
            } else if (o->encoding == OBJ_ENCODING_EMBSTR) {
                //减少引用计数来释放原来的空间
                decrRefCount(o);
                //创建String从long long value
                return createStringObjectFromLongLongForValue(value);
            }
        }
    }

    /* If the string is small and is still RAW encoded,
     * try the EMBSTR encoding which is more efficient.
     * In this representation the object and the SDS string are allocated
     * in the same chunk of memory to save space and cache misses. */
    if (len <= OBJ_ENCODING_EMBSTR_SIZE_LIMIT) {
        robj *emb;

        if (o->encoding == OBJ_ENCODING_EMBSTR) return o;
        emb = createEmbeddedStringObject(s, sdslen(s));
        decrRefCount(o);
        return emb;
    }

    /* We can't encode the object...
     *
     * Do the last try, and at least optimize the SDS string inside
     * the string object to require little space, in case there
     * is more than 10% of free space at the end of the SDS string.
     *
     * We do that only for relatively large strings as this branch
     * is only entered if the length of the string is greater than
     * OBJ_ENCODING_EMBSTR_SIZE_LIMIT. */
    trimStringObjectIfNeeded(o);

    /* Return the original object. */
    return o;
}

//当需要 LFU/LRU 信息时避免共享对象，即当对象被用作键空间中的值时，并且 Redis 配置为基于LFU/LRU 进行驱逐。
robj *createStringObjectFromLongLongForValue(long long value) {
    return createStringObjectFromLongLongWithOptions(value, 1);
}



/* Create a string object from a long long value. When possible returns a
 * shared integer object, or at least an integer encoded one.
 *
 * If valueobj is non zero, the function avoids returning a shared
 * integer, because the object is going to be used as value in the Redis key
 * space (for instance when the INCR command is used), so we want LFU/LRU
 * values specific for each key. */
robj *createStringObjectFromLongLongWithOptions(long long value, int valueobj) {
    robj *o;

    if (server.maxmemory == 0 ||
        !(server.maxmemory_policy & MAXMEMORY_FLAG_NO_SHARED_INTEGERS)) {
        /* If the maxmemory policy permits, we can still return shared integers
         * even if valueobj is true. */
        //如果 maxmemory 策略允许，即使 valueobj 为 true，我们仍然可以返回共享整数。
        valueobj = 0;
    }
	
    //如果使用共享整数
    if (value >= 0 && value < OBJ_SHARED_INTEGERS && valueobj == 0) {
        //返回共享整数
        incrRefCount(shared.integers[value]);
        o = shared.integers[value];
    } else {
        //如果value在long范围内
        if (value >= LONG_MIN && value <= LONG_MAX) {
            //采用int进行编码
            o = createObject(OBJ_STRING, NULL);
            o->encoding = OBJ_ENCODING_INT;
            o->ptr = (void *) ((long) value);
        } else {
            //超过long的范围 采用embstr或者raw编码
            o = createObject(OBJ_STRING, sdsfromlonglong(value));
        }
    }
    return o;
}


```



```c
//sds.c

//创建sds的方法
sds sdsnewlen(const void *init, size_t initlen) {
    return _sdsnewlen(init, initlen, 0);
}


/* Create a new sds string with the content specified by the 'init' pointer
 * and 'initlen'.
 * If NULL is used for 'init' the string is initialized with zero bytes.
 * If SDS_NOINIT is used, the buffer is left uninitialized;
 *
 * The string is always null-termined (all the sds strings are, always) so
 * even if you create an sds string with:
 *
 * mystring = sdsnewlen("abc",3);
 *
 * You can print the string with printf() as there is an implicit \0 at the
 * end of the string. However the string is binary safe and can contain
 * \0 characters in the middle, as the length is stored in the sds header. */
sds _sdsnewlen(const void *init, size_t initlen, int trymalloc) {
    void *sh;
    sds s;
    //根据长度决定分配sdshdr类型
    char type = sdsReqType(initlen);
    /* Empty strings are usually created in order to append. Use type 8
     * since type 5 is not good at this. */
    //如果类型是sdshdr5并且初始长度为0 那么使用sdshdr8 因为sdshdr5是不能扩充的
    if (type == SDS_TYPE_5 && initlen == 0) type = SDS_TYPE_8;
    //获取sdshdr的结构体大小
    int hdrlen = sdsHdrSize(type);
    unsigned char *fp; /* flags pointer. */
    size_t usable;

    assert(initlen + hdrlen + 1 > initlen); /* Catch size_t overflow */
    //@todo 确定内存分配
    //分配hdrlen + initlen + 1的内存空间
    sh = trymalloc ? s_trymalloc_usable(hdrlen + initlen + 1, &usable) : s_malloc_usable(hdrlen + initlen + 1, &usable);
    if (sh == NULL) return NULL;
    if (init == SDS_NOINIT)
        init = NULL;
    else if (!init)
        //初始化为0
        memset(sh, 0, hdrlen + initlen + 1);
    //根据sdshdr的基地址 找到buf的地址
    s = (char *) sh + hdrlen;
    //找到flag的地址
    fp = ((unsigned char *) s) - 1;
    //计算可用空间大小
    usable = usable - hdrlen - 1;
    if (usable > sdsTypeMaxSize(type))
        usable = sdsTypeMaxSize(type);
    switch (type) {
        case SDS_TYPE_5: {
            *fp = type | (initlen << SDS_TYPE_BITS);
            break;
        }
        case SDS_TYPE_8: {
            //找到sdshdr的基地址
            SDS_HDR_VAR(8, s);
            //设置len和alloc type
            sh->len = initlen;
            sh->alloc = usable;
            *fp = type;
            break;
        }
        case SDS_TYPE_16: {
            SDS_HDR_VAR(16, s);
            sh->len = initlen;
            sh->alloc = usable;
            *fp = type;
            break;
        }
        case SDS_TYPE_32: {
            SDS_HDR_VAR(32, s);
            sh->len = initlen;
            sh->alloc = usable;
            *fp = type;
            break;
        }
        case SDS_TYPE_64: {
            SDS_HDR_VAR(64, s);
            sh->len = initlen;
            sh->alloc = usable;
            *fp = type;
            break;
        }
    }
    if (initlen && init)
        //初始化数据
        memcpy(s, init, initlen);
    //末尾+ \0
    s[initlen] = '\0';
    return s;
}


//根据长度决定分配sdshdr类型

static inline char sdsReqType(size_t string_size) {
    if (string_size < 1 << 5)
        return SDS_TYPE_5;
    if (string_size < 1 << 8)
        return SDS_TYPE_8;
    if (string_size < 1 << 16)
        return SDS_TYPE_16;
#if (LONG_MAX == LLONG_MAX)
    if (string_size < 1ll << 32)
        return SDS_TYPE_32;
    return SDS_TYPE_64;
#else
    return SDS_TYPE_32;
#endif
}


//获取sdshdr的结构体大小
static inline int sdsHdrSize(char type) {
    switch (type & SDS_TYPE_MASK) {
        case SDS_TYPE_5:
            return sizeof(struct sdshdr5);
        case SDS_TYPE_8:
            return sizeof(struct sdshdr8);
        case SDS_TYPE_16:
            return sizeof(struct sdshdr16);
        case SDS_TYPE_32:
            return sizeof(struct sdshdr32);
        case SDS_TYPE_64:
            return sizeof(struct sdshdr64);
    }
    return 0;
}

//从long long 类型转化sds
sds sdsfromlonglong(long long value) {
    char buf[SDS_LLSTR_SIZE];
    //将value值转换成字符串
    int len = sdsll2str(buf, value);
	//新建sds
    return sdsnewlen(buf, len);
}
```



#### sds释放

```c
//sds.c

/* Free an sds string. No operation is performed if 's' is NULL. */
void sdsfree(sds s) {
    if (s == NULL) return;
    //@todo 确定释放
    //取sdshdr基地址进行释放
    s_free((char *) s - sdsHdrSize(s[-1]));
}

```



#### sds扩容

```c
//sds.c

//sds追加字符串
sds sdscatlen(sds s, const void *t, size_t len) {
    //获取当前sds的长度
    size_t curlen = sdslen(s);

    //确定是否扩容
    s = sdsMakeRoomFor(s, len);
    if (s == NULL) return NULL;
    //追加字符串
    memcpy(s + curlen, t, len);
    //设置长度
    sdssetlen(s, curlen + len);
    //结尾设置\0
    s[curlen + len] = '\0';
    return s;
}


/* Enlarge the free space at the end of the sds string so that the caller
 * is sure that after calling this function can overwrite up to addlen
 * bytes after the end of the string, plus one more byte for nul term.
 *
 * Note: this does not change the *length* of the sds string as returned
 * by sdslen(), but only the free buffer space we have. */
sds sdsMakeRoomFor(sds s, size_t addlen) {
    void *sh, *newsh;
    size_t avail = sdsavail(s);
    size_t len, newlen, reqlen;
    char type, oldtype = s[-1] & SDS_TYPE_MASK;
    int hdrlen;
    size_t usable;

    //如果剩下的内存够用 那么直接返回
    /* Return ASAP if there is enough space left. */
    if (avail >= addlen) return s;

    
    len = sdslen(s);
    sh = (char *) s - sdsHdrSize(oldtype);
    reqlen = newlen = (len + addlen);
    assert(newlen > len); /* Catch size_t overflow */
    //计算新扩容大小
    //如果newlen小于1024 * 1024 = 1M 
    if (newlen < SDS_MAX_PREALLOC)
        //直接扩容两倍
        newlen *= 2;
    else
        //否则 + 1M
        newlen += SDS_MAX_PREALLOC;

    //决定sdshdr类型
    type = sdsReqType(newlen);

    /* Don't use type 5: the user is appending to the string and type 5 is
     * not able to remember empty space, so sdsMakeRoomFor() must be called
     * at every appending operation. */
    //sdshdr5的转换为sdshdr8
    if (type == SDS_TYPE_5) type = SDS_TYPE_8;

    hdrlen = sdsHdrSize(type);
    assert(hdrlen + newlen + 1 > reqlen); /* Catch size_t overflow */
    //如果和原sdshdr类型一致
    if (oldtype == type) {
        //重新分配空间
        newsh = s_realloc_usable(sh, hdrlen + newlen + 1, &usable);
        if (newsh == NULL) return NULL;
        //设置新地址空间
        s = (char *) newsh + hdrlen;
    } else {
        //如果和原sdshdr类型不一致
        /* Since the header size changes, need to move the string forward,
         * and can't use realloc */
        //分配新空间
        newsh = s_malloc_usable(hdrlen + newlen + 1, &usable);
        if (newsh == NULL) return NULL;
        //将旧数据移到新空间
        memcpy((char *) newsh + hdrlen, s, len + 1);
        //释放旧空间
        s_free(sh);
        //设置新空间地址
        s = (char *) newsh + hdrlen;
        //设置len和type
        s[-1] = type;
        sdssetlen(s, len);
    }
    //设置alloc
    usable = usable - hdrlen - 1;
    if (usable > sdsTypeMaxSize(type))
        usable = sdsTypeMaxSize(type);
    sdssetalloc(s, usable);
    return s;
}

```



### zskiplist

zskiplist的节点都是独一无二的(score和ele不可能都一样)，span的值就是当前节点距离下一个节点的level0的小线段数量，例如 11的level0的span为1，level1的span为2，那么rank就是从header开始到当前节点的level0的小线段数量的和，也就是查找路径的span的累加和，例如11的rank就是2。

> zrank命令返回的结果是zslGetRank方法-1，是从0开始的。在代码中的rank计算都是基于1开始的

<img src=".\images\zsl01.png" alt="image-20240820112340368" style="zoom: 33%;" />

```c
//server.h


/* ZSETs use a specialized version of Skiplists */
typedef struct zskiplistNode {
    //保存的元素
    sds ele;
    //权重
    double score;
    //第一层的前一个节点
    struct zskiplistNode *backward;

    //当前节点包含的层级
    struct zskiplistLevel {
        //当前层级的后一个节点
        struct zskiplistNode *forward;
        //当前节点和后一个节点的跨度
        unsigned long span;
    } level[];
} zskiplistNode;


typedef struct zskiplist {
	//头尾节点
    struct zskiplistNode *header, *tail;
    //包含节点的长度
    unsigned long length;
    //最大层级
    int level;
} zskiplist;
```

#### zsl创建

```c
//t_zset.c

/* Create a new skiplist. */
zskiplist *zslCreate(void) {
    int j;
    zskiplist *zsl;

    //分配zsl的空间
    zsl = zmalloc(sizeof(*zsl));
    zsl->level = 1;
    zsl->length = 0;
    //创建header节点
    zsl->header = zslCreateNode(ZSKIPLIST_MAXLEVEL,0,NULL);
    //对header节点的每一层level赋初始值
    for (j = 0; j < ZSKIPLIST_MAXLEVEL; j++) {
        zsl->header->level[j].forward = NULL;
        zsl->header->level[j].span = 0;
    }
    //设置backward和tail值
    zsl->header->backward = NULL;
    zsl->tail = NULL;
    return zsl;
}


/* Create a skiplist node with the specified number of levels.
 * The SDS string 'ele' is referenced by the node after the call. */
zskiplistNode *zslCreateNode(int level, double score, sds ele) {
    //分配zslnode + level * zsllevel的大小
    zskiplistNode *zn =
        zmalloc(sizeof(*zn)+level*sizeof(struct zskiplistLevel));
    //赋初始值
    zn->score = score;
    zn->ele = ele;
    return zn;
}
```



#### zsl插入节点

```c
//t_zset.c


/* Insert a new node in the skiplist. Assumes the element does not already
 * exist (up to the caller to enforce that). The skiplist takes ownership
 * of the passed SDS string 'ele'. */
zskiplistNode *zslInsert(zskiplist *zsl, double score, sds ele) {
    //记录每个level需要更新的节点
    zskiplistNode *update[ZSKIPLIST_MAXLEVEL], *x;
    //记录每个level的rank
    unsigned int rank[ZSKIPLIST_MAXLEVEL];
    int i, level;

    serverAssert(!isnan(score));
    
    //从header开始 从最高level遍历节点
    //目的是找到要插入节点在每一level应该插入的位置 以及 每一level要插入位置的rank值
    x = zsl->header;
    for (i = zsl->level-1; i >= 0; i--) {
        //取上层level的rank值
        /* store rank that is crossed to reach the insert position */
        rank[i] = i == (zsl->level-1) ? 0 : rank[i+1];
        //找到当前节点在当前level下 score小于或者score等于 ele小于的位置
        while (x->level[i].forward &&
                (x->level[i].forward->score < score ||
                    (x->level[i].forward->score == score &&
                    sdscmp(x->level[i].forward->ele,ele) < 0)))
        {
            //记录累计的rank
            rank[i] += x->level[i].span;
            //前进
            x = x->level[i].forward;
        }
        //记录每一层应该插入节点的位置
        update[i] = x;
    }
    /* we assume the element is not already inside, since we allow duplicated
     * scores, reinserting the same element should never happen since the
     * caller of zslInsert() should test in the hash table if the element is
     * already inside or not. */
    //获取随机的level数
    level = zslRandomLevel();
    //如果随机层数超过当前跳跃表最大层级
    if (level > zsl->level) {
        //初始化level以上的层级
        for (i = zsl->level; i < level; i++) {
            rank[i] = 0;
            update[i] = zsl->header;
            //跨度为整个level[0]的长度
            update[i]->level[i].span = zsl->length;
        }
        //重新设置zsl的level
        zsl->level = level;
    }
    //创建zslnode
    x = zslCreateNode(level,score,ele);
    //对zslnode的每一层设置属性
    for (i = 0; i < level; i++) {
       	//把当前节点插入到对应level的位置 
        x->level[i].forward = update[i]->level[i].forward;
        update[i]->level[i].forward = x;
		
        //@todo
        //以rank来计算插入节点的每一层span值 rank的差值就是小线段数量
	//计算的实际上是 插入后结点的span = update[0]层结点到tail的小线段数量 = update[i].span - (rank[0]-rank[i])
        /* update span covered by update[i] as x is inserted here */
        x->level[i].span = update[i]->level[i].span - (rank[0] - rank[i]);
        //更新update[i]节点的span值
        update[i]->level[i].span = (rank[0] - rank[i]) + 1;
    }

    //对于没有碰到的层级 >=level && <zsl.level 层级上的更新节点的span值需要+1
    /* increment span for untouched levels */
    for (i = level; i < zsl->level; i++) {
        update[i]->level[i].span++;
    }

    //设置插入节点的前一个节点
    x->backward = (update[0] == zsl->header) ? NULL : update[0];
    
    //如果后一个节点为null 那么把tail指向当前节点
    if (x->level[0].forward)
        x->level[0].forward->backward = x;
    else
        zsl->tail = x;
    //长度+1
    zsl->length++;
    return x;
}

#define ZSKIPLIST_MAXLEVEL 32 /* Should be enough for 2^64 elements */
#define ZSKIPLIST_P 0.25      /* Skiplist P = 1/4 */


/* Returns a random level for the new skiplist node we are going to create.
 * The return value of this function is between 1 and ZSKIPLIST_MAXLEVEL
 * (both inclusive), with a powerlaw-alike distribution where higher
 * levels are less likely to be returned. */
int zslRandomLevel(void) {
    //level = 1 必须有值
    int level = 1;
    //决定是否向上层创建 
    //random & 0xFFFF 也就是random % 65535
    //当random随机落在 四分之一 * 65535的区域内时 才向上创建 也就是说 每增加一层的概率为 25%
    while ((random()&0xFFFF) < (ZSKIPLIST_P * 0xFFFF))
        level += 1;
    //最大32层
    return (level<ZSKIPLIST_MAXLEVEL) ? level : ZSKIPLIST_MAXLEVEL;
}


```



#### zsl查找排名

```c
//t_zset.c

/* Find the rank for an element by both score and key.
 * Returns 0 when the element cannot be found, rank otherwise.
 * Note that the rank is 1-based due to the span of zsl->header to the
 * first element. */
//从1开始的rank方法 外层做了-1处理 所以我们zrank命令得到的才是从0开始的
unsigned long zslGetRank(zskiplist *zsl, double score, sds ele) {
    zskiplistNode *x;
    unsigned long rank = 0;
    int i;

    //rank值就是从最高层开始 累计查找到目标节点的span值累加
    x = zsl->header;
    for (i = zsl->level-1; i >= 0; i--) {
        while (x->level[i].forward &&
            (x->level[i].forward->score < score ||
                (x->level[i].forward->score == score &&
                sdscmp(x->level[i].forward->ele,ele) <= 0))) {
            rank += x->level[i].span;
            x = x->level[i].forward;
        }

        //找到目标值 返回累计的span值
        /* x might be equal to zsl->header, so test if obj is non-NULL */
        if (x->ele && sdscmp(x->ele,ele) == 0) {
            return rank;
        }
    }
    return 0;
}

```



#### zsl节点删除

```c
//t_zset.c


/* Delete an element with matching score/element from the skiplist.
 * The function returns 1 if the node was found and deleted, otherwise
 * 0 is returned.
 *
 * If 'node' is NULL the deleted node is freed by zslFreeNode(), otherwise
 * it is not freed (but just unlinked) and *node is set to the node pointer,
 * so that it is possible for the caller to reuse the node (including the
 * referenced SDS string at node->ele). */
int zslDelete(zskiplist *zsl, double score, sds ele, zskiplistNode **node) {
    zskiplistNode *update[ZSKIPLIST_MAXLEVEL], *x;
    int i;

   	//找到score和ele相同的前一个节点
    x = zsl->header;
    for (i = zsl->level-1; i >= 0; i--) {
        while (x->level[i].forward &&
                (x->level[i].forward->score < score ||
                    (x->level[i].forward->score == score &&
                     sdscmp(x->level[i].forward->ele,ele) < 0)))
        {
            x = x->level[i].forward;
        }
        //目的是为了让这里记录到前一个节点
        update[i] = x;
    }
    /* We may have multiple elements with the same score, what we need
     * is to find the element with both the right score and object. */
    //走到下一个节点 也就是score和ele相同的目标节点
    x = x->level[0].forward;
    //如果节点存在
    if (x && score == x->score && sdscmp(x->ele,ele) == 0) {
        //删除节点
        zslDeleteNode(zsl, x, update);
        //释放节点空间 @todo
        if (!node)
            zslFreeNode(x);
        else
            *node = x;
        return 1;
    }
    return 0; /* not found */
}


/* Internal function used by zslDelete, zslDeleteRangeByScore and
 * zslDeleteRangeByRank. */
void zslDeleteNode(zskiplist *zsl, zskiplistNode *x, zskiplistNode **update) {
    int i;
    //更新每一个level上的前一个节点的span和forward值
    for (i = 0; i < zsl->level; i++) {
        //如果下一个值是目标节点
        if (update[i]->level[i].forward == x) {
            //span += 目标节点的span - 1
            update[i]->level[i].span += x->level[i].span - 1;
            // forward = 目标节点的forward
            update[i]->level[i].forward = x->level[i].forward;
        } else {
            //否则就是span - 1
            update[i]->level[i].span -= 1;
        }
    }
    //如果level[0]的目标节点的下一个节点不为null
    if (x->level[0].forward) {
        //把下一个节点的backward指向目标节点的backward
        x->level[0].forward->backward = x->backward;
    } else {
        //否则tail指针指向下一个节点
        zsl->tail = x->backward;
    }
    //遍历层级 只有header的层
    while(zsl->level > 1 && zsl->header->level[zsl->level-1].forward == NULL)
        //层级-1
        zsl->level--;
    //zsl长度-1
    zsl->length--;
}
```



#### 优势

1. 以空间换时间的一种优化链表算法 优化有序链表 时间复杂度：O（logN） 空间复杂度：2N
2. 兼顾链表与数组优势的数据结构 索引： span的累加--->rank 作为索引
3. 从内存占用上来说，skiplist比平衡树更少 AVL： 每个节点有2个指针 sl:每个节点有1/1-P=1.33 个指针 P:0.25



### ziplist

<img src=".\images\zl02.jpg" style="zoom: 50%;" />

```c
//ziplist.c

/* The size of a ziplist header: two 32 bit integers for the total
 * bytes count and last item offset. One 16 bit integer for the number
 * of items field. */
//一个32bit的总字节数、一个32bit的最后一个元素的偏移量、16bit的元素数量
#define ZIPLIST_HEADER_SIZE     (sizeof(uint32_t)*2+sizeof(uint16_t))

/* Size of the "end of ziplist" entry. Just one byte. */
//end的大小 一个字节
#define ZIPLIST_END_SIZE        (sizeof(uint8_t))

//end的标识符
#define ZIP_END 255         /* Special "end of ziplist" entry. */


/* We use this function to receive information about a ziplist entry.
 * Note that this is not how the data is actually encoded, is just what we
 * get filled by a function in order to operate more easily. */
typedef struct zlentry {
    //存储 prerawlen 需要用到的字节数
    unsigned int prevrawlensize; /* Bytes used to encode the previous entry len*/
    //前一个结点占用的字节数
    unsigned int prevrawlen;     /* Previous entry len. */
    //存储 len 需要的字节数
    unsigned int lensize;        /* Bytes used to encode this entry type/len.
                                    For example strings have a 1, 2 or 5 bytes
    //数据长度                       header. Integers always use a single byte.*/
    unsigned int len;            /* Bytes used to represent the actual entry.
                                    For strings this is just the string length
                                    while for integers it is 1, 2, 3, 4, 8 or
                                    0 (for 4 bit immediate) depending on the
    //首部长度（prevrawlensize+lensize）                     number range. */
    unsigned int headersize;     /* prevrawlensize + lensize. */
    //表示当前元素的编码
    unsigned char encoding;      /* Set to ZIP_STR_* or ZIP_INT_* depending on
                                    the entry encoding. However for 4 bits
                                    immediate integers this can assume a range
                                    of values and must be range-checked. */
    //当前元素首地址
    unsigned char *p;            /* Pointer to the very start of the entry, that
                                    is, this points to prev-entry-len field. */
} zlentry;
```



#### zipEntry

![](.\images\zl03.png)

```c
//ziplist.c


/* Different encoding/length possibilities */
//11000000
#define ZIP_STR_MASK 0xc0
//00110000
#define ZIP_INT_MASK 0x30
//00000000
#define ZIP_STR_06B (0 << 6)
//01000000
#define ZIP_STR_14B (1 << 6)
//10000000
#define ZIP_STR_32B (2 << 6)
//11000000
#define ZIP_INT_16B (0xc0 | 0<<4)
//11010000
#define ZIP_INT_32B (0xc0 | 1<<4)
//11100000
#define ZIP_INT_64B (0xc0 | 2<<4)
//11110000
#define ZIP_INT_24B (0xc0 | 3<<4)
//11111110
#define ZIP_INT_8B 0xfe


#define ZIP_BIG_PREVLEN 254 /* ZIP_BIG_PREVLEN - 1 is the max number of bytes of
                               the previous entry, for the "prevlen" field prefixing
                               each entry, to be represented with just a single byte.
                               Otherwise it is represented as FE AA BB CC DD, where
                               AA BB CC DD are a 4 bytes unsigned integer
                               representing the previous entry len. */


/* Return the number of bytes used to encode the length of the previous
 * entry. The length is returned by setting the var 'prevlensize'. */
//获得prevlensize的长度
#define ZIP_DECODE_PREVLENSIZE(ptr, prevlensize) do {                          \
    //如果第一个字节的值小于254 那么代表只有一个字节长
	//否则就是ZIP_BIG_PREVLEN标识+4个字节长
    if ((ptr)[0] < ZIP_BIG_PREVLEN) {                                          \
        (prevlensize) = 1;                                                     \
    } else {                                                                   \
        (prevlensize) = 5;                                                     \
    }                                                                          \
} while(0)

/* Return the length of the previous element, and the number of bytes that
 * are used in order to encode the previous element length.
 * 'ptr' must point to the prevlen prefix of an entry (that encodes the
 * length of the previous entry in order to navigate the elements backward).
 * The length of the previous entry is stored in 'prevlen', the number of
 * bytes needed to encode the previous entry length are stored in
 * 'prevlensize'. */
//获取当前entry的prevlensize 
#define ZIP_DECODE_PREVLEN(ptr, prevlensize, prevlen) do {                     \
    ZIP_DECODE_PREVLENSIZE(ptr, prevlensize);                                  \
    //如果是prevlensize = 1 那么直接读prevlen
    if ((prevlensize) == 1) {                                                  \
        (prevlen) = (ptr)[0];                                                  \
    } else { /* prevlensize == 5 */                                            \
        //否则就读取后4个字节作为prevlen
        (prevlen) = ((ptr)[4] << 24) |                                         \
                    ((ptr)[3] << 16) |                                         \
                    ((ptr)[2] <<  8) |                                         \
                    ((ptr)[1]);                                                \
    }                                                                          \
} while(0)
    
/* Extract the encoding from the byte pointed by 'ptr' and set it into
 * 'encoding' field of the zlentry structure. */
//获取entry的encoding
#define ZIP_ENTRY_ENCODING(ptr, encoding) do {  \
    //取第一个字节
    (encoding) = ((ptr)[0]); \
    //如果encoding属于str编码 那么就取实际的str编码
    if ((encoding) < ZIP_STR_MASK) (encoding) &= ZIP_STR_MASK; \
} while(0)

    

/* Decode the entry encoding type and data length (string length for strings,
 * number of bytes used for the integer for integer entries) encoded in 'ptr'.
 * The 'encoding' variable is input, extracted by the caller, the 'lensize'
 * variable will hold the number of bytes required to encode the entry
 * length, and the 'len' variable will hold the entry length.
 * On invalid encoding error, lensize is set to 0. */
//获得entry的长度    
#define ZIP_DECODE_LENGTH(ptr, encoding, lensize, len) do {                    \
    //encoding属于str的
    if ((encoding) < ZIP_STR_MASK) {                                           \
        //encoding属于ZIP_STR_06B
        if ((encoding) == ZIP_STR_06B) {                                       \
            (lensize) = 1;                                                     \
            //取encoding的后六位为长度
            (len) = (ptr)[0] & 0x3f;                                           \
        } else if ((encoding) == ZIP_STR_14B) {                                \
            (lensize) = 2;                                                     \
            //取encoding的后六位+一个字节作为长度
            (len) = (((ptr)[0] & 0x3f) << 8) | (ptr)[1];                       \
        } else if ((encoding) == ZIP_STR_32B) {                                \
            (lensize) = 5;                                                     \
            //取后四个字节作为长度
            (len) = ((uint32_t)(ptr)[1] << 24) |                               \
                    ((uint32_t)(ptr)[2] << 16) |                               \
                    ((uint32_t)(ptr)[3] <<  8) |                               \
                    ((uint32_t)(ptr)[4]);                                      \
        } else {                                                               \
            (lensize) = 0; /* bad encoding, should be covered by a previous */ \
            (len) = 0;     /* ZIP_ASSERT_ENCODING / zipEncodingLenSize, or  */ \
                           /* match the lensize after this macro with 0.    */ \
        }                                                                      \
    } else {                                                                   \
        //整数的lensize固定为1 因为只有1、2、3、4、8字节的len
        (lensize) = 1;                                                         \
        if ((encoding) == ZIP_INT_8B)  (len) = 1;                              \
        else if ((encoding) == ZIP_INT_16B) (len) = 2;                         \
        else if ((encoding) == ZIP_INT_24B) (len) = 3;                         \
        else if ((encoding) == ZIP_INT_32B) (len) = 4;                         \
        else if ((encoding) == ZIP_INT_64B) (len) = 8;                         \
        //长度存储在encoding后四位
        else if (encoding >= ZIP_INT_IMM_MIN && encoding <= ZIP_INT_IMM_MAX)   \
            (len) = 0; /* 4 bit immediate */                                   \
        else                                                                   \
            (lensize) = (len) = 0; /* bad encoding */                          \
    }                                                                          \
} while(0)
    
    
    
    

//将p地址转换成zlentry的结构体
static inline void zipEntry(unsigned char *p, zlentry *e) {
    ZIP_DECODE_PREVLEN(p, e->prevrawlensize, e->prevrawlen);
    ZIP_ENTRY_ENCODING(p + e->prevrawlensize, e->encoding);
    ZIP_DECODE_LENGTH(p + e->prevrawlensize, e->encoding, e->lensize, e->len);
    assert(e->lensize != 0); /* check that encoding was valid. */
    e->headersize = e->prevrawlensize + e->lensize;
    e->p = p;
}
```







#### ziplist创建

```c
//ziplist.c

//获得ziplist总字节数字段
/* Return total bytes a ziplist is composed of. */
#define ZIPLIST_BYTES(zl)       (*((uint32_t*)(zl)))

//后移动4个字节 获得尾部偏移量字段
/* Return the offset of the last item inside the ziplist. */
#define ZIPLIST_TAIL_OFFSET(zl) (*((uint32_t*)((zl)+sizeof(uint32_t))))

//后移动8个字节 获得元素长度字段
/* Return the length of a ziplist, or UINT16_MAX if the length cannot be
 * determined without scanning the whole ziplist. */
#define ZIPLIST_LENGTH(zl)      (*((uint16_t*)((zl)+sizeof(uint32_t)*2)))



/* Create a new empty ziplist. */
unsigned char *ziplistNew(void) {
    //计算ziplist的大小 4+4+2+1 = 11个字节
    unsigned int bytes = ZIPLIST_HEADER_SIZE+ZIPLIST_END_SIZE;
    //申请内存空间
    unsigned char *zl = zmalloc(bytes);
 	//设置总字节数为 11个字节   
    ZIPLIST_BYTES(zl) = intrev32ifbe(bytes);
    //设置尾部偏移量为 10个字节
    ZIPLIST_TAIL_OFFSET(zl) = intrev32ifbe(ZIPLIST_HEADER_SIZE);
    //设置元素长度为0
    ZIPLIST_LENGTH(zl) = 0;
    //尾部填充ZIP_END
    zl[bytes-1] = ZIP_END;
    return zl;
}
```



#### ziplist插入

```c
//ziplist.c

//指的p位置插入entry
/* Insert item at "p". */
unsigned char *__ziplistInsert(unsigned char *zl, unsigned char *p, unsigned char *s, unsigned int slen) {
    //获取ziplist的整个字节数
    size_t curlen = intrev32ifbe(ZIPLIST_BYTES(zl)), reqlen, newlen;
    unsigned int prevlensize, prevlen = 0;
    size_t offset;
    int nextdiff = 0;
    unsigned char encoding = 0;
    long long value = 123456789; /* initialized to avoid warning. Using a value
                                    that is easy to see if for some reason
                                    we use it uninitialized. */
    zlentry tail;

    //如果插入的位置不是ZIP_END
    /* Find out prevlen for the entry that is inserted. */
    if (p[0] != ZIP_END) {
        //获取插入位置的entry的prevlensize和prevlen
        ZIP_DECODE_PREVLEN(p, prevlensize, prevlen);
    } else {
        //如果是ZIP_END 那么判断一下尾部是不是ZIP_END 
        unsigned char *ptail = ZIPLIST_ENTRY_TAIL(zl);
        if (ptail[0] != ZIP_END) {
            prevlen = zipRawEntryLengthSafe(zl, curlen, ptail);
        }
    }

    //尝试将s的编码设置为int
    /* See if the entry can be encoded */
    if (zipTryEncoding(s,slen,&value,&encoding)) {
        /* 'encoding' is set to the appropriate integer encoding */
        //通过int编码计算所需长度
        reqlen = zipIntSize(encoding);
    } else {
        //否则就是str编码
        /* 'encoding' is untouched, however zipStoreEntryEncoding will use the
         * string length to figure out how to encode it. */
        //所需长度就是字符串的长度
        reqlen = slen;
    }
    
    /* We need space for both the length of the previous entry and
     * the length of the payload. */
    //计算所需要的prevlen大小
    reqlen += zipStorePrevEntryLength(NULL,prevlen);
    //计算encoding大小
    reqlen += zipStoreEntryEncoding(NULL,encoding,slen);

    /* When the insert position is not equal to the tail, we need to
     * make sure that the next entry can hold this entry's length in
     * its prevlen field. */
    int forcelarge = 0;
    //计算后一个entry的prevlen能否存储的下 当前要插入entry的长度 ，如果存储不下 计算后一个entry的prevlen的需要的差值
    nextdiff = (p[0] != ZIP_END) ? zipPrevLenByteDiff(p,reqlen) : 0;
    //@todo
    if (nextdiff == -4 && reqlen < 4) {
        nextdiff = 0;
        forcelarge = 1;
    }

    /* Store offset because a realloc may change the address of zl. */
    //因为zl的起始地址有可能因为重新分配而改变
    //存储当前p位置的偏移量
    offset = p-zl;
    //计算新ziplist所需要的空间
    newlen = curlen+reqlen+nextdiff;
    //扩容
    zl = ziplistResize(zl,newlen);
    //重新找到p
    p = zl+offset;

    //如果要插入的位置不是ZIP_END
    /* Apply memory move when necessary and update tail offset. */
    if (p[0] != ZIP_END) {
        //把p后面的数据移动到p+reqlen位置
        /* Subtract one because of the ZIP_END bytes */
        memmove(p+reqlen,p-nextdiff,curlen-offset-1+nextdiff);

        //重新设置p+reqlen的entry的prevlen
        /* Encode this entry's raw length in the next entry. */
        if (forcelarge)
            zipStorePrevEntryLengthLarge(p+reqlen,reqlen);
        else
            zipStorePrevEntryLength(p+reqlen,reqlen);

        //更新尾部偏移量为 原偏移量+reqlen
        /* Update offset for tail */
        ZIPLIST_TAIL_OFFSET(zl) =
            intrev32ifbe(intrev32ifbe(ZIPLIST_TAIL_OFFSET(zl))+reqlen);

        /* When the tail contains more than one entry, we need to take
         * "nextdiff" in account as well. Otherwise, a change in the
         * size of prevlen doesn't have an effect on the *tail* offset. */
        assert(zipEntrySafe(zl, newlen, p+reqlen, &tail, 1));
        //因为移动时 移动的是p-nextdiff，所以可能后一个entry的长度因为prevlen的改变而改变了 所以尾部偏移量还需要+nextdiff
        if (p[reqlen+tail.headersize+tail.len] != ZIP_END) {
            ZIPLIST_TAIL_OFFSET(zl) =
                intrev32ifbe(intrev32ifbe(ZIPLIST_TAIL_OFFSET(zl))+nextdiff);
        }
    } else {
        /* This element will be the new tail. */
        //新插入的entry将会成为新的尾部
        ZIPLIST_TAIL_OFFSET(zl) = intrev32ifbe(p-zl);
    }

    /* When nextdiff != 0, the raw length of the next entry has changed, so
     * we need to cascade the update throughout the ziplist */
    //如果nextdiff不为0 那么因为下一个entry的长度改变 有可能造成后面entry的prevlen的级联改变
    if (nextdiff != 0) {
        offset = p-zl;
        zl = __ziplistCascadeUpdate(zl,p+reqlen);
        p = zl+offset;
    }

    //设置新插入entry的prevlen
    /* Write the entry */
    p += zipStorePrevEntryLength(p,prevlen);
    //设置新插入entry的encoding
    p += zipStoreEntryEncoding(p,encoding,slen);
    //如果是str编码
    if (ZIP_IS_STR(encoding)) {
        //把s拷贝到data区域上
        memcpy(p,s,slen);
    } else {
        //如果是int编码
        zipSaveInteger(p,value,encoding);
    }
    //ziplist的总长度+1
    ZIPLIST_INCR_LENGTH(zl,1);
    return zl;
}



//检查能否将entry的编码转换成int
/* Check if string pointed to by 'entry' can be encoded as an integer.
 * Stores the integer value in 'v' and its encoding in 'encoding'. */
int zipTryEncoding(unsigned char *entry, unsigned int entrylen, long long *v, unsigned char *encoding) {
    long long value;

    if (entrylen >= 32 || entrylen == 0) return 0;
    //能转换成int编码
    if (string2ll((char*)entry,entrylen,&value)) {
        /* Great, the string can be encoded. Check what's the smallest
         * of our encoding types that can hold this value. */
        //根据不同大小设置编码
        if (value >= 0 && value <= 12) {
            *encoding = ZIP_INT_IMM_MIN+value;
        } else if (value >= INT8_MIN && value <= INT8_MAX) {
            *encoding = ZIP_INT_8B;
        } else if (value >= INT16_MIN && value <= INT16_MAX) {
            *encoding = ZIP_INT_16B;
        } else if (value >= INT24_MIN && value <= INT24_MAX) {
            *encoding = ZIP_INT_24B;
        } else if (value >= INT32_MIN && value <= INT32_MAX) {
            *encoding = ZIP_INT_32B;
        } else {
            *encoding = ZIP_INT_64B;
        }
        *v = value;
        return 1;
    }
    return 0;
}

//获得不同int编码的所需存储空间大小
static inline unsigned int zipIntSize(unsigned char encoding) {
    switch(encoding) {
    case ZIP_INT_8B:  return 1;
    case ZIP_INT_16B: return 2;
    case ZIP_INT_24B: return 3;
    case ZIP_INT_32B: return 4;
    case ZIP_INT_64B: return 8;
    }
    //如果是ZIP_INT_IMM_MIN和ZIP_INT_IMM_MAX之间 不需要额外空间 因为编码上直接存储了数据 ZIP_INT_IMM_MIN + value;
    if (encoding >= ZIP_INT_IMM_MIN && encoding <= ZIP_INT_IMM_MAX)
        return 0; /* 4 bit immediate */
    /* bad encoding, covered by a previous call to ZIP_ASSERT_ENCODING */
    redis_unreachable();
    return 0;
}

//根据int编码的不同 存储不同大小的整数
/* Store integer 'value' at 'p', encoded as 'encoding' */
void zipSaveInteger(unsigned char *p, int64_t value, unsigned char encoding) {
    int16_t i16;
    int32_t i32;
    int64_t i64;
    if (encoding == ZIP_INT_8B) {
        ((int8_t*)p)[0] = (int8_t)value;
    } else if (encoding == ZIP_INT_16B) {
        i16 = value;
        //从&i16地址拷贝2个字节到p地址
        memcpy(p,&i16,sizeof(i16));
        memrev16ifbe(p);
    } else if (encoding == ZIP_INT_24B) {
        i32 = ((uint64_t)value)<<8;
        memrev32ifbe(&i32);
        //从&i32地址拷贝3个字节到p地址
        memcpy(p,((uint8_t*)&i32)+1,sizeof(i32)-sizeof(uint8_t));
    } else if (encoding == ZIP_INT_32B) {
        i32 = value;
        //从&i32地址拷贝4个字节到p地址
        memcpy(p,&i32,sizeof(i32));
        memrev32ifbe(p);
    } else if (encoding == ZIP_INT_64B) {
        i64 = value;
        //从&i64地址拷贝8个字节到p地址
        memcpy(p,&i64,sizeof(i64));
        memrev64ifbe(p);
    } else if (encoding >= ZIP_INT_IMM_MIN && encoding <= ZIP_INT_IMM_MAX) {
        //不用拷贝 因为数据已经存在encoding上了
        /* Nothing to do, the value is stored in the encoding itself. */
    } else {
        assert(NULL);
    }
}


```

#### ziplistIndex

```c
//ziplist.c


/* Returns an offset to use for iterating with ziplistNext. When the given
 * index is negative, the list is traversed back to front. When the list
 * doesn't contain an element at the provided index, NULL is returned. */
unsigned char *ziplistIndex(unsigned char *zl, int index) {
    unsigned char *p;
    unsigned int prevlensize, prevlen = 0;
    size_t zlbytes = intrev32ifbe(ZIPLIST_BYTES(zl));
    //如果index<0 那么代表倒序查找
    if (index < 0) {
        index = (-index)-1;
        //从tail开始查找
        p = ZIPLIST_ENTRY_TAIL(zl);
        if (p[0] != ZIP_END) {
            /* No need for "safe" check: when going backwards, we know the header
             * we're parsing is in the range, we just need to assert (below) that
             * the size we take doesn't cause p to go outside the allocation. */
            ZIP_DECODE_PREVLENSIZE(p, prevlensize);
            assert(p + prevlensize < zl + zlbytes - ZIPLIST_END_SIZE);
            ZIP_DECODE_PREVLEN(p, prevlensize, prevlen);
            //倒序查找index个元素
            while (prevlen > 0 && index--) {
                p -= prevlen;
                assert(p >= zl + ZIPLIST_HEADER_SIZE && p < zl + zlbytes - ZIPLIST_END_SIZE);
                ZIP_DECODE_PREVLEN(p, prevlensize, prevlen);
            }
        }
    } else {
        //正序查找 从head开始
        p = ZIPLIST_ENTRY_HEAD(zl);
        //正着查找index个元素
        while (index--) {
            /* Use the "safe" length: When we go forward, we need to be careful
             * not to decode an entry header if it's past the ziplist allocation. */
            p += zipRawEntryLengthSafe(zl, zlbytes, p);
            //查找到末尾停止
            if (p[0] == ZIP_END)
                break;
        }
    }
    //如果没找到 返回null
    if (p[0] == ZIP_END || index > 0)
        return NULL;
    zipAssertValidEntry(zl, zlbytes, p);
    return p;
}
```







#### ziplist删除

```c
//ziplist.c

//在ziplist的p开始删除num个entry
/* Delete "num" entries, starting at "p". Returns pointer to the ziplist. */
unsigned char *__ziplistDelete(unsigned char *zl, unsigned char *p, unsigned int num) {
    unsigned int i, totlen, deleted = 0;
    size_t offset;
    int nextdiff = 0;
    zlentry first, tail;
    size_t zlbytes = intrev32ifbe(ZIPLIST_BYTES(zl));

    //获取第一个entry
    zipEntry(p, &first); /* no need for "safe" variant since the input pointer was validated by the function that returned it. */
    //移动到最后一个要删除的entry的下一个entry
    for (i = 0; p[0] != ZIP_END && i < num; i++) {
        p += zipRawEntryLengthSafe(zl, zlbytes, p);
        deleted++;
    }

    assert(p >= first.p);
    //计算要删除的字节数 p地址-first.p地址
    totlen = p-first.p; /* Bytes taken by the element(s) to delete. */
    if (totlen > 0) {
        uint32_t set_tail;
        if (p[0] != ZIP_END) {
            /* Storing `prevrawlen` in this entry may increase or decrease the
             * number of bytes required compare to the current `prevrawlen`.
             * There always is room to store this, because it was previously
             * stored by an entry that is now being deleted. */
            //计算当前entry的prevlen能否存储下first的prevlen的差值
            nextdiff = zipPrevLenByteDiff(p,first.prevrawlen);

            /* Note that there is always space when p jumps backward: if
             * the new previous entry is large, one of the deleted elements
             * had a 5 bytes prevlen header, so there is for sure at least
             * 5 bytes free and we need just 4. */
            //移动差值
            p -= nextdiff;
            assert(p >= first.p && p<zl+zlbytes-1);
            //将first的prevlen存到p中
            zipStorePrevEntryLength(p,first.prevrawlen);

            /* Update offset for tail */
            //更新尾部偏移量-要删除的字节数
            set_tail = intrev32ifbe(ZIPLIST_TAIL_OFFSET(zl))-totlen;

            /* When the tail contains more than one entry, we need to take
             * "nextdiff" in account as well. Otherwise, a change in the
             * size of prevlen doesn't have an effect on the *tail* offset. */
            assert(zipEntrySafe(zl, zlbytes, p, &tail, 1));
            //尾部偏移量考虑nextdiff的偏差 +nextdiff
            if (p[tail.headersize+tail.len] != ZIP_END) {
                set_tail = set_tail + nextdiff;
            }

            /* Move tail to the front of the ziplist */
            /* since we asserted that p >= first.p. we know totlen >= 0,
             * so we know that p > first.p and this is guaranteed not to reach
             * beyond the allocation, even if the entries lens are corrupted. */
            //计算需要移动的字节数 总数 - 当前p的偏移量 - ZIP_END 
            size_t bytes_to_move = zlbytes-(p-zl)-1;
            //将从p开始的bytes_to_move个字节 移动到first.p的位置
            memmove(first.p,p,bytes_to_move);
        } else {
            //如果p的位置是ZIP_END 那么无需移动字节 直接将尾部偏移量指向上一个entry的开始
            /* The entire tail was deleted. No need to move memory. */
            set_tail = (first.p-zl)-first.prevrawlen;
        }

        //对ziplist进行重新申请内存
        /* Resize the ziplist */
        offset = first.p-zl;
        //所需内存 = 总内存-删掉的内存-prevlen差值
        zlbytes -= totlen - nextdiff;
        zl = ziplistResize(zl, zlbytes);
        p = zl+offset;

        //更新ziplist的总长度
        /* Update record count */
        ZIPLIST_INCR_LENGTH(zl,-deleted);

        //设置尾部偏移量
        /* Set the tail offset computed above */
        assert(set_tail <= zlbytes - ZIPLIST_END_SIZE);
        ZIPLIST_TAIL_OFFSET(zl) = intrev32ifbe(set_tail);
		
        //如果nextdiff更改 代表可能进行级联更新
        /* When nextdiff != 0, the raw length of the next entry has changed, so
         * we need to cascade the update throughout the ziplist */
        if (nextdiff != 0)
            zl = __ziplistCascadeUpdate(zl,p);
    }
    return zl;
}

```

#### 级联更新

```c
//ziplist.c


/* When an entry is inserted, we need to set the prevlen field of the next
 * entry to equal the length of the inserted entry. It can occur that this
 * length cannot be encoded in 1 byte and the next entry needs to be grow
 * a bit larger to hold the 5-byte encoded prevlen. This can be done for free,
 * because this only happens when an entry is already being inserted (which
 * causes a realloc and memmove). However, encoding the prevlen may require
 * that this entry is grown as well. This effect may cascade throughout
 * the ziplist when there are consecutive entries with a size close to
 * ZIP_BIG_PREVLEN, so we need to check that the prevlen can be encoded in
 * every consecutive entry.
 *
 * Note that this effect can also happen in reverse, where the bytes required
 * to encode the prevlen field can shrink. This effect is deliberately ignored,
 * because it can cause a "flapping" effect where a chain prevlen fields is
 * first grown and then shrunk again after consecutive inserts. Rather, the
 * field is allowed to stay larger than necessary, because a large prevlen
 * field implies the ziplist is holding large entries anyway.
 *
 * The pointer "p" points to the first entry that does NOT need to be
 * updated, i.e. consecutive fields MAY need an update. */
//级联更新 只处理prevlen增大的情况 缩小的情况不做处理
//p是第一个不需要级联更新的entry
unsigned char *__ziplistCascadeUpdate(unsigned char *zl, unsigned char *p) {
    zlentry cur;
    size_t prevlen, prevlensize, prevoffset; /* Informat of the last changed entry. */
    size_t firstentrylen; /* Used to handle insert at head. */
    size_t rawlen, curlen = intrev32ifbe(ZIPLIST_BYTES(zl));
    size_t extra = 0, cnt = 0, offset;
    size_t delta = 4; /* Extra bytes needed to update a entry's prevlen (5-1). */
    unsigned char *tail = zl + intrev32ifbe(ZIPLIST_TAIL_OFFSET(zl));

    /* Empty ziplist */
    if (p[0] == ZIP_END) return zl;

    //从p中取出entry
    zipEntry(p, &cur); /* no need for "safe" variant since the input pointer was validated by the function that returned it. */
    //计算cur的总长度
    firstentrylen = prevlen = cur.headersize + cur.len;
    //计算存储cur长度所需的prevlensize
    prevlensize = zipStorePrevEntryLength(NULL, prevlen);
    //计算cur的偏移量
    prevoffset = p - zl;
    //p移动到cur的下一个entry
    p += prevlen;

    /* Iterate ziplist to find out how many extra bytes do we need to update it. */
    while (p[0] != ZIP_END) {
        
        //cur更新为下一个entry
        assert(zipEntrySafe(zl, curlen, p, &cur, 0));
		//如果当前entry存储的prevrawlen和前一个entry的prevlen相比没有变化 说明后续节点不需要级联更新prevlensize
        /* Abort when "prevlen" has not changed. */
        if (cur.prevrawlen == prevlen) break;

        //如果当前entry的prevrawlensize足够容纳前一个entry的len 后续节点不需要级联更新 退出循环
        /* Abort when entry's "prevlensize" is big enough. */
        if (cur.prevrawlensize >= prevlensize) {
            if (cur.prevrawlensize == prevlensize) {
                zipStorePrevEntryLength(p, prevlen);
            } else {
                /* This would result in shrinking, which we want to avoid.
                 * So, set "prevlen" in the available bytes. */
                zipStorePrevEntryLengthLarge(p, prevlen);
            }
            break;
        }

        /* cur.prevrawlen means cur is the former head entry. */
        assert(cur.prevrawlen == 0 || cur.prevrawlen + delta == prevlen);

        //将当前entry的信息记录到prevlen变量中 p指针向前推进一个entry 
        /* Update prev entry's info and advance the cursor. */
        rawlen = cur.headersize + cur.len;
        //因为当前节点需要扩容prevlensize 所以+4
        prevlen = rawlen + delta; 
        prevlensize = zipStorePrevEntryLength(NULL, prevlen);
        prevoffset = p - zl;
        p += rawlen;
        //记录总共需要扩容的空间
        extra += delta;
        //记录需要扩容的entry数量
        cnt++;
    }

    /* Extra bytes is zero all update has been done(or no need to update). */
    if (extra == 0) return zl;

    //如果走到了tail节点
    /* Update tail offset after loop. */
    if (tail == zl + prevoffset) {
        /* When the the last entry we need to update is also the tail, update tail offset
         * unless this is the only entry that was updated (so the tail offset didn't change). */
        //如果extra = 4 那么代表就尾部节点需要扩容 尾部节点的偏移量不用改变 否则需要重新计算偏移量
        if (extra - delta != 0) {
            ZIPLIST_TAIL_OFFSET(zl) =
                intrev32ifbe(intrev32ifbe(ZIPLIST_TAIL_OFFSET(zl))+extra-delta);
        }
    } else {
        //没走到尾部节点 需要将尾部节点偏移量 + extra
        /* Update the tail offset in cases where the last entry we updated is not the tail. */
        ZIPLIST_TAIL_OFFSET(zl) =
            intrev32ifbe(intrev32ifbe(ZIPLIST_TAIL_OFFSET(zl))+extra);
    }

    /* Now "p" points at the first unchanged byte in original ziplist,
     * move data after that to new ziplist. */
    //扩容 + extra空间 此时p指向需要扩容的entry的下一个节点
    offset = p - zl;
    zl = ziplistResize(zl, curlen + extra);
    p = zl + offset;
    //将p后面的数据都迁移到p+extra
    memmove(p + extra, p, curlen - offset - 1);
    //p前移extra
    p += extra;

    /* Iterate all entries that need to be updated tail to head. */
    //从后往前
    while (cnt) {
        //zl+prevoffset指向最后一个需要扩容的entry
        zipEntry(zl + prevoffset, &cur); /* no need for "safe" variant since we already iterated on all these entries above. */
        //计算cur的总字节数
        rawlen = cur.headersize + cur.len;
        //将最后一个entry的encoding和data后移到 p - (最后一个encoding+data)的位置 
        //为了腾出prevlen的空间
        /* Move entry to tail and reset prevlen. */
        memmove(p - (rawlen - cur.prevrawlensize), 
                zl + prevoffset + cur.prevrawlensize, 
                rawlen - cur.prevrawlensize);
        //p前移（原始entry长度 + 4)
        p -= (rawlen + delta);
        if (cur.prevrawlen == 0) {
            /* "cur" is the previous head entry, update its prevlen with firstentrylen. */
            zipStorePrevEntryLength(p, firstentrylen);
        } else {
            //prevlen增长了4个字节 需要重新赋值prevlensize
            /* An entry's prevlen can only increment 4 bytes. */
            zipStorePrevEntryLength(p, cur.prevrawlen+delta);
        }
        //前移到前一个要扩容的节点
        /* Foward to previous entry. */
        prevoffset -= cur.prevrawlen;
        //要处理节点数-1
        cnt--;
    }
    return zl;
}

```

#### 优势

1. **节省内存，以时间换空间** 存储前一个节点长度，通过长度来计算出前后节点的位置 encoding既存类别也存长度 连续存储，在使用时需要解码
2. **zlentry解码存储，提供快速访问** prevlen、prevlesize len、lensize



### dict

字典又称散列表，是用来存储键值(Key-Value)对的一种数据结构

- Redis主存储
- hash数据类型的实现
- 过期时间的kv、zset中value和score的映射关系

**数组**

- 有限个类型相同的对象的集合
- 连续的内存存储
- 即刻存取，时间复杂度O(1)
- 下标的定位方式：头指针+偏移量----->下标

<img src=".\images\dict01.png" style="zoom: 80%;" />

```c
//dict.h

//字典表
typedef struct dict {
    //字典类型
    dictType *type;
    //被特定的 dictType 实现用来存储额外的数据或配置信息。
    void *privdata;
    //实际存储数据的字典表 一张用于存储 一张用于渐进式扩容
    dictht ht[2];
    //标志扩容的id
    long rehashidx; /* rehashing not in progress if rehashidx == -1 */
    //当>0时停止扩容
    int16_t pauserehash; /* If >0 rehashing is paused (<0 indicates coding error) */
} dict;

typedef struct dictType {
    //计算hash值
    uint64_t (*hashFunction)(const void *key);
    //key val 复制
    void *(*keyDup)(void *privdata, const void *key);
    void *(*valDup)(void *privdata, const void *obj);
    //key比较
    int (*keyCompare)(void *privdata, const void *key1, const void *key2);
    //key val销毁
    void (*keyDestructor)(void *privdata, void *key);
    void (*valDestructor)(void *privdata, void *obj);
    //@todo 扩容
    int (*expandAllowed)(size_t moreMem, double usedRatio);
} dictType;


typedef struct dictht {
    //指向dictEntry数组的指针
    dictEntry **table;
    //数组长度
    unsigned long size;
    //掩码（size-1）
    unsigned long sizemask;
    //已用长度
    unsigned long used;
} dictht;

typedef struct dictEntry {
    //key值
    void *key;
    //使用union共享内存空间 同一时间只有一个成员有效
    union {
        void *val;
        uint64_t u64;
        int64_t s64;
        double d;
    } v;
    //hash冲突的链表
    struct dictEntry *next;
} dictEntry;

//获取hash值
#define dictHashKey(d, key) (d)->type->hashFunction(key)
#define dictGetKey(he) ((he)->key)
#define dictGetVal(he) ((he)->v.val)
#define dictGetSignedIntegerVal(he) ((he)->v.s64)
#define dictGetUnsignedIntegerVal(he) ((he)->v.u64)
#define dictGetDoubleVal(he) ((he)->v.d)
#define dictSlots(d) ((d)->ht[0].size+(d)->ht[1].size)
//计算dict的使用量
#define dictSize(d) ((d)->ht[0].used+(d)->ht[1].used)
//判断dict是不是在扩容
#define dictIsRehashing(d) ((d)->rehashidx != -1)
#define dictPauseRehashing(d) (d)->pauserehash++
#define dictResumeRehashing(d) (d)->pauserehash--


//dictrehash的模式
typedef enum {
    //启用扩容
    DICT_RESIZE_ENABLE,
    //避免扩容
    DICT_RESIZE_AVOID,
    //禁止扩容
    DICT_RESIZE_FORBID,
} dictResizeEnable;


//调用dict的type的keyDup方法 将返回值设置给entry的key
#define dictSetKey(d, entry, _key_) do { \
    if ((d)->type->keyDup) \
        (entry)->key = (d)->type->keyDup((d)->privdata, _key_); \
    else \
        (entry)->key = (_key_); \
} while(0)

//调用dict的type的keyDup方法 将返回值设置给entry的value
#define dictSetVal(d, entry, _val_) do { \
    if ((d)->type->valDup) \
        (entry)->v.val = (d)->type->valDup((d)->privdata, _val_); \
    else \
        (entry)->v.val = (_val_); \
} while(0)


```

#### dictCreat

```c
//dict.c

//创建一个新的字典表
/* Create a new hash table */
dict *dictCreate(dictType *type,
        void *privDataPtr)
{
    //分配dict内存空间
    dict *d = zmalloc(sizeof(*d));

    //dict初始化
    _dictInit(d,type,privDataPtr);
    return d;
}


/* Initialize the hash table */
int _dictInit(dict *d, dictType *type,
        void *privDataPtr)
{
    _dictReset(&d->ht[0]);
    _dictReset(&d->ht[1]);
    //设置dict的各个属性
    d->type = type;
    d->privdata = privDataPtr;
    d->rehashidx = -1;
    d->pauserehash = 0;
    return DICT_OK;
}

//对dictht进行初始化
static void _dictReset(dictht *ht)
{
    ht->table = NULL;
    ht->size = 0;
    ht->sizemask = 0;
    ht->used = 0;
}

```



#### setKey

插入键值对：

1. 查找该key是否存在：lookupKeyWrite-->dictFind
2. 不存在执行新增：dbadd--->dictAdd
3. 存在执行修改：dbOverwrite

lookupKeyWrite-->lookupKeyWriteWithFlags-->lookupKey--->dictFind

```c
//db.c

//设置key value到指定db中
/* Common case for genericSetKey() where the TTL is not retained. */
void setKey(client *c, redisDb *db, robj *key, robj *val) {
    genericSetKey(c,db,key,val,0,1);
}

/* High level Set operation. This function can be used in order to set
 * a key, whatever it was existing or not, to a new object.
 *
 * 1) The ref count of the value object is incremented.
 * 2) clients WATCHing for the destination key notified.
 * 3) The expire time of the key is reset (the key is made persistent),
 *    unless 'keepttl' is true.
 *
 * All the new keys in the database should be created via this interface.
 * The client 'c' argument may be set to NULL if the operation is performed
 * in a context where there is no clear client performing the operation. */
void genericSetKey(client *c, redisDb *db, robj *key, robj *val, int keepttl, int signal) {
    if (lookupKeyWrite(db,key) == NULL) {
        dbAdd(db,key,val);
    } else {
        dbOverwrite(db,key,val);
    }
    incrRefCount(val);
    if (!keepttl) removeExpire(db,key);
    if (signal) signalModifiedKey(c,db,key);
}


```

#### dictFind

```c
//dict.c

dictEntry *dictFind(dict *d, const void *key)
{
    dictEntry *he;
    uint64_t h, idx, table;

    //如果dict的容量为0 那么返回null
    if (dictSize(d) == 0) return NULL; /* dict is empty */
    //如果dict正在进行rehash 渐进式扩容
    if (dictIsRehashing(d)) _dictRehashStep(d);
    //计算key的hash值
    h = dictHashKey(d, key);
    //查找两张dictht
    for (table = 0; table <= 1; table++) {
        //取余 找对应下标的索引
        idx = h & d->ht[table].sizemask;
        //找到dictEntry
        he = d->ht[table].table[idx];
        //遍历链表来寻找key一致的值
        while(he) {
            if (key==he->key || dictCompareKeys(d, key, he->key))
                return he;
            he = he->next;
        }
        if (!dictIsRehashing(d)) return NULL;
    }
    //找不到返回null
    return NULL;
}

```



#### dictAdd

```c
//dict.c


/* Add an element to the target hash table */
int dictAdd(dict *d, void *key, void *val)
{
    //设置entry
    dictEntry *entry = dictAddRaw(d,key,NULL);

    if (!entry) return DICT_ERR;
    //设置val值
    dictSetVal(d, entry, val);
    return DICT_OK;
}

/* Low level add or find:
 * This function adds the entry but instead of setting a value returns the
 * dictEntry structure to the user, that will make sure to fill the value
 * field as they wish.
 *
 * This function is also directly exposed to the user API to be called
 * mainly in order to store non-pointers inside the hash value, example:
 *
 * entry = dictAddRaw(dict,mykey,NULL);
 * if (entry != NULL) dictSetSignedIntegerVal(entry,1000);
 *
 * Return values:
 *
 * If key already exists NULL is returned, and "*existing" is populated
 * with the existing entry if existing is not NULL.
 *
 * If key was added, the hash entry is returned to be manipulated by the caller.
 */
dictEntry *dictAddRaw(dict *d, void *key, dictEntry **existing)
{
    long index;
    dictEntry *entry;
    dictht *ht;

    //渐进式扩容
    if (dictIsRehashing(d)) _dictRehashStep(d);

    /* Get the index of the new element, or -1 if
     * the element already exists. */
    //计算key的idx
    if ((index = _dictKeyIndex(d, key, dictHashKey(d,key), existing)) == -1)
        return NULL;

    /* Allocate the memory and store the new entry.
     * Insert the element in top, with the assumption that in a database
     * system it is more likely that recently added entries are accessed
     * more frequently. */
    //如果正在进行扩容 那么优先插入到ht[1]表中去
    ht = dictIsRehashing(d) ? &d->ht[1] : &d->ht[0];
    //分配dictEntry的内存
    entry = zmalloc(sizeof(*entry));
    //头插法
    entry->next = ht->table[index];
    //插入节点
    ht->table[index] = entry;
    //数量+1
    ht->used++;

    /* Set the hash entry fields. */
    dictSetKey(d, entry, key);
    return entry;
}



/* Returns the index of a free slot that can be populated with
 * a hash entry for the given 'key'.
 * If the key already exists, -1 is returned
 * and the optional output parameter may be filled.
 *
 * Note that if we are in the process of rehashing the hash table, the
 * index is always returned in the context of the second (new) hash table. */
//查找对应key的索引 如果有key已经存在 返回-1 existing返回已经存在的dictEntry
static long _dictKeyIndex(dict *d, const void *key, uint64_t hash, dictEntry **existing)
{
    unsigned long idx, table;
    dictEntry *he;
    if (existing) *existing = NULL;

    //如果需要的话就扩容
    /* Expand the hash table if needed */
    if (_dictExpandIfNeeded(d) == DICT_ERR)
        return -1;
    //查找key对应hash值idx下是否已经存在 存在返回-1 不存在返回idx
    for (table = 0; table <= 1; table++) {
        idx = hash & d->ht[table].sizemask;
        /* Search if this slot does not already contain the given key */
        he = d->ht[table].table[idx];
        while(he) {
            if (key==he->key || dictCompareKeys(d, key, he->key)) {
                if (existing) *existing = he;
                return -1;
            }
            he = he->next;
        }
        if (!dictIsRehashing(d)) break;
    }
    return idx;
}



```



#### dbOverwrite

```c
//db.c
//@todo

/* Overwrite an existing key with a new value. Incrementing the reference
 * count of the new value is up to the caller.
 * This function does not modify the expire time of the existing key.
 *
 * The program is aborted if the key was not already present. */
void dbOverwrite(redisDb *db, robj *key, robj *val) {
    dictEntry *de = dictFind(db->dict,key->ptr);

    serverAssertWithInfo(NULL,key,de != NULL);
    dictEntry auxentry = *de;
    robj *old = dictGetVal(de);
    if (server.maxmemory_policy & MAXMEMORY_FLAG_LFU) {
        val->lru = old->lru;
    }
    /* Although the key is not really deleted from the database, we regard 
    overwrite as two steps of unlink+add, so we still need to call the unlink 
    callback of the module. */
    moduleNotifyKeyUnlink(key,old);
    dictSetVal(db->dict, de, val);

    if (server.lazyfree_lazy_server_del) {
        freeObjAsync(key,old);
        dictSetVal(db->dict, &auxentry, NULL);
    }

    dictFreeVal(db->dict, &auxentry);
}
```



#### dictDelete

```c
//dict.c

/* Remove an element, returning DICT_OK on success or DICT_ERR if the
 * element was not found. */
//删除指定key的节点
int dictDelete(dict *ht, const void *key) {
    return dictGenericDelete(ht,key,0) ? DICT_OK : DICT_ERR;
}

/* Search and remove an element. This is an helper function for
 * dictDelete() and dictUnlink(), please check the top comment
 * of those functions. */
static dictEntry *dictGenericDelete(dict *d, const void *key, int nofree) {
    uint64_t h, idx;
    dictEntry *he, *prevHe;
    int table;

    //如果ht为空 那么直接返回
    if (d->ht[0].used == 0 && d->ht[1].used == 0) return NULL;

    //如果正在扩容 那么渐进式扩容
    if (dictIsRehashing(d)) _dictRehashStep(d);
    //计算hash值
    h = dictHashKey(d, key);

    //从ht[0][1]中查找key
    for (table = 0; table <= 1; table++) {
        idx = h & d->ht[table].sizemask;
        he = d->ht[table].table[idx];
        prevHe = NULL;
        while(he) {
            if (key==he->key || dictCompareKeys(d, key, he->key)) {
                /* Unlink the element from the list */
                //如果不是第一个 那么从链表中断开
                if (prevHe)
                    prevHe->next = he->next;
                else
                    //否则头指向下一个节点
                    d->ht[table].table[idx] = he->next;
                //释放内存
                if (!nofree) {
                    dictFreeKey(d, he);
                    dictFreeVal(d, he);
                    zfree(he);
                }
                //计数-1
                d->ht[table].used--;
                //返回删除的节点
                return he;
            }
            prevHe = he;
            he = he->next;
        }
        if (!dictIsRehashing(d)) break;
    }
    return NULL; /* not found */
}

```



#### dictUnlink

```c
//db.c

/* Remove an element from the table, but without actually releasing
 * the key, value and dictionary entry. The dictionary entry is returned
 * if the element was found (and unlinked from the table), and the user
 * should later call `dictFreeUnlinkedEntry()` with it in order to release it.
 * Otherwise if the key is not found, NULL is returned.
 *
 * This function is useful when we want to remove something from the hash
 * table but want to use its value before actually deleting the entry.
 * Without this function the pattern would require two lookups:
 *
 *  entry = dictFind(...);
 *  // Do something with entry
 *  dictDelete(dictionary,entry);
 *
 * Thanks to this function it is possible to avoid this, and use
 * instead:
 *
 * entry = dictUnlink(dictionary,entry);
 * // Do something with entry
 * dictFreeUnlinkedEntry(entry); // <- This does not need to lookup again.
 */
//从dict中将key进行断开 而不进行删除操作
dictEntry *dictUnlink(dict *ht, const void *key) {
    return dictGenericDelete(ht,key,1);
}


/* Search and remove an element. This is an helper function for
 * dictDelete() and dictUnlink(), please check the top comment
 * of those functions. */
static dictEntry *dictGenericDelete(dict *d, const void *key, int nofree) {
    uint64_t h, idx;
    dictEntry *he, *prevHe;
    int table;

    if (d->ht[0].used == 0 && d->ht[1].used == 0) return NULL;

    if (dictIsRehashing(d)) _dictRehashStep(d);
    h = dictHashKey(d, key);

    for (table = 0; table <= 1; table++) {
        idx = h & d->ht[table].sizemask;
        he = d->ht[table].table[idx];
        prevHe = NULL;
        while(he) {
            if (key==he->key || dictCompareKeys(d, key, he->key)) {
                //找到对应key的dictEntry
                /* Unlink the element from the list */
                if (prevHe)
                    prevHe->next = he->next;
                else
                    d->ht[table].table[idx] = he->next;
                //由于nofree为1 不释放 只是将节点从dict中断开连接
                if (!nofree) {
                    dictFreeKey(d, he);
                    dictFreeVal(d, he);
                    zfree(he);
                }
                d->ht[table].used--;
                return he;
            }
            prevHe = he;
            he = he->next;
        }
        if (!dictIsRehashing(d)) break;
    }
    return NULL; /* not found */
}

```





#### 渐进式rehash

```c
//dict.c


//默认模式是启用dict扩容
static dictResizeEnable dict_can_resize = DICT_RESIZE_ENABLE;
//默认扩容比率是5
static unsigned int dict_force_resize_ratio = 5;



/* This function performs just a step of rehashing, and only if hashing has
 * not been paused for our hash table. When we have iterators in the
 * middle of a rehashing we can't mess with the two hash tables otherwise
 * some element can be missed or duplicated.
 *
 * This function is called by common lookup or update operations in the
 * dictionary so that the hash table automatically migrates from H1 to H2
 * while it is actively used. */

//渐进式rehash
static void _dictRehashStep(dict *d) {
    //如果没有停止rehash rehash一步
    if (d->pauserehash == 0) dictRehash(d,1);
}


/* Performs N steps of incremental rehashing. Returns 1 if there are still
 * keys to move from the old to the new hash table, otherwise 0 is returned.
 *
 * Note that a rehashing step consists in moving a bucket (that may have more
 * than one key as we use chaining) from the old to the new hash table, however
 * since part of the hash table may be composed of empty spaces, it is not
 * guaranteed that this function will rehash even a single bucket, since it
 * will visit at max N*10 empty buckets in total, otherwise the amount of
 * work it does would be unbound and the function may block for a long time. */
//渐进式rehash
int dictRehash(dict *d, int n) {
    //最大访问空桶的次数
    int empty_visits = n*10; /* Max number of empty buckets to visit. */
    //获得ht[0]和ht[1]的大小
    unsigned long s0 = d->ht[0].size;
    unsigned long s1 = d->ht[1].size;
    //如果dict没有进行rehash 并且禁用resize 直接返回
    if (dict_can_resize == DICT_RESIZE_FORBID || !dictIsRehashing(d)) return 0;
    //如果模式是避免resize 那么需要判断两个ht的差异满足dict_force_resize_ratio倍
    if (dict_can_resize == DICT_RESIZE_AVOID && 
        ((s1 > s0 && s1 / s0 < dict_force_resize_ratio) ||
         (s1 < s0 && s0 / s1 < dict_force_resize_ratio)))
    {
        return 0;
    }

    //迁移n个桶
    while(n-- && d->ht[0].used != 0) {
        dictEntry *de, *nextde;

        /* Note that rehashidx can't overflow as we are sure there are more
         * elements because ht[0].used != 0 */
        assert(d->ht[0].size > (unsigned long)d->rehashidx);
        //rehashidx的自增 寻找第一个不为null的桶
        //rehashidx的初始值为-1
        while(d->ht[0].table[d->rehashidx] == NULL) {
            d->rehashidx++;
            //如果超过了empty_visits 就不再进行寻找
            if (--empty_visits == 0) return 1;
        }
        //找到不为null的桶
        de = d->ht[0].table[d->rehashidx];
        /* Move all the keys in this bucket from the old to the new hash HT */
        //将当前桶的所有dictEntry迁移到新表
        while(de) {
            uint64_t h;
		   //存储next
            nextde = de->next;
            //计算hash值 找到在新表的位置
            /* Get the index in the new hash table */
            h = dictHashKey(d, de->key) & d->ht[1].sizemask;
            //头插法 把这个节点插进去
            de->next = d->ht[1].table[h];
            d->ht[1].table[h] = de;
            d->ht[0].used--;
            d->ht[1].used++;
            //继续操作下一个dictEntry
            de = nextde;
        }
        //将旧表的桶设置为null
        d->ht[0].table[d->rehashidx] = NULL;
        //rehashidx+1
        d->rehashidx++;
    }

    //如果已经把ht[0]所有的节点都迁移完成了
    /* Check if we already rehashed the whole table... */
    if (d->ht[0].used == 0) {
        //释放ht[0]的空间
        zfree(d->ht[0].table);
        //把ht[1]赋值到ht[0]
        d->ht[0] = d->ht[1];
        //ht[1]重置
        _dictReset(&d->ht[1]);
        //重置rehashidx
        d->rehashidx = -1;
        return 0;
    }

    /* More to rehash... */
    return 1;
}
```

##### 扩容

```c
//dict.c


//初始dictht的实例化大小
/* This is the initial size of every hash table */
#define DICT_HT_INITIAL_SIZE     4


/* Expand the hash table if needed */
static int _dictExpandIfNeeded(dict *d)
{
    //如果正在进行扩容 那么就直接返回
    /* Incremental rehashing already in progress. Return. */
    if (dictIsRehashing(d)) return DICT_OK;

    //如果ht[0]还没有进行初始化 初始化ht
    /* If the hash table is empty expand it to the initial size. */
    if (d->ht[0].size == 0) return dictExpand(d, DICT_HT_INITIAL_SIZE);

    /* If we reached the 1:1 ratio, and we are allowed to resize the hash
     * table (global setting) or we should avoid it but the ratio between
     * elements/buckets is over the "safe" threshold, we resize doubling
     * the number of buckets. */
    //尝试使用dictType的expandAllowed函数扩容
    if (!dictTypeExpandAllowed(d))
        return DICT_OK;
    //如果模式enable 并且使用大小>=容量大小
    //如果模式为禁止 并且使用大小是容量的dict_force_resize_ratio的倍数
    if ((dict_can_resize == DICT_RESIZE_ENABLE &&
         d->ht[0].used >= d->ht[0].size) ||
        (dict_can_resize != DICT_RESIZE_FORBID &&
         d->ht[0].used / d->ht[0].size > dict_force_resize_ratio))
    {
        //扩容
        return dictExpand(d, d->ht[0].used + 1);
    }
    return DICT_OK;
}

//dict扩容
/* return DICT_ERR if expand was not performed */
int dictExpand(dict *d, unsigned long size) {
    return _dictExpand(d, size, NULL);
}


/* Expand or create the hash table,
 * when malloc_failed is non-NULL, it'll avoid panic if malloc fails (in which case it'll be set to 1).
 * Returns DICT_OK if expand was performed, and DICT_ERR if skipped. */
//dict扩容
int _dictExpand(dict *d, unsigned long size, int* malloc_failed)
{
    if (malloc_failed) *malloc_failed = 0;

    /* the size is invalid if it is smaller than the number of
     * elements already inside the hash table */
    if (dictIsRehashing(d) || d->ht[0].used > size)
        return DICT_ERR;

    //新的dictht
    dictht n; /* the new hash table */
    //找到比size大的二的倍数
    unsigned long realsize = _dictNextPower(size);

    //处理size溢出的问题
    /* Detect overflows */
    if (realsize < size || realsize * sizeof(dictEntry*) < realsize)
        return DICT_ERR;

    /* Rehashing to the same table size is not useful. */
    if (realsize == d->ht[0].size) return DICT_ERR;

    
    /* Allocate the new hash table and initialize all pointers to NULL */
    //设置新dictht的容量和掩码
    n.size = realsize;
    n.sizemask = realsize-1;
    if (malloc_failed) {
        //分配realsize * sizeof(dictEntry) 大小的内存空间
        n.table = ztrycalloc(realsize*sizeof(dictEntry*));
        *malloc_failed = n.table == NULL;
        if (*malloc_failed)
            return DICT_ERR;
    } else
        n.table = zcalloc(realsize*sizeof(dictEntry*));

    //设置使用为0
    n.used = 0;

    /* Is this the first initialization? If so it's not really a rehashing
     * we just set the first hash table so that it can accept keys. */
    if (d->ht[0].table == NULL) {
        d->ht[0] = n;
        return DICT_OK;
    }
	
    //准备开始渐进式扩容 新表设置到ht[1]
    /* Prepare a second hash table for incremental rehashing */
    d->ht[1] = n;
    //rehashidx设置为0
    d->rehashidx = 0;
    return DICT_OK;
}

//找到比size大的2的倍数
/* Our hash table capability is a power of two */
static unsigned long _dictNextPower(unsigned long size)
{
    unsigned long i = DICT_HT_INITIAL_SIZE;

    if (size >= LONG_MAX) return LONG_MAX + 1LU;
    while(1) {
        if (i >= size)
            return i;
        i *= 2;
    }
}

```

##### 缩容

```c
//dict.c


/* Resize the table to the minimal size that contains all the elements,
 * but with the invariant of a USED/BUCKETS ratio near to <= 1 */
int dictResize(dict *d)
{
    unsigned long minimal;

    //如果正在进行rehash 那么返回
    if (dict_can_resize != DICT_RESIZE_ENABLE || dictIsRehashing(d)) return DICT_ERR;
    //将表的大小调整为包含所有元素的最小大小
    minimal = d->ht[0].used;
    if (minimal < DICT_HT_INITIAL_SIZE)
        minimal = DICT_HT_INITIAL_SIZE;
    //申请一块比minimal大的内存当做dictht[1]来进行rehash
    return dictExpand(d, minimal);
}
```



#### 优势

1. **字典存储：数组+单链表** 海量的数据存储、存取时间复杂度O(1)
2. **数据存储的多样性** Key：整型、字符串、浮点全部转化成字符串 value：任意类型 联合体
3. **渐进式rehash** 原因：数组的长度是固定的，当扩容和缩容时，数组长度变化，下标必然变化，所以要rehash 支持：ht[0]和ht[1] 渐进式：每次和批量



### intset

<img src=".\images\intset01.png" alt="image-20240826093509240" style="zoom:50%;" />

```c
//intset.h


/* Note that these encodings are ordered, so:
 * INTSET_ENC_INT16 < INTSET_ENC_INT32 < INTSET_ENC_INT64. */
#define INTSET_ENC_INT16 (sizeof(int16_t))
#define INTSET_ENC_INT32 (sizeof(int32_t))
#define INTSET_ENC_INT64 (sizeof(int64_t))


typedef struct intset {
    //编码
    uint32_t encoding;
    //元素个数
    uint32_t length;
    //存储数据的数组 根据编码的不同
    /**
    根据encoding来决定 几个字节来存一个元素
	INTSET_ENC_INT16 2个字节存
	INTSET_ENC_INT32 4个字节存
	INTSET_ENC_INT64 8个字节存
	**/
    int8_t contents[];
} intset;

//根据value值来获取encoding
/* Return the required encoding for the provided value. */
static uint8_t _intsetValueEncoding(int64_t v) {
    if (v < INT32_MIN || v > INT32_MAX)
        return INTSET_ENC_INT64;
    else if (v < INT16_MIN || v > INT16_MAX)
        return INTSET_ENC_INT32;
    else
        return INTSET_ENC_INT16;
}
```



#### intsetFind

```c
//intset.c

/* Determine whether a value belongs to this set */
uint8_t intsetFind(intset *is, int64_t value) {
    //获取查找value的编码
    uint8_t valenc = _intsetValueEncoding(value);
    //如果被查找value的编码大于intset的编码 直接返回 
    //小于编码才开始查找
    return valenc <= intrev32ifbe(is->encoding) && intsetSearch(is,value,NULL);
}


/* Search for the position of "value". Return 1 when the value was found and
 * sets "pos" to the position of the value within the intset. Return 0 when
 * the value is not present in the intset and sets "pos" to the position
 * where "value" can be inserted. */
static uint8_t intsetSearch(intset *is, int64_t value, uint32_t *pos) {
    //使用二分法查找
    int min = 0, max = intrev32ifbe(is->length)-1, mid = -1;
    int64_t cur = -1;

    //如果intset中没有元素 直接返回
    /* The value can never be found when the set is empty */
    if (intrev32ifbe(is->length) == 0) {
        if (pos) *pos = 0;
        return 0;
    } else {
        //确定这个元素找不到 但是能知道它的插入位置
        /* Check for the case where we know we cannot find the value,
         * but do know the insert position. */
        //比最大的元素还大
        if (value > _intsetGet(is,max)) {
            //返回最大的位置
            if (pos) *pos = intrev32ifbe(is->length);
            //返回0 没找到
            return 0;
         //比最小的元素还小
        } else if (value < _intsetGet(is,0)) {
            if (pos) *pos = 0;
            return 0;
        }
    }

    //二分法查找
    while(max >= min) {
        mid = ((unsigned int)min + (unsigned int)max) >> 1;
        cur = _intsetGet(is,mid);
        if (value > cur) {
            min = mid+1;
        } else if (value < cur) {
            max = mid-1;
        } else {
            break;
        }
    }

    //如果找到这个元素了 返回1 和对应元素的位置
    if (value == cur) {
        if (pos) *pos = mid;
        return 1;
    } else {
        //没找到这个元素 返回0 和这个元素应该存放的位置
        if (pos) *pos = min;
        return 0;
    }
}

//利用is的encoding来获取对应pos位置的元素
/* Return the value at pos, using the configured encoding. */
static int64_t _intsetGet(intset *is, int pos) {
    return _intsetGetEncoded(is,pos,intrev32ifbe(is->encoding));
}

//利用is的encoding来获取对应pos位置的元素
/* Return the value at pos, given an encoding. */
static int64_t _intsetGetEncoded(intset *is, int pos, uint8_t enc) {
    int64_t v64;
    int32_t v32;
    int16_t v16;

    if (enc == INTSET_ENC_INT64) {
        //将is->contents数组的第pos个元素拷贝到v64地址中去
        memcpy(&v64,((int64_t*)is->contents)+pos,sizeof(v64));
        memrev64ifbe(&v64);
        return v64;
    } else if (enc == INTSET_ENC_INT32) {
        memcpy(&v32,((int32_t*)is->contents)+pos,sizeof(v32));
        memrev32ifbe(&v32);
        return v32;
    } else {
        memcpy(&v16,((int16_t*)is->contents)+pos,sizeof(v16));
        memrev16ifbe(&v16);
        return v16;
    }
}

```

#### intsetAdd

```c
//intset.c

/* Insert an integer in the intset */
intset *intsetAdd(intset *is, int64_t value, uint8_t *success) {
    //获取value的编码
    uint8_t valenc = _intsetValueEncoding(value);
    uint32_t pos;
    if (success) *success = 1;

    /* Upgrade encoding if necessary. If we need to upgrade, we know that
     * this value should be either appended (if > 0) or prepended (if < 0),
     * because it lies outside the range of existing values. */
    //如果加入元素的编码大于已有编码 
    if (valenc > intrev32ifbe(is->encoding)) {
        //需要进行升级
        /* This always succeeds, so we don't need to curry *success. */
        return intsetUpgradeAndAdd(is,value);
    } else {
        /* Abort if the value is already present in the set.
         * This call will populate "pos" with the right position to insert
         * the value when it cannot be found. */
        //如果已经存在 那么不做插入
        if (intsetSearch(is,value,&pos)) {
            if (success) *success = 0;
            return is;
        }
		//没找到 扩容1个元素大小
        is = intsetResize(is,intrev32ifbe(is->length)+1);
        //如果插入位置不是末尾 把pos尾部字节向后移动一个元素位置
        if (pos < intrev32ifbe(is->length)) intsetMoveTail(is,pos,pos+1);
    }

    //将对应元素放置到pos位置上
    _intsetSet(is,pos,value);
    //length+1
    is->length = intrev32ifbe(intrev32ifbe(is->length)+1);
    return is;
}


//如果插入元素编码较大 那么升级编码
/* Upgrades the intset to a larger encoding and inserts the given integer. */
static intset *intsetUpgradeAndAdd(intset *is, int64_t value) {
    //获取当前编码和新元素编码
    uint8_t curenc = intrev32ifbe(is->encoding);
    uint8_t newenc = _intsetValueEncoding(value);
    int length = intrev32ifbe(is->length);
    int prepend = value < 0 ? 1 : 0;

    /* First set new encoding and resize */
    //设置新编码
    is->encoding = intrev32ifbe(newenc);
    //扩容成length+1的大小
    is = intsetResize(is,intrev32ifbe(is->length)+1);

    /* Upgrade back-to-front so we don't overwrite values.
     * Note that the "prepend" variable is used to make sure we have an empty
     * space at either the beginning or the end of the intset. */
    //从后往前 自length-1到0遍历旧元素 将旧元素迁移到新元素地址
    while(length--)
        //通过prepend来控制是否在第一位或者最后一位腾出一个元素空间
        _intsetSet(is,length+prepend,_intsetGetEncoded(is,length,curenc));

    //导致升级编码的这个元素一定要么比所有元素都大 要么比所有元素都小
    //如果元素为负数 那么放在第一位
    /* Set the value at the beginning or the end. */
    if (prepend)
        _intsetSet(is,0,value);
    else
        //不为负数放在最后一位
        _intsetSet(is,intrev32ifbe(is->length),value);
    //设置length+1
    is->length = intrev32ifbe(intrev32ifbe(is->length)+1);
    return is;
}


//将元素设置在对应位置
/* Set the value at pos, using the configured encoding. */
static void _intsetSet(intset *is, int pos, int64_t value) {
    uint32_t encoding = intrev32ifbe(is->encoding);

    if (encoding == INTSET_ENC_INT64) {
        ((int64_t*)is->contents)[pos] = value;
        memrev64ifbe(((int64_t*)is->contents)+pos);
    } else if (encoding == INTSET_ENC_INT32) {
        ((int32_t*)is->contents)[pos] = value;
        memrev32ifbe(((int32_t*)is->contents)+pos);
    } else {
        ((int16_t*)is->contents)[pos] = value;
        memrev16ifbe(((int16_t*)is->contents)+pos);
    }
}


//将from开始的尾部字节移动到to位置
static void intsetMoveTail(intset *is, uint32_t from, uint32_t to) {
    void *src, *dst;
    uint32_t bytes = intrev32ifbe(is->length)-from;
    uint32_t encoding = intrev32ifbe(is->encoding);

    if (encoding == INTSET_ENC_INT64) {
        src = (int64_t*)is->contents+from;
        dst = (int64_t*)is->contents+to;
        bytes *= sizeof(int64_t);
    } else if (encoding == INTSET_ENC_INT32) {
        src = (int32_t*)is->contents+from;
        dst = (int32_t*)is->contents+to;
        bytes *= sizeof(int32_t);
    } else {
        src = (int16_t*)is->contents+from;
        dst = (int16_t*)is->contents+to;
        bytes *= sizeof(int16_t);
    }
    memmove(dst,src,bytes);
}

```



#### intsetRemove

```c
//intset.c


/* Delete integer from intset */
intset *intsetRemove(intset *is, int64_t value, int *success) {
    uint8_t valenc = _intsetValueEncoding(value);
    uint32_t pos;
    if (success) *success = 0;

    //如果删除的元素编码在intset编码范围内 并且能找到对应的元素
    if (valenc <= intrev32ifbe(is->encoding) && intsetSearch(is,value,&pos)) {
        uint32_t len = intrev32ifbe(is->length);

        /* We know we can delete */
        if (success) *success = 1;

        //如果pos不是末尾 那么将pos后面的字节前移一个元素空间 覆盖pos位置
        /* Overwrite value with tail and update length */
        if (pos < (len-1)) intsetMoveTail(is,pos+1,pos);
        //缩小一个元素空间
        is = intsetResize(is,len-1);
        //len-1
        is->length = intrev32ifbe(len-1);
    }
    return is;
}
```

#### 优势

1. **整数的存储与处理** encoding有范围、是值，可直接比较
2. **有序集合的二分法查找** 二分法：有序 时间复杂度log(N)，找到pos
3. **编码升级并添加，要么插入第一个，要么插入最后一个** 原因：要插入值的encoding的值大于当前encoding的值 插入操作：不需要pos，小于0插头、大于0插尾



### quicklist

![](.\images\quicklist01.png)



```c
//quicklist.h


/* quicklist is a 40 byte struct (on 64-bit systems) describing a quicklist.
 * 'count' is the number of total entries.
 * 'len' is the number of quicklist nodes.
 * 'compress' is: 0 if compression disabled, otherwise it's the number
 *                of quicklistNodes to leave uncompressed at ends of quicklist.
 * 'fill' is the user-requested (or default) fill factor.
 * 'bookmakrs are an optional feature that is used by realloc this struct,
 *      so that they don't consume memory when not used. */
typedef struct quicklist {
    //头尾节点
    quicklistNode *head;
    quicklistNode *tail;
    //ziplist中所有entry的总数
    unsigned long count;        /* total count of all entries in all ziplists */
    //quicklistNodes的数量
    unsigned long len;          /* number of quicklistNodes */
    //一个ziplist能装entry的数量 当超出了这个配置，就会新建一个压缩列表
    int fill : QL_FILL_BITS;              /* fill factor for individual nodes */
    //结点压缩深度，两端各有compress个节点不压缩
    unsigned int compress : QL_COMP_BITS; /* depth of end nodes not to compress;0=off */
    //bookmarks数组的大小
    unsigned int bookmark_count: QL_BM_BITS;
    //快速列表用来重新分配内存空间时使用的数组，不使用时不占用空间
    quicklistBookmark bookmarks[];
} quicklist;

/**
fill>0 : 每个ziplist里能放多少个entry

fill<0 fill=-1 ziplist大小为4K

fill=-2 ziplist大小为8K

fill=-3 ziplist大小为16K

fill=-4 ziplist大小为32K

fill=-5 ziplist大小为64K
**/


/* Node, quicklist, and Iterator are the only data structures used currently. */

/* quicklistNode is a 32 byte struct describing a ziplist for a quicklist.
 * We use bit fields keep the quicklistNode at 32 bytes.
 * count: 16 bits, max 65536 (max zl bytes is 65k, so max count actually < 32k).
 * encoding: 2 bits, RAW=1, LZF=2.
 * container: 2 bits, NONE=1, ZIPLIST=2.
 * recompress: 1 bit, bool, true if node is temporary decompressed for usage.
 * attempted_compress: 1 bit, boolean, used for verifying during testing.
 * extra: 10 bits, free for future use; pads out the remainder of 32 bits */
typedef struct quicklistNode {
    //前后节点
    struct quicklistNode *prev;
    struct quicklistNode *next;
    //ziplist引用
    unsigned char *zl;
    //ziplist的总大小（压缩前）
    unsigned int sz;             /* ziplist size in bytes */
    //ziplist里面包含的数据项个数
    unsigned int count : 16;     /* count of items in ziplist */
    //是否被压缩 1:没有压缩 2: 压缩
    unsigned int encoding : 2;   /* RAW==1 or LZF==2 */
    //2 表示使用ziplist作为数据容器
    unsigned int container : 2;  /* NONE==1 or ZIPLIST==2 */
    //压缩标志 数据暂时解压设置为1 ，再重新压缩
    unsigned int recompress : 1; /* was this node previous compressed? */
    unsigned int attempted_compress : 1; /* node can't compress; too small */
    //扩展字段，没有启用
    unsigned int extra : 10; /* more bits to steal for future usage */
} quicklistNode;

typedef struct quicklistIter {
    //当前元素所在的quicklist
    const quicklist *quicklist;
    //当前元素所在的quicklistNode
    quicklistNode *current;
    //当前元素所在的ziplist
    unsigned char *zi;
    //该节点是ziplist的第几个节点
    long offset; /* offset in current ziplist */
    //迭代方向
    int direction;
} quicklistIter;

typedef struct quicklistEntry {
    //当前元素所在的quicklist
    const quicklist *quicklist;
    //当前元素所在的quicklistNode
    quicklistNode *node;
    //当前元素所在的ziplist中的item
    unsigned char *zi;
    //字符串类型数据
    unsigned char *value;
    //整数类型数据
    long long longval;
    //该节点大小
    unsigned int sz;
    //该节点是ziplist的第几个节点
    int offset;
} quicklistEntry;

```

#### 压缩算法：LZF

Redis采用的压缩算法是LZF，这是一种无损压缩算法

- 将数据分成多个片段
- 每个片段有两个部分：解释字段和数据字段

解释字段

- 字面型：1个字节 数据长度由后5位
- 简短重复型：2个字节 无数据字段 记录重复的起始点和重复的长度 <8字节
- 批量重复型：3个字节 无数据字段 记录重复的起始点和重复的长度 >8字节

数据压缩

- 计算重复字节的长度及位置
- 通过hash表来判断是否重复数据



**lzf_compress**

@todo

**lzf_decompress**



#### quicklistCreate

```c
//quicklist.c


/* Create a new quicklist.
 * Free with quicklistRelease(). */
quicklist *quicklistCreate(void) {
    struct quicklist *quicklist;
	
    //分配quicklist空间
    quicklist = zmalloc(sizeof(*quicklist));
    //初始化属性
    quicklist->head = quicklist->tail = NULL;
    quicklist->len = 0;
    quicklist->count = 0;
    quicklist->compress = 0;
    //设置ziplist大小为8k
    quicklist->fill = -2;
    quicklist->bookmark_count = 0;
    return quicklist;
}
```

#### quicklistPush

```c
//quicklist.c

//把quicklistNode的sz更新为ziplist的总字节数
#define quicklistNodeUpdateSz(node)                                            \
    do {                                                                       \
        (node)->sz = ziplistBlobLen((node)->zl);                               \
    } while (0)



/* Wrapper to allow argument-based switching between HEAD/TAIL pop */
void quicklistPush(quicklist *quicklist, void *value, const size_t sz,
                   int where) {
    //如果插入头
    if (where == QUICKLIST_HEAD) {
        quicklistPushHead(quicklist, value, sz);
    } else if (where == QUICKLIST_TAIL) {
        //插入尾
        quicklistPushTail(quicklist, value, sz);
    }
}


/* Add new entry to head node of quicklist.
 *
 * Returns 0 if used existing head.
 * Returns 1 if new head created. */
int quicklistPushHead(quicklist *quicklist, void *value, size_t sz) {
    quicklistNode *orig_head = quicklist->head;
    assert(sz < UINT32_MAX); /* TODO: add support for quicklist nodes that are sds encoded (not zipped) */
    //是否允许插入头节点
    if (likely(
            _quicklistNodeAllowInsert(quicklist->head, quicklist->fill, sz))) {
        //如果允许 插入到ziplist中
        quicklist->head->zl =
            ziplistPush(quicklist->head->zl, value, sz, ZIPLIST_HEAD);
        //quicklistNode的sz更新为ziplist的总字节数
        quicklistNodeUpdateSz(quicklist->head);
    } else {
        //不允许 创建新的节点
        quicklistNode *node = quicklistCreateNode();
        //新建ziplist 将node->zl绑定新ziplist 插入新value
        node->zl = ziplistPush(ziplistNew(), value, sz, ZIPLIST_HEAD);

        //quicklistNode的sz更新为ziplist的总字节数
        quicklistNodeUpdateSz(node);
        //把这个quicklistNode插入到head节点之前
        _quicklistInsertNodeBefore(quicklist, quicklist->head, node);
    }
    //ziplist的item总数+1
    quicklist->count++;
    //ziplist的item总数+1
    quicklist->head->count++;
    return (orig_head != quicklist->head);
}

/* Add new entry to tail node of quicklist.
 *
 * Returns 0 if used existing tail.
 * Returns 1 if new tail created. */
//尾部插入
int quicklistPushTail(quicklist *quicklist, void *value, size_t sz) {
    quicklistNode *orig_tail = quicklist->tail;
    assert(sz < UINT32_MAX); /* TODO: add support for quicklist nodes that are sds encoded (not zipped) */
    if (likely(
            _quicklistNodeAllowInsert(quicklist->tail, quicklist->fill, sz))) {
        quicklist->tail->zl =
            ziplistPush(quicklist->tail->zl, value, sz, ZIPLIST_TAIL);
        quicklistNodeUpdateSz(quicklist->tail);
    } else {
        quicklistNode *node = quicklistCreateNode();
        node->zl = ziplistPush(ziplistNew(), value, sz, ZIPLIST_TAIL);

        quicklistNodeUpdateSz(node);
        _quicklistInsertNodeAfter(quicklist, quicklist->tail, node);
    }
    quicklist->count++;
    quicklist->tail->count++;
    return (orig_tail != quicklist->tail);
}

//插入到ziplist中
unsigned char *ziplistPush(unsigned char *zl, unsigned char *s, unsigned int slen, int where) {
    unsigned char *p;
    //插入到头节点或者尾节点
    p = (where == ZIPLIST_HEAD) ? ZIPLIST_ENTRY_HEAD(zl) : ZIPLIST_ENTRY_END(zl);
    //调用ziplist的插入方法
    return __ziplistInsert(zl,p,s,slen);
}

//创建新的quicklistNode节点
REDIS_STATIC quicklistNode *quicklistCreateNode(void) {
    quicklistNode *node;
    //初始化属性
    node = zmalloc(sizeof(*node));
    node->zl = NULL;
    node->count = 0;
    node->sz = 0;
    node->next = node->prev = NULL;
    //设置默认不压缩和容器
    node->encoding = QUICKLIST_NODE_ENCODING_RAW;
    node->container = QUICKLIST_NODE_CONTAINER_ZIPLIST;
    node->recompress = 0;
    return node;
}


//插入到某个quicklistNode节点之前
/* Wrappers for node inserting around existing node. */
REDIS_STATIC void _quicklistInsertNodeBefore(quicklist *quicklist,
                                             quicklistNode *old_node,
                                             quicklistNode *new_node) {
    __quicklistInsertNode(quicklist, old_node, new_node, 0);
}

//插入到某个quicklistNode节点之后
REDIS_STATIC void _quicklistInsertNodeAfter(quicklist *quicklist,
                                            quicklistNode *old_node,
                                            quicklistNode *new_node) {
    __quicklistInsertNode(quicklist, old_node, new_node, 1);
}



/* Insert 'new_node' after 'old_node' if 'after' is 1.
 * Insert 'new_node' before 'old_node' if 'after' is 0.
 * Note: 'new_node' is *always* uncompressed, so if we assign it to
 *       head or tail, we do not need to uncompress it. */
REDIS_STATIC void __quicklistInsertNode(quicklist *quicklist,
                                        quicklistNode *old_node,
                                        quicklistNode *new_node, int after) {
    //如果是插入到某个节点的后面
    if (after) {
        //新节点的上一个设置为老节点
        new_node->prev = old_node;
        //如果有老节点
        if (old_node) {
            //新节点的next指向老节点的next
            new_node->next = old_node->next;
            //老节点next的prev指向新节点
            if (old_node->next)
                old_node->next->prev = new_node;
            //老节点next指向新节点
            old_node->next = new_node;
        }
        //如果尾巴是老节点
        if (quicklist->tail == old_node)
            //更新成新节点
            quicklist->tail = new_node;
    } else {
        new_node->next = old_node;
        if (old_node) {
            new_node->prev = old_node->prev;
            if (old_node->prev)
                old_node->prev->next = new_node;
            old_node->prev = new_node;
        }
        if (quicklist->head == old_node)
            quicklist->head = new_node;
    }
    //如果只有一个元素 那么设置它的head和tail都指向新节点
    /* If this insert creates the only element so far, initialize head/tail. */
    if (quicklist->len == 0) {
        quicklist->head = quicklist->tail = new_node;
    }

    //len+1
    /* Update len first, so in __quicklistCompress we know exactly len */
    quicklist->len++;
	
    if (old_node)
        quicklistCompress(quicklist, old_node);
}



//检验当前quicklistNode节点是否允许插入 
REDIS_STATIC int _quicklistNodeAllowInsert(const quicklistNode *node,
                                           const int fill, const size_t sz) {
    if (unlikely(!node))
        return 0;

    int ziplist_overhead;
    //为下一个节点的prevlen预留空间
    /* size of previous offset */
    if (sz < 254)
        ziplist_overhead = 1;
    else
        ziplist_overhead = 5;

    //当前节点真正所需的encoding大小
    /* size of forward offset */
    if (sz < 64)
        ziplist_overhead += 1;
    else if (likely(sz < 16384))
        ziplist_overhead += 2;
    else
        ziplist_overhead += 5;

    //计算 当前ziplist的字节大小 + 数据大小 + encoding + 为下一个节点预留的prevlen是否比fill设置的限制小 
    /* new_sz overestimates if 'sz' encodes to an integer type */
    unsigned int new_sz = node->sz + sz + ziplist_overhead;
    if (likely(_quicklistNodeSizeMeetsOptimizationRequirement(new_sz, fill)))
        return 1;
    /* when we return 1 above we know that the limit is a size limit (which is
     * safe, see comments next to optimization_level and SIZE_SAFETY_LIMIT) */
    //和默认的8k大小比较
    else if (!sizeMeetsSafetyLimit(new_sz))
        return 0;
    else if ((int)node->count < fill)
        return 1;
    else
        return 0;
}

//fill的限制
/* Optimization levels for size-based filling.
 * Note that the largest possible limit is 16k, so even if each record takes
 * just one byte, it still won't overflow the 16 bit count field. */
static const size_t optimization_level[] = {4096, 8192, 16384, 32768, 65536};


REDIS_STATIC int
_quicklistNodeSizeMeetsOptimizationRequirement(const size_t sz,
                                               const int fill) {
    if (fill >= 0)
        return 0;

    //取数组中的限制和sz比较
    size_t offset = (-fill) - 1;
    if (offset < (sizeof(optimization_level) / sizeof(*optimization_level))) {
        if (sz <= optimization_level[offset]) {
            return 1;
        } else {
            return 0;
        }
    } else {
        return 0;
    }
}


```



#### quicklistDelEntry

```c
//quicklist.c



/* Delete one element represented by 'entry'
 *
 * 'entry' stores enough metadata to delete the proper position in
 * the correct ziplist in the correct quicklist node. */
void quicklistDelEntry(quicklistIter *iter, quicklistEntry *entry) {
    quicklistNode *prev = entry->node->prev;
    quicklistNode *next = entry->node->next;
    int deleted_node = quicklistDelIndex((quicklist *)entry->quicklist,
                                         entry->node, &entry->zi);

    /* after delete, the zi is now invalid for any future usage. */
    //设置迭代器中的zi无效
    iter->zi = NULL;

    //如果当前zi已经被删除了 需要更新迭代器
    /* If current node is deleted, we must update iterator node and offset. */
    if (deleted_node) {
        //如果是从头
        if (iter->direction == AL_START_HEAD) {
            //指向下一个节点
            iter->current = next;
            iter->offset = 0;
        } else if (iter->direction == AL_START_TAIL) {
            //指向上一个节点
            iter->current = prev;
            iter->offset = -1;
        }
    }
    /* else if (!deleted_node), no changes needed.
     * we already reset iter->zi above, and the existing iter->offset
     * doesn't move again because:
     *   - [1, 2, 3] => delete offset 1 => [1, 3]: next element still offset 1
     *   - [1, 2, 3] => delete offset 0 => [2, 3]: next element still offset 0
     *  if we deleted the last element at offet N and now
     *  length of this ziplist is N-1, the next call into
     *  quicklistNext() will jump to the next node. */
}


//删除指定quicklist中指定quicklistNode节点的指定ziplistEntry元素
REDIS_STATIC int quicklistDelIndex(quicklist *quicklist, quicklistNode *node,
                                   unsigned char **p) {
    int gone = 0;

    //调用ziplist的方法 删除p元素
    node->zl = ziplistDelete(node->zl, p);
    node->count--;
    //如果node节点上的所有ziplistEntry都被删除
    if (node->count == 0) {
        gone = 1;
        //那么删除整个quicklistNode节点
        __quicklistDelNode(quicklist, node);
    } else {
        //更新size
        quicklistNodeUpdateSz(node);
    }
    //quicklist总数-1
    quicklist->count--;
    /* If we deleted the node, the original node is no longer valid */
    return gone ? 1 : 0;
}
```



#### quicklistPop

```c
//quicklist.c


/* Default pop function
 *
 * Returns malloc'd value from quicklist */
int quicklistPop(quicklist *quicklist, int where, unsigned char **data,
                 unsigned int *sz, long long *slong) {
    unsigned char *vstr;
    unsigned int vlen;
    long long vlong;
    if (quicklist->count == 0)
        return 0;
    //获取ziplistEntry的数据 设置到vstr、vlen和vlong中去
    int ret = quicklistPopCustom(quicklist, where, &vstr, &vlen, &vlong,
                                 _quicklistSaver);
    //根据str或者int返回不同的数据
    if (data)
        *data = vstr;
    if (slong)
        *slong = vlong;
    if (sz)
        *sz = vlen;
    return ret;
}



/* pop from quicklist and return result in 'data' ptr.  Value of 'data'
 * is the return value of 'saver' function pointer if the data is NOT a number.
 *
 * If the quicklist element is a long long, then the return value is returned in
 * 'sval'.
 *
 * Return value of 0 means no elements available.
 * Return value of 1 means check 'data' and 'sval' for values.
 * If 'data' is set, use 'data' and 'sz'.  Otherwise, use 'sval'. */
//从quicklist的头或尾pop出数据来 
int quicklistPopCustom(quicklist *quicklist, int where, unsigned char **data,
                       unsigned int *sz, long long *sval,
                       void *(*saver)(unsigned char *data, unsigned int sz)) {
    unsigned char *p;
    unsigned char *vstr;
    unsigned int vlen;
    long long vlong;
    int pos = (where == QUICKLIST_HEAD) ? 0 : -1;

    //如果quicklist中没有ziplistEntry元素
    if (quicklist->count == 0)
        return 0;

    if (data)
        *data = NULL;
    if (sz)
        *sz = 0;
    if (sval)
        *sval = -123456789;

    //看是从头还是尾pop
    quicklistNode *node;
    if (where == QUICKLIST_HEAD && quicklist->head) {
        node = quicklist->head;
    } else if (where == QUICKLIST_TAIL && quicklist->tail) {
        node = quicklist->tail;
    } else {
        return 0;
    }

    //定位对应pos位置的ziplistEntry
    p = ziplistIndex(node->zl, pos);
    //获取p对应ziplistEntry的数据 设置到vstr、vlen和vlong中去
    if (ziplistGet(p, &vstr, &vlen, &vlong)) {
        if (vstr) {
            if (data)
                *data = saver(vstr, vlen);
            if (sz)
                *sz = vlen;
        } else {
            if (data)
                *data = NULL;
            if (sval)
                *sval = vlong;
        }
        //删除对应quicklistNode节点的ziplistEntry
        quicklistDelIndex(quicklist, node, &p);
        return 1;
    }
    return 0;
}

```



#### quicklistNext

```c
//quicklist.c

/* Get next element in iterator.
 *
 * Note: You must NOT insert into the list while iterating over it.
 * You *may* delete from the list while iterating using the
 * quicklistDelEntry() function.
 * If you insert into the quicklist while iterating, you should
 * re-create the iterator after your addition.
 *
 * iter = quicklistGetIterator(quicklist,<direction>);
 * quicklistEntry entry;
 * while (quicklistNext(iter, &entry)) {
 *     if (entry.value)
 *          [[ use entry.value with entry.sz ]]
 *     else
 *          [[ use entry.longval ]]
 * }
 *
 * Populates 'entry' with values for this iteration.
 * Returns 0 when iteration is complete or if iteration not possible.
 * If return value is 0, the contents of 'entry' are not valid.
 */
//使用迭代器获取下一个元素 放到entry中
int quicklistNext(quicklistIter *iter, quicklistEntry *entry) {
    initEntry(entry);

    if (!iter) {
        D("Returning because no iter!");
        return 0;
    }
	
    //给entry赋值当前quicklist和node
    entry->quicklist = iter->quicklist;
    entry->node = iter->current;

    if (!iter->current) {
        D("Returning because current node is NULL")
        return 0;
    }

    unsigned char *(*nextFn)(unsigned char *, unsigned char *) = NULL;
    int offset_update = 0;

    //如果迭代器为空 
    if (!iter->zi) {
        //那么解压缩
        /* If !zi, use current index. */
        quicklistDecompressNodeForUse(iter->current);
        //获取当前的offset来充当第一个迭代元素
        iter->zi = ziplistIndex(iter->current->zl, iter->offset);
    } else {
        //如果不为空 根据迭代方向 找到下一个迭代元素
        /* else, use existing iterator offset and get prev/next as necessary. */
        if (iter->direction == AL_START_HEAD) {
            nextFn = ziplistNext;
            offset_update = 1;
        } else if (iter->direction == AL_START_TAIL) {
            nextFn = ziplistPrev;
            offset_update = -1;
        }
        iter->zi = nextFn(iter->current->zl, iter->zi);
        iter->offset += offset_update;
    }

    //设置entry属性
    entry->zi = iter->zi;
    entry->offset = iter->offset;

    //如果有下一个元素 直接设置entry的数据并返回
    if (iter->zi) {
        /* Populate value from existing ziplist position */
        ziplistGet(entry->zi, &entry->value, &entry->sz, &entry->longval);
        return 1;
    } else {
        //否则当前ziplist全部都迭代完了 需要跳到下一个quicklistNode去
        /* We ran out of ziplist entries.
         * Pick next node, update offset, then re-run retrieval. */
        quicklistCompress(iter->quicklist, iter->current);
        if (iter->direction == AL_START_HEAD) {
            /* Forward traversal */
            D("Jumping to start of next node");
            iter->current = iter->current->next;
            iter->offset = 0;
        } else if (iter->direction == AL_START_TAIL) {
            /* Reverse traversal */
            D("Jumping to end of previous node");
            iter->current = iter->current->prev;
            iter->offset = -1;
        }
        iter->zi = NULL;
        return quicklistNext(iter, entry);
    }
}
```

#### 优势

1. 将一个长ziplist拆分为多个短的ziplist ziplist：级联更新 折中：空间效率和时间效率上的折中
2. 压缩中间节点，LZF无损压缩进一步的节省空间
3. quicklistEntry可以直接访问到数据 数据被层层包装，有一个直接访问的方式



### listpack

<img src=".\images\listpack01.jpg" style="zoom: 50%;" />

<img src=".\images\listpack02.jpg" style="zoom: 33%;" />

对于整数编码，类似LP_ENCODING_13BIT_INT，前3bit表示编码类型，后13bit存储整数数据。

<img src=".\images\listpack03.jpg" style="zoom: 33%;" />

对于字符串编码，类似LP_ENCODING_6BIT_STR，前2bit表示编码类型，后6bit表示字符串长度，再加上字符串数据。

<img src=".\images\listpack04.jpg" style="zoom: 33%;" />

```c
//listpack的entry的编码类型

//整数编码
// 1bit表示编码类型 7bit存储无符号整数 
#define LP_ENCODING_7BIT_UINT 0
//掩码
#define LP_ENCODING_7BIT_UINT_MASK 0x80
//判断是否是当前编码类型
#define LP_ENCODING_IS_7BIT_UINT(byte) (((byte)&LP_ENCODING_7BIT_UINT_MASK)==LP_ENCODING_7BIT_UINT)

#define LP_ENCODING_13BIT_INT 0xC0
#define LP_ENCODING_13BIT_INT_MASK 0xE0
#define LP_ENCODING_IS_13BIT_INT(byte) (((byte)&LP_ENCODING_13BIT_INT_MASK)==LP_ENCODING_13BIT_INT)

#define LP_ENCODING_16BIT_INT 0xF1
#define LP_ENCODING_16BIT_INT_MASK 0xFF
#define LP_ENCODING_IS_16BIT_INT(byte) (((byte)&LP_ENCODING_16BIT_INT_MASK)==LP_ENCODING_16BIT_INT)

#define LP_ENCODING_24BIT_INT 0xF2
#define LP_ENCODING_24BIT_INT_MASK 0xFF
#define LP_ENCODING_IS_24BIT_INT(byte) (((byte)&LP_ENCODING_24BIT_INT_MASK)==LP_ENCODING_24BIT_INT)

#define LP_ENCODING_32BIT_INT 0xF3
#define LP_ENCODING_32BIT_INT_MASK 0xFF
#define LP_ENCODING_IS_32BIT_INT(byte) (((byte)&LP_ENCODING_32BIT_INT_MASK)==LP_ENCODING_32BIT_INT)

#define LP_ENCODING_64BIT_INT 0xF4
#define LP_ENCODING_64BIT_INT_MASK 0xFF
#define LP_ENCODING_IS_64BIT_INT(byte) (((byte)&LP_ENCODING_64BIT_INT_MASK)==LP_ENCODING_64BIT_INT)

//字符串编码
#define LP_ENCODING_6BIT_STR 0x80
#define LP_ENCODING_6BIT_STR_MASK 0xC0
#define LP_ENCODING_IS_6BIT_STR(byte) (((byte)&LP_ENCODING_6BIT_STR_MASK)==LP_ENCODING_6BIT_STR)

#define LP_ENCODING_12BIT_STR 0xE0
#define LP_ENCODING_12BIT_STR_MASK 0xF0
#define LP_ENCODING_IS_12BIT_STR(byte) (((byte)&LP_ENCODING_12BIT_STR_MASK)==LP_ENCODING_12BIT_STR)

#define LP_ENCODING_32BIT_STR 0xF0
#define LP_ENCODING_32BIT_STR_MASK 0xFF
#define LP_ENCODING_IS_32BIT_STR(byte) (((byte)&LP_ENCODING_32BIT_STR_MASK)==LP_ENCODING_32BIT_STR)


```



#### lpNew

```c
//listpack.c

//LP_HDR_SIZE 宏定义是在 listpack.c 中，它默认是 6 个字节，其中 4 个字节是记录 listpack 的总字节数，2 个字节是记录 listpack 的元素数量。
#define LP_HDR_SIZE 6       /* 32 bit total len + 16 bit number of elements. */
//listpack结尾
#define LP_EOF 0xFF

/* Create a new, empty listpack.
 * On success the new listpack is returned, otherwise an error is returned.
 * Pre-allocate at least `capacity` bytes of memory,
 * over-allocated memory can be shrinked by `lpShrinkToFit`.
 * */
//创建一个listpack
unsigned char *lpNew(size_t capacity) {
    //分配4字节总字节数+2字节元素数量 + 1字节结尾大小的内存
    unsigned char *lp = lp_malloc(capacity > LP_HDR_SIZE+1 ? capacity : LP_HDR_SIZE+1);
    if (lp == NULL) return NULL;
    //设置总字节数和元素数量
    lpSetTotalBytes(lp,LP_HDR_SIZE+1);
    lpSetNumElements(lp,0);
    //末尾设置EOF
    lp[LP_HDR_SIZE] = LP_EOF;
    return lp;
}
```

#### lpInsert

```c
//listpack.c



/* Insert, delete or replace the specified element 'ele' of length 'len' at
 * the specified position 'p', with 'p' being a listpack element pointer
 * obtained with lpFirst(), lpLast(), lpNext(), lpPrev() or lpSeek().
 *
 * The element is inserted before, after, or replaces the element pointed
 * by 'p' depending on the 'where' argument, that can be LP_BEFORE, LP_AFTER
 * or LP_REPLACE.
 *
 * If 'ele' is set to NULL, the function removes the element pointed by 'p'
 * instead of inserting one.
 *
 * Returns NULL on out of memory or when the listpack total length would exceed
 * the max allowed size of 2^32-1, otherwise the new pointer to the listpack
 * holding the new element is returned (and the old pointer passed is no longer
 * considered valid)
 *
 * If 'newp' is not NULL, at the end of a successful call '*newp' will be set
 * to the address of the element just added, so that it will be possible to
 * continue an interation with lpNext() and lpPrev().
 *
 * For deletion operations ('ele' set to NULL) 'newp' is set to the next
 * element, on the right of the deleted one, or to NULL if the deleted element
 * was the last one. */
//用于插入、删除、替换操作 ele是插入、替换时的新元素 size是新元素的大小、p是要插入、删除、替换的位置、newp是插入后返回的新节点
unsigned char *lpInsert(unsigned char *lp, unsigned char *ele, uint32_t size, unsigned char *p, int where, unsigned char **newp) {

    //整数类型编码存储
    unsigned char intenc[LP_MAX_INT_ENCODING_LEN];
    //backlen存储
    unsigned char backlen[LP_MAX_BACKLEN_SIZE];
	
    //编码长度 encoding + data的长度
    uint64_t enclen; /* The length of the encoded element. */

    /* An element pointer set to NULL means deletion, which is conceptually
     * replacing the element with a zero-length element. So whatever we
     * get passed as 'where', set it to LP_REPLACE. */
    //当ele=null 代表是删除节点 改为替换
    if (ele == NULL) where = LP_REPLACE;

    /* If we need to insert after the current element, we just jump to the
     * next element (that could be the EOF one) and handle the case of
     * inserting before. So the function will actually deal with just two
     * cases: LP_BEFORE and LP_REPLACE. */
    //将after统一改为before处理
    if (where == LP_AFTER) {
        //再往后走一个节点
        p = lpSkip(p);
        //就变成处理before了
        where = LP_BEFORE;
        ASSERT_INTEGRITY(lp, p);
    }

    //存储p的偏移量 后面进行重新分配空间时可以定位p
    /* Store the offset of the element 'p', so that we can obtain its
     * address again after a reallocation. */
    unsigned long poff = p-lp;

    /* Calling lpEncodeGetType() results into the encoded version of the
     * element to be stored into 'intenc' in case it is representable as
     * an integer: in that case, the function returns LP_ENCODING_INT.
     * Otherwise if LP_ENCODING_STR is returned, we'll have to call
     * lpEncodeString() to actually write the encoded string on place later.
     *
     * Whatever the returned encoding is, 'enclen' is populated with the
     * length of the encoded element. */
    //获取元素的编码类型和编码长度
    int enctype;
    //插入、替换 有ele的
    if (ele) {
        //获取ele的编码类型和编码长度
        enctype = lpEncodeGetType(ele,size,intenc,&enclen);
    } else {
        //ele为null的
        //设置编码类型为-1 编码长度为0
        enctype = -1;
        enclen = 0;
    }

    /* We need to also encode the backward-parsable length of the element
     * and append it to the end: this allows to traverse the listpack from
     * the end to the start. */
	//计算backlen的大小
    unsigned long backlen_size = ele ? lpEncodeBacklen(backlen,enclen) : 0;
    //获得原来listpack的总字节数
    uint64_t old_listpack_bytes = lpGetTotalBytes(lp);
    //获取替换的长度
    uint32_t replaced_len  = 0;
    if (where == LP_REPLACE) {
        //计算出要替换的字节长度
        replaced_len = lpCurrentEncodedSizeUnsafe(p);
        replaced_len += lpEncodeBacklen(NULL,replaced_len);
        ASSERT_INTEGRITY_LEN(lp, p, replaced_len);
    }

    //新总字节数 = 老总字节数 + (新元素编码长度 + datalen) + backlen - 替换的长度 
    uint64_t new_listpack_bytes = old_listpack_bytes + enclen + backlen_size
                                  - replaced_len;
    if (new_listpack_bytes > UINT32_MAX) return NULL;

    /* We now need to reallocate in order to make space or shrink the
     * allocation (in case 'when' value is LP_REPLACE and the new element is
     * smaller). However we do that before memmoving the memory to
     * make room for the new element if the final allocation will get
     * larger, or we do it after if the final allocation will get smaller. */

    //定位p的位置
    unsigned char *dst = lp + poff; /* May be updated after reallocation. */

    //如果是插入 那么需要新增空间
    /* Realloc before: we need more room. */
    if (new_listpack_bytes > old_listpack_bytes &&
        new_listpack_bytes > lp_malloc_size(lp)) {
        //扩容
        if ((lp = lp_realloc(lp,new_listpack_bytes)) == NULL) return NULL;
        //重新定位p的位置
        dst = lp + poff;
    }

    /* Setup the listpack relocating the elements to make the exact room
     * we need to store the new one. */
    //插入节点的
    if (where == LP_BEFORE) {
        //将要插入位置p之后的所有字节向后移动一个新元素的大小
        memmove(dst+enclen+backlen_size,dst,old_listpack_bytes-poff);
    } else { /* LP_REPLACE. */
        //计算出替换元素和新元素的差值
        long lendiff = (enclen+backlen_size)-replaced_len;
        //将替换元素后面的字节 前移/后移 移动差值个大小
        memmove(dst+replaced_len+lendiff,
                dst+replaced_len,
                old_listpack_bytes-poff-replaced_len);
    }

    //如果新总字节数变小 需要进行缩容
    /* Realloc after: we need to free space. */
    if (new_listpack_bytes < old_listpack_bytes) {
        if ((lp = lp_realloc(lp,new_listpack_bytes)) == NULL) return NULL;
        dst = lp + poff;
    }

    /* Store the entry. */
    if (newp) {
        //将新插入的元素位置赋值给newp
        *newp = dst;
        /* In case of deletion, set 'newp' to NULL if the next element is
         * the EOF element. */
        if (!ele && dst[0] == LP_EOF) *newp = NULL;
    }
    //如果有值
    if (ele) {
        //如果是整数编码
        if (enctype == LP_ENCODING_INT) {
            //直接将编码+数据拷贝到dst处
            memcpy(dst,intenc,enclen);
        } else {
            //否则将ele的编码+数据拷贝到dst处
            lpEncodeString(dst,ele,size);
        }
        //再追加一个backlen
        dst += enclen;
        memcpy(dst,backlen,backlen_size);
        dst += backlen_size;
    }

    //更新总节点数和总字节数
    /* Update header. */
    if (where != LP_REPLACE || ele == NULL) {
        uint32_t num_elements = lpGetNumElements(lp);
        if (num_elements != LP_HDR_NUMELE_UNKNOWN) {
            if (ele)
                lpSetNumElements(lp,num_elements+1);
            else
                lpSetNumElements(lp,num_elements-1);
        }
    }
    lpSetTotalBytes(lp,new_listpack_bytes);
    return lp;
}



/* Skip the current entry returning the next. It is invalid to call this
 * function if the current element is the EOF element at the end of the
 * listpack, however, while this function is used to implement lpNext(),
 * it does not return NULL when the EOF element is encountered. */
unsigned char *lpSkip(unsigned char *p) {
 	//计算encoding + datalen的长度
    unsigned long entrylen = lpCurrentEncodedSizeUnsafe(p);
    //加上 backlen = (encodinglen + datalen)的占用字节
    entrylen += lpEncodeBacklen(NULL,entrylen);
    p += entrylen;
    return p;
}
```

#### lpNext

```c
//listpack.c

/* If 'p' points to an element of the listpack, calling lpNext() will return
 * the pointer to the next element (the one on the right), or NULL if 'p'
 * already pointed to the last element of the listpack. */
unsigned char *lpNext(unsigned char *lp, unsigned char *p) {
    assert(p);
    p = lpSkip(p);
    if (p[0] == LP_EOF) return NULL;
    lpAssertValidEntry(lp, lpBytes(lp), p);
    return p;
}


/* Skip the current entry returning the next. It is invalid to call this
 * function if the current element is the EOF element at the end of the
 * listpack, however, while this function is used to implement lpNext(),
 * it does not return NULL when the EOF element is encountered. */
//根据encoding的长度和数据长度 以及backlen的长度向下一个元素移动
unsigned char *lpSkip(unsigned char *p) {
 	//计算encoding + datalen的长度
    unsigned long entrylen = lpCurrentEncodedSizeUnsafe(p);
    //加上 backlen = (encodinglen + datalen)的占用字节
    entrylen += lpEncodeBacklen(NULL,entrylen);
    p += entrylen;
    return p;
}
```





#### lpPrev

> 那么，**lpDecodeBacklen 函数如何判断 entry-len 是否结束了呢？**
>
> 这就依赖于 entry-len 的编码方式了。entry-len 每个字节的最高位，是用来表示当前字节是否为 entry-len 的最后一个字节，这里存在两种情况，分别是：
>
> - 最高位为 1，表示 entry-len 还没有结束，当前字节的左边字节仍然表示 entry-len 的内容；
> - 最高位为 0，表示当前字节已经是 entry-len 最后一个字节了。
>
> 而 entry-len 每个字节的低 7 位，则记录了实际的长度信息。这里你需要注意的是，entry-len 每个字节的低 7 位采用了**大端模式存储**，也就是说，entry-len 的低位字节保存在内存高地址上。

<img src=".\images\listpack05.jpg" style="zoom: 33%;" />

```c
//listpack.c

/* If 'p' points to an element of the listpack, calling lpPrev() will return
 * the pointer to the previous element (the one on the left), or NULL if 'p'
 * already pointed to the first element of the listpack. */
//查找前一个元素
unsigned char *lpPrev(unsigned char *lp, unsigned char *p) {
    assert(p);
    if (p-lp == LP_HDR_SIZE) return NULL;
    //先走到前一个元素的backlen的最后一个字节的起始地址上
    p--; /* Seek the first backlen byte of the last element. */
    //解码获得backlen
    uint64_t prevlen = lpDecodeBacklen(p);
    //获得backlen的长度
    prevlen += lpEncodeBacklen(NULL,prevlen);
    //前移到backlen + backlensize的地方 也就是entry的起始位置
    p -= prevlen-1; /* Seek the first byte of the previous entry. */
    lpAssertValidEntry(lp, lpBytes(lp), p);
    return p;
}


/* Decode the backlen and returns it. If the encoding looks invalid (more than
 * 5 bytes are used), UINT64_MAX is returned to report the problem. */
uint64_t lpDecodeBacklen(unsigned char *p) {
    uint64_t val = 0;
    uint64_t shift = 0;
    
    //这里backlen使用大端序的好处就出来了 
    //因为大端序的低位字节存储在高地址，那么从高地址往低地址读取的过程中，先读取的就是低位字节，在写入val时就非常方便
    do {
        //取后7bit 保存到val的对应bit位置上
        val |= (uint64_t)(p[0] & 127) << shift;
        //当第一个bit位为0时结束
        if (!(p[0] & 128)) break;
        //val写了7bit 所以前移7bit
        shift += 7;
        //读前一个字节
        p--;
        if (shift > 28) return UINT64_MAX;
    } while(1);
    //返回backlen
    return val;
}

```



#### 优势

listpack每个entry记录自己的长度，当listpack新增或修改元素时，只是操作每个entry自己，不会影响到后续的元素的长度变化，这样就彻底的避免了连锁更新（级联更新）。



### Radix Tree

<img src=".\images\rax01.jpg" style="zoom:33%;" />

Rax树分为两种节点

- **第一类节点是非压缩节点**，这类节点会包含多个指向不同子节点的指针，以及多个子节点所对应的字符
- **第二类节点是压缩节点**，这类节点会包含一个指向子节点的指针，以及子节点所代表的合并的字符串。

Rax树节点的属性

- **iskey**：表示从 Radix Tree 的根节点到当前节点路径上的字符组成的字符串，是否表示了一个完整的 key。如果是的话，那么 iskey 的值为 1。否则，iskey 的值为 0。不过，这里需要注意的是，当前节点所表示的 key，并不包含该节点自身的内容。
- **isnull**：表示当前节点是否为空节点。如果当前节点是空节点，那么该节点就不需要为指向 value 的指针分配内存空间了。
- **iscompr**：表示当前节点是非压缩节点，还是压缩节点。
- **size**：表示当前节点的大小，具体值会根据节点是压缩节点还是非压缩节点而不同。如果当前节点是压缩节点，该值表示压缩数据的长度；如果是非压缩节点，该值表示该节点指向的子节点个数。
- **对于非压缩节点来说**，data 数组包括子节点对应的字符、指向子节点的指针，以及节点表示 key 时对应的 value 指针；
- **对于压缩节点来说**，data 数组包括子节点对应的合并字符串、指向子节点的指针，以及节点为 key 时的 value 指针。

特点

- **非叶子节点无法同时指向表示单个字符的子节点和表示合并字符串的子节点**。
- 它们本身就已经包含了子节点代表的字符或合并字符串。而对于它们的子节点来说，也都属于非压缩或压缩节点，所以，**子节点本身又会保存，子节点的子节点所代表的字符或合并字符串**。
- 它们所代表的 key，**是从根节点到当前节点路径上的字符串，但并不包含当前节点**

```c
//rax.h

typedef struct rax {
    raxNode *head;
    uint64_t numele;
    uint64_t numnodes;
} rax;


typedef struct raxNode {
    uint32_t iskey:1;     /* Does this node contain a key? */
    uint32_t isnull:1;    /* Associated value is NULL (don't store it). */
    uint32_t iscompr:1;   /* Node is compressed. */
    uint32_t size:29;     /* Number of children, or compressed string len. */
    /* Data layout is as follows:
     *
     * If node is not compressed we have 'size' bytes, one for each children
     * character, and 'size' raxNode pointers, point to each child node.
     * Note how the character is not stored in the children but in the
     * edge of the parents:
     *
     * [header iscompr=0][abc][a-ptr][b-ptr][c-ptr](value-ptr?)
     *
     * if node is compressed (iscompr bit is 1) the node has 1 children.
     * In that case the 'size' bytes of the string stored immediately at
     * the start of the data section, represent a sequence of successive
     * nodes linked one after the other, for which only the last one in
     * the sequence is actually represented as a node, and pointed to by
     * the current compressed node.
     *
     * [header iscompr=1][xyz][z-ptr](value-ptr?)
     *
     * Both compressed and not compressed nodes can represent a key
     * with associated data in the radix tree at any level (not just terminal
     * nodes).
     *
     * If the node has an associated key (iskey=1) and is not NULL
     * (isnull=0), then after the raxNode pointers pointing to the
     * children, an additional value pointer is present (as you can see
     * in the representation above as "value-ptr" field).
     */
    unsigned char data[];
} raxNode;

```



#### 在Stream中的使用

<img src=".\images\rax02.jpg" style="zoom: 50%;" />



### zipmap

当redis存储hash时，如果元素个数比较少，则不使用dict，而是采用zipmap存储

![](.\images\zipmap01.png)

- zmlen：1byte，表示元素个数
- len：1byte或5byte，表示key或value的长度
- free：value后的空闲长度

#### zipmapSet

```c
//object.c


/* Set key to value, creating the key if it does not already exist.
 * If 'update' is not NULL, *update is set to 1 if the key was
 * already preset, otherwise to 0. */
unsigned char *zipmapSet(unsigned char *zm, unsigned char *key, unsigned int klen, unsigned char *val, unsigned int vlen, int *update) {
    unsigned int zmlen, offset;
    unsigned int freelen, reqlen = zipmapRequiredLength(klen,vlen);
    unsigned int empty, vempty;
    unsigned char *p;

    freelen = reqlen;
    if (update) *update = 0;
    //查找指定key的entry
    p = zipmapLookupRaw(zm,key,klen,&zmlen);
    //如果没有指定key的entry
    if (p == NULL) {
        //扩容 准备进行插入数据
        /* Key not found: enlarge */
        zm = zipmapResize(zm, zmlen+reqlen);
        p = zm+zmlen-1;
        zmlen = zmlen+reqlen;

        /* Increase zipmap length (this is an insert) */
        if (zm[0] < ZIPMAP_BIGLEN) zm[0]++;
    } else {
        //否则是更新
        /* Key found. Is there enough space for the new value? */
        /* Compute the total length: */
        if (update) *update = 1;
        freelen = zipmapRawEntryLength(p);
        //如果原来的空间不够 需要进行扩容
        if (freelen < reqlen) {
            /* Store the offset of this key within the current zipmap, so
             * it can be resized. Then, move the tail backwards so this
             * pair fits at the current position. */
            offset = p-zm;
            zm = zipmapResize(zm, zmlen-freelen+reqlen);
            p = zm+offset;

            //移动原entry后面的数据
            /* The +1 in the number of bytes to be moved is caused by the
             * end-of-zipmap byte. Note: the *original* zmlen is used. */
            memmove(p+reqlen, p+freelen, zmlen-(offset+freelen+1));
            zmlen = zmlen-freelen+reqlen;
            freelen = reqlen;
        }
    }

    /* We now have a suitable block where the key/value entry can
     * be written. If there is too much free space, move the tail
     * of the zipmap a few bytes to the front and shrink the zipmap,
     * as we want zipmaps to be very space efficient. */
    //如果空闲的空间太多 需要进行缩容回收
    empty = freelen-reqlen;
    if (empty >= ZIPMAP_VALUE_MAX_FREE) {
        /* First, move the tail <empty> bytes to the front, then resize
         * the zipmap to be <empty> bytes smaller. */
        offset = p-zm;
        memmove(p+reqlen, p+freelen, zmlen-(offset+freelen+1));
        zmlen -= empty;
        zm = zipmapResize(zm, zmlen);
        p = zm+offset;
        vempty = 0;
    } else {
        vempty = empty;
    }

    //把要插入/更新的数据放到对应位置
    /* Just write the key + value and we are done. */
    /* Key: */
    p += zipmapEncodeLength(p,klen);
    memcpy(p,key,klen);
    p += klen;
    /* Value: */
    p += zipmapEncodeLength(p,vlen);
    *p++ = vempty;
    memcpy(p,val,vlen);
    return zm;
}
```





## 存储结构

![](.\images\redisServer01.png)

<img src=".\images\redisObject02.png" alt="image-20240901161246149" style="zoom: 50%;" />

### redisObject

```c
//server.h

/* A redis object, that is a type able to hold a string / list / set */

/* The actual Redis Object */
#define OBJ_STRING 0    /* String object. */
#define OBJ_LIST 1      /* List object. */
#define OBJ_SET 2       /* Set object. */
#define OBJ_ZSET 3      /* Sorted set object. */
#define OBJ_HASH 4      /* Hash object. */

/* The "module" object type is a special one that signals that the object
 * is one directly managed by a Redis module. In this case the value points
 * to a moduleValue struct, which contains the object value (which is only
 * handled by the module itself) and the RedisModuleType struct which lists
 * function pointers in order to serialize, deserialize, AOF-rewrite and
 * free the object.
 *
 * Inside the RDB file, module types are encoded as OBJ_MODULE followed
 * by a 64 bit module type ID, which has a 54 bits module-specific signature
 * in order to dispatch the loading to the right module, plus a 10 bits
 * encoding version. */
#define OBJ_MODULE 5    /* Module object. */
#define OBJ_STREAM 6    /* Stream object. */



/* Objects encoding. Some kind of objects like Strings and Hashes can be
 * internally represented in multiple ways. The 'encoding' field of the object
 * is set to one of this fields for this object. */
#define OBJ_ENCODING_RAW 0     /* Raw representation */
#define OBJ_ENCODING_INT 1     /* Encoded as integer */
#define OBJ_ENCODING_HT 2      /* Encoded as hash table */
#define OBJ_ENCODING_ZIPMAP 3  /* Encoded as zipmap */
#define OBJ_ENCODING_LINKEDLIST 4 /* No longer used: old list encoding. */
#define OBJ_ENCODING_ZIPLIST 5 /* Encoded as ziplist */
#define OBJ_ENCODING_INTSET 6  /* Encoded as intset */
#define OBJ_ENCODING_SKIPLIST 7  /* Encoded as skiplist */
#define OBJ_ENCODING_EMBSTR 8  /* Embedded sds string encoding */
#define OBJ_ENCODING_QUICKLIST 9 /* Encoded as linked list of ziplists */
#define OBJ_ENCODING_STREAM 10 /* Encoded as a radix tree of listpacks */


typedef struct redisObject {
    unsigned type: 4;
    unsigned encoding: 4;
    unsigned lru: LRU_BITS; /* LRU time (relative to global lru_clock) or
                            * LFU data (least significant 8 bits frequency
                            * and most significant 16 bits access time). */
    int refcount;
    void *ptr;
} robj;
```

#### createObject

```c
//object.c

robj *createObject(int type, void *ptr) {
    //申请redisObject的内存空间
    robj *o = zmalloc(sizeof(*o));
    o->type = type;
    o->encoding = OBJ_ENCODING_RAW;
    o->ptr = ptr;
    o->refcount = 1;

    /* Set the LRU to the current lruclock (minutes resolution), or
     * alternatively the LFU counter. */
    if (server.maxmemory_policy & MAXMEMORY_FLAG_LFU) {
        o->lru = (LFUGetTimeInMinutes() << 8) | LFU_INIT_VAL;
    } else {
        o->lru = LRU_CLOCK();
    }
    return o;
}
```

#### freeXXXObject

```c
//object.c

//释放相应类型的空间


void freeStringObject(robj *o) {
    if (o->encoding == OBJ_ENCODING_RAW) {
        sdsfree(o->ptr);
    }
}

void freeListObject(robj *o) {
    if (o->encoding == OBJ_ENCODING_QUICKLIST) {
        quicklistRelease(o->ptr);
    } else {
        serverPanic("Unknown list encoding type");
    }
}

void freeSetObject(robj *o) {
    switch (o->encoding) {
        case OBJ_ENCODING_HT:
            dictRelease((dict *) o->ptr);
            break;
        case OBJ_ENCODING_INTSET:
            zfree(o->ptr);
            break;
        default:
            serverPanic("Unknown set encoding type");
    }
}

void freeZsetObject(robj *o) {
    zset *zs;
    switch (o->encoding) {
        case OBJ_ENCODING_SKIPLIST:
            zs = o->ptr;
            dictRelease(zs->dict);
            zslFree(zs->zsl);
            zfree(zs);
            break;
        case OBJ_ENCODING_ZIPLIST:
            zfree(o->ptr);
            break;
        default:
            serverPanic("Unknown sorted set encoding");
    }
}

void freeHashObject(robj *o) {
    switch (o->encoding) {
        case OBJ_ENCODING_HT:
            dictRelease((dict *) o->ptr);
            break;
        case OBJ_ENCODING_ZIPLIST:
            zfree(o->ptr);
            break;
        default:
            serverPanic("Unknown hash encoding type");
            break;
    }
}

void freeModuleObject(robj *o) {
    moduleValue *mv = o->ptr;
    mv->type->free(mv->value);
    zfree(mv);
}

void freeStreamObject(robj *o) {
    freeStream(o->ptr);
}
```



#### incrRefCount

```c
//object.c

//引用计数+1
void incrRefCount(robj *o) {
    if (o->refcount < OBJ_FIRST_SPECIAL_REFCOUNT) {
        o->refcount++;
    } else {
        if (o->refcount == OBJ_SHARED_REFCOUNT) {
            /* Nothing to do: this refcount is immutable. */
        } else if (o->refcount == OBJ_STATIC_REFCOUNT) {
            serverPanic("You tried to retain an object allocated in the stack");
        }
    }
}
```

#### decrRefCount

```c
//object.c

//引用计数-1
//如果引用计数要减为0了 那么就释放对应数据类型的空间
void decrRefCount(robj *o) {
    if (o->refcount == 1) {
        switch (o->type) {
            case OBJ_STRING: freeStringObject(o);
                break;
            case OBJ_LIST: freeListObject(o);
                break;
            case OBJ_SET: freeSetObject(o);
                break;
            case OBJ_ZSET: freeZsetObject(o);
                break;
            case OBJ_HASH: freeHashObject(o);
                break;
            case OBJ_MODULE: freeModuleObject(o);
                break;
            case OBJ_STREAM: freeStreamObject(o);
                break;
            default: serverPanic("Unknown object type");
                break;
        }
        zfree(o);
    } else {
        if (o->refcount <= 0)
            serverPanic("decrRefCount against refcount <= 0");
        if (o->refcount != OBJ_SHARED_REFCOUNT) o->refcount--;
    }
}
```



#### tryObjectEncoding

```c
//object.c


#define sdsEncodedObject(objptr) (objptr->encoding == OBJ_ENCODING_RAW || objptr->encoding == OBJ_ENCODING_EMBSTR)



//尝试将一个String类型的robj的编码改成int节省内存
/* Try to encode a string object in order to save space */
robj *tryObjectEncoding(robj *o) {
    long value;
    //获取sds的地址
    sds s = o->ptr;
    size_t len;

    /* Make sure this is a string object, the only type we encode
     * in this function. Other types use encoded memory efficient
     * representations but are handled by the commands implementing
     * the type. */
    //确保是一个String类型
    serverAssertWithInfo(NULL, o, o->type == OBJ_STRING);

    /* We try some specialized encoding only for objects that are
     * RAW or EMBSTR encoded, in other words objects that are still
     * in represented by an actually array of chars. */
    //确定编码是embstr或者raw
    if (!sdsEncodedObject(o)) return o;

    /* It's not safe to encode shared objects: shared objects can be shared
     * everywhere in the "object space" of Redis and may end in places where
     * they are not handled. We handle them only as values in the keyspace. */
    //确定引用计数为1
    if (o->refcount > 1) return o;

    /* Check if we can represent this string as a long integer.
     * Note that we are sure that a string larger than 20 chars is not
     * representable as a 32 nor 64 bit integer. */
    //获取sds的长度
    len = sdslen(s);
    //如果长度<=20并且转化成long long数字类型成功
    if (len <= 20 && string2l(s, len, &value)) {
        /* This object is encodable as a long. Try to use a shared object.
         * Note that we avoid using shared integers when maxmemory is used
         * because every object needs to have a private LRU field for the LRU
         * algorithm to work well. */
        //尝试使用共享整数
        if ((server.maxmemory == 0 ||
             !(server.maxmemory_policy & MAXMEMORY_FLAG_NO_SHARED_INTEGERS)) &&
            value >= 0 &&
            value < OBJ_SHARED_INTEGERS) {
            //减少引用计数来释放原来的空间
            decrRefCount(o);
            //增加共享整数的引用计数
            incrRefCount(shared.integers[value]);
            //返回共享整数
            return shared.integers[value];
        } else {
            //不使用共享整数
            //编码是raw
            if (o->encoding == OBJ_ENCODING_RAW) {
                //释放原空间
                sdsfree(o->ptr);
                //编码改成int
                o->encoding = OBJ_ENCODING_INT;
                //指针指向value
                o->ptr = (void *) value;
                return o;
             //编码是embstr
            } else if (o->encoding == OBJ_ENCODING_EMBSTR) {
                //减少引用计数来释放原来的空间
                decrRefCount(o);
                //创建String从long long value
                return createStringObjectFromLongLongForValue(value);
            }
        }
    }

    /* If the string is small and is still RAW encoded,
     * try the EMBSTR encoding which is more efficient.
     * In this representation the object and the SDS string are allocated
     * in the same chunk of memory to save space and cache misses. */
    if (len <= OBJ_ENCODING_EMBSTR_SIZE_LIMIT) {
        robj *emb;

        if (o->encoding == OBJ_ENCODING_EMBSTR) return o;
        emb = createEmbeddedStringObject(s, sdslen(s));
        decrRefCount(o);
        return emb;
    }

    /* We can't encode the object...
     *
     * Do the last try, and at least optimize the SDS string inside
     * the string object to require little space, in case there
     * is more than 10% of free space at the end of the SDS string.
     *
     * We do that only for relatively large strings as this branch
     * is only entered if the length of the string is greater than
     * OBJ_ENCODING_EMBSTR_SIZE_LIMIT. */
    trimStringObjectIfNeeded(o);

    /* Return the original object. */
    return o;
}

//当需要 LFU/LRU 信息时避免共享对象，即当对象被用作键空间中的值时，并且 Redis 配置为基于LFU/LRU 进行驱逐。
robj *createStringObjectFromLongLongForValue(long long value) {
    return createStringObjectFromLongLongWithOptions(value, 1);
}



/* Create a string object from a long long value. When possible returns a
 * shared integer object, or at least an integer encoded one.
 *
 * If valueobj is non zero, the function avoids returning a shared
 * integer, because the object is going to be used as value in the Redis key
 * space (for instance when the INCR command is used), so we want LFU/LRU
 * values specific for each key. */
robj *createStringObjectFromLongLongWithOptions(long long value, int valueobj) {
    robj *o;

    if (server.maxmemory == 0 ||
        !(server.maxmemory_policy & MAXMEMORY_FLAG_NO_SHARED_INTEGERS)) {
        /* If the maxmemory policy permits, we can still return shared integers
         * even if valueobj is true. */
        //如果 maxmemory 策略允许，即使 valueobj 为 true，我们仍然可以返回共享整数。
        valueobj = 0;
    }
	
    //如果使用共享整数
    if (value >= 0 && value < OBJ_SHARED_INTEGERS && valueobj == 0) {
        //返回共享整数
        incrRefCount(shared.integers[value]);
        o = shared.integers[value];
    } else {
        //如果value在long范围内
        if (value >= LONG_MIN && value <= LONG_MAX) {
            //采用int进行编码
            o = createObject(OBJ_STRING, NULL);
            o->encoding = OBJ_ENCODING_INT;
            o->ptr = (void *) ((long) value);
        } else {
            //超过long的范围 采用embstr或者raw编码
            o = createObject(OBJ_STRING, sdsfromlonglong(value));
        }
    }
    return o;
}

```



#### getDecodedObject

```c
//object.c

//将int编码的对象变成string返回

/* Get a decoded version of an encoded object (returned as a new object).
 * If the object is already raw-encoded just increment the ref count. */
robj *getDecodedObject(robj *o) {
    robj *dec;

    //如果是sds 引用计数+1
    if (sdsEncodedObject(o)) {
        incrRefCount(o);
        return o;
    }
    //如果是String类型 但是int编码 尝试将int转换成sds
    if (o->type == OBJ_STRING && o->encoding == OBJ_ENCODING_INT) {
        char buf[32];

        ll2string(buf, 32, (long) o->ptr);
        dec = createStringObject(buf, strlen(buf));
        return dec;
    } else {
        serverPanic("Unknown encoding type");
    }
}
```



#### objectCommand

- OBJECT REFCOUNT KEY ：查看当前键的引用计数
- OBJECT ENCODING KEY：查看当前键的编码
- OBJECT IDLETIME KEY：查看当前键的空闲时间
- OBJECT FREQ KEY：查看当前键最近访问频率的对数
- OBJECT HELP：查看OBJECT命令的帮助信息



```c
//object.c

/* Object command allows to inspect the internals of a Redis Object.
 * Usage: OBJECT <refcount|encoding|idletime|freq> <key> */
void objectCommand(client *c) {
    robj *o;

    if (c->argc == 2 && !strcasecmp(c->argv[1]->ptr, "help")) {
        const char *help[] = {
            "ENCODING <key>",
            "    Return the kind of internal representation used in order to store the value",
            "    associated with a <key>.",
            "FREQ <key>",
            "    Return the access frequency index of the <key>. The returned integer is",
            "    proportional to the logarithm of the recent access frequency of the key.",
            "IDLETIME <key>",
            "    Return the idle time of the <key>, that is the approximated number of",
            "    seconds elapsed since the last access to the key.",
            "REFCOUNT <key>",
            "    Return the number of references of the value associated with the specified",
            "    <key>.",
            NULL
        };
        addReplyHelp(c, help);
    } else if (!strcasecmp(c->argv[1]->ptr, "refcount") && c->argc == 3) {
        if ((o = objectCommandLookupOrReply(c, c->argv[2], shared.null[c->resp]))
            == NULL)
            return;
        addReplyLongLong(c, o->refcount);
    } else if (!strcasecmp(c->argv[1]->ptr, "encoding") && c->argc == 3) {
        if ((o = objectCommandLookupOrReply(c, c->argv[2], shared.null[c->resp]))
            == NULL)
            return;
        addReplyBulkCString(c, strEncoding(o->encoding));
    } else if (!strcasecmp(c->argv[1]->ptr, "idletime") && c->argc == 3) {
        if ((o = objectCommandLookupOrReply(c, c->argv[2], shared.null[c->resp]))
            == NULL)
            return;
        if (server.maxmemory_policy & MAXMEMORY_FLAG_LFU) {
            addReplyError(
                c,
                "An LFU maxmemory policy is selected, idle time not tracked. Please note that when switching between policies at runtime LRU and LFU data will take some time to adjust.");
            return;
        }
        addReplyLongLong(c, estimateObjectIdleTime(o) / 1000);
    } else if (!strcasecmp(c->argv[1]->ptr, "freq") && c->argc == 3) {
        if ((o = objectCommandLookupOrReply(c, c->argv[2], shared.null[c->resp]))
            == NULL)
            return;
        if (!(server.maxmemory_policy & MAXMEMORY_FLAG_LFU)) {
            addReplyError(
                c,
                "An LFU maxmemory policy is not selected, access frequency not tracked. Please note that when switching between policies at runtime LRU and LFU data will take some time to adjust.");
            return;
        }
        /* LFUDecrAndReturn should be called
         * in case of the key has not been accessed for a long time,
         * because we update the access time only
         * when the key is read or overwritten. */
        addReplyLongLong(c, LFUDecrAndReturn(o));
    } else {
        addReplySubcommandSyntaxError(c);
    }
}



```



#### 设计思想和优势

1. redis的value都封装在redisObject中 ptr指针指向具体对象
2. 对象的统一处理方式：引用计数、类别检查、缓存淘汰 redisObject中有这些属性
3. 不同的编码转换可以节省内存和优化查询
4. 共享对象的使用可以有效的减少内存占用 整数 共享







### redisDb

![](.\images\redisDb01.png)

- dict *dict：数据库键空间，保存着数据库中的所有键值对
- dict *expires：键的过期时间，key是键，value是过期时间
- dict *blocking_keys：处于阻塞状态的key和client
- dict *ready_keys：解除阻塞状态的key和client
- dict *watched_keys：watch的key和client
- int id：数据库id
- long long avg_ttl：数据库内所有键的平均生存时间
- list *defrag_later：尝试碎片整理的key列表

```c
//server.h


/* Redis database representation. There are multiple databases identified
 * by integers from 0 (the default database) up to the max configured
 * database. The database number is the 'id' field in the structure. */
typedef struct redisDb {
    //数据库键空间，保存着数据库中的所有键值对
    dict *dict; /* The keyspace for this DB */
    //键的过期时间，key是键，value是过期时间
    dict *expires; /* Timeout of keys with a timeout set */
    //处于阻塞状态的key和client
    dict *blocking_keys; /* Keys with clients waiting for data (BLPOP)*/
    //解除阻塞状态的key和client
    dict *ready_keys; /* Blocked keys that received a PUSH */
    //watch的key和client
    dict *watched_keys; /* WATCHED keys for MULTI/EXEC CAS */
    //数据库id
    int id; /* Database ID */
    //数据库内所有键的平均生存时间
    long long avg_ttl; /* Average TTL, just for stats */
    unsigned long expires_cursor; /* Cursor of the active expire cycle. */
    //尝试碎片整理的key列表
    list *defrag_later; /* List of key names to attempt to defrag one by one, gradually. */
} redisDb;


//在server.c的main函数中 redis-server启动时会创建db
void initServer(void) {
    //...
    
    /* Create the Redis databases, and initialize other internal state. */
    for (j = 0; j < server.dbnum; j++) {
        server.db[j].dict = dictCreate(&dbDictType,NULL);
        server.db[j].expires = dictCreate(&dbExpiresDictType,NULL);
        server.db[j].expires_cursor = 0;
        server.db[j].blocking_keys = dictCreate(&keylistDictType,NULL);
        server.db[j].ready_keys = dictCreate(&objectKeyPointerValueDictType,NULL);
        server.db[j].watched_keys = dictCreate(&keylistDictType,NULL);
        server.db[j].id = j;
        server.db[j].avg_ttl = 0;
        server.db[j].defrag_later = listCreate();
        listSetFreeMethod(server.db[j].defrag_later,(void (*)(void*))sdsfree);
    }
    //...
}
```



#### lookupKey

```c
//db.c


/* Low level key lookup API, not actually called directly from commands
 * implementations that should instead rely on lookupKeyRead(),
 * lookupKeyWrite() and lookupKeyReadWithFlags(). */
//在db中查找对应的key
robj *lookupKey(redisDb *db, robj *key, int flags) {
    //在dict中查找对应的key
    dictEntry *de = dictFind(db->dict,key->ptr);
    if (de) {
        //获取它的value
        robj *val = dictGetVal(de);

        /* Update the access time for the ageing algorithm.
         * Don't do it if we have a saving child, as this will trigger
         * a copy on write madness. */
        //如果是LFU 更新计数
        if (!hasActiveChildProcess() && !(flags & LOOKUP_NOTOUCH)){
            if (server.maxmemory_policy & MAXMEMORY_FLAG_LFU) {
                updateLFU(val);
            } else {
                //如果是lru 更新lru链
                val->lru = LRU_CLOCK();
            }
        }
        return val;
    } else {
        return NULL;
    }
}
```





#### dbAdd

```c
//db.c

/* Add the key to the DB. It's up to the caller to increment the reference
 * counter of the value if needed.
 *
 * The program is aborted if the key already exists. */
//将key value加入到redisdb中
void dbAdd(redisDb *db, robj *key, robj *val) {
    //拷贝一个新的sds来存储key
    sds copy = sdsdup(key->ptr);
    //将key value放入dict中
    int retval = dictAdd(db->dict, copy, val);

    serverAssertWithInfo(NULL,key,retval == DICT_OK);
    //如果是list或者zset类型 需要唤醒被阻塞
    signalKeyAsReady(db, key, val->type);
    if (server.cluster_enabled) slotToKeyAdd(key->ptr);
}
```



#### dbRandomKey



```c
//db.c

/* Return a random key, in form of a Redis object.
 * If there are no keys, NULL is returned.
 *
 * The function makes sure to return keys not already expired. */
//查找一个随机的key
robj *dbRandomKey(redisDb *db) {
    dictEntry *de;
	//最大查找次数
    int maxtries = 100;
    //是否全部key都过期了
    int allvolatile = dictSize(db->dict) == dictSize(db->expires);

    //循环查找
    while(1) {
        sds key;
        robj *keyobj;
		
        //从dict中随机获取一个key
        de = dictGetFairRandomKey(db->dict);
        if (de == NULL) return NULL;

        //创建key的redisObject对象
        key = dictGetKey(de);
        keyobj = createStringObject(key,sdslen(key));
        //在过期dict中查找
        if (dictFind(db->expires,key)) {
            //如果全部键都过期了
            if (allvolatile && server.masterhost && --maxtries == 0) {
                /* If the DB is composed only of keys with an expire set,
                 * it could happen that all the keys are already logically
                 * expired in the slave, so the function cannot stop because
                 * expireIfNeeded() is false, nor it can stop because
                 * dictGetRandomKey() returns NULL (there are keys to return).
                 * To prevent the infinite loop we do some tries, but if there
                 * are the conditions for an infinite loop, eventually we
                 * return a key name that may be already expired. */
                //随机返回一个key
                return keyobj;
            }
            //找到过期的key
            if (expireIfNeeded(db,keyobj)) {
                //引用-1 来进行释放
                decrRefCount(keyobj);
                //继续查找
                continue; /* search for another key. This expired. */
            }
        }
        //直到找到一个没过期的key
        return keyobj;
    }
}
```



#### dbSyncDelete

```c
//db.c

/* Delete a key, value, and associated expiration entry if any, from the DB */
int dbSyncDelete(redisDb *db, robj *key) {
    /* Deleting an entry from the expires dict will not free the sds of
     * the key, because it is shared with the main dictionary. */
    //从过期dict中删除key
    if (dictSize(db->expires) > 0) dictDelete(db->expires,key->ptr);
    //断开dict中key的连接
    dictEntry *de = dictUnlink(db->dict,key->ptr);
    if (de) {
        //获取val值
        robj *val = dictGetVal(de);
        /* Tells the module that the key has been unlinked from the database. */
        moduleNotifyKeyUnlink(key,val);
        //将断开连接的dictEntry释放
        dictFreeUnlinkedEntry(db->dict,de);
        if (server.cluster_enabled) slotToKeyDel(key->ptr);
        return 1;
    } else {
        return 0;
    }
}
```



#### dbAsyncDelete

```c
//lazyfree.c

#define LAZYFREE_THRESHOLD 64


/* Delete a key, value, and associated expiration entry if any, from the DB.
 * If there are enough allocations to free the value object may be put into
 * a lazy free list instead of being freed synchronously. The lazy free list
 * will be reclaimed in a different bio.c thread. */
int dbAsyncDelete(redisDb *db, robj *key) {
    /* Deleting an entry from the expires dict will not free the sds of
     * the key, because it is shared with the main dictionary. */
    //在过期列表中删除key
    if (dictSize(db->expires) > 0) dictDelete(db->expires,key->ptr);

    /* If the value is composed of a few allocations, to free in a lazy way
     * is actually just slower... So under a certain limit we just free
     * the object synchronously. */
    //断开dict的连接
    dictEntry *de = dictUnlink(db->dict,key->ptr);
    if (de) {
        robj *val = dictGetVal(de);

        /* Tells the module that the key has been unlinked from the database. */
        moduleNotifyKeyUnlink(key,val);

        //获取释放当前key val的工作量 
        //sds为1 其他数据结构则是容器内元素的数量
        size_t free_effort = lazyfreeGetFreeEffort(key,val);

        /* If releasing the object is too much work, do it in the background
         * by adding the object to the lazy free list.
         * Note that if the object is shared, to reclaim it now it is not
         * possible. This rarely happens, however sometimes the implementation
         * of parts of the Redis core may call incrRefCount() to protect
         * objects, and then call dbDelete(). In this case we'll fall
         * through and reach the dictFreeUnlinkedEntry() call, that will be
         * equivalent to just calling decrRefCount(). */
        //超过阈值 那么需要后台异步释放
        if (free_effort > LAZYFREE_THRESHOLD && val->refcount == 1) {
            //需要后台释放的任务+1
            atomicIncr(lazyfree_objects,1);
            //创建后台释放任务 提交到队列中等待释放
            bioCreateLazyFreeJob(lazyfreeFreeObject,1, val);
            dictSetVal(db->dict,de,NULL);
        }
    }

    //否则采用同步释放
    /* Release the key-val pair, or just the key if we set the val
     * field to NULL in order to lazy free it later. */
    if (de) {
        dictFreeUnlinkedEntry(db->dict,de);
        if (server.cluster_enabled) slotToKeyDel(key->ptr);
        return 1;
    } else {
        return 0;
    }
}
```



#### emptyDb

```c
//db.c


/* Remove all keys from all the databases in a Redis server.
 * If callback is given the function is called from time to time to
 * signal that work is in progress.
 *
 * The dbnum can be -1 if all the DBs should be flushed, or the specified
 * DB number if we want to flush only a single Redis database number.
 *
 * Flags are be EMPTYDB_NO_FLAGS if no special flags are specified or
 * EMPTYDB_ASYNC if we want the memory to be freed in a different thread
 * and the function to return ASAP.
 *
 * On success the function returns the number of keys removed from the
 * database(s). Otherwise -1 is returned in the specific case the
 * DB number is out of range, and errno is set to EINVAL. */

//清空指定数据库
long long emptyDb(int dbnum, int flags, void(callback)(void*)) {
    //是否异步清空
    int async = (flags & EMPTYDB_ASYNC);
    RedisModuleFlushInfoV1 fi = {REDISMODULE_FLUSHINFO_VERSION,!async,dbnum};
    long long removed = 0;

    if (dbnum < -1 || dbnum >= server.dbnum) {
        errno = EINVAL;
        return -1;
    }

    /* Fire the flushdb modules event. */
    moduleFireServerEvent(REDISMODULE_EVENT_FLUSHDB,
                          REDISMODULE_SUBEVENT_FLUSHDB_START,
                          &fi);
	//通知清空数据库事件给watched keys
    /* Make sure the WATCHed keys are affected by the FLUSH* commands.
     * Note that we need to call the function while the keys are still
     * there. */
    signalFlushedDb(dbnum, async);

    //清空指定数据库
    /* Empty redis database structure. */
    removed = emptyDbStructure(server.db, dbnum, async, callback);

    /* Flush slots to keys map if enable cluster, we can flush entire
     * slots to keys map whatever dbnum because only support one DB
     * in cluster mode. */
    if (server.cluster_enabled) slotToKeyFlush(async);

    //如果是清空所有数据库 那么从库的数据库也要进行清空
    if (dbnum == -1) flushSlaveKeysWithExpireList();

    /* Also fire the end event. Note that this event will fire almost
     * immediately after the start event if the flush is asynchronous. */
    moduleFireServerEvent(REDISMODULE_EVENT_FLUSHDB,
                          REDISMODULE_SUBEVENT_FLUSHDB_END,
                          &fi);

    return removed;
}



/* Remove all keys from the database(s) structure. The dbarray argument
 * may not be the server main DBs (could be a backup).
 *
 * The dbnum can be -1 if all the DBs should be emptied, or the specified
 * DB index if we want to empty only a single database.
 * The function returns the number of keys removed from the database(s). */
long long emptyDbStructure(redisDb *dbarray, int dbnum, int async,
                           void(callback)(void*))
{
    long long removed = 0;
    int startdb, enddb;

    //如果dbnum = -1 那么清空所有数据库
    if (dbnum == -1) {
        startdb = 0;
        enddb = server.dbnum-1;
    } else {
        //否则清空指定的
        startdb = enddb = dbnum;
    }

    //清空每个数据库的dict和expires
    for (int j = startdb; j <= enddb; j++) {
        removed += dictSize(dbarray[j].dict);
        //异步
        if (async) {
            emptyDbAsync(&dbarray[j]);
        } else {
            //同步清除
            dictEmpty(dbarray[j].dict,callback);
            dictEmpty(dbarray[j].expires,callback);
        }
        /* Because all keys of database are removed, reset average ttl. */
        dbarray[j].avg_ttl = 0;
        dbarray[j].expires_cursor = 0;
    }
	//返回清空的数据库数量
    return removed;
}

```

#### selectDb

```c
//db.c

//选择数据库
int selectDb(client *c, int id) {
    if (id < 0 || id >= server.dbnum)
        return C_ERR;
    c->db = &server.db[id];
    return C_OK;
}
```



### redisServer

* char *configfile：配置文件绝对路径
* int hz：serverCron的执行频次
* int dbnum：数据库的数量
* redisDb *db：数据库数组
* dict *commands：命令字典
* aeEventLoop *el：事件循环
* int port：服务器监听端口
* char *bindaddr[]：绑定的ip地址
* int ipfd[]：针对ip地址创建的socket文件描述符
* list *clients：当前连接到redis服务器的所有客户端
* char *unixsocket：socket路径
* int maxidletime：客户端超时时间
* int aof_state：aof开启标志

```c
//server.h

struct redisServer {
    /* General */
    pid_t pid; /* Main process pid. */
    pthread_t main_thread_id; /* Main thread id */
    char *configfile; /* Absolute config file path, or NULL */
    char *executable; /* Absolute executable file path. */
    char **exec_argv; /* Executable argv vector (copy). */
    int dynamic_hz; /* Change hz value depending on # of clients. */
    int config_hz; /* Configured HZ value. May be different than
                                   the actual 'hz' field value if dynamic-hz
                                   is enabled. */
    mode_t umask; /* The umask value of the process on startup */
    int hz; /* serverCron() calls frequency in hertz */
    int in_fork_child; /* indication that this is a fork child */
    redisDb *db;
    dict *commands; /* Command table */
    dict *orig_commands; /* Command table before command renaming. */
    aeEventLoop *el;
    rax *errors; /* Errors table */
    redisAtomic unsigned int lruclock; /* Clock for LRU eviction */
    volatile sig_atomic_t shutdown_asap; /* SHUTDOWN needed ASAP */
    int activerehashing; /* Incremental rehash in serverCron() */
    int active_defrag_running; /* Active defragmentation running (holds current scan aggressiveness) */
    char *pidfile; /* PID file path */
    int arch_bits; /* 32 or 64 depending on sizeof(long) */
    int cronloops; /* Number of times the cron function run */
    char runid[CONFIG_RUN_ID_SIZE + 1]; /* ID always different at every exec. */
    int sentinel_mode; /* True if this instance is a Sentinel. */
    size_t initial_memory_usage; /* Bytes used after initialization. */
    int always_show_logo; /* Show logo even for non-stdout logging. */
    int in_eval; /* Are we inside EVAL? */
    int in_exec; /* Are we inside EXEC? */
    int propagate_in_transaction; /* Make sure we don't propagate nested MULTI/EXEC */
    char *ignore_warnings; /* Config: warnings that should be ignored. */
    int client_pause_in_transaction; /* Was a client pause executed during this Exec? */
    /* Modules */
    dict *moduleapi; /* Exported core APIs dictionary for modules. */
    dict *sharedapi; /* Like moduleapi but containing the APIs that
                                   modules share with each other. */
    list *loadmodule_queue; /* List of modules to load at startup. */
    int module_blocked_pipe[2]; /* Pipe used to awake the event loop if a
                                   client blocked on a module command needs
                                   to be processed. */
    pid_t child_pid; /* PID of current child */
    int child_type; /* Type of current child */
    client *module_client; /* "Fake" client to call Redis from modules */
    /* Networking */
    int port; /* TCP listening port */
    int tls_port; /* TLS listening port */
    int tcp_backlog; /* TCP listen() backlog */
    char *bindaddr[CONFIG_BINDADDR_MAX]; /* Addresses we should bind to */
    int bindaddr_count; /* Number of addresses in server.bindaddr[] */
    char *unixsocket; /* UNIX socket path */
    mode_t unixsocketperm; /* UNIX socket permission */
    socketFds ipfd; /* TCP socket file descriptors */
    socketFds tlsfd; /* TLS socket file descriptors */
    int sofd; /* Unix socket file descriptor */
    socketFds cfd; /* Cluster bus listening socket */
    list *clients; /* List of active clients */
    list *clients_to_close; /* Clients to close asynchronously */
    list *clients_pending_write; /* There is to write or install handler. */
    list *clients_pending_read; /* Client has pending read socket buffers. */
    list *slaves, *monitors; /* List of slaves and MONITORs */
    client *current_client; /* Current client executing the command. */
    rax *clients_timeout_table; /* Radix tree for blocked clients timeouts. */
    long fixed_time_expire; /* If > 0, expire keys against server.mstime. */
    int in_nested_call; /* If > 0, in a nested call of a call */
    rax *clients_index; /* Active clients dictionary by client ID. */
    pause_type client_pause_type; /* True if clients are currently paused */
    list *paused_clients; /* List of pause clients */
    mstime_t client_pause_end_time; /* Time when we undo clients_paused */
    char neterr[ANET_ERR_LEN]; /* Error buffer for anet.c */
    dict *migrate_cached_sockets; /* MIGRATE cached sockets */
    redisAtomic uint64_t next_client_id; /* Next client unique ID. Incremental. */
    int protected_mode; /* Don't accept external connections. */
    int gopher_enabled; /* If true the server will reply to gopher
                                   queries. Will still serve RESP2 queries. */
    int io_threads_num; /* Number of IO threads to use. */
    int io_threads_do_reads; /* Read and parse from IO threads? */
    int io_threads_active; /* Is IO threads currently active? */
    long long events_processed_while_blocked; /* processEventsWhileBlocked() */

    /* RDB / AOF loading information */
    volatile sig_atomic_t loading; /* We are loading data from disk if true */
    off_t loading_total_bytes;
    off_t loading_rdb_used_mem;
    off_t loading_loaded_bytes;
    time_t loading_start_time;
    off_t loading_process_events_interval_bytes;
    /* Fast pointers to often looked up command */
    struct redisCommand *delCommand, *multiCommand, *lpushCommand,
            *lpopCommand, *rpopCommand, *zpopminCommand,
            *zpopmaxCommand, *sremCommand, *execCommand,
            *expireCommand, *pexpireCommand, *xclaimCommand,
            *xgroupCommand, *rpoplpushCommand, *lmoveCommand;
    /* Fields used only for stats */
    time_t stat_starttime; /* Server start time */
    long long stat_numcommands; /* Number of processed commands */
    long long stat_numconnections; /* Number of connections received */
    long long stat_expiredkeys; /* Number of expired keys */
    double stat_expired_stale_perc; /* Percentage of keys probably expired */
    long long stat_expired_time_cap_reached_count; /* Early expire cylce stops.*/
    long long stat_expire_cycle_time_used; /* Cumulative microseconds used. */
    long long stat_evictedkeys; /* Number of evicted keys (maxmemory) */
    long long stat_keyspace_hits; /* Number of successful lookups of keys */
    long long stat_keyspace_misses; /* Number of failed lookups of keys */
    long long stat_active_defrag_hits; /* number of allocations moved */
    long long stat_active_defrag_misses; /* number of allocations scanned but not moved */
    long long stat_active_defrag_key_hits; /* number of keys with moved allocations */
    long long stat_active_defrag_key_misses; /* number of keys scanned and not moved */
    long long stat_active_defrag_scanned; /* number of dictEntries scanned */
    size_t stat_peak_memory; /* Max used memory record */
    long long stat_fork_time; /* Time needed to perform latest fork() */
    double stat_fork_rate; /* Fork rate in GB/sec. */
    long long stat_total_forks; /* Total count of fork. */
    long long stat_rejected_conn; /* Clients rejected because of maxclients */
    long long stat_sync_full; /* Number of full resyncs with slaves. */
    long long stat_sync_partial_ok; /* Number of accepted PSYNC requests. */
    long long stat_sync_partial_err; /* Number of unaccepted PSYNC requests. */
    list *slowlog; /* SLOWLOG list of commands */
    long long slowlog_entry_id; /* SLOWLOG current entry ID */
    long long slowlog_log_slower_than; /* SLOWLOG time limit (to get logged) */
    unsigned long slowlog_max_len; /* SLOWLOG max number of items logged */
    struct malloc_stats cron_malloc_stats; /* sampled in serverCron(). */
    redisAtomic long long stat_net_input_bytes; /* Bytes read from network. */
    redisAtomic long long stat_net_output_bytes; /* Bytes written to network. */
    size_t stat_current_cow_bytes; /* Copy on write bytes while child is active. */
    monotime stat_current_cow_updated; /* Last update time of stat_current_cow_bytes */
    size_t stat_current_save_keys_processed; /* Processed keys while child is active. */
    size_t stat_current_save_keys_total; /* Number of keys when child started. */
    size_t stat_rdb_cow_bytes; /* Copy on write bytes during RDB saving. */
    size_t stat_aof_cow_bytes; /* Copy on write bytes during AOF rewrite. */
    size_t stat_module_cow_bytes; /* Copy on write bytes during module fork. */
    double stat_module_progress; /* Module save progress. */
    uint64_t stat_clients_type_memory[CLIENT_TYPE_COUNT]; /* Mem usage by type */
    long long stat_unexpected_error_replies;
    /* Number of unexpected (aof-loading, replica to master, etc.) error replies */
    long long stat_total_error_replies; /* Total number of issued error replies ( command + rejected errors ) */
    long long stat_dump_payload_sanitizations; /* Number deep dump payloads integrity validations. */
    long long stat_io_reads_processed; /* Number of read events processed by IO / Main threads */
    long long stat_io_writes_processed; /* Number of write events processed by IO / Main threads */
    redisAtomic long long stat_total_reads_processed; /* Total number of read events processed */
    redisAtomic long long stat_total_writes_processed; /* Total number of write events processed */
    /* The following two are used to track instantaneous metrics, like
     * number of operations per second, network traffic. */
    struct {
        long long last_sample_time; /* Timestamp of last sample in ms */
        long long last_sample_count; /* Count in last sample */
        long long samples[STATS_METRIC_SAMPLES];
        int idx;
    } inst_metric[STATS_METRIC_COUNT];

    /* Configuration */
    int verbosity; /* Loglevel in redis.conf */
    int maxidletime; /* Client timeout in seconds */
    int tcpkeepalive; /* Set SO_KEEPALIVE if non-zero. */
    int active_expire_enabled; /* Can be disabled for testing purposes. */
    int active_expire_effort; /* From 1 (default) to 10, active effort. */
    int active_defrag_enabled;
    int sanitize_dump_payload; /* Enables deep sanitization for ziplist and listpack in RDB and RESTORE. */
    int skip_checksum_validation; /* Disables checksum validateion for RDB and RESTORE payload. */
    int jemalloc_bg_thread; /* Enable jemalloc background thread */
    size_t active_defrag_ignore_bytes; /* minimum amount of fragmentation waste to start active defrag */
    int active_defrag_threshold_lower; /* minimum percentage of fragmentation to start active defrag */
    int active_defrag_threshold_upper; /* maximum percentage of fragmentation at which we use maximum effort */
    int active_defrag_cycle_min; /* minimal effort for defrag in CPU percentage */
    int active_defrag_cycle_max; /* maximal effort for defrag in CPU percentage */
    unsigned long active_defrag_max_scan_fields;
    /* maximum number of fields of set/hash/zset/list to process from within the main dict scan */
    size_t client_max_querybuf_len; /* Limit for client query buffer length */
    int dbnum; /* Total number of configured DBs */
    int supervised; /* 1 if supervised, 0 otherwise. */
    int supervised_mode; /* See SUPERVISED_* */
    int daemonize; /* True if running as a daemon */
    int set_proc_title; /* True if change proc title */
    char *proc_title_template; /* Process title template format */
    clientBufferLimitsConfig client_obuf_limits[CLIENT_TYPE_OBUF_COUNT];
    int pause_cron; /* Don't run cron tasks (debug) */
    /* AOF persistence */
    int aof_enabled; /* AOF configuration */
    int aof_state; /* AOF_(ON|OFF|WAIT_REWRITE) */
    int aof_fsync; /* Kind of fsync() policy */
    char *aof_filename; /* Name of the AOF file */
    int aof_no_fsync_on_rewrite; /* Don't fsync if a rewrite is in prog. */
    int aof_rewrite_perc; /* Rewrite AOF if % growth is > M and... */
    off_t aof_rewrite_min_size; /* the AOF file is at least N bytes. */
    off_t aof_rewrite_base_size; /* AOF size on latest startup or rewrite. */
    off_t aof_current_size; /* AOF current size. */
    off_t aof_fsync_offset; /* AOF offset which is already synced to disk. */
    int aof_flush_sleep; /* Micros to sleep before flush. (used by tests) */
    int aof_rewrite_scheduled; /* Rewrite once BGSAVE terminates. */
    list *aof_rewrite_buf_blocks; /* Hold changes during an AOF rewrite. */
    sds aof_buf; /* AOF buffer, written before entering the event loop */
    int aof_fd; /* File descriptor of currently selected AOF file */
    int aof_selected_db; /* Currently selected DB in AOF */
    time_t aof_flush_postponed_start; /* UNIX time of postponed AOF flush */
    time_t aof_last_fsync; /* UNIX time of last fsync() */
    time_t aof_rewrite_time_last; /* Time used by last AOF rewrite run. */
    time_t aof_rewrite_time_start; /* Current AOF rewrite start time. */
    int aof_lastbgrewrite_status; /* C_OK or C_ERR */
    unsigned long aof_delayed_fsync; /* delayed AOF fsync() counter */
    int aof_rewrite_incremental_fsync; /* fsync incrementally while aof rewriting? */
    int rdb_save_incremental_fsync; /* fsync incrementally while rdb saving? */
    int aof_last_write_status; /* C_OK or C_ERR */
    int aof_last_write_errno; /* Valid if aof write/fsync status is ERR */
    int aof_load_truncated; /* Don't stop on unexpected AOF EOF. */
    int aof_use_rdb_preamble; /* Use RDB preamble on AOF rewrites. */
    redisAtomic int aof_bio_fsync_status; /* Status of AOF fsync in bio job. */
    redisAtomic int aof_bio_fsync_errno; /* Errno of AOF fsync in bio job. */
    /* AOF pipes used to communicate between parent and child during rewrite. */
    int aof_pipe_write_data_to_child;
    int aof_pipe_read_data_from_parent;
    int aof_pipe_write_ack_to_parent;
    int aof_pipe_read_ack_from_child;
    int aof_pipe_write_ack_to_child;
    int aof_pipe_read_ack_from_parent;
    int aof_stop_sending_diff; /* If true stop sending accumulated diffs
                                      to child process. */
    sds aof_child_diff; /* AOF diff accumulator child side. */
    /* RDB persistence */
    long long dirty; /* Changes to DB from the last save */
    long long dirty_before_bgsave; /* Used to restore dirty on failed BGSAVE */
    struct saveparam *saveparams; /* Save points array for RDB */
    int saveparamslen; /* Number of saving points */
    char *rdb_filename; /* Name of RDB file */
    int rdb_compression; /* Use compression in RDB? */
    int rdb_checksum; /* Use RDB checksum? */
    int rdb_del_sync_files; /* Remove RDB files used only for SYNC if
                                       the instance does not use persistence. */
    time_t lastsave; /* Unix time of last successful save */
    time_t lastbgsave_try; /* Unix time of last attempted bgsave */
    time_t rdb_save_time_last; /* Time used by last RDB save run. */
    time_t rdb_save_time_start; /* Current RDB save start time. */
    int rdb_bgsave_scheduled; /* BGSAVE when possible if true. */
    int rdb_child_type; /* Type of save by active child. */
    int lastbgsave_status; /* C_OK or C_ERR */
    int stop_writes_on_bgsave_err; /* Don't allow writes if can't BGSAVE */
    int rdb_pipe_read; /* RDB pipe used to transfer the rdb data */
    /* to the parent process in diskless repl. */
    int rdb_child_exit_pipe; /* Used by the diskless parent allow child exit. */
    connection **rdb_pipe_conns; /* Connections which are currently the */
    int rdb_pipe_numconns; /* target of diskless rdb fork child. */
    int rdb_pipe_numconns_writing; /* Number of rdb conns with pending writes. */
    char *rdb_pipe_buff; /* In diskless replication, this buffer holds data */
    int rdb_pipe_bufflen; /* that was read from the the rdb pipe. */
    int rdb_key_save_delay; /* Delay in microseconds between keys while
                                     * writing the RDB. (for testings). negative
                                     * value means fractions of microsecons (on average). */
    int key_load_delay; /* Delay in microseconds between keys while
                                     * loading aof or rdb. (for testings). negative
                                     * value means fractions of microsecons (on average). */
    /* Pipe and data structures for child -> parent info sharing. */
    int child_info_pipe[2]; /* Pipe used to write the child_info_data. */
    int child_info_nread; /* Num of bytes of the last read from pipe */
    /* Propagation of commands in AOF / replication */
    redisOpArray also_propagate; /* Additional command to propagate. */
    int replication_allowed; /* Are we allowed to replicate? */
    /* Logging */
    char *logfile; /* Path of log file */
    int syslog_enabled; /* Is syslog enabled? */
    char *syslog_ident; /* Syslog ident */
    int syslog_facility; /* Syslog facility */
    int crashlog_enabled; /* Enable signal handler for crashlog.
                                     * disable for clean core dumps. */
    int memcheck_enabled; /* Enable memory check on crash. */
    int use_exit_on_panic; /* Use exit() on panic and assert rather than
                                     * abort(). useful for Valgrind. */
    /* Replication (master) */
    char replid[CONFIG_RUN_ID_SIZE + 1]; /* My current replication ID. */
    char replid2[CONFIG_RUN_ID_SIZE + 1]; /* replid inherited from master*/
    long long master_repl_offset; /* My current replication offset */
    long long second_replid_offset; /* Accept offsets up to this for replid2. */
    int slaveseldb; /* Last SELECTed DB in replication output */
    int repl_ping_slave_period; /* Master pings the slave every N seconds */
    char *repl_backlog; /* Replication backlog for partial syncs */
    long long repl_backlog_size; /* Backlog circular buffer size */
    long long repl_backlog_histlen; /* Backlog actual data length */
    long long repl_backlog_idx; /* Backlog circular buffer current offset,
                                       that is the next byte will'll write to.*/
    long long repl_backlog_off; /* Replication "master offset" of first
                                       byte in the replication backlog buffer.*/
    time_t repl_backlog_time_limit; /* Time without slaves after the backlog
                                       gets released. */
    time_t repl_no_slaves_since; /* We have no slaves since that time.
                                       Only valid if server.slaves len is 0. */
    int repl_min_slaves_to_write; /* Min number of slaves to write. */
    int repl_min_slaves_max_lag; /* Max lag of <count> slaves to write. */
    int repl_good_slaves_count; /* Number of slaves with lag <= max_lag. */
    int repl_diskless_sync; /* Master send RDB to slaves sockets directly. */
    int repl_diskless_load; /* Slave parse RDB directly from the socket.
                                     * see REPL_DISKLESS_LOAD_* enum */
    int repl_diskless_sync_delay; /* Delay to start a diskless repl BGSAVE. */
    /* Replication (slave) */
    char *masteruser; /* AUTH with this user and masterauth with master */
    sds masterauth; /* AUTH with this password with master */
    char *masterhost; /* Hostname of master */
    int masterport; /* Port of master */
    int repl_timeout; /* Timeout after N seconds of master idle */
    client *master; /* Client that is master for this slave */
    client *cached_master; /* Cached master to be reused for PSYNC. */
    int repl_syncio_timeout; /* Timeout for synchronous I/O calls */
    int repl_state; /* Replication status if the instance is a slave */
    off_t repl_transfer_size; /* Size of RDB to read from master during sync. */
    off_t repl_transfer_read; /* Amount of RDB read from master during sync. */
    off_t repl_transfer_last_fsync_off; /* Offset when we fsync-ed last time. */
    connection *repl_transfer_s; /* Slave -> Master SYNC connection */
    int repl_transfer_fd; /* Slave -> Master SYNC temp file descriptor */
    char *repl_transfer_tmpfile; /* Slave-> master SYNC temp file name */
    time_t repl_transfer_lastio; /* Unix time of the latest read, for timeout */
    int repl_serve_stale_data; /* Serve stale data when link is down? */
    int repl_slave_ro; /* Slave is read only? */
    int repl_slave_ignore_maxmemory; /* If true slaves do not evict. */
    time_t repl_down_since; /* Unix time at which link with master went down */
    int repl_disable_tcp_nodelay; /* Disable TCP_NODELAY after SYNC? */
    int slave_priority; /* Reported in INFO and used by Sentinel. */
    int replica_announced; /* If true, replica is announced by Sentinel */
    int slave_announce_port; /* Give the master this listening port. */
    char *slave_announce_ip; /* Give the master this ip address. */
    /* The following two fields is where we store master PSYNC replid/offset
     * while the PSYNC is in progress. At the end we'll copy the fields into
     * the server->master client structure. */
    char master_replid[CONFIG_RUN_ID_SIZE + 1]; /* Master PSYNC runid. */
    long long master_initial_offset; /* Master PSYNC offset. */
    int repl_slave_lazy_flush; /* Lazy FLUSHALL before loading DB? */
    /* Replication script cache. */
    dict *repl_scriptcache_dict; /* SHA1 all slaves are aware of. */
    list *repl_scriptcache_fifo; /* First in, first out LRU eviction. */
    unsigned int repl_scriptcache_size; /* Max number of elements. */
    /* Synchronous replication. */
    list *clients_waiting_acks; /* Clients waiting in WAIT command. */
    int get_ack_from_slaves; /* If true we send REPLCONF GETACK. */
    /* Limits */
    unsigned int maxclients; /* Max number of simultaneous clients */
    unsigned long long maxmemory; /* Max number of memory bytes to use */
    int maxmemory_policy; /* Policy for key eviction */
    int maxmemory_samples; /* Precision of random sampling */
    int maxmemory_eviction_tenacity; /* Aggressiveness of eviction processing */
    int lfu_log_factor; /* LFU logarithmic counter factor. */
    int lfu_decay_time; /* LFU counter decay factor. */
    long long proto_max_bulk_len; /* Protocol bulk length maximum size. */
    int oom_score_adj_base; /* Base oom_score_adj value, as observed on startup */
    int oom_score_adj_values[CONFIG_OOM_COUNT]; /* Linux oom_score_adj configuration */
    int oom_score_adj; /* If true, oom_score_adj is managed */
    int disable_thp; /* If true, disable THP by syscall */
    /* Blocked clients */
    unsigned int blocked_clients; /* # of clients executing a blocking cmd.*/
    unsigned int blocked_clients_by_type[BLOCKED_NUM];
    list *unblocked_clients; /* list of clients to unblock before next loop */
    list *ready_keys; /* List of readyList structures for BLPOP & co */
    /* Client side caching. */
    unsigned int tracking_clients; /* # of clients with tracking enabled.*/
    size_t tracking_table_max_keys; /* Max number of keys in tracking table. */
    list *tracking_pending_keys; /* tracking invalidation keys pending to flush */
    /* Sort parameters - qsort_r() is only available under BSD so we
     * have to take this state global, in order to pass it to sortCompare() */
    int sort_desc;
    int sort_alpha;
    int sort_bypattern;
    int sort_store;
    /* Zip structure config, see redis.conf for more information  */
    size_t hash_max_ziplist_entries;
    size_t hash_max_ziplist_value;
    size_t set_max_intset_entries;
    size_t zset_max_ziplist_entries;
    size_t zset_max_ziplist_value;
    size_t hll_sparse_max_bytes;
    size_t stream_node_max_bytes;
    long long stream_node_max_entries;
    /* List parameters */
    int list_max_ziplist_size;
    int list_compress_depth;
    /* time cache */
    redisAtomic time_t unixtime; /* Unix time sampled every cron cycle. */
    time_t timezone; /* Cached timezone. As set by tzset(). */
    int daylight_active; /* Currently in daylight saving time. */
    mstime_t mstime; /* 'unixtime' in milliseconds. */
    ustime_t ustime; /* 'unixtime' in microseconds. */
    size_t blocking_op_nesting; /* Nesting level of blocking operation, used to reset blocked_last_cron. */
    long long blocked_last_cron; /* Indicate the mstime of the last time we did cron jobs from a blocking operation */
    /* Pubsub */
    dict *pubsub_channels; /* Map channels to list of subscribed clients */
    dict *pubsub_patterns; /* A dict of pubsub_patterns */
    int notify_keyspace_events; /* Events to propagate via Pub/Sub. This is an
                                   xor of NOTIFY_... flags. */
    /* Cluster */
    int cluster_enabled; /* Is cluster enabled? */
    mstime_t cluster_node_timeout; /* Cluster node timeout. */
    char *cluster_configfile; /* Cluster auto-generated config file name. */
    struct clusterState *cluster; /* State of the cluster */
    int cluster_migration_barrier; /* Cluster replicas migration barrier. */
    int cluster_allow_replica_migration; /* Automatic replica migrations to orphaned masters and from empty masters */
    int cluster_slave_validity_factor; /* Slave max data age for failover. */
    int cluster_require_full_coverage; /* If true, put the cluster down if
                                          there is at least an uncovered slot.*/
    int cluster_slave_no_failover; /* Prevent slave from starting a failover
                                       if the master is in failure state. */
    char *cluster_announce_ip; /* IP address to announce on cluster bus. */
    int cluster_announce_port; /* base port to announce on cluster bus. */
    int cluster_announce_tls_port; /* TLS port to announce on cluster bus. */
    int cluster_announce_bus_port; /* bus port to announce on cluster bus. */
    int cluster_module_flags; /* Set of flags that Redis modules are able
                                      to set in order to suppress certain
                                      native Redis Cluster features. Check the
                                      REDISMODULE_CLUSTER_FLAG_*. */
    int cluster_allow_reads_when_down; /* Are reads allowed when the cluster
                                        is down? */
    int cluster_config_file_lock_fd; /* cluster config fd, will be flock */
    /* Scripting */
    lua_State *lua; /* The Lua interpreter. We use just one for all clients */
    client *lua_client; /* The "fake client" to query Redis from Lua */
    client *lua_caller; /* The client running EVAL right now, or NULL */
    char *lua_cur_script; /* SHA1 of the script currently running, or NULL */
    dict *lua_scripts; /* A dictionary of SHA1 -> Lua scripts */
    unsigned long long lua_scripts_mem; /* Cached scripts' memory + oh */
    mstime_t lua_time_limit; /* Script timeout in milliseconds */
    monotime lua_time_start; /* monotonic timer to detect timed-out script */
    mstime_t lua_time_snapshot; /* Snapshot of mstime when script is started */
    int lua_write_dirty; /* True if a write command was called during the
                             execution of the current script. */
    int lua_random_dirty; /* True if a random command was called during the
                             execution of the current script. */
    int lua_replicate_commands; /* True if we are doing single commands repl. */
    int lua_multi_emitted; /* True if we already propagated MULTI. */
    int lua_repl; /* Script replication flags for redis.set_repl(). */
    int lua_timedout; /* True if we reached the time limit for script
                             execution. */
    int lua_kill; /* Kill the script if true. */
    int lua_always_replicate_commands; /* Default replication type. */
    int lua_oom; /* OOM detected when script start? */
    /* Lazy free */
    int lazyfree_lazy_eviction;
    int lazyfree_lazy_expire;
    int lazyfree_lazy_server_del;
    int lazyfree_lazy_user_del;
    int lazyfree_lazy_user_flush;
    /* Latency monitor */
    long long latency_monitor_threshold;
    dict *latency_events;
    /* ACLs */
    char *acl_filename; /* ACL Users file. NULL if not configured. */
    unsigned long acllog_max_len; /* Maximum length of the ACL LOG list. */
    sds requirepass; /* Remember the cleartext password set with
                                     the old "requirepass" directive for
                                     backward compatibility with Redis <= 5. */
    int acl_pubsub_default; /* Default ACL pub/sub channels flag */
    /* Assert & bug reporting */
    int watchdog_period; /* Software watchdog period in ms. 0 = off */
    /* System hardware info */
    size_t system_memory_size; /* Total memory in system as reported by OS */
    /* TLS Configuration */
    int tls_cluster;
    int tls_replication;
    int tls_auth_clients;
    redisTLSContextConfig tls_ctx_config;
    /* cpu affinity */
    char *server_cpulist; /* cpu affinity list of redis server main/io thread. */
    char *bio_cpulist; /* cpu affinity list of bio thread. */
    char *aof_rewrite_cpulist; /* cpu affinity list of aof rewrite process. */
    char *bgsave_cpulist; /* cpu affinity list of bgsave process. */
    /* Sentinel config */
    struct sentinelConfig *sentinel_config; /* sentinel config to load at startup time. */
    /* Coordinate failover info */
    mstime_t failover_end_time; /* Deadline for failover command. */
    int force_failover; /* If true then failover will be foreced at the
                         * deadline, otherwise failover is aborted. */
    char *target_replica_host; /* Failover target host. If null during a
                                * failover then any replica can be used. */
    int target_replica_port; /* Failover target port */
    int failover_state; /* Failover state */
};
```



#### redisCommand

```c
//server.h

//所有命令的执行函数，保存在redisCommandTable[]中
struct redisCommand redisCommandTable[] = {
        {"get",getCommand,2,"read-only fast @string",0,NULL,1,1,1,0,0,0},
    .......
}


struct redisCommand {
    //命令名
    char *name;
    //命令执行函数
    redisCommandProc *proc;
    //参数个数（负数表示参数个数>=N）
    int arity;
    //命令的sflags属性字符串
    char *sflags; /* Flags as string representation, one char per flag. */
    //从sflags获得的整数值
    uint64_t flags; /* The actual flags, obtained from the 'sflags' field. */
    /* Use a function to determine keys arguments in a command line.
     * Used for Redis Cluster redirect. */
    //获取key参数的可选函数
    redisGetKeysProc *getkeys_proc;
    /* What keys should be loaded in background when calling this command? */
    //第一个key的位置（0表示没有key）
    int firstkey; /* The first argument that's a key (0 = no keys) */
    //最后一个key的位置
    int lastkey; /* The last argument that's a key */
    //第一个和最后一个key之间的跨步(k1 v1 , k2 v2 , k3 v3)
    int keystep; /* The step between first and last key */
    //命令从服务启动到现在的执行时间，执行次数，拒绝次数，执行失败次数
    long long microseconds, calls, rejected_calls, failed_calls;
    //命令id
    int id; /* Command ID. This is a progressive ID starting from 0 that
                   is assigned at runtime, and is used in order to check
                   ACLs. A connection is able to execute a given command if
                   the user associated to the connection has this command
                   bit set in the bitmap of allowed commands. */
};
```



#### client

* uint64_t id：客户端唯一ID

* int fd：客户端socket的文件描述符

* redisDb *db：客户端选择的数据库对象

* robj *name：客户端名称

* time_t lastinteraction：客户端上次与服务器交互的时间

* sds querybuf：输入缓冲区

* int argc：参数个数

* robj **argv：参数内容

* redisCommand *cmd：待执行的客户端命令

* list *reply：输出链表

* unsigned long long reply_bytes：输出链表中所有节点的存储空间总和

* size_t sentlen：已返回给客户端的字节数

* char buf[]:输出缓冲区

* int reqtype 请求类型 

  `PROTO_REQ_INLINE` 通常不涉及服务器和客户端的通讯。

  `PROTO_REQ_MULTIBULK` 用于处理RESP协议，即与客户端的通讯协议

  ```c
  //set f 1
  //RESP协议传输内容 
  //*3\r\n$3\r\nset\r\n$1\r\nf\r\n$1\r\n1\r\n
  ```

* int multibulklen 剩余待读取的数组参数的长度 这个即上面的 *3 代表数组长度为3

* long bulklen 每个数组元素的长度 这个代表$3 代表元素长度为3

```c
//server.h

//客户端协议请求类型
/* Client request types */
//单行请求 
#define PROTO_REQ_INLINE 1
//多批量请求
#define PROTO_REQ_MULTIBULK 2



typedef struct client {
    uint64_t id; /* Client incremental unique ID. */
    connection *conn;
    int resp; /* RESP protocol version. Can be 2 or 3. */
    redisDb *db; /* Pointer to currently SELECTed DB. */
    robj *name; /* As set by CLIENT SETNAME. */
    sds querybuf; /* Buffer we use to accumulate client queries. */
    size_t qb_pos; /* The position we have read in querybuf. */
    sds pending_querybuf; /* If this client is flagged as master, this buffer
                               represents the yet not applied portion of the
                               replication stream that we are receiving from
                               the master. */
    size_t querybuf_peak; /* Recent (100ms or more) peak of querybuf size. */
    int argc; /* Num of arguments of current command. */
    robj **argv; /* Arguments of current command. */
    int original_argc; /* Num of arguments of original command if arguments were rewritten. */
    robj **original_argv; /* Arguments of original command if arguments were rewritten. */
    size_t argv_len_sum; /* Sum of lengths of objects in argv list. */
    struct redisCommand *cmd, *lastcmd; /* Last command executed. */
    user *user; /* User associated with this connection. If the
                               user is set to NULL the connection can do
                               anything (admin). */
    int reqtype; /* Request protocol type: PROTO_REQ_* */
    int multibulklen; /* Number of multi bulk arguments left to read. */
    long bulklen; /* Length of bulk argument in multi bulk request. */
    list *reply; /* List of reply objects to send to the client. */
    unsigned long long reply_bytes; /* Tot bytes of objects in reply list. */
    list *deferred_reply_errors; /* Used for module thread safe contexts. */
    size_t sentlen; /* Amount of bytes already sent in the current
                               buffer or object being sent. */
    time_t ctime; /* Client creation time. */
    long duration; /* Current command duration. Used for measuring latency of blocking/non-blocking cmds */
    time_t lastinteraction; /* Time of the last interaction, used for timeout */
    time_t obuf_soft_limit_reached_time;
    uint64_t flags; /* Client flags: CLIENT_* macros. */
    int authenticated; /* Needed when the default user requires auth. */
    int replstate; /* Replication state if this is a slave. */
    int repl_put_online_on_ack; /* Install slave write handler on first ACK. */
    int repldbfd; /* Replication DB file descriptor. */
    off_t repldboff; /* Replication DB file offset. */
    off_t repldbsize; /* Replication DB file size. */
    sds replpreamble; /* Replication DB preamble. */
    long long read_reploff; /* Read replication offset if this is a master. */
    long long reploff; /* Applied replication offset if this is a master. */
    long long repl_ack_off; /* Replication ack offset, if this is a slave. */
    long long repl_ack_time; /* Replication ack time, if this is a slave. */
    long long repl_last_partial_write;
    /* The last time the server did a partial write from the RDB child pipe to this replica  */
    long long psync_initial_offset; /* FULLRESYNC reply offset other slaves
                                       copying this slave output buffer
                                       should use. */
    char replid[CONFIG_RUN_ID_SIZE + 1]; /* Master replication ID (if master). */
    int slave_listening_port; /* As configured with: REPLCONF listening-port */
    char *slave_addr; /* Optionally given by REPLCONF ip-address */
    int slave_capa; /* Slave capabilities: SLAVE_CAPA_* bitwise OR. */
    multiState mstate; /* MULTI/EXEC state */
    int btype; /* Type of blocking op if CLIENT_BLOCKED. */
    blockingState bpop; /* blocking state */
    long long woff; /* Last write global replication offset. */
    list *watched_keys; /* Keys WATCHED for MULTI/EXEC CAS */
    dict *pubsub_channels; /* channels a client is interested in (SUBSCRIBE) */
    list *pubsub_patterns; /* patterns a client is interested in (SUBSCRIBE) */
    sds peerid; /* Cached peer ID. */
    sds sockname; /* Cached connection target address. */
    listNode *client_list_node; /* list node in client list */
    listNode *paused_list_node; /* list node within the pause list */
    RedisModuleUserChangedFunc auth_callback; /* Module callback to execute
                                               * when the authenticated user
                                               * changes. */
    void *auth_callback_privdata; /* Private data that is passed when the auth
                                   * changed callback is executed. Opaque for
                                   * Redis Core. */
    void *auth_module; /* The module that owns the callback, which is used
                             * to disconnect the client if the module is
                             * unloaded for cleanup. Opaque for Redis Core.*/

    /* If this client is in tracking mode and this field is non zero,
     * invalidation messages for keys fetched by this client will be send to
     * the specified client ID. */
    uint64_t client_tracking_redirection;
    rax *client_tracking_prefixes; /* A dictionary of prefixes we are already
                                      subscribed to in BCAST mode, in the
                                      context of client side caching. */
    /* In clientsCronTrackClientsMemUsage() we track the memory usage of
     * each client and add it to the sum of all the clients of a given type,
     * however we need to remember what was the old contribution of each
     * client, and in which categoty the client was, in order to remove it
     * before adding it the new value. */
    uint64_t client_cron_last_memory_usage;
    int client_cron_last_memory_type;
    /* Response buffer */
    int bufpos;
    char buf[PROTO_REPLY_CHUNK_BYTES];
} client;
```



#### readQueryFromClient

```c
//networking.c

void readQueryFromClient(connection *conn) {
    //获取连接绑定的client数据
    client *c = connGetPrivateData(conn);
    int nread, readlen;
    size_t qblen;

    /* Check if we want to read from the client later when exiting from
     * the event loop. This is the case if threaded I/O is enabled. */
    //如果配置了threaded I/O 那么会有专门的线程来进行io读写操作 稍后读取 直接返回
    if (postponeClientRead(c)) return;

    //read次数+1
    /* Update total number of reads on server */
    atomicIncr(server.stat_total_reads_processed, 1);

    //设置读buffer的长度
    readlen = PROTO_IOBUF_LEN;
    /* If this is a multi bulk request, and we are processing a bulk reply
     * that is large enough, try to maximize the probability that the query
     * buffer contains exactly the SDS string representing the object, even
     * at the risk of requiring more read(2) calls. This way the function
     * processMultiBulkBuffer() can avoid copying buffers to create the
     * Redis Object representing the argument. */
    //如果是批量命令 尝试扩大读取buffer的容量  
    if (c->reqtype == PROTO_REQ_MULTIBULK && c->multibulklen && c->bulklen != -1
        && c->bulklen >= PROTO_MBULK_BIG_ARG)
    {
        ssize_t remaining = (size_t)(c->bulklen+2)-sdslen(c->querybuf);

        /* Note that the 'remaining' variable may be zero in some edge case,
         * for example once we resume a blocked client after CLIENT PAUSE. */
        if (remaining > 0 && remaining < readlen) readlen = remaining;
    }

    
    qblen = sdslen(c->querybuf);
    //更新最近读取buffer的峰值
    if (c->querybuf_peak < qblen) c->querybuf_peak = qblen;
    //buffer扩容
    c->querybuf = sdsMakeRoomFor(c->querybuf, readlen);
    //从连接中读取readlen长度的数据到querybuf中
    nread = connRead(c->conn, c->querybuf+qblen, readlen);
    if (nread == -1) {
        //连接保持 直接返回
        if (connGetState(conn) == CONN_STATE_CONNECTED) {
            return;
        } else {
            //释放连接 
            serverLog(LL_VERBOSE, "Reading from client: %s",connGetLastError(c->conn));
            freeClientAsync(c);
            return;
        }
    } else if (nread == 0) {
        //连接关闭 释放连接
        serverLog(LL_VERBOSE, "Client closed connection");
        freeClientAsync(c);
        return;
    } else if (c->flags & CLIENT_MASTER) {
        //如果当前client是来自主节点
        /* Append the query buffer to the pending (not applied) buffer
         * of the master. We'll use this buffer later in order to have a
         * copy of the string applied by the last command executed. */
        //将querybuf读到的数据加到pending
        c->pending_querybuf = sdscatlen(c->pending_querybuf,
                                        c->querybuf+qblen,nread);
    }

    //querybuf长度增加
    sdsIncrLen(c->querybuf,nread);
    //更新最后一次交互时间
    c->lastinteraction = server.unixtime;
    //如果当前client是来自主节点 复制偏移量+nread
    if (c->flags & CLIENT_MASTER) c->read_reploff += nread;
    //读取数据量+nread
    atomicIncr(server.stat_net_input_bytes, nread);
    //如果读取数据量超过限制
    if (sdslen(c->querybuf) > server.client_max_querybuf_len) {
        //告警并且释放client
        sds ci = catClientInfoString(sdsempty(),c), bytes = sdsempty();

        bytes = sdscatrepr(bytes,c->querybuf,64);
        serverLog(LL_WARNING,"Closing client that reached max query buffer length: %s (qbuf initial bytes: %s)", ci, bytes);
        sdsfree(ci);
        sdsfree(bytes);
        freeClientAsync(c);
        return;
    }

    //处理querybuf中读到的新数据
    /* There is more data in the client input buffer, continue parsing it
     * in case to check if there is a full command to execute. */
     processInputBuffer(c);
}
```



#### processInputBuffer

```c
//networking.c


/* This function is called every time, in the client structure 'c', there is
 * more query buffer to process, because we read more data from the socket
 * or because a client was blocked and later reactivated, so there could be
 * pending query buffer, already representing a full command, to process. */
void processInputBuffer(client *c) {
    /* Keep processing while there is something in the input buffer */
    while(c->qb_pos < sdslen(c->querybuf)) {
        /* Immediately abort if the client is in the middle of something. */
        // // 如果客户端正在阻塞、等待服务器回复，则此时客户端的请求不处理
        // 比如，客户端在执行 BRPUSH 阻塞命令
        if (c->flags & CLIENT_BLOCKED) break;

        /* Don't process more buffers from clients that have already pending
         * commands to execute in c->argv. */
        // c->argv 中已经有待处理的指令，当前这个就不处理了
        if (c->flags & CLIENT_PENDING_COMMAND) break;

        /* Don't process input from the master while there is a busy script
         * condition on the slave. We want just to accumulate the replication
         * stream (instead of replying -BUSY like we do with other clients) and
         * later resume the processing. */
        if (server.lua_timedout && c->flags & CLIENT_MASTER) break;

        /* CLIENT_CLOSE_AFTER_REPLY closes the connection once the reply is
         * written to the client. Make sure to not let the reply grow after
         * this flag has been set (i.e. don't process more commands).
         *
         * The same applies for clients we want to terminate ASAP. */
        //由于设置了标志位 CLIENT_CLOSE_AFTER_REPLY | CLIENT_CLOSE_ASAP 
        //就不应再继续处理客户端的请求，而是尽快地关闭客户端
        if (c->flags & (CLIENT_CLOSE_AFTER_REPLY|CLIENT_CLOSE_ASAP)) break;

        /* Determine request type when unknown. */
        //根据第一个字节是否是 * 来确定是不是RESP协议
        if (!c->reqtype) {
            if (c->querybuf[c->qb_pos] == '*') {
                c->reqtype = PROTO_REQ_MULTIBULK;
            } else {
                c->reqtype = PROTO_REQ_INLINE;
            }
        }

        if (c->reqtype == PROTO_REQ_INLINE) {
            if (processInlineBuffer(c) != C_OK) break;
            /* If the Gopher mode and we got zero or one argument, process
             * the request in Gopher mode. To avoid data race, Redis won't
             * support Gopher if enable io threads to read queries. */
            if (server.gopher_enabled && !server.io_threads_do_reads &&
                ((c->argc == 1 && ((char*)(c->argv[0]->ptr))[0] == '/') ||
                  c->argc == 0))
            {
                processGopherRequest(c);
                resetClient(c);
                c->flags |= CLIENT_CLOSE_AFTER_REPLY;
                break;
            }
        } else if (c->reqtype == PROTO_REQ_MULTIBULK) {
            //解析RESP协议
            if (processMultibulkBuffer(c) != C_OK) break;
        } else {
            serverPanic("Unknown request type");
        }

        //执行指令
        /* Multibulk processing could see a <= 0 length. */
        if (c->argc == 0) {
            resetClient(c);
        } else {
            //如果开启了子线程，那么不能就地执行指令，
            //如果设置了稍后读取 那么执行也设置稍后执行
            /* If we are in the context of an I/O thread, we can't really
             * execute the command here. All we can do is to flag the client
             * as one that needs to process the command. */
            if (c->flags & CLIENT_PENDING_READ) {
                c->flags |= CLIENT_PENDING_COMMAND;
                break;
            }

            //执行指令
            /* We are finally ready to execute the command. */
            if (processCommandAndResetClient(c) == C_ERR) {
                /* If the client is no longer valid, we avoid exiting this
                 * loop and trimming the client buffer later. So we return
                 * ASAP in that case. */
                return;
            }
        }
    }

    /* Trim to pos */
    if (c->qb_pos) {
        //sdsrange 裁剪掉已读数据
        sdsrange(c->querybuf,c->qb_pos,-1);
        c->qb_pos = 0;
    }
}
```





#### processMultibulkBuffer

```c
//networking.c

/* Process the query buffer for client 'c', setting up the client argument
 * vector for command execution. Returns C_OK if after running the function
 * the client has a well-formed ready to be processed command, otherwise
 * C_ERR if there is still to read more buffer to get the full command.
 * The function also returns C_ERR when there is a protocol error: in such a
 * case the client structure is setup to reply with the error and close
 * the connection.
 *
 * This function is called if processInputBuffer() detects that the next
 * command is in RESP format, so the first byte in the command is found
 * to be '*'. Otherwise for inline commands processInlineBuffer() is called. */
int processMultibulkBuffer(client *c) {
    char *newline = NULL;
    int ok;
    long long ll;

    //如果数组长度还没解析
    if (c->multibulklen == 0) {
        /* The client should have been reset */
        serverAssertWithInfo(c,NULL,c->argc == 0);

        //尝试解析数据长度
        //从querybuf中找到第一个为\r的字符
        /* Multi bulk length cannot be read without a \r\n */
        newline = strchr(c->querybuf+c->qb_pos,'\r');
        if (newline == NULL) {
            if (sdslen(c->querybuf)-c->qb_pos > PROTO_INLINE_MAX_SIZE) {
                addReplyError(c,"Protocol error: too big mbulk count string");
                setProtocolError("too big mbulk count string",c);
            }
            return C_ERR;
        }
		
        //计算是否包含\n字符
        /* Buffer should also contain \n */
        if (newline-(c->querybuf+c->qb_pos) > (ssize_t)(sdslen(c->querybuf)-c->qb_pos-2))
            return C_ERR;

        /* We know for sure there is a whole line since newline != NULL,
         * so go ahead and find out the multi bulk length. */
        serverAssertWithInfo(c,NULL,c->querybuf[c->qb_pos] == '*');
        //尝试将当前行的转换为long数字 +1是因为 *3\r\n 的*号
        ok = string2ll(c->querybuf+1+c->qb_pos,newline-(c->querybuf+1+c->qb_pos),&ll);
        if (!ok || ll > 1024*1024) {
            //回复错误信息
            addReplyError(c,"Protocol error: invalid multibulk length");
            setProtocolError("invalid mbulk count",c);
            return C_ERR;
        } else if (ll > 10 && authRequired(c)) {
            addReplyError(c, "Protocol error: unauthenticated multibulk length");
            setProtocolError("unauth mbulk count", c);
            return C_ERR;
        }
	    //设置下一个读取的位置
        c->qb_pos = (newline-c->querybuf)+2;

        if (ll <= 0) return C_OK;

        //设置数组长度
        c->multibulklen = ll;
		
        //清空参数数组
        /* Setup argv array on client structure */
        if (c->argv) zfree(c->argv);
        c->argv = zmalloc(sizeof(robj*)*c->multibulklen);
        c->argv_len_sum = 0;
    }

    //如果数组长度不为空 那么开始读取参数
    serverAssertWithInfo(c,NULL,c->multibulklen > 0);
    while(c->multibulklen) {
        //如果当前字符长度为空 先解析字符长度
        /* Read bulk length if unknown */
        if (c->bulklen == -1) {
            newline = strchr(c->querybuf+c->qb_pos,'\r');
            if (newline == NULL) {
                if (sdslen(c->querybuf)-c->qb_pos > PROTO_INLINE_MAX_SIZE) {
                    addReplyError(c,
                        "Protocol error: too big bulk count string");
                    setProtocolError("too big bulk count string",c);
                    return C_ERR;
                }
                break;
            }

            /* Buffer should also contain \n */
            if (newline-(c->querybuf+c->qb_pos) > (ssize_t)(sdslen(c->querybuf)-c->qb_pos-2))
                break;
		   //如果不是以 $3\r\n 这样的字符参数长度
            if (c->querybuf[c->qb_pos] != '$') {
                //返回错误信息
                addReplyErrorFormat(c,
                    "Protocol error: expected '$', got '%c'",
                    c->querybuf[c->qb_pos]);
                setProtocolError("expected $ but got something else",c);
                return C_ERR;
            }
		   //将 $3\r\n 转换成数字
            ok = string2ll(c->querybuf+c->qb_pos+1,newline-(c->querybuf+c->qb_pos+1),&ll);
            if (!ok || ll < 0 ||
                (!(c->flags & CLIENT_MASTER) && ll > server.proto_max_bulk_len)) {
                addReplyError(c,"Protocol error: invalid bulk length");
                setProtocolError("invalid bulk length",c);
                return C_ERR;
            } else if (ll > 16384 && authRequired(c)) {
                addReplyError(c, "Protocol error: unauthenticated bulk length");
                setProtocolError("unauth bulk length", c);
                return C_ERR;
            }
		   //设置新的读取位置
            c->qb_pos = newline-c->querybuf+2;
            
            //如果读到了一个超大的字符参数
            if (ll >= PROTO_MBULK_BIG_ARG) {
                /* If we are going to read a large object from network
                 * try to make it likely that it will start at c->querybuf
                 * boundary so that we can optimize object creation
                 * avoiding a large copy of data.
                 *
                 * But only when the data we have not parsed is less than
                 * or equal to ll+2. If the data length is greater than
                 * ll+2, trimming querybuf is just a waste of time, because
                 * at this time the querybuf contains not only our bulk. */
                //剩余不足一个完整的字符参数 
                if (sdslen(c->querybuf)-c->qb_pos <= (size_t)ll+2) {
                    //裁剪读过的字符
                    sdsrange(c->querybuf,c->qb_pos,-1);
                    c->qb_pos = 0;
                    /* Hint the sds library about the amount of bytes this string is
                     * going to contain. */
                    //扩容
                    c->querybuf = sdsMakeRoomFor(c->querybuf,ll+2-sdslen(c->querybuf));
                }
            }
            //设置字符参数的长度
            c->bulklen = ll;
        }

        //如果剩余待读取的长度不足以一个完整的字符参数
        /* Read bulk argument */
        if (sdslen(c->querybuf)-c->qb_pos < (size_t)(c->bulklen+2)) {
            /* Not enough data (+2 == trailing \r\n) */
            break;
        } else {
            /* Optimization: if the buffer contains JUST our bulk element
             * instead of creating a new object by *copying* the sds we
             * just use the current sds string. */
            //如果是一个超大的字符参数 并且整个querybuf中全部完整的包含了当前这个字符参数
            if (c->qb_pos == 0 &&
                c->bulklen >= PROTO_MBULK_BIG_ARG &&
                sdslen(c->querybuf) == (size_t)(c->bulklen+2))
            {
                //那么直接使用querybuf设置argv
                //argc++
                c->argv[c->argc++] = createObject(OBJ_STRING,c->querybuf);
                //argv的总长度+
                c->argv_len_sum += c->bulklen;
                //去掉querybuf的\r\n的长度
                sdsIncrLen(c->querybuf,-2); /* remove CRLF */
                /* Assume that if we saw a fat argument we'll see another one
                 * likely... */
                //清空原有的querybuf 设置新值
                c->querybuf = sdsnewlen(SDS_NOINIT,c->bulklen+2);
                sdsclear(c->querybuf);
            } else {
                //从qb_pos位置读取bulklen的字符参数长度 设置到argv
                c->argv[c->argc++] =
                    createStringObject(c->querybuf+c->qb_pos,c->bulklen);
                c->argv_len_sum += c->bulklen;
                //更新qb_pos
                c->qb_pos += c->bulklen+2;
            }
            //重设bulklen
            c->bulklen = -1;
            //数组长度-1
            c->multibulklen--;
        }
    }
	
    //如果数组长度减为0 那么成功读取完了
    /* We're done when c->multibulk == 0 */
    if (c->multibulklen == 0) return C_OK;

    /* Still not ready to process the command */
    return C_ERR;
}
```



#### processCommandAndResetClient 

```c
//networking.c

/* This function calls processCommand(), but also performs a few sub tasks
 * for the client that are useful in that context:
 *
 * 1. It sets the current client to the client 'c'.
 * 2. calls commandProcessed() if the command was handled.
 *
 * The function returns C_ERR in case the client was freed as a side effect
 * of processing the command, otherwise C_OK is returned. */
//执行命令 重置客户端
int processCommandAndResetClient(client *c) {
    int deadclient = 0;
    client *old_client = server.current_client;
    server.current_client = c;
    //执行命令
    if (processCommand(c) == C_OK) {
        commandProcessed(c);
    }
    if (server.current_client == NULL) deadclient = 1;
    /*
     * Restore the old client, this is needed because when a script
     * times out, we will get into this code from processEventsWhileBlocked.
     * Which will cause to set the server.current_client. If not restored
     * we will return 1 to our caller which will falsely indicate the client
     * is dead and will stop reading from its buffer.
     */
    server.current_client = old_client;
    /* performEvictions may flush slave output buffers. This may
     * result in a slave, that may be the active client, to be
     * freed. */
    return deadclient ? C_ERR : C_OK;
}
```



#### processCommand

```c
//networking.c

/* If this function gets called we already read a whole
 * command, arguments are in the client argv/argc fields.
 * processCommand() execute the command or prepare the
 * server for a bulk read from the client.
 *
 * If C_OK is returned the client is still alive and valid and
 * other operations can be performed by the caller. Otherwise
 * if C_ERR is returned the client was destroyed (i.e. after QUIT). */
int processCommand(client *c) {
    if (!server.lua_timedout) {
        /* Both EXEC and EVAL call call() directly so there should be
         * no way in_exec or in_eval or propagate_in_transaction is 1.
         * That is unless lua_timedout, in which case client may run
         * some commands. */
        serverAssert(!server.propagate_in_transaction);
        serverAssert(!server.in_exec);
        serverAssert(!server.in_eval);
    }

    moduleCallCommandFilters(c);

    /* The QUIT command is handled separately. Normal command procs will
     * go through checking for replication and QUIT will cause trouble
     * when FORCE_REPLICATION is enabled and would be implemented in
     * a regular command proc. */
    //如果是quit命令 快速回复并且设置关闭客户端
    if (!strcasecmp(c->argv[0]->ptr,"quit")) {
        addReply(c,shared.ok);
        c->flags |= CLIENT_CLOSE_AFTER_REPLY;
        return C_ERR;
    }

    //找到要执行的命令
    /* Now lookup the command and check ASAP about trivial error conditions
     * such as wrong arity, bad command name and so forth. */
    c->cmd = c->lastcmd = lookupCommand(c->argv[0]->ptr);
    //如果没找到 报错未知命令
    if (!c->cmd) {
        sds args = sdsempty();
        int i;
        for (i=1; i < c->argc && sdslen(args) < 128; i++)
            args = sdscatprintf(args, "`%.*s`, ", 128-(int)sdslen(args), (char*)c->argv[i]->ptr);
        rejectCommandFormat(c,"unknown command `%s`, with args beginning with: %s",
            (char*)c->argv[0]->ptr, args);
        sdsfree(args);
        return C_OK;
        //否则校验命令所需参数个数和实际参数个数
    } else if ((c->cmd->arity > 0 && c->cmd->arity != c->argc) ||
               (c->argc < -c->cmd->arity)) {
        rejectCommandFormat(c,"wrong number of arguments for '%s' command",
            c->cmd->name);
        return C_OK;
    }

    int is_read_command = (c->cmd->flags & CMD_READONLY) ||
                           (c->cmd->proc == execCommand && (c->mstate.cmd_flags & CMD_READONLY));
    int is_write_command = (c->cmd->flags & CMD_WRITE) ||
                           (c->cmd->proc == execCommand && (c->mstate.cmd_flags & CMD_WRITE));
    int is_denyoom_command = (c->cmd->flags & CMD_DENYOOM) ||
                             (c->cmd->proc == execCommand && (c->mstate.cmd_flags & CMD_DENYOOM));
    int is_denystale_command = !(c->cmd->flags & CMD_STALE) ||
                               (c->cmd->proc == execCommand && (c->mstate.cmd_inv_flags & CMD_STALE));
    int is_denyloading_command = !(c->cmd->flags & CMD_LOADING) ||
                                 (c->cmd->proc == execCommand && (c->mstate.cmd_inv_flags & CMD_LOADING));
    int is_may_replicate_command = (c->cmd->flags & (CMD_WRITE | CMD_MAY_REPLICATE)) ||
                                   (c->cmd->proc == execCommand && (c->mstate.cmd_flags & (CMD_WRITE | CMD_MAY_REPLICATE)));

    //auth控制
    if (authRequired(c)) {
        /* AUTH and HELLO and no auth commands are valid even in
         * non-authenticated state. */
        if (!(c->cmd->flags & CMD_NO_AUTH)) {
            rejectCommand(c,shared.noautherr);
            return C_OK;
        }
    }

    /* Check if the user can run this command according to the current
     * ACLs. */
    //acl控制
    int acl_errpos;
    int acl_retval = ACLCheckAllPerm(c,&acl_errpos);
    if (acl_retval != ACL_OK) {
        addACLLogEntry(c,acl_retval,acl_errpos,NULL);
        switch (acl_retval) {
        case ACL_DENIED_CMD:
            rejectCommandFormat(c,
                "-NOPERM this user has no permissions to run "
                "the '%s' command or its subcommand", c->cmd->name);
            break;
        case ACL_DENIED_KEY:
            rejectCommandFormat(c,
                "-NOPERM this user has no permissions to access "
                "one of the keys used as arguments");
            break;
        case ACL_DENIED_CHANNEL:
            rejectCommandFormat(c,
                "-NOPERM this user has no permissions to access "
                "one of the channels used as arguments");
            break;
        default:
            rejectCommandFormat(c, "no permission");
            break;
        }
        return C_OK;
    }

    /* If cluster is enabled perform the cluster redirection here.
     * However we don't perform the redirection if:
     * 1) The sender of this command is our master.
     * 2) The command has no key arguments. */
    //集群处理命令 @todo
    if (server.cluster_enabled &&
        !(c->flags & CLIENT_MASTER) &&
        !(c->flags & CLIENT_LUA &&
          server.lua_caller->flags & CLIENT_MASTER) &&
        !(!cmdHasMovableKeys(c->cmd) && c->cmd->firstkey == 0 &&
          c->cmd->proc != execCommand))
    {
        int hashslot;
        int error_code;
        clusterNode *n = getNodeByQuery(c,c->cmd,c->argv,c->argc,
                                        &hashslot,&error_code);
        if (n == NULL || n != server.cluster->myself) {
            if (c->cmd->proc == execCommand) {
                discardTransaction(c);
            } else {
                flagTransaction(c);
            }
            clusterRedirectClient(c,n,hashslot,error_code);
            c->cmd->rejected_calls++;
            return C_OK;
        }
    }

    /* Handle the maxmemory directive.
     *
     * Note that we do not want to reclaim memory if we are here re-entering
     * the event loop since there is a busy Lua script running in timeout
     * condition, to avoid mixing the propagation of scripts with the
     * propagation of DELs due to eviction. */
    if (server.maxmemory && !server.lua_timedout) {
        int out_of_memory = (performEvictions() == EVICT_FAIL);

        /* performEvictions may evict keys, so we need flush pending tracking
         * invalidation keys. If we don't do this, we may get an invalidation
         * message after we perform operation on the key, where in fact this
         * message belongs to the old value of the key before it gets evicted.*/
        trackingHandlePendingKeyInvalidations();

        /* performEvictions may flush slave output buffers. This may result
         * in a slave, that may be the active client, to be freed. */
        if (server.current_client == NULL) return C_ERR;

        int reject_cmd_on_oom = is_denyoom_command;
        /* If client is in MULTI/EXEC context, queuing may consume an unlimited
         * amount of memory, so we want to stop that.
         * However, we never want to reject DISCARD, or even EXEC (unless it
         * contains denied commands, in which case is_denyoom_command is already
         * set. */
        if (c->flags & CLIENT_MULTI &&
            c->cmd->proc != execCommand &&
            c->cmd->proc != discardCommand &&
            c->cmd->proc != resetCommand) {
            reject_cmd_on_oom = 1;
        }

        if (out_of_memory && reject_cmd_on_oom) {
            rejectCommand(c, shared.oomerr);
            return C_OK;
        }

        /* Save out_of_memory result at script start, otherwise if we check OOM
         * until first write within script, memory used by lua stack and
         * arguments might interfere. */
        if (c->cmd->proc == evalCommand || c->cmd->proc == evalShaCommand) {
            server.lua_oom = out_of_memory;
        }
    }

    /* Make sure to use a reasonable amount of memory for client side
     * caching metadata. */
    if (server.tracking_clients) trackingLimitUsedSlots();

    /* Don't accept write commands if there are problems persisting on disk
     * and if this is a master instance. */
    int deny_write_type = writeCommandsDeniedByDiskError();
    if (deny_write_type != DISK_ERROR_TYPE_NONE &&
        server.masterhost == NULL &&
        (is_write_command ||c->cmd->proc == pingCommand))
    {
        if (deny_write_type == DISK_ERROR_TYPE_RDB)
            rejectCommand(c, shared.bgsaveerr);
        else
            rejectCommandFormat(c,
                "-MISCONF Errors writing to the AOF file: %s",
                strerror(server.aof_last_write_errno));
        return C_OK;
    }

    /* Don't accept write commands if there are not enough good slaves and
     * user configured the min-slaves-to-write option. */
    if (server.masterhost == NULL &&
        server.repl_min_slaves_to_write &&
        server.repl_min_slaves_max_lag &&
        is_write_command &&
        server.repl_good_slaves_count < server.repl_min_slaves_to_write)
    {
        rejectCommand(c, shared.noreplicaserr);
        return C_OK;
    }

    /* Don't accept write commands if this is a read only slave. But
     * accept write commands if this is our master. */
    if (server.masterhost && server.repl_slave_ro &&
        !(c->flags & CLIENT_MASTER) &&
        is_write_command)
    {
        rejectCommand(c, shared.roslaveerr);
        return C_OK;
    }

    /* Only allow a subset of commands in the context of Pub/Sub if the
     * connection is in RESP2 mode. With RESP3 there are no limits. */
    if ((c->flags & CLIENT_PUBSUB && c->resp == 2) &&
        c->cmd->proc != pingCommand &&
        c->cmd->proc != subscribeCommand &&
        c->cmd->proc != unsubscribeCommand &&
        c->cmd->proc != psubscribeCommand &&
        c->cmd->proc != punsubscribeCommand &&
        c->cmd->proc != resetCommand) {
        rejectCommandFormat(c,
            "Can't execute '%s': only (P)SUBSCRIBE / "
            "(P)UNSUBSCRIBE / PING / QUIT / RESET are allowed in this context",
            c->cmd->name);
        return C_OK;
    }

    /* Only allow commands with flag "t", such as INFO, SLAVEOF and so on,
     * when slave-serve-stale-data is no and we are a slave with a broken
     * link with master. */
    if (server.masterhost && server.repl_state != REPL_STATE_CONNECTED &&
        server.repl_serve_stale_data == 0 &&
        is_denystale_command)
    {
        rejectCommand(c, shared.masterdownerr);
        return C_OK;
    }

    /* Loading DB? Return an error if the command has not the
     * CMD_LOADING flag. */
    if (server.loading && is_denyloading_command) {
        rejectCommand(c, shared.loadingerr);
        return C_OK;
    }

    /* Lua script too slow? Only allow a limited number of commands.
     * Note that we need to allow the transactions commands, otherwise clients
     * sending a transaction with pipelining without error checking, may have
     * the MULTI plus a few initial commands refused, then the timeout
     * condition resolves, and the bottom-half of the transaction gets
     * executed, see Github PR #7022. */
    if (server.lua_timedout &&
          c->cmd->proc != authCommand &&
          c->cmd->proc != helloCommand &&
          c->cmd->proc != replconfCommand &&
          c->cmd->proc != multiCommand &&
          c->cmd->proc != discardCommand &&
          c->cmd->proc != watchCommand &&
          c->cmd->proc != unwatchCommand &&
          c->cmd->proc != resetCommand &&
        !(c->cmd->proc == shutdownCommand &&
          c->argc == 2 &&
          tolower(((char*)c->argv[1]->ptr)[0]) == 'n') &&
        !(c->cmd->proc == scriptCommand &&
          c->argc == 2 &&
          tolower(((char*)c->argv[1]->ptr)[0]) == 'k'))
    {
        rejectCommand(c, shared.slowscripterr);
        return C_OK;
    }

    /* Prevent a replica from sending commands that access the keyspace.
     * The main objective here is to prevent abuse of client pause check
     * from which replicas are exempt. */
    if ((c->flags & CLIENT_SLAVE) && (is_may_replicate_command || is_write_command || is_read_command)) {
        rejectCommandFormat(c, "Replica can't interract with the keyspace");
        return C_OK;
    }

    /* If the server is paused, block the client until
     * the pause has ended. Replicas are never paused. */
    if (!(c->flags & CLIENT_SLAVE) && 
        ((server.client_pause_type == CLIENT_PAUSE_ALL) ||
        (server.client_pause_type == CLIENT_PAUSE_WRITE && is_may_replicate_command)))
    {
        c->bpop.timeout = 0;
        blockClient(c,BLOCKED_PAUSE);
        return C_OK;       
    }

    //如果是处于事务中 
    /* Exec the command */
    if (c->flags & CLIENT_MULTI &&
        c->cmd->proc != execCommand && c->cmd->proc != discardCommand &&
        c->cmd->proc != multiCommand && c->cmd->proc != watchCommand &&
        c->cmd->proc != resetCommand)
    {
        //加入事务命令队列
        queueMultiCommand(c);
        addReply(c,shared.queued);
    } else {
        //直接调用
        call(c,CMD_CALL_FULL);
        c->woff = server.master_repl_offset;
        if (listLength(server.ready_keys))
            handleClientsBlockedOnKeys();
    }

    return C_OK;
}
```



#### call

```c
//server.c


//@todo 看完事务和集群再来看一下这里的命令


/* Call() is the core of Redis execution of a command.
 *
 * The following flags can be passed:
 * CMD_CALL_NONE        No flags.
 * CMD_CALL_SLOWLOG     Check command speed and log in the slow log if needed.
 * CMD_CALL_STATS       Populate command stats.
 * CMD_CALL_PROPAGATE_AOF   Append command to AOF if it modified the dataset
 *                          or if the client flags are forcing propagation.
 * CMD_CALL_PROPAGATE_REPL  Send command to slaves if it modified the dataset
 *                          or if the client flags are forcing propagation.
 * CMD_CALL_PROPAGATE   Alias for PROPAGATE_AOF|PROPAGATE_REPL.
 * CMD_CALL_FULL        Alias for SLOWLOG|STATS|PROPAGATE.
 *
 * The exact propagation behavior depends on the client flags.
 * Specifically:
 *
 * 1. If the client flags CLIENT_FORCE_AOF or CLIENT_FORCE_REPL are set
 *    and assuming the corresponding CMD_CALL_PROPAGATE_AOF/REPL is set
 *    in the call flags, then the command is propagated even if the
 *    dataset was not affected by the command.
 * 2. If the client flags CLIENT_PREVENT_REPL_PROP or CLIENT_PREVENT_AOF_PROP
 *    are set, the propagation into AOF or to slaves is not performed even
 *    if the command modified the dataset.
 *
 * Note that regardless of the client flags, if CMD_CALL_PROPAGATE_AOF
 * or CMD_CALL_PROPAGATE_REPL are not set, then respectively AOF or
 * slaves propagation will never occur.
 *
 * Client flags are modified by the implementation of a given command
 * using the following API:
 *
 * forceCommandPropagation(client *c, int flags);
 * preventCommandPropagation(client *c);
 * preventCommandAOF(client *c);
 * preventCommandReplication(client *c);
 *
 */
void call(client *c, int flags) {
    long long dirty;
    int client_old_flags = c->flags;
    struct redisCommand *real_cmd = c->cmd;
    static long long prev_err_count;

    /* Initialization: clear the flags that must be set by the command on
     * demand, and initialize the array for additional commands propagation. */
    c->flags &= ~(CLIENT_FORCE_AOF|CLIENT_FORCE_REPL|CLIENT_PREVENT_PROP);
    redisOpArray prev_also_propagate = server.also_propagate;
    redisOpArrayInit(&server.also_propagate);

    /* Call the command. */
    dirty = server.dirty;
    prev_err_count = server.stat_total_error_replies;

    const long long call_timer = ustime();

    /* Update cache time, in case we have nested calls we want to
     * update only on the first call*/
    if (server.fixed_time_expire++ == 0) {
        updateCachedTimeWithUs(0,call_timer);
    }

    monotime monotonic_start = 0;
    if (monotonicGetType() == MONOTONIC_CLOCK_HW)
        monotonic_start = getMonotonicUs();

    //实际调用命令处理函数的地方
    server.in_nested_call++;
    c->cmd->proc(c);
    server.in_nested_call--;

    /* In order to avoid performance implication due to querying the clock using a system call 3 times,
     * we use a monotonic clock, when we are sure its cost is very low, and fall back to non-monotonic call otherwise. */
    ustime_t duration;
    if (monotonicGetType() == MONOTONIC_CLOCK_HW)
        duration = getMonotonicUs() - monotonic_start;
    else
        duration = ustime() - call_timer;

    c->duration = duration;
    dirty = server.dirty-dirty;
    if (dirty < 0) dirty = 0;

    /* Update failed command calls if required.
     * We leverage a static variable (prev_err_count) to retain
     * the counter across nested function calls and avoid logging
     * the same error twice. */
    if ((server.stat_total_error_replies - prev_err_count) > 0) {
        real_cmd->failed_calls++;
    }

    /* After executing command, we will close the client after writing entire
     * reply if it is set 'CLIENT_CLOSE_AFTER_COMMAND' flag. */
    if (c->flags & CLIENT_CLOSE_AFTER_COMMAND) {
        c->flags &= ~CLIENT_CLOSE_AFTER_COMMAND;
        c->flags |= CLIENT_CLOSE_AFTER_REPLY;
    }

    /* When EVAL is called loading the AOF we don't want commands called
     * from Lua to go into the slowlog or to populate statistics. */
    if (server.loading && c->flags & CLIENT_LUA)
        flags &= ~(CMD_CALL_SLOWLOG | CMD_CALL_STATS);

    /* If the caller is Lua, we want to force the EVAL caller to propagate
     * the script if the command flag or client flag are forcing the
     * propagation. */
    if (c->flags & CLIENT_LUA && server.lua_caller) {
        if (c->flags & CLIENT_FORCE_REPL)
            server.lua_caller->flags |= CLIENT_FORCE_REPL;
        if (c->flags & CLIENT_FORCE_AOF)
            server.lua_caller->flags |= CLIENT_FORCE_AOF;
    }

    /* Note: the code below uses the real command that was executed
     * c->cmd and c->lastcmd may be different, in case of MULTI-EXEC or
     * re-written commands such as EXPIRE, GEOADD, etc. */

    /* Record the latency this command induced on the main thread.
     * unless instructed by the caller not to log. (happens when processing
     * a MULTI-EXEC from inside an AOF). */
    if (flags & CMD_CALL_SLOWLOG) {
        char *latency_event = (real_cmd->flags & CMD_FAST) ?
                               "fast-command" : "command";
        latencyAddSampleIfNeeded(latency_event,duration/1000);
    }

    /* Log the command into the Slow log if needed.
     * If the client is blocked we will handle slowlog when it is unblocked. */
    if ((flags & CMD_CALL_SLOWLOG) && !(c->flags & CLIENT_BLOCKED))
        slowlogPushCurrentCommand(c, real_cmd, duration);

    /* Send the command to clients in MONITOR mode if applicable.
     * Administrative commands are considered too dangerous to be shown. */
    if (!(c->cmd->flags & (CMD_SKIP_MONITOR|CMD_ADMIN))) {
        robj **argv = c->original_argv ? c->original_argv : c->argv;
        int argc = c->original_argv ? c->original_argc : c->argc;
        replicationFeedMonitors(c,server.monitors,c->db->id,argv,argc);
    }

    /* Clear the original argv.
     * If the client is blocked we will handle slowlog when it is unblocked. */
    if (!(c->flags & CLIENT_BLOCKED))
        freeClientOriginalArgv(c);

    /* populate the per-command statistics that we show in INFO commandstats. */
    if (flags & CMD_CALL_STATS) {
        real_cmd->microseconds += duration;
        real_cmd->calls++;
    }

    /* Propagate the command into the AOF and replication link */
    if (flags & CMD_CALL_PROPAGATE &&
        (c->flags & CLIENT_PREVENT_PROP) != CLIENT_PREVENT_PROP)
    {
        int propagate_flags = PROPAGATE_NONE;

        /* Check if the command operated changes in the data set. If so
         * set for replication / AOF propagation. */
        if (dirty) propagate_flags |= (PROPAGATE_AOF|PROPAGATE_REPL);

        /* If the client forced AOF / replication of the command, set
         * the flags regardless of the command effects on the data set. */
        if (c->flags & CLIENT_FORCE_REPL) propagate_flags |= PROPAGATE_REPL;
        if (c->flags & CLIENT_FORCE_AOF) propagate_flags |= PROPAGATE_AOF;

        /* However prevent AOF / replication propagation if the command
         * implementation called preventCommandPropagation() or similar,
         * or if we don't have the call() flags to do so. */
        if (c->flags & CLIENT_PREVENT_REPL_PROP ||
            !(flags & CMD_CALL_PROPAGATE_REPL))
                propagate_flags &= ~PROPAGATE_REPL;
        if (c->flags & CLIENT_PREVENT_AOF_PROP ||
            !(flags & CMD_CALL_PROPAGATE_AOF))
                propagate_flags &= ~PROPAGATE_AOF;

        /* Call propagate() only if at least one of AOF / replication
         * propagation is needed. Note that modules commands handle replication
         * in an explicit way, so we never replicate them automatically. */
        if (propagate_flags != PROPAGATE_NONE && !(c->cmd->flags & CMD_MODULE))
            propagate(c->cmd,c->db->id,c->argv,c->argc,propagate_flags);
    }

    /* Restore the old replication flags, since call() can be executed
     * recursively. */
    c->flags &= ~(CLIENT_FORCE_AOF|CLIENT_FORCE_REPL|CLIENT_PREVENT_PROP);
    c->flags |= client_old_flags &
        (CLIENT_FORCE_AOF|CLIENT_FORCE_REPL|CLIENT_PREVENT_PROP);

    /* Handle the alsoPropagate() API to handle commands that want to propagate
     * multiple separated commands. Note that alsoPropagate() is not affected
     * by CLIENT_PREVENT_PROP flag. */
    if (server.also_propagate.numops) {
        int j;
        redisOp *rop;

        if (flags & CMD_CALL_PROPAGATE) {
            int multi_emitted = 0;
            /* Wrap the commands in server.also_propagate array,
             * but don't wrap it if we are already in MULTI context,
             * in case the nested MULTI/EXEC.
             *
             * And if the array contains only one command, no need to
             * wrap it, since the single command is atomic. */
            if (server.also_propagate.numops > 1 &&
                !(c->cmd->flags & CMD_MODULE) &&
                !(c->flags & CLIENT_MULTI) &&
                !(flags & CMD_CALL_NOWRAP))
            {
                execCommandPropagateMulti(c->db->id);
                multi_emitted = 1;
            }

            for (j = 0; j < server.also_propagate.numops; j++) {
                rop = &server.also_propagate.ops[j];
                int target = rop->target;
                /* Whatever the command wish is, we honor the call() flags. */
                if (!(flags&CMD_CALL_PROPAGATE_AOF)) target &= ~PROPAGATE_AOF;
                if (!(flags&CMD_CALL_PROPAGATE_REPL)) target &= ~PROPAGATE_REPL;
                if (target)
                    propagate(rop->cmd,rop->dbid,rop->argv,rop->argc,target);
            }

            if (multi_emitted) {
                execCommandPropagateExec(c->db->id);
            }
        }
        redisOpArrayFree(&server.also_propagate);
    }
    server.also_propagate = prev_also_propagate;

    /* Client pause takes effect after a transaction has finished. This needs
     * to be located after everything is propagated. */
    if (!server.in_exec && server.client_pause_in_transaction) {
        server.client_pause_in_transaction = 0;
    }

    /* If the client has keys tracking enabled for client side caching,
     * make sure to remember the keys it fetched via this command. */
    if (c->cmd->flags & CMD_READONLY) {
        client *caller = (c->flags & CLIENT_LUA && server.lua_caller) ?
                            server.lua_caller : c;
        if (caller->flags & CLIENT_TRACKING &&
            !(caller->flags & CLIENT_TRACKING_BCAST))
        {
            trackingRememberKeys(caller);
        }
    }

    server.fixed_time_expire--;
    server.stat_numcommands++;
    prev_err_count = server.stat_total_error_replies;

    /* Record peak memory after each command and before the eviction that runs
     * before the next command. */
    size_t zmalloc_used = zmalloc_used_memory();
    if (zmalloc_used > server.stat_peak_memory)
        server.stat_peak_memory = zmalloc_used;

    /* Do some maintenance job and cleanup */
    afterCommand(c);
}
```



#### getCommand

```c
//t_string.c

 {"get",getCommand,2,"read-only fast @string",0,NULL,1,1,1,0,0,0},


void getCommand(client *c) {
    getGenericCommand(c);
}



int getGenericCommand(client *c) {
    robj *o;

    //查找对应key的value
    if ((o = lookupKeyReadOrReply(c,c->argv[1],shared.null[c->resp])) == NULL)
        return C_OK;
	//校验类型
    if (checkType(c,o,OBJ_STRING)) {
        return C_ERR;
    }
	//返回
    addReplyBulk(c,o);
    return C_OK;
}


robj *lookupKeyReadOrReply(client *c, robj *key, robj *reply) {
    //在dict中查找key
    robj *o = lookupKeyRead(c->db, key);
    //找不到 回复keymiss
    if (!o) SentReplyOnKeyMiss(c, reply);
    return o;
}


/* Lookup a key for read operations, or return NULL if the key is not found
 * in the specified DB.
 *
 * As a side effect of calling this function:
 * 1. A key gets expired if it reached it's TTL.
 * 2. The key last access time is updated.
 * 3. The global keys hits/misses stats are updated (reported in INFO).
 * 4. If keyspace notifications are enabled, a "keymiss" notification is fired.
 *
 * This API should not be used when we write to the key after obtaining
 * the object linked to the key, but only for read only operations.
 *
 * Flags change the behavior of this command:
 *
 *  LOOKUP_NONE (or zero): no special flags are passed.
 *  LOOKUP_NOTOUCH: don't alter the last access time of the key.
 *
 * Note: this function also returns NULL if the key is logically expired
 * but still existing, in case this is a slave, since this API is called only
 * for read operations. Even if the key expiry is master-driven, we can
 * correctly report a key is expired on slaves even if the master is lagging
 * expiring our key via DELs in the replication link. */
robj *lookupKeyReadWithFlags(redisDb *db, robj *key, int flags) {
    robj *val;

    //如果key过期了 
    if (expireIfNeeded(db,key) == 1) {
        /* If we are in the context of a master, expireIfNeeded() returns 1
         * when the key is no longer valid, so we can return NULL ASAP. */
        if (server.masterhost == NULL)
            //转到keymiss代码块
            goto keymiss;

        /* However if we are in the context of a slave, expireIfNeeded() will
         * not really try to expire the key, it only returns information
         * about the "logical" status of the key: key expiring is up to the
         * master in order to have a consistent view of master's data set.
         *
         * However, if the command caller is not the master, and as additional
         * safety measure, the command invoked is a read-only command, we can
         * safely return NULL here, and provide a more consistent behavior
         * to clients accessing expired values in a read-only fashion, that
         * will say the key as non existing.
         *
         * Notably this covers GETs when slaves are used to scale reads. */
        if (server.current_client &&
            server.current_client != server.master &&
            server.current_client->cmd &&
            server.current_client->cmd->flags & CMD_READONLY)
        {
            goto keymiss;
        }
    }
    //从dict中查找key
    val = lookupKey(db,key,flags);
    if (val == NULL)
        //找不到 转到keymiss
        goto keymiss;
    server.stat_keyspace_hits++;
    return val;

keymiss:
    if (!(flags & LOOKUP_NONOTIFY)) {
        notifyKeyspaceEvent(NOTIFY_KEY_MISS, "keymiss", key, db->id);
    }
    server.stat_keyspace_misses++;
    return NULL;
}

```



#### setCommand



> 1. **`OBJ_NO_FLAGS`**:
>    - 不带任何标志位。
>    - 命令: `SET key value`
> 2. **`OBJ_SET_NX` (Set if key not exists)**:
>    - 只有在键不存在时才设置。
>    - 命令: `SET key value NX`
>    - 示例: `SET mykey "value" NX`
> 3. **`OBJ_SET_XX` (Set if key exists)**:
>    - 只有在键存在时才设置。
>    - 命令: `SET key value XX`
>    - 示例: `SET mykey "new_value" XX`
> 4. **`OBJ_EX` (Set if time in seconds is given)**:
>    - 设置键的过期时间为给定的秒数。
>    - 命令: `SET key value EX seconds`
>    - 示例: `SET mykey "value" EX 60`
> 5. **`OBJ_PX` (Set if time in ms is given)**:
>    - 设置键的过期时间为给定的毫秒数。
>    - 命令: `SET key value PX milliseconds`
>    - 示例: `SET mykey "value" PX 1000`
> 6. **`OBJ_KEEPTTL` (Set and keep the ttl)**:
>    - 设置键并保持原有的过期时间。
>    - 命令: `SET key value KEEPTTL`
>    - 示例: `SET mykey "value" KEEPTTL`
> 7. **`OBJ_SET_GET` (Set if want to get key before set)**:
>    - 设置键之前获取键的旧值。
>    - 命令: `SET key value GET`
>    - 示例: `SET mykey "value" GET`
> 8. **`OBJ_EXAT` (Set if timestamp in second is given)**:
>    - 设置键的过期时间为给定的时间戳（秒）。
>    - 命令: `SET key value EXAT timestamp`
>    - 示例: `SET mykey "value" EXAT 1592413592`
> 9. **`OBJ_PXAT` (Set if timestamp in ms is given)**:
>    - 设置键的过期时间为给定的时间戳（毫秒）。
>    - 命令: `SET key value PXAT timestamp`
>    - 示例: `SET mykey "value" PXAT 1592413592000`
> 10. **`OBJ_PERSIST` (Set if we need to remove the ttl)**:
>     - 移除键的过期时间，使其成为持久键。
>     - 命令: `SET key value PERSIST`
>     - 示例: `SET mykey "value" PERSIST`



```c
//t_string.c


/* SET key value [NX] [XX] [KEEPTTL] [GET] [EX <seconds>] [PX <milliseconds>]
 *     [EXAT <seconds-timestamp>][PXAT <milliseconds-timestamp>] */
void setCommand(client *c) {
    robj *expire = NULL;
    int unit = UNIT_SECONDS;
    int flags = OBJ_NO_FLAGS;

    if (parseExtendedStringArgumentsOrReply(c,&flags,&unit,&expire,COMMAND_SET) != C_OK) {
        return;
    }

    c->argv[2] = tryObjectEncoding(c->argv[2]);
    setGenericCommand(c,flags,c->argv[1],c->argv[2],expire,unit,NULL,NULL);
}



#define OBJ_NO_FLAGS 0
#define OBJ_SET_NX (1<<0)          /* Set if key not exists. */
#define OBJ_SET_XX (1<<1)          /* Set if key exists. */
#define OBJ_EX (1<<2)              /* Set if time in seconds is given */
#define OBJ_PX (1<<3)              /* Set if time in ms in given */
#define OBJ_KEEPTTL (1<<4)         /* Set and keep the ttl */
#define OBJ_SET_GET (1<<5)         /* Set if want to get key before set */
#define OBJ_EXAT (1<<6)            /* Set if timestamp in second is given */
#define OBJ_PXAT (1<<7)            /* Set if timestamp in ms is given */
#define OBJ_PERSIST (1<<8)         /* Set if we need to remove the ttl */

void setGenericCommand(client *c, int flags, robj *key, robj *val, robj *expire, int unit, robj *ok_reply, robj *abort_reply) {
    long long milliseconds = 0, when = 0; /* initialized to avoid any harmness warning */

    //如果有过期时间
    if (expire) {
        //将过期时间转换成long存储在milliseconds中
        if (getLongLongFromObjectOrReply(c, expire, &milliseconds, NULL) != C_OK)
            return;
        if (milliseconds <= 0 || (unit == UNIT_SECONDS && milliseconds > LLONG_MAX / 1000)) {
            /* Negative value provided or multiplication is gonna overflow. */
            addReplyErrorFormat(c, "invalid expire time in %s", c->cmd->name);
            return;
        }
        //如果是 秒 单位 转换成毫秒 
        if (unit == UNIT_SECONDS) milliseconds *= 1000;
        //设置到when
        when = milliseconds;
        //如果是ex或者px 那么设置when为当前时间+过期时间
        if ((flags & OBJ_PX) || (flags & OBJ_EX))
            when += mstime();
        if (when <= 0) {
            /* Overflow detected. */
            addReplyErrorFormat(c, "invalid expire time in %s", c->cmd->name);
            return;
        }
    }

    //如果是nx key存在 或者 xx key不存在 都直接返回
    if ((flags & OBJ_SET_NX && lookupKeyWrite(c->db,key) != NULL) ||
        (flags & OBJ_SET_XX && lookupKeyWrite(c->db,key) == NULL))
    {
        addReply(c, abort_reply ? abort_reply : shared.null[c->resp]);
        return;
    }

    //如果是set get 那么先获取一下key 如果key不存在就返回
    if (flags & OBJ_SET_GET) {
        if (getGenericCommand(c) == C_ERR) return;
    }

    //设置key value到redisDb的dict中
    genericSetKey(c,c->db,key, val,flags & OBJ_KEEPTTL,1);
    //修改操作+1
    server.dirty++;
    //发布set事件
    notifyKeyspaceEvent(NOTIFY_STRING,"set",key,c->db->id);
    //如果有过期时间
    if (expire) {
        //设置过期时间为when
        setExpire(c,c->db,key,when);
        notifyKeyspaceEvent(NOTIFY_GENERIC,"expire",key,c->db->id);

        /* Propagate as SET Key Value PXAT millisecond-timestamp if there is EXAT/PXAT or
         * propagate as SET Key Value PX millisecond if there is EX/PX flag.
         *
         * Additionally when we propagate the SET with PX (relative millisecond) we translate
         * it again to SET with PXAT for the AOF.
         *
         * Additional care is required while modifying the argument order. AOF relies on the
         * exp argument being at index 3. (see feedAppendOnlyFile)
         * */
        //在处理带有ex px exat pxat 的命令时 会对命令进行修改 方便AOF进行日志记录的统一
        //统一被处理成为 set key value pxat 某个时间戳
        robj *exp = (flags & OBJ_PXAT) || (flags & OBJ_EXAT) ? shared.pxat : shared.px;
        robj *millisecondObj = createStringObjectFromLongLong(milliseconds);
        rewriteClientCommandVector(c,5,shared.set,key,val,exp,millisecondObj);
        decrRefCount(millisecondObj);
    }
    //如果不是set get 可以返回了
    if (!(flags & OBJ_SET_GET)) {
        addReply(c, ok_reply ? ok_reply : shared.ok);
    }

    //如果没有设置过期时间 对AOF来说 get这个选项就没有意义 需要修改命令 不记录get
    /* Propagate without the GET argument (Isn't needed if we had expire since in that case we completely re-written the command argv) */
    if ((flags & OBJ_SET_GET) && !expire) {
        int argc = 0;
        int j;
        //重新分配 argc-1大小的argv空间
        robj **argv = zmalloc((c->argc-1)*sizeof(robj*));
        for (j=0; j < c->argc; j++) {
            char *a = c->argv[j]->ptr;
            //跳过get选项
            /* Skip GET which may be repeated multiple times. */
            if (j >= 3 &&
                (a[0] == 'g' || a[0] == 'G') &&
                (a[1] == 'e' || a[1] == 'E') &&
                (a[2] == 't' || a[2] == 'T') && a[3] == '\0')
                continue;
            //重新存储命令
            argv[argc++] = c->argv[j];
            incrRefCount(c->argv[j]);
        }
        //替换命令
        replaceClientCommandVector(c, argc, argv);
    }
}

```













## RESP

[RESP协议规范](https://redis.io/docs/latest/develop/reference/protocol-spec/#arrays)

RESP 本质上是一个支持多种数据类型的序列化协议。在 RESP 中，数据的第一个字节决定了其类型。

Redis 通常使用 RESP 作为[请求-响应](https://redis.io/docs/latest/develop/reference/protocol-spec/#request-response-model)协议，方式如下：

- 客户端将命令作为批量[字符串](https://redis.io/docs/latest/develop/reference/protocol-spec/#bulk-strings)[数组](https://redis.io/docs/latest/develop/reference/protocol-spec/#arrays)发送到 Redis 服务器。数组中的第一个（有时也是第二个）批量字符串是命令的名称。数组的后续元素是命令的参数。
- 服务器以 RESP 类型回复。回复的类型由命令的实现决定，也可能由客户端的协议版本决定。

客户端将命令**以 RESP 数组**的形式发送到 Redis 服务器。同样，一些返回元素集合的 Redis 命令也使用**数组**作为其回复。

```text
*3\r\n$3\r\nset\r\n$1\r\nf\r\n$1\r\n1\r\n
```



( CRLF `\r\n`) 是协议的*终止符*，它**始终**分隔协议的各个部分。

> 在某些地方，**RESP Array 类型可能被称为*multi bulk***。两者是相同的。





## Redis命令

@todo 批量命令？



## c语言知识

在使用sizeof计算结构体的内存占用大小时，char[] 不计算，char * 占用8个字节，并且结构体会按照8字节的倍数进行对齐填充。









