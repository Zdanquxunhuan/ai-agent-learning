
import json


# 1. 构造消息列表
def build_messages(question, history=None):
    if not isinstance(question, str) or not question.strip():
        raise ValueError("问题不能为空")

    messages = [
        {
            "role": "system",
            "content": "你是一名Python工程师，请用中文回答。"
        }
    ]

    if history is not None:
        messages.extend(history)

    messages.append({
        "role": "user",
        "content": question
    })

    return messages


# 2. 构造模型API请求
def build_payload(question, history=None):
    return {
        "model": "deepseek-flash",
        "messages": build_messages(question, history),
        "stream": False
    }


# 3. 提取模型回答
def extract_content(response):
    try:
        content = response["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as error:
        raise ValueError("模型响应结构异常") from error

    if not isinstance(content, str):
        raise ValueError("模型回答不是字符串")

    return content

# 返回消息总数
def count_message(messages):



# 4. 程序入口
def main():
    history = [
        {
            "role": "user",
            "content": "什么是HashMap？"
        },
        {
            "role": "assistant",
            "content": "HashMap是Java中的键值存储结构。"
        },
        {
            "role": "user",
            "content": "you can speak English？"
        },
        {
            "role": "assistant",
            "content": "yes of course。"
        }
    ]

    payload = build_payload("它是线程安全的吗？", history)

    print("=== API请求 ===")
    print(json.dumps(payload, ensure_ascii=False, indent=2))

    # 使用模拟响应，不实际调用API
    mock_response = {
        "choices": [
            {
                "message": {
                    "role": "assistant",
                    "content": "Python中的dict是键值对数据结构。"
                }
            }
        ]
    }

    answer = extract_content(mock_response)

    print("=== 模型回答 ===")
    print(answer)


if __name__ == "__main__":
    main()
