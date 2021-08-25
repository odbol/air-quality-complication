package com.odbol.wear.airquality

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.odbol.wear.airquality.purpleair.Sensor
import java.util.*
import java.util.function.Consumer

/**
 * Item types.
 */
const val HEADER = 1
const val ITEM = 2
const val FOOTER = 3

class SensorsAdapter(private val onSensorClicked: Consumer<Sensor>) : RecyclerView.Adapter<SensorsAdapter.ItemViewHolder>() {

    var sensors: List<Sensor> = Collections.emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        if (viewType == ITEM) {
            val item = LayoutInflater.from(parent.context).inflate(R.layout.sensor_item, parent, false)

            return SensorViewHolder(item)
        } else if (viewType == FOOTER) {
            val item = LayoutInflater.from(parent.context).inflate(R.layout.footer_item, parent, false)

            return ItemViewHolder(item)
        } else {
            val item = LayoutInflater.from(parent.context).inflate(R.layout.header_item, parent, false)

            return ItemViewHolder(item)
        }
    }

    override fun getItemCount() = sensors.size + 2

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        if (getItemViewType(position) == ITEM) {
            (holder as SensorViewHolder).bind(sensors[position - 1])
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) HEADER else if (position == itemCount - 1) FOOTER else ITEM
    }

    open inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }

    inner class SensorViewHolder(itemView: View) : ItemViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.title)
        private val subtitle = itemView.findViewById<TextView>(R.id.subtitle)
        private val aqi = itemView.findViewById<TextView>(R.id.aqi)

        fun bind(sensor: Sensor) {
            title.text = sensor.name ?: sensor.ID.toString()
            aqi.text = "AQI: ${AqiUtils.convertPm25ToAqi(sensor.pm25).aqi}"
            subtitle.text = "${sensor.location_type ?: ""} (GPS: ${sensor.latitude}, ${sensor.longitude})"

            itemView.isSelected = sensor.isSelected

            itemView.setOnClickListener {
                sensors
                        .forEach{ it.isSelected = false }

                sensor.isSelected = true

                this@SensorsAdapter.onSensorClicked.accept(sensor)

//                notifyItemChanged(adapterPosition)
                notifyDataSetChanged()
            }
        }

    }

}
