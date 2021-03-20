package org.ergflow.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import org.ergflow.posenet.R
import org.ergflow.posenet.databinding.ItemLayoutBinding

class ItemArrayAdapter(context: Context?, textViewResourceId: Int) :
    ArrayAdapter<ItemArrayAdapter.Item>(context!!, textViewResourceId) {

    class Item(
        val key: String,
        var left: String,
        var middle: String,
        var right: String,
        var textColor: Int?,
        var backgroundColor: Int?,
    )

    val items = mutableMapOf<String, Item>()
    private val defaultTextColor = context!!.resources.getColor(R.color.dracula_foreground, null)
    private val defaultBackgroundColor =
        context!!.resources.getColor(R.color.dracula_background, null)

    fun addOrUpdate(item: Item) {
        val oldItem = items[item.key]
        if (oldItem == null) {
            add(item)
        } else {
            oldItem.apply {
                left = item.left
                middle = item.middle
                right = item.right
                textColor = item.textColor
            }
            add(null)
        }
    }

    override fun add(item: Item?) {
        if (item != null) {
            items[item.key] = item
        }
        super.add(item)
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Item? {
        return items.values.elementAtOrNull(position)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var row = convertView
        val itemLayoutBinding: ItemLayoutBinding

        if (row == null || row.tag == null) {
            val inflater =
                this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            itemLayoutBinding = ItemLayoutBinding.inflate(inflater)
            row = itemLayoutBinding.root
        } else {
            itemLayoutBinding = row.tag as ItemLayoutBinding
        }

        val item = getItem(position)
        itemLayoutBinding.root.setBackgroundColor(item?.backgroundColor ?: defaultBackgroundColor)
        itemLayoutBinding.apply {
            left.text = item?.left
            middle.text = item?.middle
            right.text = item?.right
            left.setTextColor(item?.textColor ?: defaultTextColor)
            middle.setTextColor(item?.textColor ?: defaultTextColor)
            right.setTextColor(item?.textColor ?: defaultTextColor)
            return row
        }
    }
}
