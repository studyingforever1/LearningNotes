## 开始开发

1、企业服务平台提供了OAuth的授权登录方式，可以让从企业服务平台打开的网页获取成员的身份信息，从而免去登录的环节。

**2、统一登录认证，主要对于企业用户主动进行验证，统一登录认证成功后，会返回access_token，是携带企业用户信息（用户id），这个是关键区别，企业内部系统间互相访问时，接口可以通过access_token获取到当前请求的用户信息（用户id）和应用系统信息（oa系统）。**

3、授权码模式（PKCE）由于没有配置client_secret，不可以使用refresh_token对access_token进行刷新，续期使用，需要重新登录获取用户授权，所以也就没有返回refresh_token；

**4、企业服务平台和接入的企业内部应用系统的请求，均可通过access_token来获取成员的身份信息。**



### PKCE（Proof Key for Code Exchange）

OAuth 2.0 的授权码模式（Authorization Code Grant）是一种常见的授权方式，适用于服务器端应用程序。为了增强其安全性，特别是在移动应用和单页应用（SPA）中，OAuth 2.0 引入了 PKCE（Proof Key for Code Exchange，代码交换证明密钥）扩展。

PKCE 通过引入 `code_challenge` 和 `code_verifier` 两个参数来防止授权码拦截攻击。：

1. **code_verifier**：首先，客户端生成一个高熵的随机字符串，称为 `code_verifier`。这个字符串通常长度在43到128个字符之间，可以包含字母、数字、连字符、下划线、点和波浪线。
2. **code_challenge**：接下来，客户端对 `code_verifier` 进行处理，生成 `code_challenge`。有两种方法可以生成 `code_challenge`：
   - **plain**：直接使用 `code_verifier` 作为 `code_challenge`。
   - **S256**：对 `code_verifier` 进行 SHA-256 哈希运算，然后对结果进行 Base64 URL 安全编码，生成 `code_challenge`。

具体步骤如下：

#### 生成 `code_verifier`

首先，生成一个高熵的随机字符串。例如：

```
MDc4ZmZmYTgtMDUzMS00NzA2LWFlOTktZmEwZTc3ODgyNTli
```

#### 生成 `code_challenge`

假设我们选择使用 `S256` 方法：

1. 对 `code_verifier` 进行 SHA-256 哈希运算，得到一个字节数组。
2. 对这个字节数组进行 Base64 URL 安全编码，得到 `code_challenge`。

例如，假设 `code_verifier` 是 `MDc4ZmZmYTgtMDUzMS00NzA2LWFlOTktZmEwZTc3ODgyNTli`，其 SHA-256 哈希值为：

```
P66miz-wqc1NXl2o7ew1gTrILVFMQcwbxH5dch3PJRo
```

这个值就是 `code_challenge`。



### OAuth2.1简介

OAuth2.1的设计背景，在于允许用户在不告知第三方自己的账号密码情况下，通过授权方式，让第三方服务可以获取自己的资源信息。
详细的协议介绍，开发者可以参考[The OAuth 2.1 Authorization Framework](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1-05)，以及[OpenID Connect Core 1.0 incorporating errata set 2](https://openid.net/specs/openid-connect-core-1_0.html)。



### 统一登录（OAuth2授权码模式）接入流程

![login](assets/images/login.jpg)

redirect_uri需要先配置至应用的“跳转地址”，否则跳转时会提示“redirect_uri不正确”。
**要求配置的可信跳转地址，必须与访问链接的跳转地址完全一致；若访问链接URL带了端口号，端口号也需要登记到可信跳转地址中**。



### 关于用户ID机制

UserId用于在一个企业内唯一标识一个用户，通过网页授权接口可以获取到当前用户的用户Id信息，如果需要获取用户的更多信息可以调用开放平台接口来获取。



### 静默授权与手动授权

- 静默授权：用户点击链接后，页面直接302跳转至 redirect_uri?code=CODE&state=STATE
- 手动授权：用户点击链接后，会弹出一个中间页，让用户选择是否授权，用户确认授权后再302跳转至 redirect_uri?code=CODE&state=STATE



## 构造网页授权链接

### 链接及参数说明

**请求地址：**

生产环境：https://esp.xkw.cn/oauth2/authorize

沙箱测试环境：https://esp.doc.xkw.cn/oauth2/authorize

应用系统需要获取用户的身份信息，第一步需要构造如下的链接来获取code参数：

```
https://esp.xkw.cn/oauth2/authorize?client_id=CLIENT_ID&response_type=code&scope=openid&redirect_uri=REDIRECT_URI&state=STATE&code_challenge=CODE_CHALLENGE&code_challenge_method=S256
```

**参数说明：**

| 参数                  | 必须 | 说明                                                         |
| --------------------- | ---- | ------------------------------------------------------------ |
| client_id             | 是   | 应用的client_id                                              |
| redirect_uri          | 是   | 授权后重定向的回调链接地址，**需要对嵌套的查询参数进行URL编码，还要对完整的回调链接地址进行URL编码** |
| response_type         | 是   | 返回类型，此时固定为：code                                   |
| scope                 | 是   | 应用授权作用域，此时固定为：openid                           |
| state                 | 否   | 重定向后会带上state参数，企业可以填写a-zA-Z0-9的参数值，长度不可超过128个字节 |
| code_challenge        | 是   | 加密后的值，生成算法参考上文                                 |
| code_challenge_method | 是   | 加密算法：S256或plain，此时固定为：S256，不再支持plain       |

员工点击后，页面将跳转至 redirect_uri?code=CODE&state=STATE，应用系统可根据code参数获得员工的userid。code长度最大为512字节。



### 例子说明

比如应用系统client_id：pkce

访问链接跳转地址：https://pkce.xkw.cn/login/oauth2/code/messaging-client-oidc

根据URL规范，将上述参数分别进行UrlEncode，得到拼接的OAuth2链接为：

```
https://esp.xkw.cn/oauth2/authorize?client_id=pkce&response_type=code&scope=openid&redirect_uri=https%3A%2F%2Fpkce.xkw.cn%2Flogin%2Foauth2%2Fcode%2Fmessaging-client-oidc&code_challenge=P66miz-wqc1NXl2o7ew1gTrILVFMQcwbxH5dch3PJRo&code_challenge_method=S256
```

> 注意，构造OAuth2链接中参数的redirect_uri是经过UrlEncode的

员工点击后，页面将跳转至

```javascript
https://pkce.xkw.cn/login/oauth2/code/messaging-client-oidc?code=_XvM6ZsNclVoBJ2BNbt5XNlexd7tii9HBfVnUa8tZLjyqZ1lCvj1Xe_B0zezXG8qELx8G0mt4Cp5y4oF8gdacGNQbiqNklYWC48SUCGNlBlytjn8z0men1NurrCWV2pH
```

应用系统可根据code参数获取access_token，id_token



## 使用code获取access_token，id_token

**请求方式：**POST（**HTTPS**） Content-Type: application/x-www-form-urlencoded

**请求地址：** 

生产环境：https://esp.xkw.cn/oauth2/token

沙箱测试环境：https://esp.doc.xkw.cn/oauth2/token



### **参数说明** 

详细说明开发者可以参考[The OAuth 2.1 Authorization Framework](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1-05)

| 参数          | 必须 | 说明                                                         |
| ------------- | ---- | ------------------------------------------------------------ |
| grant_type    | 是   | 当前值为authorization_code                                   |
| redirect_uri  | 是   | 应用系统回调地址，与构造网页授权链接中的值要一样，**最后一次URL编码前的完整回调地址** |
| code          | 是   | 授权获取到的code，最大为512字节。每次成员授权带上的code将不一样，code只能使用一次，5分钟未被使用自动过期。 |
| client_id     | 是   | 企业服务平台分配的client_id                                  |
| code_verifier | 是   | 加密的明文，生成算法参考上文                                 |



### **返回结果**

| 参数              | 说明                                                         |
| ----------------- | ------------------------------------------------------------ |
| access_token      | 访问令牌，调用开放接口的凭证                                 |
| scope             | 授权范围，返回构造的授权链接请求中的scope                    |
| id_token          | ID令牌，jwt格式，使用企业服务平台的公钥进行签名验证，开放公钥：https://esp.xkw.cn/oauth2/jwks |
| token_type        | token类型                                                    |
| expires_in        | access_token有效期，单位为秒                                 |
| error_description | 错误描述                                                     |
| error_code        | 错误码                                                       |
| error_uri         | 错误详细说明地址                                             |

a) 成功返回示例如下：

```
{
    "access_token": "eyJraWQiOiJjM2Q2ZDAxYiIsInR5cCI6IkpXVCIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiJ4eTA3NjE5IiwiYXVkIjoicGtjZSIsIm5iZiI6MTcxNzU1NDkyMywic2NvcGUiOlsib3BlbmlkIl0sImlzcyI6Imh0dHBzOi8vZXNwLmRvYy54a3cuY24iLCJleHAiOjE3MTc1NTg1MjMsImlhdCI6MTcxNzU1NDkyM30.fhKCuO815b9KbCmUWxsAi6HGWYpP7QjERbm76zwHb9Rp0dnLZRfRO8uc4OORXmcnKx70xlphCCmS7Ky7A3ijEGMScgDpucUenhHN3PlSCKZlVjNv_kCPDSdmoFSl-3zgDpVxOwLYx0oL0hxNtnGcQhn_X7y7Pm9odY53XWcel87lfarKzVrifLXxILsk9tgh2YX8xFcW3EwcuWUz2C27-C5KFZp7XJQsuZGtRMhk7t1hUAm851nFw2Msq5wk6SaXlXqdfA9RX_8QJFD9R8e8ayVl30TGi2LLvZDcdMukTd9rV9_ieHeXRZC_ujJGGyWK9r0njKsuSfb2Jv_fPiP12A",
    "scope": "openid",
    "id_token": "eyJraWQiOiJjM2Q2ZDAxYiIsInR5cCI6IkpXVCIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiJ4eTA3NjE5IiwiYXVkIjoicGtjZSIsImF6cCI6InBrY2UiLCJhdXRoX3RpbWUiOjE3MTc1NTQ3NzAsImlzcyI6Imh0dHBzOi8vZXNwLmRvYy54a3cuY24iLCJleHAiOjE3MTc1NTY3MjMsImlhdCI6MTcxNzU1NDkyMywic2lkIjoiaHBqSklMNkpjYW5rRkRSaUNBMjBXZEhncktxbnJsVUpURXRxY29TeWhORSJ9.J27CPqJkzOJy5qEhTCBroncxrPOSBgVCDdN2Nmxlzc21i2aRw3d2MsH6OVMrJVmAnq4H_PlcZurKKh8TGJapohEk89MTAZOq3itieX_QBnsuMdq3gJIrBnv3HbY7-Xh3NzamQtjbuOLelxQSd-k7Ug6CJ6XUigIgT3f36Dy9Qj6PFn6zsPPnaAc5zL52xR2pu2uMrO4kCg55IZufNOL6KNGqz5dCtZkRsaAiJzraomZLOdwjpvkHYi6HDfgZcjqqJTk3yfkvSeaQLYVF7nxkdy7wiyp5cHP_ZwH0QVEDBsdA4SloewCn_x23Z0BcZybcX3qQkzmN29_dd7pybt-0Pg",
    "token_type": "Bearer",
    "expires_in": 3599
}
```

b)失败返回示例如下：

```
{
    "error_description": "code不正确",
    "error_code": "100031",
    "error_uri": "https://esp.xkw.cn/doc/error?q=error_code"
}
```



## 获取用户ID

### 查看id_token内容

可以使用jwt解码工具进行内容查看

![image-20240605103739073](assets/images/pkce-id-token.png)

从右边解码内容，可以看出通过oa应用系统构造的授权链接请求，用户：xy07619，企业服务平台的相关信息，包括加密算法，公钥等；



### 解析jwt内容，获取用户id

按jwt规范进行内容解析，也可以通过jwt第三方库进行解析，获取payload中的sub值，就是用户id。

**注意：在必要情况下，可以使用企业服务平台公钥进行签名验证，验证通过后，再解析内容，对其他信息进行验证，比如授权时间，开始时间，过期时间等；**



## 调用接口

企业服务平台和接入的企业内部应用系统的接口请求，均可通过access_token来获取成员的身份信息。

### 查看access_token内容

这里就要使用到时返回的access_token，解析后内容如下：

![image-20240605103855476](assets/images/pkce-access-token.png)

以上access_token跟id_token的内容有些区别，接口调用方不需要验证，如果需要调用企业服务开放接口或企业内部应用系统时，由对接的应用系统（接口提供方，详情查看“开放接口”说明文档)在接收到请求时，使用平台公钥进行验证；

### 授权范围说明

以上内容解析出来，scope值仅仅是openid，只是一个例子，可以为空，目前企业服务平台仅认证，不授权。后续升级使用会统一通知。



### 调用方Token缓存机制

接口返回access_token有效期为expires_in，单位是秒，调用方要做好token的缓存处理，

```
缓存时间 = expires_in - 60 * 10
```

缓存时间只要将平台返回的expires_in减去10分钟即可。

注意：不要频繁请求获取access_token，以免被限流控制；



### 调用方接口请求

在http请求的header部分，增加以下内容，key为Authorization，value为"Bearer " + access_token值

| key           | Value                                                        |
| ------------- | ------------------------------------------------------------ |
| Authorization | Bearer eyJraWQiOiJjM2Q2ZDAxYiIsInR5cCI6IkpXVCIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiJ4eTA3NjE5IiwiYXVkIjoib2EiLCJuYmYiOjE3MTA0ODM1MDQsInNjb3BlIjpbIm9wZW5pZCJdLCJpc3MiOiJodHRwczovL2VzcC5kb2MueGt3LmNuIiwiZXhwIjoxNzEwNDgzODA0LCJpYXQiOjE3MTA0ODM1MDR9.KMjDHYIzpNiuX5O6T9MhgEhr0JDThJdWDBjSVlsGHk6YrTMRqNyMbuNS-Ws9kbxM_7klZD-qypUawOHSACRWh22fk9MNmbnIhd5Zwv4TYStwSmgY2Kt3bPi3LQMZA90_nTg_pSDxNZmaOyfV1XJj5GhvUSmd4V9j_N1i_bhBp45eIIFDgAJAwcnXhOkP7xyA8YZJOomzGL3d7j_7FzAgG-U85whKLl9QLQbOBadZvQXmd0KG9vEB1VOiATeXdpxs_ojgTfNgF8lrnQwRl_NYXlMlKck-VNn6uf-cb2zqnWU0z2DhLo5vIg9wqLZsn2ztV2nzEoeoZkvwwHEqI7V4RQ |

