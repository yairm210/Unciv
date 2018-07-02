package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import java.util.*

open class CameraStageBaseScreen : Screen {

    var game: UnCivGame = UnCivGame.Current
    var stage: Stage

    private val tutorialTexts = mutableListOf<String>()

    private var isTutorialShowing = false

    init {
        val resolutions: List<Float> = game.settings.resolution.split("x").map { it.toInt().toFloat() }
        stage = Stage(ExtendViewport(resolutions[0], resolutions[1]), batch)// FitViewport(1000,600)
        Gdx.input.inputProcessor = stage
    }


    override fun show() {}

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        stage.act()
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {}

    override fun dispose() {}

    fun displayTutorials(name: String) {
        if (game.gameInfo.tutorial.contains(name)) return
        game.gameInfo.tutorial.add(name)
        val texts = GameBasics.Tutorials[name]!!
        tutorialTexts.addAll(texts)
        if (!isTutorialShowing) displayTutorial()
    }

    private fun displayTutorial() {
        isTutorialShowing = true
        val tutorialTable = Table().pad(10f)
        tutorialTable.background(ImageGetter.getDrawable(ImageGetter.WhiteDot)
                .tint(Color(0x101050cf)))
        val label = Label(tutorialTexts[0], skin)
        label.setAlignment(Align.center)
        tutorialTexts.removeAt(0)
        tutorialTable.add(label).pad(10f).row()
        val button = TextButton("Close".tr(), skin)
        button.addClickListener {
                tutorialTable.remove()
                if (!tutorialTexts.isEmpty())
                    displayTutorial()
                else
                    isTutorialShowing = false
            }
        tutorialTable.add(button).pad(10f)
        tutorialTable.pack()
        tutorialTable.center(stage)
        stage.addActor(tutorialTable)
    }

    companion object {
        var skin = Skin(Gdx.files.internal("skin/flat-earth-ui.json"))
                .apply {
                    get<TextButton.TextButtonStyle>(TextButton.TextButtonStyle::class.java).font = getFont(20)
                    get<Label.LabelStyle>(Label.LabelStyle::class.java).font = getFont(18)
                    get<TextField.TextFieldStyle>(TextField.TextFieldStyle::class.java).font = getFont(18)
                    get<SelectBox.SelectBoxStyle>(SelectBox.SelectBoxStyle::class.java).font = getFont(20)
                    get<SelectBox.SelectBoxStyle>(SelectBox.SelectBoxStyle::class.java).listStyle.font = getFont(20)
                }
        internal var batch: Batch = SpriteBatch()
    }

}


fun Button.disable(){
    touchable= Touchable.disabled
    color= Color.GRAY
}
fun Button.enable() {
    color = Color.WHITE
    touchable = Touchable.enabled
}
fun <E> List<E>.getRandom(): E = if (size == 0) throw Exception() else get((Math.random() * size).toInt())


fun colorFromRGB(r: Int, g: Int, b: Int): Color {
    return Color(r/255f, g/255f, b/255f, 1f)
}

fun Actor.centerX(parent:Actor){ x = parent.width/2 - width/2 }
fun Actor.centerY(parent:Actor){ y = parent.height/2- height/2}
fun Actor.center(parent:Actor){ centerX(parent); centerY(parent)}

fun Actor.centerX(parent:Stage){ x = parent.width/2 - width/2 }
fun Actor.centerY(parent:Stage){ y = parent.height/2- height/2}
fun Actor.center(parent:Stage){ centerX(parent); centerY(parent)}

fun Label.setFontColor(color:Color): Label {style=Label.LabelStyle(style).apply { fontColor=color }; return this}
fun String.tr(): String {
    if(contains("[")){ // Placeholders!
        /**
         * I'm SURE there's an easier way to do this but I can't think of it =\
         * So what's all this then?
         * Well, not all languages are like English. So say I want to say "work on Library has completed in Akkad",
         * but in a completely different language like Japanese or German,
         * It could come out "Akkad hast die worken onner Library gerfinishen" or whatever,
         * basically, the order of the words in the sentance is not guaranteed.
         * So to translate this, I give a sentence like "work on [building] has completed in [city]"
         * and the german can put those placeholders where he wants, so  "[city] hast die worken onner [building] gerfinishen"
         * The string on which we call tr() will look like "work on [library] has completed in [Akkad]"
         * We will find the german placeholder text, and replace the placeholders with what was filled in the text we got!
         */

        val squareBraceRegex = Regex("\\[(.*?)\\]")
        val englishTranslationPlaceholder = GameBasics.Translations.keys
                .firstOrNull { it.replace(squareBraceRegex,"[]") == replace(squareBraceRegex,"[]") }
        if(englishTranslationPlaceholder==null ||
                !GameBasics.Translations[englishTranslationPlaceholder]!!.containsKey(UnCivGame.Current.settings.language)){
            // Translation placeholder doesn't exist for this language
            return this.replace("[","").replace("]","")
        }

        val termsInMessage = squareBraceRegex.findAll(this).map { it.groups[1]!!.value }.toMutableList()
        val termsInTranslationPlaceholder = squareBraceRegex.findAll(englishTranslationPlaceholder).map { it.value }.toMutableList()
        if(termsInMessage.size!=termsInTranslationPlaceholder.size)
            throw Exception("Message $this has a different number of terms than the placeholder $englishTranslationPlaceholder!")

        var languageSpecificPlaceholder = GameBasics.Translations[englishTranslationPlaceholder]!![UnCivGame.Current.settings.language]!!
        for(i in 0 until termsInMessage.size){
            languageSpecificPlaceholder = languageSpecificPlaceholder.replace(termsInTranslationPlaceholder[i], termsInMessage[i].tr())
        }
        return languageSpecificPlaceholder.tr()
    }
    if(contains("{")){ // sentence
        return Regex("\\{(.*?)\\}").replace(this,{matchResult -> matchResult.groups[1]!!.value.tr() })
    }
    else return GameBasics.Translations.get(this,UnCivGame.Current.settings.language) // single word
}



val fontCache = HashMap<Int,BitmapFont>()
fun getFont(size: Int): BitmapFont {
    if(fontCache.containsKey(size)) return fontCache[size]!!
//    var screenScale = Gdx.graphics.width / 1000f // screen virtual width as defined in CameraStageBaseScreen
//    if(screenScale<1) screenScale=1f

    val generator = FreeTypeFontGenerator(Gdx.files.internal("skin/Arial.ttf"))
    val parameter = FreeTypeFontGenerator.FreeTypeFontParameter()
    parameter.size = size
//    parameter.genMipMaps = true
    parameter.minFilter = Texture.TextureFilter.Linear
    parameter.magFilter = Texture.TextureFilter.Linear
    parameter.characters = "ABCČĆDĐEFGHIJKLMNOPQRSŠTUVWXYZŽaäàâăbcčćdđeéfghiîjklmnoöpqrsșštțuüvwxyzžАБВГҐДЂЕЁЄЖЗЅИІЇЙЈКЛЉМНЊОПРСТЋУЎФХЦЧЏШЩЪЫЬЭЮЯабвгґдђеёєжзѕиіїйјклљмнњопрстћуўфхцчџшщъыьэюяΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψωάΆέΈέΉίϊΐΊόΌύΰϋΎΫΏĂÂÊÔƠƯăâêôơư1234567890‘?’'“!”(%)[#]{@}/&\\<-+÷×=>®©\$€£¥¢:;,.*|"
    //generator.scaleForPixelHeight(size)


    val font = generator.generateFont(parameter)
//    font.data.setScale(1f/screenScale)
    generator.dispose() // don't forget to dispose to avoid memory leaks!
    fontCache[size]=font
    return font
}

fun Label.setFont(size:Int): Label {
    style = Label.LabelStyle(style)
    style.font = getFont(size)
    style = style // because we need it to call the SetStyle function. Yuk, I know.
    return this // for chaining
}

fun Actor.addClickListener(function: () -> Unit) {
    this.addListener(object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            function()
        }
    } )
}
