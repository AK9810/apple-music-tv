# Apple Music API —— 完整逆向工程指南

没有 Apple 开发者账号。没有 MusicKit JS。一切都是从 `music.apple.com` 的流量中抓取或推断出来的。

[English](apple-music-api.md) | **简体中文**

---

## 认证

### Bearer JWT（所有请求都需要）

Apple 在它们的 Web 播放器 JavaScript bundle 中嵌入了一个长期有效的 JWT。启动时抓取一次即可；它不常轮换（几天/几周）。

```
GET https://music.apple.com/
  → 从 HTML 中解析：crossorigin src="(/assets/index.*.js)"
GET https://music.apple.com{scriptPath}
  → 正则：/(eyJ[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]*)/
```

取第一个匹配。这是一个标准 JWT（`eyJ...`）。对所有目录和公开端点有效。

### Music-User-Token（MUT）

用于：资料库、个性化内容、完整的流解密。
由用户从 `music.apple.com` → 开发者工具 → 任意 API 请求 → `Music-User-Token` 头获取。没有自动化获取方式。

保存在服务器端。在需要它的请求中，同时作为 `Music-User-Token` 和 `Media-User-Token` 头发送。

### 每个 amp-api 请求的标准头

```
Authorization: Bearer {jwt}
Music-User-Token: {mut}       ← 仅在需要时
Media-User-Token: {mut}       ← 仅在需要时（流媒体/DRM 端点）
Origin: https://music.apple.com
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15
```

### Storefront 检测

```
GET https://amp-api-edge.music.apple.com/v1/me/storefront
  headers: Authorization + Music-User-Token
  response: { data: [{ id: "us", ... }] }
```

`data[0].id` = 两位的 storefront 代码（`us`、`gb`、`jp` 等）。目录 URL 路径需要它。默认 `us`。

---

## 基础 URL

```
https://amp-api-edge.music.apple.com
```

下面所有 API 路径都相对于这个基础 URL。

---

## ID 前缀

| 前缀 | 类型 |
|--------|------|
| *(无，纯数字)* | 目录歌曲/专辑/艺人 |
| `pl.` | 目录/编辑/分享播放列表 |
| `i.` | 资料库歌曲 |
| `l.` | 资料库专辑 |
| `r.` | 资料库艺人 |
| `p.` | 用户创建的资料库播放列表 |
| `ra.` | 电台站点 |

资料库 ID 不能用于目录端点，反之亦然。始终按前缀分支。

---

## 搜索

```
GET /v1/catalog/{sf}/search
  params:
    term: string
    types: "songs,albums,artists,playlists"   ← 逗号分隔
    limit: 20
  headers: 仅 Authorization（不需要 MUT）
```

响应结构：
```json
{
  "results": {
    "songs":     { "data": [ ...songObjects ] },
    "albums":    { "data": [ ...albumObjects ] },
    "artists":   { "data": [ ...artistObjects ] },
    "playlists": { "data": [ ...playlistObjects ] }
  }
}
```

---

## 目录对象 —— 解析

### 歌曲对象

```
song.id                                          → 歌曲 ID
song.attributes.name                             → 标题
song.attributes.artistName                       → 艺人名
song.relationships.artists.data[0].id            → artistId（用于跳转艺人页）
song.attributes.albumName                        → 专辑名
song.attributes.durationInMillis                 → 时长（毫秒）
song.attributes.artwork.url                      → 封面模板（替换 {w},{h},{f}）
song.attributes.artwork.bgColor                  → 十六进制背景色字符串（无 #）
song.attributes.previews[0].url                  → 30 秒 AAC 试听 URL
song.attributes.previews[0].hlsUrl               → HLS 试听 URL
song.attributes.hasLyrics                        → bool
song.attributes.trackNumber                      → int
song.attributes.genreNames                       → string[]
```

### 专辑对象

```
album.id
album.attributes.name                            → 标题
album.attributes.artistName
album.attributes.artwork.url                     → 封面模板
album.attributes.artwork.bgColor
album.attributes.releaseDate                     → "YYYY-MM-DD"
album.attributes.trackCount
album.attributes.genreNames
album.attributes.recordLabel
album.attributes.copyright
album.attributes.editorialNotes.standard         → 简介/描述
album.attributes.isMasteredForItunes             → bool
```

### 艺人对象

```
artist.id
artist.attributes.name
artist.attributes.artwork.url                    → 封面模板
artist.attributes.genreNames
artist.attributes.editorialNotes.standard
```

### 播放列表对象

```
playlist.id
playlist.attributes.name
playlist.attributes.curatorName
playlist.attributes.artwork.url                  → 封面模板
playlist.attributes.artwork.bgColor
playlist.attributes.description.short
```

### 封面 URL 模板

每个封面 URL 都包含字面的 `{w}`、`{h}`、`{f}` 占位符：
```
https://is1-ssl.mzstatic.com/image/thumb/.../source/{w}x{h}bb.{f}

→ 把 {w} 和 {h} 替换为像素尺寸（例如 500）
→ 把 {f} 替换为 "jpg"
→ 结果：.../source/500x500bb.jpg
```

---

## 目录 —— 专辑

### 获取专辑元数据

```
GET /v1/catalog/{sf}/albums/{id}
  params: include=tracks,artists
  headers: Authorization
  response: { data: [ albumObject ] }
```

专辑在 `data[0]`。

### 获取专辑曲目

```
GET /v1/catalog/{sf}/albums/{id}/relationships/tracks
  params: limit=50
  headers: Authorization
  response: { data: [ ...songObjects ], next: "/v1/..." | null }
```

筛选 `type === "songs"` —— Apple 偶尔会在曲目列表中混入音乐视频。
跟随 `response.next` 分页。

### 资料库专辑（id 以 `l.` 开头）

```
GET /v1/me/library/albums/{id}
  params: include=catalog
  headers: Authorization + MUT

GET /v1/me/library/albums/{id}/tracks
  params: include=catalog&limit=100
  headers: Authorization + MUT
```

使用 `include=catalog` 时，真实的目录条目嵌入在：
```
album.relationships.catalog.data[0]   → 完整的目录专辑对象
song.relationships.catalog.data[0]    → 完整的目录歌曲对象
```

封面、ID 和元数据优先使用目录条目。

---

## 目录 —— 艺人

### 获取艺人（基础）

```
GET /v1/catalog/{sf}/artists/{id}
  headers: Authorization
  response: { data: [ artistObject ] }
```

### 获取艺人 —— 完整页面（简介 + 各区块）

```
GET /v1/catalog/{sf}/artists/{id}
  params:
    views: top-songs,latest-release,full-albums,featured-albums,similar-artists
    extend: editorialNotes
    limit[artists:top-songs]: 20
    limit[artists:full-albums]: 30
  headers: Authorization（+ MUT 可选）
```

响应结构：
```
artist.views["top-songs"].data        → songObjects[]
artist.views["latest-release"].data   → albumObjects[]  （通常 1 个）
artist.views["full-albums"].data      → albumObjects[]
artist.views["featured-albums"].data  → albumObjects[]
artist.views["similar-artists"].data  → artistObjects[]
artist.attributes.editorialNotes.standard → 简介文本
```

### 资料库艺人（id 以 `r.` 开头）

先解析到目录：
```
GET /v1/me/library/artists/{id}
  params: include=catalog
  → artist.relationships.catalog.data[0].id → 真实的目录 ID
```

然后用目录 ID 请求上面的完整艺人页。

### 获取艺人的专辑

```
GET /v1/catalog/{sf}/artists/{id}/relationships/albums
  params: limit=25
  headers: Authorization
  response: { data: [ ...albumObjects ] }
```

---

## 目录 —— 歌曲

### 获取单曲

```
GET /v1/catalog/{sf}/songs/{id}
  headers: Authorization
  response: { data: [ songObject ] }
```

### 获取多首歌曲

```
GET /v1/catalog/{sf}/songs
  params: ids=1234,5678,9012    ← 逗号分隔，最多约 25 个
  headers: Authorization
  response: { data: [ ...songObjects ] }
```

### 相关歌曲（用于自动播放）

```
GET /v1/catalog/{sf}/artists/{artistId}/view/top-songs
  params: limit=25
  headers: Authorization（+ MUT）
  response: { data: [ ...songObjects ] }
```

如果拿不到艺人 ID，回退到搜索：
```
GET /v1/catalog/{sf}/search
  params: term={genreName 或 artistName}, types=songs, limit=25
```

---

## 资料库

所有资料库端点都需要 MUT。没有则 401。

### 歌曲

```
GET /v1/me/library/songs
  params: limit=100, include=catalog
  headers: Authorization + MUT
  response: { data: [ ...librarySongObjects ], next: "/v1/..." | null }
```

跟随 `response.next` 分页。最多 2000 条。使用 `include=catalog` 时，每个条目在 `song.relationships.catalog.data[0]` 嵌入其目录对应项 —— 用它来获取封面和真实 ID。

### 专辑

```
GET /v1/me/library/albums
  params: limit=100, include=catalog
  headers: Authorization + MUT
  response: { data: [ ...libraryAlbumObjects ], next: string | null }
```

### 播放列表

```
GET /v1/me/library/playlists
  params: limit=100, include=catalog
  headers: Authorization + MUT
  response: { data: [ ...libraryPlaylistObjects ], next: string | null }
```

播放列表封面怪癖：资料库播放列表通常没有 `attributes.artwork`。检查顺序：
```
1. playlist.attributes.artwork.url
2. playlist.relationships.catalog.data[0].attributes.artwork.url
```

### 艺人

```
GET /v1/me/library/artists
  params: limit=100
  headers: Authorization + MUT
  response: { data: [ ...libraryArtistObjects ], next: string | null }
```

---

## 播放列表曲目

### 用户资料库播放列表（`p.xxx` 或任何非 `pl.` ID）

```
GET /v1/me/library/playlists/{id}/tracks
  params: limit=100, offset=0, include=catalog
  headers: Authorization + MUT
  response: { data: [ ...librarySongObjects ], next: string | null }
```

用 `offset += 100` 分页。每个曲目：优先使用 `relationships.catalog.data[0]` 获取真实的目录歌曲对象和目录 ID（用于流媒体播放）。回退到资料库对象 —— 它带有 `i.xxx` ID，流媒体路由会处理。

### 目录 / 编辑 / 分享播放列表（`pl.xxx`）

```
GET /v1/catalog/{sf}/playlists/{id}/tracks
  params: limit=100, offset=0
  headers: Authorization + MUT
  response: { data: [ ...songObjects ], next: string | null }
```

MUT 会发送，但公开播放列表并非严格需要。

---

## 榜单 / 浏览

### 榜单

```
GET /v1/catalog/{sf}/charts
  params:
    types: "songs" | "albums" | "playlists"   ← 一次一个，或逗号分隔
    limit: 20
    genre: {genreId}    ← 可选，按流派筛选
  headers: Authorization
  response:
    results.songs[0].data      → songObjects[]
    results.albums[0].data     → albumObjects[]
    results.playlists[0].data  → playlistObjects[]
```

`results.songs[0].name`、`results.albums[0].name` 等 = 榜单名称（例如 "Daily Top 100"）。

### 流派

```
GET /v1/catalog/{sf}/genres
  params: limit=50
  headers: Authorization
  response: { data: [ { id: "20", attributes: { name: "Rock" } }, ... ] }
```

流派 ID `34` = 播客 —— 要过滤掉。把流派 ID 传给榜单端点即可获得该流派的榜单。

---

## 流媒体（完整解密）

高层流程：
1. 从 Apple 的播放端点获取歌曲的 HLS 清单 URL + DRM 密钥 URI
2. 下载加密的 HLS 分片
3. 用 `mp4decrypt`（Bento4）解密
4. 用 ffmpeg 重封装为可拖动的渐进式 MP4

### 步骤 1 —— 获取流媒体参数

**资料库歌曲（`i.xxx`）**：
```
POST https://play.music.apple.com/WebObjects/MZPlay.woa/wa/subVideoPlaybackInfo
  headers:
    Authorization: Bearer {jwt}
    Media-User-Token: {mut}
    Content-Type: application/json
    Origin: https://music.apple.com
  body: {
    "salableAdamId": "{numericId}",   ← 去掉 "i." 前缀，或使用真实的目录 ID
    "type": "libraryTracks"
  }
  response: {
    "songList": [ { "hls-catalog-url": "...", "asset-info": { ... } } ]
  }
```

**目录歌曲（数字 ID）**：
```
POST https://play.music.apple.com/WebObjects/MZPlay.woa/wa/subVideoPlaybackInfo
  body: { "salableAdamId": "{id}", "type": "songs" }
```

`songList[0]["hls-catalog-url"]` 就是主 HLS 播放列表 URL。

### 步骤 2 —— 解析媒体播放列表

获取主播放列表。查找 `#EXT-X-STREAM-INF` 行。如果存在，说明是主播放列表 —— 选择最佳变体：
- 优先选择 `CODECS` 包含 `"ec-3"` 或 `"ac-3"` 的（AAC/AAC+）
- 带宽上限 500 kbps，以避免 ALAC（无损，100–350MB）
- 按带宽降序排列，在上限内选最高
- 如果所有变体都超过 500 kbps，选最低的

**HLS 清单中的资产选择**（比带宽上限更可靠）：
```
#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,...URI="skd://...",KEYFORMAT="..."
#EXT-X-STREAM-INF:BANDWIDTH=256000,...CODECS="ec-3"
```
优先 `ctrp64`（AES-128 CTR，64 位密钥）而不是 `ctrp256`（AES-256 = ALAC 无损）。数字是密钥长度，不是音频码率。

### 步骤 3 —— 提取加密密钥

在媒体播放列表中：
```
#EXT-X-KEY:METHOD=SAMPLE-AES-CTR,URI="skd://...",KEYFORMAT="com.apple.streamingkeydelivery",KEYFORMATVERSIONS="1"
```

把 `URI` 重写到你的代理：`skd://` → `https://your-proxy/key/...`

`skd://` 之后的 `URI` 值就是 `mp4decrypt` 用来获取 AES 密钥的密钥 URI。

**获取密钥**：
```
POST {keyUri}
  headers:
    Authorization: Bearer {jwt}
    Media-User-Token: {mut}
    Content-Type: application/json
  body: { "challenge": "..." }   ← 如果是 SAMPLE-AES，则为 base64 的 Widevine/FairPlay challenge
```

对于 AES-128 曲目（更简单），密钥直接以二进制返回。

### 步骤 4 —— 下载分片

解析媒体播放列表中的 `#EXTINF` + URL 行，以及 `#EXT-X-MAP` 初始化分片。

对于**多分片**播放列表：
- 单独下载初始化分片（`#EXT-X-MAP URI="..."`）
- 并行下载所有 `#EXTINF` 分片（`asyncio.gather`）
- 拼接：init + segment1 + segment2 + ...

只抓初始化分片会导致多分片曲目出现断断续续/无声的音频。

### 步骤 5 —— 解密

```bash
mp4decrypt --key {trackId}:{hexKey} input.mp4 output_decrypted.mp4
```

`mp4decrypt` 来自 Bento4。密钥通过步骤 3 的密钥 URI 获取。

### 步骤 6 —— 重封装为可拖动的 MP4

```bash
# 单分片（无时间戳间隙）：
ffmpeg -i decrypted.mp4 -c copy -movflags +faststart output.mp4

# 多分片（修复边界处的时间戳间隙）：
ffmpeg -i decrypted.mp4 -af aresample=async=1 -movflags +faststart output.mp4
```

`+faststart` 把 `moov` atom 移到文件开头 → HTTP Range 请求可用 → ExoPlayer 可以立即跳转，无需缓冲整个文件。

多分片曲目在分片边界处存在时间戳间隙，会导致 ExoPlayer 抛出 `UnexpectedDiscontinuityException`。`aresample=async=1` 通过重采样填补间隙来修复。对 AAC 来说重编码很快。

---

## 动态封面（动画封面）

```
GET /v1/catalog/{sf}/songs/{id}
  params: （无需特殊参数）
  → song.relationships.albums.data[0].id   → 专辑 ID

GET /v1/catalog/{sf}/albums/{albumId}
  params: extend=editorialVideo
  → album.attributes.editorialVideo.motionDetailSquare.video   → HLS URL
  → album.attributes.editorialVideo.motionSquareVideo1x1.video → HLS URL（回退）
```

并非所有专辑都有动态封面。字段缺失时返回 `null`。该 HLS URL 是一段静音循环视频，设计用于在专辑封面上播放。

---

## 歌词

### Apple（首选）

```
GET /v1/catalog/{sf}/songs/{id}/syllable-lyrics    ← 逐词 TTML
GET /v1/catalog/{sf}/songs/{id}/lyrics             ← 逐行 TTML（回退）
  headers: Authorization + MUT
```

响应是 TTML XML。解析结构：
```xml
<tt>
  <body>
    <div>
      <p begin="12.5s" end="15.2s">
        <span begin="12.5s" end="13.0s">Word</span>
        <span ttm:role="x-bg" begin="12.5s" end="15.2s">Background vocals</span>
      </p>
    </div>
  </body>
</tt>
```

- 时间戳带尾缀 `s` —— 去掉它，按浮点秒解析 → 乘 1000 得到毫秒
- `<tt>` 中的 `itunes:timing="Word"` = 逐词同步；`"Line"` = 仅逐行同步
- `ttm:role="x-bg"` 的 span = 背景和声（不同方式渲染，偏移 -300ms）
- 命名空间前缀各异（`tt:span` vs `span`）—— 匹配时要能容忍命名空间

### 回退：lrclib.net（无需认证，逐行同步）

```
GET https://lrclib.net/api/get
  params: track_name={title}&artist_name={artist}&duration={durationSeconds}
  response: { syncedLyrics: "[mm:ss.xx] Line text\n..." }
```

解析 `[mm:ss.xx]` 时间戳：
```
[01:23.45] → (1*60 + 23.45) * 1000 = 83450 ms
```

---

## 分页

所有列表端点都支持游标式分页：
```json
{ "data": [...], "next": "/v1/me/library/songs?offset=100&limit=100" }
```

`next` 是路径（不是完整 URL）。在前面加上 `https://amp-api-edge.music.apple.com`。持续请求直到 `next` 为 null 或不存在。大多数端点每页上限 100 条。

---

## 速率限制 / 坑点

- 没有文档化的速率限制，但持续约 100 req/s 会收到 429
- Bearer JWT 从 `music.apple.com` 的 JS bundle 抓取；Apple 重新部署时会变化，但通常能稳定数天
- `amp-api-edge` 比 `amp-api.music.apple.com`（非 edge）更快、更可靠
- 资料库条目（`i.`、`l.`、`r.`、`p.`）始终需要 MUT，且必须使用 `/v1/me/library/...` 路径 —— 用资料库 ID 请求目录端点会返回 404
- 资料库请求上的 `include=catalog` 会内联嵌入真实的目录条目 —— 始终请求它，以获取封面和规范 ID
- 封面 URL 中的 `{f}` 必须替换为 `"jpg"` —— 请求 `webp` 也可以，但 `jpg` 最通用
- 流解密首次请求需要 5–10 秒；按歌曲 ID 缓存输出文件，避免拖动/重连时重复解密
