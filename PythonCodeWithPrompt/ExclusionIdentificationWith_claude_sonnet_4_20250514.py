import os
import pandas as pd
from anthropic import Anthropic
import csv
import re
import PDFTableToCSV

import json
from exclusions_validator import validate_exclusions, flatten_to_rows
from pydantic import ValidationError

# ----------------------------
# 0) LLM client 
# ----------------------------
api_key = os.getenv("ANTHROPIC_API_KEY")
if not api_key:
    raise RuntimeError("Please set ANTHROPIC_API_KEY environment variable.")
client = Anthropic(api_key=api_key)

# ----------------------------
# 1.) Load the extracted table (CSV saved from Camelot/pdfplumber)
# ----------------------------
PDFTableToCSV.main("WholeTable.pdf")
csv_path = "full_extracted_table.csv"  # <-- adjust if needed
df = pd.read_csv(csv_path)

# ----------------------------
# 2.1) Clean the table:
#    - collapse newlines and extra whitespace inside cells
#    - forward-fill 'Component' so each row has its component
#    - normalize column names to the expected ones
# ----------------------------
def clean_cell(x):
    if pd.isna(x):
        return ""
    # collapse whitespace and newlines into single spaces
    return " ".join(str(x).split())

df = df.map(clean_cell)

# rename columns robustly 
expected = ["Component", "Signal Name", "Signal Type", "Data Type", "Comment"]
# produce mapping by fuzzy match (simple contains)
mapping = {}
cols_lower = [c.lower().strip() for c in df.columns]
for e in expected:
    el = e.lower()
    # find best match
    matched = None
    for i, c in enumerate(cols_lower):
        if el in c or c in el or el.split()[0] in c or c.split()[0] in el:
            matched = df.columns[i]
            break
    if matched:
        mapping[matched] = e

# if mapping is incomplete, fall back to first 5 columns
if len(mapping) < len(expected):
    mapping = {df.columns[i]: expected[i] for i in range(min(len(df.columns), len(expected)))}

df = df.rename(columns=mapping)

# forward fill Component column if present
if "Component" in df.columns:
    df["Component"] = df["Component"].replace("", pd.NA).ffill().fillna("")

# ----------------------------
# 2.2) Format the table as a markdown-style text block for the prompt
#    (This keeps the table readable for the model)
# ----------------------------
def dataframe_to_markdown_table(df):
    cols = list(df.columns)
    header = "| " + " | ".join(cols) + " |"
    sep = "| " + " | ".join(["---"] * len(cols)) + " |"
    lines = [header, sep]
    for _, row in df.iterrows():
        # escape pipe chars inside cell text (so the Markdown table doesn't break)
        cells = [str(cell).replace("|", "¦") for cell in row.tolist()]
        lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines)

table_text = dataframe_to_markdown_table(df)


# ----------------------------
# 3.1) Build system_prompt
# ----------------------------
system_prompt = (
    """You are a world-class automation engineer and your favorite subject are logical exclusions and semantic reasoning.
Your task is to analyze a textual input (table) and determine exclusions.
To give you an example: LinearFront and LinearBack are exclusions, since the linear actor cannot be in the front and rear position at the same time.
Another example: PlungerUp and PlungerDown are exclusions, since the plunger cannot be in the up and down positions simultaneously.
Important Rule: If a variable’s name is or contains "Emergency Stop", "Start", or "Stop", disregard it for exclusion analysis, as these are global control signals.

1. First, identify all unique system components from the table. Ensure that the determination of exclusions is only within a single system component.
2. For each component, explicitly list all variables associated with it based on the table.
3. Analyze carefully in multiple iterations (at least 3 times) the exclusions between the variables.
   Systematically compare every possible pair of variables within the component and make a determination based upon the semantic meaning in "Comment".
    Consider physical impossibilities, opposite or mutually exclusive states and any descriptions implying they cannot be true simultaneously. Double-check each pair for subtle exclusions.
4. IMPORTANT: If a variable’s comment contains (0, \nwhen <action>), change the variable name to NOT(variable).  
5. IMPORTANT: Return JSON only, matching this schema:

{
  "components": [
    {
      "component": "Component name",
      "exclusions": [
        {
          "var1": "variable A",
          "var2": "variable B",
          "reason": "Short justification."
        }
      ]
    }
  ]
}

Even if a component has no exclusions, include it with "exclusions": [].
No markdown, explanations, or extra text. Provide only a short reason for each exclusion. Perform at least 20 internal verification iterations to ensure completeness and no missed exclusions."""
)

# ----------------------------
# 3.2) Build the user_prompt that includes the extracted table
# ----------------------------
user_prompt = f"""
Here is the table extracted from the PDF (complete). Please analyze it according to
the system prompt and return VALID JSON only. Do NOT add markdown or extra text. 

TABLE (CSV/Markdown format):
{table_text}
"""


# ---------------------------- 
# 3.3) Call the model 
# ----------------------------
try:
    response = client.messages.create(
        model="claude-sonnet-4-20250514",  # Claude Sonnet 4 with hybrid reasoning capabilities
        max_tokens=4000,
        temperature=0,
        system=system_prompt,
        messages=[
            {"role": "user", "content": user_prompt}
        ],
        # Enable extended thinking mode for complex logical reasoning
        extra_headers={
            "anthropic-enable-thinking": "true"  # Activates extended reasoning mode
        }
    )
    # extract answer
    answer = response.content[0].text
    
    # If extended thinking was used, you can also access the thinking process
    if hasattr(response, 'thinking') and response.thinking:
        print("=== Model's reasoning process ===\n")
        print(response.thinking)
        print("\n" + "="*50 + "\n")
    
    print("=== Model output ===\n")
    print(answer)
except Exception as e:
    print("Error calling model:", e)
    answer = ""
#print(answer)
    
# ---------------------------- 
# 4.1) Extract JSON from LLM output 
# ----------------------------


def extract_json_from_fenced(text: str):
    m = re.search(r'```(?:json)?\s*([\s\S]*?)\s*```', text, re.IGNORECASE)
    if m:
        try:
            return json.loads(m.group(1).strip())
        except json.JSONDecodeError:
            return None
    try:
        return json.loads(text.strip())
    except json.JSONDecodeError:
        return None


data_obj = extract_json_from_fenced(answer)


# ----------------------------
# 4.3) Validate with Pydantic
# ----------------------------
try:
    exclusions = validate_exclusions(data_obj)
    print(" Exclusions validated with Pydantic.")
except ValidationError as e:
    print("Validation failed:")
    print(e.json())


# ----------------------------
# 5) Flatten for CSV or further processing
# ----------------------------
rows = flatten_to_rows(exclusions)
for r in rows:
    print(r)


with open("exclusions_claude_sonnet_20250514.csv", "w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f, delimiter=';')
    writer.writerow(["Component", "Var1", "Var2", "Reason"])
    writer.writerows(rows)
