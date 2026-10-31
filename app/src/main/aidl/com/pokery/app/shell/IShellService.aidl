package com.pokery.app.shell;

import com.pokery.app.shell.ShellResult;

// Implemented by ShellService, which Shizuku runs as the `shell` user (uid 2000),
// the same identity `adb shell` has.
interface IShellService {
    // Transaction code reserved by Shizuku for tearing down a user service.
    void destroy() = 16777114;

    ShellResult exec(String command) = 1;
}
