#!/bin/bash
# Script to download the MediaPipe Gemma 2B IT (int4) model used by S0PHIA OPS.
# This matches the model URL hardcoded in DashboardViewModel.downloadModel().
#
# Usage:
#   ./download_gemma3.sh [output_path]
#
# If no output path is given, the model is saved as "model.task" in the current
# directory. You can then push it to the device:
#   adb push model.task /data/data/com.sophia.ops/files/model.task

set -e

MODEL_URL="https://storage.googleapis.com/mediapipe-models/llm/gemma-2b-it-cpu-int4.task"
OUTPUT_PATH="${1:-model.task}"

echo "Downloading MediaPipe Gemma 2B IT (int4) model..."
echo "URL: $MODEL_URL"
echo "Output: $OUTPUT_PATH"

if command -v curl &> /dev/null; then
    curl -L -o "$OUTPUT_PATH" "$MODEL_URL"
elif command -v wget &> /dev/null; then
    wget -O "$OUTPUT_PATH" "$MODEL_URL"
else
    echo "Error: Neither curl nor wget is installed." >&2
    exit 1
fi

echo ""
echo "Download complete: $OUTPUT_PATH"
echo ""
echo "To install on device:"
echo "  adb push $OUTPUT_PATH /data/data/com.sophia.ops/files/model.task"
echo ""
echo "To compute the SHA-256 checksum (for EXPECTED_MODEL_SHA256 in DashboardViewModel):"
echo "  sha256sum $OUTPUT_PATH"
