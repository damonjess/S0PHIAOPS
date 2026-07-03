from mediapipe.tasks.python.genai import bundler

config = bundler.BundleConfig(
    tflite_model="./output_270m/sophia-gemma3-270m.tflite",
    tokenizer_model="./hf-gemma3-270m/tokenizer.model",
    start_token="<bos>",
    stop_tokens=["<eos>", "<end_of_turn>"],
    output_filename="./output_270m/model.task",
    prompt_prefix_user="<start_of_turn>user\n",
    prompt_suffix_user="<end_of_turn>\n",
    prompt_prefix_model="<start_of_turn>model\n",
)

bundler.create_bundle(config)
