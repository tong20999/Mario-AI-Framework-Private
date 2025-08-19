import base64
import os

def write_hyperparameters(
              working_dir,
              hyperparameters,
              pcgBase64):
      with open(os.path.join(working_dir, "hyperparameters.json"), "w", encoding='utf-8') as file:
                    file.write(hyperparameters)
      with open(os.path.join(working_dir, "pcgLevel.json"), "w", encoding='utf-8') as file:
              file.write(base64.b64decode(pcgBase64).decode("utf-8"))