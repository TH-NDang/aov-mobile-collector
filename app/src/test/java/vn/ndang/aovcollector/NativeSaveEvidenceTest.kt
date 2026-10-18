package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class NativeSaveEvidenceTest {
    @Test fun aVerifiedOwningDetailCanEstablishNoPrompt() {
        assertEquals(NativeSaveEvidence.Scene.DETAIL,
            NativeSaveEvidence.scene("quillenchieuthuctuoi",true,true))
    }

    @Test fun aRecognizedEncyclopediaPageCanEstablishNoPrompt() {
        assertEquals(NativeSaveEvidence.Scene.PAGE,
            NativeSaveEvidence.scene("bachkhoacolienquantrangbi",false,false))
        assertEquals(NativeSaveEvidence.Scene.PAGE,
            NativeSaveEvidence.scene("bachkhoatatcaquanco",false,false))
    }

    @Test fun partialSavePromptAlwaysOverridesVisibleBackgroundAndDetail() {
        for(fragment in listOf("savephoto","save","photo","sure","cancel")) {
            assertEquals(fragment,NativeSaveEvidence.Scene.PROMPT,
                NativeSaveEvidence.scene("bachkhoacolienquanquillen"+fragment,true,true))
            assertEquals(fragment,NativeSaveEvidence.Scene.PROMPT,
                NativeSaveEvidence.scene("bachkhoatatcaquanco"+fragment,false,false))
        }
    }

    @Test fun emptyOcrUnknownScreensAndUnverifiedModalsAreNotAbsence() {
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN,NativeSaveEvidence.scene("",true,true))
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN,NativeSaveEvidence.scene("colienquan",false,false))
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN,NativeSaveEvidence.scene("quillen",true,false))
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN,
            NativeSaveEvidence.scene("bachkhoacolienquan",true,false))
    }
}
