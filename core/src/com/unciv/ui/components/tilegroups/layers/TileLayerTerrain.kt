package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.random.Random

class TileLayerTerrain(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)

    val tileBaseImages: ArrayList<Image> = ArrayList()
    private var tileImageIdentifiers = listOf<String>()
    private var bottomRightRiverImage: Image? = null
    private var bottomRiverImage: Image? = null
    private var bottomLeftRiverImage: Image? = null

    private fun getTerrainImageLocations(terrainSequence: Sequence<String>): List<String> {
        val allTerrains = terrainSequence.joinToString("+")
        if (strings().tileSetConfig.ruleVariants.containsKey(allTerrains))
            return strings().tileSetConfig.ruleVariants[allTerrains]!!.map { strings().getTile(it) }
        val allTerrainTile = strings().getTile(allTerrains)
        return if (ImageGetter.imageExists(allTerrainTile)) listOf(allTerrainTile)
        else terrainSequence.map { strings().orFallback { getTile(it) } }.toList()
    }

    private fun getImprovementAndResourceImages(resourceAndImprovementSequence: Sequence<String>): List<String> {
        val altogether = resourceAndImprovementSequence.joinToString("+").let { strings().getTile(it) }
        return if (ImageGetter.imageExists(altogether)) listOf(altogether)
        else resourceAndImprovementSequence.map { strings().orFallback { getTile(it) } }.toList()
    }

    private fun usePillagedImprovementImage(tile: Tile, viewingCiv: Civilization?): Boolean {
        if (!tile.improvementIsPillaged || !UncivGame.Current.settings.showPixelImprovements) return false
        val shownImprovement = tile.getShownImprovement(viewingCiv) ?: return false
        return ImageGetter.imageExists(strings().getTile("$shownImprovement-Pillaged"))
    }

    private fun getTileBaseImageLocations(viewingCiv: Civilization?): List<String> {

        val isForceVisible = tileGroup.isForceVisible

        if (viewingCiv == null && !isForceVisible)
            return strings().hexagonList

        val baseHexagon = if (strings().tileSetConfig.useColorAsBaseTerrain)
            listOf(strings().hexagon)
        else emptyList()

        val tile = tileGroup.tile

        val shownImprovement = tile.getShownImprovement(viewingCiv)
        val shouldShowImprovement = shownImprovement != null && UncivGame.Current.settings.showPixelImprovements

        val shouldShowResource = UncivGame.Current.settings.showPixelImprovements && tile.resource != null &&
                (isForceVisible || viewingCiv == null || tile.hasViewableResource(viewingCiv))

        val resourceAndImprovementSequence = sequence {
            if (shouldShowResource)  yield(tile.resource!!)
            if (shouldShowImprovement) {
                if (usePillagedImprovementImage(tile, viewingCiv))
                    yield("$shownImprovement-Pillaged")
                else yield(shownImprovement!!)
            }
        }

        val terrainImages = if (tile.naturalWonder != null)
            sequenceOf(tile.baseTerrain, tile.naturalWonder!!)
        else  sequenceOf(tile.baseTerrain) + tile.terrainFeatures.asSequence()
        val edgeImages = getEdgeTileLocations()
        val allTogether = (terrainImages + resourceAndImprovementSequence).joinToString("+")
        val allTogetherLocation = strings().getTile(allTogether)

        // If the tilesetconfig *explicitly* lists the terrains+improvements etc, we can't know where in that list to place the edges
        //   So we default to placing them over everything else.
        // If there is no explicit list, then we can know to place them between the terrain and the improvement
        return when {
            strings().tileSetConfig.ruleVariants[allTogether] != null -> baseHexagon + 
                    strings().tileSetConfig.ruleVariants[allTogether]!!.map { strings().getTile(it) } + edgeImages
            ImageGetter.imageExists(allTogetherLocation) -> baseHexagon + allTogetherLocation + edgeImages
            tile.naturalWonder != null -> getNaturalWonderBackupImage(baseHexagon) + edgeImages
            else -> baseHexagon + getTerrainImageLocations(terrainImages) + edgeImages + getImprovementAndResourceImages(resourceAndImprovementSequence)
        }
    }
    
    private fun getEdgeTileLocations(): Sequence<String> {
        val tile = tile()
        if (!tile.isTilemapInitialized()) // fake tile 
            return emptySequence()
        return tile.neighbors
            .flatMap { getMatchingEdges(tile, it) }
    }

    private fun getMatchingEdges(originTile: Tile, neighborTile: Tile): Sequence<String>{
        val vectorToNeighbor =  neighborTile.position.cpy().sub(originTile.position)
        val direction = NeighborDirection.fromVector(vectorToNeighbor)
            ?: return emptySequence()
        val possibleEdgeFiles = strings().edgeImagesByPosition[direction] ?: return emptySequence()
        
        // Required for performance - full matchesFilter is too expensive for something that needs to run every update()
        fun matchesFilterMinimal(originTile: Tile, filter: String): Boolean {
            if (originTile.allTerrains.any { it.name == filter }) return true
            if (originTile.getBaseTerrain().type.name == filter) return true
            return false
        }

        return possibleEdgeFiles.asSequence().filter {
            if (!matchesFilterMinimal(originTile, it.originTileFilter)) return@filter false
            if (!matchesFilterMinimal(neighborTile, it.destinationTileFilter)) return@filter false
            return@filter true
        }.map { it.fileName }
    }

    private fun updateTileImage(viewingCiv: Civilization?) {
        val tileBaseImageLocations = getTileBaseImageLocations(viewingCiv)
        
        if (tileBaseImageLocations.size == tileImageIdentifiers.size) {
            if (tileBaseImageLocations.withIndex().all { (i, imageLocation) -> tileImageIdentifiers[i] == imageLocation })
                return // All image identifiers are the same as the current ones, no need to change anything
        }
        tileImageIdentifiers = tileBaseImageLocations

        for (image in tileBaseImages) image.remove()
        tileBaseImages.clear()
        for (baseLocation in tileBaseImageLocations) {
            // Here we check what actual tiles exist, and pick one - not at random, but based on the tile location,
            // so it stays consistent throughout the game
            if (!ImageGetter.imageExists(baseLocation)) continue

            val locationToCheck =
                    if (tileGroup.tile.owningCity != null)
                        strings().getOwnedTileImageLocation(baseLocation, tileGroup.tile.getOwner()!!)
                    else baseLocation

            val existingImages = ArrayList<String>()
            existingImages.add(locationToCheck)
            var i = 2
            while (true) {
                val tileVariant = locationToCheck + i
                if (ImageGetter.imageExists(tileVariant)) existingImages.add(tileVariant)
                else break
                i += 1
            }
            val finalLocation = existingImages.random(
                Random(tileGroup.tile.position.hashCode() + locationToCheck.hashCode()))
            val image = ImageGetter.getImage(finalLocation)
            image.name = finalLocation // for debug mode reveal

            tileBaseImages.add(image)
            addActor(image)

            if (strings().tileSetConfig.tileScales.isNotEmpty()) {
                val scale = strings().tileSetConfig.tileScales[baseLocation.takeLastWhile { it != '/' }]
                image.setHexagonSize(scale)
            } else {
                image.setHexagonSize()
            }
        }
    }

    private fun updateTileColor(viewingCiv: Civilization?) {
        val isViewable = viewingCiv == null || isViewable(viewingCiv)
        val tile = tileGroup.tile
        val colorPillagedTile = isViewable && tile.isPillaged() && !usePillagedImprovementImage(tile, viewingCiv)

        val baseTerrainColor = when {
            colorPillagedTile && strings().tileSetConfig.useColorAsBaseTerrain -> tile.getBaseTerrain()
                .getColor().lerp(Color.BROWN, 0.6f)
            colorPillagedTile -> Color.WHITE.cpy().lerp(Color.BROWN, 0.6f)
            strings().tileSetConfig.useColorAsBaseTerrain && !isViewable -> tile.getBaseTerrain()
                .getColor().lerp(strings().tileSetConfig.fogOfWarColor, 0.6f)
            strings().tileSetConfig.useColorAsBaseTerrain -> tile.getBaseTerrain()
                .getColor()
            !isViewable -> Color.WHITE.cpy().lerp(strings().tileSetConfig.fogOfWarColor, 0.6f)
            else -> Color.WHITE.cpy()
        }

        val color = when {
            colorPillagedTile -> Color.WHITE.cpy().lerp(Color.RED.cpy(), 0.5f)
            !isViewable -> Color.WHITE.cpy()
                .lerp(strings().tileSetConfig.fogOfWarColor, 0.6f)
            else -> Color.WHITE.cpy()
        }

        for ((index, image) in tileBaseImages.withIndex())
            image.color = if (index == 0) baseTerrainColor else color
    }

    private fun updateRivers(displayBottomRight: Boolean, displayBottom: Boolean, displayBottomLeft: Boolean) {
        bottomRightRiverImage = updateRiver(bottomRightRiverImage,displayBottomRight, strings().bottomRightRiver)
        bottomRiverImage = updateRiver(bottomRiverImage, displayBottom, strings().bottomRiver)
        bottomLeftRiverImage = updateRiver(bottomLeftRiverImage, displayBottomLeft, strings().bottomLeftRiver)
    }

    private fun updateRiver(currentImage: Image?, shouldDisplay: Boolean, imageName: String): Image? {
        if (!shouldDisplay) {
            currentImage?.remove()
            return null
        } else {
            if (currentImage != null) {
                currentImage.toFront()
                return currentImage
            }
            if (!ImageGetter.imageExists(imageName)) return null // Old "Default" tileset gets no rivers.
            val newImage = ImageGetter.getImage(imageName)
            addActor(newImage.setHexagonSize())
            return newImage
        }
    }

    fun dim(brightness: Float) {
        // Dimming with alpha looks weird with overlapping tiles— Can't just set group alpha.
        // Image.draw() doesn't inherit parent colour tints— Can't just set group colour.
        // Covering up with fogImage or adding another tileset image with variable alpha doesn't look good for all terrain shapes— Can't cover up group with image.
        // Directly setting child actor colors breaks Default tileset terrain colours— Need to interpolate between existing and background colour.
        //
        // Reapplying the colour with every update is fine, and doesn't cause the tiles to get darker with every click, because the colours are always reset in either TileGroup.updateTileColor() or the early exit by super.update()/TileGroup.update().
        // Mutating the Color() in-place seems dangerous, but GDX source already mutates even when Kotlin notation suggests it's replacing.
        for (image in tileBaseImages) {
            image.color.lerp(BaseScreen.clearColor, 1-brightness)
        }
    }

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        updateTileImage(viewingCiv)
        updateRivers(tileGroup.tile.hasBottomRightRiver, tileGroup.tile.hasBottomRiver, tileGroup.tile.hasBottomLeftRiver)
        updateTileColor(viewingCiv)
    }

    fun reset() {
        isVisible = false
        updateRivers(displayBottomRight = false, displayBottom = false, displayBottomLeft = false)
    }

    private fun getNaturalWonderBackupImage(baseHexagon: List<String>): List<String> =
            if (strings().tileSetConfig.useSummaryImages) baseHexagon + strings().naturalWonder
            else baseHexagon + strings().orFallback{ getTile(tileGroup.tile.naturalWonder!!) }

}

enum class NeighborDirection {
    Top, TopRight, TopLeft, Bottom, BottomLeft, BottomRight;

    companion object {
        fun fromVector(vector2: Vector2): NeighborDirection? {
            val x = vector2.x.toInt()
            val y = vector2.y.toInt()
            return when (x) {
                1 -> if (y == 1) Top // x == 1 && y == 1
                    else TopLeft // x == 1 && y == 0
                0 -> if (y == 1) TopRight // x == 0 && y == 1
                    else BottomLeft // x == 0 && y == -1
                -1 -> if (y == -1) Bottom // x == -1 && y == -1
                    else BottomRight // x == -1 && y == 0
                else -> null
            }
        }
    }
}

data class EdgeTileImage(val fileName:String, val originTileFilter: String, val destinationTileFilter: String, val edgeType: NeighborDirection)
