# MUT 流程 —— 搜索、资料库、播放列表曲目

[English](mut-flow.md) | **简体中文**

## MUT 存放在哪里

- **Android**：`MutPreferences`（SharedPreferences）。`NetworkModule` 中的 OkHttp 拦截器会自动给每个 Retrofit 请求加上 `X-Music-User-Token` 头。
- **服务器**：保存在内存中，并持久化到 `auth-state.json`。应用启动时会从 Android 同步过来，用户通过手机 Web 服务器（8080 端口）设置时也会同步。
- **服务器读取 MUT**：`resolveMUT(c)` → 优先使用请求中的 `X-Music-User-Token` 头，否则回退到服务器存储的 MUT。

---

## 1. 搜索

**路由**：`GET /api/search?term=...`
**文件**：`server/src/routes/search.ts`

搜索使用 `@syncfm/applemusic-api` SDK（`music.Search.search`），它只使用抓取到的 bearer JWT。**不传 MUT**。这意味着：

- 结果仅限目录内容（不混入个人资料库条目）
- 没有个性化（没有「为你推荐」的加权）
- 无需 MUT 即可工作 —— 仅 bearer JWT 就够了

`normaliseSong` 从 `relationships.artists.data[0].id` 中提取 `artistId`，以便从搜索结果跳转到艺人页。

---

## 2. 资料库

**路由**：`GET /api/library/songs|albums|playlists|artists`
**文件**：`server/src/routes/library.ts`

所有资料库路由都会调用 `guard(c)`，后者调用 `resolveMUT(c)`。MUT 缺失 → **401**。MUT 是必需的。

### 歌曲（`/library/songs`）
```
GET /v1/me/library/songs
  params: { limit: 100, include: "catalog" }
  headers: Authorization + Music-User-Token + Media-User-Token
```
分页最多 2000 首。`include: "catalog"` 参数要求 Apple 把目录条目嵌入到 `relationships.catalog.data[0]` —— 这样能拿到真实的目录 ID（而不是 `i.xxx`）、更好的封面和完整的元数据。每个条目直接调用 `normaliseSong`（不是 `normaliseLibrarySong` —— 那只用于播放列表曲目）。

### 专辑（`/library/albums`）
与歌曲相同：用 `include: "catalog"` 分页，通过 `normaliseAlbum` 映射。

### 播放列表（`/library/playlists`）
```
GET /v1/me/library/playlists
  params: { limit: 100, include: "catalog" }
```
使用 `normaliseLibraryPlaylist`，它优先取 `attr.artwork.url` → `catalog.attributes.artwork.url`（资料库播放列表本身通常没有封面，封面来自目录关系）。

返回播放列表对象 —— 而不是它们的曲目。曲目是单独的调用（见下文）。

### 艺人（`/library/artists`）
```
GET /v1/me/library/artists
  params: { limit: 100 }
  // 没有 include: "catalog" —— 艺人封面另行从目录获取
```

---

## 3. 播放列表曲目

**路由**：`GET /api/library/playlists/:id/tracks`
**文件**：`server/src/routes/library.ts` → `library.get("/playlists/:id/tracks", ...)`

ID 前缀决定调用哪个 Apple 端点：

### `pl.xxx` —— 目录 / 编辑 / 分享播放列表
```
GET /v1/catalog/{storefront}/playlists/{id}/tracks
  params: { limit: 100, offset }
  headers: Authorization + MUT
```
使用**目录**端点。MUT 仍会发送（用于可能的个性化 / 权益判断），但内容是公开的。每个曲目调用 `normaliseSong`。

### 其他任何 ID（用户资料库播放列表，例如 `p.xxx`）
```
GET /v1/me/library/playlists/{id}/tracks
  params: { limit: 100, offset, include: "catalog" }
  headers: Authorization + MUT
```
使用**资料库**端点。这里的 `include: "catalog"` 至关重要：资料库曲目条目带有 `i.xxx` ID 且元数据很少。`relationships.catalog.data[0]` 中嵌入的目录条目提供了真实的目录 ID 和完整属性。

此处使用 `normaliseLibrarySong`：
```ts
function normaliseLibrarySong(s: any) {
  const cat = s.relationships?.catalog?.data?.[0];
  if (cat) return normaliseSong(cat);   // 使用目录条目（真实 ID、完整元数据）
  return normaliseSong(s);              // 回退：带 i.xxx ID 的资料库条目
}
```

当成功解析出目录条目时，歌曲获得正式的目录 ID → 流媒体路由使用目录端点。当回退到资料库条目时，ID 仍为 `i.xxx` → 流媒体路由会单独处理 `i.` 前缀，走资料库流媒体端点。

---

## 汇总

| 流程 | 需要 MUT | Apple 端点 |
|---|---|---|
| 搜索 | 否 | 目录（通过 SDK） |
| 资料库歌曲/专辑/艺人 | 是（否则 401） | `/v1/me/library/...` |
| 资料库播放列表列表 | 是 | `/v1/me/library/playlists` |
| `pl.xxx` 曲目 | 是（发送，但非严格必需） | `/v1/catalog/{sf}/playlists/{id}/tracks` |
| 用户资料库播放列表曲目 | 是 | `/v1/me/library/playlists/{id}/tracks` |

## 关键坑点

- 资料库条目必须使用 `include: "catalog"` —— 否则会缺少封面和真实的目录 ID
- 资料库歌曲 ID（`i.xxx`）绝不能换成目录 ID —— 流媒体路由两者都能处理，但它们走的是不同的 Apple 端点
- MUT 以请求头优先的方式转发（Android OkHttp 的 `X-Music-User-Token` → 服务器 `resolveMUT`），因此服务器总是使用最新的 token
- Bearer JWT 在服务器启动时抓取；MUT 必须由用户通过手机 Web 服务器（8080 端口）单独设置
