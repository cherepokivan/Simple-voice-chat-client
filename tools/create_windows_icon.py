from pathlib import Path
from PIL import Image

source = Path("src/StandaloneVoiceChat.UI/Assets/simple-voice-chat-icon.png")
target = Path("src/StandaloneVoiceChat.UI/Assets/simple-voice-chat-icon.ico")

with Image.open(source) as image:
    rgba = image.convert("RGBA")
    rgba.save(target, format="ICO", sizes=[(16, 16), (20, 20), (24, 24), (32, 32), (40, 40), (48, 48), (64, 64), (128, 128), (256, 256)])

print(target)
