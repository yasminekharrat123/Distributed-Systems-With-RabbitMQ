package yas;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileService {
    private final String filePath;

    public FileService(String filePath) {
        this.filePath = filePath;
    }

    public List<String> readAllLines() {
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }

        return lines;
    }


    public void writeToFile(String data) {
        String lastLine = readLastLine();
        String newLinePrefix = getNextPrefix(lastLine);

        try (OutputStream outputStream = new FileOutputStream(filePath, true)) {
            outputStream.write(newLinePrefix.getBytes());
            outputStream.write(data.getBytes());
            outputStream.write("\n".getBytes());
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public String readLastLine() {
        String lastLine = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lastLine = line;
            }
        } catch (IOException e) {
            System.err.println("Error reading the last line of the file: " + e.getMessage());
            e.printStackTrace();
        }

        return lastLine;
    }


    private String getNextPrefix(String lastLine) {
        if (lastLine == null || lastLine.isEmpty()) {
            return "1 ";
        } else {
            char firstChar = lastLine.charAt(0);
            int newIndex = Character.getNumericValue(firstChar) + 1;
            return newIndex + " ";
        }
    }
}
