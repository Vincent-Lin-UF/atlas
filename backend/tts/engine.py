import torchaudio as ta
import torch
from chatterbox.tts import ChatterboxTTS
import traceback

device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"Using device: {device}")

try:
    model = ChatterboxTTS.from_pretrained(device=device)
except Exception as e:
    print(f"Error loading model: {e}")
    traceback.print_exc()
    raise

text = "I am Leo Jiang and I am trying to learn how to webscrape on android"

wav = model.generate(text, audio_prompt_path="peter.ogg")

ta.save("test.wav", wav, model.sr)
