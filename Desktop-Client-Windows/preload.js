const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("electronAPI", {
  connectWebSocket: (host, port, enableTranslation, targetLanguage) =>
    ipcRenderer.invoke(
      "connect-websocket",
      host,
      port,
      enableTranslation,
      targetLanguage
    ),
  sendAudio: (audioData) => ipcRenderer.invoke("send-audio", audioData),
  disconnectWebSocket: () => ipcRenderer.invoke("disconnect-websocket"),
  onServerReady: (callback) =>
    ipcRenderer.on("server-ready", (event) => callback()),
  onTranscription: (callback) =>
    ipcRenderer.on("transcription-segments", (event, segments) =>
      callback(segments)
    ),
  onTranslation: (callback) =>
    ipcRenderer.on("translation-segments", (event, segments) =>
      callback(segments)
    ),
  onDisconnected: (callback) =>
    ipcRenderer.on("disconnected", (event) => callback()),
});
