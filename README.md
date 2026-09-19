# subapi

AllMusic 的 Subsonic 客户端适配器。它通过 Subsonic REST API 访问已有的 Subsonic 服务，
并向 AllMusic 提供搜索、歌曲信息和播放地址。

首次加载后会生成 `subapi.json`：

```json
{
  "serverUrl": "https://music.example.com/rest",
  "username": "user",
  "password": "password",
  "clientName": "AllMusic-SubAPI",
  "apiVersion": "1.16.1",
  "debug": false,
  "authentication": "md5"
}
```

`authentication` 支持两种值：

- `md5`：使用 `t=md5(password + salt)` 和随机 `salt`，默认值。
- `password`：使用 Subsonic 的明文 `p=password` 参数。请求中的特殊字符会进行 URL 编码，
  这是参数传输格式，不是加密；服务端解析后得到的是原始密码。建议仅在 HTTPS 服务地址下使用。

适配器会严格按照该选项认证，不会自动切换认证方式。

`debug` 设置为 `true` 时，会记录请求方法、认证模式和响应状态，不会记录完整请求地址或密码。每次重新加载配置
时都会调用 Subsonic 的 `ping` 接口测试地址和账户是否可用，并记录测试成功或失败。

`serverUrl` 会被直接当作 Subsonic 接口基址使用，不会自动追加 `/rest`。例如自定义路径为
`https://music.example.com/subsonic` 时，客户端会访问
`https://music.example.com/subsonic/ping.view`。
