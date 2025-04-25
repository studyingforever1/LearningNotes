## Linux命令知识

### iptables

`iptables` 按照从上到下的顺序依次对数据包进行规则匹配。一旦数据包匹配到某条规则，就会立即执行该规则所指定的动作（如 `ACCEPT`、`DROP`、`REJECT` 等），而不会再去检查后续的规则。

```sh
## 展示规则 带行号
iptables -L -n --line-numbers

## 
sudo iptables -L -n -v

-L：列出所有规则。
-n：以数字形式显示 IP 地址和端口号，而不是解析为域名或服务名，这样可以提高查看效率。
-v：显示详细信息，包括数据包计数和字节数。
```

```sh
Chain INPUT (policy ACCEPT)
num  target     prot opt source               destination         
1    ACCEPT     all  --  0.0.0.0/0            0.0.0.0/0            state RELATED,ESTABLISHED
2    ACCEPT     icmp --  0.0.0.0/0            0.0.0.0/0           
3    ACCEPT     all  --  0.0.0.0/0            0.0.0.0/0           
4    ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:22
5    ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:7080
6    ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:3306
7    ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:6379
8    ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:5601
9    ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:4560
10   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:9876
11   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:10909
12   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:10911
13   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:10912
14   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:80
15   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:8081
16   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:8082
17   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:9200
18   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:24729
19   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpt:10050
20   ACCEPT     tcp  --  0.0.0.0/0            0.0.0.0/0            state NEW tcp dpts:9201:9209
21   ACCEPT     udp  --  0.0.0.0/0            0.0.0.0/0            udp dpts:9201:9209
22   REJECT     all  --  0.0.0.0/0            0.0.0.0/0            reject-with icmp-host-prohibited

Chain FORWARD (policy DROP)
num  target     prot opt source               destination         
1    DOCKER-USER  all  --  0.0.0.0/0            0.0.0.0/0           
2    DOCKER-ISOLATION-STAGE-1  all  --  0.0.0.0/0            0.0.0.0/0           
3    ACCEPT     all  --  0.0.0.0/0            0.0.0.0/0            ctstate RELATED,ESTABLISHED
4    DOCKER     all  --  0.0.0.0/0            0.0.0.0/0           
5    ACCEPT     all  --  0.0.0.0/0            0.0.0.0/0           
6    ACCEPT     all  --  0.0.0.0/0            0.0.0.0/0           
7    ACCEPT     all  --  0.0.0.0/0            0.0.0.0/0            ctstate RELATED,ESTABLISHED
8    DOCKER     all  --  0.0.0.0/0            0.0.0.0/0           
9    ACCEPT     all  --  0.0.0.0/0            0.0.0.0/0           
10   ACCEPT     all  --  0.0.0.0/0            0.0.0.0/0           
11   REJECT     all  --  0.0.0.0/0            0.0.0.0/0            reject-with icmp-host-prohibited

Chain OUTPUT (policy ACCEPT)
num  target     prot opt source               destination         

Chain DOCKER (2 references)
num  target     prot opt source               destination         

Chain DOCKER-ISOLATION-STAGE-1 (1 references)
num  target     prot opt source               destination         
1    DOCKER-ISOLATION-STAGE-2  all  --  0.0.0.0/0            0.0.0.0/0           
2    DOCKER-ISOLATION-STAGE-2  all  --  0.0.0.0/0            0.0.0.0/0           
3    RETURN     all  --  0.0.0.0/0            0.0.0.0/0           

Chain DOCKER-ISOLATION-STAGE-2 (2 references)
num  target     prot opt source               destination         
1    DROP       all  --  0.0.0.0/0            0.0.0.0/0           
2    DROP       all  --  0.0.0.0/0            0.0.0.0/0           
3    RETURN     all  --  0.0.0.0/0            0.0.0.0/0           

Chain DOCKER-USER (1 references)
num  target     prot opt source               destination         
1    RETURN     all  --  0.0.0.0/0            0.0.0.0/0        
```





```sh
##插入允许 TCP 协议 9201 - 9209 端口新建入站连接规则 插入到行号20的前面
sudo iptables -I INPUT 20 -p tcp -m state --state NEW --dport 9201:9209 -j ACCEPT

##插入允许 UDP 协议 9201 - 9209 端口新建入站连接规则 插入到行号20的前面
sudo iptables -I INPUT 20 -p udp --dport 9201:9209 -j ACCEPT

##保存规则
sudo systemctl enable iptables
sudo service iptables save

##删除 INPUT 链中行号为 2 的规则
sudo iptables -D INPUT 2
```

