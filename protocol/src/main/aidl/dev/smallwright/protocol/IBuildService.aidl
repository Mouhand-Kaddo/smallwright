package dev.smallwright.protocol;

import android.os.ParcelFileDescriptor;

interface IBuildService {
    String handshake(int protocolVersion);
    String aapt2Version();
    String toolchainProbe();
    String build(in ParcelFileDescriptor snapshot, in ParcelFileDescriptor artifact);
    String buildWithDiagnostics(in ParcelFileDescriptor snapshot,
        in ParcelFileDescriptor artifact, in ParcelFileDescriptor diagnostics);
    String usePlatform(in ParcelFileDescriptor platform);
    String useLibraries(in ParcelFileDescriptor[] libraries, in String[] sha256);
}
