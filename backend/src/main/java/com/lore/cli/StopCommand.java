package com.lore.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "stop", description = "Stop the session and summarize changes")
public class StopCommand implements Runnable {

    @Override
    public void run() {
        // TODO: Implement in slice 2b
        System.out.println("lore stop is not implemented yet.");
    }
}
