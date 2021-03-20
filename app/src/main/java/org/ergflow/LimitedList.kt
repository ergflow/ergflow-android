package org.ergflow

class LimitedList(private val maxSize: Int, private val sampleSize: Int) {

    val list = mutableListOf<Float>()

    fun add(element: Float?) {
        if (element == null) {
            return
        }
        list.add(element)
        if (list.size > maxSize) {
            list.removeFirst()
        }
    }

    fun truncatedAverage(): Float? {
        return Stats.truncatedAverage(list, sampleSize)
    }
}
