package io.github.acnxo.amc.data.openapi

/**
 * OpenAPI 鉴权错误码的说明文案。
 *
 * 平台返回 `{code, message, data}` 信封，鉴权类错误码集中在 19001~19010，
 * 这里把它们翻译成可直接展示给用户的中文提示。
 */
object OpenApiError {

    /** 成功码 */
    const val CODE_OK = 1

    /** 业务码对应的排查建议；未知码返回 null。 */
    fun hint(code: Int): String? = when (code) {
        19001 -> "缺少签名凭证，请先在内容服务节点中填写 AK/SK"
        19002 -> "签名凭证无效：AK 错误或未开通"
        19003 -> "凭证不可用：已停用、吊销或过期"
        19004 -> "来源 IP 不在白名单"
        19005 -> "签名校验失败：请核对 SK 是否正确"
        19006 -> "时间戳偏差过大：请检查设备时间是否准确"
        19007 -> "请求重复：nonce 被复用"
        19009 -> "该凭证未被授权访问此接口"
        19010 -> "服务端鉴权服务未就绪"
        else -> null
    }

    /** 组合成「消息（code=xxx，HTTP yyy）：排查建议」形式的可读文本。 */
    fun describe(code: Int, message: String, httpStatus: Int): String = buildString {
        append(message.ifBlank { "业务错误" })
        append("（code=").append(code)
        if (httpStatus !in 200..299) append("，HTTP ").append(httpStatus)
        append("）")
        hint(code)?.let { append("：").append(it) }
    }
}
