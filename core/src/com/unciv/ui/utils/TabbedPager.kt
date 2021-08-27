package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UncivGame

/**
 * Implements a 'Tabs' widget where different pages can be switched by selecting a header button.
 * @param prefWidth Width of shown content area, can grow if pages with wider content are added.
 * @param prefHeight Height of shown content area without header.
 * @param fontSize Size of font used for header.
 * @param fontColor Color of font used for header.
 * @param headerPadding Default padding used for header.
 * @param capacity How many pages to pre-allocate space for.
 */
@Suppress("unused")  // This is part of our API
class TabbedPager(
    prefWidth: Float,
    prefHeight: Float,
    private val fontSize: Int = 18,
    private val fontColor: Color = Color.WHITE,
    private val highlightColor: Color = Color.BLUE,
    backgroundColor: Color = ImageGetter.getBlue().lerp(Color.BLACK, 0.5f),
    private val headerPadding: Float = 10f,
    capacity: Int = 4
) : Table() {

    private class PageState(
        var content: Actor,
        var disabled: Boolean = false,
        val onActivation: ((Int, String)->Unit)? = null
    ) {
        var scrollX = 0f
        var scrollY = 0f

        var button: Button = Button(CameraStageBaseScreen.skin)
        var buttonX = 0f
        var buttonW = 0f
    }

    private var prefWidthField = prefWidth
    private var prefHeightField = prefHeight

    private val pages = ArrayList<PageState>(capacity)

    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    var activePage = -1
        private set

    private val header = Table(CameraStageBaseScreen.skin)
    private val headerScroll = AutoScrollPane(header)
    private var headerHeight = 0f

    private val contentScroll = AutoScrollPane(null)

    private enum class SecretState {Ask, Hide, Show}
    private var secretState = SecretState.Ask
    private val deferredSecretPages = ArrayDeque<PageState>(0)

    init {
        background = ImageGetter.getBackground(backgroundColor)
        header.defaults().pad(headerPadding, headerPadding * 0.5f)
        headerScroll.setOverscroll(false,false)
        headerScroll.setScrollingDisabled(false, true)
        // Measure header height, most likely its final value
        removePage(addPage("Dummy"))
        add(headerScroll).growX().minHeight(headerHeight).row()
        add(contentScroll).grow().row()
    }

    override fun getPrefWidth() = prefWidthField
    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun setPrefWidth(width: Float) {
        if (width < minWidth) throw IllegalArgumentException()
        prefWidthField = width
        invalidateHierarchy()
    }
    override fun getPrefHeight() = prefHeightField + headerHeight
    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun setPrefHeight(height: Float) {
        if (height < minHeight) throw IllegalArgumentException()
        prefHeightField = height - headerHeight
        invalidateHierarchy()
    }

    override fun getMinHeight() = headerHeight

    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun pageCount() = pages.size

    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun getPageIndex(caption: String) = pages.indexOfLast { it.button.name == caption }
    private fun getPageIndex(page: PageState) = pages.indexOf(page)

    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun selectPage(index: Int): Boolean {
        if (index !in 0 until pages.size) return false
        if (activePage == index) return false
        if (index >= 0 && pages[index].disabled) return false
        if (activePage != -1) {
            pages[activePage].apply {
                button.color = Color.WHITE
                scrollX = contentScroll.scrollX
                scrollY = contentScroll.scrollY
                contentScroll.removeActor(content)
            }
        }
        activePage = index
        if (index != -1) {
            pages[index].apply {
                button.color = highlightColor
                contentScroll.actor = content
                contentScroll.scrollX = scrollX
                contentScroll.scrollY = scrollY
                contentScroll.updateVisualScroll()
                headerScroll.let {
                    it.scrollX = (buttonX + (buttonW - it.width) / 2).coerceIn(0f, it.maxX)
                }
                onActivation?.invoke(index, button.name)
            }
        }
        return true
    }
    fun selectPage(caption: String) = selectPage(getPageIndex(caption))
    private fun selectPage(page: PageState) = selectPage(getPageIndex(page))

    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun setDisablePage(index: Int, disabled: Boolean): Boolean {
        if (index !in 0 until pages.size) return false
        val page = pages[index]
        val oldValue = page.disabled
        page.disabled = disabled
        page.button.isEnabled = !disabled
        return oldValue
    }
    fun setDisablePage(caption: String, disabled: Boolean) = setDisablePage(getPageIndex(caption), disabled)

    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun removePage(index: Int): Boolean {
        if (index !in 0 until pages.size) return false
        if (index == activePage) selectPage(-1)
        val page = pages.removeAt(index)
        header.getCell(page.button).clearActor()
        header.cells.removeIndex(index)
        return true
    }
    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun removePage(caption: String) = removePage(getPageIndex(caption))

    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun replacePage(index: Int, content: Actor) {
        if (index !in 0 until pages.size) return
        if (index == activePage) selectPage(-1)
        pages[index].content = content
        if (index == activePage) selectPage(index)
    }
    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun replacePage(caption: String, content: Actor) = replacePage(getPageIndex(caption), content)

    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun addPage(
        caption: String,
        content: Actor? = null,
        icon: Actor? = null,
        iconSize: Float = 0f,
        insertBefore: Int = -1,
        secret: Boolean = false,
        disabled: Boolean = false,
        onActivation: ((Int, String)->Unit)? = null
    ): Int {
        // Build page descriptor and header button
        val page = PageState(content ?: Group(), disabled, onActivation)
        page.button.apply {
            name = caption  // enable finding pages by untranslated caption without needing our own field
            if (icon != null) {
                if (iconSize != 0f) {
                    val wrapper = Group().apply {
                        isTransform =
                            false // performance helper - nothing here is rotated or scaled
                        setSize(iconSize, iconSize)
                        icon.setSize(iconSize, iconSize)
                        icon.center(this)
                        addActor(icon)
                    }
                    add(wrapper).padRight(headerPadding * 0.5f)
                } else {
                    add(icon)
                }
            }
            add(caption.toLabel(fontColor, fontSize))
            isEnabled = !disabled
            onClick {
                selectPage(page)
            }
            pack()
            if (height + 2 * headerPadding > headerHeight) {
                headerHeight = height + 2 * headerPadding
                if (activePage >= 0) this@TabbedPager.invalidateHierarchy()
            }
        }

        // Support 'secret' pages
        if (secret && secretState == SecretState.Ask) {
            deferredSecretPages.addLast(page)
            askForPassword()
            return -1
        }
        if (secret && secretState != SecretState.Show) return -1

        return addAndShowPage(page, insertBefore)
    }

    private fun addAndShowPage(page: PageState, insertBefore: Int): Int {
        // Update pages array and header table
        val newIndex: Int
        val buttonCell: Cell<Button>
        if (insertBefore >= 0 && insertBefore < pages.size) {
            newIndex = insertBefore
            pages.add(insertBefore, page)
            header.addActorAt(insertBefore, page.button)
            buttonCell = header.getCell(page.button)
        } else {
            newIndex = pages.size
            pages.add(page)
            buttonCell = header.add(page.button)
        }
        page.buttonX = if (newIndex == 0) 0f else pages[newIndex-1].run { buttonX + buttonW }
        page.buttonW = buttonCell.run { prefWidth + padLeft + padRight }
        for (i in newIndex + 1 until pages.size)
            pages[i].buttonX += page.buttonW

        // Content Sizing
        if (page.content is Widget) {
            val contentWidth = page.content.width
            if (contentWidth > prefWidthField) {
                prefWidthField = contentWidth
                if (activePage >= 0) invalidateHierarchy()
            }
        }

        return newIndex
    }

    private fun askForPassword() {
        class PassPopup(screen: CameraStageBaseScreen, unlockAction: ()->Unit, lockAction: ()->Unit) : Popup(screen) {
            val passEntry = TextField("", CameraStageBaseScreen.skin)
            init {
                passEntry.isPasswordMode = true
                add(passEntry).row()
                addOKButton {
                    if (passEntry.text.hashCode() == 2747985) unlockAction() else lockAction()
                }
                this.keyboardFocus = passEntry
            }
        }
        if (!UncivGame.isCurrentInitialized()) return
        PassPopup(UncivGame.Current.screen as CameraStageBaseScreen, {
            secretState = SecretState.Show
            addDeferredSecrets()
        }, {
            secretState = SecretState.Hide
            deferredSecretPages.clear()
        }).open(true)
    }

    private fun addDeferredSecrets() {
        while (true) {
            val page = deferredSecretPages.removeFirstOrNull() ?: return
            addAndShowPage(page, -1)
        }
    }
}
