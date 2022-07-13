# UI Development

Unciv is backed by [GDX's scene2d](https://libgdx.com/wiki/graphics/2d/scene2d/scene2d) for the UI, so check out [their official documentation](https://libgdx.com/wiki/graphics/2d/scene2d/scene2d) for more info about that.

We mainly use the [`Table` class](https://libgdx.com/wiki/graphics/2d/scene2d/table) of scene2d, because it offers nice flexibility in laying out all the user interface.

## The `FasterUIDevelopment` class

This class is basically just a small helper GDX application to help develop UI components faster.

It sets up the very basics of Unciv, so that you can then show one single UI component instantly. This gives you much faster response times for when you change something, so that you can immediately see the changes you made, without having to restart the game, load a bunch of stuff and navigate to where your UI component would actually be.

To use it, you change the `DevElement` class within the `FasterUIDevelopment.kt` file so that the `actor` field is set to the UI element you want to develop. A very basic usage is there by default, just showing a label, but you can put any UI element there instead.

```kotlin
class DevElement(
    val screen: UIDevScreen
) {
    lateinit var actor: Actor
    fun createDevElement() {
        actor = "This could be your UI element in development!".toLabel()
    }

    fun afterAdd() {
    }
}
```

You can then simply run the `main` method of `FasterUIDevelopment` to show your UI element.
