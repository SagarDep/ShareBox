package com.ecjtu.sharebox.presenter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.ecjtu.sharebox.Constants
import com.ecjtu.sharebox.PreferenceInfo
import com.ecjtu.sharebox.R
import com.ecjtu.sharebox.getMainApplication
import com.ecjtu.sharebox.notification.ServerComingNotification
import com.ecjtu.sharebox.ui.activity.ActionBarFragmentActivity
import com.ecjtu.sharebox.ui.activity.MainActivity
import com.ecjtu.sharebox.ui.activity.SettingsActivity
import com.ecjtu.sharebox.ui.adapter.DeviceRecyclerViewAdapter
import com.ecjtu.sharebox.ui.dialog.ApDataDialog
import com.ecjtu.sharebox.ui.dialog.EditNameDialog
import com.ecjtu.sharebox.ui.dialog.TextItemDialog
import com.ecjtu.sharebox.ui.dialog.WifiBottomSheetDialog
import com.ecjtu.sharebox.ui.fragment.FilePickDialogFragment
import com.ecjtu.sharebox.ui.fragment.HelpFragment
import com.ecjtu.sharebox.ui.fragment.WebViewFragment
import com.ecjtu.sharebox.ui.state.StateMachine
import com.ecjtu.sharebox.util.activity.ActivityUtil
import com.ecjtu.sharebox.util.photo.CapturePhotoHelper
import com.ecjtu.sharebox.util.photo.PickPhotoHelper
import org.ecjtu.channellibrary.devicesearch.DeviceSearcher
import org.ecjtu.channellibrary.devicesearch.DiscoverHelper
import org.ecjtu.channellibrary.wifiutil.NetworkUtil
import org.ecjtu.easyserver.server.DeviceInfo
import java.io.File
import java.lang.Exception


/**
 * Created by KerriGan on 2017/6/2.
 */
class MainActivityDelegate(owner: MainActivity) : Delegate<MainActivity>(owner), ActivityCompat.OnRequestPermissionsResultCallback {

    private var mToolbar: Toolbar
    private var mDrawerLayout: DrawerLayout
    private var mDrawerToggle: ActionBarDrawerToggle
    private var mFloatingActionButton: FloatingActionButton
    private var mViewSwitcher: ViewSwitcher? = null
    private var mWifiButton: Button
    private var mHotspotButton: Button
    private var mApName: TextView
    private var mWifiImage: ImageView
    private var mTextName: TextView? = null

    private val REQUEST_CODE = 0x10

    private var mServerSet = mutableListOf<DeviceSearcher.DeviceBean>()

    private var mClientSet = mutableListOf<DeviceSearcher.DeviceBean>()

    private val mRequestPermission = arrayOf(Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_STATE)

    private var mRecyclerView: RecyclerView? = null

    private var mDeviceInfoList: MutableList<DeviceInfo> = mutableListOf<DeviceInfo>()

    private var mPhotoHelper: CapturePhotoHelper? = null

    private var mImageHelper: PickPhotoHelper? = null

    private val DELAY_TIME = 8000L

    private var mWifiImageStateMachine: StateMachine? = null

    companion object {
        const val DEBUG = true
        private const val TAG = "MainActivityDelegate"
        private const val TAG_FRAGMENT = "FilePickDialogFragment"
    }

    init {
        mToolbar = findViewById(R.id.toolbar) as Toolbar
        mDrawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout

        mDrawerToggle = ActionBarDrawerToggle(owner, mDrawerLayout, mToolbar, 0, 0)
        mDrawerToggle.syncState()
        mDrawerLayout.setDrawerListener(mDrawerToggle)

        mFloatingActionButton = findViewById(R.id.floating_action_button) as FloatingActionButton
        mFloatingActionButton.setOnClickListener({ view ->
            val dlg = FilePickDialogFragment(owner)
            dlg.show(owner.supportFragmentManager, TAG_FRAGMENT)
        })

        //for view switcher
        mViewSwitcher = findViewById(R.id.view_switcher) as ViewSwitcher
        var view0: View = LayoutInflater.from(owner).inflate(R.layout.layout_main_activity_data, null)
        var view1: View = LayoutInflater.from(owner).inflate(R.layout.layout_main_activity_list, null)

        mViewSwitcher?.addView(view0)
        mViewSwitcher?.addView(view1)

        view0.findViewById(R.id.button_help).setOnClickListener {
            val intent = ActionBarFragmentActivity.newInstance(owner, HelpFragment::class.java, title = "Help")
            owner.startActivity(intent)
        }

        mWifiButton = findViewById(R.id.btn_wifi) as Button
        mHotspotButton = findViewById(R.id.btn_hotspot) as Button


        mWifiButton.setOnClickListener {
            val intent = Intent()
            val action = arrayOf(WifiManager.ACTION_PICK_WIFI_NETWORK, Settings.ACTION_WIFI_SETTINGS)
            for (str in action) {
                try {
                    intent.action = Settings.ACTION_WIFI_SETTINGS
                    owner.startActivity(intent)
                    break
                } catch (ex: Exception) {
                }
            }
        }
        mHotspotButton.setOnClickListener {
            for (index in 0 until mRequestPermission.size) {
                if (ActivityCompat.checkSelfPermission(owner, mRequestPermission[index]) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(owner, mRequestPermission, REQUEST_CODE)
                    return@setOnClickListener
                }
            }

            val dlg = WifiBottomSheetDialog(owner, owner)
            dlg.show()
        }

        mApName = findViewById(R.id.ap_name) as TextView

        mRecyclerView = view1 as RecyclerView
        mRecyclerView?.adapter = DeviceRecyclerViewAdapter(mDeviceInfoList, owner)

        var manager: LinearLayoutManager = LinearLayoutManager(owner, LinearLayoutManager.VERTICAL, false)
        mRecyclerView?.layoutManager = manager

        mWifiImage = findViewById(R.id.image_wifi) as ImageView

        mWifiImageStateMachine = object : StateMachine(owner, R.array.main_activity_delegate_array, mWifiImage) {
            override fun updateView(index: Int) {
                val value = getArrayValueByIndex(index)
                value?.let {
                    mWifiImage.setImageResource(value)
                }
            }
        }

        checkCurrentNetwork(null)

        initDrawerLayout()

        doSearch()
    }

    private fun initDrawerLayout() {
        mTextName = findViewById(R.id.text_name) as TextView

        findViewById(R.id.text_faq)?.setOnClickListener {
            var intent = ActionBarFragmentActivity.newInstance(owner, WebViewFragment::class.java,
                    WebViewFragment.openInnerUrl("faq.html"), "FAQ")
            owner.startActivity(intent)
        }

        findViewById(R.id.text_setting)?.setOnClickListener {
            owner.startActivity(Intent(owner, SettingsActivity::class.java))
        }

        findViewById(R.id.text_help)?.setOnClickListener {
            val intent = ActionBarFragmentActivity.newInstance(owner, HelpFragment::class.java, title = "Help")
            owner.startActivity(intent)
        }

        findViewById(R.id.btn_close)?.setOnClickListener {
            owner.getHandler()?.obtainMessage(MainActivity.MSG_CLOSE_APP)?.sendToTarget()
        }

        findViewById(R.id.icon)?.setOnClickListener {
            var dlg = TextItemDialog(owner)
            dlg.setupItem(arrayOf(owner.getString(R.string.pick_from_camera), owner.getString(R.string.pick_from_album), owner.getString(R.string.cancel)))
            dlg.setOnClickListener { index ->
                if (index == 0) {
                    mImageHelper = null
                    mPhotoHelper = CapturePhotoHelper(owner)
                    mPhotoHelper?.takePhoto()
                } else if (index == 1) {
                    mPhotoHelper = null
                    mImageHelper = PickPhotoHelper(owner)
                    mImageHelper?.takePhoto()
                }
                dlg.cancel()
            }
            dlg.show()
        }

        findViewById(R.id.text_name)?.setOnClickListener {
            var dlg = EditNameDialog(activity = owner, context = owner)
            dlg.show()
            dlg.setOnDismissListener({
                mTextName?.setText(PreferenceManager.getDefaultSharedPreferences(owner).
                        getString(PreferenceInfo.PREF_DEVICE_NAME, Build.MODEL))
            })
        }

        mTextName?.setText(PreferenceManager.getDefaultSharedPreferences(owner).
                getString(PreferenceInfo.PREF_DEVICE_NAME, Build.MODEL))
        checkIconHead()
    }

    fun onOptionsItemSelected(item: MenuItem?): Boolean {

        when (item?.getItemId()) {
            R.id.qr_code -> {

                var map = owner.getMainApplication().getSavedInstance()
                var state = map.get(Constants.AP_STATE)

                if (state == Constants.NetWorkState.MOBILE || state == Constants.NetWorkState.NONE) {
                    Toast.makeText(owner, R.string.need_wifi_or_hotspot, Toast.LENGTH_SHORT).show()
                } else {
                    val dialog = ApDataDialog(owner, owner)
                    dialog.show()
                }
                return true
            }
            R.id.refresh -> {
                if (owner.refreshing) {
                    owner.getMainService()?.prepareAndStartHelper(true,true)
                } else {
                    owner.getMainService()?.stopHelper(true,true)
                }
                return true
            }
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQUEST_CODE) return
        var hasPermission = true

        for (index in 0 until mRequestPermission.size) {
            if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                hasPermission = false
            }

            if (!ActivityCompat.shouldShowRequestPermissionRationale(owner, mRequestPermission[index])) {
                owner.startActivity(ActivityUtil.getAppDetailSettingIntent(owner))
                return
            }
        }

        if (hasPermission) {
            var dialog = ApDataDialog(owner, owner)
            dialog.show()
        }
    }

    fun checkCurrentNetwork(info: WifiInfo?): Boolean {
        var hasAccess = false

        if (NetworkUtil.isWifi(owner) || info != null) {
            var wifiInfo: WifiInfo? = null
            if (info != null)
                wifiInfo = info
            else
                wifiInfo = NetworkUtil.getConnectWifiInfo(owner)

            mApName.setText(getRealName(wifiInfo!!.ssid))
            mWifiButton.isActivated = true
            mHotspotButton.isActivated = false
            mWifiImageStateMachine?.updateView(0)
            hasAccess = true
            owner.getMainApplication().getSavedInstance().put(Constants.AP_STATE, Constants.NetWorkState.WIFI)

            owner.getHandler()?.obtainMessage(MainActivity.MSG_START_SERVER)?.sendToTarget()
        } else if (NetworkUtil.isHotSpot(owner)) {
            var config = NetworkUtil.getHotSpotConfiguration(owner)
            mApName.setText(getRealName(config.SSID))
            mWifiButton.isActivated = false
            mHotspotButton.isActivated = true
            mWifiImageStateMachine?.updateView(1)
            hasAccess = true
            owner.getMainApplication().getSavedInstance().put(Constants.AP_STATE, Constants.NetWorkState.AP)

            owner.getHandler()?.obtainMessage(MainActivity.MSG_START_SERVER)?.sendToTarget()
        } else if (NetworkUtil.isMobile(owner)) {
            mApName.setText(R.string.cellular)
            mWifiImageStateMachine?.updateView(2)

            mWifiButton.isActivated = false
            mHotspotButton.isActivated = false
            hasAccess = false
            owner.getMainApplication().getSavedInstance().put(Constants.AP_STATE, Constants.NetWorkState.MOBILE)
        } else {
            mApName.setText(R.string.no_internet)
            mWifiImageStateMachine?.updateView(2)
            mWifiButton.isActivated = false
            mHotspotButton.isActivated = false
            hasAccess = false
            owner.getMainApplication().getSavedInstance().put(Constants.AP_STATE, Constants.NetWorkState.NONE)
        }
        return hasAccess
    }

    private fun getRealName(name: String): String {
        var str = name
        if (str[0] == '"')
            str = str.drop(1)

        if (str[str.length - 1] == '"')
            str = str.dropLast(1)
        return str
    }

    fun doSearch() {

        val name = PreferenceManager.getDefaultSharedPreferences(owner).
                getString(PreferenceInfo.PREF_DEVICE_NAME, Build.MODEL)
        var obj = owner.getMainApplication().getSavedInstance().get(Constants.KEY_SERVER_PORT)
        var port = ""
        if (obj is String)
            port = obj

        if (TextUtils.isEmpty(port)) return

        owner.getMainService()?.createHelper(name, port.toInt(), "/API/Icon")
        owner.getMainService()?.setMessageListener { msg, deviceSet, handler ->
            var state = owner.getMainApplication().getSavedInstance().get(Constants.AP_STATE)
            var ip = ""
            if (state == Constants.NetWorkState.WIFI) {
                val ips = NetworkUtil.getLocalWLANIps()
                if (ips.isNotEmpty())
                    ip = NetworkUtil.getLocalWLANIps()[0]
            } else if (state == Constants.NetWorkState.AP) {
                val ips = NetworkUtil.getLocalApIps()
                if (ips.isNotEmpty())
                    ip = NetworkUtil.getLocalApIps()[0]
            }
            when (msg) {
                DiscoverHelper.MSG_FIND_DEVICE -> {

                    for (obj in deviceSet) {
                        if (isSelf(ip, obj)) continue
                        val index = mClientSet.indexOf(obj)
                        if (index < 0) {
                            mClientSet.add(obj)
                            ServerComingNotification(owner).buildServerComingNotification("搜索到新的设备", obj.name, "ShareBox:" + "找到新的设备").send()
                        } else {
                            val old = mClientSet.get(index)
                            old.name = obj.name
                            old.room = obj.room
                        }
                    }
                    applyDeviceInfo(mClientSet)
                    if (owner.refreshing) {
                        var msg = handler.obtainMessage(DiscoverHelper.MSG_START_FIND_DEVICE)
                        handler.sendMessageDelayed(msg, DELAY_TIME)
                    }
                }
                DiscoverHelper.MSG_BEING_SEARCHED -> {
                    for (obj in deviceSet) {
                        if (isSelf(ip, obj)) continue
                        var index = mServerSet.indexOf(obj)
                        if (index < 0) {
                            mServerSet.add(obj)
                        } else {
                            var old = mServerSet.get(index)
                            old.name = obj.name
                            old.room = obj.room
                        }
                    }
                    applyDeviceInfo(mServerSet)
                    if (owner.refreshing) {
                        var msg = handler.obtainMessage(DiscoverHelper.MSG_START_BEING_SEARCHED)
                        handler.sendMessageDelayed(msg, DELAY_TIME)
                    }
                }
                DiscoverHelper.MSG_START_FIND_DEVICE -> {
                    owner.getMainService()?.prepareAndStartHelper(true, true)
                }
                DiscoverHelper.MSG_START_BEING_SEARCHED -> {
                    owner.getMainService()?.prepareAndStartHelper(true, true)
                }
            }
        }

        if (owner.refreshing) {
            owner.getMainService()?.prepareAndStartHelper(true, true)
        }
    }

    private fun applyDeviceInfo(mutableSet: MutableList<DeviceSearcher.DeviceBean>) {
        var flag: Boolean
        for (bean in mutableSet) {
            flag = false
            var old: DeviceInfo? = null
            for (info in mDeviceInfoList) {
                if (info.ip.equals(bean.ip)) {
                    flag = true
                    old = info
                }
            }

            if (!flag) {
                val data = bean.name
                val arr = data.split(",")
                var port = 0
                port = try {
                    Integer.parseInt(arr[1])
                } catch (e: Exception) {
                    0
                }
                val deviceInfo = DeviceInfo(arr[0], bean.ip, port, arr[2])
                deviceInfo.updateTime = arr[3].toLong()
                mDeviceInfoList.add(deviceInfo)
                mRecyclerView?.adapter?.notifyDataSetChanged()
            } else {
                val data = bean.name
                val arr = data.split(",")
                var port = 0
                port = try {
                    Integer.parseInt(arr[1])
                } catch (e: Exception) {
                    0
                }
                if (port == 0) continue
                var needUpdate = false

                if (old?.port != port || old.icon != arr[2] || old.updateTime != arr[3].toLong()) {
                    needUpdate = true
                    Log.e(TAG, "need update recycler view")
                }

                old?.name = arr[0]
                old?.port = port
                old?.icon = arr[2]

                if (needUpdate) {
                    mRecyclerView?.adapter?.notifyDataSetChanged()
                }
            }
        }

        val index = mViewSwitcher?.indexOfChild(mRecyclerView)
        val nextIndex = mViewSwitcher?.indexOfChild(mViewSwitcher?.nextView)
        if (mDeviceInfoList.size != 0) {
            if (index == nextIndex) {
                mViewSwitcher?.showNext()
            }
        } else {
            if (index != nextIndex) {
                mViewSwitcher?.showNext()
            }
        }
    }


    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        mPhotoHelper?.onActivityResult(requestCode, resultCode, data)
        mImageHelper?.onActivityResult(requestCode, resultCode, data)
        checkIconHead()
    }

    fun checkIconHead() {
        val iconFile = File(owner.filesDir, Constants.ICON_HEAD)
        if (iconFile.exists()) {
            val icon = findViewById(R.id.drawer_view)?.findViewById(R.id.icon) as ImageView //有相同id 找到错误的view
            icon.setImageBitmap(BitmapFactory.decodeFile(iconFile.absolutePath))
        }
    }

    private fun isSelf(ip: String, device: DeviceSearcher.DeviceBean): Boolean {
        if (DEBUG) return false

        if (ip == device.ip) {
            return true
        }
        return false
    }

    fun onDestroy() {
        owner.getMainService()?.stopHelper(true,true)
        mPhotoHelper?.clearCache()
        mImageHelper?.clearCache()
    }

    fun getRecyclerView(): RecyclerView? {
        return mRecyclerView
    }
}