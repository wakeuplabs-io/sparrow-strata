#!/bin/bash

# Ensure the outputs directory exists
mkdir -p spikes/outputs

# Loop through all markdown files in the spikes directory
for md_file in spikes/*.md; do
  # Get the base name of the file (e.g., "reproducible" from "spikes/reproducible.md")
  base_name=$(basename "$md_file" .md)

  # Define the output file path
  output_file="spikes/outputs/${base_name}.docx"

  # Run pandoc
  pandoc "$md_file" --reference-doc=spikes/assets/reference.docx -o "$output_file"
  
  echo "Converted $md_file to $output_file"
done
