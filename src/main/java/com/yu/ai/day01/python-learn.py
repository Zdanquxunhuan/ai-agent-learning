# for i in range(3):
#     print(i)


# messages = ["hello","tell me about java "]
# messages.append("what is  hashmap")
# print(messages[1])
# print(len(messages))

# messages={
# "role":"user",
# "content":"hello"
# }
# print(messages["role"])
# print(messages["content"])

# request_data = {
#     "model": "ds",
#     "messages": [
#         {
#             "role": "system",
#             "content": "you are a java teacher"
#         },
#         {
#             "role": "user",
#             "content": "explain hashmap"
#         }
#     ],
#     "stream": False
# }
#
# print(request_data["messages"][1]["content"])
#
# def build_prompt(question):
#     return "please answer the question: " + question
#
#
# result = build_prompt("what is agent")
# print(result)


# def add_message(message, history=[]):
#     history.append(message)
#     return history
def add_message(message, history=None):
    if history is None:
        history = []

    history.append(message)
    return history
print(add_message("你好"))
print(add_message("什么是Agent？"))





















