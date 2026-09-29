package com.tradesignal.ai.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tradesignal.ai.R
import com.tradesignal.ai.core.LocalChartAnalyzer
import com.tradesignal.ai.core.SampleCharts
import com.tradesignal.ai.core.Signal
import com.tradesignal.ai.data.Settings
import com.tradesignal.ai.databinding.ActivityTestModeBinding
import com.tradesignal.ai.databinding.ItemTestCaseBinding

/** Runs the exact same CandleExtractor + SignalEngine used on real screenshots,
 *  against built-in synthetic charts, so results can be checked without any trading app. */
class TestModeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTestModeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestModeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }

        val settings = Settings(this)
        val analyzer = LocalChartAnalyzer(settings.threshold, settings.minConfidence)
        val cases = SampleCharts.all()

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = object : RecyclerView.Adapter<VH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
                VH(ItemTestCaseBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false))

            override fun getItemCount() = cases.size

            override fun onBindViewHolder(holder: VH, position: Int) {
                val c = cases[position]
                holder.b.tvName.text = "${c.name}  (expected ${c.expected})"
                holder.b.tvResult.text = "Run ▶"
                holder.b.tvResult.setBackgroundResource(R.drawable.bg_pill_wait)
                holder.b.tvResult.setOnClickListener {
                    val r = analyzer.analyze(c.frame)
                    val match = r.signal == c.expected
                    holder.b.tvResult.text = "${r.signal}" + (if (r.signal != Signal.WAIT) " ${r.confidence}%" else "") + if (match) " ✓" else " ✗"
                    holder.b.tvResult.setBackgroundResource(if (match) R.drawable.bg_pill_up else R.drawable.bg_pill_down)
                }
            }
        }
    }

    private class VH(val b: ItemTestCaseBinding) : RecyclerView.ViewHolder(b.root)
}
