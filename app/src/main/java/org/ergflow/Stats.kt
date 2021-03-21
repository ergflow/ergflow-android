package org.ergflow

import org.tensorflow.lite.examples.posenet.lib.Position
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
class Stats(private val pointShelfLifeMs: Long = 5000L) {
    var points = mutableListOf<Point>()
    var minXList = mutableListOf<Point>()
    var maxXList = mutableListOf<Point>()
    var localMinXList = mutableListOf<Point>()
    var localMaxXList = mutableListOf<Point>()
    fun addPosition(pos: Position, time: Long) {
        addPoint(Point(pos.x, pos.y, time))
    }

    private fun addPoint(newPoint: Point) {
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

    fun getMaxXPoint(): Point? {
        return maxXList.lastOrNull() ?: localMaxXList.lastOrNull()
    }

    fun clear() {
        points.clear()
        minXList.clear()
        maxXList.clear()
        localMinXList.clear()
        localMaxXList.clear()
    }

    companion object {

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
