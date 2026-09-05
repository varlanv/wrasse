package sample

class AssetDelistingTag(val haltTradeTime: Long?, val fullDelistTime: Long?)

sealed class FeedRelatedAssetTag {
    class SpotDelisting(val tag: AssetDelistingTag) : FeedRelatedAssetTag()
}

object ParsedTags {
    class AssetTag(val asset: String, val tag: FeedRelatedAssetTag)
}

class Sink {
    fun add(item: ParsedTags.AssetTag): Boolean = item.asset.isNotEmpty()
}

fun parse(t: String, out: Sink) {
    out.add(item = ParsedTags.AssetTag(asset = t, tag = FeedRelatedAssetTag.SpotDelisting(tag = AssetDelistingTag(haltTradeTime = null, fullDelistTime = null))))
}