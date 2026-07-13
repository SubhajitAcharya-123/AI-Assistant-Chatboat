import axios from "axios";

const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";
const api = axios.create({
    baseURL: API_BASE_URL
});

// A placeholder variable we will wire up in index.js to manage the spinner state
let spinnerToggler = () => {};

export const setupAxiosInterceptors = (setIsLoading) => {
    spinnerToggler = setIsLoading;
};

api.interceptors.request.use(
    (config) => {
        spinnerToggler(true); //  Turn spinner ON when a request starts

        const token = localStorage.getItem("token");
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        spinnerToggler(false); //  Turn spinner OFF if the request fails to send
        return Promise.reject(error);
    }
);

api.interceptors.response.use(
    (response) => {
        spinnerToggler(false); //  Turn spinner OFF when successful data arrives
        return response;
    },
    (error) => {
        spinnerToggler(false); // Turn spinner OFF if backend throws an error
        return Promise.reject(error);
    }
);

export default api;