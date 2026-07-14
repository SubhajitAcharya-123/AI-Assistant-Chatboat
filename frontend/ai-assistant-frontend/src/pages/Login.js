import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../styles/Login.css";
import api from "../api/axiosConfig";

function Login() {

    const navigate = useNavigate();

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const handleLogin = async (e) => {
        e.preventDefault();
        if (!email.trim() || !password.trim()) {
           alert("Please fill in all fields.");
           return;
       }

        try {

            const response = await api.post(
                "/api/auth/login",
                {
                    email,
                    password
                }
            );

            localStorage.setItem(
                "token",
                response.data.token
            );

            // alert("Login Successful");

            navigate("/chat");

        } catch (error) {

            console.error(error);

            if (error.response && (error.response.status === 401 || error.response.status === 403 || error.response.status === 404)) {
                alert("No record found. Either email/username or password incorrect.");
            } else {
                alert("An error occurred during login. Please try again.");
            }
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
                onSubmit={handleLogin}
            >

                <h2>Login</h2>

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
                    Login
                </button>

            </form>

        </div>
    );

}

export default Login;