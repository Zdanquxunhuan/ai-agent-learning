# Day 9 文档解析样本记录

| 样本 | 格式 | 预期结果 |
|---|---|---|
| sample-01.txt | TXT | 提取中文文本 |
| sample-02.txt | TXT | 提取英文及技术术语 |
| sample-03.pdf | PDF | 提取两页文本，页码分别为 1、2 |
| sample-04.docx | DOCX | 提取标题 Architecture 和正文段落 |
| sample-05-empty.pdf | PDF | 提取结果为空文本，不发生异常 |

## DOCX 预期文本

Architecture
The parser extracts paragraphs and heading metadata.

## PDF 预期文本

RAG PDF sample page one.
Second page preserves page number.

## 已知限制

- 暂不支持 OCR。
- 空白 PDF 不代表扫描版 PDF 的处理已经完成。
- DOCX 当前不计算实际页码。