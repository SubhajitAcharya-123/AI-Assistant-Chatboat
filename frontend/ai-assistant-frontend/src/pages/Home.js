import React from "react";
import { useNavigate } from "react-router-dom";
import "../styles/Home.css";

function Home() {
    const navigate = useNavigate();


    return (
        <div className="home-container">

            <div className="home-card">

                <h1>AI Assistant</h1>

                <p>
                    Chat with AI, upload documents, search memory,
                    and get intelligent assistance.
                </p>

                <div className="home-buttons">

                    <button
                        className="home-btn"
                        onClick={() => navigate("/login")}
                    >
                        Login
                    </button>

                    <button
                        className="home-btn secondary"
                        onClick={() => navigate("/register")}
                    >
                        Register
                    </button>

                </div>

            </div>

        </div>
    );
}

export default Home;
