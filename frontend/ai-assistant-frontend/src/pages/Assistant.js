import { useNavigate } from "react-router-dom";
import { useState, useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "../styles/Assistant.css";
import api from "../api/axiosConfig";

function Assistant() {
  const [isLoading, setIsLoading] = useState(false);
  const [messages, setMessages] = useState([]);
  const [sessions, setSessions] = useState([]);
  const sidebarTopRef = useRef(null);
  const messagesEndRef = useRef(null);
  const [previewFile, setPreviewFile] = useState(null);
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const isCreatingDefault = useRef(false);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [viewportHeight, setViewportHeight] = useState(window.innerHeight);
  const inputRef = useRef(null);
  const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

  const [currentSessionId, setCurrentSessionId] = useState(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      sessionStorage.removeItem("active_session_id");
      return null;
    }
    const saved = sessionStorage.getItem("active_session_id");
    return saved ? parseInt(saved, 10) : null;
  });
  
  useEffect(() => {
    const updateHeight = () => {
      setViewportHeight(window.innerHeight);
    };
    updateHeight(); 
    window.addEventListener("resize", updateHeight);

    return () => {
      window.removeEventListener("resize", updateHeight);
    };
  }, []);

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const response = await api.get("/api/auth/me");

        setUsername(response.data.username);

      } catch (err) {
        console.error(err);
        setUsername("User");
      }
    };

    fetchUser();
  }, []);
  const handleLogout = () => {

    localStorage.removeItem("token");
    sessionStorage.removeItem("active_session_id");
    navigate("/login");
  };

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
    await api.delete(`${API_BASE_URL}/api/sessions/${id}`);
    await loadSessions();
  };

  const loadMessages = async (sessionId) => {
    const response = await api.get(`/api/chat/session/${sessionId}`);
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
      setIsLoading(false);
      loadMessages(currentSessionId);
    }
  }, [currentSessionId]);

  const handleNewChat = async () => {
    try {
      const response = await api.post("/api/sessions");
      setSessions(prev => [response.data, ...prev]);
      setCurrentSessionId(response.data.id);
      setMessages([]);

      setTimeout(() => {
        sidebarTopRef.current?.scrollIntoView({ behavior: "smooth" });
      }, 50);

      return response.data; // return data to ensure the Promise resolves with the new session payload
    } catch (err) {
      console.error("Failed to create new chat session:", err);
      throw err;
    }
  };

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    try {
      const response = await api.get("/api/sessions");
      const sessionsData = response.data;
      setSessions(sessionsData);

      const savedSessionId = sessionStorage.getItem("active_session_id");

      if (sessionsData.length > 0) {
        if (savedSessionId) {
          const parsedId = parseInt(savedSessionId, 10);
          setCurrentSessionId(parsedId);
          //  Load messages for this session first before lifting the gate
          // await loadMessages(parsedId);
        } else {
          if (sessionsData[0].title === "New Chat") {
            setCurrentSessionId(sessionsData[0].id);
            sessionStorage.setItem("active_session_id", sessionsData[0].id);
            await loadMessages(sessionsData[0].id);
          } else {
            if (!isCreatingDefault.current) {
              isCreatingDefault.current = true;
              await handleNewChat();
              isCreatingDefault.current = false;
            }
          }
        }
      } else {
        if (!isCreatingDefault.current) {
          isCreatingDefault.current = true;
          await handleNewChat();
          isCreatingDefault.current = false;
        }
      }
    } catch (err) {
      console.error("Failed to populate sidebar chat items:", err);
    } finally {
      //  Initial workspace alignment is done! Lift the gate safely.
      setIsInitialLoading(false);
    }
  };

  const handleSend = async () => {
    if (isLoading) return;
    if (!input.trim() && !selectedFile) return;

    let typingInterval;
    let completionCheck;

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

        const response = await api.post("/api/chat/upload", formData, {
          headers: {
            "Content-Type": "multipart/form-data"
          }
        });

        console.log("UPLOAD RESPONSE:", response.data);
        const uploadData = response.data;

        // Populate the unique buffer cleanly 
        incomingTextBuffer = uploadData.aiResponse;

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
      } else {
        const token = localStorage.getItem("token");

        const response = await fetch(
          `${API_BASE_URL}/api/chat/stream?sessionId=${currentSessionId}&prompt=${encodeURIComponent(currentInput)}`,
          {
            headers: {
              Authorization: `Bearer ${token}`
            }
          }
        );

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let leftoverBuffer = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          const chunk = decoder.decode(value, { stream: true });
          const combinedPayload = leftoverBuffer + chunk;

          const lines = combinedPayload.split("\n");
          leftoverBuffer = lines.pop() || "";

          for (const line of lines) {
            const trimmedLine = line.trim();
            if (!trimmedLine || !trimmedLine.startsWith("data:")) continue;

            let cleanChunk = trimmedLine.replace("data:", "").trim();
            if (cleanChunk === "[DONE]") break;

            if (cleanChunk.startsWith('"') && cleanChunk.endsWith('"')) {
              cleanChunk = cleanChunk.slice(1, -1);
            }

            incomingTextBuffer += cleanChunk.replace(/\\n/g, "\n");
          }
        }
      }

      // UNIFIED TYPING ANIMATION: Executes smoothly across both text channels
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

          setMessages(prev => {
            const idx = prev.length - 1;
            if (idx >= 0 && prev[idx].sender === "assistant") {
              const updated = [...prev];
              updated[idx] = { ...updated[idx], text: displayedText };
              return updated;
            }
            return prev;
          });
        }
      }, 25);

      //  UNIFIED COMPLETION WORKER: Safely tracks and handles processing terminations
      completionCheck = setInterval(() => {
        if (incomingTextBuffer.length === 0) {
          clearInterval(completionCheck);
          if (typingInterval) clearInterval(typingInterval);

          setIsLoading(false);

          if (typeof loadSessions === "function") {
            loadSessions();
          }
        }
      }, 50);

    } catch (error) {
      console.error("Transmission error:", error);
      if (typingInterval) clearInterval(typingInterval);
      if (completionCheck) clearInterval(completionCheck);
      setIsLoading(false);

      setMessages(prev => {
        const updated = [...prev];
        if (updated.length > 0 && updated[updated.length - 1].sender === "assistant" && updated[updated.length - 1].text === "") {
          updated[updated.length - 1].text = "⚠️ Connection interrupted. Please try re-sending your message.";
        }
        return updated;
      });
    }
  };
  return (
    <div
      className="assistant-container"
      style={{ height: `${viewportHeight}px` }}
    >
      {/*  Mobile Overlay Backdrop: Closes the sidebar drawer when tapping anywhere on the chat canvas */}
      {isSidebarOpen && (
        <div
          className="sidebar-overlay"
          onClick={() => setIsSidebarOpen(false)}
        />
      )}

      {/* Sidebar - Dynamically appends the mobile visibility toggle frame modifier */}
      <div className={`sidebar ${isSidebarOpen ? "open" : ""}`}>
        <button className="logout-btn" onClick={handleLogout}>
          Logout
        </button>
        <button
          className="new-chat-btn"
          onClick={async () => {
            await handleNewChat();
            setIsSidebarOpen(false); //  Auto-close drawer view on select
          }}
        >
          + New Chat
        </button>

        {/* --- FIXED: Invisible anchor to snap scroll views upward --- */}
        <div ref={sidebarTopRef} />

        <div className="sidebar-sessions-list">
          {sessions.map(session => (
            <div
              key={session.id}
              className={currentSessionId === session.id ? "session-item active" : "session-item"}
              onClick={() => {
                setCurrentSessionId(session.id);
                setIsSidebarOpen(false); //  Auto-close drawer view after switching chats
              }}
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
        <div className="chat-header" style={{ display: 'flex', alignItems: 'center' }}>
          {/*  Mobile Toggle Trigger Hamburger Button */}
          <button
            className="mobile-menu-btn"
            onClick={() => setIsSidebarOpen(!isSidebarOpen)}
          >
            ☰
          </button>
          <h2>AI Assistant</h2>
        </div>

        <div className="messages-container">
          {/*  1: If application is performing initial load alignment, show a clean, non-disruptive loader */}
          {isInitialLoading ? (
            <div className="initial-workspace-loader" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
              <div className="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <p style={{ color: "#888", marginTop: "12px", fontSize: "14px" }}>Loading workspace...</p>
            </div>
          ) : (
            <>
              {/*  2: Welcome banner only mounts if workspace data is active, message thread is completely blank, AND username is valid */}
              {messages.length === 0 && username && (
                <div className="welcome-container">
                  <h1>
                    👋 Welcome, {username.charAt(0).toUpperCase() + username.slice(1)}!
                  </h1>
                  <p>How can I help you today?</p>
                </div>
              )}

              {/* Message Thread History Rendering */}
              {messages.map((message, index) => (
                <div key={index} className={`message ${message.sender}`}>
                  {/* IMAGE PREVIEW CARD */}
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

                  {/* PDF PREVIEW CARD */}
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

                  {/* OTHER GENERIC ATTACHMENT CARDS */}
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
                          {message.mediaType === "text/plain" ? "Click to Preview" : "Click to Open"}
                        </span>
                      </div>
                    )}

                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {message.text}
                  </ReactMarkdown>
                </div>
              ))}
            </>
          )}

          {/* Active Turn Assistant Processing Loader Indicator */}
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

        {/* Input Control Console Layout */}
        <div className="input-area-wrapper">
          {selectedFile && (
            <div className="file-preview-chip">
              <span className="file-icon">📁</span>
              <span className="file-name-text">{selectedFile.name}</span>
              <button className="remove-file-btn" onClick={handleRemoveFile}>✕</button>
            </div>
          )}

          <div className="input-container">
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              style={{ display: "none" }}
              accept=".pdf,.txt,.doc,.docx,.png,.jpg,.jpeg"
            />

            <button
              className="attachment-add-btn"
              onClick={() => fileInputRef.current?.click()}
              type="button"
            >
              ＋
            </button>

            <input
              ref={inputRef}
              type="text"
              placeholder="Message AI Assistant or upload files..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleSend();
              }}
            />

            <button
              onClick={handleSend}
              disabled={isLoading}
              className="send-action-btn"
            >
              {isLoading ? "..." : "Send"}
            </button>
          </div>
        </div>
      </div>

      {/* Interactive Floating Preview Modal Container */}
      {previewFile && (
        <div className="preview-modal-overlay" onClick={() => setPreviewFile(null)}>
          <div className="preview-modal" onClick={(e) => e.stopPropagation()}>
            <button className="close-preview-btn" onClick={() => setPreviewFile(null)}>
              ✕
            </button>

            {previewFile.type === "application/pdf" && (
              <iframe
                src={previewFile.url}
                title="pdf-preview"
                width="100%"
                height="100%"
              />
            )}

            {previewFile.type?.startsWith("image/") && (
              <img src={previewFile.url} alt="preview" className="full-image-preview" />
            )}

            {previewFile.type === "text/plain" && (
              <div className="text-preview-container">
                <h3>{previewFile.fileName}</h3>
                <pre className="text-preview-content">{previewFile.content}</pre>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default Assistant;