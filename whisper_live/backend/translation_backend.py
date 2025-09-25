import json
import logging
import os
import threading
import time
import queue
from typing import Dict, Any, Optional

import numpy as np
import torch
from transformers import SeamlessM4TProcessor, AutoModelForSeq2SeqLM
from optimum.onnxruntime import ORTModelForSeq2SeqLM
from whisper_live.backend.base import ServeClientBase


DEFAULT_SEAMLESS_MODEL_ID = "seamless_m4t_v2_large_onnx"


class ServeClientTranslation(ServeClientBase):
    """
    Handles translation of completed transcription segments in a separate thread.
    Reads from a queue populated by the transcription backend and sends translated
    segments back to the client via WebSocket.
    """
    
    def __init__(
        self,
        client_uid,
        websocket,
        translation_queue,
        target_language="fr",
        send_last_n_segments=10,
        model_path: Optional[str] = None,  # Path or HF repo id for exported ONNX model directory
        auto_load_model: bool = True,
    ):
        """
        Initialize the translation client.
        
        Args:
            client_uid (str): Unique identifier for the client
            websocket: WebSocket connection to the client
            translation_queue (queue.Queue): Queue containing completed segments to translate
            target_language (str): Target language code (default: "fr" for French)
            send_last_n_segments (int): Number of recent translated segments to send
            model_path (str | None): Filesystem path or Hugging Face repo id that contains the SeamlessM4T ONNX export.
                                     Defaults to the SEAMLESS_M4T_MODEL_PATH environment variable or the built-in id.
            auto_load_model (bool): When True, attempt to load the model immediately.
        """
        super().__init__(client_uid, websocket, send_last_n_segments)
        self.translation_queue = translation_queue
        self.target_language = target_language
        self.model_path = model_path or os.getenv(
            "SEAMLESS_M4T_MODEL_PATH",
            DEFAULT_SEAMLESS_MODEL_ID,
        )
        self.translated_segments = []
        self.translation_model = None
        self.processor: Optional[SeamlessM4TProcessor] = None
        self.device = None
        self.model_loaded = False
        self.translation_available = False
        self._sent_status_message = False
        self._uses_onnx = False

        if auto_load_model:
            self.load_translation_model()

    def load_translation_model(self):
        """Load the ONNX translation model and tokenizer."""
        try:
            # Load processor and model
            self.processor = SeamlessM4TProcessor.from_pretrained(self.model_path)
            try:
                self.translation_model = ORTModelForSeq2SeqLM.from_pretrained(
                    self.model_path,
                    provider="CPUExecutionProvider"
                )
                self.device = "cpu"
                self._uses_onnx = True
                logging.info(
                    "ONNX translation model loaded successfully from '%s'. Target language: %s",
                    self.model_path,
                    self.target_language,
                )
            except Exception as ort_error:
                logging.warning(
                    "Failed to load ONNX translation model (%s). Falling back to PyTorch.",
                    ort_error,
                )
                self.translation_model = AutoModelForSeq2SeqLM.from_pretrained(self.model_path)
                self.device = "cuda" if torch.cuda.is_available() else "cpu"
                self.translation_model.to(self.device)
                self._uses_onnx = False
                logging.info(
                    "PyTorch translation model loaded successfully on %s from '%s'. Target language: %s",
                    self.device,
                    self.model_path,
                    self.target_language,
                )

            self.model_loaded = True
            self.translation_available = True
        except Exception as e:
            logging.error(f"Failed to load translation model: {e}")
            self.translation_model = None
            self.model_loaded = False
            self.translation_available = False
            self._notify_translation_unavailable(str(e))

    def _notify_translation_unavailable(self, error_message: str):
        """Send a warning to the client when translation assets are missing."""
        if self._sent_status_message:
            return
        payload = {
            "uid": self.client_uid,
            "status": "WARNING",
            "message": (
                "Translation model unavailable. "
                "Export SeamlessM4T v2 Large to ONNX (see scripts/export_seamless_m4t.py) "
                "and set SEAMLESS_M4T_MODEL_PATH or --translation-model-path accordingly."
            )
        }
        if error_message:
            payload["details"] = error_message
        try:
            self.websocket.send(json.dumps(payload))
            self._sent_status_message = True
        except Exception as send_error:
            logging.error(f"[ERROR]: Sending translation warning to client: {send_error}")

    def translate_text(self, text: str) -> str:
        """
        Translate a single text segment using ONNX.
        
        Args:
            text (str): Text to translate
            
        Returns:
            str: Translated text or original text if translation fails
        """
        if not self.model_loaded or not text.strip():
            return text
            
        try:
            # Prepare input for text-to-text translation
            inputs = self.processor(
                text,
                src_lang="eng",
                tgt_lang=self.target_language,
                return_tensors="pt"
            )
            
            # Generate translation
            if not self._uses_onnx:
                inputs = {k: v.to(self.device) for k, v in inputs.items()}

            generated_ids = self.translation_model.generate(
                **inputs,
                max_length=512,
                num_beams=5,
                early_stopping=True,
            )

            if not self._uses_onnx:
                generated_ids = generated_ids.to("cpu")

            # Decode output
            translated_text = self.processor.batch_decode(generated_ids, skip_special_tokens=True)
            return translated_text[0]
            
        except Exception as e:
            logging.error(f"ONNX translation failed for text '{text}': {e}")
            return text
    
    def process_translation_queue(self):
        """
        Process segments from the translation queue.
        Continuously reads from the queue until None is received (exit signal).
        """
        logging.info(f"Starting translation processing for client {self.client_uid}")
        
        while not self.exit:
            try:
                # Get segment from queue with timeout
                segment = self.translation_queue.get(timeout=1.0)
                
                # Check for exit signal
                if segment is None:
                    logging.info(f"Received exit signal for translation client {self.client_uid}")
                    break
                    
                # Only translate completed segments
                if not segment.get("completed", False):
                    self.translation_queue.task_done()
                    continue
                    
                # Translate the segment
                original_text = segment.get("text", "")
                translated_text = self.translate_text(original_text)
                
                # Create translated segment
                translated_segment = {
                    "start": segment["start"],
                    "end": segment["end"],
                    "text": translated_text,
                    "completed": segment.get("completed", False),
                    "target_language": self.target_language
                }
                
                self.translated_segments.append(translated_segment)
                segments_to_send = self.prepare_translated_segments()
                self.send_translation_to_client(segments_to_send)
                
                self.translation_queue.task_done()
                
            except queue.Empty:
                continue
            except Exception as e:
                logging.error(f"Error processing translation queue: {e}")
                continue
        
        logging.info(f"Translation processing ended for client {self.client_uid}")
    
    def prepare_translated_segments(self):
        """
        Prepare the last n translated segments to send to client.
        
        Returns:
            list: List of recent translated segments
        """
        if len(self.translated_segments) >= self.send_last_n_segments:
            return self.translated_segments[-self.send_last_n_segments:]
        return self.translated_segments[:]
    
    def send_translation_to_client(self, translated_segments):
        """
        Send translated segments to the client via WebSocket.
        
        Args:
            translated_segments (list): List of translated segments to send
        """
        try:
            self.websocket.send(
                json.dumps({
                    "uid": self.client_uid,
                    "translated_segments": translated_segments,
                })
            )
        except Exception as e:
            logging.error(f"[ERROR]: Sending translation data to client: {e}")
    
    def speech_to_text(self):
        """
        Override parent method to handle translation processing.
        This method will be called when the translation thread starts.
        """
        self.process_translation_queue()
    
    def set_target_language(self, language: str):
        """
        Change the target language for translation.
        
        Args:
            language (str): New target language code
        """
        self.target_language = language
        if self.processor:
            self.processor.tgt_lang = language
            logging.info(f"Target language changed to: {language}")
    
    def cleanup(self):
        """Clean up translation resources."""
        logging.info(f"Cleaning up translation resources for client {self.client_uid}")
        self.exit = True
        
        try:
            self.translation_queue.put(None, timeout=1.0)
        except:
            pass
        
        self.translated_segments.clear()
        
        if self.translation_model:
            del self.translation_model
            self.translation_model = None
        if self.processor:
            del self.processor
            self.processor = None
