# fonts-demo

一个用于学习 TrueType 字体文件结构和字形渲染过程的 Java 示例工程。

工程不依赖第三方字体解析库，直接使用 `RandomAccessFile` 读取字体文件中的 sfnt table，并从 `cmap`、`loca`、`glyf` 等表中查找和解析字形轮廓。项目同时提供 Java2D 对照示例，以及一个不使用 `java.awt.Font` 字形 API 的手工渲染示例。

## 功能概览

- 读取并打印 TTF 的 sfnt table directory。
- 解析 `head`、`maxp`、`hhea`、`hmtx`、`cmap`、`loca`、`glyf` 等常用表。
- 根据 Unicode code point 查找 glyph ID。
- 读取简单字形的轮廓点和 contour。
- 将二次贝塞尔曲线离散化为线段并绘制字形轮廓。
- 使用 Swing 显示解析后的字形轮廓。
- 对比 Java2D 标准字体渲染、轮廓渲染和手工光栅化结果。

## 环境要求

- JDK 21 或更高版本。
- Gradle Wrapper 已包含在项目中，无需单独安装 Gradle。

项目源码使用了 `List.getFirst()` 等较新的 Java API，因此不支持直接使用 JDK 8 编译运行。

## 快速开始

先编译项目：

```bash
./gradlew classes
```

### 运行基础解析示例

`Demo01` 默认从项目根目录读取 `test.ttf`，解析汉字“赵”的 glyph，并在控制台输出字体表、字形信息和轮廓点信息：

```bash
./gradlew classes
java -cp build/classes/java/main:build/resources/main com.cell.demo.fonts.Demo01
```

该示例会打开 Swing 窗口，因此需要在支持图形界面的环境中运行。

### 运行 Java2D 对照示例

`RenderLetterA` 使用 Java2D 生成三栏对照图，默认绘制字母 `A` 并输出 `letter-a.png`：

```bash
java -cp build/classes/java/main:build/resources/main \
  com.cell.demo.fonts.RenderLetterA test.ttf letter-a.png
```

如果不传字体文件，则使用 Java 的逻辑字体：

```bash
java -cp build/classes/java/main:build/resources/main \
  com.cell.demo.fonts.RenderLetterA
```

### 运行手工 TTF 渲染示例

`ManualTtfLetterA` 不使用 `java.awt.Font`、`GlyphVector`、`Shape`、`Path2D` 或 `drawString`，而是手工完成以下流程：

```text
sfnt -> cmap -> glyph ID -> loca -> glyf -> contours
     -> quadratic Bézier flattening -> winding fill -> supersampling -> PNG
```

编译并运行：

```bash
javac -d build/manual \
  src/main/java/com/cell/demo/fonts/ManualTtfLetterA.java

java -Djava.awt.headless=true \
  -cp build/manual \
  com.cell.demo.fonts.ManualTtfLetterA \
  test.ttf manual-a.png 0041
```

第三个参数是十六进制 Unicode code point，也可以写成 `U+0041`。运行后会生成：

- `manual-a.png`：手工光栅化结果。
- `manual-a-debug.png`：带轮廓、控制点和包围盒的调试图。

## 项目结构

```text
src/main/java/com/cell/demo/fonts/
├── Demo01.java                 # 分阶段解析 test.ttf，并显示字形轮廓
├── ManualTtfLetterA.java       # 不使用 Java2D 字形 API 的手工 TTF 渲染器
├── RenderLetterA.java           # Java2D、轮廓和 Path2D 渲染对照示例
├── helper/
│   ├── BigUnsignedHelper.java   # 无符号整数读取
│   ├── ByteHelper.java          # 字节输出辅助
│   └── GlyphPointHelper.java    # 字形点和贝塞尔曲线处理
├── model/
│   ├── CmapSubTable.java
│   ├── EncodingRecord.java
│   ├── GlyphPoint.java
│   └── TableRecord.java         # 字体表和字形相关模型
└── swing/
    └── FontPanel.java           # Swing 字形轮廓面板
```

## 示例字体和输出文件

项目根目录中的字体和图片用于示例或调试：

- `test.ttf`：基础 TTF 解析和渲染示例使用的字体。
- `letter-a.png`、`output.png`、`output-debug.png`：示例输出图片。
- `letter-a.png` 等输出文件可以重新生成，不属于源码的一部分。

## 当前限制

- 目前重点支持单文件 TTF，以及 `glyf` 表中的简单字形。
- 复合字形（`numberOfContours < 0`）暂未实现，涉及组件递归和变换矩阵。
- `ManualTtfLetterA` 支持 `cmap` format 4 和 format 12，但不是完整的 OpenType/TrueType 解析器。
- `Demo01` 的输入文件路径当前写死为项目根目录下的 `test.ttf`。
- CFF/OpenType `CFF` 字体、字体 hinting、完整 kerning 和 TTC collection 尚未覆盖。

## 参考的 TrueType 解析路径

对一个 Unicode 字符进行解析时，核心路径如下：

1. 读取 sfnt 文件头和 table directory。
2. 在 `cmap` 中将 Unicode code point 映射为 glyph ID。
3. 通过 `loca` 根据 glyph ID 找到 `glyf` 中的字形数据。
4. 读取 contour 的端点、flags 和坐标增量。
5. 根据 on-curve/off-curve 点重建轮廓和二次贝塞尔曲线。
6. 将轮廓绘制到 Swing 画布或 PNG 图片中。

依赖主要用于示例输出的 JSON 格式化；字体二进制解析本身由项目代码完成。
