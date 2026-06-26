import { useState, useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "../styles/Assistant.css";
import axios from "axios"

function Assistant() {
  const [isLoading, setIsLoading] = useState(false);
  const [messages, setMessages] = useState([]);
  const [sessions, setSessions] = useState([]);
  const sidebarTopRef = useRef(null);
  const messagesEndRef = useRef(null);
  const [previewFile, setPreviewFile] = useState(null);

  const [currentSessionId, setCurrentSessionId] = useState(() => {
    const saved = sessionStorage.getItem("active_session_id");
    return saved ? parseInt(saved, 10) : null;
  });

  useEffect(() => {
    if (currentSessionId) {
      sessionStorage.setItem("active_session_id", currentSessionId);
    }
  }, [currentSessionId]);
  const [input, setInput] = useState("");

  // --- NEW FILE UPLOAD STATES ---
  const [selectedFile, setSelectedFile] = useState(null);
  const fileInputRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({
      behavior: "smooth"
    });
  }, [messages]);

  // Handle file selection change
  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  // Clear selected attachment preview
  const handleRemoveFile = () => {
    setSelectedFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const deleteSession = async (id) => {
    const confirmed = window.confirm("Delete this chat?");
    if (!confirmed) return;
    await axios.delete(`http://localhost:8080/api/sessions/${id}`);
    await loadSessions();
  };

  const loadMessages = async (sessionId) => {
    const response = await axios.get(`http://localhost:8080/api/chat/session/${sessionId}`);
    const formattedMessages = response.data.map(message => ({
      sender: message.role,
      text: message.content,
      mediaUrl: message.mediaUrl,
      mediaType: message.mediaType,
      fileName: message.fileName
    }));
    setMessages(formattedMessages);
  };

  useEffect(() => {
    if (currentSessionId) {
      loadMessages(currentSessionId);
    }
  }, [currentSessionId]);

  const handleNewChat = async () => {
    const response = await axios.post("http://localhost:8080/api/sessions");
    setSessions(prev => [response.data, ...prev]);
    setCurrentSessionId(response.data.id);
    setMessages([]);
    setTimeout(() => {
      sidebarTopRef.current?.scrollIntoView({ behavior: "smooth" });
    }, 50);
  };

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    try {
      const response = await axios.get("http://localhost:8080/api/sessions");
      const sessionsData = response.data;
      setSessions(sessionsData);

      // Read from storage to see if the user is refreshing an active tab
      const savedSessionId = sessionStorage.getItem("active_session_id");

      if (sessionsData.length > 0) {
        if (savedSessionId) {
          // SCENARIO 1: The user refreshed the page. Stay on the exact same session!
          const parsedId = parseInt(savedSessionId, 10);
          setCurrentSessionId(parsedId);
        } else {
          // SCENARIO 2: Fresh app launch. Look at the top session item.
          if (sessionsData[0].title === "New Chat") {
            // Reuse the existing empty session
            setCurrentSessionId(sessionsData[0].id);
            sessionStorage.setItem("active_session_id", sessionsData[0].id);
          } else {
            // If the top chat already has history, cleanly spin up a fresh blank canvas!
            handleNewChat();
          }
        }
      } else {
        // If the database has absolutely zero sessions, spin one up right now
        handleNewChat();
      }
    } catch (err) {
      console.error("Failed to populate sidebar chat items:", err);
    }
  };


  const handleSend = async () => {
    if (isLoading) return;
    if (!input.trim() && !selectedFile) return;

    let typingInterval;

    let userMessageText = input;
    if (selectedFile) {
      userMessageText = `📁 *Attached File: ${selectedFile.name}*\n\n${input}`;
    }

    const userMessage = {
      sender: "user",
      text: userMessageText,
      mediaUrl: null,
      mediaType: null,
      fileName: null

    };

    setMessages(prev => [...prev, userMessage]);
    const currentInput = input;
    const currentFile = selectedFile;

    setInput("");
    handleRemoveFile();

    try {
      setIsLoading(true);

      setMessages(prev => [
        ...prev,
        {
          sender: "assistant",
          text: ""
        }
      ]);

      let incomingTextBuffer = "";
      let displayedText = "";

      if (currentFile) {
        const formData = new FormData();
        formData.append("file", currentFile);
        formData.append("prompt", currentInput);
        formData.append("sessionId", currentSessionId);

        const response = await axios.post("http://localhost:8080/api/chat/upload", formData, {
          headers: {
            "Content-Type": "multipart/form-data"
          }
        });
        console.log("UPLOAD RESPONSE:", response.data);
        const uploadData = response.data;
        setMessages(prev => {
          const updated = [...prev];

          for (let i = updated.length - 1; i >= 0; i--) {
            if (updated[i].sender === "user") {
              updated[i] = {
                ...updated[i],
                mediaUrl: uploadData.mediaUrl,
                mediaType: uploadData.mediaType,
                fileName: uploadData.fileName,
                extractedContent: uploadData.extractedContent
              };
              break;
            }
          }

          return updated;
        });
        incomingTextBuffer = uploadData.aiResponse;
      } else {
        const eventSource = new EventSource(
          `http://localhost:8080/api/chat/stream?sessionId=${currentSessionId}&prompt=${encodeURIComponent(currentInput)}`
        );

        eventSource.onmessage = (event) => {
          if (event.data === "[DONE]") {
            eventSource.close();

            const completionCheck = setInterval(() => {
              if (incomingTextBuffer.length === 0) {
                clearInterval(completionCheck);
                clearInterval(typingInterval);
                setIsLoading(false);
                if (typeof loadSessions === "function") loadSessions();
              }
            }, 50);
            return;
          }

          let cleanChunk = event.data;
          if (cleanChunk.startsWith('"') && cleanChunk.endsWith('"')) {
            cleanChunk = cleanChunk.slice(1, -1);
          }
          cleanChunk = cleanChunk.replace(/\\n/g, "\n");
          incomingTextBuffer += cleanChunk;
        };

        // --- FIXED: Reset state completely on error so it never hangs ---
        eventSource.onerror = (err) => {
          console.error("EventSource failed:", err);
          eventSource.close();
          if (typingInterval) clearInterval(typingInterval);
          setIsLoading(false);
        };
      }

      typingInterval = setInterval(() => {
        if (incomingTextBuffer.length > 0) {
          const nextSpaceIdx = incomingTextBuffer.indexOf(" ");
          let chunk = "";

          if (nextSpaceIdx !== -1) {
            chunk = incomingTextBuffer.substring(0, nextSpaceIdx + 1);
            incomingTextBuffer = incomingTextBuffer.substring(nextSpaceIdx + 1);
          } else {
            chunk = incomingTextBuffer;
            incomingTextBuffer = "";
          }

          displayedText += chunk;

          setMessages(prevMessages => {
            const targetIdx = prevMessages.length - 1;
            if (targetIdx >= 0 && prevMessages[targetIdx].sender === "assistant") {
              const updatedArray = [...prevMessages];
              updatedArray[targetIdx] = {
                ...updatedArray[targetIdx],
                text: displayedText
              };
              return updatedArray;
            }
            return prevMessages;
          });
        } else if (!isLoading && currentFile) {
          // Fallback mechanism for non-streamed HTTP upload text chunks
          clearInterval(typingInterval);
          setIsLoading(false);
          loadSessions();
        }
      }, 25);

    } catch (error) {
      console.error("Transmission error:", error);
      if (typingInterval) clearInterval(typingInterval);
      setIsLoading(false); // Clean up UI state lock instantly
    }
  };
  return (
    <div className="assistant-container">
      {/* Sidebar */}
      <div className="sidebar">
        <button className="new-chat-btn" onClick={handleNewChat}>
          + New Chat
        </button>

        {/* --- FIXED: Invisible anchor to snap scroll views upward --- */}
        <div ref={sidebarTopRef} />

        <div className="sidebar-sessions-list">
          {sessions.map(session => (
            <div
              key={session.id}
              className={currentSessionId === session.id ? "session-item active" : "session-item"}
              onClick={() => setCurrentSessionId(session.id)}
            >
              <span className="session-title">{session.title}</span>
              <button
                className="delete-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  deleteSession(session.id);
                }}
              >
                🗑
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="chat-section">
        <div className="chat-header">
          <h2>AI Assistant</h2>
        </div>

        <div className="messages-container">
          {messages.length === 0 && (
            <div className="message assistant">
              Hello! How can I help you today today? Feel free to upload files or documents!
            </div>
          )}
          {messages.map((message, index) => (
            <div key={index} className={`message ${message.sender}`}>
              {/* IMAGE */}

              {message.mediaType?.startsWith("image/") && (
                <div
                  className="attachment-card"
                  onClick={() =>
                    setPreviewFile({
                      url: message.mediaUrl,
                      type: message.mediaType
                    })
                  }
                >
                  🖼️ {message.fileName}
                  <span>Click to Preview</span>
                </div>
              )}
              {/* PDF */}

              {message.mediaType === "application/pdf" && (
                <div
                  className="attachment-card"
                  onClick={() =>
                    setPreviewFile({
                      url: message.mediaUrl,
                      type: message.mediaType
                    })
                  }
                >
                  📄 {message.fileName}
                  <span>Click to Preview</span>
                </div>
              )}
              {/* OTHER FILES */}

              {message.mediaUrl &&
                !message.mediaType?.startsWith("image/") &&
                message.mediaType !== "application/pdf" && (
                  <div
                    className="attachment-card"
                    onClick={() => {

                      if (message.mediaType === "text/plain") {

                        setPreviewFile({
                          type: "text/plain",
                          fileName: message.fileName,
                          content: message.extractedContent
                        });

                      } else {
                        window.open(message.mediaUrl, "_blank");
                      }
                    }}
                  >
                    📎 {message.fileName || "Attachment"}
                    <span>
                      {message.mediaType === "text/plain"
                        ? "Click to Preview"
                        : "Click to Open"}
                    </span>
                  </div>
                )}
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {message.text}
              </ReactMarkdown>
            </div>
          ))}
          {isLoading && messages[messages.length - 1]?.text === "" && (
            <div className="message assistant loading-turn">
              <div className="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef}></div>
        </div>

        {/* Input Control Console */}
        <div className="input-area-wrapper">

          {/* File Preview Chip Section populates here right above input box */}
          {selectedFile && (
            <div className="file-preview-chip">
              <span className="file-icon">📁</span>
              <span className="file-name-text">{selectedFile.name}</span>
              <button className="remove-file-btn" onClick={handleRemoveFile}>✕</button>
            </div>
          )}

          <div className="input-container">
            {/* Hidden native input layer */}
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              style={{ display: "none" }}
              accept=".pdf,.txt,.doc,.docx,.png,.jpg,.jpeg"
            />

            {/* Trigger Button */}
            <button
              className="attachment-add-btn"
              onClick={() => fileInputRef.current?.click()}
              type="button"
            >
              ＋
            </button>

            <input
              type="text"
              placeholder="Message AI Assistant or upload files..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleSend();
              }}
            />

            <button onClick={handleSend} disabled={isLoading} className="send-action-btn">
              {isLoading ? "Thinking..." : "Send"}
            </button>
          </div>
        </div>
      </div>
      {previewFile && (
        <div
          className="preview-modal-overlay"
          onClick={() => setPreviewFile(null)}
        >
          <div
            className="preview-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="close-preview-btn"
              onClick={() => setPreviewFile(null)}
            >
              ✕
            </button>

            {/* PDF */}
            {previewFile.type === "application/pdf" && (
              <iframe
                src={previewFile.url}
                title="pdf-preview"
                width="100%"
                height="100%"
              />
            )}
            
            {/* IMAGE */}
            {previewFile.type?.startsWith("image/") && (
              <img
                src={previewFile.url}
                alt="preview"
                className="full-image-preview"
              />
            )}

            {/* TXT */}
            {previewFile.type === "text/plain" && (
              <div className="text-preview-container">

                <h3>{previewFile.fileName}</h3>

                <pre className="text-preview-content">
                  {previewFile.content}
                </pre>

              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default Assistant;