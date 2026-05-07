package de.rub.grafcet.oncon2025;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.hsu.grafcet.Transition;
import terms.Operator;
import terms.Term;
import terms.Variable;
import terms.VariableDeclarationType;
import terms.impl.BooleanConstantImpl;
import terms.impl.IntegerConstantImpl;



public class TransitionExpressionExtractor {

    public static String transitionToString(Transition transition) {
        Term term = transition.getTerm();
        Set<Variable> variables = new LinkedHashSet<>();
        return buildExpression(term, variables);
    }


    private static String buildExpression(Term term, Set<Variable> variables) {

	    if (term instanceof Variable) {
	        Variable variable = (Variable) term;
	        if (variable.getVariableDeclaration().getVariableDeclarationType() == VariableDeclarationType.INPUT || variable.getVariableDeclaration().getVariableDeclarationType() == VariableDeclarationType.INTERNAL
	        		|| variable.getVariableDeclaration().getVariableDeclarationType() == VariableDeclarationType.STEP || (!(variable.getVariableDeclaration().getVariableDeclarationType() == VariableDeclarationType.OUTPUT))) {
	            variables.add(variable);
	            return variable.toString();
	        }
	    }
	    if (term instanceof Operator) {
	        Operator operator = (Operator) term;
	        List<Term> subterms = operator.getSubterm();
	        //System.out.println(term.toString());
	        if (operator.toString().equals("terms.impl.NotImpl")) {
	            return "NOT[" + buildExpression(subterms.get(0), variables) + "]";
	        }

	        else if (operator.toString().equals("terms.impl.AndImpl")) {
	            return "AND[" + subterms.stream()
	                    .map(subterm -> buildExpression(subterm, variables))
	                    .collect(Collectors.joining(", ")) + "]";
	        }

	        else if (operator.toString().equals("terms.impl.OrImpl")) {
	            return "OR[" + subterms.stream()
	                    .map(subterm -> buildExpression(subterm, variables))
	                    .collect(Collectors.joining(", ")) + "]";
	        }
	        // HERE SHOULD EQ BE ALSO INCLUDED


	        else if (operator.toString().equals("terms.impl.RisingEdgeImpl")) {

	    	   String termOfRisingEdge = subterms.stream()
	       	                    .map(subterm -> buildExpression(subterm, variables))
	    	                    .collect(Collectors.joining(""));

	    	   if(termOfRisingEdge.contains("AND")) {
		    	   String firstPart = "AND[" + termOfRisingEdge + ", NOT[" + addBeforeToAndTerms(termOfRisingEdge, "_beforeRE") + "]]";
		    	   String secondPart = "EQ[" + termOfRisingEdge + ", NOT[" + addBeforeToAndTerms(termOfRisingEdge, "_beforeRE") + "]]";
		    	   return "AND[" + firstPart + ", " + secondPart + "]";
	    	   }
	    	   else {
	    	   String firstPart = "AND[" + termOfRisingEdge + ", NOT[" + termOfRisingEdge + "_beforeRE]]";
	    	   String secondPart = "EQ[" + termOfRisingEdge + ", NOT[" + termOfRisingEdge + "_beforeRE]]";
	    	   return "AND[" + firstPart + ", " + secondPart + "]";
	    	   }
	        }
	        else if (operator.toString().equals("terms.impl.FallingEdgeImpl")) {
	        	String termOfFallingEdge = subterms.stream()
	       	                    .map(subterm -> buildExpression(subterm, variables))
	    	                    .collect(Collectors.joining(""));
		    	if(termOfFallingEdge.contains("AND")) {
			    	   String firstPart = "AND[" + termOfFallingEdge + "_beforeFE, NOT[" + addBeforeToAndTerms(termOfFallingEdge, "_beforeRE") + "]]";
			    	   String secondPart = "EQ[" + termOfFallingEdge + "_beforeFE, NOT[" + addBeforeToAndTerms(termOfFallingEdge, "_beforeRE") + "]]";
			    	   return "AND[" + firstPart + ", " + secondPart + "]";
		    	   }
		    	else {
	        	String firstPart = "AND[" + termOfFallingEdge + "_beforeFE, NOT[" + termOfFallingEdge + "]]";
	        	String secondPart = "EQ[" + termOfFallingEdge + "_beforeFE, NOT[" + termOfFallingEdge + "]]";
	            return "AND[" + firstPart + ", " + secondPart + "]";}
	        }


	        else if (operator.toString().equals("terms.impl.EqualityImpl")) {
	        	Term otherVariable = null;
	        	int intValue = 0;
	        	String intVariableAsString = "";
	        	String booleanConstant = "";
	        	List<Term> subtermList = new ArrayList<>();

	        	for(Term subterm: subterms) {
	        		subtermList.add(subterm);
	        		if(subterm.toString().equals("IntegerConstantImpl"))
	        		{
	        			intValue = ((IntegerConstantImpl)subterm).getValue();
	        			intVariableAsString = Integer.toString(intValue);
	        		}
	        		else if(subterm.toString().equals("BooleanConstantImpl")) {
	        			boolean booleanConstantTemp = ((BooleanConstantImpl)subterm).isValue();
	        			booleanConstant = Boolean.toString(booleanConstantTemp);
	        		}
	        		else
	        		{otherVariable = subterm;}
	        	}
	        	
	        	if (!(intVariableAsString.equals(""))) {
	        		return "EQUAL["+ otherVariable.toString() + ", " + intVariableAsString + "]";}
	        	
	        	else if(!(booleanConstant.equals(""))) {
	        		return "EQ["+ otherVariable.toString() + ", " + booleanConstant + "]";}
	        	
	        	else {
	        		return "EQ["+ subtermList.get(0).toString() + ", " + subtermList.get(1).toString() + "]";
	        	}
	        }

	        else if (operator.toString().equals("terms.impl.LessThanImpl")) {
	        	Term subterm1 = null;
	        	Term subterm2 = null;
	        	int intValue = 0;

	        	for(Term subterm: subterms) {
	        		if(subterm.toString().equals("IntegerConstantImpl"))
	        		{
	        			intValue = ((IntegerConstantImpl)subterm).getValue();
	        			subterm2 = subterm;
	        		}
	        		else {
	        			subterm1 = subterm;
	        		}
	        	}
	        	String firstPart = ((Variable)subterm1).getVariableDeclaration().getName();
	        	String secondPart =  String.valueOf(intValue);
	            return "LESS[" + firstPart + ", " + secondPart + "]";
	        }
	        
	        else if (operator.toString().equals("terms.impl.GreaterThanImpl")) {
	        	Term subterm1 = null;
	        	Term subterm2 = null;
	        	int intValue = 0;

	        	for(Term subterm: subterms) {
	        		if(subterm.toString().equals("IntegerConstantImpl"))
	        		{
	        			intValue = ((IntegerConstantImpl)subterm).getValue();
	        			subterm2 = subterm;
	        		}
	        		else {
	        			subterm1 = subterm;
	        		}
	        	}
	        	String firstPart = ((Variable)subterm1).getVariableDeclaration().getName();
	        	String secondPart =  String.valueOf(intValue);
	            return "GREATER[" + firstPart + ", " + secondPart + "]";
	        }
	        return subterms.stream()
	                .map(subterm -> buildExpression(subterm, variables))
	                .collect(Collectors.joining(", "));
	    }

	    return "";
	}
    
    public static String addBeforeToAndTerms(String expression, String secondString) {
	    return addBeforeToVariable(expression.trim(), secondString);
	}

	private static String addBeforeToVariable(String expr, String suffix) {
	    if (!expr.contains("[") || !expr.contains("]")) {
	        return expr.trim() + suffix;
	    }

	    int operatorEnd = expr.indexOf("[");
	    String operator = expr.substring(0, operatorEnd).trim();

	    String inner = expr.substring(operatorEnd + 1, expr.length() - 1);

	    StringBuilder result = new StringBuilder();
	    result.append(operator).append("[");

	    List<String> parts = splitTopLevel(inner);
	    for (int i = 0; i < parts.size(); i++) {
	        result.append(addBeforeToVariable(parts.get(i).trim(), suffix));
	        if (i < parts.size() - 1) {
	            result.append(", ");
	        }
	    }

	    result.append("]");
	    return result.toString();
	}

	private static List<String> splitTopLevel(String input) {
	    List<String> result = new ArrayList<>();
	    int level = 0;
	    StringBuilder current = new StringBuilder();

	    for (char c : input.toCharArray()) {
	        if (c == '[') level++;
	        if (c == ']') level--;
	        if (c == ',' && level == 0) {
	            result.add(current.toString());
	            current.setLength(0);
	        } else {
	            current.append(c);
	        }
	    }

	    if (current.length() > 0) {
	        result.add(current.toString());
	    }

	    return result;
	}



    public static String getEqualCases(int numBits, int target, String variable) {
        List<String> terms = new ArrayList<>();

        // For L == 5, binary is 101 (for a 3-bit integer)
        for (int i = 0; i < numBits; i++) {
        	int bitValue = (target >> i) & 1; // Extract bit i
            String term = (bitValue == 1) ? "bit" + (i + 1)  + variable : "NOT[bit" + (i + 1)  +variable +"]"; // Use bit1, bit2, etc.
            terms.add(term);
        }

        return "AND[" + String.join(", ", terms) + "]";
    }



	private String extractContent(Set<String> inputSet) {
	    StringBuilder result = new StringBuilder();
	    for (String element : inputSet) {
	        if (element.startsWith("[") && element.endsWith("]")) {
	            result.append(element.substring(1, element.length() - 1));
	        } else {
	            result.append(element);
	        }
	        result.append(", "); // Add separator for clarity
	    }
	    if (result.length() > 0) {
	        result.setLength(result.length() - 2);
	    }
	    return result.toString();
	}
}

