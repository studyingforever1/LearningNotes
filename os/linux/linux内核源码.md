# Linux内核

## 系统结构

Linux内核是将计算机硬件资源通过系统调用接口分配调度给用户进程的中间层级

<img src=".\images\linux01.png" alt="image-20241024114750178" style="zoom:50%;" />

体系结构arch封装了对不同体系结构的不同代码，如x86 arm等

<img src=".\images\linux02.png" alt="image-20241028104613935" style="zoom: 67%;" />

### 内核源码目录结构

<img src=".\images\Linux内核源码组织.png" alt="Linux内核源码组织" style="zoom: 67%;" />



## 计算机启动

### BIOS加载

按下开机键的那一刻，在主板上提前写死的固件程序 **BIOS** 会将硬盘中**启动区的 512 字节**的数据，原封不动复制到**内存中的 0x7c00** 这个位置，并跳转到那个位置进行执行。只要硬盘中的 0 盘 0 道 1 扇区的 512 个字节的最后两个字节分别是 **0x55** 和 **0xaa**，那么 BIOS 就会认为它是个启动区。

> 所以对于我们理解操作系统而言，此时的 BIOS 仅仅就是个代码搬运工，把 512 字节的二进制数据从硬盘搬运到了内存中而已。**所以作为操作系统的开发人员，仅仅需要把操作系统最开始的那段代码，编译并存储在硬盘的 0 盘 0 道 1 扇区即可**。之后 BIOS 会帮我们把它放到内存里，并且跳过去执行。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\BIOS.webp" style="zoom: 67%;" />

**bootsect.s**

在Linux 0.11版本下，这个 bootsect.s 会被编译成二进制文件，存放在启动区的第一扇区。由 BIOS 搬运到内存的 0x7c00 这个位置，而 CPU 也会从这个位置开始，不断往后一条一条语句无脑地执行下去。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\bootsect.webp" style="zoom: 67%;" />

```assembly
# bootsect.s
mov ax,0x07c0
mov ds,ax
#这段代码是用汇编语言写的，含义是把 0x07c0 这个值复制到 ax 寄存器里，再将 ax 寄存器里的值复制到 ds 寄存器里。那其实这一番折腾的结果就是，让 ds 这个寄存器里的值变成了 0x07c0。
```

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\bootsect01.webp" style="zoom: 67%;" />

ds 是一个 16 位的段寄存器，具体表示数据段寄存器，在内存寻址时充当段基址的作用。就是当我们之后用汇编语言写一个内存地址时，实际上仅仅是写了偏移地址，比如：

```assembly
mov ax, [0x0001]
#相当于
mov ax, [ds:0x0001]
```

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\bootsect02.webp" style="zoom:67%;" />



将内存地址 0x7c00 处开始往后的 512 字节的数据，原封不动复制到 0x90000 处。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\bootsect03.webp" style="zoom:80%;" />









## 内核启动



```c
// init/main.c


asmlinkage __visible void __init start_kernel(void)
{
        char *command_line;
        char *after_dashes;

        set_task_stack_end_magic(&init_task);
        smp_setup_processor_id();
        debug_objects_early_init();

        cgroup_init_early();

        local_irq_disable();
        early_boot_irqs_disabled = true;

        /*
         * Interrupts are still disabled. Do necessary setups, then
         * enable them.
         */
        boot_cpu_init();
        page_address_init();
        pr_notice("%s", linux_banner);
        early_security_init();
        setup_arch(&command_line);
        setup_boot_config(command_line);
        setup_command_line(command_line);
        setup_nr_cpu_ids();
        setup_per_cpu_areas();
        smp_prepare_boot_cpu();        /* arch-specific boot-cpu hooks */
        boot_cpu_hotplug_init();

        build_all_zonelists(NULL);
        page_alloc_init();

        pr_notice("Kernel command line: %s\n", saved_command_line);
        /* parameters may set static keys */
        jump_label_init();
        parse_early_param();
        after_dashes = parse_args("Booting kernel",
                               static_command_line, __start___param,
                               __stop___param - __start___param,
                               -1, -1, NULL, &unknown_bootoption);
        if (!IS_ERR_OR_NULL(after_dashes))
               parse_args("Setting init args", after_dashes, NULL, 0, -1, -1,
                         NULL, set_init_arg);
        if (extra_init_args)
               parse_args("Setting extra init args", extra_init_args,
                         NULL, 0, -1, -1, NULL, set_init_arg);

        /*
         * These use large bootmem allocations and must precede
         * kmem_cache_init()
         */
        setup_log_buf(0);
        vfs_caches_init_early();
        sort_main_extable();
        //初始化中断向量表IDT
        trap_init();
        mm_init();

        ftrace_init();

        /* trace_printk can be enabled here */
        early_trace_init();

        /*
         * Set up the scheduler prior starting any interrupts (such as the
         * timer interrupt). Full topology setup happens at smp_init()
         * time - but meanwhile we still have a functioning scheduler.
         */
        sched_init();
        /*
         * Disable preemption - early bootup scheduling is extremely
         * fragile until we cpu_idle() for the first time.
         */
        preempt_disable();
        if (WARN(!irqs_disabled(),
                "Interrupts were enabled *very* early, fixing it\n"))
               local_irq_disable();
        radix_tree_init();

        /*
         * Set up housekeeping before setting up workqueues to allow the unbound
         * workqueue to take non-housekeeping into account.
         */
        housekeeping_init();

        /*
         * Allow workqueue creation and work item queueing/cancelling
         * early.  Work item execution depends on kthreads and starts after
         * workqueue_init().
         */
        workqueue_init_early();

        rcu_init();

        /* Trace events are available after this */
        trace_init();

        if (initcall_debug)
               initcall_debug_enable();

        context_tracking_init();
        /* init some links before init_ISA_irqs() */
        early_irq_init();
        init_IRQ();
        tick_init();
        rcu_init_nohz();
        init_timers();
        hrtimers_init();
        softirq_init();
        timekeeping_init();

        /*
         * For best initial stack canary entropy, prepare it after:
         * - setup_arch() for any UEFI RNG entropy and boot cmdline access
         * - timekeeping_init() for ktime entropy used in rand_initialize()
         * - rand_initialize() to get any arch-specific entropy like RDRAND
         * - add_latent_entropy() to get any latent entropy
         * - adding command line entropy
         */
        rand_initialize();
        add_latent_entropy();
        add_device_randomness(command_line, strlen(command_line));
        boot_init_stack_canary();

        time_init();
        perf_event_init();
        profile_init();
        call_function_init();
        WARN(!irqs_disabled(), "Interrupts were enabled early\n");

        early_boot_irqs_disabled = false;
        local_irq_enable();

        kmem_cache_init_late();

        /*
         * HACK ALERT! This is early. We're enabling the console before
         * we've done PCI setups etc, and console_init() must be aware of
         * this. But we do want output early, in case something goes wrong.
         */
        console_init();
        if (panic_later)
               panic("Too many boot %s vars at `%s'", panic_later,
                     panic_param);

        lockdep_init();

        /*
         * Need to run this when irqs are enabled, because it wants
         * to self-test [hard/soft]-irqs on/off lock inversion bugs
         * too:
         */
        locking_selftest();

        /*
         * This needs to be called before any devices perform DMA
         * operations that might use the SWIOTLB bounce buffers. It will
         * mark the bounce buffers as decrypted so that their usage will
         * not cause "plain-text" data to be decrypted when accessed.
         */
        mem_encrypt_init();

#ifdef CONFIG_BLK_DEV_INITRD
        if (initrd_start && !initrd_below_start_ok &&
            page_to_pfn(virt_to_page((void *)initrd_start)) < min_low_pfn) {
               pr_crit("initrd overwritten (0x%08lx < 0x%08lx) - disabling it.\n",
                   page_to_pfn(virt_to_page((void *)initrd_start)),
                   min_low_pfn);
               initrd_start = 0;
        }
#endif
        setup_per_cpu_pageset();
        numa_policy_init();
        acpi_early_init();
        if (late_time_init)
               late_time_init();
        sched_clock_init();
        calibrate_delay();
        pid_idr_init();
        anon_vma_init();
#ifdef CONFIG_X86
        if (efi_enabled(EFI_RUNTIME_SERVICES))
               efi_enter_virtual_mode();
#endif
        thread_stack_cache_init();
        cred_init();
        fork_init();
        proc_caches_init();
        uts_ns_init();
        buffer_init();
        key_init();
        security_init();
        dbg_late_init();
        vfs_caches_init();
        pagecache_init();
        signals_init();
        seq_file_init();
        proc_root_init();
        nsfs_init();
        cpuset_init();
        cgroup_init();
        taskstats_init_early();
        delayacct_init();

        poking_init();
        check_bugs();

        acpi_subsystem_init();
        arch_post_acpi_subsys_init();
        sfi_init_late();

        /* Do the rest non-__init'ed, we're now alive */
        arch_call_rest_init();

        prevent_tail_call_optimization();
}
```



## x86架构中的常用寄存器

在x86架构的汇编语言中，`E` 和 `R` 前缀分别标识了不同大小的寄存器，它们对应于不同的处理器模式和数据宽度。

```assembly
# 16位
mov ax xxx
# 32位
mov eax xxx
# 64位
mov rax xxx
```

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\bootsect01.webp" style="zoom: 67%;" />

1. 通用寄存器：
   - **AX (Accumulator)**：累加器寄存器，用于算术和逻辑运算。
   - **BX (Base)**：基址寄存器，用于存储地址。
   - **CX (Count)**：计数寄存器，用于循环计数。
   - **DX (Data)**：数据寄存器，用于存储数据。
2. 段寄存器：
   - **CS (Code Segment)**：代码段寄存器，指向当前执行的代码段。
   - **DS (Data Segment)**：数据段寄存器，指向当前使用的数据段。
   - **SS (Stack Segment)**：堆栈段寄存器，指向当前使用的堆栈段。
   - **ES (Extra Segment)**：附加段寄存器，用于指向额外的数据段。
   - **TR (Task Register)**: 任务寄存器，用于指向TSS描述符
3. 指针寄存器：
   - **SP (Stack Pointer)**：堆栈指针寄存器，指向堆栈的顶部。
   - **BP (Base Pointer)**：基址指针寄存器，用于存储基址。
   - **SI (Source Index)**：源变址寄存器，用于存储源地址。
   - **DI (Destination Index)**：目的变址寄存器，用于存储目的地址。
4. 控制寄存器：
   - **IP (Instruction Pointer)**：指令指针寄存器，指向当前执行的指令。
   - **Flags (Status Flags)**：状态标志寄存器，用于存储条件码和其他状态信息。
5. 描述符表寄存器：
   - **GDTR (Global Descriptor Table Register)**：全局描述符表寄存器，指向全局描述符表。
   - **IDTR (Interrupt Descriptor Table Register)**：中断描述符表寄存器，指向中断描述符表。
6. 控制寄存器：
   - **CR3 (Page Directory Base Register)**：页目录基址寄存器，用于指向页目录表。



**ax**

在许多指令中作为累加器使用。常用于算术运算，如加法、减法、乘法和除法等。

**flags**

标志寄存器

**ds**

`ds`寄存器 段寄存器 数据段寄存器

<img src="C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20241205100432776.png" alt="image-20241205100432776" style="zoom:50%;" />

> **描述符索引（Descriptor Index）**：
>
> - 位数：13位（位15到位3）
> - 功能：这部分用于索引GDT或LDT中的段描述符。每个段描述符在GDT或LDT中都有一个唯一的索引值，描述符索引就是这个索引值。
>
> **TI（Table Indicator）**：
>
> - 位数：1位（位2）
>
> - 功能
>
>   ：指示段选择子指向的是GDT还是LDT。
>
>   - 0：指向GDT（全局描述符表）。
>   - 1：指向LDT（局部描述符表）。
>
> **RPL（Requestor Privilege Level）**：
>
> - 位数：2位（位1和位0）
>
> - 功能
>
>   ：表示请求者的特权级别。在保护模式下，每个段都有一个特权级别（DPL），RPL用于检查访问权限。
>
>   - 00：特权级别0（最高特权）。
>   - 01：特权级别1。
>   - 10：特权级别2。
>   - 11：特权级别3（最低特权）。

**gs**

`gs` 是 x86 架构中的一个段寄存器，通常用于存储特定于当前 CPU 的数据的基地址。

```assembly
mov ax, gs:[x] /*常用于读取/写入当前CPU专属数据到内核.data段中*/
```















## 系统调用









## 中断

**一个中断的起末会经历设备，中断控制器，CPU 三个阶段：设备产生中断信号，中断控制器翻译信号，CPU 来实际处理信号**。

> `中断` 是为了解决外部设备完成某些工作后通知CPU的一种机制（譬如硬盘完成读写操作后通过中断告知CPU已经完成）。早期没有中断机制的计算机就不得不通过轮询来查询外部设备的状态，由于轮询是试探查询的（也就是说设备不一定是就绪状态），所以往往要做很多无用的查询，从而导致效率非常低下。由于中断是由外部设备主动通知CPU的，所以不需要CPU进行轮询去查询，效率大大提升。
>
> 从物理学的角度看，中断是一种电信号，由硬件设备产生，并直接送入中断控制器（如 8259A）的输入引脚上，然后再由中断控制器向处理器发送相应的信号。处理器一经检测到该信号，便中断自己当前正在处理的工作，转而去处理中断。此后，处理器会通知 OS 已经产生中断。这样，OS 就可以对这个中断进行适当的处理。不同的设备对应的中断不同，而每个中断都通过一个唯一的数字标识，这些值通常被称为中断请求线。

### 中断分类

中断可分为同步（synchronous）中断和异步（asynchronous）中断：

- 同步中断是当指令执行时**由 CPU 控制单元产生**，之所以称为同步，是因为只有在一条指令执行完毕后 CPU 才会发出中断，而不是发生在代码指令执行期间，比如**系统调用**。
- 异步中断是指由其他**硬件设备依照 CPU 时钟信号随机产生**，即意味着中断能够在指令之间发生，例如键盘中断。

根据 Intel 官方资料，同步中断称为异常（exception），异步中断被称为中断（interrupt）。异常即软中断，中断即硬件中断。

中断可分为 `可屏蔽中断`（Maskable interrupt）和 `非屏蔽中断`（Nomaskable interrupt）。异常可分为 `故障`（fault）、`陷阱`（trap）、`终止`（abort）三类。

从广义上讲，中断可分为四类：`中断`、`故障`、`陷阱`、`终止`。这些类别之间的异同点请参看表。

表：中断类别及其行为

| 类别 | 原因              | 异步/同步 | 返回行为             |
| ---- | ----------------- | --------- | -------------------- |
| 中断 | 来自I/O设备的信号 | 异步      | 总是返回到下一条指令 |
| 陷阱 | 有意的异常        | 同步      | 总是返回到下一条指令 |
| 故障 | 潜在可恢复的错误  | 同步      | 返回到当前指令       |
| 终止 | 不可恢复的错误    | 同步      | 不会返回             |

X86 体系结构的每个中断都被赋予一个唯一的编号或者向量（8 位无符号整数）。非屏蔽中断和异常向量是固定的，而可屏蔽中断向量可以通过对中断控制器的编程来改变。

异常分为**CPU异常**和**指令异常**

异常分为3类：

- 陷阱(trap)，陷阱并不是错误，而是想要陷入内核来执行一些操作，中断处理完成后继续执行之前的下一条指令，即 **指令异常**
- 故障(fault)，故障是程序遇到了问题需要修复，问题不一定是错误，如果问题能够修复，那么中断处理完成后会重新执行之前的指令，如果问题无法修复那就是错误，当前进程将会被杀死。即 **CPU异常**
- 中止(abort)，系统遇到了很严重的错误，无法修改，一般系统会崩溃。即 **CPU异常**

### 中断控制

中断控制从8259A PIC中断控制器到82093AA APIC中断控制器再到消息信号中断MSI



**ISA和PCI**

ISA（Industry Standard Architecture）中断是早期PC系统中用于处理硬件设备请求的一种机制。ISA总线上的每个设备都会被分配一个中断请求线（Interrupt Request Line，简称IRQ），当设备需要CPU的服务时，它会触发相应的IRQ，从而中断CPU的当前操作，使CPU能够处理该设备的请求。

> 在现代PC中，ISA总线已经被更先进的标准如PCI（Peripheral Component Interconnect）和PCIe(PCI Express)所取代，并且多个设备可以共享同一个IRQ线。
>
> | 特性       | ISA          | PCI           | PCIe        |
> | ---------- | ------------ | ------------- | ----------- |
> | 数据宽度   | 8/16 位      | 32/64 位      | 多个 lane   |
> | 频率       | 8 MHz        | 33/66 MHz     | 2.5-64 GT/s |
> | 传输速率   | 最大 8 MB/s  | 最大 533 MB/s | 最大 4 GB/s |
> | 插槽数量   | 较大         | 中等          | 多种长度    |
> | 中断和 DMA | 易冲突       | 较少冲突      | 无冲突      |
> | 即插即用   | 不完全支持   | 支持          | 完全支持    |
> | 适用设备   | 声卡、网卡等 | 显卡、网卡等  | 显卡、SSD等 |
>
> - **ISA**：已基本被淘汰，仅在一些老旧系统中仍可能见到。
> - **PCI**：仍在一些旧系统和工业应用中使用，但逐渐被 PCIe 取代。
> - **PCIe**：广泛应用于现代计算机系统中，包括台式机、服务器、笔记本电脑等，支持高性能显卡、固态硬盘（SSD）、网络适配器等多种设备。

ISA系统中共有16个中断请求线，编号从IRQ 0到IRQ 15。下面是这些中断请求线的标准分配情况：

- **IRQ 0** - 系统定时器 (通常映射到向量号32)
- **IRQ 1** - 键盘 (通常映射到向量号33)
- **IRQ 2** - 用于级联第二个PIC（可编程中断控制器），实际上管理着IRQ 8 到 IRQ 15 (通常映射到向量号34)
- **IRQ 3** - 串行端口COM2 (通常映射到向量号35)
- **IRQ 4** - 串行端口COM1 (通常映射到向量号36)
- **IRQ 5** - 并行端口LPT1 或者声卡 (通常映射到向量号37)
- **IRQ 6** - 软盘控制器 (通常映射到向量号38)
- **IRQ 7** - 并行端口LPT2 (通常映射到向量号39)
- **IRQ 8** - 实时时钟RTC (通常映射到向量号40)
- **IRQ 9** - 可用于PCI设备，也常用于网络适配器 (通常映射到向量号41)
- **IRQ 10** - 可用于PCI设备 (通常映射到向量号42)
- **IRQ 11** - 可用于PCI设备 (通常映射到向量号43)
- **IRQ 12** - 鼠标PS/2接口 (通常映射到向量号44)
- **IRQ 13** - 数学协处理器 (通常映射到向量号45)
- **IRQ 14** - IDE主控制器 (通常映射到向量号46)
- **IRQ 15** - IDE次控制器 (通常映射到向量号47)

#### 可编程中断控制器8259A

X86计算机的 CPU 为中断只提供了两条外接引脚：NMI 和 INTR。其中 NMI 是不可屏蔽中断，它通常用于电源掉电和物理存储器奇偶校验；INTR是可屏蔽中断，可以通过设置中断屏蔽位来进行中断屏蔽，它主要用于接受外部硬件的中断信号，这些信号由中断控制器传递给 CPU。

传统的 PIC（Programmable Interrupt Controller，可编程中断控制器）是由两片 8259A 风格的外部芯片以“级联”的方式连接在一起。每个芯片可处理多达 8 个不同的 IRQ。因为从 PIC 的 INT 输出线连接到主 PIC 的 IRQ2 引脚，所以可用 IRQ 线的个数达到 15 个

![](.\images\8259A.png)



**8259A的工作原理**



![](.\images\中断控制器01.png)

- **中断请求寄存器** IRR (Interrupt Request Register)用来保存中断请求输入引脚上所有请求，寄存器的8个比特位（D7—D0）分别对应引脚IR7—IR0。

- **中断屏蔽寄存器** IMR (Interrup Mask Register)用于保存被屏蔽的中断请求线对应的比特位，哪个比特位被置1就屏蔽哪一级中断请求。即IMR对IRR进行处理，其每个比特位对应IRR的每个请求比特位。对高优先级输入线的屏蔽并不会影响低优先级中断请求线的输入。

  在固定优先级方式中，IR7～IR0 的中断优先级是由系统确定的。优先级由高到低的顺序是：IR0, IR1, IR2, …, IR7。其中，IR0的优先级最高，IR7的优先级最低。

  在自动循环优先权方式中，IR7～IR0的优先级别是可以改变的，而且是自动改变。其变化规律是：当某一个中断请求`IRi`服务结束后，该中断的优先级自动降为最低，而紧跟其后的中断请求`IR(i＋1)`的优先级自动升为最高。

- **优先级解析器**PR(Priority Resolver) 用于确定 IRR 中所设置比特位的优先级，选通最高优先级的中断请求到ISR (In-Service Register)中。

- **正在服务寄存器**ISR中保存着正在接受服务的中断请求。



**工作原理**

来自各个设备的中断请求线分别连接到8259A的IR0—IR7引脚上。当这些引脚上有一个或多个中断请求信号到来时，中断请求寄存器 IRR 中相应的比特位被置位锁存。此时若中断屏蔽寄存器 IMR 中对应位被置位，则相应的中断请求就不会送到优先级解析器中。未屏蔽的中断请求被送到优先级解析器之后，优先级最高的中断请求会被选出。此时8259A就会向CPU发送一个INT信号，而CPU则会在执行完当前的一条指令之后向8259A发送一个INTA（INTERRUPT ACKNOWLEDGE）来响应中断信号。8259A在收到这个响应信号之后就会把所选出的最高优先级中断请求保存到正在服务寄存器ISR中，即ISR中对应比特被置位。与此同时，中断请求寄存器 IRR 中的对应比特位被复位，表示该中断请求开始被处理。此后，CPU会向8259A发出第2个INTA脉冲信号，该信号用于通知 8259A送出中断号。在该脉冲信号期间，8259A就会把一个代表中断号的8位数据发送到数据总线上供CPU读取。

到此为止，CPU中断周期结束。如果8259A使用的是自动结束中断(AEOI，Automatic End of Interrupt) 方式，那么在第2个 INTA 脉冲信号的结尾处正在服务寄存器 ISR 中的当前服务中断比特位就会被复位。若8259A 使用非自动结束方式，那么在中断服务程序结束时，程序就需要向8259A发送一个结束中断（EOI）命令以复位 ISR 中的比特位。如果中断请求来自级联的第2个8259A芯片，那么就需要向两个芯片都发送EOI命令。此后8259A就会去判断下一个最高优先级的中断，并重复上述处理过程。


**中断嵌套方式**

8259A的中断嵌套方式分为普通嵌套（normal nested mode）和特殊完全嵌套（The Special Fully Nest Mode）两种。

- **普通嵌套方式**
  也叫做完全嵌套或者普通完全嵌套。此方式是8259A在初始化时默认选择的方式。其特点是：IR0优先级最高，IR7优先级最低。在CPU中断服务期间，若有新的中断请求到来，只允许比当前服务的优先级更高的中断请求进入，对于“同级”或“低级”的中断请求则禁止响应。
- **特殊完全嵌套方式**
  其特点是：IR7～IR0 的优先级顺序与普通嵌套方式相同；不同之处是在CPU中断服务期间，除了允许高级别中断请求进入外，还允许同级中断请求进入，从而实现了对同级中断请求的特殊嵌套。





#### 高级可编程中断控制器（82093AA APIC）

8259A 只适合单 CPU 的情况，为了充分挖掘 SMP 体系结构的并行性，能够把中断传递给系统中的每个 CPU 至关重要。基于此理由，Intel 引入了一种名为 I/O 高级可编程控制器的新组件，来替代老式的 8259A 可编程中断控制器。

APIC 分成两部分 LAPIC 和 IOAPIC，前者 LAPIC 位于 CPU 内部，每个 CPU 都有一个 LAPIC，后者 IOAPIC 与外设相连。外设发出的中断信号经过 IOAPIC 处理之后发送某个或多个 LAPIC，再由 LAPIC 决定是否交由 CPU 进行实际的中断处理。

- **LAPIC**，主要负责传递中断信号到指定的处理器。
- **I/O APIC**，主要是收集来自 I/O 装置的 Interrupt 信号且在当那些装置需要中断时发送信号到本地 APIC。

![](.\images\APIC01.png)

##### IOAPIC

IOAPIC (I/O Advanced Programmable Interrupt Controller) 属于 Intel 芯片组的一部分，也就是说通常位于南桥。

IOAPIC 主要负责接收外部的硬件中断，将硬件产生的中断信号翻译成具有一定格式的消息，然后通过总线将消息发送给一个或者多个 LAPIC。

像 PIC 一样，连接各个设备，负责接收外部 IO 设备 (Externally connected I/O devices) 发来的中断，典型的 IOAPIC 有 24 个中断输入管脚(INTIN0~INTIN23)，没有优先级之分。

<img src=".\images\IOAPIC.jpg" style="zoom:50%;" />

在某个管脚收到中断后，会进行查询RTE，把中断转换为中断消息转发给对应的 LAPIC 。

**IOAPIC中的寄存器**

IOAPIC 的寄存器同样是通过映射一片物理地址空间实现的

- IOREGSEL(I/O REGISTER SELECT REGISTER): 选择要读写的寄存器
- IOWIN(I/O WINDOW REGISTER): 读写 IOREGSEL 选中的寄存器
- IOAPICVER(IOAPIC VERSION REGISTER): IOAPIC 的硬件版本
- IOAPICARB(IOAPIC ARBITRATION REGISTER): IOAPIC 在总线上的仲裁优先级
- IOAPICID(IOAPIC IDENTIFICATION REGISTER): IOAPIC 的 ID，在仲裁时将作为 ID 加载到 IOAPICARB 中
- IOREDTBL(I/O REDIRECTION TABLE REGISTERS): 有 0-23 共 24 个，对应 24 个引脚，每个长 64bit。当该引脚收到中断信号时，将根据该寄存器产生中断消息送给相应的 LAPIC，其中每一项简称RTE（Redirection Table Entry）。

<img src=".\images\重定向表项寄存器组.png" alt="image-20241122145656489" style="zoom:50%;" />

**中断转发**

当一个中断通过INTIN针脚输入到IOAPIC后，IOAPIC通过IOREDTBL寄存器组来查找对应的REDIRECTION TABLE REGISTERS寄存器，然后通过寄存器来获取对应的RTE，然后发送给LAPIC。

**重定向表项 RTE(Redirection Table Entry)**

- 第56-63位为Destination，代表目的CPU(s)，Physical模式则为APIC ID，Logical模式则为MDA

  - Physical模式下，仅第56-59位有效，第60-63位必须取0

- 第16位为Mask，取0表示允许接受中断，取1表示禁止，reset后初始值为1

- 第15位为Trigger Mode，取0表示edge triggered，取1表示level triggered

- 第14位为Remote IRR，只读且只对level triggered中断有意义，取1代表目的CPU已经接受中断，当收到CPU发来的EOI后，变回0表示中断已经完成

  > **Note:** Remote IRR取1时的作用，实际上是阻止Level Triggered的IRQ line上的Active信号再次触发一个中断。设想若Active信号会产生中断，则只要信号保持Active（e.g. 高电平），就会不断触发中断，这显然是不正确的，故需要由Remote IRR位将中断阻塞。由此可见，CPU应该先设法让IRQ line回到Inactive状态，然后再进行EOI，否则该中断将再次产生。

- 第13位为Interrupt Input Pin Polarity，取0表示active high，取1表示active low

- 第12位为Delivery Status（只读），取0表示空闲，取1表示CPU尚未接受中断（尚未将中断存入IRR）

  - 若目的CPU对某Vector已经有两个中断在Pending，IOAPIC等于可以为该Vector提供第三个Pending的中断

- 第11位为Destination Mode，取0表示Physical，取1表示Logical

- 第8-10位为Delivery Mode，有以下几种取值：

  - 000 (Fixed)：按Vector的值向目标CPU(s)发送相应的中断向量号

  - 001 (Lowest Priority)：按Vector的值向Destination决定的所有目标CPU(s)中Priority最低的CPU发送相应的中断向量号

    - 关于该模式，详见Intel IA32手册第三册第十章

  - 010 (SMI)：向目标CPU(s)发送一个SMI，此模式下Vector必须为0，SMI必须是edge triggered的

  - 100 (NMI)：向目标CPU(s)发送一个NMI（走#NMI引脚），此时Vector会被忽略，NMI必须是edge triggered的

  - 101 (INIT)：向目标CPU(s)发送一个INIT IPI，导致该CPU发生一次INIT（INIT后的CPU状态参考Intel IA32手册第三册表9-1），此模式下Vector必须为0，且必须是edge triggered

    > **Info:** CPU在INIT后其APIC ID和Arb ID（只在奔腾和P6上存在）不变

  - 111（ExtINT）：向目标CPU(s)发送一个与8259A兼容的中断信号，将会引起一个INTA周期，CPU(s)在该周期向外部控制器索取Vector，ExtINT必须是edge triggered的

- 第0-7位为Vector，即目标CPU收到的中断向量号，有效范围为16-254（0-15保留，255为全局广播）

在 PIC 中，vector = 起始vector+IRQ，而在 APIC 模式下，IRQ 对应的 vector 由操作系统对 IOAPIC 初始化的时候设置分配。

```c
// arch/x86/include/asm/io_apic.h

//IOAPIC中的RET条目
struct IO_APIC_route_entry {
        __u32  vector        :  8,
               delivery_mode  :  3,  /* 000: FIXED
                                     * 001: lowest prio
                                     * 111: ExtINT
                                     */
               dest_mode      :  1,  /* 0: physical, 1: logical */
               delivery_status        :  1,
               polarity       :  1,
               irr           :  1,
               trigger               :  1,  /* 0: edge, 1: level */
               mask          :  1,  /* 0: enabled, 1: disabled */
               __reserved_2   : 15;

        __u32  __reserved_3   : 24,
               dest          :  8;
} __attribute__ ((packed));

//配置RET 
static void ioapic_configure_entry(struct irq_data *irqd)
{
	struct mp_chip_data *mpd = irqd->chip_data;
	struct irq_cfg *cfg = irqd_cfg(irqd);
	struct irq_pin_list *entry;

	/*
	 * Only update when the parent is the vector domain, don't touch it
	 * if the parent is the remapping domain. Check the installed
	 * ioapic chip to verify that.
	 */
	if (irqd->chip == &ioapic_chip) {
		mpd->entry.dest = cfg->dest_apicid;
		mpd->entry.vector = cfg->vector;
	}
    //遍历IOAPIC的引脚 将每一个RET写入引脚
	for_each_irq_pin(entry, mpd->irq_2_pin)
		__ioapic_write_entry(entry->apic, entry->pin, mpd->entry);
}
```




##### **LAPIC**

LAPIC (Local Advanced Programmable Interrupt Controller) 是一种负责接收 / 发送中断的芯片，集成在 CPU 内部，每个 CPU 有一个属于自己的 LAPIC。它们通过 APIC ID 进行区分。

<img src=".\images\LAPIC.png" alt="image-20241122114222016" style="zoom: 50%;" />

**LAPIC寄存器**

APIC 寄存器是一段起始地址为 0xFEE00000 、长度为 4KB 的物理地址区域。IOAPIC 的寄存器同样是通过映射一片物理地址空间实现的。

- **IRR(Interrupt Request Register)**
  中断请求寄存器，256 位，每位代表着一个中断。当某个中断消息发来时，如果该中断没有被屏蔽，则将 IRR 对应的 bit 置 1，表示收到了该中断请求但 CPU 还未处理。
  
- **ISR(In Service Register)**
  服务中寄存器，256 位，每位代表着一个中断。当 IRR 中某个中断请求发送个 CPU 时，ISR 对应的 bit 上便置 1，表示 CPU 正在处理该中断。
  
- **EOI(End of Interrupt)**
  中断结束寄存器，32 位，写 EOI 表示中断处理完成。写 EOI 寄存器会导致 LAPIC 清理 ISR 的对应 bit。
  
  对于 level 触发的中断，还会向所有的 IOAPIC 发送 EOI 消息，通告中断处理已经完成。
  
- **ID**
  用来唯一标识一个 LAPIC，LAPIC 与 CPU 一一对应，所以也用 LAPIC ID 来标识 CPU。
  
- **TPR(Task Priority Register)**
  任务优先级寄存器，确定当前 CPU 能够处理什么优先级别的中断，CPU 只处理比 TPR 中级别更高的中断。比它低的中断暂时屏蔽掉，也就是在 IRR 中继续等待。另外优先级别=vector/16，vector 为每个中断对应的中断向量号。
  
- **PPR(Processor Priority Register)**
  处理器优先级寄存器，表示当前正处理的中断的优先级，以此来决定处于 IRR 中的中断是否发送给 CPU。处于 IRR 中的中断只有优先级高于处理器优先级才会被发送给处理器。PPR 的值为 ISR 中正服务的最高优先级中断和 TPR 两者之间选取优先级较大的，所以 TPR 就是靠间接控制 PPR 来实现暂时屏蔽比 TPR 优先级小的中断的。
  
- **SVR(Spurious Interrupt Vector Register)**
  可以通过设置这个寄存器来使 APIC 工作，原话 To enable the APIC。
  
- **ICR(Interrupt Command Register)**
  中断指令寄存器，当一个 CPU 想把中断发送给另一个 CPU 时，就在 ICR 中填写相应的中断向量和目标 LAPIC 标识，然后通过总线向目标 LAPIC 发送消息。ICR 寄存器的字段和 IOAPIC 重定向表项较为相似，都有 destination field, delivery mode, destination mode, level 等等。



**LAPIC处理中断类型**

- APIC Timer 产生的中断(APIC timer generated interrupts)    本地中断
- Performance Monitoring Counter 在 overflow 时产生的中断(Performance monitoring counter interrupts)    本地中断
- 温度传感器产生的中断(Thermal Sensor interrupts)    本地中断
- LAPIC 内部错误时产生的中断(APIC internal error interrupts)   本地中断
- 本地直连 IO 设备 (Locally connected I/O devices) 通过 LINT0 和 LINT1 引脚发来的中断   本地中断
- 其他 CPU (甚至是自己，称为 self-interrupt)发来的 IPI(Inter-processor interrupts)   IPI中断
- IOAPIC 发来的中断   硬件中断

**本地中断**

LAPIC 在收到本地中断后会设置好 LVT(Local Vector Table)的相关寄存器，通过 interrupt delivery protocol 送达 CPU。

LVT 实际上是一片连续的地址空间，每 32-bit 一项，作为各个本地中断源的 APIC register ：

<img src=".\images\APIC06.png" style="zoom:80%;" />

register 被划分成多个部分：

- bit 0-7: Vector，即CPU收到的中断向量号，其中0-15号被视为非法，会产生一个Illegal Vector错误（即ESR的bit 6，详下）
- bit 8-10: Delivery Mode，有以下几种取值：
  - 000 (Fixed)：按Vector的值向CPU发送相应的中断向量号
  - 010 (SMI)：向CPU发送一个SMI，此模式下Vector必须为0
  - 100 (NMI)：向CPU发送一个NMI，此时Vector会被忽略
  - 101 (INIT)：向CPU发送一个 INIT，此模式下Vector必须为0
  - 111 (ExtINT)：令CPU按照响应外部8259A的方式响应中断，这将会引起一个INTA周期，CPU在该周期向外部控制器索取Vector。APIC只支持一个ExtINT中断源，整个系统中应当只有一个CPU的其中一个LVT表项配置为ExtINT模式
- bit 12: Delivery Status（只读），取0表示空闲，取1表示CPU尚未接受该中断（尚未EOI）
- bit 13: Interrupt Input Pin Polarity，取0表示active high，取1表示active low
- bit 14: Remote IRR Flag（只读），若当前接受的中断为fixed mode且是level triggered的，则该位为1表示CPU已经接受中断（已将中断加入IRR），但尚未进行EOI。CPU执行EOI后，该位就恢复到0
- bit 15: Trigger Mode，取0表示edge triggered，取1表示level triggered（具体使用时尚有许多注意点，详见手册10.5.1节）
- bit 16: 为Mask，取0表示允许接受中断，取1表示禁止，reset后初始值为1
- bit 17/17-18: Timer Mode，只有LVT Timer Register有，用于切换APIC Timer的三种模式

**IPI中断和硬件中断**

IPI中断通过写 ICR 来发送。当对 ICR 进行写入时，将产生 interrupt message 并通过 system bus(Pentium 4 / Intel Xeon) 或 APIC bus(Pentium / P6 family) 送达目标 LAPIC 。IOAPIC通过interrupt message给APIC发送中断，在message中已经指定了Interrupt Vector，因此不需要APIC通过LVT表来进行配置。

当有多个 APIC 向通过 system bus / APIC bus 发送 message 时，需要进行仲裁。每个 LAPIC 会被分配一个仲裁优先级(范围为 0-15)，优先级最高的拿到 bus，从而能够发送消息。在消息发送完成后，刚刚发送消息的 LAPIC 的仲裁优先级会被设置为 0，其他的 LAPIC 会加 1。

**LAPIC中断处理过程**

1. 判断自己是否属于消息指定的 destination ，如果不是，抛弃该消息
2. 如果中断的 Delivery Mode 为 NMI / SMI / INIT / ExtINT / SIPI ，则直接将中断发送给 CPU
3. 如果不是以上的 Mode ，则设置中断消息在 IRR 中对应的 bit。如果 IRR 中 bit 已被设置(没有 open slot)，则拒绝该请求，然后给 sender 发送一个 retry 的消息
4. 对于 IRR 中的中断，LAPIC 每次会根据中断的优先级和当前 CPU 的优先级 PPR 选出一个发送给 CPU，会清空该中断在 IRR 中对应的 bit，并设置该中断在 ISR 中对应的 bit
5. CPU 在收到 LAPIC 发来的中断后，通过中断 / 异常处理机制进行处理。处理完毕后，向 LAPIC 的 EOI(end-of-interrupt)寄存器进行写入(NMI / SMI / INIT / ExtINT / SIPI 无需写入)
6. LAPIC 清除 ISR 中该中断对应的 bit(只针对 level-triggered interrupts)
7. 对于 level-triggered interrupt， EOI 会被发送给所有的 IOAPIC。可以通过设置 Spurious Interrupt Vector Register 的 bit12 来避免 EOI 广播

<img src=".\images\LAPIC中断处理过程.jpg" style="zoom:50%;" />

**APIC 中断过程**

- IOAPIC 根据 PRT 表将中断信号翻译成中断消息，然后发送给 destination field 字段列出的 LAPIC

- LAPIC 根据消息中的 destination mode，destination field，自身的寄存器 ID，LDR，DFR 来判断自己是否接收该中断消息，不是则忽略
- 如果该中断是 SMI/NMI/INIT/ExtINT/SIPI，直接送 CPU 执行，因为这些中断都是负责特殊的系统管理任务。否则的话将 IRR 相应的位置 1。
- 如果该中断的优先级高于当前 CPU 正在执行的中断，而且当前 CPU 没有屏蔽中断的话，则中断当前正处理的中断，先处理该高优先级中断，否则等待
- 准备处理下一个中断时，从 IRR 中挑选优先级最大的中断，相应位置 0，ISR 相应位置 1，然后送 CPU 执行。
- 中断处理完成后写 EOI 表示中断处理已经完成，写 EOI 导致 ISR 相应位置 0，对于 level 触发的中断，还会向所有的 I/O APIC 发送 EOI 消息，通知中断处理已经完成。





#### 消息信号中断MSI

消息信号中断(Message Signaled Interrupts) PCI Specification 2.2 引入，设备通过向某个 MMIO 地址写入 system-specified message 可实现向LAPIC发送中断的效果。写入的数据仅能用来决定发送给哪个 LAPIC，而不能携带更多的信息。

- 传统中断基于的引脚 (pin) 往往被多个设备所共享。中断触发后，OS 需要调用对应的中断处理例程来确定产生中断的设备，耗时较长。而 MSI 中断只属于一个特定的设备，不存在该问题。
- 传统中断先发送到 IOAPIC 后再转发给对应的 LAPIC ，路径较长。MSI 能让设备直接将中断送达 LAPIC 。

具体的实现方式为设备通过 PCI write command 向 Message Address Register 指示的地址写入 Message Data Register 中内容来向 LAPIC 发送中断。

**寄存器**

- Message Address Register

  Destination ID 字段存放了中断要发往 LAPIC ID。Redirection hint indication 指定了 MSI 是否直接送达 CPU。 Destination mode 指定了 Destination ID 字段存放的是逻辑还是物理 APIC ID 。

<img src=".\images\Message Address Register.jpg" style="zoom:50%;" />

- Message Data Register

  Vector 指定了中断向量号， Delivery Mode 定义同传统中断，表示中断类型。Trigger Mode 为触发模式，0 为边缘触发，1 为水平触发。 Level 指定了水平触发中断时处于的电位(边缘触发无须设置该字段)。

<img src=".\images\Message Data Register.jpg" style="zoom:50%;" />







### 中断门描述符表

IDT（Interrupt Descriptor Table，中断描述符表）中断描述符表将每个中断向量和对应的中断处理程序地址关联在一起，形成条目。

```c
// arch/x86/kernel/idt.c

//中断描述符表 idt 的每一个表项都由一个 idt_data 结构去描述
struct idt_data { 
	unsigned int	vector;  // 中断向量
	unsigned int	segment; // 中断代码处于的代码段
	struct idt_bits	bits; // 标志位
	const void	*addr; // 指向中断处理函数
};


//标志位
struct idt_bits {
	u16		ist	: 3, //中断栈表索引（Interrupt Stack Table Index），用于指定中断处理程序使用的栈。范围是 0 到 7。
			zero	: 5, // 预留位，必须设置为 0。
			type	: 5, // 类型标志，指示描述符的类型：  中断门 陷阱门 任务门
			dpl	: 2, //段描述符的特权级 范围是 0 到 3。0 表示最高特权级，3 表示最低特权级。
			p	: 1; //段是否在内存中的标识
} __attribute__((packed));


//实际的中断向量表 idt_table[中断向量]
/* Must be page-aligned because the real IDT is used in a fixmap. */
gate_desc idt_table[IDT_ENTRIES] __page_aligned_bss;

//实际的中断向量表结构
struct gate_struct {
	u16		offset_low; // 指向中断处理函数的起始地址
	u16		segment;// 中断代码处于的代码段
	struct idt_bits	bits;// 标志位
	u16		offset_middle; // 指向中断处理函数的起始地址>>16位地址
#ifdef CONFIG_X86_64
	u32		offset_high; // 指向中断处理函数的起始地址>>32位地址
	u32		reserved; //保留字段
#endif
} __attribute__((packed));

```

单个idt_data条目称为一个门，门有三种类型

- 中断门，保存中断或异常处理程序的地址。跳转到处理程序会禁用可屏蔽中断（IF 标志被清除）。

  一般用于处理硬件中断，某些异常也需要禁用可屏蔽中断时也会使用，例如INTG(X86_TRAP_PF, page_fault) 缺页异常。

- 陷阱门，类似于中断门，但在跳转到中断/异常处理程序时不会禁用可屏蔽中断。

  一般用于处理异常

- 任务门（Linux 中不使用）



#### 中断向量布局

 Linux IRQ 向量布局。前 32 个条目保留用于异常，向量 128 用于系统调用接口，其余条目主要用于硬件中断处理程序。

<img src=".\images\IDT03.png"  />



```c
//arch/x86/include/asm/irq_vectors.h


//此文件列举了所有Linux的中断向量 
//从0-31 系统陷阱和异常
//32-127 设备中断
//128 系统调用
//129-255 其他中断

/*
 * Linux IRQ vector layout.
 *
 * There are 256 IDT entries (per CPU - each entry is 8 bytes) which can
 * be defined by Linux. They are used as a jump table by the CPU when a
 * given vector is triggered - by a CPU-external, CPU-internal or
 * software-triggered event.
 *
 * Linux sets the kernel code address each entry jumps to early during
 * bootup, and never changes them. This is the general layout of the
 * IDT entries:
 *
 *  Vectors   0 ...  31 : system traps and exceptions - hardcoded events
 *  Vectors  32 ... 127 : device interrupts
 *  Vector  128         : legacy int80 syscall interface
 *  Vectors 129 ... LOCAL_TIMER_VECTOR-1
 *  Vectors LOCAL_TIMER_VECTOR ... 255 : special interrupts
 *
 * 64-bit x86 has per CPU IDT tables, 32-bit has one shared IDT table.
 *
 * This file enumerates the exact layout of them:
 */

.....

//可用于外部设备中断源的 IDT 向量从 0x20 开始。    
/*
 * IDT vectors usable for external interrupt sources start at 0x20.
 * (0x80 is the syscall vector, 0x30-0x3f are for ISA)
 */
#define FIRST_EXTERNAL_VECTOR		0x20

//系统调用    
#define IA32_SYSCALL_VECTOR		0x80 

//其他中断  
    
/*
 * Special IRQ vectors used by the SMP architecture, 0xf0-0xff
 *
 *  some of the following vectors are 'rare', they are merged
 *  into a single vector (CALL_FUNCTION_VECTOR) to save vector space.
 *  TLB, reschedule and local APIC vectors are performance-critical.
 */

#define SPURIOUS_APIC_VECTOR		0xff    
    
.....


```

<img src=".\images\中断向量分布图.png" style="zoom: 67%;" />

```c
// arch/x86/include/asm/traps.h

//从0-31 系统陷阱和异常

/* Interrupts/Exceptions */
enum {
	X86_TRAP_DE = 0,	/*  0, Divide-by-zero */
	X86_TRAP_DB,		/*  1, Debug */
	X86_TRAP_NMI,		/*  2, Non-maskable Interrupt */
	X86_TRAP_BP,		/*  3, Breakpoint */
	X86_TRAP_OF,		/*  4, Overflow */
	X86_TRAP_BR,		/*  5, Bound Range Exceeded */
	X86_TRAP_UD,		/*  6, Invalid Opcode */
	X86_TRAP_NM,		/*  7, Device Not Available */
	X86_TRAP_DF,		/*  8, Double Fault */
	X86_TRAP_OLD_MF,	/*  9, Coprocessor Segment Overrun */
	X86_TRAP_TS,		/* 10, Invalid TSS */
	X86_TRAP_NP,		/* 11, Segment Not Present */
	X86_TRAP_SS,		/* 12, Stack Segment Fault */
	X86_TRAP_GP,		/* 13, General Protection Fault */
	X86_TRAP_PF,		/* 14, Page Fault */
	X86_TRAP_SPURIOUS,	/* 15, Spurious Interrupt */
	X86_TRAP_MF,		/* 16, x87 Floating-Point Exception */
	X86_TRAP_AC,		/* 17, Alignment Check */
	X86_TRAP_MC,		/* 18, Machine Check */
	X86_TRAP_XF,		/* 19, SIMD Floating-Point Exception */
	X86_TRAP_IRET = 32,	/* 32, IRET Exception */
};
```







#### 初始化中断向量表

内核启动时会在 start_kernel() 里面调用 trap_init() 去初始化中断向量表

```c
// /arch/x86/kernel/traps.c

void __init trap_init(void)
{
	/* Init cpu_entry_area before IST entries are set up */
    //这个函数初始化 CPU 入口区域（cpu_entry_area），这些区域包含了中断栈和其他必要的数据结构。这个步骤必须在设置 IST（Interrupt Stack Table）条目之前完成。
	setup_cpu_entry_areas();

    //设置中断向量表的标准条目
	idt_setup_traps();

	/*
	 * Set the IDT descriptor to a fixed read-only location, so that the
	 * "sidt" instruction will not leak the location of the kernel, and
	 * to defend the IDT against arbitrary memory write vulnerabilities.
	 * It will be reloaded in cpu_init() */
    //将 IDT 表的物理地址映射到一个固定的只读虚拟地址 CPU_ENTRY_AREA_RO_IDT_VADDR。这一步是为了防止 sidt 指令泄露内核的地址，并且保护 IDT 不受任意内存写入漏洞的影响。
	cea_set_pte(CPU_ENTRY_AREA_RO_IDT_VADDR, __pa_symbol(idt_table),
		    PAGE_KERNEL_RO);
	idt_descr.address = CPU_ENTRY_AREA_RO_IDT;

	/*
	 * Should be a barrier for any external CPU state:
	 */
    //这个函数初始化 CPU 的状态，包括设置任务状态段（TSS）等。这个步骤确保 CPU 准备好处理中断。
	cpu_init();

    //设置中断向量表的IST（Interrupt Stack Table）条目
	idt_setup_ist_traps();

	x86_init.irqs.trap_init();

    //设置中断向量表的debug条目
	idt_setup_debugidt_traps();
}
```



```c
//arch/x86/kernel/idt.c


//中断门
/* Interrupt gate */
#define INTG(_vector, _addr)				\
	G(_vector, _addr, DEFAULT_STACK, GATE_INTERRUPT, DPL0, __KERNEL_CS)

//陷阱门
/* System interrupt gate */
#define SYSG(_vector, _addr)				\
	G(_vector, _addr, DEFAULT_STACK, GATE_INTERRUPT, DPL3, __KERNEL_CS)

//任务门
/* Task gate */
#define TSKG(_vector, _gdt)				\
	G(_vector, NULL, DEFAULT_STACK, GATE_TASK, DPL0, _gdt << 3)



//设置默认的中断向量和对应的处理程序

/*
 * The default IDT entries which are set up in trap_init() before
 * cpu_init() is invoked. Interrupt stacks cannot be used at that point and
 * the traps which use them are reinitialized with IST after cpu_init() has
 * set up TSS.
 */
static const __initconst struct idt_data def_idts[] = {
	INTG(X86_TRAP_DE,		divide_error),
	INTG(X86_TRAP_NMI,		nmi),
	INTG(X86_TRAP_BR,		bounds),
	INTG(X86_TRAP_UD,		invalid_op),
	INTG(X86_TRAP_NM,		device_not_available),
	INTG(X86_TRAP_OLD_MF,		coprocessor_segment_overrun),
	INTG(X86_TRAP_TS,		invalid_TSS),
	INTG(X86_TRAP_NP,		segment_not_present),
	INTG(X86_TRAP_SS,		stack_segment),
	INTG(X86_TRAP_GP,		general_protection),
	INTG(X86_TRAP_SPURIOUS,		spurious_interrupt_bug),
	INTG(X86_TRAP_MF,		coprocessor_error),
	INTG(X86_TRAP_AC,		alignment_check),
	INTG(X86_TRAP_XF,		simd_coprocessor_error),

#ifdef CONFIG_X86_32
	TSKG(X86_TRAP_DF,		GDT_ENTRY_DOUBLEFAULT_TSS),
#else
	INTG(X86_TRAP_DF,		double_fault),
#endif
	INTG(X86_TRAP_DB,		debug),

#ifdef CONFIG_X86_MCE
	INTG(X86_TRAP_MC,		&machine_check),
#endif

	SYSG(X86_TRAP_OF,		overflow),
#if defined(CONFIG_IA32_EMULATION)
	SYSG(IA32_SYSCALL_VECTOR,	entry_INT80_compat),
#elif defined(CONFIG_X86_32)
	SYSG(IA32_SYSCALL_VECTOR,	entry_INT80_32),
#endif
};


//实际的中断向量表
/* Must be page-aligned because the real IDT is used in a fixmap. */
gate_desc idt_table[IDT_ENTRIES] __page_aligned_bss;


/**
 * idt_setup_traps - Initialize the idt table with default traps
 */
void __init idt_setup_traps(void)
{
    //使用def_idts来初始化idt_table中断向量表
	idt_setup_from_table(idt_table, def_idts, ARRAY_SIZE(def_idts), true);
}



static void
idt_setup_from_table(gate_desc *idt, const struct idt_data *t, int size, bool sys)
{
	gate_desc desc;

    //循环迭代t数组
	for (; size > 0; t++, size--) {
		//利用idt_data来创建gate_desc
         idt_init_desc(&desc, t);
        //将刚才创建的gate_desc加入到idt_table中断向量表里
		write_idt_entry(idt, t->vector, &desc);
		if (sys)
			set_bit(t->vector, system_vectors);
	}
}


//利用idt_data来设置gate_desc
static inline void idt_init_desc(gate_desc *gate, const struct idt_data *d)
{
	unsigned long addr = (unsigned long) d->addr;

	gate->offset_low	= (u16) addr;
	gate->segment		= (u16) d->segment;
	gate->bits		= d->bits;
	gate->offset_middle	= (u16) (addr >> 16);
#ifdef CONFIG_X86_64
	gate->offset_high	= (u32) (addr >> 32);
	gate->reserved		= 0;
#endif
}


#define write_idt_entry(dt, entry, g)		native_write_idt_entry(dt, entry, g)

static inline void native_write_idt_entry(gate_desc *idt, int entry, const gate_desc *gate)
{
    //将刚才创建的gate_desc加入到idt_table中断向量表里 entry是中断向量号
	memcpy(&idt[entry], gate, sizeof(*gate));
}

```



**中断向量表加载到IDTR**

```c


//设置到IDTR的数据 .size标志IDT表的大小 .address标志IDT的起始地址
struct desc_ptr idt_descr __ro_after_init = {
	.size		= (IDT_ENTRIES * 2 * sizeof(unsigned long)) - 1,
	.address	= (unsigned long) idt_table,
};

//setup_arch();

/**
 * idt_setup_early_traps - Initialize the idt table with early traps
 *
 * On X8664 these traps do not use interrupt stacks as they can't work
 * before cpu_init() is invoked and sets up TSS. The IST variants are
 * installed after that.
 */
void __init idt_setup_early_traps(void)
{
    idt_setup_from_table(idt_table, early_idts, ARRAY_SIZE(early_idts),
               true);
    //将idt_descr设置到IDTR寄存器
    load_idt(&idt_descr);
}


//将idt_descr设置到IDTR寄存器
static inline void native_load_idt(const struct desc_ptr *dtr)
{
    //通过 lidt指令将 一个 48 位的内存操作数，其中前 16 位表示 IDT 的大小（以字节为单位），后 32 位表示 IDT 的基地址 设置到IDTR寄存器
	asm volatile("lidt %0"::"m" (*dtr));
}
```





#### 中断程序寻址过程

<img src=".\images\IDT01.png" style="zoom:80%;" />

**中断门描述符**

IDT中断向量表中的每一项都是一个中断门描述符，保存着中断处理程序的**目标代码段选择子**和**目标代码段偏移量**

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\中断门描述符.png" style="zoom: 80%;" />

**中断程序寻址过程**

1. 先判断中断号是否在合理范围（不能超过中断门描述符表中表项个数），X86最大支持256个中断源。
2. 检查描述符类型（中断门还是陷阱门）、描述符在不在内存中。如果不在内存中则会触发页面错误（Page Fault） 或 段不存在异常（Segment Not Present Exception）
3. 检查中断门描述符中的段选择子所指向的段描述符。最后做权限检查，若CPL小于等于中断门的DPL，并且CPL大于等于中断门中的段选择子指向的段描述符的DPL，则通过检查。
4. 如果CPL等于中断门段选择子指向的段描述符的DPL，视为同级别权限，不切换栈，否则会切换栈。如果涉及栈切换，还会从TSS中加载具体权限对应的SS,ESP等。如果需要切换到更高特权级别，CPU会将当前的EIP、EFLAGS、ESP等寄存器内容保存到TSS中，并更新堆栈指针为`ESP0`和`SS0`。
5. 将中断门描述符中目标代码的段选择子放到CS寄存器，目标代码段偏移加载到EIP寄存器。
6. 当中断服务处理程序结束时，CPU会从TSS中恢复之前的寄存器值，然后通过IRET指令返回到被中断的代码位置。









**寄存器IDTR**

为查询中断描述符表IDT的起始地址，在CPU中专门设置了一个IDTR——中断描述符表寄存器，这是一个48位寄存器，高32位保存了IDT的基地址，低16位保存了中断描述符表的大小。

<img src=".\images\IDT02.png" style="zoom: 80%;" />



### 中断处理程序

#### 硬件中断

硬件中断处理程序快速执行和执行大量工作这两个目标明显形成对比，由于CPU在处理中断时会禁用中断，为了减少中断禁用时间，快速响应中断。硬件中断处理被分为两个部分。硬件中断处理逻辑在硬件的驱动程序中。

- **上半部分**在收到中断后立即运行，只执行时间紧迫的工作，例如确认收到中断或重置硬件。
- **下半部分**则是处理可以稍后再做的事情，例如从网卡中读取数据等。

##### 上半部分



###### 硬件中断处理程序初始化

所有硬件中断的向量号和中断处理程序都被设置到了IDT上，而有些硬件中断是直接被设置到IDT上的，如`INTG(LOCAL_TIMER_VECTOR,    apic_timer_interrupt)`，直接指定了处理函数`apic_timer_interrupt`

部分硬件中断的中断处理程序地址则为统一处理函数入口`irq_entries_start`，通过`irq_entries_start`找到实际的处理函数

```c

// arch/x86/kernel/idt.c


//start_kernel()
//      init_IRQ()


//arch/x86/include/asm/hw_irq.h

//中断向量和对应的irq_desc表
typedef struct irq_desc* vector_irq_t[NR_VECTORS];
DECLARE_PER_CPU(vector_irq_t, vector_irq);

/*
 * Vectors 0x30-0x3f are used for ISA interrupts.
 *   round up to the next 16-vector boundary
 * 0-15编号IRQ线的中断向量在ISA中默认对应 32 + IRQ编号 
 */
#define ISA_IRQ_VECTOR(irq)		(((FIRST_EXTERNAL_VECTOR + 16) & ~15) + irq)

//中断栈
/* Per CPU interrupt stacks */
struct irq_stack {
	char		stack[IRQ_STACK_SIZE];
} __aligned(IRQ_STACK_SIZE);

//每个cpu的中断栈
DECLARE_PER_CPU(struct irq_stack *, hardirq_stack_ptr);


// arch/x86/kernel/irqinit.c 

//初始化IRQ
void __init init_IRQ(void)
{
        int i;

        /*
         * On cpu 0, Assign ISA_IRQ_VECTOR(irq) to IRQ 0..15.
         * If these IRQ's are handled by legacy interrupt-controllers like PIC,
         * then this configuration will likely be static after the boot. If
         * these IRQ's are handled by more mordern controllers like IO-APIC,
         * then this vector space can be freed and re-used dynamically as the
         * irq's migrate etc.
         */
        //设置cpu0的vector_irq表的从0x30-0x3f即IRQ线的0-15编号
        for (i = 0; i < nr_legacy_irqs(); i++)
               per_cpu(vector_irq, 0)[ISA_IRQ_VECTOR(i)] = irq_to_desc(i);

        //初始化cpu的中断栈
        BUG_ON(irq_init_percpu_irqstack(smp_processor_id()));
        //调用native_init_IRQ
        x86_init.irqs.intr_init();
}

void __init native_init_IRQ(void)
{
	/* Execute any quirks before the call gates are initialised: */
	x86_init.irqs.pre_vector_init();

    //设置idt表上的中断向量号和对应的中断条目
	idt_setup_apic_and_irq_gates();
	lapic_assign_system_vectors();

    //如果没用apic 那么使用的是传统的PIC 那么设置IRQ线2号为级联芯片
	if (!acpi_ioapic && !of_ioapic && nr_legacy_irqs())
		setup_irq(2, &irq2);
}


//在内核启动过程中，前面对IDT设置了默认的条目，用于处理常见的异常条目。
//idt_setup_apic_and_irq_gates()则设置了与APIC相关的中断条目 包括cpu间的中断等


/**
 * idt_setup_apic_and_irq_gates - Setup APIC/SMP and normal interrupt gates
 */

void __init idt_setup_apic_and_irq_gates(void)
{
	int i = FIRST_EXTERNAL_VECTOR;
	void *entry;

    //和设置默认条目一样，将与APIC相关的中断条目设置到IDT上
	idt_setup_from_table(idt_table, apic_idts, ARRAY_SIZE(apic_idts), true);

    // 此时IDT上有 默认的异常条目 + 与APIC相关的异常条目
    // 还需要设置其他硬件设备的中断处理条目，循环设置
	for_each_clear_bit_from(i, system_vectors, FIRST_SYSTEM_VECTOR) {
        //将其他硬件设备的中断处理函数都设置为irq_entries_start，entry是irq_entries_start的地址，每个条目的函数引用地址分开存储，但都是irq_entries_start函数
		entry = irq_entries_start + 8 * (i - FIRST_EXTERNAL_VECTOR);
        //将函数处理地址和中断向量绑定 设置到IDT中
		set_intr_gate(i, entry);
	}

#ifdef CONFIG_X86_LOCAL_APIC
	for_each_clear_bit_from(i, system_vectors, NR_VECTORS) {
		set_bit(i, system_vectors);
		entry = spurious_entries_start + 8 * (i - FIRST_SYSTEM_VECTOR);
		set_intr_gate(i, entry);
	}
#endif
}


//将addr函数处理地址和n中断向量绑定 设置到IDT中
static void set_intr_gate(unsigned int n, const void *addr)
{
	struct idt_data data;

	BUG_ON(n > 0xFF);

    //设置 idt_data 将中断向量和设置的处理函数地址绑定
	memset(&data, 0, sizeof(data));
	data.vector	= n;
	data.addr	= addr;
	data.segment	= __KERNEL_CS;
	data.bits.type	= GATE_INTERRUPT;
	data.bits.p	= 1;
	//将idt_data设置到IDT中
	idt_setup_from_table(idt_table, &data, 1, false);
}


```



```assembly
/*
	arch/x86/entry/entry_64.S
*/

/*通用硬件中断处理入口函数*/
/*
 * Build the entry stubs with some assembler magic.
 * We pack 1 stub into every 8-byte block.
 */
	.align 8
SYM_CODE_START(irq_entries_start)
    vector=FIRST_EXTERNAL_VECTOR
    .rept (FIRST_SYSTEM_VECTOR - FIRST_EXTERNAL_VECTOR)
	UNWIND_HINT_IRET_REGS
	pushq	$(~vector+0x80)			/* Note: always in signed byte range */
	jmp	common_interrupt /*调用common_interrupt方法*/
	.align	8
	vector=vector+1
    .endr
SYM_CODE_END(irq_entries_start)

/*截取部分common_interrupt方法*/
/* common_interrupt is a hotpath. Align it */
	.p2align CONFIG_X86_L1_CACHE_SHIFT
SYM_CODE_START_LOCAL(common_interrupt)
	addq	$-0x80, (%rsp)			/* Adjust vector to [-256, -1] range */
	call	interrupt_entry
	UNWIND_HINT_REGS indirect=1
	call	do_IRQ	/* rdi points to pt_regs */ /*调用do_IRQ方法*/
	/* 0(%rsp): old RSP */
ret_from_intr:
.....



```



###### 硬件中断相关结构体



<img src=".\images\irq_desc_t.jpg" style="zoom:67%;" />

```c
// kernel/irq/irqdesc.c

// 每个IRQ线对应着一个irq_desc结构体 数组下标代表IRQ线的编号
struct irq_desc irq_desc[NR_IRQS] __cacheline_aligned_in_smp = {
        [0 ... NR_IRQS-1] = {
               .handle_irq    = handle_bad_irq,
               .depth        = 1,
               .lock         = __RAW_SPIN_LOCK_UNLOCKED(irq_desc->lock),
        }
};



// include/linux/irqdesc.h


// 每个IRQ线对应着一个irq_desc的结构体
struct irq_desc {
        struct irq_common_data irq_common_data;
        struct irq_data               irq_data;
        unsigned int __percpu  *kstat_irqs;
    	//中断处理函数
        irq_flow_handler_t     handle_irq;
#ifdef CONFIG_IRQ_PREFLOW_FASTEOI
        irq_preflow_handler_t  preflow_handler;
#endif
    	//由于一条IRQ线可以被多个硬件共享，所以 action 是一个链表，每个 action 代表一个硬件的中断处理入口。
        struct irqaction       *action;       /* IRQ action list */
        unsigned int          status_use_accessors;
        unsigned int          core_internal_state__do_not_mess_with_it;
        unsigned int          depth;        /* nested irq disables */
        unsigned int          wake_depth;    /* nested wake enables */
        unsigned int          tot_count;
        unsigned int          irq_count;     /* For detecting broken IRQs */
        unsigned long         last_unhandled;        /* Aging timer for unhandled count */
        unsigned int          irqs_unhandled;
        atomic_t              threads_handled;
        int                  threads_handled_last;
    	//防止多核CPU同时对IRQ进行操作的自旋锁。
        raw_spinlock_t        lock;
        struct cpumask        *percpu_enabled;
        const struct cpumask   *percpu_affinity;
#ifdef CONFIG_SMP
        const struct cpumask   *affinity_hint;
        struct irq_affinity_notify *affinity_notify;
#ifdef CONFIG_GENERIC_PENDING_IRQ
        cpumask_var_t         pending_mask;
#endif
#endif
        unsigned long         threads_oneshot;
        atomic_t              threads_active;
        wait_queue_head_t       wait_for_threads;
#ifdef CONFIG_PM_SLEEP
        unsigned int          nr_actions;
        unsigned int          no_suspend_depth;
        unsigned int          cond_suspend_depth;
        unsigned int          force_resume_depth;
#endif
#ifdef CONFIG_PROC_FS
        struct proc_dir_entry  *dir;
#endif
#ifdef CONFIG_GENERIC_IRQ_DEBUGFS
        struct dentry         *debugfs_file;
        const char            *dev_name;
#endif
#ifdef CONFIG_SPARSE_IRQ
        struct rcu_head               rcu;
        struct kobject        kobj;
#endif
        struct mutex          request_mutex;
        int                  parent_irq;
        struct module         *owner;
        const char            *name;
} ____cacheline_internodealigned_in_smp;
```



```c
//include/linux/interrupt.h

struct irqaction {
    	//中断处理程序
        irq_handler_t         handler;
    	// 设备id
        void                 *dev_id;
        void __percpu         *percpu_dev_id;
   		//下一个action
        struct irqaction       *next;
        //中断处理线程的函数
        irq_handler_t         thread_fn;
        //中断处理线程
        struct task_struct     *thread;
        struct irqaction       *secondary;
        unsigned int          irq;
        unsigned int          flags;
        unsigned long         thread_flags;
        unsigned long         thread_mask;
    	//中断处理程序的名称
        const char            *name;
        struct proc_dir_entry  *dir;
} ____cacheline_internodealigned_in_smp;

// 中断处理程序 IRQ线的编号 设备号
typedef irqreturn_t (*irq_handler_t)(int, void *);


```







```c
// include/linux/irq.h

//irq设备的硬件交互函数

struct irq_chip {
    struct device  *parent_device;
    const char *name;
    unsigned int   (*irq_startup)(struct irq_data *data);
    void      (*irq_shutdown)(struct irq_data *data);
    void      (*irq_enable)(struct irq_data *data);
    void      (*irq_disable)(struct irq_data *data);

    void      (*irq_ack)(struct irq_data *data);
    void      (*irq_mask)(struct irq_data *data);
    void      (*irq_mask_ack)(struct irq_data *data);
    void      (*irq_unmask)(struct irq_data *data);
    void      (*irq_eoi)(struct irq_data *data);

    int       (*irq_set_affinity)(struct irq_data *data, const struct cpumask *dest, bool force);
    int       (*irq_retrigger)(struct irq_data *data);
    int       (*irq_set_type)(struct irq_data *data, unsigned int flow_type);
    int       (*irq_set_wake)(struct irq_data *data, unsigned int on);

    void      (*irq_bus_lock)(struct irq_data *data);
    void      (*irq_bus_sync_unlock)(struct irq_data *data);

    void      (*irq_cpu_online)(struct irq_data *data);
    void      (*irq_cpu_offline)(struct irq_data *data);

    void      (*irq_suspend)(struct irq_data *data);
    void      (*irq_resume)(struct irq_data *data);
    void      (*irq_pm_shutdown)(struct irq_data *data);

    void      (*irq_calc_mask)(struct irq_data *data);

    void      (*irq_print_chip)(struct irq_data *data, struct seq_file *p);
    int       (*irq_request_resources)(struct irq_data *data);
    void      (*irq_release_resources)(struct irq_data *data);

    void      (*irq_compose_msi_msg)(struct irq_data *data, struct msi_msg *msg);
    void      (*irq_write_msi_msg)(struct irq_data *data, struct msi_msg *msg);

    int       (*irq_get_irqchip_state)(struct irq_data *data, enum irqchip_irq_state which, bool *state);
    int       (*irq_set_irqchip_state)(struct irq_data *data, enum irqchip_irq_state which, bool state);

    int       (*irq_set_vcpu_affinity)(struct irq_data *data, void *vcpu_info);

    void      (*ipi_send_single)(struct irq_data *data, unsigned int cpu);
    void      (*ipi_send_mask)(struct irq_data *data, const struct cpumask *dest);

    int       (*irq_nmi_setup)(struct irq_data *data);
    void      (*irq_nmi_teardown)(struct irq_data *data);

    unsigned long  flags;
};
```



###### 注册硬件中断

硬件的处理程序是通过Linux内核中内置的驱动程序来注册的

```c


//将对应驱动的设备信息和处理函数注册到指定的IRQ线上

/**
 * request_irq - Add a handler for an interrupt line
 * @irq:        The interrupt line to allocate
 * @handler:    Function to be called when the IRQ occurs.
 *             Primary handler for threaded interrupts
 *             If NULL, the default primary handler is installed
 * @flags:      Handling flags
 * @name:       Name of the device generating this interrupt
 * @dev:        A cookie passed to the handler function
 *
 * This call allocates an interrupt and establishes a handler; see
 * the documentation for request_threaded_irq() for details.
 */
static inline int __must_check
request_irq(unsigned int irq, irq_handler_t handler, unsigned long flags,
            const char *name, void *dev)
{
        return request_threaded_irq(irq, handler, NULL, flags, name, dev);
}


//kernel/irq/manage.c
//注册设备处理函数和设备信息
int request_threaded_irq(unsigned int irq, irq_handler_t handler,
			 irq_handler_t thread_fn, unsigned long irqflags,
			 const char *devname, void *dev_id)
{
	struct irqaction *action;
	struct irq_desc *desc;
	int retval;

	if (irq == IRQ_NOTCONNECTED)
		return -ENOTCONN;

	/*
	 * Sanity-check: shared interrupts must pass in a real dev-ID,
	 * otherwise we'll have trouble later trying to figure out
	 * which interrupt is which (messes up the interrupt freeing
	 * logic etc).
	 *
	 * Also IRQF_COND_SUSPEND only makes sense for shared interrupts and
	 * it cannot be set along with IRQF_NO_SUSPEND.
	 */
    //共享设备必须有真实的dev_id 否则无法确认中断来源
	if (((irqflags & IRQF_SHARED) && !dev_id) ||
	    (!(irqflags & IRQF_SHARED) && (irqflags & IRQF_COND_SUSPEND)) ||
	    ((irqflags & IRQF_NO_SUSPEND) && (irqflags & IRQF_COND_SUSPEND)))
		return -EINVAL;

    //通过指定的IRQ号来查找对应的irq_desc
	desc = irq_to_desc(irq);
	if (!desc)
		return -EINVAL;

	if (!irq_settings_can_request(desc) ||
	    WARN_ON(irq_settings_is_per_cpu_devid(desc)))
		return -EINVAL;

    //如果handler和thread_fn都为空
	if (!handler) {
		if (!thread_fn)
			return -EINVAL;
        //默认处理函数 默认启动一个中断处理线程
		handler = irq_default_primary_handler;
	}

    //分配action的地址空间
	action = kzalloc(sizeof(struct irqaction), GFP_KERNEL);
	if (!action)
		return -ENOMEM;

    //设置action的中断处理函数、设备信息、标志位等
	action->handler = handler;
	action->thread_fn = thread_fn;
	action->flags = irqflags;
	action->name = devname;
	action->dev_id = dev_id;

	retval = irq_chip_pm_get(&desc->irq_data);
	if (retval < 0) {
		kfree(action);
		return retval;
	}

    //将action加入到IRQ对应的irq_desc上
	retval = __setup_irq(irq, desc, action);

	if (retval) {
		irq_chip_pm_put(&desc->irq_data);
		kfree(action->secondary);
		kfree(action);
	}

#ifdef CONFIG_DEBUG_SHIRQ_FIXME
	if (!retval && (irqflags & IRQF_SHARED)) {
		/*
		 * It's a shared IRQ -- the driver ought to be prepared for it
		 * to happen immediately, so let's make sure....
		 * We disable the irq to make sure that a 'real' IRQ doesn't
		 * run in parallel with our fake.
		 */
		unsigned long flags;

		disable_irq(irq);
		local_irq_save(flags);

		handler(irq, dev_id);

		local_irq_restore(flags);
		enable_irq(irq);
	}
#endif
	return retval;
}



//以e1000网卡的驱动程序为例
static int e1000_request_irq(struct e1000_adapter *adapter)
{
	struct net_device *netdev = adapter->netdev;
	irq_handler_t handler = e1000_intr;
	int irq_flags = IRQF_SHARED;
	int err;

    //注册中断处理函数和设备信息绑定到指定的IRQ线
	err = request_irq(adapter->pdev->irq, handler, irq_flags, netdev->name,
			  netdev);
	if (err) {
		e_err(probe, "Unable to allocate interrupt Error: %d\n", err);
	}

	return err;
}



```



```c
// kernel/irq/manage.c

// 注册硬件中断 IRQ线的编号和对应的irqaction处理程序
int setup_irq(unsigned int irq, struct irqaction *act)
{
	int retval;
    //找到IRQ线的编号对应的irq_desc
	struct irq_desc *desc = irq_to_desc(irq);

	if (!desc || WARN_ON(irq_settings_is_per_cpu_devid(desc)))
		return -EINVAL;

	retval = irq_chip_pm_get(&desc->irq_data);
	if (retval < 0)
		return retval;
	//将irqaction加入到对应的irq_desc中
	retval = __setup_irq(irq, desc, act);

	if (retval)
		irq_chip_pm_put(&desc->irq_data);

	return retval;
}

//在irq_desc数组中找到对应IRQ线的编号的irq_desc
struct irq_desc *irq_to_desc(unsigned int irq)
{
        return (irq < NR_IRQS) ? irq_desc + irq : NULL;
}


/*
 * Internal function to register an irqaction - typically used to
 * allocate special interrupts that are part of the architecture.
 *
 * Locking rules:
 *
 * desc->request_mutex	Provides serialization against a concurrent free_irq()
 *   chip_bus_lock	Provides serialization for slow bus operations
 *     desc->lock	Provides serialization against hard interrupts
 *
 * chip_bus_lock and desc->lock are sufficient for all other management and
 * interrupt related functions. desc->request_mutex solely serializes
 * request/free_irq().
 */
static int
__setup_irq(unsigned int irq, struct irq_desc *desc, struct irqaction *new)
{
	struct irqaction *old, **old_ptr;
	unsigned long flags, thread_mask = 0;
	int ret, nested, shared = 0;

	if (!desc)
		return -EINVAL;

	if (desc->irq_data.chip == &no_irq_chip)
		return -ENOSYS;
	if (!try_module_get(desc->owner))
		return -ENODEV;

	new->irq = irq;

	/*
	 * If the trigger type is not specified by the caller,
	 * then use the default for this interrupt.
	 */
	if (!(new->flags & IRQF_TRIGGER_MASK))
		new->flags |= irqd_get_trigger_type(&desc->irq_data);

	/*
	 * Check whether the interrupt nests into another interrupt
	 * thread.
	 */
	nested = irq_settings_is_nested_thread(desc);
	if (nested) {
		if (!new->thread_fn) {
			ret = -EINVAL;
			goto out_mput;
		}
		/*
		 * Replace the primary handler which was provided from
		 * the driver for non nested interrupt handling by the
		 * dummy function which warns when called.
		 */
		new->handler = irq_nested_primary_handler;
	} else {
		if (irq_settings_can_thread(desc)) {
			ret = irq_setup_forced_threading(new);
			if (ret)
				goto out_mput;
		}
	}

	/*
	 * Create a handler thread when a thread function is supplied
	 * and the interrupt does not nest into another interrupt
	 * thread.
	 */
    //如果设置了thread_fn 那么启动一个中断处理线程
	if (new->thread_fn && !nested) {
        //创建硬件irq处理线程
		ret = setup_irq_thread(new, irq, false);
		if (ret)
			goto out_mput;
        //如果设置了new->secondary 再启动一个中断处理线程
		if (new->secondary) {
			ret = setup_irq_thread(new->secondary, irq, true);
			if (ret)
				goto out_thread;
		}
	}

	/*
	 * Drivers are often written to work w/o knowledge about the
	 * underlying irq chip implementation, so a request for a
	 * threaded irq without a primary hard irq context handler
	 * requires the ONESHOT flag to be set. Some irq chips like
	 * MSI based interrupts are per se one shot safe. Check the
	 * chip flags, so we can avoid the unmask dance at the end of
	 * the threaded handler for those.
	 */
	if (desc->irq_data.chip->flags & IRQCHIP_ONESHOT_SAFE)
		new->flags &= ~IRQF_ONESHOT;

	/*
	 * Protects against a concurrent __free_irq() call which might wait
	 * for synchronize_hardirq() to complete without holding the optional
	 * chip bus lock and desc->lock. Also protects against handing out
	 * a recycled oneshot thread_mask bit while it's still in use by
	 * its previous owner.
	 */
	mutex_lock(&desc->request_mutex);

	/*
	 * Acquire bus lock as the irq_request_resources() callback below
	 * might rely on the serialization or the magic power management
	 * functions which are abusing the irq_bus_lock() callback,
	 */
	chip_bus_lock(desc);

	/* First installed action requests resources. */
	if (!desc->action) {
		ret = irq_request_resources(desc);
		if (ret) {
			pr_err("Failed to request resources for %s (irq %d) on irqchip %s\n",
			       new->name, irq, desc->irq_data.chip->name);
			goto out_bus_unlock;
		}
	}

	/*
	 * The following block of code has to be executed atomically
	 * protected against a concurrent interrupt and any of the other
	 * management calls which are not serialized via
	 * desc->request_mutex or the optional bus lock.
	 */
	raw_spin_lock_irqsave(&desc->lock, flags);
	old_ptr = &desc->action;
	old = *old_ptr;
	if (old) {
		/*
		 * Can't share interrupts unless both agree to and are
		 * the same type (level, edge, polarity). So both flag
		 * fields must have IRQF_SHARED set and the bits which
		 * set the trigger type must match. Also all must
		 * agree on ONESHOT.
		 * Interrupt lines used for NMIs cannot be shared.
		 */
		unsigned int oldtype;

		if (desc->istate & IRQS_NMI) {
			pr_err("Invalid attempt to share NMI for %s (irq %d) on irqchip %s.\n",
				new->name, irq, desc->irq_data.chip->name);
			ret = -EINVAL;
			goto out_unlock;
		}

		/*
		 * If nobody did set the configuration before, inherit
		 * the one provided by the requester.
		 */
		if (irqd_trigger_type_was_set(&desc->irq_data)) {
			oldtype = irqd_get_trigger_type(&desc->irq_data);
		} else {
			oldtype = new->flags & IRQF_TRIGGER_MASK;
			irqd_set_trigger_type(&desc->irq_data, oldtype);
		}

		if (!((old->flags & new->flags) & IRQF_SHARED) ||
		    (oldtype != (new->flags & IRQF_TRIGGER_MASK)) ||
		    ((old->flags ^ new->flags) & IRQF_ONESHOT))
			goto mismatch;

		/* All handlers must agree on per-cpuness */
		if ((old->flags & IRQF_PERCPU) !=
		    (new->flags & IRQF_PERCPU))
			goto mismatch;

		/* add new interrupt at end of irq queue */
		do {
			/*
			 * Or all existing action->thread_mask bits,
			 * so we can find the next zero bit for this
			 * new action.
			 */
			thread_mask |= old->thread_mask;
			old_ptr = &old->next;
			old = *old_ptr;
		} while (old);
		shared = 1;
	}

	/*
	 * Setup the thread mask for this irqaction for ONESHOT. For
	 * !ONESHOT irqs the thread mask is 0 so we can avoid a
	 * conditional in irq_wake_thread().
	 */
	if (new->flags & IRQF_ONESHOT) {
		/*
		 * Unlikely to have 32 resp 64 irqs sharing one line,
		 * but who knows.
		 */
		if (thread_mask == ~0UL) {
			ret = -EBUSY;
			goto out_unlock;
		}
		/*
		 * The thread_mask for the action is or'ed to
		 * desc->thread_active to indicate that the
		 * IRQF_ONESHOT thread handler has been woken, but not
		 * yet finished. The bit is cleared when a thread
		 * completes. When all threads of a shared interrupt
		 * line have completed desc->threads_active becomes
		 * zero and the interrupt line is unmasked. See
		 * handle.c:irq_wake_thread() for further information.
		 *
		 * If no thread is woken by primary (hard irq context)
		 * interrupt handlers, then desc->threads_active is
		 * also checked for zero to unmask the irq line in the
		 * affected hard irq flow handlers
		 * (handle_[fasteoi|level]_irq).
		 *
		 * The new action gets the first zero bit of
		 * thread_mask assigned. See the loop above which or's
		 * all existing action->thread_mask bits.
		 */
		new->thread_mask = 1UL << ffz(thread_mask);

	} else if (new->handler == irq_default_primary_handler &&
		   !(desc->irq_data.chip->flags & IRQCHIP_ONESHOT_SAFE)) {
		/*
		 * The interrupt was requested with handler = NULL, so
		 * we use the default primary handler for it. But it
		 * does not have the oneshot flag set. In combination
		 * with level interrupts this is deadly, because the
		 * default primary handler just wakes the thread, then
		 * the irq lines is reenabled, but the device still
		 * has the level irq asserted. Rinse and repeat....
		 *
		 * While this works for edge type interrupts, we play
		 * it safe and reject unconditionally because we can't
		 * say for sure which type this interrupt really
		 * has. The type flags are unreliable as the
		 * underlying chip implementation can override them.
		 */
		pr_err("Threaded irq requested with handler=NULL and !ONESHOT for %s (irq %d)\n",
		       new->name, irq);
		ret = -EINVAL;
		goto out_unlock;
	}

	if (!shared) {
		init_waitqueue_head(&desc->wait_for_threads);

		/* Setup the type (level, edge polarity) if configured: */
		if (new->flags & IRQF_TRIGGER_MASK) {
			ret = __irq_set_trigger(desc,
						new->flags & IRQF_TRIGGER_MASK);

			if (ret)
				goto out_unlock;
		}

		/*
		 * Activate the interrupt. That activation must happen
		 * independently of IRQ_NOAUTOEN. request_irq() can fail
		 * and the callers are supposed to handle
		 * that. enable_irq() of an interrupt requested with
		 * IRQ_NOAUTOEN is not supposed to fail. The activation
		 * keeps it in shutdown mode, it merily associates
		 * resources if necessary and if that's not possible it
		 * fails. Interrupts which are in managed shutdown mode
		 * will simply ignore that activation request.
		 */
		ret = irq_activate(desc);
		if (ret)
			goto out_unlock;

		desc->istate &= ~(IRQS_AUTODETECT | IRQS_SPURIOUS_DISABLED | \
				  IRQS_ONESHOT | IRQS_WAITING);
		irqd_clear(&desc->irq_data, IRQD_IRQ_INPROGRESS);

		if (new->flags & IRQF_PERCPU) {
			irqd_set(&desc->irq_data, IRQD_PER_CPU);
			irq_settings_set_per_cpu(desc);
		}

		if (new->flags & IRQF_ONESHOT)
			desc->istate |= IRQS_ONESHOT;

		/* Exclude IRQ from balancing if requested */
		if (new->flags & IRQF_NOBALANCING) {
			irq_settings_set_no_balancing(desc);
			irqd_set(&desc->irq_data, IRQD_NO_BALANCING);
		}

		if (irq_settings_can_autoenable(desc)) {
			irq_startup(desc, IRQ_RESEND, IRQ_START_COND);
		} else {
			/*
			 * Shared interrupts do not go well with disabling
			 * auto enable. The sharing interrupt might request
			 * it while it's still disabled and then wait for
			 * interrupts forever.
			 */
			WARN_ON_ONCE(new->flags & IRQF_SHARED);
			/* Undo nested disables: */
			desc->depth = 1;
		}

	} else if (new->flags & IRQF_TRIGGER_MASK) {
		unsigned int nmsk = new->flags & IRQF_TRIGGER_MASK;
		unsigned int omsk = irqd_get_trigger_type(&desc->irq_data);

		if (nmsk != omsk)
			/* hope the handler works with current  trigger mode */
			pr_warn("irq %d uses trigger mode %u; requested %u\n",
				irq, omsk, nmsk);
	}

	*old_ptr = new;

	irq_pm_install_action(desc, new);

	/* Reset broken irq detection when installing new handler */
	desc->irq_count = 0;
	desc->irqs_unhandled = 0;

	/*
	 * Check whether we disabled the irq via the spurious handler
	 * before. Reenable it and give it another chance.
	 */
	if (shared && (desc->istate & IRQS_SPURIOUS_DISABLED)) {
		desc->istate &= ~IRQS_SPURIOUS_DISABLED;
		__enable_irq(desc);
	}

	raw_spin_unlock_irqrestore(&desc->lock, flags);
	chip_bus_sync_unlock(desc);
	mutex_unlock(&desc->request_mutex);

	irq_setup_timings(desc, new);

	/*
	 * Strictly no need to wake it up, but hung_task complains
	 * when no hard interrupt wakes the thread up.
	 */
    //唤醒中断处理线程
	if (new->thread)
		wake_up_process(new->thread);
	if (new->secondary)
		wake_up_process(new->secondary->thread);

	register_irq_proc(irq, desc);
	new->dir = NULL;
	register_handler_proc(irq, new);
	return 0;

mismatch:
	if (!(new->flags & IRQF_PROBE_SHARED)) {
		pr_err("Flags mismatch irq %d. %08x (%s) vs. %08x (%s)\n",
		       irq, new->flags, new->name, old->flags, old->name);
#ifdef CONFIG_DEBUG_SHIRQ
		dump_stack();
#endif
	}
	ret = -EBUSY;

out_unlock:
	raw_spin_unlock_irqrestore(&desc->lock, flags);

	if (!desc->action)
		irq_release_resources(desc);
out_bus_unlock:
	chip_bus_sync_unlock(desc);
	mutex_unlock(&desc->request_mutex);

out_thread:
	if (new->thread) {
		struct task_struct *t = new->thread;

		new->thread = NULL;
		kthread_stop(t);
		put_task_struct(t);
	}
	if (new->secondary && new->secondary->thread) {
		struct task_struct *t = new->secondary->thread;

		new->secondary->thread = NULL;
		kthread_stop(t);
		put_task_struct(t);
	}
out_mput:
	module_put(desc->owner);
	return ret;
}



//在目录下创建与SMP相关的文件 /proc/irq/123/
void register_irq_proc(unsigned int irq, struct irq_desc *desc)
{
	static DEFINE_MUTEX(register_lock);
	void __maybe_unused *irqp = (void *)(unsigned long) irq;
	char name [MAX_NAMELEN];

	if (!root_irq_dir || (desc->irq_data.chip == &no_irq_chip))
		return;

	/*
	 * irq directories are registered only when a handler is
	 * added, not when the descriptor is created, so multiple
	 * tasks might try to register at the same time.
	 */
	mutex_lock(&register_lock);

	if (desc->dir)
		goto out_unlock;

	sprintf(name, "%d", irq);

    //创建irq号的目录
	/* create /proc/irq/1234 */
	desc->dir = proc_mkdir(name, root_irq_dir);
	if (!desc->dir)
		goto out_unlock;

#ifdef CONFIG_SMP
    // 允许用户设置和查看中断处理程序的 CPU 亲和性掩码。
	/* create /proc/irq/<irq>/smp_affinity */
	proc_create_data("smp_affinity", 0644, desc->dir,
			 &irq_affinity_proc_ops, irqp);

    //显示中断处理程序的 CPU 亲和性建议，格式为十六进制字符串。
	/* create /proc/irq/<irq>/affinity_hint */
	proc_create_single_data("affinity_hint", 0444, desc->dir,
			irq_affinity_hint_proc_show, irqp);

    //允许用户设置和查看中断处理程序的 CPU 亲和性列表。
	/* create /proc/irq/<irq>/smp_affinity_list */
	proc_create_data("smp_affinity_list", 0644, desc->dir,
			 &irq_affinity_list_proc_ops, irqp);

    //显示中断处理程序所属的 NUMA 节点编号。
	proc_create_single_data("node", 0444, desc->dir, irq_node_proc_show,
			irqp);
# ifdef CONFIG_GENERIC_IRQ_EFFECTIVE_AFF_MASK
    //显示当前中断处理程序实际运行的 CPU 亲和性掩码。
	proc_create_single_data("effective_affinity", 0444, desc->dir,
			irq_effective_aff_proc_show, irqp);
    //显示当前中断处理程序实际运行的 CPU 亲和性列表。
	proc_create_single_data("effective_affinity_list", 0444, desc->dir,
			irq_effective_aff_list_proc_show, irqp);
# endif
#endif
    //记录虚假中断 没有有效中断源的次数
	proc_create_single_data("spurious", 0444, desc->dir,
			irq_spurious_proc_show, (void *)(long)irq);

out_unlock:
	mutex_unlock(&register_lock);
}


//用于创建目录 /proc/irq/123/i8042
void register_handler_proc(unsigned int irq, struct irqaction *action)
{
	char name [MAX_NAMELEN];
	struct irq_desc *desc = irq_to_desc(irq);

	if (!desc->dir || action->dir || !action->name ||
					!name_unique(irq, action))
		return;

	snprintf(name, MAX_NAMELEN, "%s", action->name);

    //创建目录 对应的设备名称
	/* create /proc/irq/1234/handler/ */
	action->dir = proc_mkdir(name, desc->dir);
}



```

```shell
#/proc/irq/1
-r--------. 1 root root 0 11月 22 10:27 affinity_hint
dr-xr-xr-x. 2 root root 0 11月 22 10:27 i8042 #键盘
-r--r--r--. 1 root root 0 11月 22 10:27 node
-rw-------. 1 root root 0 11月 22 10:27 smp_affinity
-rw-------. 1 root root 0 11月 22 10:27 smp_affinity_list
-r--r--r--. 1 root root 0 11月 22 10:27 spurious
```





**中断线程化**

中断线程化是指 注册中断处理函数时，可以设置线程中断处理函数，从而启动一个中断处理线程，在对应的硬件设备产生中断时，由具体的中断处理过程由中断处理线程负责，内核只负责对中断的快速接收确认和应答。

```shell
ps aux | grep irq #展示所有irq线程
```



```c
//kernel/irq/manage.c

//创建中断处理线程 irq

static int
setup_irq_thread(struct irqaction *new, unsigned int irq, bool secondary)
{
	struct task_struct *t;
	struct sched_param param = {
		.sched_priority = MAX_USER_RT_PRIO/2,
	};

	if (!secondary) {
		t = kthread_create(irq_thread, new, "irq/%d-%s", irq,
				   new->name);
	} else {
		t = kthread_create(irq_thread, new, "irq/%d-s-%s", irq,
				   new->name);
		param.sched_priority -= 1;
	}

	if (IS_ERR(t))
		return PTR_ERR(t);

	sched_setscheduler_nocheck(t, SCHED_FIFO, &param);

	/*
	 * We keep the reference to the task struct even if
	 * the thread dies to avoid that the interrupt code
	 * references an already freed task_struct.
	 */
	new->thread = get_task_struct(t);
	/*
	 * Tell the thread to set its affinity. This is
	 * important for shared interrupt handlers as we do
	 * not invoke setup_affinity() for the secondary
	 * handlers as everything is already set up. Even for
	 * interrupts marked with IRQF_NO_BALANCE this is
	 * correct as we want the thread to move to the cpu(s)
	 * on which the requesting code placed the interrupt.
	 */
	set_bit(IRQTF_AFFINITY, &new->thread_flags);
	return 0;
}
```





###### 硬件中断处理

在当前CPU开始处理硬件中断时，会禁用当前CPU的中断响应，但在SMP架构中，多个CPU接收到同一个中断信号的并发控制需要在代码内部加上某种锁来保障

```c
//arch/x86/kernel/irq.c


// do_IRQ处理所有的普通设备的中断请求，特殊的SMP跨CPU中断请求有他们自己特殊的处理函数
/*
 * do_IRQ handles all normal device IRQ's (the special
 * SMP cross-CPU interrupts have their own specific
 * handlers).
 */
__visible unsigned int __irq_entry do_IRQ(struct pt_regs *regs)
{
    //保存寄存器状态 即上下文
	struct pt_regs *old_regs = set_irq_regs(regs);
	struct irq_desc * desc;
    //从寄存器中读出中断向量号
	/* high bit used in ret_from_ code  */
	unsigned vector = ~regs->orig_ax;

    //通知内核进入IRQ处理状态
	entering_irq();

    //检查RCU是否生效
	/* entering_irq() tells RCU that we're not quiescent.  Check it. */
	RCU_LOCKDEP_WARN(!rcu_is_watching(), "IRQ failed to wake up RCU");

    //通过vector_irq找出中断向量和irq_desc的关系
	desc = __this_cpu_read(vector_irq[vector]);
	if (likely(!IS_ERR_OR_NULL(desc))) {
		if (IS_ENABLED(CONFIG_X86_32))
			handle_irq(desc, regs);
		else
            //处理中断
			generic_handle_irq_desc(desc);
	} else {
		ack_APIC_irq();

		if (desc == VECTOR_UNUSED) {
			pr_emerg_ratelimited("%s: %d.%d No irq handler for vector\n",
					     __func__, smp_processor_id(),
					     vector);
		} else {
			__this_cpu_write(vector_irq[vector], VECTOR_UNUSED);
		}
	}

    //退出IRQ状态
	exiting_irq();
	//恢复寄存器
	set_irq_regs(old_regs);
	return 1;
}



static inline void generic_handle_irq_desc(struct irq_desc *desc)
{
    //调用desc的handle_irq函数
	desc->handle_irq(desc);
}


//退出中断上下文 处理softirq
/*
 * Exit an interrupt context. Process softirqs if needed and possible:
 */
void irq_exit(void)
{
#ifndef __ARCH_IRQ_EXIT_IRQS_DISABLED
	local_irq_disable();
#else
	lockdep_assert_irqs_disabled();
#endif
	account_irq_exit_time(current);
	preempt_count_sub(HARDIRQ_OFFSET);
    //如果有挂起的softirq 处理softirq
	if (!in_interrupt() && local_softirq_pending())
		invoke_softirq();

	tick_irq_exit();
	rcu_irq_exit();
	trace_hardirq_exit(); /* must be last! */
}



static inline void invoke_softirq(void)
{
    //如果有ksoftirqd线程正在运行 那么就交给线程就行了
	if (ksoftirqd_running(local_softirq_pending()))
		return;

    //如果不是强制使用ksoftirqd线程处理
	if (!force_irqthreads) {
#ifdef CONFIG_HAVE_IRQ_EXIT_ON_IRQ_STACK
		/*
		 * We can safely execute softirq on the current stack if
		 * it is the irq stack, because it should be near empty
		 * at this stage.
		 */
        //处理softirq
		__do_softirq();
#else
		/*
		 * Otherwise, irq_exit() is called on the task stack that can
		 * be potentially deep already. So call softirq in its own stack
		 * to prevent from any overrun.
		 */
		do_softirq_own_stack();
#endif
	} else {
        //强制使用ksoftirqd线程处理 唤醒线程
		wakeup_softirqd();
	}
}

```



```c
// include/linux/irq.h


//各种 IRQ 类型的 IRQ 处理程序，
//可通过 desc->handle_irq() 调用
/*
 * Built-in IRQ handlers for various IRQ types,
 * callable via desc->handle_irq()
 */
extern void handle_level_irq(struct irq_desc *desc);
extern void handle_fasteoi_irq(struct irq_desc *desc);
extern void handle_edge_irq(struct irq_desc *desc);
extern void handle_edge_eoi_irq(struct irq_desc *desc);
extern void handle_simple_irq(struct irq_desc *desc);
extern void handle_untracked_irq(struct irq_desc *desc);
extern void handle_percpu_irq(struct irq_desc *desc);
extern void handle_percpu_devid_irq(struct irq_desc *desc);
extern void handle_bad_irq(struct irq_desc *desc);
extern void handle_nested_irq(unsigned int irq);


//以handle_percpu_irq为例子
void handle_percpu_irq(struct irq_desc *desc)
{
    //获取irq_chip
	struct irq_chip *chip = irq_desc_get_chip(desc);

	/*
	 * PER CPU interrupts are not serialized. Do not touch
	 * desc->tot_count.
	 */
    //增加当前cpu处理中断的次数
	__kstat_incr_irqs_this_cpu(desc);

    //回复中断控制器 代表当前中断已经被响应
	if (chip->irq_ack)
		chip->irq_ack(&desc->irq_data);

    //处理中断
	handle_irq_event_percpu(desc);

    //回复中断控制器 当前中断已经处理完成
	if (chip->irq_eoi)
		chip->irq_eoi(&desc->irq_data);
}



//kernel/irq/handle.c
//处理中断

irqreturn_t handle_irq_event_percpu(struct irq_desc *desc)
{
	irqreturn_t retval;
	unsigned int flags = 0;
    
	//处理中断的核心方法
	retval = __handle_irq_event_percpu(desc, &flags);

	add_interrupt_randomness(desc->irq_data.irq, flags);

	if (!noirqdebug)
		note_interrupt(desc, retval);
	return retval;
}


irqreturn_t __handle_irq_event_percpu(struct irq_desc *desc, unsigned int *flags)
{
    //设置方法返回值
	irqreturn_t retval = IRQ_NONE;
	//获取IRQ线编号
    unsigned int irq = desc->irq_data.irq;
	//获取当前irq_desc的irqaction
    struct irqaction *action;

    //记录中断处理时间
	record_irq_time(desc);

    //当内核收到中断时，它会依次调用该线路上每个已注册的处理程序。因此，处理程序必须能够区分它是否生成了给定的中断。如果其关联设备未生成中断，处理程序必须快速退出。
    
    //遍历每一个irqaction
	for_each_action_of_desc(desc, action) {
		irqreturn_t res;

        //追踪当前中断处理进入
		trace_irq_handler_entry(irq, action);
		//调用action->handler处理中断
        res = action->handler(irq, action->dev_id);
        ////追踪当前中断处理退出
		trace_irq_handler_exit(irq, action, res);

       //如果中断没有被禁用 打印警告日志 并且禁用当前cpu中断 
		if (WARN_ONCE(!irqs_disabled(),"irq %u handler %pS enabled interrupts\n",
			      irq, action->handler))
			local_irq_disable();

        //根据返回值处理
		switch (res) {
                
		case IRQ_WAKE_THREAD:
			/*
			 * Catch drivers which return WAKE_THREAD but
			 * did not set up a thread function
			 */
			if (unlikely(!action->thread_fn)) {
				warn_no_thread(irq, action);
				break;
			}
			//唤醒线程
			__irq_wake_thread(desc, action);

			/* Fall through - to add to randomness */
		case IRQ_HANDLED:
			*flags |= action->flags;
			break;

		default:
			break;
		}
		//将结果加入到retval中
		retval |= res;
	}

	return retval;
}
```



**CPU禁用中断**

EFLAGS 寄存器是一个 32 位寄存器，用于存储各种状态标志和控制标志。IF 标志位是其中一个重要的控制标志，用于控制 CPU 是否响应可屏蔽中断。

CPU上NMI引脚上的中断不可被CPU屏蔽，而EFLAGS寄存器主要屏蔽INTR引脚上的中断，即中断控制器发送来的中断。

<img src=".\images\eflags寄存器.png" alt="image-20241114091435331" style="zoom:50%;" />

- **OF (Overflow Flag)**：溢出标志，表示算术运算结果溢出。
- **DF (Direction Flag)**：方向标志，用于字符串操作指令的方向控制。
- **IF (Interrupt Flag)**：中断标志，控制 CPU 是否响应可屏蔽中断。**IF = 1**：允许 CPU 响应可屏蔽中断。**IF = 0**：禁止 CPU 响应可屏蔽中断。
- **TF (Trap Flag)**：陷阱标志，用于单步调试。
- **SF (Sign Flag)**：符号标志，表示算术运算结果的符号。
- **ZF (Zero Flag)**：零标志，表示算术运算结果为零。
- **AF (Auxiliary Carry Flag)**：辅助进位标志，用于 BCD 算术运算。
- **PF (Parity Flag)**：奇偶标志，表示算术运算结果的低 8 位中有多少个 1。
- **CF (Carry Flag)**：进位标志，表示算术运算结果的进位或借位。

```assembly
/*cli：清零 IF 标志位，禁用可屏蔽中断。*/
cli
/*sti：设置 IF 标志位，启用可屏蔽中断。*/
sti
```

```assembly
pushf /*用于操作eflags寄存器 将eflags寄存器的值推入栈中*/
```







**禁用单条IRQ线**

```c
//禁用整个IRQ线上的所有中断
void disable_irq(unsigned int irq);
void disable_irq_nosync(unsigned int irq);
//启用整个IRQ线上的所有中断
void enable_irq(unsigned int irq);
void synchronize_irq(unsigned int irq);
```



**多设备共享同一个IRQ线**

当内核收到中断时，它会依次调用该线路上每个已注册的处理程序。因此，处理程序必须能够区分它是否生成了给定的中断。如果其关联设备未生成中断，处理程序必须快速退出。这要求硬件设备具有处理程序可以检查的**状态寄存器ICR**(Interrupt Cause Read)或者类似的机制，大多数硬件确实具有这样的功能。

```c
// 以e1000e的中断处理函数为例

/**
 * e1000_intr - Interrupt Handler
 * @irq: interrupt number
 * @data: pointer to a network interface device structure
 **/
static irqreturn_t e1000_intr(int irq, void *data)
{
        struct net_device *netdev = data;
        struct e1000_adapter *adapter = netdev_priv(netdev);
        struct e1000_hw *hw = &adapter->hw;
    	//检查网卡的寄存器 看看这个中断是不是网卡产生的
        u32 icr = er32(ICR);

    	//检查当前这条IRQ上的中断是不是e1000e这个设备产生的 如果不是立即返回
        if (unlikely((!icr)))
               return IRQ_NONE;  /* Not our interrupt */

        /* we might have caused the interrupt, but the above
         * read cleared it, and just in case the driver is
         * down there is nothing to do so return handled
         */
        if (unlikely(test_bit(__E1000_DOWN, &adapter->flags)))
               return IRQ_HANDLED;

        if (unlikely(icr & (E1000_ICR_RXSEQ | E1000_ICR_LSC))) {
               hw->get_link_status = 1;
               /* guard against interrupt when we're going down */
               if (!test_bit(__E1000_DOWN, &adapter->flags))
                      schedule_delayed_work(&adapter->watchdog_task, 1);
        }

        /* disable interrupts, without the synchronize_irq bit */
        ew32(IMC, ~0);
        E1000_WRITE_FLUSH();

        if (likely(napi_schedule_prep(&adapter->napi))) {
               adapter->total_tx_bytes = 0;
               adapter->total_tx_packets = 0;
               adapter->total_rx_bytes = 0;
               adapter->total_rx_packets = 0;
               __napi_schedule(&adapter->napi);
        } else {
               /* this really should not happen! if it does it is basically a
                * bug, but not a hard error, so enable ints and continue
                */
               if (!test_bit(__E1000_DOWN, &adapter->flags))
                      e1000_irq_enable(adapter);
        }

        return IRQ_HANDLED;
}
```



##### 下半部分

在上半部分的中断处理程序中，由于是异步中断，在中断过程中会屏蔽中断，如果中断处理程序的步骤非常繁杂耗时，那么就会影响到后续中断的处理。为了解决快速响应中断的需求，引入了下半部分，在中断处理程序处理一些必要的、快速的和响应硬件的逻辑后，恢复中断屏蔽，将类似耗时却又不急的操作放于下半部分执行，下半部分位于进程上下文中执行，在下半部分执行时不会屏蔽中断，提高了中断的处理响应速度。下半部分的处理机制有三种：**softirq、tasklet、work queue**

大部分驱动程序会选择使用tasklet来作为下半部分处理，一部分对性能要求较高的会使用softirq，例如：

**网络** 和 **SCSI**（Small Computer System Interface，小型计算机系统接口）是一种标准的硬件接口，用于连接计算机和外部设备，如硬盘驱动器、光盘驱动器、磁带驱动器、扫描仪和打印机等。

- `NET_TX_SOFTIRQ`  网络发送softirq
- `NET_RX_SOFTIRQ` 网络接收softirq
- `BLOCK_SOFTIRQ` 块设备softirq





###### softirq机制



**softirq类型**

```c
//include/linux/interrupt.h

/*应当避免使用新的softirq类型，现有的tasklets已经能够满足需求了*/
/* PLEASE, avoid to allocate new softirqs, if you need not _really_ high
   frequency threaded job scheduling. For almost all the purposes
   tasklets are more than enough. F.e. all serial device BHs et
   al. should be converted to tasklets, not to softirqs.
 */
// softirq的类型
enum
{
    HI_SOFTIRQ=0, // 高优先级 用于tasklets 
    TIMER_SOFTIRQ, // 定时器softirq
    NET_TX_SOFTIRQ, // 网络发送softirq
    NET_RX_SOFTIRQ, // 网络接收softirq
    BLOCK_SOFTIRQ, // 块设备softirq
    IRQ_POLL_SOFTIRQ, // IRQ轮询softirq
    TASKLET_SOFTIRQ, // 普通优先级 用于tasklets
    SCHED_SOFTIRQ, // 调度softirq
    HRTIMER_SOFTIRQ, // 高分辨率定时器softirq，提供更高精度的定时服务
    RCU_SOFTIRQ,    /* Preferable RCU should always be the last softirq */ //RCU机制softirq

    NR_SOFTIRQS // softirq类型长度计数
};



//kernel/softirq.c

//数量长度就是枚举的最大值 即 10
//softirq在编译时静态分配，与 tasklet 不同，无法动态注册和销毁softirq
static struct softirq_action softirq_vec[NR_SOFTIRQS] __cacheline_aligned_in_smp;



//include/linux/interrupt.h

/* softirq mask 和 active 字段移至 irq_cpustat_t in
 * asm/hardirq.h 以获得更好的缓存使用率。KAO
 * softirq mask and active fields moved to irq_cpustat_t in
 * asm/hardirq.h to get better cache usage.  KAO
 */
//表示单个softirq条目的结构
struct softirq_action
{
        void   (*action)(struct softirq_action *);
};
```

**softirq注册**

softirq的注册都是通过驱动程序来调用open_softirq注册的

```c
//kernel/softirq.c

// 将对应的softirq处理程序注册到对应的softirq类型上
void open_softirq(int nr, void (*action)(struct softirq_action *))
{
    softirq_vec[nr].action = action;
}
```



**softirq触发**

softirq的触发则是通过softirq类型下标触发数组对应的softirq

```c
// kernel/softirq.c

//触发对应的softirq类型
void raise_softirq(unsigned int nr)
{
    unsigned long flags;

    //保存当前eflags寄存器的值 设置屏蔽中断
    local_irq_save(flags);
    //触发softirq事件
    raise_softirq_irqoff(nr);
    //恢复eflags寄存器的值 恢复中断
    local_irq_restore(flags);
}


/*
 * This function must run with irqs disabled!
 */
inline void raise_softirq_irqoff(unsigned int nr)
{
    
	__raise_softirq_irqoff(nr);

	/*
	 * If we're in an interrupt or softirq, we're done
	 * (this also catches softirq-disabled code). We will
	 * actually run the softirq once we return from
	 * the irq or softirq.
	 *
	 * Otherwise we wake up ksoftirqd to make sure we
	 * schedule the softirq soon.
	 */
    //如果我们正处在正处在softirq处理程序do_softirq中，softrirq处理完成后还会restart或者ksoftirqd，那么就不用唤醒ksoftirqd
    //否则直接需要唤醒ksoftirqd 确保此次softirq的执行 不必等到下一个硬件中断触发softirq才执行
	if (!in_interrupt())
		wakeup_softirqd();
}

void __raise_softirq_irqoff(unsigned int nr)
{
    //跟踪一下触发softirq
	trace_softirq_raise(nr);
    //设置CPU独有数据irq_cpustat_t中的__softirq_pending字段
	or_softirq_pending(1UL << nr);
}
```





**softirq执行**

一个softirq只有被标记后才会执行，通常在中断处理程序完成后，会标记待处理的softirq类型。

当在下面几种地方，softirq会被处理

- 从一个硬件中断处理程序返回时
- 在ksoftirqd内核线程中
- 在处理softirq的代码中重复调用，如网络子系统中就会在处理softirq的代码中反复调用do_softirq()

```c
// arch/x86/include/asm/hardirq.h


// 宏定义 每个CPU存放在.data区域的独有数据
DECLARE_PER_CPU_SHARED_ALIGNED(irq_cpustat_t, irq_stat);


// 中断相关的CPU独有数据信息
typedef struct {
    	//待处理的softirq类型位图标识
        u16         __softirq_pending;
#if IS_ENABLED(CONFIG_KVM_INTEL)
        u8          kvm_cpu_l1tf_flush_l1d;
#endif
        unsigned int __nmi_count;      /* arch dependent */
#ifdef CONFIG_X86_LOCAL_APIC
        unsigned int apic_timer_irqs;  /* arch dependent */
        unsigned int irq_spurious_count;
        unsigned int icr_read_retry_count;
#endif
#ifdef CONFIG_HAVE_KVM
        unsigned int kvm_posted_intr_ipis;
        unsigned int kvm_posted_intr_wakeup_ipis;
        unsigned int kvm_posted_intr_nested_ipis;
#endif
        unsigned int x86_platform_ipis;        /* arch dependent */
        unsigned int apic_perf_irqs;
        unsigned int apic_irq_work_irqs;
#ifdef CONFIG_SMP
        unsigned int irq_resched_count;
        unsigned int irq_call_count;
#endif
        unsigned int irq_tlb_count;
#ifdef CONFIG_X86_THERMAL_VECTOR
        unsigned int irq_thermal_count;
#endif
#ifdef CONFIG_X86_MCE_THRESHOLD
        unsigned int irq_threshold_count;
#endif
#ifdef CONFIG_X86_MCE_AMD
        unsigned int irq_deferred_error_count;
#endif
#ifdef CONFIG_X86_HV_CALLBACK_VECTOR
        unsigned int irq_hv_callback_count;
#endif
#if IS_ENABLED(CONFIG_HYPERV)
        unsigned int irq_hv_reenlightenment_count;
        unsigned int hyperv_stimer0_count;
#endif
} ____cacheline_aligned irq_cpustat_t;


// arch/x86/include/asm/current.h

struct task_struct;

DECLARE_PER_CPU(struct task_struct *, current_task);

static __always_inline struct task_struct *get_current(void)
{
	return this_cpu_read_stable(current_task);
}

#define current get_current()




```



**do_softirq**

```c
// kernel/softirq.c

//处理执行softirq

asmlinkage __visible void do_softirq(void)
{
    __u32 pending;
    unsigned long flags;

    //判断当前是否处于硬件中断、softirq、NMI中断上下文中
    if (in_interrupt())
       return;

    //保存当前CPU的中断状态，屏蔽当前CPU硬件中断
    local_irq_save(flags);

    //获取当前挂起待处理的softirq，读取的是CPU独有数据irq_cpustat_t中的__softirq_pending字段
    pending = local_softirq_pending();

    //如果有挂起待处理的softirq 并且没有ksoftirqd线程在执行 那么立刻处理
    if (pending && !ksoftirqd_running(pending))
        //这里是之间调用了__do_softirq()
       do_softirq_own_stack();

    //恢复刚才保存的中断状态，恢复接收当前CPU硬件中断
    local_irq_restore(flags);
}

//处理执行softirq
asmlinkage __visible void __softirq_entry __do_softirq(void)
{
    //当前softirq处理的最大时间即2ms, end = 当前时钟中断 + 2ms的时钟中断数量
	unsigned long end = jiffies + MAX_SOFTIRQ_TIME;
    //保存当前进程的flags字段
	unsigned long old_flags = current->flags;
    //设置最大restart次数
	int max_restart = MAX_SOFTIRQ_RESTART;
	//softirq处理函数的引用
    struct softirq_action *h;
    //是否处于硬件中断
	bool in_hardirq;
    //当前挂起的待处理的softirq位图
	__u32 pending;
	int softirq_bit;

	/*
	 * Mask out PF_MEMALLOC as the current task context is borrowed for the
	 * softirq. A softirq handled, such as network RX, might set PF_MEMALLOC
	 * again if the socket is related to swapping.
	 */
    //将当前进程的flags去掉内存分配 防止内存分配对softirq产生影响
	current->flags &= ~PF_MEMALLOC;

    //获取当前挂起的待处理的softirq
	pending = local_softirq_pending();
    //记录下进入softirq处理时间
	account_irq_enter_time(current);

    //通过对preempt_count的softirq计数器增加 来禁用当前CPU的softirq处理
	__local_bh_disable_ip(_RET_IP_, SOFTIRQ_OFFSET);
    //锁依赖检查
	in_hardirq = lockdep_softirq_start();

restart:
    //上面保存了待处理的softirq位图 这里可以对CPU独有数据irq_cpustat_t中的__softirq_pending字段清0了
	/* Reset the pending bitmask before enabling irqs */
	set_softirq_pending(0);

    //启用当前CPU中断
	local_irq_enable();

    //赋值softirq_vec
	h = softirq_vec;

    //遍历每个pending的bit 处理对应softirq类型
	while ((softirq_bit = ffs(pending))) {
		unsigned int vec_nr;
		int prev_count;

        //当前正在处理的softirq_action引用
		h += softirq_bit - 1;

        //当前正在处理的softirq类型的枚举编号
		vec_nr = h - softirq_vec;
        //获得preempt_count
		prev_count = preempt_count();

        //增加对应softirq类型的处理次数
		kstat_incr_softirqs_this_cpu(vec_nr);

        //记录进入softirq处理
		trace_softirq_entry(vec_nr);
        //调用action方法
		h->action(h);
        //记录退出softirq处理
		trace_softirq_exit(vec_nr);
        
        //如果在处理softirq时 preempt_count发生变化 打印错误并恢复preempt_count
		if (unlikely(prev_count != preempt_count())) {
			pr_err("huh, entered softirq %u %s %p with preempt_count %08x, exited with %08x?\n",
			       vec_nr, softirq_to_name[vec_nr], h->action,
			       prev_count, preempt_count());
			preempt_count_set(prev_count);
		}
        //迭代下一个softirq_action检查
		h++;
		pending >>= softirq_bit;
	}

    //如果当前进程是ksoftirqd
	if (__this_cpu_read(ksoftirqd) == current)
        //处理RCU相关的softirq
		rcu_softirq_qs();
    
    //禁用当前CPU的中断
	local_irq_disable();

    //获取新的softirq
	pending = local_softirq_pending();
    
    //如果有新的softirq要处理
	if (pending) {
        //如果时间没超时 && 不需要重新调度 && 也没有到达最大restart次数
		if (time_before(jiffies, end) && !need_resched() &&
		    --max_restart)
            //restart继续处理softirq
			goto restart;
        
		//否则唤醒ksoftirqd线程处理
		wakeup_softirqd();
	}

	lockdep_softirq_end(in_hardirq);
	//记录退出时间
    account_irq_exit_time(current);
    //启用当前CPU的softirq处理
	__local_bh_enable(SOFTIRQ_OFFSET);
	WARN_ON_ONCE(in_interrupt());
    //恢复当前进程的flags
	current_restore_flags(old_flags, PF_MEMALLOC);
}



```



**in_interrupt()**

```c
//arch/x86/include/asm/preempt.h

//每个CPU独有一个__preempt_count

DECLARE_PER_CPU(int, __preempt_count);

/*
 * We mask the PREEMPT_NEED_RESCHED bit so as not to confuse all current users
 * that think a non-zero value indicates we cannot preempt.
 */
static __always_inline int preempt_count(void)
{
	return raw_cpu_read_4(__preempt_count) & ~PREEMPT_NEED_RESCHED;
}


// include/linux/preempt.h


// preempt_count() 32位被划分为五个部分

/*
*         PREEMPT_MASK: 0x000000ff 0-7位 抢占式计数器
*         SOFTIRQ_MASK: 0x0000ff00 8-15位 softirq计数器
*         HARDIRQ_MASK: 0x000f0000 16-19位 硬中断计数器
*             NMI_MASK: 0x00100000 20位 NMI 不可屏蔽中断计数器
* PREEMPT_NEED_RESCHED: 0x80000000 31位 用于表明内核有更高优先级的任务需要被调度
*/

#define PREEMPT_BITS	8
#define SOFTIRQ_BITS	8
#define HARDIRQ_BITS	4
#define NMI_BITS	1


//下面五个方法就是判断当前是否处于硬件中断或者下半部分
//原理就是通过上面的计数器 > 0


/*
 * Are we doing bottom half or hardware interrupt processing?
 *
 * in_irq()       - We're in (hard) IRQ context
 * in_softirq()   - We have BH disabled, or are processing softirqs
 * in_interrupt() - We're in NMI,IRQ,SoftIRQ context or have BH disabled
 * in_serving_softirq() - We're in softirq context
 * in_nmi()       - We're in NMI context
 * in_task()	  - We're in task context
 *
 * Note: due to the BH disabled confusion: in_softirq(),in_interrupt() really
 *       should not be used in new code.
 */
#define in_irq()		(hardirq_count())
#define in_softirq()		(softirq_count())
#define in_interrupt()		(irq_count())
#define in_serving_softirq()	(softirq_count() & SOFTIRQ_OFFSET)
#define in_nmi()		(preempt_count() & NMI_MASK)
#define in_task()		(!(preempt_count() & \
				   (NMI_MASK | HARDIRQ_MASK | SOFTIRQ_OFFSET)))


```



**ksoftirqd线程**

```shell
ps aux | grep ksoftirqd #展示所有ksoftirqd线程
```



```c

//include/linux/interrupt.h
//每个CPU独有ksoftirqd线程
DECLARE_PER_CPU(struct task_struct *, ksoftirqd);


//kernel/softirq.c


static struct smp_hotplug_thread softirq_threads = {
	.store			= &ksoftirqd, // ksoftirqd线程地址
	.thread_should_run	= ksoftirqd_should_run, //用于判断线程是否应该运行
	.thread_fn		= run_ksoftirqd, //线程主函数
	.thread_comm		= "ksoftirqd/%u", //线程名
};

// 创建softirq_threads
static __init int spawn_ksoftirqd(void)
{
	cpuhp_setup_state_nocalls(CPUHP_SOFTIRQ_DEAD, "softirq:dead", NULL,
				  takeover_tasklets);
	BUG_ON(smpboot_register_percpu_thread(&softirq_threads));

	return 0;
}
early_initcall(spawn_ksoftirqd);

// kernel/smpboot.c



/**
 * smpboot_register_percpu_thread - Register a per_cpu thread related
 * 					    to hotplug
 * @plug_thread:	Hotplug thread descriptor
 *
 * Creates and starts the threads on all online cpus.
 */
int smpboot_register_percpu_thread(struct smp_hotplug_thread *plug_thread)
{
	unsigned int cpu;
	int ret = 0;

	get_online_cpus();
	mutex_lock(&smpboot_threads_lock);
    //对每个cpu创建内核线程
	for_each_online_cpu(cpu) {
		ret = __smpboot_create_thread(plug_thread, cpu);
		if (ret) {
			smpboot_destroy_threads(plug_thread);
			goto out;
		}
		smpboot_unpark_thread(plug_thread, cpu);
	}
	list_add(&plug_thread->list, &hotplug_threads);
out:
	mutex_unlock(&smpboot_threads_lock);
	put_online_cpus();
	return ret;
}
EXPORT_SYMBOL_GPL(smpboot_register_percpu_thread);



// 创建内核线程
static int
__smpboot_create_thread(struct smp_hotplug_thread *ht, unsigned int cpu)
{
	struct task_struct *tsk = *per_cpu_ptr(ht->store, cpu);
	struct smpboot_thread_data *td;

	if (tsk)
		return 0;

	td = kzalloc_node(sizeof(*td), GFP_KERNEL, cpu_to_node(cpu));
	if (!td)
		return -ENOMEM;
	td->cpu = cpu;
	td->ht = ht;
	//创建内核线程 与cpu绑定
	tsk = kthread_create_on_cpu(smpboot_thread_fn, td, cpu,
				    ht->thread_comm);
	if (IS_ERR(tsk)) {
		kfree(td);
		return PTR_ERR(tsk);
	}
	/*
	 * Park the thread so that it could start right on the CPU
	 * when it is available.
	 */
	kthread_park(tsk);
	get_task_struct(tsk);
	*per_cpu_ptr(ht->store, cpu) = tsk;
	if (ht->create) {
		/*
		 * Make sure that the task has actually scheduled out
		 * into park position, before calling the create
		 * callback. At least the migration thread callback
		 * requires that the task is off the runqueue.
		 */
		if (!wait_task_inactive(tsk, TASK_PARKED))
			WARN_ON(1);
		else
			ht->create(cpu);
	}
	return 0;
}


//内核线程实际运行的方法
/**
 * smpboot_thread_fn - percpu hotplug thread loop function
 * @data:	thread data pointer
 *
 * Checks for thread stop and park conditions. Calls the necessary
 * setup, cleanup, park and unpark functions for the registered
 * thread.
 *
 * Returns 1 when the thread should exit, 0 otherwise.
 */
static int smpboot_thread_fn(void *data)
{
	struct smpboot_thread_data *td = data;
	struct smp_hotplug_thread *ht = td->ht;

    //死循环
	while (1) {
		set_current_state(TASK_INTERRUPTIBLE);
        //禁用抢占@todo
		preempt_disable();
        //线程是否应该停止
		if (kthread_should_stop()) {
			__set_current_state(TASK_RUNNING);
			preempt_enable();
			/* cleanup must mirror setup */
			if (ht->cleanup && td->status != HP_THREAD_NONE)
				ht->cleanup(td->cpu, cpu_online(td->cpu));
			kfree(td);
			return 0;
		}
		
        //线程是否应该park
		if (kthread_should_park()) {
			__set_current_state(TASK_RUNNING);
			preempt_enable();
			if (ht->park && td->status == HP_THREAD_ACTIVE) {
				BUG_ON(td->cpu != smp_processor_id());
				ht->park(td->cpu);
				td->status = HP_THREAD_PARKED;
			}
			kthread_parkme();
			/* We might have been woken for stop */
			continue;
		}

		BUG_ON(td->cpu != smp_processor_id());

        //处理线程的状态
		/* Check for state change setup */
		switch (td->status) {
		case HP_THREAD_NONE:
			__set_current_state(TASK_RUNNING);
			preempt_enable();
			if (ht->setup)
				ht->setup(td->cpu);
			td->status = HP_THREAD_ACTIVE;
			continue;

		case HP_THREAD_PARKED:
			__set_current_state(TASK_RUNNING);
			preempt_enable();
			if (ht->unpark)
				ht->unpark(td->cpu);
			td->status = HP_THREAD_ACTIVE;
			continue;
		}

        //检查当前线程主函数是否可以执行
		if (!ht->thread_should_run(td->cpu)) {
			preempt_enable_no_resched();
            //不执行就调度
			schedule();
		} else {
            //执行线程主函数
			__set_current_state(TASK_RUNNING);
			preempt_enable();
            //调用实际绑定的内核线程函数
			ht->thread_fn(td->cpu);
		}
	}
}





//ksoftirqd线程主函数
static void run_ksoftirqd(unsigned int cpu)
{
    //禁用中断
	local_irq_disable();
	//如果挂起待处理的softirq
    if (local_softirq_pending()) {
        
        //@todo stack
		/*
		 * We can safely run softirq on inline stack, as we are not deep
		 * in the task stack here.
		 */
        //调用__do_softirq处理
		__do_softirq();
        //启用中断
		local_irq_enable();
		//重新进行进程调度
        cond_resched();
		return;
	}
    //启用中断
	local_irq_enable();
}



/*
 * If ksoftirqd is scheduled, we do not want to process pending softirqs
 * right now. Let ksoftirqd handle this at its own rate, to get fairness,
 * unless we're doing some of the synchronous softirqs.
 */
#define SOFTIRQ_NOW_MASK ((1 << HI_SOFTIRQ) | (1 << TASKLET_SOFTIRQ))
static bool ksoftirqd_running(unsigned long pending)
{
    struct task_struct *tsk = __this_cpu_read(ksoftirqd);

    if (pending & SOFTIRQ_NOW_MASK)
       return false;
    return tsk && (tsk->state == TASK_RUNNING) &&
       !__kthread_should_park(tsk);
}


/*
 * we cannot loop indefinitely here to avoid userspace starvation,
 * but we also don't want to introduce a worst case 1/HZ latency
 * to the pending events, so lets the scheduler to balance
 * the softirq load for us.
 * 
 * 唤醒当前CPU的ksoftirqd线程
 */
static void wakeup_softirqd(void)
{
	/* Interrupts are disabled: no need to stop preemption */
	struct task_struct *tsk = __this_cpu_read(ksoftirqd);

	if (tsk && tsk->state != TASK_RUNNING)
		wake_up_process(tsk);
}


```



###### tasklets

tasklets是依赖于softirq的机制，通过维护两个tasklet_head队列，一旦有新tasklet加入队列，就会触发softirq的`TASKLET_SOFTIRQ`或者`HI_SOFTIRQ`，然后通过注册到softirq的tasklet_action或者tasklet_hi_action处理

```c
// kernel/softirq.c


//CPU独有数据 
// 高优先级和低优先级的两个队列 每个CPU独有两个队列
static DEFINE_PER_CPU(struct tasklet_head, tasklet_vec);
static DEFINE_PER_CPU(struct tasklet_head, tasklet_hi_vec);

/*
 * Tasklets
 */
struct tasklet_head {
	struct tasklet_struct *head;
	struct tasklet_struct **tail;
};

//tasklet_struct中的state
enum
{
	TASKLET_STATE_SCHED,	/* Tasklet is scheduled for execution */
	TASKLET_STATE_RUN	/* Tasklet is running (SMP only) */
};



struct tasklet_struct
{
	struct tasklet_struct *next; 
	unsigned long state; // 状态
	atomic_t count; //引用计数器
	void (*func)(unsigned long); // 处理tasklet的函数
	unsigned long data;
};



void __init softirq_init(void)
{
    int cpu;

    for_each_possible_cpu(cpu) {
       per_cpu(tasklet_vec, cpu).tail =
          &per_cpu(tasklet_vec, cpu).head;
       per_cpu(tasklet_hi_vec, cpu).tail =
          &per_cpu(tasklet_hi_vec, cpu).head;
    }

    open_softirq(TASKLET_SOFTIRQ, tasklet_action);
    open_softirq(HI_SOFTIRQ, tasklet_hi_action);
}
```



**tasklet创建**

tasklet的创建可以是静态的，也可以是动态创建的

```c
// include/linux/interrupt.h

// 静态创建
#define DECLARE_TASKLET(name, func, data) \
struct tasklet_struct name = { NULL, 0, ATOMIC_INIT(0), func, data }

#define DECLARE_TASKLET_DISABLED(name, func, data) \
struct tasklet_struct name = { NULL, 0, ATOMIC_INIT(1), func, data }




//kernel/softirq.c

//动态创建
void tasklet_init(struct tasklet_struct *t,
         void (*func)(unsigned long), unsigned long data)
{
    t->next = NULL;
    t->state = 0;
    atomic_set(&t->count, 0);
    t->func = func;
    t->data = data;
}
```









**tasklet_schedule**

```c
//include/linux/interrupt.h

//将tasklet_struct加入到当前CPU的tasklet_head队列中，触发softirq

static inline void tasklet_schedule(struct tasklet_struct *t)
{
    //设置tasklet_struct状态为TASKLET_STATE_SCHED
    if (!test_and_set_bit(TASKLET_STATE_SCHED, &t->state))
       __tasklet_schedule(t);
}

static inline void tasklet_hi_schedule(struct tasklet_struct *t)
{
	if (!test_and_set_bit(TASKLET_STATE_SCHED, &t->state))
		__tasklet_hi_schedule(t);
}

void __tasklet_schedule(struct tasklet_struct *t)
{
	__tasklet_schedule_common(t, &tasklet_vec,
				  TASKLET_SOFTIRQ);
}

void __tasklet_hi_schedule(struct tasklet_struct *t)
{
	__tasklet_schedule_common(t, &tasklet_hi_vec,
				  HI_SOFTIRQ);
}


static void __tasklet_schedule_common(struct tasklet_struct *t,
				      struct tasklet_head __percpu *headp,
				      unsigned int softirq_nr)
{
	struct tasklet_head *head;
	unsigned long flags;

    //保存当前eflags的值 中断屏蔽
	local_irq_save(flags);
	head = this_cpu_ptr(headp);
    //把tasklet_struct加入到当前CPU的tasklet_head队列中
	t->next = NULL;
	*head->tail = t;
	head->tail = &(t->next);
    //触发TASKLET_SOFTIRQ或者HI_SOFTIRQ
	raise_softirq_irqoff(softirq_nr);
    //恢复
	local_irq_restore(flags);
}
```

**tasklets处理**

```c
// kernel/softirq.c


static __latent_entropy void tasklet_action(struct softirq_action *a)
{
    tasklet_action_common(a, this_cpu_ptr(&tasklet_vec), TASKLET_SOFTIRQ);
}

static __latent_entropy void tasklet_hi_action(struct softirq_action *a)
{
    tasklet_action_common(a, this_cpu_ptr(&tasklet_hi_vec), HI_SOFTIRQ);
}



static void tasklet_action_common(struct softirq_action *a,
				  struct tasklet_head *tl_head,
				  unsigned int softirq_nr)
{
	struct tasklet_struct *list;

    //禁用中断
	local_irq_disable();
	//保存tasklet_head到list中
    list = tl_head->head;
    //清空CPU独有的tasklet_head
	tl_head->head = NULL;
	tl_head->tail = &tl_head->head;
    //中断恢复
	local_irq_enable();

    //循环处理tasklet_struct
	while (list) {
        //当前处理的tasklet_struct
		struct tasklet_struct *t = list;

        //迭代下一个tasklet_struct
		list = list->next;

        //尝试给当前tasklet_struct加锁
		if (tasklet_trylock(t)) {
            //如果tasklet_struct的count为0
			if (!atomic_read(&t->count)) {
                //tasklet_struct的状态为TASKLET_STATE_SCHED
				if (!test_and_clear_bit(TASKLET_STATE_SCHED,
							&t->state))
					BUG();
                  //调用tasklet_struct的func
				t->func(t->data);
                  //解锁
				tasklet_unlock(t);
				continue;
			}
            //解锁
			tasklet_unlock(t);
		}
		//禁用中断
		local_irq_disable();
        //重新加入到当前CPU的tasklet_head中
		t->next = NULL;
		*tl_head->tail = t;
		tl_head->tail = &t->next;
        //触发softirq
		__raise_softirq_irqoff(softirq_nr);
		//中断恢复
        local_irq_enable();
	}
}
```



###### work queue

工作队列和softirq机制不同，工作队列可以把工作推后，交由一个内核线程来完成，这个下半部分总会在进程上下文中执行，这样工作队列就会占尽进程进程上下文的所有优势。最主要的是工作队列允许重新调度甚至是睡眠。而softirq在中断上下文中执行，不允许睡眠和阻塞，ksoftirqd线程是在进程上下文执行。

> **中断上下文**：CPU跳到内核设置好的中断处理代码中去，由这部分内核代码来处理中断，这个处理过程中的上下文就是**中断上下文**。
>
> **进程上下文**：线程被抽象成为一个task_struct，可以被内核进行进程调度。

![](.\images\工作队列.png)

![](.\images\工作队列02.png)

![](.\images\工作队列04.png)

1. `work_struct`：工作队列调度的最小单位，`work`；
2. `workqueue_struct`：工作队列，`work`都挂入到工作队列中；
3. `worker`：`work`的处理者，每个`worker`对应一个内核线程；
4. `worker_pool`：`worker`池（内核线程池），是一个共享资源池，提供不同的`worker`来对`work`进行处理；
5. `pool_workqueue`：充当桥梁纽带的作用，用于连接`workqueue`和`worker_pool`，建立链接关系；



**workqueue_struct**

- 内核中工作队列分为两种：
  1. bound：绑定处理器的工作队列，每个`worker`创建的内核线程绑定到特定的CPU上运行；
  2. unbound：不绑定处理器的工作队列，创建的时候需要指定`WQ_UNBOUND`标志，内核线程可以在处理器间迁移；
- 内核默认创建了一些工作队列（用户也可以创建）：
  1. `system_mq`：如果`work`执行时间较短，使用本队列，调用`schedule[_delayed]_work[_on]()`接口就是添加到本队列中；
  2. `system_highpri_mq`：高优先级工作队列，以nice值-20来运行；
  3. `system_long_wq`：如果`work`执行时间较长，使用本队列；
  4. `system_unbound_wq`：该工作队列的内核线程不绑定到特定的处理器上；
  5. `system_freezable_wq`：该工作队列用于在Suspend时可冻结的`work`；
  6. `system_power_efficient_wq`：该工作队列用于节能目的而选择牺牲性能的`work`；
  7. `system_freezable_power_efficient_wq`：该工作队列用于节能或Suspend时可冻结目的的`work`；

```c
// kernel/workqueue.c

/*
 * The externally visible workqueue.  It relays the issued work items to
 * the appropriate worker_pool through its pool_workqueues.
 */
struct workqueue_struct {
	struct list_head	pwqs;		/* WR: all pwqs of this wq */ // 所有的pool_workqueue
	struct list_head	list;		/* PR: list of all workqueues */ // workqueue_struct队列头

	struct mutex		mutex;		/* protects this wq */
	int			work_color;	/* WQ: current work color */
	int			flush_color;	/* WQ: current flush color */
	atomic_t		nr_pwqs_to_flush; /* flush in progress */
	struct wq_flusher	*first_flusher;	/* WQ: first flusher */
	struct list_head	flusher_queue;	/* WQ: flush waiters */
	struct list_head	flusher_overflow; /* WQ: flush overflow list */

	struct list_head	maydays;	/* MD: pwqs requesting rescue */ // rescue状态下的pool_workqueue添加到本链表中
	struct worker		*rescuer;	/* MD: rescue worker */ // rescuer内核线程，用于处理内存紧张时创建工作线程失败的情况

	int			nr_drainers;	/* WQ: drain in progress */
	int			saved_max_active; /* WQ: saved pwq max_active */

	struct workqueue_attrs	*unbound_attrs;	/* PW: only for unbound wqs */ //unbound属性
	struct pool_workqueue	*dfl_pwq;	/* PW: only for unbound wqs */ //unbound的pool_workqueue

#ifdef CONFIG_SYSFS
	struct wq_device	*wq_dev;	/* I: for sysfs interface */
#endif
#ifdef CONFIG_LOCKDEP
	char			*lock_name;
	struct lock_class_key	key;
	struct lockdep_map	lockdep_map;
#endif
	char			name[WQ_NAME_LEN]; /* I: workqueue name */

	/*
	 * Destruction of workqueue_struct is RCU protected to allow walking
	 * the workqueues list without grabbing wq_pool_mutex.
	 * This is used to dump all workqueues from sysrq.
	 */
	struct rcu_head		rcu;

	/* hot fields used during command issue, aligned to cacheline */
	unsigned int		flags ____cacheline_aligned; /* WQ: WQ_* flags */
	struct pool_workqueue __percpu *cpu_pwqs; /* I: per-cpu pwqs */ //每个CPU的pool_workqueue
	struct pool_workqueue __rcu *numa_pwq_tbl[]; /* PWR: unbound pwqs indexed by node */
};


struct workqueue_struct *system_wq __read_mostly;
EXPORT_SYMBOL(system_wq);
struct workqueue_struct *system_highpri_wq __read_mostly;
EXPORT_SYMBOL_GPL(system_highpri_wq);
struct workqueue_struct *system_long_wq __read_mostly;
EXPORT_SYMBOL_GPL(system_long_wq);
struct workqueue_struct *system_unbound_wq __read_mostly;
EXPORT_SYMBOL_GPL(system_unbound_wq);
struct workqueue_struct *system_freezable_wq __read_mostly;
EXPORT_SYMBOL_GPL(system_freezable_wq);
struct workqueue_struct *system_power_efficient_wq __read_mostly;
EXPORT_SYMBOL_GPL(system_power_efficient_wq);
struct workqueue_struct *system_freezable_power_efficient_wq __read_mostly;
EXPORT_SYMBOL_GPL(system_freezable_power_efficient_wq);

```



**pool_workqueue**

```c
// kernel/workqueue.c

/*
 * The per-pool workqueue.  While queued, the lower WORK_STRUCT_FLAG_BITS
 * of work_struct->data are used for flags and the remaining high bits
 * point to the pwq; thus, pwqs need to be aligned at two's power of the
 * number of flag bits.
 */
struct pool_workqueue {
        struct worker_pool     *pool;        /* I: the associated pool */  //指向worker_pool
        struct workqueue_struct *wq;          /* I: the owning workqueue */ //所属的workqueue_struct
        int                  work_color;    /* L: current color */
        int                  flush_color;   /* L: flushing color */
        int                  refcnt;               /* L: reference count */
        int                  nr_in_flight[WORK_NR_COLORS];
                                           /* L: nr of in_flight works */
        int                  nr_active;     /* L: nr of active works */ //活跃的work数量
        int                  max_active;    /* L: max active works */ //最大活跃work数量
        struct list_head       delayed_works; /* L: delayed works */ //延迟执行的work队列
        struct list_head       pwqs_node;     /* WR: node on wq->pwqs */ // 用于添加到wq的pwqs中
        struct list_head       mayday_node;   /* MD: node on wq->maydays */ //用于添加到wq的maydays中

        /*
         * Release of unbound pwq is punted to system_wq.  See put_pwq()
         * and pwq_unbound_release_workfn() for details.  pool_workqueue
         * itself is also RCU protected so that the first pwq can be
         * determined without grabbing wq->mutex.
         */
        struct work_struct     unbound_release_work;
        struct rcu_head               rcu;
} __aligned(1 << WORK_STRUCT_FLAG_BITS);
```

**worker_pool**

- `worker_pool`是一个资源池，管理多个`worker`，也就是管理多个内核线程；
- 针对绑定类型的工作队列，`worker_pool`是Per-CPU创建，每个CPU都有两个`worker_pool`，对应不同的优先级，nice值分别为0和-20；
- 针对非绑定类型的工作队列，`worker_pool`创建后会添加到`unbound_pool_hash`哈希表中；
- `worker_pool`管理一个空闲链表和一个忙碌列表，其中忙碌列表由哈希管理；

```c
// kernel/workqueue.c

struct worker_pool {
        spinlock_t            lock;         /* the pool lock */ //用于保护worker_pool的自旋锁
        int                  cpu;          /* I: the associated cpu */ //对于unbound类型是-1，对于bound类型是CPU ID
        int                  node;         /* I: the associated node ID */ // 非绑定类型的workqueue，代表内存Node ID
        int                  id;           /* I: pool ID */ //woker_pool ID
        unsigned int          flags;        /* X: flags */

        unsigned long         watchdog_ts;   /* L: watchdog timestamp */

        struct list_head       worklist;      /* L: list of pending works */ //挂入pending状态的work_struct

        int                  nr_workers;    /* L: total number of workers */
        int                  nr_idle;       /* L: currently idle workers */

        struct list_head       idle_list;     /* X: list of idle workers */ //空闲的worker列表
        struct timer_list      idle_timer;    /* L: worker idle timeout */
        struct timer_list      mayday_timer;  /* L: SOS timer for workers */

        /* a workers is either on busy_hash or idle_list, or the manager */
        DECLARE_HASHTABLE(busy_hash, BUSY_WORKER_HASH_ORDER);  // 工作状态的worker添加到本哈希表中
                                           /* L: hash of busy workers */

        struct worker         *manager;      /* L: purely informational */
        struct list_head       workers;       /* A: attached workers */ // 当前worker_pool管理的worker
        struct completion      *detach_completion; /* all workers detached */

        struct ida            worker_ida;    /* worker IDs for task name */

        struct workqueue_attrs *attrs;               /* I: worker attributes */
        struct hlist_node      hash_node;     /* PL: unbound_pool_hash node */  // 用于添加到unbound_pool_hash中
        int                  refcnt;               /* PL: refcnt for unbound pools */

        /*
         * The current concurrency level.  As it's likely to be accessed
         * from other CPUs during try_to_wake_up(), put it in a separate
         * cacheline.
         */
        atomic_t              nr_running ____cacheline_aligned_in_smp; //当前正在运行的线程数量

        /*
         * Destruction of pool is RCU protected to allow dereferences
         * from get_work_pool().
         */
        struct rcu_head               rcu;
} ____cacheline_aligned_in_smp;
```

**worker**

- 每个`worker`对应一个内核线程，用于对`work`的处理；
- `worker`根据工作状态，可以添加到`worker_pool`的空闲链表或忙碌列表中；
- `worker`处于空闲状态时并接收到工作处理请求，将唤醒内核线程来处理；
- 内核线程是在每个`worker_pool`中由一个初始的空闲工作线程创建的，并根据需要动态创建和销毁；

```c
//kernel/workqueue_internal.h


/*
 * The poor guys doing the actual heavy lifting.  All on-duty workers are
 * either serving the manager role, on idle list or on busy hash.  For
 * details on the locking annotation (L, I, X...), refer to workqueue.c.
 *
 * Only to be used in workqueue and async.
 */
struct worker {
	/* on idle list while idle, on busy hash table while busy */
	union {
		struct list_head	entry;	/* L: while idle */ //用于添加到worker_pool的空闲链表中
		struct hlist_node	hentry;	/* L: while busy */ //用于添加到worker_pool的忙碌列表中
	};

	struct work_struct	*current_work;	/* L: work being processed */ //当前正在执行的work
	work_func_t		current_func;	/* L: current_work's fn */ //当前正在执行的work的函数
	struct pool_workqueue	*current_pwq; /* L: current_work's pwq */ //当前work所属的pool_workqueue
	struct list_head	scheduled;	/* L: scheduled works */ //所有被调度并正准备执行的work_struct都挂入该链表中

	/* 64 bytes boundary on 64bit, 32 on 32bit */

	struct task_struct	*task;		/* I: worker task */ //当前worker的task_struct
	struct worker_pool	*pool;		/* A: the associated pool */ //该工作线程所属的worker_pool
						/* L: for rescuers */
	struct list_head	node;		/* A: anchored at pool->workers */ //添加到worker_pool->workers链表中
						/* A: runs through worker->node */

	unsigned long		last_active;	/* L: last active timestamp */
	unsigned int		flags;		/* X: flags */
	int			id;		/* I: worker id */
	int			sleeping;	/* None */

	/*
	 * Opaque string set with work_set_desc().  Printed out with task
	 * dump for debugging - WARN, BUG, panic or sysrq.
	 */
	char			desc[WORKER_DESC_LEN];

	/* used only by rescuers to point to the target workqueue */
	struct workqueue_struct	*rescue_wq;	/* I: the workqueue to rescue */

	/* used by the scheduler to determine a worker's last known identity */
	work_func_t		last_func;
};


```



**work_struct**

![](.\images\工作队列03.png)

```c
// include/linux/workqueue.h


// work
struct work_struct {
    atomic_long_t data; //低比特存放状态位，高比特存放worker_pool的ID或者pool_workqueue的指针
    struct list_head entry; //当前work所属的work队列
    work_func_t func; //当前work要执行的函数
#ifdef CONFIG_LOCKDEP
    struct lockdep_map lockdep_map;
#endif
};

struct delayed_work {
	struct work_struct work;
	struct timer_list timer;

	/* target workqueue and CPU ->timer uses to queue ->work */
	struct workqueue_struct *wq;
	int cpu;
};

struct rcu_work {
	struct work_struct work;
	struct rcu_head rcu;

	/* target workqueue ->rcu uses to queue ->work */
	struct workqueue_struct *wq;
};


//work-data的位图 
/*
 * The first word is the work queue pointer and the flags rolled into
 * one
 */
#define work_data_bits(work) ((unsigned long *)(&(work)->data))

enum {
	WORK_STRUCT_PENDING_BIT	= 0,	/* work item is pending execution */
	WORK_STRUCT_DELAYED_BIT	= 1,	/* work item is delayed */
	WORK_STRUCT_PWQ_BIT	= 2,	/* data points to pwq */
	WORK_STRUCT_LINKED_BIT	= 3,	/* next work is linked to this one */
#ifdef CONFIG_DEBUG_OBJECTS_WORK
	WORK_STRUCT_STATIC_BIT	= 4,	/* static initializer (debugobjects) */
	WORK_STRUCT_COLOR_SHIFT	= 5,	/* color for workqueue flushing */
#else
	WORK_STRUCT_COLOR_SHIFT	= 4,	/* color for workqueue flushing */
#endif

	WORK_STRUCT_COLOR_BITS	= 4,

	WORK_STRUCT_PENDING	= 1 << WORK_STRUCT_PENDING_BIT,
	WORK_STRUCT_DELAYED	= 1 << WORK_STRUCT_DELAYED_BIT,
	WORK_STRUCT_PWQ		= 1 << WORK_STRUCT_PWQ_BIT,
	WORK_STRUCT_LINKED	= 1 << WORK_STRUCT_LINKED_BIT,
#ifdef CONFIG_DEBUG_OBJECTS_WORK
	WORK_STRUCT_STATIC	= 1 << WORK_STRUCT_STATIC_BIT,
#else
	WORK_STRUCT_STATIC	= 0,
#endif

	/*
	 * The last color is no color used for works which don't
	 * participate in workqueue flushing.
	 */
	WORK_NR_COLORS		= (1 << WORK_STRUCT_COLOR_BITS) - 1,
	WORK_NO_COLOR		= WORK_NR_COLORS,

	/* not bound to any CPU, prefer the local CPU */
	WORK_CPU_UNBOUND	= NR_CPUS,

	/*
	 * Reserve 7 bits off of pwq pointer w/ debugobjects turned off.
	 * This makes pwqs aligned to 256 bytes and allows 15 workqueue
	 * flush colors.
	 */
	WORK_STRUCT_FLAG_BITS	= WORK_STRUCT_COLOR_SHIFT +
				  WORK_STRUCT_COLOR_BITS,

	/* data contains off-queue information when !WORK_STRUCT_PWQ */
	WORK_OFFQ_FLAG_BASE	= WORK_STRUCT_COLOR_SHIFT,

	__WORK_OFFQ_CANCELING	= WORK_OFFQ_FLAG_BASE,
	WORK_OFFQ_CANCELING	= (1 << __WORK_OFFQ_CANCELING),

	/*
	 * When a work item is off queue, its high bits point to the last
	 * pool it was on.  Cap at 31 bits and use the highest number to
	 * indicate that no pool is associated.
	 */
	WORK_OFFQ_FLAG_BITS	= 1,
	WORK_OFFQ_POOL_SHIFT	= WORK_OFFQ_FLAG_BASE + WORK_OFFQ_FLAG_BITS,
	WORK_OFFQ_LEFT		= BITS_PER_LONG - WORK_OFFQ_POOL_SHIFT,
	WORK_OFFQ_POOL_BITS	= WORK_OFFQ_LEFT <= 31 ? WORK_OFFQ_LEFT : 31,
	WORK_OFFQ_POOL_NONE	= (1LU << WORK_OFFQ_POOL_BITS) - 1,

	/* convenience constants */
	WORK_STRUCT_FLAG_MASK	= (1UL << WORK_STRUCT_FLAG_BITS) - 1,
	WORK_STRUCT_WQ_DATA_MASK = ~WORK_STRUCT_FLAG_MASK,
	WORK_STRUCT_NO_POOL	= (unsigned long)WORK_OFFQ_POOL_NONE << WORK_OFFQ_POOL_SHIFT,

	/* bit mask for work_busy() return values */
	WORK_BUSY_PENDING	= 1 << 0,
	WORK_BUSY_RUNNING	= 1 << 1,

	/* maximum string length for set_worker_desc() */
	WORKER_DESC_LEN		= 24,
};
```



**初始化**

- `workqueue`子系统的初始化分成两步来完成的：`workqueue_init_early`和`workqueue_init`。

**workqueue_init_early**

![](.\images\工作队列05.png)

```c
// kernel/workqueue.c


//bound类型 每个CPU都有两个worker_pool
/* the per-cpu worker pools */
static DEFINE_PER_CPU_SHARED_ALIGNED(struct worker_pool [NR_STD_WORKER_POOLS], cpu_worker_pools);

//所有worker_pool的id分配器
static DEFINE_IDR(worker_pool_idr);	/* PR: idr of all pools */

//unbound类型 worker_pool的hash表
/* PL: hash of all unbound pools keyed by pool->attrs */
static DEFINE_HASHTABLE(unbound_pool_hash, UNBOUND_POOL_HASH_ORDER);

//unbound类型workqueue的属性
/* I: attributes used when instantiating standard unbound pools on demand */
static struct workqueue_attrs *unbound_std_wq_attrs[NR_STD_WORKER_POOLS];
//ordered类型workqueue的属性
/* I: attributes used when instantiating ordered pools on demand */
static struct workqueue_attrs *ordered_wq_attrs[NR_STD_WORKER_POOLS];

//workqueue_attrs属性
struct workqueue_attrs {
	/**
	 * 优先级
	 * @nice: nice level
	 */
	int nice;

	/**
	 * 允许执行的cpu
	 * @cpumask: allowed CPUs
	 */
	cpumask_var_t cpumask;

	/**
	 * @no_numa: disable NUMA affinity
	 *
	 * Unlike other fields, ``no_numa`` isn't a property of a worker_pool. It
	 * only modifies how :c:func:`apply_workqueue_attrs` select pools and thus
	 * doesn't participate in pool hash calculations or equality comparisons.
	 */
	bool no_numa;
};


/**
 * workqueue_init_early - early init for workqueue subsystem
 *
 * This is the first half of two-staged workqueue subsystem initialization
 * and invoked as soon as the bare basics - memory allocation, cpumasks and
 * idr are up.  It sets up all the data structures and system workqueues
 * and allows early boot code to create workqueues and queue/cancel work
 * items.  Actual work item execution starts only after kthreads can be
 * created and scheduled right before early initcalls.
 */
int __init workqueue_init_early(void)
{
    	//设置per-cpu类型worker-pool的优先级 为0和-20
        int std_nice[NR_STD_WORKER_POOLS] = { 0, HIGHPRI_NICE_LEVEL };
        int hk_flags = HK_FLAG_DOMAIN | HK_FLAG_WQ;
        int i, cpu;

        WARN_ON(__alignof__(struct pool_workqueue) < __alignof__(long long));

        BUG_ON(!alloc_cpumask_var(&wq_unbound_cpumask, GFP_KERNEL));
        cpumask_copy(wq_unbound_cpumask, housekeeping_cpumask(hk_flags));

        pwq_cache = KMEM_CACHE(pool_workqueue, SLAB_PANIC);

    	//遍历每一个CPU
        /* initialize CPU pools */
        for_each_possible_cpu(cpu) {
               struct worker_pool *pool;

               //每个CPU分配两个worker_pool 一个低优先级 一个高优先级 
               //分配到cpu_worker_pools[0]和cpu_worker_pools[1]
               i = 0;
               for_each_cpu_worker_pool(pool, cpu) {
                   	  //初始化worker_pool
                      BUG_ON(init_worker_pool(pool));
                      //绑定cpu
                      pool->cpu = cpu;
                      cpumask_copy(pool->attrs->cpumask, cpumask_of(cpu));
                      pool->attrs->nice = std_nice[i++];
                      pool->node = cpu_to_node(cpu);

                     //分配pool ID
                      /* alloc pool ID */
                      mutex_lock(&wq_pool_mutex);
                      BUG_ON(worker_pool_assign_id(pool));
                      mutex_unlock(&wq_pool_mutex);
               }
        }

        // 创建unbound类型workqueue 的属性workqueue_attrs
        // 
        /* create default unbound and ordered wq attrs */
        for (i = 0; i < NR_STD_WORKER_POOLS; i++) {
               struct workqueue_attrs *attrs;

               //创建attrs设置到unbound_std_wq_attrs[0]和unbound_std_wq_attrs[1]上
               BUG_ON(!(attrs = alloc_workqueue_attrs()));
               attrs->nice = std_nice[i];
               unbound_std_wq_attrs[i] = attrs;

               /*
                * An ordered wq should have only one pwq as ordering is
                * guaranteed by max_active which is enforced by pwqs.
                * Turn off NUMA so that dfl_pwq is used for all nodes.
                */
               BUG_ON(!(attrs = alloc_workqueue_attrs()));
               attrs->nice = std_nice[i];
               attrs->no_numa = true;
            //创建attrs设置到ordered_wq_attrs[0]和ordered_wq_attrs[1]上
               ordered_wq_attrs[i] = attrs;
        }

    	//创建各种类型的全局workqueue

        //如果`work`执行时间较短，使用本队列，调用`schedule[_delayed]_work[_on]()`接口就是添加到本队列中
        system_wq = alloc_workqueue("events", 0, 0);
    	//高优先级工作队列，以nice值-20来运行；
        system_highpri_wq = alloc_workqueue("events_highpri", WQ_HIGHPRI, 0);
        //如果`work`执行时间较长，使用本队列；
        system_long_wq = alloc_workqueue("events_long", 0, 0);
        //该工作队列的内核线程不绑定到特定的处理器上；
        system_unbound_wq = alloc_workqueue("events_unbound", WQ_UNBOUND,
                                        WQ_UNBOUND_MAX_ACTIVE);
        //该工作队列用于在Suspend时可冻结的`work`；
        system_freezable_wq = alloc_workqueue("events_freezable",
                                          WQ_FREEZABLE, 0);
        //该工作队列用于节能目的而选择牺牲性能的`work`；
        system_power_efficient_wq = alloc_workqueue("events_power_efficient",
                                          WQ_POWER_EFFICIENT, 0);
        //该工作队列用于节能或Suspend时可冻结目的的`work`；
        system_freezable_power_efficient_wq = alloc_workqueue("events_freezable_power_efficient",
                                          WQ_FREEZABLE | WQ_POWER_EFFICIENT,
                                          0);
        BUG_ON(!system_wq || !system_highpri_wq || !system_long_wq ||
               !system_unbound_wq || !system_freezable_wq ||
               !system_power_efficient_wq ||
               !system_freezable_power_efficient_wq);

        return 0;
}

//初始化worker_pool
static int init_worker_pool(struct worker_pool *pool)
{
	spin_lock_init(&pool->lock);
	pool->id = -1;
	pool->cpu = -1;
	pool->node = NUMA_NO_NODE;
	pool->flags |= POOL_DISASSOCIATED;
	pool->watchdog_ts = jiffies;
	INIT_LIST_HEAD(&pool->worklist);
	INIT_LIST_HEAD(&pool->idle_list);
	hash_init(pool->busy_hash);

    //设置定时清除空闲线程
	timer_setup(&pool->idle_timer, idle_worker_timeout, TIMER_DEFERRABLE);

	timer_setup(&pool->mayday_timer, pool_mayday_timeout, 0);

	INIT_LIST_HEAD(&pool->workers);

	ida_init(&pool->worker_ida);
	INIT_HLIST_NODE(&pool->hash_node);
	pool->refcnt = 1;

	/* shouldn't fail above this point */
	pool->attrs = alloc_workqueue_attrs();
	if (!pool->attrs)
		return -ENOMEM;
	return 0;
}



```

**alloc_workqueue**

![](.\images\工作队列06.png)



```c
// kernel/workqueue.c


//创建workqueue
struct workqueue_struct *alloc_workqueue(const char *fmt,
                                     unsigned int flags,
                                     int max_active, ...)
{
        size_t tbl_size = 0;
        va_list args;
        struct workqueue_struct *wq;
        struct pool_workqueue *pwq;

        /*
         * Unbound && max_active == 1 used to imply ordered, which is no
         * longer the case on NUMA machines due to per-node pools.  While
         * alloc_ordered_workqueue() is the right way to create an ordered
         * workqueue, keep the previous behavior to avoid subtle breakages
         * on NUMA.
         */
        if ((flags & WQ_UNBOUND) && max_active == 1)
               flags |= __WQ_ORDERED;

        /* see the comment above the definition of WQ_POWER_EFFICIENT */
        if ((flags & WQ_POWER_EFFICIENT) && wq_power_efficient)
               flags |= WQ_UNBOUND;

        /* allocate wq and format name */
        if (flags & WQ_UNBOUND)
               tbl_size = nr_node_ids * sizeof(wq->numa_pwq_tbl[0]);
		
        //分配workqueue的内存
        wq = kzalloc(sizeof(*wq) + tbl_size, GFP_KERNEL);
        if (!wq)
               return NULL;

        if (flags & WQ_UNBOUND) {
               wq->unbound_attrs = alloc_workqueue_attrs();
               if (!wq->unbound_attrs)
                      goto err_free_wq;
        }

        va_start(args, max_active);
        vsnprintf(wq->name, sizeof(wq->name), fmt, args);
        va_end(args);

        max_active = max_active ?: WQ_DFL_ACTIVE;
        max_active = wq_clamp_max_active(max_active, flags, wq->name);

        //初始化workqueue的属性
        /* init wq */
        wq->flags = flags;
        wq->saved_max_active = max_active;
        mutex_init(&wq->mutex);
        atomic_set(&wq->nr_pwqs_to_flush, 0);
        INIT_LIST_HEAD(&wq->pwqs);
        INIT_LIST_HEAD(&wq->flusher_queue);
        INIT_LIST_HEAD(&wq->flusher_overflow);
        INIT_LIST_HEAD(&wq->maydays);

        wq_init_lockdep(wq);
        INIT_LIST_HEAD(&wq->list);

        //创建并且链接pool_workqueue
        if (alloc_and_link_pwqs(wq) < 0)
               goto err_unreg_lockdep;

        if (wq_online && init_rescuer(wq) < 0)
               goto err_destroy;

        if ((wq->flags & WQ_SYSFS) && workqueue_sysfs_register(wq))
               goto err_destroy;

        /*
         * wq_pool_mutex protects global freeze state and workqueues list.
         * Grab it, adjust max_active and add the new @wq to workqueues
         * list.
         */
        mutex_lock(&wq_pool_mutex);

        mutex_lock(&wq->mutex);
        for_each_pwq(pwq, wq)
               pwq_adjust_max_active(pwq);
        mutex_unlock(&wq->mutex);

        list_add_tail_rcu(&wq->list, &workqueues);

        mutex_unlock(&wq_pool_mutex);

        return wq;

err_unreg_lockdep:
        wq_unregister_lockdep(wq);
        wq_free_lockdep(wq);
err_free_wq:
        free_workqueue_attrs(wq->unbound_attrs);
        kfree(wq);
        return NULL;
err_destroy:
        destroy_workqueue(wq);
        return NULL;
}
EXPORT_SYMBOL_GPL(alloc_workqueue);



//创建并且链接pool_workqueue
static int alloc_and_link_pwqs(struct workqueue_struct *wq)
{
	bool highpri = wq->flags & WQ_HIGHPRI;
	int cpu, ret;

    //如果workqueue是bound类型的
	if (!(wq->flags & WQ_UNBOUND)) {
         //给每个cpu都分配一个pool_workqueue
         //这里是所有cpu的pool_workqueue的队列头引用
        //将pool_workqueue队列头设置到workqueue的cpu_pwqs中
		wq->cpu_pwqs = alloc_percpu(struct pool_workqueue);
		if (!wq->cpu_pwqs)
			return -ENOMEM;
         //遍历每一个cpu
		for_each_possible_cpu(cpu) {
			//取当前cpu的pool_workqueue
             struct pool_workqueue *pwq =
				per_cpu_ptr(wq->cpu_pwqs, cpu);
            //取当前cpu的worker_pool
			struct worker_pool *cpu_pools =
				per_cpu(cpu_worker_pools, cpu);
			//将worker_pool设置到pool_workqueue中 
			init_pwq(pwq, wq, &cpu_pools[highpri]);

			mutex_lock(&wq->mutex);
             //将pool_workqueue和workqueue链接起来
			link_pwq(pwq);
			mutex_unlock(&wq->mutex);
		}
		return 0;
	}

	get_online_cpus();
	if (wq->flags & __WQ_ORDERED) {
		ret = apply_workqueue_attrs(wq, ordered_wq_attrs[highpri]);
		/* there should only be single pwq for ordering guarantee */
		WARN(!ret && (wq->pwqs.next != &wq->dfl_pwq->pwqs_node ||
			      wq->pwqs.prev != &wq->dfl_pwq->pwqs_node),
		     "ordering guarantee broken for workqueue %s\n", wq->name);
	} else {
        //如果workqueue是unbound类型的 创建unbound类型的pool_queue和workqueue绑定
		ret = apply_workqueue_attrs(wq, unbound_std_wq_attrs[highpri]);
	}
	put_online_cpus();

	return ret;
}

//创建unbound类型的pool_queue和workqueue绑定
int apply_workqueue_attrs(struct workqueue_struct *wq,
			  const struct workqueue_attrs *attrs)
{
	int ret;

	lockdep_assert_cpus_held();

	mutex_lock(&wq_pool_mutex);
    //创建unbound类型的pool_queue和workqueue绑定
	ret = apply_workqueue_attrs_locked(wq, attrs);
	mutex_unlock(&wq_pool_mutex);

	return ret;
}


static int apply_workqueue_attrs_locked(struct workqueue_struct *wq,
					const struct workqueue_attrs *attrs)
{
	struct apply_wqattrs_ctx *ctx;

	/* only unbound workqueues can change attributes */
	if (WARN_ON(!(wq->flags & WQ_UNBOUND)))
		return -EINVAL;

	/* creating multiple pwqs breaks ordering guarantee */
	if (!list_empty(&wq->pwqs)) {
		if (WARN_ON(wq->flags & __WQ_ORDERED_EXPLICIT))
			return -EINVAL;

		wq->flags &= ~__WQ_ORDERED;
	}

    //创建unbound类型的pwq 
	ctx = apply_wqattrs_prepare(wq, attrs);
	if (!ctx)
		return -ENOMEM;

	/* the ctx has been prepared successfully, let's commit it */
    //设置wq属性 将wq和pwq关联
	apply_wqattrs_commit(ctx);
	apply_wqattrs_cleanup(ctx);

	return 0;
}

//直接看apply_wqattrs_prepare()->alloc_unbound_pwq()方法 
static struct pool_workqueue *alloc_unbound_pwq(struct workqueue_struct *wq,
					const struct workqueue_attrs *attrs)
{
	struct worker_pool *pool;
	struct pool_workqueue *pwq;

	lockdep_assert_held(&wq_pool_mutex);

    //根据unbound的属性创建worker_pool
	pool = get_unbound_pool(attrs);
	if (!pool)
		return NULL;
    //分配内存创建pwq
	pwq = kmem_cache_alloc_node(pwq_cache, GFP_KERNEL, pool->node);
	if (!pwq) {
		put_unbound_pool(pool);
		return NULL;
	}
    //初始化pwd的值 绑定worker_pool pwq wq
	init_pwq(pwq, wq, pool);
	return pwq;
}

//创建一个unbound类型的worker_pool
static struct worker_pool *get_unbound_pool(const struct workqueue_attrs *attrs)
{
    //获取hash值
	u32 hash = wqattrs_hash(attrs);
	struct worker_pool *pool;
	int node;
	int target_node = NUMA_NO_NODE;

	lockdep_assert_held(&wq_pool_mutex);
    
    //通过hash查找unbound_pool_hash 寻找对应的worker_pool
	/* do we already have a matching pool? */
	hash_for_each_possible(unbound_pool_hash, pool, hash_node, hash) {
		if (wqattrs_equal(pool->attrs, attrs)) {
			pool->refcnt++;
			return pool;
		}
	}

	/* if cpumask is contained inside a NUMA node, we belong to that node */
	if (wq_numa_enabled) {
		for_each_node(node) {
			if (cpumask_subset(attrs->cpumask,
					   wq_numa_possible_cpumask[node])) {
				target_node = node;
				break;
			}
		}
	}

    //分配内存创建worker_pool
	/* nope, create a new one */
	pool = kzalloc_node(sizeof(*pool), GFP_KERNEL, target_node);
	if (!pool || init_worker_pool(pool) < 0)
		goto fail;

	lockdep_set_subclass(&pool->lock, 1);	/* see put_pwq() */
    //初始化属性
	copy_workqueue_attrs(pool->attrs, attrs);
	pool->node = target_node;

	/*
	 * no_numa isn't a worker_pool attribute, always clear it.  See
	 * 'struct workqueue_attrs' comments for detail.
	 */
	pool->attrs->no_numa = false;
    //分配pool id
	if (worker_pool_assign_id(pool) < 0)
		goto fail;

    //创建worker
	/* create and start the initial worker */
	if (wq_online && !create_worker(pool))
		goto fail;
    //将创建好的worker_pool加入到unbound_pool_hash
	/* install */
	hash_add(unbound_pool_hash, &pool->hash_node, hash);

	return pool;
fail:
	if (pool)
		put_unbound_pool(pool);
	return NULL;
}





/*
 * 在内存回收期间 可能需要rescuer来处理工作 创建一个rescuer线程
 * Workqueues which may be used during memory reclaim should have a rescuer
 * to guarantee forward progress.
 */
static int init_rescuer(struct workqueue_struct *wq)
{
	struct worker *rescuer;
	int ret;

	if (!(wq->flags & WQ_MEM_RECLAIM))
		return 0;
    //分配一个rescuer worker 
	rescuer = alloc_worker(NUMA_NO_NODE);
	if (!rescuer)
		return -ENOMEM;

	rescuer->rescue_wq = wq;
	rescuer->task = kthread_create(rescuer_thread, rescuer, "%s", wq->name);
	ret = PTR_ERR_OR_ZERO(rescuer->task);
	if (ret) {
		kfree(rescuer);
		return ret;
	}

	wq->rescuer = rescuer;
	kthread_bind_mask(rescuer->task, cpu_possible_mask);
	wake_up_process(rescuer->task);

	return 0;
}

```

**workqueue_init**

![](.\images\工作队列07.png)

- 主要完成的工作是给之前创建好的`worker_pool`，添加一个初始的`worker`；
- `create_worker`函数中，创建的内核线程名字为`kworker/XX:YY`或者`kworker/uXX:YY`，其中`XX`表示`worker_pool`的编号，`YY`表示`worker`的编号，`u`表示`unbound`；

```c
// kernel/workqueue.c

int __init workqueue_init(void)
{
        struct workqueue_struct *wq;
        struct worker_pool *pool;
        int cpu, bkt;

        /*
         * It'd be simpler to initialize NUMA in workqueue_init_early() but
         * CPU to node mapping may not be available that early on some
         * archs such as power and arm64.  As per-cpu pools created
         * previously could be missing node hint and unbound pools NUMA
         * affinity, fix them up.
         *
         * Also, while iterating workqueues, create rescuers if requested.
         */
        wq_numa_init();

        mutex_lock(&wq_pool_mutex);

        for_each_possible_cpu(cpu) {
               for_each_cpu_worker_pool(pool, cpu) {
                      pool->node = cpu_to_node(cpu);
               }
        }

        list_for_each_entry(wq, &workqueues, list) {
               wq_update_unbound_numa(wq, smp_processor_id(), true);
               WARN(init_rescuer(wq),
                    "workqueue: failed to create early rescuer for %s",
                    wq->name);
        }

        mutex_unlock(&wq_pool_mutex);

        //给每一个cpu的每一个worker_pool创建worker
        /* create the initial workers */
        for_each_online_cpu(cpu) {
               for_each_cpu_worker_pool(pool, cpu) {
                      pool->flags &= ~POOL_DISASSOCIATED;
                      BUG_ON(!create_worker(pool));
               }
        }

        hash_for_each(unbound_pool_hash, bkt, pool, hash_node)
               BUG_ON(!create_worker(pool));
        //标记wq启用
        wq_online = true;
        //初始化wq的看门狗
        wq_watchdog_init();

        return 0;
}

// ps aux | grep kworker #展示所有kworker线程

//创建worker线程
static struct worker *create_worker(struct worker_pool *pool)
{
	struct worker *worker = NULL;
	int id = -1;
	char id_buf[16];

    //从worker_pool中生成一个worker id
	/* ID is needed to determine kthread name */
	id = ida_simple_get(&pool->worker_ida, 0, 0, GFP_KERNEL);
	if (id < 0)
		goto fail;

    //分配worker的地址空间
	worker = alloc_worker(pool->node);
	if (!worker)
		goto fail;

    //设置worker id
	worker->id = id;

    //设置线程名称为kworker
	if (pool->cpu >= 0)
		snprintf(id_buf, sizeof(id_buf), "%d:%d%s", pool->cpu, id,
			 pool->attrs->nice < 0  ? "H" : "");
	else
		snprintf(id_buf, sizeof(id_buf), "u%d:%d", pool->id, id);

    //创建worker线程 设置worker_thread为主函数
	worker->task = kthread_create_on_node(worker_thread, worker, pool->node,
					      "kworker/%s", id_buf);
	if (IS_ERR(worker->task))
		goto fail;
    
    // 设置线程优先级
	set_user_nice(worker->task, pool->attrs->nice);
	kthread_bind_mask(worker->task, pool->attrs->cpumask);

    //把worker加入到worker_pool中
	/* successful, attach the worker to the pool */
	worker_attach_to_pool(worker, pool);

    //初始化worker
	/* start the newly created worker */
	spin_lock_irq(&pool->lock);
	worker->pool->nr_workers++;
	worker_enter_idle(worker);
    //唤醒worker线程
	wake_up_process(worker->task);
	spin_unlock_irq(&pool->lock);

	return worker;

fail:
	if (id >= 0)
		ida_simple_remove(&pool->worker_ida, id);
	kfree(worker);
	return NULL;
}



```



**调度执行**

![](.\images\工作队列08.png)

- `schedule_work`默认是将`work`添加到系统的`system_work`工作队列中；
- `queue_work_on`接口中的操作判断要添加`work`的标志位，如果已经置位了`WORK_STRUCT_PENDING_BIT`，表明已经添加到了队列中等待执行了，否则，需要调用`__queue_work`来进行添加。注意了，这个操作是在关中断的情况下进行的，因为工作队列使用`WORK_STRUCT_PENDING_BIT`位来同步`work`的插入和删除操作，设置了这个比特后，然后才能执行`work`，这个过程可能被中断或抢占打断；
- `workqueue`的标志位设置了`__WQ_DRAINING`，表明工作队列正在销毁，所有的`work`都要处理完，此时不允许再将`work`添加到队列中，有一种特殊情况：销毁过程中，执行`work`时又触发了新的`work`，也就是所谓的`chained work`；
- 判断`workqueue`的类型，如果是`bound`类型，根据CPU来获取`pool_workqueue`，如果是`unbound`类型，通过node号来获取`pool_workqueue`；
- `get_work_pool`获取上一次执行`work`的`worker_pool`，如果本次执行的`worker_pool`与上次执行的`worker_pool`不一致，且通过`find_worker_executing_work`判断`work`正在某个`worker_pool`中的`worker`中执行，考虑到缓存热度，放到该`worker`执行是更合理的选择，进而根据该`worker`获取到`pool_workqueue`；
- 判断`pool_workqueue`活跃的`work`数量，少于最大限值则将`work`加入到`pool->worklist`中，否则加入到`pwq->delayed_works`链表中，如果`__need_more_worker`判断没有`worker`在执行，则唤醒`worker`内核线程执行；
- 总结：
  1. `schedule_work`完成的工作是将`work`添加到对应的链表中，而在添加的过程中，首先是需要确定`pool_workqueue`；
  2. `pool_workqueue`对应一个`worker_pool`，因此确定了`pool_workqueue`也就确定了`worker_pool`，进而可以将`work`添加到工作链表中；
  3. `pool_workqueue`的确定分为三种情况：1）`bound`类型的工作队列，直接根据CPU号获取；2）`unbound`类型的工作队列，根据node号获取，针对`unbound`类型工作队列，`pool_workqueue`的释放是异步执行的，需要判断`refcnt`的计数值，因此在获取`pool_workqueue`时可能要多次`retry`；3）根据缓存热度，优先选择正在被执行的`worker_pool`；

```c
// include/linux/workqueue.h

/**
 * schedule_work - put work task in global workqueue
 * @work: job to be done
 *
 * Returns %false if @work was already on the kernel-global workqueue and
 * %true otherwise.
 *
 * This puts a job in the kernel-global workqueue if it was not already
 * queued and leaves it in the same position on the kernel-global
 * workqueue otherwise.
 *
 * Shares the same memory-ordering properties of queue_work(), cf. the
 * DocBook header of queue_work().
 */
//将work加入到workqueue里
static inline bool schedule_work(struct work_struct *work)
{
    //默认加到system_wq中
    return queue_work(system_wq, work);
}



static inline bool queue_work(struct workqueue_struct *wq,
			      struct work_struct *work)
{
    //不绑定到特定的cpu 更倾向于当前cpu
	return queue_work_on(WORK_CPU_UNBOUND, wq, work);
}


bool queue_work_on(int cpu, struct workqueue_struct *wq,
		   struct work_struct *work)
{
	bool ret = false;
	unsigned long flags;

    //中断禁用
	local_irq_save(flags);

    //通过当前work的data第0bit位来判断是否已经加入到队列
	if (!test_and_set_bit(WORK_STRUCT_PENDING_BIT, work_data_bits(work))) {
        //加入队列
		__queue_work(cpu, wq, work);
		ret = true;
	}

    //恢复中断
	local_irq_restore(flags);
	return ret;
}
EXPORT_SYMBOL(queue_work_on);






static void __queue_work(int cpu, struct workqueue_struct *wq,
			 struct work_struct *work)
{
	struct pool_workqueue *pwq;
	struct worker_pool *last_pool;
	struct list_head *worklist;
	unsigned int work_flags;
	unsigned int req_cpu = cpu;

	/*
	 * While a work item is PENDING && off queue, a task trying to
	 * steal the PENDING will busy-loop waiting for it to either get
	 * queued or lose PENDING.  Grabbing PENDING and queueing should
	 * happen with IRQ disabled.
	 */
	lockdep_assert_irqs_disabled();

	debug_work_activate(work);

    //如果当前wq正在销毁 那么不允许加入work 直接返回
	/* if draining, only works from the same workqueue are allowed */
	if (unlikely(wq->flags & __WQ_DRAINING) &&
	    WARN_ON_ONCE(!is_chained_work(wq)))
		return;
	rcu_read_lock();
retry:
    //如果当前wq是unbound类型 
	/* pwq which will be used unless @work is executing elsewhere */
	if (wq->flags & WQ_UNBOUND) {
		if (req_cpu == WORK_CPU_UNBOUND)
			cpu = wq_select_unbound_cpu(raw_smp_processor_id());
        //通过node节点找pwq
		pwq = unbound_pwq_by_node(wq, cpu_to_node(cpu));
	} else {
		if (req_cpu == WORK_CPU_UNBOUND)
			cpu = raw_smp_processor_id();
		//如果是bound类型 获取当前cpu的pwq
        pwq = per_cpu_ptr(wq->cpu_pwqs, cpu);
	}

	/*
	 * If @work was previously on a different pool, it might still be
	 * running there, in which case the work needs to be queued on that
	 * pool to guarantee non-reentrancy.
	 */
    //为了提升缓存命中的性能，如果work重复执行，会查找work的上次执行的worker和pwq，然后交给对应的pwq更好
    //获取这个work上次执行的pool_workqueue 
	last_pool = get_work_pool(work);
	//如果当前worker_pool和last_pool不一致
    if (last_pool && last_pool != pwq->pool) {
		struct worker *worker;

		spin_lock(&last_pool->lock);
	     //去找当前正在执行这个work的woker
		worker = find_worker_executing_work(last_pool, work);

        //找到worker的pwq
		if (worker && worker->current_pwq->wq == wq) {
			pwq = worker->current_pwq;
		} else {
			/* meh... not running there, queue here */
			spin_unlock(&last_pool->lock);
			spin_lock(&pwq->pool->lock);
		}
	} else {
		spin_lock(&pwq->pool->lock);
	}

	/*
	 * pwq is determined and locked.  For unbound pools, we could have
	 * raced with pwq release and it could already be dead.  If its
	 * refcnt is zero, repeat pwq selection.  Note that pwqs never die
	 * without another pwq replacing it in the numa_pwq_tbl or while
	 * work items are executing on it, so the retrying is guaranteed to
	 * make forward-progress.
	 */
	if (unlikely(!pwq->refcnt)) {
		if (wq->flags & WQ_UNBOUND) {
			spin_unlock(&pwq->pool->lock);
			cpu_relax();
			goto retry;
		}
		/* oops */
		WARN_ONCE(true, "workqueue: per-cpu pwq for %s on cpu%d has 0 refcnt",
			  wq->name, cpu);
	}

	/* pwq determined, queue */
	trace_workqueue_queue_work(req_cpu, pwq, work);

	if (WARN_ON(!list_empty(&work->entry)))
		goto out;

	pwq->nr_in_flight[pwq->work_color]++;
	work_flags = work_color_to_flags(pwq->work_color);

    //判断当前活跃work是否到达最大活跃work数量
	if (likely(pwq->nr_active < pwq->max_active)) {
		trace_workqueue_activate_work(work);
		pwq->nr_active++;
        //如果没到 加入到worklist列表
		worklist = &pwq->pool->worklist;
		if (list_empty(worklist))
			pwq->pool->watchdog_ts = jiffies;
	} else {
        //加入到delayed_works
		work_flags |= WORK_STRUCT_DELAYED;
		worklist = &pwq->delayed_works;
	}

	insert_work(pwq, work, worklist, work_flags);

out:
	spin_unlock(&pwq->pool->lock);
	rcu_read_unlock();
}



static void insert_work(struct pool_workqueue *pwq, struct work_struct *work,
			struct list_head *head, unsigned int extra_flags)
{
	struct worker_pool *pool = pwq->pool;

    //将pwd的指针设置到work的data中
	/* we own @work, set data and link */
	set_work_pwq(work, pwq, extra_flags);
    //将work加入到worker_pool中
	list_add_tail(&work->entry, head);
	get_pwq(pwq);

	/*
	 * Ensure either wq_worker_sleeping() sees the above
	 * list_add_tail() or we see zero nr_running to avoid workers lying
	 * around lazily while there are works to be processed.
	 */
	smp_mb();

    //如果需要更多worker 唤醒空闲worker
	if (__need_more_worker(pool))
		wake_up_worker(pool);
}
```

**worker_thread**

![](.\images\工作队列09.png)

- 在创建`worker`时，创建内核线程，执行函数为`worker_thread`；
- `worker_thread`在开始执行时，设置标志位`PF_WQ_WORKER`，调度器在进行调度处理时会对task进行判断，针对`workerqueue worker`有特殊处理；
- `worker`对应的内核线程，在没有处理`work`的时候是睡眠状态，当被唤醒的时候，跳转到`woke_up`开始执行；
- `woke_up`之后，如果此时`worker`是需要销毁的，那就进行清理工作并返回。否则，离开`IDLE`状态，并进入`recheck`模块执行；
- `recheck`部分，首先判断是否需要更多的`worker`来处理，如果没有任务处理，跳转到`sleep`地方进行睡眠。有任务需要处理时，会判断是否有空闲内核线程以及是否需要动态创建，再清除掉`worker`的标志位，然后遍历工作链表，对链表中的每个节点调用`process_one_worker`来处理；
- `sleep`部分比较好理解，没有任务处理时，`worker`进入空闲状态，并将当前的内核线程设置成睡眠状态，让出CPU；
- 总结：
  1. 管理`worker_pool`的内核线程池时，如果有`PENDING`状态的`work`，并且发现没有正在运行的工作线程(`worker_pool->nr_running == 0`)，唤醒空闲状态的内核线程，或者动态创建内核线程；
  2. 如果`work`已经在同一个`worker_pool`的其他`worker`中执行，不再对该`work`进行处理；

![](.\images\工作队列10.png)

```c
// kernel/workqueue.c


//kworker的主函数
static int worker_thread(void *__worker)
{
        struct worker *worker = __worker;
        struct worker_pool *pool = worker->pool;

        /* tell the scheduler that this is a workqueue worker */
        set_pf_worker(true);
woke_up:
        spin_lock_irq(&pool->lock);

    	//判断当前worker是否应该销毁 
        /* am I supposed to die? */
        if (unlikely(worker->flags & WORKER_DIE)) {
               spin_unlock_irq(&pool->lock);
               WARN_ON_ONCE(!list_empty(&worker->entry));
               set_pf_worker(false);

               set_task_comm(worker->task, "kworker/dying");
               ida_simple_remove(&pool->worker_ida, worker->id);
               worker_detach_from_pool(worker);
               kfree(worker);
               return 0;
        }

        //标志worker离开空闲 pool->nr_running+1
        worker_leave_idle(worker);
recheck:
        //判断是否需要更多worker 不需要就睡眠
        /* no more worker necessary? */
        if (!need_more_worker(pool))
               goto sleep;

        //没有空闲线程 大家都在忙 那么判断是否需要创建更多线程
        /* do we need to manage? */
        if (unlikely(!may_start_working(pool)) && manage_workers(worker))
               goto recheck;

        /*
         * ->scheduled list can only be filled while a worker is
         * preparing to process a work or actually processing it.
         * Make sure nobody diddled with it while I was sleeping.
         */
        WARN_ON_ONCE(!list_empty(&worker->scheduled));

        /*
         * Finish PREP stage.  We're guaranteed to have at least one idle
         * worker or that someone else has already assumed the manager
         * role.  This is where @worker starts participating in concurrency
         * management if applicable and concurrency management is restored
         * after being rebound.  See rebind_workers() for details.
         */
        //清空worker准备工作的标识 代表开始工作
        worker_clr_flags(worker, WORKER_PREP | WORKER_REBOUND);

        do {
            //循环处理worker_pool的worklist
               struct work_struct *work =
                      list_first_entry(&pool->worklist,
                                     struct work_struct, entry);

               pool->watchdog_ts = jiffies;
			  
               //处理worker_pool的worklist的work
               if (likely(!(*work_data_bits(work) & WORK_STRUCT_LINKED))) {
                      /* optimization path, not strictly necessary */
                      process_one_work(worker, work);
                      //处理worker->scheduled的work
                      if (unlikely(!list_empty(&worker->scheduled)))
                             process_scheduled_works(worker);
               } else {
                      move_linked_works(work, &worker->scheduled, NULL);
                      process_scheduled_works(worker);
               }
        } while (keep_working(pool));

        worker_set_flags(worker, WORKER_PREP);
sleep:
        /*
         * pool->lock is held and there's no work to process and no need to
         * manage, sleep.  Workers are woken up only while holding
         * pool->lock or from local cpu, so setting the current state
         * before releasing pool->lock is enough to prevent losing any
         * event.
         */
        //worker进入空闲列表
        worker_enter_idle(worker);
        //设置当前状态为空闲
        __set_current_state(TASK_IDLE);
        spin_unlock_irq(&pool->lock);
        //调度
        schedule();
        goto woke_up;
}




static void process_one_work(struct worker *worker, struct work_struct *work)
__releases(&pool->lock)
__acquires(&pool->lock)
{
    //通过work的data获取pwq的指针
	struct pool_workqueue *pwq = get_work_pwq(work);
	struct worker_pool *pool = worker->pool;
	bool cpu_intensive = pwq->wq->flags & WQ_CPU_INTENSIVE;
	int work_color;
	struct worker *collision;
#ifdef CONFIG_LOCKDEP
	/*
	 * It is permissible to free the struct work_struct from
	 * inside the function that is called from it, this we need to
	 * take into account for lockdep too.  To avoid bogus "held
	 * lock freed" warnings as well as problems when looking into
	 * work->lockdep_map, make a copy and use that here.
	 */
	struct lockdep_map lockdep_map;

	lockdep_copy_map(&lockdep_map, &work->lockdep_map);
#endif
	/* ensure we're on the correct CPU */
	WARN_ON_ONCE(!(pool->flags & POOL_DISASSOCIATED) &&
		     raw_smp_processor_id() != pool->cpu);

	/*
	 * A single work shouldn't be executed concurrently by
	 * multiple workers on a single cpu.  Check whether anyone is
	 * already processing the work.  If so, defer the work to the
	 * currently executing one.
	 */
    //查找当前work是否在被当前worker_pool的其他worker处理 
	collision = find_worker_executing_work(pool, work);
    //如果是 那么丢到这个worker的scheduled队列去 增加一次处理
	if (unlikely(collision)) {
		move_linked_works(work, &collision->scheduled, NULL);
		return;
	}

    //设置worker的属性 把当前work设置到worker中
	/* claim and dequeue */
	debug_work_deactivate(work);
	hash_add(pool->busy_hash, &worker->hentry, (unsigned long)work);
	worker->current_work = work;
	worker->current_func = work->func;
	worker->current_pwq = pwq;
	work_color = get_work_color(work);

	/*
	 * Record wq name for cmdline and debug reporting, may get
	 * overridden through set_worker_desc().
	 */
	strscpy(worker->desc, pwq->wq->name, WORKER_DESC_LEN);

    //把当前work从worklist链表上删除
	list_del_init(&work->entry);

	/*
	 * CPU intensive works don't participate in concurrency management.
	 * They're the scheduler's responsibility.  This takes @worker out
	 * of concurrency management and the next code block will chain
	 * execution of the pending work items.
	 */
	if (unlikely(cpu_intensive))
		worker_set_flags(worker, WORKER_CPU_INTENSIVE);

	/*
	 * Wake up another worker if necessary.  The condition is always
	 * false for normal per-cpu workers since nr_running would always
	 * be >= 1 at this point.  This is used to chain execution of the
	 * pending work items for WORKER_NOT_RUNNING workers such as the
	 * UNBOUND and CPU_INTENSIVE ones.
	 */
    //是否需要唤醒更多worker
	if (need_more_worker(pool))
		wake_up_worker(pool);

	/*
	 * Record the last pool and clear PENDING which should be the last
	 * update to @work.  Also, do this inside @pool->lock so that
	 * PENDING and queued state changes happen together while IRQ is
	 * disabled.
	 */
    //记录当前的worker_pool id 清空pending标识
	set_work_pool_and_clear_pending(work, pool->id);

	spin_unlock_irq(&pool->lock);

	lock_map_acquire(&pwq->wq->lockdep_map);
	lock_map_acquire(&lockdep_map);
	/*
	 * Strictly speaking we should mark the invariant state without holding
	 * any locks, that is, before these two lock_map_acquire()'s.
	 *
	 * However, that would result in:
	 *
	 *   A(W1)
	 *   WFC(C)
	 *		A(W1)
	 *		C(C)
	 *
	 * Which would create W1->C->W1 dependencies, even though there is no
	 * actual deadlock possible. There are two solutions, using a
	 * read-recursive acquire on the work(queue) 'locks', but this will then
	 * hit the lockdep limitation on recursive locks, or simply discard
	 * these locks.
	 *
	 * AFAICT there is no possible deadlock scenario between the
	 * flush_work() and complete() primitives (except for single-threaded
	 * workqueues), so hiding them isn't a problem.
	 */
	lockdep_invariant_state(true);
	trace_workqueue_execute_start(work);
    //执行work
	worker->current_func(work);
	/*
	 * While we must be careful to not use "work" after this, the trace
	 * point will only record its address.
	 */
	trace_workqueue_execute_end(work, worker->current_func);
	lock_map_release(&lockdep_map);
	lock_map_release(&pwq->wq->lockdep_map);

	if (unlikely(in_atomic() || lockdep_depth(current) > 0)) {
		pr_err("BUG: workqueue leaked lock or atomic: %s/0x%08x/%d\n"
		       "     last function: %ps\n",
		       current->comm, preempt_count(), task_pid_nr(current),
		       worker->current_func);
		debug_show_held_locks(current);
		dump_stack();
	}

	/*
	 * The following prevents a kworker from hogging CPU on !PREEMPTION
	 * kernels, where a requeueing work item waiting for something to
	 * happen could deadlock with stop_machine as such work item could
	 * indefinitely requeue itself while all other CPUs are trapped in
	 * stop_machine. At the same time, report a quiescent RCU state so
	 * the same condition doesn't freeze RCU.
	 */
	cond_resched();

	spin_lock_irq(&pool->lock);

	/* clear cpu intensive status */
	if (unlikely(cpu_intensive))
		worker_clr_flags(worker, WORKER_CPU_INTENSIVE);

    // 清空worker属性
	/* tag the worker for identification in schedule() */
	worker->last_func = worker->current_func;

	/* we're done with it, release */
	hash_del(&worker->hentry);
	worker->current_work = NULL;
	worker->current_func = NULL;
	worker->current_pwq = NULL;
	pwq_dec_nr_in_flight(pwq, work_color);
}


```

**worker动态管理**

![](.\images\工作队列11.png)

- `worker_pool`通过`nr_running`字段来在不同的状态机之间进行切换；
- `worker_pool`中有`work`需要处理时，需要至少保证有一个运行状态的`worker`，当`nr_running`大于1时，将多余的`worker`进入IDLE状态，没有`work`需要处理时，所有的`worker`都会进入IDLE状态；
- 执行`work`时，如果回调函数阻塞运行，那么会让`worker`进入睡眠状态，此时调度器会进行判断是否需要唤醒另一个`worker`；
- IDLE状态的`worker`都存放在`idle_list`链表中，如果空闲时间超过了300秒，则会将其进行销毁；

```c
// kernel/workqueue.c

//设置定时检查空闲线程进行销毁
static void idle_worker_timeout(struct timer_list *t)
{
	struct worker_pool *pool = from_timer(pool, t, idle_timer);

	spin_lock_irq(&pool->lock);

	while (too_many_workers(pool)) {
		struct worker *worker;
		unsigned long expires;

		/* idle_list is kept in LIFO order, check the last one */
		worker = list_entry(pool->idle_list.prev, struct worker, entry);
		expires = worker->last_active + IDLE_WORKER_TIMEOUT;

		if (time_before(jiffies, expires)) {
			mod_timer(&pool->idle_timer, expires);
			break;
		}

		destroy_worker(worker);
	}

	spin_unlock_irq(&pool->lock);
}
```

![](.\images\工作队列12.png)

- 当`worker`进入睡眠状态时，如果该`worker_pool`没有其他的`worker`处于运行状态，那么是需要唤醒一个空闲的`worker`来维持并发处理的能力；

```c
// kernel/sched/core.c 

//进程调度
asmlinkage __visible void __sched schedule(void)
{
    struct task_struct *tsk = current;

    sched_submit_work(tsk);
    do {
       preempt_disable();
       __schedule(false);
       sched_preempt_enable_no_resched();
    } while (need_resched());
    sched_update_worker(tsk);
}
EXPORT_SYMBOL(schedule);



static inline void sched_submit_work(struct task_struct *tsk)
{
	if (!tsk->state)
		return;

	/*
	 * If a worker went to sleep, notify and ask workqueue whether
	 * it wants to wake up a task to maintain concurrency.
	 * As this function is called inside the schedule() context,
	 * we disable preemption to avoid it calling schedule() again
	 * in the possible wakeup of a kworker.
	 */
    //如果当前进程是一个wq_worker
	if (tsk->flags & (PF_WQ_WORKER | PF_IO_WORKER)) {
		preempt_disable();
		if (tsk->flags & PF_WQ_WORKER)
            //调用wq_worker_sleeping
			wq_worker_sleeping(tsk);
		else
			io_wq_worker_sleeping(tsk);
		preempt_enable_no_resched();
	}

	if (tsk_is_pi_blocked(tsk))
		return;

	/*
	 * If we are going to sleep and we have plugged IO queued,
	 * make sure to submit it to avoid deadlocks.
	 */
	if (blk_needs_flush_plug(tsk))
		blk_schedule_flush_plug(tsk);
}

void wq_worker_sleeping(struct task_struct *task)
{
	struct worker *next, *worker = kthread_data(task);
	struct worker_pool *pool;

	/*
	 * Rescuers, which may not have all the fields set up like normal
	 * workers, also reach here, let's not access anything before
	 * checking NOT_RUNNING.
	 */
	if (worker->flags & WORKER_NOT_RUNNING)
		return;

	pool = worker->pool;

	if (WARN_ON_ONCE(worker->sleeping))
		return;
    //设置当前worker状态为sleeping
	worker->sleeping = 1;
	spin_lock_irq(&pool->lock);

	/*
	 * The counterpart of the following dec_and_test, implied mb,
	 * worklist not empty test sequence is in insert_work().
	 * Please read comment there.
	 *
	 * NOT_RUNNING is clear.  This means that we're bound to and
	 * running on the local cpu w/ rq lock held and preemption
	 * disabled, which in turn means that none else could be
	 * manipulating idle_list, so dereferencing idle_list without pool
	 * lock is safe.
	 */
    //pool->nr_running-1
    //如果有必要 唤醒另一个worker
	if (atomic_dec_and_test(&pool->nr_running) &&
	    !list_empty(&pool->worklist)) {
		next = first_idle_worker(pool);
		if (next)
			wake_up_process(next->task);
	}
	spin_unlock_irq(&pool->lock);
}
```

![](.\images\工作队列13.png)

- 睡眠状态可以通过`wake_up_worker`来进行唤醒处理，最终判断如果该`worker`不在运行状态，则增加`worker_pool`的`nr_running`值；





**三种机制比较**

| 下半部机制 | 执行函数时是否会禁止中断 | 执行上下文 | 能否睡眠 | 并发一致性                                    | 开销                         |
| ---------- | ------------------------ | ---------- | -------- | --------------------------------------------- | ---------------------------- |
| softirq    | 不会                     | 中断上下文 | 否       | 当前cpu的同类型softirq会被禁止，其他cpu不保证 |                              |
| tasklet    | 不会                     | 中断上下文 | 否       | 多个cpu也无法并发执行同一个tasklet            |                              |
| work queue | 不会                     | 进程上下文 | 能       | 无                                            | 大，涉及到内核进程上下文切换 |



##### 以网卡中断为例的中断全流程

@todo 缺少网卡的实际处理逻辑

![image-20241125165903286](.\images\以网卡为例的中断全流程01.png)

![image-20241125165948669](.\images\以网卡为例的硬件中断全流程02.png)

<img src=".\images\以网卡为例的硬件中断全流程03.png" alt="image-20241125170214221" style="zoom:150%;" />

![image-20241125170313733](.\images\以网卡为例的硬件中断全流程04.png)

![image-20241125170338940](.\images\以网卡为例的硬件中断全流程05.png)

![image-20241125170424755](.\images\以网卡为例的硬件中断全流程06.png)

![image-20241125170440794](.\images\以网卡为例的硬件中断全流程07.png)



**softirq注册**

```c

//net/core/dev.c



/*
 *      Initialize the DEV module. At boot time this walks the device list and
 *      unhooks any devices that fail to initialise (normally hardware not
 *      present) and leaves us with a valid list of present and active devices.
 *
 */

/*
 *       This is called single threaded during boot, so no need
 *       to take the rtnl semaphore.
 */
static int __init net_dev_init(void)
{
        int i, rc = -ENOMEM;

        BUG_ON(!dev_boot_phase);

        if (dev_proc_init())
               goto out;

        if (netdev_kobject_init())
               goto out;

        INIT_LIST_HEAD(&ptype_all);
        for (i = 0; i < PTYPE_HASH_SIZE; i++)
               INIT_LIST_HEAD(&ptype_base[i]);

        INIT_LIST_HEAD(&offload_base);

        if (register_pernet_subsys(&netdev_net_ops))
               goto out;

        /*
         *     Initialise the packet receive queues.
         */

        for_each_possible_cpu(i) {
               struct work_struct *flush = per_cpu_ptr(&flush_works, i);
               struct softnet_data *sd = &per_cpu(softnet_data, i);

               INIT_WORK(flush, flush_backlog);

               skb_queue_head_init(&sd->input_pkt_queue);
               skb_queue_head_init(&sd->process_queue);
#ifdef CONFIG_XFRM_OFFLOAD
               skb_queue_head_init(&sd->xfrm_backlog);
#endif
               INIT_LIST_HEAD(&sd->poll_list);
               sd->output_queue_tailp = &sd->output_queue;
#ifdef CONFIG_RPS
               sd->csd.func = rps_trigger_softirq;
               sd->csd.info = sd;
               sd->cpu = i;
#endif

               init_gro_hash(&sd->backlog);
               sd->backlog.poll = process_backlog;
               sd->backlog.weight = weight_p;
        }

        dev_boot_phase = 0;

        /* The loopback device is special if any other network devices
         * is present in a network namespace the loopback device must
         * be present. Since we now dynamically allocate and free the
         * loopback device ensure this invariant is maintained by
         * keeping the loopback device as the first device on the
         * list of network devices.  Ensuring the loopback devices
         * is the first device that appears and the last network device
         * that disappears.
         */
        if (register_pernet_device(&loopback_net_ops))
               goto out;

        if (register_pernet_device(&default_device_ops))
               goto out;

        //注册网卡softirq的中断处理函数
        open_softirq(NET_TX_SOFTIRQ, net_tx_action);
        open_softirq(NET_RX_SOFTIRQ, net_rx_action);

        rc = cpuhp_setup_state_nocalls(CPUHP_NET_DEV_DEAD, "net/dev:dead",
                                    NULL, dev_cpu_dead);
        WARN_ON(rc < 0);
        rc = 0;
out:
        return rc;
}

subsys_initcall(net_dev_init);
```

**网卡中断处理函数**

```c
//drivers/net/ethernet/intel/e1000e/netdev.c

//触发硬件中断后 中断处理函数
static irqreturn_t e1000_intr(int __always_unused irq, void *data)
{
        struct net_device *netdev = data;
        struct e1000_adapter *adapter = netdev_priv(netdev);
        struct e1000_hw *hw = &adapter->hw;
        u32 rctl, icr = er32(ICR);

        //检查当前中断是否由网卡产生
        if (!icr || test_bit(__E1000_DOWN, &adapter->state))
               return IRQ_NONE;       /* Not our interrupt */

        /* IMS will not auto-mask if INT_ASSERTED is not set, and if it is
         * not set, then the adapter didn't send an interrupt
         */
        if (!(icr & E1000_ICR_INT_ASSERTED))
               return IRQ_NONE;

        /* Interrupt Auto-Mask...upon reading ICR,
         * interrupts are masked.  No need for the
         * IMC write
         */

        if (icr & E1000_ICR_LSC) {
               hw->mac.get_link_status = true;
               /* ICH8 workaround-- Call gig speed drop workaround on cable
                * disconnect (LSC) before accessing any PHY registers
                */
               if ((adapter->flags & FLAG_LSC_GIG_SPEED_DROP) &&
                   (!(er32(STATUS) & E1000_STATUS_LU)))
                   //使用work queue 处理
                      schedule_work(&adapter->downshift_task);

               /* 80003ES2LAN workaround--
                * For packet buffer work-around on link down event;
                * disable receives here in the ISR and
                * reset adapter in watchdog
                */
               if (netif_carrier_ok(netdev) &&
                   (adapter->flags & FLAG_RX_NEEDS_RESTART)) {
                      /* disable receives */
                      rctl = er32(RCTL);
                      ew32(RCTL, rctl & ~E1000_RCTL_EN);
                      adapter->flags |= FLAG_RESTART_NOW;
               }
               /* guard against interrupt when we're going down */
               if (!test_bit(__E1000_DOWN, &adapter->state))
                      mod_timer(&adapter->watchdog_timer, jiffies + 1);
        }

        /* Reset on uncorrectable ECC error */
        if ((icr & E1000_ICR_ECCER) && (hw->mac.type >= e1000_pch_lpt)) {
               u32 pbeccsts = er32(PBECCSTS);

               adapter->corr_errors +=
                   pbeccsts & E1000_PBECCSTS_CORR_ERR_CNT_MASK;
               adapter->uncorr_errors +=
                   (pbeccsts & E1000_PBECCSTS_UNCORR_ERR_CNT_MASK) >>
                   E1000_PBECCSTS_UNCORR_ERR_CNT_SHIFT;

               /* Do the reset outside of interrupt context */
               schedule_work(&adapter->reset_task);

               /* return immediately since reset is imminent */
               return IRQ_HANDLED;
        }

        if (napi_schedule_prep(&adapter->napi)) {
               adapter->total_tx_bytes = 0;
               adapter->total_tx_packets = 0;
               adapter->total_rx_bytes = 0;
               adapter->total_rx_packets = 0;
               //使用softirq处理
               __napi_schedule(&adapter->napi);
        }

        return IRQ_HANDLED;
}
```



**NAPI**

NAPI（New API）是Linux内核中的一种机制，旨在提高网络设备在高负载下的性能。NAPI通过减少中断频率和增加每次中断处理的数据量，从而降低CPU的中断处理开销，提高系统的整体吞吐量。

> 随着网络带宽的发展，网速越来越快，之前的中断收包模式已经无法适应目前千兆，万兆的带宽了。如果每个数据包大小等于MTU大小1460字节。当驱动以千兆网速收包时，CPU将每秒被中断91829次。在以MTU收包的情况下都会出现每秒被中断10万次的情况。过多的中断会引起一个问题，CPU一直陷入硬中断而没有时间来处理别的事情了。为了解决这个问题，内核在2.6中引入了NAPI机制。
>
> NAPI就是混合中断和轮询的方式来收包，当有中断来了，驱动关闭中断，通知内核收包，内核软中断轮询当前网卡，在规定时间尽可能多的收包。时间用尽或者没有数据可收，内核再次开启中断，准备下一次收包。
>

@todo







## 定时器和时间管理

> 时间概念对计算机来说有些模糊，事实上内核必须在硬件的帮助下才能计算和管理时间。硬件为内核提供了一个**系统定时器**用以计算流逝的时间，该时钟在内核中可看成是一个电子时间资源，比如数字时钟或处理器频率等。**系统定时器以某种频率自行触发时钟中断**，该频率可以通过编程预定，称作**节拍率**(tickrate)。当时钟中断发生时，内核就通过一种特殊的中断处理程序对其进行处理

### 节拍率

```c
//include/uapi/asm-generic/param.h

/* SPDX-License-Identifier: GPL-2.0 WITH Linux-syscall-note */
#ifndef _UAPI__ASM_GENERIC_PARAM_H
#define _UAPI__ASM_GENERIC_PARAM_H

#ifndef HZ
#define HZ 100 //每秒钟触发100次内核时钟中断 增加100次内核jiffies
#endif

#ifndef EXEC_PAGESIZE
#define EXEC_PAGESIZE	4096
#endif

#ifndef NOGROUP
#define NOGROUP		(-1)
#endif

#define MAXHOSTNAMELEN	64	/* max length of hostname */


#endif /* _UAPI__ASM_GENERIC_PARAM_H */



```

**用户节拍率**

为了避免内核在修改HZ时，用户空间如果继续使用HZ来计算系统运行时间造成问题，引入了USER_HZ，内核可以使用宏 jiffies_to_clock_t()将一个有HZ表示的节拍计数转换为一个由USER_HZ表示的节拍计数，这样即使更改内核HZ，只要在使用用户jiffies时做转换，用户空间的时间计算就不会受到影响。

```c
// include/asm-generic/param.h

/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __ASM_GENERIC_PARAM_H
#define __ASM_GENERIC_PARAM_H

#include <uapi/asm-generic/param.h>

# undef HZ
# define HZ		CONFIG_HZ	/* Internal kernel timer frequency */
# define USER_HZ	100		/* some user interfaces are */ // 增加100次用户jiffies
# define CLOCKS_PER_SEC	(USER_HZ)       /* in "ticks" like times() */
#endif /* __ASM_GENERIC_PARAM_H */



//kernel/time/time.c
//将一个有HZ表示的节拍计数转换为一个由USER_HZ表示的节拍计数
u64 jiffies_64_to_clock_t(u64 x)
{
#if (TICK_NSEC % (NSEC_PER_SEC / USER_HZ)) == 0
# if HZ < USER_HZ
	x = div_u64(x * USER_HZ, HZ);
# elif HZ > USER_HZ
	x = div_u64(x, HZ / USER_HZ);
# else
	/* Nothing to do */
# endif
#else
	/*
	 * There are better ways that don't overflow early,
	 * but even this doesn't overflow in hundreds of years
	 * in 64 bits, so..
	 */
	x = div_u64(x * TICK_NSEC, (NSEC_PER_SEC / USER_HZ));
#endif
	return x;
}

```

查看CONFIG_HZ

```shell
[root@localhost ~]# grep CONFIG_HZ /boot/config-$(uname -r) # 查看当前Linux系统的HZ设置
# CONFIG_HZ_PERIODIC is not set
# CONFIG_HZ_100 is not set
# CONFIG_HZ_250 is not set
# CONFIG_HZ_300 is not set
CONFIG_HZ_1000=y
CONFIG_HZ=1000 #每秒钟产生1000次时钟中断
```





### jiffies

`jiffies` 是Linux内核中用于时间管理的一个重要概念。它是一个全局变量，用于记录自系统启动以来发生的时钟滴答（ticks）数量。每个时钟滴答被称为一个“jiffy”，而 `jiffies` 变量就是一个累积的计数器，记录了从系统启动以来的总滴答数。

- **时钟滴答**: 每次时钟中断发生时，`jiffies` 的值会增加1。
- **频率**: 时钟滴答的频率由内核配置参数 `HZ` 决定，表示每秒发生的时钟中断次数。常见的值有100、250、1000等。

全局变量jiffies用来记录自系统启动以来产生的节拍的总数。启动时，内核将该变量初始化为0，此后，每次时钟中断处理程序都会增加该变量的值。因为一秒内时钟中断的次数等于Hz，所以jiffies一秒内增加的值也就为Hz。系统运行时间以秒为单位计算，就等于jiffies/Hz。

<img src=".\images\jiffies.png" alt="image-20241127110246522" style="zoom:50%;" />

```c
// include/uapi/asm-generic/param.h

//默认是100 即 每秒钟产生100次时钟中断

#ifndef HZ
#define HZ 100
#endif

//include/linux/jiffies.h

/*
 * The 64-bit value is not atomic - you MUST NOT read it
 * without sampling the sequence number in jiffies_lock.
 * get_jiffies_64() will do this for you as appropriate.
 */
//jiffies_64 64位
extern u64 __cacheline_aligned_in_smp jiffies_64;
//jiffies 在32位架构上只取jiffies_64的低32位 在64位架构上取jiffies_64的64位
extern unsigned long volatile __cacheline_aligned_in_smp __jiffy_arch_data jiffies;

#if (BITS_PER_LONG < 64)
u64 get_jiffies_64(void);
#else
static inline u64 get_jiffies_64(void)
{
	return (u64)jiffies;
}
#endif

```

### LAPIC中时钟工作原理

#### 晶振

> **石英晶体谐振器**（英文**quartz crystal unit**或**quartz crystal resonator**），或**晶体振荡器**（英文**crystal oscillator**），简写为**晶振**，英文简写为**Xtal**或**X'tal**（或全大写)，简称**石英晶体**或**晶振**，是利用[石英](https://zh.wikipedia.org/wiki/石英)[晶体](https://zh.wikipedia.org/wiki/晶體)（又称[水晶](https://zh.wikipedia.org/wiki/水晶)）的[压电效应](https://zh.wikipedia.org/wiki/壓電效應)，用来产生高精度振荡频率的一种电子器件

**石英晶体谐振器** 能够产生中央处理器（CPU）执行指令所必须的时钟频率信号，CPU一切指令的执行都是建立在这个基础上的，时钟信号频率越高，通常CPU的运行速度也就越快。晶振常用标称频率在1～200MHz之间，比如32768Hz、8MHz、12MHz、24MHz、125MHz等，更高的输出频率也常用PLL（锁相环）将低频进行倍频至1GHz以上。晶振可以**高精度**和**高稳定性**的产生一定频率的信号。

拥有晶振后，现在可以准确的在计算机架构中衡量时间，一个完整的晶振频率周期为1s。

#### 时钟分频器

时钟分频器（Clock Divider）是一种电路或逻辑模块，用于将输入的高频时钟信号转换为较低频率的时钟信号。

> 时钟分频器的基本功能是将输入的时钟信号的频率降低一定的倍数。这个倍数通常是一个整数，称为分频比（Division Ratio）。例如，如果输入时钟信号的频率是 100 MHz，分频比是 10，那么输出时钟信号的频率就是 10 MHz。

#### 时钟工作原理

- **计数器**：LAPIC 内部有一个计数器（通常称为初始计数器 Initial Count Register），它使用从时钟分频器得到的时钟信号进行递减计数。
- **比较器**：还有一个比较器（Current Count Register），用于存储当前的计数值。
- **中断生成**：当计数器的值递减到零时，LAPIC 会生成一个定时器中断。此时，计数器可以重新加载初始值，继续递减计数，从而实现周期性的中断。

```c
//arch/x86/include/asm/apicdef.h

//计数器
/*380*/ struct { /* Timer Initial Count Register */
               u32   initial_count;
               u32 __reserved_2[3];
        } timer_icr;

//比较器
/*390*/ const
        struct { /* Timer Current Count Register */
               u32   curr_count;
               u32 __reserved_2[3];
        } timer_ccr;
```



**工作原理**

- 在系统启动时，LAPIC 的定时器会被初始化。初始计数器（Initial Count Register）被设置为一个预定的值，这个值决定了定时器中断的周期。

  例如，如果初始计数器设置为 100000，并且时钟频率为 1 MHz（每秒 1 百万次），那么定时器中断的周期将是 100 毫秒（100000 / 1000000 = 0.1 秒）。

- LAPIC 内部的计数器开始递减计数，每次时钟脉冲到来时，计数器的值减少 1。

- 当计数器的值递减到零时，LAPIC 生成一个定时器中断。这个中断被发送到 CPU，触发中断处理程序。

- 计数器可以自动重新加载初始值，继续递减计数，从而实现周期性的中断。



### 系统定时器和实时时钟

#### 实时时钟

实时时钟(RTC)是用来持久存放系统时间的设备，即便系统关闭后，它也可以靠主板上的微型电池提供的电力保持系统的计时。在PC体系结构中，RTC和CMOS集成在一起，而且RTC的运行和BIOS的保存设置都是通过同一个电池供电的。当系统启动时，内核通过读取RTC来初始化墙上时间，该时间存放在xtime变量中。虽然内核通常不会在系统启动后再读取xtime变量，但是有些体系结构，比如x86，会周期性地将当前时间值存回RTC中。尽管如此，实时时钟最主要的作用仍是在启动时初始化xtime变量。

#### 系统定时器

系统定时器是内核定时机制中最为重要的角色。尽管不同体系结构中的定时器实现不尽相同但是系统定时器的根本思想并没有区别--提供一种周期性触发中断机制。有些体系结构是通过对电子晶振进行分频来实现系统定时器;还有些体系结构则提供了一个衰减测量器(decrementer)-衰减测量器设置一个初始值，该值以固定频率递减，当减到零时，触发一个中断。无论哪种情况。其效果都一一样。

在0x86体系结构中，主要采用可编程中断时钟(PIT)。PIT在PC机器中普遍存在，而且从DOS时代，就开始以它作时钟中断源了。内核在启动时对PIT进行编程初始化，使其能够以Hz/秒的频率产生时钟中断。虽然PIT设备很简单，功能也有限，但它却足以满足我们的需要。x86体系结构中的其他的时钟资源还包括本地APIC时钟和时间戳计数(TSC)等。



### 时间

**实时时间**

实时时间（Real-Time Clock，RTC）是指系统中用于记录和提供当前日期和时间的硬件或软件机制。在计算机系统中，RTC 通常指的是硬件时钟，它独立于系统电源，可以在系统关闭或重启后仍然保持正确的日期和时间。

> RTC 芯片是一个专门的硬件设备，用于持续记录当前的日期和时间。它通常由一个小电池供电，即使系统断电，RTC 也能保持时间的准确性。

- **CLOCK_REALTIME**：表示系统当前的日期和时间，通常与硬件 RTC 同步。这个时钟可以被用户或管理员手动设置。
- **CLOCK_MONOTONIC**：表示系统启动以来经过的时间，不受系统时间调整的影响。

```c
// include/linux/timekeeper_internal.h

/**
 * struct timekeeper - Structure holding internal timekeeping values.
 * @tkr_mono:          The readout base structure for CLOCK_MONOTONIC
 * @tkr_raw:           The readout base structure for CLOCK_MONOTONIC_RAW
 * @xtime_sec:         Current CLOCK_REALTIME time in seconds
 * @ktime_sec:         Current CLOCK_MONOTONIC time in seconds
 * @wall_to_monotonic:  CLOCK_REALTIME to CLOCK_MONOTONIC offset
 * @offs_real:         Offset clock monotonic -> clock realtime
 * @offs_boot:         Offset clock monotonic -> clock boottime
 * @offs_tai:          Offset clock monotonic -> clock tai
 * @tai_offset:        The current UTC to TAI offset in seconds
 * @clock_was_set_seq:  The sequence number of clock was set events
 * @cs_was_changed_seq: The sequence number of clocksource change events
 * @next_leap_ktime:    CLOCK_MONOTONIC time value of a pending leap-second
 * @raw_sec:           CLOCK_MONOTONIC_RAW  time in seconds
 * @monotonic_to_boot:  CLOCK_MONOTONIC to CLOCK_BOOTTIME offset
 * @cycle_interval:     Number of clock cycles in one NTP interval
 * @xtime_interval:     Number of clock shifted nano seconds in one NTP
 *                    interval.
 * @xtime_remainder:    Shifted nano seconds left over when rounding
 *                    @cycle_interval
 * @raw_interval:       Shifted raw nano seconds accumulated per NTP interval.
 * @ntp_error:         Difference between accumulated time and NTP time in ntp
 *                    shifted nano seconds.
 * @ntp_error_shift:    Shift conversion between clock shifted nano seconds and
 *                    ntp shifted nano seconds.
 * @last_warning:       Warning ratelimiter (DEBUG_TIMEKEEPING)
 * @underflow_seen:     Underflow warning flag (DEBUG_TIMEKEEPING)
 * @overflow_seen:      Overflow warning flag (DEBUG_TIMEKEEPING)
 *
 * Note: For timespec(64) based interfaces wall_to_monotonic is what
 * we need to add to xtime (or xtime corrected for sub jiffie times)
 * to get to monotonic time.  Monotonic is pegged at zero at system
 * boot time, so wall_to_monotonic will be negative, however, we will
 * ALWAYS keep the tv_nsec part positive so we can use the usual
 * normalization.
 *
 * wall_to_monotonic is moved after resume from suspend for the
 * monotonic time not to jump. We need to add total_sleep_time to
 * wall_to_monotonic to get the real boot based time offset.
 *
 * wall_to_monotonic is no longer the boot time, getboottime must be
 * used instead.
 *
 * @monotonic_to_boottime is a timespec64 representation of @offs_boot to
 * accelerate the VDSO update for CLOCK_BOOTTIME.
 */
struct timekeeper {
        struct tk_read_base    tkr_mono;
        struct tk_read_base    tkr_raw;
        u64                  xtime_sec; //实际时间（墙上时间） 在系统初始化后通过rtc赋值给xtime_sec
        unsigned long         ktime_sec; //系统运行时间
        struct timespec64      wall_to_monotonic;
        ktime_t                      offs_real;
        ktime_t                      offs_boot;
        ktime_t                      offs_tai;
        s32                  tai_offset;
        unsigned int          clock_was_set_seq;
        u8                   cs_was_changed_seq;
        ktime_t                      next_leap_ktime;
        u64                  raw_sec;
        struct timespec64      monotonic_to_boot;

        /* The following members are for timekeeping internal use */
        u64                  cycle_interval;
        u64                  xtime_interval;
        s64                  xtime_remainder;
        u64                  raw_interval;
        /* The ntp_tick_length() value currently being used.
         * This cached copy ensures we consistently apply the tick
         * length for an entire tick, as ntp_tick_length may change
         * mid-tick, and we don't want to apply that new value to
         * the tick in progress.
         */
        u64                  ntp_tick;
        /* Difference between accumulated time and NTP time in ntp
         * shifted nano seconds. */
        s64                  ntp_error;
        u32                  ntp_error_shift;
        u32                  ntp_err_mult;
        /* Flag used to avoid updating NTP twice with same second */
        u32                  skip_second_overflow;
#ifdef CONFIG_DEBUG_TIMEKEEPING
        long                 last_warning;
        /*
         * These simple flag variables are managed
         * without locks, which is racy, but they are
         * ok since we don't really care about being
         * super precise about how many events were
         * seen, just that a problem was observed.
         */
        int                  underflow_seen;
        int                  overflow_seen;
#endif
};
```



### 时钟中断

从LAPIC的timer产生中断，cpu接收中断从IDTR找到IDT，从接收到的中断向量找到对应的中断处理程序。

```c
//arch/x86/kernel/idt.c
INTG(LOCAL_TIMER_VECTOR,	apic_timer_interrupt) //LAPIC时钟中断向量和中断处理程序
```

```assembly
# arch/x86/entry/entry_64.S
apicinterrupt LOCAL_TIMER_VECTOR       apic_timer_interrupt      smp_apic_timer_interrupt
```

```c
// arch/x86/kernel/apic/apic.c

/*
   处理LAPIC时钟中断
 * Local APIC timer interrupt. This is the most natural way for doing
 * local interrupts, but local timer interrupts can be emulated by
 * broadcast interrupts too. [in case the hw doesn't support APIC timers]
 *
 * [ if a single-CPU system runs an SMP kernel then we call the local
 *   interrupt as well. Thus we cannot inline the local irq ... ]
 */
__visible void __irq_entry smp_apic_timer_interrupt(struct pt_regs *regs)
{
    //保存寄存器
	struct pt_regs *old_regs = set_irq_regs(regs);

	/*
	 * NOTE! We'd better ACK the irq immediately,
	 * because timer handling can be slow.
	 *
	 * update_process_times() expects us to have done irq_enter().
	 * Besides, if we don't timer interrupts ignore the global
	 * interrupt lock, which is the WrongThing (tm) to do.
	 */
    //通过apic_eoi()立刻响应LAPIC 清除ISR的标志位 因为时钟处理可能很慢，而时钟中断触发频率很快
	entering_ack_irq();
    //跟踪记录触发时钟中断
	trace_local_timer_entry(LOCAL_TIMER_VECTOR);
    //时钟中断处理
	local_apic_timer_interrupt();
	trace_local_timer_exit(LOCAL_TIMER_VECTOR);
	exiting_irq();

    //恢复寄存器
	set_irq_regs(old_regs);
}


static inline void entering_ack_irq(void)
{
	entering_irq();
	ack_APIC_irq();
}

static inline void ack_APIC_irq(void)
{
	/*
	 * ack_APIC_irq() actually gets compiled as a single instruction
	 * ... yummie.
	 */
	apic_eoi();
}

//cpu独有的时钟事件设备结构体
static DEFINE_PER_CPU(struct clock_event_device, lapic_events);


/*
 * The guts of the apic timer interrupt
 */
static void local_apic_timer_interrupt(void)
{
    //获取当前cpu的时钟事件设备
	struct clock_event_device *evt = this_cpu_ptr(&lapic_events);

	/*
	 * Normally we should not be here till LAPIC has been initialized but
	 * in some cases like kdump, its possible that there is a pending LAPIC
	 * timer interrupt from previous kernel's context and is delivered in
	 * new kernel the moment interrupts are enabled.
	 *
	 * Interrupts are enabled early and LAPIC is setup much later, hence
	 * its possible that when we get here evt->event_handler is NULL.
	 * Check for event_handler being NULL and discard the interrupt as
	 * spurious.
	 */
	if (!evt->event_handler) {
		pr_warn("Spurious LAPIC timer interrupt on cpu %d\n",
			smp_processor_id());
		/* Switch it off */
		lapic_timer_shutdown(evt);
		return;
	}

	/*
	 * the NMI deadlock-detector uses this.
	 */
	inc_irq_stat(apic_timer_irqs);

    //调用evt的event_handler
	evt->event_handler(evt);
}


/**
设置event_handler的过程
setup_boot_APIC_clock()
    calibrate_APIC_clock()
    setup_APIC_timer()
        evt = this_cpu_ptr(&lapic_events);
        memcpy(levt, &lapic_clockevent, sizeof(*levt));
        clockevents_register_device(evt)
            tick_check_new_device(dev);
                tick_setup_device(td, newdev, cpu, cpumask_of(cpu));
                    tick_setup_periodic(newdev, 0);
                        tick_set_periodic_handler(dev, broadcast);
                            dev->event_handler = tick_handle_periodic;
*/                            

//kernel/time/tick-common.c

//时钟中断处理函数
/*
 * Event handler for periodic ticks
 */
void tick_handle_periodic(struct clock_event_device *dev)
{
	int cpu = smp_processor_id();
	ktime_t next = dev->next_event;

	tick_periodic(cpu);

#if defined(CONFIG_HIGH_RES_TIMERS) || defined(CONFIG_NO_HZ_COMMON)
	/*
	 * The cpu might have transitioned to HIGHRES or NOHZ mode via
	 * update_process_times() -> run_local_timers() ->
	 * hrtimer_run_queues().
	 */
	if (dev->event_handler != tick_handle_periodic)
		return;
#endif

	if (!clockevent_state_oneshot(dev))
		return;
	for (;;) {
		/*
		 * Setup the next period for devices, which do not have
		 * periodic mode:
		 */
		next = ktime_add(next, tick_period);

		if (!clockevents_program_event(dev, next, false))
			return;
		/*
		 * Have to be careful here. If we're in oneshot mode,
		 * before we call tick_periodic() in a loop, we need
		 * to be sure we're using a real hardware clocksource.
		 * Otherwise we could get trapped in an infinite
		 * loop, as the tick_periodic() increments jiffies,
		 * which then will increment time, possibly causing
		 * the loop to trigger again and again.
		 */
		if (timekeeping_valid_for_hres())
			tick_periodic(cpu);
	}
}


/*
 * tick_do_timer_cpu is a timer core internal variable which holds the CPU NR
 * which is responsible for calling do_timer(), i.e. the timekeeping stuff. This
 * variable has two functions:
 *
 * 1) Prevent a thundering herd issue of a gazillion of CPUs trying to grab the
 *    timekeeping lock all at once. Only the CPU which is assigned to do the
 *    update is handling it.
 *
 * 2) Hand off the duty in the NOHZ idle case by setting the value to
 *    TICK_DO_TIMER_NONE, i.e. a non existing CPU. So the next cpu which looks
 *    at it will take over and keep the time keeping alive.  The handover
 *    procedure also covers cpu hotplug.
 */
//只有指定的cpu才能执行do_timer() 避免争抢和惊群现象
int tick_do_timer_cpu __read_mostly = TICK_DO_TIMER_BOOT;

//时钟中断处理
/*
 * Periodic tick
 */
static void tick_periodic(int cpu)
{
    //只有指定的cpu才能去执行do_timer()
	if (tick_do_timer_cpu == cpu) {
		write_seqlock(&jiffies_lock);

		/* Keep track of the next tick event */
		tick_next_period = ktime_add(tick_next_period, tick_period);

        //更新jiffies
		do_timer(1);
		write_sequnlock(&jiffies_lock);
        //更新墙上时间
		update_wall_time();
	}

    //判断当前是用户空间还是内核空间
    //更新进程相关
	update_process_times(user_mode(get_irq_regs()));
	profile_tick(CPU_PROFILING);
}


/*
   必须在持有jiffies_lock锁的情况下才能执行
 * Must hold jiffies_lock
 */
void do_timer(unsigned long ticks)
{
    //jiffies加上ticks的值
	jiffies_64 += ticks;
	calc_global_load(ticks);
}



/**
 * update_wall_time - Uses the current clocksource to increment the wall time
 * 更新墙上时间
 */
void update_wall_time(void)
{
	timekeeping_advance(TK_ADV_TICK);
}


/*
 * Called from the timer interrupt handler to charge one tick to the current
 * process.  user_tick is 1 if the tick is user time, 0 for system.
 */
void update_process_times(int user_tick)
{
	struct task_struct *p = current;

	/* Note: this timer irq context must be accounted for as well. */
    //更新进程在用户态和内核态的运行时间
	account_process_tick(p, user_tick);
    //处理本地定时器事件
	run_local_timers();
    //更新 RCU（Read-Copy-Update）调度时钟
	rcu_sched_clock_irq(user_tick);
#ifdef CONFIG_IRQ_WORK
	if (in_irq())
		irq_work_tick();
#endif
    //处理调度器的周期性任务
	scheduler_tick();
	if (IS_ENABLED(CONFIG_POSIX_TIMERS))
		run_posix_cpu_timers();
}


/*
 * Called by the local, per-CPU timer interrupt on SMP.
 */
void run_local_timers(void)
{
	struct timer_base *base = this_cpu_ptr(&timer_bases[BASE_STD]);

	hrtimer_run_queues();
	/* Raise the softirq only if required. */
	if (time_before(jiffies, base->clk)) {
		if (!IS_ENABLED(CONFIG_NO_HZ_COMMON))
			return;
		/* CPU is awake, so check the deferrable base. */
		base++;
		if (time_before(jiffies, base->clk))
			return;
	}
    //触发softirq的TIMER_SOFTIRQ事件
	raise_softirq(TIMER_SOFTIRQ);
}

```

**timer_base**

系统中可能同时存在成千上万个定时器，如果处理不好效率会非常低下。`Linux`目前会将定时器按照绑定的`CPU`和`种类`（普通定时器还是可延迟定时器两种）进行区分，由`timer_base`结构体组织起来：

<img src=".\images\timer_base.png" style="zoom: 67%;" />

```c
//kernel/time/timer.c

//每个cpu都包含1-2个timer_base
static DEFINE_PER_CPU(struct timer_base, timer_bases[NR_BASES]);


//
struct timer_base {
	raw_spinlock_t		lock; //锁
	struct timer_list	*running_timer; //当前正在处理的timer_list
#ifdef CONFIG_PREEMPT_RT
	spinlock_t		expiry_lock;
	atomic_t		timer_waiters;
#endif
	unsigned long		clk; //当前定时器所经过的jiffies，用来判断包含的定时器是否已经到期或超时
	unsigned long		next_expiry;  //该字段指向该CPU下一个即将到期的定时器 最早 (距离超时最近的 timer) 的超时时间
	unsigned int		cpu; //所属cpu
	bool			is_idle;
	bool			must_forward_clk;
	DECLARE_BITMAP(pending_map, WHEEL_SIZE); //时间轮中有几个桶就有几个比特位。如果某个桶内有定时器存在，那么就将相应的比特位置1。
	struct hlist_head	vectors[WHEEL_SIZE]; //时间轮所有桶的数组，每一个元素是一个链表
} ____cacheline_aligned;
```

**定时器层级Level**

```c
// kernel/time/timer.c

//定时器层级Level 8-9层
/* Level depth */
#if HZ > 100
# define LVL_DEPTH  9
# else
# define LVL_DEPTH  8
#endif


//每一个Level里面有64个桶
/* Size of each clock level */
#define LVL_BITS	6
#define LVL_SIZE	(1UL << LVL_BITS)
#define LVL_MASK	(LVL_SIZE - 1)
#define LVL_OFFS(n)	((n) * LVL_SIZE)


//每个timer_base的桶总数 = 9 * 64 = 576
/*
 * The resulting wheel size. If NOHZ is configured we allocate two
 * wheels so we have a separate storage for the deferrable timers.
 */
#define WHEEL_SIZE	(LVL_SIZE * LVL_DEPTH)


// 定时器粒度 
/**
系统至少要过多少个jiffy才会检查某一个级里面的所有定时器。
每一级的64个桶的检查粒度是一样的，而不同级内的桶之间检查的粒度不同，级数越小，检查粒度越细。
每一级粒度的jiffy数由宏定义LVL_CLK_DIV的值决定：

也就是第0级内64个桶中存放的所有定时器每个Tick都会检查，第1级内64个桶中存放的所有定时器每8个jiffy才会检查，第2级内64个桶中存放的所有定时器每64个jiffy才会检查，以此类推。8的level次方。

**/
/* Clock divisor for the next level */
#define LVL_CLK_SHIFT	3
#define LVL_CLK_DIV	(1UL << LVL_CLK_SHIFT)
#define LVL_CLK_MASK	(LVL_CLK_DIV - 1)
#define LVL_SHIFT(n)	((n) * LVL_CLK_SHIFT)
#define LVL_GRAN(n)	(1UL << LVL_SHIFT(n))


//每一级的范围
/*
 * The time start value for each level to select the bucket at enqueue
 * time.
 */
#define LVL_START(n)	((LVL_SIZE - 1) << (((n) - 1) * LVL_CLK_SHIFT))



```

下面具体举个例子，内核配置选项将`HZ`配置位`1000`，那么就一共需要`9`个级别，每个级别里面有`64`个桶，所以一共需要`576`个桶。每个级别的情况如下表：

| 级别 | 编号偏移 | 粒度（Granularity）） |                                  差值范围 |
| :--- | -------: | --------------------: | ----------------------------------------: |
| 0    |        0 |                  1 ms |                              0 ms - 63 ms |
| 1    |       64 |                  8 ms |                            64 ms - 511 ms |
| 2    |      128 |                 64 ms |            512 ms - 4095 ms (512ms - ~4s) |
| 3    |      192 |                512 ms |           4096 ms - 32767 ms (~4s - ~32s) |
| 4    |      256 |         4096 ms (~4s) |         32768 ms - 262143 ms (~32s - ~4m) |
| 5    |      320 |       32768 ms (~32s) |       262144 ms - 2097151 ms (~4m - ~34m) |
| 6    |      384 |       262144 ms (~4m) |     2097152 ms - 16777215 ms (~34m - ~4h) |
| 7    |      448 |     2097152 ms (~34m) |    16777216 ms - 134217727 ms (~4h - ~1d) |
| 8    |      512 |     16777216 ms (~4h) | 134217728 ms - 1073741822 ms (~1d - ~12d) |

因为配置的是`1000Hz`，所以每次`Tick`之间经过`1`毫秒。可以看出来，定时到期时间距离现在越久，那粒度就越差，误差也越大。



**定时器**

![](.\images\timer_flags.png)

- `TIMER_MIGRATING`表示定时器正在从一个CPU迁移到另外一个CPU。
- `TIMER_DEFERRABLE`表示该定时器是可延迟的。
- `TIMER_PINNED`表示定时器已经绑死了当前的CPU，无论如何都不会迁移到别的CPU上。
- `TIMER_IRQSAFE`表示定时器是中断安全的，使用的时候只需要加锁，不需要关中断。

```c
//include/linux/timer.h

struct timer_list {
        /*
         * All fields that change during normal runtime grouped to the
         * same cacheline
         */
        struct hlist_node      entry; //定时器链表的节点
        unsigned long         expires;//过期时间 用jiffies表示
        void                 (*function)(struct timer_list *);//执行函数
        u32                  flags;//标识

#ifdef CONFIG_LOCKDEP
        struct lockdep_map     lockdep_map;
#endif
};

//flags其最高10位记录了定时器放置到桶的编号，后面会提到一共最多只有576个桶，所以10位足够了。而最低的18位指示了该定时器绑定到了哪个CPU上
#define TIMER_CPUMASK		0x0003FFFF
#define TIMER_MIGRATING		0x00040000
#define TIMER_BASEMASK		(TIMER_CPUMASK | TIMER_MIGRATING)
#define TIMER_DEFERRABLE	0x00080000
#define TIMER_PINNED		0x00100000
#define TIMER_IRQSAFE		0x00200000
#define TIMER_ARRAYSHIFT	22
#define TIMER_ARRAYMASK		0xFFC00000

/**
 * timer_setup - prepare a timer for first use
 * @timer: the timer in question
 * @callback: the function to call when timer expires
 * @flags: any TIMER_* flags
 *
 * Regular timer initialization should use either DEFINE_TIMER() above,
 * or timer_setup(). For timers on the stack, timer_setup_on_stack() must
 * be used and must be balanced with a call to destroy_timer_on_stack().
 */
//初始化一个定时器
#define timer_setup(timer, callback, flags)			\
	__init_timer((timer), (callback), (flags))


//通过add_timer()或者mod_timer()会将timer_list放置到一个桶中
int mod_timer(struct timer_list *timer, unsigned long expires)
{
	return __mod_timer(timer, expires, 0);
}

void add_timer(struct timer_list *timer)
{
	BUG_ON(timer_pending(timer));
	mod_timer(timer, timer->expires);
}

static inline int
__mod_timer(struct timer_list *timer, unsigned long expires, unsigned int options)
{
	struct timer_base *base, *new_base;
	unsigned int idx = UINT_MAX;
	unsigned long clk = 0, flags;
	int ret = 0;

	BUG_ON(!timer->function);

	/*
	 * This is a common optimization triggered by the networking code - if
	 * the timer is re-modified to have the same timeout or ends up in the
	 * same array bucket then just return:
	 */
	if (timer_pending(timer)) {
		/*
		 * The downside of this optimization is that it can result in
		 * larger granularity than you would get from adding a new
		 * timer with this expiry.
		 */
		long diff = timer->expires - expires;

		if (!diff)
			return 1;
		if (options & MOD_TIMER_REDUCE && diff <= 0)
			return 1;

		/*
		 * We lock timer base and calculate the bucket index right
		 * here. If the timer ends up in the same bucket, then we
		 * just update the expiry time and avoid the whole
		 * dequeue/enqueue dance.
		 */
		base = lock_timer_base(timer, &flags);
		forward_timer_base(base);

		if (timer_pending(timer) && (options & MOD_TIMER_REDUCE) &&
		    time_before_eq(timer->expires, expires)) {
			ret = 1;
			goto out_unlock;
		}

		clk = base->clk;
        //根据过期时间jiffies计算把当前timer_list放到哪个桶中
		idx = calc_wheel_index(expires, clk);

		/*
		 * Retrieve and compare the array index of the pending
		 * timer. If it matches set the expiry to the new value so a
		 * subsequent call will exit in the expires check above.
		 */
		if (idx == timer_get_idx(timer)) {
			if (!(options & MOD_TIMER_REDUCE))
				timer->expires = expires;
			else if (time_after(timer->expires, expires))
				timer->expires = expires;
			ret = 1;
			goto out_unlock;
		}
	} else {
		base = lock_timer_base(timer, &flags);
		forward_timer_base(base);
	}

	ret = detach_if_pending(timer, base, false);
	if (!ret && (options & MOD_TIMER_PENDING_ONLY))
		goto out_unlock;

	new_base = get_target_base(base, timer->flags);

	if (base != new_base) {
		/*
		 * We are trying to schedule the timer on the new base.
		 * However we can't change timer's base while it is running,
		 * otherwise del_timer_sync() can't detect that the timer's
		 * handler yet has not finished. This also guarantees that the
		 * timer is serialized wrt itself.
		 */
		if (likely(base->running_timer != timer)) {
			/* See the comment in lock_timer_base() */
			timer->flags |= TIMER_MIGRATING;

			raw_spin_unlock(&base->lock);
			base = new_base;
			raw_spin_lock(&base->lock);
			WRITE_ONCE(timer->flags,
				   (timer->flags & ~TIMER_BASEMASK) | base->cpu);
			forward_timer_base(base);
		}
	}

	debug_timer_activate(timer);

	timer->expires = expires;
	/*
	 * If 'idx' was calculated above and the base time did not advance
	 * between calculating 'idx' and possibly switching the base, only
	 * enqueue_timer() and trigger_dyntick_cpu() is required. Otherwise
	 * we need to (re)calculate the wheel index via
	 * internal_add_timer().
	 */
	if (idx != UINT_MAX && clk == base->clk) {
		enqueue_timer(base, timer, idx);
		trigger_dyntick_cpu(base, timer);
	} else {
		internal_add_timer(base, timer);
	}

out_unlock:
	raw_spin_unlock_irqrestore(&base->lock, flags);

	return ret;
}

//根据过期时间jiffies计算把当前timer_list放到哪个桶中
static int calc_wheel_index(unsigned long expires, unsigned long clk)
{
	unsigned long delta = expires - clk;
	unsigned int idx;

    //根据过期时间和每一级的时间范围 来确定放到哪个级别上
	if (delta < LVL_START(1)) {
		idx = calc_index(expires, 0);
	} else if (delta < LVL_START(2)) {
		idx = calc_index(expires, 1);
	} else if (delta < LVL_START(3)) {
		idx = calc_index(expires, 2);
	} else if (delta < LVL_START(4)) {
		idx = calc_index(expires, 3);
	} else if (delta < LVL_START(5)) {
		idx = calc_index(expires, 4);
	} else if (delta < LVL_START(6)) {
		idx = calc_index(expires, 5);
	} else if (delta < LVL_START(7)) {
		idx = calc_index(expires, 6);
	} else if (LVL_DEPTH > 8 && delta < LVL_START(8)) {
		idx = calc_index(expires, 7);
	} else if ((long) delta < 0) {
		idx = clk & LVL_MASK;
	} else {
		/*
		 * Force expire obscene large timeouts to expire at the
		 * capacity limit of the wheel.
		 */
		if (expires >= WHEEL_TIMEOUT_CUTOFF)
			expires = WHEEL_TIMEOUT_MAX;

		idx = calc_index(expires, LVL_DEPTH - 1);
	}
	return idx;
}


/*
 * Helper function to calculate the array index for a given expiry
 * time.
 */
//放到当前级别的哪个桶中的下一个桶 因为每个桶也是一个时间范围 事件只能在时间到了以后才能被触发
static inline unsigned calc_index(unsigned expires, unsigned lvl)
{
	expires = (expires + LVL_GRAN(lvl)) >> LVL_SHIFT(lvl);
	return LVL_OFFS(lvl) + (expires & LVL_MASK);
}
```

```c
//kernel/time/timer.c

/**
start_kernel()
	init_timers()
**/


void __init init_timers(void)
{
    init_timer_cpus();
    //注册TIMER_SOFTIRQ事件
    open_softirq(TIMER_SOFTIRQ, run_timer_softirq);
}


/*
 * This function runs timers and the timer-tq in bottom half context.
 */
static __latent_entropy void run_timer_softirq(struct softirq_action *h)
{
    //获得本cpu下的两个timer_bases
	struct timer_base *base = this_cpu_ptr(&timer_bases[BASE_STD]);

    //处理timer
	__run_timers(base);
	if (IS_ENABLED(CONFIG_NO_HZ_COMMON))
		__run_timers(this_cpu_ptr(&timer_bases[BASE_DEF]));
}



static inline void __run_timers(struct timer_base *base)
{
	struct hlist_head heads[LVL_DEPTH];
	int levels;

	if (!time_after_eq(jiffies, base->clk))
		return;

	timer_base_lock_expiry(base);
	raw_spin_lock_irq(&base->lock);

	/*
	 * timer_base::must_forward_clk must be cleared before running
	 * timers so that any timer functions that call mod_timer() will
	 * not try to forward the base. Idle tracking / clock forwarding
	 * logic is only used with BASE_STD timers.
	 *
	 * The must_forward_clk flag is cleared unconditionally also for
	 * the deferrable base. The deferrable base is not affected by idle
	 * tracking and never forwarded, so clearing the flag is a NOOP.
	 *
	 * The fact that the deferrable base is never forwarded can cause
	 * large variations in granularity for deferrable timers, but they
	 * can be deferred for long periods due to idle anyway.
	 */
	base->must_forward_clk = false;

    //如果当前时间晚于或等于timer_base的clk值
    //一直循环 直到clk到达当前jiffies 一个jiffy一个jiffy的过
	while (time_after_eq(jiffies, base->clk)) {
        //每过一个jiffy 收集一下过期的定时器
		//收集所有已经到期的定时器
		levels = collect_expired_timers(base, heads);
		//增加一个jiffy
         base->clk++;

        //按级从高到低处理所有到期定时器
		while (levels--)
			expire_timers(base, heads + levels);
	}
	raw_spin_unlock_irq(&base->lock);
	timer_base_unlock_expiry(base);
}

//时间轮不是定时器在滚动，而是到期的位置在不停的移动。定时器的位置在添加的一刹那，根据到期时间距离当前时间的间隔，以及到期时间对应相应级的6位固定好了，而且一旦固定下来就不会移动了。
//每当Tick到来，都会更新timer_base的clk值，计算所指向桶的位置，然后通过pending_map判断桶里面是不是存在定时器，如果有的话那它们一定已经到期甚至是超时了。同时，只有在相应时刻（粒度对应的3位全为0时）才会检查下一级。
static int __collect_expired_timers(struct timer_base *base,
				    struct hlist_head *heads)
{
	unsigned long clk = base->clk;
	struct hlist_head *vec;
	int i, levels = 0;
	unsigned int idx;

	for (i = 0; i < LVL_DEPTH; i++) { //按级别从低到高循环
		//找到对应clk值在指定级下面的桶下
		idx = (clk & LVL_MASK) + i * LVL_SIZE;
		//看对应的桶下面有没有定时器
		if (__test_and_clear_bit(idx, base->pending_map)) { 
			vec = base->vectors + idx; //获得对应桶链表
			hlist_move_list(vec, heads++); //将桶内所有定时器链表切换到heads参数里
			levels++;
		}
		/* Is it time to look at the next level? */
		//如果还没到下一个级的检查周期则跳出循环
		if (clk & LVL_CLK_MASK)
			break;
		//对clk移位切换下一级
		/* Shift clock for the next level granularity */
		clk >>= LVL_CLK_SHIFT;
	}
	return levels;
}

```











## 进程管理



### 进程

进程是系统进行**资源分配**和**调度**的基本单位。

> 进程包括 `代码段(text section)` 和 `数据段(data section)`, 除了代码段和数据段外, 进程一般还包含打开的文件, 要处理的信号和CPU上下文等等.
>
> 进程描述了一个程序执行所需要的内存资源、CPU资源、网络资源等集合以及具体的程序逻辑和数据。



#### 进程状态

进程描述符的state字段用于保存进程的当前状态, 进程的状态有以下几种:

- `TASK_RUNNING (运行)` -- 进程处于可执行状态, 在这个状态下的进程要么正在被CPU执行, 要么在等待执行(CPU被其他进程占用的情况下).
- `TASK_INTERRUPTIBLE (可中断等待)` -- 进程处于等待状态, 其在等待某些条件成立或者接收到某些信号, 进程会被唤醒变为运行状态.
- `TASK_UNINTERRUPTIBLE (不可中断等待)` -- 进程处于等待状态, 其在等待某些条件成立, 进程会被唤醒变为运行状态, 但不能被信号唤醒.
- `TASK_TRACED (被追踪)` -- 进程处于被追踪状态, 例如通过ptrace命令对进程进行调试.
- `TASK_STOPPED (停止)` -- 进程处于停止状态, 进程不能被执行. 一般接收到SIGSTOP, SIGTSTP, SIGTTIN, SIGTTOU信号进程会变成TASK_STOPPED状态.

![](.\images\进程状态.png)

#### 进程优先级

Linux系统的进程分为两大类：实时进程和普通进程

![image-20241030110019328](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20241030110019328.png)



```c
// include/linux/sched/prio.h

#define MAX_NICE        19
#define MIN_NICE        -20
#define NICE_WIDTH      (MAX_NICE - MIN_NICE + 1)

/*
  进程优先级通常在0->139之间，
  实时进程的优先级在0->99之间，数值越大优先级越高
  普通进程的优先级在 100->139之间，数值越小优先级越高
  
 * Priority of a process goes from 0..MAX_PRIO-1, valid RT
 * priority is 0..MAX_RT_PRIO-1, and SCHED_NORMAL/SCHED_BATCH
 * tasks are in the range MAX_RT_PRIO..MAX_PRIO-1. Priority
 * values are inverted: lower p->prio value means higher priority.
 *
 * The MAX_USER_RT_PRIO value allows the actual maximum
 * RT priority to be separate from the value exported to
 * user-space.  This allows kernel threads to set their
 * priority to a value higher than any user task. Note:
 * MAX_RT_PRIO must not be smaller than MAX_USER_RT_PRIO.
 */

#define MAX_USER_RT_PRIO        100
#define MAX_RT_PRIO            MAX_USER_RT_PRIO

#define MAX_PRIO               (MAX_RT_PRIO + NICE_WIDTH)
#define DEFAULT_PRIO           (MAX_RT_PRIO + NICE_WIDTH / 2)
```



#### 进程描述符

````c
// include/linux/sched.h

//进程结构体
struct task_struct {
    
    //进程状态标识
	/* -1 unrunnable, 0 runnable, >0 stopped: */
	volatile long			state;

    //指针 指向内核栈
    	void				*stack;
    
    //pid 全局进程id
    	pid_t				pid;
    //全局线程组id
    	pid_t				tgid;
    
    //进程号，进程组标识符和会话标识符
    struct hlist_node		pid_links[PIDTYPE_MAX];
    
    
    //指向真实的父进程
    	/* Real parent process: */
	struct task_struct __rcu	*real_parent;
    
    
    //指向父进程 
    	/* Recipient of SIGCHLD, wait4() reports: */
	struct task_struct __rcu	*parent;
    
    //指向线程组长
    	struct task_struct		*group_leader;
    
    
    //指向主体和真实客体的证书
    	/* Objective and real subjective task credentials (COW): */
	const struct cred __rcu		*real_cred;

    //指向有效客体的证书
	/* Effective (overridable) subjective task credentials (COW): */
	const struct cred __rcu		*cred;
    
    
    //进程名称
    	/*
	 * executable name, excluding path.
	 *
	 * - normally initialized setup_new_exec()
	 * - access it with [gs]et_task_comm()
	 * - lock it with task_lock()
	 */
	char				comm[TASK_COMM_LEN];
    
    //进程调度策略和优先级
    int				prio; // 动态
	int				static_prio; //进程启动的时候赋值的静态优先级
	int				normal_prio; // 动态
	unsigned int			rt_priority;

    //当前进程所属的调度器类
    const struct sched_class	*sched_class;
    
    unsigned int			policy;
	int				nr_cpus_allowed;
	const cpumask_t			*cpus_ptr; //此成员允许进程在哪个cpu上运行
	cpumask_t			cpus_mask;
    
    
	//内存描述符，这两个指针指向内存描述符。
    //进程：mm和active_mm指向同一个内存描述符。
    //内核线程：mm是空指针，当内核线程运行时，active_mm指向从进程借用内存描述符
	struct mm_struct		*mm; 
	struct mm_struct		*active_mm;

    //此成员文件系统信息，主要是进程的根目录和当前工作目录
    /* Filesystem information: */
	struct fs_struct		*fs;
    
    //打开的文件列表
    /* Open file information: */
	struct files_struct		*files;

    //命名空间
	/* Namespaces: */
	struct nsproxy			*nsproxy;

    //信号处理
	/* Signal handlers: */
	struct signal_struct		*signal;
	struct sighand_struct __rcu		*sighand;
	sigset_t			blocked;
	sigset_t			real_blocked;
	/* Restored if set_restore_sigmask() was used: */
	sigset_t			saved_sigmask;
	struct sigpending		pending;

    //下面这两个是用于unix系统的信号量和共享内存
    #ifdef CONFIG_SYSVIPC
	struct sysv_sem			sysvsem;
	struct sysv_shm			sysvshm;
	#endif
    
}


````



#### 进程创建

在Linux系统中，进程的创建使用fork()系统调用，fork()调用会创建一个与父进程一样的子进程，唯一不同就是fork()的返回值，父进程返回的是子进程的进程ID，而子进程返回的是0。



```c
// kernel/fork.c

//fork()
//创建子进程时使用了`写时复制（Copy On Write）`，也就是创建子进程时使用的是父进程的内存空间，当子进程或者父进程修改数据时才会复制相应的内存页。

#ifdef __ARCH_WANT_SYS_FORK
SYSCALL_DEFINE0(fork)
{
#ifdef CONFIG_MMU
    struct kernel_clone_args args = {
       .exit_signal = SIGCHLD,
    };

    return _do_fork(&args);
#else
    /* can not support in nommu mode */
    return -EINVAL;
#endif
}
#endif

//vfork()
//vfork() 也创建一个新进程，但与 fork() 不同的是，vfork() 不会复制父进程的内存空间。
//子进程和父进程共享相同的内存空间，直到子进程调用 exec 系列函数或退出。
    
#ifdef __ARCH_WANT_SYS_VFORK
SYSCALL_DEFINE0(vfork)
{
    struct kernel_clone_args args = {
       .flags    = CLONE_VFORK | CLONE_VM,
       .exit_signal   = SIGCHLD,
    };

    return _do_fork(&args);
}
#endif
```





#### 进程退出

进程主动终止：从main()函数返回，链接程序会自动添加到exit()系统调用；主动调用exit()系统函数；

进程被动终止：进程收到一个自己不能处理的信号；进程收到SIGKILL等终止信息；

````c
//kernel/exit.c


SYSCALL_DEFINE1(exit, int, error_code)
{
	do_exit((error_code&0xff)<<8);
}
````



#### 内核线程

Linux内核有很多任务需要去做, 例如定时把缓冲中的数据刷到硬盘,周期性的将修改的内存页与页来源块设备同步（例如 mmap的文件映射）, 当内存不足的时候进行内存的回收等, 这些工作都需要通过内核线程来完成. 内核线程与普通进程的主要区别就是: 内核线程没有自己的 `虚拟空间结构(struct mm)`, 每次内核线程执行的时候都是借助当前运行进程的虚拟内存空间结构来运行, 因为内核线程只会运行在内核态, 而每个进程的内核态空间都是一样的, 所以借助其他进程的虚拟内存空间结构来运行是完全可行的.

```c
/*
 * Create a kernel thread.
 */
pid_t kernel_thread(int (*fn)(void *), void *arg, unsigned long flags)
{
	struct kernel_clone_args args = {
		.flags		= ((lower_32_bits(flags) | CLONE_VM |
				    CLONE_UNTRACED) & ~CSIGNAL),
		.exit_signal	= (lower_32_bits(flags) & CSIGNAL),
		.stack		= (unsigned long)fn,
		.stack_size	= (unsigned long)arg,
	};

	return _do_fork(&args);
}
```









### 进程调度

#### 调度器

内核中用于安排进程执行的模块叫做调度器，调度器可以切换进程状态，如：执行，可中断睡眠，不可中断睡眠，退出，暂停等

抢占式调度器可以在一个进程正在运行时中断它，将 CPU 控制权交给另一个更高优先级的进程。抢占式调度器可以提升CPU的使用率。

<img src=".\images\调度器.png" alt="image-20241030142451526" style="zoom: 80%;" />

<img src=".\images\调度器02.png" alt="image-20241031145653532" style="zoom: 50%;" />

#### 主调度器

通过调用schedule()函数来完成进程的选择和切换







#### 周期性调度器

根据频率自动调用schedule_tick()函数，作用根据进程运行时间触发调度







#### 调度器类



```c
// kernel/sched/sched.h

//调度器类结构体

struct sched_class {
    // 链表 按照调度优先级排成链表
        const struct sched_class *next;

#ifdef CONFIG_UCLAMP_TASK
        int uclamp_enabled;
#endif

    	// 将进程加入到执行队列中，将进程task_struct加入到红黑树中，rq.nr_running + 1
        void (*enqueue_task) (struct rq *rq, struct task_struct *p, int flags);
    	// 将进程从执行队列中删除，nr_running - 1
        void (*dequeue_task) (struct rq *rq, struct task_struct *p, int flags);
    
    	//放弃CPU执行权，实际上该函数先出队后入队，调度实体放在红黑树的最右端
        void (*yield_task)   (struct rq *rq);
        bool (*yield_to_task)(struct rq *rq, struct task_struct *p, bool preempt);

    	//检查当前进程是否可以被新进程抢占
        void (*check_preempt_curr)(struct rq *rq, struct task_struct *p, int flags);

    	//选取下一个要运行的进程
        struct task_struct *(*pick_next_task)(struct rq *rq);

   		//将进程放到运行队列中去
        void (*put_prev_task)(struct rq *rq, struct task_struct *p);
        void (*set_next_task)(struct rq *rq, struct task_struct *p, bool first);

#ifdef CONFIG_SMP
        int (*balance)(struct rq *rq, struct task_struct *prev, struct rq_flags *rf);
        
    	//为进程选择一个合适的CPU
        int  (*select_task_rq)(struct task_struct *p, int task_cpu, int sd_flag, int flags);
        
    	//迁移进程到另一个CPU中
    	void (*migrate_task_rq)(struct task_struct *p, int new_cpu);
		
    	//专门用于唤醒进程
        void (*task_woken)(struct rq *this_rq, struct task_struct *task);

    	//修改进程在CPU的亲和力
        void (*set_cpus_allowed)(struct task_struct *p,
                              const struct cpumask *newmask);
		//启动/禁止运行队列
        void (*rq_online)(struct rq *rq);
        void (*rq_offline)(struct rq *rq);
#endif

        void (*task_tick)(struct rq *rq, struct task_struct *p, int queued);
        void (*task_fork)(struct task_struct *p);
        void (*task_dead)(struct task_struct *p);

        /*
         * The switched_from() call is allowed to drop rq->lock, therefore we
         * cannot assume the switched_from/switched_to pair is serliazed by
         * rq->lock. They are however serialized by p->pi_lock.
         */
        void (*switched_from)(struct rq *this_rq, struct task_struct *task);
        void (*switched_to)  (struct rq *this_rq, struct task_struct *task);
        void (*prio_changed) (struct rq *this_rq, struct task_struct *task,
                            int oldprio);

        unsigned int (*get_rr_interval)(struct rq *rq,
                                    struct task_struct *task);

        void (*update_curr)(struct rq *rq);

#define TASK_SET_GROUP         0
#define TASK_MOVE_GROUP        1

#ifdef CONFIG_FAIR_GROUP_SCHED
        void (*task_change_group)(struct task_struct *p, int type);
#endif
};








//调度器类的优先级从高到低
extern const struct sched_class stop_sched_class; //停机调度器类
extern const struct sched_class dl_sched_class; //限期调度器类
extern const struct sched_class rt_sched_class; //实时调度器类
extern const struct sched_class fair_sched_class; //公平调度器类
extern const struct sched_class idle_sched_class; //空闲调度器类


```



- 停机调度器类

  优先级是最高的调度器类，停机进程是优先级最高的进程，可以抢占所有其他进程，其他进程不能抢占停机进程。

- 限期调度器类

  最早使用优先算法，使用红黑树把进程按照绝对截止期限从小到大排列，每次调度时选择绝对截止期限最小的进程。

- 实时调度器类

  为每个调度优先级维护一个队列

- 公平调度器类

  使用完全公平调度算法。完全公平调度算法引入虚拟运行时间的相关概念：虚拟运行时间=实际运行实际*nice0对应的权重/进程的权重

- 空闲调度器类

  每个CPU上都有一个空闲线程，即0号线程。空闲调度器类优先级别最低，仅当没有其他线程可以调度的时候才调度空闲线程



**runqueue**

```c
//kernel/sched/sched.h


/*
 * This is the main, per-CPU runqueue data structure.
 *
 * Locking rule: those places that want to lock multiple runqueues
 * (such as the load balancing or the thread migration code), lock
 * acquire operations must be ordered by ascending &runqueue.
 */
struct rq {
    /* runqueue lock: */
    raw_spinlock_t    lock;

    /*
     * nr_running and cpu_load should be in the same cacheline because
     * remote CPUs use both these fields when doing load calculation.
     */
    unsigned int      nr_running;
#ifdef CONFIG_NUMA_BALANCING
    unsigned int      nr_numa_running;
    unsigned int      nr_preferred_running;
    unsigned int      numa_migrate_on;
#endif
#ifdef CONFIG_NO_HZ_COMMON
#ifdef CONFIG_SMP
    unsigned long     last_load_update_tick;
    unsigned long     last_blocked_load_update_tick;
    unsigned int      has_blocked_load;
#endif /* CONFIG_SMP */
    unsigned int      nohz_tick_stopped;
    atomic_t nohz_flags;
#endif /* CONFIG_NO_HZ_COMMON */

    unsigned long     nr_load_updates;
    u64          nr_switches;

#ifdef CONFIG_UCLAMP_TASK
    /* Utilization clamp values based on CPU's RUNNABLE tasks */
    struct uclamp_rq   uclamp[UCLAMP_CNT] ____cacheline_aligned;
    unsigned int      uclamp_flags;
#define UCLAMP_FLAG_IDLE 0x01
#endif

    struct cfs_rq     cfs;
    struct rt_rq      rt;
    struct dl_rq      dl;

#ifdef CONFIG_FAIR_GROUP_SCHED
    /* list of leaf cfs_rq on this CPU: */
    struct list_head   leaf_cfs_rq_list;
    struct list_head   *tmp_alone_branch;
#endif /* CONFIG_FAIR_GROUP_SCHED */

    /*
     * This is part of a global counter where only the total sum
     * over all CPUs matters. A task can increase this counter on
     * one CPU and if it got migrated afterwards it may decrease
     * it on another CPU. Always updated under the runqueue lock:
     */
    unsigned long     nr_uninterruptible;

    struct task_struct __rcu   *curr;
    struct task_struct *idle;
    struct task_struct *stop;
    unsigned long     next_balance;
    struct mm_struct   *prev_mm;

    unsigned int      clock_update_flags;
    u64          clock;
    /* Ensure that all clocks are in the same cache line */
    u64          clock_task ____cacheline_aligned;
    u64          clock_pelt;
    unsigned long     lost_idle_time;

    atomic_t      nr_iowait;

#ifdef CONFIG_MEMBARRIER
    int membarrier_state;
#endif

#ifdef CONFIG_SMP
    struct root_domain    *rd;
    struct sched_domain __rcu  *sd;

    unsigned long     cpu_capacity;
    unsigned long     cpu_capacity_orig;

    struct callback_head   *balance_callback;

    unsigned char     idle_balance;

    unsigned long     misfit_task_load;

    /* For active balancing */
    int          active_balance;
    int          push_cpu;
    struct cpu_stop_work   active_balance_work;

    /* CPU of this runqueue: */
    int          cpu;
    int          online;

    struct list_head cfs_tasks;

    struct sched_avg   avg_rt;
    struct sched_avg   avg_dl;
#ifdef CONFIG_HAVE_SCHED_AVG_IRQ
    struct sched_avg   avg_irq;
#endif
    u64          idle_stamp;
    u64          avg_idle;

    /* This is used to determine avg_idle's max value */
    u64          max_idle_balance_cost;
#endif

#ifdef CONFIG_IRQ_TIME_ACCOUNTING
    u64          prev_irq_time;
#endif
#ifdef CONFIG_PARAVIRT
    u64          prev_steal_time;
#endif
#ifdef CONFIG_PARAVIRT_TIME_ACCOUNTING
    u64          prev_steal_time_rq;
#endif

    /* calc_load related fields */
    unsigned long     calc_load_update;
    long         calc_load_active;

#ifdef CONFIG_SCHED_HRTICK
#ifdef CONFIG_SMP
    int          hrtick_csd_pending;
    call_single_data_t hrtick_csd;
#endif
    struct hrtimer    hrtick_timer;
#endif

#ifdef CONFIG_SCHEDSTATS
    /* latency stats */
    struct sched_info  rq_sched_info;
    unsigned long long rq_cpu_time;
    /* could above be rq->cfs_rq.exec_clock + rq->rt_rq.rt_runtime ? */

    /* sys_sched_yield() stats */
    unsigned int      yld_count;

    /* schedule() stats */
    unsigned int      sched_count;
    unsigned int      sched_goidle;

    /* try_to_wake_up() stats */
    unsigned int      ttwu_count;
    unsigned int      ttwu_local;
#endif

#ifdef CONFIG_SMP
    struct llist_head  wake_list;
#endif

#ifdef CONFIG_CPU_IDLE
    /* Must be inspected within a rcu lock section */
    struct cpuidle_state   *idle_state;
#endif
};
```







##### 完全公平调度器CFS

完全公平调度器（Completely Fair Scheduler）

在实际运行中必须会有进程优先级高或者进程优先级低，CFS采用进程权重来代表进程的优先级，根据进程权重比例来分配CPU时间。

- 实际运行时间比例 = 当前进程权重/进程总权重
- 虚拟运行时间 = 实际运行时间*NICE_0_LOAD/进程权重

```c
// kernel/sched/fair.c


/*
 * All the scheduling class methods:
 */
const struct sched_class fair_sched_class = {
        .next                = &idle_sched_class,
        .enqueue_task         = enqueue_task_fair,
        .dequeue_task         = dequeue_task_fair,
        .yield_task           = yield_task_fair,
        .yield_to_task        = yield_to_task_fair,

        .check_preempt_curr    = check_preempt_wakeup,

        .pick_next_task               = __pick_next_task_fair,
        .put_prev_task        = put_prev_task_fair,
        .set_next_task          = set_next_task_fair,

#ifdef CONFIG_SMP
        .balance              = balance_fair,
        .select_task_rq               = select_task_rq_fair,
        .migrate_task_rq       = migrate_task_rq_fair,

        .rq_online            = rq_online_fair,
        .rq_offline           = rq_offline_fair,

        .task_dead            = task_dead_fair,
        .set_cpus_allowed      = set_cpus_allowed_common,
#endif

        .task_tick            = task_tick_fair,
        .task_fork            = task_fork_fair,

        .prio_changed         = prio_changed_fair,
        .switched_from        = switched_from_fair,
        .switched_to          = switched_to_fair,

        .get_rr_interval       = get_rr_interval_fair,

        .update_curr          = update_curr_fair,

#ifdef CONFIG_FAIR_GROUP_SCHED
        .task_change_group     = task_change_group_fair,
#endif

#ifdef CONFIG_UCLAMP_TASK
        .uclamp_enabled               = 1,
#endif
};
```



**CFS调度器就绪队列**



```c

// kernel/sched/sched.h


// 用于跟踪就绪队列以及管理就绪态调度实体，并维护一颗按照虚拟运行时间排序的红黑树

/* CFS-related fields in a runqueue */
struct cfs_rq {
    struct load_weight load;
    unsigned long     runnable_weight;
    unsigned int      nr_running;
    unsigned int      h_nr_running;      /* SCHED_{NORMAL,BATCH,IDLE} */
    unsigned int      idle_h_nr_running; /* SCHED_IDLE */

    u64          exec_clock;
    u64          min_vruntime;
#ifndef CONFIG_64BIT
    u64          min_vruntime_copy;
#endif

    //红黑树
    struct rb_root_cached  tasks_timeline;

    //队列中的进程节点
    /*
     * 'curr' points to currently running entity on this cfs_rq.
     * It is set to NULL otherwise (i.e when none are currently running).
     */
    struct sched_entity    *curr;
    struct sched_entity    *next;
    struct sched_entity    *last;
    struct sched_entity    *skip;

#ifdef  CONFIG_SCHED_DEBUG
    unsigned int      nr_spread_over;
#endif

#ifdef CONFIG_SMP
    /*
     * CFS load tracking
     */
    struct sched_avg   avg;
#ifndef CONFIG_64BIT
    u64          load_last_update_time_copy;
#endif
    struct {
       raw_spinlock_t lock ____cacheline_aligned;
       int       nr;
       unsigned long  load_avg;
       unsigned long  util_avg;
       unsigned long  runnable_sum;
    } removed;

#ifdef CONFIG_FAIR_GROUP_SCHED
    unsigned long     tg_load_avg_contrib;
    long         propagate;
    long         prop_runnable_sum;

    /*
     *   h_load = weight * f(tg)
     *
     * Where f(tg) is the recursive weight fraction assigned to
     * this group.
     */
    unsigned long     h_load;
    u64          last_h_load_update;
    struct sched_entity    *h_load_next;
#endif /* CONFIG_FAIR_GROUP_SCHED */
#endif /* CONFIG_SMP */

#ifdef CONFIG_FAIR_GROUP_SCHED
    struct rq     *rq;   /* CPU runqueue to which this cfs_rq is attached */

    /*
     * leaf cfs_rqs are those that hold tasks (lowest schedulable entity in
     * a hierarchy). Non-leaf lrqs hold other higher schedulable entities
     * (like users, containers etc.)
     *
     * leaf_cfs_rq_list ties together list of leaf cfs_rq's in a CPU.
     * This list is used during load balance.
     */
    int          on_list;
    struct list_head   leaf_cfs_rq_list;
    struct task_group  *tg;   /* group that "owns" this runqueue */

#ifdef CONFIG_CFS_BANDWIDTH
    int          runtime_enabled;
    s64          runtime_remaining;

    u64          throttled_clock;
    u64          throttled_clock_task;
    u64          throttled_clock_task_time;
    int          throttled;
    int          throttle_count;
    struct list_head   throttled_list;
#endif /* CONFIG_CFS_BANDWIDTH */
#endif /* CONFIG_FAIR_GROUP_SCHED */
};
```







##### 实时调度器

```c
//kernel/sched/rt.c

const struct sched_class rt_sched_class = {
        .next                = &fair_sched_class,
        .enqueue_task         = enqueue_task_rt, //将一个task放进任务队列中
        .dequeue_task         = dequeue_task_rt, //将一个task从任务队列中取出
        .yield_task           = yield_task_rt, //主动放弃执行

        .check_preempt_curr    = check_preempt_curr_rt,

        .pick_next_task               = pick_next_task_rt, //选择下一个要被执行的任务
        .put_prev_task        = put_prev_task_rt, //当一个任务要被调度出执行
        .set_next_task          = set_next_task_rt,

#ifdef CONFIG_SMP
        .balance              = balance_rt,
        .select_task_rq               = select_task_rq_rt,
        .set_cpus_allowed       = set_cpus_allowed_common,
        .rq_online              = rq_online_rt,
        .rq_offline             = rq_offline_rt,
        .task_woken           = task_woken_rt,
        .switched_from        = switched_from_rt,
#endif

        .task_tick            = task_tick_rt,

        .get_rr_interval       = get_rr_interval_rt,

        .prio_changed         = prio_changed_rt,
        .switched_to          = switched_to_rt,

        .update_curr          = update_curr_rt,

#ifdef CONFIG_UCLAMP_TASK
        .uclamp_enabled               = 1,
#endif
};
```





**实时调度器队列**

```c
//kernel/sched/sched.h


/* Real-Time classes' related field in a runqueue: */
struct rt_rq {
    struct rt_prio_array   active; //优先级队列
    unsigned int      rt_nr_running; //在实时队列中所有活动的任务数量
    unsigned int      rr_nr_running;
#if defined CONFIG_SMP || defined CONFIG_RT_GROUP_SCHED
    struct {
       int       curr; /* highest queued rt task prio */ //当前最高优先级的任务
#ifdef CONFIG_SMP
       int       next; /* next highest */ //下一个要运行的任务
#endif
    } highest_prio;
#endif
#ifdef CONFIG_SMP
    unsigned long     rt_nr_migratory;
    unsigned long     rt_nr_total;
    int          overloaded;
    struct plist_head  pushable_tasks;

#endif /* CONFIG_SMP */
    int          rt_queued;

    int          rt_throttled;
    u64          rt_time;
    u64          rt_runtime;
    /* Nests inside the rq lock: */
    raw_spinlock_t    rt_runtime_lock;

#ifdef CONFIG_RT_GROUP_SCHED
    unsigned long     rt_nr_boosted;

    struct rq     *rq;
    struct task_group  *tg;
#endif
};
```



**实施调度器实体**

```c
//include/linux/sched.h

//实时调度类实体
struct sched_rt_entity {
    struct list_head      run_list; //专门用于加入到优先级队列中
    unsigned long        timeout; //设置的时间超时
    unsigned long        watchdog_stamp; //用于记录jiffies值
    unsigned int         time_slice; //时间片
    unsigned short       on_rq;
    unsigned short       on_list;

    struct sched_rt_entity    *back; //临时从上往下连接实时调度类实体使用
#ifdef CONFIG_RT_GROUP_SCHED
    struct sched_rt_entity    *parent; //指向父实时调度实体
    /* rq on which this entity is (to be) queued: */
    struct rt_rq         *rt_rq; //所属的实时调度器队列
    /* rq "owned" by this entity/group: */
    struct rt_rq         *my_q; //所拥有的实时调度器队列 用于管理子任务
#endif
} __randomize_layout;
```



#### 调度策略

Linux将进程划分为**实时进程**和**普通进程**，普通进程进一步划分为**交互式进程**和**批处理进程**

普通进程

- 交互式进程(interactive process) 

  此类进程经常与用户进行交互，因此需要花费很多时间等待键盘和鼠标操作。当接受了用户的输入后，进程必须很快被唤醒，否则用户会感觉系统反应迟钝shell。

- 批处理进程(batch process)

  文本编辑程序和图形应用程序此类进程不必与用户交互，因此经常在后台运行。因为这样的进程不必很快相应，因此常受到调度程序的怠慢

实时进程

- 实时进程(real-time process)

  数据库搜索引擎以及科学计算这些进程由很强的调度需要，这样的进程绝不会被低优先级的进程阻塞。并且他们的响应时间要尽可能的短



根据进程的不同分类Linux采用不同的调度策略：

实时进程

- SCHED_FIFO 实时进程调度策略，先进先出没有时间片，没有更高优先级的状态下，只有等待主动让出CPU
- SCHED_RR 实时进程调度策略，时间片轮转，进程使用完时间片后加入优先级对应的运行队列中的尾部，把CPU让给同等优先级的其他进程

实时进程的调度策略比较简单，因为实时进程只要求尽可能快的被响应，基于优先级，每个进程根据它重要程度的不同被赋予不同的优先级，调度器在每次调度时， **总选择优先级最高的进程开始执行，低优先级不可能抢占高优先级**，因此FIFO或者Round Robin的调度策略即可满足实时进程调度的需求。

普通进程

- SCHED_NORMAL 普通进程调度策略，使task选择CFS调度器来调度运行。
- SCHED_BATCH 普通进程调度策略，批量处理，使task选择CFS调度器来调度运行
- SCHED_IDLE 普通进程调度策略，使task以最低优先级选择CFS调度器来调度运行
- SCHED_DEADLINE 限期进程调度策略，使task选择Deadline调度器来调度运行

其中stop调度器和Deadline调度器，仅使用于内核，用户没有办法进行选择



```c
// include/uapi/linux/sched.h

//进程调度策略

/*
 * Scheduling policies
 */
#define SCHED_NORMAL           0
#define SCHED_FIFO             1
#define SCHED_RR               2
#define SCHED_BATCH            3
/* SCHED_ISO: reserved but not implemented yet */
#define SCHED_IDLE             5
#define SCHED_DEADLINE         6

/* Can be ORed in to make sure the process is reverted back to SCHED_NORMAL on fork */
#define SCHED_RESET_ON_FORK     0x40000000
```







#### CPU状态

```c
// include/linux/cpumask.h

/*
 * The following particular system cpumasks and operations manage
 * possible, present, active and online cpus.
 *
 *     cpu_possible_mask- has bit 'cpu' set iff cpu is populatable
 *     cpu_present_mask - has bit 'cpu' set iff cpu is populated
 *     cpu_online_mask  - has bit 'cpu' set iff cpu available to scheduler
 *     cpu_active_mask  - has bit 'cpu' set iff cpu available to migration
 *
 *  If !CONFIG_HOTPLUG_CPU, present == possible, and active == online.
 *
 *  The cpu_possible_mask is fixed at boot time, as the set of CPU id's
 *  that it is possible might ever be plugged in at anytime during the
 *  life of that system boot.  The cpu_present_mask is dynamic(*),
 *  representing which CPUs are currently plugged in.  And
 *  cpu_online_mask is the dynamic subset of cpu_present_mask,
 *  indicating those CPUs available for scheduling.
 *
 *  If HOTPLUG is enabled, then cpu_possible_mask is forced to have
 *  all NR_CPUS bits set, otherwise it is just the set of CPUs that
 *  ACPI reports present at boot.
 *
 *  If HOTPLUG is enabled, then cpu_present_mask varies dynamically,
 *  depending on what ACPI reports as currently plugged in, otherwise
 *  cpu_present_mask is just a copy of cpu_possible_mask.
 *
 *  (*) Well, cpu_present_mask is dynamic in the hotplug case.  If not
 *      hotplug, it's a copy of cpu_possible_mask, hence fixed at boot.
 *
 * Subtleties:
 * 1) UP arch's (NR_CPUS == 1, CONFIG_SMP not defined) hardcode
 *    assumption that their single CPU is online.  The UP
 *    cpu_{online,possible,present}_masks are placebos.  Changing them
 *    will have no useful affect on the following num_*_cpus()
 *    and cpu_*() macros in the UP case.  This ugliness is a UP
 *    optimization - don't waste any instructions or memory references
 *    asking if you're online or how many CPUs there are if there is
 *    only one CPU.
 */

//系统中可能可用CPU的位图。这是用来为per_cpu变量分配一些启动时的内存，这些变量 不会随着CPU的可用或移除而增加/减少。一旦在启动时的发现阶段被设置，该映射就是静态 的，也就是说，任何时候都不会增加或删除任何位。根据你的系统需求提前准确地调整它 可以节省一些启动时的内存。
extern struct cpumask __cpu_possible_mask;
//当前在线的所有CPU的位图。
extern struct cpumask __cpu_online_mask;
//系统中当前存在的CPU的位图。它们并非全部在线。
extern struct cpumask __cpu_present_mask;
extern struct cpumask __cpu_active_mask;
#define cpu_possible_mask ((const struct cpumask *)&__cpu_possible_mask)
#define cpu_online_mask   ((const struct cpumask *)&__cpu_online_mask)
#define cpu_present_mask  ((const struct cpumask *)&__cpu_present_mask)
#define cpu_active_mask   ((const struct cpumask *)&__cpu_active_mask)
```



```shell
#文件 offline 、 online 、possible 、present 代表CPU掩码。

$ ls -lh /sys/devices/system/cpu
total 0
drwxr-xr-x  9 root root    0 Dec 21 16:33 cpu0
drwxr-xr-x  9 root root    0 Dec 21 16:33 cpu1
drwxr-xr-x  9 root root    0 Dec 21 16:33 cpu2
drwxr-xr-x  9 root root    0 Dec 21 16:33 cpu3
drwxr-xr-x  9 root root    0 Dec 21 16:33 cpu4
drwxr-xr-x  9 root root    0 Dec 21 16:33 cpu5
drwxr-xr-x  9 root root    0 Dec 21 16:33 cpu6
drwxr-xr-x  9 root root    0 Dec 21 16:33 cpu7
drwxr-xr-x  2 root root    0 Dec 21 16:33 hotplug
-r--r--r--  1 root root 4.0K Dec 21 16:33 offline
-r--r--r--  1 root root 4.0K Dec 21 16:33 online
-r--r--r--  1 root root 4.0K Dec 21 16:33 possible
-r--r--r--  1 root root 4.0K Dec 21 16:33 present

[root@localhost cpu]# cat possible 
0-14  #一共15个cpu位置
[root@localhost cpu]# cat online 
0-3 #当前0-3位置上的cpu在线
[root@localhost cpu]# cat present 
0-3 #当前0-3位置上的cpu可能在线
[root@localhost cpu]# cat offline 
4-14 #4-13位置上的cpu离线

##每个CPU文件 夹包含一个 online 文件，控制逻辑上的开（1）和关（0）状态。

[root@localhost cpu]# ll cpu3
总用量 0
drwxr-xr-x. 6 root root    0 3月  12 2024 cache
-r--------. 1 root root 4096 3月  12 2024 crash_notes
-r--------. 1 root root 4096 3月  12 2024 crash_notes_size
lrwxrwxrwx. 1 root root    0 11月  4 16:20 driver -> ../../../../bus/cpu/drivers/processor
lrwxrwxrwx. 1 root root    0 11月  4 16:20 firmware_node -> ../../../LNXSYSTM:00/device:00/LNXCPU:03
lrwxrwxrwx. 1 root root    0 11月  4 16:20 node0 -> ../../node/node0
-rw-r--r--. 1 root root 4096 3月  12 2024 online
drwxr-xr-x. 2 root root    0 11月  4 16:20 power
lrwxrwxrwx. 1 root root    0 11月  4 16:20 subsystem -> ../../../../bus/cpu
drwxr-xr-x. 2 root root    0 3月  12 2024 topology
-rw-r--r--. 1 root root 4096 3月  12 2024 uevent

#要在逻辑上关闭CPU3:
$ echo 0 > /sys/devices/system/cpu/cpu3/online
 smpboot: CPU 3 is now offline

#要让CPU4重新上线:
$ echo 1 > /sys/devices/system/cpu/cpu4/online
smpboot: Booting Node 0 Processor 4 APIC 0x1

#CPU热拔插
```



#### 调度算法

##### 基于时间片轮询调度算法





##### O(1)调度算法











##### 完全公平调度算法



## 内存管理

### 内存模型

![img](.\images\内存模型.png)

<img src=".\images\进程内存布局.png" alt="image-20241115095644533" style="zoom: 50%;" />

**堆**

程序运行的时候，操作系统会给它分配一段内存，用来储存程序和运行产生的数据。这段内存有起始地址和结束地址，比如从`0x1000`到`0x8000`，起始地址是较小的那个地址，结束地址是较大的那个地址。程序运行过程中，对于动态的内存占用请求（比如新建对象，或者使用`malloc`命令），系统就会从预先分配好的那段内存之中，划出一部分给用户，具体规则是从起始地址开始划分（实际上，起始地址会有一段静态数据，这里忽略）。举例来说，用户要求得到10个字节内存，那么从起始地址`0x1000`开始给他分配，一直分配到地址`0x100A`，如果再要求得到22个字节，那么就分配到`0x1020`。

这种因为用户主动请求而划分出来的内存区域，叫做 Heap（堆）。它由起始地址开始，从低位（地址）向高位（地址）增长。Heap 的一个重要特点就是不会自动消失，必须手动释放，或者由垃圾回收机制来回收。

**栈**

Stack 是由于函数运行而临时占用的内存区域。函数执行结束后，该帧就会被回收，释放所有的内部变量，不再占用空间。

Stack 是由内存区域的结束地址开始，从高位（地址）向低位（地址）分配。比如，内存区域的结束地址是`0x8000`，第一帧假定是16字节，那么下一次分配的地址就会从`0x7FF0`开始；第二帧假定需要64字节，那么地址就会移动到`0x7FB0`。







```c
#include <stdio.h>

int global_initialized = 10;  // 存放在 .data 节
int global_uninitialized;     // 存放在 .bss 节

const char *const str = "Hello, World!";  // 字符串常量存放在 .rodata 节

void func() {
    static int static_var = 20;  // 存放在 .data 节
    int local_var = 30;          // 存放在栈中
}

int main() {
    int main_var = 50;           // 存放在栈中
    func();
    return 0;
}
```





#### 进程与线程



<img src=".\images\进程与线程内存结构图.png" alt="image-20241031160316674" style="zoom: 50%;" />







## 文件系统





## 网络





## 计算机组成原理

### 硬盘

> 硬盘中一般会有多个盘片组成，每个盘片包含两个面，每个盘面都对应地有一个读/写磁头。受到硬盘整体体积和生产成本的限制，盘片数量都受到限制，一般都在5片以内。盘片的编号自下向上从0开始，如最下边的盘片有0面和1面，再上一个盘片就编号为2面和3面。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\硬盘01.png" style="zoom: 80%;" />

**扇区和磁道**

> 下图显示的是一个盘面，盘面中一圈圈灰色同心圆为一条条磁道，从圆心向外画直线，可以将磁道划分为若干个弧段，每个磁道上一个弧段被称之为一个扇区（图践绿色部分）。扇区是磁盘的最小组成单元，通常是512字节。（由于不断提高磁盘的大小，部分厂商设定每个扇区的大小是4096字节）
>

![](D:\doc\my\studymd\LearningNotes\os\linux\images\硬盘02.png)



**磁头和柱面**

硬盘通常由重叠的一组盘片构成，每个盘面都被划分为数目相等的磁道，并从外缘的“0”开始编号，具有相同编号的磁道形成一个圆柱，称之为磁盘的柱面。

![](D:\doc\my\studymd\LearningNotes\os\linux\images\硬盘03.png)

**磁盘容量计算**

存储容量 ＝ 磁头数 × 磁道(柱面)数 × 每道扇区数 × 每扇区字节数

图3中磁盘是一个 3个圆盘6个磁头，7个柱面（每个盘片7个磁道） 的磁盘，图3中每条磁道有12个扇区，所以此磁盘的容量为：

存储容量  6 * 7 * 12 * 512 = 258048

>  每个磁道的扇区数一样是说的老的硬盘，外圈的密度小，内圈的密度大，每圈可存储的数据量是一样的。新的硬盘数据的密度都一致，这样磁道的周长越长，扇区就越多，存储的数据量就越大。

**磁盘读取响应时间**

1. 寻道时间：磁头从开始移动到数据所在磁道所需要的时间，寻道时间越短，I/O操作越快，目前磁盘的平均寻道时间一般在3－15ms，一般都在10ms左右。
2. 旋转延迟：盘片旋转将请求数据所在扇区移至读写磁头下方所需要的时间，旋转延迟取决于磁盘转速。普通硬盘一般都是7200rpm，慢的5400rpm。
3. 数据传输时间：完成传输所请求的数据所需要的时间。 小结一下：从上面的指标来看、其实最重要的、或者说、我们最关心的应该只有两个：寻道时间；旋转延迟。

>  读写一次磁盘信息所需的时间可分解为：寻道时间、延迟时间、传输时间。为提高磁盘传输效率，软件应着重考虑减少寻道时间和延迟时间。

**块/簇**

磁盘块/簇（虚拟出来的）。 块是操作系统中最小的逻辑存储单位。操作系统与磁盘打交道的最小单位是磁盘块。 通俗的来讲，在Windows下如NTFS等文件系统中叫做簇；在Linux下如Ext4等文件系统中叫做块（block）。每个簇或者块可以包括2、4、8、16、32、64…2的n次方个扇区。

**为什么存在磁盘块？**

读取方便：由于扇区的数量比较小，数目众多在寻址时比较困难，所以操作系统就将相邻的扇区组合在一起，形成一个块，再对块进行整体的操作。

分离对底层的依赖：操作系统忽略对底层物理存储结构的设计。通过虚拟出来磁盘块的概念，在系统中认为块是最小的单位。

**page**

操作系统经常与内存和硬盘这两种存储设备进行通信，类似于“块”的概念，都需要一种虚拟的基本单位。所以，与内存操作，是虚拟一个页的概念来作为最小单位。与硬盘打交道，就是以块为最小单位。

**扇区、块/簇、page的关系**

1. 扇区： 硬盘的最小读写单元
2. 块/簇： 是操作系统针对硬盘读写的最小单元
3. page： 是内存与操作系统之间操作的最小单元。



### CPU的三种工作模式

#### 实模式

这里的“实”，英文里对应real。real有真实的意思，实际上X86的实模式也代表了两方面的“真实”：运行真实的指令，执行指令真实的功能；访问内存的地址是真实的，对应的就是**物理地址**，不是“虚”的（开启MMU后的虚拟地址）。

实模式下，寄存器都是16位的，以CS为例，实际最大能寻址的范围是CS左移4位，总共20位地址，因此可以访问1MB空间的内存。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\实模式01.jpeg" style="zoom: 50%;" />

**实模式下访问内存**

**获取指令**

对于指令地址，CPU会通过CS和IP寄存器的值组合而来，值为CS所存储的地址左移4位加IP的值：（CS << 4） + IP。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\实模式03.jpeg" style="zoom: 25%;" />

**获取数据**

对于数据地址，CPU会通过DS,ES,SS加上AX,BX,CX,DX,EX,DI,SI,BP,SP寄存器组合而来,DS一般用来存放数据段内容比如C语言全局变量；SS则是栈的基地址，SS搭配SP来使用，一般用来访问C语言临时变量和函数栈信息。地址计算规则和指令地址计算规则类似。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\实模式02.jpeg" style="zoom:25%;" />



#### 保护模式

保护模式下，所有通用寄存器位32位，也可以单独使用低16位，低16位又可以拆分成两个8位寄存器。



<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\保护模式01.png" style="zoom:150%;" />

**保护模式的特权级别**

保护模式下对指令（如in,out,cli）和资源（如寄存器，I/O端口，内存地址）等进行了权限区分。

权限分为4级，R0-R3，每种权限执行的指令数量不同，R0权限最高，可以执行所有指令，R1,R2,R3权限逐级递减。内存访问的权限通过后面要说的段描述符和特权级别配合实现，对于R0来说权限最大，可以访问所有资源。

![](D:\doc\my\studymd\LearningNotes\os\linux\images\保护模式02.png)

**保护模式段描述符**

保护模式下的段寄存器还是16位的，但是常用寄存器已经扩展到了32位，为了扩展段寄存器，引入段选择子和段描述符。

GDTR寄存器中存放GDT表的基地址，查询具体的段描述符时需要段选择子+GDTR基地址

```c
// arch/x86/include/asm/desc.h

//GDT表
struct gdt_page {
    struct desc_struct gdt[GDT_ENTRIES];
} __attribute__((aligned(PAGE_SIZE)));
```

![](D:\doc\my\studymd\LearningNotes\os\linux\images\保护模式03.png)

**段描述符**

```c
//arch/x86/include/asm/desc_defs.h

//段描述符
/* 8 byte segment descriptor */
struct desc_struct {
        u16    limit0;
        u16    base0;
        u16    base1: 8, type: 4, s: 1, dpl: 2, p: 1;
        u16    limit1: 4, avl: 1, l: 1, d: 1, g: 1, base2: 8;
} __attribute__((packed));
```

![](D:\doc\my\studymd\LearningNotes\os\linux\images\保护模式04.png)

| 关键字段 | 说明                                                         |
| -------- | ------------------------------------------------------------ |
| G        | 粒度位，用于解释段界限的含义。当 G 位是“ 0”时，段界限以字节为单位。此时，段的扩展范围是从 1 字节到 1 兆字节（ 1B～1MB），因为描述符中的界限值是 20 位的。相反，如果该位是“ 1”，那么，段界限是以 4KB 为单位的。这样，段的扩展范围是从 4KB到 4GB |
| S        | 指定描述符的类型（ Descriptor Type）。当该位是“ 0”时，表示是一个系统段；为“ 1”时，表示是一个代码段或者数据段（堆栈段也是特殊的数据段）。 |
| DPL      | 描述符的特权级（ Descriptor Privilege Level， DPL）。这两位用于指定段的特权级。共有 4 种处理器支持的特权级别，分别是 0、 1、 2、 3，其中 0 是最高特权级别， 3 是最低特权级别。 |
| P        | 段存在位（ Segment Present）。 P 位用于指示描述符所对应的段是否存在。一般来说，描述符所指的段都在内存里，但在虚拟内存实现中，可能出现内存紧张时将相关空间转移到了外存中，此时P=0。处理器在尝试访问P=0的段时会产生异常。 |
| TYPE     | 描述符的子类型。<br/><br/>对数据段来说，这4个bit为：X,E,W,A<br/><br/>对代码段来说，这4个bit为：X,C,R,A<br/><br/>X代表是否可以执行（ eXecutable）。<br/><br/>E代表扩展方向，E=0 向上（高地址）扩展，比如数据段；E=1向下（低地址）扩展，比如堆栈段。<br/><br/>W代表段是否可写， W=0表示不可写，W=1表示可写。<br/><br/>C代表特权等级是否是一致的（conforming）。C=0表示非一致代码段，这种代码段可以被相同特权级别的代码段所调用，或通过们调用；C=1表示一致代码段，允许低特权级别的程序执行这个代码段。<br/><br/>R代表是否可读，R=0表示不可读，R=1表示可读。<br/><br/>A表示已访问位，如果这个段最近被访问过，则处理器会将其改为1。<br/> |



**段选择子**

 CS,DS,ES,SS,FS,GS这些寄存器，里面的格式如下：

![](D:\doc\my\studymd\LearningNotes\os\linux\images\保护模式05.png)

**CPL、DPL和RPL**

为了访问数据段中的操作数，就必须将该数据段的段选择符装载入数据段寄存器(DS，ES，FS 或 GS) 或者装载入堆栈段寄存器(SS)。(可以用如下指令装载段寄存器，MOV，POP，LDS，LES，LFS，LGS 和 LSS 指令)。

处理器将段选择符装载入段寄存器之前，它要进行特权级检验(见图 )，比较当前 运行的进程或任务的特权级(CPL)，段选择符的 RPL，还有该段的段描述符的 DPL。如果 DPL 在数值上比 CPL 和 RPL 都大或者相等，处理器会将段选择符装载入段寄存器。否则，处理器会产生一个通用保护错误，并且不会装载段寄存器。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\CPU特权校验.png" style="zoom:67%;" />

**CPL**

在 CPU 中运行的是指令，其运行过程中的指令总会属于某个代码段，该代码段的特权级，也就是代码段描述符中的 DPL，便是当前 CPU 所处的特权级，这个特权级称为当前特权级，即 CPL(Current Privilege Level)。

在同一时刻程序中可以有多个段选择子，也就是可以有多个RPL，**然而只有CS寄存器（也就是存放正在执行的代码的寄存器）中的RPL才等于CPL**也就是说当前你的正在运行的代码所在代码段的段描述符中的DPL等于CPL也等于CS.RPL

**RPL**

RPL是通过段选择子的第0和第1位表现出来的。***RPL 引入的目的是避免低特权级的程序访问高特权级的资源***。

当一个特权级为3的程序通过系统调用将CPL升级为0，但是当前CS寄存器段的选择子RPL仍为3，所以在检查权限的时候，通过RPL可以获得程序真实的特权级。



<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\段选择子.png" style="zoom:67%;" />

**DPL**

DPL是段或门的特权级别。它存储在段或门的段或门描述符的DPL字段中。当当前执行的代码段试图访问某个段或门时，将该段或门的DPL与该段或门选择器的CPL和RPL进行比较。

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\段描述符.png" style="zoom:50%;" />



**TSS**

任务状态段TSS（Task-state segment）是一块104字节的**内存**，用于存储大部分寄存器的值。CPU中无进程和线程的概念（这是操作系统的概念），CPU中只有任务概念（任务对应操作系统的线程）。1个CPU核只有一个TR（Task Register）寄存器，存储了当前TSS。TSS的地址存储在TR寄存器中，GDT表中可以存放多个TSS描述符。

TR寄存器也属于段寄存器，里面存放着TSS选择子，指向TSS描述符

- **索引（Index）**：指向GDT或LDT中的TSS描述符条目的偏移。
- **表指示符（TI bit）**：指定是使用GDT还是LDT（0表示GDT，1表示LDT）。
- **请求特权级（RPL，2位）**：定义了访问该TSS所需的最低特权级别。

```c
//arch/x86/kvm/tss.h

struct tss_segment_32 {
	u32 prev_task_link;  // 指向上一个任务的TSS的选择子
	u32 esp0;            // Ring 0时使用的堆栈指针
	u32 ss0;             // Ring 0时使用的堆栈段选择子
	u32 esp1;            // Ring 1时使用的堆栈指针（较少使用）
	u32 ss1;             // Ring 1时使用的堆栈段选择子（较少使用）
	u32 esp2;            // Ring 2时使用的堆栈指针（较少使用）
	u32 ss2;             // Ring 2时使用的堆栈段选择子（较少使用）
	u32 cr3;             // 页表基址寄存器（PDBR），用于分页机制
	u32 eip;             // 下一条指令的地址
	u32 eflags;          // 标志寄存器，包含CPU的状态标志
	u32 eax, ecx, edx, ebx, esp, ebp, esi, edi;  // 通用寄存器
	u32 es, cs, ss, ds, fs, gs;  // 段寄存器
	u32 ldt_selector;    // 局部描述符表选择子
	u16 t;               // 保留字段或特定用途字段
	u16 io_map;          // I/O权限图偏移量
};
```







## 其他知识

### 指令集架构

> **指令集架构**（英语：Instruction Set Architecture，缩写为ISA），又称**指令集**或**指令集体系**，是[计算机体系结构](https://zh.wikipedia.org/wiki/计算机体系结构)中与[程序设计](https://zh.wikipedia.org/wiki/程序設計)有关的部分，包含了[基本数据类型](https://zh.wikipedia.org/wiki/資料型別)，指令集，[寄存器](https://zh.wikipedia.org/wiki/寄存器)，[寻址模式](https://zh.wikipedia.org/wiki/寻址模式)，[存储体系](https://zh.wikipedia.org/w/index.php?title=存储体系&action=edit&redlink=1)，[中断](https://zh.wikipedia.org/wiki/中斷)，[异常处理](https://zh.wikipedia.org/wiki/异常处理)以及外部[I/O](https://zh.wikipedia.org/wiki/I/O)。指令集架构包含一系列的[opcode](https://zh.wikipedia.org/w/index.php?title=Opcode&action=edit&redlink=1)即操作码（[机器语言](https://zh.wikipedia.org/wiki/機器語言)），以及由特定处理器执行的基本命令。
>
> ### 指令集架构的主要组成部分
>
> 1. **指令集**：
>
>    - **指令编码**：定义了每条指令的二进制表示形式。
>
>    - **指令类型**：包括算术逻辑指令、数据传输指令、控制转移指令等。
>
>    - **操作码**：每条指令的唯一标识符，指示处理器执行的具体操作。
>
>    - **操作数**：指令中涉及的数据或地址。
>
>      #### 示例：加法指令 `ADD`
>
>      ```assembly
>      ADD EAX, EBX  ; 将 EBX 的值加到 EAX 上
>      ```
>
>      - **指令编码**：`0x01`（操作码）+ 源操作数 + 目标操作数
>      - **操作码**：`0x01`（ADD 指令的操作码）
>      - **操作数**：源操作数和目标操作数
>
>      
>
> 2. **寄存器**：
>
>    - **通用寄存器**：用于存储临时数据和计算结果。
>    - **专用寄存器**：如程序计数器（PC）、状态寄存器（PSW）等，用于特定的控制功能。
>
> 3. **数据类型**：
>
>    - **整数**：有符号和无符号整数。
>    - **浮点数**：单精度和双精度浮点数。
>    - **向量**：用于 SIMD（Single Instruction Multiple Data）操作。
>
> 4. **寻址模式**：
>
>    - **立即寻址**：操作数直接包含在指令中。
>    - **寄存器寻址**：操作数位于寄存器中。
>    - **直接寻址**：操作数的地址直接包含在指令中。
>    - **间接寻址**：操作数的地址存储在寄存器或内存中。
>    - **基址寻址**：操作数的地址是基址寄存器的值加上偏移量。
>    - **索引寻址**：操作数的地址是基址寄存器的值加上索引寄存器的值。
>
> 5. **异常和中断**：
>
>    - **异常**：处理器在执行过程中遇到的错误或特殊情况，如除零错误、非法指令等。
>    - **中断**：来自外部设备的请求，如定时器中断、I/O 中断等。
>
> 6. **内存模型**：
>
>    - **大端字节序**：高位字节存储在低地址。
>    - **小端字节序**：低位字节存储在低地址。

**寄存器？ 寻址模式？**

汇编语言和指令集的区别，汇编语言是一种开发语言，而指令集则是CPU处理器的指令集合

```assembly
; 例如下面一段汇编代码

section .data
    hello db 'Hello, World!', 0x0A  ; 定义字符串 "Hello, World!"，后面跟一个换行符（0x0A）

section .text
    global _start                   ; 声明入口点

_start:
    ; 写入字符串到标准输出
    ; 这里调用处理器的指令来完成操作
    mov eax, 4                      ; 系统调用号 4 (sys_write)
    mov ebx, 1                      ; 文件描述符 1 (标准输出)
    mov ecx, hello                  ; 字符串的地址
    mov edx, 14                     ; 字符串长度
    int 0x80                        ; 调用内核

    ; 退出程序
    mov eax, 1                      ; 系统调用号 1 (sys_exit)
    xor ebx, ebx                    ; 退出码 0
    int 0x80                        ; 调用内核
```



**指令集架构和操作系统的关系**

- **底层实现**：操作系统的核心功能确实依赖于 CPU 指令集。例如：
  - **进程管理**：通过 `int` 指令触发中断，调用内核进行进程切换。
  - **内存管理**：通过 `mov`、`cmp`、`jmp` 等指令管理内存分配和页面替换。
  - **设备管理**：通过 `in`、`out` 指令与硬件设备进行通信。



### 对称多处理器结构（统一内存访问架构）

**对称多处理**（英语：Symmetric multiprocessing，缩写：**SMP**），在对称多处理器系统中，所有处理器的地位都是平等的，所有CPU共享全部资源，比如内存，总线，中断以及I/O系统等等，都具有相同的可访问性，消除结构上的障碍，最大的特点就是共享所有资源。SMP和UMA是一致的，**均匀访存模型**（英语：Uniform Memory Access，缩写：**UMA**），亦称作**统一寻址技术**或**统一内存存取架构**，指所有的物理存储器被均匀共享，即处理器访问它们的时间是一样的。

<img src=".\images\对称多处理器结构.png" alt="image-20241104144949281" style="zoom:50%;" />

### 非统一内存访问架构

**非统一内存访问架构**（英语：**non-uniform memory access**，简称NUMA）是一种为[多处理器](https://zh.wikipedia.org/wiki/多處理器)的电脑设计的内存架构，内存访问时间取决于内存相对于处理器的位置。在NUMA下，处理器访问它自己的本地内存的速度比非本地内存（内存位于另一个处理器，或者是处理器之间共享的内存）快一些。

非统一内存访问架构的特点是：被共享的内存物理上是分布式的，所有这些内存的集合就是全局[地址空间](https://zh.wikipedia.org/wiki/地址空间)。所以处理器访问这些内存的时间是不一样的，显然访问本地内存的速度要比访问全局共享内存或远程访问外地内存要快些。另外，NUMA中内存可能是分层的：本地内存，群内共享内存，全局共享内存。

<img src=".\images\非统一内存访问架构.png" alt="image-20241104150757479" style="zoom:50%;" />



### CPU处理器分类

**SMT**

**同时多线程**（英语：**Simultaneous multithreading**，缩写**SMT**）也称**同步多线程**，是一种提高具有硬件[多线程](https://zh.wikipedia.org/wiki/多线程)的[超标量](https://zh.wikipedia.org/wiki/超純量)[CPU](https://zh.wikipedia.org/wiki/中央处理器)整体效率的技术。同时多线程允许多个独立的执行[线程](https://zh.wikipedia.org/wiki/线程)更好地利用现代[处理器架构](https://zh.wikipedia.org/wiki/CPU设计)提供的资源。即一个CPU多个核心，使用相同的 CPU 资源 , 共享 L1 Cache 缓存

**MC**

**多核心处理器**（英语：Multi-core processor），又称**多核微处理器**，是在单个计算组件中加入两个或以上的独立实体[中央处理单元](https://zh.wikipedia.org/wiki/中央處理單元)（简称核心，英语：Core）。这些核心可以分别独立地执行程序指令，利用[并行计算](https://zh.wikipedia.org/wiki/平行計算)的能力加快程序的执行速度。即多个CPU

**SoC**

**单片系统**或**片上系统**（英语：**System on a Chip**，[缩写](https://zh.wikipedia.org/wiki/縮寫)：**SoC**）是一个将[电脑](https://zh.wikipedia.org/wiki/電腦)或其他[电子](https://zh.wikipedia.org/wiki/电子学)[系统](https://zh.wikipedia.org/wiki/系统)集成到单一芯片的[集成电路](https://zh.wikipedia.org/wiki/集成电路)[[1\]](https://zh.wikipedia.org/wiki/单片系统#cite_note-Atlantic2007-1)。





进程间通讯



文件描述符

每个进程打开同一个文件时，各自进程的文件描述符（fd）通常是不同的。这是因为在操作系统中，文件描述符是进程私有的资源，每个进程都有自己的文件描述符表。即使多个进程打开了同一个文件，它们各自的文件描述符也会被分配不同的值。

```shell
# 正常情况下，默认文件描述符上限为1024，可以通过ulimit命令查看和修改
ulimit -n
1024
```



### 汇编语言







## 疑问

进程的内存结构 ？？

<img src=".\images\linux03.png" alt="image-20241030101218248" style="zoom:50%;" />

Linux中线程和进程？

中断时的用户态内核态切换？ 中断堆栈？

时钟中断如果是1000HZ 那么微秒 纳秒 又是怎么统计的

```java
public static void main(String[] args) {
    for (int i = 0; i < 20 ; i++) {
 		//为什么差值总是100?
        System.out.println(System.nanoTime() - System.nanoTime());
    }
}
```
