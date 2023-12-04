package com.unciv.ui.components

import com.unciv.logic.civilization.Civilization
import com.unciv.ui.screens.victoryscreen.DataPoint
import com.unciv.ui.screens.victoryscreen.LineChart
import org.junit.Assert
import org.junit.Test

class LineChartTests {

    private val civ = Civilization("My civ")
    private val lineChart = LineChart(Civilization(civ.civName))

    @Test
    fun `labels for an empty list are just 0 label`() {
        val data = mutableListOf <DataPoint<Int>>()
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 1 && result[0] == 0)
    }

    @Test
    fun `chart goes along 0 line`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,0, civ))
        data.add(DataPoint(1,0, civ))
        data.add(DataPoint(2,0, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 2 && result[0] == 0 && result[1] == 1)
    }

    @Test
    fun `chart is from 0 to the positive value`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,-100, civ))
        data.add(DataPoint(1,200, civ))
        data.add(DataPoint(2,1600, civ))
        val result = lineChart.generateLabels(data, false) // testing the X axis
        Assert.assertTrue(result.size == 11 && result.first() == 0 && result[1] == 1 && result.last() == 10)
    }

    @Test
    fun `chart is from 0 to the negative value`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,0, civ))
        data.add(DataPoint(1,-2, civ))
        data.add(DataPoint(2,-6, civ))
        val result = lineChart.generateLabels(data, true) // testing the Y axis
        Assert.assertTrue(result.size == 11 && result.first() == -10 && result[1] == -9 && result.last() == 0)
    }

    @Test
    fun `chart goes from the negative to the positive value near 0`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,-5, civ))
        data.add(DataPoint(1,2, civ))
        data.add(DataPoint(2,6, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 21 && result.first() == -10 && result[1] == -9 && result.last() == 10)
    }

    @Test
    fun `chart goes from the negative to the positive far from 0`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,-59, civ))
        data.add(DataPoint(1,191, civ))
        data.add(DataPoint(2,160, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 14 && result.first() == -60 && result[1] == -40 && result[3] == 0 && result.last() == 200)
    }

    @Test
    fun `chart goes from the positive to the negative far from 0`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,-485, civ))
        data.add(DataPoint(1,191, civ))
        data.add(DataPoint(2,160, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 15 && result.first() == -500 && result[1] == -450 && result[10] == 0 && result.last() == 200)
    }

    @Test
    fun `chart is within the positive values far from 0`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,290, civ))
        data.add(DataPoint(1,1159, civ))
        data.add(DataPoint(2,280, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 11 && result.first() == 200 && result[1] == 300 && result.last() == 1200)
    }

    @Test
    fun `chart is within the negative values far from 0`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,-290, civ))
        data.add(DataPoint(1,-1160, civ))
        data.add(DataPoint(2,-280, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 10 && result.first() == -1200 && result[1] == -1080 && result.last() == -120)
    }


    @Test
    fun `chart is within the positive values near 0`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,180, civ))
        data.add(DataPoint(1,1101, civ))
        data.add(DataPoint(2,980, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 11 && result.first() == 0 && result[1] == 120 && result.last() == 1200)
    }

    @Test
    fun `chart is within the negative values near 0`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,-180, civ))
        data.add(DataPoint(1,-1101, civ))
        data.add(DataPoint(2,-980, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 11 && result.first() == -1200 && result[1] == -1080 && result.last() == 0)
    }

    @Test
    fun `chart is mostly in the positive values but not only`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,210, civ))
        data.add(DataPoint(1,1101, civ))
        data.add(DataPoint(2,-89, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 12 && result.first() == -90 && result[1] == 0 && result.last() == 1200)
    }

    @Test
    fun `chart is mostly in the negative values but not only`() {
        val data = mutableListOf <DataPoint<Int>>()
        data.add(DataPoint(0,111, civ))
        data.add(DataPoint(1,-2101, civ))
        data.add(DataPoint(2,-345, civ))
        val result = lineChart.generateLabels(data, true)
        Assert.assertTrue(result.size == 12 && result.first() == -2200 && result[10] == 0 && result.last() == 200)
    }

}
