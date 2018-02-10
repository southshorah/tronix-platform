package org.tron.program;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Args {

  private static final Args INSTANCE = new Args();


  @Parameter(names = {"-d", "--output-directory"}, description = "set directory")
  private String outputDirectory = new String("");

  @Parameter(names = {"-h", "--help"}, help = true, description = "help view")
  private boolean help = false;

  @Parameter(names = {"-dev", "--develop"}, help = true, description = "develop model")
  private boolean develop = false;

  @Parameter(names = {"-wit", "--witness"}, help = true, description = "witness model")
  private boolean witness = false;

  @Parameter(description = "-seed-nodes")
  private List<String> seedNodes = new ArrayList<>();


  private Args() {
  }

  /**
   * set param value form args.
   */
  public static void setParam(String[] args) {
    JCommander.newBuilder()
        .addObject(INSTANCE)
        .build()
        .parse(args);
  }

  public static Args getInstance() {
    return INSTANCE;
  }

  /**
   * get directory with separator.
   */
  public String getOutputDirectory() {
    if (outputDirectory != "" && !outputDirectory.endsWith(File.separator)) {
      return outputDirectory + File.separator;
    }
    return outputDirectory;
  }

  public boolean isHelp() {
    return help;
  }

  public List<String> getSeedNodes() {
    return seedNodes;
  }

  public boolean isDevelop() {
    return develop;
  }

  public boolean isWitness() {
    return witness;
  }
}
