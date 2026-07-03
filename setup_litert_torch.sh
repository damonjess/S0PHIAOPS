#!/bin/bash
# Setup script for LiteRT Torch conversion environment

echo "Creating python virtual environment..."
python -m venv litert-torch

echo "Activating environment..."
source litert-torch/bin/activate

echo "Installing LiteRT Torch..."
pip install "litert-torch>=0.8.0"

echo "Done. You can now use litert-torch to convert PyTorch models to LiteRT format."
echo "Use 'source litert-torch/bin/activate' to start working."
