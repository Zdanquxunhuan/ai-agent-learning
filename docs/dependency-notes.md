# Day 9 文档解析依赖记录

## 1. PDF 解析

- 依赖：Apache PDFBox
- Maven 坐标：org.apache.pdfbox:pdfbox
- 版本：3.0.8
- 用途：提取 PDF 文档中的文本及页码信息
- 官方网站：https://pdfbox.apache.org/

## 2. DOCX 解析

- 依赖：Apache POI
- Maven 坐标：org.apache.poi:poi-ooxml
- 版本：5.5.1
- 用途：读取 DOCX 文档中的段落和标题信息
- 官方网站：https://poi.apache.org/

## 3. 实现边界

- JDK：17
- 暂不实现 OCR
- 扫描版 PDF 如果没有可提取的文本，需要明确提示用户