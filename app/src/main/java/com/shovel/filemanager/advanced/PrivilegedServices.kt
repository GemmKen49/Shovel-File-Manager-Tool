package com.shovel.filemanager.advanced

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager

@Keep
class ShizukuFileService : IPrivilegedFileService.Stub {
    constructor()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context)

    override fun getFileSystemService(): IBinder = FileSystemManager.getService()

    override fun getProcessUid(): Int = Process.myUid()

    override fun listRunningAppMemory(): Array<String> = PrivilegedAppProcessTools.listRunningAppMemory()

    override fun forceStopPackage(packageName: String): Boolean = PrivilegedAppProcessTools.forceStopPackage(packageName)

    override fun destroy() {
        System.exit(0)
    }
}

@Keep
class RootFileService : RootService() {
    private val binder = object : IPrivilegedFileService.Stub() {
        override fun getFileSystemService(): IBinder = FileSystemManager.getService()

        override fun getProcessUid(): Int = Process.myUid()

        override fun listRunningAppMemory(): Array<String> = PrivilegedAppProcessTools.listRunningAppMemory()

        override fun forceStopPackage(packageName: String): Boolean = PrivilegedAppProcessTools.forceStopPackage(packageName)

        override fun destroy() {}
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
    }
}
