# 一、SDS的源码分析

## 1.1、Redis底层数据结构

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/91aefb9dd5574fd296bb10ec6b594916.png)

学习底层数据结构的意义：

1、数据结构的设计是Redis设计的精髓所在

2、掌握底层数据结构可以更好的去使用基本数据类型

## 1.2、SDS概述

SDS（Simple Dynamic String） 简单动态字符串，是Redis用来实现字符串的一种数据结构。

SDS用途：

1. 存储key、值（字符串、整数）
2. AOF缓冲区
3. 用户输入缓冲

## 1.3、SDS结构体

sds.h

### sdshdr5

```c
struct __attribute__ ((__packed__)) sdshdr5 {
    unsigned char flags; /* 3 lsb of type, and 5 msb of string length */
    char buf[];
};
```

* 常量字符串，不支持扩容
* flags : 1字节标识 低3位表类型（type）高5位表已使用长度
* buf[] : 柔性数据，存放数据
* ___——attribute__ ((__packed——___))：按1字节对齐，节省空间
  （默认情况下结构体按变量大小的最小公倍数做字节对齐，按照实际占用字节数进行对齐）

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/65dd68f4b5e4489d8fb094b4c546f730.png)

### sdshdr8

```c
struct __attribute__ ((__packed__)) sdshdr8 {
    uint8_t len; /* used */
    uint8_t alloc; /* excluding the header and null terminator */
    unsigned char flags; /* 3 lsb of type, 5 unused bits */
    char buf[];
};

```

* len：已使用长度
* alloc: 总长度
* flags : 1字节标识 低3位表类型（type）高5位预留
* buf[] : 柔性数组，存放数据
* len和alloc的类型不同(uint8、uint16、uint32、uint64)
* 所占的字节数不同（uint8：1个字节、uint16：2个字节、uint32：4个字节、uint64：8个字节）

### sdshdr16

```c
struct __attribute__ ((__packed__)) sdshdr16 {
    uint16_t len; /* used */
    uint16_t alloc; /* excluding the header and null terminator */
    unsigned char flags; /* 3 lsb of type, 5 unused bits */
    char buf[];
};

```

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/2b72d25badbd445b9343c9d63b10ed44.png)

### sdshdr32

```c
struct __attribute__ ((__packed__)) sdshdr32 {
    uint32_t len; /* used */
    uint32_t alloc; /* excluding the header and null terminator */
    unsigned char flags; /* 3 lsb of type, 5 unused bits */
    char buf[];
};
```

### sdshdr64

```c
struct __attribute__ ((__packed__)) sdshdr64 {
    uint64_t len; /* used */
    uint64_t alloc; /* excluding the header and null terminator */
    unsigned char flags; /* 3 lsb of type, 5 unused bits */
    char buf[];
};
```

## 1.4、API解析

sds.c

### sdsnewlen

创建字符串：

1. 根据不同的长度选择不同的sds类型
2. 如果是type5并且初始长度为0，强制转化为type8
3. 计算不同type需要的长度，根据长度申请空间
4. 根据不同类型给sdshdr属性赋初值
5. 返回sds (sds.buf)

### sdsfree

释放字符串：

1. 通过对s的偏移，定位到sds结构体的首地址
2. 调用s_free方法释放内存

### sdscatlen

拼接字符串：

1. 调用sdsMakeRoomFor判断是否对拼接的串进行扩容
2. 如无需扩容则直接返回，如需扩容则返回扩容后的字符串
3. 用memcpy函数将字符串拼接
4. 最后加上结束符\0

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/f7b6030d00434e3daf8c978ca892cb33.png)

### sdsMakeRoomFor

SDS扩容：

1. 获取当前可用空间长度avail，若大于等于新增长度addlen则无需扩容，直接返回
2. 若avail小于addlen，len+addlen&#x3c;1M，则按新长度的2倍扩容
3. 若avail小于addlen，len+addlen>1M，则按新长度+1M
4. 根据新长度选择sds类型，如果sds类型和原类型相同，则通过realloc扩大柔性数组
5. 如果sds类型和原类型不相同，则malloc重新申请内存，并把原buf内容移动到新位置
6. 对新串的属性进行赋值后返回

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/56cf228b36504ca6b6a7ff0ec4b54726.png)

## 1.6、int、embstr和raw

### 字符串的三种编码

OBJ_ENCODING_INT：整型数字编码

OBJ_ENCODING_EMBSTR : 短字串编码

OBJ_ENCODING_RAW ： 普通字串编码

### createStringObject

如果长度小于或等于44字节，则按embstr方式编码，否则按raw方式编码

### tryObjectEncoding

1. 如果字符串长度&#x3c;=20则调用string21转化为long long
2. 如果能够使用shared.integers中的数(0-9999)，则返回
3. 否则如果编码为RAW，则替换为字符串转换后的数值

## 1.7、SDS的设计思想和优势

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/9aa3d337520645afbd75dd81fe0a07b4.png)

1. **SDS是对C字符串的增强。**
   C字符串不能存储二进制，因为是以\0结束，不能用\0截串
   sds存储len、alloc，计算free，获得这些值的时间复杂度是O(1)
   可以做内存重新分配，杜绝缓冲区溢出
   sds在创建时，返回的是buf[]的指针，兼容C
2. **根据不同的长度创建不同的sds，节省空间。**
   sds5、sds8、sds16、sds32、sds64
3. **采用三种编码方式实现，便于操作和进一步节省空间。**
   int、embstr、raw

# 二、跳跃表的源码分析

## 2.1、跳跃表的基本思想

跳跃表是有序集合（sorted-set）的底层实现，将有序链表中的部分节点分层，每一层都是一个有序链表。

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/375e5a55c3cd4b68b14e27f7b435e257.png)

1. 分层，每层由有序链表构成
2. 头节点在每层出现
3. 某节点在上层出现，则在下层也出现
4. 节点层数随机

## 2.2、节点与结构

### 跳跃表节点

```c
typedef struct zskiplistNode {
    sds ele;
    double score;
    struct zskiplistNode *backward;
    struct zskiplistLevel {
        struct zskiplistNode *forward;
        unsigned long span;
    } level[];
} zskiplistNode;
```

* ele：存储字符串数据
* score：存储排序分值
* backward：后退指针，指向当前节点最底层的前一个节点
* level[]：柔性数组，随机生成1-64的值
  forward：指向本层下一个节点
* span：本层下个节点到本节点的元素个数

### 跳跃表链表

```c
typedef struct zskiplist {
    struct zskiplistNode *header, *tail;
    unsigned long length;
    int level;
} zskiplist;
```

* zskiplistNode *header *tail：头节点和尾节点
* length：跳跃表长度（不包括头节点）
* level：跳跃表高度

## 2.3、API解析

t_zset.c

### zslCreate

创建跳跃表：

1. 定义一个zsl，申请内存，并赋初始值
2. 调用zslCreateNode创建头节点
3. 每层头节点赋初始值
4. 尾节点赋值null

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/5f9fd16fa08d40409fd1f3af1eb1d9a8.png)

### zslCreateNode

创建节点：

1. 在创建时level、score、ele都是确定的
2. 申请内存：节点内存+柔性数组内存
3. 属性赋值

### zslInsert

插入节点：

1. 从最上层开始遍历节点
2. 如果 i 不是 zsl->level-1 层，那么 i 层的起始 rank 值为 i+1 层的 rank 值
3. 如果当前分值大于下一个分值，则累加span（比对分值，如果分值一样就比对ele）ele按字典序
4. 指向本层的下一个节点
5. 记录新节点的前一个节点
6. 调用zslRandomLevel函数获得一个随机层数
7. 如果新节点层数大于跳跃表层高，那么初始化表头节点中未使用的层，并将它们记录到 update 数组中
8. 创建新节点
9. 将前面记录的指针指向新节点，并做相应的设置
10. 设置新节点的 forward 指针
11. 将沿途记录的各个节点的 forward 指针指向新节点
12. 计算新节点跨越的节点数量
13. 更新新节点插入之后，沿途节点的 span 值
14. 未接触的节点的 span 值也需要增一，这些节点直接从表头指向新节点
15. 设置新节点的后退指针
16. 跳跃表的节点计数增一
17. 返回新节点

    span：本次后驱节点跨越多少个第一层节点（包括跨越到的节点）

    rank也叫节点索引值，是span的累加值

    阶段1：查找要插入的位置

    ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/996daaddd4594de792d2043d33ceb297.png)

    阶段2：调整层高

    ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/70cc221e87194e9b97cfd04a1158b827.png)

    阶段3：生成节点并插入

    ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/ddc6bd71a2874629a6bc0cbe04d85425.png)

    阶段4：设置backward

    ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/2865bd9645804533aec0def4d85075d2.png)

### zslGetRank

查找排位：

1. 排位就是累积跨越的节点数量
2. 从最上层开始遍历节点并对比元素
3. 如果当前分值大于下一个分值，则累加span（比对分值，如果分值一样就比对ele）ele按字典序
4. 指向本层的下一个节点
5. 如果找到了（ele相同）则返回

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/eedb8138e7b24f2d9da18f5281449c06.png)

### zslDelete

删除节点：

1. 遍历跳跃表
2. 比对分值，比对ele
3. 分值小于或等于当前值并且ele不相等
4. 继续下一个，并记录节点
5. 分值和元素名都相同，则调用zslDeleteNode函数删除该节点

## 2.4、随机层数

* 新插入的节点，调用随机算法分配层数
* 期望上每层按50%递减，Redis标准层晋升率为25% (ZSKIPLIST_P)

### zslRandomLevel

1. 初始level设置为1
2. 循环只要random()&0xFFFF&#x3c;16383(0.25*65535)，则level加1
3. 如果level值小于最大level（32）则返回level，否则返回最大level

## 2.5、设计思想与优势

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/215d6435c60e4d709db6f0b335f63372.png)

1. 以空间换时间的一种优化链表算法
   优化有序链表
   时间复杂度：O（logN）
   空间复杂度：2N
2. 兼顾链表与数组优势的数据结构
   索引： span的累加--->rank 作为索引
3. 从内存占用上来说，skiplist比平衡树更少
   AVL： 每个节点有2个指针
   sl:每个节点有1/1-P=1.33 个指针  P:0.25

# 三、压缩列表的源码分析

## 3.1、存储结构

压缩列表就是一个字节数组（char *zl），有序集合、散列和列表都直接或间接使用到压缩列表

### 字节数组结构

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/12146a062b434070b467295e6094b95d.png)

* zlbytes：压缩列表的字节长度
* zltail：压缩列表尾元素相对于压缩列表起始地址的偏移量
* zllen：压缩列表的元素个数
* entry1..entryX : 压缩列表的各个节点
* zlend：压缩列表的结尾，占一个字节，恒为0xFF（255）

### 节点结构

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/bb56b74a867549eaae12766c0b60206d.png)

* previous_entry_length：前一个元素的字节长度
* encoding:表示当前元素的编码 （1个字节8位，前2位表示content的类型，后6位表示content的长度，0-12整数，将数字存在encoding的后4位中，没有content）
* content:数据内容

## 3.2、压缩解码

ziplist.c

zlentry是压缩列表中元素解码后的结构体，其结构如下：

```c
typedef struct zlentry {
    unsigned int prevrawlensize; /* Bytes used to encode the previous entry len*/
    unsigned int prevrawlen;     /* Previous entry len. */
    unsigned int lensize;        /* Bytes used to encode this entry type/len.
                                    For example strings have a 1, 2 or 5 bytes
                                    header. Integers always use a single byte.*/
    unsigned int len;            /* Bytes used to represent the actual entry.
                                    For strings this is just the string length
                                    while for integers it is 1, 2, 3, 4, 8 or
                                    0 (for 4 bit immediate) depending on the
                                    number range. */
    unsigned int headersize;     /* prevrawlensize + lensize. */
    unsigned char encoding;      /* Set to ZIP_STR_* or ZIP_INT_* depending on
                                    the entry encoding. However for 4 bits
                                    immediate integers this can assume a range
                                    of values and must be range-checked. */
    unsigned char *p;            /* Pointer to the very start of the entry, that
                                    is, this points to prev-entry-len field. */
} zlentry;
```

* prevrawlensize：存储 prerawlen 需要用到的字节数
* prevrawlen：前一个结点占用的字节数
* lensize：存储 len 需要的字节数
* len：数据长度
* headersize：首部长度（prevrawlensize+lensize）
* encoding:表示当前元素的编码
* *p：当前元素首地址

### zipEntry

解压压缩列表：

1. 解码previous_entry_length字段
2. 解码encoding字段
3. 设置其他字段(headersize、p)

### ZIP_DECODE_PREVLEN

解码previous_entry_length字段

1. 判断编码类型，如果第一个字节的值小于254，则prevlensize=1
2. 否则prevlensize=5
3. 如果prevlensize=1则直接读取长度
4. 否则读取后4个字节作为长度

### ZIP_DECODE_LENGTH

解码encoding字段

1. 取出值的编码类型
2. 字符串编码
3. 整数编码

## 3.3、API解析

### ziplistNew

创建压缩列表：

1. 设置长度11个字节
2. 根据长度申请内存
3. 各个字段赋初始值
4. 返回字节数组首地址

### ziplistInsert

插入元素：

1. 编码

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/69f196e496574afd85741942845e698f.png)
2. 重新分配空间

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/4efe9b518c6c4cc0a26748eae5086c50.png)
3. 数据复制

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/1ae850f4b0a54775a7c74b5a9ec9335b.png)

#### ziplistDelete

删除元素：

1. 计算待删除元素的总长度

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/f08161a37417458ba2bfbb71b3643584.png)
2. 数据复制

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/f66c4d58dd034682b0cfe77c38588f57.png)
3. 重新分配空间

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/c7b4a897b5064709ac629715580e9f1a.png)

#### ziplistFind

查找元素：

1. 计算节点属性
2. 判断节点类型
3. 是字符串对比内容
4. 是整数对比数值
5. 指向下一个

## 3.4、级联更新

```
__ziplistCascadeUpdate
```

处理的节点后面还有n个节点(n>1)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/fadb8e006ac349b89f6d6cfbe4e53612.png)

## 3.5、设计思想与优势

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/b870cf49f85f496cb75102b73971929b.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/3c66f45c50d349798f5c6c82913c9118.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/9d54634bfb7b4fea9b17a2f088126ff7.png)

1. **节省内存，以时间换空间**
   存储前一个节点长度，通过长度来计算出前后节点的位置
   encoding既存类别也存长度
   连续存储，在使用时需要解码
2. **zlentry解码存储，提供快速访问**
   prevlen、prevlesize
   len、lensize
3. **级联更新**
   条件
   流程

# 四、字典的源码分析

## 4.1、字典的内部结构

字典又称散列表，是用来存储键值(Key-Value)对的一种数据结构

* Redis主存储
* hash数据类型的实现
* 过期时间的kv、zset中value和score的映射关系

### 数组

* 有限个类型相同的对象的集合
* 连续的内存存储
* 即刻存取，时间复杂度O(1)
* 下标的定位方式：头指针+偏移量----->下标

### 字典

```c

typedef struct dict {
    dictType *type;
    void *privdata;
    dictht ht[2];
    long rehashidx; /* rehashing not in progress if rehashidx == -1 */
    int16_t pauserehash; /* If >0 rehashing is paused (<0 indicates coding error) */
} dict;

typedef struct dictType {
    uint64_t (*hashFunction)(const void *key);
    void *(*keyDup)(void *privdata, const void *key);
    void *(*valDup)(void *privdata, const void *obj);
    int (*keyCompare)(void *privdata, const void *key1, const void *key2);
    void (*keyDestructor)(void *privdata, void *key);
    void (*valDestructor)(void *privdata, void *obj);
    int (*expandAllowed)(size_t moreMem, double usedRatio);
} dictType;
```

* dictht ： 哈希表   ht[2]
* rehashidx：rehash索引

### Hash表

```c
typedef struct dictht {
    dictEntry **table;
    unsigned long size;
    unsigned long sizemask;
    unsigned long used;
} dictht;
```

* **table：指针数组，数组中的对象为dictEntry
* size：数组长度
* sizemask：掩码（size-1）
* used：已用长度

### Hash表节点

```c
typedef struct dictEntry {
    void *key;
    union {
        void *val;
        uint64_t u64;
        int64_t s64;
        double d;
    } v;
    struct dictEntry *next;
} dictEntry;
```

* *key：键
* v:值 联合体
* *next：指向下一个节点，形成单链表

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/5a2ccbe4779544deba89b0f74e8e32c3.png)

## 4.2、Hash函数

### Hash算法

将字符串或数字统一成固定长度的整数

```
dictHashKey-->
(d)->type->hashFunction(key)-->dbDictType.hashFunction(key)-->
dictSdsHash-->
dictGenHashFunction--->
siphash
```

siphash.c

### KV的存取

* 利用Hash值计算下标

  key——————>索引------->数组下标

  hash(key) : 统一的值  数字

  数字%数组大小= 余数 ----->下标
* 存取Key和Value

  ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/9ca5c056b41b4debaa5bb0af77a64895.png)

## 4.3、Hash冲突

不同的key通过hash计算%size得到的余数 一样，数组下标是同一个。

解决方法：

* 开放地址法：实现简单，空间利用率不高，查找复杂
* 拉链法：用头插的方式实现单链表

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/24fdd4c083784892899ed7f396b40de9.png)

## 4.4、API解析

dict.c

### dictCreat

创建字典：

1. 申请内存空间：zmalloc
2. 调用_dictInit，给字典字段赋初值

   ```
   dictCreate-->_dictInit--->_dictReset
   ```

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/ac64ca61222e41cd8fe0af2531f43dd4.png)

db.c

```
setKey--->genericSetKey
```

### setKey

插入键值对：

1. 查找该key是否存在：lookupKeyWrite-->dictFind
2. 不存在执行新增：dbadd--->dictAdd
3. 存在执行修改：dbOverwrite

```
lookupKeyWrite-->lookupKeyWriteWithFlags-->lookupKey--->dictFind
```

### dictFind

查找元素：

1. 得到键的hash值
2. 定位hash表：ht[0]和ht[1]
3. 根据hash值计算索引值：数组下标
4. 取出该数组下标元素，遍历该元素的单链表
5. 参数key与元素key匹配则返回元素的值，否则返回空

### dictAdd

插入字典：

1. 添加键
2. 设置值
3. 调用dictKeyIndex，计算索引，如果索引处有值，则将值带入并返回-1，否则返回索引值
4. 如果在rehash，则插入ht[1]，否则插入ht[0]
5. 申请新内存
6. 设置键：dictSetKey

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/b876dfbe1fc34c3c8bfc071027518361.png)db.c

### dbOverwrite

修改键值对：

1. 调用dictFind查找键是否存在
2. 不存在中断执行
3. 存在修改节点键值对为新值
4. 释放旧节点内存

### dictDelete

```
dictDelete-->dictGenericDelete
```

删除键值对：

1. 查找该key是否存在于该字典中
2. 存在把该节点从单链表中去除
3. 释放键和值的内存

## 4.5、渐进式Rehash

### 字典扩容与缩容

* 添加键值对时：使用量/总量>=75%，执行字典扩容
* ServerCron—>databasesCron 使用量/总量&#x3c;10%，执行字典缩容
* 字典扩容： 最大阈值 ：used/size >= 75%  扩：size=size*2
* 字典缩容：最小阈值 ： used/size&#x3c;10% 缩 ： used的最小2的n次幂
* 扩容和缩容后会进行重新计算索引值

### 渐进式rehash

rehash原因：数组长度变化

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/82cd4cb54a1a4b37bed8a0e6bb60ec2b.png)

1. 扩容（dictExpand）和缩容（tryResizeHashTables）时触发
2. ht[0]的数据重新计算索引值后迁移到ht[1]中
3. 渐进式（dictRehash）：分散操作，每次操作一个节点
4. 服务器空闲，批量操作（dictRehashMilliseconds），每次操作100个节点

dictExpand

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/d0c7deb5d7e1476a8310062f640aa273.png)

## 4.6、设计思想与优势

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/542ef98ad4c04cb190c38d167c4da78f.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/29578c59a46a4c4b932252810a2da66a.png)

1. **字典存储：数组+单链表**
   海量的数据存储、存取时间复杂度O(1)
2. **数据存储的多样性**
   Key：整型、字符串、浮点全部转化成字符串
   value：任意类型  联合体
3. **渐进式rehash**
   原因：数组的长度是固定的，当扩容和缩容时，数组长度变化，下标必然变化，所以要rehash
   支持：ht[0]和ht[1]
   渐进式：每次和批量

# 五、整数集合的源码分析

## 5.1、存储结构

整数集合(intset)是一个有序的、存储整型数据的结构。

Set类型存储元素为64位有符号整数且存储个数小于512（配置）个时使用该结构存储。

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/9874b5629d854e5db53de043bbcb2bcb.png)

### encoding：编码类型

* INTSET_ENC_INT16     -2的15次方-1 到 2的15次方-1
* INTSET_ENC_INT32    -2的31次方-1到-2的15次方-1 或者2的15次方-1到2的31次方-1
* INTSET_ENC_INT64   -2的63次方-1到-2的31次方-1 或者2的31次方-1到2的63次方-1

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/eeb844ee9b1542c59fa2e518929ff04d.png)

encoding是有值的

INTSET_ENC_INT16     2

INTSET_ENC_INT32     4

INTSET_ENC_INT64     8

### length：元素个数

### element[]：柔性数组

根据encoding来决定 几个字节来存一个元素

* INTSET_ENC_INT16     2个字节存
* INTSET_ENC_INT32     4个字节存
* INTSET_ENC_INT64      8个字节存

## 5.2、API解析

intset.c

```
intsetFind-->intsetSearch-->_intsetGet
```

### intsetFind

查询元素：

1. 如果待查询元素值编码大于intset编码，则直接返回0
2. 调用intsetSearch，判断如果intset的元素个数为0，则直接返回0
3. 如果元素值大于最大值或小于最小值，则返回0
4. 使用二分查找法查找元素
5. 找到返回1，未找到返回0

### intsetAdd

添加元素：

1. 如果待添加元素值编码大于intset编码，则进行编码升级
2. 调用intsetSearch，如果能找到待添加的值则返回
3. 调用intsetResize扩展intset
4. 挪动现有元素空出插入位置
5. 插入新值，length+1

### intsetRemove

删除元素：

1. 如果待删除元素值编码大于intset编码，则直接返回
2. 调用intsetSearch，如果不能找到则返回
3. 找到该值在intset中的位置
4. 如果pos&#x3c;len-1，则将intset的值从pos+1移到pos
5. 否则resize intset len-1 并更新len的值为len-1

## 5.3、编码升级

### 升级原因

待添加元素值编码大于intset编码，则进行编码升级，要进行扩容

### intsetUpgradeAndAdd

编码升级并添加：

1. 调用intsetResize按新的encoding扩展空间
2. 重新设置数据
3. 判断新插入的值是否小于0
4. 是，则插入头部
5. 否，则插入尾部
6. 插入新值，并将长度加1

## 5.4、设计思想和优势

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/ea0b185c99614c8a8875b88c62e9e8b2.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/ce56ee26f25f4376aaabe3551169d8f4.png)

1. **整数的存储与处理**
   encoding有范围、是值，可直接比较
2. **有序集合的二分法查找**
   二分法：有序 时间复杂度log(N)，找到pos
3. **编码升级并添加，要么插入第一个，要么插入最后一个**
   原因：要插入值的encoding的值大于当前encoding的值
   插入操作：不需要pos，小于0插头、大于0插尾

# 六、快速列表的源码分析

## 6.1、数据存储

快速列表是List的底层实现

快速列表(quicklist)是一个双向链表，链表中的每个节点是一个ziplist

双向链表

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/d238e7e553d7440e95599b8868e5986f.png)

quickList的数据存储由quicklist和quicklistNode构成

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/9f27bcdbf43f48908e96265dd1f505b9.png)

quicklist.h

### quicklist

```c
typedef struct quicklist {
    quicklistNode *head;
    quicklistNode *tail;
    unsigned long count;        /* total count of all entries in all ziplists */
    unsigned long len;          /* number of quicklistNodes */
    int fill : QL_FILL_BITS;              /* fill factor for individual nodes */
    unsigned int compress : QL_COMP_BITS; /* depth of end nodes not to compress;0=off */
    unsigned int bookmark_count: QL_BM_BITS;
    quicklistBookmark bookmarks[];
} quicklist;
```

* *head：头节点
* *tail：尾节点
* count:所有ziplist数据项的个数总和
* len:节点个数
* fill:ziplist的最大大小，当超出了这个配置，就会新建一个压缩列表
  ```
  fill>0 : 每个ziplist里能放多少个entry

  fill<0 :

  fill=-1

  ziplist大小为4K

  fill=-2

  ziplist大小为8K

  fill=-3

  ziplist大小为16K

  fill=-4

  ziplist大小为32K

  fill=-5

  ziplist大小为64K
  ```
* compress：结点压缩深度，两端各有compress个节点不压缩
* bookmark_count：bookmarks数组的大小
* bookmarks[]：快速列表用来重新分配内存空间时使用的数组，不使用时不占用空间

### quicklistNode

```
typedef struct quicklistNode {
    struct quicklistNode *prev;
    struct quicklistNode *next;
    unsigned char *zl;
    unsigned int sz;             /* ziplist size in bytes */
    unsigned int count : 16;     /* count of items in ziplist */
    unsigned int encoding : 2;   /* RAW==1 or LZF==2 */
    unsigned int container : 2;  /* NONE==1 or ZIPLIST==2 */
    unsigned int recompress : 1; /* was this node previous compressed? */
    unsigned int attempted_compress : 1; /* node can't compress; too small */
    unsigned int extra : 10; /* more bits to steal for future usage */
} quicklistNode;
```

* *prev：指向前节点
* *next：指向后节点
* *zl：指向保存的数据
* sz：ziplist的总大小（压缩前）
* count：ziplist里面包含的数据项个数
* encoding：是否被压缩 1:没有压缩  2: 压缩
* container：2 表示使用ziplist作为数据容器
* recompress：压缩标志 数据暂时解压设置为1 ，再重新压缩
* attempted_compress: 测试时使用
* extra：扩展字段，没有启用

### quicklistEntry

ziplist中的节点

```c
typedef struct quicklistEntry {
    const quicklist *quicklist;
    quicklistNode *node;
    unsigned char *zi;
    unsigned char *value;
    long long longval;
    unsigned int sz;
    int offset;
} quicklistEntry;
```

* *quicklist：当前元素所在的quicklist
* *node:当前元素所在的quicklistNode
* *zi: 当前元素所在的ziplist
* *value：字符串类型数据
* longval：整数类型数据
* sz：该节点大小
* offset：该节点是ziplist的第几个节点

### quicklistIter

遍历迭代器

```c
typedef struct quicklistIter {
    const quicklist *quicklist;
    quicklistNode *current;
    unsigned char *zi;
    long offset; /* offset in current ziplist */
    int direction;
} quicklistIter;
```

* *quicklist：当前元素所在的quicklist
* *current: 当前元素所在的quicklistNode
* *zi: 当前元素所在的ziplist
* offset：该节点是ziplist的第几个节点
* direction：迭代方向

## 6.2、压缩与解压缩

### quicklistLZF

对ziplist进行压缩(LZF)，quicklistNode节点指向结构为quicklistLZF

```c
typedef struct quicklistLZF {
    unsigned int sz; /* LZF size in bytes*/
    char compressed[];
} quicklistLZF;
```

* sz：压缩后ziplist的大小（字节数）
* compressed[]：柔性数组，存储数据（字节数组）

#### 压缩算法：LZF

Redis采用的压缩算法是LZF，这是一种无损压缩算法

* 将数据分成多个片段
* 每个片段有两个部分：解释字段和数据字段

#### 解释字段

* 字面型：1个字节  数据长度由后5位
* 简短重复型：2个字节  无数据字段  记录重复的起始点和重复的长度  &#x3c;8字节
* 批量重复型：3个字节   无数据字段 记录重复的起始点和重复的长度  >8字节

#### 数据压缩

* 计算重复字节的长度及位置
* 通过hash表来判断是否重复数据

### lzf_compress

lzf_c.c

数据压缩：

1. 判断是否有处理数据
2. 有数据，则计算该元素及后面2个元素的hash值
3. 计算元素在hash表中的位置
4. 如果该位置有值，则说明出现过该数据
5. 统计重复长度，将重复长度和重复位置的偏移量写入
6. 如果len&#x3c;7，输出位为偏移量右移8位加长度左移5位
7. 否则输出位为偏移量右移8位加7左移5位
8. 输出位等于长度-7
9. 更新hash表
10. 将剩余数据写入输出数组
11. 返回压缩后的数据长度

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/a6f7527360c844c69b6eac4158f13398.png)

### lzf_decompress

lzf_d.c

数据解压缩：

1. 判断是否有处理数据
2. 获得待处理数据的首字节
3. 如果数值小于32，则是非压缩数据，直接读取拷贝
4. 否则是压缩数据，计算重复长度和位置
5. 再进行数据拷贝

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/510d50292d0e4e3db0b659b54e301689.png)

## 6.3、API解析

quicklist.c

### quicklistCreate

创建快速列表：

1. 申请空间
2. 根据长度申请内存
3. 初始化头节点，各个字段赋初始值
4. 返回快速列表首地址

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/c6e5627b7a364b0797e4643ecd605a2d.png)

### quicklistPush

添加元素：

1. 如果插入位设置为head(0)
2. 则调用quicklistPushHead，从头部添加元素
3. 如果插入位设置为tail(-1)
4. 则调用quicklistPushTail，从尾部添加元素

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/7a494533a7654882bb7a259f2576797e.png)

#### quicklistPushHead

从头部插入数据：

1. 如果原有的head节点可插入，则利用ziplist接口进行插入
2. 否则新建quicklistNode节点和新建该节点下的ziplist
3. 再利用ziplist接口进行插入
4. 将新建的quicklistNode插入到quicklist中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/c8359656287f47b6ae2ba59e32488a3f.png)

#### quicklistPushTail

从尾部插入数据：

1. 如果原有的tail节点可插入，则利用ziplist接口进行插入
2. 否则新建quicklistNode节点和新建该节点下的ziplist
3. 再利用ziplist接口进行插入
4. 将新建的quicklistNode插入到quicklist中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/c44c3c26f17940a8aa102404c1f2d54f.png)

### _quicklistNodeAllowInsert

是否允许插入节点：

1. 根据ziplist结构，如果长度小于254使用1字节存储长度，否则使用5字节存储长度
2. 如果长度小于64，则存储长度自增1，如果长度&#x3c;16384,则存储长度自增2，否则存储长度自增5
3. 计算新的长度
4. 调用_quicklistNodeSizeMeetsOptimizationRequirement方法，判断新的长度是否超过fill，如果超过则不允许插入
5. _quicklistNodeSizeMeetsOptimizationRequirement 比较new_sz和fill，如果fill>=0返回0，否则返回-fill对应optimization_level的大小与sz对比
6. 如果quicklistNodeSizeMeetsOptimizationRequirement返回1，则允许插入
7. 否则判断如果count&#x3c;fill，则允许插入
8. 否则不允许插入

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/235d8a10ae4142fbae95ab4bcca10ce7.png)

### quicklistDelEntry

删除元素：

1. 赋值前一个和后一个节点
2. 调用quicklistDelIndex，删除指定位置的元素
3. 更新迭代器和偏移量

#### quicklistDelIndex

1. 调用ziplistDelete，删除ziplist中指定位置的元素
2. node的count自减
3. 如果count减为0，则调用quicklistDelNode，删除该node
4. 否则更新该node的大小
5. quicklist的count自减

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/58c2e0c168ab4319b16e1af4775f994b.png)

### quicklistPop

弹出元素：

1. 调用quicklistPopCustom弹出并返回数据
2. 判断弹出位置，如果是从头弹出则指向头，否则指向尾
3. 如果是从头弹出，则通过ziplist接口获得头节点的第一个元素
4. 否则获得尾节点的最后一个元素
5. 删除该元素
6. 返回元素数据

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/e164947c4903437bbe1a59e6e9882b2c.png)

### quicklistNext

获取迭代器指向的下一个元素：

1. 如果是第一次查询，则获取当前索引的ziplist
2. 否则如果迭代方向为从头开始迭代，则向后遍历
3. 如果迭代方向是从尾开始则向前遍历
4. 指向ziplist的下一个元素
5. 找到元素，获得数据
6. 如果当前节点没有元素，则跳到下一个节点查询

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/a36d82b69515484f9c5d1e8487eb1231.png)

## 6.4、设计思想与优势

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/6ae12edf80864934b3b04f742ac635bb.png)

1. 将一个长ziplist拆分为多个短的ziplist
   ziplist：级联更新
   折中：空间效率和时间效率上的折中
2. 压缩中间节点，LZF无损压缩进一步的节省空间
3. quicklistEntry可以直接访问到数据
   数据被层层包装，有一个直接访问的方式

# 七、Stream的源码分析

## 7.1、Listpack数据结构

### Stream概述

Stream是消息队列，主要由消息、生成者、消费者、消费组4部分构成

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/2e9c5e46b92a4ee9af90170d4d209cc6.png)

### Listpack

Listpack：紧凑列表

A list of String serilization format : 将一个字符串列表序列化存储

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/d702482fd1f24e758c4e22dc97713411.png)

* Total Bytes :  整个listpack空间大小
* Num Elem :  listpack中的元素(Entry)个数
* Entry: 具体的元素
* End：结束标志(0xFF)

连续的存储空间：

| 存储     | 寻址方式                                    |
| -------- | ------------------------------------------- |
| 数组     | 存的对象都一样 ，首地址+偏移量---->数组下标 |
| ziplist  | prevlen  上一个节点的长度 计算地址          |
| listpack | backlen  当前节点的长度                     |

### 节点Entry

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/1b6cf464ef51474086674098a537d385.png)

* Encoding：元素编码，编码 1字节 （数据类型+数据长度）
* Content :  内容，整数或字符串
* backlen：元素长度（Encoding+content），1到5字节长 ，用于从后向前遍历

## 7.2、Rax树数据结构

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/4e30503771fc43bfba402f5173db4418.png)

左侧是前缀树

每个节点一个字符

字符串相同则使用同一个节点

缺点：

1、每个节点一个字符，占空间

2、检索效率不高

* Rax树也叫基数树，不仅可以存储字符串，还可以为这个字串设置一个值，也就是key-value。
* Rax树可以对键的内容进行压缩。
* 某个节点可以设置为key，该节点所有的父节点组成key，但不包含该节点。
* Rax树中某个节点有相同的内容，则只需保存一份。
* 多个节点只有一个子节点，则可以压缩到一个节点中。

### 优化点

* 每个节点存字符串
* 字符串还可以压缩
* 多节点只有一个子节点时可压缩到一个节点中
* 还可以带value，存储键值对

**比较复杂的Rax树**

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/3bbf25b08aa347698ebd670f228ea1da.png)

**键值对**

axy--->data1

axz---->data3

bp----->data2

cdef---->data4

cdefg---->data5

**节点压缩：**

abc

yz

### Rax

rax.h

```c
typedef struct rax {
    raxNode *head;
    uint64_t numele;
    uint64_t numnodes;
} rax;
```

* raxNode *head：头（根）节点指针
* numele：元素个数（key的个数）
* numnodes：节点个数

### RaxNode

```c
typedef struct raxNode {
    uint32_t iskey:1;   
    uint32_t isnull:1;  
    uint32_t iscompr:1;   
    uint32_t size:29; 
    unsigned char data[];
} raxNode;
```

* iskey：是否是key  该节点是否为key (iskey=1 表示该节点的父节点以以上为key)
* isnull：是否value为空
  iskey=1 isnull=0  data[] 的value指针(value_ptr)不为空
  iskey=1 isnull=1  data[] 的value指针(value_ptr)为空，无指针
  iskey=0 isnull=0  data[] 的value指针没有，但子节点有可能有
  iskey=0 isnull=1 data[] 的value指针(value_ptr)为空，无指针
* iscompr：是否为压缩节点
* size：子节点数或压缩字符串长度  非压缩：子节点数  压缩：字符串长度
* data[]：柔性数组，当前节点的字符串及子节点的指针，key对应的value指针

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/0ef0c791fbdc4360b8f2851138f0afbb.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/eda3578771d740c28c45dd4a246d62ab.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/b1201a59d00b441b8fa8f097769e81a1.png)

### raxStack

raxStack用于存储从根节点到当前节点的路径

```
typedef struct raxStack {
    void **stack;
    size_t items, maxitems;
    void *static_items[RAX_STACK_STATIC_ITEMS];
    int oom;
} raxStack;
```

* **stack：用于记录路径的指针
* items：stack指向的空间的已用空间
* maxitems：stack指向的空间的最大空间
* *static_items[32]：用于存储路径
* oom：当前栈是否出现过内存溢出

### raxIterator

raxIterator用于遍历Rax树中所有的key和value

```
typedef struct raxIterator {
	int flags;
	rax *rt;
	unsigned char *key;
	void *data;
	size_t key_len;
	size_t key_max;
	unsigned char key_static_string[RAX_ITER_STATIC_LEN];
	raxNode *node;
	raxStack stack;
	raxNodeCallback node_cb;
} raxIterator;
```

* flags：当前迭代标志
* *rt：当前迭代器对应的rax树
* *key：当前迭代器遍历到的key
* *data：当前key对应的value
* key_len：key指向空间的已用空间
* key_max：key指向空间的最大空间
* key_static_string[128]：key的默认存储空间
* *node：当前key所在的raxNode
* stack：从根节点到当前节点的路径
* node_cb：节点回调函数，通常为空

## 7.3、Stream数据结构

Stream依赖于Rax和listpack，相关数据结构如下：

* streamID：消息ID
* stream：消息
* streamCG：消费组
* streamConsumer：消费者
* streamNACK：未确认消息
* streamIterator：消息迭代器

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/556423f0430843d9a0db82b717fd30d9.png)

```
stream.h
```

### streamID

消息ID，以每个消息创建的时间+序号构成

```c
typedef struct streamID {
    uint64_t ms;        /* Unix time in milliseconds. */
    uint64_t seq;       /* Sequence number. */
} streamID;
```

* ms：消息创建时间
* seq：序号

ms（1970年1月1日至今的毫秒数）64+seq(序号)64

一共是128位

### stream

Stream以消息ID为key，以listpack为Value的Rax树

```c
typedef struct stream {
    rax *rax;   
    uint64_t length;  
    streamID last_id;  
    rax *cgroups;  
} stream;
```

* *rax：存储消息，以消息ID为key，消息内容为值(listpack)
* length：当前stream中的消息个数
* last_id：最后插入的消息id
* *cgroup：当前stream相关消费组

### streamCG

消费组，每个stream有多个消费组

```
typedef struct streamCG {
	streamID last_id;
	rax *pel;
	rax *consumers;
} streamCG;
```

* last_id：消费组确认的最后一个消息ID
* *pel：消费组尚未确认的消息，以消息ID为key，streamNACK为值的Rax树
* *consumers：消费组中的消费者，以消费者名称为key，streamConsumer为值的Rax树

### streamConsumer

消费者，每个streamCG有多个消费者

```
typedef struct streamConsumer {
	mstime_t seen_time;
	sds name;
	rax *pel;
} streamConsumer;
```

* seen_time：该消费者最后一次活跃的时间
* name：消费者名称
* *pel：该消费者未确认消息，以消息ID为key，streamNACK为值的rax树

### streamNACK

未确认消息，包括消费组和消费者

```
typedef struct streamNACK {
	mstime_t delivery_time;
	uint64_t delivery_count;
	streamConsumer *consumer;
} streamNACK;
```

* delivery_time：消息最后发送给消费方的时间
* delivery_count：消息已经发送的次数
* *consumer：该消息当前归属的消费者

### listpack

stream的消息内容存储在listpack中，作为值

* 消息的每个字段都是一个entry
* 每个listpack可以存储多个消息
* 每个消息会占用多个listpack Entry

### master entry

每个listpack在创建时，会根据第一个插入的消息构建master entry

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/3016d4f73bed49bc93599d695a6001e4.png)

* count：当前listpack的消息个数
* deleted：当前listpack删除的消息个数
* num-fields：field的个数
* field-1....field-N：当前listpack插入第一个消息的field域
* 0：master entry结束

### 消息存储(消息域与master entry相同)

存储消息时，如果该消息的field域与master entry域相同，则不再存储field域

* flags：消息标志位
* streamID.ms和streamID.seq：该消息ID减去master entry id后的值
* value-1....value-N :消息的每个field域对应的内容
* lp-count：消息占用listpack的元素个数（3+N）

### 消息存储(消息域与master entry不同)

存储消息时，如果该消息的field域与master entry域不同，则要存储field域

* flags：消息标志位
* streamID.ms和streamID.seq：该消息ID减去master entry id后的值
* num-fields：该消息field域个数（与master entry不同）
* field-1 value-1 ....field-N value-N：消息的域值对
* lp-count：消息占用listpack的元素个数（4+2N）

```
xadd msg1 * name zhangf age 20 
```

master entry

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/52663e7658f54bdcaadbeea37b1e1107.png)

消息域

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/d2d6561efe6c431da64554b76ce0840a.png)

```
xadd msg2 sex 1 
```

消息域

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/501a4a92ccc641a1a22fce6311007b43.png)

### streamIterator

迭代器，用于消息的遍历

```
typedef struct streamIterator {
	stream *stream;
	streamID master_id;
	uint64_t master_fields_count;
	unsigned char *master_fields_start;
	unsigned char *master_fields_ptr;
	int entry_flags;
	int rev;
	uint64_t start_key[2];
	uint64_t end_key[2];
	raxIterator ri;
	unsigned char *lp;
	unsigned char *lp_ele;
	unsigned char *lp_flags;
	unsigned char field_buf[LP_INTBUF_SIZE];
	unsigned char value_buf[LP_INTBUF_SIZE];
} streamIterator;
```

* *stream：当前迭代器正在遍历的消息流
* master_id：master entry的消息ID
* master_fields_count：master entry 中的field域个数
* *master_fields_start：master entry 中的field域存储的首地址
* *master_fields_ptr：指向field域地址的具体位置
* entry_flags：当前遍历消息的标志位
* rev：当前迭代器的方向
* start_key，end_key：迭代器处理的消息ID范围
* ri：rax迭代器，遍历rax中所有的key
* *lp：指向当前listpack
* *lp_ele：指向当前正在遍历的元素
* *lp_flags：指向当前消息的flag
* field_buffer[21]：读取field域的数据缓存
* value_buffer[21]：读取value的数据缓存

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/92ba57007f614e18bbfcb5adf2ef2a5d.png)

## 7.4、Listpack的实现

listpack.c

### lpNew

listpack初始化：

1. 申请空间
2. 初始化属性
3. 返回首地址

### lpInsert

在任意位置插入元素、在末尾插入元素，底层调用lpInsert、删除和修改也是底层调用lpInsert、删除是用空字串替换，修改是用待替换的字串替换。

插入元素：

1. 如果插入元素为null，则where设为replace(实际为删除)
2. 如果where为after，则需找到其后驱节点，并将where设为before
3. 如果插入元素不为null，则对元素进行编码，返回编码类型，并存储长度
4. 计算backlen和backlen_size
5. 如果where等于replace，则计算替换长度和替换的backlen
6. 计算新的listpack需要的空间(字节数)

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/34ccdcff0c484c969f2e9cce2d723f60.png)
7. 如果新的空间大于原来的空间，则需要申请新的空间
8. 如果是插入元素则将插入位置后的元素后移，否则调整替换元素大小
9. 如果新空间小于原来的空间(删除或替换为更小的元素)，则调整listpack空间
10. 将新元素插入到目标位(dst)，newp指针指向插入元素

    ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/81ea460339fa4c9a90d0cae2ec30dfc2.png)
11. 如果插入内容是数字，则将intenc写入目标位
12. 否则写入元素编码和元素内容
13. 写入backlen
14. 如果where不等于replace或ele是空，则更新numele
15. 更新totalBytes

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/80969ff0d95b404db54cbe6ad9a44e63.png)

### 获得entry的地址指针

#### lpFirst

获得第一个元素：

1. 跳过header
2. 如果没有元素，则返回null
3. 返回地址

#### lpLast

获得最后一个元素：

1. 获得结尾的前一个位置
2. 通过lpPrev()获得最后一个元素地址，并返回地址

#### lpNext

获得下一个元素：

1. 计算元素长度
2. 指针后移元素长度
3. 返回地址

#### lpPrev

获得上一个元素

1. 如果是头则返回null
2. 获得元素的len
3. lpDecodeBacklen：能够获得entry_len
4. lpEncodeBacklen:根据entry_len计算entry_len_size (1-5)
5. 获得元素的上一个元素地址并返回

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/c4d4878829fc4bd59f29c650401aafe5.png)

### listpack如何避免连锁更新

listpack每个entry记录自己的长度，当listpack新增或修改元素时，只是操作每个entry自己，不会影响到后续的元素的长度变化，这样就彻底的避免了连锁更新（级联更新）。

## 7.5、设计思想与优势

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/805b10983c7a4a859d821ef3cafa30b2.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/7386c69599384ef8b2ffab3bb79c1cfd.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1663578377037/55eae6280cbc4eef935306870da4660d.png)

1. Listpack是连续存储空间使用的升级
   是ziplist的升级
   避免级联更新、计算相对简单
2. Rax树是trie树的升级
   压缩、非压缩、kv
3. Stream以消息ID为key，以listpack为Value的Rax树
