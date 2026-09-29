package com.tradesignal.ai.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tradesignal.ai.R
import com.tradesignal.ai.data.AppDatabase
import com.tradesignal.ai.data.SignalRecord
import com.tradesignal.ai.databinding.ActivityHistoryBinding
import com.tradesignal.ai.databinding.ItemHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private val dao by lazy { AppDatabase.get(this).signalDao() }
    private var all: List<SignalRecord> = emptyList()
    private var filter: String? = null
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = Adapter { id, outcome -> lifecycleScope.launch { dao.setOutcome(id, outcome); reload() } }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnClear.setOnClickListener { lifecycleScope.launch { dao.clearAll(); reload() } }
        binding.filterAll.setOnClickListener { filter = null; render() }
        binding.filterUp.setOnClickListener { filter = "UP"; render() }
        binding.filterDown.setOnClickListener { filter = "DOWN"; render() }
        binding.filterWait.setOnClickListener { filter = "WAIT"; render() }

        lifecycleScope.launch {
            dao.observeAll().collect { records ->
                all = records
                render()
            }
        }
    }

    private suspend fun reload() { all = dao.getAll(); render() }

    private fun render() {
        val list = if (filter == null) all else all.filter { it.signal == filter }
        adapter.submit(list)

        val up = all.count { it.signal == "UP" }
        val down = all.count { it.signal == "DOWN" }
        val wait = all.count { it.signal == "WAIT" }
        binding.tvStats.text = "Total: ${all.size} · UP: $up · DOWN: $down · WAIT: $wait"

        val wins = all.count { it.outcome == "WIN" }
        val losses = all.count { it.outcome == "LOSS" }
        val recorded = wins + losses
        val rate = if (recorded > 0) "${(wins * 100 / recorded)}%" else "—"
        binding.tvWinRate.text = "Recorded: $wins wins / $losses losses · Win rate: $rate"
    }

    private class Adapter(val onOutcome: (Long, String) -> Unit) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<SignalRecord> = emptyList()
        private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun submit(list: List<SignalRecord>) { items = list; notifyDataSetChanged() }

        inner class VH(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val b = ItemHistoryBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = items[position]
            val b = holder.b
            b.tvTime.text = fmt.format(Date(r.timestamp))
            b.tvSignal.text = r.signal
            val (bg, color) = when (r.signal) {
                "UP" -> R.drawable.bg_pill_up to R.color.up_green
                "DOWN" -> R.drawable.bg_pill_down to R.color.down_red
                else -> R.drawable.bg_pill_wait to R.color.wait_gray
            }
            b.tvSignal.setBackgroundResource(bg)
            b.tvSignal.setTextColor(holder.itemView.context.getColor(color))
            b.tvConfidence.text = if (r.signal == "WAIT") "—" else "${r.confidence}%"
            b.tvDetail.text = r.waitReason ?: "${r.structure} · ${r.momentum} · ${r.levels}"

            if (r.outcome == null) {
                b.outcomeRow.visibility = android.view.View.VISIBLE
                b.tvOutcome.visibility = android.view.View.GONE
                b.btnWin.setOnClickListener { onOutcome(r.id, "WIN") }
                b.btnLoss.setOnClickListener { onOutcome(r.id, "LOSS") }
                b.btnNotTraded.setOnClickListener { onOutcome(r.id, "NOT_TRADED") }
            } else {
                b.outcomeRow.visibility = android.view.View.GONE
                b.tvOutcome.visibility = android.view.View.VISIBLE
                b.tvOutcome.text = "Result: ${r.outcome}"
                b.tvOutcome.setTextColor(holder.itemView.context.getColor(
                    if (r.outcome == "WIN") R.color.up_green else if (r.outcome == "LOSS") R.color.down_red else R.color.text_secondary
                ))
            }
        }
    }
}
