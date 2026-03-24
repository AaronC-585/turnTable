package com.turntable.barcodescanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.databinding.ActivityRedactedSimpleListBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedConversationBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedDetailBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedForumMainBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedSubscriptionsBinding
import com.google.android.material.tabs.TabLayout
import com.turntable.barcodescanner.databinding.ActivityRedactedGenericListBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedMailboxBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedPagedListBinding
import com.turntable.barcodescanner.redacted.AnnouncementRow
import com.turntable.barcodescanner.redacted.AnnouncementsAdapter
import com.turntable.barcodescanner.redacted.ConversationMessagesAdapter
import com.turntable.barcodescanner.redacted.RedactedAnnouncementHtml
import com.turntable.barcodescanner.redacted.RedactedBbCodeToRtf
import com.turntable.barcodescanner.redacted.InboxRow
import com.turntable.barcodescanner.redacted.InboxRowsAdapter
import com.turntable.barcodescanner.redacted.parseConversationMessageRows
import com.turntable.barcodescanner.redacted.parseForumThreadRows
import com.turntable.barcodescanner.redacted.resolveConversationReplyRecipientId
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentParse
import com.turntable.barcodescanner.redacted.RedactedGazelleTorrentUser
import com.turntable.barcodescanner.redacted.RedactedHtmlSafe
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedRtfClipboard
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class RedactedInboxActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedMailboxBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private lateinit var inboxAdapter: InboxRowsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedMailboxBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_mailbox_title)
        supportActionBar?.subtitle = getString(R.string.redacted_inbox_hints)

        binding.tabMailbox.addTab(
            binding.tabMailbox.newTab().setText(getString(R.string.redacted_mailbox_tab_inbox)),
        )
        binding.tabMailbox.addTab(
            binding.tabMailbox.newTab().setText(getString(R.string.redacted_mailbox_tab_sent)),
        )
        binding.tabMailbox.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    loadInbox(isPullRefresh = false)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        inboxAdapter = InboxRowsAdapter { convId -> openInboxConversation(convId) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = inboxAdapter

        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val pos = viewHolder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        showInboxSwipeMenu(viewHolder.itemView, pos)
                        // Reset swipe translation after menu (next frame so anchor stays valid).
                        viewHolder.itemView.post { inboxAdapter.notifyItemChanged(pos) }
                    }
                }
            },
        ).attachToRecyclerView(binding.recycler)

        binding.swipeRefreshMailbox.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.home_token_label),
            ContextCompat.getColor(this, R.color.home_ratio_ok),
        )
        binding.swipeRefreshMailbox.setOnRefreshListener {
            loadInbox(isPullRefresh = true)
        }

        binding.fabCompose.setOnClickListener {
            startActivity(Intent(this, RedactedSendPmActivity::class.java))
        }

        loadInbox(isPullRefresh = false)
    }

    override fun onPause() {
        super.onPause()
        AppBottomBars.refreshMailUnreadBadgeNow(this)
    }

    private fun openInboxConversation(convId: Int) {
        startActivity(
            Intent(this, RedactedConversationActivity::class.java)
                .putExtra(RedactedExtras.CONV_ID, convId),
        )
    }

    private fun showInboxSwipeMenu(anchor: View, position: Int) {
        val row = inboxAdapter.rows.getOrNull(position) ?: return
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_inbox_row, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.inbox_menu_open -> {
                    openInboxConversation(row.convId)
                    true
                }
                R.id.inbox_menu_browser -> {
                    openMailboxConversationInBrowser(row.convId)
                    true
                }
                R.id.inbox_menu_copy_subject -> {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("subject", row.subject))
                    Toast.makeText(this, R.string.redacted_inbox_subject_copied, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * API `type` for [com.turntable.barcodescanner.redacted.RedactedApiClient.inbox]:
     * `null` = inbox, [SENTBOX_TYPE] = sent.
     */
    private fun selectedMailboxApiType(): String? =
        when (binding.tabMailbox.selectedTabPosition) {
            0 -> null
            1 -> SENTBOX_TYPE
            else -> null
        }

    private fun openMailboxConversationInBrowser(convId: Int) {
        RedactedUiHelper.openSite(this, "inbox.php?action=viewconv&id=$convId")
    }

    private fun loadInbox(isPullRefresh: Boolean) {
        if (!isPullRefresh) {
            binding.progress.visibility = View.VISIBLE
        }
        val type = selectedMailboxApiType()
        Thread {
            val r = api.inbox(type = type, sort = "unread")
            runOnUiThread {
                binding.progress.visibility = View.GONE
                binding.swipeRefreshMailbox.isRefreshing = false
                when (r) {
                    is RedactedResult.Failure -> {
                        binding.textEmpty.visibility = View.VISIBLE
                        binding.textEmpty.text = r.message
                    }
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        val arr = resp.optJSONArray("messages")
                        val rows = mutableListOf<InboxRow>()
                        if (arr != null) {
                            val hideStickiesOnSent = type == SENTBOX_TYPE
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val cid = o.optInt("convId")
                                val unread = o.optBoolean("unread")
                                val sticky = o.optBoolean("sticky")
                                if (hideStickiesOnSent && sticky) continue
                                rows.add(
                                    InboxRow(
                                        convId = cid,
                                        subject = o.optString("subject"),
                                        subtitle = "${o.optString("username")} · ${o.optString("date")}",
                                        unread = unread,
                                        sticky = sticky,
                                    ),
                                )
                            }
                        }
                        inboxAdapter.rows = rows
                        binding.textEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                    }
                    else -> {}
                }
            }
        }.start()
    }

    companion object {
        /** Gazelle JSON API: sent messages folder (see docs `type` for `action=inbox`). */
        const val SENTBOX_TYPE: String = "sentbox"
    }
}

class RedactedConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedConversationBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private val messageAdapter = ConversationMessagesAdapter()
    private var convId: Int = 0
    private var replyToUserId: Int = 0
    private var sendingReply: Boolean = false
    private var lastConvResponse: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = RedactedUiHelper.requireApi(this) ?: return
        convId = intent.getIntExtra(RedactedExtras.CONV_ID, 0)
        if (convId <= 0) {
            finish()
            return
        }
        binding = ActivityRedactedConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_inbox)

        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = messageAdapter

        binding.swipeRefreshConversation.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.home_token_label),
            ContextCompat.getColor(this, R.color.home_ratio_ok),
        )
        binding.swipeRefreshConversation.setOnRefreshListener {
            loadConversation(isPullRefresh = true)
        }

        binding.buttonSendReply.setOnClickListener { sendReply() }

        loadConversation(isPullRefresh = false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_redacted_copy_rtf, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_copy_as_rtf) {
            val resp = lastConvResponse
            if (resp == null) {
                Toast.makeText(this, R.string.redacted_unexpected, Toast.LENGTH_SHORT).show()
            } else {
                val rtf = buildConversationRtfDocument(resp)
                val plain = buildConversationPlainFallback(resp)
                RedactedRtfClipboard.copyRtf(this, getString(R.string.redacted_inbox), rtf, plain)
                Toast.makeText(this, R.string.redacted_rtf_copied, Toast.LENGTH_LONG).show()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun buildConversationRtfDocument(resp: JSONObject): String {
        val sb = StringBuilder()
        val subject = resp.optString("subject")
        if (subject.isNotBlank()) {
            sb.append("\\b ").append(RedactedBbCodeToRtf.escapeRtfPlain(subject)).append("\\b0\\line\\line ")
        }
        val arr = resp.optJSONArray("messages")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val head = "${m.optString("senderName")} · ${m.optString("sentDate")}"
                sb.append(RedactedBbCodeToRtf.escapeRtfPlain(head)).append("\\line ")
                sb.append(RedactedBbCodeToRtf.bbToRtf(m.optString("bbBody"), 0))
                sb.append("\\par\\par ")
            }
        }
        return RedactedBbCodeToRtf.wrapDocument(sb.toString())
    }

    private fun buildConversationPlainFallback(resp: JSONObject): String =
        RedactedGazelleTorrentParse.stripBbCodeForPreview(
            buildString {
                append(resp.optString("subject")).append("\n\n")
                val arr = resp.optJSONArray("messages")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        append(arr.optJSONObject(i)?.optString("bbBody")).append("\n\n")
                    }
                }
            },
        )

    private fun updateReplyRecipientUi() {
        if (replyToUserId > 0) {
            binding.layoutReplyRecipientId.visibility = View.GONE
        } else {
            binding.layoutReplyRecipientId.visibility = View.VISIBLE
        }
    }

    private fun sendReply() {
        if (sendingReply) return
        val body = binding.editReplyBody.text?.toString()?.trim().orEmpty()
        if (body.isBlank()) {
            Toast.makeText(this, R.string.redacted_pm_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        var toId = replyToUserId
        if (toId <= 0) {
            toId = binding.editReplyRecipientId.text?.toString()?.toIntOrNull() ?: 0
        }
        if (toId <= 0) {
            Toast.makeText(this, R.string.redacted_reply_need_recipient, Toast.LENGTH_LONG).show()
            return
        }
        sendingReply = true
        binding.buttonSendReply.isEnabled = false
        Thread {
            val r = api.sendPm(toUserId = toId, subject = null, body = body, convId = convId)
            runOnUiThread {
                sendingReply = false
                binding.buttonSendReply.isEnabled = true
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(
                        this,
                        RedactedHtmlSafe.safePlainTextForUi(r.message),
                        Toast.LENGTH_LONG,
                    ).show()
                    is RedactedResult.Success -> {
                        binding.editReplyBody.text?.clear()
                        loadConversation(isPullRefresh = true)
                    }
                    else -> {}
                }
            }
        }.start()
    }

    private fun scrollMessagesToBottom() {
        val n = messageAdapter.itemCount
        if (n <= 0) return
        binding.recyclerMessages.post {
            binding.recyclerMessages.scrollToPosition(n - 1)
        }
    }

    private fun loadConversation(isPullRefresh: Boolean) {
        if (!isPullRefresh) {
            binding.progress.visibility = View.VISIBLE
        }
        binding.textError.visibility = View.GONE
        Thread {
            val r = api.inboxConversation(convId)
            val myId = when (val idx = api.index()) {
                is RedactedResult.Success -> idx.response?.optInt("id") ?: 0
                else -> 0
            }
            runOnUiThread {
                binding.progress.visibility = View.GONE
                binding.swipeRefreshConversation.isRefreshing = false
                when (r) {
                    is RedactedResult.Failure -> {
                        lastConvResponse = null
                        binding.cardReply.visibility = View.GONE
                        binding.textError.visibility = View.VISIBLE
                        binding.textError.text = r.message
                        messageAdapter.rows = emptyList()
                    }
                    is RedactedResult.Success -> {
                        lastConvResponse = r.response
                        binding.textError.visibility = View.GONE
                        replyToUserId = resolveConversationReplyRecipientId(r.response, myId)
                        updateReplyRecipientUi()
                        binding.cardReply.visibility = View.VISIBLE
                        val (subject, rows) = parseConversationMessageRows(r.response)
                        if (subject.isNotBlank()) {
                            supportActionBar?.title = subject
                        }
                        messageAdapter.rows = rows
                        scrollMessagesToBottom()
                    }
                    else -> {
                        lastConvResponse = null
                        binding.cardReply.visibility = View.GONE
                        binding.textError.visibility = View.VISIBLE
                        binding.textError.text = getString(R.string.redacted_unexpected)
                        messageAdapter.rows = emptyList()
                    }
                }
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        AppBottomBars.refreshMailUnreadBadgeNow(this)
    }
}

class RedactedForumMainActivity : AppCompatActivity() {
    private data class ForumCategoryTab(
        val name: String,
        val forumIds: List<Int>,
        val forumNames: List<String>,
        val rows: List<TwoLineRow>,
    )

    private lateinit var binding: ActivityRedactedForumMainBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private lateinit var adapter: TwoLineRowsAdapter
    private var tabs: List<ForumCategoryTab> = emptyList()
    private var activeForumIds: List<Int> = emptyList()
    private var activeForumNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedForumMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_forums)

        adapter = TwoLineRowsAdapter { pos ->
            val fid = activeForumIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            val name = activeForumNames.getOrNull(pos).orEmpty()
            startActivity(
                Intent(this, RedactedForumThreadsActivity::class.java)
                    .putExtra(RedactedExtras.FORUM_ID, fid)
                    .putExtra(RedactedExtras.FORUM_NAME, name),
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.tabForums.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    showCategory(tab?.position ?: 0)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.forumMain()
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        val cats = resp.optJSONArray("categories")
                        val builtTabs = mutableListOf<ForumCategoryTab>()
                        if (cats != null) {
                            for (i in 0 until cats.length()) {
                                val cat = cats.optJSONObject(i) ?: continue
                                val catName = cat.optString("categoryName").ifBlank {
                                    "Category ${i + 1}"
                                }
                                val forums = cat.optJSONArray("forums")
                                val rows = mutableListOf<TwoLineRow>()
                                val ids = mutableListOf<Int>()
                                val names = mutableListOf<String>()
                                if (forums != null) {
                                    for (j in 0 until forums.length()) {
                                        val f = forums.optJSONObject(j) ?: continue
                                        val fid = f.optInt("forumId")
                                        val fn = f.optString("forumName")
                                        rows.add(TwoLineRow(fn, catName))
                                        ids.add(fid)
                                        names.add(fn)
                                    }
                                }
                                builtTabs += ForumCategoryTab(
                                    name = catName,
                                    forumIds = ids,
                                    forumNames = names,
                                    rows = rows.sortedBy { it.title.lowercase(Locale.US) },
                                )
                            }
                        }
                        tabs = builtTabs
                        bindTabs()
                    }
                    else -> {}
                }
            }
        }.start()
    }

    private fun bindTabs() {
        binding.tabForums.removeAllTabs()
        if (tabs.isEmpty()) {
            activeForumIds = emptyList()
            activeForumNames = emptyList()
            adapter.rows = emptyList()
            binding.textEmpty.visibility = View.VISIBLE
            binding.textEmpty.text = getString(R.string.redacted_no_results)
            return
        }
        binding.textEmpty.visibility = View.GONE
        tabs.forEach { t ->
            binding.tabForums.addTab(binding.tabForums.newTab().setText(t.name))
        }
        binding.tabForums.getTabAt(0)?.select()
        showCategory(0)
    }

    private fun showCategory(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        activeForumIds = tab.forumIds
        activeForumNames = tab.forumNames
        adapter.rows = tab.rows
        if (tab.rows.isEmpty()) {
            binding.textEmpty.visibility = View.VISIBLE
            binding.textEmpty.text = getString(R.string.redacted_no_results)
        } else {
            binding.textEmpty.visibility = View.GONE
        }
    }
}

class RedactedForumThreadsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedPagedListBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var forumId = 0
    private var page = 1
    private var totalPages = 1
    private val threadIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        forumId = intent.getIntExtra(RedactedExtras.FORUM_ID, 0)
        if (forumId <= 0) {
            finish()
            return
        }
        binding = ActivityRedactedPagedListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = intent.getStringExtra(RedactedExtras.FORUM_NAME)
            ?: getString(R.string.redacted_threads)

        val adapter = TwoLineRowsAdapter { pos ->
            val tid = threadIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            startActivity(
                Intent(this, RedactedForumThreadActivity::class.java)
                    .putExtra(RedactedExtras.THREAD_ID, tid),
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonPrev.setOnClickListener {
            if (page > 1) {
                page--
                load(adapter)
            }
        }
        binding.buttonNext.setOnClickListener {
            if (page < totalPages) {
                page++
                load(adapter)
            }
        }
        load(adapter)
    }

    private fun load(adapter: TwoLineRowsAdapter) {
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.forumViewForum(forumId, page)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        totalPages = resp.optInt("pages", 1).coerceAtLeast(1)
                        page = resp.optInt("currentPage", page).coerceAtLeast(1)
                        binding.textPage.text = getString(R.string.redacted_page_fmt, page, totalPages)
                        val arr = resp.optJSONArray("threads")
                        val rows = mutableListOf<TwoLineRow>()
                        threadIds.clear()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val t = arr.optJSONObject(i) ?: continue
                                val tid = t.optInt("topicId")
                                rows.add(
                                    TwoLineRow(
                                        t.optString("title"),
                                        "${t.optString("authorName")} · ${t.optString("lastTime")}",
                                    ),
                                )
                                threadIds.add(tid)
                            }
                        }
                        adapter.rows = rows
                    }
                    else -> {}
                }
            }
        }.start()
    }
}

class RedactedForumThreadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedConversationBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private val messageAdapter = ConversationMessagesAdapter()
    private var threadResponse: JSONObject? = null
    private var threadId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = RedactedUiHelper.requireApi(this) ?: return
        threadId = intent.getIntExtra(RedactedExtras.THREAD_ID, 0)
        if (threadId <= 0) {
            finish()
            return
        }
        binding = ActivityRedactedConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_thread)

        binding.layoutReplyRecipientId.visibility = View.GONE
        binding.layoutReplyComposeRow.visibility = View.VISIBLE
        binding.editReplyBody.isEnabled = false
        binding.editReplyBody.isFocusable = false
        binding.editReplyBody.isFocusableInTouchMode = false
        binding.textForumReplyDisabled.visibility = View.VISIBLE
        binding.textForumReplyDisabled.text = getString(R.string.redacted_forum_post_not_available)
        binding.buttonSendReply.isEnabled = false
        binding.cardReply.alpha = 0.92f

        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = messageAdapter

        binding.swipeRefreshConversation.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.home_token_label),
            ContextCompat.getColor(this, R.color.home_ratio_ok),
        )
        binding.swipeRefreshConversation.setOnRefreshListener {
            loadThread(isPullRefresh = true)
        }

        binding.cardReply.visibility = View.VISIBLE
        loadThread(isPullRefresh = false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_redacted_forum_thread, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_copy_as_rtf -> {
                val resp = threadResponse
                if (resp == null) {
                    Toast.makeText(this, R.string.redacted_unexpected, Toast.LENGTH_SHORT).show()
                } else {
                    val rtf = buildForumThreadRtfDocument(resp)
                    val plain = buildForumThreadPlainFallback(resp)
                    RedactedRtfClipboard.copyRtf(this, getString(R.string.redacted_thread), rtf, plain)
                    Toast.makeText(this, R.string.redacted_rtf_copied, Toast.LENGTH_LONG).show()
                }
                return true
            }
            R.id.menu_open_forum_in_browser -> {
                RedactedUiHelper.openSite(this, "forums.php?action=viewthread&threadid=$threadId")
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun scrollMessagesToBottom() {
        val n = messageAdapter.itemCount
        if (n <= 0) return
        binding.recyclerMessages.post {
            binding.recyclerMessages.scrollToPosition(n - 1)
        }
    }

    private fun loadThread(isPullRefresh: Boolean) {
        if (!isPullRefresh) {
            binding.progress.visibility = View.VISIBLE
        }
        binding.textError.visibility = View.GONE
        Thread {
            val r = api.forumThread(threadId)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                binding.swipeRefreshConversation.isRefreshing = false
                when (r) {
                    is RedactedResult.Failure -> {
                        threadResponse = null
                        binding.cardReply.visibility = View.GONE
                        binding.textError.visibility = View.VISIBLE
                        binding.textError.text = RedactedHtmlSafe.safePlainTextForUi(r.message)
                        messageAdapter.rows = emptyList()
                    }
                    is RedactedResult.Success -> {
                        threadResponse = r.response
                        binding.textError.visibility = View.GONE
                        val (title, rows) = parseForumThreadRows(r.response)
                        if (title.isNotBlank()) {
                            supportActionBar?.title = title
                        }
                        messageAdapter.rows = rows
                        scrollMessagesToBottom()
                        val locked = r.response?.optBoolean("locked") == true
                        binding.cardReply.visibility = if (locked) View.GONE else View.VISIBLE
                    }
                    else -> {
                        threadResponse = null
                        binding.cardReply.visibility = View.GONE
                        binding.textError.visibility = View.VISIBLE
                        binding.textError.text = getString(R.string.redacted_unexpected)
                        messageAdapter.rows = emptyList()
                    }
                }
            }
        }.start()
    }

    private fun buildForumThreadRtfDocument(resp: JSONObject): String {
        val sb = StringBuilder()
        val title = resp.optString("threadTitle")
        if (title.isNotBlank()) {
            sb.append("\\b ").append(RedactedBbCodeToRtf.escapeRtfPlain(title)).append("\\b0\\line\\line ")
        }
        val posts = resp.optJSONArray("posts")
        if (posts != null) {
            for (i in 0 until posts.length()) {
                val p = posts.optJSONObject(i) ?: continue
                val auth = p.optJSONObject("author")
                val name = auth?.optString("authorName").orEmpty()
                val head = "— $name @ ${p.optString("addedTime")} —"
                sb.append(RedactedBbCodeToRtf.escapeRtfPlain(head)).append("\\line ")
                sb.append(RedactedBbCodeToRtf.bbToRtf(p.optString("bbBody"), 0))
                sb.append("\\par\\par ")
            }
        }
        return RedactedBbCodeToRtf.wrapDocument(sb.toString())
    }

    private fun buildForumThreadPlainFallback(resp: JSONObject): String =
        RedactedGazelleTorrentParse.stripBbCodeForPreview(
            buildString {
                append(resp.optString("threadTitle")).append("\n\n")
                val posts = resp.optJSONArray("posts")
                if (posts != null) {
                    for (i in 0 until posts.length()) {
                        append(posts.optJSONObject(i)?.optString("bbBody")).append("\n\n")
                    }
                }
            },
        )
}

class RedactedNotificationsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedPagedListBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private var page = 1
    private var totalPages = 1
    private val groupIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedPagedListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_notifications)

        val adapter = TwoLineRowsAdapter { pos ->
            val gid = groupIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            startActivity(
                Intent(this, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, gid),
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonPrev.setOnClickListener {
            if (page > 1) {
                page--
                load(adapter)
            }
        }
        binding.buttonNext.setOnClickListener {
            if (page < totalPages) {
                page++
                load(adapter)
            }
        }
        load(adapter)
    }

    private fun load(adapter: TwoLineRowsAdapter) {
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.notifications(page)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        totalPages = resp.optInt("pages", 1).coerceAtLeast(1)
                        val cp = resp.optInt("currentPage", resp.optInt("currentPages", page))
                        page = cp.coerceAtLeast(1)
                        binding.textPage.text = getString(R.string.redacted_page_fmt, page, totalPages)
                        val arr = resp.optJSONArray("results")
                        val rows = mutableListOf<TwoLineRow>()
                        groupIds.clear()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val gid = o.optInt("groupId")
                                rows.add(
                                    TwoLineRow(
                                        o.optString("groupName"),
                                        "${o.optString("format")} / ${o.optString("encoding")}",
                                        showSeedingUtorrentIcon = RedactedGazelleTorrentUser.jsonIndicatesUserSeeding(o),
                                    ),
                                )
                                groupIds.add(gid)
                            }
                        }
                        adapter.rows = rows
                    }
                    else -> {}
                }
            }
        }.start()
    }
}

class RedactedAnnouncementsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedPagedListBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private lateinit var announcementsAdapter: AnnouncementsAdapter
    private var page = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        announcementsAdapter = AnnouncementsAdapter(api.redactedAuthorizationValue())
        binding = ActivityRedactedPagedListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_announcements)
        binding.toolbar.setLogo(ContextCompat.getDrawable(this, R.drawable.ic_newspaper))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.toolbar.logoDescription = getString(R.string.redacted_announcements_logo_desc)
        }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = announcementsAdapter

        binding.buttonPrev.setOnClickListener {
            if (page > 1) {
                page--
                loadAnnouncements()
            }
        }
        binding.buttonNext.setOnClickListener {
            page++
            loadAnnouncements()
        }
        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.announcements(page = page, perPage = 10)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        val declaredPages = resp.optInt("pages", 0).let { p -> if (p > 0) p else null }
                        val currentPage = resp.optInt("currentPage", resp.optInt("currentPages", page))
                            .coerceAtLeast(1)
                        page = currentPage
                        binding.textPage.text =
                            if (declaredPages != null) {
                                getString(R.string.redacted_page_fmt, page, declaredPages)
                            } else {
                                getString(R.string.redacted_page_fmt, page, page.coerceAtLeast(1))
                            }
                        binding.buttonPrev.isEnabled = page > 1

                        val arr = resp.optJSONArray("announcements")
                        val rows = mutableListOf<AnnouncementRow>()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val rawHtml = RedactedAnnouncementHtml.contentHtml(o)
                                val imageUrls = RedactedAnnouncementHtml.extractImageSrcs(rawHtml)
                                val htmlBody = RedactedAnnouncementHtml.stripImgTags(rawHtml)
                                rows.add(
                                    AnnouncementRow(
                                        title = RedactedHtmlSafe.safePlainTextForUi(o.optString("title")),
                                        time = RedactedHtmlSafe.safePlainTextForUi(o.optString("newsTime")),
                                        htmlContent = htmlBody,
                                        imageUrls = imageUrls,
                                        useAltStripe = i % 2 == 1,
                                    ),
                                )
                            }
                        }
                        announcementsAdapter.rows = rows
                        binding.buttonNext.isEnabled =
                            if (declaredPages != null) page < declaredPages else rows.size >= 10
                    }
                    else -> {}
                }
            }
        }.start()
    }
}

class RedactedUserSearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedSimpleListBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private val userIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedSimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_user_search)
        binding.inputQueryLayout.hint = getString(R.string.redacted_search_users_hint)

        val adapter = TwoLineRowsAdapter { pos ->
            val uid = userIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            startActivity(
                Intent(this, RedactedUserProfileActivity::class.java)
                    .putExtra(RedactedExtras.USER_ID, uid),
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonSearch.setOnClickListener { search(adapter) }
        binding.buttonPrev.setOnClickListener { }
        binding.buttonNext.setOnClickListener { }
        binding.textPage.visibility = View.GONE
        binding.buttonPrev.visibility = View.GONE
        binding.buttonNext.visibility = View.GONE
    }

    private fun search(adapter: TwoLineRowsAdapter) {
        val q = binding.editQuery.text?.toString()?.trim().orEmpty()
        if (q.isBlank()) {
            Toast.makeText(this, R.string.redacted_search_users_hint, Toast.LENGTH_SHORT).show()
            return
        }
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.userSearch(q)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        val arr = resp.optJSONArray("results")
                            ?: resp.optJSONArray("users")
                            ?: JSONArray()
                        val rows = mutableListOf<TwoLineRow>()
                        userIds.clear()
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val uid = o.optInt("userId", o.optInt("id"))
                            rows.add(
                                TwoLineRow(
                                    o.optString("username"),
                                    o.optString("class"),
                                ),
                            )
                            userIds.add(uid)
                        }
                        adapter.rows = rows
                    }
                    else -> {}
                }
            }
        }.start()
    }
}

class RedactedSubscriptionsActivity : AppCompatActivity() {
    private data class SubscriptionEntry(
        val row: TwoLineRow,
        val payload: JSONObject,
    )

    private data class SubscriptionTab(
        /** [JSONObject] field `forumName` (or `forum_name`); used as tab label and sort key. */
        val forumName: String,
        /** From JSON `forumId` / `forum_id` when present; used when opening the forum from the tab. */
        val forumId: Int,
        val entries: List<SubscriptionEntry>,
    )

    private lateinit var binding: ActivityRedactedSubscriptionsBinding
    private lateinit var adapter: TwoLineRowsAdapter
    private var tabs: List<SubscriptionTab> = emptyList()
    private var activeEntries: List<SubscriptionEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        binding = ActivityRedactedSubscriptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_subscriptions)

        adapter = TwoLineRowsAdapter { pos ->
            openSubscriptionEntry(activeEntries.getOrNull(pos))
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.tabSubscriptions.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    showTab(tab?.position ?: 0)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.subscriptions(showUnreadOnly = false)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> {
                        binding.textEmpty.visibility = View.VISIBLE
                        binding.textEmpty.text = RedactedHtmlSafe.safePlainTextForUi(r.message)
                    }
                    is RedactedResult.Success -> {
                        val response = r.response ?: r.root
                        tabs = buildSubscriptionTabs(response)
                        bindTabs()
                    }
                    else -> {
                        binding.textEmpty.visibility = View.VISIBLE
                        binding.textEmpty.text = getString(R.string.redacted_unexpected)
                    }
                }
            }
        }.start()
    }

    private fun bindTabs() {
        binding.tabSubscriptions.removeAllTabs()
        if (tabs.isEmpty()) {
            adapter.rows = emptyList()
            binding.textEmpty.visibility = View.VISIBLE
            binding.textEmpty.text = getString(R.string.redacted_no_results)
            return
        }
        tabs.forEach { t ->
            binding.tabSubscriptions.addTab(
                binding.tabSubscriptions.newTab().setText(prettyKeyLabel(t.forumName)),
            )
        }
        val initial = 0
        binding.tabSubscriptions.getTabAt(initial)?.select()
        showTab(initial)
    }

    private fun showTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        activeEntries = tab.entries
        adapter.rows = tab.entries.map { it.row }
        if (tab.entries.isEmpty()) {
            binding.textEmpty.visibility = View.VISIBLE
            binding.textEmpty.text = getString(R.string.redacted_no_results)
        } else {
            binding.textEmpty.visibility = View.GONE
        }
    }

    private fun buildSubscriptionTabs(response: JSONObject): List<SubscriptionTab> {
        val root = response.optJSONObject("subscriptions") ?: response
        val otherLabel = getString(R.string.redacted_subscriptions_forum_other)
        val grouped = linkedMapOf<String, MutableList<SubscriptionEntry>>()
        val forumIdByTab = mutableMapOf<String, Int>()
        val keys = root.keys().asSequence().toList().sorted()
        for (key in keys) {
            val arr = root.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val rawForum = firstNonBlank(
                    o.optString("forumName"),
                    o.optString("forum_name"),
                )?.trim().orEmpty()
                val tabName = rawForum.ifBlank { otherLabel }
                val fid = o.optInt("forumId", o.optInt("forum_id"))
                if (fid > 0 && forumIdByTab[tabName] == null) {
                    forumIdByTab[tabName] = fid
                }
                val title = firstNonBlank(
                    o.optString("threadTitle"),
                    o.optString("subject"),
                    o.optString("title"),
                    o.optString("name"),
                    o.optString("artistName"),
                    o.optString("groupName"),
                    o.optString("torrentName"),
                    "Item ${i + 1}",
                ) ?: "Item ${i + 1}"
                val unread = isUnread(o)
                val subtitleParts = mutableListOf<String>()
                if (unread) subtitleParts += "Unread"
                firstNonBlank(
                    o.optString("categoryName"),
                    o.optString("type"),
                    o.optString("name"),
                    o.optString("lastPostTime"),
                    o.optString("time"),
                    o.optString("created"),
                )?.let { part ->
                    if (part.isNotBlank() &&
                        !part.equals(tabName, ignoreCase = true) &&
                        !part.equals(rawForum, ignoreCase = true)
                    ) {
                        subtitleParts += part
                    }
                }
                val subtitle = subtitleParts.joinToString(" · ")
                grouped.getOrPut(tabName) { mutableListOf() }
                    .add(SubscriptionEntry(TwoLineRow(title = title, subtitle = subtitle), o))
            }
        }
        return grouped.entries
            .map { (forumName, entries) ->
                val sortedEntries = entries.sortedWith(
                    compareByDescending<SubscriptionEntry> { it.row.subtitle.contains("Unread") }
                        .thenBy { it.row.title.lowercase(Locale.US) },
                )
                SubscriptionTab(
                    forumName = forumName,
                    forumId = forumIdByTab[forumName] ?: 0,
                    entries = sortedEntries,
                )
            }
            .sortedWith(
                compareBy<SubscriptionTab> { tab ->
                    if (tab.forumName == otherLabel) 1 else 0
                }.thenBy { it.forumName.lowercase(Locale.US) },
            )
    }

    private fun openForumForTab(tab: SubscriptionTab?) {
        val t = tab ?: return
        if (t.forumId <= 0) {
            Toast.makeText(this, R.string.redacted_subscriptions_forum_nav_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, RedactedForumThreadsActivity::class.java)
                .putExtra(RedactedExtras.FORUM_ID, t.forumId)
                .putExtra(RedactedExtras.FORUM_NAME, t.forumName),
        )
    }

    private fun openSubscriptionEntry(entry: SubscriptionEntry?) {
        val o = entry?.payload ?: return
        val directUrl = firstNonBlank(
            o.optString("url"),
            o.optString("link"),
            o.optString("href"),
            o.optString("permalink"),
        )?.trim()
        if (!directUrl.isNullOrBlank()) {
            RedactedUiHelper.openSite(this, directUrl)
            return
        }
        val threadId = o.optInt("threadId", o.optInt("topicId"))
        if (threadId > 0) {
            startActivity(
                Intent(this, RedactedForumThreadActivity::class.java)
                    .putExtra(RedactedExtras.THREAD_ID, threadId),
            )
            return
        }
        val groupId = o.optInt("groupId", o.optInt("torrentGroupId"))
        if (groupId > 0) {
            startActivity(
                Intent(this, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, groupId),
            )
            return
        }
        val artistId = o.optInt("artistId")
        if (artistId > 0) {
            startActivity(
                Intent(this, RedactedArtistActivity::class.java)
                    .putExtra(RedactedExtras.ARTIST_ID, artistId),
            )
            return
        }
        val userId = o.optInt("userId", o.optInt("id"))
        if (userId > 0) {
            startActivity(
                Intent(this, RedactedUserProfileActivity::class.java)
                    .putExtra(RedactedExtras.USER_ID, userId),
            )
            return
        }
        val requestId = o.optInt("requestId")
        if (requestId > 0) {
            startActivity(
                Intent(this, RedactedRequestDetailActivity::class.java)
                    .putExtra(RedactedExtras.REQUEST_ID, requestId),
            )
            return
        }
        val collageId = o.optInt("collageId")
        if (collageId > 0) {
            RedactedUiHelper.openSite(this, "collages.php?id=$collageId")
            return
        }
        Toast.makeText(this, getString(R.string.redacted_unexpected), Toast.LENGTH_SHORT).show()
    }

    private fun isUnread(o: JSONObject): Boolean {
        if (o.optBoolean("isUnread", false)) return true
        if (o.optBoolean("unread", false)) return true
        if (o.optInt("numUnread", 0) > 0) return true
        if (o.optInt("unreadCount", 0) > 0) return true
        return false
    }

    private fun prettyKeyLabel(key: String): String {
        val clean = key.replace('_', ' ').replace('-', ' ').trim()
        if (clean.isEmpty()) return key
        return clean.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
    }

    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() }
}
