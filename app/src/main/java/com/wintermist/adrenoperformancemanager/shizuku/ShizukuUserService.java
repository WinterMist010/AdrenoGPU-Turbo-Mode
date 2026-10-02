package com.wintermist.adrenoperformancemanager.shizuku;

import android.content.Context;
import android.os.Process;
import android.os.RemoteException;
import androidx.annotation.Keep;
import com.wintermist.adrenoperformancemanager.IUserService;
import com.wintermist.adrenoperformancemanager.utils.ShellUtils;

@Keep
public class ShizukuUserService extends IUserService.Stub {

    public ShizukuUserService() {
    }

    public ShizukuUserService(Context context) {
    }

    @Override
    public void destroy() throws RemoteException {
        System.exit(0);
    }

    @Override
    public int getUid() throws RemoteException {
        return Process.myUid();
    }

    @Override
    public String execCommand(String command) throws RemoteException {
        return execCommandWithTimeout(command, ShellUtils.DEFAULT_TIMEOUT_MS);
    }

    @Override
    public String execCommandWithTimeout(String command, long timeoutMs) throws RemoteException {
        return ShellUtils.executeLocalCommand(command, timeoutMs);
    }
}
