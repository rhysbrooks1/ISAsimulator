1. Compile the simulator

From the directory containing S12_IL_Interface.java, S12_IL.java, and S12_Sim.java:
	javac S12_IL_Interface.java S12_IL.java S12_Sim.java
	




2. Run the simulator

	java S12_Sim <memFile> <optional: -o outputFileBaseName> <optional: -c cyclesToExecute>

<memFile>: Path to the input memory file (e.g., .mem file from your benchmarks).

-o (optional): Sets the base name for output files.

Default: derived from the input filename (e.g., Maxfinder_genSize_time).

-c (optional): Maximum cycles to execute before stopping.

Default: unlimited (runs until HALT).




3. Examples

# Run with default base name
java S12_Sim Benchmarks/Maxfinder_genSize_time.mem

# Run with custom base name
java S12_Sim Benchmarks/Maxfinder_genSize_space.mem -o MaxSpaceRun

# Run with cycle cap of 50,000
java S12_Sim Benchmarks/Maxfinder_genSize_time.mem -c 50000

