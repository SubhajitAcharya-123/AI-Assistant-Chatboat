# AI Assistant Chatbot

A full-stack AI-powered chatbot built using Spring Boot, React, Spring AI, Google Gemini, MySQL, Cloudinary, and Vector Search.

## Overview

This project is an AI Assistant designed to support conversational chat, file understanding, image analysis, and retrieval-augmented responses using semantic search.

Users can create multiple chat sessions, upload documents/images, ask questions about uploaded content, and receive AI-generated responses powered by Google's Gemini models.

The application is currently under active development and new capabilities are being added continuously.

---

## Current Features

### Chat System

* Multi-session chat support
* Persistent chat history
* Real-time streaming AI responses using Server-Sent Events (SSE)
* Session-based conversation management

### AI Integration

* Google Gemini integration via Spring AI
* Streaming and non-streaming response generation
* Context-aware conversations using stored chat history

### File Upload Support

* Upload PDF, TXT, DOC, DOCX, PNG, JPG, and JPEG files
* Automatic file storage using Cloudinary
* File metadata persistence
* Uploaded file preview inside chat interface

### Image Understanding

* Multimodal image analysis using Gemini Vision
* Ask questions about uploaded images
* AI-generated responses based on image content

### Document Processing

* Apache Tika integration for document text extraction
* Automatic content parsing from supported files
* Semantic indexing of extracted text

### Vector Search

* Embedding generation using Gemini Embedding Model
* Semantic similarity search
* Cross-session retrieval of previously indexed content
* Retrieval-Augmented Generation (RAG) workflow

### Modern Chat UI

* Markdown rendering support
* File attachment previews
* Image preview modal
* PDF preview modal
* Responsive chat interface

---

## Tech Stack

### Backend

* Java 17+
* Spring Boot
* Spring AI
* Spring Data JPA
* Spring WebFlux
* MySQL
* Apache Tika
* Cloudinary

### AI Models

* Gemini 2.5 Flash
* Gemini Embedding Model

### Frontend

* React
* Axios
* React Markdown
* CSS

---

## Project Architecture

Frontend (React)
↓
Spring Boot REST API
↓
Spring AI
↓
Google Gemini

Additional Services:

* Cloudinary (File Storage)
* MySQL (Chat Persistence)
* Vector Search Layer (Semantic Retrieval)

---

## Example Workflow

1. User uploads a PDF.
2. Document text is extracted using Apache Tika.
3. Extracted content is converted into embeddings.
4. Embeddings are indexed into the vector search layer.
5. User asks a question.
6. Relevant content is retrieved using similarity search.
7. Gemini receives both the query and retrieved context.
8. AI generates a contextual response.

---

## Features In Progress

The following features are currently being implemented:

* PostgreSQL + PGVector migration
* Persistent vector database storage
* Authentication & Authorization
* Tool Calling
* Docker containerization
* Production deployment
* User memory management
* Advanced RAG pipeline
* Image vector search
* File vector search optimization

---

## Learning Objectives

This project focuses on practical experience with:

* Retrieval-Augmented Generation (RAG)
* Vector Databases
* Large Language Models (LLMs)
* Spring AI
* Full Stack Development
* Cloud File Storage
* Streaming APIs
* AI Application Architecture

---

## Project Status

🚧 Active Development

This project is continuously evolving as new AI capabilities and production-grade features are added.

Current focus:

* PGVector integration
* Persistent memory system
* Docker deployment
* Authentication system

---

## Author

Subhajit Acharya


