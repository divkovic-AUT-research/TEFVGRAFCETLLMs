package de.rub.grafcet.oncon2025;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessCSVFile {
    // Instance variable
    private List<Map<String, String>> rows;

    // Constructor
    public ProcessCSVFile() {
        rows = new ArrayList<>();
    }
    
    public void readAndSaveCSVFile(String path) {
        //String path = "C:\\Users\\AUTUser\\Desktop\\Dokumente\\Konferenzen, Kongresse, etc\\Oncon 2025\\Code\\exclusions_claude_sonnet_20250514.csv";
        String delimiter = ";";
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                System.out.println("CSV file is empty!");
                return;
            }
            String[] headers = headerLine.split(delimiter);
            String line;
            
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue; // skip empty lines
                String[] values = line.split(delimiter);
                if (values.length != headers.length) {
                    //System.out.println("Skipping malformed line: " + line);
                    continue;
                }
                
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], values[i]);
                }
                
                this.rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    
    
    public void printRows() {
        for (Map<String, String> row : this.rows) {
            String component = row.get("Component");
            String var1 = row.get("Var1");
            String var2 = row.get("Var2");
            String reason = row.get("Reason");
            System.out.println(component + "; " + var1 + "; " + var2 + "; " + reason);
        }
    }
    
    public List<Map<String, String>> getCSVFile(){
    	return this.rows;
    }
}