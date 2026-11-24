package com.example.hibernatelab.g14_lead.i3_buggy;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface I3BuggyJobRepository extends JpaRepository<I3BuggyJob, Long> {

	/** KHÔNG khoá: hai worker cùng gọi sẽ cùng thấy việc NEW đầu tiên (G11-S09b). */
	@Query("select j from G14I3B_Job j where j.status = 'NEW' order by j.id")
	List<I3BuggyJob> findNewJobs(Limit limit);
}
