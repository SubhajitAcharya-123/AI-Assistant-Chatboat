import axios from "axios";

const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";
const api = axios.create({
    baseURL: API_BASE_URL
});

// A placeholder variable we will wire up in index.js to manage the spinner state
let spinnerToggler = () => {};
let spinnerTimeout = null;

export const setupAxiosInterceptors = (setIsLoading) => {
    spinnerToggler = setIsLoading;
};

api.interceptors.request.use(
    (config) => {
        if (spinnerTimeout) clearTimeout(spinnerTimeout);
        spinnerTimeout = setTimeout(() => {
            spinnerToggler(true);
        }, 1500);

        const token = localStorage.getItem("token");
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        if (spinnerTimeout) clearTimeout(spinnerTimeout);
        spinnerToggler(false); //  Turn spinner OFF if the request fails to send
        return Promise.reject(error);
    }
);

api.interceptors.response.use(
    (response) => {
        if (spinnerTimeout) clearTimeout(spinnerTimeout);
        spinnerToggler(false); //  Turn spinner OFF when successful data arrives
        return response;
    },
    (error) => {
        if (spinnerTimeout) clearTimeout(spinnerTimeout);
        spinnerToggler(false); // Turn spinner OFF if backend throws an error
        return Promise.reject(error);
    }
);

export default api;