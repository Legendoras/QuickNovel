package com.example.epubdownloader

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.example.epubdownloader.MainActivity.Companion.activity
import com.example.epubdownloader.ui.download.DownloadFragment
import com.google.android.material.button.MaterialButton
import kotlinx.android.synthetic.main.download_result_compact.view.*
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.search_result_compact.view.backgroundCard
import kotlinx.android.synthetic.main.search_result_compact.view.imageText
import kotlinx.android.synthetic.main.search_result_compact.view.imageView
import kotlin.concurrent.thread


class DloadAdapter(
    context: Context,
    animeList: ArrayList<DownloadFragment.DownloadDataLoaded>,
    resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var context: Context? = context
    var resView: RecyclerView? = resView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = R.layout.download_result_compact
        return DownloadCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            context!!,
            resView!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DownloadCardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    override fun getItemId(position: Int): Long {
        return cardList[position].id.toLong()
    }

    companion object {
        val cachedLoadResponse = hashMapOf<Int, LoadResponse>()
    }

    class DownloadCardViewHolder
    constructor(itemView: View, _context: Context, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView: ImageView = itemView.imageView
        val cardText: TextView = itemView.imageText
        val download_progress_text: TextView = itemView.download_progress_text
        val download_progressbar: ProgressBar = itemView.download_progressbar
        val download_update: MaterialButton = itemView.download_update
        val download_open_btt: MaterialButton = itemView.download_open_btt
        val download_progressbar_indeterment: ProgressBar = itemView.download_progressbar_indeterment

        //        val cardTextExtra: TextView = itemView.imageTextExtra
        val bg = itemView.backgroundCard
        fun bind(card: DownloadFragment.DownloadDataLoaded) {
            cardText.text = card.name
            download_progress_text.text =
                "${card.downloadedCount}/${card.downloadedTotal}" + if (card.ETA == "") "" else " - ${card.ETA}"
            download_progressbar.progress = card.downloadedCount * 100 / card.downloadedTotal

            val glideUrl =
                GlideUrl(card.posterUrl)
            context.let {
                Glide.with(it)
                    .load(glideUrl)
                    .into(cardView)
            }

            download_update.text = when (card.state) {
                BookDownloader.DownloadType.IsDone -> "Downloaded"
                BookDownloader.DownloadType.IsDownloading -> "Pause"
                BookDownloader.DownloadType.IsPaused -> "Resume"
                BookDownloader.DownloadType.IsFailed -> "Re-Download"
                BookDownloader.DownloadType.IsStopped -> "Update"
            }

            download_update.setIconResource(when (card.state) {
                BookDownloader.DownloadType.IsDownloading -> R.drawable.netflix_pause
                BookDownloader.DownloadType.IsPaused -> R.drawable.netflix_play
                BookDownloader.DownloadType.IsStopped -> R.drawable.ic_baseline_autorenew_24
                else -> R.drawable.netflix_download
            })

            fun getEpub(): Boolean {
                return DataStore.getKey(DOWNLOAD_EPUB_SIZE, card.id.toString(), 0) != card.downloadedCount
            }

            fun updateBar(isGenerating: Boolean?) {
                val isIndeterminate = isGenerating ?: BookDownloader.isTurningIntoEpub.containsKey(card.id)
                download_progressbar.visibility = if(!isIndeterminate) View.VISIBLE else View.INVISIBLE
                download_progressbar_indeterment.visibility = if(isIndeterminate) View.VISIBLE else View.INVISIBLE
            }

            fun updateEpub() {
                val generateEpub = getEpub()
                if (generateEpub) {
                    download_open_btt.setIconResource(R.drawable.ic_baseline_create_24)
                    download_open_btt.text = "Generate"
                } else {
                    download_open_btt.setIconResource(R.drawable.ic_baseline_menu_book_24)
                    download_open_btt.text = "Read"
                }
            }
            updateEpub()
            updateBar(null)

            download_open_btt.setOnClickListener {
                if (getEpub()) {
                    updateBar(true)
                    thread {
                        val done = BookDownloader.turnToEpub(card.author, card.name, card.apiName)
                        MainActivity.activity.runOnUiThread {
                            if (done) {
                                Toast.makeText(context, "Created ${card.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error creating the Epub", Toast.LENGTH_LONG).show()
                            }
                            updateEpub()
                            updateBar(null)
                        }
                    }
                } else {
                    //TODO READ
                }
            }

            download_update.setOnClickListener {
                thread {
                    val res =
                        if (cachedLoadResponse.containsKey(card.id))
                            cachedLoadResponse[card.id] else
                            MainActivity.api.load(card.source)
                    if (res == null) {
                        activity.runOnUiThread {
                            Toast.makeText(context, "Error loading", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        cachedLoadResponse[card.id] = res
                        val localId = card.id//BookDownloader.generateId(res, MainActivity.api)
                        when (if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped) {
                            BookDownloader.DownloadType.IsFailed -> BookDownloader.download(res, MainActivity.api)
                            BookDownloader.DownloadType.IsStopped -> BookDownloader.download(res, MainActivity.api)
                            BookDownloader.DownloadType.IsDownloading -> BookDownloader.updateDownload(localId,
                                BookDownloader.DownloadType.IsPaused)
                            BookDownloader.DownloadType.IsPaused -> BookDownloader.updateDownload(localId,
                                BookDownloader.DownloadType.IsDownloading)
                            else -> println("ERROR")
                        }
                    }
                }
            }

            bg.setOnClickListener {
                MainActivity.loadResult(card.source)
            }
        }
    }
}
