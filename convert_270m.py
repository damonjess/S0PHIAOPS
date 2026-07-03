from litert_torch.generative.examples.gemma3 import gemma3
from litert_torch.generative.utilities import converter
from litert_torch.generative.utilities.export_config import ExportConfig
from litert_torch.generative.layers import kv_cache

# Build the PyTorch model from the downloaded Hugging Face weights
pytorch_model = gemma3.build_model_270m("./hf-gemma3-270m")

# Configure the export settings for mobile performance
export_config = ExportConfig()
export_config.kvcache_layout = kv_cache.KV_LAYOUT_TRANSPOSED
export_config.mask_as_input = True

# Convert to LiteRT (TFLite) format with 8-bit quantization
converter.convert_to_tflite(
    pytorch_model,
    output_path="./output_270m",
    output_name_prefix="sophia-gemma3-270m",
    prefill_seq_len=1024,
    kv_cache_max_len=2048,
    quantize="dynamic_int8",
    export_config=export_config,
)
