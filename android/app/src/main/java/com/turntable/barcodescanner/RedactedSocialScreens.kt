package com.turntable.barcodescanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.databinding.ActivityRedactedSimpleListBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedDetailBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedGenericListBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedPagedListBinding
import com.turntable.barcodescanner.redacted.InboxRow
import com.turntable.barcodescanner.redacted.InboxRowsAdapter
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray
import org.json.JSONObject

class RedactedInboxActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedGenericListBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private lateinit var inboxAdapter: InboxRowsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedGenericListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_inbox)
        supportActionBar?.subtitle = getString(R.string.redacted_inbox_hints)

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

        loadInbox()
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
                    RedactedUiHelper.openSite(this, "inbox.php?action=viewconv&id=${row.convId}")
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

    private fun loadInbox() {
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.inbox(sort = "unread")
            runOnUiThread {
                binding.progress.visibility = View.GONE
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
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val cid = o.optInt("convId")
                                val unread = o.optBoolean("unread")
                                val sticky = o.optBoolean("sticky")
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
}

class RedactedConversationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val convId = intent.getIntExtra(RedactedExtras.CONV_ID, 0)
        if (convId <= 0) {
            finish()
            return
        }
        val binding = ActivityRedactedDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_inbox)

        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.inboxConversation(convId)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                binding.textBody.text = when (r) {
                    is RedactedResult.Failure -> r.message
                    is RedactedResult.Success -> formatConversation(r.response)
                    else -> ""
                }
            }
        }.start()
    }

    private fun formatConversation(resp: JSONObject?): String {
        if (resp == null) return ""
        val sb = StringBuilder()
        sb.appendLine(resp.optString("subject")).appendLine()
        val arr = resp.optJSONArray("messages")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                sb.appendLine("— ${m.optString("senderName")} @ ${m.optString("sentDate")} —")
                sb.appendLine(m.optString("bbBody"))
                sb.appendLine()
            }
        }
        return sb.toString()
    }
}

class RedactedForumMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedactedGenericListBinding
    private lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient
    private val forumIds = mutableListOf<Int>()
    private val forumNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedGenericListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_forums)

        val adapter = TwoLineRowsAdapter { pos ->
            val fid = forumIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            val name = forumNames.getOrNull(pos).orEmpty()
            startActivity(
                Intent(this, RedactedForumThreadsActivity::class.java)
                    .putExtra(RedactedExtras.FORUM_ID, fid)
                    .putExtra(RedactedExtras.FORUM_NAME, name),
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

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
                        val rows = mutableListOf<TwoLineRow>()
                        forumIds.clear()
                        forumNames.clear()
                        if (cats != null) {
                            for (i in 0 until cats.length()) {
                                val cat = cats.optJSONObject(i) ?: continue
                                val catName = cat.optString("categoryName")
                                val forums = cat.optJSONArray("forums")
                                if (forums != null) {
                                    for (j in 0 until forums.length()) {
                                        val f = forums.optJSONObject(j) ?: continue
                                        val fid = f.optInt("forumId")
                                        val fn = f.optString("forumName")
                                        rows.add(TwoLineRow(fn, catName))
                                        forumIds.add(fid)
                                        forumNames.add(fn)
                                    }
                                }
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val threadId = intent.getIntExtra(RedactedExtras.THREAD_ID, 0)
        if (threadId <= 0) {
            finish()
            return
        }
        val binding = ActivityRedactedDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_thread)

        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.forumThread(threadId)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                binding.textBody.text = when (r) {
                    is RedactedResult.Failure -> r.message
                    is RedactedResult.Success -> formatThread(r.response)
                    else -> ""
                }
            }
        }.start()
    }

    private fun formatThread(resp: JSONObject?): String {
        if (resp == null) return ""
        val sb = StringBuilder()
        sb.appendLine(resp.optString("threadTitle")).appendLine()
        val posts = resp.optJSONArray("posts")
        if (posts != null) {
            for (i in 0 until posts.length()) {
                val p = posts.optJSONObject(i) ?: continue
                val auth = p.optJSONObject("author")
                val name = auth?.optString("authorName").orEmpty()
                sb.appendLine("— $name @ ${p.optString("addedTime")} —")
                sb.appendLine(p.optString("bbBody"))
                sb.appendLine()
            }
        }
        return sb.toString()
    }
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
    private var page = 1

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
        supportActionBar?.title = getString(R.string.redacted_announcements)

        val adapter = TwoLineRowsAdapter { /* no-op */ }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonPrev.setOnClickListener {
            if (page > 1) {
                page--
                load(adapter)
            }
        }
        binding.buttonNext.setOnClickListener {
            page++
            load(adapter)
        }
        load(adapter)
    }

    private fun load(adapter: TwoLineRowsAdapter) {
        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.announcements(page = page, perPage = 10)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (r) {
                    is RedactedResult.Failure -> Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    is RedactedResult.Success -> {
                        val resp = r.response ?: return@runOnUiThread
                        val arr = resp.optJSONArray("announcements")
                        val rows = mutableListOf<TwoLineRow>()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                rows.add(
                                    TwoLineRow(
                                        o.optString("title"),
                                        o.optString("newsTime"),
                                    ),
                                )
                            }
                        }
                        adapter.rows = rows
                        binding.textPage.text = getString(R.string.redacted_page_fmt, page, page)
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_subscriptions)

        binding.progress.visibility = View.VISIBLE
        Thread {
            val r = api.subscriptions(showUnreadOnly = false)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                binding.textBody.text = when (r) {
                    is RedactedResult.Failure -> r.message
                    is RedactedResult.Success -> r.response?.toString(2) ?: r.root.toString(2)
                    else -> ""
                }
            }
        }.start()
    }
}
