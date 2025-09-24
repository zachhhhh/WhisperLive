const { app, BrowserWindow, ipcMain } = require("electron");
const path = require("path");
const WebSocket = require("ws");

let mainWindow;
let wsClient;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 800,
    height: 600,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  mainWindow.loadFile("index.html");

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

app.whenReady().then(createWindow);

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

app.on("activate", () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});

// IPC handlers for WebSocket and audio
ipcMain.handle(
  "connect-websocket",
  (event, host, port, enableTranslation, targetLanguage) => {
    return new Promise((resolve, reject) => {
      const uid = Math.random().toString(36).substring(7);
      const wsUrl = `ws://${host}:${port}`;
      wsClient = new WebSocket(wsUrl);

      wsClient.on("open", () => {
        const json = {
          uid: uid,
          language: "en",
          task: "transcribe",
          model: "small",
          use_vad: true,
          enable_translation: enableTranslation,
          target_language: targetLanguage,
          send_last_n_segments: 10,
        };
        wsClient.send(JSON.stringify(json));
        resolve({ status: "connected", uid: uid });
      });

      wsClient.on("message", (data) => {
        try {
          const message = JSON.parse(data.toString());
          if (message.status === "SERVER_READY") {
            mainWindow.webContents.send("server-ready");
          } else if (message.segments) {
            mainWindow.webContents.send(
              "transcription-segments",
              message.segments
            );
          } else if (message.translated_segments) {
            mainWindow.webContents.send(
              "translation-segments",
              message.translated_segments
            );
          } else if (message.message === "DISCONNECT") {
            mainWindow.webContents.send("disconnected");
          }
        } catch (e) {
          // Handle binary audio if needed, but for now assume text
        }
      });

      wsClient.on("close", () => {
        mainWindow.webContents.send("disconnected");
      });

      wsClient.on("error", (error) => {
        reject(error);
      });
    });
  }
);

ipcMain.handle("send-audio", (event, audioData) => {
  if (wsClient && wsClient.readyState === WebSocket.OPEN) {
    wsClient.send(audioData);
  }
});

ipcMain.handle("disconnect-websocket", () => {
  if (wsClient) {
    wsClient.send("END_OF_AUDIO");
    wsClient.close();
    wsClient = null;
  }
});
