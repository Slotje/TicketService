package nl.ticketservice.dto;

import java.math.BigDecimal;

public record TicketSalesDTO(
        Long eventId,
        String eventName,
        String eventStatus,

        // Totals
        int maxTickets,
        int physicalTickets,
        int onlineTickets,
        int totalSold,
        int totalRemaining,

        // Online breakdown
        int onlineSold,
        int onlineReserved,
        int onlineAvailable,

        // Physical breakdown
        int physicalSold,
        int physicalAvailable,
        boolean physicalTicketsGenerated,

        // Revenue
        BigDecimal ticketPrice,
        BigDecimal serviceFeePerTicket,
        BigDecimal effectiveOnlineServiceFee,
        BigDecimal totalOnlineRevenue,
        BigDecimal totalPhysicalRevenue,
        BigDecimal totalServiceFeeRevenue,
        BigDecimal totalRevenue,

        // Scanning stats
        int ticketsScanned,
        int ticketsNotScanned
) {}
