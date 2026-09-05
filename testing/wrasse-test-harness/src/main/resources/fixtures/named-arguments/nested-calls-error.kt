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
    out.add(ParsedTags.AssetTag(t, FeedRelatedAssetTag.SpotDelisting(AssetDelistingTag(null, null))))
}

// expect-error 18:12 named-arguments "Positional arguments should be named"
// expect-error 18:32 named-arguments "Positional arguments should be named"
// expect-error 18:69 named-arguments "Positional arguments should be named"
// expect-error 18:87 named-arguments "Positional arguments should be named"
