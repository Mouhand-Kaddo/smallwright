package dev.smallwright.worker;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Binds the debug-only isolated service and returns what it was allowed to do. */
final class IsolatedProcessProbe {
    private static final String SERVICE = "dev.smallwright.worker.IsolatedProbeService";

    private IsolatedProcessProbe() {}

    static String run(Instrumentation instrumentation) throws Exception {
        Context context = instrumentation.getTargetContext();
        File hostPrivate = new File(context.getFilesDir(), "isolation-host-private.txt");
        Files.write(hostPrivate.toPath(), "host-private".getBytes(StandardCharsets.UTF_8));
        File handoff = new File(context.getCacheDir(), "isolation-handoff.txt");
        Files.write(handoff.toPath(), new byte[0]);
        LinkedBlockingQueue<IBinder> bound = new LinkedBlockingQueue<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        bound.add(service);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };
        if (!context.bindService(
                new Intent().setClassName(context, SERVICE),
                connection,
                Context.BIND_AUTO_CREATE)) {
            throw new IllegalStateException("Isolated probe service is not in this build");
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try (ParcelFileDescriptor handoffDescriptor =
                ParcelFileDescriptor.open(handoff, ParcelFileDescriptor.MODE_WRITE_ONLY)) {
            IBinder service = bound.poll(20, TimeUnit.SECONDS);
            if (service == null) throw new IllegalStateException("Isolated probe did not bind");
            data.writeFileDescriptor(handoffDescriptor.getFileDescriptor());
            data.writeString(hostPrivate.getAbsolutePath());
            service.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0);
            return "host uid="
                    + Process.myUid()
                    + "\n"
                    + reply.readString()
                    + "host sees handoff: "
                    + new String(Files.readAllBytes(handoff.toPath()), StandardCharsets.UTF_8)
                    + "\n";
        } finally {
            data.recycle();
            reply.recycle();
            context.unbindService(connection);
            Files.deleteIfExists(hostPrivate.toPath());
            Files.deleteIfExists(handoff.toPath());
        }
    }
}
