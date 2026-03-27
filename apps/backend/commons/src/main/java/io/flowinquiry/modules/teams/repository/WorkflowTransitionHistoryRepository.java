package io.flowinquiry.modules.teams.repository;

import io.flowinquiry.modules.teams.domain.WorkflowTransitionHistory;
import io.flowinquiry.modules.teams.domain.WorkflowTransitionHistoryStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowTransitionHistoryRepository
        extends JpaRepository<WorkflowTransitionHistory, Long> {

    /**
     * Finds and sorts the workflow transition history for a specific ticket.
     *
     * @param ticketId the ID of the ticket
     * @return a list of WorkflowTransitionHistory sorted by transitionDate
     */
    @Query(
            "SELECT wth FROM WorkflowTransitionHistory wth "
                    + "WHERE wth.ticket.id = :ticketId "
                    + "ORDER BY wth.transitionDate ASC")
    List<WorkflowTransitionHistory> findByTicketId(@Param("ticketId") Long ticketId);

    @Query(
            """
    SELECT wth FROM WorkflowTransitionHistory wth
    WHERE wth.status = io.flowinquiry.modules.teams.domain.WorkflowTransitionHistoryStatus.IN_PROGRESS
    AND wth.slaDueDate <= :checkTime
    """)
    List<WorkflowTransitionHistory> findViolatingTransitions(@Param("checkTime") Instant checkTime);

    /**
     * Finds all SLA breached transitions within a date range with optional filtering.
     *
     * @param teamId The team ID to filter by (optional)
     * @param projectId The project ID to filter by (optional)
     * @param iterationId The iteration ID to filter by (optional)
     * @param assigneeId The assignee ID to filter by (optional)
     * @param priority The priority to filter by (optional)
     * @param fromDate The start date of the range (optional)
     * @param toDate The end date of the range (optional)
     * @param pageable Pagination information
     * @return A page of WorkflowTransitionHistory entries that breached SLA
     */
    @Query(
            """
    SELECT wth FROM WorkflowTransitionHistory wth
    JOIN wth.ticket t
    WHERE wth.slaDueDate IS NOT NULL
    AND wth.status <> :completedStatus
    AND wth.slaDueDate < CURRENT_TIMESTAMP
    AND (:teamId IS NULL OR t.team.id = :teamId)
    AND (:projectId IS NULL OR t.project.id = :projectId)
    AND (:iterationId IS NULL OR t.iteration.id = :iterationId)
    AND (:assigneeId IS NULL OR t.assignUser.id = :assigneeId)
    AND (:priority IS NULL OR t.priority = :priority)
    AND (:fromDate IS NULL OR wth.slaDueDate >= :fromDate)
    AND (:toDate IS NULL OR wth.slaDueDate <= :toDate)
    ORDER BY wth.slaDueDate ASC
    """)
    Page<WorkflowTransitionHistory> findSlaBreachedTransitions(
            @Param("teamId") Long teamId,
            @Param("projectId") Long projectId,
            @Param("iterationId") Long iterationId,
            @Param("assigneeId") Long assigneeId,
            @Param("priority") Integer priority,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("completedStatus") WorkflowTransitionHistoryStatus completedStatus,
            Pageable pageable);

    /**
     * Counts all SLA breached transitions within a date range with optional filtering.
     *
     * @param teamId The team ID to filter by (optional)
     * @param projectId The project ID to filter by (optional)
     * @param iterationId The iteration ID to filter by (optional)
     * @param assigneeId The assignee ID to filter by (optional)
     * @param priority The priority to filter by (optional)
     * @param fromDate The start date of the range (optional)
     * @param toDate The end date of the range (optional)
     * @return The count of WorkflowTransitionHistory entries that breached SLA
     */
    @Query(
            """
    SELECT COUNT(wth) FROM WorkflowTransitionHistory wth
    JOIN wth.ticket t
    WHERE wth.slaDueDate IS NOT NULL
    AND wth.status <> :completedStatus
    AND wth.slaDueDate < CURRENT_TIMESTAMP
    AND (:teamId IS NULL OR t.team.id = :teamId)
    AND (:projectId IS NULL OR t.project.id = :projectId)
    AND (:iterationId IS NULL OR t.iteration.id = :iterationId)
    AND (:assigneeId IS NULL OR t.assignUser.id = :assigneeId)
    AND (:priority IS NULL OR t.priority = :priority)
    AND (:fromDate IS NULL OR wth.slaDueDate >= :fromDate)
    AND (:toDate IS NULL OR wth.slaDueDate <= :toDate)
    """)
    Long countSlaBreachedTransitions(
            @Param("teamId") Long teamId,
            @Param("projectId") Long projectId,
            @Param("iterationId") Long iterationId,
            @Param("assigneeId") Long assigneeId,
            @Param("priority") Integer priority,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("completedStatus") WorkflowTransitionHistoryStatus completedStatus);

    /**
     * Counts all tickets within a date range with optional filtering.
     *
     * @param teamId The team ID to filter by (optional)
     * @param projectId The project ID to filter by (optional)
     * @param iterationId The iteration ID to filter by (optional)
     * @param assigneeId The assignee ID to filter by (optional)
     * @param priority The priority to filter by (optional)
     * @param fromDate The start date of the range (optional)
     * @param toDate The end date of the range (optional)
     * @return The count of tickets that have SLA defined
     */
    @Query(
            """
    SELECT COUNT(DISTINCT t.id) FROM WorkflowTransitionHistory wth
    JOIN wth.ticket t
    WHERE wth.slaDueDate IS NOT NULL
    AND (:teamId IS NULL OR t.team.id = :teamId)
    AND (:projectId IS NULL OR t.project.id = :projectId)
    AND (:iterationId IS NULL OR t.iteration.id = :iterationId)
    AND (:assigneeId IS NULL OR t.assignUser.id = :assigneeId)
    AND (:priority IS NULL OR t.priority = :priority)
    AND (:fromDate IS NULL OR wth.slaDueDate >= :fromDate)
    AND (:toDate IS NULL OR wth.slaDueDate <= :toDate)
    """)
    Long countTicketsWithSla(
            @Param("teamId") Long teamId,
            @Param("projectId") Long projectId,
            @Param("iterationId") Long iterationId,
            @Param("assigneeId") Long assigneeId,
            @Param("priority") Integer priority,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate);
}
