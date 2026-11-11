package tech.csm.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import tech.csm.model.EmployeeSkill;

/**
 * Home dashboard ki read-only queries (bench-skills, pending decisions). Koi entity ya table naya nahi -
 * Repository<EmployeeSkill,...> sirf anchor hai, saari methods @Query hain.
 */
public interface HomeDashboardRepository extends Repository<EmployeeSkill, Integer> {

    /**
     * Free employees (current availability BENCH/PARTIALLY_AVAILABLE, window me koi occupying allocation nahi,
     * latest decision SELECTED-bina-allocation nahi) ki active skills, skill ke hisaab se count.
     * Rows = [skill_id, skill_name, count]. Pageable se top-N.
     */
    @Query("select s.skillId, s.skillName, count(distinct es.employee.employeeId)"
            + " from EmployeeSkill es join es.skill s"
            + " where es.isActive = 1"
            + " and es.employee.employeeId in ("
            + "   select ra.employee.employeeId from ResourceAvailability ra"
            + "   where ra.availabilityStatus in :statuses"
            + "   and ra.availabilityId = (select max(r2.availabilityId) from ResourceAvailability r2"
            + "                            where r2.employee = ra.employee))"
            + " and not exists (select 1 from EmployeeAllocation a"
            + "   where a.employeeId = es.employee.employeeId and a.allocationStatus in :occupying"
            + "   and a.startDate <= :windowTo and (a.endDate is null or a.endDate >= :windowFrom))"
            + " and not exists (select 1 from AllocationDecision d, MatchingResult mr"
            + "   where mr.matchingResultId = d.matchingResultId and mr.employeeId = es.employee.employeeId"
            + "   and d.decision = 'SELECTED' and d.allocationId is null"
            + "   and d.allocationDecisionId = (select max(d2.allocationDecisionId)"
            + "       from AllocationDecision d2, MatchingResult mr2"
            + "       where mr2.matchingResultId = d2.matchingResultId and mr2.employeeId = mr.employeeId"
            + "       and d2.requirementId = d.requirementId))"
            + " group by s.skillId, s.skillName"
            + " order by count(distinct es.employee.employeeId) desc, s.skillName asc")
    List<Object[]> benchSkills(@Param("statuses") Collection<String> statuses,
                               @Param("occupying") Collection<String> occupying,
                               @Param("windowFrom") LocalDate windowFrom,
                               @Param("windowTo") LocalDate windowTo,
                               Pageable pageable);

    // ---------- SHORTLISTED: RECOMMENDED aur abhi tak koi allocation_decisions row nahi ----------

    /**
     * Rows = [matching_result_id, matching_run_id, requirement_id, employee_id, employee_name, designation,
     * role_name, project_name, overall_score, match_category, waiting_since(created_at)].
     */
    @Query("select mr.matchingResultId, run.matchingRunId, pr.requirementId, e.employeeId, e.name, des.designationName,"
            + " pr.roleName, p.projectName, mr.overallScore, mr.matchCategory, mr.createdAt"
            + " from MatchingResult mr"
            + " join MatchingRun run on run.matchingRunId = mr.matchingRunId"
            + " join ProjectRequirement pr on pr.requirementId = run.requirementId"
            + " join Employees e on e.employeeId = mr.employeeId"
            + " left join e.designation des"
            + " left join Project p on p.projectId = pr.projectId"
            + " where mr.recommendationStatus = 'RECOMMENDED'"
            + " and not exists (select 1 from AllocationDecision d where d.matchingResultId = mr.matchingResultId)"
            + " order by mr.createdAt desc, mr.matchingResultId desc")
    List<Object[]> shortlisted(Pageable pageable);

    /** Ek row: [count, oldest created_at]. */
    @Query("select count(mr), min(mr.createdAt) from MatchingResult mr"
            + " where mr.recommendationStatus = 'RECOMMENDED'"
            + " and not exists (select 1 from AllocationDecision d where d.matchingResultId = mr.matchingResultId)")
    List<Object[]> shortlistedStats();

    // ---------- APPROVED_PENDING: matching_result ka sabse naya decision SELECTED aur allocation_id null ----------

    /** Same row shape as shortlisted(); waiting_since = decision ka created_at (decision_date me IST/UTC mix hai). */
    @Query("select mr.matchingResultId, run.matchingRunId, pr.requirementId, e.employeeId, e.name, des.designationName,"
            + " pr.roleName, p.projectName, mr.overallScore, mr.matchCategory, coalesce(d.createdAt, d.decisionDate)"
            + " from AllocationDecision d"
            + " join MatchingResult mr on mr.matchingResultId = d.matchingResultId"
            + " join MatchingRun run on run.matchingRunId = mr.matchingRunId"
            + " join ProjectRequirement pr on pr.requirementId = run.requirementId"
            + " join Employees e on e.employeeId = mr.employeeId"
            + " left join e.designation des"
            + " left join Project p on p.projectId = pr.projectId"
            + " where d.decision = 'SELECTED' and d.allocationId is null"
            + " and d.allocationDecisionId = (select max(d2.allocationDecisionId) from AllocationDecision d2"
            + "     where d2.matchingResultId = d.matchingResultId)"
            + " order by coalesce(d.createdAt, d.decisionDate) desc, d.allocationDecisionId desc")
    List<Object[]> approvedPending(Pageable pageable);

    /** Ek row: [count, oldest waiting_since]. */
    @Query("select count(d), min(coalesce(d.createdAt, d.decisionDate)) from AllocationDecision d"
            + " where d.decision = 'SELECTED' and d.allocationId is null"
            + " and d.allocationDecisionId = (select max(d2.allocationDecisionId) from AllocationDecision d2"
            + "     where d2.matchingResultId = d.matchingResultId)")
    List<Object[]> approvedPendingStats();
}
