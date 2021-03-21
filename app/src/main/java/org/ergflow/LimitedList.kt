package org.ergflow

/**
 * List that will only grow to the given maxSize.
 *
 * @param maxSize if adding an element causes size to exceed this then the first element will be
 * removed
 * @param sampleSize the number of middle elements to average when calling truncatedAverage()
 */
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
