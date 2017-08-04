package me.shreyasr.chatse.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.text.InputType
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.koushikdutta.ion.Ion
import com.squareup.okhttp.FormEncodingBuilder
import com.squareup.okhttp.Request
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.room_nav_header.*
import me.shreyasr.chatse.R
import me.shreyasr.chatse.chat.adapters.RoomAdapter
import me.shreyasr.chatse.chat.service.IncomingEventService
import me.shreyasr.chatse.chat.service.IncomingEventServiceBinder
import me.shreyasr.chatse.login.LoginActivity
import me.shreyasr.chatse.network.Client
import me.shreyasr.chatse.network.ClientManager
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

class ChatActivity : AppCompatActivity(), ServiceConnection {
    private lateinit var serviceBinder: IncomingEventServiceBinder
    private var networkHandler: Handler? = null
    private val uiThreadHandler = Handler(Looper.getMainLooper())
    val soRoomList = arrayListOf<Room>()
    val seRoomList = arrayListOf<Room>()
    lateinit var fkey: String
    lateinit var soRoomAdapter: RoomAdapter
    lateinit var seRoomAdapter: RoomAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_chat)

        soRoomAdapter = RoomAdapter(Client.SITE_STACK_OVERFLOW, soRoomList, this)
        seRoomAdapter = RoomAdapter(Client.SITE_STACK_EXCHANGE, seRoomList, this)
        stackoverflow_room_list.adapter = soRoomAdapter
        stackoverflow_room_list.layoutManager = LinearLayoutManager(applicationContext, LinearLayoutManager.VERTICAL, false)
        stackexchange_room_list.adapter = seRoomAdapter
        stackexchange_room_list.layoutManager = LinearLayoutManager(applicationContext, LinearLayoutManager.VERTICAL, false)

        runOnUiThread {
            soRoomAdapter.notifyDataSetChanged()
            seRoomAdapter.notifyDataSetChanged()
        }
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)

        toggle.syncState()
        loadUserData()

        val serviceIntent = Intent(this, IncomingEventService::class.java)
        this.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)

        val handlerThread = HandlerThread("ChatActivityNetworkHandlerThread")
        handlerThread.start()
        networkHandler = Handler(handlerThread.looper)
    }

    override fun onResume() {
        super.onResume()
    }

    fun loadUserData() {
        val userID = defaultSharedPreferences.getInt("SOID", -1)
        val seID = defaultSharedPreferences.getInt("SEID", -1)
        if (userID != -1) {
            Ion.with(applicationContext)
                    .load("https://chat.stackoverflow.com/users/thumbs/$userID")
                    .asJsonObject()
                    .setCallback { e, result ->
                        if (result != null) {
                            userName.text = result.get("name").asString
                            userEmail.text = defaultSharedPreferences.getString("email", "")
                        }
                    }
        } else if (seID != -1) {
            Ion.with(applicationContext)
                    .load("https://chat.stackexchange.com/users/thumbs/$userID")
                    .asJsonObject()
                    .setCallback { e, result ->
                        if (result != null) {
                            userName.text = result.get("name").asString
                            userEmail.text = defaultSharedPreferences.getString("email", "")
                        }
                    }
        } else {
            Log.e("ChatActivity", "Userid not found")
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        Timber.d("Service connect")
        serviceBinder = binder as IncomingEventServiceBinder
        doAsync {
            fkey = serviceBinder.loadRoom(ChatRoom(Client.SITE_STACK_OVERFLOW, 1)).fkey
            addRoomsToDrawer(fkey)
        }
        loadChatFragment(ChatRoom(Client.SITE_STACK_OVERFLOW, 15))
    }

    fun addRoomsToDrawer(fkey: String) {
        val soID = defaultSharedPreferences.getInt("SOID", -1)

        if (soID != -1) {
            Ion.with(applicationContext)
                    .load("${Client.SITE_STACK_OVERFLOW}/users/thumbs/$soID")
                    .asJsonObject()
                    .setCallback { e, result ->
                        if (result != null) {
                            soRoomList.clear()
                            so_header_text.visibility = View.VISIBLE
                            stackoverflow_room_list.visibility = View.VISIBLE

                            val rooms = result.get("rooms").asJsonArray
                            rooms.forEach {
                                val room = it.asJsonObject
                                val roomName = room.get("name").asString
                                val roomNum = room.get("id").asLong
                                createRoom(Client.SITE_STACK_OVERFLOW, roomName, roomNum, 0, fkey)
                            }
                            if(rooms.size() == 0){
                                so_header_text.visibility = View.GONE
                                stackoverflow_room_list.visibility = View.GONE
                            }
                        }
                    }
        } else {
            so_header_text.visibility = View.GONE
            stackoverflow_room_list.visibility = View.GONE
        }

        val seID = defaultSharedPreferences.getInt("SEID", -1)
        if (seID != -1) {
            Ion.with(applicationContext)
                    .load("${Client.SITE_STACK_EXCHANGE}/users/thumbs/$seID")
                    .asJsonObject()
                    .setCallback { e, result ->
                        if (e != null) {
                            Log.e("addRoomsToDrawer", e.message.toString())
                        } else {
                            if (result != null) {
                                seRoomList.clear()
                                se_header_text.visibility = View.VISIBLE
                                stackexchange_room_list.visibility = View.VISIBLE
                                val rooms = result.get("rooms").asJsonArray
                                rooms.forEach {
                                    val room = it.asJsonObject
                                    val roomName = room.get("name").asString
                                    val roomNum = room.get("id").asLong
                                    createRoom(Client.SITE_STACK_EXCHANGE, roomName, roomNum, 0, fkey)
                                }
                                if(rooms.size() == 0){
                                    se_header_text.visibility = View.GONE
                                    stackexchange_room_list.visibility = View.GONE
                                }
                            }
                        }
                    }
        } else {
            se_header_text.visibility = View.GONE
            stackexchange_room_list.visibility = View.GONE
        }
    }

    fun createRoom(site: String, roomName: String, roomNum: Long, lastActive: Long, fkey: String) {
        doAsync {
            val client = ClientManager.client

            val soChatPageRequest = Request.Builder()
                    .url("$site/rooms/thumbs/$roomNum/")
                    .build()
            val response = client.newCall(soChatPageRequest).execute()
            val jsonData = response.body().string()
            val json = JSONObject(jsonData)
            val description = json.getString("description")
            val isFavorite = json.getBoolean("isFavorite")
            val tags = json.getString("tags")

            if (site == Client.SITE_STACK_OVERFLOW) {
                soRoomList.add(Room(roomName, roomNum, description, lastActive, isFavorite, tags, fkey))
                runOnUiThread {
                    soRoomAdapter.notifyDataSetChanged()
                }
            } else {
                seRoomList.add(Room(roomName, roomNum, description, lastActive, isFavorite, tags, fkey))
                runOnUiThread {
                    seRoomAdapter.notifyDataSetChanged()
                }
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            when (item.itemId) {
                R.id.search_rooms -> {
                    val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AppTheme))
                    builder.setTitle("Edit message")

                    val l = LinearLayout(applicationContext)
                    l.orientation = LinearLayout.VERTICAL
                    val dpi = application.resources.displayMetrics.density.toInt()
                    val input = EditText(applicationContext)
                    l.setPadding((19 * dpi), (5 * dpi), (14 * dpi), (5 * dpi))
                    input.hint = "Enter Room ID"
                    input.inputType = InputType.TYPE_CLASS_NUMBER
                    input.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                    l.addView(input)

                    val spinner = Spinner(applicationContext)
                    input.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                    spinner.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, arrayListOf("Stackoverflow", "Stackexchange"))
                    var site = Client.SITE_STACK_OVERFLOW
                    l.addView(spinner)
                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

                        override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
                            when (pos) {
                                0 -> site = Client.SITE_STACK_OVERFLOW
                                1 -> site = Client.SITE_STACK_EXCHANGE
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<out Adapter>?) {
                            site = Client.SITE_STACK_OVERFLOW
                        }

                    }

                    builder.setView(l)

                    builder.setPositiveButton("Join Room", { dialog, _ ->
                        Log.wtf("JOIN", site + " " + input.text.toString())
                        loadChatFragment(ChatRoom(site, input.text.toString().toInt()))
                        dialog.dismiss()
                    })
                    builder.setNegativeButton("Cancel", { dialog, _ ->
                        dialog.cancel()
                    })

                    builder.show()

                }
                R.id.action_logout -> {
                    startActivity(Intent(applicationContext, LoginActivity::class.java))
                    defaultSharedPreferences.edit().clear().apply()
                }
                R.id.room_information -> return false
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        Timber.d("Service disconnect")
    }

    fun loadChatFragment(room: ChatRoom) {
        networkHandler?.post {
            try {
                addChatFragment(createChatFragment(room))
            } catch (e: IOException) {
                Timber.e("Failed to create chat fragment", e)
            } catch (e: JSONException) {
                Timber.e("Failed to create chat fragment", e)
            }
        }
        drawer_layout.closeDrawers()
    }

    private fun addChatFragment(fragment: ChatFragment) {
        uiThreadHandler.post {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commitAllowingStateLoss()
        }
    }

    @Throws(IOException::class, JSONException::class)
    private fun createChatFragment(room: ChatRoom): ChatFragment {
        val roomInfo = serviceBinder.loadRoom(room)
        rejoinFavoriteRooms()
        serviceBinder.joinRoom(room, roomInfo.fkey)
        val chatFragment = ChatFragment.createInstance(room, roomInfo.name, roomInfo.fkey)
        serviceBinder.registerListener(room, chatFragment)
        runOnUiThread {
            supportActionBar?.title = roomInfo.name
        }
        return chatFragment
    }

    fun rejoinFavoriteRooms() {
        val client = ClientManager.client
        doAsync {
            val soRoomInfo = serviceBinder.loadRoom(ChatRoom(Client.SITE_STACK_OVERFLOW, 1))
            val soRequestBody = FormEncodingBuilder()
                    .add("fkey", soRoomInfo.fkey)
                    .add("immediate", "true")
                    .add("quiet", "true")
                    .build()
            val soChatPageRequest = Request.Builder()
                    .url(Client.SITE_STACK_OVERFLOW + "/chats/join/favorite")
                    .post(soRequestBody)
                    .build()
            client.newCall(soChatPageRequest).execute()

            val seRoomInfo = serviceBinder.loadRoom(ChatRoom(Client.SITE_STACK_EXCHANGE, 1))
            val seRequestBody = FormEncodingBuilder()
                    .add("fkey", seRoomInfo.fkey)
                    .add("immediate", "true")
                    .add("quiet", "true")
                    .build()
            val seChatPageRequest = Request.Builder()
                    .url(Client.SITE_STACK_EXCHANGE + "/chats/join/favorite")
                    .post(seRequestBody)
                    .build()
            client.newCall(seChatPageRequest).execute()
        }
    }
}

data class Room(val name: String, val roomID: Long, val description: String, val lastActive: Long?, var isFavorite: Boolean, val tags: String, val fkey: String)