
# 用户咨询文本
message = "我的订单为什么还没有发货？"

# 简单意图识别函数
def identify_intent(text):
    if "发货" in text:
        return "SHIPPING_QUERY, yuge is niubi and handsome, let is fucking go . this is not my destination, so i will keep going"
    elif "退款" in text:
        return "REFUND"
    else:
        return "OTHER"

# 调用函数
result = identify_intent(message)

print("用户输入：", message)
print("识别结果：", result)
