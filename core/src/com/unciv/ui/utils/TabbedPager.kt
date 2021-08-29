package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UncivGame

/*
    Unimplemented ideas:
    Allow "fixed header" content that does not participate in scrolling
        (OptionsPopup mod check tab)
    `scrollAlign: Align` property controls initial content scroll position (currently it's Align.top)
 */

/**
 * Implements a 'Tabs' widget where different pages can be switched by selecting a header button.
 * 
 * Each page is an Actor, passed to the Widget via [addPage]. Pages can be [removed][removePage],
 * [replaced][replacePage] or dynamically added after the Widget is already shown.

 * Pages are automatically scrollable, switching pages preserves scroll positions individually.
 * Pages can be disabled or secret - any 'secret' pages added require a later call to [askForPassword]
 * to activate them (or discard if the password is wrong).
 * 
 * @param expectedWidth Width of shown content area, can grow if pages with wider content are added.
 * @param expectedHeight Height of shown content area without header.
 * @param fontSize Size of font used for header.
 * @param fontColor Color of font used for header.
 * @param headerPadding Default padding used for header.
 * @param capacity How many pages to pre-allocate space for.
 */
@Suppress("unused")  // This is part of our API
//region Fields and initialization
class TabbedPager(
    private val expectedWidth: Float,
    private val expectedHeight: Float,
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

    private var prefWidthField = expectedWidth
    private var prefHeightField = expectedHeight

    private val pages = ArrayList<PageState>(capacity)

    /**
     * Index of currently selected page, or -1 of none. Read-only, use [selectPage] to change.
     */
    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    var activePage = -1
        private set

    private val header = Table(CameraStageBaseScreen.skin)
    private val headerScroll = AutoScrollPane(header)
    private var headerHeight = 0f

    private val contentScroll = AutoScrollPane(null)

    private val deferredSecretPages = ArrayDeque<PageState>(0)
    private var askPasswordLock = false

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

    //endregion
    //region Widget interface

    // The following are part of the Widget interface and serve dynamic sizing
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

    //endregion
    //region API

    /** @return Number of pages currently stored */
    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun pageCount() = pages.size

    /** @return index of a page by its (untranslated) caption, or -1 if no such page exists */
    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun getPageIndex(caption: String) = pages.indexOfLast { it.button.name == caption }

    /** Change the selected page by using its index.
     * @param index Page number or -1 to deselect the current page.
     * @return `true` if the page was successfully changed.
     */
    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun selectPage(index: Int): Boolean {
        if (index !in -1 until pages.size) return false
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
                contentScroll.layout()
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

    /** Change the selected page by using its caption.
     * @param caption Caption of the page to select. A nonexistent name will deselect the current page.
     * @return `true` if the page was successfully changed.
     */
    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun selectPage(caption: String) = selectPage(getPageIndex(caption))
    private fun selectPage(page: PageState) = selectPage(getPageIndex(page))

    /** Change the disabled property of a page by its index.
     * @return previous value or `false` if index invalid.
     */
    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun setPageDisabled(index: Int, disabled: Boolean): Boolean {
        if (index !in 0 until pages.size) return false
        val page = pages[index]
        val oldValue = page.disabled
        page.disabled = disabled
        page.button.isEnabled = !disabled
        if (disabled && index == activePage) selectPage(-1)
        return oldValue
    }

    /** Change the disabled property of a page by its caption.
     * @return previous value or `false` if caption not found.
     */
    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun setPageDisabled(caption: String, disabled: Boolean) = setPageDisabled(getPageIndex(caption), disabled)

    /** Remove a page by its index.
     * @return `true` if page successfully removed */
    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun removePage(index: Int): Boolean {
        if (index !in 0 until pages.size) return false
        if (index == activePage) selectPage(-1)
        val page = pages.removeAt(index)
        header.getCell(page.button).clearActor()
        header.cells.removeIndex(index)
        return true
    }

    /** Remove a page by its caption.
     * @return `true` if page successfully removed */
    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun removePage(caption: String) = removePage(getPageIndex(caption))

    /** Replace a page's content by its index. */
    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun replacePage(index: Int, content: Actor) {
        if (index !in 0 until pages.size) return
        val isActive = index == activePage
        if (isActive) selectPage(-1)
        pages[index].content = content
        if (isActive) selectPage(index)
    }

    /** Replace a page's content by its caption. */
    @Suppress("MemberVisibilityCanBePrivate", "unused")  // This is part of our API
    fun replacePage(caption: String, content: Actor) = replacePage(getPageIndex(caption), content)

    /** Add a page!
     * @param caption Text to be shown on the header button (automatically translated), can later be used to reference the page in other calls.
     * @param content Actor to show when this page is selected.
     * @param icon Actor, typically an [Image], to show before the caption.
     * @param iconSize Size for [icon] - if not zero, the icon is wrapped to allow a [setSize] even on [Image] which ignores size.
     * @param insertBefore -1 to add at the end or index of existing page to insert this before
     * @param secret Marks page as 'secret'. A password is asked once per [TabbedPager] and if it does not match the has passed in the constructor the page and all subsequent secret pages are dropped.
     * @param disabled Initial disabled state. Disabled pages cannot be selected even with [selectPage], their button is dimmed.
     * @param onActivation _Optional_ callback called when this page is shown (per actual change to this page, not per header click). Lambda arguments are page index and caption.
     * @return The new page's index or -1 if it could not be immediately added (secret).
     */
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
        if (secret) {
            deferredSecretPages.addLast(page)
            return -1
        }

        return addAndShowPage(page, insertBefore)
    }

    /**
     * Activate any [secret][addPage] pages by asking for the password.
     *
     * If the parent of this Widget is a Popup, then this needs to be called _after_ the parent
     * is shown to ensure proper popup stacking.
     */
    @Suppress("MemberVisibilityCanBePrivate")  // This is part of our API
    fun askForPassword(secretHashCode: Int = 0) {
        class PassPopup(screen: CameraStageBaseScreen, unlockAction: ()->Unit, lockAction: ()->Unit) : Popup(screen) {
            val passEntry = TextField("", CameraStageBaseScreen.skin)
            init {
                passEntry.isPasswordMode = true
                add(passEntry).row()
                addOKButton {
                    if (passEntry.text.hashCode() == secretHashCode) unlockAction() else lockAction()
                }
                this.keyboardFocus = passEntry
            }
        }
        if (!UncivGame.isCurrentInitialized() || askPasswordLock || deferredSecretPages.isEmpty()) return
        askPasswordLock = true  // race condition: Popup closes _first_, then deferredSecretPages is emptied -> parent shows and calls us again
        PassPopup(UncivGame.Current.screen as CameraStageBaseScreen, {
            addDeferredSecrets()
        }, {
            deferredSecretPages.clear()
        }).open(true)
    }

    //endregion
    //region Helper routines

    private fun getPageIndex(page: PageState) = pages.indexOf(page)

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
        if (page.content is WidgetGroup) {
            (page.content as WidgetGroup).packIfNeeded()
            val contentWidth = page.content.width
            if (contentWidth > prefWidthField) {
                prefWidthField = contentWidth
                if (activePage >= 0) invalidateHierarchy()
            }
            page.scrollX = ((contentWidth - expectedWidth) / 2).coerceIn(0f, contentScroll.maxX)
        }

        return newIndex
    }

    private fun addDeferredSecrets() {
        while (true) {
            val page = deferredSecretPages.removeFirstOrNull() ?: return
            addAndShowPage(page, -1)
        }
    }
}
