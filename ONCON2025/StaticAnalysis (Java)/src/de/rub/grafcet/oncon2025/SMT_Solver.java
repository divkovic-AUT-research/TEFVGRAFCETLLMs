package de.rub.grafcet.oncon2025;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;

import com.microsoft.z3.*;

public class SMT_Solver {

	public static String parseExpression(String input) {
        
	    // Base case: No nested structure (simple expression)
	    if (!input.contains("[") || !input.contains("]")) {
	        return input.trim();
	    }

	    // Identify the operator (e.g., AND, OR, NOT)
	    int operatorEnd = input.indexOf("[");
	    String operator = input.substring(0, operatorEnd);

	    // Extract the inner content between brackets
	    String innerContent = input.substring(operatorEnd + 1, input.length() - 1);
	    // Use a stack to track nested brackets
	    Stack<Character> stack = new Stack<>();
	    StringBuilder currentPart = new StringBuilder();
	    StringBuilder result = new StringBuilder("(");
	    boolean first = true;

	    // Iterate through innerContent while handling nested structures
	    for (int i = 0; i < innerContent.length(); i++) {
	        char c = innerContent.charAt(i);

	        // Track nested levels using a stack
	        if (c == '[') stack.push('[');
	        if (c == ']') stack.pop();

	        // If we hit a comma at the top level, process the current part
	        if (c == ',' && stack.isEmpty()) {
	            if (!first) {
	                result.append(getOperatorSymbol(operator)); // Add & or |
	            }
	            result.append(parseExpression(currentPart.toString().trim()));
	            currentPart.setLength(0); // Clear the current part
	            first = false;
	        } else {
	            currentPart.append(c);
	        }
	    }

	    // Process the last remaining part
	    if (currentPart.length() > 0) {
	        if (!first) {
	            result.append(getOperatorSymbol(operator));
	        }
	        result.append(parseExpression(currentPart.toString().trim()));
	    }

	    result.append(")");

	    // Handle NOT operator by adding negation (~)
	    if (operator.equals("NOT")) {
	        return "~" + result.toString() + "";
	    }

	    return result.toString();
	}
	
	// Helper to map logical operators to symbols
	private static String getOperatorSymbol(String operator) {
	    return switch (operator) {
	        case "AND" -> "&";
	        case "OR" -> "|";
	        case "NOT" -> "~";
	        case "EQ" -> "#"; // Logical Equality Symbol
	        case "LESS" -> "<";
	        case "GREATER" -> ">";
	        case "EQUAL" -> "=";
	        default -> "?";
	    };
	}
	
	
	public static String removeOuterParentheses(String expr) {
	    if (expr.startsWith("(") && expr.endsWith(")")) {
	        // Optionally: ensure they are truly outermost 
	        int balance = 0;
	        for (int i = 0; i < expr.length(); i++) {
	            if (expr.charAt(i) == '(') balance++;
	            else if (expr.charAt(i) == ')') balance--;

	            if (balance == 0 && i < expr.length() - 1) {
	                // There's more outside the outermost '(', so don't strip
	                return expr;
	            }
	        }
	        return expr.substring(1, expr.length() - 1);
	    }
	    return expr;
	}
	

	
	public static BoolExpr parseSMTStack(String expr, Context ctx, Map<String, BoolExpr> boolVars, Map<String, IntExpr> intVars) {
	    expr = removeOuterParentheses(expr);
	    char[] chars = expr.toCharArray();
	    int i = -1;

	    Stack<Object> stack = new Stack<>();
	    StringTokenizer tokenizer = new StringTokenizer(expr, "#()&|~<>!= ", true); // include delimiters

	    while (tokenizer.hasMoreTokens()) {
	        String token = tokenizer.nextToken().trim();
	        if (token.isEmpty()) continue;

	        switch (token) {
	            case "(":
	                stack.push(token);
	                i++;
	                break;

	            case ")":
	                List<Object> subExprTokens = new ArrayList<>();

	                // Stop early at the end
	                if (i + 1 >= chars.length && stack.size() <= 2) break;

	                // Extract the sub-expression between parentheses
	                while (!(stack.peek().equals("(") || stack.peek().equals("~"))) {
	                    subExprTokens.add(0, stack.pop());

	                    if (stack.size() > 2 && stack.get(stack.size() - 2).equals("~")) {
	                        subExprTokens.add("~");
	                        stack.pop(); // remove "~"
	                    }
	                }

	                stack.pop(); // Pop the "("
	                BoolExpr subFormula = buildSubExpression(subExprTokens, ctx, boolVars, intVars);
	                stack.push(subFormula);
	                i++;
	                break;

	            case "&":
	            case "|":
	            case "~":
	            case "#":
	                stack.push(token);
	                i++;
	                break;

	            default:
	                // Handle comparisons like K>3, K=2, etc.
	                if ((i + token.length() < chars.length && (chars[i+token.length()+1] == '>' || chars[i+token.length()+1] == '=' || chars[i+token.length()+1] == '<'))) {
	                    StringBuilder tokenBuilder = new StringBuilder(token);
	                    i += token.length();
	                    // Merge tokens until closing parenthesis or operator
	                    while (i + 1 < chars.length && chars[i + 1] != ')') {
	                        /*if (!isValidIntegerWithMoreThanTwoDigits(token)) {
	                            token = tokenizer.nextToken().trim();
	                            tokenBuilder.append(token);
	                        }*/
	                        token = tokenizer.nextToken().trim();
                            tokenBuilder.append(token);
	                        i += token.length();
	                    }

	                    stack.pop(); // Remove variable from stack (we're replacing it)
	                    stack.push(buildComparisonFormula(tokenBuilder.toString(), ctx, intVars));

	                    // Skip over processed characters
	                    tokenizer.nextToken(); // Advance tokenizer
	                    i += 1;
	                } else {
	                    // Handle literal integers or boolean variables
	                    i += token.length();

	                    if (isInteger(token)) {
	                        stack.push(ctx.mkInt(Integer.parseInt(token)));
	                    } else {
	                        stack.push(boolVars.computeIfAbsent(token, v -> ctx.mkBoolConst(v)));
	                    }
	                }
	                break;
	        }
	    }

	    return (BoolExpr) stack.pop();
	}
	
	public static boolean isInteger(String token) {
	    try {
	        Integer.parseInt(token);
	        return true;
	    } catch (NumberFormatException e) {
	        return false;
	    }
	}
	
	public static boolean isValidIntegerWithMoreThanTwoDigits(String token) {
	    try {
	        Integer.parseInt(token); // Check if token is a valid integer
	        String digitsOnly = token.startsWith("-") ? token.substring(1) : token;
	        return digitsOnly.length() >= 2;
	    } catch (NumberFormatException e) {
	        return false; // Not a valid integer
	    }
	}
	
	
	public static BoolExpr buildSubExpression(List<Object> tokens, Context ctx, Map<String, BoolExpr> boolVars, Map<String, IntExpr> intVars) {
	    Stack<BoolExpr> exprStack = new Stack<>();
	    String currentOperator = null;
	    String firstVar = null;
	    String secondVar = null;
	    Iterator<String> it = boolVars.keySet().iterator();


	    for (int i = 0; i < tokens.size(); i++) {
	        Object token = tokens.get(i);
	        
	        if (boolVars.size()>0 && i <= boolVars.size()) {
	        if (i==0) {
	        	firstVar = it.next();}
	        if (i==2) {
	        	secondVar = it.next();}
	        }
	   
	        if (token instanceof BoolExpr) {
	            BoolExpr expr = (BoolExpr) token;

	            // Check if next token is "~" and apply NOT
	            if (i + 1 < tokens.size() && tokens.get(i + 1) instanceof String && tokens.get(i + 1).equals("~")) {
	                expr = ctx.mkNot(expr);
	                i++; // skip "~"
	            }

	            if (exprStack.isEmpty()) {
	                exprStack.push(expr);
	            } else if (currentOperator != null) {
	                BoolExpr prev = exprStack.pop();
	                BoolExpr combined;

	                switch (currentOperator) {
	                    case "&":
	                    	combined = ctx.mkAnd(prev, expr);
	                        break;
	                    case "|":
	                        combined = ctx.mkOr(prev, expr);
	                        break;
	                    case "#":                    	
	                    	if(secondVar.equals("false")){combined = ctx.mkIff(prev, ctx.mkFalse());} 	                   
	                    	else if (secondVar.equals("true")){combined = ctx.mkIff(prev, ctx.mkTrue());}
	                    	else if (firstVar.equals("false")){combined = ctx.mkIff(ctx.mkFalse(), expr);} 
	                    	else if (firstVar.equals("true")){combined = ctx.mkIff(ctx.mkTrue(), expr);} 
	                    	else {combined = ctx.mkIff(prev, expr);} // Logical equivalence A ⇔ B 
	                        break;
	                    default:
	                        throw new IllegalStateException("Unknown operator: " + currentOperator);
	                }

	                exprStack.push(combined);
	                currentOperator = null;
	            } else {
	                throw new IllegalStateException("Missing logical operator between expressions.");
	            }

	        } else if (token instanceof String) {
	            String op = (String) token;
	            if (op.equals("&") || op.equals("|") || op.equals("#")) {
	                currentOperator = op;
	            }
	        }
	    }

	    if (!exprStack.isEmpty()) {
	        return exprStack.pop();
	    } else {
	        throw new IllegalStateException("No valid expression built.");
	    }
	}
	
	
	
	private static BoolExpr buildComparisonFormula(String token, Context ctx, Map<String, IntExpr> intVars) {
	    if (token.contains(">")) {
	        String[] parts = token.split(">");
	        return ctx.mkGt(getIntVar(parts[0], ctx, intVars), ctx.mkInt(Integer.parseInt(parts[1])));
	    } else if (token.contains("<")) {
	        String[] parts = token.split("<");
	        return ctx.mkLt(getIntVar(parts[0], ctx, intVars), ctx.mkInt(Integer.parseInt(parts[1])));
	    } else if (token.contains("=")) {
	        String[] parts = token.split("=");
	        return ctx.mkEq(getIntVar(parts[0], ctx, intVars), ctx.mkInt(Integer.parseInt(parts[1])));
	    } else {
	        throw new IllegalArgumentException("Unsupported comparison: " + token);
	    }
	} 
	
	private static IntExpr getIntVar(String name, Context ctx, Map<String, IntExpr> intVars) {
	    return intVars.computeIfAbsent(name.trim(), v -> ctx.mkIntConst(v));
	}
	
	public static boolean checkIsSatisfiableZ3(String booleanExpression, List<Map<String, String>> csvExclusionsFile) {
	    booleanExpression = parseExpression(booleanExpression);
	    try (Context ctx = new Context()) {   
	    	
	        Map<String, BoolExpr> boolVars = new HashMap<>();
	        Map<String, IntExpr> intVars = new HashMap<>();
	        BoolExpr formula = parseSMTStack(booleanExpression, ctx, boolVars, intVars);
	        
	        Solver solver = ctx.mkSolver();
	        solver.add(formula);  
	        initializeMutualExclusion(csvExclusionsFile, ctx, solver, boolVars);
	        
	        return solver.check() == Status.SATISFIABLE;
	    } 
	    catch (Z3Exception e) {
	        System.out.println("Z3 Exception: " + e.getMessage());
	        return false;
	    }
	}
	
	public static void initializeMutualExclusion(
	        List<Map<String, String>> csvExclusionsFile,
	        Context ctx,
	        Solver solver,
	        Map<String, BoolExpr> boolVars
	) 
	{
	    for (Map<String, String> row : csvExclusionsFile) {
	        String var1Name = row.get("Var1");
	        String var2Name = row.get("Var2");

	        BoolExpr var1;
	        BoolExpr var2;
	        
	        if (var1Name.startsWith("NOT[")) {
	            // Extract the inner variable name, e.g. from "NOT[x]" → "x"
	            String innerName = var1Name.substring(4, var1Name.length() - 1);
	            // Retrieve or create the base variable
	            BoolExpr innerVar = boolVars.computeIfAbsent(innerName, v -> ctx.mkBoolConst(v));
	            // Negate it
	            var1 = ctx.mkNot(innerVar);
	        } else {
	            // Normal variable (not negated)
	            var1 = boolVars.computeIfAbsent(var1Name, v -> ctx.mkBoolConst(v));
	        }
	        
	        if (var2Name.startsWith("NOT[")) {
	            String innerName = var2Name.substring(4, var2Name.length() - 1);
	            BoolExpr innerVar = boolVars.computeIfAbsent(innerName, v -> ctx.mkBoolConst(v));
	            var2 = ctx.mkNot(innerVar);
	        } else {
	            var2 = boolVars.computeIfAbsent(var2Name, v -> ctx.mkBoolConst(v));
	        }
	        
	        BoolExpr exclusionConstraint; // declare before if/else
	            // Default mutual exclusion: not both true
	        exclusionConstraint = ctx.mkNot(ctx.mkAnd(var1, var2));
	        
	        // Add to solver
	        solver.add(exclusionConstraint);

	    }
	}



	
	/*public static void main(String[] args) {
        System.load("C:\\Users\\AUTUser\\Desktop\\AGRAFE\\Solver\\z3\\bin\\libz3.dll");
        System.load("C:\\Users\\AUTUser\\Desktop\\AGRAFE\\Solver\\z3\\bin\\libz3java.dll");
		String input = "[AND[A, B, NOT[A]]]";
		boolean inputIsSatisfied = checkIsSatisfiableZ3(input);
		System.out.println(inputIsSatisfied);
	}*/

}
