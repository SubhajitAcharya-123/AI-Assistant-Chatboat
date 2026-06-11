import { useState, useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "../styles/Assistant.css";
import axios from "axios"

function Assistant() {
  const [isLoading, setIsLoading] = useState(false);
  const [messages, setMessages] = useState([]);
  const [sessions, setSessions] = useState([]);
  const messagesEndRef = useRef(null);

  const [currentSessionId, setCurrentSessionId] = useState(1);
  const [input, setInput] = useState("");
  useEffect(() => {

    messagesEndRef.current?.scrollIntoView({
      behavior: "smooth"
    });

  }, [messages]);
  const deleteSession = async (id) => {

    const confirmed = window.confirm(
      "Delete this chat?"
    );

    if (!confirmed) return;

    await axios.delete(
      `http://localhost:8080/api/sessions/${id}`
    );

    await loadSessions();
  };
  const loadMessages = async (sessionId) => {

    const response = await axios.get(
      `http://localhost:8080/api/chat/session/${sessionId}`
    );

    const formattedMessages = response.data.map(message => ({
      sender: message.role,
      text: message.content
    }));

    setMessages(formattedMessages);
  };
  useEffect(() => {

    if (currentSessionId) {
      loadMessages(currentSessionId);
    }

  }, [currentSessionId]);
  const handleNewChat = async () => {

    const response = await axios.post(
      "http://localhost:8080/api/sessions"
    );

    setSessions(prev => [
      response.data,
      ...prev
    ]);

    setCurrentSessionId(response.data.id);
    setMessages([]);

  };
  useEffect(() => {
    loadSessions();
  }, []);
  const loadSessions = async () => {

    const response = await axios.get(
      "http://localhost:8080/api/sessions"
    );

    if (response.data.length > 0) {
      setCurrentSessionId(response.data[0].id);
    }
    setSessions(response.data);
  };
  const handleSend = async () => {
    if (isLoading) return;
    if (!input.trim()) return;

    const userMessage = {
      sender: "user",
      text: input
    };

    setMessages(prev => [...prev, userMessage]);

    const currentInput = input;

    setInput("");

    try {
      setIsLoading(true);
      const response = await axios.post(
        "http://localhost:8080/api/chat",
        {
          sessionId: currentSessionId,
          message: currentInput
        }
      );
      await loadSessions();
      const aiMessage = {
        sender: "assistant",
        text: response.data
      };

      setMessages(prev => [...prev, aiMessage]);

    } catch (error) {
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };
  return (
    <div className="assistant-container">
      {/* Sidebar */}
      <div className="sidebar">
        <button
          className="new-chat-btn"
          onClick={handleNewChat}
        >
          + New Chat
        </button>
        {sessions.map(session => (
          <div
            key={session.id}
            className={
              currentSessionId === session.id
                ? "session-item active"
                : "session-item"
            }
            onClick={() => setCurrentSessionId(session.id)}
          >
            <span className="session-title">
              {session.title}
            </span>

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

      {/* Main Chat Area */}
      <div className="chat-section">
        <div className="chat-header">
          <h2>AI Assistant</h2>
        </div>

        <div className="messages-container">
          {messages.length === 0 && (
            <div className="message assistant">
              Hello! How can I help you today?
            </div>
          )}
          {messages.map((message, index) => (
            <div
              key={index}
              className={`message ${message.sender}`}
            >
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {message.text}
              </ReactMarkdown>
            </div>
          ))}
          {isLoading && (
            <div className="message assistant">
              <div className="typing-indicator">
                ● ● ●
              </div>
            </div>
          )}
          <div ref={messagesEndRef}></div>
        </div>

        <div className="input-container">
          <input
            type="text"
            placeholder="Message AI Assistant..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                handleSend();
              }
            }}
          />

          <button
            onClick={handleSend}
            disabled={isLoading}
          >
            {isLoading ? "Thinking..." : "Send"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default Assistant;