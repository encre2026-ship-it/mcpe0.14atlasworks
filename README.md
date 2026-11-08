# 图集工坊 AtlasWorks

把散图材质包合成 Minecraft Bedrock 0.14.x 认的 `terrain-atlas` / `items-opaque` 图集，
同时生成 `.meta` 和 mip 链。

## 功能

- 按文件名自动匹配贴图：精确名 → 别名表 → 归一化 → 后缀 → 相似度模糊匹配
- 倍率 1× / 2× / 4×（terrain 512×256 → 2048×1024，items 1024×1024 → 4096×4096）
- 多格族（剑/工具/盔甲/羊毛/木板等）按固定顺序表填格
- 去饱和 0–100%，用于色差过大的材质包
- 输出：`.tga` + `.png` 预览 + `.meta` + `terrain-atlas_mip0~3.tga` + `assets/images/` 成套 + `loose/` 散图 + 分配表 CSV
- 全离线；结果写到 `Download/图集工坊/<包名>_<倍率>x/`，Android 10+ 不需要存储权限

## 构建

```bash
# 1) 从你自己的 0.14.x APK 里取参考图集（仓库不附带版权素材）
python3 tools/extract_refs.py /path/to/minecraft-0.14.3.apk

# 2) 准备 API 29+ 的 android.jar
curl -LO https://dl.google.com/android/repository/platform-32_r01.zip
unzip -q platform-32_r01.zip -d /tmp/p32

# 3) 打包
sh build.sh
```

需要 JDK 17、aapt2、d8、apksigner、zip/unzip。不需要 Gradle 和 Android Studio。

## 声明

- **输出的图集可能有瑕疵。** 匹配靠文件名、别名和模糊相似度，不是像素级识别，
  所以可能出现：个别方块/物品没换上、贴图错格、多格族顺序与你的材质包不一致、
  高清包缩图后边缘发糊。用之前请先查看生成的 `.png` 预览，或在游戏里核对。
- 倍率越高越吃内存（4× 峰值约 400 MB），低配机建议 1×。
- 仓库不含 Mojang 美术资源，参考图集由 `tools/extract_refs.py` 从你自己持有的 APK 生成；
  请勿把生成结果提交到公开仓库。
- 代码以 MIT 协议开源，与 Mojang 无关。

## 作者

本项目由 **DeepSeek** 编写。
