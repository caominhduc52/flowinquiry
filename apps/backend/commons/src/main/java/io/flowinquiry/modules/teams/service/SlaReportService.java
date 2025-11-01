package io.flowinquiry.modules.teams.service;

import static io.flowinquiry.modules.teams.domain.WorkflowTransitionHistoryStatus.COMPLETED;

import io.flowinquiry.modules.teams.domain.WorkflowTransitionHistory;
import io.flowinquiry.modules.teams.repository.WorkflowTransitionHistoryRepository;
import io.flowinquiry.modules.teams.service.dto.SlaBreachGroupDTO;
import io.flowinquiry.modules.teams.service.dto.SlaBreachReportDTO;
import io.flowinquiry.modules.teams.service.dto.SlaBreachTicketDTO;
import io.flowinquiry.modules.teams.service.mapper.WorkflowTransitionHistoryMapper;
import io.flowinquiry.modules.usermanagement.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for generating SLA breach reports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaReportService {

    private final WorkflowTransitionHistoryRepository workflowTransitionHistoryRepository;
    private final WorkflowTransitionHistoryMapper workflowTransitionHistoryMapper;

    /**
     * Generates an SLA breach report with optional filtering and grouping.
     *
     * @param teamId The team ID to filter by (optional)
     * @param projectId The project ID to filter by (optional)
     * @param iterationId The iteration ID to filter by (optional)
     * @param assigneeId The assignee ID to filter by (optional)
     * @param priority The priority to filter by (optional)
     * @param fromDate The start date of the range (optional)
     * @param toDate The end date of the range (optional)
     * @param groupBy The field to group by (assignee, priority, or type)
     * @param pageable Pagination information for breached tickets
     * @return An SLA breach report
     */
    @Transactional(readOnly = true)
    public SlaBreachReportDTO generateSlaBreachReport(
            Long teamId,
            Long projectId,
            Long iterationId,
            Long assigneeId,
            Integer priority,
            Instant fromDate,
            Instant toDate,
            String groupBy,
            Pageable pageable) {

        // Count total tickets with SLA defined
        Long totalTicketsChecked = workflowTransitionHistoryRepository.countTicketsWithSla(
                teamId, projectId, iterationId, assigneeId, priority, fromDate, toDate);

        // Count breached tickets
        Long ticketsBreached = workflowTransitionHistoryRepository.countSlaBreachedTransitions(
                teamId, projectId, iterationId, assigneeId, priority, fromDate, toDate, COMPLETED);

        // Calculate breach rate
        Double breachRate = totalTicketsChecked > 0 
                ? (double) ticketsBreached / totalTicketsChecked * 100 
                : 0.0;

        // Get breached transitions with pagination
        Page<WorkflowTransitionHistory> breachedTransitions = 
                workflowTransitionHistoryRepository.findSlaBreachedTransitions(
                        teamId, projectId, iterationId, assigneeId, priority, 
                        fromDate, toDate, COMPLETED, pageable);

        // Convert to DTOs
        List<SlaBreachTicketDTO> breachedTickets = breachedTransitions.getContent().stream()
                .map(this::mapToSlaBreachTicketDTO)
                .collect(Collectors.toList());

        // Calculate average time over SLA
        Duration averageTimeOverSla = calculateAverageTimeOverSla(breachedTickets);

        // Group data if requested
        List<SlaBreachGroupDTO> groupedData = groupBy != null && !groupBy.isEmpty() 
                ? groupBreachedTickets(breachedTickets, groupBy)
                : new ArrayList<>();

        // Build and return the report
        return SlaBreachReportDTO.builder()
                .totalTicketsChecked(totalTicketsChecked)
                .ticketsBreached(ticketsBreached)
                .breachRate(breachRate)
                .averageTimeOverSla(averageTimeOverSla)
                .groupedData(groupedData)
                .breachedTickets(breachedTickets)
                .build();
    }

    /**
     * Maps a WorkflowTransitionHistory to an SlaBreachTicketDTO.
     */
    private SlaBreachTicketDTO mapToSlaBreachTicketDTO(WorkflowTransitionHistory history) {
        SlaBreachTicketDTO dto = new SlaBreachTicketDTO();

        dto.setId(history.getTicket().getId());
        dto.setTitle(history.getTicket().getRequestTitle());

        if (history.getTicket().getAssignUser() != null) {
            User assignUser = history.getTicket().getAssignUser();
            String fullName = (assignUser.getFirstName() != null ? assignUser.getFirstName() : "") +
                    " " + (assignUser.getLastName() != null ? assignUser.getLastName() : "");
            dto.setAssigneeName(fullName.trim());
        }

        if (history.getTicket().getPriority() != null) {
            dto.setPriority(history.getTicket().getPriority().getCode());
        }

        if (history.getTicket().getProject() != null) {
            dto.setProject(history.getTicket().getProject().getName());
        }

        if (history.getTicket().getIteration() != null) {
            dto.setIteration(history.getTicket().getIteration().getName());
        }

        dto.setSlaDueDate(history.getSlaDueDate());
        dto.setTransitionDate(history.getTransitionDate());
        dto.setStatus(history.getStatus().name());

        if (history.getTicket().getWorkflow() != null) {
            dto.setWorkflowName(history.getTicket().getWorkflow().getName());
        }

        if (history.getToState() != null) {
            dto.setStateName(history.getToState().getStateName());
        }

        // Calculate time over SLA
        if (history.getSlaDueDate() != null) {
            Duration timeOverSla = Duration.between(history.getSlaDueDate(), Instant.now());
            dto.setTimeOverSla(timeOverSla);
        }

        return dto;
    }

    /**
     * Calculates the average time over SLA for a list of breached tickets.
     */
    private Duration calculateAverageTimeOverSla(List<SlaBreachTicketDTO> breachedTickets) {
        if (breachedTickets.isEmpty()) {
            return Duration.ZERO;
        }

        long totalSeconds = breachedTickets.stream()
                .filter(ticket -> ticket.getTimeOverSla() != null)
                .mapToLong(ticket -> ticket.getTimeOverSla().getSeconds())
                .sum();

        return Duration.ofSeconds(totalSeconds / breachedTickets.size());
    }

    /**
     * Groups breached tickets by the specified field.
     */
    private List<SlaBreachGroupDTO> groupBreachedTickets(List<SlaBreachTicketDTO> breachedTickets, String groupBy) {
        Map<String, List<SlaBreachTicketDTO>> groupedTickets;

        // Group tickets by the specified field
        switch (groupBy.toLowerCase()) {
            case "assignee":
                groupedTickets = breachedTickets.stream()
                        .collect(Collectors.groupingBy(
                                ticket -> ticket.getAssigneeName() != null ? ticket.getAssigneeName() : "Unassigned"));
                break;
            case "priority":
                groupedTickets = breachedTickets.stream()
                        .collect(Collectors.groupingBy(
                                ticket -> ticket.getPriority() != null ? ticket.getPriority().toString() : "None"));
                break;
            case "type":
                groupedTickets = breachedTickets.stream()
                        .collect(Collectors.groupingBy(
                                ticket -> ticket.getType() != null ? ticket.getType() : "None"));
                break;
            default:
                return new ArrayList<>();
        }

        // Convert grouped tickets to SlaBreachGroupDTO
        return groupedTickets.entrySet().stream()
                .map(entry -> {
                    String groupKey = entry.getKey();
                    List<SlaBreachTicketDTO> tickets = entry.getValue();
                    long totalTickets = tickets.size();
                    long breachedTicketsCount = tickets.size(); // All tickets in this group are breached
                    double breachRate = 100.0; // 100% breach rate for this group
                    Duration averageTimeOverSla = calculateAverageTimeOverSla(tickets);

                    return SlaBreachGroupDTO.builder()
                            .groupKey(groupKey)
                            .totalTickets(totalTickets)
                            .breachedTickets(breachedTicketsCount)
                            .breachRate(breachRate)
                            .averageTimeOverSla(averageTimeOverSla)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
