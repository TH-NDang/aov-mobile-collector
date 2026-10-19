package vn.ndang.aovcollector

/** File/display names only. Keep the original chess token when choosing its collection folder. */
object ChessCaptureFiles {
    const val NATIVE_FOLDER="anh-goc"
    private val slug=Regex("[a-z0-9][a-z0-9-]{0,89}")
    private val detailToken=Regex("(?:[0-9]{4,}-)?chess__quan-co__tat-ca-quan-co__tuong__([a-z0-9][a-z0-9-]{0,89})__([0-9]{3,})")
    private val head=Regex("[a-z0-9][a-z0-9-]{0,89}-head")
    private val savedHead=Regex("[a-z0-9][a-z0-9-]{0,89}-head(?:-[a-f0-9]{14}(?:-(?:[2-9]|[1-9][0-9]+))?)?")
    data class Detail(val hero:String,val page:Int)
    private val topicFolders=setOf("thong-tin/quy-trinh","thong-tin/thang-thua-vong","thong-tin/thang-thua-tran",
        "quan-co/tat-ca-quan-co","quan-co/phe-nghe","tuong-tac/phe","tuong-tac/nghe",
        "trang-bi/tat-ca-trang-bi","nhiem-vu/danh-sach-nhiem-vu",
        "chuc-phuc/thuong","chuc-phuc/huyen-thoai-su-thi","chuc-phuc/truyen-thuyet")
    fun topicPath(section:String,topic:String)= "$section/$topic".takeIf { it in topicFolders }
    fun isDetailCapture(token:String)=token.substringAfter("chess__","").split("__").getOrNull(2) !in setOf("trang","hang",null)


    /** Chess tabs/items get readable page names; hero details retain the requested detail-N convention. */
    fun screenshotBase(token:String):String {
        val overview=Regex("(?:[0-9]{4,}-)?chess__([a-z0-9-]+)__([a-z0-9-]+)__(?:trang|hang)__(?:grid-v2__)?([0-9]{3,})").matchEntire(token)
        if(overview!=null&&topicPath(overview.groupValues[1],overview.groupValues[2])!=null) {
            val page=overview.groupValues[3].toIntOrNull()?.takeIf { it>0 }?:return token
            return "${overview.groupValues[2]}-page-$page"
        }

        val item=Regex("(?:[0-9]{4,}-)?chess__(?:trang-bi__tat-ca-trang-bi__vat-pham|tuong-tac__(?:phe__phe|nghe__nghe))__([a-z0-9][a-z0-9-]{0,89})__([0-9]{3,})").matchEntire(token)
        if(item!=null) {
            val page=item.groupValues[2].toIntOrNull()?.takeIf { it>0 }?:return token
            val name=item.groupValues[1].replace(Regex("-[a-f0-9]{8}$"),"")
            return "$name-page-$page"
        }
        val match=detailToken.matchEntire(token)?:return token
        val page=match.groupValues[2].toIntOrNull()?.takeIf { it>0 }?:return token
        return "${match.groupValues[1]}-detail-$page"
    }

    /** Non-hero detail pages share their topic folder; no extra kind/item directory is needed. */
    fun directoryParts(token:String):List<String>? {
        val parts=token.substringAfter("chess__","").split("__")
        if(parts.size<4) return null
        val nonHero=parts.size==5&&
            ((parts.take(3)==listOf("trang-bi","tat-ca-trang-bi","vat-pham"))||
             (parts.take(3)==listOf("tuong-tac","phe","phe"))||
             (parts.take(3)==listOf("tuong-tac","nghe","nghe")))
        val dirs=when {
            nonHero||parts[2] in setOf("trang","hang") -> parts.take(2)
            parts.size>=5 -> parts.take(4)
            else -> return null
        }
        return dirs.takeIf { it.all { p -> p.matches(Regex("[a-z0-9_-]{1,90}")) } }
    }

    fun nativeHeadBase(group:String,item:String):String? =
        item.takeIf { group=="quan-co"&&head.matches(it) }

    /** Human-readable native names. Hash aliases remain in the journal, never the normal basename. */
    fun nativeBase(group:String,item:String,ordinal:Int):String {
        require(ordinal>0)
        nativeHeadBase(group,item)?.let { return it }
        if(group=="chieu-quan-co") {
            val hero=item.substringBefore("-skill-","")
            if(slug.matches(hero)) return "$hero-skill-$ordinal"
        }
        val name=HeroNames.slug(item).take(90)
        if(slug.matches(name)&&!Regex("[a-f0-9]{8,64}").matches(name)) return name
        // A neutral numbered icon is honest when the game provides no verified text label.
        require(slug.matches(group))
        return "$group-icon-$ordinal"
    }

    /**
     * Claims map basenames (including extension) to a full source fingerprint. Persist a new claim
     * before copying. An existing file alone is never evidence that it came from this source.
     * Reuse the prior claim on restart, even if the original conflicting file has since disappeared.
     */
    fun nativeHeadTarget(base:String,extension:String,identity:String,
                         existing:Set<String>,claims:Map<String,String>):String {
        require(head.matches(base))
        return nativeTarget(base,extension,identity,existing,claims)
    }

    fun nativeTarget(base:String,extension:String,identity:String,
                     existing:Set<String>,claims:Map<String,String>):String {
        require(slug.matches(base))
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

    /** Both layouts are readable; new originals belong to their capture run. */
    fun nativeRelativePath(path:String):String? {
        val parts=path.split('/')
        if(parts.any { it.isBlank()||it==".."||it=="." }) return null
        return when {
            parts.first()==GamePhotoImport.ROOT -> parts.drop(1).joinToString("/")
            parts.size>=2&&parts[1]==NATIVE_FOLDER -> parts.drop(2).joinToString("/")
            else -> null
        }
    }

    fun nativeGroup(path:String):String? {
        val relative=nativeRelativePath(path)?:return null
        val parts=relative.split('/')
        if(!GamePhotoImport.imageExtension(parts.last().substringAfterLast('.',""))) return null
        val legacy=path.substringBefore('/')==GamePhotoImport.ROOT
        return GamePhotoImport.GROUPS.firstOrNull { group ->
            if(group.key=="chua-phan-loai") false else {
                val prefix=GamePhotoImport.groupPath(group.key).split('/')
                parts.size==prefix.size+(if(legacy) 2 else 1)&&parts.take(prefix.size)==prefix
            }
        }?.key
    }

    fun nativeFolderTitle(path:String):String? {
        val relative=nativeRelativePath(path)?:return null
        val legacy=path.substringBefore('/')==GamePhotoImport.ROOT
        if(relative.isEmpty()) return if(legacy) "Ảnh gốc Cờ Liên Quân · Pictures" else "Ảnh gốc"
        GamePhotoImport.nativeFolderTitle(relative)?.let { return it }
        if(legacy) {
            val parent=relative.substringBeforeLast('/',"")
            if(GamePhotoImport.nativeFolderTitle(parent)!=null)
                return "Lượt "+relative.substringAfterLast('/').replace('-',' ')
            GamePhotoImport.GROUPS.firstOrNull { it.key==relative }?.let { return it.title }
            if(parent in GamePhotoImport.GROUPS.map { it.key })
                return "Lượt "+relative.substringAfterLast('/').replace('-',' ')
        }
        return null
    }

    fun exactDisplayName(path:String):String? {
        if(detail(path)!=null) return path.substringAfterLast('/').removeSuffix(".png")
        val sections=path.split('/')
        if(sections.size==4&&topicPath(sections[1],sections[2])!=null&&
            sections.all { it.isNotBlank()&&it!=".."&&it!="." }&&
            Regex("[a-z0-9][a-z0-9-]{0,89}-page-[1-9][0-9]*\\.png").matches(sections.last()))
            return sections.last().removeSuffix(".png")

        val group=nativeGroup(path)?:return null
        val base=path.substringAfterLast('/').substringBeforeLast('.',"")
        val recognized=when(group) {
            "quan-co" -> savedHead.matches(base)
            "chieu-quan-co" -> Regex("[a-z0-9][a-z0-9-]*-skill-[1-9][0-9]*(?:-[a-f0-9]{14}(?:-[0-9]+)?)?").matches(base)
            else -> Regex("[a-z0-9][a-z0-9-]{0,120}").matches(base)
        }
        return base.takeIf { recognized }
    }
}
