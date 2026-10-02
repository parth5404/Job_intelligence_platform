package com.samyak.job_intelligence.company.web;

import com.samyak.job_intelligence.company.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class CompanyControllerIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("job_intelligence_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerCandidateAndGetAccessToken() {
        String registerBody = """
                {
                    "name": "Company Tester",
                    "email": "company-tester@example.com",
                    "password": "correct-horse-battery",
                    "confirmPassword": "correct-horse-battery",
                    "preferredLocations": ["Bangalore"],
                    "experienceYears": 2,
                    "preferredEmploymentTypes": ["FULL_TIME"]
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/register",
                new HttpEntity<>(registerBody, headers),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return objectMapper.readTree(response.getBody()).get("accessToken").asString();
    }

    @Test
    void shouldRejectCompanyCreationWithoutAuthentication() {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/api/v1/companies",
                        new HttpEntity<>("""
                                {
                                    "canonicalName": "unauthenticated-co",
                                    "displayName": "Unauthenticated Co",
                                    "websiteUrl": "https://example.com"
                                }
                                """, headers),
                        String.class
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(companyRepository.findByCanonicalName("unauthenticated-co"))
                .isEmpty();
    }

    @Test
    void shouldCreateCompany() {

        String requestBody = """
                {
                    "canonicalName": "google",
                    "displayName": "Google",
                    "websiteUrl": "https://www.google.com"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(registerCandidateAndGetAccessToken());

        HttpEntity<String> request =
                new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/api/v1/companies",
                        request,
                        String.class
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(companyRepository.findByCanonicalName("google"))
                .isPresent();
    }
}