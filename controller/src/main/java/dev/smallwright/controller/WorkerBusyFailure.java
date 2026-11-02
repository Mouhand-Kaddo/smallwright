package dev.smallwright.controller;

import java.io.IOException;

/** Exact authenticated worker admission refusal; not a compiler or source failure. */
final class WorkerBusyFailure extends IOException {
    WorkerBusyFailure() {
        super("ERROR: Worker busy");
    }
}
