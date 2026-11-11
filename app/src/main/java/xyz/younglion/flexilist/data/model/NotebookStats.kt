package xyz.younglion.flexilist.data.model

/** Read-only Room projection; no schema change and no per-card database subscription. */
data class NotebookStats(val notebookId: Long, val total: Int, val done: Int)
