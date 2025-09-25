import os
from pathlib import Path

from optimum.exporters.onnx import main_export

model_id = "facebook/seamless-m4t-v2-large"
cache_dir = Path.home() / ".cache" / "whisper-live"
output_dir = cache_dir / "seamless_m4t_onnx"

os.makedirs(output_dir, exist_ok=True)

print(f"Exporting {model_id} to {output_dir}")

main_export(
    model_id,
    output=str(output_dir),
    task="text2text-generation",
    opset=14,
    cache_dir=str(cache_dir)
)

print("Export complete.")