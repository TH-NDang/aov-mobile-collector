package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskIconsTest {
    @Test fun pickerOrderMatchesExistingActionsAndHasDistinctIcons() {
        assertEquals(listOf("all", "ranking", "board", "chess", "pro-builds", "event-invite"), TaskIcons.builtInModes)
        assertEquals(6, TaskIcons.builtInModes.map(TaskIcons::drawable).toSet().size)
    }

    @Test fun heroCollectionModesShareIconAndUnknownMacrosHaveFallback() {
        assertEquals(TaskIcons.drawable("all"), TaskIcons.drawable("single"))
        assertEquals(TaskIcons.drawable("all"), TaskIcons.drawable("pick"))
        assertEquals(R.drawable.ic_task_macro, TaskIcons.drawable("custom"))
        assertEquals(R.drawable.ic_task_macro, TaskIcons.drawable("future-mode"))
        assertEquals(R.drawable.ic_task_macro, TaskIcons.drawable(""))
        assertEquals(R.drawable.ic_panel_camera, TaskIcons.drawable("manual"))
    }
}
