import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../styles/Register.css";
import api from "../api/axiosConfig";

function Register() {

    const navigate = useNavigate();
    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const handleRegister = async (e) => {
        e.preventDefault();
        try {
            if (!username.trim() || !email.trim() || !password.trim()) {
              alert("Please fill in all required registration fields.");
              return;
            }
            const response = await api.post(
                "/api/auth/register",
                {
                    username,
                    email,
                    password
                }
            );

            localStorage.setItem(
                "token",
                response.data.token
            );

            alert("Registration Successful");

            navigate("/chat");

        } catch (error) {

            console.error(error);

            alert(
                error.response?.data?.message
                || "Registration Failed"
            );
        }
    };

    return (
        <div className="auth-container">

            <div
                className="back-arrow"
                onClick={() => navigate("/")}
            >
                ← Back
            </div>

            <form
                className="auth-card"
                onSubmit={handleRegister}
            >

                <h2>Create Account</h2>

                <input
                    type="text"
                    placeholder="Username"
                    value={username}
                    onChange={(e) =>
                        setUsername(e.target.value)
                    }
                    required
                />

                <input
                    type="email"
                    placeholder="Email"
                    value={email}
                    onChange={(e) =>
                        setEmail(e.target.value)
                    }
                    required
                />

                <input
                    type="password"
                    placeholder="Password"
                    value={password}
                    onChange={(e) =>
                        setPassword(e.target.value)
                    }
                    required
                />

                <button type="submit">
                    Register
                </button>

            </form>

        </div>
    );

}

export default Register;