package com.tradesignal.ai.core

/** Swap this interface to plug in a future (local or cloud) AI model. */
interface ChartAnalyzer {
    fun analyze(frame: Frame): SignalResult
}

/** Default, fully offline analyzer: colour/shape candle extraction + rule-based scoring. */
class LocalChartAnalyzer(private val threshold: Int, private val minConfidence: Int) : ChartAnalyzer {
    override fun analyze(frame: Frame): SignalResult =
        SignalEngine(threshold, minConfidence).analyze(CandleExtractor.extract(frame))
}
