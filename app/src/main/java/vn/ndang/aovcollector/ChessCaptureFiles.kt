package vn.ndang.aovcollector

/** File/display names only. Keep the original chess token when choosing its collection folder. */
object ChessCaptureFiles {
    private val slug=Regex("[a-z0-9][a-z0-9-]{0,89}")
    private val detailToken=Regex("(?:[0-9]{4,}-)?chess__quan-co__tat-ca-quan-co__tuong__([a-z0-9][a-z0-9-]{0,89})__([0-9]{3,})")
    private val head=Regex("[a-z0-9][a-z0-9-]{0,89}-head")
    private val savedHead=Regex("[a-z0-9][a-z0-9-]{0,89}-head(?:-[a-f0-9]{14}(?:-(?:[2-9]|[1-9][0-9]+))?)?")
    data class Detail(val hero:String,val page:Int)

    /** Other chess topics and all non-chess captures retain their established filenames. */
    fun screenshotBase(token:String):String {
        val match=detailToken.matchEntire(token)?:return token
        val page=match.groupValues[2].toIntOrNull()?.takeIf { it>0 }?:return token
        return "${match.groupValues[1]}-detail-$page"
    }

    fun nativeHeadBase(group:String,item:String):String? =
        item.takeIf { group=="quan-co"&&head.matches(it) }

    /**
     * Claims map basenames (including extension) to a full source fingerprint. Persist a new claim
     * before copying. An existing file alone is never evidence that it came from this source.
     * Reuse the prior claim on restart, even if the original conflicting file has since disappeared.
     */
    fun nativeHeadTarget(base:String,extension:String,identity:String,
                         existing:Set<String>,claims:Map<String,String>):String {
        require(head.matches(base))
        require(extension in setOf("png","jpg","jpeg","webp"))
        require(identity.matches(Regex("[a-f0-9]{64}")))
        val stem="$base-${identity.take(14)}"
        val collision=Regex(Regex.escape(stem)+"(?:-(?:[2-9]|[1-9][0-9]+))?\\."+Regex.escape(extension))
        fun candidate(name:String)=name=="$base.$extension"||collision.matches(name)
        claims.entries.filter { it.value==identity&&candidate(it.key) }.minByOrNull { it.key }
            ?.let { return it.key }
        fun free(name:String)=name !in existing&&"$name.part" !in existing&&name !in claims
        val exact="$base.$extension"
        if(free(exact)) return exact
        var name="$stem.$extension";var suffix=2
        while(!free(name)) name="$stem-${suffix++}.$extension"
        return name
    }

    /** Routing metadata now lives in the folder, so a generic *-detail-1 file is not enough. */
    fun detail(path:String):Detail? {
        val parts=path.split('/')
        if(parts.size<6||parts.any { it==".."||it=="."||it.isBlank() }) return null
        val tail=parts.takeLast(5)
        if(tail.take(3)!=listOf("quan-co","tat-ca-quan-co","tuong")||!slug.matches(tail[3])) return null
        val match=Regex(Regex.escape(tail[3])+"-detail-([1-9][0-9]*)\\.png").matchEntire(tail[4])?:return null
        return match.groupValues[1].toIntOrNull()?.let { Detail(tail[3],it) }
    }

    fun exactDisplayName(path:String):String? {
        if(detail(path)!=null) return path.substringAfterLast('/').removeSuffix(".png")
        val parts=path.split('/')
        if(parts.size!=5||parts.take(3)!=listOf("anh-goc-co-lien-quan","quan-co","anh-quan-co")||
            !slug.matches(parts[3])) return null
        val file=parts.last();val base=file.substringBeforeLast('.',"")
        return base.takeIf { savedHead.matches(it)&&file.substringAfterLast('.') in setOf("png","jpg","jpeg","webp") }
    }
}
