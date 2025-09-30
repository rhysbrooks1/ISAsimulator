//Group: Rhys Brooks and Pearl Bless Afegenui
//
// S12_Sim.java
// Command-line interface for the S12 simulator using the provided interface.
//
// Usage (per assignment):
//   java S12_Sim <memFile> <optional: -o outputFileBaseName> <optional: -c cyclesToExecute>
//
// Behavior:
//  - Loads <memFile>
//  - Executes until HALT or cycle cap, whichever comes first
//  - Writes <baseName>_memOut (project mem format) and <baseName>_trace (assembly per line)
//  - Prints summary to console (Cycles, PC, ACC)
//
// Notes:
//  - getProcessorState() returns ["0x%02X" (PC), "0x%03X" (ACC)] in that order.

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class S12_Sim {

    private static void usage() {
        System.out.println("USAGE:");
        System.out.println("  java S12_Sim <memFile> <optional: -o outputFileBaseName> <optional: -c cyclesToExecute>");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            System.exit(1);
        }

        String memFile = null;
        String baseName = null;
        long cycleCap = Long.MAX_VALUE;

        // Parse args
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-o") && i + 1 < args.length) {
                baseName = args[++i];
            } else if (a.equals("-c") && i + 1 < args.length) {
                try {
                    cycleCap = Long.parseLong(args[++i]);
                } catch (NumberFormatException nfe) {
                    System.err.println("Invalid cycle cap for -c: " + args[i]);
                    System.exit(2);
                }
            } else if (a.startsWith("-")) {
                System.err.println("Warning: ignoring unrecognized flag: " + a);
            } else {
                if (memFile == null) memFile = a;
                else System.err.println("Warning: extra argument ignored: " + a);
            }
        }

        if (memFile == null) {
            usage();
            System.exit(1);
        }

        if (baseName == null) {
            String fn = Paths.get(memFile).getFileName().toString();
            int dot = fn.lastIndexOf('.');
            baseName = (dot > 0) ? fn.substring(0, dot) : fn;
        }

        String memOutName = baseName + "_memOut";
        String traceName   = baseName + "_trace";

        // Create and load simulator
        S12_IL_Interface sim = new S12_IL();
        boolean validFile = sim.intializeMem(memFile);
        if (!validFile) {
            System.err.println("Failed to read/parse memory file: " + memFile);
            System.exit(3);
        }

        // Execute
        long executed = 0;
        while (executed < cycleCap) {
            String bin = sim.update(); // 12-bit binary of the instruction executed
            executed++;
            // Stop if HALT (opcode 0 -> first 4 bits "0000")
            if (bin != null && bin.length() >= 4) {
                if (bin.startsWith("0000")) break;
            } else {
                // defensive: if update returns null/empty, stop
                break;
            }
        }

        // Write outputs
        if (!sim.writeMem(memOutName)) {
            System.err.println("Warning: failed to write memory output file: " + memOutName);
        }
        if (!sim.writeTrace(traceName)) {
            System.err.println("Warning: failed to write trace file: " + traceName);
        }

        // Console output
        String[] regs = sim.getProcessorState(); // ["0xPC", "0xACC"]
        System.out.println("Cycles Executed: " + executed);
        System.out.println("PC: " + (regs.length > 0 ? regs[0] : "n/a"));
        System.out.println("ACC: " + (regs.length > 1 ? regs[1] : "n/a"));
    }
}
