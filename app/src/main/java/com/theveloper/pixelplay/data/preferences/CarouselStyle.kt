package com.theveloper.pixelplay.data.preferences

object CarouselStyle {
    const val NO_PEEK = "no_peek"
    const val ONE_PEEK = "one_peek"
    const val TWO_PEEK = "two_peek"

    /**
     * 预览轮播：仿首页精选轮播（平板形态）的多浏览布局 —— 中间一张大封面、两侧各露出一张
     * 圆角缩略图（只显示封面，不带歌名 / 播放按钮）。
     */
    const val PREVIEW = "preview"

    /** 主页「为你推荐」：一屏 3 张完整卡片 + 右侧 1 张预览。 */
    const val THREE_ITEMS_PREVIEW = "three_items_preview"
}
