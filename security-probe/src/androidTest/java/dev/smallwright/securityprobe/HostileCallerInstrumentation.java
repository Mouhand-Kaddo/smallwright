package dev.smallwright.securityprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import dev.smallwright.protocol.IBuildService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A separately signed app tries to bind and invoke the protected build service. */
public final class HostileCallerInstrumentation extends Instrumentation {
    private static final String WORKER = "dev.smallwright.worker";
    private static final String PERMISSION = "dev.smallwright.permission.BIND_BUILD_WORKER";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            PackageManager packages = context.getPackageManager();
            if (Process.myUid() != context.getApplicationInfo().uid
                    || Process.myUid() == packages.getApplicationInfo(WORKER, 0).uid) {
                throw new AssertionError("Probe is not running under its own UID");
            }
            ComponentName service = new ComponentName(WORKER, WORKER + ".BuildService");
            if (!PERMISSION.equals(packages.getServiceInfo(service, 0).permission)) {
                throw new AssertionError("Worker service is missing its signature permission");
            }
            if (packages.checkSignatures(context.getPackageName(), WORKER)
                    != PackageManager.SIGNATURE_NO_MATCH) {
                throw new AssertionError("Probe and worker are not separately signed");
            }
            if (packages.checkPermission(PERMISSION, context.getPackageName())
                    != PackageManager.PERMISSION_DENIED) {
                throw new AssertionError("Probe unexpectedly holds worker bind permission");
            }

            String denial = requireBindOrCallDenied(context, service);
            result.putString(
                    "stream",
                    "Separate-signer probe: signature permission denied; " + denial + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "Separate-signer probe FAILED: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static String requireBindOrCallDenied(Context context, ComponentName service)
            throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        IBinder[] received = new IBinder[1];
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        received[0] = binder;
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        connected.countDown();
                    }
                };
        boolean bound;
        try {
            bound =
                    context.bindService(
                            new Intent().setComponent(service),
                            connection,
                            Context.BIND_AUTO_CREATE);
        } catch (SecurityException denied) {
            return "bind threw SecurityException";
        }
        if (!bound) return "bind returned false";
        try {
            if (!connected.await(5, TimeUnit.SECONDS) || received[0] == null) {
                throw new AssertionError("Bind reported success without a service response");
            }
            try {
                String response = IBuildService.Stub.asInterface(received[0]).handshake(1);
                throw new AssertionError("Unauthorized call succeeded: " + response);
            } catch (SecurityException denied) {
                return "handshake threw SecurityException";
            }
        } finally {
            context.unbindService(connection);
        }
    }
}
