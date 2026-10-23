package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * One checkpointed hero = one macro step. A step starts on the lobby or "Chuẩn bị" equipment page,
 * chooses the indexed hero, then applies and captures up to five different Pro recommendation rows.
 *
 * Every hero change reopens the chooser, checks "Đã nhận" and unchecks it when necessary.
 * Every recommendation change reopens "Ph.án khác" / "Đề cử Pro". No fixed pro-list row index is
 * trusted: rows are found by their yellow buttons and deduplicated by account/avatar appearance.
 *
 * A failed screen check ends the step without advancing the checkpoint. No arbitrary taps to recover.
 */
class ProBuildRunner(
    private val hero: Int,
    private val grid: GridState,
    private val game: Game,
    private val finish: (Outcome) -> Unit
) {
    class GridState(
        var offset: Double = 0.0,
        var synced: Boolean = false,
        var ref: FloatArray? = null,
        var step: Double = 1.0
    )
    sealed class Outcome {
        object Collected: Outcome()
        object EndOfHeroes: Outcome()
        data class Failed(val reason: String): Outcome()
    }
    interface Shot {
        val frame: ProBuildVision.Frame
        /** Release the screenshot once, optionally storing it as diagnostic evidence. */
        fun done(keep: String? = null)
    }
    interface Game {
        val alive: Boolean
        fun look(then: (Shot?) -> Unit)
        fun tap(x: Double, y: Double, then: (Boolean) -> Unit)
        /**
         * [d] is screen-height displacement of content upwards; negative scrolls content down.
         * A held slow drag avoids uncontrolled inertial scrolling of the list.
         */
        fun swipe(d: Double, flick: Boolean, then: (Boolean) -> Unit)
        fun screenshot(name: String, then: (Boolean) -> Unit)
        fun later(ms: Long, then: () -> Unit)
        fun note(text: String)
    }

    private val prefix = "hero-%03d".format(hero + 1)
    private var finished = false
    private var gridMoves = 0
    private var gridStalls = 0
    private var gridResyncs = 0
    private var pickerChecks = 0
    private var blankRetries = 0
    private val applied = ArrayList<ProBuildVision.Proposal>()

    private fun end(outcome: Outcome) {
        if (finished) return
        finished = true
        finish(outcome)
    }
    private fun fail(why: String) = end(Outcome.Failed(why))
    private fun active(): Boolean {
        if (finished) return false
        if (!game.alive) { fail("Tác vụ Pro Builds đã bị tạm dừng. Mở lại màn hình Trang bị để tiếp tục tướng hiện tại."); return false }
        return true
    }
    private fun later(ms: Long, action: () -> Unit) = game.later(ms) { if (active()) action() }

    fun start() {
        if (!active()) return
        game.note("Pro Builds $prefix: bắt đầu, tối đa 5 combo")
        look { shot ->
            val page = ProBuildVision.page(shot.frame)
            shot.done()
            when (page) {
                ProBuildVision.Page.LOBBY -> touch(.410, .919, 1400) { confirmPreparation(0) }
                ProBuildVision.Page.PREPARATION -> openPicker()
                else -> fail("Bắt đầu Pro Builds tại màn hình chính hoặc Trang bị/Chuẩn bị; hiện đang ở $page.")
            }
        }
    }
    private fun look(then: (Shot) -> Unit) {
        if (!active()) return
        game.look { shot ->
            if (!active()) { shot?.done(); return@look }
            if (shot == null) { fail("Không chụp được màn hình để xác nhận thao tác"); return@look }
            then(shot)
        }
    }
    private fun expected(page: ProBuildVision.Page, description: String, then: (Shot) -> Unit) {
        look { shot ->
            val current = ProBuildVision.page(shot.frame)
            if (current != page) {
                shot.done("$prefix-unexpected-$description")
                fail("Không thấy $description ($page), hiện tại là $current. Đã lưu ảnh kiểm tra; sửa màn hình rồi Tiếp tục.")
            } else then(shot)
        }
    }
    private fun touch(x: Double, y: Double, waitMs: Long, then: () -> Unit) {
        if (!active()) return
        game.tap(x, y) { ok -> if (!active()) return@tap
            if (!ok) fail("Không gửi được thao tác chạm ($x, $y)")
            else later(waitMs, then)
        }
    }
    private fun swipe(d: Double, flick: Boolean, waitMs: Long, then: () -> Unit) {
        if (!active()) return
        game.swipe(d, flick) { ok -> if (!active()) return@swipe
            if (!ok) fail("Không cuộn được danh sách trong game")
            else later(waitMs, then)
        }
    }

    private fun confirmPreparation(retry: Int, then: () -> Unit = ::openPicker) {
        look { shot ->
            val page = ProBuildVision.page(shot.frame)
            shot.done(if (retry >= 2 && page != ProBuildVision.Page.PREPARATION) "$prefix-after-tap" else null)
            when {
                page == ProBuildVision.Page.PREPARATION -> then()
                retry < 2 -> later(950) { confirmPreparation(retry + 1, then) }
                else -> fail("Game không quay về màn hình Trang bị sau thao tác (hiện $page).")
            }
        }
    }

    private fun openPicker() {
        // Bottom icon BELOW the vertical hero portraits, not the left-hand portraits themselves.
        touch(.094, .825, 750) {
            expected(ProBuildVision.Page.HERO_PICKER, "popup Chọn tướng") { shot ->
                shot.done(); ensureAllHeroes()
            }
        }
    }
    private fun ensureAllHeroes() {
        if (++pickerChecks > 3) return fail("Không thể tắt bộ lọc Đã nhận sau 3 lần kiểm tra")
        expected(ProBuildVision.Page.HERO_PICKER, "bộ lọc Đã nhận") { shot ->
            val checked = ProBuildVision.ownedOnlyChecked(shot.frame)
            shot.done()
            when (checked) {
                true -> {
                    game.note("$prefix: bỏ tích Đã nhận")
                    touch(.813, .232, 450) { ensureAllHeroes() }
                }
                false -> {
                    game.note("$prefix: đã xác nhận Đã nhận = bỏ tích")
                    pickHero()
                }
                null -> fail("Không đọc được trạng thái Đã nhận; không tự chạm để tránh tích nhầm")
            }
        }
    }

    private fun pickHero() {
        expected(ProBuildVision.Page.HERO_PICKER, "danh sách 8 cột") { shot ->
            val now = ProHeroGrid.profile(shot.frame)
            val height = shot.frame.height
            shot.done()
            val prev = grid.ref
            if (grid.synced && prev != null) {
                val shift = ProHeroGrid.shift(prev, now)
                if (shift.reliable) {
                    grid.offset += shift.px.toDouble() / height
                    grid.ref = now
                    gridAim()
                    return@expected
                }
            }
            grid.synced = false
            grid.ref = null
            gridHome(0)
        }
    }
    /** Go to top ONLY when the previous screen cannot be registered. Check displacement after every swipe. */
    private fun gridHome(round: Int) {
        if (round >= 48) return fail("Không tìm được đầu danh sách chọn tướng sau 48 lần vuốt")
        expected(ProBuildVision.Page.HERO_PICKER, "danh sách khi tìm đầu") { before ->
            val profile = ProHeroGrid.profile(before.frame)
            before.done()
            swipe(-.29, false, 650) {
                expected(ProBuildVision.Page.HERO_PICKER, "danh sách sau vuốt lên") { after ->
                    val updated = ProHeroGrid.profile(after.frame)
                    val height = after.frame.height
                    after.done()
                    val delta = ProHeroGrid.shift(profile, updated)
                    if (!delta.reliable) {
                        if (++gridResyncs > 2) fail("Ảnh danh sách tướng thay đổi không đo được; kiểm tra có popup che hoặc hiệu ứng.")
                        else gridHome(round + 1)
                    } else if (abs(delta.px) < maxOf(5, height / 150)) {
                        grid.offset = 0.0; grid.synced = true; grid.ref = updated
                        grid.step = 1.0; gridStalls = 0
                        gridAim()
                    } else gridHome(round + 1)
                }
            }
        }
    }
    private fun gridAim() {
        expected(ProBuildVision.Page.HERO_PICKER, "danh sách tướng trước khi chạm") { shot ->
            val plan = ProHeroGrid.plan(hero, grid.offset)
            when (plan) {
                is ProHeroGrid.Plan.Tap -> {
                    val present = ProHeroGrid.hasCard(shot.frame, plan.x, plan.y)
                    grid.ref = ProHeroGrid.profile(shot.frame)
                    shot.done()
                    if (!present) {
                        if (gridStalls >= 2) {
                            game.note("$prefix: đã đến cuối danh sách tướng")
                            end(Outcome.EndOfHeroes)
                        } else fail("Ô tướng ${hero + 1} không rõ hình; không dám chọn nhầm. Kiểm tra ảnh popup.")
                    } else {
                        touch(plan.x, plan.y, 1150) {
                            confirmPreparation(0) {
                                game.note("$prefix: đã chọn tướng tại ô ${hero + 1}")
                                openPro()
                            }
                        }
                    }
                }
                is ProHeroGrid.Plan.Move -> {
                    grid.ref = ProHeroGrid.profile(shot.frame)
                    shot.done()
                    moveGrid(plan.d * grid.step)
                }
            }
        }
    }
    private fun moveGrid(d: Double) {
        if (++gridMoves > 22 + 5 * (hero / 8))
            return fail("Vượt giới hạn cuộn để tìm tướng ${hero + 1}; đã giữ vị trí để kiểm tra")
        val before = grid.ref ?: return gridHome(0)
        swipe(d, false, 750) {
            expected(ProBuildVision.Page.HERO_PICKER, "danh sách sau khi cuộn") { after ->
                val now = ProHeroGrid.profile(after.frame)
                val height = after.frame.height
                after.done()
                val shift = ProHeroGrid.shift(before, now)
                if (!shift.reliable) {
                    if (++gridResyncs <= 1) {
                        grid.synced = false; grid.ref = null; grid.step = maxOf(.35, grid.step / 2)
                        game.note("$prefix: mất dấu cuộn, đo lại từ đầu")
                        gridHome(0)
                    } else fail("Danh sách tướng cuộn không ổn định, dừng để tránh lấy trùng tướng.")
                    return@expected
                }
                grid.offset += shift.px.toDouble() / height
                grid.ref = now
                if (abs(shift.px) < maxOf(5, height / 150)) {
                    gridStalls++
                    if (gridStalls >= 2 && d > 0) {
                        val (_, y) = ProHeroGrid.centre(hero, grid.offset)
                        if (y > .84 || y < .27) {
                            game.note("$prefix: hết danh sách tướng")
                            end(Outcome.EndOfHeroes)
                            return@expected
                        }
                    }
                    if (gridStalls >= 3) {
                        fail("Danh sách tướng không di chuyển, tránh chọn nhầm hàng.")
                        return@expected
                    }
                } else gridStalls = 0
                gridAim()
            }
        }
    }

    private fun openPro() {
        touch(.894, .918, 800) {
            expected(ProBuildVision.Page.PRO_PANEL, "popup Ph.án khác") { shot ->
                shot.done()
                // Always select "Đề cử Pro"; the popup may remember a different tab.
                touch(.210, .455, 750) {
                    expected(ProBuildVision.Page.PRO_PANEL, "Đề cử Pro") { ready ->
                        ready.done()
                        proHome(0)
                    }
                }
            }
        }
    }
    /** Home the proposal scroll with measured *row changes*, not three unconditional up-swipes. */
    private fun proHome(round: Int) {
        if (round >= 8) return fail("Đề cử Pro không thể trở về đầu danh sách")
        expected(ProBuildVision.Page.PRO_PANEL, "danh sách Đề cử Pro") { before ->
            val rows = ProBuildVision.proposals(before.frame)
            before.done()
            if (rows.isEmpty()) { scanPro(0, 0); return@expected }
            swipe(-.38, false, 600) {
                expected(ProBuildVision.Page.PRO_PANEL, "Đề cử Pro sau vuốt lên") { after ->
                    val moved = ProBuildVision.proposals(after.frame)
                    after.done()
                    if (ProBuildVision.sameViewport(rows, moved)) scanPro(0, 0)
                    else proHome(round + 1)
                }
            }
        }
    }
    private fun scanPro(swipes: Int, stalls: Int) {
        if (swipes >= 12 || stalls >= 2) return noMorePro()
        expected(ProBuildVision.Page.PRO_PANEL, "đang duyệt Đề cử Pro") { shot ->
            val rows = ProBuildVision.proposals(shot.frame)
            val candidate = rows.firstOrNull { row -> applied.none { row.same(it) } }
            shot.done()
            if (candidate != null) {
                blankRetries = 0
                game.note("$prefix: combo ${applied.size + 1}, nút Dùng combo tại y=${"%.3f".format(candidate.buttonY)}")
                touch(.757, candidate.buttonY, 1100) {
                    confirmPreparation(0) {
                        val name = "$prefix-pro-%02d".format(applied.size + 1)
                        game.screenshot(name) { saved ->
                            if (!active()) return@screenshot
                            if (!saved) return@screenshot fail("Không lưu được ảnh $name sau khi dùng combo")
                            applied.add(candidate)
                            game.note("$prefix: đã lưu combo ${applied.size}/5")
                            if (applied.size >= 5) end(Outcome.Collected)
                            else later(500) { openPro() }
                        }
                    }
                }
                return@expected
            }
            // A genuinely empty tab can initially be loading; verify twice before saving proof.
            if (rows.isEmpty() && swipes == 0 && blankRetries++ < 2) {
                later(950) { scanPro(0, 0) }
                return@expected
            }
            swipe(.29, false, 600) {
                expected(ProBuildVision.Page.PRO_PANEL, "Đề cử Pro sau cuộn xuống") { after ->
                    val next = ProBuildVision.proposals(after.frame)
                    after.done()
                    scanPro(swipes + 1, if (ProBuildVision.sameViewport(rows, next)) stalls + 1 else 0)
                }
            }
        }
    }
    private fun noMorePro() {
        if (applied.isNotEmpty()) {
            game.note("$prefix: chỉ có ${applied.size} combo Pro khác nhau")
            return closePro()
        }
        // No "Dùng combo" at all: preserve the requested evidence of the empty recommendations panel.
        game.screenshot("$prefix-pro-none") { saved ->
            if (!active()) return@screenshot
            if (!saved) fail("Không lưu được ảnh popup Đề cử Pro khi không có combo")
            else { game.note("$prefix: không có combo Pro; đã chụp popup"); closePro() }
        }
    }
    private fun closePro() {
        touch(.828, .110, 600) {
            confirmPreparation(0) { end(Outcome.Collected) }
        }
    }
}
