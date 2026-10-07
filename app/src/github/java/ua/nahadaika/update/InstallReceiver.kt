package ua.nahadaika.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import ua.nahadaika.R
import ua.nahadaika.Res

/** Відповідь системного встановлювача: попросити підтвердження або показати помилку. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // застосунок перезапуститься вже новим
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: Res.s(R.string.upd_install_status_code, status)
                val info = (Updates.state as? Updates.State.ReadyToInstall)?.info
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Updates.state = Updates.State.Failed(Res.s(R.string.upd_install_failed, message), info)
                    Toast.makeText(context, Res.s(R.string.upd_install_toast_failed, message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
