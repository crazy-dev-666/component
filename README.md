## 常用组件

### request-log-boot-starter

灵活配置，记录请求参数和响应体到日志中。

#### 配置参数

| 配置参数（前缀request.log.） | 含义                                                         | 默认值 |
| ---------------------------- | ------------------------------------------------------------ | ------ |
| enabled                      | 是否启用记录日志功能                                         | true   |
| order                        | 过滤器排序值                                                 | -10    |
| type                        | 日志使用场景。<br /><br />`DEV`：开发场景<br />`TEST`：测试场景<br />`ONLINE`：生产场景<br />`CUSTOMER`：自定义场景 | `DEV`  |
| requestLevel                 | 请求日志输出级别。默认根据日志场景决定<br /><br />`NOTHING`：什么都不输出<br />`URL`：仅输出URL<br />`URL_BODY`：输出URL和请求体<br />`URL_BODY_SOME_HEADER`：输出URL和请求体，及部分请求头<br />`ALL`：输出请求全部信息 |        |
| headers                      | 当 requestLevel 值为 `URL_BODY_SOME_HEADER` 时，设置需输出的请求头。 |        |
| responseLevel                | 响应日志级别，默认根据日志场景决定<br /><br />`NOTHING`：什么都不输出<br />`ERROR`：输出错误响应<br />`ERROR_NOBODY`：输出错误响应URL<br />`SLOW_ERROR`：输出慢响应和错误响应<br />`SLOW_ERROR_NOBODY`： 输出慢响应和错误响应URL<br />`ALL`：输出所有响应体 |        |
| requestOmitLength            | 当输出请求体时，长度超出阈值时，截取输出                     | 4KB    |
| responseOmitLength           | 当输出响应体时，长度超出阈值时，截取输出                     | 8KB    |
| slowRequestThreshold         | 慢请求阈值，请求耗时大于此值为慢请求                         | 3秒    |



#### 配置使用

1、引入依赖包

```xml

      <dependency>
            <groupId>cn.dev666.component</groupId>
            <artifactId>request-log-boot-starter</artifactId>
            <version>0.0.1</version>
        </dependency>
```

2、添加配置

```yaml
request:
  log:
    type: dev                 # 启用开发场景的请求日志配置
    headers:                   # 输出的请求头列表
      - Auth-Token
      - Content-Type
```

3、日志示例

![image-20220309143856440](README.assets/image-20220309143856440.png)