# 国内地图与坐标纠偏

本分支在上游 [0xfnzero/gps-locator](https://github.com/0xfnzero/gps-locator) 的基础上，针对中国大陆使用做了以下适配：

- 默认底图改为**高德地图**（标准 / 卫星），国内加载快、无需 Key
- 可选**天地图**（矢量 / 影像，带注记层），需要免费开发者 Key
- 保留 **OpenStreetMap**，海外使用或自定义瓦片服务时可切回
- **GCJ-02 ↔ WGS-84 自动纠偏**：在高德底图上选点、画路线不再偏移几百米
- 坐标输入框支持 **WGS-84 / GCJ-02 / BD-09** 三种格式，可直接粘贴从高德、腾讯、百度地图复制的坐标
- 国内地点搜索可使用**高德 Web 服务 API**（填写 Key 后自动启用，否则回退到 Nominatim）

入口：主界面右上角 **设置 → 地图与坐标**。

## 为什么需要纠偏

中国大陆的地图服务按规定使用加密坐标系 GCJ-02（俗称"火星坐标"），百度在此基础上又加了一层 BD-09。而 Android 系统、GPS 芯片、GPX/KML 文件使用的都是 WGS-84。两者在国内相差约 100–700 米。

如果直接在高德底图上取点并注入系统，所有 App 看到的位置都会偏一段距离。本分支的处理方式：

| 环节 | 坐标系 |
|---|---|
| 存储（收藏、历史、路线、偏好） | WGS-84（与上游一致，数据兼容） |
| 注入系统模拟定位 | WGS-84 |
| GPX / KML 导入导出 | WGS-84 |
| 高德底图上的显示与选点 | GCJ-02，进出地图时自动转换 |
| 天地图 / OSM 底图 | WGS-84（天地图使用 CGCS2000，与 WGS-84 差异可忽略） |
| 坐标输入框 | 可选 WGS-84 / GCJ-02 / BD-09，读入后立即转成 WGS-84 |
| 高德搜索结果 | GCJ-02，解析时转成 WGS-84 |

转换只在大陆范围内生效，境外坐标原样保留。GCJ-02 → WGS-84 使用迭代求精的精确反解（往返误差 < 1 cm），而不是常见的一步近似（误差 1–2 m）。

## 配置 Key

两种方式任选：

**1. App 内填写**（推荐）：设置 → 地图与坐标 → 天地图 Key / 高德搜索 Key。

**2. 编译时内置**：在 `gradle.properties` 或环境变量中设置：

```properties
GPS_TIANDITU_KEY=你的天地图Key
GPS_AMAP_WEB_KEY=你的高德Web服务Key
```

App 内填写的值优先于编译时内置的值。

### 天地图 Key

1. 登录 <https://console.tianditu.gov.cn/>，进入"应用管理"创建应用
2. Key 类型选择 **服务端**（瓦片请求不带浏览器 Referer，选"浏览器端"会被拒绝）
3. 未填写 Key 时选择天地图，会先弹出 Key 输入框；Key 为空时自动回退到高德底图

### 高德 Web 服务 Key

1. 登录 <https://console.amap.com/>，"应用管理 → 我的应用"创建应用
2. 添加 Key 时服务平台选择 **Web服务**（不是 Android 平台）
3. 个人开发者免费额度足够日常使用
4. 仅在搜索国家选择"中国"时使用；其他国家仍走 Nominatim

高德底图瓦片本身不需要 Key。

## 代码改动一览

| 文件 | 说明 |
|---|---|
| `geo/CoordinateTransform.kt` | 新增。WGS-84 / GCJ-02 / BD-09 互转 |
| `geocoding/MapSource.kt` | 新增。可选底图定义（瓦片地址、坐标系、注记层、是否需要 Key） |
| `geocoding/MapSettings.kt` | 新增。底图、输入坐标格式、Key 的持久化 |
| `geocoding/MapServiceConfiguration.kt` | 重写。应用底图和注记层，提供 `MapCoordinateAdapter` 做地图边界的坐标转换 |
| `geocoding/AmapGeocoder.kt` | 新增。高德地点搜索，以及按国家自动选择高德/Nominatim 的 `ChinaAwareGeocoder` |
| `geocoding/AmapResponseParser.kt` | 新增。解析高德接口返回并转为 WGS-84 |
| `MainActivity.kt` | 地图中心读写、跳转、输入框读写经过坐标转换；设置菜单新增"地图与坐标" |
| `routes/RouteEditorActivity.kt` | 路线编辑器的取点、折线、标记、缩放范围经过坐标转换 |
| `app/build.gradle.kts` | 新增 `GPS_TIANDITU_KEY`、`GPS_AMAP_WEB_KEY` 构建参数 |
| `res/values*/strings.xml` | 中、英、日、西四种语言的新文案 |
| `src/test/.../CoordinateTransformTest.kt` | 新增。对照 coordtransform 参考值、往返精度、境外不变 |
| `src/test/.../AmapResponseParserTest.kt` | 新增。高德返回解析，含空数组字段、错误码 |

运行单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

## 注意事项

- 高德、天地图瓦片的使用需遵守各自的服务条款，本项目仅用于开发测试
- 港澳台地区在高德底图上实际为 WGS-84，但处于常用的"大陆范围"矩形内，会被当作 GCJ-02 处理。在这些地区建议切换到天地图或 OSM 底图
- 切换底图时，当前选中的点保持不动（按真实坐标重新定位）
