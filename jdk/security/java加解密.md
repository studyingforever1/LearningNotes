# Java加解密

**对称加密**

**对称密钥算法**（英语：**Symmetric-key algorithm**）又称为**对称加密**、**私钥加密**、**共享密钥加密**，是[密码学](https://zh.wikipedia.org/wiki/密碼學)中的一类加密算法。这类算法在加密和解密时使用相同的密钥，或是使用两个可以简单地相互推算的密钥。事实上，这组密钥成为在两个或多个成员间的共同秘密，以便维持专属的通信联系。与[非对称密钥加密](https://zh.wikipedia.org/wiki/公开密钥加密)相比，要求双方获取相同的密钥是对称密钥加密的主要缺点之一。

对称加密的速度比[非对称密钥加密](https://zh.wikipedia.org/wiki/公钥加密)快很多，在很多场合都需要对称加密。

**非对称加密**

**公开密钥密码学**（英语：**Public-key cryptography**）也称**非对称式密码学**（英语：**Asymmetric cryptography**）是[密码学](https://zh.wikipedia.org/wiki/密碼學)的一种[算法](https://zh.wikipedia.org/wiki/演算法)，它需要两个[密钥](https://zh.wikipedia.org/wiki/密钥)，一个是公开密钥，另一个是私有密钥；公钥用作加密，私钥则用作解密。使用公钥把[明文](https://zh.wikipedia.org/wiki/明文)加密后所得的[密文](https://zh.wikipedia.org/wiki/密文)，只能用相对应的[私钥](https://zh.wikipedia.org/wiki/私钥)才能解密并得到原本的明文，最初用来加密的公钥不能用作解密。由于加密和解密需要两个不同的密钥，故被称为非对称加密；不同于加密和解密都使用同一个密钥的[对称加密](https://zh.wikipedia.org/wiki/对称加密)。公钥可以公开，可任意向外发布；私钥不可以公开，必须由用户自行严格秘密保管，绝不透过任何途径向任何人提供，也不会透露给被信任的要通信的另一方。

基于公开密钥加密的特性，它还能提供[数字签名](https://zh.wikipedia.org/wiki/數位簽章)的功能，使电子文件可以得到如同在纸本文件上亲笔签署的效果。

## 常见类

**java.security包**

| 类                                | 作用                        | 示例用途                                          |
| --------------------------------- | --------------------------- | ------------------------------------------------- |
| `Key`                             | 公共接口，表示密钥          | `SecretKey`、`PrivateKey`、`PublicKey` 都实现了它 |
| `PublicKey`                       | 非对称加密公钥              | RSA/SM2 公钥                                      |
| `PrivateKey`                      | 非对称加密私钥              | RSA/SM2 私钥                                      |
| `KeyFactory`                      | 将编码后的密钥生成 Key 对象 | 从 X.509/PKCS8 字节生成 PublicKey/PrivateKey      |
| `KeyPair`                         | 公私钥对                    | RSA、DSA、EC                                      |
| `KeyPairGenerator`                | 生成非对称密钥对            | RSA/DSA/ECC 密钥生成                              |
| `Signature`                       | 数字签名                    | SHA256withRSA、SM3withSM2                         |
| `MessageDigest`                   | 消息摘要                    | MD5、SHA-1、SHA-256、SM3                          |
| `SecureRandom`                    | 安全随机数生成              | 用于生成 IV、密钥、盐值                           |
| `Certificate` / `X509Certificate` | 证书管理                    | 解析和验证证书                                    |

**javax.crypto包**

| 类                 | 作用                 | 示例用途                |
| ------------------ | -------------------- | ----------------------- |
| `Cipher`           | 加解密核心类         | AES、DES、RSA、SM4、SM2 |
| `Mac`              | 消息认证码（HMAC）   | HmacSHA256、HmacSM3     |
| `SecretKey`        | 对称密钥接口         | AES、DES                |
| `SecretKeySpec`    | 将字节数组包装为密钥 | AES、DES                |
| `KeyGenerator`     | 生成对称密钥         | AES/128、DES/3DES       |
| `IvParameterSpec`  | 初始化向量           | AES/CBC 模式            |
| `PBEKeySpec`       | 基于口令的密钥       | PBEWithMD5AndDES        |
| `PBEParameterSpec` | PBE 加密参数         | 盐 + 迭代次数           |

- **对称加密** → `Cipher` + `SecretKey` / `SecretKeySpec` + `KeyGenerator` + `IvParameterSpec`
- **非对称加密** → `Cipher` + `PublicKey` / `PrivateKey` + `KeyFactory` + `KeyPairGenerator`
- **签名与验证** → `Signature` + 非对称密钥
- **消息摘要** → `MessageDigest`
- **消息认证码** → `Mac` + `SecretKey`
- **安全随机数** → `SecureRandom`
- **公钥证书** → `Certificate` / `X509Certificate`



### 算法/模式/填充

| 名称 | 英文      | 作用         | 说明                                                         |
| ---- | --------- | ------------ | ------------------------------------------------------------ |
| 算法 | Algorithm | 加密算法本身 | 比如 AES、DES、RSA、SM2、SM4                                 |
| 模式 | Mode      | 分组加密模式 | 定义如何处理数据块：ECB、CBC、CFB、OFB、GCM 等               |
| 填充 | Padding   | 填充方式     | 当数据长度不是分组长度倍数时，用特定方式补齐：PKCS5Padding、NoPadding、ISO10126Padding 等 |

```JAVA
Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
```

#### 对称加密

**对称加密算法**（AES、DES、SM4）是 **块加密算法（Block Cipher）**

它一次只能处理 **固定长度的数据块**（AES = 16 字节/块）

当数据很长时，需要分块加密，每块单独加密

因此，对称加密常用 **模式（ECB、CBC、GCM）** 来处理多个块

- **ECB**：每块独立加密（不安全）
- **CBC**：每块与前一块密文 XOR（推荐）
- **GCM**：带认证的加密模式（现代推荐）

**填充方式**：

- PKCS5Padding：常用，自动补齐
- NoPadding：原文长度必须是分组倍数



#### 非对称加密

**非对称加密算法**（RSA、SM2、ECC）是 **数学函数直接作用在整数或点上的加密**

它不是按固定块大小处理明文，而是受 **密钥长度限制**

- RSA 2048 位 → 明文最多 245 字节（用 PKCS1 填充）

数据量超过限制时，需要分段加密，但算法本身不是块加密







### 对称加密

- `KeyGenerator`负责生成随机的对称密钥
- `SecretKeySpec`负责接受固定的对称密钥
- `IvParameterSpec`用于生成随机向量
- `Cipher`用于加解密

```java
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AesExample {

    public static void main(String[] args) throws Exception {
        // 1. 生成 AES 对称密钥（128 位）
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        SecretKey secretKey = keyGen.generateKey();

        // 2. 将 SecretKey 转换为 SecretKeySpec
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getEncoded(), "AES");

        // 3. 初始化 IV（CBC 模式需要）
        byte[] ivBytes = new byte[16]; // 这里为了示例使用零数组，实际应随机生成
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        // 4. 待加密数据
        String plaintext = "Hello, AES CBC!";

        // 5. 加密  算法/模式/填充
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);
        System.out.println("加密后(Base64): " + encryptedBase64);

        // 6. 解密
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
        String decryptedText = new String(decryptedBytes, StandardCharsets.UTF_8);
        System.out.println("解密后: " + decryptedText);
    }
}
```







### 非对称加密

- `KeyPairGenerator `负责生成非对称加密的密钥对
- `PublicKey`是公钥
- `PrivateKey`是私钥
- `Cipher`负责非对称加解密
- `KeyFactory`负责从公私密钥字节数组转换成`PublicKey/PrivateKey`

```java
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.crypto.Cipher;
import java.util.Base64;

public class RsaExample {

    public static void main(String[] args) throws Exception {
        // 1. 生成 RSA 密钥对（2048 位）
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        // 2. 待加密数据
        String plaintext = "Hello, RSA!";
        System.out.println("原文: " + plaintext);

        // 3. 使用公钥加密
        Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = encryptCipher.doFinal(plaintext.getBytes());
        String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);
        System.out.println("加密后(Base64): " + encryptedBase64);

        // 4. 使用私钥解密
        Cipher decryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = decryptCipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
        String decryptedText = new String(decryptedBytes);
        System.out.println("解密后: " + decryptedText);

        // 5. 示例：使用 KeyFactory 从编码加载公钥/私钥
        byte[] pubEncoded = publicKey.getEncoded();
        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey loadedPubKey = keyFactory.generatePublic(pubKeySpec);

        byte[] priEncoded = privateKey.getEncoded();
        PKCS8EncodedKeySpec priKeySpec = new PKCS8EncodedKeySpec(priEncoded);
        PrivateKey loadedPriKey = keyFactory.generatePrivate(priKeySpec);
    }
}

```





### 消息摘要

**作用**：把任意长度的数据映射成 **固定长度的摘要**（哈希值）

**密钥**：**没有密钥**，是公开函数

**特点**：

- 不可逆（无法从摘要恢复原文）
- 相同数据 → 相同摘要
- 不保证身份，只保证完整性

**常用算法**：MD5、SHA-1、SHA-256、SM3

**用途**：

- 文件完整性校验
- 签名前生成摘要
- 生成哈希值存储密码（加盐更安全）

```java
import java.security.MessageDigest;
import java.util.Base64;

public class MessageDigestExample {

    public static void main(String[] args) throws Exception {
        // 1. 待计算哈希的数据
        String data = "Hello, MessageDigest!";
        byte[] dataBytes = data.getBytes();

        // 2. 获取 MessageDigest 实例（SHA-256）
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        // 3. 更新数据（可多次调用 update()）
        md.update(dataBytes);

        // 4. 生成摘要
        byte[] digestBytes = md.digest();

        // 5. 转 Base64 方便打印
        String digestBase64 = Base64.getEncoder().encodeToString(digestBytes);
        System.out.println("SHA-256 摘要(Base64): " + digestBase64);

        // 6. 验证数据完整性（重新生成摘要比较）
        MessageDigest mdVerify = MessageDigest.getInstance("SHA-256");
        byte[] verifyBytes = mdVerify.digest(dataBytes);
        boolean isSame = java.util.Arrays.equals(digestBytes, verifyBytes);
        System.out.println("数据完整性验证: " + isSame);
    }
}

```



### 消息认证

**作用**：生成 **消息认证码**，既保证数据完整性，又保证身份验证

**密钥**：**需要密钥**，双方共享密钥

**特点**：

- HMAC = Hash + 密钥
- 只有知道密钥的人才能生成/验证正确的 MAC

**常用算法**：HmacSHA1、HmacSHA256、HmacSM3

**用途**：

- API 请求签名
- 防篡改数据校验
- 对称加密场景下的消息认证

```java
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class HmacExample {

    public static void main(String[] args) throws Exception {
        // 1. 定义对称密钥（可以用随机生成或固定字节数组）
        String keyString = "mysecretkey12345";
        SecretKey secretKey = new SecretKeySpec(keyString.getBytes(), "HmacSHA256");

        // 2. 待计算 HMAC 的数据
        String data = "Hello, HMAC!";
        byte[] dataBytes = data.getBytes();

        // 3. 创建 Mac 实例，初始化密钥
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);

        // 4. 生成 HMAC
        byte[] hmacBytes = mac.doFinal(dataBytes);
        String hmacBase64 = Base64.getEncoder().encodeToString(hmacBytes);
        System.out.println("HMAC(Base64): " + hmacBase64);

        // 5. 验证 HMAC（示例）
        // 假设收到的数据和 HMAC，重新计算 HMAC 比较
        Mac macVerify = Mac.getInstance("HmacSHA256");
        macVerify.init(secretKey);
        byte[] verifyBytes = macVerify.doFinal(dataBytes);
        boolean isValid = java.util.Arrays.equals(hmacBytes, verifyBytes);
        System.out.println("HMAC 验证结果: " + isValid);
    }
}

```





### 数字签名

**作用**：生成和验证 **数字签名**，保证数据完整性和身份认证

**密钥**：**非对称密钥对**（私钥签名、公钥验证）

**特点**：

- 公钥验证 → 任何人都可以验证签名
- 私钥签名 → 只有密钥持有者能签名
- 一般对长数据先做 **摘要（MessageDigest）**，再签名

**常用算法**：SHA256withRSA、SHA256withECDSA、SM3withSM2

**用途**：

- 文件/消息数字签名
- SSL/TLS 证书签名
- 软件发布签名

```java
import java.security.*;
import java.util.Base64;

public class SignatureExample {

    public static void main(String[] args) throws Exception {
        // 1. 生成 RSA 密钥对
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // 2. 待签名数据
        String data = "Hello, digital signature!";
        byte[] dataBytes = data.getBytes();

        // 3. 用私钥生成签名
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(dataBytes);
        byte[] signatureBytes = signer.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
        System.out.println("数字签名(Base64): " + signatureBase64);

        // 4. 用公钥验证签名
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(dataBytes);
        boolean isValid = verifier.verify(Base64.getDecoder().decode(signatureBase64));
        System.out.println("签名验证结果: " + isValid);

        // 5. 演示：修改数据后验证签名
        String tamperedData = "Hello, digital signature!改动";
        verifier.update(tamperedData.getBytes());
        boolean isValidTampered = verifier.verify(Base64.getDecoder().decode(signatureBase64));
        System.out.println("篡改数据验证结果: " + isValidTampered);
    }
}

```





| 类              | 密钥           | 可逆性 | 保证内容 | 保证身份         | 用法               |
| --------------- | -------------- | ------ | -------- | ---------------- | ------------------ |
| `MessageDigest` | ❌ 无           | 不可逆 | ✅ 完整性 | ❌ 无法验证身份   | 文件校验、摘要     |
| `Mac`           | ✅ 对称密钥     | 不可逆 | ✅ 完整性 | ✅ 双方共享密钥   | API 签名、防篡改   |
| `Signature`     | ✅ 非对称密钥对 | 不可逆 | ✅ 完整性 | ✅ 私钥持有者身份 | 数字签名、证书验证 |





### 证书

`Certificate` 是 Java 中表示 **数字证书** 的抽象类，用于封装公钥及相关信息。

验证公钥的合法性、保证身份可信、用于数字签名验证。

**`X509Certificate`** 是 `Certificate` 的子类，最常用的证书类型

**内容包括**：

- 公钥（PublicKey）
- 证书持有者信息（Subject DN）
- 证书颁发机构信息（Issuer DN）
- 有效期
- 签名算法及签名值

**使用场景**

1. **HTTPS / SSL**
   - 浏览器或服务端通过证书验证公钥身份，保证通信安全。
2. **数字签名**
   - 签名时用私钥生成，验证时用证书里的公钥验证。
3. **身份认证 / PKI**
   - 公司、组织或 CA 签发证书，确保身份可信。



```java
import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.net.URL;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

public class WebsiteCertificateExample {

    public static void main(String[] args) throws Exception {
        String httpsUrl = "https://www.google.com";

        // 1. 创建 HTTPS 连接
        URL url = new URL(httpsUrl);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.connect();

        // 2. 获取服务器证书链
        Certificate[] certs = conn.getServerCertificates();

        for (Certificate cert : certs) {
            if (cert instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) cert;

                System.out.println("------ 证书信息 ------");
                System.out.println("持有者: " + x509.getSubjectDN());
                System.out.println("颁发者: " + x509.getIssuerDN());
                System.out.println("有效期: " + x509.getNotBefore() + " - " + x509.getNotAfter());
                System.out.println("签名算法: " + x509.getSigAlgName());
                System.out.println("公钥: " + x509.getPublicKey());

                // 3. 验证证书有效期
                try {
                    x509.checkValidity(new Date());
                    System.out.println("证书在有效期内 ✅");
                } catch (Exception e) {
                    System.out.println("证书不在有效期内 ❌");
                }

                // 4. 验证证书签名（使用颁发者公钥）
                try {
                    PublicKey issuerPubKey = x509.getPublicKey(); // 注意：真实场景需使用上级 CA 公钥
                    x509.verify(issuerPubKey);
                    System.out.println("证书签名验证成功 ✅");
                } catch (Exception e) {
                    System.out.println("证书签名验证失败 ❌");
                }

                System.out.println("-------------------------\n");
            }
        }

        conn.disconnect();
    }
}
```



#### X.509 格式

**定义**：X.509 是一种国际标准，用于 **公钥基础设施（PKI）中的数字证书**。

**用途**：

1. 认证公钥所属的实体（人、组织、网站）
2. 支持数字签名验证
3. HTTPS/SSL、VPN、邮件加密、软件签名

**Java 对应类**：`java.security.cert.X509Certificate`

| 部分                                   | 说明                                   |
| -------------------------------------- | -------------------------------------- |
| **版本 (Version)**                     | X.509 v1、v2、v3，目前常用 v3          |
| **序列号 (Serial Number)**             | 唯一标识证书，颁发者分配               |
| **签名算法 (Signature Algorithm)**     | 证书签名使用的算法，例如 SHA256withRSA |
| **证书持有者 (Subject)**               | 证书所属实体信息，如 CN、O、C          |
| **证书颁发者 (Issuer)**                | 颁发证书的 CA 信息                     |
| **有效期 (Validity)**                  | NotBefore（起始） / NotAfter（截止）   |
| **公钥信息 (Subject Public Key Info)** | 公钥 + 算法                            |
| **扩展字段 (Extensions)**              | 可选，如 KeyUsage、SAN、CRL 分发点     |
| **证书签名 (Signature Value)**         | 由颁发者私钥对证书信息签名             |



**X.509 的常见编码格式**

1. **DER（Distinguished Encoding Rules）**

   - 二进制编码
   - 文件扩展名：`.der` 或 `.cer`（有时二进制）
   - Java `CertificateFactory.getInstance("X.509")` 支持

2. **PEM（Privacy Enhanced Mail）**

   - Base64 编码 + 标记头尾

   - 格式示例：

     ```css
     -----BEGIN CERTIFICATE-----
     MIIDyzCCArOgAwIBAgIJAO7N1...（Base64编码）
     -----END CERTIFICATE-----
     ```

   - 文件扩展名：`.pem`、`.crt`、`.cer`

   - Java 可以通过 `Base64` 解码后转换成 `X509Certificate`

```java
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class X509LoadDemo {
    public static void main(String[] args) throws Exception {
        FileInputStream fis = new FileInputStream("cert.cer"); // DER 或 PEM
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);

        System.out.println("持有者: " + cert.getSubjectDN());
        System.out.println("颁发者: " + cert.getIssuerDN());
        System.out.println("有效期: " + cert.getNotBefore() + " - " + cert.getNotAfter());
    }
}

```



#### PKCS#8

- **定义**：PKCS#8（Public-Key Cryptography Standards #8）是 **私钥信息语法标准**
- **作用**：统一存储和传输 **私钥**，支持各种加密算法（RSA、DSA、EC 等）
- **特点**：
  - 可存储私钥信息（算法类型 + 私钥数据）
  - 可以加密或不加密私钥
  - 独立于证书（证书中存的是公钥）

> 对应公钥的标准是 **X.509 SubjectPublicKeyInfo**

**PKCS#8 编码格式**

1. **DER（二进制编码）**

   - 扩展名：`.der`、`.key`

2. **PEM（Base64 + 标记头尾）**

   - 示例：

   ```
   -----BEGIN PRIVATE KEY-----
   MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBK...
   -----END PRIVATE KEY-----
   ```

3. **加密私钥**（Optional）

   ```
   -----BEGIN ENCRYPTED PRIVATE KEY-----
   ...
   -----END ENCRYPTED PRIVATE KEY-----
   ```

```java
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class LoadPKCS8PrivateKey {
    public static PrivateKey loadPrivateKey(String filePath, String algorithm) throws Exception {
        byte[] keyBytes = Files.readAllBytes(Paths.get(filePath));

        // 如果是 PEM 格式，需要去掉头尾和换行
        String keyPem = new String(keyBytes)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(keyPem);

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        return kf.generatePrivate(spec);
    }

    public static void main(String[] args) throws Exception {
        PrivateKey privateKey = loadPrivateKey("private_key.pem", "RSA");
        System.out.println("私钥算法: " + privateKey.getAlgorithm());
        System.out.println("私钥格式: " + privateKey.getFormat());
    }
}

```



## 常用算法



| 类型                    | 常用类                               | 算法/模式                                                  | 用途                           | 说明                     |
| ----------------------- | ------------------------------------ | ---------------------------------------------------------- | ------------------------------ | ------------------------ |
| **消息摘要（Hash）**    | MessageDigest                        | MD2 / MD5 / SHA-1 / SHA-256 / SHA-512 / SM3                | 数据完整性校验、签名前生成摘要 | 不可逆，固定长度         |
| **数字签名**            | Signature                            | SHA1withRSA / SHA256withRSA / SHA256withECDSA / SM3withSM2 | 数据签名与验证                 | 非对称加密 + 摘要        |
| **非对称密钥生成**      | KeyPairGenerator                     | RSA / DSA / EC / SM2                                       | 生成公私钥对                   | 用于非对称加密、签名     |
| **密钥工厂**            | KeyFactory                           | RSA / DSA / EC / SM2                                       | 将编码密钥转换为 Key 对象      | PKCS#8 / X.509 格式      |
| **证书处理**            | CertificateFactory / X509Certificate | X.509                                                      | 证书解析与验证                 | 用于 HTTPS、数字签名验证 |
| **安全随机数**          | SecureRandom                         | SHA1PRNG / NativePRNG / DRBG                               | 密钥、IV、随机数生成           | 用于加密初始化或密钥生成 |
| **消息认证码（HMAC）**  | Mac                                  | HmacMD5 / HmacSHA1 / HmacSHA256 / HmacSM3                  | 数据完整性和身份验证           | 对称密钥 + 哈希          |
| **对称加密**            | Cipher                               | AES / DES / DESede / SM4                                   | 数据加密和解密                 | 结合模式和填充使用       |
| **对称密钥生成**        | KeyGenerator                         | AES / DES / DESede / SM4                                   | 生成随机对称密钥               | 用于 Cipher 初始化       |
| **对称密钥封装**        | SecretKeySpec                        | AES / DES / DESede / SM4                                   | 将字节数组包装为 SecretKey     | 用于加密/解密            |
| **初始化向量**          | IvParameterSpec                      | -                                                          | CBC/GCM 模式加密初始化向量     | 必须随机生成，保证安全性 |
| **PBE（基于口令加密）** | PBEKeySpec / PBEParameterSpec        | PBEWithMD5AndDES / PBEWithSHA1AndDESede                    | 基于口令的加密                 | 加盐 + 多轮迭代          |
| **加密模式（Cipher）**  | Cipher                               | ECB / CBC / GCM                                            | 控制块加密方式                 | 安全性：GCM > CBC > ECB  |
| **填充方式（Cipher）**  | Cipher                               | PKCS5Padding / PKCS1Padding / OAEP                         | 防止明文泄露或块不足           | 与模式组合使用           |



**常见模式与填充方式**

| 模式/填充    | Java 表达方式                         | 说明                           |
| ------------ | ------------------------------------- | ------------------------------ |
| ECB          | AES/ECB/PKCS5Padding                  | 简单模式，每块独立，安全性差   |
| CBC          | AES/CBC/PKCS5Padding                  | 每块与上一块异或，需 IV        |
| GCM          | AES/GCM/NoPadding                     | 加密 + 消息认证码（AEAD 模式） |
| PKCS1Padding | RSA/ECB/PKCS1Padding                  | RSA 填充，防止明文泄露         |
| OAEP         | RSA/ECB/OAEPWithSHA-256AndMGF1Padding | RSA 高安全填充方式             |