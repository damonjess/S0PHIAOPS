#!/bin/bash
# Script to download Gemma 3 270M IT from Hugging Face

echo "Setting up Hugging Face environment..."
python -m venv hf
source hf/bin/activate

echo "Installing huggingface_hub[cli]..."
pip install "huggingface_hub[cli]"

echo "Downloading Gemma 3 270M IT..."
# This downloads the model to the local directory ./hf-gemma3-270m
huggingface-cli download google/gemma-3-270m-it --local-dir "./hf-gemma3-270m"

echo "Download complete. Model files are in ./hf-gemma3-270m"
