import camelot
import pandas as pd


def main(pdf_path):
    # Extract ALL tables across ALL pages
    tables = camelot.read_pdf(pdf_path, pages="all", flavor="lattice")

    dfs = []
    for idx, t in enumerate(tables):
        df = t.df

        # First row is header
        df.columns = df.iloc[0]
        df = df.drop(0).reset_index(drop=True)

        dfs.append(df)

    # Merge everything into one DataFrame
    full_df = pd.concat(dfs, ignore_index=True)

    # Clean: forward fill empty "Component" cells if they exist
    if "Component" in full_df.columns:
        full_df["Component"] = full_df["Component"].replace("", None).ffill()
    print(full_df)

    # Save result
    full_df.to_csv("full_extracted_table.csv", index=False)


