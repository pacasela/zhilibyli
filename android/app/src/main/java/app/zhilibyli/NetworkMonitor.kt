package app.zhilibyli

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Один источник правды про интернет. Слушаем именно default network:
 * Wi-Fi без выхода в интернет (captive portal) считается офлайном,
 * как и полное отсутствие сети. Это не даёт ExoPlayer уйти в сеть,
 * когда реального соединения уже нет.
 */
object NetworkMonitor {

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var connectivity: ConnectivityManager? = null
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update()
        override fun onLost(network: Network) = update()
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = update()
    }

    fun init(context: Context) {
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivity = manager
        _online.value = checkNow(manager)
        if (registered) return

        runCatching {
            // API 24+: автоматически следит за сетью, которую система
            // выбрала для приложения, и не путает её с «просто Wi-Fi».
            manager.registerDefaultNetworkCallback(callback)
            registered = true
        }.onFailure {
            // На некоторых прошивках default callback недоступен — берём
            // обычный запрос с обязательной валидацией интернета.
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            runCatching {
                manager.registerNetworkCallback(request, callback)
                registered = true
            }
        }
    }

    fun isOnline(): Boolean {
        val manager = connectivity ?: return _online.value
        val current = checkNow(manager)
        _online.value = current
        return current
    }

    /** Есть ли вообще сеть с выходом в интернет (без проверки валидации).
     *  Используется для решений о воспроизведении: не отбрасываем треки
     *  из-за задержки валидации (иначе музыка не включается). */
    fun hasActiveNetwork(): Boolean {
        val manager = connectivity ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isOffline(): Boolean = !isOnline()

    private fun update() {
        val manager = connectivity ?: return
        _online.value = checkNow(manager)
    }

    private fun checkNow(manager: ConnectivityManager): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
