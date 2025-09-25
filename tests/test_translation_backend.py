import json
import queue
from unittest.mock import MagicMock

from whisper_live.backend.translation_backend import ServeClientTranslation


def test_translation_queue_passes_through_text_when_model_missing():
    websocket = MagicMock()
    translation_queue = queue.Queue()

    client = ServeClientTranslation(
        client_uid="test-client",
        websocket=websocket,
        translation_queue=translation_queue,
        target_language="es",
        auto_load_model=False,
    )

    translation_queue.put({
        "start": "0.0",
        "end": "1.0",
        "text": "hola mundo",
        "completed": True,
    })
    translation_queue.put(None)

    client.process_translation_queue()

    websocket.send.assert_called_once()
    payload = json.loads(websocket.send.call_args[0][0])
    translated = payload["translated_segments"][0]
    assert translated["text"] == "hola mundo"
    assert translated["target_language"] == "es"


def test_warning_sent_once_when_translation_unavailable():
    websocket = MagicMock()
    translation_queue = queue.Queue()

    client = ServeClientTranslation(
        client_uid="test-client",
        websocket=websocket,
        translation_queue=translation_queue,
        auto_load_model=False,
    )

    client._notify_translation_unavailable("missing")
    client._notify_translation_unavailable("missing again")

    websocket.send.assert_called_once()
    payload = json.loads(websocket.send.call_args[0][0])
    assert payload["status"] == "WARNING"
    assert "Translation model unavailable" in payload["message"]
