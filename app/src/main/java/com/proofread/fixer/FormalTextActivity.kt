package com.proofread.fixer

/** Selection-menu action "Make formal". */
class FormalTextActivity : ProcessTextActivity() {
    override val fixedAction: Action?
        get() = Action.FORMAL
}
