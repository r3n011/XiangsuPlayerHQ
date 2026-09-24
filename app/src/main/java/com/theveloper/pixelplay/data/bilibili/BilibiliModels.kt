package com.theveloper.pixelplay.data.bilibili

import kotlinx.serialization.Serializable

@Serializable
data class BilibiliSongInfo(
    val id: String = "",
    val bvid: String = "",
    val aid: Long = 0L,
    val cid: Long = 0L,
    val name: String = "",
    val singer: String = "",
    val albumName: String = "",
    val duration: Long = 0L,
    val pic: String = "",
    val playUrl: String = ""
)

@Serializable
data class BilibiliSearchResult(
    val isEnd: Boolean = true,
    val list: List<BilibiliSongInfo> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

@Serializable
data class BilibiliVideoDetail(
    val aid: Long = 0L,
    val bvid: String = "",
    val title: String = "",
    val duration: Long = 0L,
    val pic: String = "",
    val cid: Long = 0L
)

/**
 * 单条 B 站评论（对齐 PiliPlus ReplyItemModel 的核心字段）。
 * 来源：/x/v2/reply/main（web 未登录接口）。
 * 新增字段对齐 PiliPlus：root/parent（楼中楼）、emotes（表情）、pictures（图片）、
 * subReplies（内嵌子回复）、liked（reply_control.like_state）、isUp/isUpTop（UP 置顶）。
 */
@Serializable
data class BilibiliComment(
    val rpid: Long = 0L,
    val mid: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String = "",
    val level: Int = 0,
    val message: String = "",
    val like: Int = 0,
    val replyCount: Int = 0,
    val ctime: Long = 0L,
    val isTop: Boolean = false,
    // —— 对齐 PiliPlus 新增 ——
    val root: Long = 0L,
    val parent: Long = 0L,
    val isUp: Boolean = false,
    val isUpTop: Boolean = false,
    val liked: Boolean = false,
    // —— 对齐 PiliPlus replyControl.action / member.vip / reply_control.location / dialog ——
    val action: Int = 0,          // 0=未操作 1=已赞 2=已踩
    val vipType: Int = 0,         // member.vip.vipType（2=年度大会员）
    val location: String = "",    // reply_control.location（如「IP属地：上海」）
    val dialog: Long = 0L,        // 对话 id（楼中楼会话根）
    val emotes: Map<String, String> = emptyMap(),
    val pictures: List<String> = emptyList(),
    val subReplies: List<BilibiliComment> = emptyList()
)

/**
 * B 站评论分页结果。mode=3 按热度 / mode=2 按时间，
 * 游标分页使用 nextOffset（JSON 中的 offset 字符串）。
 * upMid 为视频 UP 主 uid（data.upper.mid），用于识别 UP 本人评论与置顶权限。
 * error 非空表示接口返回错误（评论可匿名浏览，不需要登录）。
 */
@Serializable
data class BilibiliCommentResult(
    val comments: List<BilibiliComment> = emptyList(),
    val topComments: List<BilibiliComment> = emptyList(),
    val hasMore: Boolean = false,
    val nextOffset: String = "",
    val upMid: Long = 0L,
    val error: String = ""
)

/**
 * 楼中楼（子回复）分页结果，对齐 PiliPlus ReplyReplyData。
 * 来源：/x/v2/reply/reply?oid&root&pn&type=1&sort=1
 */
@Serializable
data class BilibiliReplyRepliesResult(
    val replies: List<BilibiliComment> = emptyList(),
    val hasMore: Boolean = false,
    val nextPage: Int = 1,
    val error: String = ""
)

/**
 * 评论区互动状态（对齐 PiliPlus ReplyInteractData / InteractStatus）。
 * 来源：/x/v2/reply/subject/interaction-status
 */
@Serializable
data class BilibiliReplyInteraction(
    val selectionEnabled: Boolean = false,  // 评论精选是否开启（up_reply_selection.status==1）
    val selectionCanModify: Boolean = false,
    val replyEnabled: Boolean = false,      // 评论是否开启（up_reply.status==1）
    val replyCanModify: Boolean = false
)

/**
 * 评论交互操作结果（发布/点赞/举报）。
 */
@Serializable
data class BilibiliCommentActionResult(
    val success: Boolean = false,
    val message: String = "",
    val rpid: Long = 0L
)

/**
 * 极验人机验证的 challenge 参数（gee_gt/gee_challenge/recaptcha_token）。
 * 对应 PiliPlus captchaData：gt/challenge 用于初始化极验，token 用于随后的接口请求。
 */
@Serializable
data class BilibiliCaptchaChallenge(
    val gt: String = "",
    val challenge: String = "",
    val token: String = ""
)

/**
 * 发送短信验证码结果。captchaKey 为登录时的凭证（对应 PiliPlus data.captcha_key）。
 * 需要人机验证时 captchaRequired=true 且 challenge 非空。
 */
@Serializable
data class BilibiliSmsSendResult(
    val success: Boolean = false,
    val message: String = "",
    val captchaRequired: Boolean = false,
    val captchaKey: String = "",
    val challenge: BilibiliCaptchaChallenge? = null
)

/**
 * 扫码登录：第一步生成的二维码信息。
 */
@Serializable
data class BilibiliQrCodeResult(
    val qrcodeKey: String = "",
    val url: String = ""
)

/**
 * 扫码登录：轮询结果。
 * code：0=登录成功（cookies 非空）；86101=未扫码；86090=已扫码未确认；86038=已过期。
 */
@Serializable
data class BilibiliLoginPollResult(
    val success: Boolean = false,
    val code: Int = -1,
    val message: String = "",
    val cookies: Map<String, String> = emptyMap()
)

/**
 * 登录用户信息（/x/web-interface/nav）。
 */
@Serializable
data class BilibiliUserInfo(
    val uid: Long = 0L,
    val uname: String = "",
    val face: String = ""
)

/**
 * B 站收藏夹（/x/v3/fav/folder/created/list-all）。
 */
@Serializable
data class BilibiliFavoriteFolder(
    val id: Long = 0L,
    val title: String = "",
    val mediaCount: Int = 0
)

/**
 * 收藏夹内的单个视频（/x/v3/fav/resource/list），可当作歌曲播放。
 */
@Serializable
data class BilibiliFavoriteVideo(
    val aid: Long = 0L,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val upName: String = "",
    val duration: Long = 0L,
    val cid: Long = 0L
)

/** 收藏夹内视频分页结果（供列表滚动加载更多） */
data class BilibiliFavoritePage(
    val videos: List<BilibiliFavoriteVideo>,
    val hasMore: Boolean,
    val total: Int = 0
)