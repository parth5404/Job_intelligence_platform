package com.samyak.job_intelligence.candidate.domain;

import com.samyak.job_intelligence.common.persistence.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "candidate_profiles",indexes = {@Index(name="idx_candidate_profiles_active",columnList = "is_active")})
public class CandidateProfile extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false ,length = 255)
    private String name;

    @Column(nullable = false,length = 320,unique = true)
    private String email;

    /** BCrypt hash of the candidate's password. Internal authentication state: never serialized. */
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @JsonIgnore
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "current_location")
    private String currentLocation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_locations",columnDefinition = "jsonb")
    private JsonNode preferredLocations;

    @Column(name = "minimum_salary" ,precision = 12,scale = 2)
    private BigDecimal minimumSalary;

    @Column(name = "minimum_salary_currency")
    private String minimumSalaryCurrency;

    @Column(name = "is_active" ,nullable = false)
    private boolean isActive;

    @Column(name = "experience_years",precision = 4,scale = 1)
    private BigDecimal experienceYears;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_employment_types", columnDefinition = "jsonb")
    private JsonNode preferredEmploymentTypes;

    protected CandidateProfile(){
        //required by JPA
    }

    public CandidateProfile(String name,String email,String passwordHash,String currentLocation,JsonNode preferredLocations,BigDecimal minimumSalary,String minimumSalaryCurrency,boolean isActive,BigDecimal experienceYears,JsonNode preferredEmploymentTypes){
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = false;
        this.currentLocation = currentLocation;
        this.preferredLocations = preferredLocations;
        this.minimumSalary = minimumSalary;
        this.minimumSalaryCurrency = minimumSalaryCurrency;
        this.isActive = isActive;
        this.experienceYears = experienceYears;
        this.preferredEmploymentTypes = preferredEmploymentTypes;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateEmail(String email) {
        this.email = email;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updateCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public void updatePreferredLocations(JsonNode preferredLocations) {
        this.preferredLocations = preferredLocations;
    }

    public void updateMinimumSalary(BigDecimal minimumSalary) {
        this.minimumSalary = minimumSalary;
    }

    public void updateMinimumSalaryCurrency(String minimumSalaryCurrency) {
        this.minimumSalaryCurrency = minimumSalaryCurrency;
    }

    public void updateExperienceYears(BigDecimal experienceYears) {
        this.experienceYears = experienceYears;
    }

    public void updatePreferredEmploymentTypes(JsonNode preferredEmploymentTypes) {
        this.preferredEmploymentTypes = preferredEmploymentTypes;
    }
}
