# Redis
## 整体架构设计

![](.\images\redis核心设计.png)



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
		
        //以rank来计算插入节点的每一层span值 rank的差值就是小线段数量
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









## c语言知识

在使用sizeof计算结构体的内存占用大小时，char[] 不计算，char * 占用8个字节，并且少于8个字节会进行对齐填充。









