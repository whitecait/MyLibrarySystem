# MyLibrarySystem — Full-Stack Library Management System

A full-stack library management system with dual-role support (admin & reader),
covering the complete lifecycle of book borrowing, returning, renewal, and fine management.

```
User Browser → Vue 3 Frontend (Vite) → Spring Boot Backend → MySQL
                                            ↑
                                    Docker Compose (one-command startup)
```

## Features

| Module              | Description                                                     |
| ------------------- | --------------------------------------------------------------- |
| **Auth**            | JWT-based stateless authentication for admin & reader roles     |
| **Book Management** | CRUD, category management, cover image upload, paginated search |
| **Borrowing**       | Apply → Approve → Borrow → Return → Renewal workflow            |
| **Fine System**     | Auto-calculated overdue fines, payment tracking                 |
| **Announcements**   | CRUD for library news/notices                                   |
| **Dashboard**       | Admin statistics: total counts, category grouping               |
| **Messages**        | Reader feedback submission & admin reply                        |

## Tech Stack

| Layer    | Technology                       |
| -------- | -------------------------------- |
| Frontend | Vue 3, Vite, Element Plus, Axios |
| Backend  | Spring Boot 3.5, Spring Data JPA |
| Database | MySQL 8.0                        |
| Auth     | JWT (jjwt)                       |
| API Docs | SpringDoc OpenAPI (Swagger)      |
| Testing  | JUnit 5, MockMvc                 |
| Build    | Maven (backend), Vite (frontend) |
| Deploy   | Docker Compose                   |

## Project Structure

```
MyLibrarySystem/
├── library-server/        # Spring Boot backend
├── library-frontend/      # Vue 3 frontend
├── docs/                  # Design docs & references
├── Logs/                  # Runtime logs
├── docker-compose.yml     # One-command deploy
├── README.md              # This file
└── .gitignore
```

## Quick Start

### Prerequisites

- JDK 17+
- Node.js 18+
- MySQL 8.0
- Maven 3.9+

### Development

```bash
# Backend
cd library-server
mvn spring-boot:run
# → http://localhost:8080/swagger-ui.html

# Frontend
cd library-frontend
npm install
npm run dev
# → http://localhost:5173
```

### Docker Deployment

```bash
docker compose up -d
# → http://localhost
```

## API Overview

Full API documentation available at `http://localhost:8080/swagger-ui.html` after starting the backend.

| Endpoint Group | Description                                  |
| -------------- | -------------------------------------------- |
| `/users/*`     | Admin login & management                     |
| `/duzhe/*`     | Reader registration, login, profile          |
| `/book/*`      | Book CRUD & paginated search                 |
| `/category/*`  | Category management                          |
| `/borrow/*`    | Borrow/return/renewal application & approval |
| `/penalty/*`   | Fine query & payment                         |
| `/news/*`      | Announcement CRUD                            |
| `/messages/*`  | Reader messages & admin reply                |
| `/file/upload` | Cover image upload                           |
| `/common/*`    | Dashboard statistics                         |
| `/remind/*`    | Pending count & due-date reminders           |

## Status

> 🚧 **Phase 1** — Backend scaffolding in progress.

See [docs/](docs/) for detailed design documents and migration notes.
