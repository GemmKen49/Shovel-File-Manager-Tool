package com.shovel.filemanager.advanced;

interface IPrivilegedFileService {
    void destroy() = 16777114;
    IBinder getFileSystemService() = 1;
    int getProcessUid() = 2;
    String[] listRunningAppMemory() = 8;
    boolean forceStopPackage(String packageName) = 9;
}
