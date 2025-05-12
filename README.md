# Google Docs Clone

A real-time collaborative text editor inspired by Google Docs. This project uses **Spring Boot** for the backend and **JavaFX** for the frontend, leveraging WebSockets and CRDTs to enable multiple users to edit documents simultaneously with live cursor presence.

---

## Table of Contents

1. [Features](#-features)  
2. [Tech Stack](#-tech-stack)  
3. [Prerequisites](#-prerequisites)  

---

##  Features

- **Real-time Collaboration**: Edit documents with multiple users at once.  
- **CRDT Conflict Resolution**: Merge changes seamlessly without conflicts.  
- **WebSocket Communication**: Low-latency updates via a Spring WebSocket server.  
- **Live Cursor Presence**: Track other users’ cursors and selections in real time.  
- **Session Management**: Create or join sessions using unique session IDs.  

---

##  Tech Stack

- **Backend**: Spring Boot, Spring WebSocket  
- **Frontend**: JavaFX, Tyrus WebSocket Client  
- **Data Model**: Sequence CRDT (e.g., RGA/YATA)  
- **Build Tools**: Maven (Backend), Gradle or Maven (Frontend)  
- **Language**: Java 11+  

---

##  Prerequisites

- Java 11 or higher  
- Maven 3.6+ (Backend)  
- Gradle 6+ or Maven (Frontend)  
- Git 2+  


