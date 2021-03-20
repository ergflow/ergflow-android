package org.ergflow

import org.tensorflow.lite.examples.posenet.lib.Position
import java.util.NavigableMap
import java.util.TreeMap
import kotlin.math.ceil
import kotlin.math.roundToInt

class Point(
    val x: Int,
    val y: Int,
    val time: Long,
)

/**
 * Class that holds a list of points to be used for min, max, and median values.
 * Points expire depending on pointShelfLifeMs.
 *
 * @param pointShelfLifeMs remove points that are older than this (default 5000ms)
 */
class Stats(val pointShelfLifeMs: Long = 5000L) {
    var points = mutableListOf<Point>()
    var minXList = mutableListOf<Point>()
    var maxXList = mutableListOf<Point>()
    var localMinXList = mutableListOf<Point>()
    var localMaxXList = mutableListOf<Point>()
    fun addPosition(pos: Position, time: Long) {
        addPoint(Point(pos.x, pos.y, time))
    }

    fun addPoint(newPoint: Point) {
        expireOldPoints()
        points.add(newPoint)

        if (localMinXList.isEmpty() || newPoint.x < localMinXList.last().x) {
            localMinXList.add(newPoint)
        }
        if (localMaxXList.isEmpty() || newPoint.x > localMaxXList.last().x) {
            localMaxXList.add(newPoint)
        }

        if (localMinXList.size > 1 && System.currentTimeMillis() - localMinXList.last().time > 500) {
            addPoint(localMinXList.last(), minXList)
            localMinXList.clear()
        }
        if (localMaxXList.size > 1 && System.currentTimeMillis() - localMaxXList.last().time > 500) {
            addPoint(localMaxXList.last(), maxXList)
            localMaxXList.clear()
        }
    }

    private fun expireOldPoints() {
        val now = System.currentTimeMillis()
        val expired = points.filter { point -> now - point.time > pointShelfLifeMs }
        points.removeAll(expired)
        minXList.removeAll(expired)
        maxXList.removeAll(expired)
        localMinXList.removeAll(expired)
        localMaxXList.removeAll(expired)
    }

    private fun addPoint(newPoint: Point, list: MutableList<Point>) {
        if (list.contains(newPoint)) {
            return
        }
        list.add(newPoint)
        if (list.size > 7) {
            list.removeAt(0)
        }
    }

    fun getMinXPoint(): Point? {
        return minXList.lastOrNull() ?: localMinXList.lastOrNull()
    }

    fun getMinAverageMedianX(): Point? {
        return truncatedAveX(minXList)
    }

    fun truncatedAveX(): Point? {
        return truncatedAveX(points)
    }

    fun truncatedAveY(): Point? {
        return truncatedAveY(points)
    }

    fun getMaxXPoint(): Point? {
        return maxXList.lastOrNull() ?: localMaxXList.lastOrNull()
    }

    fun getRecentMedian(numPoints: Int): Point {
        val list = points.takeLast(numPoints).sortedBy { it.x }
        return list[(list.size / 2.toDouble()).roundToInt()]
    }

    private fun truncatedAveX(list: MutableList<Point>): Point? {
        val sortedList = list.sortedBy { point -> point.x }
        return when (sortedList.size) {
            0 -> null
            1 -> sortedList[0]
            2 -> sortedList[1]
            3 -> sortedList[1]
            4 -> average(listOf(sortedList[1], sortedList[2]))
            5 -> average(listOf(sortedList[1], sortedList[2], sortedList[3]))
            6 -> average(listOf(sortedList[1], sortedList[2], sortedList[3], sortedList[4]))
            else -> average(listOf(sortedList[2], sortedList[3], sortedList[4]))
        }
    }

    private fun truncatedAveY(list: MutableList<Point>): Point? {
        val sortedList = list.sortedBy { point -> point.y }
        return when (sortedList.size) {
            0 -> null
            1 -> sortedList[0]
            2 -> sortedList[1]
            3 -> sortedList[1]
            4 -> average(listOf(sortedList[1], sortedList[2]))
            5 -> average(listOf(sortedList[1], sortedList[2], sortedList[3]))
            6 -> average(listOf(sortedList[1], sortedList[2], sortedList[3], sortedList[4]))
            else -> average(listOf(sortedList[2], sortedList[3], sortedList[4]))
        }
    }

    private fun average(list: List<Point>): Point? {
        if (list.isEmpty()) {
            return null
        }
        var totalX = 0
        var totalY = 0
        list.forEach { point: Point ->
            totalX += point.x
            totalY += point.y
        }
        val middlePoint = list[(list.size / 2.toDouble()).roundToInt()]
        return Point(
            (totalX / list.size.toDouble()).roundToInt(),
            (totalY / list.size.toDouble()).roundToInt(),
            middlePoint.time
        )
    }

    fun clear() {
        points.clear()
        minXList.clear()
        maxXList.clear()
        localMinXList.clear()
        localMaxXList.clear()
    }

    companion object {
        fun binCounts(x: DoubleArray, binEdges: DoubleArray): DoubleArray {
            val binEdgesSize = binEdges.size
            val binEdgesMap: NavigableMap<Double, Int> = TreeMap()
            for (i in 0 until binEdgesSize) binEdgesMap[binEdges[i]] = i
            val ret = DoubleArray(binEdgesSize - 1)
            for (d in x) {
                binEdgesMap.ceilingEntry(d)?.apply { ++ret[value] }
            }
            return ret
        }

        /**
         * Sort the list, take the middle n number of elements, and compute the average.
         * Useful in excluding outliers when calculating average.
         *
         * @param list of numbers
         * @param n number of middle elements to use when calculating average. If n >= the size
         * of the list then list.average() is returned
         * @return the truncated average or null if the list is null or empty
         */
        fun truncatedAverage(list: List<Double>?, n: Int): Double? {
            if (list == null || list.isEmpty()) {
                return null
            }
            if (list.size <= n) {
                return list.average()
            }
            val endsToTruncate = (list.size - n) / 2.0f
            return list.sorted().takeLast(list.size - ceil(endsToTruncate).toInt()).take(n)
                .average()
        }

        /**
         * Sort the list, take the middle n number of elements, and compute the average.
         * Useful in excluding outliers when calculating average.
         *
         * @param list of numbers
         * @param n number of middle elements to use when calculating average. If n >= the size
         * of the list then list.average() is returned
         * @return the truncated average or null if the list is null or empty
         */
        fun truncatedAverage(list: List<Float>?, n: Int): Float? {
            if (list == null || list.isEmpty()) {
                return null
            }
            if (list.size <= n) {
                return list.average().toFloat()
            }
            val endsToTruncate = (list.size - n) / 2.0f
            return list.sorted().takeLast(list.size - endsToTruncate.toInt()).take(n).average()
                .toFloat()
        }

        /**
         * Sort the list, take the middle n number of elements, and compute the average.
         * Useful in excluding outliers when calculating average.
         *
         * @param list of numbers
         * @param n number of middle elements to use when calculating average. If n >= the size
         * of the list then list.average() is returned
         * @return the truncated average or null if the list is null or empty
         */
        fun truncatedAverage(list: List<Int>?, n: Int): Int? {
            if (list == null || list.isEmpty()) {
                return null
            }
            if (list.size <= n) {
                return list.average().roundToInt()
            }
            val endsToTruncate = list.size - n / 2.0f
            return list.sorted().takeLast(list.size - endsToTruncate.toInt())
                .take(list.size - endsToTruncate.toInt()).average().roundToInt()
        }
    }
}
