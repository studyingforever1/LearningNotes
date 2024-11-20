# Linux内核

## 系统结构

Linux内核是将计算机硬件资源通过系统调用接口分配调度给用户进程的中间层级

<img src=".\images\linux01.png" alt="image-20241024114750178" style="zoom:50%;" />

体系结构arch封装了对不同体系结构的不同代码，如x86 arm等

<img src=".\images\linux02.png" alt="image-20241028104613935" style="zoom: 67%;" />

### 内核源码目录结构

<img src=".\images\Linux内核源码组织.png" alt="Linux内核源码组织" style="zoom: 67%;" />





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

- `gs` 段寄存器

  `gs` 是 x86 架构中的一个段寄存器，通常用于存储特定于当前 CPU 的数据的基地址。

  ```assembly
  mov ax, gs:[x] /*常用于读取/写入当前CPU专属数据到内核.data段中*/
  ```

- `eflags` 寄存器 标志寄存器



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

### 中断控制器

X86计算机的 CPU 为中断只提供了两条外接引脚：NMI 和 INTR。其中 NMI 是不可屏蔽中断，它通常用于电源掉电和物理存储器奇偶校验；INTR是可屏蔽中断，可以通过设置中断屏蔽位来进行中断屏蔽，它主要用于接受外部硬件的中断信号，这些信号由中断控制器传递给 CPU。

#### 可编程中断控制器8259A

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

Linux 曾经支持嵌套中断，但为了避免堆栈溢出问题的解决方案越来越复杂，Linux 不久前取消了该功能 - 只允许一层嵌套，允许多层嵌套，直至达到一定的内核堆栈深度，等等。

但是，异常（系统调用）和中断（硬件中断）之间仍然可以嵌套，但规则相当严格：

- 异常（例如页面错误、系统调用）不能抢占中断；如果发生这种情况，则被视为错误

- 中断可以抢占异常

- 中断不能抢占另一个中断（以前是可以的）







#### 高级可编程中断控制器（APIC）

8259A 只适合单 CPU 的情况，为了充分挖掘 SMP 体系结构的并行性，能够把中断传递给系统中的每个 CPU 至关重要。基于此理由，Intel 引入了一种名为 I/O 高级可编程控制器的新组件，来替代老式的 8259A 可编程中断控制器。

APIC 分成两部分 LAPIC 和 IOAPIC，前者 LAPIC 位于 CPU 内部，每个 CPU 都有一个 LAPIC，后者 IOAPIC 与外设相连。外设发出的中断信号经过 IOAPIC 处理之后发送某个或多个 LAPIC，再由 LAPIC 决定是否交由 CPU 进行实际的中断处理。

- **LAPIC**，主要负责传递中断信号到指定的处理器；举例来说，一台具有三个处理器的机器，则它必须相对的要有三个本地 APIC。
- **I/O APIC**，主要是收集来自 I/O 装置的 Interrupt 信号且在当那些装置需要中断时发送信号到本地 APIC，系统中最多可拥有 8 个 I/O APIC。

![](.\images\APIC01.png)

##### IOAPIC

IOAPIC 主要负责接收外部的硬件中断，将硬件产生的中断信号翻译成具有一定格式的消息，然后通过总线将消息发送给一个或者多个 LAPIC。

**重定向表项 RTE(Redirection Table Entry)**

了解 IOAPIC 的工作，最重要的就是了解重定向表项/寄存器，每个管脚都对应着一个 64 位的重定向表项，来具体看看这 64 位代表的具体信息：

<img src=".\images\APIC02.png" style="zoom:67%;" />

<img src=".\images\APIC03.png" style="zoom:67%;" />

<img src=".\images\APIC04.png" style="zoom:67%;" />

- Destination Field (目的字段) 和 Destination Mode（目的地模式） 字段决定了该中断发送给哪个或哪些 LAPIC

- Interrupt Vector（中断向量），中断控制器很重要的一项工作就是将中断信号翻译成中断向量，这个中断向量就是 IDT(中断描述符表) 的索引，IDT 里面的中断描述符就存放着中断处理程序的地址。在 PIC 中，vector = 起始vector+IRQ，而在 APIC 模式下，IRQ 对应的 vector 由操作系统对 IOAPIC 初始化的时候设置分配。






##### **LAPIC**

LAPIC 要比 IOAPIC 复杂的多，其主要功能是接收中断消息然后交由 CPU 处理，再者就是自身也能作为中断源产生中断发送给自身或其他 CPU。所以其实 LAPIC 能够收到三个来源的中断：

- 本地中断：时钟，温度监测等

- 外部中断：IOAPIC 发来的
- IPI：处理器间中断，其他 LAPIC 发来的

<img src=".\images\APIC05.png" style="zoom: 80%;" />

**主要寄存器**

- **IRR(Interrupt Request Register)**
  中断请求寄存器，256 位，每位代表着一个中断。当某个中断消息发来时，如果该中断没有被屏蔽，则将 IRR 对应的 bit 置 1，表示收到了该中断请求但 CPU 还未处理。
- **ISR(In Service Register)**
  服务中寄存器，256 位，每位代表着一个中断。当 IRR 中某个中断请求发送个 CPU 时，ISR 对应的 bit 上便置 1，表示 CPU 正在处理该中断。
- **EOI(End of Interrupt)**
  中断结束寄存器，32 位，写 EOI 表示中断处理完成。写 EOI 寄存器会导致 LAPIC 清理 ISR 的对应 bit，对于 level 触发的中断，还会向所有的 IOAPIC 发送 EOI 消息，通告中断处理已经完成。
- **ID**
  用来唯一标识一个 LAPIC，LAPIC 与 CPU 一一对应，所以也用 LAPIC ID 来标识 CPU。
- **TPR(Task Priority Register)**
  任务优先级寄存器，确定当前 CPU 能够处理什么优先级别的中断，CPU 只处理比 TPR 中级别更高的中断。比它低的中断暂时屏蔽掉，也就是在 IRR 中继续等到。另外优先级别=vector/16，vector 为每个中断对应的中断向量号。
- **PPR(Processor Priority Register)**
  处理器优先级寄存器，表示当前正处理的中断的优先级，以此来决定处于 IRR 中的中断是否发送给 CPU。处于 IRR 中的中断只有优先级高于处理器优先级才会被发送给处理器。PPR 的值为 ISR 中正服务的最高优先级中断和 TPR 两者之间选取优先级较大的，所以 TPR 就是靠间接控制 PPR 来实现暂时屏蔽比 TPR 优先级小的中断的。
- **SVR(Spurious Interrupt Vector Register)**
  可以通过设置这个寄存器来使 APIC 工作，原话 To enable the APIC。
- **ICR(Interrupt Command Register)**
  中断指令寄存器，当一个 CPU 想把中断发送给另一个 CPU 时，就在 ICR 中填写相应的中断向量和目标 LAPIC 标识，然后通过总线向目标 LAPIC 发送消息。ICR 寄存器的字段和 IOAPIC 重定向表项较为相似，都有 destination field, delivery mode, destination mode, level 等等。

**本地中断**

LAPIC 本身还能作为中断源产生中断，**LVT(Local Vector Table)** 就是自身作为中断源的一个配置表，总共 7 项(不同架构下可能不同)，每项 32 位，同 IOAPIC，每一项也是一个寄存器，如下所示：

<img src=".\images\APIC06.png" style="zoom:80%;" />

1. **Timer**: 时钟中断
   - **Vector**: 指定中断服务程序的入口点。
   - **Delivery Status**: 表明中断状态，0表示空闲，1表示待发送。
   - **Delivery Mode**: 确定中断传递方式，包括固定、SMI、NMI、EXTINT、INIT等。
   - **Trigger Mode**: 触发模式，0代表边沿触发，1代表电平触发。
   - **Interrupt Input Pin Polarity**: 输入引脚的极性。
   - **Remote IRR**: 远程IRR（In Service Register）。
   - **Mask**: 中断屏蔽标志，0未屏蔽，1已屏蔽。
   - **Timer Mode**: 定时器工作模式，包括单次触发、周期性触发和TSC截止日期模式。
2. **CMCI**: 核心多线程兼容中断。
3. **LINT0/LINT1**: 局部中断输入0/1。
4. **Error**: 错误报告。
5. **Performance Mon. Counters**: 性能监视计数器。
6. **Thermal Sensor**: 温度传感器。



**APIC 中断过程**

- IOAPIC 根据 PRT 表将中断信号翻译成中断消息，然后发送给 destination field 字段列出的 LAPIC

- LAPIC 根据消息中的 destination mode，destination field，自身的寄存器 ID，LDR，DFR 来判断自己是否接收该中断消息，不是则忽略
- 如果该中断是 SMI/NMI/INIT/ExtINT/SIPI，直接送 CPU 执行，因为这些中断都是负责特殊的系统管理任务。否则的话将 IRR 相应的位置 1。
- 如果该中断的优先级高于当前 CPU 正在执行的中断，而且当前 CPU 没有屏蔽中断的话，则中断当前正处理的中断，先处理该高优先级中断，否则等待
- 准备处理下一个中断时，从 IRR 中挑选优先级最大的中断，相应位置 0，ISR 相应位置 1，然后送 CPU 执行。
- 中断处理完成后写 EOI 表示中断处理已经完成，写 EOI 导致 ISR 相应位置 0，对于 level 触发的中断，还会向所有的 I/O APIC 发送 EOI 消息，通知中断处理已经完成。



### 中断描述符表

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
//          native_init_IRQ()
//              idt_setup_apic_and_irq_gates()

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
```



```c
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
        irq_handler_t         thread_fn;
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

//示例 时钟中断
static struct irqaction irq0  = {
	.handler = timer_interrupt, //时钟中断处理程序
	.flags = IRQF_NOBALANCING | IRQF_IRQPOLL | IRQF_TIMER, //标记
	.name = "timer" // 中断处理程序名称
};

```



**常见设备的IRQ编号与中断向量号**

以8259A为例，中断向量号 = IRQ + 32

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

	if (!handler) {
		if (!thread_fn)
			return -EINVAL;
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
	if (new->thread_fn && !nested) {
        //创建硬件irq处理线程
		ret = setup_irq_thread(new, irq, false);
		if (ret)
			goto out_mput;
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





```



**内核线程**

```c
//kernel/irq/manage.c

//创建内核硬件处理线程

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





```c
static struct irqaction irq0  = {
        .handler = timer_interrupt,
        .flags = IRQF_NOBALANCING | IRQF_IRQPOLL | IRQF_TIMER,
        .name = "timer"
};

//时钟中断的注册
static void __init setup_default_timer_irq(void)
{
        /*
         * Unconditionally register the legacy timer; even without legacy
         * PIC/PIT we need this for the HPET0 in legacy replacement mode.
         */
    	//注册到irq_desc中 IRQ线的编号为0
        if (setup_irq(0, &irq0))
               pr_info("Failed to register legacy timer interrupt\n");
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



**jiffies**

`jiffies` 是Linux内核中用于时间管理的一个重要概念。它是一个全局变量，用于记录自系统启动以来发生的时钟滴答（ticks）数量。每个时钟滴答被称为一个“jiffy”，而 `jiffies` 变量就是一个累积的计数器，记录了从系统启动以来的总滴答数。

- **时钟滴答**: 每次时钟中断发生时，`jiffies` 的值会增加1。
- **频率**: 时钟滴答的频率由内核配置参数 `HZ` 决定，表示每秒发生的时钟中断次数。常见的值有100、250、1000等。

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
extern u64 __cacheline_aligned_in_smp jiffies_64;
extern unsigned long volatile __cacheline_aligned_in_smp __jiffy_arch_data jiffies;


```

`grep CONFIG_HZ /boot/config-$(uname -r) ` 查看当前Linux系统的HZ设置

```bash
[root@localhost ~]# grep CONFIG_HZ /boot/config-$(uname -r)
# CONFIG_HZ_PERIODIC is not set
# CONFIG_HZ_100 is not set
# CONFIG_HZ_250 is not set
# CONFIG_HZ_300 is not set
CONFIG_HZ_1000=y
CONFIG_HZ=1000 #每秒钟产生1000次时钟中断
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

**work_struct**

```c
// include/linux/workqueue.h

struct work_struct {
    atomic_long_t data;
    struct list_head entry; //当前work所属的work队列
    work_func_t func;
#ifdef CONFIG_LOCKDEP
    struct lockdep_map lockdep_map;
#endif
};
```







##### 以网卡中断为例的中断全流程

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

<img src="D:\doc\my\studymd\LearningNotes\os\linux\images\进程内存布局.png" alt="image-20241115095644533" style="zoom: 50%;" />

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
