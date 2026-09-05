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
    out.add(ParsedTags.AssetTag(asset = t, tag = FeedRelatedAssetTag.SpotDelisting(AssetDelistingTag(haltTradeTime = null, fullDelistTime = null))))
}