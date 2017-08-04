package se.lnu.cs.doris.metrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class MSEConverter {
    public MSEConverter(String projectPath, String projectName, String[] mseDirs) {
        this(new File(projectPath), projectName, mseDirs);
    }

    public MSEConverter(File projectDir, String projectName, String[] mseDirs) {
        this.projectDir = projectDir;
        this.projectName = projectName;
        this.mseDirs = mseDirs;
        if (this.mseDirs == null) {
            this.mseDirs = new String[] {""};  // just run one MSE in the root directory
            System.out.println("null mseDirs!");
        }
    }

    // TODO loop through commit directories (0, 1, 2, ...) and run jdt2famix script
    public void generateMSEFiles() throws IOException {

        for (File f : this.projectDir.listFiles()) {
            // TODO probably have an argument to the doris command that specifies directory(ies) within commits where MSE files should start
            // by default, it could be like projectPath\commitNumber\firstDirectory, e.g., nextgenpos-java_master/0/Larman PointOfSale/
            if (f.isDirectory() && !f.getName().contains(this.m_avoid)) {
                /*
                 * // TODO For each directory, generate an MSE file
                 */
                for (String dir :this.mseDirs) {
                    // run the jdt2famix script for now // TODO change this to work within Java rather than a shell command
                    String theCommand = "cd \"" + f.getAbsolutePath() + "\\" + dir + "\" && " + jdt2famixPath + "jdt2famix.cmd";
                    System.out.println("About to execute cmd.exe with: '" + theCommand + "'");
                    // TODO verify the directory to which we are going to CD is correct
                    ProcessBuilder builder = new ProcessBuilder(
                            "cmd.exe", "/c", theCommand);
                    builder.redirectErrorStream(true);
                    Process p = builder.start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while (true) {
                        line = r.readLine();
                        if (line == null) {
                            break;
                        }
                        System.out.println(line);
                    }
                }

            }
        }

    }

    final private String jdt2famixPath = "C:\\jdt2famix-1.0.3\\";  // TODO make this a flag to the doris command
    private File projectDir;
    private String projectName;
    private String[] mseDirs;
    private final String m_avoid = ".git";

}
