import os
from transformers import SeamlessM4TModel, SeamlessM4TProcessor
from optimum.onnxruntime import ORTModelForSeq2SeqLM
from optimum.exporters.onnx import main_export

# Load model and processor
model_name = "facebook/seamless-m4t-v2-large"
model = SeamlessM4TModel.from_pretrained(model_name, trust_remote_code=True)
processor = SeamlessM4TProcessor.from_pretrained(model_name, trust_remote_code=True)

# Export to ONNX for text-to-text translation (t2tt task)
# Use optimum for Seq2SeqLM export
ort_model = ORTModelForSeq2SeqLM.from_pretrained(model_name, export=True)

# Save to directory
output_dir = "seamless_m4t_v2_large_onnx"
os.makedirs(output_dir, exist_ok=True)

ort_model.save_pretrained(output_dir)
processor.save_pretrained(output_dir)

print(f"Model exported to {output_dir}")
