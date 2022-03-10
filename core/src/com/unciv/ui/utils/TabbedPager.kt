package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.Constants
import com.unciv.UncivGame
import kotlin.math.min

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
 * The size parameters are lower and upper bounds of the page content area. The widget will always report
 * these bounds (plus header height) as layout properties min/max-Width/Height, and measure the content
 * area of added pages and set the reported pref-W/H to their maximum within these bounds. But, if a
 * maximum is not specified, that coordinate will grow with content unlimited, and layout max-W/H will
 * always report the same as pref-W/H.
 */
//region Fields and initialization
@Suppress("MemberVisibilityCanBePrivate", "unused")  // All member are part of our API
class TabbedPager(
    private val minimumWidth: Float = 0f,
    private var maximumWidth: Float = Float.MAX_VALUE,
    private val minimumHeight: Float = 0f,
    private var maximumHeight: Float = Float.MAX_VALUE,
    private val headerFontSize: Int = Constants.defaultFontSize,
    private val headerFontColor: Color = Color.WHITE,
    private val highlightColor: Color = Color.BLUE,
    backgroundColor: Color = ImageGetter.getBlue().darken(0.5f),
    private val headerPadding: Float = 10f,
    capacity: Int = 4
) : Table() {

    private class PageState(
        caption: String,
        var content: Actor,
        var disabled: Boolean = false,
        val onActivation: ((Int, String)->Unit)? = null,
        icon: Actor? = null,
        iconSize: Float = 0f,
        pager: TabbedPager
    ) {

        var scrollX = 0f
        var scrollY = 0f

        val button = IconTextButton(caption, icon, pager.headerFontSize, pager.headerFontColor).apply {
            if (icon != null) {
                if (iconSize != 0f)
                    iconCell!!.size(iconSize)
                iconCell!!.padRight(pager.headerPadding * 0.5f)
            }
        }
        var buttonX = 0f
        var buttonW = 0f
    }

    private var preferredWidth = minimumWidth
    private val growMaxWidth = maximumWidth == Float.MAX_VALUE
    private val limitWidth = maximumWidth
    private var preferredHeight = minimumHeight
    private val growMaxHeight = maximumHeight == Float.MAX_VALUE
    private val limitHeight = maximumHeight

    private val pages = ArrayList<PageState>(capacity)

    /**
     * Index of currently selected page, or -1 of none. Read-only, use [selectPage] to change.
     */
    var activePage = -1
        private set

    private val header = Table(BaseScreen.skin)
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
    override fun getPrefWidth() = preferredWidth
    fun setPrefWidth(width: Float) {
        if (width !in minimumWidth..maximumWidth) throw IllegalArgumentException()
        preferredWidth = width
        invalidateHierarchy()
    }
    override fun getPrefHeight() = preferredHeight + headerHeight
    fun setPrefHeight(height: Float) {
        if (height - headerHeight !in minimumHeight..maximumHeight) throw IllegalArgumentException()
        preferredHeight = height - headerHeight
        invalidateHierarchy()
    }
    override fun getMinWidth() = minimumWidth
    override fun getMaxWidth() = maximumWidth
    override fun getMinHeight() = headerHeight + minimumHeight
    override fun getMaxHeight() = headerHeight + maximumHeight

    //endregion
    //region API

    /** @return Number of pages currently stored */
    fun pageCount() = pages.size

    /** @return index of a page by its (untranslated) caption, or -1 if no such page exists */
    fun getPageIndex(caption: String) = pages.indexOfLast { it.button.name == caption }

    /** Change the selected page by using its index.
     * @param index Page number or -1 to deselect the current page.
     * @return `true` if the page was successfully changed.
     */
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
                if (scrollX < 0f)  // was marked to center on first show
                    scrollX = ((content.width - this@TabbedPager.width) / 2).coerceIn(0f, contentScroll.maxX)
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
    fun selectPage(caption: String) = selectPage(getPageIndex(caption))
    private fun selectPage(page: PageState) = selectPage(getPageIndex(page))

    /** Change the disabled property of a page by its index.
     * @return previous value or `false` if index invalid.
     */
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
    fun setPageDisabled(caption: String, disabled: Boolean) = setPageDisabled(getPageIndex(caption), disabled)

    /** Remove a page by its index.
     * @return `true` if page successfully removed */
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
    fun removePage(caption: String) = removePage(getPageIndex(caption))

    /** Replace a page's content by its index. */
    fun replacePage(index: Int, content: Actor) {
        if (index !in 0 until pages.size) return
        val isActive = index == activePage
        if (isActive) selectPage(-1)
        pages[index].content = content
        if (isActive) selectPage(index)
    }

    /** Replace a page's content by its caption. */
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
        val page = PageState(
                caption = caption,
                content = content ?: Group(),
                disabled = disabled,
                onActivation = onActivation,
                icon = icon,
                iconSize = iconSize,
                pager = this
        )
        page.button.apply {
            name = caption  // enable finding pages by untranslated caption without needing our own field
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
    fun askForPassword(secretHashCode: Int = 0) {
        class PassPopup(screen: BaseScreen, unlockAction: ()->Unit, lockAction: ()->Unit) : Popup(screen) {
            val passEntry = TextField("", BaseScreen.skin)
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

        PassPopup(UncivGame.Current.screen as BaseScreen, {
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
            val contentWidth = min(page.content.width, limitWidth)
            if (contentWidth > preferredWidth) {
                preferredWidth = contentWidth
                if (activePage >= 0) invalidateHierarchy()
            }
            val contentHeight = min(page.content.height, limitHeight)
            if (contentHeight > preferredHeight) {
                preferredHeight = contentHeight
                if (activePage >= 0) invalidateHierarchy()
            }
            page.scrollX = -1f  // mark to center later when all pages are measured
        }
        if (growMaxWidth) maximumWidth = minimumWidth
        if (growMaxHeight) maximumHeight = minimumHeight

        return newIndex
    }

    private fun addDeferredSecrets() {
        while (true) {
            val page = deferredSecretPages.removeFirstOrNull() ?: return
            addAndShowPage(page, -1)
        }
    }
}
