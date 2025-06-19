package org.gbif.pipelines.maven;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.apache.maven.plugin.logging.Log;

/** A utility class that modifies Java files to add specified interfaces. */
public class InterfaceInjector {

  public static void addInterfaces(Log log, Path filePath, Set<String> interfacesToAdd) {
    if (interfacesToAdd == null || interfacesToAdd.isEmpty()) return;

    try {
      log.info("Processing file: " + filePath);

      List<String> lines = Files.readAllLines(filePath);
      Set<String> existingImports = new HashSet<>();
      Set<String> existingInterfaces = new HashSet<>();
      List<String> newImports = new ArrayList<>();

      Pattern importPattern = Pattern.compile("^import\\s+([\\w\\.]+);");
      Pattern packagePattern = Pattern.compile("^package\\s+[\\w\\.]+;");
      Pattern classPattern =
          Pattern.compile(
              "^(\\s*(public|protected|private)?\\s*(final|abstract)?\\s*class\\s+\\w+\\s*"
                  + "(extends\\s+\\S+\\s*)?)"
                  + // group 1: class declaration with optional extends
                  "(implements\\s+([\\w\\s,\\.]+))?"
                  + // group 6: full implements, group 7: just the interface list
                  "\\s*\\{");

      int insertIndex = -1;

      // Find the correct place to insert imports
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);

        Matcher importMatcher = importPattern.matcher(line);
        if (importMatcher.matches()) {
          existingImports.add(importMatcher.group(1));
          insertIndex = i + 1;
        } else if (insertIndex == -1
            && !line.trim().isEmpty()
            && !line.startsWith("@")
            && !line.startsWith("import")
            && !packagePattern.matcher(line).matches()) {
          insertIndex = i;
        }
      }

      // Collect missing imports
      for (String iface : interfacesToAdd) {
        if (!existingImports.contains(iface)) {
          newImports.add("import " + iface + ";");
        }
      }

      List<String> updatedLines = new ArrayList<>(lines);
      if (!newImports.isEmpty() && insertIndex >= 0) {
        updatedLines.addAll(insertIndex, newImports);
      }

      // Modify class declaration
      for (int i = 0; i < updatedLines.size(); i++) {
        String line = updatedLines.get(i);
        Matcher classMatcher = classPattern.matcher(line);
        if (classMatcher.find()) {
          String classDecl = classMatcher.group(1);
          String interfaceList = classMatcher.groupCount() >= 7 ? classMatcher.group(7) : null;

          if (interfaceList != null) {
            existingInterfaces.addAll(Arrays.asList(interfaceList.split("\\s*,\\s*")));
          }

          Set<String> allInterfaces = new LinkedHashSet<>(existingInterfaces);
          for (String iface : interfacesToAdd) {
            String simpleName = iface.substring(iface.lastIndexOf('.') + 1);
            allInterfaces.add(simpleName);
          }

          String newLine = classDecl + "implements " + String.join(", ", allInterfaces) + " {";
          updatedLines.set(i, newLine);
          break; // Only modify the first class declaration
        }
      }

      Files.write(filePath, updatedLines);
      log.info("Updated: " + filePath);

    } catch (IOException e) {
      throw new RuntimeException("Failed to write modified file: " + filePath, e);
    }
  }
}
