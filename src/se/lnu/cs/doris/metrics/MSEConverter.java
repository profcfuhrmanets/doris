package se.lnu.cs.doris.metrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class MSEConverter {
    public MSEConverter(String projectPath, String projectName) {
        this(new File(projectPath), projectName);
    }

    public MSEConverter(File projectDir, String projectName) {
        this.projectDir = projectDir;
        this.projectName = projectName;
    }

    // TODO loop through commit directories (0, 1, 2, ...) and run jdt2famix script
    public void generateMSEFiles() throws IOException {

        for (File f : this.projectDir.listFiles()) {
            // TODO probably have an argument to the doris command that specifies root within commits where MSE files should start
            // by default, it could be like projectPath\commitNumber\firstDirectory, e.g., nextgenpos-java_master/0/Larman PointOfSale/
            if (f.isDirectory() && !f.getName().contains(this.m_avoid)) {
                // run the jdt2famix script for now // TODO change this to work within Java rather than a shell command
                String theCommand = "cd \"" + f.getAbsolutePath() + "\" && " + jdt2famixPath + "jdt2famix.cmd";
                System.out.println("About to execute cmd.exe with: '" + theCommand + "'");
                ProcessBuilder builder = new ProcessBuilder(
                        "cmd.exe", "/c", theCommand);
                builder.redirectErrorStream(true);
                Process p = builder.start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while (true) {
                    line = r.readLine();
                    if (line == null) { break; }
                    System.out.println(line);
                }

            }
        }

    }

    final private String jdt2famixPath = "C:\\jdt2famix-1.0.3\\";  // TODO make this a flag to the doris command
    private File projectDir;
    private String projectName;
    private final String m_avoid = ".git";

}
