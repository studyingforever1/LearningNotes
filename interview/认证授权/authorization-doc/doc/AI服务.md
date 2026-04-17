# AI服务

## AI接口
### 模型列表
使用openai兼容的模型列表，可参考：https://platform.openai.com/docs/api-reference/models

沙箱测试环境：GET https://esp.doc.xkw.cn/ai/v1/models

生产环境：GET https://esp.xkw.cn/ai/v1/models

Authorization: Bearer sk-xxxxxxxxx

```
# 沙箱测试环境
curl https://esp.doc.xkw.cn/ai/v1/models \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ESP API KEY>" \
```
返回内容
 ```
 {
    "data": [
        {
            "id": "glm-4",
            "object": "chat.completion.model",
            "owned_by": "zhipu-ai"
        },
        {
            "id": "gpt-4o",
            "object": "chat.completion.model",
            "owned_by": "azure"
        },
        {
            "id": "deepseek-r1",
            "object": "chat.completion.model",
            "owned_by": "tencent-llm,aliyun-bailian"
        },
        {
            "id": "qwen3-8b",
            "object": "chat.completion.model",
            "owned_by": "aliyun-bailian"
        },
        {
            "id": "text-embedding-3-small",
            "object": "text.embedding.model",
            "owned_by": "azure-east-us"
        }
    ],
    "object": "list"
}
 ```
目前支持的模型和提供商，持续增加中...

| 提供商名称 | 提供商用ID     | 模型ID列表                                                   |
| ---------- | -------------- | ------------------------------------------------------------ |
| 智谱AI     | zhipu-ai       | glm-4m，glm-4-plus，glm-z1-airx                              |
| 微软OpenAI | azure          | gpt-4-turbo，gpt-4o，gpt-4o-audio，gpt-o1-preview，gpt-4.1   |
| 腾讯云     | tencent-llm    | deepseek-r1，deepseek-v3，deepseek-v3-0324                   |
| 阿里云     | aliyun-bailian | qwen-max，qwen3-235b，qwen2.5-vl-7b-instruct，qwen3-8b，qwen3-14b |



### 聊天模型

使用openai兼容的聊天模型，可参考：https://platform.openai.com/docs/api-reference/chat/create

沙箱测试环境：POST https://esp.doc.xkw.cn/ai/v1/chat/completions

生产环境：POST https://esp.xkw.cn/ai/v1/chat/completions

Authorization: Bearer sk-xxxxxxxxx

```
# 沙箱测试环境
curl https://esp.doc.xkw.cn/ai/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ESP API KEY>" \
  -d '{
        "model": "gpt-4o",
        "messages": [
          {"role": "system", "content": "You are a helpful assistant."},
          {"role": "user", "content": "Hello!"}
        ],
        "stream": false
      }'
```



### 嵌入模型

使用openai兼容的嵌入模型，可参考：https://platform.openai.com/docs/api-reference/embeddings/create

沙箱测试环境：POST https://esp.doc.xkw.cn/ai/v1/embeddings

生产环境：POST https://esp.xkw.cn/ai/v1/embeddings

Authorization: Bearer sk-xxxxxxxxx

```
# 沙箱测试环境
curl https://esp.doc.xkw.cn/ai/v1/embeddings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ESP API KEY>" \
  -d '{
        "model": "text-embedding-v4",
        "input": "You are a helpful assistant.",  
        "dimension": "1024",  
        "encoding_format": "float"
    }'
```



### 录音文件识别

沙箱测试环境：POST https://esp.doc.xkw.cn/v1/speech/flash-recognition

生产环境：POST https://esp.xkw.cn/v1/speech/flash-recognition

Authorization: Bearer sk-xxxxxxxxx

参数：

| 参数名称    | 描述                                                         |
| ----------- | ------------------------------------------------------------ |
| voiceUrl    | 录音文件地址                                                 |
| engineType  | 引擎模型类型，电话场景：8k_zh：中文电话通用，8k_zh_finance：中文电话金融，8k_en：英文电话通用。非电话场景：16k_zh：中文通用，16k_zh-PY：中英粤，16k_zh-TW：中文繁体，16k_zh_edu：中文教育，16k_zh_medical：中文医疗，16k_zh_court：中文法庭 |
| voiceFormat | 语音编码方式，可选，默认值为4。1：pcm；4：speex(sp)；6：silk；8：mp3；10：opus（opus 格式音频流封装说明）；12：wav；14：m4a（每个分片须是一个完整的 m4a 音频）；16：aac |

例如：

```
{
    "voiceUrl":"https://oss-dataoper-repo.oss-cn-hangzhou.aliyuncs.com/call-log/20210112/04fbbb00-24dd-461d-b32b-c7aceb064a93.mp3?x-oss-credential=TMP.3KpA6rH6qvDiibK6gwsrbQiY8Vff2SdiVaJ3xtrmQUr8C6AVVoG8abC2LwsVKDj6FUqwbfaZBRZzhxUMXbGqPtsRXc4MDq%2F20250624%2Fcn-hangzhou%2Foss%2Faliyun_v4_request&x-oss-date=20250624T072724Z&x-oss-expires=3600&x-oss-signature-version=OSS4-HMAC-SHA256&x-oss-signature=c988da9382d4c4ac9a0f613958841e1373d416cd125d835d528308eaaa99549c",
    "engineType":"8k_zh_large",
    "voiceFormat":"mp3"
}
 
```



### 实时语音识别

使用 websocket进行对接。

沙箱测试环境：wss://esp.doc.xkw.cn/ai/ws/v1/tencent/asr?apiKey=xxx&engineModelType=xxx&voiceFormat=xxx

生产环境：wss://esp.xkw.cn/ai/ws/v1/tencent/asr?apiKey=xxx&engineModelType=xxx&voiceFormat=xxx

参数：

| 参数名称    | 描述                                                         |
| ----------- | ------------------------------------------------------------ |
| apiKey      | 接口密钥                                                     |
| engineType  | 引擎模型类型，电话场景：8k_zh：中文电话通用，8k_zh_finance：中文电话金融，8k_en：英文电话通用。非电话场景：16k_zh：中文通用，16k_zh-PY：中英粤，16k_zh-TW：中文繁体，16k_zh_edu：中文教育，16k_zh_medical：中文医疗，16k_zh_court：中文法庭 |
| voiceFormat | 语音编码方式，可选，默认值为4。1：pcm；4：speex(sp)；6：silk；8：mp3；10：opus（opus 格式音频流封装说明）；12：wav；14：m4a（每个分片须是一个完整的 m4a 音频）；16：aac |

例如沙箱测试环境：

wss://esp.doc.xkw.cn/ai/ws/v1/tencent/asr?apiKey=sk-xxxxxxxxx&engineModelType=16k_zh&voiceFormat=10





## 构建高效的智能体

想象一下，构建中。。。