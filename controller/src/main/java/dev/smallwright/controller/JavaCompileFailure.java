package dev.smallwright.controller;

import dev.smallwright.protocol.CompilerDiagnostics;
import java.io.IOException;

/** Fixed classification for an ECJ Java source compilation failure from the worker. */
final class JavaCompileFailure extends IOException {
    final CompilerDiagnostics.Report diagnostics;

    JavaCompileFailure() {
        this(null);
    }

    JavaCompileFailure(CompilerDiagnostics.Report diagnostics) {
        super("Java source compilation failed");
        this.diagnostics = diagnostics;
    }
}
