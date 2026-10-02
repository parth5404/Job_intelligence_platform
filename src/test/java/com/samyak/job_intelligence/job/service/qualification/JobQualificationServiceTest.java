package com.samyak.job_intelligence.job.service.qualification;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.normalization.CandidateTextNormalizer;
import com.samyak.job_intelligence.candidate.service.CandidateEmploymentTypeService;
import com.samyak.job_intelligence.candidate.service.CandidateLocationService;
import com.samyak.job_intelligence.company.domain.Company;
import com.samyak.job_intelligence.job.domain.EmploymentType;
import com.samyak.job_intelligence.job.domain.Job;
import com.samyak.job_intelligence.job.domain.JobLocation;
import com.samyak.job_intelligence.job.domain.RemoteType;
import com.samyak.job_intelligence.job.domain.SeniorityLevel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobQualificationServiceTest {

    private final CandidateLocationService candidateLocationService =
            new CandidateLocationService(new CandidateTextNormalizer());

    private final CandidateEmploymentTypeService candidateEmploymentTypeService =
            new CandidateEmploymentTypeService();
    private final CandidateTextNormalizer candidateTextNormalizer=
            new CandidateTextNormalizer();

    private Job createJob(
            BigDecimal experienceMin,
            BigDecimal experienceMax,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            EmploymentType employmentType
    ) {
        Company company = new Company(
                "example-company",
                "Example Company",
                "https://example.com"
        );

        return new Job(
                company,
                "Software Engineer",
                "software engineer",
                "Backend engineering role.",
                employmentType,
                SeniorityLevel.MID,
                experienceMin,
                experienceMax,
                salaryMin,
                salaryMax,
                salaryCurrency,
                Instant.now(),
                null,
                "https://example.com/apply/123",
                "fingerprint-123",
                "description-hash"
        );
    }

    private JobLocation createLocation(
            Job job,
            String city,
            String state,
            String country,
            String displayText
    ) {
        return new JobLocation(
                job,
                city,
                state,
                country,
                RemoteType.UNKNOWN,
                displayText
        );
    }

    private CandidateProfile createCandidate(
            JsonNode preferredLocations,
            BigDecimal minimumSalary,
            String minimumSalaryCurrency,
            BigDecimal experienceYears,
            JsonNode preferredEmploymentTypes
    ) {
        return new CandidateProfile(
                "Samyak",
                "samyak@example.com",
                "test-password-hash",
                "Bhopal",
                preferredLocations,
                minimumSalary,
                minimumSalaryCurrency,
                true,
                experienceYears,
                preferredEmploymentTypes
        );
    }

    private final JobQualificationService service =
            new JobQualificationService(
                    candidateLocationService,
                    candidateEmploymentTypeService,
                    candidateTextNormalizer
            );

    @Test
    void shouldRejectWhenRequiredExperienceExceedsCandidateExperience() {

        Job job = createJob(
                new BigDecimal("3"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertFalse(result.qualified());

        assertEquals(
                1,
                result.rejectionReasons().size()
        );

        assertEquals(
                "Required experience exceeds candidate experience",
                result.rejectionReasons().getFirst()
        );
    }

    @Test
    void shouldQualifyWhenCandidateExperienceMatchesMinimumRequirement() {

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldQualifyWhenJobHasNoExperienceRequirement() {

        Job job = createJob(
                null,
                null,
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldQualifyWhenCandidateExperienceIsUnknown() {

        Job job = createJob(
                new BigDecimal("3"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                null,
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldRejectWhenJobMaximumSalaryIsBelowCandidateMinimumSalary() {

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                new BigDecimal("800000"),
                new BigDecimal("1000000"),
                "INR",
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                new BigDecimal("1200000"),
                "INR",
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertFalse(result.qualified());

        assertEquals(
                1,
                result.rejectionReasons().size()
        );

        assertEquals(
                "Job maximum salary is below candidate minimum salary",
                result.rejectionReasons().getFirst()
        );
    }

    @Test
    void shouldQualifyWhenJobMaximumSalaryMatchesCandidateMinimumSalary() {

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                new BigDecimal("800000"),
                new BigDecimal("1200000"),
                "INR",
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                new BigDecimal("1200000"),
                "INR",
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldQualifyWhenJobSalaryIsUnknown() {

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                new BigDecimal("1200000"),
                "INR",
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldQualifyWhenCandidateMinimumSalaryIsUnknown() {

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                new BigDecimal("800000"),
                new BigDecimal("1000000"),
                "INR",
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldQualifyWhenSalaryCurrenciesDoNotMatch() {

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                new BigDecimal("80000"),
                new BigDecimal("100000"),
                "USD",
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                new BigDecimal("1200000"),
                "INR",
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldQualifyWhenJobLocationMatchesCandidatePreference() throws Exception {

        JsonNode preferredLocations =
                new ObjectMapper().readTree("""
                        [
                            "Bangalore"
                        ]
                        """);

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        List<JobLocation> locations = List.of(
                createLocation(
                        job,
                        "bangalore",
                        "karnataka",
                        "india",
                        "bangalore karnataka india"
                )
        );

        CandidateProfile candidate = createCandidate(
                preferredLocations,
                new BigDecimal("1200000"),
                "INR",
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        locations
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldRejectWhenNoJobLocationMatchesCandidatePreference() throws Exception {

        JsonNode preferredLocations =
                new ObjectMapper().readTree("""
                        [
                            "Bangalore"
                        ]
                        """);

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        List<JobLocation> locations = List.of(
                createLocation(
                        job,
                        "mumbai",
                        "maharashtra",
                        "india",
                        "mumbai region"
                )
        );

        CandidateProfile candidate = createCandidate(
                preferredLocations,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        locations
                );

        assertFalse(result.qualified());
    }

    @Test
    void shouldQualifyWhenCandidateHasNoPreferredLocations() {

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        List<JobLocation> locations = List.of(
                createLocation(
                        job,
                        "bangalore",
                        "karnataka",
                        "india",
                        "bangalore karnataka india"
                )
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        locations
                );

        assertTrue(result.qualified());
    }

    @Test
    void shouldQualifyWhenAnyJobLocationMatchesCandidatePreference() throws Exception {

        JsonNode preferredLocations =
                new ObjectMapper().readTree("""
                        [
                            "Bangalore"
                        ]
                        """);

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        List<JobLocation> locations = List.of(
                createLocation(
                        job,
                        "mumbai",
                        "maharashtra",
                        "india",
                        "mumbai region"
                ),
                createLocation(
                        job,
                        "bangalore",
                        "karnataka",
                        "india",
                        "bangalore region"
                )
        );

        CandidateProfile candidate = createCandidate(
                preferredLocations,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        locations
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldReturnAllRejectionReasonsWhenMultipleRulesFail() throws Exception {

        JsonNode preferredLocations =
                new ObjectMapper().readTree("""
                        [
                            "Bangalore"
                        ]
                        """);

        Job job = createJob(
                new BigDecimal("5"),
                new BigDecimal("7"),
                new BigDecimal("600000"),
                new BigDecimal("800000"),
                "INR",
                EmploymentType.FULL_TIME
        );

        List<JobLocation> locations = List.of(
                createLocation(
                        job,
                        "mumbai",
                        "maharashtra",
                        "india",
                        "mumbai region"
                )
        );

        CandidateProfile candidate = createCandidate(
                preferredLocations,
                new BigDecimal("1200000"),
                "INR",
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        locations
                );

        assertFalse(result.qualified());

        assertEquals(
                3,
                result.rejectionReasons().size()
        );

        assertTrue(result.rejectionReasons().contains(
                "Required experience exceeds candidate experience"
        ));

        assertTrue(result.rejectionReasons().contains(
                "Job maximum salary is below candidate minimum salary"
        ));

        assertTrue(result.rejectionReasons().contains(
                "Job location does not match candidate preferred locations"
        ));
    }

    @Test
    void shouldQualifyWhenJobEmploymentTypeMatchesPreference() throws Exception {

        JsonNode preferredTypes =
                new ObjectMapper().readTree("""
                        ["FULL_TIME"]
                        """);

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                preferredTypes
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }

    @Test
    void shouldRejectWhenJobEmploymentTypeDoesNotMatchPreference() throws Exception {

        JsonNode preferredTypes =
                new ObjectMapper().readTree("""
                        ["FULL_TIME"]
                        """);

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.INTERNSHIP
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                preferredTypes
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertFalse(result.qualified());

        assertTrue(result.rejectionReasons().contains(
                "Job employment type does not match candidate preferences"
        ));
    }

    @Test
    void shouldQualifyWhenJobMatchesAnyPreferredEmploymentType() throws Exception {

        JsonNode preferredTypes =
                new ObjectMapper().readTree("""
                        ["FULL_TIME", "CONTRACT"]
                        """);

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.CONTRACT
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                preferredTypes
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
    }

    @Test
    void shouldQualifyWhenCandidateHasNoEmploymentTypePreference() {

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.INTERNSHIP
        );

        CandidateProfile candidate = createCandidate(
                null,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        List.of()
                );

        assertTrue(result.qualified());
    }

    @Test
    void shouldNotRejectWhenJobLocationIsUnknown() throws Exception {

        JsonNode preferredLocations =
                new ObjectMapper().readTree("""
                [
                    "Bangalore"
                ]
                """);

        Job job = createJob(
                new BigDecimal("2"),
                new BigDecimal("5"),
                null,
                null,
                null,
                EmploymentType.FULL_TIME
        );

        List<JobLocation> locations = List.of(
                createLocation(
                        job,
                        null,
                        null,
                        null,
                        null
                )
        );

        CandidateProfile candidate = createCandidate(
                preferredLocations,
                null,
                null,
                new BigDecimal("2"),
                null
        );

        JobQualificationResult result =
                service.qualify(
                        job,
                        candidate,
                        locations
                );

        assertTrue(result.qualified());
        assertTrue(result.rejectionReasons().isEmpty());
    }
}