# Travyn Backend

> **Trusted Solo Travel Network** — Spring Boot REST API

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?style=flat-square&logo=spring)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Private-red?style=flat-square)]()

---

## Overview

Travyn helps verified solo travelers discover, connect, and co-travel with compatible companions. This repository contains the backend REST API powering authentication, user management, and platform services.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Framework** | Spring Boot 3.2.5 |
| **Language** | Java 17 |
| **Security** | Spring Security + JWT (HS512) + BCrypt(12) |
| **Database** | PostgreSQL 15+ (Neon) / MySQL 8+ (local) |
| **Migrations** | Liquibase |
| **Cache** | Redis 7+ |
| **Email** | Spring Mail (Gmail SMTP) |
| **Docs** | SpringDoc OpenAPI 3.0 (Swagger UI) |
| **Build** | Maven 3.9+ |

## Project Structure

```
backend/
├── src/main/java/com/travyn/
│   ├── TravynApplication.java          # Entry point
│   ├── auth/
│   │   ├── controller/
│   │   │   └── AuthController.java     # Auth REST endpoints
│   │   ├── dto/                        # Request/Response DTOs
│   │   │   ├── AuthResponse.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── RegisterRequest.java
│   │   │   ├── RefreshTokenRequest.java
│   │   │   ├── PasswordResetRequest.java
│   │   │   ├── PasswordResetConfirm.java
│   │   │   └── UserDTO.java
│   │   ├── entity/                     # JPA Entities
│   │   │   ├── User.java
│   │   │   ├── RefreshToken.java
│   │   │   ├── Role.java
│   │   │   └── UserStatus.java
│   │   ├── repository/                 # Spring Data JPA
│   │   │   ├── UserRepository.java
│   │   │   └── RefreshTokenRepository.java
│   │   ├── security/                   # JWT & Security
│   │   │   ├── SecurityConfig.java
│   │   │   ├── JwtUtil.java
│   │   │   └── JwtAuthenticationFilter.java
│   │   └── service/
│   │       └── AuthService.java        # Business logic
│   └── common/
│       ├── aop/
│       │   └── LoggingAspect.java      # Method-level logging
│       ├── config/
│       │   └── AppConfig.java          # ModelMapper, Async
│       ├── dto/
│       │   └── ErrorResponse.java      # Standard error envelope
│       ├── exception/                  # Custom exception hierarchy
│       │   ├── BaseException.java
│       │   ├── GlobalExceptionHandler.java
│       │   ├── DuplicateEmailException.java
│       │   ├── InvalidCredentialsException.java
│       │   ├── EmailNotVerifiedException.java
│       │   ├── AccountLockedException.java
│       │   ├── TokenExpiredException.java
│       │   └── UserNotFoundException.java
│       └── service/
│           └── EmailService.java       # Async HTML email sending
├── src/main/resources/
│   ├── application.properties          # Shared config
│   ├── application-neon.properties     # Neon PostgreSQL (remote)
│   ├── application-local.properties    # Local MySQL
│   └── db/changelog/                   # Liquibase migrations
│       ├── db.changelog-master.yaml
│       ├── 001-create-users-table.yaml
│       └── 002-create-refresh-tokens-table.yaml
└── pom.xml
```

## API Endpoints

### Authentication (`/api/v1/auth`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/register` | Register new user | Public |
| `POST` | `/login` | Login with email/password | Public |
| `POST` | `/refresh` | Refresh access token | Public |
| `POST` | `/logout` | Revoke refresh token | Public |
| `GET` | `/verify-email?token=` | Verify email address | Public |
| `POST` | `/resend-verification` | Resend verification email | Public |
| `POST` | `/password-reset/request` | Request password reset | Public |
| `POST` | `/password-reset/confirm` | Reset password with token | Public |

### Swagger UI

Once running, visit: **http://localhost:8080/swagger-ui.html**

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.9+ (or use included `mvnw.cmd`)
- PostgreSQL 15+ or MySQL 8+

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/kundanpure/Travyn-backend.git
   cd Travyn-backend
   ```

2. **Configure secrets**

   Copy the example config files and fill in your credentials:
   ```bash
   cd src/main/resources
   cp application.properties.example application.properties
   cp application-neon.properties.example application-neon.properties
   cp application-local.properties.example application-local.properties
   ```

   Then edit each file and replace the placeholder values (`YOUR_*`) with your actual credentials.

   > ⚠️ The real `.properties` files are **gitignored** — only `.example` files are committed. Never commit secrets.

3. **Choose a database profile**

   | Profile | Database | Activate |
   |---------|----------|----------|
   | `neon` (default) | Neon PostgreSQL (remote) | `-Dspring-boot.run.profiles=neon` |
   | `local` | MySQL on localhost:3306 | `-Dspring-boot.run.profiles=local` |

   For local MySQL, create the database:
   ```sql
   CREATE DATABASE IF NOT EXISTS travyn_db;
   ```

4. **Run the application**
   ```bash
   # Using Maven wrapper (Windows)
   .\mvnw.cmd spring-boot:run

   # With specific profile
   .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
   ```

5. **Verify it's running**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

## Security Features

- **JWT Authentication** — HS512 signed, 15-min access tokens, 7-day refresh tokens with rotation
- **Brute Force Protection** — Account lockout after 5 failed attempts (15 min)
- **Refresh Token Reuse Detection** — Revokes all tokens on suspicious reuse
- **BCrypt** — Password hashing with cost factor 12
- **CORS** — Configurable allowed origins
- **Anti-Enumeration** — Password reset and resend-verification always return success
- **Secrets Management** — All credentials in gitignored `.properties` files; only `.example` templates committed

## Docker

```bash
cd docker
docker-compose up -d   # Starts PostgreSQL + Redis
```

## License

Private — All rights reserved.
