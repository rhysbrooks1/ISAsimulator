//Group: Rhys Brooks and Pearl Bless Afegenui
//
// S12_IL.java
// Implements the S12_IL_Interface provided by the course.
// This class simulates a Simple‑12 machine with:
//  - 12‑bit ACC (accumulator) and 8‑bit PC (program counter)
//  - 256 x 12‑bit word-addressable memory
//  - 4‑bit opcode and 8‑bit address operand per instruction
//
// ISA used (consistent with the provided benchmarks):
//  0x0 HALT
//  0x1 ADD addr
//  0x2 JZ  addr
//  0x3 JN  addr
//  0x4 LOAD  addr
//  0x5 STORE addr
//  0x6 LOADI addr
//  0x7 STOREI addr
//  0x8 AND addr
//  0x9 OR  addr
//  0xA JMP addr
//  0xB SUB addr
//
// File formats:
//  * Input memFile: tolerant reader supports both of these:
//      1) Project binary format:
//         <8b PC> <space> <12b ACC>
//         00 <space> <12b word>
//         01 <space> <12b word>
//         ...
//         FF <space> <12b word>
//      2) Compact hex format (commonly used for benchmarks):
//         00 <space> 4A0
//         01 <space> BFF
//         ...
//  * writeMem(): writes the project binary format (binary header PC ACC + 256 lines of "AA WWWWWWWWWWWW").
//  * writeTrace(): writes one executed instruction per line using assembly mnemonic + two hex digits (e.g., "LOAD A0").
//
// NOTE ON getProcessorState():
//   Returns an array of two strings in this exact order:
//     [0] PC as hex string: "0x%02X"
//     [1] ACC as hex string: "0x%03X"
//
//   This ordering makes it easy for the CLI to print the summary required by the spec.

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class S12_IL implements S12_IL_Interface {

    private static final int MEM_SIZE = 256;
    private final int[] mem = new int[MEM_SIZE]; // 12-bit words
    private int pc = 0;   // 8-bit
    private int acc = 0;  // 12-bit
    private boolean halted = false;

    // Trace is stored in assembly form, per project requirement for <base>_trace
    private final List<String> traceAsm = new ArrayList<>();

    // ---------- helpers ----------
    private static int mask12(int v) { return v & 0xFFF; }
    private static int mask8(int v) { return v & 0xFF; }
    private static boolean isNeg12(int v) { return (v & 0x800) != 0; }
    private static String bin8(int v) { return String.format("%8s", Integer.toBinaryString(mask8(v))).replace(' ', '0'); }
    private static String bin12(int v) { return String.format("%12s", Integer.toBinaryString(mask12(v))).replace(' ', '0'); }
    private static String hex2(int v) { return String.format("%02X", mask8(v)); }
    private static String hex3(int v) { return String.format("%03X", mask12(v)); }

    private static String stripComment(String s) {
        int i = s.indexOf(';');
        if (i >= 0) s = s.substring(0, i);
        i = s.indexOf("//");
        if (i >= 0) s = s.substring(0, i);
        return s.trim();
    }

    private int m(int a) { return mem[mask8(a)]; }
    private void mset(int a, int v) { mem[mask8(a)] = mask12(v); }

    private static final String[] MNEM = new String[16];
    static {
        Arrays.fill(MNEM, "UNK");
        MNEM[0x0] = "HALT";
        MNEM[0x1] = "ADD";
        MNEM[0x2] = "JZ";
        MNEM[0x3] = "JN";
        MNEM[0x4] = "LOAD";
        MNEM[0x5] = "STORE";
        MNEM[0x6] = "LOADI";
        MNEM[0x7] = "STOREI";
        MNEM[0x8] = "AND";
        MNEM[0x9] = "OR";
        MNEM[0xA] = "JMP";
        MNEM[0xB] = "SUB";
    }

    public S12_IL() {
        Arrays.fill(mem, 0);
    }

    @Override
    public boolean intializeMem(String filename) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filename));
            Arrays.fill(mem, 0);
            pc = 0;
            acc = 0;
            halted = false;
            traceAsm.clear();

            boolean sawHeader = false;
            for (String raw : lines) {
                String line = stripComment(raw);
                if (line.isEmpty() || line.equals("...")) continue;

                String[] toks = line.split("\\s+");
                if (toks.length != 2) continue;

                // Header (PC ACC) in binary
                if (!sawHeader && toks[0].matches("[01]{8}") && toks[1].matches("[01]{12}")) {
                    pc  = Integer.parseInt(toks[0], 2) & 0xFF;
                    acc = Integer.parseInt(toks[1], 2) & 0xFFF;
                    sawHeader = true;
                    continue;
                }

                // Memory lines: address hex + value (binary or hex)
                if (toks[0].matches("(?i)[0-9a-f]{2}") &&
                    (toks[1].matches("[01]{12}") || toks[1].matches("(?i)[0-9a-f]{3}"))) {
                    int addr = Integer.parseInt(toks[0], 16) & 0xFF;
                    int val = toks[1].matches("[01]{12}")
                            ? Integer.parseInt(toks[1], 2)
                            : Integer.parseInt(toks[1], 16);
                    mem[addr] = mask12(val);
                }
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * getProcessorState returns two strings in this exact order:
     *   [0] PC formatted as "0x%02X"
     *   [1] ACC formatted as "0x%03X"
     */
    @Override
    public String[] getProcessorState() {
        return new String[] {
            String.format("0x%02X", pc & 0xFF),
            String.format("0x%03X", acc & 0xFFF)
        };
    }

    @Override
    public String getMemState() {
        // No header here; just "AA WWWWWWWWWWWW" per line, address hex + 12-bit binary word.
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < MEM_SIZE; a++) {
            sb.append(hex2(a)).append(' ').append(bin12(mem[a])).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String update() {
        if (halted) {
            // Once halted, keep returning HALT encoding to be safe.
            return "000000000000";
        }
        int instr = m(pc);
        int op = (instr >>> 8) & 0xF;
        int addr = instr & 0xFF;

        // Record trace in assembly form
        if (op == 0x0) {
            traceAsm.add("HALT");
        } else {
            traceAsm.add(MNEM[op] + " " + hex2(addr));
        }

        // Execute
        int nextPC = mask8(pc + 1);
        switch (op) {
            case 0x0: // HALT
                halted = true;
                break;
            case 0x1: // ADD
                acc = mask12(acc + m(addr));
                break;
            case 0x2: // JZ
                if ((acc & 0xFFF) == 0) nextPC = addr;
                break;
            case 0x3: // JN
                if (isNeg12(acc)) nextPC = addr;
                break;
            case 0x4: // LOAD
                acc = m(addr);
                break;
            case 0x5: // STORE
                mset(addr, acc);
                break;
            case 0x6: // LOADI
                acc = m(m(addr) & 0xFF);
                break;
            case 0x7: // STOREI
                mset(m(addr) & 0xFF, acc);
                break;
            case 0x8: // AND
                acc = (acc & m(addr)) & 0xFFF;
                break;
            case 0x9: // OR
                acc = (acc | m(addr)) & 0xFFF;
                break;
            case 0xA: // JMP
                nextPC = addr;
                break;
            case 0xB: // SUB
                acc = mask12(acc - m(addr));
                break;
            default:
                throw new IllegalStateException(String.format(
                    "Unknown opcode 0x%X at PC=0x%02X (word=0x%03X)", op, pc, instr));
        }
        pc = nextPC;

        // Return the 12-bit binary of the executed instruction *at the old PC*
        return bin12(instr);
    }

    @Override
    public boolean writeMem(String filename) {
        // Write the project-format memory file
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(filename))) {
            bw.write(bin8(pc));
            bw.write(' ');
            bw.write(bin12(acc));
            bw.newLine();
            for (int a = 0; a < MEM_SIZE; a++) {
                bw.write(hex2(a)); bw.write(' '); bw.write(bin12(mem[a])); bw.newLine();
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean writeTrace(String filename) {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(filename))) {
            for (String line : traceAsm) {
                bw.write(line);
                bw.newLine();
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }


}
