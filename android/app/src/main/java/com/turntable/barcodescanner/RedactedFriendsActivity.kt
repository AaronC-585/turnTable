package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.turntable.barcodescanner.databinding.ActivityRedactedFriendsBinding
import com.turntable.barcodescanner.redacted.FriendsListAdapter
import com.turntable.barcodescanner.redacted.RedactedApiClient
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedFriendsStore
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.TwoLineRow
import com.turntable.barcodescanner.redacted.TwoLineRowsAdapter
import org.json.JSONArray

/**
 * Local friends list plus [RedactedApiClient.userSearch] to find and bookmark users.
 */
class RedactedFriendsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedFriendsBinding
    private lateinit var api: RedactedApiClient
    private val searchUserIds = mutableListOf<Int>()

    private lateinit var friendsAdapter: FriendsListAdapter
    private lateinit var searchAdapter: TwoLineRowsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_friends_title)

        friendsAdapter = FriendsListAdapter(
            onOpen = { e ->
                startActivity(
                    Intent(this, RedactedUserProfileActivity::class.java)
                        .putExtra(RedactedExtras.USER_ID, e.userId),
                )
            },
            onRemove = { e ->
                RedactedFriendsStore.remove(this, e.userId)
                refreshFriendsList()
                Toast.makeText(this, R.string.redacted_friends_removed, Toast.LENGTH_SHORT).show()
            },
        )
        binding.recyclerFriends.layoutManager = LinearLayoutManager(this)
        binding.recyclerFriends.adapter = friendsAdapter

        searchAdapter = TwoLineRowsAdapter { pos ->
            val uid = searchUserIds.getOrNull(pos) ?: return@TwoLineRowsAdapter
            val row = searchAdapter.rows.getOrNull(pos) ?: return@TwoLineRowsAdapter
            val entry = RedactedFriendsStore.Entry(userId = uid, username = row.title)
            if (RedactedFriendsStore.add(this, entry)) {
                Toast.makeText(this, R.string.redacted_friends_added, Toast.LENGTH_SHORT).show()
                refreshFriendsList()
            } else {
                Toast.makeText(this, R.string.redacted_friends_already, Toast.LENGTH_SHORT).show()
            }
        }
        binding.recyclerSearch.layoutManager = LinearLayoutManager(this)
        binding.recyclerSearch.adapter = searchAdapter

        binding.buttonSearch.setOnClickListener { runSearch() }
        binding.editQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else {
                false
            }
        }

        refreshFriendsList()
    }

    override fun onResume() {
        super.onResume()
        refreshFriendsList()
    }

    private fun refreshFriendsList() {
        val list = RedactedFriendsStore.load(this)
        friendsAdapter.rows = list
        binding.textFriendsEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun runSearch() {
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
                        searchUserIds.clear()
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val uid = o.optInt("userId", o.optInt("id"))
                            rows.add(
                                TwoLineRow(
                                    o.optString("username"),
                                    o.optString("class"),
                                ),
                            )
                            searchUserIds.add(uid)
                        }
                        searchAdapter.rows = rows
                        if (rows.isEmpty()) {
                            Toast.makeText(this, R.string.redacted_no_results, Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {}
                }
            }
        }.start()
    }
}
