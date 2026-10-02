package com.samyak.job_intelligence.job.repository;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import com.samyak.job_intelligence.company.domain.Company;
import com.samyak.job_intelligence.company.repository.CompanyRepository;
import com.samyak.job_intelligence.job.domain.EmploymentType;
import com.samyak.job_intelligence.job.domain.Job;
import com.samyak.job_intelligence.job.domain.JobMatch;
import com.samyak.job_intelligence.job.domain.SeniorityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobMatchRepositoryTest {

    @Autowired
    private JobMatchRepository jobMatchRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private CandidateProfile candidateA;

    private Job job1;
    private Job job2;
    private Job job3;
    private Job job4;

    private Instant older;
    private Instant newer;

    @BeforeEach
    void setUp() {

        Company company = companyRepository.save(
                new Company(
                        "test-company",
                        "Test Company",
                        "https://test-company.com"
                )
        );

        candidateA = candidateProfileRepository.save(
                new CandidateProfile(
                        "Candidate A",
                        "candidate-a@test.com",
                        "test-password-hash",
                        "Bhopal",
                        null,
                        new BigDecimal("50000.00"),
                        "INR",
                        true,
                        new BigDecimal("3.0"),
                        null
                )
        );

        Instant postedAt =
                Instant.parse("2026-09-01T10:00:00Z");

        job1 = jobRepository.save(
                createJob(
                        company,
                        "Backend Engineer 1",
                        "fingerprint-job-1",
                        postedAt
                )
        );

        job2 = jobRepository.save(
                createJob(
                        company,
                        "Backend Engineer 2",
                        "fingerprint-job-2",
                        postedAt
                )
        );

        job3 = jobRepository.save(
                createJob(
                        company,
                        "Backend Engineer 3",
                        "fingerprint-job-3",
                        postedAt
                )
        );

        job4 = jobRepository.save(
                createJob(
                        company,
                        "Backend Engineer 4",
                        "fingerprint-job-4",
                        postedAt
                )
        );

        older = Instant.parse("2026-09-10T10:00:00Z");
        newer = Instant.parse("2026-09-11T10:00:00Z");

        jobMatchRepository.saveAll(List.of(

                // candidate A -> qualified, 90, newer
                createMatch(
                        job1,
                        candidateA,
                        true,
                        "90.00",
                        newer
                ),

                // candidate A -> qualified, 90, older
                createMatch(
                        job2,
                        candidateA,
                        true,
                        "90.00",
                        older
                ),

                // candidate A -> qualified, 80
                createMatch(
                        job3,
                        candidateA,
                        true,
                        "80.00",
                        newer
                ),

                // candidate A -> rejected, 100
                // Must NOT be returned.
                createMatch(
                        job4,
                        candidateA,
                        false,
                        "100.00",
                        newer
                )
        ));
    }

    @Test
    void shouldFindQualifiedMatchesOrderedByFinalScoreAndEvaluationTime() {

        List<JobMatch> matches =
                jobMatchRepository
                        .findCurrentlyEligibleMatches(
                                candidateA.getId()
                        );

        assertThat(matches)
                .hasSize(3);

        assertThat(matches.get(0).getJob())
                .isEqualTo(job1);

        assertThat(matches.get(1).getJob())
                .isEqualTo(job2);

        assertThat(matches.get(2).getJob())
                .isEqualTo(job3);

        assertThat(matches)
                .extracting(JobMatch::getFinalScore)
                .containsExactly(
                        new BigDecimal("90.00"),
                        new BigDecimal("90.00"),
                        new BigDecimal("80.00")
                );

        assertThat(matches)
                .extracting(JobMatch::getEvaluatedAt)
                .containsExactly(
                        newer,
                        older,
                        newer
                );

        assertThat(matches)
                .allMatch(JobMatch::isHardQualified);
    }

    private Job createJob(
            Company company,
            String title,
            String fingerprint,
            Instant postedAt
    ) {
        return new Job(
                company,
                title,
                title.toLowerCase(),
                "Test job description",
                EmploymentType.FULL_TIME,
                SeniorityLevel.MID,
                new BigDecimal("2.0"),
                new BigDecimal("5.0"),
                new BigDecimal("50000.00"),
                new BigDecimal("100000.00"),
                "INR",
                postedAt,
                null,
                "https://example.com/" + fingerprint,
                fingerprint,
                "description-hash-" + fingerprint
        );
    }
    private JobMatch createMatch(
            Job job,
            CandidateProfile candidate,
            boolean hardQualified,
            String finalScore,
            Instant evaluatedAt
    ) {
        return new JobMatch(
                job,
                candidate,
                hardQualified,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal(finalScore),
                null,
                null,
                null,
                null,
                evaluatedAt,
                List.of(),
                List.of()
        );
    }
}