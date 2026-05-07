package de.rub.grafcet.oncon2025;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import de.hsu.grafcet.*;


public class main {
	// ADJUST:
	static String projectDir = System.getProperty("user.dir"); // current project folder
    static String csvPath = projectDir+ "\\csvTablesFolder\\exclusions_claude_sonnet_20250514.csv";
    
    //
    static SMT_Solver smtSolver = new SMT_Solver();
    static List<Map<String, String>> outputTable = new ArrayList<>();
    static TransitionExpressionExtractor transitionBuilder = new TransitionExpressionExtractor();

	public static void main(String[] args) {
		// LOAD GRAFCET
        GrafcetPackage.eINSTANCE.eClass();
		Grafcet loadedGrafcet = grafcetFunctions.loadGrafcet(projectDir+"\\grafcetSpecifications\\oncon2025_faultySchumacher.grafcet");
		
		// LOAD DLLs
		System.load(projectDir + "\\z3\\bin\\libz3.dll");
		System.load(projectDir + "\\z3\\bin\\libz3java.dll");
		
		// LOAD CSV FILE
        ProcessCSVFile csvFile = new ProcessCSVFile();
        csvFile.readAndSaveCSVFile(csvPath);
        List<Map<String, String>> csvExclusionsFile = csvFile.getCSVFile();
        
        // PERFORM STATIC ANALYSIS AND PRINT RESULTS
		outputTable = grafcetFunctions.staticAnalysisAlgorithm(csvExclusionsFile, loadedGrafcet, smtSolver);
		grafcetFunctions.printOutputTable(outputTable);
	}
	
	

}
