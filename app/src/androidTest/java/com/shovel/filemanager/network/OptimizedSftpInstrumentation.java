package com.shovel.filemanager.network;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.shovel.filemanager.terminal.PrivilegedTerminalRuntimeVerifier;
import com.shovel.filemanager.media.BackgroundPlaybackRuntimeVerifier;

import java.util.Arrays;

public final class OptimizedSftpInstrumentation extends Instrumentation {
    private Bundle testArguments;

    @Override
    public void onCreate(Bundle arguments) {
        testArguments = arguments == null ? new Bundle() : new Bundle(arguments);
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        String suite = testArguments.getString("shovelSuite", "sftp");
        char[] password = new char[0];
        try {
            boolean verified;
            if ("share-index".equals(suite)) {
                launchTargetActivity();
                verified = com.shovel.filemanager.transfer.ReleaseShareIndexVerifier.verify(this);
            } else if ("issue-regression".equals(suite)) {
                launchTargetActivity();
                verified = (Boolean) Class.forName("com.shovel.filemanager.transfer.IssueRegressionRuntimeVerifier")
                        .getMethod("verify", Instrumentation.class).invoke(null, this);
            } else if ("nearby".equals(suite)) {
                launchTargetActivity();
                verified = com.shovel.filemanager.transfer.NearbyRuntimeVerifier.verify(this);
            } else if ("transfer-diagnostics".equals(suite)) {
                launchTargetActivity();
                verified = com.shovel.filemanager.transfer.NearbyRuntimeVerifier.verifyDiagnostics(this);
            } else if ("localization".equals(suite)) {
                launchTargetActivity();
                verified = com.shovel.filemanager.transfer.NearbyRuntimeVerifier.verifyLocales(this);
            } else if ("split-apk".equals(suite)) {
                launchTargetActivity();
                verified = com.shovel.filemanager.apk.SplitApkRuntimeVerifier.verify(this);
            } else if ("background-playback".equals(suite)) {
                launchTargetActivity();
                verified = BackgroundPlaybackRuntimeVerifier.verify(this);
            } else if ("protected-media".equals(suite)) {
                launchTargetActivity();
                verified = com.shovel.filemanager.media.ProtectedMediaRuntimeVerifier.verify(this);
            } else if ("webdav".equals(suite)) {
                password = required("shovelWebDavPassword").toCharArray();
                verified = WebDavRuntimeVerifier.verify(
                        required("shovelWebDavHost"), Integer.parseInt(required("shovelWebDavPort")),
                        Boolean.parseBoolean(testArguments.getString("shovelWebDavTls", "false")),
                        required("shovelWebDavUsername"), password, required("shovelWebDavPath"),
                        testArguments.getString("shovelWebDavExpectedFile", ""),
                        testArguments.getString("shovelWebDavExpectedError", "")
                );
            } else if ("privileged".equals(suite)) {
                launchTargetActivity();
                verified = PrivilegedTerminalRuntimeVerifier.verifyAuthorizedBackend(
                        getTargetContext(), Integer.parseInt(required("shovelExpectedUid"))
                );
            } else if ("root-service".equals(suite)) {
                verified = PrivilegedTerminalRuntimeVerifier.verifyRootServiceContract(getTargetContext());
            } else if ("terminal-failure".equals(suite)) {
                launchTargetActivity();
                verified = PrivilegedTerminalRuntimeVerifier.verifyImmediateFailure(getTargetContext());
            } else if ("sftp".equals(suite)) {
                password = required("shovelSftpPassword").toCharArray();
                verified = SftpRuntimeVerifier.verifyPasswordConnection(
                        required("shovelSftpHost"), Integer.parseInt(required("shovelSftpPort")),
                        required("shovelSftpUsername"), password, required("shovelSftpExpectedFile")
                );
            } else {
                throw new IllegalArgumentException("Unknown optimized test suite");
            }
            if (!verified) {
                throw new IllegalStateException("Optimized runtime check did not match its expected result");
            }
            result.putString("stream", "\nAF_OPTIMIZED_" + suite.toUpperCase(java.util.Locale.ROOT) + "_OK\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString(
                    "stream",
                    "\nAF_OPTIMIZED_" + suite.toUpperCase(java.util.Locale.ROOT) + "_FAILED: " + error.getClass().getSimpleName()
                            + "\n" + Log.getStackTraceString(error) + "\n"
            );
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void launchTargetActivity() {
        // Graph-backed checks must wait for normal application initialization.
        startActivitySync(new Intent(Intent.ACTION_MAIN)
                .setClassName(getTargetContext(), "com.shovel.filemanager.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private String required(String name) {
        String value = testArguments.getString(name);
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException("Missing instrumentation argument: " + name);
        }
        return value;
    }
}
