package com.aiyu.ridelink.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.aiyu.ridelink.core.protocol.ClusterProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LinkState {
    data object Idle : LinkState
    data class Connecting(val address: String) : LinkState
    data class Ready(val address: String, val name: String?) : LinkState
    data class Failed(val reason: String) : LinkState
}

data class FoundCluster(val name: String, val address: String, val rssi: Int)

/**
 * One GATT link to the cluster. Callers must hold BLUETOOTH_SCAN / BLUETOOTH_CONNECT
 * (checked in the UI before any call), hence the MissingPermission suppression.
 */
@SuppressLint("MissingPermission")
@Singleton
class ClusterLink @Inject constructor(@ApplicationContext private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _found = MutableStateFlow<List<FoundCluster>>(emptyList())
    val found: StateFlow<List<FoundCluster>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Frames the cluster notifies on 5354. */
    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    val incoming = _incoming.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var wanted: String? = null
    private val writeLock = Mutex()
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            // Stock app filter: name contains "TVS" and not "HUD".
            if (!name.contains("TVS", true) || name.contains("HUD", true)) return
            val hit = FoundCluster(name, result.device.address, result.rssi)
            _found.update { list -> (list.filterNot { it.address == hit.address } + hit).sortedByDescending { it.rssi } }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
            Log.w(TAG, "scan failed $errorCode")
        }
    }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        _found.value = emptyList()
        _scanning.value = true
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
    }

    fun stopScan() {
        if (!_scanning.value) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _scanning.value = false
    }

    fun connect(address: String) {
        val device = adapter?.getRemoteDevice(address) ?: return fail("Bluetooth is off")
        wanted = address
        gatt?.close()
        _state.value = LinkState.Connecting(address)
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        wanted = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        _state.value = LinkState.Idle
    }

    /** Queued write-with-response (stock write type 2); returns false when not ready or on timeout. */
    suspend fun send(frame: ByteArray): Boolean = writeLock.withLock {
        val g = gatt ?: return false
        val c = writeChar ?: return false
        val done = CompletableDeferred<Boolean>().also { pendingWrite = it }
        val started = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            c.value = frame
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
        started && withTimeoutOrNull(WRITE_TIMEOUT_MS) { done.await() } == true
    }

    private fun fail(reason: String) {
        _state.value = LinkState.Failed(reason)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.requestMtu(ClusterProtocol.MTU)
                return
            }
            writeChar = null
            pendingWrite?.complete(false)
            val address = wanted ?: return
            // Out of range or scooter switched off: let Android reconnect in the background.
            g.close()
            _state.value = LinkState.Connecting(address)
            gatt = g.device.connectGatt(context, true, this, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(ClusterProtocol.SERVICE) ?: return fail("Not a TVS cluster (service missing)")
            val notify = service.getCharacteristic(ClusterProtocol.NOTIFY)
            writeChar = service.getCharacteristic(ClusterProtocol.WRITE) ?: return fail("Write characteristic missing")
            if (notify == null) return ready(g)
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(ClusterProtocol.CCCD) ?: return ready(g)
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, enable)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = enable
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = ready(g)

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            _incoming.tryEmit(value)
        }

        @Deprecated("API < 33 path")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) _incoming.tryEmit(c.value ?: return)
        }

        private fun ready(g: BluetoothGatt) {
            _state.value = LinkState.Ready(g.device.address, g.device.name)
        }
    }

    private companion object {
        const val TAG = "ClusterLink"
        const val WRITE_TIMEOUT_MS = 2_000L
    }
}
