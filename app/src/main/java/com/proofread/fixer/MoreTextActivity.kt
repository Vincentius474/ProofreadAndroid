package com.proofread.fixer

/** Selection-menu action "Proofread…": choose any action from a list. */
class MoreTextActivity : ProcessTextActivity() {
    override val fixedAction: Action?
        get() = null
}
