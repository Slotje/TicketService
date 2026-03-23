# Functioneel Ontwerp - TicketService

**Versie:** 1.0
**Datum:** 2026-03-23
**Project:** TicketService - Multi-tenant Ticketverkoop Platform

---

## 1. Inleiding

TicketService is een multi-tenant platform waarmee evenementorganisatoren (klanten) evenementen kunnen aanmaken, tickets verkopen en bezoekers kunnen beheren. Eindgebruikers kunnen evenementen bekijken, tickets kopen en digitale tickets ontvangen. Het platform ondersteunt zowel online als fysieke tickets.

### 1.1 Doelgroepen

| Rol | Beschrijving |
|-----|-------------|
| **Admin** | Platformbeheerder; beheert klanten, evenementen en scanners |
| **Klant (Customer)** | Evenementorganisator; maakt evenementen aan en beheert ticketverkoop |
| **Gebruiker (User)** | Eindgebruiker; koopt tickets en bekijkt aankoopgeschiedenis |
| **Scanner** | Medewerker bij evenement; scant QR-codes bij de ingang |

---

## 2. Functionele Eisen

### 2.1 Evenementbeheer

| ID | Eis | Actor |
|----|-----|-------|
| F-EV-01 | Aanmaken van evenementen met naam, datum, locatie, beschrijving en afbeelding | Klant, Admin |
| F-EV-02 | Instellen van maximaal aantal tickets (online + fysiek apart) | Klant, Admin |
| F-EV-03 | Instellen van ticketprijs en servicekosten per ticket | Klant, Admin |
| F-EV-04 | Evenementstatus beheren: Concept, Gepubliceerd, Uitverkocht, Geannuleerd, Afgerond | Klant, Admin |
| F-EV-05 | Evenementen zijn zichtbaar voor eindgebruikers wanneer status = Gepubliceerd | Gebruiker |
| F-EV-06 | Overzicht van ticketverkoop (online en fysiek) per evenement | Klant, Admin |

### 2.2 Ticketverkoop (Online)

| ID | Eis | Actor |
|----|-----|-------|
| F-TK-01 | Bestellen van 1 tot 10 tickets per bestelling | Gebruiker |
| F-TK-02 | Reservering is 10 minuten geldig; daarna verloopt de bestelling automatisch | Systeem |
| F-TK-03 | Na bevestiging ontvangt de koper een e-mail met PDF-tickets | Systeem |
| F-TK-04 | Elk ticket bevat een unieke QR-code (HMAC-SHA256 gesigneerd) | Systeem |
| F-TK-05 | Gebruiker kan bestelbevestiging en PDF opnieuw downloaden via ordernummer | Gebruiker |
| F-TK-06 | Wanneer alle tickets verkocht zijn, wordt de status automatisch Uitverkocht | Systeem |

### 2.3 Fysieke Tickets

| ID | Eis | Actor |
|----|-----|-------|
| F-FT-01 | Klant kan fysieke tickets genereren als afdrukbare PDF | Klant |
| F-FT-02 | Fysieke tickets worden apart bijgehouden van online tickets | Systeem |
| F-FT-03 | Fysieke tickets bevatten dezelfde QR-code beveiliging als online tickets | Systeem |

### 2.4 Ticketscanning

| ID | Eis | Actor |
|----|-----|-------|
| F-SC-01 | Scanner kan QR-codes scannen via camera op mobiel apparaat | Scanner |
| F-SC-02 | Systeem valideert de QR-code handtekening (HMAC) | Systeem |
| F-SC-03 | Systeem markeert ticket als gescand met tijdstip | Systeem |
| F-SC-04 | Dubbel scannen wordt gedetecteerd en gemeld | Systeem |

### 2.5 Klantbeheer (Multi-tenant)

| ID | Eis | Actor |
|----|-----|-------|
| F-KL-01 | Admin kan klanten (organisatoren) aanmaken met bedrijfsnaam en e-mail | Admin |
| F-KL-02 | Klant ontvangt een uitnodigingslink per e-mail om account te activeren | Systeem |
| F-KL-03 | Elke klant krijgt een unieke slug voor een publieke landingspagina | Systeem |
| F-KL-04 | Klant kan huisstijl instellen (primaire kleur, logo) | Klant |
| F-KL-05 | Klant heeft een eigen dashboard met overzicht van eigen evenementen | Klant |

### 2.6 Authenticatie & Autorisatie

| ID | Eis | Actor |
|----|-----|-------|
| F-AU-01 | Admin, klant, gebruiker en scanner hebben gescheiden login-flows | Alle |
| F-AU-02 | Tokens verlopen na 24 uur | Systeem |
| F-AU-03 | Wachtwoord-vergeten flow met reset-link per e-mail | Klant, Gebruiker |
| F-AU-04 | Eerste admin kan zichzelf registreren via setup-endpoint | Admin |

### 2.7 E-mail

| ID | Eis | Actor |
|----|-----|-------|
| F-EM-01 | Bevestigingsmail met PDF-tickets na succesvolle bestelling | Systeem |
| F-EM-02 | Uitnodigingsmail voor nieuwe klanten | Systeem |
| F-EM-03 | Wachtwoord-reset e-mails | Systeem |
| F-EM-04 | Automatische retry bij mislukte e-mailverzending | Systeem |

---

## 3. Gebruikersflows

### 3.1 Ticket Kopen (Gebruiker)

```
Evenement bekijken → Aantal tickets kiezen → Bestelling plaatsen (RESERVED)
→ Betaling bevestigen → Bestelling bevestigd (CONFIRMED) → E-mail met PDF ontvangen
```

**Alternatief:** Niet bevestigd binnen 10 minuten → Bestelling vervalt (EXPIRED) → Tickets vrijgegeven.

### 3.2 Evenement Aanmaken (Klant)

```
Inloggen → Dashboard → Nieuw evenement → Details invullen → Opslaan als Concept
→ Publiceren → Evenement zichtbaar voor gebruikers
```

### 3.3 Tickets Scannen (Scanner)

```
Inloggen → Camera openen → QR-code scannen → Validatie resultaat tonen
→ Ticket gemarkeerd als gescand
```

### 3.4 Klant Onboarding (Admin)

```
Admin maakt klant aan → Systeem stuurt uitnodiging → Klant klikt link
→ Wachtwoord instellen → Klant is actief → Dashboard beschikbaar
```

---

## 4. Niet-functionele Eisen

| ID | Eis | Categorie |
|----|-----|-----------|
| NF-01 | Maximale bestandsgrootte voor uploads: 5MB | Performance |
| NF-02 | Ondersteunde bestandstypen: JPEG, PNG, GIF, WebP | Functionaliteit |
| NF-03 | Maximaal 10 tickets per bestelling | Business rule |
| NF-04 | Reserveringstimeout: 10 minuten | Business rule |
| NF-05 | Token-verloop: 24 uur | Beveiliging |
| NF-06 | Applicatie moet schaalbaar zijn (2-10 pods via HPA) | Schaalbaarheid |
| NF-07 | Health-endpoints beschikbaar voor monitoring | Beschikbaarheid |
