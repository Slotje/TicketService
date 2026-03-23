# Technisch Ontwerp - TicketService

**Versie:** 1.0
**Datum:** 2026-03-23
**Project:** TicketService - Multi-tenant Ticketverkoop Platform

---

## 1. Architectuuroverzicht

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
│   Frontend   │────▶│     Backend      │────▶│  PostgreSQL  │
│  Angular 19  │     │  Quarkus 3.17    │     │     16       │
│  + Nginx     │     │  Java 21         │     │              │
└──────────────┘     └──────────────────┘     └──────────────┘
     :80               :8080                      :5432
```

| Laag | Technologie | Doel |
|------|------------|------|
| Frontend | Angular 19.2, PrimeNG, Nginx | SPA met UI-componenten |
| Backend | Quarkus 3.17.8, Java 21, Hibernate Panache | REST API, business logic |
| Database | PostgreSQL 16 (prod), H2 (dev/test) | Persistentie |
| Deployment | Docker Compose, Kubernetes, GitHub Actions | CI/CD en hosting |

---

## 2. Backend Architectuur

### 2.1 Lagenstructuur

```
Resource (REST)  →  Service (Business Logic)  →  Entity (JPA/Panache)  →  Database
    ↕                      ↕
   DTO                  Exception
```

### 2.2 Package-structuur

```
nl.ticketservice/
├── config/         → Configuratie, sample data loader
├── dto/            → Data Transfer Objects (request/response)
├── entity/         → JPA-entiteiten + enums
├── exception/      → Custom exceptions + global handler
├── resource/       → JAX-RS REST endpoints
└── service/        → Business logic
```

### 2.3 REST API Endpoints

| Resource | Pad | Methoden | Authenticatie |
|----------|-----|----------|---------------|
| EventResource | `/api/events` | GET, POST, PUT, DELETE | Admin/Klant (schrijven) |
| OrderResource | `/api/orders` | GET, POST | Publiek (aanmaken) |
| CustomerResource | `/api/customers` | GET, POST, PUT, DELETE | Admin |
| CustomerAuthResource | `/api/customer/auth` | POST | Publiek |
| UserAuthResource | `/api/user/auth` | POST | Publiek |
| AdminAuthResource | `/api/admin/auth` | POST | Publiek (setup), Auth (overig) |
| AuthResource | `/api/auth` | POST | Publiek (login), Admin (beheer) |
| ImageResource | `/api/images` | GET, POST | Auth (upload), Publiek (download) |

---

## 3. Datamodel

### 3.1 Entity Relatie Diagram

```
Customer (1) ──────< Event (1) ──────< TicketOrder (1) ──────< Ticket
                         │                                        │
                         └────────────────────────────────────────┘
                                    (1) ──────< (many)

AdminUser       ScannerUser       User
(standalone)    (standalone)      (standalone)
```

### 3.2 Entiteiten

#### Customer
| Veld | Type | Constraint |
|------|------|-----------|
| id | Long | PK, auto-generated |
| companyName | String(100) | NOT NULL, UNIQUE |
| email | String(255) | UNIQUE |
| slug | String(100) | UNIQUE |
| passwordHash | String(255) | |
| inviteToken | String(255) | |
| primaryColor | String(7) | Hex kleurcode |
| logoUrl | String(500) | |
| active | boolean | |
| createdAt / updatedAt | LocalDateTime | |

#### Event
| Veld | Type | Constraint |
|------|------|-----------|
| id | Long | PK |
| customer | Customer | FK, ManyToOne |
| name | String(200) | NOT NULL |
| description | String (TEXT) | |
| eventDate / endDate | LocalDateTime | NOT NULL / nullable |
| location | String(300) | NOT NULL |
| maxTickets | int | NOT NULL |
| physicalTickets | int | |
| ticketPrice | BigDecimal(10,2) | |
| serviceFee | BigDecimal(10,2) | |
| ticketsSold / ticketsReserved | int | |
| physicalTicketsSold | int | |
| status | EventStatus | DRAFT, PUBLISHED, SOLD_OUT, CANCELLED, COMPLETED |
| imageUrl | String(500) | |

#### TicketOrder
| Veld | Type | Constraint |
|------|------|-----------|
| id | Long | PK |
| event | Event | FK, ManyToOne |
| orderNumber | String(20) | UNIQUE |
| buyerFirstName | String(100) | |
| buyerEmail | String(255) | |
| quantity | int | |
| totalPrice | BigDecimal(10,2) | |
| serviceFeePerTicket | BigDecimal(10,2) | |
| totalServiceFee | BigDecimal(10,2) | |
| status | OrderStatus | RESERVED, CONFIRMED, CANCELLED, EXPIRED |
| expiresAt | LocalDateTime | |
| emailSent | boolean | |
| emailRetryCount | int | |

#### Ticket
| Veld | Type | Constraint |
|------|------|-----------|
| id | Long | PK |
| event | Event | FK, ManyToOne |
| order | TicketOrder | FK, ManyToOne (nullable voor fysiek) |
| ticketCode | String(20) | UNIQUE |
| qrCodeData | String(255) | UNIQUE |
| ticketType | TicketType | ONLINE, PHYSICAL |
| scanned | boolean | |
| scannedAt | LocalDateTime | |

#### User / AdminUser / ScannerUser
Standalone entiteiten met: `id`, `email/username`, `passwordHash`, `displayName`, `active`, `createdAt`.

---

## 4. Beveiliging

### 4.1 Authenticatie

- **Methode:** Custom HMAC-gebaseerde tokens (vergelijkbaar met JWT)
- **Token-verloop:** 24 uur (configureerbaar via `AUTH_TOKEN_EXPIRY_HOURS`)
- **Geheime sleutels:** Via environment variabelen (`AUTH_SECRET`, `QR_SECRET`)
- **Wachtwoorden:** Gehashed opgeslagen (BCrypt/Argon2)

### 4.2 Autorisatie

| Endpoint-groep | Admin | Klant | Gebruiker | Scanner | Publiek |
|----------------|-------|-------|-----------|---------|---------|
| Klantbeheer | RW | - | - | - | - |
| Evenementen (schrijven) | RW | Eigen | - | - | - |
| Evenementen (lezen) | Alle | Eigen | Gepubliceerd | - | Gepubliceerd |
| Bestellingen | Alle | Eigen events | Eigen | - | Aanmaken |
| Scannen | Ja | - | - | Ja | - |
| Afbeeldingen uploaden | Ja | Ja | - | - | - |

### 4.3 QR-code Beveiliging

```
QR-data = ticketCode + "|" + HMAC-SHA256(ticketCode, QR_SECRET)
```

Bij scannen wordt de HMAC herberekend en vergeleken om vervalsing te detecteren.

---

## 5. Servicekosten Berekening

```
Totale servicekosten = serviceFee × maxTickets
Effectieve fee per online ticket = totale servicekosten / aantal online tickets
```

Fysieke tickets zijn vrijgesteld van servicekosten; de kosten worden verdeeld over online tickets.

---

## 6. Reserveringssysteem

```
Bestelling aangemaakt → status: RESERVED, expiresAt: now + 10 min
  ↓
ReservationCleanupService (elke 60s):
  - Vind orders waar status=RESERVED en expiresAt < now
  - Zet status op EXPIRED
  - Verlaag ticketsReserved op het evenement
  - Geef tickets vrij
  ↓
Bevestiging ontvangen → status: CONFIRMED
  - ticketsReserved --, ticketsSold ++
```

---

## 7. Frontend Architectuur

### 7.1 Componentstructuur

```
app/
├── pages/
│   ├── home/           → Publieke pagina's (eventlijst, detail, bestelling)
│   ├── customer/       → Klant-dashboard en login
│   ├── admin/          → Admin-dashboard en beheer
│   └── scanner/        → QR-scanner met camera
├── services/           → API-communicatie en auth-state
├── guards/             → Route-bescherming per rol
├── interceptors/       → HTTP auth-header injectie
└── models/             → TypeScript interfaces
```

### 7.2 Routing (Lazy-loaded)

| Pad | Component | Auth |
|-----|-----------|------|
| `/` | Home (eventlijst) | Nee |
| `/event/:id` | Event detail + kopen | Nee |
| `/order/:orderNumber` | Bestelbevestiging | Nee |
| `/my-tickets` | Mijn tickets | Gebruiker |
| `/klant/dashboard` | Klant-dashboard | Klant |
| `/klant/:slug` | Publieke landingspagina | Nee |
| `/admin` | Admin-dashboard | Admin |
| `/admin/customers` | Klantbeheer | Admin |
| `/admin/events` | Evenementbeheer | Admin |
| `/scan` | QR-scanner | Scanner |

### 7.3 Technologiestack

| Library | Versie | Doel |
|---------|--------|------|
| Angular | 19.2 | SPA framework |
| PrimeNG | 19.1.4 | UI-componentenbibliotheek |
| html5-qrcode | 2.3.8 | QR-code scanning via camera |
| RxJS | 7.8 | Reactive programming |
| AOS | 2.3.4 | Scroll-animaties |

---

## 8. Deployment

### 8.1 Docker Compose (Ontwikkeling)

```yaml
Services:
  - postgres:16     → Poort 5432
  - backend         → Poort 8080 (Quarkus)
  - frontend        → Poort 80 (Nginx → Angular)
  - cloudflared     → Tunnel voor externe toegang
```

### 8.2 Kubernetes (Productie)

| Component | Replicas | Resources (mem) | HPA |
|-----------|----------|-----------------|-----|
| Backend | 2 | 256Mi - 512Mi | 2-10 pods (CPU/mem) |
| Frontend | 2 | 64Mi - 128Mi | 2-10 pods |
| PostgreSQL | 1 | StatefulSet | Nee |

**Namespace:** `ticketservice`
**Ingress:** Nginx met max body size 10MB (voor PDF-uploads)
**Update-strategie:** Rolling update (maxUnavailable: 0)

### 8.3 CI/CD (GitHub Actions)

```
Push/PR → Backend tests (JDK 21 + PostgreSQL 16)
       → Frontend build (Node 20)
       → Deploy (alleen main branch)
```

---

## 9. Configuratie (Environment Variabelen)

| Variabele | Standaard | Beschrijving |
|-----------|-----------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL poort |
| `DB_NAME` | ticketservice | Database naam |
| `DB_USERNAME` | ticketservice | Database gebruiker |
| `DB_PASSWORD` | ticketservice | Database wachtwoord |
| `AUTH_SECRET` | (dev default) | Geheime sleutel voor tokens |
| `QR_SECRET` | (dev default) | Geheime sleutel voor QR-codes |
| `MAIL_HOST` | smtp.example.com | SMTP server |
| `MAIL_PORT` | 465 | SMTP poort |
| `MAIL_FROM` | noreply@ticketservice.nl | Afzender e-mail |
| `CORS_ORIGINS` | http://localhost | Toegestane origins |
| `APP_BASE_URL` | http://localhost:80 | Basis-URL voor e-maillinks |

---

## 10. Externe Afhankelijkheden

| Dependency | Versie | Doel |
|-----------|--------|------|
| Quarkus | 3.17.8 | Backend framework |
| Hibernate Panache | (via BOM) | ORM |
| ZXing | 3.5.3 | QR-code generatie |
| iText | 8.0.5 | PDF-generatie |
| Quarkus Mailer | (via BOM) | E-mailverzending |
| PostgreSQL JDBC | (via BOM) | Database driver |
| H2 | (via BOM) | In-memory DB (dev/test) |
