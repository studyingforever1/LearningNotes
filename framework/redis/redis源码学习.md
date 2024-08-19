# Redis
## 整体架构设计

![](.\images\redis核心设计.png)



## 数据类型

<img src=".\images\基本数据类型.png" style="zoom: 67%;" />





## 数据结构

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



















