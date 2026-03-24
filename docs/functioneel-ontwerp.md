# Functioneel Ontwerp - TicketService

**Versie:** 3.0
**Datum:** 2026-03-24
**Project:** TicketService - Multi-tenant Ticketverkoop Platform

---

## 1. Inleiding

### 1.1 Projectbeschrijving

TicketService is een multi-tenant platform waarmee evenementorganisatoren (klanten) evenementen kunnen aanmaken, tickets verkopen en bezoekers kunnen beheren. Eindgebruikers kunnen evenementen bekijken, tickets kopen en digitale tickets ontvangen. Het platform ondersteunt zowel online als fysieke tickets.

Het platform biedt een volledige end-to-end oplossing: van het aanmaken van een evenement en het configureren van tickettypen, tot het afhandelen van bestellingen, het genereren van QR-code-beveiligde tickets, het versturen van bevestigingsmails met PDF-bijlagen, en het scannen van tickets bij de ingang.

### 1.2 Doelgroepen

| Rol | Beschrijving | Toegang via |
|-----|-------------|-------------|
| **Admin** | Platformbeheerder; beheert klanten, evenementen en scanners | `/admin/login` |
| **Klant (Customer)** | Evenementorganisator; maakt evenementen aan en beheert ticketverkoop | `/klant/login` |
| **Gebruiker (User)** | Eindgebruiker; koopt tickets en bekijkt aankoopgeschiedenis | `/login` |
| **Scanner** | Medewerker bij evenement; scant QR-codes bij de ingang | `/scan/login` |

### 1.3 Scope

Het systeem omvat de volgende functionele gebieden:

1. **Evenementbeheer** – Aanmaken, bewerken, publiceren en verwijderen van evenementen
2. **Ticketcategorieën** – Meerdere tickettypen per evenement met eigen prijs, capaciteit en geldigheidsdata (meerdaagse ondersteuning)
3. **Online ticketverkoop** – Reserveren, betalen en bevestigen van tickets via de website met winkelwagenfunctie
4. **Fysieke ticketverkoop** – Genereren van afdrukbare tickets voor verkoop aan de deur
5. **Ticketscanning** – QR-code validatie bij de ingang met camera of handmatige invoer
6. **Klantbeheer (multi-tenant)** – Onboarding, branding en dashboards per organisator
7. **Gebruikersbeheer** – Registratie, login, profielbeheer en aankoopgeschiedenis voor eindgebruikers
8. **E-mailcommunicatie** – Bevestigingen, uitnodigingen en wachtwoord-reset berichten
9. **Rapportage** – Verkoopoverzichten met omzet- en scanstatistieken

### 1.4 Begrippen

| Term | Definitie |
|------|-----------|
| **Tenant** | Een klant (organisator) met eigen evenementen, branding en slug |
| **Slug** | Unieke URL-vriendelijke naam van een klant, afgeleid van de bedrijfsnaam |
| **Reservering** | Een tijdelijke blokkering van tickets (10 minuten) totdat de bestelling wordt bevestigd |
| **Servicekosten** | Toeslag per ticket die wordt verdeeld over online tickets; fysieke tickets zijn vrijgesteld |
| **Fysieke tickets** | Tickets bedoeld voor verkoop aan de deur, apart gegenereerd als afdrukbare PDF |
| **Effectieve servicefee** | De herberekende servicefee per online ticket na verdeling van de totale servicekosten |
| **Ticketcategorie** | Een tickettype binnen een evenement met eigen naam, prijs, capaciteit en optionele geldigheidsdata |
| **Winkelwagen** | Tijdelijke verzameling van gereserveerde bestellingen die de gebruiker kan bevestigen |

---

## 2. Functionele Eisen

### 2.1 Evenementbeheer

| ID | Eis | Actor | Validatie |
|----|-----|-------|-----------|
| F-EV-01 | Aanmaken van evenementen met naam, datum, locatie, beschrijving en afbeelding | Klant, Admin | Naam: 2-200 tekens, verplicht. Locatie: max 300, verplicht. Beschrijving: max 2000. Datum moet in de toekomst liggen. |
| F-EV-02 | Instellen van maximaal aantal tickets (online + fysiek apart) | Klant, Admin | Minimaal 1, maximaal 100.000 tickets. Fysieke tickets mogen niet hoger zijn dan totaal. |
| F-EV-03 | Instellen van ticketprijs en servicekosten per ticket | Klant, Admin | Prijs verplicht, ≥ €0,00. Servicekosten optioneel, ≥ €0,00. Precisie: 2 decimalen. |
| F-EV-04 | Evenementstatus beheren: Concept → Gepubliceerd → Uitverkocht / Geannuleerd / Afgerond | Klant, Admin | Status kan niet worden gewijzigd als dat inconsistent is met ticketverkoop. |
| F-EV-05 | Evenementen zijn zichtbaar voor eindgebruikers wanneer status = Gepubliceerd of Uitverkocht, en datum in de toekomst | Gebruiker | Automatisch gefilterd door backend. |
| F-EV-06 | Overzicht van ticketverkoop per evenement met splitsing online/fysiek, omzet en scanstatistieken | Klant, Admin | Rapportage toont: totaal verkocht, gereserveerd, beschikbaar, gescand, omzet per kanaal. |
| F-EV-07 | Uploaden van evenementafbeelding (JPEG, PNG, GIF, WebP, max 5MB) | Klant, Admin | Bestandstype en -grootte worden server-side gevalideerd. |
| F-EV-08 | Instellen van maximaal aantal tickets per bestelling (1-10, standaard 10) | Klant, Admin | Wordt afgedwongen bij het plaatsen van een bestelling. |
| F-EV-09 | Optioneel einddatum/tijd instellen voor meerdaagse evenementen | Klant, Admin | Einddatum is optioneel; wordt getoond op tickets indien ingevuld. |
| F-EV-10 | Evenement kan alleen verwijderd worden als er nog geen tickets zijn verkocht | Klant, Admin | Systeem weigert verwijdering met foutmelding bij verkochte tickets. |
| F-EV-11 | Instellen of beschikbaarheid zichtbaar is voor eindgebruikers (showAvailability) | Klant, Admin | Standaard aan; indien uit worden beschikbare aantallen niet getoond. |
| F-EV-12 | Ticketcategorieën aanmaken, bewerken en verwijderen per evenement | Klant, Admin | Categorieën met eigen naam, beschrijving, prijs, servicekosten, capaciteit en sorteervolgorde. |
| F-EV-13 | Ticketcategorieën kunnen optionele geldigheidsdata hebben (meerdaagse ondersteuning) | Klant, Admin | Start-/einddatum en start-/eindtijd per categorie; null = geldig voor alle dagen. |
| F-EV-14 | Ticketcategorieën kunnen actief/inactief gezet worden | Klant, Admin | Inactieve categorieën zijn niet beschikbaar voor verkoop. |

### 2.2 Ticketverkoop (Online)

| ID | Eis | Actor | Details |
|----|-----|-------|---------|
| F-TK-01 | Bestellen van 1 tot N tickets per bestelling (max instelbaar per evenement, standaard 10) | Gebruiker | Aantal wordt begrensd door min(maxTicketsPerOrder, beschikbare tickets). Optioneel per ticketcategorie. |
| F-TK-02 | Reservering is 10 minuten geldig; daarna verloopt de bestelling automatisch | Systeem | Cleanup-service draait elke 60 seconden. Tickets worden vrijgegeven bij verval. |
| F-TK-03 | Na bevestiging ontvangt de koper een e-mail met PDF-tickets | Systeem | PDF bevat per ticket een pagina met QR-code, evenementdetails en prijsinformatie. |
| F-TK-04 | Elk ticket bevat een unieke QR-code (HMAC-SHA256 gesigneerd) | Systeem | QR-code bevat ticketdata + handtekening gescheiden door pipe-karakter. |
| F-TK-05 | Gebruiker kan bestelbevestiging en PDF opnieuw downloaden via ordernummer | Gebruiker | Toegankelijk via URL `/order/{orderNumber}`. |
| F-TK-06 | Wanneer alle tickets verkocht zijn, wordt de status automatisch Uitverkocht | Systeem | Status wordt teruggezet naar Gepubliceerd bij annulering van een bestelling. |
| F-TK-07 | Bestelling vereist voornaam, achternaam en geldig e-mailadres | Gebruiker | Telefoonnummer is optioneel. |
| F-TK-08 | Adresgegevens (straat, huisnummer, postcode, plaats) moeten worden ingevuld vóór bevestiging | Gebruiker | Adres wordt in een tweede stap gevraagd na de initiële reservering. |
| F-TK-09 | Bij bevestiging worden servicekosten berekend en opgeslagen per bestelling | Systeem | Formule: effectieve servicefee × aantal tickets. |
| F-TK-10 | Formulier wordt automatisch voorgevuld als de gebruiker is ingelogd | Systeem | Voornaam, achternaam, e-mail en telefoon worden overgenomen uit gebruikersprofiel. |
| F-TK-11 | Bestelling kan worden geannuleerd met teruggave van tickets | Admin, Systeem | Tickets worden vrijgegeven en tellers bijgewerkt. Beschikbaar via annuleer-endpoint. |
| F-TK-12 | Bij bestelling kan een ticketcategorie worden opgegeven | Gebruiker | Categorie bepaalt prijs, geldigheidsdata en capaciteitscontrole. |
| F-TK-13 | Tickets in een bestelling bevatten de categorienaam en geldigheidsdata | Systeem | Categorienaam en validDate/validEndDate worden opgeslagen per ticket. |

### 2.3 Fysieke Tickets

| ID | Eis | Actor | Details |
|----|-----|-------|---------|
| F-FT-01 | Klant kan fysieke tickets genereren als afdrukbare PDF (4 tickets per A4-pagina) | Klant | PDF is geoptimaliseerd voor dubbelzijdig printen met voorkant/achterkant in spiegelorde. |
| F-FT-02 | Fysieke tickets worden apart bijgehouden van online tickets | Systeem | Eigen tellers: physicalTickets, physicalTicketsSold, physicalTicketsGenerated. |
| F-FT-03 | Fysieke tickets bevatten dezelfde QR-code beveiliging als online tickets | Systeem | Zelfde HMAC-SHA256 handtekening als online tickets. |
| F-FT-04 | Fysieke tickets kunnen slechts één keer worden gegenereerd per evenement | Systeem | Na generatie wordt `physicalTicketsGenerated=true` gezet. PDF kan wel opnieuw gedownload worden. |
| F-FT-05 | Klant kan fysieke ticketverkoop registreren (aantal verkochte tickets bijwerken) | Klant | Via "markeer als verkocht" functie met hoeveelheidsinvoer. |
| F-FT-06 | Klant kan het aantal verkochte fysieke tickets corrigeren | Klant | Via "pas verkoop aan" functie. Kan niet hoger dan totaal fysieke tickets of lager dan 0. |
| F-FT-07 | Na generatie wordt de fysieke ticket-PDF per e-mail naar de klant gestuurd | Systeem | E-mail bevat PDF als bijlage met printinstructies. |
| F-FT-08 | Fysieke tickets bevatten op de achterkant: bedrijfsinformatie, evenementdetails, ticketcode en voorwaarden | Systeem | Layout toont 3-punts disclaimer over persoonlijk gebruik, ID-plicht en kopieerverbod. |

### 2.4 Ticketscanning

| ID | Eis | Actor | Details |
|----|-----|-------|---------|
| F-SC-01 | Scanner kan QR-codes scannen via camera op mobiel apparaat (10 FPS) | Scanner | Ondersteunt achter- en voorcamera. |
| F-SC-02 | Systeem valideert de QR-code handtekening (HMAC) | Systeem | Ongeldige handtekening resulteert in foutmelding. |
| F-SC-03 | Systeem markeert ticket als gescand met tijdstip | Systeem | `scanned=true`, `scannedAt=now()`. |
| F-SC-04 | Dubbel scannen wordt gedetecteerd en gemeld met waarschuwing | Systeem | Toont foutmelding dat ticket al is gescand. |
| F-SC-05 | Scanner moet een evenement selecteren vóór het scannen | Scanner | Dropdown met gepubliceerde evenementen, doorzoekbaar. |
| F-SC-06 | Scanner kan QR-code ook handmatig invoeren als alternatief voor camera | Scanner | Tekstinvoer met enter-toets of knop. |
| F-SC-07 | Scanresultaat wordt visueel getoond met kleurcodering (groen=geldig, oranje=waarschuwing, rood=fout) | Scanner | Pop-up verdwijnt automatisch na 3 seconden. |
| F-SC-08 | Scangeschiedenis wordt bijgehouden gedurende de sessie | Scanner | Lijst van alle scans in de huidige sessie met status. |
| F-SC-09 | Ticket moet behoren tot het geselecteerde evenement en status CONFIRMED hebben | Systeem | Cross-event scanning wordt afgewezen. |
| F-SC-10 | Cooldown van 500ms tussen scans om dubbele scans te voorkomen | Systeem | Frontend-beveiliging tegen te snelle herhaalde scans. |

### 2.5 Klantbeheer (Multi-tenant)

| ID | Eis | Actor | Details |
|----|-----|-------|---------|
| F-KL-01 | Admin kan klanten (organisatoren) aanmaken met bedrijfsnaam, contactpersoon en e-mail | Admin | E-mailadres moet uniek zijn in het systeem. |
| F-KL-02 | Klant ontvangt een uitnodigingslink per e-mail om account te activeren (7 dagen geldig) | Systeem | Na activatie wordt het wachtwoord ingesteld en de inviteToken gewist. |
| F-KL-03 | Elke klant krijgt een unieke slug voor een publieke landingspagina (`/klant/{slug}`) | Systeem | Slug wordt automatisch gegenereerd: bedrijfsnaam → lowercase, diacrieten verwijderd, speciale tekens → koppeltekens. Bij duplicaat wordt een teller toegevoegd. |
| F-KL-04 | Klant kan huisstijl instellen: primaire kleur, secundaire kleur, logo en website | Klant | Kleuren in hex-formaat (#RRGGBB). Logo via bestandsupload. Huisstijl wordt toegepast op tickets en e-mails. |
| F-KL-04a | Klant kan een voorbeeld-ticket PDF downloaden met huidige huisstijl | Klant | Toont hoe een ticket eruitziet met de ingestelde branding (primaire kleur, logo, bedrijfsnaam). |
| F-KL-05 | Klant heeft een eigen dashboard met overzicht van eigen evenementen, verkoopstatistieken en omzet | Klant | Dashboard toont: totaal evenementen, totaal verkochte tickets, geschatte omzet. |
| F-KL-06 | Admin kan uitnodiging opnieuw versturen als klant nog geen wachtwoord heeft ingesteld | Admin | Nieuwe token wordt gegenereerd met nieuwe verloopdatum. |
| F-KL-07 | Admin kan klant activeren/deactiveren | Admin | Inactieve klanten kunnen niet inloggen en hun evenementen zijn niet zichtbaar. |
| F-KL-08 | Klant kan niet verwijderd worden als er actieve evenementen bestaan | Admin | Systeem geeft foutmelding (409 Conflict). |
| F-KL-09 | Klant kan eigen evenementen aanmaken, bewerken, publiceren en verwijderen | Klant | Klant kan alleen eigen evenementen bewerken (eigendomscontrole). |
| F-KL-10 | Klant heeft een optionele publieke landingspagina met al hun gepubliceerde evenementen | Gebruiker | Toegankelijk via `/klant/{slug}`. |

### 2.6 Authenticatie & Autorisatie

| ID | Eis | Actor | Details |
|----|-----|-------|---------|
| F-AU-01 | Admin, klant, gebruiker en scanner hebben gescheiden login-flows en token-namespaces | Alle | Tokens worden gescheiden door prefix: "admin\|", "user\|", scanner zonder prefix. |
| F-AU-02 | Tokens verlopen na 24 uur (configureerbaar) | Systeem | Verlooptijd wordt opgeslagen in het token zelf. |
| F-AU-03 | Wachtwoord-vergeten flow met reset-link per e-mail (1 uur geldig) | Klant, Gebruiker | Reset-token bevat huidige password hash; wordt ongeldig bij wachtwoordwijziging. |
| F-AU-04 | Eerste admin kan zichzelf registreren via setup-endpoint als er nog geen admin bestaat | Admin | Na eerste registratie wordt setup geblokkeerd. |
| F-AU-05 | Gebruikers kunnen zich registreren met e-mail, wachtwoord, naam en telefoonnummer | Gebruiker | E-mail moet uniek zijn. Wachtwoord minimaal 6 tekens. |
| F-AU-05a | Gebruikers kunnen hun profiel bijwerken (naam, telefoon, adresgegevens) | Gebruiker | Adresvelden: straat, huisnummer, postcode, plaats. Worden ook gebruikt om bestelformulier voor te vullen. |
| F-AU-06 | Scanner-accounts worden beheerd door admin (aanmaken, verwijderen, activeren/deactiveren) | Admin | Scanner logt in met gebruikersnaam en wachtwoord (geen e-mail). |
| F-AU-07 | Elke login-flow retourneert een Bearer-token dat meegegeven wordt in de Authorization-header | Alle | Frontend slaat tokens op in localStorage per rol. |
| F-AU-08 | Wachtwoord-vergeten endpoint retourneert altijd hetzelfde bericht (geen user enumeration) | Systeem | "Als er een account bestaat met dit e-mailadres, is er een e-mail verzonden." |
| F-AU-09 | Inactieve accounts (admin, klant, scanner) kunnen niet inloggen, ook niet met geldig token | Systeem | Token-validatie controleert actief-status. |

### 2.7 E-mail

| ID | Eis | Actor | Details |
|----|-----|-------|---------|
| F-EM-01 | Bevestigingsmail met PDF-tickets na succesvolle bestelling | Systeem | Bevat: evenementdetails, aantal tickets, prijsopbouw, PDF als bijlage. Afzendernaam is de bedrijfsnaam van de organisator. |
| F-EM-02 | Uitnodigingsmail voor nieuwe klanten met activatielink (7 dagen geldig) | Systeem | Bevat: accountgegevens, activatielink, beschrijving van beschikbare functies. |
| F-EM-03 | Wachtwoord-reset e-mails met reset-link (1 uur geldig) | Systeem | Beschikbaar voor klanten en eindgebruikers. |
| F-EM-04 | Automatische retry bij mislukte e-mailverzending (max 5 pogingen, elke 120 seconden) | Systeem | Retry-teller en laatste poging worden bijgehouden per bestelling. |
| F-EM-05 | E-mail met fysieke ticket-PDF na generatie | Systeem | Bevat printinstructies en aantal gegenereerde tickets. |
| F-EM-06 | Dynamisch afzenderadres per klant: `{bedrijfsnaam}@ticketing.lockitree.com` | Systeem | Fallback naar `noreply@ticketing.lockitree.com` bij ongeldig adres. |

### 2.8 Afbeeldingenbeheer

| ID | Eis | Actor | Details |
|----|-----|-------|---------|
| F-IM-01 | Uploaden van afbeeldingen voor evenementen en logo's | Klant, Admin | Maximaal 5MB, toegestane typen: JPEG, PNG, GIF, WebP. |
| F-IM-02 | Afbeeldingen worden opgeslagen met unieke UUID-bestandsnaam | Systeem | Oorspronkelijke bestandsnaam wordt niet bewaard. |
| F-IM-03 | Afbeeldingen zijn publiek toegankelijk via `/api/images/{filename}` | Alle | Caching: 24 uur. Path-traversal preventie op bestandsnamen. |
| F-IM-04 | Afbeeldingen uploaden vereist authenticatie (klant of admin) | Systeem | Niet-geauthenticeerde uploads worden geweigerd. |

---

## 3. Gebruikersflows

### 3.1 Ticket Kopen (Gebruiker)

**Hoofdflow:**
```
1. Gebruiker bezoekt homepage → overzicht van gepubliceerde evenementen
2. Klik op evenement → detailpagina met beschrijving, datum, locatie, prijzen
3. Selecteer ticketcategorie (indien beschikbaar) en aantal tickets
4. Vul bestelformulier in: voornaam, achternaam, e-mail, (telefoon), aantal tickets
5. Klik "Bestelling Plaatsen" → bestelling status: RESERVED, timer: 10 minuten
6. Bestelling wordt toegevoegd aan winkelwagen (gereserveerde bestellingen)
7. Ga naar bevestigingspagina (/order/{orderNumber})
8. Vul adresgegevens in: straat, huisnummer, postcode, plaats → Sla op
9. Klik "Bestelling Bevestigen" → status: CONFIRMED
10. E-mail met PDF-tickets wordt automatisch verstuurd
11. PDF kan opnieuw gedownload worden via de bevestigingspagina
```

**Stap-indicator op bevestigingspagina:**
```
Stap 1: Gereserveerd  →  Stap 2: Adres invullen  →  Stap 3: Bevestigd
```

**Alternatieve flows:**
- **Timeout:** Niet bevestigd binnen 10 minuten → status: EXPIRED → tickets vrijgegeven → countdown op scherm toont "Verlopen".
- **Ingelogde gebruiker:** Formulier wordt automatisch voorgevuld met naam, e-mail en telefoon.
- **Uitverkocht:** Bestelknop is uitgeschakeld, melding "Uitverkocht" wordt getoond.
- **E-mail mislukt:** Systeem probeert tot 5 keer opnieuw (elke 120 seconden).

### 3.2 Evenement Aanmaken (Klant)

**Hoofdflow:**
```
1. Klant logt in via /klant/login → redirect naar dashboard
2. Dashboard toont: statistieken (evenementen, tickets verkocht, omzet) + evenementlijst
3. Klik "Nieuw Evenement" → dialoogvenster opent
4. Vul in: naam, beschrijving, startdatum, (einddatum), locatie, (adres),
   totaal tickets, (fysieke tickets), ticketprijs, (servicekosten),
   (max per bestelling), (afbeelding uploaden), (beschikbaarheid tonen)
5. Optioneel: Voeg ticketcategorieën toe met eigen naam, prijs, capaciteit en geldigheidsdata
6. Opslaan → evenement status: DRAFT (Concept)
7. Klik "Publiceren" → status: PUBLISHED → evenement zichtbaar voor eindgebruikers
```

**Aanvullende acties op dashboard:**
- **Bewerken:** Klik op bewerkicoon → dialoogvenster met voorgevulde gegevens.
- **Verwijderen:** Alleen mogelijk als er geen tickets zijn verkocht.
- **Fysieke tickets genereren:** Klik "Genereer" → PDF download + e-mail naar klant.
- **Fysieke tickets downloaden:** Klik "Download" → eerder gegenereerde PDF opnieuw ophalen.
- **Fysieke verkoop registreren:** Invoer aantal verkochte fysieke tickets.
- **Verkoopinzicht:** Pop-up met gedetailleerde omzet- en ticketstatistieken.
- **Ticketcategorieën beheren:** Toevoegen, bewerken en verwijderen van categorieën per evenement.
- **Branding instellen:** Dialoogvenster met logo-upload, kleurkiezer (16 presets), website-URL, voorbeeld-ticket PDF.

### 3.3 Tickets Scannen (Scanner)

**Hoofdflow:**
```
1. Scanner logt in via /scan/login met gebruikersnaam + wachtwoord
2. Selecteer evenement uit dropdown (gepubliceerde evenementen, doorzoekbaar)
3. Start camera → live videofeed met QR-code detectie (10 FPS)
4. QR-code gedetecteerd → automatisch verstuurd naar backend
5. Resultaat getoond als popup:
   - Groen: Ticket geldig, succesvol gescand
   - Oranje: Waarschuwing (bijv. al gescand)
   - Rood: Fout (ongeldig ticket, verkeerd evenement)
6. Popup verdwijnt na 3 seconden
7. Scan toegevoegd aan sessiegeschiedenis
```

**Alternatieve flow (handmatig):**
```
1. Voer QR-code data handmatig in via tekstveld
2. Druk op Enter of klik "Scan" knop
3. Zelfde validatie en resultaatweergave als bij camera
```

**Foutscenario's:**
- Camera niet beschikbaar → handmatige invoer als fallback
- QR-code ongeldig (verkeerde HMAC) → rode foutmelding
- Ticket al gescand → oranje waarschuwing met timestamp van eerste scan
- Verkeerd evenement → foutmelding dat ticket niet bij dit evenement hoort

### 3.4 Klant Onboarding (Admin)

**Hoofdflow:**
```
1. Admin logt in via /admin/login
2. Navigeer naar Klantbeheer (/admin/customers)
3. Klik "Nieuwe Klant" → dialoogvenster opent
4. Vul in: bedrijfsnaam, contactpersoon, e-mail, (telefoon), (website),
   (logo URL), (primaire kleur), (secundaire kleur), actief-status
5. Opslaan → systeem genereert unieke slug en verstuurt uitnodigingsmail
6. Klant ontvangt e-mail met activatielink (7 dagen geldig)
7. Klant klikt link → wachtwoord instellen pagina (/klant/activeren/{token})
8. Klant stelt wachtwoord in → automatisch ingelogd → redirect naar dashboard
```

**Admin-acties op klantlijst:**
- **Bewerken:** Wijzig klantgegevens (bedrijfsnaam, contactpersoon, e-mail, etc.)
- **Uitnodiging opnieuw sturen:** Alleen mogelijk als klant nog geen wachtwoord heeft.
- **Verwijderen:** Alleen mogelijk als klant geen evenementen heeft.

### 3.5 Admin Setup (Eerste Gebruik)

```
1. Bezoek /admin/login → systeem detecteert dat er geen admin bestaat
2. Redirect naar setup-formulier
3. Vul in: e-mail, wachtwoord (min. 6 tekens), voornaam, achternaam
4. Account aangemaakt → automatisch ingelogd → redirect naar admin-dashboard
5. Setup-endpoint is daarna geblokkeerd
```

### 3.6 Wachtwoord Herstellen (Klant/Gebruiker)

```
1. Klik "Wachtwoord vergeten" op loginpagina
2. Voer e-mailadres in → systeem verstuurt altijd zelfde bevestigingsbericht
3. Als account bestaat: e-mail met reset-link (1 uur geldig)
4. Klik op reset-link → wachtwoord-reset pagina
5. Stel nieuw wachtwoord in → bevestigingsmelding → redirect naar login
```

### 3.7 Gebruikersregistratie

```
1. Bezoek /login → klik "Registreren"
2. Vul in: e-mail, wachtwoord (min. 6 tekens), voornaam, achternaam, (telefoon)
3. Account aangemaakt → automatisch ingelogd → redirect naar homepage
4. Naam, e-mail en adresgegevens worden bij volgende bestellingen automatisch ingevuld
5. Gebruiker kan profiel bijwerken via /my-tickets: naam, telefoon, straat, huisnummer, postcode, plaats
```

---

## 4. Niet-functionele Eisen

### 4.1 Performance & Limieten

| ID | Eis | Waarde | Toelichting |
|----|-----|--------|-------------|
| NF-01 | Maximale bestandsgrootte voor uploads | 5 MB | Server-side validatie in ImageResource |
| NF-02 | Ondersteunde bestandstypen | JPEG, PNG, GIF, WebP | MIME-type validatie op server |
| NF-03 | Maximaal tickets per bestelling | 1-10 (configureerbaar per evenement) | Standaard 10, instelbaar via maxTicketsPerOrder |
| NF-04 | Reserveringstimeout | 10 minuten (configureerbaar) | Via `ticket.reservation.timeout-minutes` |
| NF-05 | Maximaal aantal tickets per evenement | 100.000 | Database constraint |
| NF-06 | QR-code afmetingen | 300×300 pixels | PNG-formaat |
| NF-07 | E-mail retry-interval | 120 seconden | Maximaal 5 pogingen per bestelling |
| NF-08 | Reservering cleanup-interval | 60 seconden | Achtergrondtaak in ReservationCleanupService |

### 4.2 Beveiliging

| ID | Eis | Toelichting |
|----|-----|-------------|
| NF-10 | Token-verloop | 24 uur (configureerbaar via `AUTH_TOKEN_EXPIRY_HOURS`) |
| NF-11 | Reset-token verloop | 1 uur (hardcoded) |
| NF-12 | Uitnodigingstoken verloop | 7 dagen |
| NF-13 | Wachtwoorden worden gehashed opgeslagen | SHA-256 met salt-prefix (auth secret) |
| NF-14 | QR-codes zijn HMAC-SHA256 gesigneerd | Voorkomt vervalsing van tickets |
| NF-15 | Path-traversal preventie op bestandsuploads | Bestandsnamen met `..`, `/` of `\` worden geweigerd |
| NF-16 | Geen user enumeration via wachtwoord-vergeten | Altijd hetzelfde bericht ongeacht of account bestaat |
| NF-17 | CORS-configuratie via environment variabele | Alleen geconfigureerde origins toegestaan |

### 4.3 Schaalbaarheid & Beschikbaarheid

| ID | Eis | Toelichting |
|----|-----|-------------|
| NF-20 | Horizontale schaalbaarheid backend | 2-10 pods via Horizontal Pod Autoscaler |
| NF-21 | CPU-drempel voor schaling | 70% CPU-gebruik |
| NF-22 | Geheugen-drempel voor schaling | 80% geheugengebruik |
| NF-23 | Zero-downtime deployments | Rolling update met maxUnavailable: 0 |
| NF-24 | Health-endpoints voor monitoring | Liveness-probe (GET /api/health/live) en readiness-probe (GET /api/health/ready) |
| NF-25 | Database persistentie | 5 GiB PersistentVolumeClaim voor PostgreSQL |

### 4.4 Gebruiksvriendelijkheid

| ID | Eis | Toelichting |
|----|-----|-------------|
| NF-30 | Taal van de applicatie | Nederlands (foutmeldingen, labels, e-mails) |
| NF-31 | Datumnotatie | Nederlands formaat (bijv. "15 juli 2026 14:00") |
| NF-32 | Valutanotatie | Euro (€), 2 decimalen |
| NF-33 | Responsief ontwerp | Werkt op desktop en mobiel (PrimeNG responsive components) |
| NF-34 | Scroll-animaties | AOS-bibliotheek voor visuele verrijking |
| NF-35 | Kleurconsistentie | Huisstijlkleuren van klant worden toegepast op tickets, e-mails en PDF's |

---

## 5. Foutafhandeling

### 5.1 HTTP Statuscodes

| Code | Betekenis | Voorbeeld |
|------|-----------|-----------|
| 200 | Succesvol | Evenement opgehaald |
| 201 | Aangemaakt | Nieuw evenement of klant aangemaakt |
| 204 | Geen inhoud | Succesvol verwijderd |
| 400 | Validatiefout | Ontbrekende verplichte velden, ongeldig e-mailadres |
| 401 | Niet geautoriseerd | Ongeldig of verlopen token |
| 403 | Geen toegang | Klant probeert evenement van andere klant te bewerken |
| 404 | Niet gevonden | Evenement, bestelling of ticket niet gevonden |
| 409 | Conflict | E-mailadres al in gebruik, klant heeft actieve evenementen |
| 500 | Interne fout | PDF-generatie fout, e-mailverzending fout |

### 5.2 Validatiemeldingen (Nederlands)

| Context | Melding |
|---------|---------|
| Verplicht veld leeg | "{Veldnaam} is verplicht" |
| Ongeldig e-mailadres | "Ongeldig e-mailadres" |
| Tickets uitverkocht | "Er zijn niet genoeg tickets beschikbaar" |
| Reservering verlopen | "De reservering is verlopen" |
| Al gescand | "Dit ticket is al gescand" |
| Uitnodiging verlopen | "Uitnodiging is verlopen" |
| Admin al aanwezig | "Er bestaat al een admin account. Gebruik /login." |
