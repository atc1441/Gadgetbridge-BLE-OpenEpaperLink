package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.ImageViewCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.adapter.DeviceCardItemBinder
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DeviceCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DeviceCardLayout
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import nodomain.freeyourgadget.gadgetbridge.util.dialogs.IconListAdapter
import java.text.Collator
import java.util.Collections

class DeviceCardItemsActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private lateinit var adapter: ItemAdapter
    private lateinit var fab: FloatingActionButton
    private var defaults: List<DeviceCardItem> = emptyList()
    private var items: MutableList<DeviceCardItem> = mutableListOf()
    private var removed: MutableList<DeviceCardItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_widgets)

        val intentDevice = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (intentDevice == null) {
            finish()
            return
        }
        device = intentDevice
        defaults = device.deviceCoordinator.getCardItems(device)

        adapter = ItemAdapter()
        val recyclerView = findViewById<RecyclerView>(R.id.dashboard_widgets_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                rv: RecyclerView,
                holder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = holder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                Collections.swap(items, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not swipeable; only the drag handle reorders.
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                save()
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.touchHelper = touchHelper

        fab = findViewById(R.id.fab)
        fab.contentDescription = getString(R.string.device_card_add_item)
        fab.setOnClickListener { showAddItemDialog() }

        items = DeviceCardLayout.apply(device, defaults).toMutableList()
        removed = DeviceCardLayout.removed(device, defaults).toMutableList()
        refresh()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DeviceManager.ACTION_REFRESH_DEVICELIST))
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_card_items, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.device_card_items_reset -> {
                showResetDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eightbitdo_keymap_reset_title)
            .setMessage(R.string.device_card_items_reset_confirmation)
            .setPositiveButton(R.string.reset) { _, _ -> reset() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reset() {
        DeviceCardLayout.reset(device)
        items = DeviceCardLayout.apply(device, defaults).toMutableList()
        removed = DeviceCardLayout.removed(device, defaults).toMutableList()
        refresh()
    }

    private fun save() {
        DeviceCardLayout.save(device, items, removed)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refresh() {
        adapter.notifyDataSetChanged()
        fab.visibility = if (removed.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showAddItemDialog() {
        val collator = Collator.getInstance()
        val entries = removed
            .map {
                it to IconListAdapter.Entry(
                    DeviceCardItemBinder.icon(it, device),
                    DeviceCardItemBinder.title(it, device, this)
                )
            }
            .sortedWith { a, b -> collator.compare(a.second.title, b.second.title) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_card_add_item)
            .setAdapter(IconListAdapter(this, entries.map { it.second })) { _, which ->
                val item = entries[which].first
                removed.remove(item)
                items.add(item)
                save()
                refresh()
            }
            .show()
    }

    private fun remove(item: DeviceCardItem) {
        items.remove(item)
        removed.add(item)
        save()
        refresh()
    }

    private fun showOverflowMenu(anchor: View, item: DeviceCardItem) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_REMOVE, 0, R.string.widgets_remove)
        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == MENU_REMOVE) {
                remove(item)
                true
            } else {
                false
            }
        }
        popup.show()
    }

    private inner class ItemAdapter : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {
        var touchHelper: ItemTouchHelper? = null

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.widget_instance_icon)
            val title: TextView = view.findViewById(R.id.widget_instance_title)
            val overflow: ImageButton = view.findViewById(R.id.widget_instance_overflow)
            val dragHandle: View = view.findViewById(R.id.widget_instance_drag_handle)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dashboard_widget_instance, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageResource(DeviceCardItemBinder.icon(item, device))
            ImageViewCompat.setImageTintList(
                holder.icon,
                ColorStateList.valueOf(MaterialColors.getColor(holder.icon, R.attr.textColorSecondary))
            )
            holder.title.text = DeviceCardItemBinder.title(item, device, holder.itemView.context)
            holder.overflow.setOnClickListener { anchor -> showOverflowMenu(anchor, item) }
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper?.startDrag(holder)
                }
                false
            }
        }
    }

    companion object {
        private const val MENU_REMOVE = 1
    }
}
