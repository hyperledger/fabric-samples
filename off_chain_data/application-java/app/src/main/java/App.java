/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.grpc.ManagedChannel;

public final class App {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 3;
    private static final Map<String, Command> COMMANDS = Map.ofEntries(
            Map.entry("getAllAssets", new GetAllAssets()),
            Map.entry("transact", new Transact()),
            Map.entry("listen", new Listen())
    );

    private final List<String> commandNames;
    private final PrintStream out = System.out;

    App(final String[] args) {
        commandNames = List.of(args);
    }

    public void run() throws Exception {
        List<Command> commands = getCommands();
        ManagedChannel grpcChannel = Connections.newGrpcConnection();
        try {
            for (Command command : commands) {
                command.run(grpcChannel);
            }
        } finally {
            grpcChannel.shutdownNow().awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private List<Command> getCommands() {
        List<Command> commands = commandNames.stream()
                .map(name -> {
                    Command command = COMMANDS.get(name);
                    if (command == null) {
                        printUsage();
                        throw new IllegalArgumentException("Unknown command: " + name);
                    }
                    return command;
                })
                .collect(Collectors.toList());

        if (commands.isEmpty()) {
            printUsage();
            throw new IllegalArgumentException("Missing command");
        }

        return commands;
    }

    private void printUsage() {
        out.println("Arguments: <command1> [<command2> ...]");
        out.println("Available commands: " + COMMANDS.keySet());
    }

    public static void main(final String[] args) {
        try {
            new App(args).run();
        } catch (ExpectedException e) {
            e.printStackTrace(System.out);
        } catch (Exception e) {
            System.err.print("\nUnexpected application error: ");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
