package de.rub.grafcet.oncon2025;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import com.microsoft.z3.BoolExpr;

import de.hsu.grafcet.Grafcet;
import de.hsu.grafcet.Transition;

public class grafcetFunctions {
	public static void saveGrafcet(Grafcet grafcet, String filePath) {
	    try {
	        ResourceSet resourceSet = new ResourceSetImpl();
	        URI uri = URI.createFileURI(filePath);

	        Resource resource = resourceSet.createResource(uri);
	        resource.getContents().add(grafcet);
	        resource.save(null);
	        System.out.println("Saved Grafcet to: " + filePath);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}

    public static Grafcet loadGrafcet(String filePath) {
        try {
            
            Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("grafcet", new XMIResourceFactoryImpl());

            ResourceSet resourceSet = new ResourceSetImpl();
            URI uri = URI.createFileURI(filePath);

            Resource resource = resourceSet.getResource(uri, true);
            resource.load(null);

            return (Grafcet) resource.getContents().get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    
    public static List<Map<String, String>> staticAnalysisAlgorithm(List<Map<String, String>> csvExclusionsFile, Grafcet grafcet, SMT_Solver smtSolver){
    	List<Map<String, String>> outputTable = new ArrayList<>();
    	for(Grafcet partialGrafcet: grafcet.getPartialGrafcets()){
			for(Transition transition: partialGrafcet.getTransitions()) {
				String formatedTransition = TransitionExpressionExtractor.transitionToString(transition);			
				for (Map<String, String> row : csvExclusionsFile) {
				    String var1 = row.get("Var1");
				    String var2 = row.get("Var2");
	
				    // skip rows with missing data
				    if (var1 == null || var2 == null) {
				        continue;
				    }
	
				    // build regex patterns to match whole words (tokens)
				    String regexVar1 = ".*\\b" + Pattern.quote(var1) + "\\b.*";
				    String regexVar2 = ".*\\b" + Pattern.quote(var2) + "\\b.*";
	
				    boolean containsVar1 = formatedTransition.matches(regexVar1);
				    boolean containsVar2 = formatedTransition.matches(regexVar2);
				    if (containsVar1 && containsVar2) {
				    	
				    	Map<String, String> rowOutputTable = new HashMap<>();
				    	String regex = "^AND\\[\\s*([A-Za-z0-9_]+)\\s*,\\s*([A-Za-z0-9_]+)\\s*\\]$";
				    	
				    	if (formatedTransition.matches(regex))
				    	{
					    	rowOutputTable.put("PartialGrafcetName", partialGrafcet.getName());
					    	rowOutputTable.put("TransitionID", String.valueOf(transition.getId()));
					    	rowOutputTable.put("TransitionCondition", formatedTransition);
					    	rowOutputTable.put("Satisfiability", "Not Satisfied"); 
				    	} 
				    	else {			    	   
				    		boolean inputIsSatisfied = smtSolver.checkIsSatisfiableZ3(formatedTransition, csvExclusionsFile);
					    	rowOutputTable.put("PartialGrafcetName", partialGrafcet.getName());
					    	rowOutputTable.put("TransitionID", String.valueOf(transition.getId()));
					    	rowOutputTable.put("TransitionCondition", formatedTransition);
					    	if(inputIsSatisfied) 
					    	{
					    		rowOutputTable.put("Satisfiability", String.valueOf(inputIsSatisfied) + " - redundantly designed?"); 
					    	}
				    	else 
				    		{
				    		rowOutputTable.put("Satisfiability", String.valueOf(inputIsSatisfied)); 
				    		}
				    	}
				    	outputTable.add(rowOutputTable);
				    }
				}
			}
		}
    	return outputTable;
    }
    
    public static void printOutputTable(List<Map<String, String>> outputTable) {
        for (Map<String, String> row : outputTable) {
            String partialGrafcetName = row.get("PartialGrafcetName");
            String transitionID = row.get("TransitionID");
            String transitionCondition = row.get("TransitionCondition");
            String satisfiability = row.get("Satisfiability");
            System.out.println(partialGrafcetName + "; " + transitionID + "; " + transitionCondition + "; " + satisfiability);
        }
    }
}
