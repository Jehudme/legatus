package dev.legatus.server.web.rest;

import dev.legatus.server.service.ExecutionProfileService;
import dev.legatus.server.service.dto.ExecutionProfileDTO;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for managing {@link dev.legatus.server.domain.ExecutionProfile}.
 */
@RestController
@RequestMapping({ "/api/v1/execution-profiles", "/api/execution-profiles" })
public class ExecutionProfileResource {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionProfileResource.class);

    private final ExecutionProfileService executionProfileService;

    public ExecutionProfileResource(ExecutionProfileService executionProfileService) {
        this.executionProfileService = executionProfileService;
    }

    /**
     * {@code POST  /api/v1/execution-profiles} : Create a new execution profile.
     */
    @PostMapping("")
    public ResponseEntity<ExecutionProfileDTO> createProfile(@Valid @RequestBody ExecutionProfileDTO dto)
        throws URISyntaxException {
        LOG.debug("REST request to save ExecutionProfile : {}", dto);
        if (dto.getProfileId() != null && executionProfileService.findOne(dto.getProfileId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A profile with this ID already exists");
        }
        ExecutionProfileDTO result = executionProfileService.save(dto);
        return ResponseEntity.created(new URI("/api/v1/execution-profiles/" + result.getProfileId()))
            .body(result);
    }

    /**
     * {@code PUT  /api/v1/execution-profiles/:id} : Updates an existing execution profile.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExecutionProfileDTO> updateProfile(
        @PathVariable(value = "id") final String id,
        @Valid @RequestBody ExecutionProfileDTO dto
    ) {
        LOG.debug("REST request to update ExecutionProfile : {}, {}", id, dto);
        if (dto.getProfileId() == null) {
            dto.setProfileId(id);
        } else if (!Objects.equals(id, dto.getProfileId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path ID does not match body profileId");
        }

        if (executionProfileService.findOne(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }

        ExecutionProfileDTO result = executionProfileService.save(dto);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /api/v1/execution-profiles} : Get profiles for a function.
     */
    @GetMapping("")
    public ResponseEntity<List<ExecutionProfileDTO>> getProfilesByFunction(
        @RequestParam(name = "functionId", required = false) UUID functionId
    ) {
        LOG.debug("REST request to get ExecutionProfiles for function : {}", functionId);
        if (functionId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(executionProfileService.findAllByFunctionId(functionId));
    }

    /**
     * {@code GET  /api/v1/execution-profiles/:id} : Get the execution profile by id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExecutionProfileDTO> getProfile(@PathVariable("id") String id) {
        LOG.debug("REST request to get ExecutionProfile : {}", id);
        return executionProfileService.findOne(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * {@code DELETE  /api/v1/execution-profiles/:id} : Delete the execution profile by id.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ExecutionProfile : {}", id);
        executionProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
