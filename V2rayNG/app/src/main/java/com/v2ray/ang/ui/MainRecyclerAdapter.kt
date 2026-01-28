package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.ServersCache
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.viewmodel.MainViewModel
import java.util.Collections

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(),
    ItemTouchHelperAdapter {

    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private val doubleColumnDisplay =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)

    private var data: MutableList<ServersCache> = mutableListOf()

    // ---------------- data ----------------

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        data = newData?.toMutableList() ?: mutableListOf()
        if (position in data.indices) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    fun removeServerSub(guid: String, position: Int) {
        val idx = data.indexOfFirst { it.guid == guid }
        if (idx >= 0) {
            data.removeAt(idx)
            notifyItemRemoved(idx)
            notifyItemRangeChanged(idx, data.size - idx)
        }
    }

    /**
     * 用于「切换选中服务器」时，仅刷新旧位置和新位置
     */
    fun setSelectServer(fromPosition: Int, toPosition: Int) {
        if (fromPosition in 0 until data.size) notifyItemChanged(fromPosition)
        if (toPosition in 0 until data.size) notifyItemChanged(toPosition)
    }

    // ---------------- adapter ----------------

    override fun getItemCount(): Int = data.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position == data.size) VIEW_TYPE_FOOTER else VIEW_TYPE_ITEM
    }

    // ---------------- bind ----------------

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder !is MainViewHolder) return
        if (position >= data.size) return

        val context = holder.itemMainBinding.root.context
        val server = data[position]
        val guid = server.guid
        val profile = server.profile

        // ---------- basic info ----------
        holder.itemMainBinding.tvName.text = profile.remarks
        holder.itemMainBinding.tvStatistics.text = getAddress(profile)
        holder.itemMainBinding.tvType.text = profile.configType.name

        // ---------- test result ----------
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
        holder.itemMainBinding.tvTestResult.setTextColor(
            ContextCompat.getColor(
                context,
                if ((aff?.testDelayMillis ?: 0L) < 0L)
                    R.color.colorPingRed
                else
                    R.color.colorPing
            )
        )

        // ---------- Material 选中态（核心） ----------
        val card = holder.itemMainBinding.itemBg
        card.isChecked = (guid == MmkvManager.getSelectServer())

        // ---------- subscription ----------
        val subRemarks = getSubscriptionRemarks(profile)
        holder.itemMainBinding.tvSubscription.text = subRemarks
        holder.itemMainBinding.layoutSubscription.visibility =
            if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

        // ---------- buttons ----------
        if (doubleColumnDisplay) {
            holder.itemMainBinding.layoutShare.visibility = View.GONE
            holder.itemMainBinding.layoutEdit.visibility = View.GONE
            holder.itemMainBinding.layoutRemove.visibility = View.GONE
            holder.itemMainBinding.layoutMore.visibility = View.VISIBLE

            holder.itemMainBinding.layoutMore.setOnClickListener {
                adapterListener?.onShare(guid, profile, position, true)
            }
        } else {
            holder.itemMainBinding.layoutShare.visibility = View.VISIBLE
            holder.itemMainBinding.layoutEdit.visibility = View.VISIBLE
            holder.itemMainBinding.layoutRemove.visibility = View.VISIBLE
            holder.itemMainBinding.layoutMore.visibility = View.GONE

            holder.itemMainBinding.layoutShare.setOnClickListener {
                adapterListener?.onShare(guid, profile, position, false)
            }
            holder.itemMainBinding.layoutEdit.setOnClickListener {
                adapterListener?.onEdit(guid, position, profile)
            }
            holder.itemMainBinding.layoutRemove.setOnClickListener {
                adapterListener?.onRemove(guid, position)
            }
        }

        // ---------- click ----------
        holder.itemMainBinding.root.setOnClickListener {
            adapterListener?.onSelectServer(guid)
        }
    }

    // ---------------- view holders ----------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(
                    ItemRecyclerMainBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            else ->
                FooterViewHolder(
                    ItemRecyclerFooterBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
        }
    }

    open class BaseViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView),
        ItemTouchHelperViewHolder {

        override fun onItemSelected() {
            (itemView as? MaterialCardView)?.cardElevation =
                itemView.resources.getDimension(R.dimen.card_drag_elevation)
        }

        override fun onItemClear() {
            (itemView as? MaterialCardView)?.cardElevation =
                itemView.resources.getDimension(R.dimen.card_normal_elevation)
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root)

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    // ---------------- helper ----------------

    private fun getAddress(profile: ProfileItem): String {
        val server = profile.server
        val port = profile.serverPort
        if (server.isNullOrBlank() && port.isNullOrBlank()) return ""

        val addr = server?.let {
            if (it.contains(":"))
                it.split(":").take(2).joinToString(":", postfix = ":***")
            else
                it.split('.').dropLast(1).joinToString(".", postfix = ".***")
        } ?: ""

        return "$addr : ${port.orEmpty()}"
    }

    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        return if (mainViewModel.subscriptionId.isEmpty()) {
            MmkvManager.decodeSubscription(profile.subscriptionId)
                ?.remarks
                ?.firstOrNull()
                ?.toString()
                .orEmpty()
        } else ""
    }

    // ---------------- drag ----------------

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition >= data.size || toPosition >= data.size) return false

        mainViewModel.swapServer(fromPosition, toPosition)
        Collections.swap(data, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {}

    override fun onItemDismiss(position: Int) {}
    }
