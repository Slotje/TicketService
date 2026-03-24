# Technisch Ontwerp - TicketService

**Versie:** 3.0
**Datum:** 2026-03-24
**Project:** TicketService - Multi-tenant Ticketverkoop Platform

---

## 1. Architectuuroverzicht

### 1.1 Systeemarchitectuur

```
                         ┌─────────────────┐
                         │   Cloudflared    │
                         │   (Tunnel)       │
                         └────────┬────────┘
                                  │
┌──────────────┐     ┌───────────┴────────┐     ┌──────────────┐
│   Frontend   │────▶│     Backend        │────▶│  PostgreSQL  │
│  Angular 19  │     │  Quarkus 3.17      │     │     16       │
│  + Nginx     │     │  Java 21           │     │   Alpine     │
└──────────────┘     └────────────────────┘     └──────────────┘
     :80               :8080                      :5432
       │                  │
       │                  ├── SMTP Server (e-mail)
       │                  ├── Filesystem (afbeeldingen)
       │                  └── QR/PDF generatie (in-process)
       │
       └── Reverse proxy /api/* → backend:8080
```

### 1.2 Technologie-stack

| Laag | Technologie | Versie | Doel |
|------|------------|--------|------|
| Frontend | Angular, PrimeNG, Nginx | 19.2, 19.1.4, Alpine | SPA met UI-componenten, reverse proxy |
| Backend | Quarkus, Java, Hibernate Panache | 3.17.8, 21, via BOM | REST API, business logic, ORM |
| Database | PostgreSQL (prod), H2 (dev/test) | 16, via BOM | Persistentie, transacties |
| PDF | iText | 8.0.5 | Ticket-PDF generatie (online + fysiek) |
| QR | ZXing | 3.5.3 | QR-code generatie met HMAC |
| E-mail | Quarkus Mailer | via BOM | SMTP-gebaseerde e-mailverzending |
| Deployment | Docker Compose, Kubernetes | - | Containerisatie en orchestratie |
| CI/CD | GitHub Actions | - | Automatisch bouwen, testen en deployen |
| Tunnel | Cloudflared | latest | Externe toegang via Cloudflare tunnel |

### 1.3 Ontwikkelomgevingen

| Omgeving | Database | Modus | Gegevens |
|----------|----------|-------|----------|
| Development | H2 (in-memory) | drop-and-create | SampleDataLoader + StartupService laden testgegevens |
| Test (CI) | PostgreSQL 16 | drop-and-create | Lege database, geen seed data |
| Productie | PostgreSQL 16 | update | Geen automatische data, handmatige admin-setup |

---

## 2. Backend Architectuur

### 2.1 Lagenstructuur

```
Resource (REST)  →  Service (Business Logic)  →  Entity (JPA/Panache)  →  Database
    ↕                      ↕                          ↕
   DTO                  Exception                    Enum
```

**Verantwoordelijkheden per laag:**
- **Resource:** Ontvangen/valideren van HTTP-requests, autorisatiecontrole, delegeren naar service-laag, HTTP-response samenstellen.
- **Service:** Business logic, berekeningen, cross-entity operaties, e-mailverzending, PDF/QR-generatie.
- **Entity:** JPA-mapping, database-constraints, lifecycle callbacks (@PrePersist), relaties.
- **DTO:** Validatie-annotaties (@NotBlank, @Email, @Min, @Max), request/response structuur.
- **Exception:** Custom TicketServiceException met statuscode, GlobalExceptionHandler voor JSON-responses, ConstraintViolationExceptionMapper voor validatiefouten.

### 2.2 Package-structuur

```
nl.ticketservice/
├── config/         → SampleDataLoader (dev seed data)
├── dto/            → 16 DTO-records (EventDTO, OrderRequestDTO, OrderResponseDTO, TicketDTO,
│                     CustomerDTO, BuyerDetailsDTO, LoginDTO, LoginResponseDTO, UserLoginDTO,
│                     UserResponseDTO, UserUpdateDTO, RegisterDTO, ScannerUserDTO,
│                     CreateScannerUserDTO, TicketSalesDTO, TicketCategoryDTO)
├── entity/         → 8 entiteiten (Customer, Event, TicketOrder, Ticket, TicketCategory,
│                     User, AdminUser, ScannerUser) + 3 enums (EventStatus, OrderStatus, TicketType)
├── exception/      → TicketServiceException, GlobalExceptionHandler,
│                     ConstraintViolationExceptionMapper
├── resource/       → 8 JAX-RS resources (Event, Order, Customer, CustomerAuth, UserAuth,
│                     AdminAuth, Auth, Image)
└── service/        → 16 services (Order, Event, Customer, CustomerAuth, UserAuth, AdminAuth,
                      Auth, QrCode, Pdf, PhysicalTicket, PhysicalTicketPdf, Email,
                      EmailRetry, ReservationCleanup, Startup, ImageLoader)
```

### 2.3 Service-afhankelijkheden

```
OrderService ─────────────→ QrCodeService
    │                            ↑
    └──→ EmailService            │
              ↑                  │
CustomerService ──→ CustomerAuthService
              ↑
PhysicalTicketService ──→ PhysicalTicketPdfService ──→ QrCodeService
              │
              └──→ EmailService

PdfService ──→ QrCodeService
    │
    └──→ ImageLoaderService (afbeeldingen laden voor PDF)

PhysicalTicketPdfService ──→ ImageLoaderService

EmailRetryService ──→ EmailService
ReservationCleanupService (standalone, scheduled)
StartupService ──→ AuthService, AdminAuthService
```

### 2.4 Achtergrondprocessen (Scheduled Services)

| Service | Interval | Functie |
|---------|----------|---------|
| ReservationCleanupService | 60 seconden | Verloopt RESERVED orders waarvan expiresAt < now. Geeft tickets vrij. |
| EmailRetryService | 120 seconden | Herprobeert e-mailverzending voor CONFIRMED orders met emailSent=false (max 5 pogingen). |

### 2.5 REST API Endpoints (volledig)

#### EventResource (`/api/events`)

| Methode | Pad | Auth | Request | Response | Beschrijving |
|---------|-----|------|---------|----------|-------------|
| GET | `/api/events` | Admin | - | `List<EventDTO>` | Alle evenementen |
| GET | `/api/events/published` | Publiek | - | `List<EventDTO>` | Gepubliceerde/uitverkochte met toekomstige datum |
| GET | `/api/events/{id}` | Publiek | - | `EventDTO` | Enkel evenement |
| GET | `/api/events/customer/{customerId}` | Publiek | - | `List<EventDTO>` | Evenementen van klant |
| POST | `/api/events` | Admin | `EventDTO` | `EventDTO` (201) | Nieuw evenement |
| PUT | `/api/events/{id}` | Admin | `EventDTO` | `EventDTO` | Evenement bijwerken |
| PATCH | `/api/events/{id}/status` | Admin | `{"status":"..."}` | `EventDTO` | Status wijzigen |
| DELETE | `/api/events/{id}` | Admin | - | 204 | Evenement verwijderen |
| GET | `/api/events/my` | Klant | - | `List<EventDTO>` | Eigen evenementen |
| POST | `/api/events/my` | Klant | `EventDTO` | `EventDTO` (201) | Eigen evenement aanmaken |
| PUT | `/api/events/my/{id}` | Klant (eigenaar) | `EventDTO` | `EventDTO` | Eigen evenement bijwerken |
| PATCH | `/api/events/my/{id}/status` | Klant (eigenaar) | `{"status":"..."}` | `EventDTO` | Eigen evenement status |
| DELETE | `/api/events/my/{id}` | Klant (eigenaar) | - | 204 | Eigen evenement verwijderen |
| POST | `/api/events/{id}/physical-tickets/generate` | Admin | - | PDF (binary) | Fysieke tickets genereren |
| GET | `/api/events/{id}/physical-tickets/pdf` | Admin | - | PDF (binary) | Fysieke tickets PDF downloaden |
| POST | `/api/events/{id}/physical-tickets/sell` | Admin | `{"quantity":N}` | `EventDTO` | Fysieke verkoop registreren |
| PUT | `/api/events/{id}/physical-tickets/sold-count` | Admin | `{"count":N}` | `EventDTO` | Verkoopaantal corrigeren |
| GET | `/api/events/{id}/sales` | Admin | - | `TicketSalesDTO` | Verkooprapportage |
| POST | `/api/events/my/{id}/physical-tickets/generate` | Klant (eigenaar) | - | PDF (binary) | (zelfde als admin, eigen events) |
| GET | `/api/events/my/{id}/physical-tickets/pdf` | Klant (eigenaar) | - | PDF (binary) | |
| POST | `/api/events/my/{id}/physical-tickets/sell` | Klant (eigenaar) | `{"quantity":N}` | `EventDTO` | |
| PUT | `/api/events/my/{id}/physical-tickets/sold-count` | Klant (eigenaar) | `{"count":N}` | `EventDTO` | |
| GET | `/api/events/my/{id}/sales` | Klant (eigenaar) | - | `TicketSalesDTO` | |
| GET | `/api/events/{id}/categories` | Publiek | - | `List<TicketCategoryDTO>` | Categorieën van evenement |
| POST | `/api/events/{id}/categories` | Admin | `TicketCategoryDTO` | `TicketCategoryDTO` | Categorie aanmaken |
| PUT | `/api/events/{id}/categories/{categoryId}` | Admin | `TicketCategoryDTO` | `TicketCategoryDTO` | Categorie bijwerken |
| DELETE | `/api/events/{id}/categories/{categoryId}` | Admin | - | 204 | Categorie verwijderen |
| POST | `/api/events/my/{id}/categories` | Klant (eigenaar) | `TicketCategoryDTO` | `TicketCategoryDTO` | Eigen categorie aanmaken |
| PUT | `/api/events/my/{id}/categories/{categoryId}` | Klant (eigenaar) | `TicketCategoryDTO` | `TicketCategoryDTO` | Eigen categorie bijwerken |
| DELETE | `/api/events/my/{id}/categories/{categoryId}` | Klant (eigenaar) | - | 204 | Eigen categorie verwijderen |

#### OrderResource (`/api/orders`)

| Methode | Pad | Auth | Request | Response | Beschrijving |
|---------|-----|------|---------|----------|-------------|
| GET | `/api/orders/{id}` | Publiek | - | `OrderResponseDTO` | Bestelling ophalen |
| GET | `/api/orders/number/{orderNumber}` | Publiek | - | `OrderResponseDTO` | Bestelling via ordernummer |
| GET | `/api/orders/email/{email}` | Publiek | - | `List<OrderResponseDTO>` | Bestellingen via e-mail |
| POST | `/api/orders` | Publiek | `OrderRequestDTO` | `OrderResponseDTO` | Nieuwe bestelling (RESERVED) |
| PUT | `/api/orders/{id}/details` | Publiek | `BuyerDetailsDTO` | `OrderResponseDTO` | Adresgegevens bijwerken |
| POST | `/api/orders/{id}/confirm` | Publiek | - | `OrderResponseDTO` | Bestelling bevestigen |
| POST | `/api/orders/{id}/cancel` | Publiek | - | `OrderResponseDTO` | Bestelling annuleren |
| GET | `/api/orders/{id}/pdf` | Publiek | - | PDF (binary) | Ticket-PDF downloaden |
| GET | `/api/orders/ticket/{qrCodeData}/qr` | Publiek | - | PNG (binary) | QR-code afbeelding |
| GET | `/api/orders/event/{eventId}` | Admin | - | `List<OrderResponseDTO>` | Bestellingen per evenement |
| POST | `/api/orders/scan/{qrCodeData}?eventId={id}` | Scanner | - | `TicketDTO` | Ticket scannen |

#### CustomerResource (`/api/customers`)

| Methode | Pad | Auth | Request | Response | Beschrijving |
|---------|-----|------|---------|----------|-------------|
| GET | `/api/customers` | Admin | - | `List<CustomerDTO>` | Alle klanten |
| GET | `/api/customers/{id}` | Admin | - | `CustomerDTO` | Klant ophalen |
| GET | `/api/customers/slug/{slug}` | Publiek | - | `CustomerDTO` | Klant via slug |
| POST | `/api/customers` | Admin | `CustomerDTO` | `CustomerDTO` (201) | Klant aanmaken + uitnodiging |
| PUT | `/api/customers/{id}` | Admin | `CustomerDTO` | `CustomerDTO` | Klant bijwerken |
| DELETE | `/api/customers/{id}` | Admin | - | 204 | Klant verwijderen |
| POST | `/api/customers/{id}/resend-invite` | Admin | - | 200 | Uitnodiging opnieuw sturen |

#### Authenticatie Resources

**CustomerAuthResource (`/api/customer/auth`)**

| Methode | Pad | Auth | Beschrijving |
|---------|-----|------|-------------|
| POST | `/login` | Publiek | Klant-login → token + klantgegevens |
| POST | `/set-password` | Publiek (invite token) | Activatie → token + klantgegevens |
| GET | `/verify` | Klant | Token valideren → klantgegevens |
| GET | `/invite/{token}` | Publiek | Uitnodiging controleren → bedrijfsnaam + e-mail |
| POST | `/forgot-password` | Publiek | Reset-link versturen |
| POST | `/reset-password` | Publiek (reset token) | Wachtwoord resetten |
| PUT | `/branding` | Klant | Huisstijl bijwerken (logo, kleuren, website) |
| GET | `/branding/preview-ticket` | Klant | Voorbeeld-ticket PDF downloaden met huidige huisstijl |

**UserAuthResource (`/api/user/auth`)**

| Methode | Pad | Auth | Beschrijving |
|---------|-----|------|-------------|
| POST | `/register` | Publiek | Registratie → token + gebruikersgegevens |
| POST | `/login` | Publiek | Login → token + gebruikersgegevens |
| GET | `/verify` | Gebruiker | Token valideren → gebruikersgegevens |
| PUT | `/profile` | Gebruiker | Profiel bijwerken (naam, telefoon, adres) |
| POST | `/forgot-password` | Publiek | Reset-link versturen |
| POST | `/reset-password` | Publiek (reset token) | Wachtwoord resetten |

**AdminAuthResource (`/api/admin/auth`)**

| Methode | Pad | Auth | Beschrijving |
|---------|-----|------|-------------|
| GET | `/setup` | Publiek | Check of setup nodig is (needsSetup: boolean) |
| POST | `/setup` | Publiek (eenmalig) | Eerste admin aanmaken |
| POST | `/login` | Publiek | Admin-login → token |
| GET | `/verify` | Admin | Token valideren |

**AuthResource (`/api/auth`) — Scanner**

| Methode | Pad | Auth | Beschrijving |
|---------|-----|------|-------------|
| POST | `/login` | Publiek | Scanner-login → token |
| GET | `/verify` | Scanner | Token valideren |
| GET | `/users` | Admin | Alle scanner-gebruikers ophalen |
| POST | `/users` | Admin | Scanner-gebruiker aanmaken |
| DELETE | `/users/{id}` | Admin | Scanner-gebruiker verwijderen |
| PATCH | `/users/{id}/toggle` | Admin | Actief-status wisselen |

#### ImageResource (`/api/images`)

| Methode | Pad | Auth | Beschrijving |
|---------|-----|------|-------------|
| POST | `/upload` | Admin of Klant | Afbeelding uploaden (multipart, max 5MB) → `{"url": "/api/images/{filename}"}` |
| GET | `/{filename}` | Publiek | Afbeelding ophalen (cache: 24 uur, path-traversal beveiliging) |

---

## 3. Datamodel

### 3.1 Entity Relatie Diagram

```
Customer (1) ──────< Event (1) ──────< TicketOrder (1) ──────< Ticket
   │                    │                                        │
   │ companyName        │ customer_id (FK)                       │ order_id (FK, nullable)
   │ slug (unique)      │                                        │ event_id (FK)
   │ email (unique)     │                                        │ ticket_category_id (FK, nullable)
   │                    ├────────────────────────────────────────┘
   │                    │          (1) Event ──────< (many) Ticket
   │                    │
   │                    └──────< TicketCategory
   │                               │ event_id (FK)
   │                               │ name, price, maxTickets
   │                               │ validDate, validEndDate
   │                               │ startTime, endTime
   │
   └── events (cascade ALL, orphanRemoval)

AdminUser            ScannerUser           User
(standalone)         (standalone)          (standalone)
  │ email (unique)     │ username (unique)   │ email (unique)
  │ active             │ active              │ firstName/lastName
  │ displayName        │ displayName         │ phone, street, city
```

### 3.2 Entiteiten

#### Customer

| Veld | Type | Constraint | Validatie | Beschrijving |
|------|------|-----------|-----------|-------------|
| id | Long | PK, auto-generated | - | Primaire sleutel (PanacheEntity) |
| companyName | String(100) | NOT NULL | @NotBlank, @Size(min=2, max=100) | Bedrijfsnaam |
| contactPerson | String(100) | NOT NULL | @NotBlank, @Size(min=2, max=100) | Naam contactpersoon |
| email | String(255) | NOT NULL, UNIQUE | @NotBlank, @Email | E-mailadres (lowercase genormaliseerd) |
| phone | String(20) | nullable | @Size(max=20) | Telefoonnummer |
| logoUrl | String(500) | nullable | @Size(max=500) | URL naar geüploade logo-afbeelding |
| primaryColor | String(7) | nullable | @Size(max=7), regex `^#[0-9a-fA-F]{6}$` | Hex kleurcode (bijv. #E74C3C) |
| secondaryColor | String(7) | nullable | @Size(max=7) | Secundaire hex kleurcode |
| website | String(200) | nullable | @Size(max=200) | Website-URL |
| slug | String(100) | UNIQUE | @Size(max=100) | Auto-gegenereerde URL-slug |
| passwordHash | String(255) | nullable | - | SHA-256 hash van wachtwoord + secret |
| inviteToken | String(255) | nullable | - | Activatietoken (gewist na gebruik) |
| inviteTokenExpiry | LocalDateTime | nullable | - | Verloopdatum uitnodiging (7 dagen) |
| active | boolean | NOT NULL | default: true | Actief-status |
| createdAt | LocalDateTime | NOT NULL, immutable | @PrePersist: now() | Aanmaakdatum |
| updatedAt | LocalDateTime | nullable | @PrePersist/@PreUpdate: now() | Laatste wijzigingsdatum |
| events | List\<Event\> | OneToMany | cascade=ALL, orphanRemoval=true | Evenementen van deze klant |

**Slug-generatie algoritme:**
```
1. bedrijfsnaam → NFD normalisatie (diacrieten verwijderen)
2. → lowercase
3. → speciale tekens vervangen door koppelteken
4. → opeenvolgende koppeltekens samenvoegen
5. → koppeltekens aan begin/einde verwijderen
6. Als slug al bestaat: teller toevoegen (bijv. "festival-events-2")
```

#### Event

| Veld | Type | Constraint | Validatie | Beschrijving |
|------|------|-----------|-----------|-------------|
| id | Long | PK | - | Primaire sleutel |
| name | String(200) | NOT NULL | @NotBlank, @Size(min=2, max=200) | Evenementnaam |
| description | String(2000) | nullable | @Size(max=2000) | Beschrijving |
| eventDate | LocalDateTime | NOT NULL | @NotNull, @Future | Startdatum en -tijd |
| endDate | LocalDateTime | nullable | - | Einddatum (optioneel, voor meerdaagse events) |
| location | String(300) | NOT NULL | @NotBlank, @Size(max=300) | Locatienaam |
| address | String(500) | nullable | @Size(max=500) | Volledig adres |
| maxTickets | Integer | NOT NULL | @NotNull, @Min(1), @Max(100000) | Maximaal aantal tickets (online + fysiek) |
| physicalTickets | Integer | NOT NULL | @Min(0), default: 0 | Aantal fysieke tickets |
| ticketPrice | BigDecimal(10,2) | NOT NULL | @NotNull, @DecimalMin("0.00") | Prijs per ticket in euro's |
| serviceFee | BigDecimal(10,2) | NOT NULL | @NotNull, @DecimalMin("0.00"), default: 0 | Servicekosten per ticket |
| maxTicketsPerOrder | Integer | NOT NULL | @Min(1), @Max(10), default: 10 | Maximum tickets per bestelling |
| ticketsSold | Integer | NOT NULL | default: 0 | Aantal online verkochte tickets |
| ticketsReserved | Integer | NOT NULL | default: 0 | Aantal gereserveerde tickets |
| physicalTicketsSold | Integer | NOT NULL | default: 0 | Aantal verkochte fysieke tickets |
| physicalTicketsGenerated | boolean | NOT NULL | default: false | Of fysieke tickets zijn gegenereerd |
| showAvailability | boolean | NOT NULL | default: true | Of beschikbare aantallen zichtbaar zijn voor eindgebruikers |
| imageUrl | String(500) | nullable | @Size(max=500) | URL naar evenementafbeelding |
| status | EventStatus | NOT NULL | EnumType.STRING, default: DRAFT | Evenementstatus |
| customer | Customer | NOT NULL (FK) | @ManyToOne(LAZY) | Eigenaar (organisator) |
| orders | List\<TicketOrder\> | OneToMany | cascade=ALL | Bestellingen voor dit event |
| ticketCategories | List\<TicketCategory\> | OneToMany | cascade=ALL, orphanRemoval=true, @OrderBy("sortOrder, id") | Ticketcategorieën van dit event |
| createdAt | LocalDateTime | NOT NULL, immutable | @PrePersist: now() | Aanmaakdatum |
| updatedAt | LocalDateTime | nullable | @PrePersist/@PreUpdate: now() | Laatste wijzigingsdatum |

**Berekende waarden (in EventDTO):**
```
onlineTickets        = maxTickets - physicalTickets
availableTickets     = maxTickets - ticketsSold - ticketsReserved
availablePhysical    = physicalTickets - physicalTicketsSold
totalSold            = ticketsSold + physicalTicketsSold
effectiveServiceFee  = (serviceFee × maxTickets) / onlineTickets
showAvailability     = boolean (of beschikbare aantallen getoond worden)
ticketCategories     = List<TicketCategoryDTO> (categorieën van het event)
```

#### TicketCategory

| Veld | Type | Constraint | Validatie | Beschrijving |
|------|------|-----------|-----------|-------------|
| id | Long | PK | - | Primaire sleutel (PanacheEntity) |
| event | Event | NOT NULL (FK) | @ManyToOne(LAZY) | Gekoppeld evenement |
| name | String(200) | NOT NULL | @NotBlank, @Size(max=200) | Categorienaam |
| description | String(500) | nullable | @Size(max=500) | Beschrijving van de categorie |
| price | BigDecimal(10,2) | NOT NULL | @NotNull, @DecimalMin("0.00") | Prijs per ticket in deze categorie |
| serviceFee | BigDecimal(10,2) | nullable | @DecimalMin("0.00") | Servicekosten per ticket (optioneel) |
| maxTickets | Integer | NOT NULL | @Min(0), default: 0 | Maximaal aantal tickets (0 = gebruikt event-capaciteit) |
| ticketsSold | Integer | NOT NULL | default: 0 | Aantal verkochte tickets in deze categorie |
| ticketsReserved | Integer | NOT NULL | default: 0 | Aantal gereserveerde tickets in deze categorie |
| validDate | LocalDate | nullable | - | Eerste datum waarvoor ticket geldig is (null = alle dagen) |
| validEndDate | LocalDate | nullable | - | Laatste datum waarvoor ticket geldig is (null = zelfde als validDate) |
| startTime | LocalDateTime | nullable | - | Starttijd (bijv. deuren open) |
| endTime | LocalDateTime | nullable | - | Eindtijd |
| sortOrder | Integer | NOT NULL | default: 0 | Sorteervolgorde |
| active | boolean | NOT NULL | default: true | Of de categorie beschikbaar is voor verkoop |

**Berekende waarden:**
```
availableTickets = maxTickets - ticketsSold - ticketsReserved  (als maxTickets > 0)
                 = onbeperkt (binnen event-capaciteit)          (als maxTickets == 0)
```

#### TicketOrder

| Veld | Type | Constraint | Validatie | Beschrijving |
|------|------|-----------|-----------|-------------|
| id | Long | PK | - | Primaire sleutel |
| orderNumber | String | NOT NULL, UNIQUE, immutable | @PrePersist: "ORD-" + UUID(8) | Ordernummer (bijv. ORD-A1B2C3D4) |
| buyerFirstName | String(100) | NOT NULL | @NotBlank, @Size(min=1, max=100) | Voornaam koper |
| buyerLastName | String(100) | NOT NULL | @NotBlank, @Size(min=1, max=100) | Achternaam koper |
| buyerEmail | String(255) | NOT NULL | @NotBlank, @Email | E-mailadres koper |
| buyerPhone | String(20) | nullable | @Size(max=20) | Telefoonnummer |
| buyerStreet | String(200) | nullable | @Size(max=200) | Straat (ingevuld in stap 2) |
| buyerHouseNumber | String(10) | nullable | @Size(max=10) | Huisnummer |
| buyerPostalCode | String(10) | nullable | @Size(max=10) | Postcode |
| buyerCity | String(100) | nullable | @Size(max=100) | Plaats |
| quantity | Integer | NOT NULL | @NotNull, @Min(1), @Max(10) | Aantal tickets |
| serviceFeePerTicket | BigDecimal(10,2) | NOT NULL | default: 0 | Effectieve servicefee per ticket |
| totalServiceFee | BigDecimal(10,2) | NOT NULL | default: 0 | Totale servicekosten |
| totalPrice | BigDecimal(10,2) | NOT NULL | - | Totaalprijs incl. servicekosten |
| status | OrderStatus | NOT NULL | EnumType.STRING, default: RESERVED | Bestelstatus |
| event | Event | NOT NULL (FK) | @ManyToOne(LAZY) | Gekoppeld evenement |
| tickets | List\<Ticket\> | OneToMany | cascade=ALL, orphanRemoval=true | Tickets in bestelling |
| createdAt | LocalDateTime | NOT NULL, immutable | @PrePersist: now() | Aanmaakdatum |
| confirmedAt | LocalDateTime | nullable | - | Bevestigingsdatum |
| expiresAt | LocalDateTime | nullable | - | Verloopdatum reservering |
| emailSent | boolean | NOT NULL | default: false | Of bevestigingsmail is verstuurd |
| emailRetryCount | int | NOT NULL | default: 0 | Aantal e-mail herhaalpogingen |
| lastEmailAttempt | LocalDateTime | nullable | - | Tijdstip laatste e-mailpoging |

**Prijsberekening bij aanmaken:**
```
serviceFeePerTicket = (event.serviceFee × event.maxTickets) / onlineTickets
totalServiceFee     = serviceFeePerTicket × quantity
totalPrice          = (event.ticketPrice × quantity) + totalServiceFee
```

#### Ticket

| Veld | Type | Constraint | Validatie | Beschrijving |
|------|------|-----------|-----------|-------------|
| id | Long | PK | - | Primaire sleutel |
| ticketCode | String | NOT NULL, UNIQUE, immutable | @PrePersist: "TKT-" + UUID(12) | Ticketcode (bijv. TKT-A1B2C3D4E5F6) |
| qrCodeData | String | NOT NULL, UNIQUE, immutable | @PrePersist: UUID | Unieke data voor QR-code |
| ticketType | TicketType | NOT NULL | EnumType.STRING, default: ONLINE | Type: ONLINE of PHYSICAL |
| scanned | boolean | NOT NULL | default: false | Of ticket is gescand |
| scannedAt | LocalDateTime | nullable | - | Tijdstip van scannen |
| order | TicketOrder | nullable (FK) | @ManyToOne(LAZY) | Bestelling (null voor fysieke tickets) |
| event | Event | FK | @ManyToOne(LAZY) | Gekoppeld evenement |
| ticketCategory | TicketCategory | nullable (FK) | @ManyToOne(LAZY) | Ticketcategorie (optioneel) |
| validDate | LocalDate | nullable | - | Eerste datum waarvoor ticket geldig is (overgenomen van categorie) |
| validEndDate | LocalDate | nullable | - | Laatste datum waarvoor ticket geldig is |
| categoryName | String | nullable | - | Naam van de ticketcategorie (gedenormaliseerd) |
| createdAt | LocalDateTime | NOT NULL, immutable | @PrePersist: now() | Aanmaakdatum |

#### User

| Veld | Type | Constraint | Beschrijving |
|------|------|-----------|-------------|
| id | Long | PK | Primaire sleutel |
| email | String(255) | NOT NULL, UNIQUE | @NotBlank, @Email |
| passwordHash | String(255) | NOT NULL | @NotBlank, SHA-256 hash |
| firstName | String(100) | NOT NULL | @NotBlank, @Size(max=100) |
| lastName | String(100) | NOT NULL | @NotBlank, @Size(max=100) |
| phone | String(20) | nullable | @Size(max=20) |
| street | String(200) | nullable | @Size(max=200) |
| houseNumber | String(10) | nullable | @Size(max=10) |
| postalCode | String(10) | nullable | @Size(max=10) |
| city | String(100) | nullable | @Size(max=100) |
| createdAt | LocalDateTime | NOT NULL, immutable | @PrePersist: now() |

#### AdminUser

| Veld | Type | Constraint | Beschrijving |
|------|------|-----------|-------------|
| id | Long | PK | Primaire sleutel |
| email | String(255) | NOT NULL, UNIQUE | @NotBlank, @Email |
| passwordHash | String(255) | NOT NULL | @NotBlank |
| displayName | String(200) | nullable | @Size(max=200) |
| active | boolean | NOT NULL | default: true |
| createdAt | LocalDateTime | NOT NULL, immutable | @PrePersist: now() |

#### ScannerUser

| Veld | Type | Constraint | Beschrijving |
|------|------|-----------|-------------|
| id | Long | PK | Primaire sleutel |
| username | String(100) | NOT NULL, UNIQUE | @NotBlank, @Size(min=3, max=100) |
| passwordHash | String(255) | NOT NULL | @NotBlank |
| displayName | String(200) | nullable | @Size(max=200) |
| active | boolean | NOT NULL | default: true |
| createdAt | LocalDateTime | NOT NULL, immutable | @PrePersist: now() |

### 3.3 Enumeraties

| Enum | Waarden | Beschrijving |
|------|---------|-------------|
| EventStatus | `DRAFT`, `PUBLISHED`, `SOLD_OUT`, `CANCELLED`, `COMPLETED` | Levenscyclus van een evenement |
| OrderStatus | `RESERVED`, `CONFIRMED`, `CANCELLED`, `EXPIRED` | Levenscyclus van een bestelling |
| TicketType | `ONLINE`, `PHYSICAL` | Verkoopkanaal van een ticket |

### 3.4 DTO's

| DTO | Type | Richting | Beschrijving |
|-----|------|----------|-------------|
| EventDTO | record | Request + Response | Evenementgegevens met berekende velden en ticketcategorieën |
| OrderRequestDTO | record | Request | Bestelformulier: eventId, ticketCategoryId (optioneel), kopergegevens, aantal |
| OrderResponseDTO | record | Response | Volledige bestelling met tickets, ticketCategoryName en ticketCategoryId |
| TicketDTO | record | Response | Ticketinformatie met QR-data, categoryName, validDate, validEndDate |
| TicketCategoryDTO | record | Request + Response | Ticketcategorie met naam, prijs, capaciteit en geldigheidsdata |
| CustomerDTO | record | Request + Response | Klantgegevens |
| BuyerDetailsDTO | record | Request | Adresgegevens (straat, huisnummer, postcode, plaats) |
| LoginDTO | record | Request | Scanner login (username, password) |
| LoginResponseDTO | record | Response | Scanner token + displayName + username |
| UserLoginDTO | record | Request | User/Admin login (email, password) |
| UserResponseDTO | record | Response | User token + gegevens inclusief adresvelden |
| UserUpdateDTO | record | Request | Profielupdate (naam, telefoon, adresgegevens) |
| RegisterDTO | record | Request | Registratie (email, password, naam, telefoon) |
| ScannerUserDTO | record | Response | Scannergebruiker info |
| CreateScannerUserDTO | record | Request | Nieuwe scannergebruiker |
| TicketSalesDTO | record | Response | Verkooprapportage met omzet en aantallen |

---

## 4. Beveiliging

### 4.1 Authenticatie

**Methode:** Custom HMAC-gebaseerde tokens (vergelijkbaar met JWT maar eenvoudiger).

**Token-structuur:**
```
Base64UrlEncode(userId + "|" + expiryTimestamp + "|" + HMAC-SHA256(userId + "|" + expiryTimestamp, secret))
```

**Per authenticatiedomein:**

| Domein | Service | Token-prefix in HMAC | Verloop | Secret |
|--------|---------|---------------------|---------|--------|
| Admin | AdminAuthService | `"admin\|"` | 24 uur (configureerbaar) | AUTH_SECRET |
| Klant | CustomerAuthService | (geen prefix) | 24 uur (configureerbaar) | AUTH_SECRET |
| Gebruiker | UserAuthService | `"user\|"` | 24 uur (configureerbaar) | AUTH_SECRET |
| Scanner | AuthService | (geen prefix) | 24 uur (configureerbaar) | AUTH_SECRET |

**Token-validatie proces:**
```
1. Ontvang Bearer-token uit Authorization header
2. Base64-decode → splits op "|"
3. Controleer of verlooptijdstip > nu
4. Herbereken HMAC met opgeslagen secret
5. Vergelijk berekende HMAC met ontvangen HMAC
6. Haal gebruiker op uit database via ID
7. Controleer of gebruiker actief is (admin, klant, scanner)
8. Retourneer gebruikersobject of gooi 401
```

**Wachtwoord-hashing:**
```
hash = SHA-256(AUTH_SECRET + password)
```

**Reset-tokens:**
```
Token = Base64UrlEncode(userId + "|" + expiryTimestamp + "|" + currentPasswordHash + "|" + HMAC)
Verloop: 1 uur
Invalidatie: automatisch bij wachtwoordwijziging (passwordHash wijzigt)
```

**Uitnodigingstokens (Klant):**
```
Token = Base64UrlEncode(customerId + "|" + expiryTimestamp + "|" + HMAC)
Verloop: 7 dagen
Opgeslagen als: customer.inviteToken + customer.inviteTokenExpiry
Gewist na gebruik: inviteToken = null
```

### 4.2 Autorisatie

**Autorisatiematrix:**

| Endpoint-groep | Admin | Klant | Gebruiker | Scanner | Publiek |
|----------------|-------|-------|-----------|---------|---------|
| Klantbeheer (CRUD) | RW | - | - | - | Slug lookup |
| Evenementen (schrijven) | RW (alle) | RW (eigen) | - | - | - |
| Evenementen (lezen) | Alle | Eigen | Gepubliceerd | - | Gepubliceerd |
| Fysieke tickets | RW (alle) | RW (eigen) | - | - | - |
| Verkooprapportage | Alle events | Eigen events | - | - | - |
| Bestellingen (lezen) | Per event | - | - | - | Per ordernummer/e-mail |
| Ticketcategorieën (lezen) | Alle | Eigen | - | - | Per event |
| Ticketcategorieën (schrijven) | RW (alle) | RW (eigen) | - | - | - |
| Bestellingen (aanmaken/bevestigen/annuleren) | - | - | - | - | Ja |
| Scannen | - | - | - | Ja | - |
| Scanner-gebruikersbeheer | RW | - | - | - | - |
| Afbeeldingen uploaden | Ja | Ja | - | - | - |
| Afbeeldingen downloaden | Ja | Ja | Ja | Ja | Ja |
| Branding instellen | - | Eigen | - | - | - |
| Profiel bijwerken | - | - | Eigen | - | - |

**Eigendomscontrole (Klant):**
Bij alle `/api/events/my/*` endpoints wordt gecontroleerd dat `event.customer.id == ingelogde klant.id`. Bij mismatch: 403 Forbidden.

**Frontend token-opslag:**

| Rol | localStorage key | Interceptor |
|-----|-----------------|-------------|
| Admin | `admin_token` | Automatisch voor `/api/customers`, `/api/events`, `/api/orders/event`, `/api/auth/users` |
| Klant | `customer_token` | Automatisch voor `/api/events/my`, `/api/customer/auth/verify`, `/api/customer/auth/branding` |
| Gebruiker | `user_token` | Automatisch voor `/api/user/auth/verify`, `/api/user/auth/profile`; handmatig voor overige |
| Scanner | `scanner_token` | Handmatig per request |

### 4.3 QR-code Beveiliging

```
QR-data generatie:
  ticketData = ticket.qrCodeData (UUID)
  signature  = HMAC-SHA256(ticketData, QR_SECRET)
  qrString   = ticketData + "|" + hex(signature)
  qrImage    = ZXing.encode(qrString, QR_CODE, 300x300)
```

**Verificatie bij scannen:**
```
1. Ontvang gescande data: "ticketData|signature"
2. Splits op laatste "|" karakter
3. Herbereken HMAC: expected = HMAC-SHA256(ticketData, QR_SECRET)
4. Vergelijk expected met ontvangen signature
5. Als gelijk: zoek ticket op via qrCodeData
6. Controleer: order.status == CONFIRMED, ticket.event.id == geselecteerd eventId
7. Controleer: ticket.scanned == false
8. Markeer als gescand: scanned=true, scannedAt=now()
```

**Edge cases:**
- Geen pipe-karakter in gescande data → originele data wordt als ticketData gebruikt
- Meerdere pipe-karakters → laatste pipe wordt als separator gebruikt
- Ongeldige handtekening → 400 fout

### 4.4 Overige beveiligingsmaatregelen

| Maatregel | Implementatie |
|-----------|--------------|
| CORS | Geconfigureerd via `CORS_ORIGINS` environment variabele |
| Path-traversal preventie | Bestandsnamen met `..`, `/` of `\` worden geweigerd |
| Geen user enumeration | Wachtwoord-vergeten retourneert altijd hetzelfde bericht |
| Base64 URL-encoding | Tokens zonder padding voor URL-veiligheid |
| HMAC-SHA256 | Alle tokens en QR-codes cryptografisch gesigneerd |
| E-mail normalisatie | Alle e-mailadressen worden naar lowercase getrimd |
| Inactieve accounts | Token-validatie controleert actief-status bij elke request |

---

## 5. Servicekosten Berekening

### 5.1 Berekeningslogica

Het systeem verdeelt de totale servicekosten gelijkmatig over online tickets. Fysieke tickets zijn vrijgesteld van servicekosten.

```
Invoer:
  serviceFee       = ingestelde servicefee per ticket (bijv. €3,50)
  maxTickets       = totaal aantal tickets (online + fysiek)
  physicalTickets  = aantal fysieke tickets
  onlineTickets    = maxTickets - physicalTickets
  quantity         = aantal bestelde tickets

Berekening:
  totaleServicekosten      = serviceFee × maxTickets
  effectieveOnlineServiceFee = totaleServicekosten / onlineTickets
  serviceFeePerTicket      = effectieveOnlineServiceFee  (opgeslagen per order)
  totalServiceFee          = serviceFeePerTicket × quantity
  totalPrice               = (ticketPrice × quantity) + totalServiceFee
```

### 5.2 Rekenvoorbeeld

```
Event: 500 tickets totaal, 100 fysiek, ticketprijs €35,00, servicefee €3,50

  onlineTickets              = 500 - 100 = 400
  totaleServicekosten        = €3,50 × 500 = €1.750,00
  effectieveOnlineServiceFee = €1.750,00 / 400 = €4,375 per online ticket

Bestelling van 3 online tickets:
  serviceFeePerTicket = €4,375
  totalServiceFee     = €4,375 × 3 = €13,125
  totalPrice          = (€35,00 × 3) + €13,125 = €118,125
```

### 5.3 Omzetrapportage (TicketSalesDTO)

```
totalOnlineRevenue      = ticketPrice × onlineSold
totalPhysicalRevenue    = ticketPrice × physicalSold
totalServiceFeeRevenue  = effectieveOnlineServiceFee × onlineSold
totalRevenue            = totalOnlineRevenue + totalPhysicalRevenue + totalServiceFeeRevenue
```

### 5.4 Weergave op ticket-PDF

- Ticketprijs en servicekosten worden apart getoond per ticket
- Servicekosten worden alleen getoond als ze > €0,00 zijn
- Totaalprijs is de som van beide

---

## 6. Reserveringssysteem

### 6.1 Bestelflow (state machine)

```
                    ┌────────────┐
    Bestelling ────▶│  RESERVED  │──── timeout (10 min) ──── ┌─────────┐
    aangemaakt      └──────┬─────┘                            │ EXPIRED │
                           │                                  └─────────┘
                    bevestigen                                      ↑
                           │                            ReservationCleanupService
                    ┌──────▼──────┐                        (elke 60 seconden)
                    │  CONFIRMED  │
                    └──────┬──────┘
                           │
                    annuleren (admin)
                           │
                    ┌──────▼──────┐
                    │  CANCELLED  │
                    └─────────────┘
```

### 6.2 Reservering aanmaken

```
1. Controleer: event.status == PUBLISHED
2. Controleer: quantity <= min(event.maxTicketsPerOrder, global.maxTicketsPerOrder)
3. Controleer: beschikbaar = maxTickets - ticketsSold - ticketsReserved >= quantity
4. Optioneel: controleer ticketcategorie-capaciteit (als ticketCategoryId opgegeven)
5. Maak TicketOrder aan (status=RESERVED, expiresAt=now + timeout)
6. Genereer {quantity} Ticket-entiteiten met unieke ticketCode en qrCodeData
   - Als ticketCategory: sla categoryName, validDate, validEndDate op per ticket
7. Verhoog event.ticketsReserved met quantity
8. Optioneel: verhoog ticketCategory.ticketsReserved met quantity
9. Retourneer OrderResponseDTO (inclusief ticketCategoryName en ticketCategoryId)
```

### 6.3 Adresgegevens bijwerken

```
1. Controleer: order.status == RESERVED
2. Controleer: order.expiresAt > now (niet verlopen)
3. Sla adresgegevens op (straat, huisnummer, postcode, plaats)
4. Retourneer bijgewerkte OrderResponseDTO
```

### 6.4 Bestelling bevestigen

```
1. Controleer: order.status == RESERVED
2. Controleer: order.expiresAt > now (niet verlopen)
3. Controleer: adresgegevens zijn ingevuld (alle 4 velden)
4. Zet order.status = CONFIRMED, confirmedAt = now()
5. Verlaag event.ticketsReserved met quantity
6. Verhoog event.ticketsSold met quantity
7. Als maxTickets - ticketsSold - ticketsReserved == 0: zet event.status = SOLD_OUT
8. Genereer QR-codes met HMAC voor alle tickets
9. Verstuur bevestigingsmail met PDF (async, met retry)
10. Retourneer bevestigde OrderResponseDTO
```

### 6.5 Bestelling annuleren

```
1. Controleer: order.status == RESERVED (alleen gereserveerde bestellingen kunnen worden geannuleerd)
2. Zet order.status = CANCELLED
3. Verlaag event.ticketsReserved met quantity
4. Als event.status == SOLD_OUT: zet terug naar PUBLISHED
5. Retourneer geannuleerde OrderResponseDTO
```

### 6.6 Automatische cleanup (ReservationCleanupService)

```
Elke 60 seconden:
1. Query: SELECT * FROM TicketOrder WHERE status='RESERVED' AND expiresAt < now()
2. Voor elke verlopen order:
   a. Zet status = EXPIRED
   b. Verlaag event.ticketsReserved met order.quantity
   c. Log: "Order {orderNumber} verlopen"
3. Log: "{n} reserveringen opgeruimd" (als n > 0)
```

### 6.7 E-mail retry (EmailRetryService)

```
Elke 120 seconden:
1. Query: SELECT * FROM TicketOrder WHERE status='CONFIRMED' AND emailSent=false AND emailRetryCount < 5
2. Voor elke order:
   a. Verhoog emailRetryCount
   b. Probeer e-mail te versturen
   c. Als succesvol: emailSent = true
   d. Log resultaat met retry-telling
```

---

## 7. Frontend Architectuur

### 7.1 Componentstructuur

```
app/
├── pages/
│   ├── home/
│   │   ├── home.component              → Eventlijst met hero, statistieken en grid
│   │   ├── event-detail/               → Eventdetails, bestelformulier, categorie-selector, prijsberekening
│   │   ├── order-confirmation/         → Stap-indicator, adresformulier, bevestiging, PDF-download
│   │   ├── cart/                       → Winkelwagen met gereserveerde bestellingen
│   │   ├── user-login/                 → Gebruiker login/registratie
│   │   ├── user-profile/              → Profielbewerking (naam, telefoon, adres)
│   │   ├── my-tickets/                → Aankoopgeschiedenis
│   │   ├── forgot-password/           → Wachtwoord vergeten (gebruiker)
│   │   └── reset-password/            → Wachtwoord resetten (gebruiker + klant)
│   ├── customer/
│   │   ├── customer-login/             → Klant-login formulier
│   │   ├── customer-dashboard/         → Dashboard met events, stats, branding, categorieën, fysieke tickets
│   │   ├── customer-activate/          → Account activatie (wachtwoord instellen)
│   │   ├── customer-forgot-password/  → Wachtwoord vergeten (klant)
│   │   └── customer-landing/           → Publieke pagina per klant (/klant/:slug)
│   ├── admin/
│   │   ├── admin.component             → Dashboard met navigatiekaarten
│   │   ├── admin-login/                → Admin-login en setup
│   │   ├── customer-management/        → CRUD voor klanten met tabel en dialoog
│   │   ├── event-management/           → CRUD voor evenementen (admin-perspectief)
│   │   ├── order-management/           → Bestellingenlijst per event
│   │   ├── scanner-management/         → Scanner-gebruikersbeheer
│   │   └── ticket-scanner/            → Admin ticket-scanner pagina
│   ├── scanner/
│   │   ├── scanner-login/              → Scanner-login
│   │   └── ticket-scanner-camera/      → Live camera, handmatige invoer, scangeschiedenis
├── services/
│   ├── api.service.ts                  → Centrale HTTP-client voor alle API-calls
│   ├── admin-auth.service.ts           → Admin token-management
│   ├── customer-auth.service.ts        → Klant token-management
│   ├── user-auth.service.ts            → Gebruiker token-management
│   ├── auth.service.ts                 → Scanner token-management
│   └── cart.service.ts                 → Winkelwagen-state (gereserveerde bestellingen, timers)
├── guards/
│   ├── admin-auth.guard.ts             → Route-bescherming admin (localStorage + /verify)
│   ├── customer-auth.guard.ts          → Route-bescherming klant
│   ├── user-auth.guard.ts              → Route-bescherming gebruiker
│   └── scanner-auth.guard.ts           → Route-bescherming scanner
├── interceptors/
│   └── admin-auth.interceptor.ts       → Automatische Bearer-token injectie per endpoint
└── models/
    └── models.ts                       → TypeScript interfaces (Customer, Event, Order, Ticket, etc.)
```

### 7.2 Routing

**Publieke routes (geen authenticatie):**

| Pad | Component | Beschrijving |
|-----|-----------|-------------|
| `/` | HomeComponent | Eventlijst met hero-sectie en statistieken |
| `/event/:id` | EventDetailComponent | Eventdetails, categorie-selector en bestelformulier |
| `/order/:orderNumber` | OrderConfirmationComponent | Besteloverzicht, adres invullen, bevestigen |
| `/winkelwagen` | CartComponent | Winkelwagen met gereserveerde bestellingen |
| `/klant/:slug` | CustomerLandingComponent | Publieke pagina van organisator |
| `/login` | UserLoginComponent | Gebruiker login/registratie |
| `/forgot-password` | ForgotPasswordComponent | Wachtwoord vergeten (gebruiker) |
| `/reset-password` | ResetPasswordComponent | Wachtwoord resetten |
| `/klant/login` | CustomerLoginComponent | Klant-login |
| `/klant/activeren/:token` | CustomerActivateComponent | Account activatie |
| `/klant/forgot-password` | CustomerForgotPasswordComponent | Wachtwoord vergeten (klant) |
| `/klant/reset-password` | ResetPasswordComponent | Wachtwoord resetten (klant) |
| `/scan/login` | ScannerLoginComponent | Scanner-login |
| `/admin/login` | AdminLoginComponent | Admin-login/setup |

**Beveiligde routes:**

| Pad | Component | Guard | Beschrijving |
|-----|-----------|-------|-------------|
| `/profiel` | UserProfileComponent | userAuthGuard | Profielbewerking (naam, adres) |
| `/my-tickets` | MyTicketsComponent | userAuthGuard | Aankoopgeschiedenis |
| `/klant/dashboard` | CustomerDashboardComponent | customerAuthGuard | Klant-dashboard |
| `/admin` | AdminComponent | adminAuthGuard | Admin-dashboard |
| `/admin/customers` | CustomerManagementComponent | adminAuthGuard | Klantbeheer |
| `/admin/events` | EventManagementComponent | adminAuthGuard | Evenementbeheer |
| `/admin/orders/:eventId` | OrderManagementComponent | adminAuthGuard | Bestellingen per event |
| `/admin/scanners` | ScannerManagementComponent | adminAuthGuard | Scanner-beheer |
| `/scan` | TicketScannerCameraComponent | scannerAuthGuard | QR-scanner |

### 7.3 Auth Guards

Alle guards volgen hetzelfde patroon:
```
1. Check of token bestaat in localStorage
2. Als niet: redirect naar login-pagina, return false
3. Als wel: roep /verify endpoint aan
4. Bij succesvolle verificatie: return true
5. Bij fout (401): verwijder token, redirect naar login
```

### 7.4 HTTP Interceptor

De `AdminAuthInterceptor` voegt automatisch Bearer-tokens toe op basis van het endpoint:

```
Request URL bevat:                    → Token bron:
/api/events/my                        → customer_token
/api/customer/auth/verify             → customer_token
/api/customer/auth/branding           → customer_token
/api/customers                        → admin_token
/api/events (niet /my)                → admin_token
/api/orders/event                     → admin_token
/api/auth/users                       → admin_token

Uitgesloten van interceptie:
- GET /api/events/published
- GET /api/events/customer/
- GET /api/events/{id} (enkel event)
- GET /api/events/{id}/categories
- GET /api/customers/slug/
- /api/images/ (GET)
- /api/images/upload (handmatige auth)
```

### 7.5 Belangrijke UI-patronen

**Event Detail (bestelformulier):**
- Ticketcategorie-selector: dropdown/kaarten per categorie met eigen prijs en beschikbaarheid
- Per-categorie quantity-selectors met +/- knoppen
- Realtime prijsberekening: `totalPrice = (ticketPrice + serviceFee) × quantity`
- Beschikbaarheidsbalk: visuele voortgang van verkochte tickets (instelbaar via showAvailability)
- Quantity-selector begrensd op min(maxTicketsPerOrder, availableTickets)
- Automatisch voorvullen vanuit ingelogde gebruiker (inclusief adresgegevens)
- Winkelwagenknop en directe bestelknop

**Winkelwagen (CartComponent):**
- Overzicht van alle gereserveerde bestellingen met countdown timers
- Per bestelling: evenementnaam, categorie, aantal, prijs, status
- Mogelijkheid om bestellingen te bevestigen of te annuleren
- Totaalprijs berekening

**Order Confirmation (3-stappen):**
- Countdown timer in mm:ss formaat (update elke seconde)
- Stap 1 (RESERVED): Toon orderdetails + timer
- Stap 2 (Adres): Formulier met 4 verplichte velden
- Stap 3 (CONFIRMED): Ticket-cards met QR-codes + PDF download

**Customer Dashboard:**
- Statistiekenkaarten: totaal events, tickets verkocht, geschatte omzet
- Event-tabel met inline acties (publiceren, bewerken, verwijderen)
- Ticketcategorieën beheren per evenement (toevoegen, bewerken, verwijderen, meerdaagse ondersteuning)
- Dialoogvensters voor: event CRUD, verkoopinzicht, branding, voorbeeld-ticket PDF
- Drag-drop zone voor afbeeldingsupload met preview
- 16 kleurpresets voor snelle brandingselectie

**Ticket Scanner:**
- Camera-feed op 10 FPS met automatische QR-detectie
- Kleurgecodeerde pop-ups (groen/oranje/rood) met 3s auto-dismiss
- Sessie-gebaseerde scangeschiedenis
- Evenementselectie met doorzoekbare dropdown

### 7.6 Technologiestack

| Library | Versie | Doel |
|---------|--------|------|
| Angular | 19.2 | SPA framework (standalone components) |
| PrimeNG | 19.1.4 | UI-componentenbibliotheek (tabel, dialoog, formulier, etc.) |
| html5-qrcode | 2.3.8 | QR-code scanning via camera (Html5Qrcode) |
| RxJS | 7.8 | Reactive programming, HTTP-calls |
| AOS | 2.3.4 | Scroll-animaties op homepage |
| TypeScript | 5.x | Type-veilige ontwikkeling |

---

## 8. Deployment

### 8.1 Docker Compose (Ontwikkeling/Staging)

```yaml
Services:
  postgres:
    image: postgres:16-alpine
    port: 5432:5432
    environment:
      TZ: Europe/Amsterdam
      POSTGRES_DB: ticketservice
      POSTGRES_USER: ticketservice
      POSTGRES_PASSWORD: ticketservice
    volume: postgres_data → /var/lib/postgresql/data
    healthcheck: pg_isready (10s interval, 5 retries)

  backend:
    build: ./backend (multi-stage: maven:3.9-eclipse-temurin-21 → temurin:21-jre)
    port: 8080:8080
    environment:
      QUARKUS_PROFILE: prod
      DB_HOST: postgres
      AUTH_SECRET / QR_SECRET: (configureerbaar)
      MAIL_*: SMTP-configuratie
      APP_BASE_URL: http://localhost:80
      CORS_ORIGINS: http://localhost:80,http://localhost
    depends_on: postgres (healthy)
    JVM: -Xmx512m -Xms256m

  frontend:
    build: ./frontend (multi-stage: node:22-alpine → nginx:alpine)
    port: 80:80
    depends_on: backend

  cloudflared:
    image: cloudflare/cloudflared:latest
    command: tunnel --no-autoupdate run
    environment: CLOUDFLARE_TUNNEL_TOKEN (vereist)
    depends_on: frontend, backend
    restart: unless-stopped
```

### 8.2 Nginx Configuratie (Frontend)

```
server {
    listen 80;

    # Gzip compressie
    gzip on (text, css, json, js, xml)

    # Angular SPA routing
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API reverse proxy
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header X-Real-IP, X-Forwarded-For, X-Forwarded-Proto;
        proxy_set_header Origin "";  # CORS-issues voorkomen
    }
}
```

### 8.3 Kubernetes (Productie)

**Namespace:** `ticketservice`

| Component | Replicas | Memory (req/limit) | CPU (req/limit) | HPA |
|-----------|----------|-------------------|-----------------|-----|
| Backend | 2 | 256Mi / 512Mi | 250m / 500m | 2-10 pods (70% CPU, 80% mem) |
| Frontend | 2 | 64Mi / 128Mi | 50m / 100m | - |
| PostgreSQL | 1 | 256Mi / 512Mi | 250m / 500m | - |

**Backend Deployment:**
- Image: `ticketservice/ticket-service-backend:1.0.0-SNAPSHOT`
- Health checks:
  - Readiness: `GET /api/health/ready` (initialDelay: 10s, period: 10s)
  - Liveness: `GET /api/health/live` (initialDelay: 15s, period: 20s)
- Rolling update: maxUnavailable=0, maxSurge=1
- Environment: via ConfigMap en Secret (postgres-secret)

**Frontend Deployment:**
- Image: `ticketservice/ticket-service-frontend:1.0.0`
- Health check: Readiness `GET /` (initialDelay: 5s, period: 10s)

**PostgreSQL Deployment:**
- Image: `postgres:16-alpine`
- PersistentVolumeClaim: 5Gi (ReadWriteOnce)
- Credentials: via `postgres-secret` (Kubernetes Secret)

**Ingress:**
- Class: nginx
- Host: lockitree.com
- Proxy body size: 10MB
- Routes:
  - `/` → frontend:80
  - `/api` → backend:8080

### 8.4 CI/CD (GitHub Actions)

```
Trigger: Push naar main/master/develop, PR naar main/master

┌──────────────────────────────────────────────────┐
│                    Parallel                        │
│                                                    │
│  ┌─────────────────┐    ┌──────────────────────┐  │
│  │  Backend Job     │    │  Frontend Job         │  │
│  │  JDK 21 Temurin  │    │  Node.js 20           │  │
│  │  + PostgreSQL 16 │    │  npm ci               │  │
│  │  mvn test        │    │  ng build --prod      │  │
│  │  mvn package     │    │  upload artifact      │  │
│  │  upload artifact │    │  (retention: 7 dagen)  │  │
│  │  (retention: 7d) │    └──────────────────────┘  │
│  └─────────────────┘                               │
└──────────────────────────────────────────────────┘
                        │
                        ▼ (alleen main branch push)
              ┌─────────────────┐
              │   Deploy Job     │
              │   Download       │
              │   artifacts      │
              └─────────────────┘
```

### 8.5 Seed Data (Ontwikkeling)

**StartupService** (draait bij opstart als database drop-and-create):
- Scanner: username=`scanner`, password=`scanner123`
- Admin: email=`admin@ticketservice.nl`, password=`admin`

**SampleDataLoader** (draait bij opstart als database drop-and-create):
- 2 klanten: Festival Events BV, Nachtleven Productions
- 3 evenementen: Zomerfestival 2026, Neon Night Party, Oktoberfest Deluxe
- Status: alle evenementen PUBLISHED

---

## 9. Configuratie (Environment Variabelen)

### 9.1 Database

| Variabele | Standaard | Beschrijving |
|-----------|-----------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL poort |
| `DB_NAME` | ticketservice | Database naam |
| `DB_USERNAME` | ticketservice | Database gebruiker |
| `DB_PASSWORD` | ticketservice | Database wachtwoord |

### 9.2 Beveiliging

| Variabele | Standaard | Beschrijving |
|-----------|-----------|-------------|
| `AUTH_SECRET` | (dev default) | Geheime sleutel voor HMAC-tokens en wachtwoord-hashing |
| `QR_SECRET` | (dev default) | Geheime sleutel voor QR-code HMAC-handtekeningen |
| `AUTH_TOKEN_EXPIRY_HOURS` | 24 | Token-verlooptijd in uren |
| `CORS_ORIGINS` | http://localhost | Komma-gescheiden lijst van toegestane origins |

### 9.3 E-mail

| Variabele | Standaard | Beschrijving |
|-----------|-----------|-------------|
| `MAIL_HOST` | smtp.example.com | SMTP-server hostname |
| `MAIL_PORT` | 465 | SMTP-server poort |
| `MAIL_FROM` | noreply@ticketservice.nl | Fallback afzender e-mailadres |
| `MAIL_USERNAME` | - | SMTP-gebruikersnaam |
| `MAIL_PASSWORD` | - | SMTP-wachtwoord |
| `MAIL_SSL` | false | SSL inschakelen |
| `MAIL_START_TLS` | - | STARTTLS-modus |
| `MAIL_TICKET_DOMAIN` | tickets.lockitree.com | Domein voor dynamische afzenderadressen |

### 9.4 Applicatie

| Variabele | Standaard | Beschrijving |
|-----------|-----------|-------------|
| `APP_BASE_URL` | http://localhost:80 | Basis-URL voor links in e-mails (activatie, reset) |
| `QUARKUS_PROFILE` | dev | Profiel: dev, test, prod |
| `TZ` | Europe/Amsterdam | Tijdzone |

### 9.5 Business-regels (Quarkus properties)

| Property | Standaard | Beschrijving |
|----------|-----------|-------------|
| `ticket.reservation.timeout-minutes` | 10 | Minuten voordat een reservering verloopt |
| `ticket.order.max-tickets` | 10 | Globaal maximum tickets per bestelling |

---

## 10. Externe Afhankelijkheden

### 10.1 Backend Dependencies

| Dependency | Versie | Doel |
|-----------|--------|------|
| Quarkus | 3.17.8 | Backend framework (RESTEasy, CDI, Scheduler) |
| Hibernate ORM Panache | via BOM | ORM met Active Record patroon |
| PostgreSQL JDBC | via BOM | Database driver (productie) |
| H2 | via BOM | In-memory database (dev/test) |
| ZXing (core + javase) | 3.5.3 | QR-code generatie (BarcodeFormat.QR_CODE) |
| iText (kernel + layout) | 8.0.5 | PDF-generatie (online + fysieke tickets) |
| Quarkus Mailer | via BOM | E-mailverzending via SMTP |
| Quarkus Scheduler | via BOM | Periodieke taken (cleanup, retry) |
| Quarkus Health | via BOM | Health-endpoints (/health/live, /health/ready) |
| Maven | 3.9 | Build-tool |
| Java | 21 (Temurin) | Runtime |

### 10.2 Frontend Dependencies

| Dependency | Versie | Doel |
|-----------|--------|------|
| Angular | 19.2 | SPA framework (standalone components) |
| PrimeNG | 19.1.4 | UI-componenten (Table, Dialog, InputText, DatePicker, etc.) |
| html5-qrcode | 2.3.8 | QR-code scanning via device-camera |
| RxJS | 7.8 | Reactive HTTP-calls en state management |
| AOS (Animate on Scroll) | 2.3.4 | Scroll-gebaseerde animaties |
| Node.js | 22 (build) / 20 (CI) | Build-omgeving |
| Nginx | Alpine | Webserver voor SPA + reverse proxy |

### 10.3 Infrastructuur

| Component | Versie | Doel |
|-----------|--------|------|
| PostgreSQL | 16-alpine | Relationele database |
| Docker | - | Containerisatie |
| Kubernetes | - | Container-orchestratie (productie) |
| Cloudflared | latest | Cloudflare tunnel voor externe toegang |
| GitHub Actions | - | CI/CD pipeline |

---

## 11. PDF-generatie

### 11.1 Online Tickets (PdfService)

**Layout:** A4 formaat, 1 ticket per pagina.

```
┌─────────────────────────────────────────────────────┐
│                                                       │
│  ┌──────────────────────┐  ┌──────────────────────┐  │
│  │   Linkerkolom (55%)  │  │  Rechterkolom (45%)  │  │
│  │                      │  │                      │  │
│  │  Bedrijfsnaam        │  │    ┌────────────┐    │  │
│  │  Ordernummer         │  │    │  QR-CODE   │    │  │
│  │  Ticketcode          │  │    │  300×300    │    │  │
│  │                      │  │    └────────────┘    │  │
│  │  Evenementnaam       │  │                      │  │
│  │  Datum & tijd        │  │    Ticketcode        │  │
│  │  Locatie             │  │                      │  │
│  │  Adres               │  │                      │  │
│  │                      │  │                      │  │
│  │  Ticketprijs         │  │                      │  │
│  │  Servicekosten*      │  │                      │  │
│  │  Totaalprijs         │  │                      │  │
│  │                      │  │                      │  │
│  │  Kopergegevens       │  │                      │  │
│  └──────────────────────┘  └──────────────────────┘  │
│                                                       │
│  Footer: disclaimer (persoonlijk gebruik, ID-plicht)  │
└─────────────────────────────────────────────────────┘
* Alleen getoond als servicekosten > €0,00
```

**Styling:**
- Primaire kleur van klant (standaard: #2980b9)
- Lichte achtergrondkleur (92% lightness variant)
- Nederlandse datumnotatie

### 11.2 Fysieke Tickets (PhysicalTicketPdfService)

**Layout:** A4 formaat, 4 tickets per pagina (2×2 grid), dubbelzijdig geoptimaliseerd.

```
Voorkant:                    Achterkant (gespiegeld voor duplex):
┌────────────┬────────────┐  ┌────────────┬────────────┐
│  Ticket 0  │  Ticket 1  │  │  Ticket 1  │  Ticket 0  │
│  QR + info │  QR + info │  │  Back info │  Back info │
├────────────┼────────────┤  ├────────────┼────────────┤
│  Ticket 2  │  Ticket 3  │  │  Ticket 3  │  Ticket 2  │
│  QR + info │  QR + info │  │  Back info │  Back info │
└────────────┴────────────┘  └────────────┴────────────┘

Ticket afmeting: 247.5 × 371 punten
Marges: 25pt rondom
Stippellijnen: snijgeleiders
```

**Voorkant per ticket:**
- Bedrijfsnaam header met klantkleur
- QR-code
- Evenementnaam, datum/tijd, locatie, prijs
- Ticketcode

**Achterkant per ticket:**
- Bedrijfsinformatie (logo, naam, website, adres)
- Evenementdetails
- Ticketcode
- Voorwaarden (3-punts disclaimer):
  1. Ticket is persoonlijk en niet overdraagbaar
  2. Toon geldig identiteitsbewijs bij de ingang
  3. Kopiëren of doorverkopen is verboden
